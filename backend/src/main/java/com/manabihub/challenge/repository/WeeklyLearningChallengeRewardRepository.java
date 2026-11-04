package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.WeeklyLearningChallengeReward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WeeklyLearningChallengeRewardRepository extends JpaRepository<WeeklyLearningChallengeReward, UUID> {
    boolean existsByChallengeIdAndStudentId(UUID challengeId, UUID studentId);
    boolean existsByChallengeId(UUID challengeId);
}
