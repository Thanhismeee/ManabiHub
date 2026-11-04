package com.manabihub.payout.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutSecurityServiceTest {

    private PayoutSecurityService service;

    @BeforeEach
    void setUp() {
        service = new PayoutSecurityService(
                "test-only-payout-secret-key-32chars-minimum-length"
        );
        service.initialize();
    }

    @Test
    void accountEncryption_UsesRandomAuthenticatedCiphertext() {
        String first = service.encryptAccountNumber("0123456789");
        String second = service.encryptAccountNumber("0123456789");

        assertTrue(first.startsWith("enc:v1:"));
        assertNotEquals(first, second);
        assertFalse(first.contains("0123456789"));
        assertEquals("0123456789", service.decryptAccountNumber(first));
        assertEquals("****6789", service.maskAccountNumber(first));
    }

    @Test
    void accountFingerprint_IsStableWithoutExposingPlaintext() {
        String first = service.fingerprintAccountNumber("0123456789");
        String second = service.fingerprintAccountNumber("0123456789");

        assertEquals(first, second);
        assertFalse(first.contains("0123456789"));
    }

    @Test
    void accountEncryption_RejectsTamperedCiphertext() {
        String encrypted = service.encryptAccountNumber("0123456789");
        int changedIndex = encrypted.indexOf(':', "enc:v1".length()) + 5;
        char replacement = encrypted.charAt(changedIndex) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, changedIndex)
                + replacement
                + encrypted.substring(changedIndex + 1);

        assertThrows(
                IllegalStateException.class,
                () -> service.decryptAccountNumber(tampered)
        );
    }

    @Test
    void otpHash_IsBoundToUserNonceAndCode() {
        UUID userId = UUID.randomUUID();
        String nonce = service.newOtpNonce();
        String hash = service.hashOtp(userId, nonce, "123456");

        assertTrue(service.otpMatches(userId, nonce, "123456", hash));
        assertFalse(service.otpMatches(userId, nonce, "654321", hash));
        assertFalse(service.otpMatches(UUID.randomUUID(), nonce, "123456", hash));
    }

    @Test
    void shortSecret_IsRejected() {
        PayoutSecurityService insecure = new PayoutSecurityService("too-short");

        assertThrows(IllegalStateException.class, insecure::initialize);
    }
}
