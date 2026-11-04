export interface PasswordPolicyResult {
  valid: boolean;
  checks: {
    length: boolean;
    uppercase: boolean;
    lowercase: boolean;
    digit: boolean;
    special: boolean;
    noWhitespace: boolean;
  };
}

export function evaluateAdminPassword(password: string): PasswordPolicyResult {
  const byteLength = new TextEncoder().encode(password).length;
  const checks = {
    length: byteLength >= 12 && byteLength <= 72,
    uppercase: /[A-Z]/.test(password),
    lowercase: /[a-z]/.test(password),
    digit: /\d/.test(password),
    special: /[^A-Za-z0-9]/.test(password),
    noWhitespace: !/\s/.test(password),
  };

  return {
    valid: Object.values(checks).every(Boolean),
    checks,
  };
}
