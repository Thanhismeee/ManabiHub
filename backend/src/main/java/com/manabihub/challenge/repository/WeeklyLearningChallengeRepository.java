package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.enums.ChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface WeeklyLearningChallengeRepository extends JpaRepository<WeeklyLearningChallenge, UUID> {
    List<WeeklyLearningChallenge> findAllByOrderByWeekStartDesc();
    Optional<WeeklyLearningChallenge> findByWeekStart(LocalDate weekStart);
    Optional<WeeklyLearningChallenge> findByWeekStartAndStatus(LocalDate weekStart, ChallengeStatus status);
    List<WeeklyLearningChallenge> findByStatusAndWeekStartLessThanEqualAndSettledAtIsNull(
            ChallengeStatus status, LocalDate latestWeekStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from WeeklyLearningChallenge challenge where challenge.id = :id")
    Optional<WeeklyLearningChallenge> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from WeeklyLearningChallenge challenge where challenge.weekStart = :weekStart")
    Optional<WeeklyLearningChallenge> findByWeekStartForUpdate(@Param("weekStart") LocalDate weekStart);
}
