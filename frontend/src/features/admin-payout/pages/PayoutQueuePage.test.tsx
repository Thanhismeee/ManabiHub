import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePayoutQueue } from '../hooks/usePayoutQueue';
import type { PayoutQueueItem } from '../types/payout.types';
import { PayoutQueuePage } from './PayoutQueuePage';

vi.mock('../hooks/usePayoutQueue', () => ({ usePayoutQueue: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('PayoutQueuePage', () => {
  it('separates internal, provider and reconciliation status while preserving stale data', () => {
    vi.mocked(usePayoutQueue).mockReturnValue({
      data: {
        content: [payout()],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      },
      isLoading: false,
      isFetching: false,
      isError: true,
      error: new Error('refresh failed'),
      refetch: vi.fn(),
    } as never);

    render(
      <MemoryRouter>
        <PayoutQueuePage />
      </MemoryRouter>,
    );

    expect(screen.getByText('Không thể cập nhật dữ liệu mới. Bảng bên dưới là dữ liệu gần nhất đã tải được.')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Minh')).toBeInTheDocument();
    expect(screen.getAllByText('Đã duyệt').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Đang xử lý').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Sai lệch nghiêm trọng').length).toBeGreaterThan(0);
    expect(screen.getByText('VNPAY')).toBeInTheDocument();
    expect(screen.getByText('VNPAY-TXN-001')).toBeInTheDocument();
    expect(screen.getByText('15/08/2026 14:00')).toBeInTheDocument();
    expect(screen.queryByText('CRITICAL_MISMATCH')).not.toBeInTheDocument();
    expect(screen.queryByText('PROCESSING')).not.toBeInTheDocument();
  });

  it('blocks an invalid date range before it reaches the API hook', () => {
    vi.mocked(usePayoutQueue).mockReturnValue({
      data: {
        content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
      },
      isLoading: false,
      isFetching: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);

    render(
      <MemoryRouter>
        <PayoutQueuePage />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-08-20' } });
    fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-08-10' } });

    expect(screen.getByText('Ngày kết thúc phải từ ngày bắt đầu trở đi')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Áp dụng' })).toBeDisabled();
  });
});

function payout(): PayoutQueueItem {
  return {
    withdrawalRequestId: 'payout-1',
    walletId: 'wallet-1',
    ownerType: 'TEACHER',
    ownerId: 'teacher-1',
    ownerName: 'Nguyễn Minh',
    teacherId: 'teacher-1',
    teacherName: 'Nguyễn Minh',
    requestedAmount: 1_500_000,
    bankName: 'Vietcombank',
    accountNumberMasked: '****1234',
    status: 'APPROVED',
    settlementStatus: 'PROCESSING',
    reconciliationStatus: 'CRITICAL_MISMATCH',
    requestedAt: '2026-08-15T07:00:00Z',
    processingStartedAt: '2026-08-15T07:05:00Z',
    provider: 'VNPAY',
    providerReference: 'VNPAY-TXN-001',
    decidedBy: 'finance-manager-1',
    decidedAt: '2026-08-15T07:03:00Z',
    updatedAt: '2026-08-15T07:06:00Z',
    settlementUpdatedAt: '2026-08-15T07:06:00Z',
    retryCount: 1,
  };
}
