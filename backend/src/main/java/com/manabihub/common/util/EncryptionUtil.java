package com.manabihub.common.util;

import java.util.Base64;

/**
 * Utility class for simple encryption/decryption of sensitive data.
 * NOTE: For production, this should be replaced with a robust encryption mechanism
 * like Jasypt or AES with a securely managed key.
 */
public class EncryptionUtil {
    
    public static String encrypt(String rawValue) {
        if (rawValue == null) return null;
        return Base64.getEncoder().encodeToString(rawValue.getBytes());
    }
    
    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        return new String(Base64.getDecoder().decode(encryptedValue));
    }
    
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
