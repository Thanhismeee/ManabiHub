package com.manabihub.identity.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.identity.dto.request.StudentIdentityVerificationRequest;
import com.manabihub.identity.dto.response.StudentIdentityVerificationResponse;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.AccountIdentityVerificationService;
import com.manabihub.kyc.domain.VnptIdentityTransactionClaim;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.identity.service.DatabaseAuthRateLimiter;
import com.manabihub.identity.service.StudentIdentityVerificationService;
import com.manabihub.kyc.port.NationalIdRecordDto;
import com.manabihub.kyc.port.NationalIdRegistryPort;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.kyc.service.VnptSdkDecision;
import com.manabihub.kyc.service.VnptSdkPayloadPolicy;
import com.manabihub.kyc.service.VnptSdkResultEvaluator;
import lombok.RequiredArgsConstructor;
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
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentIdentityVerificationServiceImpl implements StudentIdentityVerificationService {

    private static final String DIRECT_SDK_DEMO_PROVIDER = "VNPT_EKYC_WEB_SDK_DEMO";
    private static final String DIRECT_SDK_UAT_PROVIDER = "VNPT_EKYC_WEB_SDK_UAT";
    private static final String SERVER_VERIFIED_PROVIDER = "VNPT_EKYC_WEB_SDK";
    private static final String SESSION_REPLAY_PREFIX = "SESSION:";
    private static final int MAX_PROVIDER_ID_LENGTH = 128;
    private static final int MAX_SERVER_ID_LENGTH = 64;
    private static final int MAX_SERVER_NAME_LENGTH = 240;
    private static final int MAX_SERVER_DATE_LENGTH = 32;
    private static final Duration SERVER_RESULT_MAX_AGE = Duration.ofMinutes(30);
    private static final Duration SERVER_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final List<String> ID_KEYS = List.of("idNumber", "idNo", "identityNumber", "documentNumber", "cardNumber", "soCccd", "cccd", "soid", "id");
    private static final List<String> NAME_KEYS = List.of("fullName", "name", "hoTen", "hoten", "holderName");
    private static final List<String> DOB_KEYS = List.of("dateOfBirth", "dob", "birthDate", "ngaySinh", "birthday");
    private static final List<DateTimeFormatter> DOB_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.BASIC_ISO_DATE
    );

    private final StudentProfileRepository studentProfileRepository;
    private final CurrentUserService currentUserService;
    private final NationalIdRegistryPort nationalIdRegistry;
    private final VnptVerificationPort vnptVerificationPort;
    private final VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;
    private final DatabaseAuthRateLimiter vnptKycRateLimiter;
    private final AccountIdentityVerificationService accountIdentityVerificationService;
    private final SecurityAuditService securityAuditService;

    @Value("${manabihub.kyc.identity-secret:}")
    private String identitySecret;

    @Value("${manabihub.kyc.identity-verification-mode:direct-sdk-mock}")
    private String identityVerificationMode;

    @Override
    @Transactional(readOnly = true)
    public StudentIdentityVerificationResponse getStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        return accountIdentityVerificationService.findVerified(userId)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(requireStudent(userId)));
    }

    @Override
    @Transactional
    public StudentIdentityVerificationResponse verify(StudentIdentityVerificationRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        StudentProfile student = requireStudent(userId);
        Optional<AccountIdentityVerification> sharedVerification =
                accountIdentityVerificationService.findVerified(userId);
        if (sharedVerification.isPresent()) {
            return toResponse(sharedVerification.get());
        }
        if (student.getIdentityVerifiedAt() != null) {
            return toResponse(student);
        }
        VnptSdkPayloadPolicy.validate(request.sdkResult());
        boolean directSdkDemo = isDirectSdkDemo();
        boolean directSdkUat = isDirectSdkUat();
        ProviderBinding providerBinding = null;
        if (!directSdkDemo) {
            providerBinding = requireProviderBinding(request, directSdkUat);
            if (!vnptKycRateLimiter.consume(
                    "student-vnpt-kyc",
                    userId.toString(),
                    "/api/v1/student/identity-verification",
                    6,
                    600,
                    600)) {
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "VNPT identity verification is temporarily rate limited",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            Optional<VnptIdentityTransactionClaim> existingClaim =
                    vnptIdentityTransactionClaimRepository
                            .findByProviderAndProviderTransactionId(
                                    SERVER_VERIFIED_PROVIDER,
                                    providerBinding.replayId());
            if (existingClaim.isPresent()) {
                VnptIdentityTransactionClaim claim = existingClaim.get();
                boolean sameCompletedStudent = userId.equals(claim.getUserId())
                        && "STUDENT".equals(claim.getSubjectType())
                        && providerBinding.sessionId().equals(claim.getProviderSessionId())
                        && student.getIdentityVerifiedAt() != null;
                if (sameCompletedStudent) {
                    return toResponse(student);
                }
                throw new BusinessException(
                        MessageCodes.MSG_KYC_008,
                        "VNPT transaction is already bound to another identity verification",
                        HttpStatus.CONFLICT);
            }
        }
        VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(request.sdkResult());
        if (!decision.verified()) {
            String reason = decision.failureReasons().isEmpty()
                    ? "VNPT eKYC verification was not successful"
                    : decision.failureReasons().get(0);
            throw invalidIdentity(reason);
        }

        VerifiedIdentity verifiedIdentity;
        if (directSdkDemo || directSdkUat) {
            verifiedIdentity = new VerifiedIdentity(
                    decision.identityOcr().get("idNumber"),
                    decision.identityOcr().get("fullName"),
                    decision.identityOcr().get("dateOfBirth"),
                    directSdkDemo ? DIRECT_SDK_DEMO_PROVIDER : DIRECT_SDK_UAT_PROVIDER);
        } else {
            verifiedIdentity = verifyWithVnptServer(providerBinding);
        }

        String idNumber = normalizeId(verifiedIdentity.idNumber());
        String fullName = verifiedIdentity.fullName();
        String dateOfBirthRaw = verifiedIdentity.dateOfBirth();
        LocalDate dateOfBirth = parseDate(dateOfBirthRaw);

        String verifiedFullName = fullName.trim();
        LocalDate verifiedDateOfBirth = dateOfBirth;
        if (directSdkDemo) {
            NationalIdRecordDto registry = nationalIdRegistry.findActiveByIdNumber(idNumber)
                    .orElseThrow(() -> invalidIdentity("CCCD không tồn tại trong dữ liệu demo."));
            if (!sameName(fullName, registry.fullName()) || !dateOfBirth.equals(registry.dateOfBirth())) {
                throw invalidIdentity("Thông tin VNPT không khớp dữ liệu CCCD demo.");
            }
            verifiedFullName = registry.fullName();
            verifiedDateOfBirth = registry.dateOfBirth();
        }

        ensureSecret();
        String identityFingerprint = fingerprint(idNumber);
        if (student.getIdentityVerifiedAt() != null) {
            if (identityFingerprint.equals(student.getIdentityFingerprint())) {
                return toResponse(student);
            }
            throw new BusinessException(
                    MessageCodes.MSG_KYC_008,
                    "A verified CCCD cannot be replaced on this account",
                    HttpStatus.CONFLICT);
        }

        Instant verifiedAt = Instant.now();
        if (!directSdkDemo) {
            bindProviderTransaction(userId, providerBinding, Instant.now());
        }

        try {
            accountIdentityVerificationService.recordVerified(
                    userId,
                    identityFingerprint,
                    verifiedIdentity.provider(),
                    verifiedFullName,
                    verifiedDateOfBirth,
                    verifiedAt,
                    "STUDENT");
        } catch (BusinessException exception) {
            if (MessageCodes.MSG_KYC_008.equals(exception.getMessageCode())) {
                throw duplicateIdentity(student, userId, exception);
            }
            throw exception;
        }

        student.setIdentityFingerprint(identityFingerprint);
        student.setIdentityProvider(verifiedIdentity.provider());
        student.setIdentityFullName(verifiedFullName);
        student.setIdentityDateOfBirth(verifiedDateOfBirth);
        student.setIdentityVerifiedAt(verifiedAt);
        try {
            return toResponse(studentProfileRepository.save(student));
        } catch (DataIntegrityViolationException ex) {
            // The fingerprint is unique across students. Do not leak the raw
            // identity or database constraint to the client.
            throw duplicateIdentity(student, userId, ex);
        }
    }

    private BusinessException duplicateIdentity(StudentProfile student, UUID userId, Throwable cause) {
        securityAuditService.logStudentDuplicateIdentityAudit(student.getId(), userId);
        return new BusinessException(
                MessageCodes.MSG_IDV_002,
                "CCCD này đã được liên kết với một tài khoản khác.",
                HttpStatus.CONFLICT,
                cause);
    }

    private VerifiedIdentity verifyWithVnptServer(ProviderBinding providerBinding) {
        String providerTransactionId = providerBinding.transactionId();
        String providerSessionId = providerBinding.sessionId();
        Instant requestedAt = Instant.now();

        VnptServerVerificationResult result;
        try {
            result = vnptVerificationPort.verifyTransaction(providerTransactionId, providerSessionId);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_002,
                    "VNPT server verification is unavailable",
                    HttpStatus.BAD_GATEWAY,
                    exception);
        }

        if (result == null || !result.verified()) {
            throw invalidIdentity("VNPT server did not confirm this verification transaction.");
        }
        if (!providerTransactionId.equals(result.confirmedTransactionId())
                || !providerSessionId.equals(result.confirmedSessionId())) {
            throw invalidIdentity("VNPT server confirmation does not match the requested transaction.");
        }
        if (!isAcceptedServerText(result.serverIdNumber(), MAX_SERVER_ID_LENGTH)
                || !isAcceptedServerText(result.serverFullName(), MAX_SERVER_NAME_LENGTH)
                || !isAcceptedServerText(result.serverDateOfBirth(), MAX_SERVER_DATE_LENGTH)) {
            throw invalidIdentity("VNPT server confirmation is missing verified identity data.");
        }
        if (result.providerVerifiedAt() == null
                || result.providerVerifiedAt().isBefore(requestedAt.minus(SERVER_RESULT_MAX_AGE))
                || result.providerVerifiedAt().isAfter(requestedAt.plus(SERVER_CLOCK_SKEW))) {
            throw invalidIdentity("VNPT server confirmation timestamp is invalid or expired.");
        }

        return new VerifiedIdentity(
                result.serverIdNumber(),
                result.serverFullName(),
                result.serverDateOfBirth(),
                SERVER_VERIFIED_PROVIDER);
    }

    private void bindProviderTransaction(
            UUID userId,
            ProviderBinding providerBinding,
            Instant claimedAt
    ) {
        VnptIdentityTransactionClaim claim = new VnptIdentityTransactionClaim();
        claim.setUserId(userId);
        claim.setSubjectType("STUDENT");
        claim.setProvider(SERVER_VERIFIED_PROVIDER);
        claim.setProviderTransactionId(providerBinding.replayId());
        claim.setProviderSessionId(providerBinding.sessionId());
        claim.setClaimedAt(claimedAt);
        try {
            vnptIdentityTransactionClaimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_008,
                    "VNPT transaction or session is already bound to another identity verification",
                    HttpStatus.CONFLICT,
                    exception);
        }
    }

    private boolean isAcceptedServerText(String value, int maxLength) {
        return StringUtils.hasText(value)
                && value.length() <= maxLength
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private String requireProviderId(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalidIdentity("Missing VNPT " + fieldName + ".");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_PROVIDER_ID_LENGTH) {
            throw invalidIdentity("VNPT " + fieldName + " exceeds the accepted length.");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidIdentity("VNPT " + fieldName + " contains invalid control characters.");
        }
        return normalized;
    }

    private ProviderBinding requireProviderBinding(
            StudentIdentityVerificationRequest request,
            boolean transactionOptional
    ) {
        String sessionId = requireProviderId(request.providerSessionId(), "providerSessionId");
        String transactionId = StringUtils.hasText(request.providerTransactionId())
                ? requireProviderId(request.providerTransactionId(), "providerTransactionId")
                : null;
        if (!transactionOptional && transactionId == null) {
            throw invalidIdentity("Missing VNPT providerTransactionId.");
        }
        String replayId = transactionId;
        if (replayId == null) {
            int maximumSessionLength = MAX_PROVIDER_ID_LENGTH - SESSION_REPLAY_PREFIX.length();
            if (sessionId.length() > maximumSessionLength) {
                throw invalidIdentity("VNPT providerSessionId exceeds the accepted replay-binding length.");
            }
            replayId = SESSION_REPLAY_PREFIX + sessionId;
        }
        return new ProviderBinding(transactionId, sessionId, replayId);
    }

    private StudentProfile requireStudent(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND,
                        "Student profile was not found",
                        HttpStatus.NOT_FOUND));
    }

    private StudentIdentityVerificationResponse toResponse(StudentProfile student) {
        boolean verified = student.getIdentityVerifiedAt() != null;
        boolean uatEvaluated = !verified
                && DIRECT_SDK_UAT_PROVIDER.equals(student.getIdentityProvider())
                && StringUtils.hasText(student.getIdentityFullName())
                && student.getIdentityDateOfBirth() != null;
        return new StudentIdentityVerificationResponse(
                verified,
                verified ? "VERIFIED" : uatEvaluated ? "UAT_EVALUATED" : "NOT_VERIFIED",
                verified || uatEvaluated ? student.getIdentityProvider() : null,
                verified || uatEvaluated ? "••••••••" : null,
                verified || uatEvaluated ? student.getIdentityFullName() : null,
                verified || uatEvaluated ? student.getIdentityDateOfBirth() : null,
                student.getIdentityVerifiedAt());
    }

    private StudentIdentityVerificationResponse toResponse(AccountIdentityVerification verification) {
        return new StudentIdentityVerificationResponse(
                true,
                "VERIFIED",
                verification.getProvider(),
                "••••••••",
                verification.getFullName(),
                verification.getDateOfBirth(),
                verification.getVerifiedAt());
    }

    private String normalizeId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw invalidIdentity("VNPT chưa trả về số CCCD.");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 12) {
            throw invalidIdentity("Số CCCD phải gồm đúng 12 chữ số.");
        }
        return digits;
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw invalidIdentity("VNPT chưa trả về ngày sinh.");
        }
        for (DateTimeFormatter formatter : DOB_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported VNPT representation.
            }
        }
        throw invalidIdentity("Ngày sinh VNPT trả về không hợp lệ.");
    }

    private boolean sameName(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.replaceAll("[^\\p{Alnum}]", "")
                .toUpperCase(Locale.ROOT);
    }

    private String firstValue(Object value, List<String> keys) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && keys.stream().anyMatch(key -> key.equalsIgnoreCase(String.valueOf(entry.getKey())))) {
                    String result = scalar(entry.getValue());
                    if (StringUtils.hasText(result)) {
                        return result.trim();
                    }
                }
            }
            for (Object child : map.values()) {
                String result = firstValue(child, keys);
                if (StringUtils.hasText(result)) {
                    return result;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                String result = firstValue(child, keys);
                if (StringUtils.hasText(result)) {
                    return result;
                }
            }
        }
        return null;
    }

    private String scalar(Object value) {
        return value instanceof String || value instanceof Number ? String.valueOf(value) : null;
    }

    private String fingerprint(String idNumber) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(identitySecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(idNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new BusinessException(MessageCodes.COMMON_INTERNAL_ERROR, "Không thể lưu bằng chứng xác minh demo", HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private void ensureSecret() {
        if (!StringUtils.hasText(identitySecret) || identitySecret.trim().length() < 32) {
            throw new BusinessException(MessageCodes.COMMON_INTERNAL_ERROR, "KYC_IDENTITY_SECRET chưa được cấu hình đủ mạnh", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isDirectSdkDemo() {
        return "direct-sdk-mock".equalsIgnoreCase(identityVerificationMode);
    }

    private boolean isDirectSdkUat() {
        return "direct-sdk".equalsIgnoreCase(identityVerificationMode);
    }

    private BusinessException invalidIdentity(String message) {
        return new BusinessException(MessageCodes.MSG_KYC_002, message, HttpStatus.BAD_REQUEST);
    }

    private record VerifiedIdentity(
            String idNumber,
            String fullName,
            String dateOfBirth,
            String provider) {
    }

    private record ProviderBinding(
            String transactionId,
            String sessionId,
            String replayId) {
    }
}
