package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.enums.PhoneOtpMethod;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.util.PhoneNumberNormalizer;
import com.manabihub.identity.config.PhoneVerificationSmsProperties;
import com.manabihub.identity.dto.response.PhoneVerificationResponse;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.PhoneVerificationChallenge;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.PhoneVerificationChallengeRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneVerificationService {

    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration FIREBASE_AUTH_TIME_SKEW = Duration.ofSeconds(5);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AppUserRepository appUserRepository;
    private final PhoneVerificationChallengeRepository challengeRepository;
    private final PayoutSecurityService securityService;
    private final SmsSender smsSender;
    private final PhoneVerificationSmsProperties smsProperties;
    private final FirebasePhoneIdentityVerifier firebasePhoneIdentityVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PhoneVerificationResponse requestCode(UUID userId, String requestedPhoneNumber) {
        AppUser user = findUserForUpdate(userId);
        String phoneNumber = normalizeAndValidate(requestedPhoneNumber);

        if (user.getPhoneVerifiedAt() != null) {
            if (Objects.equals(user.getPhoneNumber(), phoneNumber)) {
                return PhoneVerificationResponse.verified(user.getPhoneNumber(), user.getPhoneVerifiedAt());
            }
            throw alreadyVerified();
        }

        ensureAvailableForUser(phoneNumber, userId);
        Instant now = Instant.now();
        PhoneVerificationChallenge challenge = challengeRepository.findByUserIdForUpdate(userId).orElse(null);
        if (challenge != null && challenge.getResendAvailableAt().isAfter(now)) {
            throw new BusinessException(
                    MessageCodes.PHONE_VERIFICATION_RATE_LIMITED,
                    "Please wait before requesting another verification code",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        boolean firebaseMode = isFirebaseMode();
        UUID challengeId = UUID.randomUUID();
        String code = firebaseMode ? null : String.format("%06d", secureRandom.nextInt(1_000_000));
        String nonce = firebaseMode ? null : securityService.newOtpNonce();
        if (challenge == null) {
            challenge = PhoneVerificationChallenge.builder()
                    .userId(userId)
                    .createdAt(now)
                    .build();
        }
        challenge.setPhoneNumber(phoneNumber);
        challenge.setChallengeId(challengeId);
        challenge.setVerificationMethod(firebaseMode ? PhoneOtpMethod.FIREBASE : PhoneOtpMethod.SMS);
        challenge.setNonce(nonce);
        challenge.setCodeHash(firebaseMode ? null : securityService.hashOtp(userId, nonce, code));
        challenge.setExpiresAt(now.plus(OTP_LIFETIME));
        challenge.setResendAvailableAt(now.plus(RESEND_COOLDOWN));
        challenge.setFailedAttempts(0);
        challenge.setUpdatedAt(now);
        challengeRepository.saveAndFlush(challenge);

        if (!firebaseMode) {
            smsSender.send(phoneNumber, "Ma xac thuc ManabiHub cua ban la " + code + ". Ma co hieu luc trong 5 phut.");
        }
        return new PhoneVerificationResponse(
                phoneNumber,
                false,
                null,
                challenge.getVerificationMethod().name(),
                challengeId,
                PhoneNumberNormalizer.toE164(phoneNumber),
                challenge.getExpiresAt()
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class
    )
    public PhoneVerificationResponse confirmCode(UUID userId, String requestedPhoneNumber, String code) {
        return confirmCode(userId, requestedPhoneNumber, code, null, null);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class
    )
    public PhoneVerificationResponse confirmCode(
            UUID userId,
            String requestedPhoneNumber,
            String code,
            UUID challengeId,
            String firebaseIdToken
    ) {
        AppUser user = findUserForUpdate(userId);
        String phoneNumber = normalizeAndValidate(requestedPhoneNumber);
        if (user.getPhoneVerifiedAt() != null) {
            if (Objects.equals(user.getPhoneNumber(), phoneNumber)) {
                return PhoneVerificationResponse.verified(user.getPhoneNumber(), user.getPhoneVerifiedAt());
            }
            throw alreadyVerified();
        }

        PhoneVerificationChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidCode);
        Instant now = Instant.now();
        if (!Objects.equals(challenge.getPhoneNumber(), phoneNumber)
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            if (!challenge.getExpiresAt().isAfter(now)) {
                challengeRepository.delete(challenge);
            }
            throw invalidCode();
        }

        boolean proofMatches = challenge.getVerificationMethod() == PhoneOtpMethod.FIREBASE
                ? firebaseProofMatches(challenge, challengeId, firebaseIdToken, now)
                : smsProofMatches(userId, challenge, code);
        if (!proofMatches) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            challenge.setUpdatedAt(now);
            challengeRepository.save(challenge);
            throw invalidCode();
        }

        ensureAvailableForUser(phoneNumber, userId);
        user.setPhoneNumber(phoneNumber);
        user.setPhoneVerifiedAt(now);
        appUserRepository.saveAndFlush(user);
        challengeRepository.delete(challenge);
        return PhoneVerificationResponse.verified(phoneNumber, now);
    }

    private boolean smsProofMatches(UUID userId, PhoneVerificationChallenge challenge, String code) {
        return code != null
                && challenge.getNonce() != null
                && challenge.getCodeHash() != null
                && securityService.otpMatches(userId, challenge.getNonce(), code, challenge.getCodeHash());
    }

    private boolean firebaseProofMatches(
            PhoneVerificationChallenge challenge,
            UUID challengeId,
            String firebaseIdToken,
            Instant now
    ) {
        if (!Objects.equals(challenge.getChallengeId(), challengeId)
                || firebaseIdToken == null
                || firebaseIdToken.isBlank()) {
            return false;
        }
        try {
            VerifiedFirebasePhone verified = firebasePhoneIdentityVerifier.verify(firebaseIdToken);
            Instant challengeIssuedAt = challenge.getUpdatedAt() != null
                    ? challenge.getUpdatedAt()
                    : challenge.getCreatedAt();
            return Objects.equals(challenge.getPhoneNumber(), verified.phoneNumber())
                    && !verified.authenticatedAt().isBefore(
                            challengeIssuedAt.minus(FIREBASE_AUTH_TIME_SKEW))
                    && !verified.authenticatedAt().isAfter(now.plusSeconds(30));
        } catch (FirebasePhoneVerificationException exception) {
            if (exception.isConfigurationFailure()) {
                throw new BusinessException(
                        MessageCodes.PHONE_VERIFICATION_SMS_NOT_CONFIGURED,
                        "Firebase Phone Auth is not configured",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        exception
                );
            }
            return false;
        }
    }

    private boolean isFirebaseMode() {
        return "firebase".equalsIgnoreCase(
                smsProperties.getSmsMode() == null ? "" : smsProperties.getSmsMode().trim());
    }

    private AppUser findUserForUpdate(UUID userId) {
        return appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));
    }

    private String normalizeAndValidate(String phoneNumber) {
        String normalized = PhoneNumberNormalizer.normalize(phoneNumber);
        if (normalized == null || !normalized.matches("0\\d{9}")) {
            throw new BusinessException(MessageCodes.MSG_PRO_002, "Phone number is invalid");
        }
        return normalized;
    }

    private void ensureAvailableForUser(String phoneNumber, UUID userId) {
        appUserRepository.findByPhoneNumber(phoneNumber)
                .filter(existing -> !Objects.equals(existing.getId(), userId))
                .ifPresent(existing -> {
                    throw new BusinessException(
                            MessageCodes.PHONE_VERIFICATION_ALREADY_IN_USE,
                            "This phone number is already used by another account",
                            HttpStatus.CONFLICT
                    );
                });
    }

    private BusinessException alreadyVerified() {
        return new BusinessException(
                MessageCodes.PHONE_VERIFICATION_ALREADY_VERIFIED,
                "A verified phone number cannot be changed",
                HttpStatus.CONFLICT
        );
    }

    private BusinessException invalidCode() {
        return new BusinessException(MessageCodes.PHONE_VERIFICATION_INVALID_OTP, "Invalid or expired verification code");
    }
}
