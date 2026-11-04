package com.manabihub.wallet.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import com.manabihub.wallet.dto.response.TeacherWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.mapper.WalletMapper;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {


    private final TeacherProfileRepository teacherProfileRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletMapper walletMapper;
    private final CommercialPolicyService commercialPolicyService;
    private final EscrowLedgerRepository escrowLedgerRepository;

    @Override
    @Transactional(readOnly = true)
    public TeacherWalletResponse getTeacherWalletByUserId(UUID userId) {
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.KYC_TEACHER_NOT_FOUND,
                        "Teacher profile not found",
                        HttpStatus.NOT_FOUND));

        Wallet wallet = walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacherProfile.getId())
                .orElseThrow(() -> new BusinessException(MessageCodes.WALLET_NOT_FOUND, "Teacher wallet not found"));

        CommercialPolicy policy = commercialPolicyService.getCurrentPolicy();
        int clearingPeriodDays = policy.escrowHoldingDays();
        LocalDate nextPayoutDate = LocalDate.now().plusDays(clearingPeriodDays);

        TeacherWalletResponse response = walletMapper.toResponse(
                wallet,
                policy.payoutThreshold(),
                clearingPeriodDays,
                nextPayoutDate);

        // frozenBalance contains both escrow holds and withdrawal reservations.
        // Expose the two buckets separately so a teacher cannot mistake held funds
        // for money that has already settled into the withdrawable wallet.
        BigDecimal heldEscrow = BigDecimal.ZERO;
        if (escrowLedgerRepository != null) {
            heldEscrow = safeAmount(escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                    teacherProfile.getId(), EscrowStatus.HELD))
                    .add(safeAmount(escrowLedgerRepository.sumAmountByTeacherIdAndStatus(
                            teacherProfile.getId(), EscrowStatus.FROZEN)));
        }
        response.setPendingBalance(heldEscrow);
        response.setReservedBalance(safeAmount(wallet.getFrozenBalance())
                .subtract(heldEscrow)
                .max(BigDecimal.ZERO));
        return response;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    @Override
    @Transactional
    public Wallet getOrCreatePlatformWallet() {
        return walletRepository.findFirstByOwnerType(WalletOwnerType.PLATFORM)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .ownerType(WalletOwnerType.PLATFORM)
                        .build()));
    }

    @Override
    @Transactional
    public Wallet getOrCreateTeacherWallet(TeacherProfile teacher) {
        return walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .ownerType(WalletOwnerType.TEACHER)
                        .teacher(teacher)
                        .build()));
    }

    @Override
    @Transactional
    public WalletTransaction holdEscrow(TeacherProfile teacher, BigDecimal amount,
                                        String referenceType, UUID referenceId, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Escrow hold amount must be positive");
        }

        Wallet wallet = getOrCreateTeacherWallet(teacher);
        Wallet locked = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WALLET_NOT_FOUND,
                        "Teacher wallet was not found",
                        HttpStatus.NOT_FOUND));

        locked.setBalance(locked.getBalance().add(amount));
        locked.setFrozenBalance(locked.getFrozenBalance().add(amount));
        walletRepository.save(locked);

        return transactionRepository.save(WalletTransaction.builder()
                .walletId(locked.getId())
                .transactionType(WalletTransactionType.ESCROW_HOLD)
                .amount(amount)
                .direction(WalletDirection.IN)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .note(note)
                .build());
    }

    @Override
    @Transactional
    public void reserveBalance(String teacherId, BigDecimal amount, String withdrawalId) {
        Wallet wallet = walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(WalletOwnerType.TEACHER, UUID.fromString(teacherId))
                .orElseThrow(() -> new BusinessException(MessageCodes.WALLET_NOT_FOUND, "Teacher wallet not found"));

        if (wallet.isFrozen()) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_BALANCE_FROZEN,
                    "Teacher wallet is frozen and cannot create a withdrawal"
            );
        }

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(MessageCodes.WALLET_INSUFFICIENT_BALANCE, "Insufficient available balance");
        }

        // Update balances for V002 schema
        // Available balance is inherently (balance - frozenBalance)
        // To reserve money, we just increase frozenBalance. The total balance remains unchanged until actual payout.
        wallet.setFrozenBalance(wallet.getFrozenBalance().add(amount));
        
        walletRepository.save(wallet);

        // Create Transaction
        WalletTransaction transaction = WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.WITHDRAWAL_RESERVATION)
                .amount(amount) // magnitude only; WalletDirection carries the sign
                .direction(WalletDirection.OUT)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(UUID.fromString(withdrawalId))
                .build();
                
        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void releaseBalance(String teacherId, BigDecimal amount, String withdrawalId) {
        Wallet wallet = walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(WalletOwnerType.TEACHER, UUID.fromString(teacherId))
                .orElseThrow(() -> new BusinessException(MessageCodes.WALLET_NOT_FOUND, "Teacher wallet not found"));

        if (wallet.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(MessageCodes.WALLET_INSUFFICIENT_BALANCE, "Insufficient frozen balance to release");
        }

        // Update balances: decrease frozen, which inherently increases available balance
        wallet.setFrozenBalance(wallet.getFrozenBalance().subtract(amount));
        
        walletRepository.save(wallet);

        // Create Transaction
        WalletTransaction transaction = WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(WalletTransactionType.WITHDRAWAL_CANCELLED)
                .amount(amount) // positive for adding back to available
                .direction(WalletDirection.IN)
                .referenceType("WITHDRAWAL_REQUEST")
                .referenceId(UUID.fromString(withdrawalId))
                .note("Refund for cancelled withdrawal")
                .build();
                
        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public WalletTransaction releaseEscrow(TeacherProfile teacher, BigDecimal amount,
                                           String referenceType, UUID referenceId, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Escrow release amount must be positive");
        }

        Wallet wallet = getOrCreateTeacherWallet(teacher);

        Wallet locked = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WALLET_NOT_FOUND,
                        "Teacher wallet was not found",
                        HttpStatus.NOT_FOUND));

        if (locked.isFrozen()) {
            throw new BusinessException(
                    MessageCodes.WALLET_FROZEN,
                    "Teacher wallet is frozen and cannot receive an escrow release");
        }

        if (locked.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    MessageCodes.WALLET_INSUFFICIENT_BALANCE,
                    "Frozen wallet balance is lower than the escrow release amount");
        }

        locked.setFrozenBalance(locked.getFrozenBalance().subtract(amount));
        walletRepository.save(locked);

        WalletTransaction transaction = WalletTransaction.builder()
                .walletId(locked.getId())
                .transactionType(WalletTransactionType.ESCROW_RELEASE)
                .amount(amount)
                .direction(WalletDirection.IN)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .note(note)
                .build();

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public WalletTransaction refundHeldEscrow(
            TeacherProfile teacher,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            String note
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Escrow refund amount must be positive");
        }

        return transactionRepository
                .findByReferenceTypeAndReferenceIdAndTransactionType(
                        referenceType,
                        referenceId,
                        WalletTransactionType.REFUND)
                .orElseGet(() -> {
                    Wallet wallet = getOrCreateTeacherWallet(teacher);
                    Wallet locked = walletRepository.findByIdForUpdate(wallet.getId())
                            .orElseThrow(() -> new BusinessException(
                                    MessageCodes.WALLET_NOT_FOUND,
                                    "Teacher wallet was not found",
                                    HttpStatus.NOT_FOUND));

                    if (locked.getFrozenBalance().compareTo(amount) < 0
                            || locked.getBalance().compareTo(amount) < 0) {
                        throw new BusinessException(
                                MessageCodes.WALLET_INSUFFICIENT_BALANCE,
                                "Wallet balance is lower than the escrow refund amount",
                                HttpStatus.CONFLICT);
                    }

                    locked.setBalance(locked.getBalance().subtract(amount));
                    locked.setFrozenBalance(locked.getFrozenBalance().subtract(amount));
                    walletRepository.save(locked);

                    return transactionRepository.save(WalletTransaction.builder()
                            .walletId(locked.getId())
                            .transactionType(WalletTransactionType.REFUND)
                            .amount(amount)
                            .direction(WalletDirection.OUT)
                            .referenceType(referenceType)
                            .referenceId(referenceId)
                            .note(note)
                            .build());
                });
    }


}
