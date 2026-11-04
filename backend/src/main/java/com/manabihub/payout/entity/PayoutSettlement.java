package com.manabihub.payout.entity;

import com.manabihub.payout.enums.PayoutStatus;
import com.manabihub.payout.enums.PayoutNotificationStatus;
import com.manabihub.payout.enums.PayoutTransferMethod;
import com.manabihub.payout.enums.ReconciliationStatus;
import com.manabihub.wallet.enums.WalletOwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

@Entity
@Table(name = "payout_settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "withdrawal_request_id", nullable = false, unique = true)
    private UUID withdrawalRequestId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "student_id")
    private UUID studentId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private WalletOwnerType ownerType = WalletOwnerType.TEACHER;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_reference_id", length = 255)
    private String providerReferenceId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_method", nullable = false, length = 20)
    private PayoutTransferMethod transferMethod = PayoutTransferMethod.GATEWAY;

    @Column(name = "manual_proof_storage_key", length = 500)
    private String manualProofStorageKey;

    @Column(name = "manual_proof_original_name", length = 255)
    private String manualProofOriginalName;

    @Column(name = "manual_proof_content_type", length = 100)
    private String manualProofContentType;

    @Column(name = "manual_proof_size")
    private Long manualProofSize;

    @Column(name = "manual_transferred_at")
    private Instant manualTransferredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus;

    @Column(name = "reconciliation_note", length = 500)
    private String reconciliationNote;

    @Column(length = 50)
    private String decision;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "executed_by")
    private UUID executedBy;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message_sanitized", length = 500)
    private String failureMessageSanitized;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 20)
    private PayoutNotificationStatus notificationStatus = PayoutNotificationStatus.NOT_REQUIRED;

    @Builder.Default
    @Column(name = "notification_attempts", nullable = false)
    private int notificationAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
