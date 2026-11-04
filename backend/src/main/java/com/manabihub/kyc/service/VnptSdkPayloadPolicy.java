package com.manabihub.kyc.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Array;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side bounds for the untrusted VNPT browser callback payload.
 *
 * <p>The frontend compacts the vendor result, but callers can bypass the
 * frontend and post arbitrary JSON. These checks therefore belong at the
 * backend trust boundary as well.</p>
 */
public final class VnptSdkPayloadPolicy {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 2_048;
    private static final int MAX_ARRAY_ITEMS = 50;
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_STRING_LENGTH = 4_096;
    private static final int MAX_TOTAL_CHARACTERS = 128 * 1_024;

    private VnptSdkPayloadPolicy() {
    }

    public static void validate(Map<String, Object> sdkResult) {
        if (sdkResult == null || sdkResult.isEmpty()) {
            throw invalidPayload();
        }

        Budget budget = new Budget();
        inspect(sdkResult, 0, budget);
    }

    private static void inspect(Object value, int depth, Budget budget) {
        budget.nodes++;
        if (depth > MAX_DEPTH || budget.nodes > MAX_NODES) {
            throw invalidPayload();
        }

        if (value == null || value instanceof Boolean) {
            return;
        }
        if (value instanceof Number number) {
            if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || number instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw invalidPayload();
            }
            return;
        }
        if (value instanceof String text) {
            budget.characters += text.length();
            if (text.length() > MAX_STRING_LENGTH
                    || budget.characters > MAX_TOTAL_CHARACTERS
                    || looksLikeEmbeddedMedia(text)) {
                throw invalidPayload();
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || key.isBlank()
                        || key.length() > MAX_KEY_LENGTH
                        || isSensitiveOrBinaryKey(key)) {
                    throw invalidPayload();
                }
                budget.characters += key.length();
                if (budget.characters > MAX_TOTAL_CHARACTERS) {
                    throw invalidPayload();
                }
                inspect(entry.getValue(), depth + 1, budget);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object item : iterable) {
                if (++count > MAX_ARRAY_ITEMS) {
                    throw invalidPayload();
                }
                inspect(item, depth + 1, budget);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length > MAX_ARRAY_ITEMS) {
                throw invalidPayload();
            }
            for (int index = 0; index < length; index++) {
                inspect(Array.get(value, index), depth + 1, budget);
            }
            return;
        }

        throw invalidPayload();
    }

    private static boolean isSensitiveOrBinaryKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("accesstoken")
                || normalized.contains("accesskey")
                || normalized.contains("tokenkey")
                || normalized.contains("tokenid")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.equals("header")
                || normalized.equals("headers")
                || normalized.contains("headersrequest")
                || normalized.contains("signature")
                || normalized.contains("base64")
                || normalized.contains("blob")
                || normalized.contains("filecontent")
                || normalized.contains("filedata")
                || normalized.contains("videodata");
    }

    private static boolean looksLikeEmbeddedMedia(String value) {
        String trimmed = value.trim();
        return trimmed.regionMatches(true, 0, "data:image/", 0, "data:image/".length())
                || trimmed.regionMatches(true, 0, "data:video/", 0, "data:video/".length())
                || trimmed.regionMatches(true, 0, "data:application/", 0, "data:application/".length());
    }

    private static BusinessException invalidPayload() {
        return new BusinessException(
                MessageCodes.MSG_KYC_002,
                "VNPT SDK result payload is invalid or exceeds the accepted limits",
                HttpStatus.BAD_REQUEST
        );
    }

    private static final class Budget {
        private int nodes;
        private int characters;
    }
}
