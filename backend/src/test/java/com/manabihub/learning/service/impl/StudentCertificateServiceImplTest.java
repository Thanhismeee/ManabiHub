package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.audit.service.SecurityEventRecorder;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.response.CertificateEligibilityResponse;
import com.manabihub.learning.dto.response.LearningCertificateResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LearningCertificate;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.LearningCertificateRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.CertificateEligibilityService;
import com.manabihub.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCertificateServiceImplTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private LessonBlockProgressRepository lessonBlockProgressRepository;
    @Mock
    private LearningCertificateRepository certificateRepository;
    @Mock
    private CertificateEligibilityService eligibilityService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private SecurityEventRecorder securityEventRecorder;
    @Mock
    private NotificationService notificationService;
    @Captor
    private org.mockito.ArgumentCaptor<List<LessonBlock>> lessonBlocksCaptor;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StudentCertificateServiceImpl service;

    private UUID userId;
    private Course course;
    private StudentProfile student;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        course = Course.builder()
                .id(UUID.randomUUID())
                .title("Japanese Foundations")
                .modules(new ArrayList<>())
                .build();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@example.com")
                .fullName("Nguyen An")
                .build();
        student = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("An Nguyen")
                .build();
        enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .status(EnrollmentStatus.COMPLETED)
                .completedAt(Instant.parse("2026-07-23T23:59:58Z"))
                .build();
    }

    @Test
    void generateCertificate_createsImmutableSnapshotWhenEligible() {
        mockOwnedEnrollment();
        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByEnrollmentId(enrollment.getId())).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(eligible());
        when(certificateRepository.save(any())).thenAnswer(invocation -> {
            LearningCertificate value = invocation.getArgument(0);
            return LearningCertificate.builder()
                    .id(UUID.randomUUID())
                    .enrollment(value.getEnrollment())
                    .certificateNumber(value.getCertificateNumber())
                    .studentName(value.getStudentName())
                    .courseTitle(value.getCourseTitle())
                    .eligibilitySnapshot(value.getEligibilitySnapshot())
                    .issuedAt(Instant.parse("2026-07-24T00:00:00Z"))
                    .build();
        });

        LearningCertificateResponse result = service.generateCertificate(course.getId());

        assertEquals("An Nguyen", result.studentName());
        assertEquals(course.getTitle(), result.courseTitle());
        assertEquals(enrollment.getCompletedAt(), result.completedAt());
        assertEquals(Instant.parse("2026-07-24T00:00:00Z"), result.issuedAt());
        assertTrue(result.certificateNumber().startsWith("MHB-"));
        ArgumentCaptor<LearningCertificate> certificateCaptor =
                ArgumentCaptor.forClass(LearningCertificate.class);
        verify(certificateRepository).save(certificateCaptor.capture());
        assertTrue(certificateCaptor.getValue().getIssuedAt() != null);
        verify(notificationService).createNotificationOnce(
                "course-completed:" + enrollment.getId(),
                userId,
                "student@example.com",
                "Chúc mừng bạn đã hoàn thành khóa học",
                "Bạn đã hoàn thành khóa học \"Japanese Foundations\" và chứng chỉ đã sẵn sàng.",
                "COURSE_COMPLETED",
                "/student/courses/" + course.getId() + "/learn"
        );
    }

    @Test
    void generateCertificate_returnsExistingRecordIdempotently() {
        mockOwnedEnrollment();
        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        LearningCertificate existing = certificate("MHB-EXISTING");
        when(certificateRepository.findByEnrollmentId(enrollment.getId())).thenReturn(Optional.of(existing));

        LearningCertificateResponse first = service.generateCertificate(course.getId());
        LearningCertificateResponse second = service.generateCertificate(course.getId());

        assertEquals(first.id(), second.id());
        assertEquals("MHB-EXISTING", first.certificateNumber());
        verify(eligibilityService, never()).evaluate(any(), any(), any());
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void generateCertificate_rejectsWhenAnyRuleFails() {
        mockOwnedEnrollment();
        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByEnrollmentId(enrollment.getId())).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(
                new CertificateEligibilityResponse(
                        false, false, false, false, null, 85, false,
                        List.of("PROGRESS_INCOMPLETE", "FINAL_TEST_NOT_PASSED")
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.generateCertificate(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_CERTIFICATE_NOT_ELIGIBLE, exception.getMessageCode());
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void generateCertificate_excludesModerationHiddenLessonsFromEligibility() {
        CourseModule module = CourseModule.builder()
                .id(UUID.randomUUID())
                .blocks(new ArrayList<>())
                .build();
        LessonBlock visible = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.TEXT)
                .title("Visible lesson")
                .build();
        LessonBlock hidden = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.TEXT)
                .title("Hidden lesson")
                .moderationHidden(true)
                .build();
        course.addModule(module);
        module.addBlock(visible);
        module.addBlock(hidden);
        mockOwnedEnrollment();
        when(enrollmentRepository.findByIdForUpdate(enrollment.getId()))
                .thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByEnrollmentId(enrollment.getId()))
                .thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId()))
                .thenReturn(List.of());
        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(
                new CertificateEligibilityResponse(
                        false, false, false, false, null, 85, false,
                        List.of("PROGRESS_INCOMPLETE")
                )
        );

        assertThrows(
                BusinessException.class,
                () -> service.generateCertificate(course.getId())
        );

        verify(eligibilityService).evaluate(
                any(),
                lessonBlocksCaptor.capture(),
                any()
        );
        assertEquals(List.of(visible), lessonBlocksCaptor.getValue());
    }

    @Test
    void getCertificate_returnsNotFoundWithoutLeakingOtherEnrollment() {
        mockOwnedEnrollment();
        when(certificateRepository.findByEnrollmentId(enrollment.getId())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getCertificate(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_CERTIFICATE_NOT_FOUND, exception.getMessageCode());
    }

    @Test
    void getCertificate_rejectsStudentWithoutOwnedEnrollment() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getCertificate(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, exception.getMessageCode());
        verify(securityEventRecorder).recordAccessDenied(
                eq(userId),
                eq("CERTIFICATE_ACCESS_DENIED"),
                eq("COURSE"),
                eq(course.getId()),
                eq(java.util.Map.of("reason", "NO_ACTIVE_ENROLLMENT"))
        );
    }

    private void mockOwnedEnrollment() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                .thenReturn(Optional.of(enrollment));
    }

    private CertificateEligibilityResponse eligible() {
        return new CertificateEligibilityResponse(
                true, true, true, true, null, 85, true, List.of()
        );
    }

    private LearningCertificate certificate(String number) {
        return LearningCertificate.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .certificateNumber(number)
                .studentName("An Nguyen")
                .courseTitle(course.getTitle())
                .eligibilitySnapshot(objectMapper.valueToTree(eligible()))
                .issuedAt(Instant.parse("2026-07-24T00:00:00Z"))
                .build();
    }
}
