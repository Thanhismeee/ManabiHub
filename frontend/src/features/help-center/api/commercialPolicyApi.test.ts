import { describe, expect, it } from 'vitest';
import {
  parseCommercialPolicy,
  PROVISIONAL_COMMERCIAL_POLICY,
} from './commercialPolicyApi';

describe('provisional commercial policy', () => {
  it('matches the reviewed development contract and uses a stable effective date', () => {
    expect(PROVISIONAL_COMMERCIAL_POLICY).toMatchObject({
      currency: 'VND',
      commissionRate: 0.2,
      refundWindowDays: 14,
      refundProgressLimitPercent: 20,
      escrowHoldingDays: 14,
      payoutThreshold: 100_000,
      withdrawalFee: 0,
      policyVersion: 'br-ref-01-2026-08-03',
      effectiveAt: '2026-08-03T00:00:00Z',
    });
  });

  it('fails closed when the backend omits or corrupts a public policy field', () => {
    expect(() => parseCommercialPolicy({
      ...PROVISIONAL_COMMERCIAL_POLICY,
      commissionRate: 20,
    })).toThrow('Invalid commercial policy response');

    const { kycTargetDaysMax: _omitted, ...incompletePolicy } = PROVISIONAL_COMMERCIAL_POLICY;
    expect(() => parseCommercialPolicy(incompletePolicy))
      .toThrow('Invalid commercial policy response');
  });
});
