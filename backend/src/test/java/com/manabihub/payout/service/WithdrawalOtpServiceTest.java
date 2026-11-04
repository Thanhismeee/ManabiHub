package com.manabihub.payout.service;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.enums.PhoneOtpMethod;
import com.manabihub.common.mail.EmailService;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.FirebasePhoneIdentityVerifier;
import com.manabihub.identity.service.VerifiedFirebasePhone;
import com.manabihub.payout.config.WithdrawalOtpProperties;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.entity.WithdrawalOtpChallenge;
import com.manabihub.payout.repository.WithdrawalOtpChallengeRepository;
import com.manabihub.payout.security.PayoutSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalOtpServiceTest {

    @Mock private WithdrawalOtpChallengeRepository challengeRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private EmailService emailService;
    @Mock private FirebasePhoneIdentityVerifier firebasePhoneIdentityVerifier;

    private PayoutSecurityService securityService;
    private WithdrawalOtpService service;
    private WithdrawalOtpProperties otpProperties;
    private UUID userId;
    private AppUser user;

    @BeforeEach
    void setUp() {
        securityService = new PayoutSecurityService(
                "test-only-payout-secret-key-32chars-minimum-length"
        );
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                securityService,
                "initialize"
        );
        otpProperties = new WithdrawalOtpProperties();
        otpProperties.setWithdrawalOtpMode("email");
        service = new WithdrawalOtpService(
                challengeRepository,
                appUserRepository,
                emailService,
                securityService,
                otpProperties,
                firebasePhoneIdentityVerifier
        );
        userId = UUID.randomUUID();
        user = AppUser.builder()
                .id(userId)
                .email("teacher@example.com")
                .fullName("Teacher")
                .build();
    }

    @Test
    void sendOtp_PersistsOnlyHashAndAppliesCooldown() {
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(userId)).thenReturn(Optional.empty());

        service.sendOtp(userId.toString());

        ArgumentCaptor<WithdrawalOtpChallenge> challengeCaptor =
                ArgumentCaptor.forClass(WithdrawalOtpChallenge.class);
        verify(challengeRepository).saveAndFlush(challengeCaptor.capture());
        WithdrawalOtpChallenge challenge = challengeCaptor.getValue();
        assertEquals(userId, challenge.getUserId());
        assertTrue(challenge.getExpiresAt().isAfter(Instant.now()));
        assertTrue(challenge.getResendAvailableAt().isAfter(Instant.now()));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(
                org.mockito.ArgumentMatchers.eq("teacher@example.com"),
                any(),
                bodyCaptor.capture()
        );
        Matcher matcher = Pattern.compile(">(\\d{6})</h2>").matcher(bodyCaptor.getValue());
        assertTrue(matcher.find());
        String plaintextCode = matcher.group(1);
        assertNotEquals(plaintextCode, challenge.getCodeHash());
        assertTrue(securityService.otpMatches(
                userId,
                challenge.getNonce(),
                plaintextCode,
                challenge.getCodeHash()
        ));
    }

    @Test
    void sendOtp_BeforeCooldownExpires_IsRejectedWithoutEmail() {
        WithdrawalOtpChallenge existing = challenge("123456");
        existing.setResendAvailableAt(Instant.now().plusSeconds(30));
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(userId)).thenReturn(Optional.of(existing));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.sendOtp(userId.toString())
        );

        assertEquals("PAYOUT_OTP_RATE_LIMITED", error.getMessageCode());
        verifyNoInteractions(emailService);
    }

    @Test
    void consumeOtp_DeletesChallengeAfterOneSuccessfulUse() {
        WithdrawalOtpChallenge challenge = challenge("123456");
        when(challengeRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(challenge));

        service.consumeOtp(userId.toString(), "123456");

        verify(challengeRepository).delete(challenge);
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void consumeOtp_InvalidCodePersistsFailedAttempt() {
        WithdrawalOtpChallenge challenge = challenge("123456");
        when(challengeRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(challenge));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.consumeOtp(userId.toString(), "654321")
        );

        assertEquals("PAYOUT_INVALID_OTP", error.getMessageCode());
        assertEquals(1, challenge.getFailedAttempts());
        verify(challengeRepository).save(challenge);
        verify(challengeRepository, never()).delete(any());
    }

    private WithdrawalOtpChallenge challenge(String code) {
        Instant now = Instant.now();
        String nonce = securityService.newOtpNonce();
        return WithdrawalOtpChallenge.builder()
                .userId(userId)
                .challengeId(UUID.randomUUID())
                .verificationMethod(PhoneOtpMethod.EMAIL)
                .nonce(nonce)
                .codeHash(securityService.hashOtp(userId, nonce, code))
                .expiresAt(now.plusSeconds(300))
                .resendAvailableAt(now)
                .failedAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void firebaseModeCreatesPhoneChallengeWithoutEmailOrPlaintextCode() {
        otpProperties.setWithdrawalOtpMode("firebase");
        user.setPhoneNumber("0912345678");
        user.setPhoneVerifiedAt(Instant.now());
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(userId)).thenReturn(Optional.empty());

        var response = service.sendOtp(userId.toString());

        assertEquals("FIREBASE", response.verificationMethod());
        assertEquals("+84912345678", response.phoneNumberE164());
        ArgumentCaptor<WithdrawalOtpChallenge> captor =
                ArgumentCaptor.forClass(WithdrawalOtpChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        assertEquals(PhoneOtpMethod.FIREBASE, captor.getValue().getVerificationMethod());
        assertEquals(null, captor.getValue().getCodeHash());
        assertEquals("0912345678", captor.getValue().getPhoneNumber());
        verifyNoInteractions(emailService);
    }

    @Test
    void firebaseWithdrawalProofIsFreshPhoneBoundAndConsumedOnce() {
        otpProperties.setWithdrawalOtpMode("firebase");
        Instant issuedAt = Instant.now().minusSeconds(2);
        UUID challengeId = UUID.randomUUID();
        WithdrawalOtpChallenge challenge = firebaseChallenge(challengeId, issuedAt);
        when(challengeRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(challenge));
        when(firebasePhoneIdentityVerifier.verify("firebase-token"))
                .thenReturn(new VerifiedFirebasePhone("0912345678", Instant.now(), "firebase-uid"));
        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .phoneAuthChallengeId(challengeId)
                .firebaseIdToken("firebase-token")
                .build();

        service.consumeVerification(userId.toString(), request);

        verify(challengeRepository).delete(challenge);
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void firebaseWithdrawalRejectsTokenFromPreviousChallenge() {
        otpProperties.setWithdrawalOtpMode("firebase");
        Instant issuedAt = Instant.now();
        UUID challengeId = UUID.randomUUID();
        WithdrawalOtpChallenge challenge = firebaseChallenge(challengeId, issuedAt);
        when(challengeRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(challenge));
        when(firebasePhoneIdentityVerifier.verify("stale-token"))
                .thenReturn(new VerifiedFirebasePhone(
                        "0912345678", issuedAt.minusSeconds(10), "firebase-uid"));
        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .phoneAuthChallengeId(challengeId)
                .firebaseIdToken("stale-token")
                .build();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.consumeVerification(userId.toString(), request));

        assertEquals("PAYOUT_INVALID_OTP", error.getMessageCode());
        assertEquals(1, challenge.getFailedAttempts());
        verify(challengeRepository).save(challenge);
        verify(challengeRepository, never()).delete(challenge);
    }

    @Test
    void firebaseWithdrawalRejectsTokenForDifferentPhone() {
        otpProperties.setWithdrawalOtpMode("firebase");
        Instant issuedAt = Instant.now().minusSeconds(2);
        UUID challengeId = UUID.randomUUID();
        WithdrawalOtpChallenge challenge = firebaseChallenge(challengeId, issuedAt);
        when(challengeRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(challenge));
        when(firebasePhoneIdentityVerifier.verify("wrong-phone-token"))
                .thenReturn(new VerifiedFirebasePhone("0987654321", Instant.now(), "firebase-uid"));
        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .phoneAuthChallengeId(challengeId)
                .firebaseIdToken("wrong-phone-token")
                .build();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.consumeVerification(userId.toString(), request));

        assertEquals("PAYOUT_INVALID_OTP", error.getMessageCode());
        verify(challengeRepository, never()).delete(challenge);
    }

    private WithdrawalOtpChallenge firebaseChallenge(UUID challengeId, Instant issuedAt) {
        return WithdrawalOtpChallenge.builder()
                .userId(userId)
                .challengeId(challengeId)
                .verificationMethod(PhoneOtpMethod.FIREBASE)
                .phoneNumber("0912345678")
                .expiresAt(issuedAt.plusSeconds(300))
                .resendAvailableAt(issuedAt.plusSeconds(60))
                .failedAttempts(0)
                .createdAt(issuedAt.minusSeconds(60))
                .updatedAt(issuedAt)
                .build();
    }
}
