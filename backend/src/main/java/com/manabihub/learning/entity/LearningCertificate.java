package com.manabihub.learning.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_certificates")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    @Column(name = "certificate_number", nullable = false, unique = true, length = 64)
    private String certificateNumber;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode eligibilitySnapshot;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
}
