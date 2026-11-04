package com.manabihub.course.controller;

import com.manabihub.course.dto.response.PublicCourseSummaryResponse;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.service.CourseService;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.service.CourseReviewService;
import com.manabihub.security.config.SecurityConfig;
import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCourseController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class PublicCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private CourseReviewService courseReviewService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    private PublicCourseSummaryResponse buildSummary(String title, BigDecimal price) {
        return PublicCourseSummaryResponse.builder()
                .id(UUID.randomUUID())
                .title(title)
                .slug(title.toLowerCase().replace(" ", "-"))
                .jlptLevel(JlptLevel.N3)
                .category("grammar")
                .price(price)
                .currency("VND")
                .teacherName("Teacher Test")
                .totalLessons(10)
                .publishedAt(Instant.now())
                .build();
    }

    @Test
    void searchCourses_returnsPagedResults() throws Exception {
        List<PublicCourseSummaryResponse> items = List.of(
                buildSummary("JLPT N3 Grammar", new BigDecimal("299000")),
                buildSummary("Kanji Mastery", new BigDecimal("199000"))
        );
        Page<PublicCourseSummaryResponse> page = new PageImpl<>(
                items,
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "publishedAt")),
                2
        );

        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/public/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.first", is(true)))
                .andExpect(jsonPath("$.data.last", is(true)));
    }

    @Test
    void searchCourses_withKeyword_passesKeywordToService() throws Exception {
        Page<PublicCourseSummaryResponse> emptyPage = new PageImpl<>(
                Collections.emptyList(),
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "publishedAt")),
                0
        );

        when(courseService.searchPublicCourses(
                eq("kanji"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("keyword", "kanji"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)));

        verify(courseService).searchPublicCourses(
                eq("kanji"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        );
    }

    @Test
    void searchCourses_withAllFilters_passesAllToService() throws Exception {
        Page<PublicCourseSummaryResponse> emptyPage = new PageImpl<>(
                Collections.emptyList(),
                PageRequest.of(0, 12, Sort.by(Sort.Direction.ASC, "price")),
                0
        );

        when(courseService.searchPublicCourses(
                eq("grammar"), eq("grammar"), eq(JlptLevel.N3),
                eq(new BigDecimal("100000")), eq(new BigDecimal("500000")),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("keyword", "grammar")
                        .param("category", "grammar")
                        .param("jlptLevel", "N3")
                        .param("minPrice", "100000")
                        .param("maxPrice", "500000")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void searchCourses_emptyResult_returnsEmptyPage() throws Exception {
        Page<PublicCourseSummaryResponse> emptyPage = new PageImpl<>(
                Collections.emptyList(),
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "publishedAt")),
                0
        );

        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/public/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)))
                .andExpect(jsonPath("$.data.totalPages", is(0)));
    }

    @Test
    void searchCourses_withPagination_respectsPageAndSize() throws Exception {
        Page<PublicCourseSummaryResponse> page = new PageImpl<>(
                List.of(buildSummary("Page 2 Course", new BigDecimal("399000"))),
                PageRequest.of(1, 6, Sort.by(Sort.Direction.DESC, "publishedAt")),
                13
        );

        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("page", "1")
                        .param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.size", is(6)))
                .andExpect(jsonPath("$.data.first", is(false)));
    }

    @Test
    void searchCourses_acceptsAggregateSortsForCatalogTabs() throws Exception {
        Page<PublicCourseSummaryResponse> emptyPage = new PageImpl<>(Collections.emptyList());
        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("sort", "enrollmentCount,desc")
                        .param("size", "24"))
                .andExpect(status().isOk());

        verify(courseService).searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 24, Sort.by(Sort.Direction.DESC, "enrollmentCount")))
        );
    }

    @Test
    void searchCourses_withNegativePage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/public/courses")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(courseService, never()).searchPublicCourses(
                any(), any(), any(), any(), any(), any(Pageable.class)
        );
    }

    @Test
    void searchCourses_withInvertedPriceRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/public/courses")
                        .param("minPrice", "500000")
                        .param("maxPrice", "100000"))
                .andExpect(status().isBadRequest());

        verify(courseService, never()).searchPublicCourses(
                any(), any(), any(), any(), any(), any(Pageable.class)
        );
    }

    @Test
    void searchCourses_withUnsupportedSort_fallsBackToPublishedAtDescending() throws Exception {
        Page<PublicCourseSummaryResponse> emptyPage = new PageImpl<>(Collections.emptyList());
        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("sort", "teacher.passwordHash,asc"))
                .andExpect(status().isOk());

        verify(courseService).searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "publishedAt")))
        );
    }

    @Test
    void searchCourses_withEnrollmentRanking_passesWhitelistedDescendingSort() throws Exception {
        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("page", "2")
                        .param("size", "4")
                        .param("sort", "enrollmentCount,desc"))
                .andExpect(status().isOk());

        verify(courseService).searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(2, 4, Sort.by(Sort.Direction.DESC, "enrollmentCount")))
        );
    }

    @Test
    void searchCourses_withRatingRanking_forcesDescendingLeaderboard() throws Exception {
        when(courseService.searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/public/courses")
                        .param("sort", "averageRating,asc"))
                .andExpect(status().isOk());

        verify(courseService).searchPublicCourses(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "averageRating")))
        );
    }

    @Test
    void getCourseReviews_returnsOnlyPublicSafeReviewFields() throws Exception {
        UUID reviewId = UUID.randomUUID();
        CourseReviewResponse review = new CourseReviewResponse(
                reviewId,
                5,
                "Khóa học rất dễ hiểu.",
                "Học viên An",
                "/avatars/student.png",
                Instant.parse("2026-07-27T00:00:00Z")
        );
        when(courseReviewService.getPublicReviews(
                eq("n5-foundations"),
                eq(PageRequest.of(0, 10))
        )).thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/public/courses/{identifier}/reviews", "n5-foundations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id", is(reviewId.toString())))
                .andExpect(jsonPath("$.data.content[0].rating", is(5)))
                .andExpect(jsonPath("$.data.content[0].authorDisplayName", is("Học viên An")))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].studentId").doesNotExist());
    }

    @Test
    void getCourseReviews_rejectsUnboundedPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/public/courses/{identifier}/reviews", "n5-foundations")
                        .param("size", "21"))
                .andExpect(status().isBadRequest());

        verify(courseReviewService, never()).getPublicReviews(any(), any());
    }
}
