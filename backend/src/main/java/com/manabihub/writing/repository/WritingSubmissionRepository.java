package com.manabihub.writing.repository;

import com.manabihub.writing.entity.WritingSubmission;
import com.manabihub.writing.repository.projection.WritingReviewFacetProjection;
import com.manabihub.writing.repository.projection.WritingSubmissionQueueProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, UUID> {

    String QUEUE_FROM = """
            FROM writing_submissions ws
            JOIN enrollments e ON e.id = ws.enrollment_id
            JOIN courses c ON c.id = e.course_id
            JOIN student_profiles sp ON sp.id = ws.student_id
            JOIN app_users au ON au.id = sp.user_id
            LEFT JOIN lessons legacy_lesson ON legacy_lesson.id = ws.lesson_id
            LEFT JOIN course_lesson_blocks lesson_block ON lesson_block.id = ws.lesson_block_id
            WHERE c.teacher_id = :teacherId
              AND ws.status <> 'DRAFT'
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (
                  :lessonId IS NULL
                  OR COALESCE(legacy_lesson.id, lesson_block.id) = :lessonId
              )
              AND (:status IS NULL OR ws.status = :status)
              AND (
                  :searchQuery = ''
                  OR LOWER(COALESCE(NULLIF(sp.display_name, ''), au.full_name, au.email))
                     LIKE LOWER(CONCAT('%', :searchQuery, '%'))
                  OR LOWER(c.title) LIKE LOWER(CONCAT('%', :searchQuery, '%'))
                  OR LOWER(COALESCE(legacy_lesson.title, lesson_block.title, ''))
                     LIKE LOWER(CONCAT('%', :searchQuery, '%'))
              )
              AND (
                  :reviewed IS NULL
                  OR (:reviewed = TRUE AND EXISTS (
                      SELECT 1 FROM teacher_writing_feedback twf
                      WHERE twf.writing_submission_id = ws.id
                  ))
                  OR (:reviewed = FALSE AND NOT EXISTS (
                      SELECT 1 FROM teacher_writing_feedback twf
                      WHERE twf.writing_submission_id = ws.id
                  ))
              )
            """;

    @Query(
            value = """
                    SELECT ws.id AS id,
                           c.id AS courseId,
                           c.title AS courseTitle,
                           COALESCE(legacy_lesson.id, lesson_block.id) AS lessonId,
                           COALESCE(legacy_lesson.title, lesson_block.title, 'Writing activity') AS lessonTitle,
                           COALESCE(NULLIF(sp.display_name, ''), au.full_name, au.email) AS studentName,
                           au.email AS studentEmail,
                           ws.status AS status,
                           ws.submitted_at AS submittedAt,
                           EXISTS (
                               SELECT 1 FROM ai_writing_suggestions aws
                               WHERE aws.writing_submission_id = ws.id
                           ) AS hasAiSuggestion,
                           EXISTS (
                               SELECT 1 FROM teacher_writing_feedback twf
                               WHERE twf.writing_submission_id = ws.id
                           ) AS hasTeacherFeedback,
                           (
                               SELECT twf.score
                               FROM teacher_writing_feedback twf
                               WHERE twf.writing_submission_id = ws.id
                               ORDER BY twf.created_at DESC
                               LIMIT 1
                           ) AS score
                    """ + QUEUE_FROM + " ORDER BY ws.submitted_at DESC",
            countQuery = "SELECT COUNT(*) " + QUEUE_FROM,
            nativeQuery = true
    )
    Page<WritingSubmissionQueueProjection> findOwnedQueue(
            @Param("teacherId") UUID teacherId,
            @Param("searchQuery") String searchQuery,
            @Param("reviewed") Boolean reviewed,
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = "SELECT COUNT(*) " + QUEUE_FROM, nativeQuery = true)
    long countOwnedQueue(
            @Param("teacherId") UUID teacherId,
            @Param("searchQuery") String searchQuery,
            @Param("reviewed") Boolean reviewed,
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId,
            @Param("status") String status
    );

    @Query(value = """
            SELECT AVG((
                SELECT twf.score
                FROM teacher_writing_feedback twf
                WHERE twf.writing_submission_id = ws.id
                ORDER BY twf.created_at DESC
                LIMIT 1
            ))
            """ + QUEUE_FROM, nativeQuery = true)
    BigDecimal averageOwnedScore(
            @Param("teacherId") UUID teacherId,
            @Param("searchQuery") String searchQuery,
            @Param("reviewed") Boolean reviewed,
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId,
            @Param("status") String status
    );

    @Query(value = """
            SELECT DISTINCT
                   c.id AS courseId,
                   c.title AS courseTitle,
                   COALESCE(legacy_lesson.id, lesson_block.id) AS lessonId,
                   COALESCE(legacy_lesson.title, lesson_block.title, 'Writing activity') AS lessonTitle
            FROM writing_submissions ws
            JOIN enrollments e ON e.id = ws.enrollment_id
            JOIN courses c ON c.id = e.course_id
            LEFT JOIN lessons legacy_lesson ON legacy_lesson.id = ws.lesson_id
            LEFT JOIN course_lesson_blocks lesson_block ON lesson_block.id = ws.lesson_block_id
            WHERE c.teacher_id = :teacherId
              AND ws.status <> 'DRAFT'
            ORDER BY c.title, lessonTitle
            """, nativeQuery = true)
    List<WritingReviewFacetProjection> findOwnedFacets(@Param("teacherId") UUID teacherId);

    @EntityGraph(attributePaths = {
            "enrollment", "enrollment.course", "student", "student.user"
    })
    @Query("""
            SELECT ws
            FROM WritingSubmission ws
            WHERE ws.id = :submissionId
              AND ws.enrollment.course.teacher.id = :teacherId
              AND ws.status <> com.manabihub.writing.enums.WritingSubmissionStatus.DRAFT
            """)
    Optional<WritingSubmission> findOwnedById(
            @Param("submissionId") UUID submissionId,
            @Param("teacherId") UUID teacherId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "enrollment", "enrollment.course", "student", "student.user"
    })
    @Query("""
            SELECT ws
            FROM WritingSubmission ws
            WHERE ws.id = :submissionId
              AND ws.enrollment.course.teacher.id = :teacherId
              AND ws.status <> com.manabihub.writing.enums.WritingSubmissionStatus.DRAFT
            """)
    Optional<WritingSubmission> findOwnedByIdForUpdate(
            @Param("submissionId") UUID submissionId,
            @Param("teacherId") UUID teacherId
    );

    @Query(value = """
            SELECT COALESCE(legacy_lesson.title, lesson_block.title, 'Writing activity')
            FROM writing_submissions ws
            LEFT JOIN lessons legacy_lesson ON legacy_lesson.id = ws.lesson_id
            LEFT JOIN course_lesson_blocks lesson_block ON lesson_block.id = ws.lesson_block_id
            WHERE ws.id = :submissionId
            """, nativeQuery = true)
    Optional<String> findLessonTitle(@Param("submissionId") UUID submissionId);

    @EntityGraph(attributePaths = {
            "enrollment", "enrollment.course", "student", "student.user"
    })
    Optional<WritingSubmission> findByEnrollmentIdAndLessonBlockId(UUID enrollmentId, UUID lessonBlockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "enrollment", "enrollment.course", "student", "student.user"
    })
    @Query("""
            SELECT ws
            FROM WritingSubmission ws
            WHERE ws.enrollment.id = :enrollmentId
              AND ws.lessonBlockId = :lessonBlockId
            """)
    Optional<WritingSubmission> findByEnrollmentIdAndLessonBlockIdForUpdate(
            @Param("enrollmentId") UUID enrollmentId,
            @Param("lessonBlockId") UUID lessonBlockId
    );

    @EntityGraph(attributePaths = {
            "enrollment", "enrollment.course", "student", "student.user"
    })
    Optional<WritingSubmission> findByIdAndEnrollmentIdAndLessonBlockId(UUID id, UUID enrollmentId, UUID lessonBlockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WritingSubmission w WHERE w.id = :id AND w.enrollment.id = :enrollmentId AND w.lessonBlockId = :lessonBlockId")
    Optional<WritingSubmission> findByIdAndEnrollmentIdAndLessonBlockIdForUpdate(@Param("id") UUID id, @Param("enrollmentId") UUID enrollmentId, @Param("lessonBlockId") UUID lessonBlockId);
}
