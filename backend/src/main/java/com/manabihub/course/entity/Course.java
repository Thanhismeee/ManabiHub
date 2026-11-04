package com.manabihub.course.entity;

import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.kyc.domain.TeacherProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_code")
    private JlptLevel jlptLevel;

    /** Language being learned; separate from the Vietnamese application locale. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "learning_language", nullable = false, length = 32)
    private LearningLanguage learningLanguage = LearningLanguage.JAPANESE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_framework", nullable = false, length = 32)
    private ProficiencyFramework proficiencyFramework = ProficiencyFramework.JLPT;

    @Builder.Default
    @Column(name = "framework_version", nullable = false, length = 32)
    private String frameworkVersion = "CURRENT";

    @Column(name = "proficiency_level", length = 32)
    private String proficiencyLevel;

    @Column(length = 100)
    private String category;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(columnDefinition = "TEXT")
    private String outcomes;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    @Column(name = "target_students", columnDefinition = "TEXT")
    private String targetStudents;

    /** Relative access duration captured on each enrollment unless a fixed expiry is set. */
    @Builder.Default
    @Column(name = "access_duration_days", nullable = false)
    private Integer accessDurationDays = 180;

    /** Optional fixed expiry for cohort/JLPT-season courses. */
    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.DRAFT;

    @Builder.Default
    @Column(name = "ai_supported", nullable = false)
    private boolean aiSupported = false;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder.Default
    @OrderBy("orderIndex ASC")
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseLearningGoal> learningGoals = new ArrayList<>();

    @OneToOne(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private com.manabihub.finaltest.entity.FinalTest finalTest;

    @Builder.Default
    @OrderBy("orderIndex ASC")
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseModule> modules = new ArrayList<>();

    public Instant resolveEnrollmentExpiry(Instant enrolledAt) {
        if (accessExpiresAt != null) {
            return accessExpiresAt;
        }
        int days = accessDurationDays == null || accessDurationDays < 1 ? 180 : accessDurationDays;
        return enrolledAt.plus(java.time.Duration.ofDays(days));
    }

    public void addLearningGoal(String goalText, int orderIndex) {
        CourseLearningGoal goal = CourseLearningGoal.builder()
                .course(this)
                .goalText(goalText)
                .orderIndex(orderIndex)
                .build();
        learningGoals.add(goal);
    }

    public void addModule(CourseModule module) {
        module.setCourse(this);
        modules.add(module);
    }
}
