package com.manabihub.kyc.domain;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_language_qualifications")
public class TeacherLanguageQualification {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_language", nullable = false, length = 32)
    private LearningLanguage learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_framework", nullable = false, length = 32)
    private ProficiencyFramework proficiencyFramework;

    @Column(name = "framework_version", nullable = false, length = 32)
    private String frameworkVersion;

    @Column(name = "credential_level", nullable = false, length = 64)
    private String credentialLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 40)
    private TeacherCredentialType credentialType;

    @Column(name = "credential_code", length = 120)
    private String credentialCode;

    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;

    @Column(name = "evidence_path", nullable = false, columnDefinition = "TEXT")
    private String evidencePath;

    @Column(name = "evidence_file_name", nullable = false, length = 255)
    private String evidenceFileName;

    @Column(name = "evidence_mime_type", nullable = false, length = 100)
    private String evidenceMimeType;

    @Column(name = "evidence_file_size", nullable = false)
    private long evidenceFileSize;

    @Column(name = "evidence_sha256", nullable = false, length = 64)
    private String evidenceSha256;

    @Column(name = "copyright_agreed", nullable = false)
    private boolean copyrightAgreed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeacherQualificationStatus status;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = TeacherQualificationStatus.PENDING;
        }
        if (submittedAt == null) {
            submittedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
