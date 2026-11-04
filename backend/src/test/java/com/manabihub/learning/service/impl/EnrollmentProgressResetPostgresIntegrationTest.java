package com.manabihub.learning.service.impl;

import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.service.EnrollmentProgressResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class EnrollmentProgressResetPostgresIntegrationTest {

    private static final UUID ENROLLMENT_ID =
            UUID.fromString("f4000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID =
            UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_LESSON_ID =
            UUID.fromString("f2000000-0000-0000-0000-000000000001");
    private static final UUID WRITING_SUBMISSION_ID =
            UUID.fromString("f5000000-0000-0000-0000-000000000001");

    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private EnrollmentProgressResetService resetService;

    @Test
    void resetForRepurchase_removesPersistedProgressAndKeepsAuditHistory() {
        UUID moduleId = UUID.randomUUID();
        UUID lessonBlockId = UUID.randomUUID();
        UUID finalTestId = UUID.randomUUID();
        UUID aiUsageLogId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            createLearningArtifacts(moduleId, lessonBlockId, finalTestId, aiUsageLogId);

            Enrollment enrollment = enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID)
                    .orElseThrow();
            enrollment.setStatus(EnrollmentStatus.REFUNDED);
            enrollment.setCompletedAt(Instant.parse("2026-07-01T00:00:00Z"));
            enrollment.setProtectedMaterialsFullyDownloadedAt(
                    Instant.parse("2026-07-02T00:00:00Z"));

            resetService.resetForRepurchase(enrollment);
        });

        assertEquals(0, countForEnrollment("lesson_progress"));
        assertEquals(0, countForEnrollment("lesson_block_progress"));
        assertEquals(0, countForEnrollment("flashcard_progress"));
        assertEquals(0, countForEnrollment("quiz_attempts"));
        assertEquals(0, countForEnrollment("final_test_attempts"));
        assertEquals(0, countForEnrollment("learning_certificates"));
        assertEquals(0, countForEnrollment("writing_submissions"));
        assertEquals(0, countForWritingSubmission("ai_writing_suggestions"));
        assertEquals(0, countForWritingSubmission("teacher_writing_feedback"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_usage_logs WHERE id = ?", Integer.class, aiUsageLogId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT writing_submission_id FROM ai_usage_logs WHERE id = ?",
                UUID.class,
                aiUsageLogId));

        Enrollment resetEnrollment = enrollmentRepository.findById(ENROLLMENT_ID).orElseThrow();
        assertEquals(EnrollmentStatus.ACTIVE, resetEnrollment.getStatus());
        assertNull(resetEnrollment.getCompletedAt());
        assertNull(resetEnrollment.getProtectedMaterialsFullyDownloadedAt());
    }

    private void createLearningArtifacts(
            UUID moduleId,
            UUID lessonBlockId,
            UUID finalTestId,
            UUID aiUsageLogId
    ) {
        jdbcTemplate.update("""
                INSERT INTO course_modules (id, course_id, title, order_index)
                VALUES (?, ?, 'Repurchase reset module', 999)
                """, moduleId, COURSE_ID);
        jdbcTemplate.update("""
                INSERT INTO course_lesson_blocks
                    (id, module_id, block_type, title, content, order_index)
                VALUES (?, ?, 'TEXT', 'Reset block', 'Content', 1)
                """, lessonBlockId, moduleId);
        jdbcTemplate.update("""
                INSERT INTO final_tests
                    (id, course_id, time_limit_minutes, passing_score, max_retakes,
                     jlpt_level, skill_focus)
                VALUES (?, ?, 30, 70, 3, 'N3', 'ALL')
                """, finalTestId, COURSE_ID);

        jdbcTemplate.update("""
                INSERT INTO lesson_progress
                    (id, enrollment_id, lesson_id, status, progress_percent, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', 100, NOW())
                """, UUID.randomUUID(), ENROLLMENT_ID, LEGACY_LESSON_ID);
        jdbcTemplate.update("""
                INSERT INTO lesson_block_progress
                    (id, enrollment_id, lesson_block_id, status,
                     last_video_position_seconds, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', 120, NOW())
                """, UUID.randomUUID(), ENROLLMENT_ID, lessonBlockId);
        jdbcTemplate.update("""
                INSERT INTO flashcard_progress
                    (id, enrollment_id, lesson_block_id, card_index, status)
                VALUES (?, ?, ?, 0, 'REMEMBERED')
                """, UUID.randomUUID(), ENROLLMENT_ID, lessonBlockId);
        jdbcTemplate.update("""
                INSERT INTO quiz_attempts
                    (id, enrollment_id, lesson_block_id, score, passed, answers_json)
                VALUES (?, ?, ?, 100, TRUE, '{}'::jsonb)
                """, UUID.randomUUID(), ENROLLMENT_ID, lessonBlockId);
        jdbcTemplate.update("""
                INSERT INTO final_test_attempts
                    (id, enrollment_id, final_test_id, score, passed, answers_json,
                     status, started_at, submitted_at)
                VALUES (?, ?, ?, 100, TRUE, '{}'::jsonb,
                        'SUBMITTED', NOW(), NOW())
                """, UUID.randomUUID(), ENROLLMENT_ID, finalTestId);
        jdbcTemplate.update("""
                INSERT INTO learning_certificates
                    (id, enrollment_id, certificate_number, student_name,
                     course_title, eligibility_snapshot)
                VALUES (?, ?, ?, 'Reset Student', 'Reset Course', '{}'::jsonb)
                """, UUID.randomUUID(), ENROLLMENT_ID, "RESET-" + UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO ai_usage_logs
                    (id, writing_submission_id, feature_code, request_status)
                VALUES (?, ?, 'AI_WRITING_ASSISTANCE', 'SUCCESS')
                """, aiUsageLogId, WRITING_SUBMISSION_ID);
    }

    private int countForEnrollment(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE enrollment_id = ?",
                Integer.class,
                ENROLLMENT_ID);
    }

    private int countForWritingSubmission(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE writing_submission_id = ?",
                Integer.class,
                WRITING_SUBMISSION_ID);
    }
}
