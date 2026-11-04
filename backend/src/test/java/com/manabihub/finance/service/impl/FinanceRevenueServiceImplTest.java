package com.manabihub.finance.service.impl;

import com.manabihub.finance.dto.response.RevenueDashboardResponse;
import com.manabihub.finance.enums.RevenueGranularity;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceRevenueServiceImplTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock private CurrentUserService currentUserService;
    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private ResultSet resultSet;

    private FinanceRevenueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FinanceRevenueServiceImpl(jdbcTemplate, currentUserService, adminRepository);
    }

    @Test
    void dashboard_UsesServerAggregatesAndReportsPercentageRate() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_REVENUE_VIEW")).thenReturn(true);
        when(resultSet.getObject("bucket_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 8, 1));
        when(resultSet.getBigDecimal("gross_sales")).thenReturn(new BigDecimal("1000"));
        when(resultSet.getLong("successful_orders")).thenReturn(2L);
        when(resultSet.getBigDecimal("refund_amount")).thenReturn(new BigDecimal("100"));
        when(resultSet.getLong("refund_count")).thenReturn(1L);
        when(resultSet.getBigDecimal("commission_recognized")).thenReturn(new BigDecimal("300"));
        when(resultSet.getBigDecimal("commission_reversed")).thenReturn(new BigDecimal("50"));
        when(resultSet.getBigDecimal("payment_fees")).thenReturn(new BigDecimal("10"));
        when(resultSet.getBigDecimal("operating_expenses")).thenReturn(new BigDecimal("20"));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(resultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        RevenueDashboardResponse response = service.getDashboard(
                Instant.parse("2026-07-31T17:00:00Z"),
                Instant.parse("2026-08-01T17:00:00Z"),
                RevenueGranularity.DAY
        );

        assertEquals(1, response.points().size());
        assertEquals(new BigDecimal("10.00"), response.summary().refundRate());
        assertEquals(new BigDecimal("900.00"), response.summary().netCollected());
        assertEquals(new BigDecimal("250.00"), response.summary().platformRevenue());
        assertEquals(new BigDecimal("30.00"), response.summary().totalActualExpenses());
        assertEquals(new BigDecimal("220.00"), response.summary().netOperatingResult());
        assertEquals("Asia/Ho_Chi_Minh", response.timezone());
        assertEquals(true, response.generatedAt() != null);

        ArgumentCaptor<SqlParameterSource> parametersCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), parametersCaptor.capture(), any(RowCallbackHandler.class));
        SqlParameterSource parameters = parametersCaptor.getValue();
        assertInstanceOf(OffsetDateTime.class, parameters.getValue("from"));
        assertInstanceOf(OffsetDateTime.class, parameters.getValue("to"));
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, parameters.getSqlType("from"));
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, parameters.getSqlType("to"));
    }
}
