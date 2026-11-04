package com.manabihub.writing.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.writing.dto.request.TeacherWritingFeedbackRequest;
import com.manabihub.writing.dto.response.AiWritingSuggestionResponse;
import com.manabihub.writing.dto.response.TeacherWritingFeedbackResponse;
import com.manabihub.writing.dto.response.WritingReviewFacetResponse;
import com.manabihub.writing.dto.response.WritingReviewOverviewResponse;
import com.manabihub.writing.dto.response.WritingSubmissionDetailResponse;
import com.manabihub.writing.dto.response.WritingSubmissionSummaryResponse;
import com.manabihub.writing.entity.AiWritingSuggestion;
import com.manabihub.writing.entity.TeacherWritingFeedback;
import com.manabihub.writing.entity.WritingSubmission;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import com.manabihub.writing.repository.AiWritingSuggestionRepository;
import com.manabihub.writing.repository.TeacherWritingFeedbackRepository;
import com.manabihub.writing.repository.WritingSubmissionRepository;
import com.manabihub.writing.repository.projection.WritingReviewFacetProjection;
import com.manabihub.writing.repository.projection.WritingSubmissionQueueProjection;
import com.manabihub.writing.service.TeacherWritingReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherWritingReviewServiceImpl implements TeacherWritingReviewService {

    private final CurrentUserService currentUserService;
    private final TeacherProfileRepository teacherProfileRepository;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiWritingSuggestionRepository aiWritingSuggestionRepository;
    private final TeacherWritingFeedbackRepository teacherWritingFeedbackRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    @Override
    public PageResponse<WritingSubmissionSummaryResponse> listSubmissions(
            String searchQuery,
            Boolean reviewed,
            UUID courseId,
            UUID lessonId,
            WritingSubmissionStatus status,
            Pageable pageable
    ) {
        TeacherProfile teacher = getCurrentTeacher();
        String normalizedQuery = searchQuery == null ? "" : searchQuery.trim();

        Page<WritingSubmissionSummaryResponse> responsePage = writingSubmissionRepository
                .findOwnedQueue(
                        teacher.getId(), normalizedQuery, reviewed, courseId, lessonId,
                        status == null ? null : status.name(), pageable
                )
                .map(this::toSummaryResponse);

        return PageResponse.from(responsePage);
    }

    @Override
    public WritingReviewFacetResponse getFacets() {
        TeacherProfile teacher = getCurrentTeacher();
        List<WritingReviewFacetProjection> rows = writingSubmissionRepository
                .findOwnedFacets(teacher.getId());

        Map<UUID, MutableCourseFacet> courses = new LinkedHashMap<>();
        for (WritingReviewFacetProjection row : rows) {
            MutableCourseFacet course = courses.computeIfAbsent(
                    row.getCourseId(),
                    ignored -> new MutableCourseFacet(row.getCourseId(), row.getCourseTitle())
            );
            if (row.getLessonId() != null) {
                course.lessons.putIfAbsent(
                        row.getLessonId(),
                        new WritingReviewFacetResponse.LessonOption(
                                row.getLessonId(), row.getLessonTitle()
                        )
                );
            }
        }

        List<WritingReviewFacetResponse.CourseOption> options = courses.values().stream()
                .map(course -> new WritingReviewFacetResponse.CourseOption(
                        course.id,
                        course.title,
                        new ArrayList<>(course.lessons.values())
                ))
                .toList();
        return new WritingReviewFacetResponse(options);
    }

    @Override
    public WritingReviewOverviewResponse getOverview(
            String searchQuery,
            UUID courseId,
            UUID lessonId,
            WritingSubmissionStatus status
    ) {
        TeacherProfile teacher = getCurrentTeacher();
        String normalizedQuery = searchQuery == null ? "" : searchQuery.trim();
        String statusValue = status == null ? null : status.name();
        long pending = writingSubmissionRepository.countOwnedQueue(
                teacher.getId(), normalizedQuery, false, courseId, lessonId, statusValue
        );
        long reviewed = writingSubmissionRepository.countOwnedQueue(
                teacher.getId(), normalizedQuery, true, courseId, lessonId, statusValue
        );
        BigDecimal average = writingSubmissionRepository.averageOwnedScore(
                teacher.getId(), normalizedQuery, true, courseId, lessonId, statusValue
        );
        return new WritingReviewOverviewResponse(
                pending + reviewed,
                pending,
                reviewed,
                average == null ? null : average.setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Override
    public WritingSubmissionDetailResponse getSubmission(UUID submissionId) {
        TeacherProfile teacher = getCurrentTeacher();
        WritingSubmission submission = findOwnedSubmission(submissionId, teacher.getId(), false);
        return toDetailResponse(submission);
    }

    @Override
    @Transactional
    public WritingSubmissionDetailResponse saveFeedback(
            UUID submissionId,
            TeacherWritingFeedbackRequest request
    ) {
        TeacherProfile teacher = getCurrentTeacher();
        WritingSubmission submission = findOwnedSubmission(submissionId, teacher.getId(), true);

        TeacherWritingFeedback feedback = teacherWritingFeedbackRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId)
                .orElseGet(() -> TeacherWritingFeedback.builder()
                        .writingSubmission(submission)
                        .teacher(teacher)
                        .official(true)
                        .build());

        String normalizedComment = request.comment().trim();
        boolean unchanged = feedback.getId() != null
                && sameScore(feedback.getScore(), request.score())
                && Objects.equals(feedback.getComment(), normalizedComment)
                && submission.getStatus() == WritingSubmissionStatus.TEACHER_FEEDBACK_READY;

        if (!unchanged) {
            boolean creatingFeedback = feedback.getId() == null;
            BigDecimal previousScore = feedback.getScore();
            String previousComment = feedback.getComment();
            feedback.setScore(request.score());
            feedback.setComment(normalizedComment);
            feedback.setTeacher(teacher);
            feedback.setOfficial(true);
            teacherWritingFeedbackRepository.save(feedback);

            submission.setStatus(WritingSubmissionStatus.TEACHER_FEEDBACK_READY);
            writingSubmissionRepository.save(submission);

            recordFeedbackAudit(
                    currentUserService.getCurrentUserId(),
                    submission,
                    feedback,
                    previousScore,
                    previousComment,
                    creatingFeedback
            );

            notificationService.createNotification(
                    submission.getStudent().getUser().getId(),
                    submission.getStudent().getUser().getEmail(),
                    "Giảng viên đã phản hồi bài viết",
                    "Bài viết của bạn trong khóa học "
                            + submission.getEnrollment().getCourse().getTitle()
                            + " đã được giảng viên nhận xét.",
                    NotificationTypes.TEACHER_WRITING_FEEDBACK,
                    "/student/courses/" + submission.getEnrollment().getCourse().getId() + "/learn"
            );
        }

        return toDetailResponse(submission);
    }

    private void recordFeedbackAudit(
            UUID actorUserId,
            WritingSubmission submission,
            TeacherWritingFeedback feedback,
            BigDecimal previousScore,
            String previousComment,
            boolean creatingFeedback
    ) {
        auditLogRepository.save(AuditLog.builder()
                .actorType("USER")
                .actorUserId(actorUserId)
                .actorRoleCode("TEACHER")
                .action(creatingFeedback
                        ? "TEACHER_WRITING_FEEDBACK_CREATED"
                        : "TEACHER_WRITING_FEEDBACK_UPDATED")
                .targetType("WRITING_SUBMISSION")
                .targetId(submission.getId())
                .beforeValue(feedbackSnapshot(previousScore, previousComment))
                .afterValue(feedbackSnapshot(feedback.getScore(), feedback.getComment()))
                .metadata(Map.of(
                        "feedbackId", String.valueOf(feedback.getId()),
                        "official", true
                ))
                .build());
    }

    private Map<String, Object> feedbackSnapshot(BigDecimal score, String comment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("score", score == null ? null : score.toPlainString());
        snapshot.put("commentLength", comment == null ? 0 : comment.length());
        return snapshot;
    }

    private TeacherProfile getCurrentTeacher() {
        UUID userId = currentUserService.getCurrentUserId();
        return teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.AUTH_FORBIDDEN,
                        "Teacher profile is required",
                        HttpStatus.FORBIDDEN
                ));
    }

    private WritingSubmission findOwnedSubmission(
            UUID submissionId,
            UUID teacherId,
            boolean forUpdate
    ) {
        return (forUpdate
                ? writingSubmissionRepository.findOwnedByIdForUpdate(submissionId, teacherId)
                : writingSubmissionRepository.findOwnedById(submissionId, teacherId))
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.WRITING_SUBMISSION_NOT_FOUND,
                        "Writing submission was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private WritingSubmissionSummaryResponse toSummaryResponse(
            WritingSubmissionQueueProjection projection
    ) {
        return new WritingSubmissionSummaryResponse(
                projection.getId(),
                projection.getCourseId(),
                projection.getCourseTitle(),
                projection.getLessonId(),
                projection.getLessonTitle(),
                projection.getStudentName(),
                projection.getStudentEmail(),
                WritingSubmissionStatus.valueOf(projection.getStatus()),
                projection.getSubmittedAt(),
                projection.getHasAiSuggestion(),
                projection.getHasTeacherFeedback(),
                projection.getScore()
        );
    }

    private WritingSubmissionDetailResponse toDetailResponse(WritingSubmission submission) {
        AiWritingSuggestionResponse aiSuggestion = aiWritingSuggestionRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submission.getId())
                .map(this::toAiResponse)
                .orElse(null);

        TeacherWritingFeedbackResponse teacherFeedback = teacherWritingFeedbackRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submission.getId())
                .map(this::toFeedbackResponse)
                .orElse(null);

        return new WritingSubmissionDetailResponse(
                submission.getId(),
                submission.getEnrollment().getCourse().getId(),
                submission.getEnrollment().getCourse().getTitle(),
                submission.getLegacyLessonId() != null
                        ? submission.getLegacyLessonId()
                        : submission.getLessonBlockId(),
                writingSubmissionRepository.findLessonTitle(submission.getId())
                        .orElse("Writing activity"),
                resolveStudentName(submission),
                submission.getStudent().getUser().getEmail(),
                submission.getContent(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                aiSuggestion,
                teacherFeedback
        );
    }

    private String resolveStudentName(WritingSubmission submission) {
        String displayName = submission.getStudent().getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return submission.getStudent().getUser().getFullName();
    }

    private AiWritingSuggestionResponse toAiResponse(AiWritingSuggestion suggestion) {
        return new AiWritingSuggestionResponse(
                suggestion.getId(),
                suggestion.getStatus(),
                suggestion.getGrammarSuggestions(),
                suggestion.getVocabularySuggestions(),
                suggestion.getStructureSuggestions(),
                suggestion.getRevisionGuidance(),
                suggestion.getConfidenceLevel(),
                suggestion.isOfficial(),
                suggestion.getFailureReason(),
                suggestion.getCreatedAt()
        );
    }

    private TeacherWritingFeedbackResponse toFeedbackResponse(TeacherWritingFeedback feedback) {
        return new TeacherWritingFeedbackResponse(
                feedback.getId(),
                feedback.getScore(),
                feedback.getComment(),
                feedback.getRubricResult(),
                feedback.isOfficial(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }

    private boolean sameScore(BigDecimal current, BigDecimal requested) {
        if (current == null || requested == null) {
            return current == requested;
        }
        return current.compareTo(requested) == 0;
    }

    private static final class MutableCourseFacet {
        private final UUID id;
        private final String title;
        private final Map<UUID, WritingReviewFacetResponse.LessonOption> lessons =
                new LinkedHashMap<>();

        private MutableCourseFacet(UUID id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
