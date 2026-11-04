package com.manabihub.kyc.service;

import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.CertificateVerificationStatus;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.VnptIdentityTransactionClaim;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VnptVerificationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(VnptVerificationCoordinator.class);

    private static final String VNPT_PROVIDER = "VNPT_EKYC_WEB_SDK";
    private static final Duration SERVER_VERIFICATION_TTL = Duration.ofMinutes(30);
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_SERVER_VERIFICATION_ATTEMPTS = 3;
    /** Provider reason codes meaning "the provider could not verify", not "the identity failed". */
    private static final java.util.Set<String> PROVIDER_UNAVAILABLE_REASONS =
            java.util.Set.of("PROVIDER_NOT_CONFIGURED");
    private static final Duration RETRY_COOLDOWN = Duration.ofSeconds(30);

    private final TeacherProfileRepository teacherProfileRepository;
    private final KycRequestRepository kycRequestRepository;
    private final VnptVerificationPort vnptVerificationPort;
    private final TeacherIdentityClaimService teacherIdentityClaimService;
    private final SecurityAuditService securityAuditService;
    private final VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<VnptVerificationCoordinator> selfProvider;

    public VnptVerificationCoordinator(
            TeacherProfileRepository teacherProfileRepository,
            KycRequestRepository kycRequestRepository,
            VnptVerificationPort vnptVerificationPort,
            TeacherIdentityClaimService teacherIdentityClaimService,
            SecurityAuditService securityAuditService,
            VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository,
            Clock clock,
            org.springframework.beans.factory.ObjectProvider<VnptVerificationCoordinator> selfProvider
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.kycRequestRepository = kycRequestRepository;
        this.vnptVerificationPort = vnptVerificationPort;
        this.teacherIdentityClaimService = teacherIdentityClaimService;
        this.securityAuditService = securityAuditService;
        this.vnptIdentityTransactionClaimRepository = vnptIdentityTransactionClaimRepository;
        this.clock = clock;
        this.selfProvider = selfProvider;
    }

    public record BindResult(
            BindStatus status,
            UUID requestId,
            UUID teacherProfileId,
            UUID userId,
            String providerTransactionId,
            String providerSessionId,
            boolean auditLogged
    ) {
        public enum BindStatus {
            NEEDS_SERVER_CALL,
            IDEMPOTENT_VERIFIED,
            TERMINAL_FAILED,
            CONFLICT
        }
    }

    public record ApplyResult(
            IdentityVerificationStatus finalStatus,
            boolean claimProcessed
    ) {}

    public record VerificationOutcome(
            UUID requestId,
            UUID teacherProfileId,
            UUID userId,
            IdentityVerificationStatus finalStatus,
            boolean auditLogged
    ) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BindResult bindVerificationAttempt(
            UUID userId,
            KycIdentityVerificationRequest request,
            VnptSdkDecision sdkDecision,
            String ipAddress,
            String userAgent
    ) {
        String incTxId = blankToNull(request.providerTransactionId());
        String incSessionId = blankToNull(request.providerSessionId());

        TeacherProfile teacherProfile = teacherProfileRepository.findForUpdateByUserId(userId)
                .orElseGet(() -> {
                    teacherProfileRepository.createCandidateIfAbsent(UUID.randomUUID(), userId);
                    return teacherProfileRepository.findForUpdateByUserId(userId)
                            .orElseThrow(() -> new BusinessException(MessageCodes.MSG_ADM_002, "Failed to resolve teacher profile"));
                });
        AppUser user = teacherProfile.getUser();

        if (user.getUserStatus() != com.manabihub.kyc.domain.UserStatus.ACTIVE) {
            throw new BusinessException(MessageCodes.MSG_ADM_002, "Teacher account is not allowed to verify identity", HttpStatus.FORBIDDEN);
        }
        if (teacherProfile.getKycStatus() == com.manabihub.kyc.domain.TeacherKycStatus.APPROVED) {
            throw new BusinessException(MessageCodes.KYC_ALREADY_APPROVED, "Approved KYC cannot be verified again", HttpStatus.CONFLICT);
        }

        KycRequest latestRequest = kycRequestRepository
                .findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                .orElse(null);

        if (latestRequest != null && latestRequest.getStatus() == KycRequestStatus.PENDING) {
            throw new BusinessException(MessageCodes.KYC_ALREADY_PENDING, "Cannot verify identity while KYC is under review", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now(clock);

        if (latestRequest != null && latestRequest.getStatus() == KycRequestStatus.DRAFT) {
            String boundTx = latestRequest.getProviderTransactionId();
            String boundSession = latestRequest.getProviderSessionId();

            if (boundTx != null) {
                boolean txMatch = incTxId != null && incTxId.equals(boundTx);
                boolean sessionMatch = incSessionId != null && incSessionId.equals(boundSession);

                if (txMatch && sessionMatch) {
                    if (latestRequest.getIdentityStatus() == IdentityVerificationStatus.VERIFIED) {
                        return new BindResult(BindResult.BindStatus.IDEMPOTENT_VERIFIED, latestRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, false);
                    }
                    if (latestRequest.getIdentityStatus() == IdentityVerificationStatus.FAILED) {
                        return new BindResult(BindResult.BindStatus.TERMINAL_FAILED, latestRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, false);
                    }

                    Instant expiresAt = latestRequest.getServerVerificationExpiresAt();
                    if (expiresAt != null && !now.isBefore(expiresAt)) {
                        latestRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
                        kycRequestRepository.saveAndFlush(latestRequest);
                        securityAuditService.logVerificationEvent("EXPIRED", teacherProfile.getId(), latestRequest.getId(), user.getId(), ipAddress, userAgent);
                        return new BindResult(BindResult.BindStatus.TERMINAL_FAILED, latestRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, true);
                    }

                    int attempts = latestRequest.getServerVerificationAttemptCount();
                    if (attempts >= MAX_SERVER_VERIFICATION_ATTEMPTS) {
                        latestRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
                        kycRequestRepository.saveAndFlush(latestRequest);
                        securityAuditService.logVerificationEvent("MAX_ATTEMPTS", teacherProfile.getId(), latestRequest.getId(), user.getId(), ipAddress, userAgent);
                        throw new BusinessException(MessageCodes.MSG_KYC_008, "Maximum verification attempts exceeded", HttpStatus.CONFLICT);
                    }

                    Instant nextRetryAt = latestRequest.getServerVerificationNextRetryAt();
                    if (nextRetryAt != null && now.isBefore(nextRetryAt)) {
                        throw new BusinessException(MessageCodes.MSG_KYC_008, "Verification in progress or cooling down", HttpStatus.CONFLICT);
                    }

                    latestRequest.setServerVerificationAttemptCount(attempts + 1);
                    latestRequest.setServerVerificationNextRetryAt(now.plus(RETRY_COOLDOWN));
                    kycRequestRepository.saveAndFlush(latestRequest);
                    return new BindResult(BindResult.BindStatus.NEEDS_SERVER_CALL, latestRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, false);
                } else {
                    String mismatchType = !txMatch ? "TRANSACTION_MISMATCH" : "SESSION_MISMATCH";
                    if (incTxId == null || incSessionId == null) {
                        mismatchType = "MISSING_PROVIDER_IDENTIFIERS";
                    } else if (kycRequestRepository.existsByEkycProviderAndProviderTransactionId(VNPT_PROVIDER, incTxId)) {
                        mismatchType = "DUPLICATE_TRANSACTION_ID";
                    }

                    securityAuditService.logVerificationEvent(mismatchType, teacherProfile.getId(), latestRequest.getId(), user.getId(), ipAddress, userAgent);

                    if ("DUPLICATE_TRANSACTION_ID".equals(mismatchType)) {
                        throw new BusinessException(MessageCodes.MSG_KYC_008, "Duplicate provider transaction", HttpStatus.CONFLICT);
                    }

                    return new BindResult(BindResult.BindStatus.CONFLICT, latestRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, true);
                }
            }
        }

        KycRequest kycRequest;
        if (latestRequest != null && latestRequest.getStatus() == KycRequestStatus.DRAFT && latestRequest.getProviderTransactionId() == null) {
            kycRequest = latestRequest;
        } else {
            kycRequest = new KycRequest();
        }

        if (incTxId == null || incTxId.isBlank() || incSessionId == null || incSessionId.isBlank()) {
            securityAuditService.logVerificationEvent("MISSING_PROVIDER_IDENTIFIERS", teacherProfile.getId(), kycRequest.getId(), user.getId(), ipAddress, userAgent);
            throw new BusinessException(MessageCodes.MSG_KYC_008, "Missing provider identifiers", HttpStatus.CONFLICT);
        }

        // Fast-path duplicate check (non-authoritative; DB constraint is the real guard)
        boolean isDuplicate = kycRequestRepository.existsByEkycProviderAndProviderTransactionId(VNPT_PROVIDER, incTxId);
        if (isDuplicate) {
            securityAuditService.logVerificationEvent("DUPLICATE_TRANSACTION_ID", teacherProfile.getId(), kycRequest.getId(), user.getId(), ipAddress, userAgent);
            throw new BusinessException(MessageCodes.MSG_KYC_008, "Duplicate transaction id", HttpStatus.CONFLICT);
        }

        boolean sdkPassed = sdkDecision != null && sdkDecision.verified();

        VnptIdentityTransactionClaim providerClaim = new VnptIdentityTransactionClaim();
        providerClaim.setUserId(userId);
        providerClaim.setSubjectType("TEACHER");
        providerClaim.setProvider(VNPT_PROVIDER);
        providerClaim.setProviderTransactionId(incTxId);
        providerClaim.setProviderSessionId(incSessionId);
        providerClaim.setClaimedAt(now);
        vnptIdentityTransactionClaimRepository.saveAndFlush(providerClaim);

        kycRequest.setId(kycRequest.getId() != null ? kycRequest.getId() : UUID.randomUUID());
        kycRequest.setTeacherProfile(teacherProfile);
        kycRequest.setStatus(KycRequestStatus.DRAFT);
        kycRequest.setEkycProvider(VNPT_PROVIDER);
        kycRequest.setProviderTransactionId(incTxId);
        kycRequest.setProviderSessionId(incSessionId);
        kycRequest.setServerVerificationExpiresAt(now.plus(SERVER_VERIFICATION_TTL));
        kycRequest.setServerVerificationAttemptCount(1);
        kycRequest.setServerVerificationNextRetryAt(now.plus(RETRY_COOLDOWN));

        Map<String, Object> payload = new HashMap<>();
        if (sdkDecision != null && sdkDecision.failureReasons() != null) {
            payload.put("failureReasons", sdkDecision.failureReasons());
        }
        kycRequest.setVerificationPayload(payload);

        if (!sdkPassed) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequest.setCertificateStatus(CertificateVerificationStatus.LOCKED);
            kycRequest = kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("SDK_FAILED", teacherProfile.getId(), kycRequest.getId(), user.getId(), ipAddress, userAgent);
            return new BindResult(BindResult.BindStatus.TERMINAL_FAILED, kycRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, true);
        }

        kycRequest.setIdentityStatus(IdentityVerificationStatus.PENDING_SERVER_VERIFICATION);
        kycRequest.setCertificateStatus(CertificateVerificationStatus.LOCKED);
        kycRequest = kycRequestRepository.saveAndFlush(kycRequest);
        return new BindResult(BindResult.BindStatus.NEEDS_SERVER_CALL, kycRequest.getId(), teacherProfile.getId(), userId, incTxId, incSessionId, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplyResult applyProviderResult(UUID requestId, UUID userId, String ipAddress, String userAgent, VnptServerVerificationResult result) {
        KycRequest kycRequest = kycRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(MessageCodes.MSG_KYC_008, "Request not found", HttpStatus.NOT_FOUND));

        if (kycRequest.getIdentityStatus() == IdentityVerificationStatus.VERIFIED) {
            return new ApplyResult(IdentityVerificationStatus.VERIFIED, false);
        }
        if (kycRequest.getIdentityStatus() == IdentityVerificationStatus.FAILED) {
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        UUID teacherProfileId = kycRequest.getTeacherProfile().getId();
        Instant now = Instant.now(clock);

        // Check absolute expiry first
        Instant expiresAt = kycRequest.getServerVerificationExpiresAt();
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("EXPIRED", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        if (result == null) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("PROVIDER_INVALID_RESPONSE", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        if (!result.verified()) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);

            Map<String, Object> payload = new HashMap<>(kycRequest.getVerificationPayload() != null ? kycRequest.getVerificationPayload() : Map.of());
            payload.put("providerStatus", result.providerStatus());
            if (result.reasonCode() != null) {
                payload.put("reasonCodes", java.util.List.of(result.reasonCode()));
            }
            kycRequest.setVerificationPayload(payload);
            kycRequestRepository.saveAndFlush(kycRequest);

            String auditType = "PROVIDER_NOT_CONFIGURED".equals(result.reasonCode()) ? "PROVIDER_NOT_CONFIGURED" : "PROVIDER_REJECTED";
            securityAuditService.logVerificationEvent(auditType, teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        if (result.confirmedTransactionId() == null || !result.confirmedTransactionId().equals(kycRequest.getProviderTransactionId())) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("TRANSACTION_MISMATCH", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }
        if (result.confirmedSessionId() == null || !result.confirmedSessionId().equals(kycRequest.getProviderSessionId())) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("SESSION_MISMATCH", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        Instant verifiedAt = result.providerVerifiedAt();
        if (verifiedAt == null) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("MISSING_TIMESTAMP", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        // Stale: compare against submittedAt - CLOCK_SKEW_TOLERANCE, not just now - 30min
        Instant submittedAt = kycRequest.getSubmittedAt();
        if (submittedAt != null && verifiedAt.isBefore(submittedAt.minus(CLOCK_SKEW_TOLERANCE))) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("STALE_TIMESTAMP", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }
        if (verifiedAt.isAfter(now.plus(CLOCK_SKEW_TOLERANCE))) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("FUTURE_TIMESTAMP", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }
        // providerVerifiedAt must not exceed the request's absolute expiry
        if (expiresAt != null && verifiedAt.isAfter(expiresAt)) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("EXPIRED", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        if (result.serverIdNumber() == null || result.serverIdNumber().isBlank() ||
            result.serverFullName() == null || result.serverFullName().isBlank() ||
            result.serverDateOfBirth() == null || result.serverDateOfBirth().isBlank()) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("INCOMPLETE_SERVER_IDENTITY", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        String canonicalCccd;
        java.time.LocalDate canonicalDob;
        try {
            canonicalCccd = teacherIdentityClaimService.normalizeCccd(result.serverIdNumber());
            canonicalDob = parseSupportedDate(result.serverDateOfBirth());
        } catch (Exception ex) {
            kycRequest.setIdentityStatus(IdentityVerificationStatus.FAILED);
            kycRequestRepository.saveAndFlush(kycRequest);
            securityAuditService.logVerificationEvent("INVALID_SERVER_IDENTITY", teacherProfileId, requestId, userId, ipAddress, userAgent);
            return new ApplyResult(IdentityVerificationStatus.FAILED, false);
        }

        String canonicalName = result.serverFullName().trim();

        kycRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        kycRequest.setServerFullName(canonicalName);
        kycRequest.setServerDateOfBirth(canonicalDob.toString());
        kycRequest.setServerVerifiedAt(result.providerVerifiedAt());
        kycRequest.setIdentityVerifiedAt(result.providerVerifiedAt());
        kycRequest.setCertificateStatus(CertificateVerificationStatus.NOT_SUBMITTED);

        Map<String, Object> payload = new HashMap<>(kycRequest.getVerificationPayload() != null ? kycRequest.getVerificationPayload() : Map.of());
        payload.put("providerStatus", result.providerStatus());
        if (result.maskedReference() != null) {
            payload.put("maskedReference", result.maskedReference());
        }
        kycRequest.setVerificationPayload(payload);

        kycRequestRepository.saveAndFlush(kycRequest);
        securityAuditService.logVerificationEvent("SERVER_VERIFIED", teacherProfileId, requestId, userId, ipAddress, userAgent);

        teacherIdentityClaimService.processIdentityClaim(
                kycRequest.getTeacherProfile().getId(),
                canonicalCccd,
                kycRequest.getTeacherProfile().getUser(),
                ipAddress,
                userAgent
        );

        return new ApplyResult(IdentityVerificationStatus.VERIFIED, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markClaimFailed(UUID requestId, UUID userId, String ipAddress, String userAgent) {
        kycRequestRepository.findByIdForUpdate(requestId).ifPresent(r -> {
            r.setIdentityStatus(IdentityVerificationStatus.FAILED);

            Map<String, Object> payload = new HashMap<>(r.getVerificationPayload() != null ? r.getVerificationPayload() : Map.of());
            payload.put("failureReason", "IDENTITY_CLAIM_FAILED");
            r.setVerificationPayload(payload);

            kycRequestRepository.saveAndFlush(r);
            securityAuditService.logVerificationEvent("IDENTITY_CLAIM_FAILED", r.getTeacherProfile().getId(), r.getId(), userId, ipAddress, userAgent);
        });
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VerificationOutcome recordProviderTimeout(UUID requestId, UUID userId, String ipAddress, String userAgent) {
        return recordProviderUnavailable(requestId, userId, ipAddress, userAgent, "PROVIDER_TIMEOUT");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VerificationOutcome recordProviderUnavailable(UUID requestId, UUID userId, String ipAddress, String userAgent, String auditType) {
        return kycRequestRepository.findByIdForUpdate(requestId).map(r -> {
            int attempts = r.getServerVerificationAttemptCount();
            if (attempts >= MAX_SERVER_VERIFICATION_ATTEMPTS) {
                r.setIdentityStatus(IdentityVerificationStatus.FAILED);
                kycRequestRepository.saveAndFlush(r);
                securityAuditService.logVerificationEvent("MAX_ATTEMPTS", r.getTeacherProfile().getId(), r.getId(), userId, ipAddress, userAgent);
                return new VerificationOutcome(requestId, r.getTeacherProfile().getId(), userId, IdentityVerificationStatus.FAILED, true);
            } else {
                securityAuditService.logVerificationEvent(auditType, r.getTeacherProfile().getId(), r.getId(), userId, ipAddress, userAgent);
                return new VerificationOutcome(requestId, r.getTeacherProfile().getId(), userId, IdentityVerificationStatus.PENDING_SERVER_VERIFICATION, true);
            }
        }).orElseGet(() -> new VerificationOutcome(requestId, null, userId, IdentityVerificationStatus.FAILED, false));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditDuplicateTransaction(UUID teacherProfileId, UUID userId, String ipAddress, String userAgent) {
        securityAuditService.logVerificationEvent("DUPLICATE_TRANSACTION", teacherProfileId, null, userId, ipAddress, userAgent);
    }

    public VerificationOutcome orchestrate(
            UUID userId,
            KycIdentityVerificationRequest request,
            VnptSdkDecision sdkDecision,
            String ipAddress,
            String userAgent
    ) {
        VnptVerificationCoordinator self = selfProvider.getObject();

        BindResult bindResult;
        try {
            bindResult = self.bindVerificationAttempt(userId, request, sdkDecision, ipAddress, userAgent);
        } catch (DataIntegrityViolationException dive) {
            if (isUniqueConstraint(dive, "uq_kyc_requests_provider_tx")
                    || isUniqueConstraint(dive, "uq_vnpt_identity_claim_provider_transaction")) {
                UUID teacherProfileId = resolveTeacherProfileIdSafe(userId);
                self.auditDuplicateTransaction(teacherProfileId, userId, ipAddress, userAgent);
                throw new BusinessException(MessageCodes.MSG_KYC_008, "Duplicate provider transaction", HttpStatus.CONFLICT);
            }
            throw dive;
        }

        switch (bindResult.status()) {
            case IDEMPOTENT_VERIFIED:
                return new VerificationOutcome(bindResult.requestId(), bindResult.teacherProfileId(), bindResult.userId(), IdentityVerificationStatus.VERIFIED, bindResult.auditLogged());
            case TERMINAL_FAILED:
                return new VerificationOutcome(bindResult.requestId(), bindResult.teacherProfileId(), bindResult.userId(), IdentityVerificationStatus.FAILED, bindResult.auditLogged());
            case CONFLICT:
                throw new BusinessException(MessageCodes.MSG_KYC_008, "Verification conflict", HttpStatus.CONFLICT);
            default:
                break;
        }

        VnptServerVerificationResult providerResult;
        try {
            providerResult = vnptVerificationPort.verifyTransaction(
                    bindResult.providerTransactionId(),
                    bindResult.providerSessionId()
            );
        } catch (Exception ex) {
            log.warn("VNPT server verification network/timeout error for tx: {}", bindResult.providerTransactionId(), ex);
            self.recordProviderTimeout(bindResult.requestId(), bindResult.userId(), ipAddress, userAgent);
            throw providerUnavailable();
        }

        if (isProviderUnavailable(providerResult)) {
            log.warn("VNPT server verification unavailable ({}) for tx: {}",
                    providerResult.reasonCode(), bindResult.providerTransactionId());
            self.recordProviderUnavailable(bindResult.requestId(), bindResult.userId(),
                    ipAddress, userAgent, providerResult.reasonCode());
            throw providerUnavailable();
        }

        try {
            ApplyResult applyResult = self.applyProviderResult(bindResult.requestId(), bindResult.userId(), ipAddress, userAgent, providerResult);
            return new VerificationOutcome(bindResult.requestId(), bindResult.teacherProfileId(), bindResult.userId(), applyResult.finalStatus(), true);
        } catch (BusinessException ex) {
            self.markClaimFailed(bindResult.requestId(), bindResult.userId(), ipAddress, userAgent);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error applying VNPT server result", ex);
            self.markClaimFailed(bindResult.requestId(), bindResult.userId(), ipAddress, userAgent);
            return new VerificationOutcome(bindResult.requestId(), bindResult.teacherProfileId(), bindResult.userId(), IdentityVerificationStatus.FAILED, true);
        }
    }

    private boolean isProviderUnavailable(VnptServerVerificationResult result) {
        return result != null
                && !result.verified()
                && PROVIDER_UNAVAILABLE_REASONS.contains(result.reasonCode());
    }

    private BusinessException providerUnavailable() {
        return new BusinessException(
                MessageCodes.MSG_KYC_009,
                "VNPT identity verification is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private UUID resolveTeacherProfileIdSafe(UUID userId) {
        try {
            return teacherProfileRepository.findByUserId(userId)
                    .map(tp -> tp.getId())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUniqueConstraint(DataIntegrityViolationException dive, String constraintName) {
        String message = dive.getMessage();
        if (message != null && message.contains(constraintName)) {
            return true;
        }
        Throwable cause = dive.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    private String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    private java.time.LocalDate parseSupportedDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new BusinessException(MessageCodes.MSG_KYC_002, "Missing date");
        }
        try {
            if (dateStr.contains("/")) {
                return java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else if (dateStr.contains("-")) {
                return java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            throw new BusinessException(MessageCodes.MSG_KYC_002, "Unsupported format");
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BusinessException(MessageCodes.MSG_KYC_002, "Invalid date format");
        }
    }
}
