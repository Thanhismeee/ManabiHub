package com.manabihub.cohort.repository;

import com.manabihub.cohort.domain.CohortAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CohortAssignmentRepository extends JpaRepository<CohortAssignment, UUID> {
    List<CohortAssignment> findByCohort_IdOrderByDueAtAsc(UUID cohortId);
}
