package com.manabihub.cohort.dto;

import com.manabihub.cohort.domain.AssignmentType;
import com.manabihub.course.enums.LearningLanguage;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import com.manabihub.cohort.domain.CohortStatus;
import java.util.List;
import java.util.UUID;

public final class CohortWorkspaceDto {
    public record CreateAssignment(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 10000) String instructions,
            @NotNull AssignmentType type,
            @NotNull Instant dueAt, @NotNull Instant acceptUntil,
            @Min(1) @Max(10) int maxAttempts,
            @Min(10) @Max(600) int maxAudioSeconds) {}
    public record Feedback(@NotBlank @Size(max = 10000) String feedback,
                           @NotNull @Min(0) @Max(100) Integer score,
                           boolean revisionRequested, @Min(1) @Max(180) int reviewMinutes) {}
    public record PackageSettings(@Min(1) @Max(200) int feedbackQuotaPerStudent,
                                  @Min(1) @Max(168) int reviewSlaHours) {}
    public record Assignment(UUID id, String title, String instructions, AssignmentType type,
                             Instant dueAt, Instant acceptUntil, int maxAttempts,
                             int maxAudioSeconds, boolean closed) {}
    public record Submission(UUID id, UUID assignmentId, UUID memberId, String studentName,
                             int attempt, String content, boolean hasAudio, Integer reportedAudioSeconds, Integer verifiedAudioSeconds,
                             Instant submittedAt, Instant reviewDueAt, String feedback, Integer score,
                             boolean revisionRequested, Integer reviewMinutes, Instant reviewedAt) {}
    public record MemberProgress(UUID memberId, String fullName, String email, int overdueAssignments,
                                 int awaitingRevision, int submittedAssignments, int feedbackUsed,
                                 int feedbackRemaining, int feedbackReserved, Double averageScore, Instant lastSubmissionAt,
                                 Instant lastReminderAt) {}
    public record Workspace(UUID cohortId, String name, String code, UUID courseId,
                            LearningLanguage learningLanguage, CohortStatus status, LocalDate startDate, LocalDate endDate,
                            boolean submissionAllowed, String submissionBlockReason, int feedbackQuotaPerStudent, int reviewSlaHours,
                            int feedbackUsed, int feedbackRemaining, List<Assignment> assignments,
                            List<Submission> submissions, List<MemberProgress> members) {}
    public record Report(UUID cohortId, String name, String code, String organizationName, String branchName,
                         String teacherName, LearningLanguage learningLanguage, int activeStudents,
                         int assignments, int expectedDueSubmissions, int submittedDueAssignments,
                         int overdueAssignments, int studentsAtRisk, int pendingReviews, int overdueReviews,
                         int reviewedAttempts, int reviewedWithinSla, int reviewMinutes, int feedbackUsed,
                         int feedbackQuotaPerStudent, Double averageScore) {}
    public record ClassSummary(UUID cohortId, String name, String code, UUID courseId, CohortStatus status,
                               LocalDate startDate, LocalDate endDate, int assignmentCount, int feedbackRemaining) {}
    public record ReportPage(List<Report> items, int page, int size, long totalElements,
                             List<String> organizations, List<String> branches) {}
    private CohortWorkspaceDto() {}
}
