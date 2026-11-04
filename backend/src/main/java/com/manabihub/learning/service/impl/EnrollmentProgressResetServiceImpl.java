package com.manabihub.learning.service.impl;

import com.manabihub.learning.entity.Enrollment;
import com.manabihub.course.entity.Course;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.service.EnrollmentProgressResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class EnrollmentProgressResetServiceImpl implements EnrollmentProgressResetService {

    private final JdbcTemplate jdbcTemplate;
    private final EnrollmentRepository enrollmentRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetForRepurchase(Enrollment enrollment) {
        UUID enrollmentId = enrollment.getId();

        // AI usage is an audit/billing record, so keep it but detach it from the
        // writing submission that is about to be removed.
        jdbcTemplate.update("""
                UPDATE ai_usage_logs
                SET writing_submission_id = NULL
                WHERE writing_submission_id IN (
                    SELECT id FROM writing_submissions WHERE enrollment_id = ?
                )
                """, enrollmentId);
        jdbcTemplate.update("""
                DELETE FROM teacher_writing_feedback
                WHERE writing_submission_id IN (
                    SELECT id FROM writing_submissions WHERE enrollment_id = ?
                )
                """, enrollmentId);
        jdbcTemplate.update("""
                DELETE FROM ai_writing_suggestions
                WHERE writing_submission_id IN (
                    SELECT id FROM writing_submissions WHERE enrollment_id = ?
                )
                """, enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM writing_submissions WHERE enrollment_id = ?", enrollmentId);

        jdbcTemplate.update(
                "DELETE FROM learning_certificates WHERE enrollment_id = ?", enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM final_test_attempts WHERE enrollment_id = ?", enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM quiz_attempts WHERE enrollment_id = ?", enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM flashcard_progress WHERE enrollment_id = ?", enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM lesson_block_progress WHERE enrollment_id = ?", enrollmentId);
        jdbcTemplate.update(
                "DELETE FROM lesson_progress WHERE enrollment_id = ?", enrollmentId);

        enrollment.setCompletedAt(null);
        enrollment.setProtectedMaterialsFullyDownloadedAt(null);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        // The course association may be intentionally lazy/detached when this
        // service is called (and in a few command-line/test flows). Keep the
        // repurchase reset safe in that case while preserving the course policy
        // whenever the association is available.
        Course course = enrollment.getCourse();
        Instant repurchaseAt = Instant.now();
        enrollment.setExpiresAt(course == null
                ? repurchaseAt.plus(180, ChronoUnit.DAYS)
                : course.resolveEnrollmentExpiry(repurchaseAt));
        enrollmentRepository.save(enrollment);
    }
}
