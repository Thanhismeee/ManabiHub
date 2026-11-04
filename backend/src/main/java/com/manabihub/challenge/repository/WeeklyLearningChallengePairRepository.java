package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.WeeklyLearningChallengePair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.*;

public interface WeeklyLearningChallengePairRepository extends JpaRepository<WeeklyLearningChallengePair, UUID> {
    List<WeeklyLearningChallengePair> findByChallengeIdOrderByOrderIndex(UUID challengeId);
    long countByChallengeId(UUID challengeId);
    @Modifying
    void deleteByChallengeId(UUID challengeId);
}
