package com.manabihub.finance.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.finance.dto.response.RevenueDashboardResponse;
import com.manabihub.finance.dto.response.RevenueSummaryResponse;
import com.manabihub.finance.dto.response.RevenueTimePointResponse;
import com.manabihub.finance.enums.RevenueGranularity;
import com.manabihub.finance.service.FinanceRevenueService;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceRevenueServiceImpl implements FinanceRevenueService {

    private static final String VIEW_PERMISSION = "FINANCE_REVENUE_VIEW";
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final InternalAdminAccountRepository adminRepository;

    @Override
    @Transactional(readOnly = true)
    public RevenueDashboardResponse getDashboard(
            Instant requestedFrom,
            Instant requestedTo,
            RevenueGranularity requestedGranularity
    ) {
        requireFinanceAccess();
        Instant now = Instant.now();
        Instant to = requestedTo == null ? now : requestedTo;
        Instant from = requestedFrom == null
                ? YearMonth.from(to.atZone(REPORTING_ZONE)).atDay(1)
                        .atStartOfDay(REPORTING_ZONE).toInstant()
                : requestedFrom;
        RevenueGranularity granularity = requestedGranularity == null
                ? RevenueGranularity.DAY
                : requestedGranularity;
        validateRange(from, to);

        ZonedDateTime expenseToInReportingZone = to.atZone(REPORTING_ZONE);
        LocalDate expenseToExclusive = expenseToInReportingZone.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? expenseToInReportingZone.toLocalDate()
                : expenseToInReportingZone.toLocalDate().plusDays(1);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", from.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("expenseFrom", from.atZone(REPORTING_ZONE).toLocalDate())
                .addValue("expenseTo", expenseToExclusive);
        String sql = revenueSql(granularity);
        Map<LocalDate, RevenueTimePointResponse> actualPoints = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, resultSet -> {
            LocalDate bucket = resultSet.getObject("bucket_date", LocalDate.class);
            BigDecimal recognized = money(resultSet.getBigDecimal("commission_recognized"));
            BigDecimal reversed = money(resultSet.getBigDecimal("commission_reversed"));
            actualPoints.put(bucket, new RevenueTimePointResponse(
                    bucket,
                    money(resultSet.getBigDecimal("gross_sales")),
                    resultSet.getLong("successful_orders"),
                    money(resultSet.getBigDecimal("refund_amount")),
                    resultSet.getLong("refund_count"),
                    recognized,
                    reversed,
                    recognized.subtract(reversed).setScale(2, RoundingMode.HALF_UP),
                    money(resultSet.getBigDecimal("payment_fees")),
                    money(resultSet.getBigDecimal("operating_expenses"))
            ));
        });

        List<RevenueTimePointResponse> points = fillMissingBuckets(
                actualPoints,
                from.atZone(REPORTING_ZONE),
                to.atZone(REPORTING_ZONE),
                granularity
        );
        return new RevenueDashboardResponse(
                from,
                to,
                REPORTING_ZONE.getId(),
                granularity,
                now,
                summarize(points),
                points
        );
    }

    private String revenueSql(RevenueGranularity granularity) {
        String unit = switch (granularity) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
        return """
                WITH paid_orders AS (
                    SELECT payment.order_id, MIN(payment.succeeded_at) AS occurred_at
                    FROM payment_transactions payment
                    JOIN orders purchase_order ON purchase_order.id = payment.order_id
                    WHERE purchase_order.order_type = 'COURSE'
                      AND payment.status IN ('SUCCESS', 'REFUNDED')
                      AND payment.succeeded_at >= :from
                      AND payment.succeeded_at < :to
                    GROUP BY payment.order_id
                ), events AS (
                    SELECT
                        date_trunc('%s', paid.occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS bucket_date,
                        SUM(snapshot.gross_amount)::numeric AS gross_sales,
                        COUNT(DISTINCT paid.order_id)::bigint AS successful_orders,
                        0::numeric AS refund_amount,
                        0::bigint AS refund_count,
                        0::numeric AS commission_recognized,
                        0::numeric AS commission_reversed,
                        0::numeric AS payment_fees,
                        0::numeric AS operating_expenses
                    FROM paid_orders paid
                    JOIN order_items item ON item.order_id = paid.order_id
                    JOIN order_item_snapshots snapshot ON snapshot.order_item_id = item.id
                    GROUP BY bucket_date

                    UNION ALL

                    SELECT
                        date_trunc('%s', COALESCE(refund.settled_at, refund.decided_at, refund.updated_at)
                            AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS bucket_date,
                        0::numeric, 0::bigint,
                        SUM(snapshot.gross_amount)::numeric,
                        COUNT(DISTINCT refund.id)::bigint,
                        0::numeric, 0::numeric, 0::numeric, 0::numeric
                    FROM refund_requests refund
                    JOIN order_item_snapshots snapshot ON snapshot.order_item_id = refund.order_item_id
                    WHERE refund.status = 'APPROVED'
                      AND COALESCE(refund.settled_at, refund.decided_at, refund.updated_at) >= :from
                      AND COALESCE(refund.settled_at, refund.decided_at, refund.updated_at) < :to
                    GROUP BY bucket_date

                    UNION ALL

                    SELECT
                        date_trunc('%s', ledger.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS bucket_date,
                        0::numeric, 0::bigint, 0::numeric, 0::bigint,
                        SUM(CASE WHEN ledger.event_type = 'COMMISSION_RECOGNIZED' THEN ledger.amount ELSE 0 END)::numeric,
                        SUM(CASE
                            WHEN ledger.event_type = 'COMMISSION_REVERSED'
                             AND EXISTS (
                                SELECT 1
                                FROM platform_commission_ledgers recognized
                                WHERE recognized.order_item_id = ledger.order_item_id
                                  AND recognized.event_type = 'COMMISSION_RECOGNIZED'
                                  AND recognized.created_at <= ledger.created_at
                             )
                            THEN ledger.amount
                            ELSE 0
                        END)::numeric,
                        0::numeric, 0::numeric
                    FROM platform_commission_ledgers ledger
                    WHERE ledger.event_type IN ('COMMISSION_RECOGNIZED', 'COMMISSION_REVERSED')
                      AND ledger.created_at >= :from
                      AND ledger.created_at < :to
                    GROUP BY bucket_date

                    UNION ALL

                    SELECT
                        date_trunc('%s', expense.incurred_at::timestamp)::date AS bucket_date,
                        0::numeric, 0::bigint, 0::numeric, 0::bigint, 0::numeric, 0::numeric,
                        SUM(CASE WHEN line.category_code IN (
                            'PAYMENT_GATEWAY_FEE', 'PAYMENT_REFUND_FEE', 'PAYMENT_CHARGEBACK_FEE'
                        ) THEN line.amount_vnd ELSE 0 END)::numeric,
                        SUM(CASE WHEN line.category_code NOT IN (
                            'PAYMENT_GATEWAY_FEE', 'PAYMENT_REFUND_FEE', 'PAYMENT_CHARGEBACK_FEE'
                        ) THEN line.amount_vnd ELSE 0 END)::numeric
                    FROM system_expenses expense
                    JOIN system_expense_lines line ON line.expense_id = expense.id
                    WHERE expense.status IN ('CONFIRMED', 'PAID')
                      AND expense.incurred_at >= :expenseFrom
                      AND expense.incurred_at < :expenseTo
                    GROUP BY bucket_date
                )
                SELECT bucket_date,
                       COALESCE(SUM(gross_sales), 0) AS gross_sales,
                       COALESCE(SUM(successful_orders), 0) AS successful_orders,
                       COALESCE(SUM(refund_amount), 0) AS refund_amount,
                       COALESCE(SUM(refund_count), 0) AS refund_count,
                       COALESCE(SUM(commission_recognized), 0) AS commission_recognized,
                       COALESCE(SUM(commission_reversed), 0) AS commission_reversed,
                       COALESCE(SUM(payment_fees), 0) AS payment_fees,
                       COALESCE(SUM(operating_expenses), 0) AS operating_expenses
                FROM events
                GROUP BY bucket_date
                ORDER BY bucket_date
                """.formatted(unit, unit, unit, unit);
    }

    private List<RevenueTimePointResponse> fillMissingBuckets(
            Map<LocalDate, RevenueTimePointResponse> actual,
            ZonedDateTime from,
            ZonedDateTime to,
            RevenueGranularity granularity
    ) {
        LocalDate cursor = bucketStart(from.toLocalDate(), granularity);
        LocalDate finalBucket = bucketStart(to.minusNanos(1).toLocalDate(), granularity);
        List<RevenueTimePointResponse> result = new ArrayList<>();
        while (!cursor.isAfter(finalBucket)) {
            result.add(actual.getOrDefault(cursor, emptyPoint(cursor)));
            cursor = nextBucket(cursor, granularity);
        }
        return List.copyOf(result);
    }

    private RevenueSummaryResponse summarize(List<RevenueTimePointResponse> points) {
        BigDecimal gross = sum(points, RevenueTimePointResponse::grossSales);
        long orders = points.stream().mapToLong(RevenueTimePointResponse::successfulOrders).sum();
        BigDecimal refunds = sum(points, RevenueTimePointResponse::refundAmount);
        BigDecimal netCollected = gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
        long refundCount = points.stream().mapToLong(RevenueTimePointResponse::refundCount).sum();
        BigDecimal recognized = sum(points, RevenueTimePointResponse::commissionRecognized);
        BigDecimal reversed = sum(points, RevenueTimePointResponse::commissionReversed);
        BigDecimal platformRevenue = recognized.subtract(reversed).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paymentFees = sum(points, RevenueTimePointResponse::paymentFees);
        BigDecimal operatingExpenses = sum(points, RevenueTimePointResponse::operatingExpenses);
        BigDecimal totalExpenses = paymentFees.add(operatingExpenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = platformRevenue.subtract(totalExpenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundRate = gross.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : refunds.multiply(BigDecimal.valueOf(100))
                        .divide(gross, 2, RoundingMode.HALF_UP);
        return new RevenueSummaryResponse(
                gross, orders, refunds, refundCount, refundRate, netCollected, recognized, reversed,
                platformRevenue, paymentFees, operatingExpenses, totalExpenses, net
        );
    }

    private BigDecimal sum(
            List<RevenueTimePointResponse> points,
            java.util.function.Function<RevenueTimePointResponse, BigDecimal> value
    ) {
        return points.stream().map(value).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private RevenueTimePointResponse emptyPoint(LocalDate bucket) {
        return new RevenueTimePointResponse(bucket, ZERO, 0, ZERO, 0, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private LocalDate bucketStart(LocalDate date, RevenueGranularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private LocalDate nextBucket(LocalDate current, RevenueGranularity granularity) {
        return switch (granularity) {
            case DAY -> current.plusDays(1);
            case WEEK -> current.plusWeeks(1);
            case MONTH -> current.plusMonths(1);
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Revenue range must satisfy from < to");
        }
        if (from.isBefore(to.minusSeconds(366L * 5L * 24L * 60L * 60L))) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Revenue range cannot exceed five years");
        }
    }

    private void requireFinanceAccess() {
        if (!adminRepository.hasPermission(currentUserService.getCurrentUserId(), VIEW_PERMISSION)) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_008,
                    "Finance revenue permission is required",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
