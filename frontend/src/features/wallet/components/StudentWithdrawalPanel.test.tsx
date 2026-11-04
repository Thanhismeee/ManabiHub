import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createStudentWithdrawal,
  getStudentBankAccounts,
  getStudentWithdrawals,
  sendStudentWithdrawalOtp,
} from '../services/studentWalletService';
import { StudentWithdrawalPanel } from './StudentWithdrawalPanel';
import { startFirebasePhoneOtp } from '../../../shared/auth/firebasePhoneAuth';

vi.mock('../services/studentWalletService', () => ({
  getStudentWithdrawals: vi.fn(),
  getStudentBankAccounts: vi.fn(),
  sendStudentWithdrawalOtp: vi.fn(),
  createStudentWithdrawal: vi.fn(),
  cancelStudentWithdrawal: vi.fn(),
}));

vi.mock('../../../shared/auth/firebasePhoneAuth', () => ({
  firebasePhoneErrorMessage: () => 'Không thể xác thực SMS qua Firebase.',
  startFirebasePhoneOtp: vi.fn(),
}));

afterEach(() => cleanup());

describe('StudentWithdrawalPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getStudentWithdrawals).mockResolvedValue({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    vi.mocked(getStudentBankAccounts).mockResolvedValue([]);
    vi.mocked(sendStudentWithdrawalOtp).mockResolvedValue({
      verificationMethod: 'EMAIL',
      challengeId: 'email-challenge',
      phoneNumberE164: null,
      maskedDestination: 'te***@example.com',
      expiresAt: '2026-08-03T10:05:00Z',
    });
    vi.mocked(createStudentWithdrawal).mockResolvedValue({
      id: 'withdrawal-1',
      requestedAmount: 200000,
      currency: 'VND',
      status: 'PENDING',
      requestedAt: '2026-08-03T10:00:00Z',
    });
  });

  it('submits a fresh Firebase phone proof instead of a reusable OTP code', async () => {
    const confirm = vi.fn().mockResolvedValue({
      phoneAuthChallengeId: 'firebase-challenge',
      firebaseIdToken: 'firebase-id-token',
    });
    vi.mocked(startFirebasePhoneOtp).mockResolvedValue({
      confirm,
      cancel: vi.fn().mockResolvedValue(undefined),
    });
    vi.mocked(sendStudentWithdrawalOtp).mockResolvedValue({
      verificationMethod: 'FIREBASE',
      challengeId: 'firebase-challenge',
      phoneNumberE164: '+84912345678',
      maskedDestination: '******5678',
      expiresAt: '2026-08-03T10:05:00Z',
    });
    render(
      <StudentWithdrawalPanel
        wallet={{
          balance: 500000,
          frozenBalance: 0,
          availableBalance: 500000,
          withdrawableBalance: 300000,
          availableWithdrawableBalance: 300000,
          currency: 'VND',
        }}
        minimumAmount={100000}
        identityVerified
        onVerifyIdentity={vi.fn()}
        onChanged={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Tạo yêu cầu rút tiền' }));
    fireEvent.change(screen.getByLabelText('Số tiền rút'), { target: { value: '200000' } });
    fireEvent.change(screen.getByLabelText('Số tài khoản'), { target: { value: '0123456789' } });
    fireEvent.change(screen.getByLabelText('Tên chủ tài khoản'), { target: { value: 'Nguyen Van A' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi OTP xác nhận' }));

    expect(await screen.findByText(/Firebase đã gửi SMS thật tới/)).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('Nhập mã OTP gồm 6 số'), {
      target: { value: '654321' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận rút tiền' }));

    await waitFor(() => expect(startFirebasePhoneOtp).toHaveBeenCalledWith({
      challengeId: 'firebase-challenge',
      phoneNumberE164: '+84912345678',
    }));
    expect(confirm).toHaveBeenCalledWith('654321');
    await waitFor(() => expect(createStudentWithdrawal).toHaveBeenCalledWith(
      expect.objectContaining({
        phoneAuthChallengeId: 'firebase-challenge',
        firebaseIdToken: 'firebase-id-token',
      }),
    ));
    expect(vi.mocked(createStudentWithdrawal).mock.calls.at(-1)?.[0]).not.toHaveProperty('otpCode');
  });

  it('creates an OTP-protected request from withdrawable refund balance', async () => {
    const onChanged = vi.fn().mockResolvedValue(undefined);
    render(
      <StudentWithdrawalPanel
        wallet={{
          balance: 500000,
          frozenBalance: 0,
          availableBalance: 500000,
          withdrawableBalance: 300000,
          availableWithdrawableBalance: 300000,
          currency: 'VND',
        }}
        minimumAmount={100000}
        identityVerified
        onVerifyIdentity={vi.fn()}
        onChanged={onChanged}
      />,
    );

    await waitFor(() => expect(getStudentWithdrawals).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu rút tiền' }));
    fireEvent.change(screen.getByLabelText('Số tiền rút'), {
      target: { value: '200000' },
    });
    fireEvent.change(screen.getByLabelText('Số tài khoản'), {
      target: { value: '0123456789' },
    });
    fireEvent.change(screen.getByLabelText('Tên chủ tài khoản'), {
      target: { value: 'Nguyen Van A' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi OTP xác nhận' }));

    await waitFor(() => expect(sendStudentWithdrawalOtp).toHaveBeenCalledTimes(1));
    // The OTP input only mounts once the awaited send call resolves and flips otpSent,
    // so wait for the element itself rather than the mock call — a sync query here is a
    // race that happens to win on fast machines and loses on slower CI runners.
    fireEvent.change(await screen.findByPlaceholderText('Nhập mã OTP gồm 6 số'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận rút tiền' }));

    await waitFor(() => expect(createStudentWithdrawal).toHaveBeenCalledWith({
      amount: 200000,
      bankAccountId: undefined,
      bankAccount: {
        bankCode: 'VCB',
        bankName: 'Vietcombank',
        accountNumber: '0123456789',
        accountHolderName: 'NGUYEN VAN A',
      },
      otpCode: '123456',
      saveAccount: true,
      ownershipConfirmed: true,
    }));
    expect(onChanged).toHaveBeenCalled();
  });

  it('disables withdrawal creation when refund balance is below the minimum', async () => {
    render(
      <StudentWithdrawalPanel
        wallet={{
          balance: 500000,
          frozenBalance: 0,
          availableBalance: 500000,
          withdrawableBalance: 0,
          availableWithdrawableBalance: 0,
          currency: 'VND',
        }}
        minimumAmount={100000}
        identityVerified={false}
        onVerifyIdentity={vi.fn()}
        onChanged={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    expect(screen.getByRole('button', { name: 'Tạo yêu cầu rút tiền' }))
      .toBeDisabled();
  });
});
