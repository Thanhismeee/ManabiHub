import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from './adminFinanceApi';
import { FinanceRevenueDashboardPage } from './FinanceRevenueDashboardPage';
import type { RevenueDashboard } from './types';

vi.mock('./adminFinanceApi', () => ({
  adminFinanceApi: { getRevenueDashboard: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('FinanceRevenueDashboardPage', () => {
  it('renders the audited formulas, server period and real zero values', async () => {
    vi.mocked(adminFinanceApi.getRevenueDashboard).mockResolvedValue(dashboard());

    render(
      <MemoryRouter>
        <FinanceRevenueDashboardPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Báo cáo doanh thu và vận hành')).toBeInTheDocument();
    expect(screen.getByText('Kỳ dữ liệu: 01/08/2026 – 03/08/2026')).toBeInTheDocument();
    expect(screen.getByText('Múi giờ: Asia/Ho_Chi_Minh')).toBeInTheDocument();
    expect(screen.getAllByText(/3\.205\.000/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/19\.800/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/4\.800/).length).toBeGreaterThan(0);
    expect(screen.getByText(/Ghi nhận 19\.800/)).toHaveTextContent(/đảo 0/);

    await waitFor(() => {
      const request = vi.mocked(adminFinanceApi.getRevenueDashboard).mock.calls[0]?.[0];
      expect(request).toEqual(expect.objectContaining({ granularity: 'DAY' }));
      expect(request?.from).toMatch(/T17:00:00\.000Z$/);
      expect(request?.to).toMatch(/T17:00:00\.000Z$/);
    });
  });

  it('keeps the last successful report visible when refresh fails', async () => {
    vi.mocked(adminFinanceApi.getRevenueDashboard)
      .mockResolvedValueOnce(dashboard())
      .mockRejectedValueOnce(new Error('network down'));

    render(
      <MemoryRouter>
        <FinanceRevenueDashboardPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Kỳ dữ liệu: 01/08/2026 – 03/08/2026')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));

    expect(await screen.findByText(/Dữ liệu bên dưới là lần tải thành công gần nhất/)).toBeInTheDocument();
    expect(screen.getAllByText(/3\.205\.000/).length).toBeGreaterThan(0);
  });
});

function dashboard(): RevenueDashboard {
  return {
    from: '2026-07-31T17:00:00Z',
    to: '2026-08-03T17:00:00Z',
    timezone: 'Asia/Ho_Chi_Minh',
    granularity: 'DAY',
    generatedAt: '2026-08-03T17:15:00Z',
    summary: {
      grossSales: 3_205_000,
      successfulOrders: 17,
      refundAmount: 99_000,
      refundCount: 1,
      refundRate: 3.09,
      netCollected: 3_106_000,
      commissionRecognized: 19_800,
      commissionReversed: 0,
      platformRevenue: 19_800,
      paymentFees: 5_000,
      operatingExpenses: 10_000,
      totalActualExpenses: 15_000,
      netOperatingResult: 4_800,
    },
    points: [
      {
        bucket: '2026-08-01',
        grossSales: 3_205_000,
        successfulOrders: 17,
        refundAmount: 99_000,
        refundCount: 1,
        commissionRecognized: 19_800,
        commissionReversed: 0,
        platformRevenue: 19_800,
        paymentFees: 5_000,
        operatingExpenses: 10_000,
      },
    ],
  };
}
