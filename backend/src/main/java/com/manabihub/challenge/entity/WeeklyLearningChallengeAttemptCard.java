package com.manabihub.challenge.entity;

import com.manabihub.challenge.enums.ChallengeCardKind;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "weekly_learning_challenge_attempt_cards")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyLearningChallengeAttemptCard {
    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "pair_id", nullable = false)
    private UUID pairId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_kind", nullable = false, length = 10)
    private ChallengeCardKind cardKind;

    @Column(name = "display_value", nullable = false, length = 240)
    private String displayValue;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean matched;
}
