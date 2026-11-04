package com.manabihub.cohort.repository;

import com.manabihub.cohort.domain.LearningCohort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningCohortRepository extends JpaRepository<LearningCohort, UUID> {

    @EntityGraph(attributePaths = {"course", "members", "members.student", "members.student.user"})
    List<LearningCohort> findByOwnerTeacher_User_IdOrderByStartDateDesc(UUID userId);

    @EntityGraph(attributePaths = {"course", "members", "members.student", "members.student.user"})
    Optional<LearningCohort> findByIdAndOwnerTeacher_User_Id(UUID id, UUID userId);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LearningCohort c where c.id = :id")
    Optional<LearningCohort> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"course", "ownerTeacher", "ownerTeacher.user", "members", "members.student", "members.student.user"})
    List<LearningCohort> findAllByOrderByStartDateDesc();

    @EntityGraph(attributePaths = {"course"})
    @Query("select c from LearningCohort c where exists (select m.id from CohortMember m where m.cohort = c and m.student.user.id = :userId and m.status = com.manabihub.cohort.domain.CohortMemberStatus.ACTIVE) order by c.startDate desc")
    List<LearningCohort> findForStudent(@Param("userId") UUID userId);
}
