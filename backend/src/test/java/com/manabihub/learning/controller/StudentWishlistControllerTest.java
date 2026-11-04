package com.manabihub.learning.controller;

import com.manabihub.learning.dto.response.WishlistItemResponse;
import com.manabihub.learning.service.StudentWishlistService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentWishlistController.class)
@Import({SecurityConfig.class, com.manabihub.security.DummyFilterConfig.class})
class StudentWishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentWishlistService wishlistService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void getWishlist_asStudentReturnsOwnedItems() throws Exception {
        when(wishlistService.getWishlist()).thenReturn(List.of(item()));

        mockMvc.perform(get("/api/v1/student/wishlist")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title", is("N4 Foundation")));
    }

    @Test
    void addCourse_asStudentReturnsItem() throws Exception {
        WishlistItemResponse item = item();
        when(wishlistService.addCourse(item.courseId())).thenReturn(item);

        mockMvc.perform(post("/api/v1/student/wishlist/{courseId}", item.courseId())
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("LEARNING_WISHLIST_ADDED")));
    }

    @Test
    void removeCourse_asStudentInvokesOwnerScopedService() throws Exception {
        UUID courseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/student/wishlist/{courseId}", courseId)
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCode", is("LEARNING_WISHLIST_REMOVED")));

        verify(wishlistService).removeCourse(courseId);
    }

    @Test
    void wishlist_rejectsTeacherRole() throws Exception {
        mockMvc.perform(get("/api/v1/student/wishlist")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(wishlistService);
    }

    @Test
    void wishlist_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/student/wishlist"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor studentJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private WishlistItemResponse item() {
        return new WishlistItemResponse(
                UUID.randomUUID(),
                Instant.parse("2026-07-24T00:00:00Z"),
                UUID.randomUUID(),
                "N4 Foundation",
                "n4-foundation",
                null,
                null,
                "LANGUAGE",
                BigDecimal.ZERO,
                "VND",
                "Teacher A",
                12
        );
    }
}
