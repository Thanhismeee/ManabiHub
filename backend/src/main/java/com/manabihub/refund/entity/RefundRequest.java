package com.manabihub.refund.entity;

import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.refund.enums.RefundDecisionReason;
import com.manabihub.refund.enums.RefundProviderStatus;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.enums.RefundSettlementMethod;
import com.manabihub.refund.enums.RefundSettlementStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import com.manabihub.refund.dto.RefundEligibilitySnapshot;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot", columnDefinition = "jsonb", updatable = false)
    private RefundEligibilitySnapshot eligibilitySnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private InternalAdminAccount decidedBy;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_reason_code", length = 60)
    private RefundDecisionReason decisionReasonCode;

    @Column(name = "reconciliation_reason_code", length = 80)
    private String reconciliationReasonCode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "provider_status", nullable = false, length = 40)
    private RefundProviderStatus providerStatus = RefundProviderStatus.NOT_REQUESTED;

    @Builder.Default
    @Column(name = "provider_attempt_count", nullable = false)
    private int providerAttemptCount = 0;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_method", length = 30)
    private RefundSettlementMethod settlementMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", length = 30)
    private RefundSettlementStatus settlementStatus;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "wallet_transaction_id")
    private UUID walletTransactionId;

    // Durable automatic-settlement work, committed with the student's request.
    @Column(name = "auto_refund_next_attempt_at")
    private Instant autoRefundNextAttemptAt;

    @Builder.Default
    @Column(name = "auto_refund_attempt_count", nullable = false)
    private int autoRefundAttemptCount = 0;

    @Column(name = "auto_refund_last_error_code", length = 80)
    private String autoRefundLastErrorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
