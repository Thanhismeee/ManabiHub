package com.manabihub.cohort.domain;

import com.manabihub.course.entity.Course;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "learning_cohorts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningCohort {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_teacher_id", nullable = false)
    private TeacherProfile ownerTeacher;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Builder.Default
    @Column(name = "organization_name", nullable = false, length = 160)
    private String organizationName = "SOFL";

    @Column(name = "branch_name", length = 160)
    private String branchName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(name = "max_students", nullable = false)
    private int maxStudents = 30;

    @Builder.Default
    @Column(name = "feedback_quota_per_student", nullable = false)
    private int feedbackQuotaPerStudent = 12;

    @Builder.Default
    @Column(name = "review_sla_hours", nullable = false)
    private int reviewSlaHours = 48;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortStatus status = CohortStatus.PLANNED;

    @Builder.Default
    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt ASC")
    private List<CohortMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
