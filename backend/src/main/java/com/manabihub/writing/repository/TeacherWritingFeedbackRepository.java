package com.manabihub.writing.repository;

import com.manabihub.writing.entity.TeacherWritingFeedback;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherWritingFeedbackRepository extends JpaRepository<TeacherWritingFeedback, UUID> {

    @EntityGraph(attributePaths = "teacher")
    Optional<TeacherWritingFeedback> findFirstByWritingSubmission_IdOrderByCreatedAtDesc(UUID submissionId);
}
