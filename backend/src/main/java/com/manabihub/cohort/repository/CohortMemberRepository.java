package com.manabihub.cohort.repository;

import com.manabihub.cohort.domain.CohortMember;
import com.manabihub.cohort.domain.CohortMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CohortMemberRepository extends JpaRepository<CohortMember, UUID> {

    Optional<CohortMember> findByCohort_IdAndStudent_Id(UUID cohortId, UUID studentId);

    long countByCohort_IdAndStatus(UUID cohortId, CohortMemberStatus status);

    List<CohortMember> findByStudent_IdAndCohort_Course_IdAndStatus(UUID studentId, UUID courseId, CohortMemberStatus status);
}
