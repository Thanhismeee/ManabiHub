package com.manabihub.cohort.repository;

import com.manabihub.cohort.domain.CohortSubmission;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CohortSubmissionRepository extends JpaRepository<CohortSubmission, UUID> {
    @EntityGraph(attributePaths = {"assignment", "member", "member.student", "member.student.user"})
    List<CohortSubmission> findByAssignment_Cohort_IdOrderBySubmittedAtDesc(UUID cohortId);
    @EntityGraph(attributePaths = {"assignment", "member", "member.student", "member.student.user"})
    List<CohortSubmission> findByMember_IdOrderBySubmittedAtDesc(UUID memberId);
    List<CohortSubmission> findByAssignment_IdAndMember_IdOrderByAttemptDesc(UUID assignmentId, UUID memberId);
    long countByMember_Id(UUID memberId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CohortSubmission s where s.id = :id")
    Optional<CohortSubmission> findForUpdate(@Param("id") UUID id);
}
