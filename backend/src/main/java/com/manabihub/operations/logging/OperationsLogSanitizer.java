package com.manabihub.operations.logging;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class OperationsLogSanitizer {

    static final String REDACTED = "[REDACTED]";
    private static final int MAX_TEXT_LENGTH = 8_000;
    private static final int MAX_SCAN_LENGTH = 16_000;

    private static final List<Replacement> REPLACEMENTS = List.of(
            // A cookie header can contain many semicolon-separated values. Once
            // the header marker is seen, dropping its remainder is safer than
            // attempting to preserve individual cookie attributes.
            new Replacement(
                    Pattern.compile("(?i)(cookie|set-cookie)\\s*[:=].*$"),
                    "$1=" + REDACTED
            ),
            new Replacement(
                    Pattern.compile("(?i)(authorization|proxy-authorization)\\s*[:=]\\s*(?:bearer\\s+)?[^,;\\s]+"),
                    "$1=" + REDACTED
            ),
            new Replacement(
                    Pattern.compile("(?i)(\"(?:authorization|proxy-authorization|cookie|set-cookie|password|passwd|pwd|secret|api[-_]?key|jwt|access[-_]?token|refresh[-_]?token|hash[-_]?secret)\"\\s*:\\s*\")[^\"]*(\")"),
                    "$1" + REDACTED + "$2"
            ),
            new Replacement(
                    Pattern.compile("(?i)('(?:authorization|proxy-authorization|cookie|set-cookie|password|passwd|pwd|secret|api[-_]?key|jwt|access[-_]?token|refresh[-_]?token|hash[-_]?secret)'\\s*:\\s*')[^']*(')"),
                    "$1" + REDACTED + "$2"
            ),
            new Replacement(
                    Pattern.compile("(?i)(password|passwd|pwd|secret|api[-_ ]?key|jwt|access[-_]?token|refresh[-_]?token|hash[-_]?secret)\\s*[:=]\\s*([^,;\\s}&]+)"),
                    "$1=" + REDACTED
            ),
            new Replacement(
                    Pattern.compile("(?i)([?&](?:password|secret|token|api_key|apikey|access_token|refresh_token)=)[^&#\\s]+"),
                    "$1" + REDACTED
            ),
            new Replacement(
                    Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+"),
                    "Bearer " + REDACTED
            ),
            new Replacement(
                    Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
                    REDACTED
            ),
            // Redact URL user-info before the email matcher can interpret
            // "password@host" as an email address and leave the username behind.
            new Replacement(
                    Pattern.compile("(?i)(https?://)[^/@\\s]+@"),
                    "$1" + REDACTED + "@"
            ),
            new Replacement(
                    Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
                    "[REDACTED_EMAIL]"
            ),
            new Replacement(
                    Pattern.compile("(?i)(otp|verification[-_ ]?code|m[aã]\\s+x[aá]c\\s+th[uự]c)([^\\d\\r\\n]{0,100})\\d{4,8}"),
                    "$1$2[REDACTED_OTP]"
            ),
            new Replacement(
                    Pattern.compile("(?<![\\p{L}\\d])(?:\\+?84|0)(?:[ .-]?\\d){9,10}(?!\\d)"),
                    "[REDACTED_PHONE]"
            ),
            new Replacement(
                    Pattern.compile("(?<![\\p{L}\\d])(?:\\d[ -]?){8,19}(?![\\p{L}\\d])"),
                    "[REDACTED_NUMBER]"
            )
    );

    public String sanitize(String value) {
        if (value == null) {
            return null;
        }

        // Bound regex work as well as the final response. A malformed dependency
        // must not be able to make the diagnostic appender scan an unbounded line.
        String bounded = value.length() > MAX_SCAN_LENGTH
                ? value.substring(0, MAX_SCAN_LENGTH) + "…[TRUNCATED]"
                : value;
        String sanitized = bounded.replaceAll("[\\r\\n]+", " ");
        for (Replacement replacement : REPLACEMENTS) {
            sanitized = replacement.pattern().matcher(sanitized)
                    .replaceAll(replacement.replacement());
        }
        if (sanitized.length() > MAX_TEXT_LENGTH) {
            return sanitized.substring(0, MAX_TEXT_LENGTH) + "…[TRUNCATED]";
        }
        return sanitized;
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
