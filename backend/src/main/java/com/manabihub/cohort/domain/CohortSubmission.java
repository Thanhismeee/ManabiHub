package com.manabihub.cohort.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/** Each attempt is immutable; feedback is attached once and revisions create a new row. */
@Entity
@Table(name = "cohort_submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "member_id", "attempt"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CohortSubmission {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignment_id", nullable = false) private CohortAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id", nullable = false) private CohortMember member;
    @Column(nullable = false) private int attempt;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(name = "audio_path", length = 1024) private String audioPath;
    @Column(name = "audio_mime_type", length = 80) private String audioMimeType;
    @Column(name = "audio_bytes") private Long audioBytes;
    @Column(name = "audio_sha256", length = 64) private String audioSha256;
    @Column(name = "reported_audio_seconds") private Integer reportedAudioSeconds;
    @Column(name = "verified_audio_seconds") private Integer verifiedAudioSeconds;
    @CreationTimestamp @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "review_due_at", nullable = false) private Instant reviewDueAt;
    @Column(columnDefinition = "TEXT") private String feedback;
    private Integer score;
    @Builder.Default @Column(name = "revision_requested", nullable = false) private boolean revisionRequested = false;
    @Column(name = "review_minutes") private Integer reviewMinutes;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
}
