import { useCallback, useEffect, useMemo, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button } from '@mui/material';
import type { WithdrawalFormValues } from '../schemas/withdrawalSchema';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import type { TeacherWallet } from '../types/wallet.types';
import { walletService } from '../services/walletService';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  firebasePhoneErrorMessage,
  startFirebasePhoneOtp,
  type FirebasePhoneOtpSession,
  type PhoneOtpChallengeResponse,
} from '../../../shared/auth/firebasePhoneAuth';

const BANK_LIST = [
  { code: 'VCB', name: 'Vietcombank - Ngân hàng TMCP Ngoại thương VN' },
  { code: 'TCB', name: 'Techcombank - Ngân hàng TMCP Kỹ thương VN' },
  { code: 'MB', name: 'MBBank - Ngân hàng TMCP Quân đội' },
  { code: 'VPB', name: 'VPBank - Ngân hàng TMCP Việt Nam Thịnh Vượng' },
  { code: 'CTG', name: 'VietinBank - Ngân hàng TMCP Công thương VN' },
  { code: 'BIDV', name: 'BIDV - Ngân hàng TMCP Đầu tư và Phát triển VN' },
  { code: 'ACB', name: 'ACB - Ngân hàng TMCP Á Châu' },
  { code: 'STB', name: 'Sacombank - Ngân hàng TMCP Sài Gòn Thương Tín' },
  { code: 'TPB', name: 'TPBank - Ngân hàng TMCP Tiên Phong' },
  { code: 'VIB', name: 'VIB - Ngân hàng TMCP Quốc tế VN' },
];

interface WithdrawalRequestFormProps {
  wallet: TeacherWallet;
  onSubmit: (values: WithdrawalSubmission) => Promise<void>;
  isSubmitting: boolean;
}

export type WithdrawalSubmission = WithdrawalFormValues & {
  otpCode?: string;
  phoneAuthChallengeId?: string;
  firebaseIdToken?: string;
  saveAccount: boolean;
};

