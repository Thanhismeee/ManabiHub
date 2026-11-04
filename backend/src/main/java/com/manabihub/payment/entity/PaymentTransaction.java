package com.manabihub.payment.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.manabihub.order.entity.Order;
import com.manabihub.payment.enums.PaymentStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A record of one payment attempt against an {@link Order} at a payment provider (UC-08).
 * <p>
 * Payment is confirmed from a backend-verified VNPay callback (IPN or signed browser
 * return), never from unsigned client state. Maps the {@code payment_transactions} table (V002).
 */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Payment provider identifier, e.g. {@code VNPAY}. */
    @Column(nullable = false, length = 50)
    private String provider;

    /** Provider-side transaction id (e.g. {@code vnp_TransactionNo}); the webhook idempotency key. */
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    /** Raw provider payload captured for audit/troubleshooting. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private JsonNode rawResponse;

    /** Immutable business timestamp captured when the provider first confirms payment. */
    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
