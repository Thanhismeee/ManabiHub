package com.manabihub.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "weekly_learning_challenge_pairs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyLearningChallengePair {
    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(nullable = false, length = 120)
    private String prompt;

    @Column(nullable = false, length = 240)
    private String answer;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
