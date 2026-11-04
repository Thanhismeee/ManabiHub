package com.manabihub.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link CorrelationIdFilter}.
 * Verifies the real backend header path:
 *   1. Missing header → filter generates a UUID and sets it on the response
 *   2. Present header → filter preserves the client-provided value
 *   3. MDC is populated during filter chain execution
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationId_whenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            // MDC should be populated during the filter chain
            assertThat(MDC.get("correlationId")).isNotBlank();
        });

        String responseHeader = response.getHeader("X-Correlation-ID");
        assertThat(responseHeader).isNotBlank();
        // Should be a valid UUID
        assertThat(responseHeader).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        // MDC should be cleaned up after the filter chain
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void preservesCorrelationId_whenHeaderIsProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "client-trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(MDC.get("correlationId")).isEqualTo("client-trace-abc-123");
        });

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("client-trace-abc-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void cleansUpMdc_evenWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(request, response, (req, res) -> {
                assertThat(MDC.get("correlationId")).isNotBlank();
                throw new ServletException("Simulated failure");
            });
        } catch (ServletException | IOException ignored) {
            // expected
        }

        // MDC must still be cleaned up
        assertThat(MDC.get("correlationId")).isNull();
        // Response header should still have been set before the exception
        assertThat(response.getHeader("X-Correlation-ID")).isNotBlank();
    }
}
