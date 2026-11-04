package com.manabihub.course.repository;

import com.manabihub.course.repository.projection.PublicCourseRankProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@ActiveProfiles("it")
public class CourseRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("manabihub_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hasFinalTestByCourseId_returnsFalse_whenQuizBlockExistsButNoFinalTest() {
        UUID userId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO app_users (id, email, full_name, created_at, updated_at) VALUES (?, 't1@test.com', 'T1', now(), now())", userId);
        jdbcTemplate.update("INSERT INTO teacher_profiles (id, user_id, created_at, updated_at) VALUES (?, ?, now(), now())", teacherId, userId);
        jdbcTemplate.update("INSERT INTO courses (id, teacher_id, title, description, slug, status, created_at, updated_at) VALUES (?, ?, 'T', 'D', 'slug1', 'DRAFT', now(), now())", courseId, teacherId);
        jdbcTemplate.update("INSERT INTO course_modules (id, course_id, title, order_index, created_at, updated_at) VALUES (?, ?, 'M', 1, now(), now())", moduleId, courseId);
        jdbcTemplate.update("INSERT INTO course_lesson_blocks (id, module_id, title, block_type, order_index, created_at, updated_at) VALUES (?, ?, 'B', 'QUIZ', 1, now(), now())", blockId, moduleId);

        boolean result = courseRepository.hasFinalTestByCourseId(courseId);
        assertThat(result).isFalse();
    }

    @Test
    void hasFinalTestByCourseId_returnsTrue_whenFinalTestExists() {
        UUID userId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO app_users (id, email, full_name, created_at, updated_at) VALUES (?, 't2@test.com', 'T2', now(), now())", userId);
        jdbcTemplate.update("INSERT INTO teacher_profiles (id, user_id, created_at, updated_at) VALUES (?, ?, now(), now())", teacherId, userId);
        jdbcTemplate.update("INSERT INTO courses (id, teacher_id, title, description, slug, status, created_at, updated_at) VALUES (?, ?, 'T', 'D', 'slug2', 'DRAFT', now(), now())", courseId, teacherId);
        jdbcTemplate.update("INSERT INTO final_tests (id, course_id, time_limit_minutes, passing_score, max_retakes, jlpt_level, skill_focus, created_at, updated_at) VALUES (?, ?, 60, 50, 3, 'N3', 'READING', now(), now())", UUID.randomUUID(), courseId);

        boolean result = courseRepository.hasFinalTestByCourseId(courseId);
        assertThat(result).isTrue();
    }

    @Test
    void enrollmentAccessQueries_distinguishCurrentAndElapsedAccess() {
        UUID teacherId = insertTeacher("access-state");
        UUID courseId = insertCourse(
                teacherId,
                "Course access state",
                "course-access-state",
                "PUBLISHED",
                Instant.parse("2026-07-01T00:00:00Z")
        );
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, full_name, created_at, updated_at) "
                        + "VALUES (?, ?, 'Access Student', now(), now())",
                userId,
                "access-" + userId + "@test.com"
        );
        jdbcTemplate.update(
                "INSERT INTO student_profiles (id, user_id, created_at, updated_at) "
                        + "VALUES (?, ?, now(), now())",
                studentId,
                userId
        );
        jdbcTemplate.update(
                "INSERT INTO enrollments "
                        + "(id, student_id, course_id, enrollment_status, enrolled_at, expires_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now() - interval '2 days', now() - interval '1 day')",
                enrollmentId,
                studentId,
                courseId
        );

        assertThat(courseRepository.checkEnrollmentExists(courseId, userId)).isFalse();
        assertThat(courseRepository.checkExpiredEnrollmentExists(courseId, userId)).isTrue();

        jdbcTemplate.update(
                "UPDATE enrollments SET expires_at = now() + interval '1 day' WHERE id = ?",
                enrollmentId
        );

        assertThat(courseRepository.checkEnrollmentExists(courseId, userId)).isTrue();
        assertThat(courseRepository.checkExpiredEnrollmentExists(courseId, userId)).isFalse();
    }

    @Test
    void enrollmentRanking_ordersCompletePublishedCatalogueBeforePagination() {
        UUID teacherId = insertTeacher("rank-enrollment");
        UUID mostEnrolled = insertCourse(
                teacherId, "Rank enrollment most enrolled", "most-enrolled", "PUBLISHED",
                Instant.parse("2026-07-01T00:00:00Z")
        );
        UUID newerTie = insertCourse(
                teacherId, "Rank enrollment newer tied", "newer-tied", "PUBLISHED",
                Instant.parse("2026-07-03T00:00:00Z")
        );
        UUID olderTie = insertCourse(
                teacherId, "Rank enrollment older tied", "older-tied", "PUBLISHED",
                Instant.parse("2026-07-02T00:00:00Z")
        );
        UUID draft = insertCourse(
                teacherId, "Rank enrollment hidden draft", "draft-ranked", "DRAFT", null
        );

        insertEnrollments(mostEnrolled, 3, "ACTIVE");
        insertEnrollments(newerTie, 2, "ACTIVE");
        insertEnrollments(olderTie, 2, "COMPLETED");
        insertEnrollments(draft, 5, "ACTIVE");
        insertEnrollments(mostEnrolled, 1, "REFUNDED");

        Page<PublicCourseRankProjection> firstPage =
                courseRepository.findPublicCoursesRankedByEnrollments(
                        "%rank enrollment%", null, null, null, null, PageRequest.of(0, 2)
                );
        Page<PublicCourseRankProjection> secondPage =
                courseRepository.findPublicCoursesRankedByEnrollments(
                        "%rank enrollment%", null, null, null, null, PageRequest.of(1, 2)
                );

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(PublicCourseRankProjection::getCourseId)
                .containsExactly(mostEnrolled, newerTie);
        assertThat(firstPage.getContent())
                .extracting(PublicCourseRankProjection::getEnrollmentCount)
                .containsExactly(3L, 2L);
        assertThat(secondPage.getContent())
                .extracting(PublicCourseRankProjection::getCourseId)
                .containsExactly(olderTie);
    }

    @Test
    void ratingRanking_usesApprovedActiveReviewsAndDeterministicReviewVolumeTieBreak() {
        UUID teacherId = insertTeacher("rank-rating");
        UUID moreReviews = insertCourse(
                teacherId, "Course more reviews", "course-more-reviews", "PUBLISHED",
                Instant.parse("2026-07-01T00:00:00Z")
        );
        UUID oneReview = insertCourse(
                teacherId, "Course one review", "course-one-review", "PUBLISHED",
                Instant.parse("2026-07-02T00:00:00Z")
        );
        UUID lowerAverage = insertCourse(
                teacherId, "Course lower average", "course-lower-average", "PUBLISHED",
                Instant.parse("2026-07-03T00:00:00Z")
        );
        UUID draft = insertCourse(
                teacherId, "Course hidden draft", "course-hidden-draft", "DRAFT", null
        );

        insertReview(insertEnrollment(moreReviews, "ACTIVE"), 5, "APPROVED");
        insertReview(insertEnrollment(moreReviews, "COMPLETED"), 5, "APPROVED");
        insertReview(insertEnrollment(oneReview, "ACTIVE"), 5, "APPROVED");
        // A review on an inactive/refunded enrolment must not affect public metrics.
        insertReview(insertEnrollment(oneReview, "REFUNDED"), 1, "APPROVED");
        insertReview(insertEnrollment(lowerAverage, "ACTIVE"), 5, "APPROVED");
        insertReview(insertEnrollment(lowerAverage, "ACTIVE"), 4, "APPROVED");
        insertReview(insertEnrollment(draft, "ACTIVE"), 5, "APPROVED");

        Page<PublicCourseRankProjection> result = courseRepository.findPublicCoursesRankedByRating(
                "%course%",
                "KANJI",
                "N5",
                BigDecimal.ZERO,
                new BigDecimal("300000"),
                PageRequest.of(0, 2)
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(PublicCourseRankProjection::getCourseId)
                .containsExactly(moreReviews, oneReview);
        assertThat(result.getContent().get(0).getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(result.getContent().get(0).getReviewCount()).isEqualTo(2);
        assertThat(result.getContent().get(1).getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(result.getContent().get(1).getReviewCount()).isEqualTo(1);
    }

    private UUID insertTeacher(String suffix) {
        UUID userId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, full_name, created_at, updated_at) "
                        + "VALUES (?, ?, 'Ranking Teacher', now(), now())",
                userId,
                suffix + "-" + userId + "@test.com"
        );
        jdbcTemplate.update(
                "INSERT INTO teacher_profiles "
                        + "(id, user_id, display_name, created_at, updated_at) "
                        + "VALUES (?, ?, 'Ranking Teacher', now(), now())",
                teacherId,
                userId
        );
        return teacherId;
    }

    private UUID insertCourse(
            UUID teacherId,
            String title,
            String slug,
            String status,
            Instant publishedAt
    ) {
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO courses "
                        + "(id, teacher_id, title, description, slug, status, level_code, category, "
                        + " price, currency, published_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Public catalogue ranking course', ?, ?, 'N5', 'KANJI', "
                        + " 199000, 'VND', ?, now(), now())",
                courseId,
                teacherId,
                title,
                slug,
                status,
                publishedAt == null ? null : java.sql.Timestamp.from(publishedAt)
        );
        return courseId;
    }

    private void insertEnrollments(UUID courseId, int count, String status) {
        for (int index = 0; index < count; index++) {
            insertEnrollment(courseId, status);
        }
    }

    private UUID insertEnrollment(UUID courseId, String status) {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, full_name, created_at, updated_at) "
                        + "VALUES (?, ?, 'Ranking Student', now(), now())",
                userId,
                "ranking-" + userId + "@test.com"
        );
        jdbcTemplate.update(
                "INSERT INTO student_profiles (id, user_id, created_at, updated_at) "
                        + "VALUES (?, ?, now(), now())",
                studentId,
                userId
        );
        jdbcTemplate.update(
                "INSERT INTO enrollments (id, student_id, course_id, enrollment_status, enrolled_at) "
                        + "VALUES (?, ?, ?, ?, now())",
                enrollmentId,
                studentId,
                courseId,
                status
        );
        return enrollmentId;
    }

    private void insertReview(UUID enrollmentId, int rating, String status) {
        jdbcTemplate.update(
                "INSERT INTO course_reviews "
                        + "(id, enrollment_id, rating, review_text, review_status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Useful ranking review', ?, now(), now())",
                UUID.randomUUID(),
                enrollmentId,
                rating,
                status
        );
    }
}
