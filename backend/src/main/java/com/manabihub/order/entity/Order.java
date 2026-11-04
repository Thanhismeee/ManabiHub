package com.manabihub.order.entity;

import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.enums.OrderType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's purchase order for one or more courses (UC-08).
 * <p>
 * Maps the {@code orders} table created in the baseline schema (V002).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    /** Human-readable order reference, also used as {@code vnp_TxnRef} at the payment gateway. */
    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Portion of the total paid from the student's wallet (combined payment); 0 otherwise. */
    @Builder.Default
    @Column(name = "wallet_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal walletAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /** Whether this order is a course purchase (UC-08) or a wallet top-up (MHB-37). */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    @Builder.Default
    private OrderType type = OrderType.COURSE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Amount to charge via the payment gateway = total minus the wallet-paid portion. */
    public BigDecimal getGatewayAmount() {
        BigDecimal wallet = walletAmount == null ? BigDecimal.ZERO : walletAmount;
        return totalAmount.subtract(wallet);
    }
}
