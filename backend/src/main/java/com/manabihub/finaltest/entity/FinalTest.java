package com.manabihub.finaltest.entity;

import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "final_tests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalTest {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    private Course course;

    @Column(name = "time_limit_minutes", nullable = false)
    private Integer timeLimitMinutes;

    @Column(name = "passing_score", nullable = false)
    private Integer passingScore;

    @Column(name = "max_retakes", nullable = false)
    private Integer maxRetakes;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", length = 10)
    private JlptLevel jlptLevel;

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

    @Builder.Default
    @Column(name = "assessment_format", nullable = false, length = 32)
    private String assessmentFormat = "COURSE";

    @Column(name = "skill_focus", nullable = false, length = 50)
    private String skillFocus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder.Default
    @OrderBy("orderIndex ASC")
    @OneToMany(mappedBy = "finalTest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinalTestQuestion> questions = new ArrayList<>();
}
