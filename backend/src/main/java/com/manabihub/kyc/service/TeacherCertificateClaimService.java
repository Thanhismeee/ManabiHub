package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherCertificateClaim;
import com.manabihub.kyc.repository.TeacherCertificateClaimRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class TeacherCertificateClaimService {

    private static final String CERTIFICATE_TYPE = "JLPT";

    private final TeacherCertificateClaimRepository claimRepository;
    private final SecurityAuditService securityAuditService;
    private final TeacherCredentialRegistry credentialRegistry;

    public TeacherCertificateClaimService(
            TeacherCertificateClaimRepository claimRepository,
            SecurityAuditService securityAuditService,
            TeacherCredentialRegistry credentialRegistry
    ) {
        this.claimRepository = claimRepository;
        this.securityAuditService = securityAuditService;
        this.credentialRegistry = credentialRegistry;
    }

    public String normalizeJlptCertificateCode(String rawCertificateCode) {
        if (!StringUtils.hasText(rawCertificateCode)) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "JLPT certificate code is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalized = rawCertificateCode
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (normalized.length() < 4 || normalized.length() > 100) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "JLPT certificate code is invalid",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    @Transactional
    public String processCertificateClaim(
            UUID teacherId,
            UUID kycRequestId,
            String rawCertificateCode,
            AppUser actorUser,
            String ipAddress,
            String userAgent
    ) {
        String normalizedCode = normalizeJlptCertificateCode(rawCertificateCode);
        credentialRegistry.claim(teacherId, com.manabihub.course.enums.ProficiencyFramework.JLPT,
                com.manabihub.kyc.domain.TeacherCredentialType.OFFICIAL_CERTIFICATE, "JLPT", normalizedCode);
        var existing = claimRepository.findByCertificateTypeAndNormalizedCertificateCode(
                CERTIFICATE_TYPE,
                normalizedCode
        );

        if (existing.isPresent()) {
            TeacherCertificateClaim claim = existing.get();
            if (!claim.getTeacherId().equals(teacherId)) {
                blockDuplicate(teacherId, actorUser, ipAddress, userAgent);
            }
            claim.setKycRequestId(kycRequestId);
            claim.setUpdatedAt(Instant.now());
            claimRepository.save(claim);
            return normalizedCode;
        }

        TeacherCertificateClaim claim = TeacherCertificateClaim.builder()
                .teacherId(teacherId)
                .kycRequestId(kycRequestId)
                .certificateType(CERTIFICATE_TYPE)
                .normalizedCertificateCode(normalizedCode)
                .build();

        try {
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException exception) {
            if (isTypeCodeUniqueConstraintViolation(exception)) {
                blockDuplicate(teacherId, actorUser, ipAddress, userAgent);
            }
            throw exception;
        }

        return normalizedCode;
    }

    private void blockDuplicate(
            UUID teacherId,
            AppUser actorUser,
            String ipAddress,
            String userAgent
    ) {
        securityAuditService.logDuplicateCertificateAudit(
                teacherId,
                actorUser.getId(),
                ipAddress,
                userAgent
        );
        throw new BusinessException(
                MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED,
                "This JLPT certificate is already linked to another teacher account",
                HttpStatus.CONFLICT
        );
    }

    private boolean isTypeCodeUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName != null
                        && TeacherCertificateClaim.CONSTRAINT_UK_TYPE_CODE.equalsIgnoreCase(constraintName)) {
                    return true;
                }
            }
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().toLowerCase(Locale.ROOT)
                    .contains(TeacherCertificateClaim.CONSTRAINT_UK_TYPE_CODE)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
