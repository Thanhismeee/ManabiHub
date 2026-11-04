package com.manabihub.wallet.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.order.entity.Order;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import com.manabihub.wallet.dto.request.WalletTransactionFilterRequest;
import com.manabihub.wallet.dto.response.WalletTransactionDetailResponse;
import com.manabihub.wallet.dto.response.WalletTransactionResponse;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.repository.WalletTransactionSpecifications;
import com.manabihub.wallet.service.WalletTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model for "Manage My Wallet" (UC-17): transaction history and transaction detail.
 * <p>
 * The wallet is always resolved from the authenticated user id, so a Student can only ever
 * read their own money wallet and a Teacher only their own revenue wallet — a Student
 * cannot reach teacher revenue lines and vice versa (UC-17 exceptions 4b / 5b).
 */
@Service
@RequiredArgsConstructor
public class WalletTransactionServiceImpl implements WalletTransactionService {

    /** Reference types written into {@code wallet_transactions.reference_type}. */
    private static final String REF_ORDER = "ORDER";
    private static final String REF_WALLET_TOPUP = "WALLET_TOPUP";
    private static final String REF_ESCROW = "ESCROW";
    private static final String REF_WITHDRAWAL = "WITHDRAWAL_REQUEST";

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final OrderRepository orderRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    // ──────────────────────────────────────────────
    // History
    // ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getStudentTransactions(
            UUID userId, WalletTransactionFilterRequest filter, Pageable pageable) {

        StudentProfile student = requireStudent(userId);
        Optional<Wallet> wallet = walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, student.getId());
        if (wallet.isEmpty()) {
            // No wallet yet simply means no activity — an empty history, not an error.
            return PageResponse.from(Page.<WalletTransactionResponse>empty(sorted(pageable)));
        }

        Collection<UUID> referenceMatches =
                resolveStudentReferenceMatches(student.getId(), filter);

        return query(wallet.get().getId(), wallet.get().getCurrency(),
                filter, referenceMatches, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getTeacherTransactions(
            UUID userId, WalletTransactionFilterRequest filter, Pageable pageable) {

        TeacherProfile teacher = requireTeacher(userId);
        Optional<Wallet> wallet = walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId());
        if (wallet.isEmpty()) {
            return PageResponse.from(Page.<WalletTransactionResponse>empty(sorted(pageable)));
        }

        Collection<UUID> referenceMatches =
                resolveTeacherReferenceMatches(teacher.getId(), filter);

        return query(wallet.get().getId(), wallet.get().getCurrency(),
                filter, referenceMatches, pageable);
    }

    private PageResponse<WalletTransactionResponse> query(UUID walletId,
                                                          String currency,
                                                          WalletTransactionFilterRequest filter,
                                                          Collection<UUID> referenceMatches,
                                                          Pageable pageable) {
        Page<WalletTransaction> page = walletTransactionRepository.findAll(
                WalletTransactionSpecifications.forWallet(walletId, filter, referenceMatches),
                sorted(pageable));

        return PageResponse.from(page.map(tx -> toResponse(tx, currency)));
    }

    // ──────────────────────────────────────────────
    // Detail (UC-17 alternative flow 6a)
    // ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public WalletTransactionDetailResponse getStudentTransactionDetail(UUID userId, UUID transactionId) {
        StudentProfile student = requireStudent(userId);
        Wallet wallet = walletRepository.findByOwnerTypeAndStudent_Id(WalletOwnerType.STUDENT, student.getId())
                .orElseThrow(() -> walletNotFound("Student wallet was not found"));

        WalletTransaction transaction = requireOwnedTransaction(transactionId, wallet.getId());
        return toDetail(transaction, wallet.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public WalletTransactionDetailResponse getTeacherTransactionDetail(UUID userId, UUID transactionId) {
        TeacherProfile teacher = requireTeacher(userId);
        Wallet wallet = walletRepository.findByOwnerTypeAndTeacher_Id(WalletOwnerType.TEACHER, teacher.getId())
                .orElseThrow(() -> walletNotFound("Teacher wallet was not found"));

        WalletTransaction transaction = requireOwnedTransaction(transactionId, wallet.getId());
        return toDetail(transaction, wallet.getCurrency());
    }

    /**
     * Loads a transaction only if it belongs to {@code walletId}. A foreign or unknown id is
     * reported the same way (404) so the endpoint cannot be used to probe other users' ledgers.
     */
    private WalletTransaction requireOwnedTransaction(UUID transactionId, UUID walletId) {
        return walletTransactionRepository.findByIdAndWalletId(transactionId, walletId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WALLET_TRANSACTION_NOT_FOUND,
                        "Giao dịch không tồn tại hoặc không thuộc về ví của bạn",
                        HttpStatus.NOT_FOUND));
    }

    // ──────────────────────────────────────────────
    // Reference-code search
    // ──────────────────────────────────────────────

    /**
     * Translates a free-text reference code into the set of {@code reference_id}s it can match
     * for this student. Returns {@code null} when no search was requested.
     */
    private Collection<UUID> resolveStudentReferenceMatches(UUID studentId,
                                                            WalletTransactionFilterRequest filter) {
        String code = filter == null ? null : filter.normalizedReferenceCode();
        if (code == null) {
            return null;
        }
        // Student ledger lines reference orders (course purchase and wallet top-up).
        List<UUID> matches = new ArrayList<>(
                orderRepository.findIdsByStudentIdAndOrderCodeLike(studentId, code));
        parseUuid(code).ifPresent(matches::add);
        return matches;
    }

    /**
     * Teacher ledger lines reference escrow entries and withdrawal requests, so the search
     * resolves the code against the teacher's own escrow orders and withdrawal ids.
     */
    private Collection<UUID> resolveTeacherReferenceMatches(UUID teacherId,
                                                            WalletTransactionFilterRequest filter) {
        String code = filter == null ? null : filter.normalizedReferenceCode();
        if (code == null) {
            return null;
        }
        List<UUID> matches = new ArrayList<>(
                escrowLedgerRepository.findIdsByTeacherIdAndOrderCodeLike(teacherId, code));
        parseUuid(code).ifPresent(matches::add);
        return matches;
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    // ──────────────────────────────────────────────
    // Mapping
    // ──────────────────────────────────────────────

    private WalletTransactionResponse toResponse(WalletTransaction tx, String currency) {
        return new WalletTransactionResponse(
                tx.getId(),
                tx.getTransactionType(),
                tx.getDirection(),
                tx.getAmount(),
                currency,
                tx.getReferenceType(),
                tx.getReferenceId(),
                resolveReferenceCode(tx),
                tx.getNote(),
                tx.getCreatedAt());
    }

    private WalletTransactionDetailResponse toDetail(WalletTransaction tx, String currency) {
        return new WalletTransactionDetailResponse(
                tx.getId(),
                tx.getTransactionType(),
                tx.getDirection(),
                tx.getAmount(),
                currency,
                tx.getReferenceType(),
                tx.getReferenceId(),
                resolveReferenceCode(tx),
                tx.getNote(),
                tx.getCreatedAt(),
                resolveRelatedRecord(tx).orElse(null));
    }

    /** Short human-readable code shown in the history table. */
    private String resolveReferenceCode(WalletTransaction tx) {
        if (tx.getReferenceType() == null || tx.getReferenceId() == null) {
            return null;
        }
        return switch (tx.getReferenceType()) {
            case REF_ORDER, REF_WALLET_TOPUP -> orderRepository.findById(tx.getReferenceId())
                    .map(Order::getOrderCode)
                    .orElse(null);
            case REF_ESCROW -> escrowLedgerRepository.findById(tx.getReferenceId())
                    .map(escrow -> escrow.getOrder().getOrderCode())
                    .orElse(null);
            case REF_WITHDRAWAL -> shortId(tx.getReferenceId());
            default -> null;
        };
    }

    /** Full related record for the detail dialog; empty when the reference cannot be resolved. */
    private Optional<WalletTransactionDetailResponse.RelatedRecord> resolveRelatedRecord(WalletTransaction tx) {
        if (tx.getReferenceType() == null || tx.getReferenceId() == null) {
            return Optional.empty();
        }
        return switch (tx.getReferenceType()) {
            case REF_ORDER, REF_WALLET_TOPUP -> orderRepository.findById(tx.getReferenceId())
                    .map(order -> new WalletTransactionDetailResponse.RelatedRecord(
                            tx.getReferenceType(),
                            order.getId(),
                            order.getOrderCode(),
                            order.getStatus().name(),
                            null,
                            order.getTotalAmount(),
                            toLocalDateTime(order.getCreatedAt())));

            case REF_ESCROW -> escrowLedgerRepository.findById(tx.getReferenceId())
                    .map(this::toEscrowRecord);

            case REF_WITHDRAWAL -> withdrawalRequestRepository.findById(tx.getReferenceId())
                    .map(this::toWithdrawalRecord);

            default -> Optional.<WalletTransactionDetailResponse.RelatedRecord>empty();
        };
    }

    private WalletTransactionDetailResponse.RelatedRecord toEscrowRecord(EscrowLedger escrow) {
        return new WalletTransactionDetailResponse.RelatedRecord(
                REF_ESCROW,
                escrow.getId(),
                escrow.getOrder().getOrderCode(),
                escrow.getStatus().name(),
                escrow.getCourse() == null ? null : escrow.getCourse().getTitle(),
                escrow.getAmount(),
                toLocalDateTime(escrow.getCreatedAt()));
    }

    private WalletTransactionDetailResponse.RelatedRecord toWithdrawalRecord(WithdrawalRequest withdrawal) {
        return new WalletTransactionDetailResponse.RelatedRecord(
                REF_WITHDRAWAL,
                withdrawal.getId(),
                shortId(withdrawal.getId()),
                withdrawal.getStatus().name(),
                null,
                withdrawal.getRequestedAmount(),
                withdrawal.getRequestedAt());
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /** History is always newest-first unless the caller asked for an explicit sort. */
    private Pageable sorted(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, NEWEST_FIRST);
        }
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    private StudentProfile requireStudent(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND,
                        "Student profile was not found",
                        HttpStatus.NOT_FOUND));
    }

    private TeacherProfile requireTeacher(UUID userId) {
        return teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.KYC_TEACHER_NOT_FOUND,
                        "Teacher profile was not found",
                        HttpStatus.NOT_FOUND));
    }

    private BusinessException walletNotFound(String message) {
        return new BusinessException(MessageCodes.WALLET_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private String shortId(UUID id) {
        return id == null ? null : id.toString().substring(0, 8).toUpperCase();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
