package com.manabihub.course.repository;

import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.projection.PublicCourseCardProjection;
import com.manabihub.course.repository.projection.PublicCourseLessonCountProjection;
import com.manabihub.course.repository.projection.PublicCourseRankProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    String PUBLIC_COURSE_RANK_SELECT = """
            SELECT course.id AS "courseId",
                   COUNT(DISTINCT enrollment.id) AS "enrollmentCount",
                   COALESCE(AVG(review.rating), 0) AS "averageRating",
                   COUNT(DISTINCT review.id) AS "reviewCount"
            FROM courses course
            LEFT JOIN enrollments enrollment
                   ON enrollment.course_id = course.id
                  AND enrollment.enrollment_status IN ('ACTIVE', 'COMPLETED')
            LEFT JOIN course_reviews review
                   ON review.enrollment_id = enrollment.id
                  AND review.review_status = 'APPROVED'
            WHERE course.status = 'PUBLISHED'
              AND (CAST(:keywordPattern AS text) IS NULL
                   OR LOWER(course.title) LIKE CAST(:keywordPattern AS text)
                   OR LOWER(COALESCE(course.description, '')) LIKE CAST(:keywordPattern AS text))
              AND (CAST(:category AS text) IS NULL OR course.category = CAST(:category AS text))
              AND (CAST(:jlptLevel AS text) IS NULL OR course.level_code = CAST(:jlptLevel AS text))
              AND (CAST(:minPrice AS numeric) IS NULL OR course.price >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL OR course.price <= CAST(:maxPrice AS numeric))
            GROUP BY course.id, course.published_at
            """;

    String PUBLIC_COURSE_RANK_COUNT = """
            SELECT COUNT(course.id)
            FROM courses course
            WHERE course.status = 'PUBLISHED'
              AND (CAST(:keywordPattern AS text) IS NULL
                   OR LOWER(course.title) LIKE CAST(:keywordPattern AS text)
                   OR LOWER(COALESCE(course.description, '')) LIKE CAST(:keywordPattern AS text))
              AND (CAST(:category AS text) IS NULL OR course.category = CAST(:category AS text))
              AND (CAST(:jlptLevel AS text) IS NULL OR course.level_code = CAST(:jlptLevel AS text))
              AND (CAST(:minPrice AS numeric) IS NULL OR course.price >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL OR course.price <= CAST(:maxPrice AS numeric))
            """;

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    List<Course> findByTeacher_IdAndStatusOrderByCreatedAtDesc(UUID teacherId, CourseStatus status);

    List<Course> findByTeacher_IdAndStatusOrderByPublishedAtDesc(UUID teacherId, CourseStatus status);

    long countByTeacher_IdAndStatus(UUID teacherId, CourseStatus status);

    List<Course> findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(UUID teacherId, CourseStatus status);

    Optional<Course> findByIdAndTeacher_IdAndStatus(UUID id, UUID teacherId, CourseStatus status);

    Optional<Course> findByIdAndTeacher_IdAndStatusIn(
            UUID id,
            UUID teacherId,
            java.util.Collection<CourseStatus> statuses
    );

    Optional<Course> findByIdAndTeacher_Id(UUID id, UUID teacherId);

    Optional<Course> findByIdAndStatus(UUID id, CourseStatus status);

    Optional<Course> findBySlugAndStatus(String slug, CourseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT course
            FROM Course course
            JOIN FETCH course.teacher teacher
            JOIN FETCH teacher.user
            WHERE course.id = :courseId
            """)
    Optional<Course> findByIdForModeration(@Param("courseId") UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT course
            FROM Course course
            JOIN FETCH course.teacher teacher
            JOIN FETCH teacher.user
            WHERE course.id = :courseId
            """)
    Optional<Course> findByIdForApprovalReview(@Param("courseId") UUID courseId);

    List<Course> findAllByStatusInOrderBySubmittedAtDesc(java.util.Collection<CourseStatus> statuses);

    List<Course> findAllByStatusOrderBySubmittedAtAsc(CourseStatus status);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT CASE WHEN COUNT(e.id) > 0 THEN true ELSE false END
            FROM enrollments e
            JOIN student_profiles sp ON e.student_id = sp.id
            WHERE e.course_id = :courseId
              AND sp.user_id = :userId
              AND e.enrollment_status IN ('ACTIVE', 'COMPLETED')
              AND (e.expires_at IS NULL OR e.expires_at > CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    boolean checkEnrollmentExists(@org.springframework.data.repository.query.Param("courseId") UUID courseId, @org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT CASE WHEN COUNT(e.id) > 0 THEN true ELSE false END
            FROM enrollments e
            JOIN student_profiles sp ON e.student_id = sp.id
            WHERE e.course_id = :courseId
              AND sp.user_id = :userId
              AND (
                    e.enrollment_status = 'EXPIRED'
                    OR (
                        e.enrollment_status IN ('ACTIVE', 'COMPLETED')
                        AND e.expires_at IS NOT NULL
                        AND e.expires_at <= CURRENT_TIMESTAMP
                    )
              )
            """, nativeQuery = true)
    boolean checkExpiredEnrollmentExists(
            @org.springframework.data.repository.query.Param("courseId") UUID courseId,
            @org.springframework.data.repository.query.Param("userId") UUID userId
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"teacher.user", "modules"})
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"teacher.user", "modules"})
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Course c WHERE c.slug = :slug")
    Optional<Course> findBySlugWithDetails(@org.springframework.data.repository.query.Param("slug") String slug);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) FROM course_lesson_blocks clb " +
            "JOIN course_modules cm ON clb.module_id = cm.id " +
            "WHERE cm.course_id = :courseId", nativeQuery = true)
    int countLessonBlocksByCourseId(@org.springframework.data.repository.query.Param("courseId") UUID courseId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS (" +
            "SELECT 1 FROM final_tests " +
            "WHERE course_id = :courseId)", nativeQuery = true)
    boolean hasFinalTestByCourseId(@org.springframework.data.repository.query.Param("courseId") UUID courseId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS (" +
            "SELECT 1 FROM internal_admin_accounts admin " +
            "JOIN internal_admin_roles admin_role ON admin_role.admin_account_id = admin.id " +
            "JOIN roles role ON role.id = admin_role.role_id " +
            "WHERE admin.id = :adminId AND role.code IN (:roleCodes) AND admin.account_status = 'ACTIVE')", nativeQuery = true)
    boolean hasAdminRole(@org.springframework.data.repository.query.Param("adminId") UUID adminId,
            @org.springframework.data.repository.query.Param("roleCodes") java.util.Collection<String> roleCodes);

    /**
     * Ranks the complete filtered public catalogue by current valid
     * enrolments before applying LIMIT/OFFSET. The fixed SQL order is an
     * intentional whitelist: no request-controlled identifier is interpolated.
     */
    @Query(
            value = PUBLIC_COURSE_RANK_SELECT + """
                    ORDER BY COUNT(DISTINCT enrollment.id) DESC,
                             CASE WHEN course.published_at IS NULL THEN 1 ELSE 0 END,
                             course.published_at DESC,
                             course.id ASC
                    """,
            countQuery = PUBLIC_COURSE_RANK_COUNT,
            nativeQuery = true
    )
    Page<PublicCourseRankProjection> findPublicCoursesRankedByEnrollments(
            @Param("keywordPattern") String keywordPattern,
            @Param("category") String category,
            @Param("jlptLevel") String jlptLevel,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable
    );

    /**
     * Ranks by approved-review average, then review volume. Enrolment volume,
     * publication time and UUID make ties deterministic across page requests.
     */
    @Query(
            value = PUBLIC_COURSE_RANK_SELECT + """
                    ORDER BY COALESCE(AVG(review.rating), 0) DESC,
                             COUNT(DISTINCT review.id) DESC,
                             COUNT(DISTINCT enrollment.id) DESC,
                             CASE WHEN course.published_at IS NULL THEN 1 ELSE 0 END,
                             course.published_at DESC,
                             course.id ASC
                    """,
            countQuery = PUBLIC_COURSE_RANK_COUNT,
            nativeQuery = true
    )
    Page<PublicCourseRankProjection> findPublicCoursesRankedByRating(
            @Param("keywordPattern") String keywordPattern,
            @Param("category") String category,
            @Param("jlptLevel") String jlptLevel,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable
    );

    @Query(value = """
            SELECT course.id AS "courseId",
                   course.title AS title,
                   course.slug AS slug,
                   course.thumbnail_url AS "thumbnailUrl",
                   course.level_code AS "jlptLevel",
                   course.learning_language AS "learningLanguage",
                   course.proficiency_framework AS "proficiencyFramework",
                   course.framework_version AS "frameworkVersion",
                   course.proficiency_level AS "proficiencyLevel",
                   course.category AS category,
                   course.price AS price,
                   course.currency AS currency,
                   teacher.id AS "teacherId",
                   COALESCE(teacher.display_name, app_user.full_name) AS "teacherName",
                   app_user.avatar_url AS "teacherAvatarUrl",
                   course.published_at AS "publishedAt"
            FROM courses course
            JOIN teacher_profiles teacher ON teacher.id = course.teacher_id
            JOIN app_users app_user ON app_user.id = teacher.user_id
            WHERE course.status = 'PUBLISHED'
              AND course.id IN (:courseIds)
            """, nativeQuery = true)
    List<PublicCourseCardProjection> findPublicCourseCardsByIds(@Param("courseIds") java.util.Collection<UUID> courseIds);

    @Query(value = """
            SELECT course.id AS "courseId",
                   COUNT(block.id) AS "totalLessons"
            FROM courses course
            LEFT JOIN course_modules module ON module.course_id = course.id
            LEFT JOIN course_lesson_blocks block
                   ON block.module_id = module.id
                  AND block.moderation_hidden = FALSE
            WHERE course.status = 'PUBLISHED'
              AND course.id IN (:courseIds)
            GROUP BY course.id
            """, nativeQuery = true)
    List<PublicCourseLessonCountProjection> countVisibleLessonsForPublicCourses(
            @Param("courseIds") java.util.Collection<UUID> courseIds
    );

}
