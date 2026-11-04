package com.manabihub.challenge.entity;

import com.manabihub.challenge.enums.ChallengeAttemptState;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekly_learning_challenge_attempts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyLearningChallengeAttempt {
    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeAttemptState state;

    @Column(nullable = false)
    private boolean ranked;

    @Column(name = "ranked_day", nullable = false)
    private LocalDate rankedDay;

    @Column(name = "matched_pairs", nullable = false)
    private int matchedPairs;

    @Column(name = "penalty_millis", nullable = false)
    private long penaltyMillis;

    @Column(name = "total_millis")
    private Long totalMillis;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
