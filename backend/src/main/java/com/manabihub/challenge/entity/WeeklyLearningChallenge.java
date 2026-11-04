package com.manabihub.challenge.entity;

import com.manabihub.challenge.enums.ChallengeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekly_learning_challenges")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyLearningChallenge {
    @Id
    private UUID id;

    @Column(name = "week_start", nullable = false, unique = true)
    private LocalDate weekStart;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "jlpt_level", nullable = false, length = 10)
    private String jlptLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeStatus status;

    @Column(name = "daily_ranked_limit", nullable = false)
    private int dailyRankedLimit;

    @Column(name = "wrong_penalty_seconds", nullable = false)
    private int wrongPenaltySeconds;

    @Column(name = "daily_attendance_reward", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyAttendanceReward;

    @Column(name = "first_prize", nullable = false, precision = 12, scale = 2)
    private BigDecimal firstPrize;

    @Column(name = "second_prize", nullable = false, precision = 12, scale = 2)
    private BigDecimal secondPrize;

    @Column(name = "third_prize", nullable = false, precision = 12, scale = 2)
    private BigDecimal thirdPrize;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
