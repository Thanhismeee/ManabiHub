import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useApprovePayout } from '../hooks/useApprovePayout';
import { usePayoutDetail } from '../hooks/usePayoutDetail';
import { useRejectPayout } from '../hooks/useRejectPayout';
import { useRetryPayout } from '../hooks/useRetryPayout';
import { useReviewReconciliation } from '../hooks/useReviewReconciliation';
import type { PayoutDetail } from '../types/payout.types';
import { PayoutSettlementPage } from './PayoutSettlementPage';

vi.mock('../hooks/usePayoutDetail', () => ({ usePayoutDetail: vi.fn() }));
vi.mock('../hooks/useApprovePayout', () => ({ useApprovePayout: vi.fn() }));
vi.mock('../hooks/useRejectPayout', () => ({ useRejectPayout: vi.fn() }));
vi.mock('../hooks/useRetryPayout', () => ({ useRetryPayout: vi.fn() }));
vi.mock('../hooks/useReviewReconciliation', () => ({ useReviewReconciliation: vi.fn() }));

const approveMutate = vi.fn();

beforeEach(() => {
  vi.mocked(usePayoutDetail).mockReturnValue({
    data: detail(), isLoading: false, isError: false, error: null, refetch: vi.fn(),
  } as never);
  vi.mocked(useApprovePayout).mockReturnValue({ isPending: false, mutate: approveMutate } as never);
  vi.mocked(useRejectPayout).mockReturnValue({ isPending: false, mutate: vi.fn() } as never);
  vi.mocked(useRetryPayout).mockReturnValue({ isPending: false, mutate: vi.fn() } as never);
  vi.mocked(useReviewReconciliation).mockReturnValue({ isPending: false, mutate: vi.fn() } as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('PayoutSettlementPage', () => {
  it('requires an explicit review of amount, destination and idempotency before approval', () => {
    renderPage();

    const openConfirmation = screen.queryByRole('button', { name: 'Chạy payout giả lập' })
      ?? screen.getByRole('button', { name: 'Duyệt qua Gateway' });
    fireEvent.click(openConfirmation);

    expect(screen.getByText('Xác nhận thực hiện chi trả?')).toBeInTheDocument();
    expect(screen.getByText(/khóa idempotency nhằm ngăn một yêu cầu bị chuyển trùng/)).toBeInTheDocument();
    expect(screen.getAllByText(/1\.500\.000/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Vietcombank').length).toBeGreaterThan(0);
    expect(screen.getAllByText('****1234').length).toBeGreaterThan(0);
    expect(approveMutate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận chi trả' }));

    expect(approveMutate).toHaveBeenCalledWith(
      expect.objectContaining({ withdrawalRequestId: 'payout-1' }),
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/payouts/payout-1']}>
      <Routes>
        <Route path="/admin/payouts/:id" element={<PayoutSettlementPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function detail(): PayoutDetail {
  return {
    withdrawalRequestId: 'payout-1',
    settlementId: null,
    ownerType: 'TEACHER',
    ownerId: 'teacher-1',
    ownerName: 'Nguyễn Minh',
    ownerAccountStatus: 'ACTIVE',
    teacherId: 'teacher-1',
    teacherName: 'Nguyễn Minh',
    teacherAccountStatus: 'ACTIVE',
    requestedAmount: 1_500_000,
    availableBalance: 2_000_000,
    reservedBalance: 1_500_000,
    pendingClearing: 0,
    walletFrozen: false,
    escrowStatus: 'CLEARED',
    status: 'PENDING',
    settlementStatus: null,
    reconciliationStatus: 'MATCHED',
    reconciliationAlerts: [],
    bankName: 'Vietcombank',
    bankBranch: 'Hà Nội',
    accountHolderName: 'NGUYEN MINH',
    accountNumberMasked: '****1234',
    requestedAt: '2026-08-25T03:00:00Z',
    processingStartedAt: null,
    settledAt: null,
    decision: null,
    decisionReason: null,
    gatewayProvider: null,
    gatewayReference: null,
    transferMethod: null,
    manualProofAvailable: false,
    bankQrAvailable: false,
    manualProofOriginalName: null,
    manualProofSize: null,
    manualTransferredAt: null,
    failureCode: null,
    failureMessage: null,
    retryCount: 0,
    notificationStatus: 'NOT_REQUIRED',
    notificationAttempts: 0,
    reconciliationHistory: [],
  };
}
