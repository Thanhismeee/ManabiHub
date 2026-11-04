package com.manabihub.violation.repository;

import com.manabihub.identity.entity.AppUser;
import com.manabihub.violation.entity.ViolationReport;
import com.manabihub.violation.enums.ViolationStatus;
import com.manabihub.violation.enums.ViolationTargetType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ViolationReportRepositoryPostgresTest {

    private static PostgreSQLContainer<?> postgresContainer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine");
        postgresContainer.start();
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private ViolationReportRepository violationReportRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pendingReviewReportCanBeSavedAndDetectedAsDuplicate() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, full_name) VALUES (?, ?, ?)",
                reporterId,
                "reporter-" + reporterId + "@example.test",
                "Violation Reporter"
        );

        UUID reportId = transactionTemplate.execute(status -> {
            AppUser reporter = entityManager.getReference(AppUser.class, reporterId);
            ViolationReport report = ViolationReport.builder()
                    .reporter(reporter)
                    .targetType(ViolationTargetType.LESSON_BLOCK)
                    .targetId(targetId)
                    .reason("Nội dung bài học không phù hợp với mô tả.")
                    .status(ViolationStatus.PENDING_REVIEW)
                    .build();
            return violationReportRepository.saveAndFlush(report).getId();
        });

        assertTrue(violationReportRepository.isDuplicateReport(
                reporterId,
                ViolationTargetType.LESSON_BLOCK,
                targetId,
                Instant.now().minus(1, ChronoUnit.HOURS)
        ));
        assertEquals(
                "PENDING_REVIEW",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM violation_reports WHERE id = ?",
                        String.class,
                        reportId
                )
        );
    }
}
