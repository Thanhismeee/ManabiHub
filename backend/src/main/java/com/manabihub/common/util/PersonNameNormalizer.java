package com.manabihub.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical form used whenever two person names must be compared:
 * KYC OCR against the national-ID registry, JLPT certificate against CCCD,
 * and bank-account holder against the verified identity (BR-WAL-04).
 */
public final class PersonNameNormalizer {

    private PersonNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String preprocessed = value.replace('Đ', 'D').replace('đ', 'd');
        return Normalizer.normalize(preprocessed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }
}