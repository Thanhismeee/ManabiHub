package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherCertificateClaim;
import com.manabihub.kyc.repository.TeacherCertificateClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherCertificateClaimServiceTest {

    @Mock
    private TeacherCertificateClaimRepository claimRepository;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private TeacherCredentialRegistry credentialRegistry;

    private TeacherCertificateClaimService service;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new TeacherCertificateClaimService(claimRepository, securityAuditService, credentialRegistry);
        actor = new AppUser();
        actor.setId(UUID.randomUUID());
    }

    @Test
    void normalizeJlptCertificateCode_removesFormattingAndUppercases() {
        assertEquals(
                "JLPT20260001",
                service.normalizeJlptCertificateCode(" jlpt-2026 0001 ")
        );
    }

    @Test
    void processCertificateClaim_createsUniqueJlptClaim() {
        UUID teacherId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(claimRepository.findByCertificateTypeAndNormalizedCertificateCode(
                "JLPT",
                "JLPT20260001"
        )).thenReturn(Optional.empty());

        String normalized = service.processCertificateClaim(
                teacherId,
                requestId,
                "JLPT-2026-0001",
                actor,
                "127.0.0.1",
                "JUnit"
        );

        assertEquals("JLPT20260001", normalized);
        ArgumentCaptor<TeacherCertificateClaim> captor =
                ArgumentCaptor.forClass(TeacherCertificateClaim.class);
        verify(claimRepository).saveAndFlush(captor.capture());
        assertEquals(teacherId, captor.getValue().getTeacherId());
        assertEquals(requestId, captor.getValue().getKycRequestId());
    }

    @Test
    void processCertificateClaim_isIdempotentForSameTeacher() {
        UUID teacherId = UUID.randomUUID();
        UUID newRequestId = UUID.randomUUID();
        TeacherCertificateClaim existing = TeacherCertificateClaim.builder()
                .teacherId(teacherId)
                .kycRequestId(UUID.randomUUID())
                .certificateType("JLPT")
                .normalizedCertificateCode("JLPT20260001")
                .build();
        when(claimRepository.findByCertificateTypeAndNormalizedCertificateCode(
                "JLPT",
                "JLPT20260001"
        )).thenReturn(Optional.of(existing));

        service.processCertificateClaim(
                teacherId,
                newRequestId,
                "JLPT-2026-0001",
                actor,
                "127.0.0.1",
                "JUnit"
        );

        assertEquals(newRequestId, existing.getKycRequestId());
        verify(claimRepository).save(existing);
    }

    @Test
    void processCertificateClaim_blocksAnotherTeacherAndWritesSecurityAudit() {
        UUID claimantTeacherId = UUID.randomUUID();
        TeacherCertificateClaim existing = TeacherCertificateClaim.builder()
                .teacherId(UUID.randomUUID())
                .kycRequestId(UUID.randomUUID())
                .certificateType("JLPT")
                .normalizedCertificateCode("JLPT20260001")
                .build();
        when(claimRepository.findByCertificateTypeAndNormalizedCertificateCode(
                "JLPT",
                "JLPT20260001"
        )).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.processCertificateClaim(
                        claimantTeacherId,
                        UUID.randomUUID(),
                        "JLPT-2026-0001",
                        actor,
                        "127.0.0.1",
                        "JUnit"
                )
        );

        assertEquals(MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED, exception.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(securityAuditService).logDuplicateCertificateAudit(
                claimantTeacherId,
                actor.getId(),
                "127.0.0.1",
                "JUnit"
        );
        verify(claimRepository, never()).saveAndFlush(any());
    }
}
