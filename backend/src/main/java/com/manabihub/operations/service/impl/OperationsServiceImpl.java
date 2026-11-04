package com.manabihub.operations.service.impl;

import com.manabihub.operations.dto.OperationsOverviewResponse;
import com.manabihub.operations.dto.OperationsOverviewResponse.BuildSnapshot;
import com.manabihub.operations.dto.OperationsOverviewResponse.MemorySnapshot;
import com.manabihub.operations.dto.OperationsOverviewResponse.ThreadSnapshot;
import com.manabihub.operations.dto.RecentApplicationLogResponse;
import com.manabihub.operations.dto.RecentApplicationLogResponse.LogEntry;
import com.manabihub.operations.dto.RuntimeConfigurationResponse;
import com.manabihub.operations.dto.RuntimeConfigurationResponse.ComponentStatus;
import com.manabihub.operations.logging.RecentApplicationLogBuffer;
import com.manabihub.operations.service.OperationsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class OperationsServiceImpl implements OperationsService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String UNKNOWN = "unknown";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DataSource dataSource;
    private final Environment environment;
    private final RecentApplicationLogBuffer logBuffer;
    private final BuildProperties buildProperties;
    private final GitProperties gitProperties;

    public OperationsServiceImpl(
            DataSource dataSource,
            Environment environment,
            RecentApplicationLogBuffer logBuffer,
            ObjectProvider<BuildProperties> buildProperties,
            ObjectProvider<GitProperties> gitProperties
    ) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.logBuffer = logBuffer;
        this.buildProperties = buildProperties.getIfAvailable();
        this.gitProperties = gitProperties.getIfAvailable();
    }

    @Override
    public OperationsOverviewResponse getOverview() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        String databaseStatus = databaseStatus();

        return new OperationsOverviewResponse(
                UP.equals(databaseStatus) ? UP : "DEGRADED",
                databaseStatus,
                Instant.ofEpochMilli(runtime.getStartTime()),
                Math.max(0, runtime.getUptime() / 1_000),
                List.copyOf(Arrays.asList(environment.getActiveProfiles())),
                ZoneId.systemDefault().getId(),
                BUSINESS_ZONE.getId(),
                System.getProperty("java.version", UNKNOWN),
                runtime.getVmName(),
                new MemorySnapshot(
                        heap.getUsed(),
                        heap.getCommitted(),
                        heap.getMax(),
                        nonHeap.getUsed()
                ),
                new ThreadSnapshot(
                        threads.getThreadCount(),
                        threads.getDaemonThreadCount(),
                        threads.getPeakThreadCount()
                ),
                buildSnapshot()
        );
    }

    @Override
    public RuntimeConfigurationResponse getRuntimeConfiguration() {
        String smsMode = property("manabihub.phone-verification.sms-mode");
        boolean smsEnabled = hasText(smsMode)
                && !"disabled".equalsIgnoreCase(smsMode)
                && !"console".equalsIgnoreCase(smsMode);
        boolean smsConfigured = hasAll(
                "manabihub.phone-verification.esms.api-key",
                "manabihub.phone-verification.esms.secret-key",
                "manabihub.phone-verification.esms.brandname"
        ) || hasAll(
                "manabihub.phone-verification.sms-webhook-url",
                "manabihub.phone-verification.sms-api-key"
        );
        String identityMode = property("manabihub.kyc.identity-verification-mode");
        boolean vnptEnabled = hasText(identityMode)
                && !identityMode.toLowerCase(Locale.ROOT).contains("mock");
        String allowedOrigins = property("CORS_ALLOWED_ORIGINS");

        return new RuntimeConfigurationResponse(List.of(
                status("DATABASE", hasAll("spring.datasource.url", "spring.datasource.username"), true),
                status("MAIL", hasAll("spring.mail.username", "spring.mail.password"),
                        hasAll("spring.mail.username", "spring.mail.password")),
                status("GOOGLE_OAUTH", hasAll(
                        "spring.security.oauth2.client.registration.google.client-id",
                        "spring.security.oauth2.client.registration.google.client-secret"
                ), hasAll(
                        "spring.security.oauth2.client.registration.google.client-id",
                        "spring.security.oauth2.client.registration.google.client-secret"
                )),
                status("VNPAY", hasAll(
                        "manabihub.payment.vnpay.tmn-code",
                        "manabihub.payment.vnpay.hash-secret",
                        "manabihub.payment.vnpay.return-url"
                ), hasAll(
                        "manabihub.payment.vnpay.tmn-code",
                        "manabihub.payment.vnpay.hash-secret",
                        "manabihub.payment.vnpay.return-url"
                )),
                status("VNPT_EKYC", hasText(identityMode), vnptEnabled),
                status("AI_PROVIDER", hasAll(
                        "manabihub.ai.chat.base-url",
                        "manabihub.ai.chat.api-key"
                ), hasAll(
                        "manabihub.ai.chat.base-url",
                        "manabihub.ai.chat.api-key"
                )),
                status("SMS_PROVIDER", smsConfigured, smsEnabled),
                status("JWT_SIGNING", hasText(property("jwt.secret")), true),
                status("PAYOUT_SECURITY", hasText(property("manabihub.payout.security-secret")), true),
                status("CORS_POLICY", hasText(allowedOrigins) && !"*".equals(allowedOrigins.trim()), true)
        ));
    }

    @Override
    public RecentApplicationLogResponse getRecentLogs(
            String level,
            String query,
            String correlationId,
            int limit
    ) {
        List<LogEntry> entries = logBuffer.find(level, query, correlationId, limit);
        return new RecentApplicationLogResponse(
                "CURRENT_INSTANCE_MEMORY",
                logBuffer.processStartedAt(),
                logBuffer.capacity(),
                entries.size(),
                entries
        );
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? UP : DOWN;
        } catch (Exception ignored) {
            // Error details are intentionally not returned by the diagnostics API.
            return DOWN;
        }
    }

    private BuildSnapshot buildSnapshot() {
        String applicationName = environment.getProperty("spring.application.name", "manabihub-backend");
        String version = firstPresent(
                environment.getProperty("MANABIHUB_BUILD_VERSION"),
                buildProperty("deployment.version"),
                buildProperties == null ? null : buildProperties.getVersion(),
                UNKNOWN
        );
        Instant buildTime = buildProperties == null ? null : buildProperties.getTime();
        String gitCommit = firstPresent(
                environment.getProperty("MANABIHUB_GIT_COMMIT"),
                buildProperty("git.commit"),
                gitProperties == null ? null : gitProperties.getShortCommitId(),
                UNKNOWN
        );
        return new BuildSnapshot(applicationName, version, buildTime, gitCommit);
    }

    private String buildProperty(String key) {
        return buildProperties == null ? null : buildProperties.get(key);
    }

    private String firstPresent(String... candidates) {
        return Arrays.stream(candidates)
                .filter(this::isUsableMetadata)
                .findFirst()
                .orElse(UNKNOWN);
    }

    private boolean isUsableMetadata(String value) {
        return hasText(value) && !UNKNOWN.equalsIgnoreCase(value.trim());
    }

    private ComponentStatus status(String component, boolean configured, boolean enabled) {
        return new ComponentStatus(component, configured, enabled);
    }

    private boolean hasAll(String... keys) {
        return Arrays.stream(keys).allMatch(key -> hasText(property(key)));
    }

    private String property(String key) {
        return environment.getProperty(key);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
