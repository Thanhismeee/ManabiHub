package com.manabihub.mock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mock_national_id_registry")
public class MockNationalIdRegistryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_number", nullable = false, unique = true, length = 20)
    private String idNumber;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String gender;

    @Column(name = "permanent_address", nullable = false, columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "issue_place", nullable = false, columnDefinition = "TEXT")
    private String issuePlace;

    @Column(name = "document_status", nullable = false, length = 30)
    private String documentStatus;

    @Column(name = "front_back_match_status", nullable = false, length = 30)
    private String frontBackMatchStatus;

    @Column(name = "corner_blur_status", nullable = false, length = 30)
    private String cornerBlurStatus;

    @Column(name = "id_quality_status", nullable = false, length = 30)
    private String idQualityStatus;

    @Column(name = "issue_date_status", nullable = false, length = 30)
    private String issueDateStatus;

    @Column(name = "expiry_status", nullable = false, length = 30)
    private String expiryStatus;

    @Column(name = "document_identification_status", nullable = false, length = 50)
    private String documentIdentificationStatus;

    @Column(name = "warning_status", nullable = false, length = 30)
    private String warningStatus;

    @Column(name = "overlay_image_status", nullable = false, length = 30)
    private String overlayImageStatus;

    @Column(name = "open_eyes_status", nullable = false, length = 30)
    private String openEyesStatus;

    @Column(name = "blurred_face_status", nullable = false, length = 30)
    private String blurredFaceStatus;

    @Column(name = "face_validation_status", nullable = false, length = 30)
    private String faceValidationStatus;

    @Column(name = "covered_face_status", nullable = false, length = 30)
    private String coveredFaceStatus;

    @Column(name = "face_matching_score", nullable = false, precision = 7, scale = 4)
    private BigDecimal faceMatchingScore;

    @Column(name = "source_provider", nullable = false, length = 50)
    private String sourceProvider;

    @Column(name = "source_reference", nullable = false, length = 100)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = Map.of();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
