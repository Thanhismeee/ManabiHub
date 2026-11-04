/** The only phone formats accepted by the profile and onboarding flows. */
export const PHONE_PATTERN = /^(0\d{9}|\+84\d{9})$/;

/**
 * Keep the field usable while the user is typing, without allowing letters,
 * whitespace, punctuation, or a second `+` to reach the form state.
 */
export function sanitizePhoneInput(value: string): string {
    const compact = value.replace(/[^\d+]/g, "");
    if (compact.startsWith("+")) {
        return `+${compact.slice(1).replace(/\+/g, "")}`.slice(0, 12);
    }
    return compact.replace(/\+/g, "").slice(0, 10);
}

export function sanitizeOtpInput(value: string): string {
    return value.replace(/\D/g, "").slice(0, 6);
}
