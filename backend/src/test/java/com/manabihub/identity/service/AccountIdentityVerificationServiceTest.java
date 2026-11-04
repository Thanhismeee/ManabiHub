package com.manabihub.identity.service;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.repository.AccountIdentityVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountIdentityVerificationServiceTest {

    @Mock
    private AccountIdentityVerificationRepository repository;

    @InjectMocks
    private AccountIdentityVerificationService service;

    @Test
    void recordVerified_createsOneAccountLevelIdentity() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.findByIdentityFingerprint("fingerprint")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccountIdentityVerification result = service.recordVerified(
                userId,
                "fingerprint",
                "VNPT_EKYC_WEB_SDK",
                "Nguyen Van A",
                LocalDate.of(1990, 1, 2),
                Instant.parse("2026-08-14T00:00:00Z"),
                "STUDENT");

        assertEquals(userId, result.getUserId());
        assertEquals("fingerprint", result.getIdentityFingerprint());
        assertEquals("STUDENT", result.getSourceSubject());
    }

    @Test
    void recordVerified_isIdempotentAcrossTeacherAndStudentForSameAccount() {
        UUID userId = UUID.randomUUID();
        AccountIdentityVerification existing = verification(userId, "fingerprint");
        when(repository.findById(userId)).thenReturn(Optional.of(existing));

        AccountIdentityVerification result = service.recordVerified(
                userId,
                "fingerprint",
                "VNPT_EKYC_WEB_SDK",
                "Nguyen Van A",
                LocalDate.of(1990, 1, 2),
                Instant.now(),
                "TEACHER");

        assertSame(existing, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recordVerified_rejectsCccdOwnedByAnotherAccount() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.findByIdentityFingerprint("fingerprint"))
                .thenReturn(Optional.of(verification(UUID.randomUUID(), "fingerprint")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.recordVerified(
                userId,
                "fingerprint",
                "VNPT_EKYC_WEB_SDK",
                "Nguyen Van A",
                LocalDate.of(1990, 1, 2),
                Instant.now(),
                "STUDENT"));

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    private AccountIdentityVerification verification(UUID userId, String fingerprint) {
        AccountIdentityVerification verification = new AccountIdentityVerification();
        verification.setUserId(userId);
        verification.setIdentityFingerprint(fingerprint);
        verification.setProvider("VNPT_EKYC_WEB_SDK");
        verification.setVerifiedAt(Instant.now());
        verification.setSourceSubject("STUDENT");
        return verification;
    }
}
