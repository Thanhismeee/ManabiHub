package com.manabihub.learning.controller;

import com.manabihub.learning.dto.response.FinalTestEligibilityResponse;
import com.manabihub.learning.dto.response.QuizSubmissionResponse;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.service.StudentAssessmentService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAssessmentController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAssessmentService assessmentService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void submitQuiz_asStudent_returnsScore() throws Exception {
        UUID blockId = UUID.randomUUID();
        when(assessmentService.submitQuiz(eq(blockId), any())).thenReturn(
                new QuizSubmissionResponse(
                        new BigDecimal("100.00"),
                        true,
                        1,
                        1,
                        LessonProgressStatus.COMPLETED,
                        List.of(new QuizSubmissionResponse.QuizQuestionFeedback(0, true, "A"))
                )
        );

        mockMvc.perform(post("/api/v1/student/lessons/{blockId}/quiz-submissions", blockId)
                        .contentType("application/json")
                        .content("{\"answers\":[\"A\"]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("LEARNING_QUIZ_SUBMITTED")))
                .andExpect(jsonPath("$.data.score", is(100.0)))
                .andExpect(jsonPath("$.data.feedback[0].correctAnswer", is("A")));
    }

    @Test
    void submitQuiz_withMissingAnswers_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/student/lessons/{blockId}/quiz-submissions", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void eligibility_asTeacher_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student/courses/{courseId}/final-test/eligibility", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void eligibility_asStudent_returnsGatingState() throws Exception {
        UUID courseId = UUID.randomUUID();
        when(assessmentService.getFinalTestEligibility(courseId)).thenReturn(
                new FinalTestEligibilityResponse(
                        true,
                        false,
                        "LESSONS_INCOMPLETE",
                        UUID.randomUUID(),
                        10,
                        8,
                        0,
                        2,
                        false
                )
        );

        mockMvc.perform(get("/api/v1/student/courses/{courseId}/final-test/eligibility", courseId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible", is(false)))
                .andExpect(jsonPath("$.data.reason", is("LESSONS_INCOMPLETE")));
    }

    @Test
    void finalTestEndpoints_asAnonymous_areUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/student/courses/{courseId}/final-test/attempts", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void terminateFinalTestAttempt_asStudent_delegatesToService() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/student/courses/{courseId}/final-test/attempts/{attemptId}/terminate",
                        courseId,
                        attemptId
                ).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("COMMON_UPDATED")));

        verify(assessmentService).terminateFinalTestAttempt(courseId, attemptId);
    }
}
