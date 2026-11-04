package com.manabihub.payout.entity;

import com.manabihub.payout.enums.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payout_reconciliation_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "withdrawal_request_id", nullable = false)
    private UUID withdrawalRequestId;

    @Column(name = "payout_settlement_id")
    private UUID payoutSettlementId;

    @Column(name = "checked_by", nullable = false)
    private UUID checkedBy;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconciliationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> alerts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
