package com.manabihub.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weekly_learning_challenge_rewards")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyLearningChallengeReward {
    @Id private UUID id;
    @Column(name = "challenge_id", nullable = false) private UUID challengeId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "rank_position", nullable = false) private int rankPosition;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "wallet_transaction_id") private UUID walletTransactionId;
    @Column(name = "awarded_at", nullable = false) private Instant awardedAt;
}
