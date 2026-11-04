package com.manabihub.cohort.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cohort_assignments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CohortAssignment {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cohort_id", nullable = false)
    private LearningCohort cohort;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AssignmentType type;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(name = "accept_until", nullable = false) private Instant acceptUntil;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "max_audio_seconds", nullable = false) private int maxAudioSeconds;
    @Builder.Default @Column(nullable = false) private boolean closed = false;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
