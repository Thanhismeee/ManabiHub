package com.manabihub.learning.entity;

import com.manabihub.learning.enums.FlashcardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flashcard_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"enrollment_id", "lesson_block_id", "card_index"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardProgress {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_block_id", nullable = false)
    private UUID lessonBlockId;

    @Column(name = "card_index", nullable = false)
    private int cardIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FlashcardStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
