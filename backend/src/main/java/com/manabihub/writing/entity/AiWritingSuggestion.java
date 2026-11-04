package com.manabihub.writing.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_writing_suggestions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWritingSuggestion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_submission_id", nullable = false)
    private WritingSubmission writingSubmission;

    @Column(length = 100)
    private String provider;

    @Column(name = "suggestion_status", nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grammar_suggestions", nullable = false, columnDefinition = "jsonb")
    private JsonNode grammarSuggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vocabulary_suggestions", nullable = false, columnDefinition = "jsonb")
    private JsonNode vocabularySuggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structure_suggestions", nullable = false, columnDefinition = "jsonb")
    private JsonNode structureSuggestions;

    @Column(name = "revision_guidance", columnDefinition = "TEXT")
    private String revisionGuidance;

    @Column(name = "confidence_level", length = 30)
    private String confidenceLevel;

    @Column(name = "is_official", nullable = false)
    private boolean official;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
