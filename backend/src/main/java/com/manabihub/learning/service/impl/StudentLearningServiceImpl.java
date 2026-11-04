package com.manabihub.learning.service.impl;

import com.manabihub.common.response.PageResponse;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.learning.dto.response.StudentCourseSummaryResponse;
import com.manabihub.learning.dto.response.StudentDashboardStatsResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.StudentLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentLearningServiceImpl implements StudentLearningService {

    private static final List<EnrollmentStatus> LEARNING_STATUSES =
            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LessonBlockProgressRepository lessonBlockProgressRepository;
    private final LessonBlockRepository lessonBlockRepository;

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardStatsResponse getDashboardStats(UUID userId) {
        StudentProfile profile = studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Student profile not found for user",
                        HttpStatus.NOT_FOUND));

        UUID studentId = profile.getId();
        int active = enrollmentRepository.countByStudentIdAndStatus(studentId, EnrollmentStatus.ACTIVE);
        int completed = enrollmentRepository.countByStudentIdAndStatus(studentId, EnrollmentStatus.COMPLETED);
        int total = active + completed;

        return StudentDashboardStatsResponse.builder()
                .totalEnrolledCourses(total)
                .activeCourses(active)
                .completedCourses(completed)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentCourseSummaryResponse> getEnrolledCourses(UUID userId, Pageable pageable) {
        StudentProfile profile = studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Student profile not found for user",
                        HttpStatus.NOT_FOUND));

        Page<Enrollment> enrollmentsPage = enrollmentRepository.findByStudentIdAndStatusIn(
                profile.getId(),
                LEARNING_STATUSES,
                pageable);

        Map<UUID, Long> completedByEnrollment = loadCompletedCounts(enrollmentsPage);
        Map<UUID, Long> totalByCourse = loadTotalBlockCounts(enrollmentsPage);

        Page<StudentCourseSummaryResponse> responsePage = enrollmentsPage.map(enrollment -> {
            long completed = completedByEnrollment.getOrDefault(enrollment.getId(), 0L);
            long total = totalByCourse.getOrDefault(enrollment.getCourse().getId(), 0L);
            return mapToSummaryResponse(enrollment, progressPercentage(completed, total));
        });
        return PageResponse.from(responsePage);
    }

    private Map<UUID, Long> loadCompletedCounts(Page<Enrollment> enrollmentsPage) {
        List<UUID> enrollmentIds = enrollmentsPage.getContent().stream()
                .map(Enrollment::getId)
                .toList();
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return lessonBlockProgressRepository
                .countByEnrollmentIdsAndStatus(enrollmentIds, LessonProgressStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        LessonBlockProgressRepository.CompletedProgressCount::getEnrollmentId,
                        LessonBlockProgressRepository.CompletedProgressCount::getCompletedCount));
    }

    private Map<UUID, Long> loadTotalBlockCounts(Page<Enrollment> enrollmentsPage) {
        List<UUID> courseIds = enrollmentsPage.getContent().stream()
                .map(Enrollment::getCourse)
                .map(Course::getId)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return lessonBlockRepository.countByCourseIds(courseIds).stream()
                .collect(Collectors.toMap(
                        LessonBlockRepository.CourseBlockCount::getCourseId,
                        LessonBlockRepository.CourseBlockCount::getTotalCount));
    }

    private StudentCourseSummaryResponse mapToSummaryResponse(
            Enrollment enrollment,
            double progressPercentage) {
        Course course = enrollment.getCourse();
        String teacherName = course.getTeacher() != null && course.getTeacher().getUser() != null
                ? course.getTeacher().getUser().getFullName()
                : null;

        return StudentCourseSummaryResponse.builder()
                .enrollmentId(enrollment.getId())
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .learningLanguage(course.getLearningLanguage())
                .proficiencyFramework(course.getProficiencyFramework())
                .frameworkVersion(course.getFrameworkVersion())
                .proficiencyLevel(course.getProficiencyLevel())
                .thumbnailUrl(course.getThumbnailUrl())
                .teacherName(teacherName)
                .enrollmentStatus(enrollment.isExpired(Instant.now())
                        ? EnrollmentStatus.EXPIRED
                        : enrollment.getStatus())
                .enrolledAt(enrollment.getEnrolledAt())
                .expiresAt(enrollment.getExpiresAt())
                .daysRemaining(daysRemaining(enrollment.getExpiresAt()))
                .progressPercentage(progressPercentage)
                .build();
    }

    private long daysRemaining(Instant expiresAt) {
        if (expiresAt == null) {
            return -1;
        }
        long seconds = java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
        return Math.max(0, (seconds + 86_399) / 86_400);
    }

    private double progressPercentage(long completed, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.min(100.0, Math.round((completed * 10_000.0) / total) / 100.0);
    }
}
