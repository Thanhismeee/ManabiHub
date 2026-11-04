import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PROVISIONAL_COMMERCIAL_POLICY } from '../api/commercialPolicyApi';
import { useCommercialPolicy } from '../hooks/useCommercialPolicy';
import { PolicyBoundary } from './PolicyBoundary';

vi.mock('../hooks/useCommercialPolicy', () => ({
  useCommercialPolicy: vi.fn(),
}));

const useCommercialPolicyMock = vi.mocked(useCommercialPolicy);

const queryResult = (
  overrides: Partial<ReturnType<typeof useCommercialPolicy>>,
) => ({
  data: undefined,
  isError: false,
  isLoading: false,
  refetch: vi.fn(),
  ...overrides,
}) as unknown as ReturnType<typeof useCommercialPolicy>;

describe('PolicyBoundary', () => {
  beforeEach(() => {
    useCommercialPolicyMock.mockReturnValue(queryResult({ isLoading: true }));
  });

  it('announces loading without rendering policy values', () => {
    render(
      <PolicyBoundary>
        {(policy) => <span>{policy.commissionRate}</span>}
      </PolicyBoundary>,
    );

    expect(screen.getByRole('status')).toHaveAccessibleName(
      'Đang tải điều khoản hiện hành...',
    );
    expect(screen.queryByText('0.2')).not.toBeInTheDocument();
  });

  it('fails closed and lets the user retry', () => {
    const refetch = vi.fn();
    useCommercialPolicyMock.mockReturnValue(queryResult({ isError: true, refetch }));

    render(
      <PolicyBoundary>
        {(policy) => <span>{policy.commissionRate}</span>}
      </PolicyBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Không thể tải điều khoản hiện hành',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(refetch).toHaveBeenCalledOnce();
  });

  it('renders children only after a valid policy is available', () => {
    useCommercialPolicyMock.mockReturnValue(queryResult({
      data: { ...PROVISIONAL_COMMERCIAL_POLICY },
    }));

    render(
      <PolicyBoundary>
        {(policy) => <span>{policy.policyVersion}</span>}
      </PolicyBoundary>,
    );

    expect(screen.getByText('br-ref-01-2026-08-03')).toBeInTheDocument();
  });
});
