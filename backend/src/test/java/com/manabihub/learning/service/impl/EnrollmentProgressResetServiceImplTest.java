package com.manabihub.learning.service.impl;

import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentProgressResetServiceImplTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EnrollmentRepository enrollmentRepository;

    private EnrollmentProgressResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentProgressResetServiceImpl(jdbcTemplate, enrollmentRepository);
    }

    @Test
    void resetForRepurchase_clearsAllLearningArtifactsAndStartsEnrollmentFromZero() {
        Enrollment enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .status(EnrollmentStatus.REFUNDED)
                .completedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .protectedMaterialsFullyDownloadedAt(Instant.parse("2026-07-02T00:00:00Z"))
                .build();

        service.resetForRepurchase(enrollment);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(10)).update(sqlCaptor.capture(), eq(enrollment.getId()));
        List<String> statements = sqlCaptor.getAllValues();
        assertContainsTable(statements, "ai_usage_logs");
        assertContainsTable(statements, "teacher_writing_feedback");
        assertContainsTable(statements, "ai_writing_suggestions");
        assertContainsTable(statements, "writing_submissions");
        assertContainsTable(statements, "learning_certificates");
        assertContainsTable(statements, "final_test_attempts");
        assertContainsTable(statements, "quiz_attempts");
        assertContainsTable(statements, "flashcard_progress");
        assertContainsTable(statements, "lesson_block_progress");
        assertContainsTable(statements, "lesson_progress");

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        assertNull(enrollment.getCompletedAt());
        assertNull(enrollment.getProtectedMaterialsFullyDownloadedAt());
        verify(enrollmentRepository).save(enrollment);
    }

    private void assertContainsTable(List<String> statements, String tableName) {
        assertTrue(statements.stream().anyMatch(sql -> sql.contains(tableName)),
                () -> "Missing reset statement for " + tableName);
    }
}
