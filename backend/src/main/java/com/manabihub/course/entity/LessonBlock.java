package com.manabihub.course.entity;

import com.manabihub.course.enums.LessonBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "course_lesson_blocks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonBlock {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 30)
    private LessonBlockType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "quiz_question", columnDefinition = "TEXT")
    private String quizQuestion;

    @Column(name = "quiz_options_json", columnDefinition = "TEXT")
    private String quizOptionsJson;

    @Column(name = "quiz_answer", columnDefinition = "TEXT")
    private String quizAnswer;

    @Column(name = "quiz_items_json", columnDefinition = "TEXT")
    private String quizItemsJson;

    @Column(name = "flashcards_json", columnDefinition = "TEXT")
    private String flashcardsJson;

    @Column(name = "writing_prompt", columnDefinition = "TEXT")
    private String writingPrompt;

    @Column(columnDefinition = "TEXT")
    private String rubric;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Builder.Default
    @Column(name = "moderation_hidden", nullable = false)
    private boolean moderationHidden = false;

    @Column(name = "moderation_hidden_at")
    private Instant moderationHiddenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
