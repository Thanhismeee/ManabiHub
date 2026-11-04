package com.manabihub.finance.entity;

import com.manabihub.finance.enums.ExpenseSourceType;
import com.manabihub.finance.enums.ExpenseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "system_expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expense_code", nullable = false, unique = true, length = 50)
    private String expenseCode;

    @Column(name = "vendor_name", nullable = false, length = 255)
    private String vendorName;

    @Column(name = "provider_code", length = 80)
    private String providerCode;

    @Column(name = "invoice_number", length = 120)
    private String invoiceNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "original_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalTotal;

    @Column(name = "total_amount_vnd", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmountVnd;

    @Column(name = "incurred_at", nullable = false)
    private LocalDate incurredAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "exchange_rate_source", length = 120)
    private String exchangeRateSource;

    @Column(name = "billing_period_from")
    private LocalDate billingPeriodFrom;

    @Column(name = "billing_period_to")
    private LocalDate billingPeriodTo;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "evidence_reference", length = 500)
    private String evidenceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ExpenseSourceType sourceType;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineOrder ASC")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<SystemExpenseLine> lines = new ArrayList<>();

    @Formula("(SELECT COUNT(*) FROM system_expense_lines expense_line WHERE expense_line.expense_id = id)")
    private int lineCount;

    public void replaceLines(List<SystemExpenseLine> replacement) {
        lines.clear();
        replacement.forEach(line -> {
            line.setExpense(this);
            lines.add(line);
        });
    }
}
