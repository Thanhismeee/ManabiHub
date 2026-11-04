package com.manabihub.moderation.entity;

import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.moderation.enums.ModerationDecisionType;
import com.manabihub.moderation.enums.EvidenceRequestedFrom;
import com.manabihub.moderation.enums.ViolationReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "moderation_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationDecision {

    @Id
    @UuidGenerator
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_report_id", nullable = false)
    private ViolationReport violationReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by", nullable = false)
    private InternalAdminAccount decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false)
    private ModerationDecisionType decisionType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before")
    private ViolationReportStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after")
    private ViolationReportStatus statusAfter;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_requested_from")
    private EvidenceRequestedFrom evidenceRequestedFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "moderationDecision", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModerationActionRecord> actions;
}
