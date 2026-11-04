package com.manabihub.kyc.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherIdentityClaim;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.dto.KycIdentityVerificationResponse;
import com.manabihub.kyc.dto.KycStatusResponse;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherIdentityClaimRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TeacherIdentityClaimDuplicatePostgresIntegrationTest {

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

    private static final UUID TEACHER_ROLE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    @Autowired
    private TeacherKycService teacherKycService;

    @Autowired
    private TeacherIdentityClaimService claimService;

    @Autowired
    private TeacherIdentityClaimBackfillRunner backfillRunner;

    @Autowired
    private TeacherIdentityClaimRepository claimRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private KycRequestRepository kycRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private AppUser createTestUser(String emailPrefix, String fullName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(emailPrefix + "_" + UUID.randomUUID() + "@test.com");
        user.setFullName(fullName);
        user.setProvider("LOCAL");
        user.setUserStatus(UserStatus.ACTIVE);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private TeacherProfile createTestProfile(AppUser user) {
        TeacherProfile profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setDisplayName(user.getFullName());
        profile.setKycStatus(TeacherKycStatus.NOT_SUBMITTED);
        profile.setCanPublishCourse(false);
        return teacherProfileRepository.save(profile);
    }

    private void grantTeacherRole(UUID userId) {
        entityManager.createNativeQuery(
                "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId) ON CONFLICT DO NOTHING"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .executeUpdate();
    }

    private long countTeacherRoles(UUID userId) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = :userId AND role_id = :roleId"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .getSingleResult();
        return count.longValue();
    }

    private KycIdentityVerificationRequest createMockSdkRequest(String idNumber, String name, String txId) {
        Map<String, Object> sdkResult = Map.of(
                "idNumber", idNumber,
                "fullName", name,
                "dateOfBirth", "01/01/1990",
                "liveness", "success",
                "faceMatch", true
        );
        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        return new KycIdentityVerificationRequest("sess-" + uniqueSuffix, txId, sdkResult);
    }

    private KycIdentityVerificationRequest createMockSdkRequest(String idNumber, String name) {
        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        return createMockSdkRequest(idNumber, name, "tx-" + uniqueSuffix);
    }

    private String generateUniqueCccdDigits() {
        long num = Math.abs(java.util.UUID.randomUUID().getLeastSignificantBits() % 1000000000L);
        return String.format("099%09d", num);
    }

    @Test
    void testEndToEndDuplicateProtectionAndAuditPersistence() {
        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String cccdNormalized = generateUniqueCccdDigits();
        String cccdRawWithSpaces = cccdNormalized.substring(0, 3) + " " + cccdNormalized.substring(3, 6) + " " + cccdNormalized.substring(6, 9) + " " + cccdNormalized.substring(9, 12);

        // 1. Create & commit Teacher A in a committed transaction so actor_user_id FK exists for audit logs
        UUID userAId = tx1.execute(status -> {
            AppUser userA = createTestUser("teacherA", "Nguyen Van A");
            TeacherProfile profileA = createTestProfile(userA);
            return userA.getId();
        });

        // 2. Teacher A successfully verifies CCCD in TX 1
        AtomicReference<KycIdentityVerificationRequest> reqARef = new AtomicReference<>();
        tx1.executeWithoutResult(status -> {
            KycIdentityVerificationRequest reqA = createMockSdkRequest(cccdRawWithSpaces, "Nguyen Van A");
            reqARef.set(reqA);
            com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(reqA.providerTransactionId(), reqA.providerSessionId(), cccdNormalized, "Nguyen Van A", "01/01/1990");
            KycIdentityVerificationResponse respA = teacherKycService.verifyIdentity(userAId, reqA, "127.0.0.1", "TestAgent");

            assertNotNull(respA);
            TeacherProfile profileA = teacherProfileRepository.findByUserId(userAId).orElseThrow();
            assertTrue(claimRepository.findByTeacherId(profileA.getId()).isPresent());
        });

        // 3. Teacher A retries with same CCCD -> Idempotent success
        tx1.executeWithoutResult(status -> {
            KycIdentityVerificationRequest originalReqA = reqARef.get();
            KycIdentityVerificationRequest retryReqA = new KycIdentityVerificationRequest(
                    originalReqA.providerSessionId(),
                    originalReqA.providerTransactionId(),
                    originalReqA.sdkResult()
            );
            
            KycIdentityVerificationResponse retryRespA = teacherKycService.verifyIdentity(userAId, retryReqA, "127.0.0.1", "TestAgent");
            assertNotNull(retryRespA);
        });

        // 4. Create & commit Teacher B user in committed transaction so REQUIRES_NEW audit log can reference actor_user_id FK
        UUID userBId = tx1.execute(status -> {
            AppUser userB = createTestUser("teacherB", "Nguyen Van A");
            TeacherProfile profileB = createTestProfile(userB);
            return userB.getId();
        });

        // 5. Teacher B attempts to use Teacher A's CCCD in separate TX 2
        TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
        tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        UUID teacherBId = tx2.execute(status -> {
            TeacherProfile profileB = teacherProfileRepository.findByUserId(userBId).orElseThrow();

            KycIdentityVerificationRequest reqB = createMockSdkRequest(cccdNormalized, "Nguyen Van A");
            com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(reqB.providerTransactionId(), reqB.providerSessionId(), cccdNormalized, "Nguyen Van A", "01/01/1990");

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> teacherKycService.verifyIdentity(userBId, reqB, "127.0.0.1", "TestAgent")
            );

            // Assert 409 Conflict + MSG-KYC-008
            assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

            // Assert Teacher B state protected: profile NOT approved, canPublishCourse remains false
            TeacherProfile reloadedB = teacherProfileRepository.findById(profileB.getId()).orElseThrow();
            assertFalse(reloadedB.isCanPublishCourse());
            assertEquals(TeacherKycStatus.NOT_SUBMITTED, reloadedB.getKycStatus());

            // Force rollback of TX 2
            status.setRollbackOnly();
            return profileB.getId();
        });

        // 6. Assert Security Audit Log committed & persisted in DB despite TX 2 rollback!
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        AuditLog duplicateAudit = auditLogs.stream()
                .filter(a -> "KYC_DUPLICATE_IDENTITY_DETECTED".equals(a.getAction()))
                .filter(a -> teacherBId.equals(a.getTargetId()))
                .findFirst()
                .orElse(null);

        assertNotNull(duplicateAudit, "Security audit log (REQUIRES_NEW) must persist after outer transaction rollback");
        assertEquals("TEACHER", duplicateAudit.getActorRoleCode());
        assertFalse(duplicateAudit.getAfterValue().toString().contains(cccdNormalized), "Audit log must NOT leak raw CCCD");
    }

    @Test
    void testConcurrentRace_TwoThreadsAttemptSameIdentity() throws Exception {
        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String cccdNormalized = generateUniqueCccdDigits();

        // 1. Create and commit Teacher A and Teacher B
        UUID userAId = tx1.execute(status -> {
            AppUser userA = createTestUser("raceA", "Nguyen Van A");
            createTestProfile(userA);
            return userA.getId();
        });

        UUID userBId = tx1.execute(status -> {
            AppUser userB = createTestUser("raceB", "Nguyen Van A");
            createTestProfile(userB);
            return userB.getId();
        });

        // 2. Setup 2 threads to race unblocked by a CountDownLatch
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicReference<Object> outcomeA = new AtomicReference<>();
        AtomicReference<Object> outcomeB = new AtomicReference<>();

        Future<?> f1 = executor.submit(() -> {
            try {
                startLatch.await();
                KycIdentityVerificationRequest req = createMockSdkRequest(cccdNormalized, "Nguyen Van A");
                com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(req.providerTransactionId(), req.providerSessionId(), cccdNormalized, "Nguyen Van A", "01/01/1990");
                KycIdentityVerificationResponse resp = teacherKycService.verifyIdentity(userAId, req, "127.0.0.1", "Thread-1");
                outcomeA.set(resp);
            } catch (Throwable t) {
                outcomeA.set(t);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                startLatch.await();
                KycIdentityVerificationRequest req = createMockSdkRequest(cccdNormalized, "Nguyen Van A");
                com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(req.providerTransactionId(), req.providerSessionId(), cccdNormalized, "Nguyen Van A", "01/01/1990");
                KycIdentityVerificationResponse resp = teacherKycService.verifyIdentity(userBId, req, "127.0.0.1", "Thread-2");
                outcomeB.set(resp);
            } catch (Throwable t) {
                outcomeB.set(t);
            }
        });

        // Release both threads simultaneously
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 3. Assert EXACTLY ONE thread succeeded and EXACTLY ONE thread failed with HTTP 409 Conflict / MSG-KYC-008
        boolean aSuccess = isVerificationSuccess(outcomeA.get());
        boolean bSuccess = isVerificationSuccess(outcomeB.get());

        if (!(aSuccess ^ bSuccess)) {
            fail("Concurrency race assertion failed! aSuccess=" + aSuccess + " [outcomeA=" + outcomeA.get() + "], bSuccess=" + bSuccess + " [outcomeB=" + outcomeB.get() + "]");
        }

        Throwable failure = (Throwable) (aSuccess ? outcomeB.get() : outcomeA.get());
        while (failure.getCause() != null && !(failure instanceof BusinessException)) {
            failure = failure.getCause();
        }

        assertTrue(failure instanceof BusinessException, "Losing thread must throw BusinessException");
        BusinessException busEx = (BusinessException) failure;
        assertEquals(MessageCodes.MSG_KYC_008, busEx.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, busEx.getHttpStatus());

        // 4. Assert database contains EXACTLY ONE claim row for this fingerprint
        String fingerprint = claimService.generateFingerprint(cccdNormalized);
        assertTrue(claimRepository.findByIdentityFingerprint(fingerprint).isPresent());
        assertEquals(1, claimRepository.findAll().stream().filter(c -> fingerprint.equals(c.getIdentityFingerprint())).count());

        // 5. Assert durable security audit log persisted for the losing attempt
        UUID losingUserId = aSuccess ? userBId : userAId;
        TeacherProfile losingProfile = teacherProfileRepository.findByUserId(losingUserId).orElseThrow();

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean auditFound = auditLogs.stream()
                .filter(a -> "KYC_DUPLICATE_IDENTITY_DETECTED".equals(a.getAction()))
                .anyMatch(a -> losingProfile.getId().equals(a.getTargetId()));

        assertTrue(auditFound, "Security audit log must be persisted for the losing thread in concurrency race");
    }

    private boolean isVerificationSuccess(Object outcome) {
        if (outcome instanceof KycIdentityVerificationResponse resp) {
            return resp.request() != null && "VERIFIED".equals(resp.request().identityStatus());
        }
        return false;
    }

    @Test
    void testDirectDatabaseUniqueConstraintViolationHandling() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String cccd = generateUniqueCccdDigits();

        tx.executeWithoutResult(status -> {
            AppUser userA = createTestUser("user1", "User 1");
            TeacherProfile profileA = createTestProfile(userA);

            String fingerprint = claimService.generateFingerprint(cccd);

            TeacherIdentityClaim claim1 = TeacherIdentityClaim.builder()
                    .teacherId(profileA.getId())
                    .identityFingerprint(fingerprint)
                    .build();
            claimRepository.saveAndFlush(claim1);

            AppUser userB = createTestUser("user2", "User 2");
            TeacherProfile profileB = createTestProfile(userB);

            // Directly save conflicting claim in DB to trigger UK constraint
            TeacherIdentityClaim claim2 = TeacherIdentityClaim.builder()
                    .teacherId(profileB.getId())
                    .identityFingerprint(fingerprint)
                    .build();

            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
                claimRepository.saveAndFlush(claim2);
            });

            status.setRollbackOnly();
        });
    }

    @Test
    void testHistoricalBackfillQuarantinesLegacyDuplicateCCCDsFailClosedAndRevokesTeacherRole() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String cccd = generateUniqueCccdDigits();
        String cccdFormatted = cccd.substring(0, 3) + " " + cccd.substring(3, 6) + " " + cccd.substring(6, 9) + " " + cccd.substring(9, 12);

        tx.executeWithoutResult(status -> {
            // Create two legacy profiles with identical CCCD in verificationPayload and seed TEACHER database roles
            AppUser legacyUser1 = createTestUser("legacy1", "Legacy User 1");
            TeacherProfile legacyProfile1 = createTestProfile(legacyUser1);
            legacyProfile1.setKycStatus(TeacherKycStatus.PENDING);
            legacyProfile1.setCanPublishCourse(true);
            teacherProfileRepository.save(legacyProfile1);
            grantTeacherRole(legacyUser1.getId());
            assertEquals(1, countTeacherRoles(legacyUser1.getId()), "Teacher 1 must have TEACHER database role prior to backfill");

            KycRequest req1 = new KycRequest();
            req1.setTeacherProfile(legacyProfile1);
            req1.setStatus(KycRequestStatus.DRAFT);
            req1.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
            req1.setVerificationPayload(Map.of("identityOcr", Map.of("idNumber", cccd)));
            kycRequestRepository.save(req1);

            AppUser legacyUser2 = createTestUser("legacy2", "Legacy User 2");
            TeacherProfile legacyProfile2 = createTestProfile(legacyUser2);
            legacyProfile2.setKycStatus(TeacherKycStatus.PENDING);
            legacyProfile2.setCanPublishCourse(true);
            teacherProfileRepository.save(legacyProfile2);
            grantTeacherRole(legacyUser2.getId());
            assertEquals(1, countTeacherRoles(legacyUser2.getId()), "Teacher 2 must have TEACHER database role prior to backfill");

            KycRequest req2 = new KycRequest();
            req2.setTeacherProfile(legacyProfile2);
            req2.setStatus(KycRequestStatus.DRAFT);
            req2.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
            req2.setVerificationPayload(Map.of("identityOcr", Map.of("idNumber", cccdFormatted)));
            kycRequestRepository.save(req2);

            // Execute backfill
            backfillRunner.backfillExistingIdentityClaims();

            String fingerprint = claimService.generateFingerprint(cccd);
            // Fail closed: Neither profile is automatically claimed
            assertTrue(claimRepository.findByIdentityFingerprint(fingerprint).isEmpty());
            assertTrue(claimRepository.findByTeacherId(legacyProfile1.getId()).isEmpty());
            assertTrue(claimRepository.findByTeacherId(legacyProfile2.getId()).isEmpty());

            // Assert FAIL-CLOSED: Rights revoked (kycStatus set to REJECTED and canPublishCourse set to false)
            TeacherProfile updatedProfile1 = teacherProfileRepository.findById(legacyProfile1.getId()).orElseThrow();
            TeacherProfile updatedProfile2 = teacherProfileRepository.findById(legacyProfile2.getId()).orElseThrow();

            assertEquals(TeacherKycStatus.REJECTED, updatedProfile1.getKycStatus());
            assertFalse(updatedProfile1.isCanPublishCourse());

            assertEquals(TeacherKycStatus.REJECTED, updatedProfile2.getKycStatus());
            assertFalse(updatedProfile2.isCanPublishCourse());

            // Assert TEACHER database role revoked from user_roles table for both users!
            assertEquals(0, countTeacherRoles(legacyUser1.getId()), "TEACHER role must be revoked from user_roles during quarantine");
            assertEquals(0, countTeacherRoles(legacyUser2.getId()), "TEACHER role must be revoked from user_roles during quarantine");

            // Quarantine audit log created for both
            List<AuditLog> quarantineAudits = auditLogRepository.findAll().stream()
                    .filter(a -> "KYC_BACKFILL_DUPLICATE_QUARANTINED".equals(a.getAction()))
                    .toList();
            assertFalse(quarantineAudits.isEmpty(), "Quarantine security audit log must be recorded for historical duplicates");

            status.setRollbackOnly();
        });
    }

    @Test
    void testTeacherKycStatusResponseSanitizesPii() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String cccdNormalized = generateUniqueCccdDigits();

        UUID userId = tx.execute(status -> {
            AppUser user = createTestUser("pii", "Nguyen Van A");
            TeacherProfile profile = createTestProfile(user);
            return user.getId();
        });

        tx.executeWithoutResult(status -> {
            KycIdentityVerificationRequest req = createMockSdkRequest(cccdNormalized, "Nguyen Van A");
            com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(req.providerTransactionId(), req.providerSessionId(), cccdNormalized, "Nguyen Van A", "01/01/1990");
            teacherKycService.verifyIdentity(userId, req, "127.0.0.1", "TestAgent");

            KycStatusResponse statusResp = teacherKycService.getStatus(userId);
            assertNotNull(statusResp);
            assertNotNull(statusResp.latestRequest());

            Map<String, Object> payload = statusResp.latestRequest().verificationPayload();
            // Assert providerResult removed
            assertFalse(payload.containsKey("providerResult"), "Teacher response must NOT contain providerResult");

            // Assert idNumber and identityOcr completely redacted/removed
            assertFalse(payload.containsKey("identityOcr"), "Teacher response must NOT contain OCR data");

            status.setRollbackOnly();
        });
    }
    @Test
    void testDuplicateProviderTransactionId_shouldReject() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String sharedTxId = "TEST-TX-" + UUID.randomUUID().toString();
        String cccd1 = generateUniqueCccdDigits();
        String cccd2 = generateUniqueCccdDigits();

        // 1. Create Teacher A & Teacher B
        UUID userAId = tx.execute(status -> {
            AppUser userA = createTestUser("teacherTxA", "Nguyen Van A");
            createTestProfile(userA);
            return userA.getId();
        });

        UUID userBId = tx.execute(status -> {
            AppUser userB = createTestUser("teacherTxB", "Nguyen Van B");
            createTestProfile(userB);
            return userB.getId();
        });

        // 2. Teacher A successfully verifies CCCD with sharedTxId
        tx.executeWithoutResult(status -> {
            KycIdentityVerificationRequest reqA = createMockSdkRequest(cccd1, "Nguyen Van A", sharedTxId);
            com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(reqA.providerTransactionId(), reqA.providerSessionId(), cccd1, "Nguyen Van A", "01/01/1990");
            KycIdentityVerificationResponse respA = teacherKycService.verifyIdentity(userAId, reqA, "127.0.0.1", "TestAgent");
            assertNotNull(respA);
        });

        // 3. Teacher B tries to verify using the SAME sharedTxId
        KycIdentityVerificationRequest reqB = createMockSdkRequest(cccd2, "Nguyen Van B", sharedTxId);
        com.manabihub.kyc.service.impl.TestVnptVerificationAdapter.allowTransaction(reqB.providerTransactionId(), reqB.providerSessionId(), cccd2, "Nguyen Van B", "01/01/1990");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> teacherKycService.verifyIdentity(userBId, reqB, "127.0.0.1", "TestAgent")
        );

        assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }
}
