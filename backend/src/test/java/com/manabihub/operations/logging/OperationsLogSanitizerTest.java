package com.manabihub.operations.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsLogSanitizerTest {

    private final OperationsLogSanitizer sanitizer = new OperationsLogSanitizer();

    @Test
    void removesCredentialsHeadersCookiesAndJsonSecrets() {
        String input = "Authorization: Bearer eyJheader.payload.signature "
                + "Cookie: session=abc; refresh_token=def\n"
                + "{\"password\":\"P@ss word\",\"api_key\":\"secret-key\"}";

        String result = sanitizer.sanitize(input);

        assertThat(result)
                .doesNotContain("eyJheader", "session=abc", "refresh_token=def", "P@ss word", "secret-key")
                .contains("[REDACTED]");
    }

    @Test
    void removesSensitiveQueryParametersAndUrlCredentials() {
        String input = "https://db-user:db-pass@example.test/path?token=abc123&api_key=xyz789&safe=yes";

        String result = sanitizer.sanitize(input);

        assertThat(result)
                .doesNotContain("db-user", "db-pass", "abc123", "xyz789")
                .contains("[REDACTED]");
    }

    @Test
    void removesEmailPhoneOtpCccdAndBankLikeNumbers() {
        String input = "user@example.com +84912345678 OTP: 123456 CCCD 012345678901 account 1234567890123";

        String result = sanitizer.sanitize(input);

        assertThat(result)
                .doesNotContain("user@example.com", "+84912345678", "123456", "012345678901", "1234567890123")
                .contains("[REDACTED_EMAIL]", "[REDACTED_PHONE]", "[REDACTED_OTP]", "[REDACTED_NUMBER]");
    }

    @Test
    void removesOtpFromActualConsoleSmsLogShape() {
        String input = "PHONE VERIFICATION DEMO SMS to ******5678: "
                + "Ma xac thuc ManabiHub cua ban la 654321. Ma co hieu luc trong 5 phut.";

        String result = sanitizer.sanitize(input);

        assertThat(result)
                .doesNotContain("654321")
                .contains("[REDACTED_OTP]");
    }

    @Test
    void flattensLineBreaksAndBoundsOutput() {
        String result = sanitizer.sanitize("first\r\n" + "x".repeat(1_000_000));

        assertThat(result)
                .doesNotContain("\r", "\n")
                .endsWith("…[TRUNCATED]")
                .hasSizeLessThan(8_020);
    }
}
