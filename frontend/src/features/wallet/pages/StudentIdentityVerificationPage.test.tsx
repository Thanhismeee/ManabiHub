import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StudentIdentityVerificationPage } from './StudentIdentityVerificationPage';

const sdkMocks = vi.hoisted(() => ({
  launch: vi.fn(),
  reset: vi.fn(),
}));

const serviceMocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  verify: vi.fn(),
}));

vi.mock('../../kyc/vnptIdentitySdk', () => ({
  launchVnptIdentitySdk: sdkMocks.launch,
  resetVnptIdentitySdkRuntime: sdkMocks.reset,
}));

vi.mock('../services/studentIdentityVerificationService', () => ({
  getStudentIdentityVerificationStatus: serviceMocks.getStatus,
  verifyStudentIdentity: serviceMocks.verify,
}));

describe('StudentIdentityVerificationPage', () => {
  beforeEach(() => {
    sdkMocks.launch.mockReset();
    sdkMocks.reset.mockReset();
    serviceMocks.getStatus.mockReset();
    serviceMocks.verify.mockReset();
    serviceMocks.getStatus.mockResolvedValue({ verified: false, status: 'NOT_VERIFIED' });
  });

  afterEach(() => cleanup());

  it('keeps a verified identity read-only and returns to the requested student page', async () => {
    serviceMocks.getStatus.mockResolvedValue({
      verified: true,
      status: 'VERIFIED',
      fullName: 'NGUYỄN VĂN A',
    });

    renderPage('/student/identity-verification?returnTo=%2Fstudent%2Fpayments');

    expect(await screen.findByText(/Đã xác minh: NGUYỄN VĂN A/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đã xác thực CCCD thành công' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Quay lại Ví & Thanh toán' }));
    expect(screen.getByTestId('current-location')).toHaveTextContent('/student/payments');
    expect(sdkMocks.launch).not.toHaveBeenCalled();
  });

  it('rejects an external return URL instead of creating an open redirect', async () => {
    renderPage('/student/identity-verification?returnTo=https%3A%2F%2Fevil.example%2Fsteal');

    await screen.findByText(/Chưa xác minh/i);
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại Ví & Thanh toán' }));
    expect(screen.getByTestId('current-location')).toHaveTextContent('/student/payments');
  });

  it('surfaces terminal SDK callback errors and lets the student retry', async () => {
    sdkMocks.launch.mockImplementation(async (_onResult, options) => {
      options.onError(new Error('VNPT chưa trả về đủ mã phiên và mã giao dịch.'));
    });

    renderPage('/student/identity-verification');

    fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu xác minh' }));

    expect(await screen.findByText(/chưa trả về đủ mã phiên và mã giao dịch/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bắt đầu xác minh' })).toBeEnabled();
    expect(serviceMocks.verify).not.toHaveBeenCalled();
  });

  it('does not present cancellation as effective after the verification POST starts', async () => {
    let terminalCallback: ((result: Record<string, unknown>) => Promise<void>) | undefined;
    sdkMocks.launch.mockImplementation(async (onResult) => {
      terminalCallback = onResult;
    });
    serviceMocks.verify.mockReturnValue(new Promise(() => undefined));

    renderPage('/student/identity-verification');
    fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu xác minh' }));
    await waitFor(() => expect(terminalCallback).toBeDefined());

    await act(async () => {
      void terminalCallback?.({ sdkResult: { status: 'SUCCESS' } });
    });

    expect(await screen.findByRole('button', { name: 'Không thể hủy khi đang ghi nhận' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Quay lại Ví & Thanh toán' })).toBeDisabled();
    expect(serviceMocks.verify).toHaveBeenCalledTimes(1);
  });
});

function renderPage(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <StudentIdentityVerificationPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="current-location">{location.pathname}{location.search}</span>;
}
