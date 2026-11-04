package com.manabihub.operations.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.operations.dto.OperationsOverviewResponse;
import com.manabihub.operations.dto.RecentApplicationLogResponse;
import com.manabihub.operations.dto.RuntimeConfigurationResponse;
import com.manabihub.operations.logging.OperationsLogSanitizer;
import com.manabihub.operations.logging.RecentApplicationLogBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsServiceImplTest {

    private DataSource dataSource;
    private Connection connection;
    private MockEnvironment environment;
    private RecentApplicationLogBuffer logBuffer;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.application.name", "manabihub-backend");
        logBuffer = new RecentApplicationLogBuffer(
                new OperationsLogSanitizer(), 100, 3_145_728
        );
    }

    @Test
    void overviewReturnsSafeJvmAndDatabaseHealthWithoutConnectionDetails() throws Exception {
        when(connection.isValid(2)).thenReturn(true);
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://secret-host/secret-db");

        OperationsOverviewResponse result = service().getOverview();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);

        assertThat(result.applicationStatus()).isEqualTo("UP");
        assertThat(result.databaseStatus()).isEqualTo("UP");
        assertThat(result.activeProfiles()).containsExactly("prod");
        assertThat(result.businessTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.uptimeSeconds()).isNotNegative();
        assertThat(result.memory().heapUsedBytes()).isPositive();
        assertThat(result.threads().live()).isPositive();
        assertThat(json).doesNotContain("secret-host", "secret-db", "jdbc:postgresql");
    }

    @Test
    void overviewDegradesWithoutReturningDatabaseFailureDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException(
                "password=hunter2 host=private-db"
        ));

        OperationsOverviewResponse result = service().getOverview();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);

        assertThat(result.applicationStatus()).isEqualTo("DEGRADED");
        assertThat(result.databaseStatus()).isEqualTo("DOWN");
        assertThat(json).doesNotContain("hunter2", "private-db");
    }

    @Test
    void runtimeConfigurationReturnsOnlyFixedNamesAndBooleans() throws Exception {
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://private/database");
        environment.setProperty("spring.datasource.username", "private-user");
        environment.setProperty("spring.mail.username", "private@example.com");
        environment.setProperty("spring.mail.password", "mail-secret");
        environment.setProperty("manabihub.payment.vnpay.tmn-code", "merchant-secret");
        environment.setProperty("manabihub.payment.vnpay.hash-secret", "hash-secret-value");
        environment.setProperty("manabihub.payment.vnpay.return-url", "https://private.example/return");
        environment.setProperty("jwt.secret", "jwt-secret-value");

        RuntimeConfigurationResponse result = service().getRuntimeConfiguration();
        String json = new ObjectMapper().writeValueAsString(result);

        assertThat(result.components())
                .extracting(RuntimeConfigurationResponse.ComponentStatus::component)
                .containsExactly(
                        "DATABASE", "MAIL", "GOOGLE_OAUTH", "VNPAY", "VNPT_EKYC",
                        "AI_PROVIDER", "SMS_PROVIDER", "JWT_SIGNING", "PAYOUT_SECURITY",
                        "CORS_POLICY"
                );
        assertThat(json)
                .doesNotContain(
                        "private", "mail-secret", "merchant-secret", "hash-secret-value",
                        "jwt-secret-value", "spring.", "manabihub.", "password", "url"
                );
    }

    @Test
    void logsResponseTruthfullyIdentifiesCurrentInstanceMemorySource() {
        RecentApplicationLogResponse result = service()
                .getRecentLogs(null, null, null, 100);

        assertThat(result.logSource()).isEqualTo("CURRENT_INSTANCE_MEMORY");
        assertThat(result.currentProcessStartedAt()).isNotNull();
        assertThat(result.capacity()).isEqualTo(100);
        assertThat(result.returned()).isZero();
    }

    @Test
    void overviewPrefersEmbeddedDeploymentMetadataAndFallsBackToGitProperties() throws Exception {
        when(connection.isValid(2)).thenReturn(true);

        Properties buildEntries = new Properties();
        buildEntries.setProperty("version", "0.0.1-SNAPSHOT");
        buildEntries.setProperty("time", "2026-08-27T01:02:03Z");
        buildEntries.setProperty("deployment.version", "manabihub-v1-13");
        buildEntries.setProperty("git.commit", "unknown");

        Properties gitEntries = new Properties();
        gitEntries.setProperty("commit.id.abbrev", "abc123def456");

        OperationsOverviewResponse result = service(
                new BuildProperties(buildEntries),
                new GitProperties(gitEntries)
        ).getOverview();

        assertThat(result.build().version()).isEqualTo("manabihub-v1-13");
        assertThat(result.build().gitCommit()).isEqualTo("abc123def456");
        assertThat(result.build().buildTime()).isEqualTo(Instant.parse("2026-08-27T01:02:03Z"));
    }

    @SuppressWarnings("unchecked")
    private OperationsServiceImpl service() {
        return service(null, null);
    }

    @SuppressWarnings("unchecked")
    private OperationsServiceImpl service(BuildProperties buildProperties, GitProperties gitProperties) {
        ObjectProvider<BuildProperties> buildProvider = mock(ObjectProvider.class);
        ObjectProvider<GitProperties> gitProvider = mock(ObjectProvider.class);
        when(buildProvider.getIfAvailable()).thenReturn(buildProperties);
        when(gitProvider.getIfAvailable()).thenReturn(gitProperties);
        return new OperationsServiceImpl(
                dataSource,
                environment,
                logBuffer,
                buildProvider,
                gitProvider
        );
    }
}
