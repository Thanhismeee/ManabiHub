package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.WeeklyLearningChallengeAttempt;
import com.manabihub.challenge.enums.ChallengeAttemptState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface WeeklyLearningChallengeAttemptRepository extends JpaRepository<WeeklyLearningChallengeAttempt, UUID> {
    long countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(UUID challengeId, UUID studentId, LocalDate rankedDay);

    Optional<WeeklyLearningChallengeAttempt> findFirstByChallengeIdAndStudentIdAndStateAndRankedTrueOrderByTotalMillisAsc(
            UUID challengeId, UUID studentId, ChallengeAttemptState state);

    boolean existsByChallengeId(UUID challengeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from WeeklyLearningChallengeAttempt attempt where attempt.id = :id and attempt.studentId = :studentId")
    Optional<WeeklyLearningChallengeAttempt> findOwnedByIdForUpdate(@Param("id") UUID id, @Param("studentId") UUID studentId);

    @Query(value = """
            SELECT student_id AS studentId, MIN(total_millis) AS bestMillis
            FROM weekly_learning_challenge_attempts
            WHERE challenge_id = :challengeId AND ranked = TRUE AND state = 'COMPLETED'
            GROUP BY student_id
            ORDER BY MIN(total_millis), MIN(completed_at), student_id
            """, nativeQuery = true)
    List<BestScore> findRankedBestScores(@Param("challengeId") UUID challengeId);

    interface BestScore {
        UUID getStudentId();
        Long getBestMillis();
    }
}
