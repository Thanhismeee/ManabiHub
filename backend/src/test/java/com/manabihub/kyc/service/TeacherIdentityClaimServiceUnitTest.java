package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherIdentityClaim;
import com.manabihub.kyc.repository.TeacherIdentityClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherIdentityClaimServiceUnitTest {

    @Mock
    private TeacherIdentityClaimRepository claimRepository;
    @Mock
    private SecurityAuditService securityAuditService;

    private TeacherIdentityClaimService claimService;
    private final String testSecret = "test-secret-key-1234567890-32chars-min-length";

    @BeforeEach
    void setUp() {
        claimService = new TeacherIdentityClaimService(
                claimRepository,
                securityAuditService,
                testSecret
        );
        claimService.afterPropertiesSet();
    }

    @Test
    void secretValidation_FailsFast_WhenMissingOrTooShort() {
        TeacherIdentityClaimService emptySecretService = new TeacherIdentityClaimService(
                claimRepository,
                securityAuditService,
                ""
        );
        assertThrows(IllegalStateException.class, emptySecretService::afterPropertiesSet);

        TeacherIdentityClaimService shortSecretService = new TeacherIdentityClaimService(
                claimRepository,
                securityAuditService,
                "short-secret"
        );
        assertThrows(IllegalStateException.class, shortSecretService::afterPropertiesSet);
    }

    @Test
    void normalizeCccd_StripsSpacesAndHyphens_Validates12Digits() {
        String inputWithSpaces = "012 345 678 901";
        String normalized = claimService.normalizeCccd(inputWithSpaces);
        assertEquals("012345678901", normalized);

        String inputWithHyphens = "012-345-678-901";
        assertEquals("012345678901", claimService.normalizeCccd(inputWithHyphens));
    }

    @Test
    void normalizeCccd_ThrowsException_WhenNot12Digits() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> claimService.normalizeCccd("12345")
        );
        assertEquals(MessageCodes.MSG_KYC_002, ex.getMessageCode());
        assertTrue(ex.getMessage().contains("12 chữ số"));
    }

    @Test
    void generateFingerprint_ProducesConsistent64HexChars() {
        String cccd = "012345678901";
        String fp1 = claimService.generateFingerprint(cccd);
        String fp2 = claimService.generateFingerprint(cccd);

        assertNotNull(fp1);
        assertEquals(64, fp1.length());
        assertEquals(fp1, fp2);
    }

    @Test
    void processIdentityClaim_Idempotent_ForSameTeacher() {
        UUID teacherId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        String cccd = "012 345 678 901";
        String fingerprint = claimService.generateFingerprint("012345678901");

        TeacherIdentityClaim existingClaim = TeacherIdentityClaim.builder()
                .teacherId(teacherId)
                .identityFingerprint(fingerprint)
                .build();

        when(claimRepository.findByIdentityFingerprint(fingerprint)).thenReturn(Optional.of(existingClaim));

        assertDoesNotThrow(() -> claimService.processIdentityClaim(teacherId, cccd, user, "127.0.0.1", "TestAgent"));

        verify(claimRepository).save(existingClaim);
        verifyNoInteractions(securityAuditService);
    }

    @Test
    void processIdentityClaim_ThrowsConflictAndLogsAudit_WhenClaimedByDifferentTeacher() {
        UUID teacherAId = UUID.randomUUID();
        UUID teacherBId = UUID.randomUUID();
        AppUser teacherBUser = new AppUser();
        teacherBUser.setId(UUID.randomUUID());

        String cccd = "012-345-678-901";
        String fingerprint = claimService.generateFingerprint("012345678901");

        TeacherIdentityClaim claimTeacherA = TeacherIdentityClaim.builder()
                .teacherId(teacherAId)
                .identityFingerprint(fingerprint)
                .build();

        when(claimRepository.findByIdentityFingerprint(fingerprint)).thenReturn(Optional.of(claimTeacherA));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> claimService.processIdentityClaim(teacherBId, cccd, teacherBUser, "127.0.0.1", "TestAgent")
        );

        assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

        verify(securityAuditService).logDuplicateIdentityAudit(teacherBId, teacherBUser.getId(), "127.0.0.1", "TestAgent");
    }

    @Test
    void processIdentityClaim_HandlesExplicitFingerprintConstraintViolation() {
        UUID teacherId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        String cccd = "012345678901";

        when(claimRepository.findByIdentityFingerprint(any())).thenReturn(Optional.empty());
        when(claimRepository.findByTeacherId(teacherId)).thenReturn(Optional.empty());

        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "duplicate key value violates unique constraint uk_teacher_identity_claims_fingerprint",
                new SQLException("duplicate key value violates unique constraint uk_teacher_identity_claims_fingerprint", "23505"),
                "uk_teacher_identity_claims_fingerprint"
        );

        when(claimRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("Constraint violation", cve)
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> claimService.processIdentityClaim(teacherId, cccd, user, "127.0.0.1", "TestAgent")
        );

        assertEquals(MessageCodes.MSG_KYC_008, ex.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

        verify(securityAuditService).logDuplicateIdentityAudit(teacherId, user.getId(), "127.0.0.1", "TestAgent");
    }

    @Test
    void processIdentityClaim_RethrowsUnrelatedDatabaseErrors() {
        UUID teacherId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        String cccd = "012345678901";

        when(claimRepository.findByIdentityFingerprint(any())).thenReturn(Optional.empty());
        when(claimRepository.findByTeacherId(teacherId)).thenReturn(Optional.empty());

        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "foreign key constraint violation fk_teacher_id",
                new SQLException("foreign key constraint violation fk_teacher_id", "23503"),
                "fk_teacher_id"
        );

        when(claimRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("FK violation", cve)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> claimService.processIdentityClaim(teacherId, cccd, user, "127.0.0.1", "TestAgent")
        );

        verifyNoInteractions(securityAuditService);
    }
}
