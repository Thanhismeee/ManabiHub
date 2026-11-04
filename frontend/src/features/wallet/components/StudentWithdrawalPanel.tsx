import { useCallback, useEffect, useState } from 'react';
import {
  cancelStudentWithdrawal,
  createStudentWithdrawal,
  getStudentBankAccounts,
  getStudentWithdrawals,
  sendStudentWithdrawalOtp,
} from '../services/studentWalletService';
import type {
  StudentBankAccount,
  StudentWalletResponse,
  StudentWithdrawal,
  StudentWithdrawalStatus,
} from '../types';
import {
  firebasePhoneErrorMessage,
  startFirebasePhoneOtp,
  type FirebasePhoneOtpSession,
  type PhoneOtpChallengeResponse,
} from '../../../shared/auth/firebasePhoneAuth';

const BANKS = [
  { code: 'VCB', name: 'Vietcombank' },
  { code: 'TCB', name: 'Techcombank' },
  { code: 'MB', name: 'MBBank' },
  { code: 'BIDV', name: 'BIDV' },
  { code: 'CTG', name: 'VietinBank' },
  { code: 'ACB', name: 'ACB' },
  { code: 'VPB', name: 'VPBank' },
  { code: 'TPB', name: 'TPBank' },
];

const STATUS_LABELS: Record<StudentWithdrawalStatus, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
  EXECUTED: 'Đã chuyển tiền',
  FAILED: 'Cần xử lý lại',
  CANCELLED: 'Đã hủy',
};

interface Props {
  wallet: StudentWalletResponse | null;
  minimumAmount: number;
  identityVerified: boolean;
  onVerifyIdentity: () => void;
  onChanged: () => Promise<void>;
}

