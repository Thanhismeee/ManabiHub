package com.manabihub.kyc.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.*;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class VnptVerificationCoordinatorConcurrencyIntegrationTest {

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
    private VnptVerificationCoordinator coordinator;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private KycRequestRepository kycRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockBean
    private VnptVerificationPort vnptVerificationPort;

    private AppUser createTestUser() {
        return transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setId(UUID.randomUUID());
            user.setEmail("test-" + UUID.randomUUID() + "@example.com");
            user.setFullName("Concurrency Test");
            user.setUserStatus(UserStatus.ACTIVE);
            entityManager.persist(user);
            return user;
        });
    }

    private TeacherProfile createTestProfile(AppUser user) {
        return transactionTemplate.execute(status -> {
            TeacherProfile profile = new TeacherProfile();
            profile.setId(UUID.randomUUID());
            profile.setUser(user);
            profile.setKycStatus(TeacherKycStatus.NOT_SUBMITTED);
            teacherProfileRepository.save(profile);
            return profile;
        });
    }

    @Test
    void duplicateProviderTransactionRace() throws Exception {
        // Create TWO separate active users with TWO separate teacher profiles
        AppUser user1 = createTestUser();
        TeacherProfile profile1 = createTestProfile(user1);
        AppUser user2 = createTestUser();
        TeacherProfile profile2 = createTestProfile(user2);

        // Both threads use the SAME providerTransactionId and sessionId
        String sharedTxId = "dup_tx_" + UUID.randomUUID();
        String sharedSessionId = "dup_session_" + UUID.randomUUID();

        // Mock provider to return success so orchestrate exercises the full path
        when(vnptVerificationPort.verifyTransaction(anyString(), anyString()))
                .thenReturn(VnptServerVerificationResult.success(
                        sharedTxId, sharedSessionId, "SUCCESS", Instant.now(),
                        "012345678901", "Test Name", "1990-01-01", "ref"
                ));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflict409Count = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        UUID[] userIds = {user1.getId(), user2.getId()};

        for (int i = 0; i < threads; i++) {
            final UUID threadUserId = userIds[i];
            executor.submit(() -> {
                try {
                    latch.await();
                    KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(
                            sharedSessionId, sharedTxId, Map.of()
                    );
                    VnptSdkDecision sdkDecision = new VnptSdkDecision(true, Map.of(), List.of());

                    VnptVerificationCoordinator.VerificationOutcome outcome =
                            coordinator.orchestrate(threadUserId, request, sdkDecision, "127.0.0.1", "agent");

                    if (outcome.finalStatus() == IdentityVerificationStatus.VERIFIED
                            || outcome.finalStatus() == IdentityVerificationStatus.PENDING_SERVER_VERIFICATION) {
                        successCount.incrementAndGet();
                    }
                } catch (BusinessException ex) {
                    if (ex.getHttpStatus() == HttpStatus.CONFLICT) {
                        conflict409Count.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(ex);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await();
        executor.shutdown();

        // Assert: 0 unexpected exceptions
        assertTrue(unexpectedExceptions.isEmpty(),
                "Unexpected exceptions: " + unexpectedExceptions);

        // Assert: exactly 1 success, exactly 1 conflict 409
        assertEquals(1, successCount.get(), "Exactly one thread should succeed");
        assertEquals(1, conflict409Count.get(), "Exactly one thread should get 409 CONFLICT");

        // Assert: DB has exactly 1 KycRequest with this transaction ID
        long dbCount = transactionTemplate.execute(status -> {
            String jpql = "SELECT COUNT(r) FROM KycRequest r WHERE r.providerTransactionId = :txId";
            return entityManager.createQuery(jpql, Long.class)
                    .setParameter("txId", sharedTxId)
                    .getSingleResult();
        });
        assertEquals(1L, dbCount, "DB must have exactly 1 KycRequest with the duplicate txId");

        // Assert: DUPLICATE_TRANSACTION audit exists with non-null targetId (teacherProfileId)
        List<AuditLog> dupAudits = auditLogRepository.findAll().stream()
                .filter(a -> "KYC_DUPLICATE_TRANSACTION".equals(a.getAction()))
                .toList();

        assertFalse(dupAudits.isEmpty(), "DUPLICATE_TRANSACTION audit must exist");
        assertNotNull(dupAudits.get(0).getTargetId(), "audit targetId must not be null");
    }

    @Test
    void sameTeacherConcurrentAttempt() throws Exception {
        AppUser user = createTestUser();
        TeacherProfile profile = createTestProfile(user);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String txId = "tx_" + index + "_" + UUID.randomUUID();
                    String sessionId = "session_" + index + "_" + UUID.randomUUID();

                    KycIdentityVerificationRequest request = new KycIdentityVerificationRequest(sessionId, txId, Map.of());
                    VnptSdkDecision sdkDecision = new VnptSdkDecision(true, Map.of(), List.of());

                    VnptVerificationCoordinator.BindResult result = coordinator.bindVerificationAttempt(user.getId(), request, sdkDecision, "127.0.0.1", "agent");
                    if (result.status() == VnptVerificationCoordinator.BindResult.BindStatus.NEEDS_SERVER_CALL) {
                        successCount.incrementAndGet();
                    } else if (result.status() == VnptVerificationCoordinator.BindResult.BindStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (BusinessException ex) {
                    if (ex.getHttpStatus() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(ex);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await();
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(),
                "Unexpected exceptions: " + unexpectedExceptions);
        assertEquals(1, successCount.get());
        assertEquals(threads - 1, conflictCount.get());
    }
}
