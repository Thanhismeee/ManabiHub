package com.manabihub.payout.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.enums.PhoneOtpMethod;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.mail.EmailService;
import com.manabihub.common.util.PhoneNumberNormalizer;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.FirebasePhoneIdentityVerifier;
import com.manabihub.identity.service.FirebasePhoneVerificationException;
import com.manabihub.identity.service.VerifiedFirebasePhone;
import com.manabihub.payout.config.WithdrawalOtpProperties;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import com.manabihub.payout.entity.WithdrawalOtpChallenge;
import com.manabihub.payout.repository.WithdrawalOtpChallengeRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalOtpService {

    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration FIREBASE_AUTH_TIME_SKEW = Duration.ofSeconds(5);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final WithdrawalOtpChallengeRepository challengeRepository;
    private final AppUserRepository appUserRepository;
    private final EmailService emailService;
    private final PayoutSecurityService securityService;
    private final WithdrawalOtpProperties otpProperties;
    private final FirebasePhoneIdentityVerifier firebasePhoneIdentityVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public WithdrawalOtpResponse sendOtp(String userId) {
        UUID userUuid = parseUserId(userId);
        AppUser user = appUserRepository.findByIdForUpdate(userUuid)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "User not found"
                ));
        boolean firebaseMode = otpProperties.isFirebaseMode();
        if (!firebaseMode && (user.getEmail() == null || user.getEmail().isBlank())) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_EMAIL_REQUIRED,
                    "User does not have an email address configured"
            );
        }
        if (firebaseMode && (user.getPhoneVerifiedAt() == null
                || user.getPhoneNumber() == null
                || user.getPhoneNumber().isBlank())) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PHONE_VERIFICATION_REQUIRED,
                    "A verified phone number is required before withdrawal",
                    HttpStatus.FORBIDDEN
            );
        }

        Instant now = Instant.now();
        WithdrawalOtpChallenge challenge = challengeRepository.findById(userUuid).orElse(null);
        if (challenge != null && challenge.getResendAvailableAt().isAfter(now)) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_OTP_RATE_LIMITED,
                    "Please wait before requesting another OTP",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        UUID challengeId = UUID.randomUUID();
        String code = firebaseMode ? null : String.format("%06d", secureRandom.nextInt(1_000_000));
        String nonce = firebaseMode ? null : securityService.newOtpNonce();
        if (challenge == null) {
            challenge = WithdrawalOtpChallenge.builder()
                    .userId(userUuid)
                    .createdAt(now)
                    .build();
        }
        challenge.setChallengeId(challengeId);
        challenge.setVerificationMethod(firebaseMode ? PhoneOtpMethod.FIREBASE : PhoneOtpMethod.EMAIL);
        challenge.setPhoneNumber(firebaseMode ? user.getPhoneNumber() : null);
        challenge.setNonce(nonce);
        challenge.setCodeHash(firebaseMode ? null : securityService.hashOtp(userUuid, nonce, code));
        challenge.setExpiresAt(now.plus(OTP_LIFETIME));
        challenge.setResendAvailableAt(now.plus(RESEND_COOLDOWN));
        challenge.setFailedAttempts(0);
        challenge.setUpdatedAt(now);
        challengeRepository.saveAndFlush(challenge);

        if (!firebaseMode) {
            emailService.sendEmail(
                    user.getEmail(),
                    "[ManabiHub] Mã xác thực rút tiền",
                    "<p>Xin chào,</p>"
                            + "<p>Bạn vừa yêu cầu rút tiền từ ví trên ManabiHub. "
                            + "Mã xác thực (OTP) của bạn là:</p>"
                            + "<h2 style=\"color: #2563eb; letter-spacing: 5px;\">"
                            + code
                            + "</h2>"
                            + "<p>Mã này sẽ hết hạn trong vòng 5 phút. "
                            + "Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>"
                            + "<br><p>Trân trọng,<br>Đội ngũ ManabiHub</p>"
            );
        }
        log.info("Withdrawal {} challenge created for user {}", challenge.getVerificationMethod(), userUuid);
        return new WithdrawalOtpResponse(
                challenge.getVerificationMethod().name(),
                challengeId,
                firebaseMode ? PhoneNumberNormalizer.toE164(user.getPhoneNumber()) : null,
                firebaseMode ? maskPhone(user.getPhoneNumber()) : maskEmail(user.getEmail()),
                challenge.getExpiresAt()
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class
    )
    public void consumeOtp(String userId, String code) {
        consumeLocked(userId, code, null, null);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class
    )
    public void consumeVerification(String userId, CreateWithdrawalRequest request) {
        if (request == null) {
            throw invalidOtp("Invalid or expired OTP");
        }
        consumeLocked(
                userId,
                request.getOtpCode(),
                request.getPhoneAuthChallengeId(),
                request.getFirebaseIdToken()
        );
    }

    private void consumeLocked(
            String userId,
            String code,
            UUID challengeId,
            String firebaseIdToken
    ) {
        UUID userUuid = parseUserId(userId);
        WithdrawalOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userUuid)
                .orElseThrow(() -> invalidOtp("Invalid or expired OTP"));
        Instant now = Instant.now();

        if (!challenge.getExpiresAt().isAfter(now)) {
            challengeRepository.delete(challenge);
            throw invalidOtp("Invalid or expired OTP");
        }
        if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw invalidOtp("OTP attempt limit exceeded");
        }
        boolean proofMatches = challenge.getVerificationMethod() == PhoneOtpMethod.FIREBASE
                ? firebaseProofMatches(challenge, challengeId, firebaseIdToken, now)
                : emailProofMatches(userUuid, challenge, code);
        if (!proofMatches) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            challenge.setUpdatedAt(now);
            challengeRepository.save(challenge);
            throw invalidOtp(
                    challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS
                            ? "OTP attempt limit exceeded"
                            : "Invalid or expired OTP"
            );
        }

        challengeRepository.delete(challenge);
    }

    private boolean emailProofMatches(
            UUID userId,
            WithdrawalOtpChallenge challenge,
            String code
    ) {
        return code != null
                && challenge.getNonce() != null
                && challenge.getCodeHash() != null
                && securityService.otpMatches(
                        userId, challenge.getNonce(), code, challenge.getCodeHash());
    }

    private boolean firebaseProofMatches(
            WithdrawalOtpChallenge challenge,
            UUID challengeId,
            String firebaseIdToken,
            Instant now
    ) {
        if (!Objects.equals(challenge.getChallengeId(), challengeId)
                || firebaseIdToken == null
                || firebaseIdToken.isBlank()) {
            return false;
        }
        try {
            VerifiedFirebasePhone verified = firebasePhoneIdentityVerifier.verify(firebaseIdToken);
            Instant challengeIssuedAt = challenge.getUpdatedAt() != null
                    ? challenge.getUpdatedAt()
                    : challenge.getCreatedAt();
            return Objects.equals(challenge.getPhoneNumber(), verified.phoneNumber())
                    && !verified.authenticatedAt().isBefore(
                            challengeIssuedAt.minus(FIREBASE_AUTH_TIME_SKEW))
                    && !verified.authenticatedAt().isAfter(now.plusSeconds(30));
        } catch (FirebasePhoneVerificationException exception) {
            if (exception.isConfigurationFailure()) {
                throw new BusinessException(
                        MessageCodes.PAYOUT_PHONE_AUTH_NOT_CONFIGURED,
                        "Firebase Phone Auth is not configured",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        exception
                );
            }
            return false;
        }
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    MessageCodes.AUTH_UNAUTHORIZED,
                    "Invalid authenticated user"
            );
        }
    }

    private BusinessException invalidOtp(String message) {
        return new BusinessException(MessageCodes.PAYOUT_INVALID_OTP, message);
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "******" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
