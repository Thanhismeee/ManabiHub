package com.manabihub.learning.service.impl;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.learning.dto.response.StudentCourseSummaryResponse;
import com.manabihub.learning.dto.response.StudentDashboardStatsResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.enums.LessonProgressStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentLearningServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private LessonBlockProgressRepository lessonBlockProgressRepository;

    @Mock
    private LessonBlockRepository lessonBlockRepository;

    @InjectMocks
    private StudentLearningServiceImpl studentLearningService;

    @Test
    void getDashboardStats_countsOnlyLearningStatuses() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentProfile profile = StudentProfile.builder().id(studentId).build();

        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(enrollmentRepository.countByStudentIdAndStatus(studentId, EnrollmentStatus.ACTIVE))
                .thenReturn(3);
        when(enrollmentRepository.countByStudentIdAndStatus(studentId, EnrollmentStatus.COMPLETED))
                .thenReturn(2);

        StudentDashboardStatsResponse result = studentLearningService.getDashboardStats(userId);

        assertEquals(5, result.getTotalEnrolledCourses());
        assertEquals(3, result.getActiveCourses());
        assertEquals(2, result.getCompletedCourses());
    }

    @Test
    void getEnrolledCourses_filtersAndMapsActiveAndCompletedEnrollments() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        StudentProfile profile = StudentProfile.builder().id(studentId).build();
        Course course = Course.builder()
                .id(courseId)
                .title("JLPT N3 Grammar")
                .thumbnailUrl("https://cdn.example.test/course.png")
                .build();
        Enrollment enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(profile)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .enrolledAt(Instant.parse("2026-07-16T00:00:00Z"))
                .build();
        PageRequest pageable = PageRequest.of(0, 12);
        List<EnrollmentStatus> statuses = List.of(
                EnrollmentStatus.ACTIVE,
                EnrollmentStatus.COMPLETED);

        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(enrollmentRepository.findByStudentIdAndStatusIn(studentId, statuses, pageable))
                .thenReturn(new PageImpl<>(List.of(enrollment), pageable, 1));
        LessonBlockProgressRepository.CompletedProgressCount completedCount =
                mock(LessonBlockProgressRepository.CompletedProgressCount.class);
        LessonBlockRepository.CourseBlockCount totalCount =
                mock(LessonBlockRepository.CourseBlockCount.class);
        when(completedCount.getEnrollmentId()).thenReturn(enrollment.getId());
        when(completedCount.getCompletedCount()).thenReturn(3L);
        when(totalCount.getCourseId()).thenReturn(courseId);
        when(totalCount.getTotalCount()).thenReturn(4L);
        when(lessonBlockProgressRepository.countByEnrollmentIdsAndStatus(
                List.of(enrollment.getId()), LessonProgressStatus.COMPLETED))
                .thenReturn(List.of(completedCount));
        when(lessonBlockRepository.countByCourseIds(List.of(courseId)))
                .thenReturn(List.of(totalCount));

        PageResponse<StudentCourseSummaryResponse> result =
                studentLearningService.getEnrolledCourses(userId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(courseId, result.getContent().getFirst().getCourseId());
        assertEquals(EnrollmentStatus.ACTIVE, result.getContent().getFirst().getEnrollmentStatus());
        assertEquals(75.0, result.getContent().getFirst().getProgressPercentage());
        verify(enrollmentRepository).findByStudentIdAndStatusIn(studentId, statuses, pageable);
    }

    @Test
    void getDashboardStats_returnsNotFoundWhenStudentProfileIsMissing() {
        UUID userId = UUID.randomUUID();
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studentLearningService.getDashboardStats(userId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    }
}
