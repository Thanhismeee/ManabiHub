package com.manabihub.review.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.review.dto.request.TeacherCourseReviewReplyRequest;
import com.manabihub.review.dto.request.UpsertCourseReviewRequest;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import com.manabihub.review.repository.CourseReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CourseReviewServiceImpl}.
 * <p>
 * Grouped with {@code @Nested} so Surefire reports one summary line per Report 5.1 sheet:
 * <pre>
 *   CourseReviewServiceImplTest$UpsertMyReview   -> sheet 47 upsertMyReview
 *   CourseReviewServiceImplTest$GetPublicReviews -> sheet 48 getPublicReviews
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class CourseReviewServiceImplTest {

    @Mock
    private CourseReviewRepository courseReviewRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.manabihub.audit.service.SecurityEventRecorder securityEventRecorder;

    private CourseReviewServiceImpl service;
    private UUID currentUserId;
    private StudentProfile student;
    private Course course;
    private Enrollment enrollment;
    private com.manabihub.kyc.domain.AppUser teacherUser;

    @BeforeEach
    void setUp() {
        service = new CourseReviewServiceImpl(
                courseReviewRepository,
                enrollmentRepository,
                studentProfileRepository,
                courseRepository,
                currentUserService,
                notificationService,
                securityEventRecorder
        );
        currentUserId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(currentUserId)
                .email("private@example.test")
                .fullName("Private Legal Name")
                .avatarUrl("/avatars/student.png")
                .build();
        student = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("Học viên An")
                .build();
        teacherUser = new com.manabihub.kyc.domain.AppUser();
        teacherUser.setId(UUID.randomUUID());
        teacherUser.setEmail("teacher@example.test");
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(teacherUser);
        teacher.setDisplayName("Cô An");
        teacher.setKycStatus(TeacherKycStatus.APPROVED);
        course = Course.builder()
                .id(UUID.randomUUID())
                .teacher(teacher)
                .title("Tiếng Nhật nhập môn")
                .slug("tieng-nhat-nhap-mon")
                .status(CourseStatus.PUBLISHED)
                .build();
        enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .build();
    }

    private void mockLockedEnrollment() {
        mockStudent();
        when(enrollmentRepository.findByStudentIdAndCourseIdForReview(
                student.getId(),
                course.getId()
        )).thenReturn(Optional.of(enrollment));
    }

    private void mockStudent() {
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(studentProfileRepository.findByUser_Id(currentUserId))
                .thenReturn(Optional.of(student));
    }

    private CourseReview review(CourseReviewStatus status) {
        return CourseReview.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .rating(5)
                .reviewText("Nội dung thực tế và dễ hiểu.")
                .status(status)
                .createdAt(Instant.parse("2026-07-26T00:00:00Z"))
                .updatedAt(Instant.parse("2026-07-27T00:00:00Z"))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 47 — upsertMyReview (UC-19 Review Purchased Course) — 12 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 47 - upsertMyReview (UC-19)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UpsertMyReview {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - no review yet -> a new APPROVED review is created")
        void upsert_createsOneReviewUsingLockedCurrentStudentsEnrollment() {
            mockLockedEnrollment();
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.empty());
            when(courseReviewRepository.saveAndFlush(any(CourseReview.class)))
                    .thenAnswer(invocation -> {
                        CourseReview review = invocation.getArgument(0);
                        review.setId(UUID.randomUUID());
                        review.setUpdatedAt(Instant.parse("2026-07-27T00:00:00Z"));
                        return review;
                    });

            CourseReviewResponse response = service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(
                            5,
                            "  Nội dung  thực tế\nvà dễ hiểu.  "
                    )
            );

            assertEquals(5, response.rating());
            assertEquals("Nội dung thực tế và dễ hiểu.", response.reviewText());
            assertEquals("Học viên An", response.authorDisplayName());
            ArgumentCaptor<CourseReview> saved = ArgumentCaptor.forClass(CourseReview.class);
            verify(courseReviewRepository).saveAndFlush(saved.capture());
            assertEquals(enrollment.getId(), saved.getValue().getEnrollment().getId());
            verify(enrollmentRepository).findByStudentIdAndCourseIdForReview(
                    student.getId(),
                    course.getId()
            );
            verify(notificationService).createNotification(
                    eq(teacherUser.getId()),
                    eq(teacherUser.getEmail()),
                    eq("Khóa học có đánh giá mới"),
                    contains("5/5 sao"),
                    eq(NotificationTypes.STUDENT_COURSE_RATING),
                    eq("/courses/tieng-nhat-nhap-mon#course-reviews")
            );
            verify(notificationService).createNotification(
                    eq(teacherUser.getId()),
                    eq(teacherUser.getEmail()),
                    eq("Khóa học có bình luận mới"),
                    contains("Tiếng Nhật nhập môn"),
                    eq(NotificationTypes.STUDENT_COURSE_COMMENT),
                    eq("/courses/tieng-nhat-nhap-mon#course-reviews")
            );
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - same payload -> idempotent, nothing saved")
        void upsert_samePayloadIsIdempotentAndDoesNotRewriteTimestamp() {
            mockLockedEnrollment();
            CourseReview existing = review(CourseReviewStatus.APPROVED);
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.of(existing));

            CourseReviewResponse response = service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(5, existing.getReviewText())
            );

            assertEquals(existing.getId(), response.id());
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (N) - edit an APPROVED review -> new rating and text stored")
        void upsert_editingAnApprovedReview_storesTheNewRatingAndText() {
            mockLockedEnrollment();
            CourseReview existing = review(CourseReviewStatus.APPROVED);
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.of(existing));
            when(courseReviewRepository.saveAndFlush(existing)).thenReturn(existing);

            CourseReviewResponse response = service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(3, "Khoá học ổn nhưng phần nghe hơi ngắn.")
            );

            assertEquals(3, response.rating());
            assertEquals("Khoá học ổn nhưng phần nghe hơi ngắn.", response.reviewText());
            assertEquals(CourseReviewStatus.APPROVED, existing.getStatus());
            verify(courseReviewRepository).saveAndFlush(existing);
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - HIDDEN review cannot re-approve itself through an edit")
        void upsert_hiddenReviewCannotSelfApproveThroughEdit() {
            mockLockedEnrollment();
            CourseReview existing = review(CourseReviewStatus.HIDDEN);
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.of(existing));
            when(courseReviewRepository.saveAndFlush(existing)).thenReturn(existing);

            service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(4, "Nội dung đã được chỉnh sửa hợp lệ.")
            );

            assertEquals(CourseReviewStatus.HIDDEN, existing.getStatus());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - REFUNDED enrollment -> COURSE_REVIEW_NOT_ELIGIBLE")
        void upsert_rejectsRefundedOrRevokedEnrollment() {
            mockStudent();
            enrollment.setStatus(EnrollmentStatus.REFUNDED);
            when(enrollmentRepository.findByStudentIdAndCourseIdForReview(
                    student.getId(),
                    course.getId()
            )).thenReturn(Optional.of(enrollment));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(
                                    5,
                                    "Nội dung hợp lệ nhưng đã hoàn tiền."
                            )
                    )
            );

            assertEquals(
                    MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE,
                    exception.getMessageCode()
            );
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (A) - no enrollment at all -> COURSE_REVIEW_NOT_ELIGIBLE")
        void upsert_withoutAnyEnrollment_isRejected() {
            mockStudent();
            when(enrollmentRepository.findByStudentIdAndCourseIdForReview(
                    student.getId(),
                    course.getId()
            )).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(5, "Nội dung hợp lệ nhưng chưa mua khoá.")
                    )
            );

            assertEquals(MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE, exception.getMessageCode());
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("UTCID07 (A) - no student profile -> COURSE_REVIEW_NOT_ELIGIBLE")
        void upsert_withoutStudentProfile_isRejected() {
            when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
            when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(5, "Nội dung hợp lệ nhưng không có hồ sơ.")
                    )
            );

            assertEquals(MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE, exception.getMessageCode());
            verify(enrollmentRepository, never()).findByStudentIdAndCourseIdForReview(any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(8)
        @DisplayName("UTCID08 (A) - course ARCHIVED -> COURSE_REVIEW_NOT_ELIGIBLE")
        void upsert_courseNoLongerPublished_isRejected() {
            mockStudent();
            course.setStatus(CourseStatus.ARCHIVED);
            when(enrollmentRepository.findByStudentIdAndCourseIdForReview(
                    student.getId(),
                    course.getId()
            )).thenReturn(Optional.of(enrollment));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(5, "Khoá học đã bị gỡ khỏi danh mục.")
                    )
            );

            assertEquals(MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE, exception.getMessageCode());
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @org.junit.jupiter.api.Order(9)
        @DisplayName("UTCID09 (B) - exactly 10 characters = lower bound -> accepted")
        void upsert_textOfExactlyTenCharacters_isAccepted() {
            mockLockedEnrollment();
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.empty());
            when(courseReviewRepository.saveAndFlush(any(CourseReview.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CourseReviewResponse response = service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(4, "a".repeat(10))
            );

            assertEquals(10, response.reviewText().length());
        }

        @Test
        @org.junit.jupiter.api.Order(10)
        @DisplayName("UTCID10 (B) - 9 characters = lower bound - 1 -> COURSE_REVIEW_INVALID")
        void upsert_textShorterThanTenCharacters_isRejected() {
            mockLockedEnrollment();

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(4, "a".repeat(9))
                    )
            );

            assertEquals(MessageCodes.COURSE_REVIEW_INVALID, exception.getMessageCode());
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @org.junit.jupiter.api.Order(11)
        @DisplayName("UTCID11 (B) - exactly 2000 characters = upper bound -> accepted")
        void upsert_textOfExactlyTwoThousandCharacters_isAccepted() {
            mockLockedEnrollment();
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.empty());
            when(courseReviewRepository.saveAndFlush(any(CourseReview.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CourseReviewResponse response = service.upsertMyReview(
                    course.getId(),
                    new UpsertCourseReviewRequest(5, "b".repeat(2000))
            );

            assertEquals(2000, response.reviewText().length());
        }

        @Test
        @org.junit.jupiter.api.Order(12)
        @DisplayName("UTCID12 (B) - 2001 characters = upper bound + 1 -> COURSE_REVIEW_INVALID")
        void upsert_textLongerThanTwoThousandCharacters_isRejected() {
            mockLockedEnrollment();

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.upsertMyReview(
                            course.getId(),
                            new UpsertCourseReviewRequest(5, "b".repeat(2001))
                    )
            );

            assertEquals(MessageCodes.COURSE_REVIEW_INVALID, exception.getMessageCode());
            verify(courseReviewRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Teacher replies to course comments")
    class ReplyToReview {

        @Test
        void reply_savesReplyAndNotifiesStudent() {
            CourseReview existing = review(CourseReviewStatus.APPROVED);
            when(courseReviewRepository.findByIdForTeacherReply(existing.getId()))
                    .thenReturn(Optional.of(existing));
            when(currentUserService.getCurrentUserId()).thenReturn(teacherUser.getId());
            when(courseReviewRepository.saveAndFlush(existing)).thenReturn(existing);

            CourseReviewResponse response = service.replyToReview(
                    existing.getId(),
                    new TeacherCourseReviewReplyRequest("Em đã nhận xét rất đúng trọng tâm.")
            );

            assertEquals("Em đã nhận xét rất đúng trọng tâm.", response.teacherReplyText());
            verify(notificationService).createNotification(
                    eq(student.getUser().getId()),
                    eq(student.getUser().getEmail()),
                    eq("Giảng viên đã phản hồi bình luận của bạn"),
                    contains("Tiếng Nhật nhập môn"),
                    eq(NotificationTypes.TEACHER_REVIEW_REPLY),
                    eq("/courses/tieng-nhat-nhap-mon#course-reviews")
            );
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 48 — getPublicReviews (UC-19 Review Purchased Course) — 4 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 48 - getPublicReviews (UC-19)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetPublicReviews {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - identifier is a UUID -> only APPROVED reviews")
        void getPublicReviews_byCourseId_returnsOnlyApprovedReviews() {
            Pageable pageable = PageRequest.of(0, 10);
            when(courseRepository.findByIdAndStatus(course.getId(), CourseStatus.PUBLISHED))
                    .thenReturn(Optional.of(course));
            when(courseReviewRepository.findPublicReviews(
                    eq(course.getId()),
                    eq(CourseReviewStatus.APPROVED),
                    anySet(),
                    eq(pageable)
            )).thenReturn(new PageImpl<>(List.of(review(CourseReviewStatus.APPROVED)), pageable, 1));

            Page<CourseReviewResponse> page =
                    service.getPublicReviews(course.getId().toString(), pageable);

            assertEquals(1, page.getTotalElements());
            assertEquals("Học viên An", page.getContent().get(0).authorDisplayName());
            verify(courseRepository, never()).findBySlugAndStatus(any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - identifier is a slug -> slug lookup is used")
        void getPublicReviews_bySlug_fallsBackToSlugLookup() {
            Pageable pageable = PageRequest.of(0, 10);
            when(courseRepository.findBySlugAndStatus("n3-grammar", CourseStatus.PUBLISHED))
                    .thenReturn(Optional.of(course));
            when(courseReviewRepository.findPublicReviews(
                    eq(course.getId()),
                    eq(CourseReviewStatus.APPROVED),
                    eq(Set.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)),
                    eq(pageable)
            )).thenReturn(new PageImpl<>(List.of(review(CourseReviewStatus.APPROVED)), pageable, 1));

            Page<CourseReviewResponse> page = service.getPublicReviews("n3-grammar", pageable);

            assertEquals(1, page.getTotalElements());
            verify(courseRepository).findBySlugAndStatus("n3-grammar", CourseStatus.PUBLISHED);
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - no PUBLISHED course matches -> MSG_CATALOG_001")
        void getPublicReviews_courseNotPublished_throwsNotFound() {
            Pageable pageable = PageRequest.of(0, 10);
            when(courseRepository.findBySlugAndStatus("unknown-course", CourseStatus.PUBLISHED))
                    .thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.getPublicReviews("unknown-course", pageable)
            );

            assertEquals(MessageCodes.MSG_CATALOG_001, exception.getMessageCode());
            verify(courseReviewRepository, never())
                    .findPublicReviews(any(), any(), anySet(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (B) - course without any review -> empty page")
        void getPublicReviews_courseWithoutAnyReview_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(courseRepository.findByIdAndStatus(course.getId(), CourseStatus.PUBLISHED))
                    .thenReturn(Optional.of(course));
            when(courseReviewRepository.findPublicReviews(
                    eq(course.getId()),
                    eq(CourseReviewStatus.APPROVED),
                    anySet(),
                    eq(pageable)
            )).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<CourseReviewResponse> page =
                    service.getPublicReviews(course.getId().toString(), pageable);

            assertTrue(page.getContent().isEmpty());
            assertEquals(0, page.getTotalElements());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - getMyReview")
    class GetMyReview {

        @Test
        void getMyReviewScopesLookupThroughCurrentStudentsEnrollment() {
            mockStudent();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(
                    student.getId(),
                    course.getId()
            )).thenReturn(Optional.of(enrollment));
            CourseReview existing = review(CourseReviewStatus.APPROVED);
            when(courseReviewRepository.findByEnrollment_Id(enrollment.getId()))
                    .thenReturn(Optional.of(existing));

            CourseReviewResponse response = service.getMyReview(course.getId());

            assertEquals(existing.getId(), response.id());
            assertEquals("Học viên An", response.authorDisplayName());
            verify(courseReviewRepository).findByEnrollment_Id(enrollment.getId());
        }
    }
}
