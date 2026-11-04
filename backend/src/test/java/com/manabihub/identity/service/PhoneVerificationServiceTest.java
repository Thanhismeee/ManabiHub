package com.manabihub.identity.service;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.enums.PhoneOtpMethod;
import com.manabihub.identity.config.PhoneVerificationSmsProperties;
import com.manabihub.identity.dto.response.PhoneVerificationResponse;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.PhoneVerificationChallenge;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.PhoneVerificationChallengeRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PhoneVerificationChallengeRepository challengeRepository;
    @Mock
    private PayoutSecurityService securityService;
    @Mock
    private SmsSender smsSender;
    @Mock
    private FirebasePhoneIdentityVerifier firebasePhoneIdentityVerifier;

    private PhoneVerificationService service;
    private PhoneVerificationSmsProperties smsProperties;

    @BeforeEach
    void setUp() {
        smsProperties = new PhoneVerificationSmsProperties();
        smsProperties.setSmsMode("console");
        service = new PhoneVerificationService(
                appUserRepository,
                challengeRepository,
                securityService,
                smsSender,
                smsProperties,
                firebasePhoneIdentityVerifier
        );
    }

    @Test
    void requestCodeNormalizesPhoneAndStoresOnlyHash() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder().id(userId).email("student@example.com").build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.empty());
        when(challengeRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
        when(securityService.newOtpNonce()).thenReturn("nonce");
        when(securityService.hashOtp(eq(userId), eq("nonce"), any())).thenReturn("hash");

        PhoneVerificationResponse response = service.requestCode(userId, "+84912345678");

        assertEquals("0912345678", response.phoneNumber());
        assertTrue(!response.verified());
        ArgumentCaptor<PhoneVerificationChallenge> captor = ArgumentCaptor.forClass(PhoneVerificationChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        assertEquals("0912345678", captor.getValue().getPhoneNumber());
        assertEquals("hash", captor.getValue().getCodeHash());
        verify(smsSender).send(eq("0912345678"), any());
    }

    @Test
    void requestCodeRejectsPhoneAlreadyOwnedByAnotherAccount() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AppUser user = AppUser.builder().id(userId).build();
        AppUser other = AppUser.builder().id(otherId).build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(other));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestCode(userId, "0912345678"));

        assertEquals("PHONE_VERIFICATION_ALREADY_IN_USE", exception.getMessageCode());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void confirmCodeSetsVerifiedTimestampAndDeletesChallenge() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder().id(userId).build();
        PhoneVerificationChallenge challenge = PhoneVerificationChallenge.builder()
                .userId(userId)
                .challengeId(UUID.randomUUID())
                .verificationMethod(PhoneOtpMethod.SMS)
                .phoneNumber("0912345678")
                .nonce("nonce")
                .codeHash("hash")
                .expiresAt(Instant.now().plusSeconds(60))
                .failedAttempts(0)
                .createdAt(Instant.now().minusSeconds(5))
                .updatedAt(Instant.now().minusSeconds(5))
                .build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(challenge));
        when(securityService.otpMatches(userId, "nonce", "123456", "hash")).thenReturn(true);
        when(appUserRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(user)).thenReturn(user);

        PhoneVerificationResponse response = service.confirmCode(userId, "0912345678", "123456");

        assertTrue(response.verified());
        assertEquals("0912345678", user.getPhoneNumber());
        assertTrue(user.getPhoneVerifiedAt() != null);
        verify(challengeRepository).delete(challenge);
    }

    @Test
    void verifiedPhoneCannotBeChanged() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .phoneNumber("0912345678")
                .phoneVerifiedAt(Instant.now())
                .build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestCode(userId, "0987654321"));

        assertEquals("PHONE_VERIFICATION_ALREADY_VERIFIED", exception.getMessageCode());
    }

    @Test
    void firebaseModeCreatesChallengeWithoutStoringOrSendingOtp() {
        smsProperties.setSmsMode("firebase");
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder().id(userId).build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.empty());
        when(challengeRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        PhoneVerificationResponse response = service.requestCode(userId, "+84912345678");

        assertEquals("FIREBASE", response.verificationMethod());
        assertEquals("+84912345678", response.phoneNumberE164());
        ArgumentCaptor<PhoneVerificationChallenge> captor =
                ArgumentCaptor.forClass(PhoneVerificationChallenge.class);
        verify(challengeRepository).saveAndFlush(captor.capture());
        assertEquals(PhoneOtpMethod.FIREBASE, captor.getValue().getVerificationMethod());
        assertEquals(null, captor.getValue().getCodeHash());
        assertEquals(null, captor.getValue().getNonce());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void firebaseProofMustMatchChallengePhoneAndFreshAuthTime() {
        smsProperties.setSmsMode("firebase");
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(2);
        AppUser user = AppUser.builder().id(userId).build();
        PhoneVerificationChallenge challenge = PhoneVerificationChallenge.builder()
                .userId(userId)
                .challengeId(challengeId)
                .verificationMethod(PhoneOtpMethod.FIREBASE)
                .phoneNumber("0912345678")
                .expiresAt(Instant.now().plusSeconds(60))
                .resendAvailableAt(Instant.now().plusSeconds(30))
                .failedAttempts(0)
                .createdAt(issuedAt.minusSeconds(60))
                .updatedAt(issuedAt)
                .build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(challenge));
        when(firebasePhoneIdentityVerifier.verify("firebase-token"))
                .thenReturn(new VerifiedFirebasePhone("0912345678", Instant.now(), "firebase-uid"));
        when(appUserRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(user)).thenReturn(user);

        PhoneVerificationResponse response = service.confirmCode(
                userId, "0912345678", null, challengeId, "firebase-token");

        assertTrue(response.verified());
        assertEquals("0912345678", user.getPhoneNumber());
        verify(challengeRepository).delete(challenge);
    }

    @Test
    void firebaseProofIssuedBeforeLatestChallengeIsRejected() {
        smsProperties.setSmsMode("firebase");
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        AppUser user = AppUser.builder().id(userId).build();
        PhoneVerificationChallenge challenge = PhoneVerificationChallenge.builder()
                .userId(userId)
                .challengeId(challengeId)
                .verificationMethod(PhoneOtpMethod.FIREBASE)
                .phoneNumber("0912345678")
                .expiresAt(issuedAt.plusSeconds(60))
                .resendAvailableAt(issuedAt.plusSeconds(30))
                .failedAttempts(0)
                .createdAt(issuedAt.minusSeconds(120))
                .updatedAt(issuedAt)
                .build();
        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(challengeRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(challenge));
        when(firebasePhoneIdentityVerifier.verify("stale-token"))
                .thenReturn(new VerifiedFirebasePhone(
                        "0912345678", issuedAt.minusSeconds(10), "firebase-uid"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.confirmCode(userId, "0912345678", null, challengeId, "stale-token"));

        assertEquals("PHONE_VERIFICATION_INVALID_OTP", exception.getMessageCode());
        assertEquals(1, challenge.getFailedAttempts());
        verify(challengeRepository).save(challenge);
        verify(challengeRepository, never()).delete(challenge);
    }
}
