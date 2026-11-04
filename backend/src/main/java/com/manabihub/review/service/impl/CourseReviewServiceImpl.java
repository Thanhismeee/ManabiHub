package com.manabihub.review.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.review.dto.request.TeacherCourseReviewReplyRequest;
import com.manabihub.review.dto.request.UpsertCourseReviewRequest;
import com.manabihub.review.dto.response.CourseReviewAggregateResponse;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import com.manabihub.review.repository.CourseReviewRepository;
import com.manabihub.review.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseReviewServiceImpl implements CourseReviewService {

    private static final Set<EnrollmentStatus> ELIGIBLE_ENROLLMENT_STATUSES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);
    private static final String PRIVATE_AUTHOR_FALLBACK = "Học viên ManabiHub";

    private final CourseReviewRepository courseReviewRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final com.manabihub.audit.service.SecurityEventRecorder securityEventRecorder;

    @Override
    public CourseReviewResponse getMyReview(UUID courseId) {
        Enrollment enrollment = resolveEligibleEnrollment(courseId, false);
        return courseReviewRepository.findByEnrollment_Id(enrollment.getId())
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public CourseReviewResponse upsertMyReview(
            UUID courseId,
            UpsertCourseReviewRequest request
    ) {
        Enrollment enrollment = resolveEligibleEnrollment(courseId, true);
        String normalizedText = normalizePlainText(request.reviewText());

        if (normalizedText.length() < 10 || normalizedText.length() > 2000) {
            throw new BusinessException(
                    MessageCodes.COURSE_REVIEW_INVALID,
                    "Review text must contain between 10 and 2000 characters.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Optional<CourseReview> existingReview =
                courseReviewRepository.findByEnrollment_Id(enrollment.getId());
        if (existingReview.isPresent()) {
            CourseReview review = existingReview.get();
            boolean ratingChanged = review.getRating() != request.rating();
            boolean commentChanged = !review.getReviewText().equals(normalizedText);
            if (!ratingChanged && !commentChanged) {
                return toResponse(review);
            }
            review.setRating(request.rating());
            review.setReviewText(normalizedText);
            // A hidden review cannot bypass moderation by editing itself.
            if (review.getStatus() != CourseReviewStatus.HIDDEN) {
                review.setStatus(CourseReviewStatus.APPROVED);
            }
            CourseReview savedReview = courseReviewRepository.saveAndFlush(review);
            notifyTeacher(savedReview, ratingChanged, commentChanged);
            return toResponse(savedReview);
        }

        CourseReview review = CourseReview.builder()
                .enrollment(enrollment)
                .rating(request.rating())
                .reviewText(normalizedText)
                .status(CourseReviewStatus.APPROVED)
                .build();
        CourseReview savedReview = courseReviewRepository.saveAndFlush(review);
        notifyTeacher(savedReview, true, true);
        return toResponse(savedReview);
    }

    @Override
    @Transactional
    public CourseReviewResponse replyToReview(
            UUID reviewId,
            TeacherCourseReviewReplyRequest request
    ) {
        CourseReview review = courseReviewRepository.findByIdForTeacherReply(reviewId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy đánh giá khóa học.",
                        HttpStatus.NOT_FOUND
                ));

        UUID currentUserId = currentUserService.getCurrentUserId();
        Course course = review.getEnrollment().getCourse();
        if (course.getTeacher() == null
                || course.getTeacher().getUser() == null
                || !currentUserId.equals(course.getTeacher().getUser().getId())) {
            UUID ownerUserId = course.getTeacher() == null || course.getTeacher().getUser() == null
                    ? null
                    : course.getTeacher().getUser().getId();
            // NFR-SEC-51: vi pham quyen so huu phai duoc ghi nhan truoc khi tu choi.
            securityEventRecorder.recordAccessDenied(
                    currentUserId,
                    "REVIEW_REPLY_OWNERSHIP_DENIED",
                    "COURSE_REVIEW",
                    review.getId(),
                    java.util.Map.of(
                            "courseId", String.valueOf(course.getId()),
                            "ownerUserId", String.valueOf(ownerUserId)
                    )
            );
            throw new BusinessException(
                    MessageCodes.AUTH_FORBIDDEN,
                    "Bạn chỉ có thể phản hồi bình luận trong khóa học của mình.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (review.getStatus() != CourseReviewStatus.APPROVED) {
            throw new BusinessException(
                    MessageCodes.COURSE_REVIEW_INVALID,
                    "Không thể phản hồi đánh giá đang bị ẩn hoặc chờ kiểm duyệt.",
                    HttpStatus.CONFLICT
            );
        }

        String normalizedReply = normalizePlainText(request.replyText());
        if (normalizedReply.length() < 2 || normalizedReply.length() > 2000) {
            throw new BusinessException(
                    MessageCodes.COURSE_REVIEW_INVALID,
                    "Nội dung phản hồi phải có từ 2 đến 2.000 ký tự.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (normalizedReply.equals(review.getTeacherReplyText())) {
            return toResponse(review);
        }

        review.setTeacherReplyText(normalizedReply);
        review.setTeacherRepliedAt(java.time.Instant.now());
        CourseReview savedReview = courseReviewRepository.saveAndFlush(review);
        notifyStudentOfTeacherReply(savedReview);
        return toResponse(savedReview);
    }

    @Override
    public Page<CourseReviewResponse> getPublicReviews(
            String courseIdentifier,
            Pageable pageable
    ) {
        Course course = resolvePublishedCourse(courseIdentifier);
        return courseReviewRepository.findPublicReviews(
                        course.getId(),
                        CourseReviewStatus.APPROVED,
                        ELIGIBLE_ENROLLMENT_STATUSES,
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    public CourseReviewAggregateResponse getAggregate(UUID courseId) {
        return getAggregates(Set.of(courseId))
                .getOrDefault(courseId, CourseReviewAggregateResponse.empty());
    }

    @Override
    public Map<UUID, CourseReviewAggregateResponse> getAggregates(
            Collection<UUID> courseIds
    ) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(courseIds);
        Map<UUID, CourseReviewAggregateResponse> aggregates = new HashMap<>();
        courseReviewRepository.findAggregates(
                uniqueIds,
                CourseReviewStatus.APPROVED,
                ELIGIBLE_ENROLLMENT_STATUSES
        ).forEach(row -> aggregates.put(
                row.getCourseId(),
                new CourseReviewAggregateResponse(
                        BigDecimal.valueOf(row.getAverageRating())
                                .setScale(1, RoundingMode.HALF_UP),
                        row.getReviewCount()
                )
        ));
        return aggregates;
    }

    private Enrollment resolveEligibleEnrollment(UUID courseId, boolean lockForWrite) {
        StudentProfile student = studentProfileRepository
                .findByUser_Id(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE,
                        "A student profile and eligible enrollment are required to review this course.",
                        HttpStatus.FORBIDDEN
                ));

        Optional<Enrollment> enrollment = lockForWrite
                ? enrollmentRepository.findByStudentIdAndCourseIdForReview(
                        student.getId(),
                        courseId
                )
                : enrollmentRepository.findByStudent_IdAndCourse_Id(
                        student.getId(),
                        courseId
                );

        Enrollment resolved = enrollment.orElseThrow(() -> notEligible());
        if (!ELIGIBLE_ENROLLMENT_STATUSES.contains(resolved.getStatus())
                || resolved.getCourse().getStatus() != CourseStatus.PUBLISHED) {
            throw notEligible();
        }
        return resolved;
    }

    private Course resolvePublishedCourse(String identifier) {
        Optional<Course> course;
        try {
            course = courseRepository.findByIdAndStatus(
                    UUID.fromString(identifier),
                    CourseStatus.PUBLISHED
            );
        } catch (IllegalArgumentException ignored) {
            course = courseRepository.findBySlugAndStatus(
                    identifier,
                    CourseStatus.PUBLISHED
            );
        }
        return course.orElseThrow(() -> new BusinessException(
                MessageCodes.MSG_CATALOG_001,
                "Course was not found.",
                HttpStatus.NOT_FOUND
        ));
    }

    private BusinessException notEligible() {
        return new BusinessException(
                MessageCodes.COURSE_REVIEW_NOT_ELIGIBLE,
                "Only an active or completed enrollment can review this course.",
                HttpStatus.FORBIDDEN
        );
    }

    private CourseReviewResponse toResponse(CourseReview review) {
        StudentProfile student = review.getEnrollment().getStudent();
        String displayName = student.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = PRIVATE_AUTHOR_FALLBACK;
        }
        String avatarUrl = student.getUser() == null
                ? null
                : student.getUser().getAvatarUrl();
        return new CourseReviewResponse(
                review.getId(),
                review.getRating(),
                review.getReviewText(),
                displayName,
                avatarUrl,
                review.getUpdatedAt(),
                review.getTeacherReplyText(),
                review.getTeacherRepliedAt()
        );
    }

    private void notifyTeacher(
            CourseReview review,
            boolean ratingChanged,
            boolean commentChanged
    ) {
        if (review.getStatus() != CourseReviewStatus.APPROVED) {
            return;
        }
        Course course = review.getEnrollment().getCourse();
        if (course.getTeacher() == null || course.getTeacher().getUser() == null) {
            return;
        }

        var teacherUser = course.getTeacher().getUser();
        String studentName = resolveStudentDisplayName(review.getEnrollment().getStudent());
        String actionUrl = courseActionUrl(course);
        if (ratingChanged) {
            notificationService.createNotification(
                    teacherUser.getId(),
                    teacherUser.getEmail(),
                    "Khóa học có đánh giá mới",
                    studentName + " đã đánh giá " + review.getRating()
                            + "/5 sao cho khóa học \"" + course.getTitle() + "\".",
                    NotificationTypes.STUDENT_COURSE_RATING,
                    actionUrl
            );
        }
        if (commentChanged) {
            notificationService.createNotification(
                    teacherUser.getId(),
                    teacherUser.getEmail(),
                    "Khóa học có bình luận mới",
                    studentName + " vừa bình luận về khóa học \""
                            + course.getTitle() + "\".",
                    NotificationTypes.STUDENT_COURSE_COMMENT,
                    actionUrl
            );
        }
    }

    private void notifyStudentOfTeacherReply(CourseReview review) {
        StudentProfile student = review.getEnrollment().getStudent();
        if (student == null || student.getUser() == null) {
            return;
        }
        Course course = review.getEnrollment().getCourse();
        notificationService.createNotification(
                student.getUser().getId(),
                student.getUser().getEmail(),
                "Giảng viên đã phản hồi bình luận của bạn",
                "Giảng viên khóa học \"" + course.getTitle()
                        + "\" vừa phản hồi bình luận của bạn.",
                NotificationTypes.TEACHER_REVIEW_REPLY,
                courseActionUrl(course)
        );
    }

    private String resolveStudentDisplayName(StudentProfile student) {
        if (student != null && student.getDisplayName() != null
                && !student.getDisplayName().isBlank()) {
            return student.getDisplayName().trim();
        }
        return PRIVATE_AUTHOR_FALLBACK;
    }

    private String courseActionUrl(Course course) {
        String identifier = course.getSlug() == null || course.getSlug().isBlank()
                ? course.getId().toString()
                : course.getSlug();
        return "/courses/" + identifier + "#course-reviews";
    }

    private String normalizePlainText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[\\p{Cc}\\p{Cf}&&[^\\r\\n\\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
