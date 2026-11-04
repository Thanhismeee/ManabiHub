package com.manabihub.writing.repository;

import com.manabihub.writing.entity.AiWritingSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiWritingSuggestionRepository extends JpaRepository<AiWritingSuggestion, UUID> {
    Optional<AiWritingSuggestion> findFirstByWritingSubmission_IdOrderByCreatedAtDesc(UUID submissionId);
}
