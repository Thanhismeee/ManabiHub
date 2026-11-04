package com.manabihub.finance.service.impl;

import com.manabihub.finance.dto.response.RevenueDashboardResponse;
import com.manabihub.finance.enums.RevenueGranularity;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class FinanceRevenueServicePostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static NamedParameterJdbcTemplate jdbcTemplate;
    private static JdbcTemplate database;

    @BeforeAll
    static void createRevenueSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        database = new JdbcTemplate(dataSource);
        database.execute("CREATE TABLE orders (id UUID PRIMARY KEY, order_type TEXT NOT NULL)");
        database.execute("""
                CREATE TABLE payment_transactions (
                    order_id UUID NOT NULL,
                    status TEXT NOT NULL,
                    succeeded_at TIMESTAMPTZ
                )
                """);
        database.execute("CREATE TABLE order_items (id UUID PRIMARY KEY, order_id UUID NOT NULL)");
        database.execute("CREATE TABLE order_item_snapshots (order_item_id UUID NOT NULL, gross_amount NUMERIC NOT NULL)");
        database.execute("""
                CREATE TABLE refund_requests (
                    id UUID PRIMARY KEY,
                    order_item_id UUID NOT NULL,
                    status TEXT NOT NULL,
                    settled_at TIMESTAMPTZ,
                    decided_at TIMESTAMPTZ,
                    updated_at TIMESTAMPTZ NOT NULL
                )
                """);
        database.execute("""
                CREATE TABLE platform_commission_ledgers (
                    order_item_id UUID NOT NULL,
                    event_type TEXT NOT NULL,
                    amount NUMERIC NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL
                )
                """);
        database.execute("""
                CREATE TABLE system_expenses (
                    id UUID PRIMARY KEY,
                    status TEXT NOT NULL,
                    incurred_at DATE NOT NULL
                )
                """);
        database.execute("""
                CREATE TABLE system_expense_lines (
                    expense_id UUID NOT NULL,
                    category_code TEXT NOT NULL,
                    amount_vnd NUMERIC NOT NULL
                )
                """);
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @BeforeEach
    void clearRevenueEvents() {
        database.update("DELETE FROM platform_commission_ledgers");
    }

    @Test
    void dashboardBindsInstantRangeAgainstPostgresWithoutTypeInferenceFailure() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        InternalAdminAccountRepository adminRepository = mock(InternalAdminAccountRepository.class);
        UUID adminId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_REVENUE_VIEW")).thenReturn(true);

        FinanceRevenueServiceImpl service = new FinanceRevenueServiceImpl(
                jdbcTemplate,
                currentUserService,
                adminRepository
        );

        RevenueDashboardResponse response = service.getDashboard(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                RevenueGranularity.DAY
        );

        assertEquals(2, response.points().size());
        assertEquals("0.00", response.summary().grossSales().toPlainString());
    }

    @Test
    void heldCommissionReversalDoesNotCreateNegativePlatformRevenue() {
        UUID orderItemId = UUID.randomUUID();
        database.update(
                """
                INSERT INTO platform_commission_ledgers (order_item_id, event_type, amount, created_at)
                VALUES (?, 'COMMISSION_HELD', 40000, TIMESTAMPTZ '2026-08-01 01:00:00Z'),
                       (?, 'COMMISSION_REVERSED', 40000, TIMESTAMPTZ '2026-08-01 02:00:00Z')
                """,
                orderItemId,
                orderItemId
        );

        RevenueDashboardResponse response = serviceWithFinancePermission().getDashboard(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                RevenueGranularity.DAY
        );

        assertEquals("0.00", response.summary().commissionRecognized().toPlainString());
        assertEquals("0.00", response.summary().commissionReversed().toPlainString());
        assertEquals("0.00", response.summary().platformRevenue().toPlainString());
    }

    @Test
    void reversalAfterRecognitionReducesPlatformRevenueInReversalPeriod() {
        UUID orderItemId = UUID.randomUUID();
        database.update(
                """
                INSERT INTO platform_commission_ledgers (order_item_id, event_type, amount, created_at)
                VALUES (?, 'COMMISSION_RECOGNIZED', 40000, TIMESTAMPTZ '2026-07-31 02:00:00Z'),
                       (?, 'COMMISSION_REVERSED', 40000, TIMESTAMPTZ '2026-08-01 02:00:00Z')
                """,
                orderItemId,
                orderItemId
        );

        RevenueDashboardResponse response = serviceWithFinancePermission().getDashboard(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                RevenueGranularity.DAY
        );

        assertEquals("0.00", response.summary().commissionRecognized().toPlainString());
        assertEquals("40000.00", response.summary().commissionReversed().toPlainString());
        assertEquals("-40000.00", response.summary().platformRevenue().toPlainString());
    }

    private FinanceRevenueServiceImpl serviceWithFinancePermission() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        InternalAdminAccountRepository adminRepository = mock(InternalAdminAccountRepository.class);
        UUID adminId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_REVENUE_VIEW")).thenReturn(true);
        return new FinanceRevenueServiceImpl(jdbcTemplate, currentUserService, adminRepository);
    }
}
