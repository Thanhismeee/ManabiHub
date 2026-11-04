package com.manabihub.learning.controller;

import com.manabihub.common.response.PageResponse;
import com.manabihub.learning.dto.response.StudentCourseSummaryResponse;
import com.manabihub.learning.dto.response.StudentDashboardStatsResponse;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.service.LearningService;
import com.manabihub.learning.service.StudentLearningService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentLearningController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentLearningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentLearningService studentLearningService;

    @MockBean
    private LearningService learningService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void getDashboardStats_success() throws Exception {
        UUID userId = UUID.randomUUID();
        StudentDashboardStatsResponse response = StudentDashboardStatsResponse.builder()
                .totalEnrolledCourses(5)
                .activeCourses(3)
                .completedCourses(2)
                .build();

        when(studentLearningService.getDashboardStats(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/student/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.messageCode", is("COMMON_SUCCESS")))
                .andExpect(jsonPath("$.data.totalEnrolledCourses", is(5)))
                .andExpect(jsonPath("$.data.activeCourses", is(3)))
                .andExpect(jsonPath("$.data.completedCourses", is(2)));
    }

    @Test
    void getDashboardStats_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/student/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardStats_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEnrolledCourses_success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        StudentCourseSummaryResponse summary = StudentCourseSummaryResponse.builder()
                .enrollmentId(enrollmentId)
                .courseId(courseId)
                .courseTitle("Test Course")
                .thumbnailUrl("http://example.com/thumb.jpg")
                .teacherName("John Doe")
                .enrollmentStatus(EnrollmentStatus.ACTIVE)
                .enrolledAt(Instant.now())
                .build();

        PageResponse<StudentCourseSummaryResponse> pageResponse = PageResponse.<StudentCourseSummaryResponse>builder()
                .content(List.of(summary))
                .page(0)
                .size(12)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(studentLearningService.getEnrolledCourses(eq(userId), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/student/courses")
                        .with(jwt().jwt(builder -> builder.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.messageCode", is("COMMON_SUCCESS")))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].courseTitle", is("Test Course")))
                .andExpect(jsonPath("$.data.content[0].enrollmentStatus", is("ACTIVE")));
    }

    @Test
    void getEnrolledCourses_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEnrolledCourses_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCourseLearning_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        com.manabihub.learning.dto.response.CourseLearningResponse mockResponse = new com.manabihub.learning.dto.response.CourseLearningResponse(
                courseId,
                "Test Course",
                UUID.randomUUID(),
                List.of(
                        new com.manabihub.learning.dto.response.LearningModuleResponse(
                                UUID.randomUUID(),
                                "Module 1",
                                1,
                                List.of(
                                        new com.manabihub.learning.dto.response.LearningLessonBlockResponse(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                com.manabihub.course.enums.LessonBlockType.QUIZ,
                                                "Quiz 1",
                                                null, null, null, null, null,
                                                List.of(new com.manabihub.learning.dto.response.StudentQuizQuestionResponse("Q1", List.of("A", "B"))),
                                                List.of(), null, null, null, 1, true,
                                                com.manabihub.learning.enums.LessonProgressStatus.NOT_STARTED,
                                                null, null, false
                                        )
                                )
                        )
                ),
                null,
                1, 0, 0.0, false, List.of()
        );

        when(learningService.openOrResumeCourse(courseId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/learn", courseId)
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.modules[0].blocks[0].quizItems[0].question", is("Q1")))
                .andExpect(jsonPath("$.data.modules[0].blocks[0].quizItems[0].options", hasSize(2)))
                .andExpect(jsonPath("$.data.modules[0].blocks[0].quizItems[0].answer").doesNotExist());
    }

    @Test
    void getCourseLearning_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/learn", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCourseLearning_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/learn", UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveVideoProgress_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        com.manabihub.learning.dto.request.SaveVideoProgressRequest request = new com.manabihub.learning.dto.request.SaveVideoProgressRequest(120);
        com.manabihub.learning.dto.response.LessonProgressResponse response = new com.manabihub.learning.dto.response.LessonProgressResponse(
                lessonBlockId,
                UUID.randomUUID(),
                com.manabihub.learning.enums.LessonProgressStatus.IN_PROGRESS,
                120,
                null,
                null
        );

        when(learningService.saveVideoProgress(eq(lessonBlockId), any(com.manabihub.learning.dto.request.SaveVideoProgressRequest.class))).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/video-progress", lessonBlockId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\": 120}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));
    }

    @Test
    void saveVideoProgress_unauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/video-progress", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\": 120}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveVideoProgress_forbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/video-progress", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\": 120}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewFlashcard_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        com.manabihub.learning.dto.request.ReviewFlashcardRequest request = new com.manabihub.learning.dto.request.ReviewFlashcardRequest(0, com.manabihub.learning.enums.FlashcardStatus.REMEMBERED);
        com.manabihub.learning.dto.response.LessonProgressResponse response = new com.manabihub.learning.dto.response.LessonProgressResponse(
                lessonBlockId,
                UUID.randomUUID(),
                com.manabihub.learning.enums.LessonProgressStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        when(learningService.reviewFlashcard(eq(lessonBlockId), any(com.manabihub.learning.dto.request.ReviewFlashcardRequest.class))).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/flashcards/review", lessonBlockId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"cardIndex\": 0, \"status\": \"REMEMBERED\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));
    }

    @Test
    void reviewFlashcard_missingCardIndex_badRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/flashcards/review", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REMEMBERED\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(learningService);
    }

    @Test
    void reviewFlashcard_unauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/flashcards/review", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"cardIndex\": 0, \"status\": \"REMEMBERED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewFlashcard_forbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/student/lessons/{lessonBlockId}/flashcards/review", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"cardIndex\": 0, \"status\": \"REMEMBERED\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void markLessonComplete_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        com.manabihub.learning.dto.response.LessonProgressResponse response = new com.manabihub.learning.dto.response.LessonProgressResponse(
                lessonBlockId,
                UUID.randomUUID(),
                com.manabihub.learning.enums.LessonProgressStatus.COMPLETED,
                null,
                Instant.now(),
                null
        );

        when(learningService.markLessonComplete(lessonBlockId)).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/complete", lessonBlockId)
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    void markLessonComplete_unauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/complete", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markLessonComplete_forbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/complete", UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCourseProgressSummary_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        com.manabihub.learning.dto.response.CourseProgressSummaryResponse response = new com.manabihub.learning.dto.response.CourseProgressSummaryResponse(
                courseId,
                "Test Course",
                10,
                5,
                50.0,
                null,
                null,
                false,
                new com.manabihub.learning.dto.response.FinalTestEligibilityResponse(
                        true, false, "LESSONS_INCOMPLETE", UUID.randomUUID(), 10, 5, 0, 2, false
                ),
                new com.manabihub.learning.dto.response.CertificateEligibilityResponse(
                        false, false, false, true, null, 85, false,
                        java.util.List.of("PROGRESS_INCOMPLETE", "FINAL_TEST_NOT_PASSED")
                )
        );

        when(learningService.getCourseProgress(courseId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/progress", courseId)
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalLessons", is(10)))
                .andExpect(jsonPath("$.data.completedLessons", is(5)))
                .andExpect(jsonPath("$.data.finalTestEligibility.eligible", is(false)))
                .andExpect(jsonPath("$.data.certificateEligibility.eligible", is(false)))
                .andExpect(jsonPath("$.data.certificateEligibility.exerciseScoreThreshold", is(85)));
    }

    @Test
    void getCourseProgressSummary_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/progress", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCourseProgressSummary_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/progress", UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWritingSubmission_returnsFeedbackSources() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        var aiSuggestion = new com.manabihub.writing.dto.response.AiWritingSuggestionResponse(
                UUID.randomUUID(),
                "READY",
                null,
                null,
                null,
                "Revise the introduction.",
                null,
                false,
                null,
                Instant.now()
        );
        var teacherFeedback = new com.manabihub.writing.dto.response.TeacherWritingFeedbackResponse(
                UUID.randomUUID(),
                new java.math.BigDecimal("8.50"),
                "Good revision.",
                null,
                true,
                Instant.now(),
                Instant.now()
        );
        var response = new com.manabihub.writing.dto.response.StudentWritingSubmissionResponse(
                UUID.randomUUID(),
                lessonBlockId,
                "My essay",
                com.manabihub.writing.enums.WritingSubmissionStatus.TEACHER_FEEDBACK_READY,
                Instant.now(),
                aiSuggestion,
                teacherFeedback
        );
        when(learningService.getWritingSubmission(lessonBlockId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/student/lessons/{lessonBlockId}/writing-submissions/me", lessonBlockId)
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSuggestion.official", is(false)))
                .andExpect(jsonPath("$.data.teacherFeedback.official", is(true)))
                .andExpect(jsonPath("$.data.teacherFeedback.comment", is("Good revision.")));
    }

    @Test
    void getWritingSubmission_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/lessons/{lessonBlockId}/writing-submissions/me", UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitWriting_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        com.manabihub.writing.dto.response.StudentWritingSubmissionResponse response = new com.manabihub.writing.dto.response.StudentWritingSubmissionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Content",
                com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED, Instant.now(), null, null
        );

        when(learningService.submitWriting(eq(lessonBlockId), any())).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/writing-submissions", lessonBlockId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"My essay\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")));
    }

    @Test
    void saveWritingDraft_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        var response = new com.manabihub.writing.dto.response.StudentWritingSubmissionResponse(
                UUID.randomUUID(), lessonBlockId, "Draft content",
                com.manabihub.writing.enums.WritingSubmissionStatus.DRAFT, Instant.now(), null, null
        );

        when(learningService.saveWritingDraft(eq(lessonBlockId), any())).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/student/lessons/{lessonBlockId}/writing-submissions/draft", lessonBlockId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Draft content\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.content", is("Draft content")));
    }

    @Test
    void submitWriting_forbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/writing-submissions", UUID.randomUUID())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"My essay\"}")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestAiWritingAssistance_success() throws Exception {
        UUID lessonBlockId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        com.manabihub.writing.dto.response.StudentWritingSubmissionResponse response = new com.manabihub.writing.dto.response.StudentWritingSubmissionResponse(
                submissionId, UUID.randomUUID(), "Content",
                com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED, Instant.now(), null, null
        );

        when(learningService.requestAiWritingAssistance(lessonBlockId, submissionId)).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/writing-submissions/{submissionId}/ai-assistance", lessonBlockId, submissionId)
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void requestAiWritingAssistance_forbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/lessons/{lessonBlockId}/writing-submissions/{submissionId}/ai-assistance", UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }
}
