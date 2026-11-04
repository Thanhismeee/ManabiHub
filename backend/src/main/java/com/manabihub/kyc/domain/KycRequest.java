package com.manabihub.kyc.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "kyc_requests")
public class KycRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacherProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycRequestStatus status = KycRequestStatus.DRAFT;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "ekyc_provider")
    private String ekycProvider;

    @Column(name = "ekyc_reference_id")
    private String ekycReferenceId;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false)
    private IdentityVerificationStatus identityStatus = IdentityVerificationStatus.NOT_STARTED;

    @Column(name = "identity_verified_at")
    private Instant identityVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_status", nullable = false)
    private CertificateVerificationStatus certificateStatus = CertificateVerificationStatus.LOCKED;

    @Column(name = "certificate_submitted_at")
    private Instant certificateSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "certificate_code")
    private String certificateCode;

    @Column(name = "copyright_agreed", nullable = false)
    private boolean copyrightAgreed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> verificationPayload = Map.of();

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "server_verified_at")
    private Instant serverVerifiedAt;

    @Column(name = "server_verification_attempt_count", nullable = false)
    private int serverVerificationAttemptCount = 0;

    @Column(name = "server_verification_expires_at")
    private Instant serverVerificationExpiresAt;

    @Column(name = "server_verification_next_retry_at")
    private Instant serverVerificationNextRetryAt;

    @Column(name = "server_full_name")
    private String serverFullName;

    @Column(name = "server_date_of_birth")
    private String serverDateOfBirth;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        submittedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
