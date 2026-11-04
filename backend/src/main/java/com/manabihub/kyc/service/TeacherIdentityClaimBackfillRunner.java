package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.TeacherIdentityClaim;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherIdentityClaimRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class TeacherIdentityClaimBackfillRunner {

    private static final int PAGE_SIZE = 100;
    private static final UUID TEACHER_ROLE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private final KycRequestRepository kycRequestRepository;
    private final TeacherIdentityClaimRepository claimRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherIdentityClaimService claimService;
    private final SecurityAuditService securityAuditService;
    private final EntityManager entityManager;

    public TeacherIdentityClaimBackfillRunner(
            KycRequestRepository kycRequestRepository,
            TeacherIdentityClaimRepository claimRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherIdentityClaimService claimService,
            SecurityAuditService securityAuditService,
            EntityManager entityManager
    ) {
        this.kycRequestRepository = kycRequestRepository;
        this.claimRepository = claimRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.claimService = claimService;
        this.securityAuditService = securityAuditService;
        this.entityManager = entityManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillExistingIdentityClaims() {
        log.info("Checking for unmapped historical KYC identity claims to backfill...");

        Map<String, Set<UUID>> fingerprintToTeachers = new HashMap<>();

        int page = 0;
        Page<KycRequest> requestPage;
        do {
            try {
                requestPage = kycRequestRepository.findAll(
                        PageRequest.of(page, PAGE_SIZE, Sort.by("createdAt").ascending())
                );
            } catch (Exception e) {
                break;
            }

            if (requestPage == null || requestPage.getContent() == null || requestPage.getContent().isEmpty()) {
                break;
            }

            for (KycRequest request : requestPage.getContent()) {
                if (request.getIdentityStatus() != IdentityVerificationStatus.VERIFIED
                        || request.getTeacherProfile() == null) {
                    continue;
                }

                String idNumber = extractIdNumber(request.getVerificationPayload());
                if (StringUtils.hasText(idNumber)) {
                    try {
                        String normalized = claimService.normalizeCccd(idNumber);
                        String fingerprint = claimService.generateFingerprint(normalized);

                        fingerprintToTeachers
                                .computeIfAbsent(fingerprint, k -> new HashSet<>())
                                .add(request.getTeacherProfile().getId());
                    } catch (Exception ex) {
                        log.warn("Could not parse CCCD for backfill request {}: {}", request.getId(), ex.getMessage());
                    }
                }
            }

            page++;
        } while (requestPage.hasNext());

        int backfilledCount = 0;
        int quarantinedCount = 0;

        for (Map.Entry<String, Set<UUID>> entry : fingerprintToTeachers.entrySet()) {
            String fingerprint = entry.getKey();
            Set<UUID> teacherIds = entry.getValue();

            if (teacherIds.size() == 1) {
                UUID teacherId = teacherIds.iterator().next();
                if (claimRepository.findByTeacherId(teacherId).isEmpty()
                        && claimRepository.findByIdentityFingerprint(fingerprint).isEmpty()) {
                    TeacherIdentityClaim claim = TeacherIdentityClaim.builder()
                            .teacherId(teacherId)
                            .identityFingerprint(fingerprint)
                            .build();
                    claimRepository.save(claim);
                    backfilledCount++;
                }
            } else {
                // MULTIPLE HISTORICAL TEACHERS HAVE THE SAME CCCD!
                // STRICT FAIL-CLOSED QUARANTINE: Revoke publishing rights, KYC status, and TEACHER database role!
                log.warn("Historical duplicate CCCD detected across {} teachers for fingerprint. Revoking rights and TEACHER role.", teacherIds.size());
                for (UUID teacherId : teacherIds) {
                    teacherProfileRepository.findById(teacherId).ifPresent(profile -> {
                        profile.setKycStatus(TeacherKycStatus.REJECTED);
                        profile.setCanPublishCourse(false);
                        teacherProfileRepository.save(profile);

                        if (profile.getUser() != null && profile.getUser().getId() != null) {
                            revokeTeacherRole(profile.getUser().getId());
                        }
                    });

                    securityAuditService.logBackfillQuarantineAudit(teacherId, teacherIds.size());
                    quarantinedCount++;
                }
            }
        }

        log.info("Identity claim backfill finished. Backfilled: {}, Quarantined conflicting profiles: {}.", backfilledCount, quarantinedCount);
    }

    private void revokeTeacherRole(UUID userId) {
        int deleted = entityManager.createNativeQuery(
                "DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .executeUpdate();
        log.info("Revoked TEACHER role for user {}: {} row(s) deleted from user_roles.", userId, deleted);
    }

    private String extractIdNumber(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object ocrObj = payload.get("identityOcr");
        if (ocrObj instanceof Map<?, ?> ocrMap) {
            Object idVal = ocrMap.get("idNumber");
            return idVal != null ? String.valueOf(idVal) : null;
        }
        return null;
    }
}
