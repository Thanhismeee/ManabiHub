package com.manabihub.finance.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.finance.dto.request.ExpenseFilterRequest;
import com.manabihub.finance.dto.request.ExpenseLineRequest;
import com.manabihub.finance.dto.request.UpsertExpenseRequest;
import com.manabihub.finance.dto.response.ExpenseDetailResponse;
import com.manabihub.finance.dto.response.ExpenseLineResponse;
import com.manabihub.finance.dto.response.ExpenseOverviewResponse;
import com.manabihub.finance.dto.response.ExpenseSummaryResponse;
import com.manabihub.finance.entity.SystemExpense;
import com.manabihub.finance.entity.SystemExpenseLine;
import com.manabihub.finance.enums.ExpenseStatus;
import com.manabihub.finance.repository.SystemExpenseRepository;
import com.manabihub.finance.repository.ExpenseOverviewProjection;
import com.manabihub.finance.repository.SystemExpenseSpecifications;
import com.manabihub.finance.service.SystemExpenseService;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemExpenseServiceImpl implements SystemExpenseService {

    private static final String VIEW_PERMISSION = "FINANCE_EXPENSE_VIEW";
    private static final String MANAGE_PERMISSION = "FINANCE_EXPENSE_MANAGE";
    private static final String DUPLICATE_INVOICE_CONSTRAINT =
            "uq_system_expenses_active_provider_invoice_date";
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "incurredAt", "createdAt", "updatedAt", "totalAmountVnd", "expenseCode", "vendorName"
    );

    private final SystemExpenseRepository expenseRepository;
    private final CurrentUserService currentUserService;
    private final InternalAdminAccountRepository adminRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseSummaryResponse> search(ExpenseFilterRequest filter, Pageable pageable) {
        requirePermission(VIEW_PERMISSION);
        validateFilter(filter);
        Page<SystemExpense> page = expenseRepository.findAll(
                SystemExpenseSpecifications.from(filter),
                safePageable(pageable)
        );
        return PageResponse.from(page.map(this::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseDetailResponse get(UUID id) {
        requirePermission(VIEW_PERMISSION);
        return toDetail(requireDetail(id));
    }

    private void validateFilter(ExpenseFilterRequest filter) {
        if (filter == null) return;
        if ((filter.getMinAmountVnd() != null && filter.getMinAmountVnd().signum() < 0)
                || (filter.getMaxAmountVnd() != null && filter.getMaxAmountVnd().signum() < 0)) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Expense amount filters cannot be negative");
        }
        if (filter.getMinAmountVnd() != null && filter.getMaxAmountVnd() != null
                && filter.getMinAmountVnd().compareTo(filter.getMaxAmountVnd()) > 0) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Minimum expense amount cannot exceed maximum expense amount"
            );
        }
        if (filter.getIncurredFrom() != null && filter.getIncurredTo() != null
                && filter.getIncurredFrom().isAfter(filter.getIncurredTo())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Expense date range is invalid");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseOverviewResponse overview(LocalDate requestedFrom, LocalDate requestedTo) {
        requirePermission(VIEW_PERMISSION);
        LocalDate today = LocalDate.now(REPORTING_ZONE);
        LocalDate from = requestedFrom == null ? YearMonth.from(today).atDay(1) : requestedFrom;
        LocalDate to = requestedTo == null ? today : requestedTo;
        if (from.isAfter(to)) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Expense range must satisfy from <= to");
        }
        if (from.isBefore(to.minusYears(5))) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Expense range cannot exceed five years");
        }
        ExpenseOverviewProjection summary = expenseRepository.summarize(from, to, today);
        return new ExpenseOverviewResponse(
                from,
                to,
                longValue(summary == null ? null : summary.getTotalDocuments()),
                moneyValue(summary == null ? null : summary.getTotalConfirmedVnd()),
                longValue(summary == null ? null : summary.getDraftCount()),
                longValue(summary == null ? null : summary.getConfirmedCount()),
                longValue(summary == null ? null : summary.getPaidCount()),
                longValue(summary == null ? null : summary.getOverdueCount()),
                Instant.now()
        );
    }

    @Override
    @Transactional
    public ExpenseDetailResponse create(UpsertExpenseRequest request) {
        UUID adminId = requirePermission(MANAGE_PERMISSION);
        validateRequest(request, null);
        SystemExpense expense = SystemExpense.builder()
                .expenseCode(generateCode())
                .status(ExpenseStatus.DRAFT)
                .createdBy(adminId)
                .build();
        applyDraft(expense, request);
        SystemExpense saved = saveDraft(expense);
        audit(saved, adminId, "EXPENSE_DRAFT_CREATED", null, ExpenseStatus.DRAFT, Map.of());
        return toDetail(saved);
    }

    @Override
    @Transactional
    public ExpenseDetailResponse update(UUID id, UpsertExpenseRequest request) {
        UUID adminId = requirePermission(MANAGE_PERMISSION);
        SystemExpense expense = requireForUpdate(id);
        requireStatus(expense, ExpenseStatus.DRAFT, "Only draft expenses can be edited");
        if (request.getVersion() == null || request.getVersion() != expense.getVersion()) {
            throw conflict("Expense was changed by another session; reload before editing");
        }
        validateRequest(request, id);
        BigDecimal previousAmountVnd = expense.getTotalAmountVnd();
        String previousInvoiceNumber = expense.getInvoiceNumber();
        applyDraft(expense, request);
        SystemExpense saved = saveDraft(expense);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("previousAmountVnd", previousAmountVnd);
        changes.put("newAmountVnd", saved.getTotalAmountVnd());
        changes.put("amountChanged", previousAmountVnd.compareTo(saved.getTotalAmountVnd()) != 0);
        changes.put("previousInvoiceNumber", previousInvoiceNumber == null ? "" : previousInvoiceNumber);
        changes.put("newInvoiceNumber", saved.getInvoiceNumber() == null ? "" : saved.getInvoiceNumber());
        audit(saved, adminId, "EXPENSE_DRAFT_UPDATED", ExpenseStatus.DRAFT, ExpenseStatus.DRAFT, changes);
        return toDetail(saved);
    }

    @Override
    @Transactional
    public ExpenseDetailResponse confirm(UUID id) {
        UUID adminId = requirePermission(MANAGE_PERMISSION);
        SystemExpense expense = requireForUpdate(id);
        requireStatus(expense, ExpenseStatus.DRAFT, "Only a draft expense can be confirmed");
        if (expense.getLines().isEmpty()) {
            throw conflict("Expense must contain at least one line before confirmation");
        }
        expense.setStatus(ExpenseStatus.CONFIRMED);
        expense.setConfirmedBy(adminId);
        expense.setConfirmedAt(Instant.now());
        SystemExpense saved = expenseRepository.save(expense);
        audit(saved, adminId, "EXPENSE_CONFIRMED", ExpenseStatus.DRAFT, ExpenseStatus.CONFIRMED,
                Map.of("amountVnd", saved.getTotalAmountVnd()));
        return toDetail(saved);
    }

    @Override
    @Transactional
    public ExpenseDetailResponse markPaid(UUID id) {
        UUID adminId = requirePermission(MANAGE_PERMISSION);
        SystemExpense expense = requireForUpdate(id);
        requireStatus(expense, ExpenseStatus.CONFIRMED, "Only a confirmed expense can be marked paid");
        expense.setStatus(ExpenseStatus.PAID);
        expense.setPaidAt(Instant.now());
        expense.setPaidBy(adminId);
        SystemExpense saved = expenseRepository.save(expense);
        audit(saved, adminId, "EXPENSE_PAID", ExpenseStatus.CONFIRMED, ExpenseStatus.PAID,
                Map.of("amountVnd", saved.getTotalAmountVnd()));
        return toDetail(saved);
    }

    @Override
    @Transactional
    public ExpenseDetailResponse voidExpense(UUID id, String reason) {
        UUID adminId = requirePermission(MANAGE_PERMISSION);
        SystemExpense expense = requireForUpdate(id);
        if (expense.getStatus() == ExpenseStatus.VOID) {
            return toDetail(expense);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    MessageCodes.MSG_EXP_002,
                    "Vui lòng nhập lý do hủy chứng từ chi phí.",
                    HttpStatus.BAD_REQUEST
            );
        }
        ExpenseStatus before = expense.getStatus();
        expense.setStatus(ExpenseStatus.VOID);
        expense.setVoidedBy(adminId);
        expense.setVoidedAt(Instant.now());
        expense.setVoidReason(reason.trim());
        SystemExpense saved = expenseRepository.save(expense);
        audit(saved, adminId, "EXPENSE_VOIDED", before, ExpenseStatus.VOID,
                Map.of("reason", reason.trim(), "amountVnd", saved.getTotalAmountVnd()));
        return toDetail(saved);
    }

    private void applyDraft(SystemExpense expense, UpsertExpenseRequest request) {
        String currency = request.getCurrency().trim().toUpperCase();
        BigDecimal exchangeRate = request.getExchangeRate().setScale(6, RoundingMode.HALF_UP);
        List<SystemExpenseLine> lines = toLines(request.getLines(), exchangeRate);
        BigDecimal originalTotal = lines.stream()
                .map(SystemExpenseLine::getOriginalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVnd = lines.stream()
                .map(SystemExpenseLine::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        expense.setVendorName(request.getVendorName().trim());
        expense.setProviderCode(normalizeNullable(request.getProviderCode()));
        expense.setInvoiceNumber(normalizeNullable(request.getInvoiceNumber()));
        expense.setDescription(normalizeNullable(request.getDescription()));
        expense.setCurrency(currency);
        expense.setExchangeRate(exchangeRate);
        expense.setOriginalTotal(originalTotal);
        expense.setTotalAmountVnd(totalVnd);
        expense.setIncurredAt(request.getIncurredAt());
        expense.setDueDate(request.getDueDate());
        expense.setExchangeRateDate("VND".equals(currency) ? null : request.getExchangeRateDate());
        expense.setExchangeRateSource("VND".equals(currency)
                ? null
                : normalizeNullable(request.getExchangeRateSource()));
        expense.setBillingPeriodFrom(request.getBillingPeriodFrom());
        expense.setBillingPeriodTo(request.getBillingPeriodTo());
        expense.setEvidenceReference(normalizeNullable(request.getEvidenceReference()));
        expense.setSourceType(request.getSourceType());
        expense.replaceLines(lines);
    }

    private List<SystemExpenseLine> toLines(List<ExpenseLineRequest> requests, BigDecimal exchangeRate) {
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    ExpenseLineRequest request = requests.get(index);
                    BigDecimal original = request.originalAmount().setScale(2, RoundingMode.HALF_UP);
                    return SystemExpenseLine.builder()
                            .categoryCode(request.categoryCode())
                            .description(request.description().trim())
                            .originalAmount(original)
                            .amountVnd(original.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
                            .lineOrder(index)
                            .build();
                })
                .toList();
    }

    private void validateRequest(UpsertExpenseRequest request, UUID existingId) {
        if ((request.getBillingPeriodFrom() == null) != (request.getBillingPeriodTo() == null)) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Billing period must contain both start and end dates"
            );
        }
        if (request.getBillingPeriodFrom() != null
                && request.getBillingPeriodFrom().isAfter(request.getBillingPeriodTo())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Billing period is invalid");
        }
        if (request.getIncurredAt().isAfter(LocalDate.now(REPORTING_ZONE))) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Incurred date cannot be in the future");
        }
        if (request.getDueDate() != null && request.getDueDate().isBefore(request.getIncurredAt())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Due date cannot precede incurred date");
        }
        String currency = request.getCurrency().trim().toUpperCase();
        if ("VND".equals(currency) && request.getExchangeRate().compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "VND exchange rate must equal 1");
        }
        if (!"VND".equals(currency)
                && (request.getExchangeRateDate() == null
                    || normalizeNullable(request.getExchangeRateSource()) == null)) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Foreign-currency expenses require an exchange-rate date and source"
            );
        }
        String provider = normalizeNullable(request.getProviderCode());
        String invoice = normalizeNullable(request.getInvoiceNumber());
        if (provider != null && invoice != null) {
            boolean duplicate = existingId == null
                    ? expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNot(
                            provider, invoice, request.getIncurredAt(), ExpenseStatus.VOID)
                    : expenseRepository.existsByProviderCodeIgnoreCaseAndInvoiceNumberIgnoreCaseAndIncurredAtAndStatusNotAndIdNot(
                            provider, invoice, request.getIncurredAt(), ExpenseStatus.VOID, existingId);
            if (duplicate) {
                throw conflict("A document with the same provider, invoice number, and invoice date already exists");
            }
        }
    }

    private UUID requirePermission(String permission) {
        UUID adminId = currentUserService.getCurrentUserId();
        if (!adminRepository.hasPermission(adminId, permission)) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_008,
                    "Finance expense permission is required: " + permission,
                    HttpStatus.FORBIDDEN
            );
        }
        return adminId;
    }

    private SystemExpense requireDetail(UUID id) {
        return expenseRepository.findDetailById(id)
                .orElseThrow(() -> notFound("Expense document not found"));
    }

    private SystemExpense requireForUpdate(UUID id) {
        return expenseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Expense document not found"));
    }

    private void requireStatus(SystemExpense expense, ExpenseStatus expected, String message) {
        if (expense.getStatus() != expected) {
            throw conflict(message);
        }
    }

    private void audit(
            SystemExpense expense,
            UUID adminId,
            String action,
            ExpenseStatus before,
            ExpenseStatus after,
            Map<String, Object> metadata
    ) {
        Map<String, Object> auditMetadata = new LinkedHashMap<>(metadata);
        auditMetadata.put("expenseCode", expense.getExpenseCode());
        auditMetadata.put("vendorName", expense.getVendorName());
        auditMetadata.put("currency", expense.getCurrency());
        auditLogService.logAdminAction(
                adminId,
                "FINANCE_MANAGER",
                action,
                "SYSTEM_EXPENSE",
                expense.getId(),
                before == null ? Map.of() : Map.of("status", before.name()),
                Map.of("status", after.name(), "totalAmountVnd", expense.getTotalAmountVnd()),
                auditMetadata
        );
    }

    private ExpenseSummaryResponse toSummary(SystemExpense expense) {
        return new ExpenseSummaryResponse(
                expense.getId(),
                expense.getExpenseCode(),
                expense.getVendorName(),
                expense.getProviderCode(),
                expense.getInvoiceNumber(),
                expense.getCurrency(),
                expense.getOriginalTotal(),
                expense.getTotalAmountVnd(),
                expense.getIncurredAt(),
                expense.getDueDate(),
                expense.getStatus(),
                expense.getSourceType(),
                expense.getLineCount(),
                expense.getCreatedBy(),
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }

    private ExpenseDetailResponse toDetail(SystemExpense expense) {
        List<ExpenseLineResponse> lines = expense.getLines().stream()
                .map(line -> new ExpenseLineResponse(
                        line.getId(),
                        line.getCategoryCode(),
                        line.getDescription(),
                        line.getOriginalAmount(),
                        line.getAmountVnd(),
                        line.getLineOrder()
                ))
                .toList();
        return new ExpenseDetailResponse(
                expense.getId(), expense.getExpenseCode(), expense.getVendorName(),
                expense.getProviderCode(), expense.getInvoiceNumber(), expense.getDescription(),
                expense.getCurrency(), expense.getExchangeRate(), expense.getOriginalTotal(),
                expense.getTotalAmountVnd(), expense.getIncurredAt(), expense.getDueDate(),
                expense.getExchangeRateDate(), expense.getExchangeRateSource(), expense.getBillingPeriodFrom(),
                expense.getBillingPeriodTo(), expense.getPaidAt(), expense.getPaidBy(), expense.getEvidenceReference(),
                expense.getStatus(), expense.getSourceType(), expense.getCreatedBy(), expense.getConfirmedBy(),
                expense.getConfirmedAt(), expense.getVoidedBy(), expense.getVoidedAt(), expense.getVoidReason(),
                expense.getVersion(), expense.getCreatedAt(), expense.getUpdatedAt(), lines
        );
    }

    private Pageable safePageable(Pageable requested) {
        int page = Math.max(0, requested == null ? 0 : requested.getPageNumber());
        int size = Math.min(100, Math.max(1, requested == null ? 20 : requested.getPageSize()));
        List<Sort.Order> orders = requested == null ? List.of() : requested.getSort().stream()
                .filter(order -> ALLOWED_SORTS.contains(order.getProperty()))
                .toList();
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.desc("incurredAt"), Sort.Order.desc("id"))
                : Sort.by(orders).and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(page, size, sort);
    }

    private String generateCode() {
        return "EXP-" + LocalDate.now(REPORTING_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SystemExpense saveDraft(SystemExpense expense) {
        try {
            return expenseRepository.saveAndFlush(expense);
        } catch (DataIntegrityViolationException exception) {
            if (causedByConstraint(exception, DUPLICATE_INVOICE_CONSTRAINT)) {
                throw conflict("A document with the same provider, invoice number, and invoice date already exists");
            }
            throw exception;
        }
    }

    private boolean causedByConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(constraintName.toLowerCase())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal moneyValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(MessageCodes.COMMON_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(MessageCodes.COMMON_CONFLICT, message, HttpStatus.CONFLICT);
    }
}
