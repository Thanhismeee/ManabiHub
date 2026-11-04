package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.WeeklyLearningChallengeAttemptCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WeeklyLearningChallengeAttemptCardRepository extends JpaRepository<WeeklyLearningChallengeAttemptCard, UUID> {
    List<WeeklyLearningChallengeAttemptCard> findByAttemptIdOrderByPosition(UUID attemptId);
    List<WeeklyLearningChallengeAttemptCard> findByIdInAndAttemptId(Collection<UUID> ids, UUID attemptId);
}
