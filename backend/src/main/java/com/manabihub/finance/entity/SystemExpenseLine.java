package com.manabihub.finance.entity;

import com.manabihub.finance.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "system_expense_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemExpenseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private SystemExpense expense;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_code", nullable = false, length = 50)
    private ExpenseCategory categoryCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "amount_vnd", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountVnd;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;
}
