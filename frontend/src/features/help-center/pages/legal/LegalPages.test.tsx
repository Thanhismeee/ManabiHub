import type { ReactNode } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CommercialPolicy } from '../../types';
import { InstructorTermsPage, RefundPolicyPage } from './LegalPages';

vi.mock('../../components/ArticleLayout', () => ({
  ArticleLayout: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('../../components/PolicyBoundary', () => {
  const policy: CommercialPolicy = {
    currency: 'VND',
    commissionRate: 0.2,
    refundWindowDays: 14,
    refundProgressLimitPercent: 20,
    escrowHoldingDays: 14,
    payoutThreshold: 100_000,
    withdrawalFee: 0,
    kycTargetDaysMin: 1,
    kycTargetDaysMax: 2,
    policyVersion: 'test-policy',
    effectiveAt: '2026-08-01T00:00:00Z',
  };

  return {
    PolicyBoundary: ({ children }: { children: (value: CommercialPolicy) => ReactNode }) => (
      <>{children(policy)}</>
    ),
  };
});

const renderPage = (page: ReactNode) => render(<MemoryRouter>{page}</MemoryRouter>);

afterEach(cleanup);

describe('legal policy pages', () => {
  it('states the inclusive BR-REF-01 boundary and self-service entry point', () => {
    renderPage(<RefundPolicyPage />);

    expect(screen.getByRole('link', { name: /Lịch sử thanh toán/i })).toHaveAttribute('href', '/student/payments');
    expect(screen.getByText(/14 ngày theo lịch/i)).toBeInTheDocument();
    expect(screen.getByText(/không vượt quá 20%/i)).toBeInTheDocument();
    expect(screen.getByText(/chưa tải toàn bộ tài liệu/i)).toBeInTheDocument();
    expect(screen.getAllByText(/tranh chấp.*xét duyệt thủ công/i)).not.toHaveLength(0);
  });

  it('renders the configured 20/80 split and payout boundaries', () => {
    renderPage(<InstructorTermsPage />);

    expect(screen.getByText('20%')).toBeInTheDocument();
    expect(screen.getByText('80%')).toBeInTheDocument();
    expect(screen.getByText(/14 ngày theo lịch/i)).toBeInTheDocument();
    expect(screen.getByText(/100\.000/)).toBeInTheDocument();
    expect(screen.getByText(/test-policy/)).toBeInTheDocument();
  });
});
