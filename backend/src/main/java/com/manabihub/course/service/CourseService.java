package com.manabihub.course.service;

import com.manabihub.course.dto.request.CreateCourseDraftRequest;
import com.manabihub.course.dto.response.CourseDraftResponse;
import com.manabihub.course.dto.response.PublicCourseSummaryResponse;
import com.manabihub.course.dto.response.TeacherCourseAnalyticsResponse;
import com.manabihub.course.dto.response.TeacherDashboardResponse;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CourseService {

    CourseDraftResponse createDraft(CreateCourseDraftRequest request);

    List<CourseDraftResponse> listMyDrafts();

    List<CourseDraftResponse> listMyCourses();
    
    TeacherCourseAnalyticsResponse getCourseAnalytics(UUID courseId, java.time.Instant startDate, java.time.Instant endDate);

    com.manabihub.course.dto.response.TeacherDashboardResponse getTeacherDashboardStats();

    CourseDraftResponse updateDraft(UUID draftId, CreateCourseDraftRequest request);

    void deleteDraft(UUID draftId);

    void submitForReview(UUID draftId);

    void publishCourse(UUID courseId);

    /**
     * Takes a published course out of the public catalogue and returns it to
     * the teacher draft workspace so its cover/content can be edited safely.
     * Existing enrolments are not revoked by this transition.
     */
    CourseDraftResponse unpublishCourse(UUID courseId);

    com.manabihub.course.dto.response.PublicCourseDetailResponse getPublicCourseDetail(String identifier);

    Page<PublicCourseSummaryResponse> searchPublicCourses(
            String keyword,
            String category,
            JlptLevel jlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    Page<PublicCourseSummaryResponse> searchPublicCourses(
            String keyword,
            String category,
            LearningLanguage learningLanguage,
            ProficiencyFramework proficiencyFramework,
            String proficiencyLevel,
            JlptLevel legacyJlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );
}
