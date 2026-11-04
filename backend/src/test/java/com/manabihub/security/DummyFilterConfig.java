package com.manabihub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.security.config.TeacherEligibilityFilter;
import com.manabihub.security.config.InternalAdminRoleFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

@TestConfiguration
public class DummyFilterConfig {

    /** Pass-through fixture for controller slices that are not testing eligibility. */
    @Bean
    @Primary
    public TeacherEligibilityFilter teacherEligibilityFilter() {
        return new TeacherEligibilityFilter(new JdbcTemplate(), new ObjectMapper(),
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)) {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                filterChain.doFilter(request, response);
            }
        };
    }

    /** Pass-through fixture for controller slices that are not testing live admin roles. */
    @Bean
    @Primary
    public InternalAdminRoleFilter internalAdminRoleFilter() {
        return new InternalAdminRoleFilter(new JdbcTemplate(), new ObjectMapper()) {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                filterChain.doFilter(request, response);
            }
        };
    }
    @Bean
    @Primary
    public com.manabihub.identity.service.PublicUserSessionService publicUserSessionService() {
        com.manabihub.identity.service.PublicUserSessionService mock = org.mockito.Mockito.mock(com.manabihub.identity.service.PublicUserSessionService.class);
        org.mockito.Mockito.when(mock.isSessionValid(org.mockito.Mockito.any(), org.mockito.Mockito.any())).thenReturn(true);
        return mock;
    }
    @Bean
    @Primary
    public com.manabihub.identity.service.LearningSessionLeaseService learningSessionLeaseService() {
        com.manabihub.identity.service.LearningSessionLeaseService mock = org.mockito.Mockito.mock(com.manabihub.identity.service.LearningSessionLeaseService.class);
        org.mockito.Mockito.when(mock.ownsLease(org.mockito.Mockito.any(), org.mockito.Mockito.any())).thenReturn(true);
        return mock;
    }
}
