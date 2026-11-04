package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherIdentityClaim;
import com.manabihub.kyc.repository.TeacherIdentityClaimRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class TeacherIdentityClaimService implements InitializingBean {

    public static final String CONSTRAINT_UK_FINGERPRINT = "uk_teacher_identity_claims_fingerprint";

    private final TeacherIdentityClaimRepository claimRepository;
    private final SecurityAuditService securityAuditService;
    private final String identitySecret;

    public TeacherIdentityClaimService(
            TeacherIdentityClaimRepository claimRepository,
            SecurityAuditService securityAuditService,
            @Value("${manabihub.kyc.identity-secret:}") String identitySecret
    ) {
        this.claimRepository = claimRepository;
        this.securityAuditService = securityAuditService;
        this.identitySecret = identitySecret;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(identitySecret)) {
            throw new IllegalStateException(
                    "KYC_IDENTITY_SECRET environment variable is missing. A secure, stable secret key is required for HMAC-SHA-256 identity fingerprinting."
            );
        }
        if (identitySecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "KYC_IDENTITY_SECRET is too short/weak (must be at least 32 characters long for security)."
            );
        }
    }

    /**
     * Normalizes raw CCCD string into exactly 12 digits.
     */
    public String normalizeCccd(String rawIdNumber) {
        if (!StringUtils.hasText(rawIdNumber)) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Số CCCD là bắt buộc để xác thực danh tính"
            );
        }

        String digits = rawIdNumber.replaceAll("[^0-9]", "");
        if (digits.length() != 12) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Số CCCD phải bao gồm đúng 12 chữ số"
            );
        }

        return digits;
    }

    /**
     * Generates HMAC-SHA-256 fingerprint for a normalized CCCD.
     */
    public String generateFingerprint(String normalizedCccd) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    identitySecret.trim().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(normalizedCccd.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Không thể tạo fingerprint bảo mật cho CCCD",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e
            );
        }
    }

    /**
     * Claims or updates an identity fingerprint for a teacher profile.
     * Idempotent for the same teacher using the same CCCD.
     * Throws HTTP 409 + MSG-KYC-008 and logs security audit if claimed by another teacher.
     */
    @Transactional
    public void processIdentityClaim(
            UUID teacherId,
            String rawIdNumber,
            AppUser actorUser,
            String ipAddress,
            String userAgent
    ) {
        String normalizedCccd = normalizeCccd(rawIdNumber);
        String fingerprint = generateFingerprint(normalizedCccd);

        Optional<TeacherIdentityClaim> existingByFingerprint = claimRepository.findByIdentityFingerprint(fingerprint);

        if (existingByFingerprint.isPresent()) {
            TeacherIdentityClaim claim = existingByFingerprint.get();
            if (!claim.getTeacherId().equals(teacherId)) {
                // Duplicate CCCD used by another teacher!
                securityAuditService.logDuplicateIdentityAudit(teacherId, actorUser.getId(), ipAddress, userAgent);
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "Số CCCD này đã được sử dụng bởi một tài khoản giáo viên khác",
                        HttpStatus.CONFLICT
                );
            }
            // Same teacher retry with same CCCD -> Idempotent!
            claim.setUpdatedAt(Instant.now());
            claimRepository.save(claim);
            return;
        }

        // Check if current teacher has a claim under a different fingerprint (updating their CCCD)
        Optional<TeacherIdentityClaim> existingByTeacher = claimRepository.findByTeacherId(teacherId);
        TeacherIdentityClaim claim = existingByTeacher.orElseGet(() -> TeacherIdentityClaim.builder()
                .teacherId(teacherId)
                .build());

        claim.setIdentityFingerprint(fingerprint);
        claim.setUpdatedAt(Instant.now());

        try {
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException ex) {
            if (isFingerprintUniqueConstraintViolation(ex)) {
                securityAuditService.logDuplicateIdentityAudit(teacherId, actorUser.getId(), ipAddress, userAgent);
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "Số CCCD này đã được sử dụng bởi một tài khoản giáo viên khác",
                        HttpStatus.CONFLICT
                );
            }
            // Rethrow any other database error (e.g. FK, NOT NULL, type mismatch)
            throw ex;
        }
    }

    private boolean isFingerprintUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && CONSTRAINT_UK_FINGERPRINT.equalsIgnoreCase(constraintName)) {
                    return true;
                }
            }
            if (cause instanceof java.sql.SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    String msg = sqlEx.getMessage();
                    if (msg != null && msg.toLowerCase().contains(CONSTRAINT_UK_FINGERPRINT.toLowerCase())) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
