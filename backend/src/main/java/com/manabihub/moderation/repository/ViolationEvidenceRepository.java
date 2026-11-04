package com.manabihub.moderation.repository;

import com.manabihub.moderation.entity.ViolationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViolationEvidenceRepository extends JpaRepository<ViolationEvidence, UUID> {

    List<ViolationEvidence> findByViolationReport_IdOrderByCreatedAtAsc(UUID reportId);

    Optional<ViolationEvidence> findByIdAndViolationReport_Id(UUID evidenceId, UUID reportId);
}
