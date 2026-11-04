package com.manabihub.review;

import com.manabihub.review.dto.response.CourseReviewAggregateResponse;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.repository.CourseReviewRepository;
import com.manabihub.review.service.CourseReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CourseReviewPostgresIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine");
        postgresContainer.start();
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CourseReviewService courseReviewService;
    @Autowired
    private CourseReviewRepository courseReviewRepository;

    @Test
    void teacherReplyLockQueryLoadsReviewOnPostgres() {
        Fixture fixture = transactionTemplate.execute(status -> createBaseFixture());
        assertNotNull(fixture);
        UUID reviewId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO course_reviews
                    (id, enrollment_id, rating, review_text, review_status)
                VALUES (?, ?, 5, ?, 'APPROVED')
                """,
                reviewId,
                fixture.activeEnrollmentId(),
                "Bình luận cần giảng viên phản hồi."
        );

        Boolean loaded = transactionTemplate.execute(status -> {
            var review = courseReviewRepository.findByIdForTeacherReply(reviewId).orElseThrow();
            assertEquals(reviewId, review.getId());
            assertNotNull(review.getEnrollment().getCourse().getTeacher().getUser());
            assertNotNull(review.getEnrollment().getStudent().getUser());
            return true;
        });

        assertTrue(Boolean.TRUE.equals(loaded));
    }

    @Test
    void publicQueriesIncludeOnlyApprovedReviewsFromEligibleEnrollments() {
        Fixture fixture = transactionTemplate.execute(status -> createFixture());
        assertNotNull(fixture);

        Page<CourseReviewResponse> page = courseReviewService.getPublicReviews(
                fixture.courseSlug(),
                PageRequest.of(0, 10)
        );
        CourseReviewAggregateResponse aggregate =
                courseReviewService.getAggregate(fixture.courseId());

        assertEquals(1, page.getTotalElements());
        assertEquals("Public Student", page.getContent().getFirst().authorDisplayName());
        assertFalse(page.getContent().getFirst().reviewText().contains("private@example.test"));
        assertEquals(0, aggregate.averageRating().compareTo(new java.math.BigDecimal("5.0")));
        assertEquals(1, aggregate.reviewCount());
    }

    @Test
    void concurrentInsertsCannotCreateDuplicateReviewsForOneEnrollment() throws Exception {
        Fixture fixture = transactionTemplate.execute(status -> createBaseFixture());
        assertNotNull(fixture);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                int rating = 4 + index;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        jdbcTemplate.update(
                                """
                                INSERT INTO course_reviews
                                    (id, enrollment_id, rating, review_text, review_status)
                                VALUES (?, ?, ?, ?, 'APPROVED')
                                """,
                                UUID.randomUUID(),
                                fixture.activeEnrollmentId(),
                                rating,
                                "Nội dung đồng thời vẫn phải duy nhất."
                        );
                        return true;
                    } catch (DataIntegrityViolationException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }

            assertEquals(1, successes);
            Integer stored = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM course_reviews WHERE enrollment_id = ?",
                    Integer.class,
                    fixture.activeEnrollmentId()
            );
            assertEquals(1, stored);
        }
    }

    private Fixture createFixture() {
        Fixture fixture = createBaseFixture();
        jdbcTemplate.update(
                """
                INSERT INTO course_reviews
                    (id, enrollment_id, rating, review_text, review_status)
                VALUES (?, ?, 5, ?, 'APPROVED')
                """,
                UUID.randomUUID(),
                fixture.activeEnrollmentId(),
                "Khóa học thực tế và rất dễ hiểu."
        );
        jdbcTemplate.update(
                """
                INSERT INTO course_reviews
                    (id, enrollment_id, rating, review_text, review_status)
                VALUES (?, ?, 1, ?, 'APPROVED')
                """,
                UUID.randomUUID(),
                fixture.refundedEnrollmentId(),
                "Review đã hoàn tiền không được công khai."
        );
        jdbcTemplate.update(
                """
                INSERT INTO course_reviews
                    (id, enrollment_id, rating, review_text, review_status)
                VALUES (?, ?, 2, ?, 'HIDDEN')
                """,
                UUID.randomUUID(),
                fixture.hiddenEnrollmentId(),
                "Review ẩn không được tính vào tổng hợp."
        );
        return fixture;
    }

    private Fixture createBaseFixture() {
        UUID teacherUserId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        String courseSlug = "review-course-" + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, full_name) VALUES (?, ?, ?)",
                teacherUserId,
                "teacher-" + UUID.randomUUID() + "@example.test",
                "Teacher Legal Name"
        );
        jdbcTemplate.update(
                """
                INSERT INTO teacher_profiles
                    (id, user_id, display_name, kyc_status, can_publish_course)
                VALUES (?, ?, 'Public Teacher', 'APPROVED', TRUE)
                """,
                teacherId,
                teacherUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO courses
                    (id, teacher_id, title, slug, price, currency, status, published_at)
                VALUES (?, ?, 'Verified Review Course', ?, 100000, 'VND', 'PUBLISHED', NOW())
                """,
                courseId,
                teacherId,
                courseSlug
        );

        UUID activeEnrollmentId = createEnrollment(
                courseId,
                "ACTIVE",
                "Public Student"
        );
        UUID refundedEnrollmentId = createEnrollment(
                courseId,
                "REFUNDED",
                "Refunded Student"
        );
        UUID hiddenEnrollmentId = createEnrollment(
                courseId,
                "COMPLETED",
                "Hidden Student"
        );

        return new Fixture(
                courseId,
                courseSlug,
                activeEnrollmentId,
                refundedEnrollmentId,
                hiddenEnrollmentId
        );
    }

    private UUID createEnrollment(
            UUID courseId,
            String enrollmentStatus,
            String displayName
    ) {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO app_users
                    (id, email, full_name, avatar_url)
                VALUES (?, ?, 'Private Legal Name', '/avatars/public.png')
                """,
                userId,
                "private-" + UUID.randomUUID() + "@example.test"
        );
        jdbcTemplate.update(
                """
                INSERT INTO student_profiles (id, user_id, display_name)
                VALUES (?, ?, ?)
                """,
                studentId,
                userId,
                displayName
        );
        jdbcTemplate.update(
                """
                INSERT INTO enrollments
                    (id, student_id, course_id, enrollment_status)
                VALUES (?, ?, ?, ?)
                """,
                enrollmentId,
                studentId,
                courseId,
                enrollmentStatus
        );
        return enrollmentId;
    }

    private record Fixture(
            UUID courseId,
            String courseSlug,
            UUID activeEnrollmentId,
            UUID refundedEnrollmentId,
            UUID hiddenEnrollmentId
    ) {
    }
}
