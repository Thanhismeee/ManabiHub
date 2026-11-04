package com.manabihub.moderation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class ModerationMigrationPostgresTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID REPORTER_ID =
            UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID =
            UUID.fromString("f0000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void v46PreservesLegacyEnforcementDecisionsAsActionRecords() {
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("44"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ));
        UUID reportId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO violation_reports (
                    id, reporter_user_id, target_type, target_id, reason, status
                )
                VALUES (?, ?, 'COURSE', ?, 'Legacy moderation report', 'PENDING')
                """, reportId, REPORTER_ID, COURSE_ID);

        for (String decisionType : List.of("FORCE_DRAFT", "REMOVE_CONTENT", "BAN")) {
            jdbc.update("""
                    INSERT INTO moderation_decisions (
                        id, violation_report_id, decided_by, decision, reason
                    )
                    VALUES (?, ?, ?, ?, 'Legacy enforcement')
                    """, UUID.randomUUID(), reportId, ADMIN_ID, decisionType);
        }

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("47"))
                .load()
                .migrate();

        assertEquals(
                3,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM moderation_decisions
                        WHERE violation_report_id = ?
                          AND decision_type = 'UPHELD'
                        """, Integer.class, reportId)
        );
        assertEquals(
                List.of("BAN_ACCOUNT", "FORCE_DRAFT", "REMOVE_CONTENT"),
                jdbc.queryForList("""
                        SELECT action.action_type
                        FROM moderation_action_records action
                        JOIN moderation_decisions decision
                          ON decision.id = action.moderation_decision_id
                        WHERE decision.violation_report_id = ?
                        ORDER BY action.action_type
                        """, String.class, reportId)
        );
        assertEquals(
                3,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM moderation_action_records action
                        JOIN moderation_decisions decision
                          ON decision.id = action.moderation_decision_id
                        WHERE decision.violation_report_id = ?
                          AND action.target_type = 'COURSE'
                          AND action.target_id = ?
                        """, Integer.class, reportId, COURSE_ID)
        );
    }
}
