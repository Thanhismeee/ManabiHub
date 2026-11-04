package com.manabihub.kyc.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.kyc.domain.CertificateVerificationStatus;
import com.manabihub.kyc.domain.IdentityVerificationStatus;
import com.manabihub.kyc.domain.KycDocument;
import com.manabihub.kyc.domain.KycDocumentType;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.domain.VnptIdentityTransactionClaim;
import com.manabihub.kyc.dto.KycCertificateSubmissionResponse;
import com.manabihub.kyc.dto.KycDocumentResponse;
import com.manabihub.kyc.dto.KycIdentityVerificationRequest;
import com.manabihub.kyc.dto.KycIdentityVerificationResponse;
import com.manabihub.kyc.dto.KycModuleStatusResponse;
import com.manabihub.kyc.dto.KycRequestResponse;
import com.manabihub.kyc.dto.KycRestartVerificationResponse;
import com.manabihub.kyc.dto.KycStatusResponse;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.mock.domain.MockNationalIdRegistryRecord;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import com.manabihub.notification.entity.Notification;
import com.manabihub.notification.repository.NotificationRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.security.service.PublicJwtTokenService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class TeacherKycService {

    private static final Logger log = LoggerFactory.getLogger(TeacherKycService.class);

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_OCR_TEXT_LENGTH = 20_000;
    private static final Set<String> CERTIFICATE_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final String VNPT_PROVIDER = "VNPT_EKYC_WEB_SDK";
    private static final String VNPT_SESSION_REPLAY_PREFIX = "SESSION:";
    private static final int MAX_VNPT_PROVIDER_ID_LENGTH = 128;
    private static final UUID TEACHER_ROLE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final String REVIEW_ETA =
            "1-2 business days, excluding Saturdays, Sundays, and public holidays";
    /** Server verification must complete within this duration after SDK result is submitted. */
    private static final Duration SERVER_VERIFICATION_TTL = Duration.ofMinutes(30);

    private final TeacherProfileRepository teacherProfileRepository;
    private final KycRequestRepository kycRequestRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TeacherIdentityClaimService teacherIdentityClaimService;
    private final TeacherCertificateClaimService teacherCertificateClaimService;
    private final PublicJwtTokenService publicJwtTokenService;
    private final VnptVerificationPort vnptVerificationPort;
    private final SecurityAuditService securityAuditService;
    private final MockNationalIdRegistryRepository mockNationalIdRegistryRepository;
    private final EntityManager entityManager;
    private final Path storageRoot;
    private final VnptVerificationCoordinator verificationCoordinator;
    private final VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    private final AccountIdentityVerificationService accountIdentityVerificationService;
    private final String identityVerificationMode;

    public TeacherKycService(
            TeacherProfileRepository teacherProfileRepository,
            KycRequestRepository kycRequestRepository,
            KycDocumentRepository kycDocumentRepository,
            AuditLogRepository auditLogRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            TeacherIdentityClaimService teacherIdentityClaimService,
            TeacherCertificateClaimService teacherCertificateClaimService,
            PublicJwtTokenService publicJwtTokenService,
            VnptVerificationPort vnptVerificationPort,
            SecurityAuditService securityAuditService,
            MockNationalIdRegistryRepository mockNationalIdRegistryRepository,
            EntityManager entityManager,
            VnptVerificationCoordinator verificationCoordinator,
            VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository,
            AccountIdentityVerificationService accountIdentityVerificationService,
            @Value("${manabihub.kyc.storage-root:storage/kyc}") String storageRoot,
            @Value("${manabihub.kyc.identity-verification-mode:direct-sdk-mock}") String identityVerificationMode
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.kycRequestRepository = kycRequestRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.teacherIdentityClaimService = teacherIdentityClaimService;
        this.teacherCertificateClaimService = teacherCertificateClaimService;
        this.publicJwtTokenService = publicJwtTokenService;
        this.vnptVerificationPort = vnptVerificationPort;
        this.securityAuditService = securityAuditService;
        this.mockNationalIdRegistryRepository = mockNationalIdRegistryRepository;
        this.entityManager = entityManager;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.verificationCoordinator = verificationCoordinator;
        this.vnptIdentityTransactionClaimRepository = vnptIdentityTransactionClaimRepository;
        this.accountIdentityVerificationService = accountIdentityVerificationService;
        this.identityVerificationMode = identityVerificationMode;
    }

    @Transactional
    public KycStatusResponse getStatus(UUID userId) {
        TeacherProfile teacherProfile = resolveTeacher(userId);
        KycRequest latestRequest = kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                .orElse(null);
        AccountIdentityVerification sharedVerification =
                accountIdentityVerificationService.findVerified(userId).orElse(null);
        if (sharedVerification != null) {
            latestRequest = ensureAccountIdentityRequest(teacherProfile, latestRequest, sharedVerification);
        }

        return new KycStatusResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getId(),
                teacherProfile.getKycStatus().name(),
                statusLabel(teacherProfile.getKycStatus()),
                teacherProfile.isCanPublishCourse(),
                identityModuleStatus(teacherProfile, latestRequest),
                certificateModuleStatus(teacherProfile, latestRequest),
                latestRequest == null ? null : toRequestResponse(latestRequest),
                srsTrace()
        );
    }

    @Transactional
    public KycIdentityVerificationResponse verifyIdentity(
            UUID userId,
            KycIdentityVerificationRequest request,
            String ipAddress,
            String userAgent
    ) {
        if (request == null) {
            throw new BusinessException(MessageCodes.MSG_KYC_002, "VNPT eKYC SDK result is required");
        }
        VnptSdkPayloadPolicy.validate(request.sdkResult());

        AccountIdentityVerification sharedVerification =
                accountIdentityVerificationService.findVerified(userId).orElse(null);
        if (sharedVerification != null) {
            TeacherProfile teacherProfile = resolveTeacher(userId);
            KycRequest latestRequest = kycRequestRepository
                    .findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                    .orElse(null);
            KycRequest sharedRequest = ensureAccountIdentityRequest(
                    teacherProfile,
                    latestRequest,
                    sharedVerification);
            return buildIdentityResponse(teacherProfile, sharedRequest, false);
        }

        if (usesDirectSdkVerification()) {
            return verifyIdentityFromSdk(
                    userId,
                    request,
                    ipAddress,
                    userAgent,
                    usesMockNationalIdRegistry()
            );
        }

        String incTxId = blankToNull(request.providerTransactionId());
        String incSessionId = blankToNull(request.providerSessionId());
        if (incTxId == null || incSessionId == null) {
            throw new BusinessException(MessageCodes.MSG_KYC_002, "Provider transaction ID and session ID are required");
        }

        VnptSdkDecision sdkDecision = VnptSdkResultEvaluator.evaluate(request.sdkResult());

        VnptVerificationCoordinator.VerificationOutcome outcome = verificationCoordinator.orchestrate(
            userId, request, sdkDecision, ipAddress, userAgent
        );

        TeacherProfile teacherProfile = teacherProfileRepository.findById(outcome.teacherProfileId())
                .orElseThrow();
        KycRequest kycRequest = kycRequestRepository.findById(outcome.requestId())
                .orElseThrow();

        if (resolvedIdentityStatus(kycRequest) == IdentityVerificationStatus.VERIFIED) {
            recordAccountIdentity(
                    userId,
                    sdkDecision.identityOcr(),
                    VNPT_PROVIDER,
                    kycRequest.getIdentityVerifiedAt(),
                    "TEACHER");
        }

        return buildIdentityResponse(teacherProfile, kycRequest, outcome.auditLogged());
    }

    /**
     * Direct SDK mode is intentionally explicit. It is useful for the AWS demo/UAT
     * deployment where the VNPT browser SDK is available but server-to-server
     * verification is not. Production tenants can opt into SERVER explicitly.
     */
    private KycIdentityVerificationResponse verifyIdentityFromSdk(
            UUID userId,
            KycIdentityVerificationRequest request,
            String ipAddress,
            String userAgent,
            boolean crossCheckMockNationalId
    ) {
        TeacherProfile teacherProfile = resolveTeacher(userId);
        AppUser user = teacherProfile.getUser();
        String providerTransactionId = blankToNull(request.providerTransactionId());
        String providerSessionId = blankToNull(request.providerSessionId());
        String providerReplayId = directSdkReplayId(
                providerTransactionId,
                providerSessionId,
                crossCheckMockNationalId
        );

        if (providerTransactionId != null) {
            KycRequest boundRequest = kycRequestRepository
                    .findByEkycProviderAndProviderTransactionId("VNPT_EKYC_WEB_SDK", providerTransactionId)
                    .orElse(null);
            if (boundRequest != null) {
                boolean sameTeacher = boundRequest.getTeacherProfile() != null
                        && teacherProfile.getId().equals(boundRequest.getTeacherProfile().getId());
                boolean sameSession = Objects.equals(
                        blankToNull(boundRequest.getProviderSessionId()), providerSessionId);
                if (sameTeacher && sameSession) {
                    // A browser callback can be retried after a slow network response.
                    // Return the canonical stored outcome without claiming the identity twice.
                    return buildIdentityResponse(teacherProfile, boundRequest, false);
                }
                securityAuditService.logVerificationEvent(
                        "DUPLICATE_TRANSACTION",
                        teacherProfile.getId(),
                        boundRequest.getId(),
                        user.getId(),
                        ipAddress,
                        userAgent
                );
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "VNPT transaction is already bound to another verification session",
                        HttpStatus.CONFLICT
                );
            }
        }

        KycRequest latestRequest = findLatestRequest(teacherProfile);
        validateIdentityAllowed(user, teacherProfile, latestRequest);

        KycRequest kycRequest = findReusableRealtimeRequest(teacherProfile, latestRequest);
        VnptSdkDecision sdkDecision = VnptSdkResultEvaluator.evaluate(request.sdkResult());
        boolean verified = sdkDecision.verified();
        List<String> failureReasons = new ArrayList<>(sdkDecision.failureReasons());
        Map<String, String> identityOcr = sdkDecision.identityOcr();

        String normalizedCccd = null;
        if (verified) {
            try {
                normalizedCccd = teacherIdentityClaimService.normalizeCccd(identityOcr.get("idNumber"));
                parseSupportedDate(identityOcr.get("dateOfBirth"));
                if (crossCheckMockNationalId) {
                    verified = crossCheckMockNationalId(
                            normalizedCccd,
                            identityOcr,
                            failureReasons
                    ) && verified;
                }
            } catch (BusinessException exception) {
                verified = false;
                failureReasons.add(exception.getMessage());
            }

            if (verified && normalizedCccd != null) {
                teacherIdentityClaimService.processIdentityClaim(
                        teacherProfile.getId(),
                        normalizedCccd,
                        user,
                        ipAddress,
                        userAgent
                );
                accountIdentityVerificationService.recordVerified(
                        user.getId(),
                        teacherIdentityClaimService.generateFingerprint(normalizedCccd),
                        VNPT_PROVIDER,
                        blankToNull(identityOcr.get("fullName")),
                        parseSupportedDate(identityOcr.get("dateOfBirth")),
                        Instant.now(),
                        "TEACHER");
            }
        }

        Instant now = Instant.now();
        kycRequest.setStatus(KycRequestStatus.DRAFT);
        kycRequest.setTeacherProfile(teacherProfile);
        kycRequest.setEkycProvider(VNPT_PROVIDER);
        kycRequest.setEkycReferenceId("VNPT-SDK-" + UUID.randomUUID());
        kycRequest.setProviderSessionId(providerSessionId);
        kycRequest.setProviderTransactionId(providerTransactionId);
        kycRequest.setIdentityStatus(verified ? IdentityVerificationStatus.VERIFIED : IdentityVerificationStatus.FAILED);
        kycRequest.setIdentityVerifiedAt(verified ? now : null);
        kycRequest.setCertificateStatus(
                verified ? CertificateVerificationStatus.NOT_SUBMITTED : CertificateVerificationStatus.LOCKED
        );
        kycRequest.setServerFullName(verified ? blankToNull(identityOcr.get("fullName")) : null);
        kycRequest.setServerDateOfBirth(verified ? blankToNull(identityOcr.get("dateOfBirth")) : null);

        Map<String, Object> verificationPayload = new LinkedHashMap<>();
        verificationPayload.put("identityProvider", VNPT_PROVIDER);
        verificationPayload.put("verificationMode", normalizedIdentityVerificationMode());
        // Do not persist the untrusted raw browser callback. The normalized OCR,
        // terminal decision and failure reasons below are the durable evidence.
        verificationPayload.put("providerStatus", verified ? "SDK_VERIFIED" : "SDK_FAILED");
        if (verified) {
            verificationPayload.put("identityOcr", storedIdentityEvidence(identityOcr));
        }
        verificationPayload.put("failureReasons", failureReasons);
        verificationPayload.put("messageCode", verified ? "KYC_IDENTITY_VERIFIED" : MessageCodes.MSG_KYC_002);
        verificationPayload.put("certificateManualAuthenticityReviewRequired", true);
        verificationPayload.put("autoApproval", false);
        verificationPayload.put("srs", srsTrace());
        kycRequest.setVerificationPayload(verificationPayload);

        KycRequest savedRequest;
        try {
            if (providerReplayId != null) {
                VnptIdentityTransactionClaim providerClaim = new VnptIdentityTransactionClaim();
                providerClaim.setUserId(user.getId());
                providerClaim.setSubjectType("TEACHER");
                providerClaim.setProvider(VNPT_PROVIDER);
                providerClaim.setProviderTransactionId(providerReplayId);
                providerClaim.setProviderSessionId(providerSessionId);
                providerClaim.setClaimedAt(now);
                vnptIdentityTransactionClaimRepository.saveAndFlush(providerClaim);
            }
            savedRequest = kycRequestRepository.saveAndFlush(kycRequest);
        } catch (DataIntegrityViolationException exception) {
            if (providerReplayId != null && isProviderTransactionConstraint(exception)) {
                securityAuditService.logVerificationEvent(
                        "DUPLICATE_TRANSACTION",
                        teacherProfile.getId(),
                        null,
                        user.getId(),
                        ipAddress,
                        userAgent
                );
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "VNPT transaction is already bound to another verification session",
                        HttpStatus.CONFLICT,
                        exception
                );
            }
            throw exception;
        }
        boolean auditLogged = createIdentityAudit(savedRequest, user, ipAddress, userAgent);
        return buildIdentityResponse(teacherProfile, savedRequest, auditLogged);
    }

    private KycRequest ensureAccountIdentityRequest(
            TeacherProfile teacherProfile,
            KycRequest latestRequest,
            AccountIdentityVerification verification
    ) {
        if (latestRequest != null
                && resolvedIdentityStatus(latestRequest) == IdentityVerificationStatus.VERIFIED) {
            return latestRequest;
        }

        KycRequest sharedRequest = new KycRequest();
        sharedRequest.setTeacherProfile(teacherProfile);
        sharedRequest.setStatus(KycRequestStatus.DRAFT);
        sharedRequest.setEkycProvider(VNPT_PROVIDER);
        sharedRequest.setEkycReferenceId("ACCOUNT-IDENTITY-" + UUID.randomUUID());
        sharedRequest.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        sharedRequest.setIdentityVerifiedAt(verification.getVerifiedAt());
        sharedRequest.setCertificateStatus(CertificateVerificationStatus.NOT_SUBMITTED);
        sharedRequest.setServerFullName(verification.getFullName());
        sharedRequest.setServerDateOfBirth(
                verification.getDateOfBirth() == null ? null : verification.getDateOfBirth().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identityProvider", verification.getProvider());
        payload.put("providerStatus", "ACCOUNT_VERIFIED");
        payload.put("sourceSubject", verification.getSourceSubject());
        payload.put("messageCode", "KYC_IDENTITY_VERIFIED");
        payload.put("sharedAcrossAccountRoles", true);
        sharedRequest.setVerificationPayload(payload);
        return kycRequestRepository.saveAndFlush(sharedRequest);
    }

    private void recordAccountIdentity(
            UUID userId,
            Map<String, String> identityOcr,
            String provider,
            Instant verifiedAt,
            String sourceSubject
    ) {
        String normalizedCccd = teacherIdentityClaimService.normalizeCccd(identityOcr.get("idNumber"));
        accountIdentityVerificationService.recordVerified(
                userId,
                teacherIdentityClaimService.generateFingerprint(normalizedCccd),
                provider,
                blankToNull(identityOcr.get("fullName")),
                parseSupportedDate(identityOcr.get("dateOfBirth")),
                verifiedAt == null ? Instant.now() : verifiedAt,
                sourceSubject);
    }

    private String directSdkReplayId(
            String providerTransactionId,
            String providerSessionId,
            boolean mockRegistryMode
    ) {
        validateProviderIdentifier(providerTransactionId, "providerTransactionId");
        validateProviderIdentifier(providerSessionId, "providerSessionId");
        if (!mockRegistryMode && providerSessionId == null) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "VNPT providerSessionId is required in direct-sdk mode",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (providerTransactionId != null) {
            return providerTransactionId;
        }
        if (providerSessionId == null) {
            return null;
        }
        if (providerSessionId.length() > MAX_VNPT_PROVIDER_ID_LENGTH - VNPT_SESSION_REPLAY_PREFIX.length()) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "VNPT providerSessionId exceeds the accepted replay-binding length",
                    HttpStatus.BAD_REQUEST
            );
        }
        return VNPT_SESSION_REPLAY_PREFIX + providerSessionId;
    }

    private void validateProviderIdentifier(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.length() > MAX_VNPT_PROVIDER_ID_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Invalid VNPT " + fieldName,
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Map<String, String> storedIdentityEvidence(Map<String, String> identityOcr) {
        Map<String, String> evidence = new LinkedHashMap<>();
        String idNumber = blankToNull(identityOcr.get("idNumber"));
        if (idNumber != null) {
            String digits = idNumber.replaceAll("[^0-9]", "");
            evidence.put("idNumber", digits.length() > 4
                    ? "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4)
                    : "****");
        }
        String fullName = blankToNull(identityOcr.get("fullName"));
        if (fullName != null) {
            evidence.put("fullName", fullName);
        }
        String dateOfBirth = blankToNull(identityOcr.get("dateOfBirth"));
        if (dateOfBirth != null) {
            evidence.put("dateOfBirth", dateOfBirth);
        }
        return Map.copyOf(evidence);
    }

    private boolean crossCheckMockNationalId(
            String normalizedCccd,
            Map<String, String> identityOcr,
            List<String> failureReasons
    ) {
        MockNationalIdRegistryRecord registryRecord = mockNationalIdRegistryRepository
                .findByIdNumberAndActiveTrue(normalizedCccd)
                .orElse(null);
        if (registryRecord == null) {
            failureReasons.add("Thông tin CCCD không tồn tại trong cơ sở dữ liệu quốc gia mô phỏng");
            return false;
        }

        boolean matches = true;
        if (!normalizePersonName(identityOcr.get("fullName")).equals(normalizePersonName(registryRecord.getFullName()))) {
            failureReasons.add("Họ và tên không khớp với cơ sở dữ liệu quốc gia mô phỏng");
            matches = false;
        }
        try {
            LocalDate ocrDateOfBirth = parseSupportedDate(identityOcr.get("dateOfBirth"));
            if (!ocrDateOfBirth.equals(registryRecord.getDateOfBirth())) {
                failureReasons.add("Ngày sinh không khớp với cơ sở dữ liệu quốc gia mô phỏng");
                matches = false;
            }
        } catch (BusinessException exception) {
            failureReasons.add(exception.getMessage());
            matches = false;
        }

        return matches;
    }

    private boolean usesDirectSdkVerification() {
        String mode = normalizedIdentityVerificationMode();
        return "DIRECT_SDK".equals(mode) || "DIRECT_SDK_MOCK".equals(mode);
    }

    private boolean usesMockNationalIdRegistry() {
        return "DIRECT_SDK_MOCK".equals(normalizedIdentityVerificationMode());
    }

    private String normalizedIdentityVerificationMode() {
        return (identityVerificationMode == null ? "DIRECT_SDK_MOCK" : identityVerificationMode)
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
    }

    private KycIdentityVerificationResponse buildIdentityResponse(TeacherProfile teacherProfile, KycRequest request, boolean auditLogged) {
        return new KycIdentityVerificationResponse(
                teacherProfile.getId(),
                teacherProfile.getKycStatus().name(),
                toRequestResponse(request),
                identityModuleStatus(teacherProfile, request),
                certificateModuleStatus(teacherProfile, request),
                auditLogged,
                srsTrace()
        );
    }

    @Transactional
    public KycRestartVerificationResponse restartVerification(UUID userId, String ipAddress, String userAgent) {
        resolveTeacher(userId);
        TeacherProfile teacherProfile = teacherProfileRepository.findForUpdateByUserId(userId)
                .orElseThrow(this::teacherProfileNotFound);
        entityManager.refresh(teacherProfile);
        AppUser user = teacherProfile.getUser();

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_002,
                    "Teacher account is not allowed to restart verification",
                    HttpStatus.FORBIDDEN
            );
        }

        if (teacherProfile.getKycStatus() == TeacherKycStatus.APPROVED) {
            throw new BusinessException(
                    MessageCodes.KYC_ALREADY_APPROVED,
                    "Approved KYC cannot be restarted",
                    HttpStatus.CONFLICT
            );
        }

        TeacherKycStatus beforeStatus = teacherProfile.getKycStatus();
        KycRequest restartRequest = new KycRequest();
        restartRequest.setTeacherProfile(teacherProfile);
        restartRequest.setStatus(KycRequestStatus.DRAFT);
        restartRequest.setIdentityStatus(IdentityVerificationStatus.NOT_STARTED);
        restartRequest.setCertificateStatus(CertificateVerificationStatus.LOCKED);
        restartRequest.setCopyrightAgreed(false);
        restartRequest.setVerificationPayload(Map.of(
                "restart", true,
                "previousTeacherKycStatus", beforeStatus.name(),
                "moduleFlow", "FULL_RESTART",
                "srs", srsTrace()
        ));

        teacherProfile.setKycStatus(TeacherKycStatus.NOT_SUBMITTED);
        teacherProfile.setCanPublishCourse(false);

        KycRequest savedRequest = kycRequestRepository.save(restartRequest);
        boolean auditLogged = createRestartAudit(savedRequest, user, beforeStatus, ipAddress, userAgent);

        return new KycRestartVerificationResponse(
                teacherProfile.getId(),
                teacherProfile.getKycStatus().name(),
                teacherProfile.isCanPublishCourse(),
                toRequestResponse(savedRequest, List.of()),
                identityModuleStatus(teacherProfile, savedRequest),
                certificateModuleStatus(teacherProfile, savedRequest),
                auditLogged,
                srsTrace()
        );
    }

    @Transactional
    public KycCertificateSubmissionResponse submitCertificate(

            UUID userId,
            MultipartFile certificate,
            String certificateCode,
            String certificateHolderName,
            String certificateDateOfBirth,
            String certificateLevel,
            String certificateOcrText,
            boolean copyrightAgreementAccepted,
            String ipAddress,
            String userAgent
    ) {
        if (certificate == null || certificate.isEmpty()) {
            throw invalidFile("file is required");
        }
        resolveTeacher(userId);
        TeacherProfile teacherProfile = teacherProfileRepository.findForUpdateByUserId(userId)
                .orElseThrow(this::teacherProfileNotFound);
        entityManager.refresh(teacherProfile);
        AppUser user = teacherProfile.getUser();
        KycRequest kycRequest = validateCertificateSubmissionAllowed(user, teacherProfile);
        validateAgreement(copyrightAgreementAccepted);

        PreparedFile certificateFile = prepareCertificateFile(certificate);
        String normalizedCertificateCode =
                teacherCertificateClaimService.normalizeJlptCertificateCode(certificateCode);
        CertificateEvidence certificateEvidence = validateCertificateEvidence(
                kycRequest,
                normalizedCertificateCode,
                certificateHolderName,
                certificateDateOfBirth,
                certificateLevel,
                certificateOcrText
        );
        teacherCertificateClaimService.processCertificateClaim(
                teacherProfile.getId(),
                kycRequest.getId(),
                normalizedCertificateCode,
                user,
                ipAddress,
                userAgent
        );
        KycDocument certificateDocument = storeDocument(kycRequest, certificateFile);
        kycDocumentRepository.save(certificateDocument);

        TeacherKycStatus beforeStatus = teacherProfile.getKycStatus();
        kycRequest.setCertificateCode(normalizedCertificateCode);
        kycRequest.setCertificateSubmittedAt(Instant.now());
        kycRequest.setCopyrightAgreed(true);
        kycRequest.setStatus(KycRequestStatus.PENDING);
        kycRequest.setCertificateStatus(CertificateVerificationStatus.PENDING_REVIEW);
        kycRequest.setVerificationPayload(withCertificatePayload(
                kycRequest,
                certificateEvidence
        ));

        teacherProfile.setKycStatus(TeacherKycStatus.PENDING);
        teacherProfile.setCanPublishCourse(false);
        grantTeacherRoleIfAbsent(user.getId());
        teacherProfileRepository.save(teacherProfile);
        kycRequestRepository.saveAndFlush(kycRequest);
        entityManager.flush();

        boolean auditLogged = createCertificateSubmissionAudit(kycRequest, user, beforeStatus, ipAddress, userAgent);
        boolean adminNotificationCreated = createPendingReviewNotifications(kycRequest, user);
        String sessionToken = publicJwtTokenService.issueCurrentRoleToken(user.getId());
        List<KycDocument> documents = kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(kycRequest.getId());

        return new KycCertificateSubmissionResponse(
                teacherProfile.getId(),
                teacherProfile.getKycStatus().name(),
                teacherProfile.isCanPublishCourse(),
                toRequestResponse(kycRequest, documents),
                identityModuleStatus(teacherProfile, kycRequest),
                certificateModuleStatus(teacherProfile, kycRequest),
                adminNotificationCreated,
                auditLogged,
                true,
                REVIEW_ETA,
                sessionToken,
                srsTrace()
        );
    }

    private TeacherProfile resolveTeacher(UUID userId) {
        return teacherProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    teacherProfileRepository.createCandidateIfAbsent(UUID.randomUUID(), userId);
                    return teacherProfileRepository.findByUserId(userId)
                            .orElseThrow(() -> teacherProfileNotFound());
                });
    }

    private BusinessException teacherProfileNotFound() {
        return new BusinessException(
                MessageCodes.KYC_TEACHER_NOT_FOUND,
                "Teacher profile could not be initialized for the current user",
                HttpStatus.NOT_FOUND
        );
    }

    private void validateIdentityAllowed(AppUser user, TeacherProfile teacherProfile, KycRequest latestRequest) {
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    MessageCodes.MSG_ADM_002,
                    "Teacher account is not allowed to start identity verification",
                    HttpStatus.FORBIDDEN
            );
        }

        if (latestRequest != null && latestRequest.getStatus() == KycRequestStatus.PENDING) {
            throw new BusinessException(
                    MessageCodes.KYC_ALREADY_PENDING,
                    "JLPT certificate authenticity review is already pending",
                    HttpStatus.CONFLICT
            );
        }

        if (teacherProfile.getKycStatus() == TeacherKycStatus.APPROVED) {
            throw new BusinessException(
                    MessageCodes.KYC_ALREADY_APPROVED,
                    "KYC is already approved",
                    HttpStatus.CONFLICT
            );
        }
    }

    private KycRequest validateCertificateSubmissionAllowed(AppUser user, TeacherProfile teacherProfile) {
        KycRequest latestRequest = kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MSG_KYC_002,
                        "Identity verification must be completed before certificate submission"
                ));
        validateIdentityAllowed(user, teacherProfile, latestRequest);

        if (resolvedIdentityStatus(latestRequest) != IdentityVerificationStatus.VERIFIED) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Identity verification must be successful before certificate submission"
            );
        }

        CertificateVerificationStatus certificateStatus = resolvedCertificateStatus(latestRequest);
        if (latestRequest.getStatus() == KycRequestStatus.PENDING
                || certificateStatus == CertificateVerificationStatus.PENDING_REVIEW) {
            throw new BusinessException(
                    MessageCodes.KYC_ALREADY_PENDING,
                    "JLPT certificate is already waiting for manual authenticity review",
                    HttpStatus.CONFLICT
            );
        }

        if (certificateStatus != CertificateVerificationStatus.NOT_SUBMITTED) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Start a fresh teacher verification attempt before submitting another certificate"
            );
        }

        return latestRequest;
    }

    private void validateAgreement(boolean accepted) {
        if (!accepted) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "Digital Copyright Liability Agreement must be accepted"
            );
        }
    }

    private KycRequest findLatestRequest(TeacherProfile teacherProfile) {
        return kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherProfile.getId())
                .orElse(null);
    }

    private KycRequest findReusableRealtimeRequest(TeacherProfile teacherProfile, KycRequest latestRequest) {
        return java.util.Optional.ofNullable(latestRequest)
                .filter(request -> request.getStatus() == KycRequestStatus.DRAFT
                        && request.getIdentityStatus() == IdentityVerificationStatus.NOT_STARTED
                        && request.getProviderTransactionId() == null)
                .orElseGet(KycRequest::new);
    }

    private boolean isProviderTransactionConstraint(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT)
                    .contains("uq_kyc_requests_provider_tx")
                    || message != null && message.toLowerCase(Locale.ROOT)
                    .contains("uq_vnpt_identity_claim_provider_transaction")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private PreparedFile prepareCertificateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("Certificate file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw invalidFile("File must not exceed 5MB");
        }

        try {
            byte[] bytes = file.getBytes();
            String detectedMimeType = detectCertificateMimeType(bytes);
            if (!CERTIFICATE_MIME_TYPES.contains(detectedMimeType)) {
                throw invalidFile("Certificate must be a genuine JPG or PNG image");
            }
            String originalFileName = sanitizeFileName(file.getOriginalFilename());
            String canonicalFileName = withDetectedExtension(originalFileName, detectedMimeType);
            String hash = sha256(bytes);

            return new PreparedFile(
                    KycDocumentType.CERTIFICATE,
                    canonicalFileName,
                    detectedMimeType,
                    file.getSize(),
                    hash,
                    bytes
            );
        } catch (IOException ex) {
            throw invalidFile("Could not read uploaded certificate");
        }
    }

    private KycDocument storeDocument(KycRequest request, PreparedFile preparedFile) {
        String storedFileName = UUID.randomUUID() + "-" + preparedFile.fileName();
        Path targetDirectory = storageRoot.resolve(request.getId().toString()).normalize();
        Path targetPath = targetDirectory.resolve(storedFileName).normalize();

        if (!targetPath.startsWith(storageRoot)) {
            throw new BusinessException(MessageCodes.MSG_KYC_002, "Invalid storage path");
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.write(targetPath, preparedFile.bytes());
            registerRollbackCleanup(targetPath);
        } catch (IOException ex) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Could not store KYC document securely",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex
            );
        }

        KycDocument document = new KycDocument();
        document.setKycRequest(request);
        document.setDocumentType(preparedFile.documentType());
        document.setFileName(preparedFile.fileName());
        document.setMimeType(preparedFile.mimeType());
        document.setFileSize(preparedFile.fileSize());
        document.setFileHash(preparedFile.fileHash());
        document.setFileUrl("restricted://kyc/" + request.getId() + "/" + storedFileName);

        return document;
    }

    private String detectCertificateMimeType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String withDetectedExtension(String fileName, String mimeType) {
        String extension = "image/png".equals(mimeType) ? ".png" : ".jpg";
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return (baseName.isBlank() ? "jlpt-certificate" : baseName) + extension;
    }

    private void registerRollbackCleanup(Path targetPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        Files.deleteIfExists(targetPath);
                    } catch (IOException ignored) {
                        // The database rollback remains authoritative; stale file cleanup is best-effort.
                    }
                }
            }
        });
    }

    private boolean createPendingReviewNotifications(KycRequest request, AppUser user) {
        notificationService.createNotification(
                user.getId(),
                user.getEmail(),
                "Đã tiếp nhận chứng chỉ JLPT",
                "Hệ thống đã tiếp nhận chứng chỉ và chuyển sang bước kiểm tra tính xác thực. "
                        + "Kết quả dự kiến có trong 1-2 ngày làm việc, không tính cuối tuần và ngày nghỉ lễ.",
                NotificationTypes.KYC_CERTIFICATE_PENDING,
                "/teacher/kyc"
        );

        List<UUID> courseManagerIds = notificationRepository.findActiveAdminIdsByRoleCode("COURSE_MANAGER");
        if (courseManagerIds.isEmpty()) {
            return false;
        }
        notificationService.createNotificationForAdminRole(
                "COURSE_MANAGER",
                "Chứng chỉ JLPT cần được xác minh",
                "Các bước đối chiếu danh tính, OCR và kiểm tra trùng lặp đã hoàn tất. "
                        + "Vui lòng kiểm tra tính xác thực của chứng chỉ trước khi ra quyết định.",
                NotificationTypes.KYC_CERTIFICATE_REVIEW,
                "/admin/kyc/" + request.getId()
        );
        return true;
    }



    private boolean createIdentityAudit(
            KycRequest request,
            AppUser user,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(user.getId())
                .actorRoleCode("TEACHER")
                .action("KYC_IDENTITY_VERIFY")
                .targetType("KYC_REQUEST")
                .targetId(request.getId())
                .afterValue(Map.of(
                        "identityStatus", request.getIdentityStatus().name(),
                        "provider", request.getEkycProvider()
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "IDENTITY_VERIFICATION",
                        "provider", "VNPT_EKYC_WEB_SDK"
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(auditLog);
        return true;
    }

    private boolean createCertificateSubmissionAudit(
            KycRequest request,
            AppUser user,
            TeacherKycStatus beforeStatus,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(user.getId())
                .actorRoleCode("TEACHER")
                .action("KYC_CERTIFICATE_SUBMIT")
                .targetType("KYC_REQUEST")
                .targetId(request.getId())
                .beforeValue(Map.of("teacherKycStatus", beforeStatus.name()))
                .afterValue(Map.of(
                        "teacherKycStatus", TeacherKycStatus.PENDING.name(),
                        "requestStatus", request.getStatus().name(),
                        "certificateStatus", request.getCertificateStatus().name()
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "br", List.of("BR-KYC-01", "BR-KYC-03", "BR-NOTIF-02", "BR-AUD-01"),
                        "msg", MessageCodes.MSG_KYC_003,
                        "module", "CERTIFICATE_ASYNC_REVIEW"
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(auditLog);
        return true;
    }

    private boolean createRestartAudit(
            KycRequest request,
            AppUser user,
            TeacherKycStatus beforeStatus,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(user.getId())
                .actorRoleCode("TEACHER")
                .action("KYC_RESTART_VERIFICATION")
                .targetType("KYC_REQUEST")
                .targetId(request.getId())
                .beforeValue(Map.of("teacherKycStatus", beforeStatus.name()))
                .afterValue(Map.of(
                        "teacherKycStatus", TeacherKycStatus.NOT_SUBMITTED.name(),
                        "requestStatus", request.getStatus().name(),
                        "identityStatus", request.getIdentityStatus().name(),
                        "certificateStatus", request.getCertificateStatus().name()
                ))
                .metadata(Map.of(
                        "uc", "UC-22",
                        "module", "FULL_RESTART",
                        "reason", "Teacher requested fresh identity and certificate verification"
                ))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(auditLog);
        return true;
    }

    private KycRequestResponse toRequestResponse(KycRequest request) {
        List<KycDocument> documents = kycDocumentRepository.findByKycRequestIdOrderByCreatedAtAsc(request.getId());

        return toRequestResponse(request, documents);
    }

    private KycRequestResponse toRequestResponse(KycRequest request, List<KycDocument> documents) {
        return new KycRequestResponse(
                request.getId(),
                request.getStatus().name(),
                requestStatusLabel(request.getStatus()),
                request.getSubmittedAt(),
                request.getEkycProvider(),
                request.getEkycReferenceId(),
                request.getProviderSessionId(),
                request.getProviderTransactionId(),
                resolvedIdentityStatus(request).name(),
                identityStatusLabel(resolvedIdentityStatus(request)),
                resolvedIdentityStatus(request) == IdentityVerificationStatus.VERIFIED ? request.getIdentityVerifiedAt() : null,
                resolvedCertificateStatus(request).name(),
                certificateStatusLabel(resolvedCertificateStatus(request)),
                request.getCertificateSubmittedAt(),
                request.getRiskLevel() == null ? null : request.getRiskLevel().name(),
                request.getCertificateCode(),
                request.isCopyrightAgreed(),
                sanitizeVerificationPayloadForTeacher(request.getVerificationPayload()),
                documents.stream().map(this::toDocumentResponse).toList()
        );
    }

    private Map<String, Object> sanitizeVerificationPayloadForTeacher(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(payload);
        sanitized.remove("providerResult");

        Object ocrObj = sanitized.get("identityOcr");
        if (ocrObj instanceof Map<?, ?> ocrMap) {
            Map<String, Object> sanitizedOcr = new LinkedHashMap<>();
            ocrMap.forEach((k, v) -> {
                if ("idNumber".equals(k)) {
                    if (v != null) {
                        String clean = String.valueOf(v).replaceAll("[^0-9]", "");
                        if (clean.length() == 12) {
                            sanitizedOcr.put("idNumber", clean.substring(0, 3) + "******" + clean.substring(9));
                        } else {
                            sanitizedOcr.put("idNumber", "************");
                        }
                    } else {
                        sanitizedOcr.put("idNumber", "************");
                    }
                } else {
                    sanitizedOcr.put(String.valueOf(k), v);
                }
            });
            sanitized.put("identityOcr", sanitizedOcr);
        }
        return sanitized;
    }

    private KycDocumentResponse toDocumentResponse(KycDocument document) {
        return new KycDocumentResponse(
                document.getId(),
                document.getDocumentType().name(),
                document.getFileName(),
                document.getMimeType(),
                document.getFileSize() == null ? 0 : document.getFileSize(),
                document.getFileHash(),
                document.getCreatedAt()
        );
    }

    private KycModuleStatusResponse identityModuleStatus(TeacherProfile teacherProfile, KycRequest latestRequest) {
        if (latestRequest == null) {
            return new KycModuleStatusResponse(
                IdentityVerificationStatus.NOT_STARTED.name(),
                identityStatusLabel(IdentityVerificationStatus.NOT_STARTED),
                teacherProfile.getKycStatus() != TeacherKycStatus.APPROVED,
                null,
                "Bắt đầu VNPT eKYC để chụp CCCD và kiểm tra liveness khuôn mặt."
        );
        }

        IdentityVerificationStatus status = resolvedIdentityStatus(latestRequest);
        return new KycModuleStatusResponse(
                status.name(),
                identityStatusLabel(status),
                canInteractWithIdentityModule(teacherProfile, latestRequest),
                status == IdentityVerificationStatus.VERIFIED ? latestRequest.getIdentityVerifiedAt() : null,
                identityStatusDetail(status)
        );
    }

    private KycModuleStatusResponse certificateModuleStatus(TeacherProfile teacherProfile, KycRequest latestRequest) {
        if (latestRequest == null || resolvedIdentityStatus(latestRequest) != IdentityVerificationStatus.VERIFIED) {
            return new KycModuleStatusResponse(
                    CertificateVerificationStatus.LOCKED.name(),
                    certificateStatusLabel(CertificateVerificationStatus.LOCKED),
                    false,
                    null,
                    "Hoàn tất xác thực danh tính trước khi nộp chứng chỉ."
            );
        }

        CertificateVerificationStatus status = resolvedCertificateStatus(latestRequest);
        boolean canInteract = teacherProfile.getKycStatus() != TeacherKycStatus.APPROVED
                && status == CertificateVerificationStatus.NOT_SUBMITTED;

        return new KycModuleStatusResponse(
                status.name(),
                certificateStatusLabel(status),
                canInteract,
                latestRequest.getCertificateSubmittedAt(),
                certificateStatusDetail(status)
        );
    }

    private CertificateVerificationStatus resolvedCertificateStatus(KycRequest request) {
        return switch (request.getStatus()) {
            case APPROVED -> CertificateVerificationStatus.APPROVED;
            case REJECTED, CORRECTION_REQUIRED, REVOKED -> CertificateVerificationStatus.REJECTED;
            case DRAFT, PENDING -> request.getCertificateStatus();
        };
    }

    private IdentityVerificationStatus resolvedIdentityStatus(KycRequest request) {
        if (request.getIdentityStatus() == IdentityVerificationStatus.NOT_STARTED
                || request.getIdentityStatus() == IdentityVerificationStatus.FAILED
                || request.getIdentityStatus() == IdentityVerificationStatus.PROCESSING) {
            return request.getIdentityStatus();
        }

        return VNPT_PROVIDER.equals(request.getEkycProvider())
                ? request.getIdentityStatus()
                : IdentityVerificationStatus.NOT_STARTED;
    }

    private boolean canInteractWithIdentityModule(TeacherProfile teacherProfile, KycRequest latestRequest) {
        if (teacherProfile.getKycStatus() == TeacherKycStatus.APPROVED) {
            return false;
        }

        IdentityVerificationStatus status = resolvedIdentityStatus(latestRequest);
        if (status == IdentityVerificationStatus.VERIFIED
                || status == IdentityVerificationStatus.PROCESSING
                || status == IdentityVerificationStatus.PENDING_SERVER_VERIFICATION) {
            return false;
        }

        return latestRequest.getStatus() != KycRequestStatus.PENDING
                || resolvedCertificateStatus(latestRequest) != CertificateVerificationStatus.PENDING_REVIEW;
    }

    private Map<String, Object> withCertificatePayload(
            KycRequest request,
            CertificateEvidence evidence
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(request.getVerificationPayload());
        payload.put("certificateStatus", request.getCertificateStatus().name());
        payload.put("certificateCode", request.getCertificateCode());
        payload.put("certificateType", "JLPT");
        payload.put("certificateHolderName", evidence.holderName());
        payload.put("certificateDateOfBirth", evidence.dateOfBirth().toString());
        payload.put("certificateLevel", evidence.level());
        payload.put("certificateOcrText", evidence.ocrText());
        payload.put("copyrightAgreement", "ACCEPTED_BY_CHECKBOX");
        payload.put("autoApproval", false);
        payload.put("certificateReviewMode", "MANUAL_JAPAN_FOUNDATION");
        payload.put("ocrReadStatus", "SUCCESS");
        payload.put("identityCrossMatch", "MATCHED");
        payload.put("duplicateCertificateCheck", "PASSED");
        payload.put("exceptionStage", "CERTIFICATE");
        payload.put("exceptionType", "JLPT_AUTHENTICITY_CHECK");
        payload.put(
                "exceptionReason",
                "OCR data matches the VNPT-verified identity; Course Manager must verify certificate authenticity"
        );
        payload.put("reviewEta", REVIEW_ETA);
        payload.put("teacherWorkspaceAvailable", true);
        payload.put("publishLockedUntilKycApproval", true);
        return payload;
    }

    private CertificateEvidence validateCertificateEvidence(
            KycRequest kycRequest,
            String normalizedCertificateCode,
            String certificateHolderName,
            String certificateDateOfBirth,
            String certificateLevel,
            String certificateOcrText
    ) {
        if (!StringUtils.hasText(certificateOcrText)
                || certificateOcrText.trim().length() > MAX_OCR_TEXT_LENGTH) {
            throw certificateMismatch(
                    "The JLPT image could not be read reliably. Upload a clear JPG or PNG image"
            );
        }
        if (!StringUtils.hasText(certificateHolderName)
                || !StringUtils.hasText(certificateDateOfBirth)
                || !StringUtils.hasText(certificateLevel)) {
            throw certificateMismatch(
                    "OCR must extract the certificate holder name, date of birth, and JLPT level"
            );
        }

        String normalizedLevel = certificateLevel.trim().toUpperCase(Locale.ROOT);
        if (!normalizedLevel.matches("N[1-5]")) {
            throw certificateMismatch("Only JLPT levels N1 through N5 are accepted");
        }

        Map<String, String> identityOcr = extractOcrFromPayload(kycRequest.getVerificationPayload());
        String identityFullName = StringUtils.hasText(kycRequest.getServerFullName())
                ? kycRequest.getServerFullName()
                : identityOcr.get("fullName");
        String identityDateOfBirth = StringUtils.hasText(kycRequest.getServerDateOfBirth())
                ? kycRequest.getServerDateOfBirth()
                : identityOcr.get("dateOfBirth");
        if (!StringUtils.hasText(identityFullName) || !StringUtils.hasText(identityDateOfBirth)) {
            throw certificateMismatch(
                    "VNPT identity result is missing the name or date of birth required for matching"
            );
        }

        String normalizedCertificateName = normalizePersonName(certificateHolderName);
        String normalizedIdentityName = normalizePersonName(identityFullName);
        if (normalizedCertificateName.isBlank()
                || !normalizedCertificateName.equals(normalizedIdentityName)) {
            throw certificateMismatch(
                    "The name read from the JLPT certificate does not match the VNPT-verified CCCD"
            );
        }

        LocalDate certificateDob = parseSupportedDate(certificateDateOfBirth);
        LocalDate identityDob = parseSupportedDate(identityDateOfBirth);
        if (!certificateDob.equals(identityDob)) {
            throw certificateMismatch(
                    "The date of birth read from the JLPT certificate does not match the VNPT-verified CCCD"
            );
        }

        String searchableOcr = normalizeCertificateOcrText(certificateOcrText);
        if (!searchableOcr.contains(normalizedCertificateName)) {
            throw certificateMismatch(
                    "OCR output does not contain the submitted JLPT certificate holder name"
            );
        }
        if (!searchableOcr.contains(normalizedLevel)) {
            throw certificateMismatch(
                    "OCR output does not contain the submitted JLPT level"
            );
        }
        if (!searchableOcr.contains(normalizedCertificateCode)) {
            throw certificateMismatch(
                    "OCR output does not contain the submitted JLPT certificate code"
            );
        }

        String ocrDigits = certificateOcrText.replaceAll("[^0-9]", "");
        String dobDmy = certificateDob.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        String dobYmd = certificateDob.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!ocrDigits.contains(dobDmy) && !ocrDigits.contains(dobYmd)) {
            throw certificateMismatch(
                    "OCR output does not contain the submitted JLPT certificate date of birth"
            );
        }

        return new CertificateEvidence(
                certificateHolderName.trim(),
                certificateDob,
                normalizedLevel,
                certificateOcrText.trim()
        );
    }

    private Map<String, String> extractOcrFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object ocrObj = payload.get("identityOcr");
        if (ocrObj instanceof Map<?, ?> ocrMap) {
            Map<String, String> result = new LinkedHashMap<>();
            ocrMap.forEach((k, v) -> {
                if (k instanceof String key && v != null) {
                    result.put(key, String.valueOf(v));
                }
            });
            return result;
        }
        return Map.of();
    }

    private LocalDate parseSupportedDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            throw certificateMismatch("Date of birth is required");
        }
        String value = rawDate.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM-uuuu"),
                DateTimeFormatter.ofPattern("dd.MM.uuuu"),
                DateTimeFormatter.ofPattern("ddMMyyyy"),
                DateTimeFormatter.ofPattern("uuuu/MM/dd"),
                DateTimeFormatter.ofPattern("uuuu-MM-dd"),
                DateTimeFormatter.ofPattern("uuuu.MM.dd"),
                DateTimeFormatter.ofPattern("uuuuMMdd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        throw certificateMismatch("Date of birth could not be parsed");
    }

    private BusinessException certificateMismatch(String message) {
        return new BusinessException(
                MessageCodes.KYC_CERTIFICATE_OCR_MISMATCH,
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private String normalizePersonName(String value) {
        return com.manabihub.common.util.PersonNameNormalizer.normalize(value);
    }

    private String normalizeCertificateOcrText(String value) {
        return normalizePersonName(value);
    }

    private void grantTeacherRoleIfAbsent(UUID userId) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = :userId AND role_id = :roleId"
        ).setParameter("userId", userId)
         .setParameter("roleId", TEACHER_ROLE_ID)
         .getSingleResult();

        if (count.longValue() == 0) {
            entityManager.createNativeQuery(
                    "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId)"
            ).setParameter("userId", userId)
             .setParameter("roleId", TEACHER_ROLE_ID)
             .executeUpdate();
        }
    }

    private VnptSdkDecision evaluateSdkResult(Map<String, Object> sdkResult) {
        if (sdkResult == null || sdkResult.isEmpty()) {
            return new VnptSdkDecision(false, Map.of(), List.of("VNPT SDK did not return a result payload"));
        }

        List<ResultEntry> entries = flattenResult(sdkResult);
        boolean hasInvalidProviderSignal = entries.stream().anyMatch(this::isExplicitInvalidValue)
                || hasNonEmptyCollection(entries, "generalwarning", "warning")
                || hasAffirmativeSignal(entries, "masked");
        Map<String, String> identityOcr = extractIdentityOcr(entries);
        boolean hasRequiredOcr = StringUtils.hasText(identityOcr.get("idNumber"))
                && StringUtils.hasText(identityOcr.get("fullName"))
                && StringUtils.hasText(identityOcr.get("dateOfBirth"));
        boolean hasFaceVerification = hasAcceptedFaceVerification(entries);

        java.util.ArrayList<String> failureReasons = new java.util.ArrayList<>();
        if (hasInvalidProviderSignal) {
            failureReasons.add("VNPT validation returned invalid document, mismatch, failed, or null result");
        }
        if (!hasRequiredOcr) {
            failureReasons.add("VNPT OCR did not return CCCD number, full name, and date of birth");
        }
        if (!hasFaceVerification) {
            failureReasons.add("VNPT liveness/face compare result was not successful");
        }

        return new VnptSdkDecision(failureReasons.isEmpty(), identityOcr, failureReasons);
    }

    private List<ResultEntry> flattenResult(Map<String, Object> value) {
        java.util.ArrayList<ResultEntry> entries = new java.util.ArrayList<>();
        collectResultEntries(value, "", entries, 0);
        return entries;
    }

    private void collectResultEntries(Object current, String path, List<ResultEntry> entries, int depth) {
        if (current == null || depth > 8) {
            return;
        }

        if (current instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String nextPath = path.isBlank() ? String.valueOf(key).toLowerCase() : path + "." + String.valueOf(key).toLowerCase();
                entries.add(new ResultEntry(nextPath, value));
                collectResultEntries(value, nextPath, entries, depth + 1);
            });
            return;
        }

        if (current instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectResultEntries(item, path, entries, depth + 1);
            }
        }
    }

    private boolean isExplicitInvalidValue(ResultEntry entry) {
        Object value = entry.value();
        String key = entry.key();
        String normalizedKey = normalizeKey(key);

        if (isDecisionKey(key) && value instanceof Boolean booleanValue) {
            boolean isNegativeKey = isNegativeDecisionKey(normalizedKey);
            if (isNegativeKey) {
                if (booleanValue) return true; // Fake is true -> Invalid
            } else {
                if (!booleanValue) return true; // Valid is false -> Invalid
            }
        }

        if (isNegativeDecisionKey(normalizedKey) && isAffirmativeFlagValue(value)) {
            return true;
        }

        if (isDecisionKey(key) && value instanceof String text) {
            String normalized = normalizeSearchText(text).trim();
            return normalized.contains("khong hop le")
                    || normalized.contains("khong cung loai")
                    || normalized.contains("khong trung khop")
                    || normalized.contains("khong khop")
                    || normalized.contains("khong thanh cong")
                    || normalized.contains("that bai")
                    || normalized.contains("invalid")
                    || normalized.contains("not valid")
                    || normalized.contains("not same")
                    || normalized.contains("not match")
                    || normalized.contains("nomatch")
                    || normalized.equals("nothing")
                    || normalized.contains("mismatch")
                    || normalized.contains("failed")
                    || normalized.contains("failure")
                    || normalized.contains("null%");
        }

        return false;
    }

    private boolean isNegativeDecisionKey(String normalizedKey) {
        return normalizedKey.contains("fake")
                || normalizedKey.contains("spoof")
                || normalizedKey.contains("tamper")
                || normalizedKey.contains("multiple")
                || normalizedKey.contains("warning")
                || normalizedKey.contains("swapping");
    }

    private boolean hasNonEmptyCollection(List<ResultEntry> entries, String... aliases) {
        Set<String> normalizedAliases = java.util.Arrays.stream(aliases)
                .map(this::normalizeKey)
                .collect(java.util.stream.Collectors.toSet());

        return entries.stream()
                .filter(entry -> normalizedAliases.contains(normalizeKey(lastPathSegment(entry.key()))))
                .map(ResultEntry::value)
                .anyMatch(value -> {
                    if (value instanceof Iterable<?> iterable) {
                        return iterable.iterator().hasNext();
                    }
                    return value != null && value.getClass().isArray()
                            && java.lang.reflect.Array.getLength(value) > 0;
                });
    }

    private boolean hasAffirmativeSignal(List<ResultEntry> entries, String... aliases) {
        Set<String> normalizedAliases = java.util.Arrays.stream(aliases)
                .map(this::normalizeKey)
                .collect(java.util.stream.Collectors.toSet());

        return entries.stream()
                .filter(entry -> normalizedAliases.contains(normalizeKey(lastPathSegment(entry.key()))))
                .anyMatch(entry -> isAffirmativeValue(entry.value()));
    }

    private boolean isAffirmativeValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() > 0.0D;
        }
        if (value instanceof String text) {
            String normalized = normalizeSearchText(text).trim();
            return normalized.equals("true")
                    || normalized.equals("yes")
                    || normalized.equals("1")
                    || normalized.equals("co")
                    || normalized.equals("có");
        }
        return false;
    }

    private boolean isAffirmativeFlagValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            String normalized = normalizeSearchText(text).trim();
            return normalized.equals("true")
                    || normalized.equals("yes")
                    || normalized.equals("1")
                    || normalized.equals("co");
        }
        return false;
    }

    private boolean hasAcceptedFaceVerification(List<ResultEntry> entries) {
        boolean hasFaceSignal = entries.stream().anyMatch(entry -> isFaceVerificationKey(entry.key()));
        if (!hasFaceSignal) {
            return false;
        }

        return entries.stream()
                .filter(entry -> isFaceVerificationKey(entry.key()))
                .anyMatch(this::isExplicitSuccessValue);
    }

    private boolean isExplicitSuccessValue(ResultEntry entry) {
        Object value = entry.value();
        String key = entry.key();

        if (isDecisionKey(key) && value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof Number numberValue && isVerificationScoreKey(key)) {
            return isAcceptedScore(numberValue.doubleValue());
        }

        if (value instanceof String text) {
            String normalized = normalizeSearchText(text);
            if (isVerificationScoreKey(key)) {
                if (normalized.matches(".*\\b(8\\d|9\\d|100)(\\.\\d+)?\\s*%?.*")) {
                    return true;
                }

                try {
                    return isAcceptedScore(Double.parseDouble(normalized.replace("%", "").trim()));
                } catch (NumberFormatException ignored) {
                    // Fall through to the textual success values below.
                }
            }

            return normalized.equals("valid")
                    || normalized.equals("success")
                    || normalized.equals("verified")
                    || normalized.equals("matched")
                    || normalized.equals("match")
                    || normalized.equals("pass")
                    || normalized.contains("hop le")
                    || normalized.contains("thanh cong");
        }

        return false;
    }

    private boolean isDecisionKey(String key) {
        String normalizedKey = normalizeKey(key);
        return isValidationKey(key)
                || normalizedKey.endsWith("msg")
                || normalizedKey.endsWith("message")
                || normalizedKey.contains("fake")
                || normalizedKey.contains("spoof")
                || normalizedKey.contains("tamper");
    }

    private boolean isScoreKey(String key) {
        String normalizedKey = normalizeKey(key);
        return normalizedKey.contains("prob")
                || normalizedKey.contains("score")
                || normalizedKey.contains("similarity")
                || normalizedKey.contains("confidence")
                || normalizedKey.contains("percentage");
    }

    private boolean isVerificationScoreKey(String key) {
        if (!isFaceVerificationKey(key) || !isScoreKey(key)) {
            return false;
        }

        // VNPT uses low values for quality/anti-spoof indicators where a low
        // value is a good result (for example blur_face_score and
        // fake_liveness_prob). Those are not confidence scores and must not be
        // evaluated with the face-match threshold below.
        String normalizedKey = normalizeKey(key);
        return !normalizedKey.contains("blur")
                && !normalizedKey.contains("fake")
                && !normalizedKey.contains("spoof")
                && !normalizedKey.contains("tamper")
                && !normalizedKey.contains("swapping")
                && !normalizedKey.contains("masked");
    }

    private boolean isAcceptedScore(double value) {
        return (value >= 0.0D && value <= 1.0D && value >= 0.8D) || value >= 80.0D;
    }

    private Map<String, String> extractIdentityOcr(List<ResultEntry> entries) {
        Map<String, String> identityOcr = new LinkedHashMap<>();
        putIfPresent(identityOcr, "idNumber", findEntryValue(entries, "idnumber", "idno", "identitynumber", "documentnumber", "cardnumber", "socccd", "cccd", "soid", "id"));
        putIfPresent(identityOcr, "fullName", findEntryValue(entries, "fullname", "hoten", "name", "customername"));
        putIfPresent(identityOcr, "dateOfBirth", findEntryValue(entries, "dateofbirth", "birthdate", "birthday", "dob", "ngaysinh"));
        putIfPresent(identityOcr, "gender", findEntryValue(entries, "gender", "sex", "gioitinh"));
        putIfPresent(identityOcr, "address", findEntryValue(entries, "address", "residentaddress", "permanentaddress", "noithuongtru", "thuongtru"));
        return identityOcr;
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String findEntryValue(List<ResultEntry> entries, String... aliases) {
        Set<String> normalizedAliases = java.util.Arrays.stream(aliases)
                .map(this::normalizeKey)
                .collect(java.util.stream.Collectors.toSet());

        return entries.stream()
                .filter(entry -> normalizedAliases.contains(normalizeKey(lastPathSegment(entry.key()))))
                .map(entry -> displayScalar(entry.value()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> entries.stream()
                        .filter(entry -> normalizedAliases.stream()
                                .anyMatch(alias -> alias.length() > 2 && normalizeKey(entry.key()).endsWith(alias)))
                        .map(entry -> displayScalar(entry.value()))
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null));
    }

    private String displayScalar(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.length() > 240 ? null : trimmed;
        }

        if (value instanceof Number number) {
            return String.valueOf(number);
        }

        return null;
    }

    private String lastPathSegment(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    private boolean isValidationKey(String key) {
        String normalizedKey = normalizeKey(key);
        return normalizedKey.contains("success")
                || normalizedKey.contains("verified")
                || normalizedKey.contains("valid")
                || normalizedKey.contains("validation")
                || normalizedKey.contains("result")
                || normalizedKey.contains("status")
                || normalizedKey.contains("same")
                || normalizedKey.contains("match")
                || normalizedKey.contains("compare")
                || normalizedKey.contains("liveness");
    }

    private boolean isFaceVerificationKey(String key) {
        String normalizedKey = normalizeKey(key);
        return normalizedKey.contains("face")
                || normalizedKey.contains("liveness")
                || normalizedKey.contains("live")
                || normalizedKey.contains("compare")
                || normalizedKey.contains("comparison")
                || normalizedKey.contains("matching")
                || normalizedKey.contains("similarity")
                || normalizedKey.contains("portrait")
                || normalizedKey.contains("selfie");
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String preprocessed = value.replace('Đ', 'D').replace('đ', 'd');
        return java.text.Normalizer.normalize(preprocessed, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase();
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String preprocessed = value.replace('Đ', 'D').replace('đ', 'd');
        return java.text.Normalizer.normalize(preprocessed, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private BusinessException invalidFile(String reason) {
        return new BusinessException(
                MessageCodes.MSG_KYC_002,
                "JLPT certificate: " + reason
        );
    }

    private String sanitizeFileName(String value) {
        String cleanName = StringUtils.cleanPath(value == null ? "kyc-certificate" : value);
        String fileName = Path.of(cleanName).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");

        return fileName.isBlank() ? "kyc-certificate" : fileName;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "SHA-256 digest is not available",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex
            );
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String statusLabel(TeacherKycStatus status) {
        return switch (status) {
            case NOT_SUBMITTED -> "Chưa nộp";
            case PENDING -> "Chờ kiểm tra KYC";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Bị từ chối";
            case CORRECTION_REQUIRED -> "Yêu cầu bổ sung";
            case REVOKED -> "Đã thu hồi";
        };
    }

    private String requestStatusLabel(KycRequestStatus status) {
        return switch (status) {
            case DRAFT -> "Bản nháp xác thực danh tính";
            case PENDING -> "Chờ kiểm tra KYC";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Bị từ chối";
            case CORRECTION_REQUIRED -> "Yêu cầu bổ sung";
            case REVOKED -> "Đã thu hồi";
        };
    }

    private String identityStatusLabel(IdentityVerificationStatus status) {
        return switch (status) {
            case NOT_STARTED -> "Chưa xác thực danh tính";
            case PROCESSING -> "Đang xác thực danh tính";
            case PENDING_SERVER_VERIFICATION -> "Đang xác nhận với VNPT";
            case VERIFIED -> "Xác thực danh tính thành công";
            case FAILED -> "Xác thực danh tính thất bại";
        };
    }

    private String identityStatusDetail(IdentityVerificationStatus status) {
        return switch (status) {
            case NOT_STARTED -> "Bắt đầu VNPT eKYC để chụp CCCD và kiểm tra liveness khuôn mặt.";
            case PROCESSING -> "VNPT eKYC đang xử lý phiên xác thực realtime.";
            case PENDING_SERVER_VERIFICATION -> "Hệ thống đang xác nhận kết quả với máy chủ VNPT. Vui lòng chờ.";
            case VERIFIED -> "CCCD và liveness khuôn mặt đã được xác thực qua VNPT eKYC.";
            case FAILED -> "Kết quả VNPT eKYC không hợp lệ. Giáo viên có thể thực hiện lại ngay.";
        };
    }

    private String certificateStatusLabel(CertificateVerificationStatus status) {
        return switch (status) {
            case LOCKED -> "Chưa mở khóa";
            case NOT_SUBMITTED -> "Chưa nộp chứng chỉ";
            case PENDING_REVIEW -> "Đang chờ đối soát chứng chỉ";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Bị từ chối";
        };
    }

    private String certificateStatusDetail(CertificateVerificationStatus status) {
        return switch (status) {
            case LOCKED -> "Hoàn tất xác thực danh tính trước khi nộp chứng chỉ.";
            case NOT_SUBMITTED -> "Chỉ chấp nhận ảnh chứng chỉ JLPT và mã chứng chỉ tương ứng.";
            case PENDING_REVIEW ->
                    "Hệ thống đã đọc chứng chỉ, khớp họ tên và ngày sinh với CCCD, đồng thời kiểm tra trùng. "
                            + "Course Manager sẽ kiểm tra tính xác thực trên Japan Foundation trong 1-2 ngày "
                            + "làm việc, không tính thứ Bảy, Chủ nhật và ngày nghỉ lễ. Bạn có thể dùng không gian "
                            + "giảng viên nhưng khóa học chưa được hiển thị trên nền tảng.";
            case APPROVED -> "Chứng chỉ JLPT đã được xác minh tính xác thực và đạt yêu cầu.";
            case REJECTED ->
                    "Chứng chỉ JLPT chưa đạt yêu cầu xác minh. Vui lòng xem lý do và thực hiện lại theo hướng dẫn.";
        };
    }

    private Map<String, Object> srsTrace() {
        return Map.of(
                "uc", "UC-22",
                "br", List.of(
                        "BR-KYC-01",
                        "BR-KYC-03",
                        "BR-KYC-05",
                        "BR-KYC-CCCD-DUPLICATE",
                        "BR-KYC-JLPT-DUPLICATE",
                        "BR-NOTIF-02",
                        "BR-AUD-01"
                ),
                "msg", List.of(
                        MessageCodes.MSG_KYC_003,
                        MessageCodes.MSG_KYC_002,
                        MessageCodes.MSG_KYC_008,
                        MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED
                ),
                "moduleFlow", List.of(
                        "VNPT realtime identity verification",
                        "JLPT OCR and identity cross-match",
                        "Course Manager authenticity review"
                )
        );
    }

    private record PreparedFile(
            KycDocumentType documentType,
            String fileName,
            String mimeType,
            long fileSize,
            String fileHash,
            byte[] bytes
    ) {
    }

    private record ResultEntry(String key, Object value) {
    }



    private record CertificateEvidence(
            String holderName,
            LocalDate dateOfBirth,
            String level,
            String ocrText
    ) {
    }
}
