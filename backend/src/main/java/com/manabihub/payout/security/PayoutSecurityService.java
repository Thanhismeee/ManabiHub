package com.manabihub.payout.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class PayoutSecurityService {

    private static final String ENCRYPTION_PREFIX = "enc:v1:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String configuredSecret;
    private SecretKeySpec encryptionKey;
    private SecretKeySpec hmacKey;

    public PayoutSecurityService(
            @Value("${manabihub.payout.security-secret:}") String configuredSecret
    ) {
        this.configuredSecret = configuredSecret;
    }

    @PostConstruct
    void initialize() {
        if (configuredSecret == null
                || configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "PAYOUT_SECURITY_SECRET must contain at least 32 UTF-8 bytes"
            );
        }

        byte[] rootKey = sha256(configuredSecret.getBytes(StandardCharsets.UTF_8));
        encryptionKey = new SecretKeySpec(sha256(join("encryption", rootKey)), "AES");
        hmacKey = new SecretKeySpec(sha256(join("authentication", rootKey)), "HmacSHA256");
    }

    public String encryptAccountNumber(String accountNumber) {
        String normalized = normalizeAccountNumber(accountNumber);
        if (isEncrypted(normalized)) {
            return normalized;
        }

        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return ENCRYPTION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt payout account number", exception);
        }
    }

    public String decryptAccountNumber(String storedAccountNumber) {
        if (storedAccountNumber == null || !isEncrypted(storedAccountNumber)) {
            return storedAccountNumber;
        }

        try {
            byte[] payload = Base64.getUrlDecoder()
                    .decode(storedAccountNumber.substring(ENCRYPTION_PREFIX.length()));
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Encrypted payout account payload is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt payout account number", exception);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }

    public String fingerprintAccountNumber(String accountNumber) {
        return hmac("bank-account:" + normalizeAccountNumber(decryptAccountNumber(accountNumber)));
    }

    public String newOtpNonce() {
        byte[] nonce = new byte[18];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    public String hashOtp(UUID userId, String nonce, String code) {
        return hmac("withdrawal-otp:" + userId + ":" + nonce + ":" + code);
    }

    public boolean otpMatches(UUID userId, String nonce, String code, String expectedHash) {
        if (code == null || expectedHash == null) {
            return false;
        }
        byte[] actual = hashOtp(userId, nonce, code).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    public String maskAccountNumber(String storedAccountNumber) {
        String accountNumber = decryptAccountNumber(storedAccountNumber);
        if (accountNumber == null || accountNumber.isBlank()) {
            return "****";
        }
        String normalized = accountNumber.trim();
        int visible = Math.min(4, normalized.length());
        return "****" + normalized.substring(normalized.length() - visible);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect payout data", exception);
        }
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        return accountNumber.trim();
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] join(String purpose, byte[] key) {
        byte[] prefix = purpose.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(prefix.length + key.length)
                .put(prefix)
                .put(key)
                .array();
    }
}
