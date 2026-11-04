package com.manabihub.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthenticationFailureHandlerTest {

    @Test
    void redirectsWithStableErrorCodeWithoutExposingProviderMessage() throws Exception {
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "frontendLoginUrl", "https://demo.example.com/login");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new AuthenticationServiceException("client_secret=must-not-leak"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://demo.example.com/login?error=oauth2-login-failed")
                .doesNotContain("client_secret");
    }
}