export function WithdrawalRequestForm({ wallet, onSubmit, isSubmitting }: WithdrawalRequestFormProps) {
  const { data: savedAccountsResponse } = useQuery({
    queryKey: ['savedBankAccounts'],
    queryFn: () => walletService.getSavedBankAccounts(),
  });
  
  const savedAccounts = useMemo(
    () => savedAccountsResponse?.data ?? [],
    [savedAccountsResponse?.data],
  );

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    control,
    formState: { errors },
  } = useForm<WithdrawalFormValues>({
    defaultValues: {
      amount: undefined,
      useNewAccount: true,
      bankAccountId: undefined,
      bankCode: '',
      bankName: '',
      accountNumber: '',
      accountHolderName: '',
      branch: '',
      bankQrDataUrl: '',
    },
  });

  const [selectedAccountId, setSelectedAccountId] = useState<string>('');
  const [useNewAccount, setUseNewAccount] = useState<boolean>(true);

  const handleSelectAccount = useCallback((id: string) => {
    setSelectedAccountId(id);
    setValue('bankAccountId', id, { shouldValidate: true });
    setValue('useNewAccount', false);
    const acc = savedAccounts.find(a => a.id === id);
    if (acc) {
      setValue('bankCode', acc.bankCode);
      setValue('bankName', acc.bankName);
      setValue('accountNumber', acc.accountNumber);
      setValue('accountHolderName', acc.accountHolderName);
    }
  }, [savedAccounts, setValue]);

  // When saved accounts load, default to first one if exists.
  useEffect(() => {
    if (savedAccounts.length > 0) {
      setUseNewAccount(false);
      handleSelectAccount(savedAccounts[0].id);
    }
  }, [handleSelectAccount, savedAccounts]);

  const amount = watch('amount');
  const amountNum = Number(amount) || 0;
  
  const [showOtp, setShowOtp] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [pendingValues, setPendingValues] = useState<WithdrawalFormValues | null>(null);
  const [countdown, setCountdown] = useState(0);
  const [saveAccount, setSaveAccount] = useState(true);
  const [otpChallenge, setOtpChallenge] = useState<PhoneOtpChallengeResponse | null>(null);
  const [firebaseOtpSession, setFirebaseOtpSession] = useState<FirebasePhoneOtpSession | null>(null);
  const [isConfirmingOtp, setIsConfirmingOtp] = useState(false);

  const { mutate: sendOtp, isPending: isSendingOtp } = useMutation({
    mutationFn: async () => {
      await firebaseOtpSession?.cancel();
      setFirebaseOtpSession(null);
      const response = await walletService.sendWithdrawalOtp();
      const challenge = response.data;
      const session = challenge.verificationMethod === 'FIREBASE'
        ? await startFirebasePhoneOtp({
          challengeId: challenge.challengeId,
          phoneNumberE164: requiredPhoneNumber(challenge),
        })
        : null;
      return { challenge, session };
    },
    onSuccess: ({ challenge, session }) => {
      setOtpChallenge(challenge);
      setFirebaseOtpSession(session);
      toast.success(challenge.verificationMethod === 'FIREBASE'
        ? 'Firebase đã gửi SMS thật tới số điện thoại đã xác thực.'
        : 'Mã OTP đã được gửi tới email của bạn.');
      setCountdown(60);
    },
    onError: (error) => {
      toast.error(apiErrorMessage(error) ?? firebasePhoneErrorMessage(error));
      setShowOtp(false);
    }
  });

  useEffect(() => () => {
    void firebaseOtpSession?.cancel();
  }, [firebaseOtpSession]);

  useEffect(() => {
    let timer: any;
    if (showOtp && countdown > 0) {
      timer = setInterval(() => setCountdown(c => c - 1), 1000);
    }
    return () => clearInterval(timer);
  }, [showOtp, countdown]);

  const isExceedBalance = amountNum > wallet.availableBalance;
  const isBelowMinimum = amountNum > 0 && amountNum < wallet.minimumPayoutAmount;
  const isDisabled = isSubmitting || isExceedBalance || isBelowMinimum || amountNum <= 0;

  const onPreSubmit = (values: WithdrawalFormValues) => {
    setPendingValues(values);
    setOtpCode('');
    setShowOtp(true);
    sendOtp();
  };

  const handleConfirmOtp = async () => {
    if (otpCode.length !== 6) {
      toast.error('Mã OTP phải gồm 6 chữ số');
      return;
    }
    if (!pendingValues || !otpChallenge) {
      toast.error('Phiên xác thực không hợp lệ. Hãy yêu cầu mã mới.');
      return;
    }
    try {
      setIsConfirmingOtp(true);
      const verification = firebaseOtpSession
        ? await firebaseOtpSession.confirm(otpCode)
        : { otpCode };
      await onSubmit({
        ...pendingValues,
        ...verification,
        saveAccount: useNewAccount ? saveAccount : false
      });
      setShowOtp(false);
      setFirebaseOtpSession(null);
      setOtpChallenge(null);
    } catch (error) {
      toast.error(apiErrorMessage(error) ?? firebasePhoneErrorMessage(error));
    } finally {
      setIsConfirmingOtp(false);
    }
  };

  const closeOtpDialog = () => {
    void firebaseOtpSession?.cancel();
    setFirebaseOtpSession(null);
    setOtpChallenge(null);
    setShowOtp(false);
  };

  return (
    <>
    <form onSubmit={handleSubmit(onPreSubmit)} className="space-y-6">
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">
            Số tiền rút (VND)
          </label>
          <Controller
            name="amount"
            control={control}
            rules={{
              required: 'Vui lòng nhập số tiền',
              min: {
                value: 1,
                message: 'Số tiền phải lớn hơn 0',
              },
            }}
            render={({ field }) => (
              <input
                ref={field.ref}
                type="text"
                inputMode="numeric"
                autoComplete="off"
                value={field.value
                  ? new Intl.NumberFormat('vi-VN', {
                      maximumFractionDigits: 0,
                    }).format(field.value)
                  : ''}
                onBlur={field.onBlur}
                onChange={(event) => {
                  const digits = event.target.value.replace(/\D/g, '');
                  field.onChange(digits ? Number(digits) : undefined);
                }}
                className={`w-full rounded-lg border px-3 py-2.5 shadow-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100 ${errors.amount ? 'border-red-500' : 'border-slate-300'}`}
                placeholder="Nhập số tiền..."
              />
            )}
          />
          {errors.amount && (
            <p className="mt-1 text-sm text-red-600">{errors.amount.message as string}</p>
          )}
          <p className="text-sm text-slate-500 mt-1">
            Khả dụng: <span className="font-semibold text-slate-900">{formatCurrency(wallet.availableBalance)}</span>
          </p>
        </div>

        {isExceedBalance && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded relative" role="alert">
            <span className="block sm:inline"> Số tiền yêu cầu vượt quá số dư khả dụng.</span>
          </div>
        )}

        {isBelowMinimum && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded relative" role="alert">
            <span className="block sm:inline"> Số tiền tối thiểu một lần rút là {formatCurrency(wallet.minimumPayoutAmount)}.</span>
          </div>
        )}
      </div>

      <div className="border-t pt-4">
        <div className="flex justify-between items-center mb-4">
          <h4 className="font-medium text-sm">Thông tin nhận tiền</h4>
          {savedAccounts.length > 0 && (
            <button
              type="button"
              onClick={() => {
                const nextUseNewAccount = !useNewAccount;
                setUseNewAccount(nextUseNewAccount);
                setValue('useNewAccount', nextUseNewAccount);
                if (useNewAccount && savedAccounts.length > 0) {
                  handleSelectAccount(savedAccounts[0].id);
                } else {
                  setSelectedAccountId('');
                  setValue('bankAccountId', undefined);
                  setValue('bankCode', '');
                  setValue('bankName', '');
                  setValue('accountNumber', '');
                  setValue('accountHolderName', '');
                }
              }}
              className="text-sm font-semibold text-[#C41E3A] hover:underline"
            >
              {useNewAccount ? 'Chọn tài khoản đã lưu' : 'Dùng tài khoản mới'}
            </button>
          )}
        </div>

        {!useNewAccount && savedAccounts.length > 0 ? (
          <div className="space-y-4">
            <label className="block text-sm font-medium text-slate-700 mb-1">Tài khoản đã lưu</label>
            <select
              value={selectedAccountId}
              onChange={(e) => handleSelectAccount(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2.5 shadow-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100"
            >
              {savedAccounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.bankCode} - {acc.accountNumber} - {acc.accountHolderName}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Ngân hàng</label>
              <Controller
                name="bankCode"
                control={control}
                rules={{ required: 'Vui lòng chọn ngân hàng' }}
                render={({ field }) => (
                  <select
                    {...field}
                    onChange={(e) => {
                      field.onChange(e);
                      const bank = BANK_LIST.find(b => b.code === e.target.value);
                      if (bank) setValue('bankName', bank.name);
                    }}
                    className={`w-full rounded-lg border px-3 py-2.5 shadow-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100 ${errors.bankCode ? 'border-red-500' : 'border-slate-300'}`}
                  >
                    <option value="">-- Chọn ngân hàng --</option>
                    {BANK_LIST.map(bank => (
                      <option key={bank.code} value={bank.code}>
                        {bank.code} - {bank.name}
                      </option>
                    ))}
                  </select>
                )}
              />
              {errors.bankCode && (
                <p className="mt-1 text-sm text-red-600">{errors.bankCode.message as string}</p>
              )}
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Số tài khoản</label>
              <input
                type="text"
                inputMode="numeric"
                autoComplete="off"
                {...register('accountNumber', { required: 'Vui lòng nhập số tài khoản' })}
                className={`w-full rounded-lg border px-3 py-2.5 shadow-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100 ${errors.accountNumber ? 'border-red-500' : 'border-slate-300'}`}
                placeholder="Nhập số tài khoản"
              />
              {errors.accountNumber && (
                <p className="mt-1 text-sm text-red-600">{errors.accountNumber.message as string}</p>
              )}
              <p className="mt-1 text-xs text-slate-500">
                ManabiHub chưa hỗ trợ tra cứu tên tài khoản tự động. Vui lòng tự kiểm tra thông tin trước khi gửi.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Tên chủ tài khoản</label>
              <input
                type="text"
                {...register('accountHolderName', { required: 'Vui lòng nhập tên chủ tài khoản' })}
                className={`w-full rounded-lg border px-3 py-2.5 shadow-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100 ${errors.accountHolderName ? 'border-red-500' : 'border-slate-300'}`}
                placeholder="VD: NGUYEN VAN A"
              />
              {errors.accountHolderName && (
                <p className="mt-1 text-sm text-red-600">{errors.accountHolderName.message as string}</p>
              )}
            </div>

            <div className="mt-4 flex items-center">
              <input
                id="save-account"
                type="checkbox"
                checked={saveAccount}
                onChange={(e) => setSaveAccount(e.target.checked)}
                className="h-4 w-4 rounded border-gray-300 text-[#C41E3A] focus:ring-[#C41E3A]"
              />
              <label htmlFor="save-account" className="ml-2 block text-sm text-gray-900">
                Lưu thông tin tài khoản cho lần rút sau
              </label>
            </div>
          </div>
        )}
      </div>

      <div className="border-t pt-4">
        <Controller
          name="bankQrDataUrl"
          control={control}
          rules={{ required: 'Vui lòng chọn ảnh QR ngân hàng để Finance quét.' }}
          render={({ field, fieldState }) => (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Ảnh mã QR ngân hàng <span className="text-red-600">*</span>
              </label>
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (!file) {
                    field.onChange('');
                    return;
                  }
                  if (file.size > 2 * 1024 * 1024) {
                    toast.error('Ảnh QR không được vượt quá 2 MB.');
                    event.target.value = '';
                    field.onChange('');
                    return;
                  }
                  const reader = new FileReader();
                  reader.onload = () => field.onChange(String(reader.result ?? ''));
                  reader.onerror = () => {
                    toast.error('Không thể đọc ảnh QR. Vui lòng chọn lại.');
                    field.onChange('');
                  };
                  reader.readAsDataURL(file);
                }}
                className={`block w-full rounded-lg border px-3 py-2 text-sm ${fieldState.error ? 'border-red-500' : 'border-slate-300'}`}
              />
              <p className="mt-1 text-xs text-slate-500">
                Ảnh chỉ được gửi riêng cho Finance để quét, không hiển thị công khai.
              </p>
              {fieldState.error && (
                <p className="mt-1 text-sm text-red-600">{fieldState.error.message}</p>
              )}
            </div>
          )}
        />
      </div>

      <button
        type="submit"
        disabled={isDisabled}
        className={`flex w-full justify-center rounded-lg border border-transparent bg-[#C41E3A] px-4 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-[#9d182e] focus:outline-none focus:ring-2 focus:ring-[#C41E3A] focus:ring-offset-2 ${isDisabled ? 'cursor-not-allowed opacity-50' : ''}`}
      >
        {isSubmitting ? 'Đang xử lý...' : 'Gửi yêu cầu rút tiền'}
      </button>
    </form>

    <Dialog
      open={showOtp}
      onClose={isSubmitting || isConfirmingOtp ? undefined : closeOtpDialog}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle sx={{ textAlign: 'center', fontWeight: 'bold' }}>Xác thực OTP</DialogTitle>
      <DialogContent>
        <div className="flex flex-col items-center mt-2">
          <p className="text-sm text-slate-500 text-center mb-6">
            {otpChallenge?.verificationMethod === 'FIREBASE'
              ? `Firebase đã gửi SMS thật tới ${otpChallenge.maskedDestination ?? 'số điện thoại đã xác thực'}.`
              : 'Mã xác thực 6 số đã được gửi về email của bạn. Vui lòng kiểm tra hộp thư.'}
          </p>
          <input
            type="text"
            className="w-48 rounded-lg border-2 px-4 py-3 text-center font-mono text-2xl tracking-[0.75em] shadow-inner outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100"
            placeholder="------"
            maxLength={6}
            value={otpCode}
            onChange={(e) => setOtpCode(e.target.value.replace(/[^0-9]/g, ''))}
            autoFocus
          />
          <div className="mt-6 text-sm">
            {countdown > 0 ? (
              <span className="text-slate-500">Gửi lại mã sau <span className="font-semibold text-[#C41E3A]">{countdown}s</span></span>
            ) : (
              <button 
                type="button" 
                onClick={() => sendOtp()}
                disabled={isSendingOtp}
                className="font-semibold text-[#C41E3A] hover:underline focus:outline-none disabled:opacity-50"
              >
                {isSendingOtp ? 'Đang gửi...' : 'Gửi lại mã OTP'}
              </button>
            )}
          </div>
        </div>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, justifyContent: 'center', gap: 2 }}>
        <Button
          onClick={closeOtpDialog}
          disabled={isSubmitting || isConfirmingOtp}
          variant="outlined"
          color="inherit"
          sx={{ width: '120px' }}
        >
          Hủy
        </Button>
        <Button 
          onClick={handleConfirmOtp} 
          variant="contained" 
          color="primary" 
          disabled={otpCode.length !== 6 || isSubmitting || isConfirmingOtp}
          sx={{ width: '120px' }}
        >
          {isSubmitting || isConfirmingOtp ? 'Đang xác thực...' : 'Xác nhận'}
        </Button>
      </DialogActions>
    </Dialog>
    </>
  );
}

function requiredPhoneNumber(challenge: PhoneOtpChallengeResponse): string {
  if (!challenge.challengeId || !challenge.phoneNumberE164) {
    throw new Error('Backend không trả về challenge Firebase hợp lệ.');
  }
  return challenge.phoneNumberE164;
}

function apiErrorMessage(error: unknown): string | null {
  return (error as { response?: { data?: { message?: string } } })
    ?.response?.data?.message ?? null;
}
