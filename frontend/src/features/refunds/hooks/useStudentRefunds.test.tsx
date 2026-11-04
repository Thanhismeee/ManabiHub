import { type PropsWithChildren } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor, cleanup } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchStudentRefundDetail } from '../api/studentRefundApi';
import { useStudentRefundDetail } from './useStudentRefunds';
import type { StudentRefundResponse } from '../types';

vi.mock('../api/studentRefundApi', () => ({
  fetchStudentRefundDetail: vi.fn(),
  fetchStudentRefunds: vi.fn(),
  createStudentRefund: vi.fn(),
  cancelStudentRefund: vi.fn(),
}));

afterEach(() => { cleanup(); vi.useRealTimers(); vi.resetAllMocks(); });

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(client, 'invalidateQueries');
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { ...renderHook(() => useStudentRefundDetail('refund-1'), { wrapper }), invalidate };
}

describe('automatic wallet refund polling', () => {
  it('polls an automatic refund through approval and refreshes wallet transactions', async () => {
    vi.mocked(fetchStudentRefundDetail)
      .mockResolvedValueOnce({ id: 'refund-1', status: 'PENDING', automaticRefundPending: true } as StudentRefundResponse)
      .mockResolvedValue({ id: 'refund-1', status: 'APPROVED', automaticRefundPending: false } as StudentRefundResponse);
    const { result, invalidate } = setup();
    await waitFor(() => expect(result.current.data?.status).toBe('PENDING'));
    await waitFor(() => expect(result.current.data?.status).toBe('APPROVED'), { timeout: 4000 });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['student-wallet-transactions'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['student-order-history'] });
    vi.useFakeTimers();
    const calls = vi.mocked(fetchStudentRefundDetail).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(fetchStudentRefundDetail).toHaveBeenCalledTimes(calls);
  });

  it('does not poll a manual-review request as if money were being credited', async () => {
    vi.mocked(fetchStudentRefundDetail).mockResolvedValue({
      id: 'refund-1', status: 'PENDING', automaticRefundPending: false,
    } as StudentRefundResponse);
    const { result } = setup();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    vi.useFakeTimers();
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(fetchStudentRefundDetail).toHaveBeenCalledTimes(1);
  });
});
