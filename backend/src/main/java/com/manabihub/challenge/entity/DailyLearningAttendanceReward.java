package com.manabihub.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_learning_attendance_rewards")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyLearningAttendanceReward {
    @Id private UUID id;
    @Column(name = "reward_date", nullable = false) private LocalDate rewardDate;
    @Column(name = "challenge_id", nullable = false) private UUID challengeId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "wallet_transaction_id") private UUID walletTransactionId;
    @Column(name = "awarded_at", nullable = false) private Instant awardedAt;
}
