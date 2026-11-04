import type { CommercialPolicy } from '../types';
import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';

export const PROVISIONAL_COMMERCIAL_POLICY: Readonly<CommercialPolicy> = {
  currency: 'VND',
  commissionRate: 0.2,
  refundWindowDays: 14,
  refundProgressLimitPercent: 20,
  escrowHoldingDays: 14,
  payoutThreshold: 100_000,
  withdrawalFee: 0,
  kycTargetDaysMin: 1,
  kycTargetDaysMax: 2,
  policyVersion: 'br-ref-01-2026-08-03',
  effectiveAt: '2026-08-03T00:00:00Z',
};

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

export const parseCommercialPolicy = (value: unknown): CommercialPolicy => {
  if (!value || typeof value !== 'object') {
    throw new Error('Invalid commercial policy response');
  }

  const policy = value as Record<string, unknown>;
  const isValid = (
    typeof policy.currency === 'string'
    && policy.currency.length > 0
    && isFiniteNumber(policy.commissionRate)
    && policy.commissionRate >= 0
    && policy.commissionRate <= 1
    && isFiniteNumber(policy.refundWindowDays)
    && Number.isInteger(policy.refundWindowDays)
    && policy.refundWindowDays >= 0
    && isFiniteNumber(policy.refundProgressLimitPercent)
    && policy.refundProgressLimitPercent >= 0
    && policy.refundProgressLimitPercent <= 100
    && isFiniteNumber(policy.escrowHoldingDays)
    && Number.isInteger(policy.escrowHoldingDays)
    && policy.escrowHoldingDays >= 0
    && isFiniteNumber(policy.payoutThreshold)
    && policy.payoutThreshold >= 0
    && isFiniteNumber(policy.withdrawalFee)
    && policy.withdrawalFee >= 0
    && isFiniteNumber(policy.kycTargetDaysMin)
    && Number.isInteger(policy.kycTargetDaysMin)
    && policy.kycTargetDaysMin > 0
    && isFiniteNumber(policy.kycTargetDaysMax)
    && Number.isInteger(policy.kycTargetDaysMax)
    && policy.kycTargetDaysMax >= policy.kycTargetDaysMin
    && typeof policy.policyVersion === 'string'
    && policy.policyVersion.length > 0
    && typeof policy.effectiveAt === 'string'
    && !Number.isNaN(Date.parse(policy.effectiveAt))
  );

  if (!isValid) {
    throw new Error('Invalid commercial policy response');
  }

  return policy as unknown as CommercialPolicy;
};

export const getCommercialPolicy = async (): Promise<CommercialPolicy> => {
  const response = await axiosClient.get<ApiResponse<unknown>>(
    ENDPOINTS.publicCommercialPolicy.current,
  );

  return parseCommercialPolicy(response.data.data);
};
