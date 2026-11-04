package com.manabihub.learning.repository;

import com.manabihub.learning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    List<QuizAttempt> findByEnrollmentId(UUID enrollmentId);

    List<QuizAttempt> findByEnrollmentIdAndLessonBlockIdOrderByCreatedAtDesc(
            UUID enrollmentId,
            UUID lessonBlockId
    );
}
