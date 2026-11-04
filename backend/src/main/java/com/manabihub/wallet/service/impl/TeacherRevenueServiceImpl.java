package com.manabihub.wallet.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.wallet.dto.response.TeacherCourseRevenueResponse;
import com.manabihub.wallet.dto.response.TeacherRevenueSummaryResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.enums.WalletDirection;
import com.manabihub.wallet.enums.WalletOwnerType;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.wallet.repository.WalletTransactionRepository;
import com.manabihub.wallet.service.TeacherRevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherRevenueServiceImpl implements TeacherRevenueService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;

    @Override
    @Transactional(readOnly = true)
    public TeacherRevenueSummaryResponse getRevenueSummary(UUID userId) {
        TeacherProfile teacher = teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.KYC_TEACHER_NOT_FOUND,
                        "Teacher profile not found",
                        HttpStatus.NOT_FOUND));

        Wallet wallet = walletRepository.findByOwnerTypeAndTeacher_Id(
                        WalletOwnerType.TEACHER, teacher.getId())
                .orElse(null);

        List<TeacherCourseRevenueResponse> courses = escrowLedgerRepository
                .summarizeTeacherRevenueByCourse(teacher.getId())
                .stream()
                .map(row -> TeacherCourseRevenueResponse.builder()
                        .courseId(row.getCourseId())
                        .courseTitle(row.getCourseTitle())
                        .purchaseCount(value(row.getPurchaseCount()))
                        .refundedCount(value(row.getRefundedCount()))
                        .grossRevenue(amount(row.getGrossRevenue()))
                        .teacherNetRevenue(amount(row.getTeacherNetRevenue()))
                        .heldAmount(amount(row.getHeldAmount()))
                        .releasedAmount(amount(row.getReleasedAmount()))
                        .refundedAmount(amount(row.getRefundedAmount()))
                        .build())
                .toList();

        BigDecimal gross = courses.stream()
                .map(TeacherCourseRevenueResponse::getGrossRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = courses.stream()
                .map(TeacherCourseRevenueResponse::getTeacherNetRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal held = courses.stream()
                .map(TeacherCourseRevenueResponse::getHeldAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settled = courses.stream()
                .map(TeacherCourseRevenueResponse::getReleasedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long sales = courses.stream()
                .mapToLong(TeacherCourseRevenueResponse::getPurchaseCount)
                .sum();
        long refunds = courses.stream()
                .mapToLong(TeacherCourseRevenueResponse::getRefundedCount)
                .sum();

        BigDecimal available = wallet == null ? BigDecimal.ZERO : amount(wallet.getAvailableBalance());
        BigDecimal frozen = wallet == null ? BigDecimal.ZERO : amount(wallet.getFrozenBalance());
        BigDecimal reserved = frozen.subtract(held).max(BigDecimal.ZERO);
        BigDecimal withdrawn = wallet == null ? BigDecimal.ZERO : amount(
                walletTransactionRepository.sumAmountByWalletIdAndTypeAndDirection(
                        wallet.getId(), WalletTransactionType.WITHDRAWAL_COMPLETED, WalletDirection.OUT));

        return TeacherRevenueSummaryResponse.builder()
                .totalGrossRevenue(gross)
                .totalTeacherNetRevenue(net)
                .settledRevenue(settled)
                .heldInEscrow(held)
                .availableInWallet(available)
                .reservedForWithdrawal(reserved)
                .totalWithdrawn(withdrawn)
                .totalSales(sales)
                .totalRefundedSales(refunds)
                .courseRevenue(courses)
                .build();
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long value(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
