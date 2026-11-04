package com.manabihub.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DatabaseAuthRateLimiter {

    private static final String CONSUME_SQL = """
            INSERT INTO internal_admin_auth_rate_limits (
                rate_key,
                endpoint,
                window_started_at,
                attempt_count,
                blocked_until,
                updated_at
            )
            VALUES (?, ?, CURRENT_TIMESTAMP, 1, NULL, CURRENT_TIMESTAMP)
            ON CONFLICT (rate_key) DO UPDATE SET
                endpoint = EXCLUDED.endpoint,
                attempt_count = CASE
                    WHEN internal_admin_auth_rate_limits.window_started_at
                            <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        THEN 1
                    ELSE internal_admin_auth_rate_limits.attempt_count + 1
                END,
                window_started_at = CASE
                    WHEN internal_admin_auth_rate_limits.window_started_at
                            <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        THEN CURRENT_TIMESTAMP
                    ELSE internal_admin_auth_rate_limits.window_started_at
                END,
                blocked_until = CASE
                    WHEN (
                        CASE
                            WHEN internal_admin_auth_rate_limits.window_started_at
                                    <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                                THEN 1
                            ELSE internal_admin_auth_rate_limits.attempt_count + 1
                        END
                    ) >= ?
                        THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                    WHEN internal_admin_auth_rate_limits.blocked_until <= CURRENT_TIMESTAMP
                        THEN NULL
                    ELSE internal_admin_auth_rate_limits.blocked_until
                END,
                updated_at = CURRENT_TIMESTAMP
            RETURNING blocked_until
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SecureTokenService tokenService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(
            String scope,
            String value,
            String endpoint,
            int maximumAttempts,
            int windowSeconds,
            int blockSeconds
    ) {
        Timestamp blockedUntil = jdbcTemplate.queryForObject(
                CONSUME_SQL,
                Timestamp.class,
                key(scope, value),
                endpoint,
                windowSeconds,
                windowSeconds,
                windowSeconds,
                maximumAttempts,
                blockSeconds
        );
        return blockedUntil == null || !blockedUntil.toInstant().isAfter(Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String scope, String value) {
        Boolean blocked = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM internal_admin_auth_rate_limits
                            WHERE rate_key = ?
                              AND blocked_until > CURRENT_TIMESTAMP
                        )
                        """,
                Boolean.class,
                key(scope, value)
        );
        return Boolean.TRUE.equals(blocked);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String scope, String value) {
        jdbcTemplate.update(
                "DELETE FROM internal_admin_auth_rate_limits WHERE rate_key = ?",
                key(scope, value)
        );
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void removeExpiredBuckets() {
        jdbcTemplate.update("""
                DELETE FROM internal_admin_auth_rate_limits
                WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '48 hours'
                  AND (blocked_until IS NULL OR blocked_until < CURRENT_TIMESTAMP)
                """);
    }

    private String key(String scope, String value) {
        return tokenService.hash(scope + ":" + (value == null ? "" : value));
    }
}
