package com.manabihub.payout.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.payout.dto.request.BankAccountDto;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.TeacherBankAccountResponse;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.TeacherBankAccount;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.payout.mapper.WithdrawalMapper;
import com.manabihub.payout.repository.TeacherBankAccountRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import com.manabihub.payout.service.WithdrawalService;
import com.manabihub.payout.service.WithdrawalOtpService;
import com.manabihub.payout.service.WithdrawalNotificationService;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRepository;
    private final WalletRepository walletRepository;
    private final TeacherBankAccountRepository bankAccountRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final KycRequestRepository kycRequestRepository;
    private final AppUserRepository appUserRepository;
    private final WalletService walletService;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalNotificationService notificationService;
    private final WithdrawalOtpService otpService;
    private final PayoutSecurityService securityService;
    private final CommercialPolicyService commercialPolicyService;
    private final AccountIdentityVerificationService accountIdentityVerificationService;

    @Value("${manabihub.kyc.identity-verification-mode:direct-sdk-mock}")
    private String identityVerificationMode;

    @Override
    @Transactional
    public WithdrawalRequestResponse createWithdrawalRequest(String userId, CreateWithdrawalRequest request) {
        BigDecimal configuredPayoutThreshold =
                commercialPolicyService.getCurrentPolicy().payoutThreshold();
        if (request.getAmount().compareTo(configuredPayoutThreshold) < 0) {
            throw new BusinessException(MessageCodes.PAYOUT_AMOUNT_BELOW_MINIMUM, "Amount below minimum threshold");
        }

        UUID userUuid = UUID.fromString(userId);
        TeacherProfile teacherProfile = resolveTeacherProfile(userUuid);
        if (appUserRepository.findById(userUuid)
                .map(user -> user.getPhoneVerifiedAt() == null)
                .orElse(true)) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PHONE_VERIFICATION_REQUIRED,
                    "Vui lòng xác minh số điện thoại trước khi rút tiền",
                    HttpStatus.FORBIDDEN);
        }
        var latestKyc = kycRequestRepository
                .findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                .orElse(null);
        boolean identityVerified = accountIdentityVerificationService.findVerified(userUuid).isPresent()
                || (latestKyc != null
                && latestKyc.getIdentityStatus() == IdentityVerificationStatus.VERIFIED
                && (isDirectSdkPayoutMode() || latestKyc.getServerVerifiedAt() != null));
        if (!identityVerified) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Vui lòng hoàn tất xác minh CCCD trước khi rút tiền",
                    HttpStatus.FORBIDDEN);
        }
        BankQrPayload bankQr = decodeBankQr(request.getBankQrDataUrl());
        UUID teacherProfileId = teacherProfile.getId();

        // Serialize withdrawal creation per teacher across all application instances.
        var wallet = walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(
                        WalletOwnerType.TEACHER, teacherProfileId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WALLET_NOT_FOUND,
                        "Wallet not found"
                ));

        long pendingCount = withdrawalRepository.countByTeacherIdAndStatus(
                teacherProfileId,
                WithdrawalStatus.PENDING
        );
        if (pendingCount > 0) {
            throw new BusinessException(MessageCodes.PAYOUT_PENDING_REQUEST_EXISTS, "You already have a pending withdrawal request.");
        }

        long monthlyCount = withdrawalRepository.countByTeacherIdAndCreatedAtAfter(
                teacherProfileId,
                java.time.LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0)
        );
        if (monthlyCount >= 2) {
            throw new BusinessException(MessageCodes.PAYOUT_MONTHLY_LIMIT_EXCEEDED, "You can only make 2 withdrawal requests per month.");
        }

        BankAccountSnapshot snapshot = buildSnapshot(teacherProfileId, request);
        otpService.consumeVerification(userId, request);

        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .teacherId(teacherProfileId)
                .walletId(wallet.getId())
                .ownerType(WalletOwnerType.TEACHER)
                .requestedAmount(request.getAmount())
                .status(WithdrawalStatus.PENDING)
                .bankAccountSnapshot(snapshot)
                .bankQrCode(bankQr.bytes())
                .bankQrContentType(bankQr.contentType())
                .build();
        
        try {
            withdrawalRequest = withdrawalRepository.saveAndFlush(withdrawalRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PENDING_REQUEST_EXISTS,
                    "You already have a pending withdrawal request.",
                    HttpStatus.CONFLICT,
                    exception
            );
        }

        walletService.reserveBalance(
                teacherProfileId.toString(),
                request.getAmount(),
                withdrawalRequest.getId().toString()
        );

        UUID withdrawalId = withdrawalRequest.getId();
        notifyAfterCommit(
                () -> notificationService.notifyFinanceManager(
                        withdrawalId,
                        request.getAmount()
                ),
                "withdrawal request " + withdrawalId
        );

        if (request.isSaveAccount() && request.getBankAccount() != null) {
            saveTeacherBankAccount(teacherProfileId, request.getBankAccount());
        }

        return withdrawalMapper.toResponse(withdrawalRequest);
    }

    private void saveTeacherBankAccount(UUID teacherId, BankAccountDto dto) {
        String fingerprint = securityService.fingerprintAccountNumber(dto.getAccountNumber());
        var existing = bankAccountRepository.findByTeacherIdAndAccountFingerprint(
                teacherId,
                fingerprint
        );
        if (existing.isEmpty()) {
            TeacherBankAccount newAccount = TeacherBankAccount.builder()
                    .teacherId(teacherId)
                    .bankCode(dto.getBankCode())
                    .bankName(dto.getBankName())
                    .accountNumber(securityService.encryptAccountNumber(dto.getAccountNumber()))
                    .accountFingerprint(fingerprint)
                    .accountHolderName(dto.getAccountHolderName())
                    .branch(dto.getBranch())
                    .isDefault(false)
                    .build();
            bankAccountRepository.save(newAccount);
        }
    }

    private BankAccountSnapshot buildSnapshot(
            UUID teacherId,
            CreateWithdrawalRequest request
    ) {
        if (request.getBankAccountId() != null && !request.getBankAccountId().isBlank()) {
            UUID accountId;
            try {
                accountId = UUID.fromString(request.getBankAccountId());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        MessageCodes.PAYOUT_BANK_ACCOUNT_REQUIRED,
                        "Saved bank account was not found"
                );
            }
            TeacherBankAccount account = bankAccountRepository
                    .findByIdAndTeacherId(accountId, teacherId)
                    .orElseThrow(() -> new BusinessException(
                            MessageCodes.PAYOUT_BANK_ACCOUNT_REQUIRED,
                            "Saved bank account was not found"
                    ));
            return BankAccountSnapshot.builder()
                    .bankCode(account.getBankCode())
                    .bankName(account.getBankName())
                    .accountHolderName(account.getAccountHolderName())
                    .accountNumber(account.getAccountNumber())
                    .branch(account.getBranch())
                    .build();
        }

        BankAccountDto dto = request.getBankAccount();
        if (dto == null) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_BANK_ACCOUNT_REQUIRED,
                    "Bank account must be provided"
            );
        }
        return BankAccountSnapshot.builder()
                .bankCode(dto.getBankCode())
                .bankName(dto.getBankName())
                .accountHolderName(dto.getAccountHolderName())
                .accountNumber(securityService.encryptAccountNumber(dto.getAccountNumber()))
                .branch(dto.getBranch())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestResponse> getTeacherWithdrawals(String userId, Pageable pageable) {
        UUID teacherProfileId = resolveTeacherProfileId(userId);
        return withdrawalRepository.findByTeacherId(teacherProfileId, pageable)
                .map(withdrawalMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalDetail(String userId, String withdrawalId) {
        UUID teacherProfileId = resolveTeacherProfileId(userId);
        WithdrawalRequest request = withdrawalRepository.findByIdAndTeacherId(
                        UUID.fromString(withdrawalId),
                        teacherProfileId
                )
                .orElseThrow(() -> new BusinessException(MessageCodes.PAYOUT_WITHDRAWAL_NOT_FOUND, "Withdrawal request not found"));
        return withdrawalMapper.toResponse(request);
    }

    @Override
    @Transactional
    public void cancelWithdrawal(String userId, String withdrawalId) {
        UUID teacherProfileId = resolveTeacherProfileId(userId);
        WithdrawalRequest request = withdrawalRepository.findByIdAndTeacherIdWithLock(
                        UUID.fromString(withdrawalId),
                        teacherProfileId
                )
                .orElseThrow(() -> new BusinessException(MessageCodes.PAYOUT_WITHDRAWAL_NOT_FOUND, "Withdrawal request not found"));

        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new BusinessException("PAYOUT_CANNOT_CANCEL", "Only pending withdrawals can be cancelled");
        }

        request.setStatus(WithdrawalStatus.CANCELLED);
        withdrawalRepository.save(request);

        walletService.releaseBalance(
                teacherProfileId.toString(),
                request.getRequestedAmount(),
                withdrawalId
        );

        notifyAfterCommit(
                () -> notificationService.notifyTeacherCancellation(
                        UUID.fromString(userId),
                        teacherEmail(teacherProfileId),
                        request.getRequestedAmount()
                ),
                "withdrawal cancellation " + withdrawalId
        );
    }

    @Override
    public WithdrawalOtpResponse sendWithdrawalOtp(String userId) {
        return otpService.sendOtp(userId);
    }

    @Override
    public java.util.List<TeacherBankAccountResponse> getSavedBankAccounts(String userId) {
        UUID teacherProfileId = resolveTeacherProfileId(userId);
        return bankAccountRepository.findByTeacherIdOrderByCreatedAtDesc(teacherProfileId).stream()
                .map(account -> TeacherBankAccountResponse.builder()
                        .id(account.getId().toString())
                        .bankCode(account.getBankCode())
                        .bankName(account.getBankName())
                        .accountNumber(securityService.maskAccountNumber(account.getAccountNumber()))
                        .accountHolderName(account.getAccountHolderName())
                        .branch(account.getBranch())
                        .isDefault(account.isDefault())
                        .build())
                .toList();
    }

    private UUID resolveTeacherProfileId(String userId) {
        return resolveTeacherProfile(UUID.fromString(userId)).getId();
    }

    private boolean isDirectSdkPayoutMode() {
        return !org.springframework.util.StringUtils.hasText(identityVerificationMode)
                || "direct-sdk-mock".equalsIgnoreCase(identityVerificationMode)
                || "direct-sdk".equalsIgnoreCase(identityVerificationMode);
    }

    private TeacherProfile resolveTeacherProfile(UUID userId) {
        return teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.KYC_TEACHER_NOT_FOUND,
                        "Teacher profile not found"
                ));
    }

    private BankQrPayload decodeBankQr(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_BANK_QR_REQUIRED,
                    "A bank QR image is required for Finance."
            );
        }
        int comma = dataUrl.indexOf(',');
        if (comma <= 0 || comma == dataUrl.length() - 1) {
            throw invalidBankQr();
        }
        String metadata = dataUrl.substring(0, comma).toLowerCase(Locale.ROOT);
        String encoded = dataUrl.substring(comma + 1);
        String contentType = metadata.startsWith("data:image/png;base64")
                ? "image/png"
                : metadata.startsWith("data:image/jpeg;base64") || metadata.startsWith("data:image/jpg;base64")
                    ? "image/jpeg"
                    : metadata.startsWith("data:image/webp;base64")
                        ? "image/webp"
                        : null;
        if (contentType == null) {
            throw invalidBankQr();
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw invalidBankQr();
        }
        if (bytes.length == 0 || bytes.length > 2L * 1024L * 1024L || !matchesSignature(bytes, contentType)) {
            throw invalidBankQr();
        }
        return new BankQrPayload(bytes, contentType);
    }

    private boolean matchesSignature(byte[] bytes, String contentType) {
        if ("image/png".equals(contentType)) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            return bytes.length >= signature.length
                    && java.util.Arrays.equals(signature, java.util.Arrays.copyOf(bytes, signature.length));
        }
        if ("image/jpeg".equals(contentType)) {
            return bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
        }
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private BusinessException invalidBankQr() {
        return new BusinessException(
                MessageCodes.PAYOUT_BANK_QR_INVALID,
                "The bank QR image is invalid. Upload a PNG, JPEG, or WEBP image under 2 MB."
        );
    }

    private record BankQrPayload(byte[] bytes, String contentType) {
    }

    private String teacherEmail(UUID teacherProfileId) {
        return teacherProfileRepository.findById(teacherProfileId)
                .map(profile -> profile.getUser().getEmail())
                .orElse(null);
    }

    private void notifyAfterCommit(Runnable notification, String operation) {
        Runnable safeNotification = () -> {
            try {
                notification.run();
            } catch (Exception exception) {
                log.warn("Failed to send notification for {}", operation, exception);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeNotification.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safeNotification.run();
                    }
                }
        );
    }
}
