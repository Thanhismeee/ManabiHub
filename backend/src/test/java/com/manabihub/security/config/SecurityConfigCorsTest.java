package com.manabihub.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    @Test
    void productionRejectsCredentialedWildcardOrigin() {
        SecurityConfig config = config("prod", List.of("*"));

        assertThrows(
                IllegalStateException.class,
                config::corsConfigurationSource
        );
    }

    @Test
    void productionAcceptsExplicitTrustedOrigin() {
        SecurityConfig config = config(
                "prod",
                List.of("https://develop.example.com")
        );

        assertDoesNotThrow(config::corsConfigurationSource);
    }

    private SecurityConfig config(String profile, List<String> origins) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        SecurityConfig config = new SecurityConfig(
                mock(TeacherEligibilityFilter.class),
                mock(InternalAdminRoleFilter.class),
                mock(AppUserStatusFilter.class),
                mock(PublicUserSessionFilter.class),
                environment
        );
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config;
    }
}
