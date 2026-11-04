package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.audit.service.SecurityEventRecorder;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.response.CertificateEligibilityResponse;
import com.manabihub.learning.dto.response.LearningCertificateResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LearningCertificate;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.LearningCertificateRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.CertificateEligibilityService;
import com.manabihub.learning.service.StudentCertificateService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentCertificateServiceImpl implements StudentCertificateService {

    private final CourseRepository courseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonBlockProgressRepository lessonBlockProgressRepository;
    private final LearningCertificateRepository certificateRepository;
    private final CertificateEligibilityService eligibilityService;
    private final CurrentUserService currentUserService;
    private final SecurityEventRecorder securityEventRecorder;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    public LearningCertificateResponse getCertificate(UUID courseId) {
        Enrollment enrollment = resolveEnrollment(courseId);
        return certificateRepository.findByEnrollmentId(enrollment.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_CERTIFICATE_NOT_FOUND,
                        "A certificate has not been issued for this course.",
                        HttpStatus.NOT_FOUND
                ));
    }

    @Override
    @Transactional
    public LearningCertificateResponse generateCertificate(UUID courseId) {
        Enrollment resolved = resolveEnrollment(courseId);
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(resolved.getId())
                .orElseThrow(() -> notEnrolled("Enrollment was not found."));

        LearningCertificate existing = certificateRepository.findByEnrollmentId(enrollment.getId())
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        List<LessonBlock> blocks = enrollment.getCourse().getModules().stream()
                .flatMap(module -> module.getBlocks().stream())
                .filter(block -> !block.isModerationHidden())
                .toList();
        Map<UUID, LessonBlockProgress> progressByBlockId = lessonBlockProgressRepository
                .findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.toMap(
                        LessonBlockProgress::getLessonBlockId,
                        Function.identity()
                ));
        CertificateEligibilityResponse eligibility = eligibilityService.evaluate(
                enrollment,
                blocks,
                progressByBlockId
        );
        if (!eligibility.eligible()) {
            throw new BusinessException(
                    MessageCodes.LEARNING_CERTIFICATE_NOT_ELIGIBLE,
                    "Certificate requirements are not met: " + String.join(", ", eligibility.reasons()),
                    HttpStatus.CONFLICT
            );
        }

        StudentProfile student = enrollment.getStudent();
        String studentName = StringUtils.hasText(student.getDisplayName())
                ? student.getDisplayName().trim()
                : student.getUser().getFullName();
        LearningCertificate certificate = certificateRepository.save(
                LearningCertificate.builder()
                        .enrollment(enrollment)
                        .certificateNumber(newCertificateNumber())
                        .studentName(studentName)
                        .courseTitle(enrollment.getCourse().getTitle())
                        .eligibilitySnapshot(objectMapper.valueToTree(eligibility))
                        .issuedAt(Instant.now())
                        .build()
        );
        notificationService.createNotificationOnce(
                "course-completed:" + enrollment.getId(),
                student.getUser().getId(),
                student.getUser().getEmail(),
                "Chúc mừng bạn đã hoàn thành khóa học",
                "Bạn đã hoàn thành khóa học \"" + enrollment.getCourse().getTitle()
                        + "\" và chứng chỉ đã sẵn sàng.",
                NotificationTypes.COURSE_COMPLETED,
                "/student/courses/" + courseId + "/learn"
        );
        return toResponse(certificate);
    }

    private Enrollment resolveEnrollment(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Course was not found.",
                        HttpStatus.NOT_FOUND
                ));
        UUID currentUserId = currentUserService.getCurrentUserId();
        StudentProfile student = studentProfileRepository
                .findByUser_Id(currentUserId)
                .orElse(null);
        if (student == null) {
            recordCertificateAccessDenied(currentUserId, course.getId(), "NO_STUDENT_PROFILE");
            throw notEnrolled("You are not enrolled in this course.");
        }

        Enrollment enrollment = enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId())
                .filter(value -> value.getStatus() == EnrollmentStatus.ACTIVE
                        || value.getStatus() == EnrollmentStatus.COMPLETED)
                .orElse(null);
        if (enrollment == null) {
            recordCertificateAccessDenied(currentUserId, course.getId(), "NO_ACTIVE_ENROLLMENT");
            throw notEnrolled("You are not enrolled in this course.");
        }
        return enrollment;
    }

    private void recordCertificateAccessDenied(UUID actorUserId, UUID courseId, String reason) {
        securityEventRecorder.recordAccessDenied(
                actorUserId,
                "CERTIFICATE_ACCESS_DENIED",
                "COURSE",
                courseId,
                Map.of("reason", reason)
        );
    }

    private BusinessException notEnrolled(String message) {
        return new BusinessException(
                MessageCodes.LEARNING_NOT_ENROLLED,
                message,
                HttpStatus.FORBIDDEN
        );
    }

    private String newCertificateNumber() {
        return "MHB-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private LearningCertificateResponse toResponse(LearningCertificate certificate) {
        return new LearningCertificateResponse(
                certificate.getId(),
                certificate.getEnrollment().getId(),
                certificate.getEnrollment().getCourse().getId(),
                certificate.getCertificateNumber(),
                certificate.getStudentName(),
                certificate.getCourseTitle(),
                certificate.getIssuedAt(),
                certificate.getEnrollment().getCompletedAt()
        );
    }
}
