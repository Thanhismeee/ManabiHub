import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import SecurityIcon from '@mui/icons-material/Security';
import {
  createStudentWithdrawal,
  getStudentBankAccounts,
  sendStudentWithdrawalOtp,
} from '../services/studentWalletService';
import type { StudentBankAccount, StudentWalletResponse } from '../types';
import {
  firebasePhoneErrorMessage,
  startFirebasePhoneOtp,
  type FirebasePhoneOtpSession,
  type PhoneOtpChallengeResponse,
} from '../../../shared/auth/firebasePhoneAuth';

const BANKS = [
  { code: 'VCB', name: 'Vietcombank (Ngân hàng TMCP Ngoại thương Việt Nam)' },
  { code: 'TCB', name: 'Techcombank (Ngân hàng Kỹ thương Việt Nam)' },
  { code: 'MB', name: 'MBBank (Ngân hàng Quân đội)' },
  { code: 'BIDV', name: 'BIDV (Ngân hàng Đầu tư và Phát triển Việt Nam)' },
  { code: 'CTG', name: 'VietinBank (Ngân hàng Công thương Việt Nam)' },
  { code: 'ACB', name: 'ACB (Ngân hàng Á Châu)' },
  { code: 'VPB', name: 'VPBank (Ngân hàng Việt Nam Thịnh Vượng)' },
  { code: 'TPB', name: 'TPBank (Ngân hàng Tiên Phong)' },
];

