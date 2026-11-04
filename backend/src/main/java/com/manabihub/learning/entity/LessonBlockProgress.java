package com.manabihub.learning.entity;

import com.manabihub.learning.enums.LessonProgressStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_block_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lesson_block_progress_enrollment_block", columnNames = {"enrollment_id", "lesson_block_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonBlockProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_block_id", nullable = false)
    private UUID lessonBlockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private LessonProgressStatus status;

    @Column(name = "last_video_position_seconds")
    private Integer lastVideoPositionSeconds;

    @Builder.Default
    @Column(name = "watched_video_seconds", nullable = false)
    private Integer watchedVideoSeconds = 0;

    /** Duration observed from player metadata; lesson durationMinutes is editorial. */
    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
