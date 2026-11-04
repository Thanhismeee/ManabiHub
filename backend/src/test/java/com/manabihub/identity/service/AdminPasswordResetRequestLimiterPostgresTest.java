package com.manabihub.identity.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real CONSUME_SQL against PostgreSQL. The unit test mocks
 * DatabaseAuthRateLimiter, so it cannot observe how attempts are counted.
 */
@Testcontainers(disabledWithoutDocker = true)
class AdminPasswordResetRequestLimiterPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private static AdminPasswordResetRequestLimiter limiter;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DatabaseAuthRateLimiter rateLimiter = new DatabaseAuthRateLimiter(
                new JdbcTemplate(dataSource), new SecureTokenService());
        limiter = new AdminPasswordResetRequestLimiter(rateLimiter);
    }

    @Test
    void processesThreeRequestsPerEmailAndBlocksTheFourth() {
        String email = "limit-email@example.com";
        assertTrue(limiter.allow(email, "10.0.0.1"), "request 1 must be processed");
        assertTrue(limiter.allow(email, "10.0.0.2"), "request 2 must be processed");
        assertTrue(limiter.allow(email, "10.0.0.3"), "request 3 must be processed");
        assertFalse(limiter.allow(email, "10.0.0.4"), "request 4 must be blocked");
    }

    @Test
    void processesTenRequestsPerIpAndBlocksTheEleventh() {
        String ip = "10.9.9.9";
        for (int i = 1; i <= 10; i++) {
            assertTrue(limiter.allow("ip-limit-" + i + "@example.com", ip),
                    "request " + i + " from one IP must be processed");
        }
        assertFalse(limiter.allow("ip-limit-11@example.com", ip),
                "request 11 from one IP must be blocked");
    }
}