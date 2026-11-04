package com.manabihub.challenge.repository;

import com.manabihub.challenge.entity.DailyLearningAttendanceReward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.UUID;

public interface DailyLearningAttendanceRewardRepository extends JpaRepository<DailyLearningAttendanceReward, UUID> {
    boolean existsByRewardDateAndStudentId(LocalDate rewardDate, UUID studentId);
    boolean existsByChallengeId(UUID challengeId);
}
