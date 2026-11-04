package com.manabihub.finance.repository;

import com.manabihub.finance.entity.SystemExpense;
import com.manabihub.finance.enums.ExpenseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SystemExpenseRepository
        extends JpaRepository<SystemExpense, UUID>, JpaSpecificationExecutor<SystemExpense> {

    @EntityGraph(attributePaths = "lines")
    @Query("select expense from SystemExpense expense where expense.id = :id")
    Optional<SystemExpense> findDetailById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select expense from SystemExpense expense where expense.id = :id")
    Optional<SystemExpense> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
            String providerCode,
            String invoiceNumber,
            LocalDate incurredAt,
            ExpenseStatus excludedStatus
    );

    boolean existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNotAndIdNot(
            String providerCode,
            String invoiceNumber,
            LocalDate incurredAt,
            ExpenseStatus excludedStatus,
            UUID id
    );

    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE status <> 'VOID') AS "totalDocuments",
                COALESCE(SUM(total_amount_vnd) FILTER (
                    WHERE status IN ('CONFIRMED', 'PAID')
                ), 0) AS "totalConfirmedVnd",
                COUNT(*) FILTER (WHERE status = 'DRAFT') AS "draftCount",
                COUNT(*) FILTER (WHERE status = 'CONFIRMED') AS "confirmedCount",
                COUNT(*) FILTER (WHERE status = 'PAID') AS "paidCount",
                COUNT(*) FILTER (
                    WHERE status = 'CONFIRMED' AND due_date < :today
                ) AS "overdueCount"
            FROM system_expenses
            WHERE incurred_at >= :fromDate AND incurred_at <= :toDate
            """, nativeQuery = true)
    ExpenseOverviewProjection summarize(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("today") LocalDate today
    );
}
