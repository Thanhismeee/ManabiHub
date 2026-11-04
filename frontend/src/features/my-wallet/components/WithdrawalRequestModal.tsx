import CloseIcon from '@mui/icons-material/Close';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import {
  Box,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import toast from 'react-hot-toast';
import { useCreateWithdrawal } from '../hooks/useCreateWithdrawal';
import type { TeacherWallet } from '../types/wallet.types';
import {
  WithdrawalRequestForm,
  type WithdrawalSubmission,
} from './WithdrawalRequestForm';

interface WithdrawalRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  wallet: TeacherWallet;
}

export function WithdrawalRequestModal({
  isOpen,
  onClose,
  wallet,
}: WithdrawalRequestModalProps) {
  const createWithdrawal = useCreateWithdrawal();

  const handleSubmit = async (values: WithdrawalSubmission) => {
    try {
      await createWithdrawal.mutateAsync({
      amount: values.amount,
      bankAccountId: values.useNewAccount ? undefined : values.bankAccountId,
      bankAccount: values.useNewAccount
        ? {
            accountHolderName: values.accountHolderName || '',
            accountNumber: values.accountNumber || '',
            bankCode: values.bankCode || '',
            bankName: values.bankName || '',
            branch: values.branch,
          }
        : undefined,
      otpCode: values.otpCode,
      phoneAuthChallengeId: values.phoneAuthChallengeId,
      firebaseIdToken: values.firebaseIdToken,
      saveAccount: values.useNewAccount && values.saveAccount,
      bankQrDataUrl: values.bankQrDataUrl || '',
      });
      toast.success('Đã gửi yêu cầu rút tiền');
      onClose();
    } catch (error) {
      const messages: Record<string, string> = {
          WALLET_INSUFFICIENT_BALANCE: 'Số dư khả dụng không đủ để thực hiện yêu cầu.',
          WALLET_FROZEN: 'Ví doanh thu đang bị tạm khóa.',
          PAYOUT_AMOUNT_BELOW_MINIMUM: 'Số tiền rút chưa đạt mức tối thiểu.',
          PAYOUT_PENDING_REQUEST_EXISTS: 'Bạn đang có một yêu cầu rút tiền chờ xử lý.',
          PAYOUT_MONTHLY_LIMIT_EXCEEDED: 'Bạn đã vượt quá giới hạn rút tiền trong tháng.',
          PAYOUT_PHONE_VERIFICATION_REQUIRED: 'Bạn cần xác thực số điện thoại trước khi rút hoa hồng.',
          PAYOUT_BANK_QR_REQUIRED: 'Vui lòng chọn ảnh QR ngân hàng để Finance đối soát.',
          PAYOUT_BANK_QR_INVALID: 'Ảnh QR không hợp lệ. Hãy chọn PNG, JPEG hoặc WEBP dưới 2 MB.',
          PAYOUT_PHONE_AUTH_NOT_CONFIGURED: 'Xác thực SMS cho rút tiền chưa được cấu hình.',
        };
      const response = (error as {
        response?: { data?: { messageCode?: string; message?: string } };
      }).response?.data;
      const code = response?.messageCode ?? '';
      throw new Error(messages[code] ?? response?.message ?? 'Không thể tạo yêu cầu rút tiền. Vui lòng thử lại.');
    }
  };

  return (
    <Dialog open={isOpen} onClose={createWithdrawal.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle component="div" sx={{ borderBottom: '1px solid', borderColor: 'divider', px: 3, py: 2.5 }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Box
            sx={{
              alignItems: 'center',
              bgcolor: '#fef2f2',
              borderRadius: 2,
              color: 'primary.main',
              display: 'flex',
              height: 40,
              justifyContent: 'center',
              width: 40,
            }}
          >
            <PaymentsOutlinedIcon />
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Yêu cầu rút tiền</Typography>
            <Typography variant="body2" color="text.secondary">
              Tiền sẽ được giữ an toàn trong khi Finance Manager xử lý.
            </Typography>
          </Box>
          <IconButton aria-label="Đóng" disabled={createWithdrawal.isPending} onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Stack>
      </DialogTitle>
      <DialogContent sx={{ px: 3, py: 3 }}>
        <WithdrawalRequestForm
          wallet={wallet}
          onSubmit={handleSubmit}
          isSubmitting={createWithdrawal.isPending}
        />
      </DialogContent>
    </Dialog>
  );
}
