package com.manabihub.finance.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.finance.dto.request.ExpenseLineRequest;
import com.manabihub.finance.dto.request.UpsertExpenseRequest;
import com.manabihub.finance.dto.response.ExpenseDetailResponse;
import com.manabihub.finance.entity.SystemExpense;
import com.manabihub.finance.entity.SystemExpenseLine;
import com.manabihub.finance.enums.ExpenseCategory;
import com.manabihub.finance.enums.ExpenseSourceType;
import com.manabihub.finance.enums.ExpenseStatus;
import com.manabihub.finance.repository.SystemExpenseRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemExpenseServiceImplTest {

    @Mock private SystemExpenseRepository expenseRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private AuditLogService auditLogService;

    private final UUID adminId = UUID.randomUUID();
    private SystemExpenseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemExpenseServiceImpl(
                expenseRepository,
                currentUserService,
                adminRepository,
                auditLogService
        );
    }

    @Test
    void create_DerivesDocumentTotalsFromComponentLines() {
        allowFinanceManager();
        when(expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
                "AWS", "INV-001", LocalDate.now(), ExpenseStatus.VOID))
                .thenReturn(false);
        when(expenseRepository.saveAndFlush(any(SystemExpense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseDetailResponse result = service.create(request());

        assertEquals(ExpenseStatus.DRAFT, result.status());
        assertEquals(new BigDecimal("15.00"), result.originalTotal());
        assertEquals(new BigDecimal("375000.00"), result.totalAmountVnd());
        assertEquals(2, result.lines().size());
        assertEquals(new BigDecimal("250000.00"), result.lines().getFirst().amountVnd());
        assertNotNull(result.expenseCode());
        verify(auditLogService).logAdminAction(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void create_RejectsDuplicateProviderInvoice() {
        allowFinanceManager();
        when(expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
                "AWS", "INV-001", LocalDate.now(), ExpenseStatus.VOID))
                .thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request()));

        assertEquals(MessageCodes.COMMON_CONFLICT, error.getMessageCode());
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmThenPaid_UsesControlledStateTransitions() {
        allowFinanceManager();
        SystemExpense expense = existingDraft();
        when(expenseRepository.findByIdForUpdate(expense.getId())).thenReturn(Optional.of(expense));
        when(expenseRepository.save(expense)).thenReturn(expense);

        ExpenseDetailResponse confirmed = service.confirm(expense.getId());
        ExpenseDetailResponse paid = service.markPaid(expense.getId());

        assertEquals(ExpenseStatus.CONFIRMED, confirmed.status());
        assertNotNull(confirmed.confirmedAt());
        assertEquals(ExpenseStatus.PAID, paid.status());
        assertNotNull(paid.paidAt());
        assertEquals(adminId, paid.paidBy());
    }

    @Test
    void create_RequiresLiveFinancePermission() {
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_EXPENSE_MANAGE")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request()));

        assertEquals(MessageCodes.MSG_ADM_008, error.getMessageCode());
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void get_UsesViewPermissionInsteadOfManagePermission() {
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_EXPENSE_VIEW")).thenReturn(true);
        SystemExpense expense = existingDraft();
        when(expenseRepository.findDetailById(expense.getId())).thenReturn(Optional.of(expense));

        ExpenseDetailResponse result = service.get(expense.getId());

        assertEquals(expense.getId(), result.id());
        verify(adminRepository).hasPermission(adminId, "FINANCE_EXPENSE_VIEW");
        verify(adminRepository, never()).hasPermission(adminId, "FINANCE_EXPENSE_MANAGE");
    }

    @Test
    void create_MapsConcurrentDuplicateConstraintToConflict() {
        allowFinanceManager();
        when(expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
                "AWS", "INV-001", LocalDate.now(), ExpenseStatus.VOID))
                .thenReturn(false);
        when(expenseRepository.saveAndFlush(any(SystemExpense.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates uq_system_expenses_active_provider_invoice_date"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request()));

        assertEquals(MessageCodes.COMMON_CONFLICT, error.getMessageCode());
    }

    @Test
    void create_DoesNotMisreportUnrelatedDatabaseFailuresAsDuplicates() {
        allowFinanceManager();
        when(expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
                "AWS", "INV-001", LocalDate.now(), ExpenseStatus.VOID))
                .thenReturn(false);
        DataIntegrityViolationException databaseError = new DataIntegrityViolationException("connection lost");
        when(expenseRepository.saveAndFlush(any(SystemExpense.class))).thenThrow(databaseError);

        DataIntegrityViolationException result = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.create(request())
        );

        assertEquals(databaseError, result);
    }

    @Test
    void create_RejectsFutureInvoiceDateInVietnamTimezone() {
        allowFinanceManager();
        UpsertExpenseRequest request = request();
        request.setIncurredAt(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(MessageCodes.VALIDATION_FAILED, error.getMessageCode());
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_RequiresForeignExchangeRateEvidence() {
        allowFinanceManager();
        UpsertExpenseRequest request = request();
        request.setExchangeRateDate(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(MessageCodes.VALIDATION_FAILED, error.getMessageCode());
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    private void allowFinanceManager() {
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(adminRepository.hasPermission(adminId, "FINANCE_EXPENSE_MANAGE")).thenReturn(true);
    }

    private UpsertExpenseRequest request() {
        UpsertExpenseRequest request = new UpsertExpenseRequest();
        request.setVendorName("Amazon Web Services");
        request.setProviderCode("AWS");
        request.setInvoiceNumber("INV-001");
        request.setCurrency("USD");
        request.setExchangeRate(new BigDecimal("25000"));
        request.setExchangeRateDate(LocalDate.now());
        request.setExchangeRateSource("Vietcombank");
        request.setIncurredAt(LocalDate.now());
        request.setSourceType(ExpenseSourceType.MANUAL_INVOICE);
        request.setLines(List.of(
                new ExpenseLineRequest(ExpenseCategory.INFRA_APP_COMPUTE, "Compute", new BigDecimal("10")),
                new ExpenseLineRequest(ExpenseCategory.INFRA_OBJECT_STORAGE, "Storage", new BigDecimal("5"))
        ));
        return request;
    }

    private SystemExpense existingDraft() {
        SystemExpense expense = SystemExpense.builder()
                .id(UUID.randomUUID())
                .expenseCode("EXP-TEST")
                .vendorName("AWS")
                .currency("VND")
                .exchangeRate(BigDecimal.ONE.setScale(6))
                .originalTotal(new BigDecimal("100000.00"))
                .totalAmountVnd(new BigDecimal("100000.00"))
                .incurredAt(LocalDate.now())
                .status(ExpenseStatus.DRAFT)
                .sourceType(ExpenseSourceType.MANUAL_INVOICE)
                .createdBy(adminId)
                .build();
        expense.replaceLines(List.of(SystemExpenseLine.builder()
                .id(UUID.randomUUID())
                .categoryCode(ExpenseCategory.SMS_OTP)
                .description("SMS")
                .originalAmount(new BigDecimal("100000.00"))
                .amountVnd(new BigDecimal("100000.00"))
                .lineOrder(0)
                .build()));
        return expense;
    }
}
