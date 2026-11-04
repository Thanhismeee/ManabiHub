package com.manabihub.writing.service.impl;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.course.entity.Course;
import com.manabihub.writing.dto.request.TeacherWritingFeedbackRequest;
import com.manabihub.writing.repository.projection.WritingReviewFacetProjection;
import com.manabihub.writing.repository.projection.WritingSubmissionQueueProjection;
import com.manabihub.writing.entity.TeacherWritingFeedback;
import com.manabihub.writing.entity.WritingSubmission;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import com.manabihub.writing.repository.AiWritingSuggestionRepository;
import com.manabihub.writing.repository.TeacherWritingFeedbackRepository;
import com.manabihub.writing.repository.WritingSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherWritingReviewServiceImplTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private WritingSubmissionRepository writingSubmissionRepository;

    @Mock
    private AiWritingSuggestionRepository aiWritingSuggestionRepository;

    @Mock
    private TeacherWritingFeedbackRepository teacherWritingFeedbackRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TeacherWritingReviewServiceImpl service;

    private UUID userId;
    private UUID teacherId;
    private UUID submissionId;
    private TeacherProfile teacher;
    private WritingSubmission submission;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        teacher = new TeacherProfile();
        teacher.setId(teacherId);

        AppUser studentUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("student@example.com")
                .fullName("Student A")
                .build();
        StudentProfile student = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(studentUser)
                .displayName("Student A")
                .build();
        Course course = Course.builder()
                .id(UUID.randomUUID())
                .title("N3 Writing")
                .teacher(teacher)
                .build();
        Enrollment enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .build();
        submission = WritingSubmission.builder()
                .id(submissionId)
                .enrollment(enrollment)
                .student(student)
                .lessonBlockId(UUID.randomUUID())
                .content("Japanese writing")
                .status(WritingSubmissionStatus.SUBMITTED)
                .submittedAt(Instant.now())
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(teacher));
    }

    @Test
    void getSubmission_whenNotOwned_returnsNotFound() {
        when(writingSubmissionRepository.findOwnedById(submissionId, teacherId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmission(submissionId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getMessageCode()).isEqualTo("WRITING_SUBMISSION_NOT_FOUND");
                });
    }

    @Test
    void listSubmissions_passesCourseLessonAndStatusFilters() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 10);
        WritingSubmissionQueueProjection row = mock(WritingSubmissionQueueProjection.class);
        when(row.getId()).thenReturn(submissionId);
        when(row.getCourseId()).thenReturn(courseId);
        when(row.getCourseTitle()).thenReturn("N3 Writing");
        when(row.getLessonId()).thenReturn(lessonId);
        when(row.getLessonTitle()).thenReturn("Self introduction");
        when(row.getStudentName()).thenReturn("Student A");
        when(row.getStudentEmail()).thenReturn("student@example.com");
        when(row.getStatus()).thenReturn(WritingSubmissionStatus.SUBMITTED.name());
        when(row.getSubmittedAt()).thenReturn(Instant.now());
        when(row.getScore()).thenReturn(null);
        when(writingSubmissionRepository.findOwnedQueue(
                teacherId,
                "Student A",
                false,
                courseId,
                lessonId,
                WritingSubmissionStatus.SUBMITTED.name(),
                pageable
        )).thenReturn(new PageImpl<>(List.of(row), pageable, 11));

        var result = service.listSubmissions(
                " Student A ",
                false,
                courseId,
                lessonId,
                WritingSubmissionStatus.SUBMITTED,
                pageable
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().lessonId()).isEqualTo(lessonId);
        assertThat(result.getTotalElements()).isEqualTo(11);
        verify(writingSubmissionRepository).findOwnedQueue(
                teacherId,
                "Student A",
                false,
                courseId,
                lessonId,
                WritingSubmissionStatus.SUBMITTED.name(),
                pageable
        );
    }

    @Test
    void getOverview_returnsOwnedAggregateForCurrentFilters() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(writingSubmissionRepository.countOwnedQueue(
                teacherId, "", false, courseId, lessonId, null
        )).thenReturn(3L);
        when(writingSubmissionRepository.countOwnedQueue(
                teacherId, "", true, courseId, lessonId, null
        )).thenReturn(2L);
        when(writingSubmissionRepository.averageOwnedScore(
                teacherId, "", true, courseId, lessonId, null
        )).thenReturn(new BigDecimal("8.125"));

        var result = service.getOverview(null, courseId, lessonId, null);

        assertThat(result.totalSubmissions()).isEqualTo(5);
        assertThat(result.pendingSubmissions()).isEqualTo(3);
        assertThat(result.reviewedSubmissions()).isEqualTo(2);
        assertThat(result.averageScore()).isEqualByComparingTo("8.13");
    }

    @Test
    void getFacets_groupsLessonsUnderTheirOwnedCourse() {
        UUID courseId = UUID.randomUUID();
        WritingReviewFacetProjection first = mock(WritingReviewFacetProjection.class);
        WritingReviewFacetProjection second = mock(WritingReviewFacetProjection.class);
        when(first.getCourseId()).thenReturn(courseId);
        when(first.getCourseTitle()).thenReturn("N3 Writing");
        when(first.getLessonId()).thenReturn(UUID.randomUUID());
        when(first.getLessonTitle()).thenReturn("Self introduction");
        when(second.getCourseId()).thenReturn(courseId);
        when(second.getLessonId()).thenReturn(UUID.randomUUID());
        when(second.getLessonTitle()).thenReturn("Opinion essay");
        when(writingSubmissionRepository.findOwnedFacets(teacherId))
                .thenReturn(List.of(first, second));

        var result = service.getFacets();

        assertThat(result.courses()).hasSize(1);
        assertThat(result.courses().getFirst().title()).isEqualTo("N3 Writing");
        assertThat(result.courses().getFirst().lessons())
                .extracting(lesson -> lesson.title())
                .containsExactly("Self introduction", "Opinion essay");
    }

    @Test
    void saveFeedback_updatesSubmissionAndNotifiesStudent() {
        TeacherWritingFeedback feedback = TeacherWritingFeedback.builder()
                .id(UUID.randomUUID())
                .writingSubmission(submission)
                .teacher(teacher)
                .official(true)
                .createdAt(Instant.now())
                .build();
        when(writingSubmissionRepository.findOwnedByIdForUpdate(submissionId, teacherId))
                .thenReturn(Optional.of(submission));
        when(teacherWritingFeedbackRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.of(feedback));
        when(aiWritingSuggestionRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.empty());
        when(writingSubmissionRepository.findLessonTitle(submissionId))
                .thenReturn(Optional.of("Self introduction"));

        var result = service.saveFeedback(
                submissionId,
                new TeacherWritingFeedbackRequest(new BigDecimal("8.50"), "Good revision.")
        );

        assertThat(submission.getStatus())
                .isEqualTo(WritingSubmissionStatus.TEACHER_FEEDBACK_READY);
        assertThat(feedback.getScore()).isEqualByComparingTo("8.50");
        assertThat(feedback.getComment()).isEqualTo("Good revision.");
        assertThat(result.teacherFeedback()).isNotNull();
        verify(notificationService).createNotification(
                eq(submission.getStudent().getUser().getId()),
                eq(submission.getStudent().getUser().getEmail()),
                eq("Giảng viên đã phản hồi bài viết"),
                eq("Bài viết của bạn trong khóa học N3 Writing đã được giảng viên nhận xét."),
                eq("TEACHER_WRITING_FEEDBACK"),
                eq("/student/courses/" + submission.getEnrollment().getCourse().getId() + "/learn")
        );
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActorUserId()).isEqualTo(userId);
        assertThat(audit.getActorRoleCode()).isEqualTo("TEACHER");
        assertThat(audit.getAction()).isEqualTo("TEACHER_WRITING_FEEDBACK_UPDATED");
        assertThat(audit.getTargetType()).isEqualTo("WRITING_SUBMISSION");
        assertThat(audit.getTargetId()).isEqualTo(submissionId);
        assertThat(audit.getAfterValue()).containsEntry("score", "8.50")
                .containsEntry("commentLength", 14);
        assertThat(audit.getAfterValue()).doesNotContainKey("comment");
    }

    @Test
    void saveFeedback_whenPayloadIsUnchanged_isIdempotent() {
        submission.setStatus(WritingSubmissionStatus.TEACHER_FEEDBACK_READY);
        TeacherWritingFeedback feedback = TeacherWritingFeedback.builder()
                .id(UUID.randomUUID())
                .writingSubmission(submission)
                .teacher(teacher)
                .official(true)
                .score(new BigDecimal("8.50"))
                .comment("Good revision.")
                .createdAt(Instant.now())
                .build();
        when(writingSubmissionRepository.findOwnedByIdForUpdate(submissionId, teacherId))
                .thenReturn(Optional.of(submission));
        when(teacherWritingFeedbackRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.of(feedback));
        when(aiWritingSuggestionRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.empty());
        when(writingSubmissionRepository.findLessonTitle(submissionId))
                .thenReturn(Optional.of("Self introduction"));

        var result = service.saveFeedback(
                submissionId,
                new TeacherWritingFeedbackRequest(new BigDecimal("8.500"), " Good revision. ")
        );

        assertThat(result.teacherFeedback()).isNotNull();
        verify(teacherWritingFeedbackRepository, never()).save(any());
        verify(writingSubmissionRepository, never()).save(any());
        verifyNoInteractions(notificationService);
        verify(auditLogRepository, never()).save(any());
    }
}
