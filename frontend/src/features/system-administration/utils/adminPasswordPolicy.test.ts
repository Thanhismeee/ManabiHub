import { describe, expect, it } from 'vitest';
import { evaluateAdminPassword } from './adminPasswordPolicy';

describe('evaluateAdminPassword', () => {
  it('accepts a strong password that matches the backend policy', () => {
    expect(evaluateAdminPassword('StrongPassword!42').valid).toBe(true);
  });

  it('rejects weak, whitespace, and overlong passwords', () => {
    expect(evaluateAdminPassword('weak').valid).toBe(false);
    expect(evaluateAdminPassword('Strong Password!42').valid).toBe(false);
    expect(evaluateAdminPassword(`Aa1!${'x'.repeat(69)}`).valid).toBe(false);
  });
});
