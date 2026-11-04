import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminKycService } from '../../admin-kyc/services/adminKycService';
import { courseApprovalService } from '../../admin-course-approval/services/courseApprovalService';
import { adminViolationService } from '../../admin-violation/services/adminViolationService';
import { adminPayoutService } from '../../admin-payout/services/adminPayoutService';
import { adminRefundApi } from '../../admin-refund/api/adminRefundApi';
import { adminFinanceApi } from '../../admin-finance/adminFinanceApi';
import { AdminDashboardPage } from './AdminDashboardPage';

const authState = vi.hoisted(() => ({ roles: ['COURSE_MANAGER'] as string[] }));

vi.mock('../../../shared/auth/authSession', () => ({
  getAuthSession: vi.fn(() => ({ roles: authState.roles })),
  hasAnyRole: vi.fn((_: unknown, roles: string[]) => roles.some((role) => authState.roles.includes(role))),
}));

vi.mock('../../admin-kyc/services/adminKycService', () => ({
  adminKycService: { getPendingKycQueue: vi.fn() },
}));

vi.mock('../../admin-course-approval/services/courseApprovalService', () => ({
  courseApprovalService: { getQueue: vi.fn() },
}));

vi.mock('../../admin-violation/services/adminViolationService', () => ({
  adminViolationService: { getViolationQueue: vi.fn() },
}));

vi.mock('../../admin-payout/services/adminPayoutService', () => ({
  adminPayoutService: { getPayoutQueue: vi.fn() },
}));

vi.mock('../../admin-refund/api/adminRefundApi', () => ({
  adminRefundApi: { getPendingRefunds: vi.fn() },
}));

vi.mock('../../admin-finance/adminFinanceApi', () => ({
  adminFinanceApi: {
    getRevenueDashboard: vi.fn(),
    searchExpenses: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  authState.roles = ['COURSE_MANAGER'];
  vi.mocked(adminKycService.getPendingKycQueue).mockResolvedValue([]);
  vi.mocked(courseApprovalService.getQueue).mockResolvedValue([]);
  vi.mocked(adminViolationService.getViolationQueue).mockResolvedValue({
    content: [],
    page: 0,
    size: 1,
    totalElements: 3,
    totalPages: 3,
    first: true,
    last: false,
  });
});

describe('AdminDashboardPage for Course Manager', () => {
  it('shows the operational queues and a discoverable weekly game entry', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Báo cáo vi phạm')).toBeTruthy();
    expect(screen.getByText('Báo cáo chờ xem xét')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Đến hàng đợi Vi phạm/i })).toBeTruthy();
    expect(screen.getByText('Trò chơi & thưởng tuần')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Quản lý trò chơi tuần/i })).toBeTruthy();

    await waitFor(() => {
      expect(adminViolationService.getViolationQueue).toHaveBeenCalledWith({
        page: 0,
        size: 1,
        status: 'PENDING_REVIEW',
      });
    });
  });
});

describe('AdminDashboardPage for Finance Manager', () => {
  it('keeps partial failures explicit and never invents a missing queue total', async () => {
    authState.roles = ['FINANCE_MANAGER'];
    vi.mocked(adminPayoutService.getPayoutQueue)
      .mockRejectedValueOnce(new Error('payout queue unavailable'))
      .mockResolvedValueOnce(emptyPage(1));
    vi.mocked(adminRefundApi.getPendingRefunds).mockResolvedValue(emptyPage(4));
    vi.mocked(adminFinanceApi.searchExpenses).mockResolvedValue(emptyPage(3));
    vi.mocked(adminFinanceApi.getRevenueDashboard).mockResolvedValue({
      from: '2026-07-31T17:00:00Z',
      to: '2026-08-26T17:00:00Z',
      timezone: 'Asia/Ho_Chi_Minh',
      granularity: 'DAY',
      generatedAt: '2026-08-26T02:30:00Z',
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
      points: [],
    });

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Bảng điều hành tài chính')).toBeInTheDocument();
    expect(await screen.findByText(/Một phần dữ liệu tài chính chưa đồng bộ/)).toBeInTheDocument();
    expect(screen.getAllByText(/3\.205\.000/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/19\.800/).length).toBeGreaterThan(0);
    expect(screen.getByText('Tiền đang chờ chi trả')).toBeInTheDocument();
    expect(screen.getAllByText('Chưa đồng bộ').length).toBeGreaterThan(0);
    expect(screen.queryByText('0 ₫')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(adminFinanceApi.getRevenueDashboard).toHaveBeenCalledWith(expect.objectContaining({
        granularity: 'DAY',
      }));
      expect(adminRefundApi.getPendingRefunds).toHaveBeenCalledWith(0, 1, { status: 'PENDING' });
    });
  });
});

function emptyPage(totalElements: number) {
  return {
    content: [],
    page: 0,
    size: 1,
    totalElements,
    totalPages: totalElements > 0 ? 1 : 0,
    first: true,
    last: true,
  };
}
