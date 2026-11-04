package com.manabihub.wallet.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.wallet.dto.response.StudentWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletPaymentReservation;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletReservationStatus;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.repository.WalletPaymentReservationRepository;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.systemconfig.service.CommercialPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudentWalletServiceImpl}.
 * <p>
 * Grouped with {@code @Nested} so Surefire reports one summary line per Report 5.1 sheet:
 * <pre>
 *   StudentWalletServiceImplTest$GetWalletOverview -> sheet 42 getWalletOverview
 *   StudentWalletServiceImplTest$CreditTopUp       -> sheet 43 creditTopUp
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class StudentWalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private WalletPaymentReservationRepository reservationRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CommercialPolicyService commercialPolicyService;

    private StudentWalletServiceImpl service;
    private UUID studentId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new StudentWalletServiceImpl(
                walletRepository,
                walletTransactionRepository,
                reservationRepository,
                studentProfileRepository,
                commercialPolicyService);
        studentId = UUID.randomUUID();
        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .ownerType(WalletOwnerType.STUDENT)
                .balance(new BigDecimal("100000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .currency("VND")
                .build();
    }

    private void stubExistingWallet() {
        when(walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByOwnerTypeAndStudent_IdForUpdate(WalletOwnerType.STUDENT, studentId)).thenReturn(Optional.of(wallet));
    }

    @Nested
    @DisplayName("Promotional rewards")
    class PromotionalRewards {

        @Test
        void gameReward_increasesPurchasingBalanceButNeverWithdrawableBalance() {
            UUID challengeId = UUID.randomUUID();
            String idempotencyKey = "weekly-game:" + challengeId + ":" + studentId;
            stubExistingWallet();
            when(walletTransactionRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.empty());
            when(walletTransactionRepository.save(any(WalletTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WalletTransaction transaction = service.creditPromotionalReward(
                    studentId,
                    new BigDecimal("30000.00"),
                    WalletTransactionType.GAME_REWARD,
                    "WEEKLY_CHALLENGE",
                    challengeId,
                    idempotencyKey,
                    "Weekly game reward");

            assertEquals(new BigDecimal("130000.00"), wallet.getBalance());
            assertEquals(BigDecimal.ZERO, wallet.getWithdrawableBalance());
            assertEquals(WalletTransactionType.GAME_REWARD, transaction.getTransactionType());
            assertEquals(idempotencyKey, transaction.getIdempotencyKey());
        }

        @Test
        void promotionalReward_rejectsAWithdrawableTransactionType() {
            assertThrows(BusinessException.class, () -> service.creditPromotionalReward(
                    studentId,
                    BigDecimal.ONE,
                    WalletTransactionType.REFUND,
                    "REFUND_REQUEST",
                    UUID.randomUUID(),
                    "invalid-reward",
                    "Invalid reward"));

            verify(walletRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 42 — getWalletOverview (UC-17 Manage My Wallet) — 3 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 42 - getWalletOverview (UC-17)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetWalletOverview {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - wallet exists -> every balance of the own student")
        void getWalletOverview_existingWallet_returnsEveryBalanceOfTheOwnStudent() {
            UUID userId = UUID.randomUUID();
            wallet.setBalance(new BigDecimal("300000.00"));
            wallet.setFrozenBalance(new BigDecimal("50000.00"));
            wallet.setWithdrawableBalance(new BigDecimal("120000.00"));
            wallet.setFrozenWithdrawableBalance(new BigDecimal("20000.00"));
            when(studentProfileRepository.findByUser_Id(userId))
                    .thenReturn(Optional.of(StudentProfile.builder().id(studentId).build()));
            when(walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId))
                    .thenReturn(Optional.of(wallet));

            StudentWalletResponse overview = service.getWalletOverview(userId);

            assertEquals(new BigDecimal("300000.00"), overview.balance());
            assertEquals(new BigDecimal("50000.00"), overview.frozenBalance());
            assertEquals(new BigDecimal("250000.00"), overview.availableBalance());
            assertEquals(new BigDecimal("120000.00"), overview.withdrawableBalance());
            assertEquals(new BigDecimal("100000.00"), overview.availableWithdrawableBalance());
            assertEquals("VND", overview.currency());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (B) - wallet not created yet -> all balances 0, currency VND")
        void getWalletOverview_walletNotCreatedYet_returnsZeroBalancesInsteadOfFailing() {
            UUID userId = UUID.randomUUID();
            when(studentProfileRepository.findByUser_Id(userId))
                    .thenReturn(Optional.of(StudentProfile.builder().id(studentId).build()));
            when(walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId))
                    .thenReturn(Optional.empty());

            StudentWalletResponse overview = service.getWalletOverview(userId);

            assertEquals(BigDecimal.ZERO, overview.balance());
            assertEquals(BigDecimal.ZERO, overview.frozenBalance());
            assertEquals(BigDecimal.ZERO, overview.availableBalance());
            assertEquals(BigDecimal.ZERO, overview.withdrawableBalance());
            assertEquals(BigDecimal.ZERO, overview.availableWithdrawableBalance());
            assertEquals("VND", overview.currency());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - no student profile -> LEARNING_STUDENT_PROFILE_NOT_FOUND")
        void getWalletOverview_studentProfileMissing_throws() {
            UUID userId = UUID.randomUUID();
            when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.getWalletOverview(userId));

            assertEquals(MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND, error.getMessageCode());
            verify(walletRepository, never()).findByOwnerTypeAndStudent_Id(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 43 — creditTopUp (UC-17 Manage My Wallet) — 6 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 43 - creditTopUp (UC-17)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreditTopUp {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - amount 50000 -> balance 150000 + idempotent TOP_UP ledger")
        void creditTopUp_increasesBalanceAndWritesIdempotentLedger() {
            UUID orderId = UUID.randomUUID();
            stubExistingWallet();
            when(walletTransactionRepository.findByIdempotencyKey("wallet-topup:" + orderId))
                    .thenReturn(Optional.empty());
            when(walletTransactionRepository.save(any(WalletTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WalletTransaction transaction = service.creditTopUp(
                    studentId, new BigDecimal("50000.00"), orderId, "Top up");

            assertEquals(new BigDecimal("150000.00"), wallet.getBalance());
            assertEquals(WalletTransactionType.TOP_UP, transaction.getTransactionType());
            assertEquals(WalletDirection.IN, transaction.getDirection());
            assertEquals("wallet-topup:" + orderId, transaction.getIdempotencyKey());
            verify(walletRepository).save(wallet);
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - top-up does not raise withdrawableBalance, only refund does")
        void creditRefund_increasesWithdrawableBalanceButTopUpDoesNot() {
            UUID refundId = UUID.randomUUID();
            UUID topUpId = UUID.randomUUID();
            stubExistingWallet();
            when(walletTransactionRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());
            when(walletTransactionRepository.save(any(WalletTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            service.creditTopUp(
                    studentId, new BigDecimal("50000.00"), topUpId, "Top up");
            service.creditRefund(
                    studentId, new BigDecimal("80000.00"), refundId, "Refund");

            assertEquals(new BigDecimal("230000.00"), wallet.getBalance());
            assertEquals(new BigDecimal("80000.00"), wallet.getWithdrawableBalance());
            assertEquals(new BigDecimal("80000.00"), wallet.getAvailableWithdrawableBalance());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - replay of the same orderId -> existing ledger, no double credit")
        void creditTopUp_replayOfTheSameOrder_returnsExistingLedgerWithoutCreditingTwice() {
            UUID orderId = UUID.randomUUID();
            WalletTransaction existing = WalletTransaction.builder().id(UUID.randomUUID()).build();
            when(walletTransactionRepository.findByIdempotencyKey("wallet-topup:" + orderId))
                    .thenReturn(Optional.of(existing));

            assertSame(existing, service.creditTopUp(
                    studentId, new BigDecimal("50000.00"), orderId, "Top up"));

            assertEquals(new BigDecimal("100000.00"), wallet.getBalance());
            verify(walletRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - amount null -> COMMON_BAD_REQUEST")
        void creditTopUp_nullAmount_throws() {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.creditTopUp(studentId, null, UUID.randomUUID(), "Top up"));

            assertEquals(MessageCodes.COMMON_BAD_REQUEST, error.getMessageCode());
            verify(walletTransactionRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - amount -1000 -> COMMON_BAD_REQUEST")
        void creditTopUp_negativeAmount_throws() {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.creditTopUp(
                            studentId, new BigDecimal("-1000.00"), UUID.randomUUID(), "Top up"));

            assertEquals(MessageCodes.COMMON_BAD_REQUEST, error.getMessageCode());
            verify(walletTransactionRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (B) - amount 0 = lower bound -> COMMON_BAD_REQUEST")
        void creditTopUp_zeroAmount_throws() {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.creditTopUp(studentId, BigDecimal.ZERO, UUID.randomUUID(), "Top up"));

            assertEquals(MessageCodes.COMMON_BAD_REQUEST, error.getMessageCode());
            verify(walletTransactionRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - wallet reservation / withdrawal internals")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class WalletInternals {

        @Test
        @org.junit.jupiter.api.Order(1)
        void getOrCreateStudentWallet_whenWalletExists_returnsExistingAndDoesNotInsert() {
            when(walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId))
                    .thenReturn(Optional.of(wallet));

            assertSame(wallet, service.getOrCreateStudentWallet(studentId));

            verify(walletRepository, never()).insertStudentWalletIfAbsent(any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        void getOrCreateStudentWallet_whenWalletMissing_insertsAndReturnsCanonical() {
            when(walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, studentId))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(wallet));

            assertSame(wallet, service.getOrCreateStudentWallet(studentId));

            verify(walletRepository).insertStudentWalletIfAbsent(any(UUID.class), eq(studentId));
            verify(walletRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        void creditRefund_replayReturnsExistingLedgerWithoutCreditingTwice() {
            UUID refundId = UUID.randomUUID();
            WalletTransaction existing = WalletTransaction.builder().id(UUID.randomUUID()).build();
            when(walletTransactionRepository.findByIdempotencyKey("wallet-refund:" + refundId))
                    .thenReturn(Optional.of(existing));

            assertSame(existing, service.creditRefund(
                    studentId, new BigDecimal("250000.00"), refundId, "Refund"));

            assertEquals(new BigDecimal("100000.00"), wallet.getBalance());
            verify(walletRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        void orderReservationConsumesNonWithdrawableFundsBeforeRefundFunds() {
            UUID orderId = UUID.randomUUID();
            wallet.setBalance(new BigDecimal("200000.00"));
            wallet.setWithdrawableBalance(new BigDecimal("100000.00"));
            stubExistingWallet();
            when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
            when(reservationRepository.save(any(WalletPaymentReservation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WalletPaymentReservation reservation = service.reserveForOrder(
                    studentId,
                    orderId,
                    new BigDecimal("150000.00"),
                    Instant.now().plusSeconds(60));

            assertEquals(new BigDecimal("50000.00"), reservation.getWithdrawableAmount());
            assertEquals(new BigDecimal("150000.00"), wallet.getFrozenBalance());
            assertEquals(new BigDecimal("50000.00"), wallet.getFrozenWithdrawableBalance());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        void studentWithdrawalReserveReleaseAndCompleteAreCompositionSafe() {
            UUID cancelledId = UUID.randomUUID();
            UUID completedId = UUID.randomUUID();
            wallet.setBalance(new BigDecimal("300000.00"));
            wallet.setWithdrawableBalance(new BigDecimal("200000.00"));
            stubExistingWallet();
            when(walletTransactionRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());
            when(walletTransactionRepository.save(any(WalletTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(walletRepository.reserveStudentWithdrawalBalance(eq(studentId), any()))
                    .thenAnswer(invocation -> {
                        BigDecimal amount = invocation.getArgument(1);
                        wallet.setFrozenBalance(wallet.getFrozenBalance().add(amount));
                        wallet.setFrozenWithdrawableBalance(
                                wallet.getFrozenWithdrawableBalance().add(amount));
                        return 1;
                    });

            service.reserveForWithdrawal(
                    studentId, cancelledId, new BigDecimal("50000.00"));
            service.releaseWithdrawal(
                    studentId,
                    cancelledId,
                    new BigDecimal("50000.00"),
                    WalletTransactionType.WITHDRAWAL_CANCELLED,
                    "Cancelled");

            assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenBalance());
            assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenWithdrawableBalance());

            service.reserveForWithdrawal(
                    studentId, completedId, new BigDecimal("100000.00"));
            service.completeWithdrawal(
                    studentId, completedId, new BigDecimal("100000.00"));

            assertEquals(new BigDecimal("200000.00"), wallet.getBalance());
            assertEquals(new BigDecimal("100000.00"), wallet.getWithdrawableBalance());
            assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenBalance());
            assertEquals(BigDecimal.ZERO.setScale(2), wallet.getFrozenWithdrawableBalance());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        void reserveForOrder_freezesOnlyAvailableFunds() {
            UUID orderId = UUID.randomUUID();
            wallet.setFrozenBalance(new BigDecimal("20000.00"));
            stubExistingWallet();
            when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
            when(reservationRepository.save(any(WalletPaymentReservation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WalletPaymentReservation reservation = service.reserveForOrder(
                    studentId, orderId, new BigDecimal("70000.00"), Instant.now().plusSeconds(60));

            assertEquals(new BigDecimal("90000.00"), wallet.getFrozenBalance());
            assertEquals(WalletReservationStatus.RESERVED, reservation.getStatus());
            assertEquals(new BigDecimal("70000.00"), reservation.getAmount());
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        void reserveForOrder_rejectsAmountGreaterThanAvailableBalance() {
            UUID orderId = UUID.randomUUID();
            wallet.setFrozenBalance(new BigDecimal("20000.00"));
            stubExistingWallet();
            when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.reserveForOrder(
                    studentId, orderId, new BigDecimal("90000.00"), Instant.now().plusSeconds(60)));

            assertEquals(new BigDecimal("20000.00"), wallet.getFrozenBalance());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(8)
        void captureForOrder_debitsBalanceAndFrozenBalanceExactlyOnce() {
            UUID orderId = UUID.randomUUID();
            String key = "wallet-purchase:" + orderId;
            wallet.setFrozenBalance(new BigDecimal("30000.00"));
            WalletPaymentReservation reservation = WalletPaymentReservation.builder()
                    .walletId(wallet.getId())
                    .orderId(orderId)
                    .amount(new BigDecimal("30000.00"))
                    .status(WalletReservationStatus.RESERVED)
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
            when(walletTransactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(reservation));
            when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
            when(walletTransactionRepository.save(any(WalletTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WalletTransaction transaction = service.captureForOrder(orderId, Instant.now());

            assertEquals(new BigDecimal("70000.00"), wallet.getBalance());
            assertEquals(new BigDecimal("0.00"), wallet.getFrozenBalance());
            assertEquals(WalletReservationStatus.CAPTURED, reservation.getStatus());
            assertEquals(key, transaction.getIdempotencyKey());
            assertEquals(WalletTransactionType.PURCHASE, transaction.getTransactionType());
        }

        @Test
        @org.junit.jupiter.api.Order(9)
        void releaseForOrder_unfreezesFundsAndIsIdempotent() {
            UUID orderId = UUID.randomUUID();
            wallet.setFrozenBalance(new BigDecimal("30000.00"));
            WalletPaymentReservation reservation = WalletPaymentReservation.builder()
                    .walletId(wallet.getId())
                    .orderId(orderId)
                    .amount(new BigDecimal("30000.00"))
                    .status(WalletReservationStatus.RESERVED)
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
            when(reservationRepository.findByOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(reservation));
            when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

            service.releaseForOrder(orderId, Instant.now());
            service.releaseForOrder(orderId, Instant.now());

            assertEquals(new BigDecimal("0.00"), wallet.getFrozenBalance());
            assertEquals(WalletReservationStatus.RELEASED, reservation.getStatus());
            verify(walletRepository).save(wallet);
        }
    }
}
