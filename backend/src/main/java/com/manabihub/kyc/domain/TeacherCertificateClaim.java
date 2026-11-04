package com.manabihub.kyc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "teacher_certificate_claims",
        uniqueConstraints = @UniqueConstraint(
                name = TeacherCertificateClaim.CONSTRAINT_UK_TYPE_CODE,
                columnNames = {"certificate_type", "normalized_certificate_code"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherCertificateClaim {

    public static final String CONSTRAINT_UK_TYPE_CODE = "uk_teacher_certificate_claims_type_code";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "kyc_request_id", nullable = false)
    private UUID kycRequestId;

    @Builder.Default
    @Column(name = "certificate_type", nullable = false, length = 20)
    private String certificateType = "JLPT";

    @Column(name = "normalized_certificate_code", nullable = false, length = 100)
    private String normalizedCertificateCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
