package com.manabihub.course.service.impl;

import com.manabihub.course.dto.response.TeacherDashboardResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CourseServiceImplDashboardTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CourseEditDraftService courseEditDraftService;

    @InjectMocks
    private CourseServiceImpl courseService;

    @Test
    public void getTeacherDashboardStats_shouldReturnCorrectCounts() {
        UUID teacherUserId = UUID.randomUUID();
        UUID teacherProfileId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(teacherUserId);
        when(courseEditDraftService.resolveEditableCourse(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TeacherProfile profile = new TeacherProfile();
        profile.setId(teacherProfileId);
        profile.setKycStatus(TeacherKycStatus.APPROVED);
        profile.setCanPublishCourse(true);
        when(teacherProfileRepository.findByUserId(teacherUserId)).thenReturn(Optional.of(profile));

        Course draftCourse = Course.builder().id(UUID.randomUUID()).title("Draft").status(CourseStatus.DRAFT).teacher(profile).build();
        Course forcedDraftCourse = Course.builder().id(UUID.randomUUID()).title("Forced Draft").status(CourseStatus.FORCED_DRAFT).teacher(profile).build();
        Course rejectedCourse = Course.builder().id(UUID.randomUUID()).title("Rejected").status(CourseStatus.REJECTED).teacher(profile).build();
        Course pendingCourse = Course.builder().id(UUID.randomUUID()).title("Pending").status(CourseStatus.PENDING).teacher(profile).build();
        Course publishedCourse = Course.builder().id(UUID.randomUUID()).title("Published").status(CourseStatus.PUBLISHED).teacher(profile).build();
        Course publishedCourse2 = Course.builder().id(UUID.randomUUID()).title("Published 2").status(CourseStatus.PUBLISHED).teacher(profile).build();

        // Total 6 courses returned by the repository (ARCHIVED should be excluded by the repository query)
        List<Course> mockCourses = List.of(
            publishedCourse2, publishedCourse, pendingCourse, rejectedCourse, forcedDraftCourse, draftCourse
        );

        when(courseRepository.findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(teacherProfileId, CourseStatus.ARCHIVED))
                .thenReturn(mockCourses);

        TeacherDashboardResponse stats = courseService.getTeacherDashboardStats();

        assertThat(stats.getTotalCourses()).isEqualTo(6);
        assertThat(stats.getDraftOrCorrection()).isEqualTo(3); // DRAFT, FORCED_DRAFT, REJECTED
        assertThat(stats.getPendingApproval()).isEqualTo(1); // PENDING
        assertThat(stats.getPublished()).isEqualTo(2); // PUBLISHED

        // verify recentCourses is limited to 4
        assertThat(stats.getRecentCourses()).hasSize(4);
        assertThat(stats.getRecentCourses().get(0).id()).isEqualTo(publishedCourse2.getId());

        // Verify the repository query excludes ARCHIVED
        org.mockito.Mockito.verify(courseRepository).findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(teacherProfileId, CourseStatus.ARCHIVED);
    }
}
