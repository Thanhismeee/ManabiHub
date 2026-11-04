package com.manabihub.finance.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.finance.dto.request.ExpenseFilterRequest;
import com.manabihub.finance.dto.request.UpsertExpenseRequest;
import com.manabihub.finance.dto.request.VoidExpenseRequest;
import com.manabihub.finance.dto.response.ExpenseDetailResponse;
import com.manabihub.finance.dto.response.ExpenseOverviewResponse;
import com.manabihub.finance.dto.response.ExpenseSummaryResponse;
import com.manabihub.finance.service.SystemExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/finance/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_MANAGER')")
public class AdminExpenseController {

    private final SystemExpenseService expenseService;

    @GetMapping
    public ApiResponse<PageResponse<ExpenseSummaryResponse>> search(
            @ModelAttribute ExpenseFilterRequest filter,
            @PageableDefault(size = 20, sort = "incurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(expenseService.search(filter, pageable));
    }

    @GetMapping("/overview")
    public ApiResponse<ExpenseOverviewResponse> overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(expenseService.overview(from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseDetailResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(expenseService.get(id));
    }

    @PostMapping
    public ApiResponse<ExpenseDetailResponse> create(@Valid @RequestBody UpsertExpenseRequest request) {
        return ApiResponse.success(expenseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertExpenseRequest request
    ) {
        return ApiResponse.success(expenseService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ExpenseDetailResponse> confirm(@PathVariable UUID id) {
        return ApiResponse.success(expenseService.confirm(id));
    }

    @PostMapping("/{id}/paid")
    public ApiResponse<ExpenseDetailResponse> markPaid(@PathVariable UUID id) {
        return ApiResponse.success(expenseService.markPaid(id));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<ExpenseDetailResponse> voidExpense(
            @PathVariable UUID id,
            @Valid @RequestBody VoidExpenseRequest request
    ) {
        return ApiResponse.success(expenseService.voidExpense(id, request.reason()));
    }
}
