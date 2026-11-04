package com.manabihub.review.repository;

import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseReviewRepository extends JpaRepository<CourseReview, UUID> {

    Optional<CourseReview> findByEnrollment_Id(UUID enrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "enrollment",
            "enrollment.student",
            "enrollment.student.user",
            "enrollment.course",
            "enrollment.course.teacher",
            "enrollment.course.teacher.user"
    })
    @Query("SELECT review FROM CourseReview review WHERE review.id = :reviewId")
    Optional<CourseReview> findByIdForModeration(@Param("reviewId") UUID reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM CourseReview review WHERE review.id = :reviewId")
    Optional<CourseReview> findByIdForTeacherReply(@Param("reviewId") UUID reviewId);

    @EntityGraph(attributePaths = {
            "enrollment",
            "enrollment.student",
            "enrollment.student.user"
    })
    @Query("""
            SELECT review
            FROM CourseReview review
            WHERE review.enrollment.course.id = :courseId
              AND review.status = :reviewStatus
              AND review.enrollment.status IN :enrollmentStatuses
            ORDER BY review.updatedAt DESC, review.id DESC
            """)
    Page<CourseReview> findPublicReviews(
            @Param("courseId") UUID courseId,
            @Param("reviewStatus") CourseReviewStatus reviewStatus,
            @Param("enrollmentStatuses") Collection<EnrollmentStatus> enrollmentStatuses,
            Pageable pageable
    );

    @Query("""
            SELECT review.enrollment.course.id AS courseId,
                   AVG(review.rating) AS averageRating,
                   COUNT(review.id) AS reviewCount
            FROM CourseReview review
            WHERE review.enrollment.course.id IN :courseIds
              AND review.status = :reviewStatus
              AND review.enrollment.status IN :enrollmentStatuses
            GROUP BY review.enrollment.course.id
            """)
    List<CourseReviewAggregateProjection> findAggregates(
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("reviewStatus") CourseReviewStatus reviewStatus,
            @Param("enrollmentStatuses") Collection<EnrollmentStatus> enrollmentStatuses
    );

    interface CourseReviewAggregateProjection {
        UUID getCourseId();

        Double getAverageRating();

        Long getReviewCount();
    }
}
