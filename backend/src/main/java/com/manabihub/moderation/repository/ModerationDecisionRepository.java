package com.manabihub.moderation.repository;

import com.manabihub.moderation.entity.ModerationDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ModerationDecisionRepository extends JpaRepository<ModerationDecision, UUID> {
    List<ModerationDecision> findByViolationReportIdOrderByCreatedAtDesc(UUID reportId);
}
