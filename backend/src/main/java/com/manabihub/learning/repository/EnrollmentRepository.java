package com.manabihub.learning.repository;

import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    int countByStudentIdAndStatus(UUID studentId, EnrollmentStatus status);
    
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :courseId " +
           "AND e.enrolledAt >= :startDate AND e.enrolledAt <= :endDate")
    long countByCourseIdAndDateRange(
            @Param("courseId") UUID courseId, 
            @Param("startDate") java.time.Instant startDate, 
            @Param("endDate") java.time.Instant endDate);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :courseId AND e.status = :status " +
           "AND e.enrolledAt >= :startDate AND e.enrolledAt <= :endDate")
    long countByCourseIdAndStatusAndDateRange(
            @Param("courseId") UUID courseId, 
            @Param("status") EnrollmentStatus status,
            @Param("startDate") java.time.Instant startDate, 
            @Param("endDate") java.time.Instant endDate);

    /**
     * Counts active/completed learners in one round trip for the public catalog.
     * Refunded, revoked and expired enrollments are intentionally excluded from
     * social proof so the number represents current valid access.
     */
    @Query("""
            SELECT e.course.id AS courseId, COUNT(e.id) AS enrollmentCount
            FROM Enrollment e
            WHERE e.course.id IN :courseIds
              AND e.status IN :statuses
            GROUP BY e.course.id
            """)
    List<CourseEnrollmentCount> countByCourseIdsAndStatuses(
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("statuses") Collection<EnrollmentStatus> statuses);

    interface CourseEnrollmentCount {
        UUID getCourseId();

        long getEnrollmentCount();
    }

    @EntityGraph(attributePaths = {"course", "course.teacher", "course.teacher.user"})
    Page<Enrollment> findByStudentIdAndStatusIn(
            UUID studentId,
            List<EnrollmentStatus> statuses,
            Pageable pageable);

    // UC-10: study course lessons — resolve the current student's enrollment for a course.
    Optional<Enrollment> findByStudent_IdAndCourse_Id(UUID studentId, UUID courseId);

    List<Enrollment> findByCourse_IdAndStudent_IdIn(UUID courseId, Collection<UUID> studentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT enrollment
            FROM Enrollment enrollment
            WHERE enrollment.student.id = :studentId
              AND enrollment.course.id = :courseId
            """)
    Optional<Enrollment> findByStudentIdAndCourseIdForUpdate(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId
    );

    /**
     * Serializes review upserts for the same enrollment. The database unique
     * constraint remains the final guarantee, while this lock makes concurrent
     * PUT requests deterministic at the service boundary.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT enrollment
            FROM Enrollment enrollment
            JOIN FETCH enrollment.course
            WHERE enrollment.student.id = :studentId
              AND enrollment.course.id = :courseId
            """)
    Optional<Enrollment> findByStudentIdAndCourseIdForReview(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e WHERE e.id = :id")
    Optional<Enrollment> findByIdForUpdate(@Param("id") UUID id);
}
