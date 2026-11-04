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
import com.manabihub.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private WalletMapper walletMapper;
    @Mock private CommercialPolicyService commercialPolicyService;
    @Mock private com.manabihub.wallet.repository.EscrowLedgerRepository escrowLedgerRepository;

    @InjectMocks
    private WalletServiceImpl service;

    private TeacherProfile teacher;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        lenient().when(commercialPolicyService.getCurrentPolicy())
                .thenReturn(policy(new BigDecimal("500000.00"), 14));
        teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.TEACHER)
                .teacher(teacher)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void getTeacherWallet_resolvesTeacherProfileBeforeWalletLookup() {
        UUID userId = UUID.randomUUID();
        Wallet teacherWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.TEACHER)
                .teacher(teacher)
                .balance(new BigDecimal("5000000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        TeacherWalletResponse expected = new TeacherWalletResponse();

        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacher));
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(teacherWallet));
        when(walletMapper.toResponse(
                eq(teacherWallet),
                eq(new BigDecimal("500000.00")),
                eq(14),
                any(LocalDate.class)))
                .thenReturn(expected);

        TeacherWalletResponse actual = service.getTeacherWalletByUserId(userId);

        assertSame(expected, actual);
        verify(teacherProfileRepository).findByUserId(userId);
        verify(walletRepository).findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId());
        verify(walletMapper).toResponse(
                teacherWallet,
                new BigDecimal("500000.00"),
                14,
                LocalDate.now().plusDays(14));
    }

    @Test
    void holdEscrow_incrementsTotalAndFrozenWithoutChangingAvailableBalance() {
        UUID referenceId = UUID.randomUUID();
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletTransaction tx = service.holdEscrow(
                teacher, new BigDecimal("150000.00"), "ORDER", referenceId, "Escrow hold for order OD1");

        assertEquals(new BigDecimal("150000.00"), wallet.getFrozenBalance());
        assertEquals(new BigDecimal("150000.00"), wallet.getBalance());
        assertEquals(
                BigDecimal.ZERO.setScale(2),
                wallet.getBalance().subtract(wallet.getFrozenBalance()));

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        WalletTransaction saved = captor.getValue();
        assertEquals(WalletTransactionType.ESCROW_HOLD, saved.getTransactionType());
        assertEquals(WalletDirection.IN, saved.getDirection());
        assertEquals(new BigDecimal("150000.00"), saved.getAmount());
        assertEquals("ORDER", saved.getReferenceType());
        assertEquals(referenceId, saved.getReferenceId());
        assertEquals(tx, saved);
    }

    @Test
    void getOrCreateTeacherWallet_whenMissing_createsNewWallet() {
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet created = service.getOrCreateTeacherWallet(teacher);

        assertEquals(WalletOwnerType.TEACHER, created.getOwnerType());
        assertEquals(teacher, created.getTeacher());
    }

    @Test
    void reserveBalance_whenWalletIsFrozen_rejectsWithdrawalBeforeMutation() {
        UUID withdrawalId = UUID.randomUUID();
        Wallet teacherWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.TEACHER)
                .teacher(teacher)
                .balance(new BigDecimal("2000000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .frozen(true)
                .build();
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(teacherWallet));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.reserveBalance(
                        teacher.getId().toString(),
                        new BigDecimal("500000.00"),
                        withdrawalId.toString()
                )
        );

        assertEquals(MessageCodes.PAYOUT_BALANCE_FROZEN, exception.getMessageCode());
        assertEquals(new BigDecimal("2000000.00"), teacherWallet.getBalance());
        assertEquals(BigDecimal.ZERO, teacherWallet.getFrozenBalance());
        verifyNoInteractions(walletTransactionRepository);
    }

    @Test
    void releaseEscrow_whenWalletIsFrozen_rejectsWithoutMutation() {
        UUID escrowId = UUID.randomUUID();
        wallet.setFrozen(true);
        wallet.setBalance(new BigDecimal("150000.00"));
        wallet.setFrozenBalance(new BigDecimal("150000.00"));
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.releaseEscrow(
                        teacher,
                        new BigDecimal("150000.00"),
                        "ESCROW",
                        escrowId,
                        "Release"));

        assertEquals(MessageCodes.WALLET_FROZEN, exception.getMessageCode());
        assertEquals(new BigDecimal("150000.00"), wallet.getBalance());
        assertEquals(new BigDecimal("150000.00"), wallet.getFrozenBalance());
        verifyNoInteractions(walletTransactionRepository);
    }

    @Test
    void releaseEscrow_whenFrozenBalanceIsInsufficient_rejectsWithoutMutation() {
        UUID escrowId = UUID.randomUUID();
        wallet.setBalance(new BigDecimal("100000.00"));
        wallet.setFrozenBalance(new BigDecimal("100000.00"));
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.releaseEscrow(
                        teacher,
                        new BigDecimal("150000.00"),
                        "ESCROW",
                        escrowId,
                        "Release"));

        assertEquals(MessageCodes.WALLET_INSUFFICIENT_BALANCE, exception.getMessageCode());
        assertEquals(new BigDecimal("100000.00"), wallet.getBalance());
        assertEquals(new BigDecimal("100000.00"), wallet.getFrozenBalance());
        verifyNoInteractions(walletTransactionRepository);
    }

    @Test
    void releaseEscrow_whenEligibleUnfreezesFundsWithoutDoubleCreditingTotal() {
        UUID escrowId = UUID.randomUUID();
        wallet.setBalance(new BigDecimal("150000.00"));
        wallet.setFrozenBalance(new BigDecimal("150000.00"));
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction transaction = service.releaseEscrow(
                teacher,
                new BigDecimal("150000.00"),
                "ESCROW",
                escrowId,
                "Release");

        assertEquals(new BigDecimal("150000.00"), wallet.getBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenBalance());
        assertEquals(new BigDecimal("150000.00"), wallet.getBalance().subtract(wallet.getFrozenBalance()));
        assertEquals(WalletTransactionType.ESCROW_RELEASE, transaction.getTransactionType());
        assertEquals(WalletDirection.IN, transaction.getDirection());
        assertEquals(escrowId, transaction.getReferenceId());
    }

    @Test
    void refundHeldEscrow_decrementsTotalAndFrozenAndPreservesAvailableBalance() {
        UUID escrowId = UUID.randomUUID();
        wallet.setBalance(new BigDecimal("130000.00"));
        wallet.setFrozenBalance(new BigDecimal("80000.00"));
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "ESCROW",
                escrowId,
                WalletTransactionType.REFUND))
                .thenReturn(Optional.empty());
        when(walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId()))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction transaction = service.refundHeldEscrow(
                teacher,
                new BigDecimal("80000.00"),
                "ESCROW",
                escrowId,
                "Refund");

        assertEquals(new BigDecimal("50000.00"), wallet.getBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenBalance());
        assertEquals(new BigDecimal("50000.00"), wallet.getBalance().subtract(wallet.getFrozenBalance()));
        assertEquals(WalletTransactionType.REFUND, transaction.getTransactionType());
        assertEquals(WalletDirection.OUT, transaction.getDirection());
        assertEquals(new BigDecimal("80000.00"), transaction.getAmount());
        assertEquals(escrowId, transaction.getReferenceId());
    }

    @Test
    void refundHeldEscrow_whenTransactionAlreadyExistsIsIdempotent() {
        UUID escrowId = UUID.randomUUID();
        WalletTransaction existing = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionType(WalletTransactionType.REFUND)
                .referenceType("ESCROW")
                .referenceId(escrowId)
                .build();
        when(walletTransactionRepository.findByReferenceTypeAndReferenceIdAndTransactionType(
                "ESCROW",
                escrowId,
                WalletTransactionType.REFUND))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.refundHeldEscrow(
                teacher,
                new BigDecimal("80000.00"),
                "ESCROW",
                escrowId,
                "Refund"));

        verifyNoInteractions(walletRepository);
    }

    private CommercialPolicy policy(BigDecimal payoutThreshold, int escrowHoldingDays) {
        return new CommercialPolicy(
                "VND",
                new BigDecimal("0.20"),
                7,
                30,
                escrowHoldingDays,
                payoutThreshold,
                BigDecimal.ZERO,
                1,
                2,
                "test-policy",
                Instant.parse("2026-07-28T00:00:00Z"));
    }
}
