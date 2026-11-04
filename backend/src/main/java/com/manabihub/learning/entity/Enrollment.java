package com.manabihub.learning.entity;

import com.manabihub.course.entity.Course;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.EnrollmentAccessSource;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 30)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    /** Cached effective deadline across the independent access grants. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "access_source", nullable = false, length = 30)
    private EnrollmentAccessSource accessSource = EnrollmentAccessSource.PURCHASE;

    @Column(name = "source_cohort_id")
    private UUID sourceCohortId;

    /**
     * Set when the learner has downloaded every protected material belonging to
     * this enrollment. BR-REF-01 uses this immutable evidence to block automatic
     * approval while still allowing a dispute/manual-review request.
     */
    @Column(name = "protected_materials_fully_downloaded_at")
    private Instant protectedMaterialsFullyDownloadedAt;

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