function formatMoney(amount: number, currency: string = 'VND') {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

interface StudentWithdrawalModalProps {
  open: boolean;
  onClose: () => void;
  wallet: StudentWalletResponse | null;
  minimumAmount: number;
  identityVerified: boolean;
  onSuccess: () => Promise<void>;
}

export function StudentWithdrawalModal({
  open,
  onClose,
  wallet,
  minimumAmount,
  identityVerified,
  onSuccess,
}: StudentWithdrawalModalProps) {
  const [accounts, setAccounts] = useState<StudentBankAccount[]>([]);
  const [amount, setAmount] = useState<string>('');
  const [accountId, setAccountId] = useState<string>('');
  const [bankCode, setBankCode] = useState<string>('VCB');
  const [accountNumber, setAccountNumber] = useState<string>('');
  const [accountHolderName, setAccountHolderName] = useState<string>('');
  const [saveAccount, setSaveAccount] = useState<boolean>(true);
  const [otpCode, setOtpCode] = useState<string>('');
  const [otpSent, setOtpSent] = useState<boolean>(false);
  const [otpChallenge, setOtpChallenge] = useState<PhoneOtpChallengeResponse | null>(null);
  const [firebaseOtpSession, setFirebaseOtpSession] = useState<FirebasePhoneOtpSession | null>(null);
  const [processing, setProcessing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const available = wallet?.availableWithdrawableBalance ?? 0;
  const currency = wallet?.currency ?? 'VND';
  const selectedBank = BANKS.find((b) => b.code === bankCode) ?? BANKS[0];
  const selectedSavedAccount = accounts.find((a) => a.id === accountId);
  const ownershipVerified = selectedSavedAccount?.ownershipVerified || identityVerified;

  const loadSavedAccounts = useCallback(async () => {
    try {
      const saved = await getStudentBankAccounts();
      setAccounts(saved);
      if (saved.length > 0 && !accountId) {
        setAccountId(saved[0].id);
      }
    } catch {
      // Ignore transient error
    }
  }, [accountId]);

  useEffect(() => {
    if (open) {
      void loadSavedAccounts();
      setError(null);
    }
  }, [open, loadSavedAccounts]);

  useEffect(() => () => {
    void firebaseOtpSession?.cancel();
  }, [firebaseOtpSession]);

  const resetAndClose = () => {
    void firebaseOtpSession?.cancel();
    setFirebaseOtpSession(null);
    setOtpChallenge(null);
    setAmount('');
    setOtpCode('');
    setOtpSent(false);
    setError(null);
    onClose();
  };

  const handleClose = () => {
    if (processing) return;
    resetAndClose();
  };

  const amountValue = Number(amount);

  const validate = (): string | null => {
    if (!amount || !Number.isInteger(amountValue) || amountValue < minimumAmount) {
      return `Số tiền rút tối thiểu là ${formatMoney(minimumAmount, currency)}.`;
    }
    if (amountValue > available) {
      return `Số dư có thể rút không đủ (Tối đa ${formatMoney(available, currency)}).`;
    }
    if (!accountId && (!accountNumber.trim() || !accountHolderName.trim())) {
      return 'Vui lòng nhập đầy đủ số tài khoản và tên chủ tài khoản.';
    }
    if (!identityVerified) {
      return 'Vui lòng xác nhận tài khoản chính chủ trước khi tiếp tục.';
    }
    return null;
  };

  const handleSendOtp = async () => {
    const err = validate();
    if (err) {
      setError(err);
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
      setError(getErrorMessage(requestError, firebasePhoneErrorMessage(requestError)));
    } finally {
      setProcessing(false);
    }
  };

  const handleSubmit = async () => {
    const err = validate();
    if (err) {
      setError(err);
      return;
    }
    if (!/^\d{6}$/.test(otpCode)) {
      setError('Mã OTP xác nhận phải gồm đúng 6 chữ số.');
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
              bankName: selectedBank.name.split(' (')[0],
              accountNumber: accountNumber.trim(),
              accountHolderName: accountHolderName.trim().toUpperCase(),
            },
        ...verification,
        saveAccount: !accountId && saveAccount,
        ownershipConfirmed: ownershipVerified,
      });

      await onSuccess();
      resetAndClose();
    } catch (requestError) {
      setError(getErrorMessage(requestError, firebasePhoneErrorMessage(requestError)));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      slotProps={{
        paper: {
          sx: {
            borderRadius: 3,
            p: 1,
          },
        },
      }}
    >
      <DialogTitle sx={{ m: 0, p: 2, pb: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              bgcolor: 'rgba(196, 30, 58, 0.1)',
              color: '#C41E3A',
              p: 1,
              borderRadius: 2,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <AccountBalanceIcon fontSize="small" />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800, fontSize: '1.15rem' }}>
              Yêu cầu rút tiền hoàn
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Rút tiền từ ví về tài khoản ngân hàng cá nhân
            </Typography>
          </Box>
        </Stack>
        <IconButton onClick={handleClose} disabled={processing} aria-label="Đóng">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers sx={{ borderTopColor: 'divider', borderBottomColor: 'divider', py: 2.5 }}>
        <Stack spacing={2.5}>
          {error && <Alert severity="error">{error}</Alert>}

          {/* Balance overview */}
          <Box
            sx={{
              p: 2,
              borderRadius: 2.5,
              bgcolor: '#FAF9F6',
              border: '1px solid #E5E7EB',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
              Số dư tiền hoàn có thể rút:
            </Typography>
            <Typography variant="subtitle1" sx={{ fontWeight: 900, color: '#C41E3A' }}>
              {formatMoney(available, currency)}
            </Typography>
          </Box>

          {/* Step 1: Amount input */}
          <TextField
            label="Số tiền muốn rút"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={`Tối thiểu ${minimumAmount.toLocaleString('vi-VN')}đ`}
            disabled={processing || otpSent}
            fullWidth
            slotProps={{
              input: {
                endAdornment: <InputAdornment position="end">đ</InputAdornment>,
              },
            }}
            helperText={`Hạn mức rút từ ${formatMoney(minimumAmount, currency)} đến ${formatMoney(available, currency)}`}
          />

          {/* Step 2: Bank selection */}
          {accounts.length > 0 && (
            <FormControl fullWidth disabled={processing || otpSent}>
              <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.75 }}>
                Tài khoản ngân hàng
              </Typography>
              <Select
                value={accountId}
                onChange={(e) => {
                  setAccountId(e.target.value);
                }}
                size="small"
              >
                {accounts.map((acc) => (
                  <MenuItem key={acc.id} value={acc.id}>
                    {acc.bankName} · {acc.accountNumber} · {acc.accountHolderName}
                  </MenuItem>
                ))}
                <MenuItem value="">+ Sử dụng tài khoản ngân hàng mới</MenuItem>
              </Select>
            </FormControl>
          )}

          {(!accountId || accounts.length === 0) && (
            <Stack spacing={2} sx={{ p: 2, borderRadius: 2.5, border: '1px dashed #CBD5E1', bgcolor: '#F8FAFC' }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                Thông tin tài khoản mới
              </Typography>

              <FormControl fullWidth size="small" disabled={processing || otpSent}>
                <Select value={bankCode} onChange={(e) => setBankCode(e.target.value)}>
                  {BANKS.map((b) => (
                    <MenuItem key={b.code} value={b.code}>
                      {b.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <TextField
                label="Số tài khoản ngân hàng"
                size="small"
                value={accountNumber}
                onChange={(e) => setAccountNumber(e.target.value.replace(/\D/g, ''))}
                disabled={processing || otpSent}
                placeholder="Nhập số tài khoản"
              />

              <TextField
                label="Họ và tên chủ tài khoản (viết hoa)"
                size="small"
                value={accountHolderName}
                onChange={(e) => setAccountHolderName(e.target.value.toUpperCase())}
                disabled={processing || otpSent}
                placeholder="NGUYEN VAN A"
              />

              <FormControlLabel
                control={
                  <Checkbox
                    checked={saveAccount}
                    onChange={(e) => setSaveAccount(e.target.checked)}
                    disabled={processing || otpSent}
                    size="small"
                    sx={{ color: '#C41E3A', '&.Mui-checked': { color: '#C41E3A' } }}
                  />
                }
                label={<Typography variant="caption">Lưu tài khoản ngân hàng này cho lần rút sau</Typography>}
              />
            </Stack>
          )}

          {/* Verification check */}
          <Box
            sx={{
              p: 1.75,
              borderRadius: 2,
              bgcolor: 'rgba(245, 158, 11, 0.08)',
              border: '1px solid rgba(245, 158, 11, 0.3)',
            }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
              <SecurityIcon sx={{ color: '#D97706', fontSize: 20, mt: 0.2 }} />
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 700, color: '#B45309' }}>
                  Xác nhận thông tin chính chủ
                </Typography>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={ownershipVerified}
                      disabled
                      onChange={() => undefined}
                      size="small"
                      sx={{ color: '#D97706', '&.Mui-checked': { color: '#D97706' } }}
                    />
                  }
                  label={
                    <Typography variant="body2" sx={{ fontWeight: 600, color: '#92400E' }}>
                      Tôi cam kết tài khoản ngân hàng trên là chính chủ.
                    </Typography>
                  }
                />
              </Box>
            </Stack>
          </Box>

          {/* OTP Section */}
          {otpSent && (
            <Stack spacing={1} sx={{ p: 2, borderRadius: 2, bgcolor: '#FEF2F2', border: '1px solid #FCA5A5' }}>
              <Typography variant="body2" sx={{ fontWeight: 700, color: '#991B1B' }}>
                {otpChallenge?.verificationMethod === 'FIREBASE'
                  ? `Firebase đã gửi SMS thật tới ${otpChallenge.maskedDestination ?? 'số điện thoại đã xác thực'}`
                  : 'Mã OTP xác nhận đã được gửi tới email'}
              </Typography>
              <TextField
                label="Mã OTP (6 chữ số)"
                size="small"
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                slotProps={{
                  htmlInput: { maxLength: 6 },
                }}
                disabled={processing}
                autoFocus
              />
              <FormHelperText sx={{ color: '#7F1D1D' }}>
                Vui lòng kiểm tra mã OTP để xác nhận giao dịch rút tiền.
              </FormHelperText>
            </Stack>
          )}

          {/* Actions */}
          {!otpSent ? (
            <Button
              variant="contained"
              fullWidth
              size="large"
              disabled={processing || available < minimumAmount}
              onClick={() => void handleSendOtp()}
              sx={{
                bgcolor: '#C41E3A',
                '&:hover': { bgcolor: '#9D182E' },
                py: 1.25,
                fontWeight: 700,
                borderRadius: 2.5,
              }}
            >
              {processing ? <CircularProgress size={24} color="inherit" /> : 'Gửi mã OTP xác nhận'}
            </Button>
          ) : (
            <Button
              variant="contained"
              fullWidth
              size="large"
              disabled={processing || otpCode.length !== 6}
              onClick={() => void handleSubmit()}
              sx={{
                bgcolor: '#C41E3A',
                '&:hover': { bgcolor: '#9D182E' },
                py: 1.25,
                fontWeight: 700,
                borderRadius: 2.5,
              }}
            >
              {processing ? <CircularProgress size={24} color="inherit" /> : 'Xác nhận rút tiền'}
            </Button>
          )}
        </Stack>
      </DialogContent>
    </Dialog>
  );
}

function getErrorMessage(error: unknown, fallback: string): string {
  return (
    (error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
  );
}

function requiredPhoneNumber(challenge: PhoneOtpChallengeResponse): string {
  if (!challenge.challengeId || !challenge.phoneNumberE164) {
    throw new Error('Backend không trả về challenge Firebase hợp lệ.');
  }
  return challenge.phoneNumberE164;
}
