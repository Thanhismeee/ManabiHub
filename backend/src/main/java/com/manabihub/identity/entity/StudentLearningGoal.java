package com.manabihub.identity.entity;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_learning_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLearningGoal {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_language", nullable = false, length = 32)
    private LearningLanguage learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_framework", nullable = false, length = 32)
    private ProficiencyFramework proficiencyFramework;

    @Builder.Default
    @Column(name = "framework_version", nullable = false, length = 32)
    private String frameworkVersion = "CURRENT";

    @Column(name = "proficiency_level", nullable = false, length = 32)
    private String proficiencyLevel;

    @Column(name = "primary_goal", nullable = false)
    private boolean primaryGoal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