export function StudentWithdrawalPanel({
  wallet,
  minimumAmount,
  identityVerified,
  onVerifyIdentity,
  onChanged,
}: Props) {
  const [withdrawals, setWithdrawals] = useState<StudentWithdrawal[]>([]);
  const [accounts, setAccounts] = useState<StudentBankAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [amount, setAmount] = useState('');
  const [accountId, setAccountId] = useState('');
  const [bankCode, setBankCode] = useState('VCB');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolderName, setAccountHolderName] = useState('');
  const [saveAccount, setSaveAccount] = useState(true);
  const [otpCode, setOtpCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpChallenge, setOtpChallenge] = useState<PhoneOtpChallengeResponse | null>(null);
  const [firebaseOtpSession, setFirebaseOtpSession] = useState<FirebasePhoneOtpSession | null>(null);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [history, savedAccounts] = await Promise.all([
        getStudentWithdrawals(),
        getStudentBankAccounts(),
      ]);
      setWithdrawals(history.content);
      setAccounts(savedAccounts);
      if (!accountId && savedAccounts.length > 0) {
        setAccountId(savedAccounts[0].id);
      }
    } catch {
      setError('Không thể tải lịch sử rút tiền.');
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => () => {
    void firebaseOtpSession?.cancel();
  }, [firebaseOtpSession]);

  const amountValue = Number(amount);
  const available = wallet?.availableWithdrawableBalance ?? 0;
  const selectedBank = BANKS.find((bank) => bank.code === bankCode) ?? BANKS[0];
  const selectedSavedAccount = accounts.find((account) => account.id === accountId);
  const ownershipVerified = selectedSavedAccount?.ownershipVerified || identityVerified;

  const validate = () => {
    if (!Number.isInteger(amountValue) || amountValue < minimumAmount) {
      return `Số tiền rút tối thiểu là ${minimumAmount.toLocaleString('vi-VN')}đ.`;
    }
    if (amountValue > available) {
      return 'Số dư có thể rút không đủ.';
    }
    if (!identityVerified) {
      return 'Vui lòng xác minh CCCD trước khi rút tiền.';
    }
    if (!accountId && (!accountNumber.trim() || !accountHolderName.trim())) {
      return 'Vui lòng nhập đầy đủ thông tin tài khoản ngân hàng.';
    }
    if (!ownershipVerified) {
      return 'Vui lòng xác nhận chính chủ để tiếp tục mô phỏng rút tiền.';
    }
    return null;
  };

  const handleSendOtp = async () => {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setProcessing(true);
    setError(null);
    try {
      await firebaseOtpSession?.cancel();
      setFirebaseOtpSession(null);
      const challenge = await sendStudentWithdrawalOtp();
      const session = challenge.verificationMethod === 'FIREBASE'
        ? await startFirebasePhoneOtp({
          challengeId: challenge.challengeId,
          phoneNumberE164: requiredPhoneNumber(challenge),
        })
        : null;
      setOtpChallenge(challenge);
      setFirebaseOtpSession(session);
      setOtpSent(true);
    } catch (requestError) {
      setOtpSent(false);
      setError(apiMessage(requestError, firebasePhoneErrorMessage(requestError)));
    } finally {
      setProcessing(false);
    }
  };

  const handleSubmit = async () => {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    if (!/^\d{6}$/.test(otpCode)) {
      setError('Mã OTP phải gồm 6 chữ số.');
      return;
    }
    setProcessing(true);
    setError(null);
    try {
      const verification = firebaseOtpSession
        ? await firebaseOtpSession.confirm(otpCode)
        : { otpCode };
      await createStudentWithdrawal({
        amount: amountValue,
        bankAccountId: accountId || undefined,
        bankAccount: accountId
          ? undefined
          : {
              bankCode: selectedBank.code,
              bankName: selectedBank.name,
              accountNumber: accountNumber.trim(),
              accountHolderName: accountHolderName.trim().toUpperCase(),
            },
        ...verification,
        saveAccount: !accountId && saveAccount,
        ownershipConfirmed: ownershipVerified,
      });
      setFormOpen(false);
      setAmount('');
      setOtpCode('');
      setOtpSent(false);
      setOtpChallenge(null);
      setFirebaseOtpSession(null);
      await Promise.all([loadData(), onChanged()]);
    } catch (requestError) {
      setError(apiMessage(requestError, firebasePhoneErrorMessage(requestError)));
    } finally {
      setProcessing(false);
    }
  };

  const handleCancel = async (id: string) => {
    setProcessing(true);
    setError(null);
    try {
      await cancelStudentWithdrawal(id);
      await Promise.all([loadData(), onChanged()]);
    } catch (requestError) {
      setError(apiMessage(requestError, 'Không thể hủy yêu cầu rút tiền.'));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <section className="mt-8 space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="font-bold text-slate-900">Rút tiền hoàn khóa học</h2>
            <p className="mt-1 text-sm text-slate-500">
              Có thể rút: <strong>{available.toLocaleString('vi-VN')} VND</strong>. Tiền nạp trực tiếp không thể rút.
            </p>
          </div>
          <button
            type="button"
            disabled={available < minimumAmount}
            onClick={() => {
              setFormOpen((current) => {
                if (current) {
                  void firebaseOtpSession?.cancel();
                  setFirebaseOtpSession(null);
                  setOtpChallenge(null);
                  setOtpCode('');
                  setOtpSent(false);
                }
                return !current;
              });
              setError(null);
            }}
            className="rounded-xl bg-slate-900 px-5 py-2.5 font-bold text-white disabled:cursor-not-allowed disabled:opacity-40"
          >
            {formOpen ? 'Đóng' : 'Tạo yêu cầu rút tiền'}
          </button>
        </div>

        {formOpen && (
          <div className="mt-6 grid gap-4 border-t border-slate-100 pt-6">
            {!identityVerified && (
              <div className="rounded-xl border border-blue-200 bg-blue-50 p-4">
                <p className="text-sm font-bold text-blue-900">Cần xác minh CCCD</p>
                <p className="mt-1 text-xs text-blue-700">
                  VNPT eKYC sẽ trả kết quả để hệ thống đối chiếu họ tên và ngày sinh với dữ liệu CCCD demo.
                </p>
                <button
                  type="button"
                  onClick={onVerifyIdentity}
                  className="mt-3 rounded-xl bg-blue-700 px-4 py-2 text-sm font-bold text-white"
                >
                  Đến trang xác minh
                </button>
              </div>
            )}
            <label className="grid gap-1 text-sm font-medium text-slate-700">
              Số tiền rút
              <input
                type="number"
                min={minimumAmount}
                step={1000}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                className="rounded-xl border border-slate-300 px-4 py-3"
              />
            </label>

            {accounts.length > 0 && (
              <label className="grid gap-1 text-sm font-medium text-slate-700">
                Tài khoản đã lưu
                <select
                  value={accountId}
                  onChange={(event) => {
                    setAccountId(event.target.value);
                  }}
                  className="rounded-xl border border-slate-300 px-4 py-3"
                >
                  <option value="">Sử dụng tài khoản mới</option>
                  {accounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.bankName} · {account.accountNumber} · {account.accountHolderName}
                      {account.ownershipVerified ? ' · Đã xác minh' : ' · Chưa xác minh'}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {!accountId && (
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="grid gap-1 text-sm font-medium text-slate-700">
                  Ngân hàng
                  <select
                    value={bankCode}
                    onChange={(event) => setBankCode(event.target.value)}
                    className="rounded-xl border border-slate-300 px-4 py-3"
                  >
                    {BANKS.map((bank) => (
                      <option key={bank.code} value={bank.code}>{bank.name}</option>
                    ))}
                  </select>
                </label>
                <label className="grid gap-1 text-sm font-medium text-slate-700">
                  Số tài khoản
                  <input
                    value={accountNumber}
                    onChange={(event) => setAccountNumber(event.target.value.replace(/\D/g, ''))}
                    className="rounded-xl border border-slate-300 px-4 py-3"
                  />
                </label>
                <label className="grid gap-1 text-sm font-medium text-slate-700 sm:col-span-2">
                  Tên chủ tài khoản
                  <input
                    value={accountHolderName}
                    onChange={(event) => setAccountHolderName(event.target.value)}
                    className="rounded-xl border border-slate-300 px-4 py-3 uppercase"
                  />
                </label>
                <label className="flex items-center gap-2 text-sm text-slate-600 sm:col-span-2">
                  <input
                    type="checkbox"
                    checked={saveAccount}
                    onChange={(event) => setSaveAccount(event.target.checked)}
                  />
                  Lưu tài khoản cho lần rút sau
                </label>
              </div>
            )}

            <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <p className="text-sm font-bold text-amber-900">Đối chiếu chính chủ (demo)</p>
              <p className="mt-1 text-xs text-amber-700">
                Luồng demo dùng kết quả VNPT và dữ liệu CCCD giả lập; không phải xác minh quốc gia thật.
              </p>
              <p className="mt-2 text-sm font-semibold text-amber-900">
                {identityVerified ? 'Đã xác minh CCCD và sẵn sàng đối chiếu.' : 'Hãy xác minh CCCD trước.'}
              </p>
            </div>

            {!otpSent ? (
              <button
                type="button"
                disabled={processing || !identityVerified}
                onClick={() => void handleSendOtp()}
                className="rounded-xl bg-red-600 px-5 py-3 font-bold text-white disabled:opacity-50"
              >
                Gửi OTP xác nhận
              </button>
            ) : (
              <div className="grid gap-3">
                <p className="text-sm text-slate-600">
                  {otpChallenge?.verificationMethod === 'FIREBASE'
                    ? `Firebase đã gửi SMS thật tới ${otpChallenge.maskedDestination ?? 'số điện thoại đã xác thực'}.`
                    : 'Mã OTP đã được gửi tới email của bạn.'}
                </p>
                <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
                <input
                  inputMode="numeric"
                  maxLength={6}
                  value={otpCode}
                  onChange={(event) => setOtpCode(event.target.value.replace(/\D/g, ''))}
                  placeholder="Nhập mã OTP gồm 6 số"
                  className="rounded-xl border border-slate-300 px-4 py-3"
                />
                <button
                  type="button"
                  disabled={processing || !identityVerified}
                  onClick={() => void handleSubmit()}
                  className="rounded-xl bg-red-600 px-5 py-3 font-bold text-white disabled:opacity-50"
                >
                  Xác nhận rút tiền
                </button>
                </div>
              </div>
            )}
          </div>
        )}

        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow">
        <h2 className="mb-4 font-bold text-slate-900">Lịch sử yêu cầu rút tiền</h2>
        {loading ? (
          <p className="text-sm text-slate-500">Đang tải…</p>
        ) : withdrawals.length === 0 ? (
          <p className="text-sm text-slate-500">Chưa có yêu cầu rút tiền.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {withdrawals.map((withdrawal) => (
              <div key={withdrawal.id} className="flex flex-col gap-2 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-bold text-slate-900">
                    {withdrawal.requestedAmount.toLocaleString('vi-VN')} {withdrawal.currency}
                  </p>
                  <p className="text-sm text-slate-500">
                    {withdrawal.bankName} · {withdrawal.accountNumberMasked} · {' '}
                    {new Date(withdrawal.requestedAt).toLocaleString('vi-VN')}
                  </p>
                  {withdrawal.rejectionReason && (
                    <p className="text-sm text-red-600">{withdrawal.rejectionReason}</p>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold text-slate-700">
                    {STATUS_LABELS[withdrawal.status]}
                  </span>
                  {withdrawal.status === 'PENDING' && (
                    <button
                      type="button"
                      disabled={processing}
                      onClick={() => void handleCancel(withdrawal.id)}
                      className="text-sm font-semibold text-red-600 disabled:opacity-50"
                    >
                      Hủy
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function apiMessage(error: unknown, fallback: string) {
  return (error as { response?: { data?: { message?: string } } })
    ?.response?.data?.message ?? fallback;
}

function requiredPhoneNumber(challenge: PhoneOtpChallengeResponse): string {
  if (!challenge.challengeId || !challenge.phoneNumberE164) {
    throw new Error('Backend không trả về challenge Firebase hợp lệ.');
  }
  return challenge.phoneNumberE164;
}
