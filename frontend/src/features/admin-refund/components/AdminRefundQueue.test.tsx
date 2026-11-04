import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminRefundApi } from '../api/adminRefundApi';
import { AdminRefundQueue } from './AdminRefundQueue';

vi.mock('../api/adminRefundApi', () => ({
  adminRefundApi: {
    getPendingRefunds: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminRefundQueue', () => {
  it('shows the amount, payment provider and reconciliation state needed for triage', async () => {
    vi.mocked(adminRefundApi.getPendingRefunds).mockResolvedValue({
      content: [
        {
          id: 'refund-1',
          orderId: 'order-1',
          orderCode: 'ORD-001',
          studentId: 'student-1',
          studentName: 'Nguyễn An',
          studentEmail: 'an@example.com',
          reason: 'Provider timeout',
          status: 'RECONCILIATION_REQUIRED',
          courseTitle: 'Spring Boot thực chiến',
          currency: 'VND',
          grossAmount: 1_000_000,
          paymentStatus: 'SUCCESS',
          paymentProvider: 'VNPAY',
          providerStatus: 'INVALID_RESULT',
          reconciliationReasonCode: 'PROVIDER_RESULT_UNKNOWN',
          createdAt: '2026-07-29T08:00:00Z',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    render(
      <MemoryRouter>
        <AdminRefundQueue />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Spring Boot thực chiến')).toBeInTheDocument();
    expect(screen.getAllByText(/1\.000\.000/).length).toBeGreaterThan(0);
    expect(screen.getByText('Thanh toán thành công')).toBeInTheDocument();
    expect(screen.getByText('VNPAY')).toBeInTheDocument();
    expect(screen.getAllByText('Cần đối soát').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Phản hồi provider không hợp lệ').length).toBeGreaterThan(0);
    expect(screen.getByText('Có cảnh báo đối soát')).toBeInTheDocument();
    expect(screen.queryByText('PROVIDER_RESULT_UNKNOWN')).not.toBeInTheDocument();
  });

  it('does not present a rejected request amount as approved', async () => {
    vi.mocked(adminRefundApi.getPendingRefunds).mockResolvedValue({
      content: [{
        id: 'refund-rejected', orderId: 'order-2', orderCode: 'ORD-002', studentId: 'student-2',
        studentName: 'Trần Bình', studentEmail: 'binh@example.com', reason: 'Không đủ điều kiện',
        status: 'REJECTED', courseTitle: 'JLPT N3', currency: 'VND', grossAmount: 99_000,
        paymentStatus: 'SUCCESS', providerStatus: 'NOT_REQUESTED',
        createdAt: '2026-08-11T08:00:00Z', decidedAt: '2026-08-12T08:00:00Z', decidedBy: 'admin-1',
      }],
      page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true,
    });

    render(<MemoryRouter><AdminRefundQueue /></MemoryRouter>);

    expect(await screen.findByText('Được duyệt: Không áp dụng')).toBeInTheDocument();
  });

  it('blocks invalid date filters before sending another request', async () => {
    vi.mocked(adminRefundApi.getPendingRefunds).mockResolvedValue({
      content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
    });

    render(<MemoryRouter><AdminRefundQueue /></MemoryRouter>);
    expect(await screen.findByText('Không có yêu cầu hoàn tiền chờ quyết định')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-08-20' } });
    fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-08-10' } });

    expect(screen.getByText(/Khoảng ngày không hợp lệ/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })).toBeDisabled();
    expect(adminRefundApi.getPendingRefunds).toHaveBeenCalledTimes(1);
  });
});
