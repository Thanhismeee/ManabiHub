import { useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Pagination, Stack, Typography } from '@mui/material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import type { StudentWithdrawal, StudentWithdrawalStatus } from '../types';
import { cancelStudentWithdrawal } from '../services/studentWalletService';

const WITHDRAWAL_STATUS_MAP: Record<StudentWithdrawalStatus, { label: string; bgcolor: string; color: string }> = {
  PENDING: { label: 'Đang chờ', bgcolor: '#FFFBEB', color: '#B45309' },
  APPROVED: { label: 'Đã duyệt', bgcolor: '#ECFDF5', color: '#047857' },
  REJECTED: { label: 'Từ chối', bgcolor: '#FEF2F2', color: '#B91C1C' },
  EXECUTED: { label: 'Đã chuyển tiền', bgcolor: '#ECFDF5', color: '#047857' },
  FAILED: { label: 'Cần xử lý lại', bgcolor: '#FEF2F2', color: '#B91C1C' },
  CANCELLED: { label: 'Đã hủy', bgcolor: '#F3F4F6', color: '#4B5563' },
};

function formatMoney(amount: number, currency: string = 'VND') {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

interface StudentWithdrawalHistoryProps {
  withdrawals: StudentWithdrawal[];
  loading: boolean;
  error: boolean;
  onRetry: () => void;
  onChanged: () => Promise<void>;
  page?: number;
  totalPages?: number;
  onPageChange?: (newPage: number) => void;
}

export function StudentWithdrawalHistory({
  withdrawals,
  loading,
  error,
  onRetry,
  onChanged,
  page = 0,
  totalPages = 1,
  onPageChange,
}: StudentWithdrawalHistoryProps) {
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const handleCancel = async (id: string) => {
    setCancellingId(id);
    setActionError(null);
    try {
      await cancelStudentWithdrawal(id);
      await onChanged();
    } catch {
      setActionError('Không thể hủy yêu cầu rút tiền. Vui lòng thử lại.');
    } finally {
      setCancellingId(null);
    }
  };

  if (loading) return <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Đang tải lịch sử rút tiền…</Typography>;

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={onRetry}>Thử lại</Button>}>
        Không thể tải lịch sử yêu cầu rút tiền.
      </Alert>
    );
  }

  if (withdrawals.length === 0) {
    return (
      <Stack spacing={1.5} sx={{ py: 6, alignItems: 'center', textAlign: 'center' }}>
        <AccountBalanceIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
        <Typography color="text.secondary" sx={{ fontWeight: 600 }}>Chưa có yêu cầu rút tiền về ngân hàng nào.</Typography>
      </Stack>
    );
  }

  return (
    <Stack spacing={1.5}>
      {actionError && <Alert severity="error">{actionError}</Alert>}

      {withdrawals.map((item) => {
        const badge = WITHDRAWAL_STATUS_MAP[item.status] ?? { label: item.status, bgcolor: '#F3F4F6', color: '#4B5563' };
        const isCancelling = cancellingId === item.id;

        return (
          <Box
            key={item.id}
            sx={{
              p: 2,
              borderRadius: 2.5,
              border: '1px solid #E5E7EB',
              bgcolor: '#FFFFFF',
              display: 'flex',
              flexDirection: { xs: 'column', sm: 'row' },
              justifyContent: 'space-between',
              alignItems: { sm: 'center' },
              gap: 1.5,
            }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box
                sx={{
                  bgcolor: 'rgba(196, 30, 58, 0.08)',
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
                <Typography sx={{ fontWeight: 800, fontSize: '0.95rem' }}>
                  Rút {formatMoney(item.requestedAmount, item.currency)} về {item.bankName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  TK: {item.accountNumberMasked} ({item.accountHolderName}) · {new Date(item.requestedAt).toLocaleString('vi-VN')}
                </Typography>
                {item.rejectionReason && (
                  <Typography variant="caption" sx={{ display: 'block', color: 'error.main', mt: 0.25 }}>
                    Lý do từ chối: {item.rejectionReason}
                  </Typography>
                )}
              </Box>
            </Stack>

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip
                label={badge.label}
                size="small"
                sx={{
                  fontWeight: 700,
                  fontSize: '0.75rem',
                  bgcolor: badge.bgcolor,
                  color: badge.color,
                  border: 'none',
                  px: 0.5,
                }}
              />
              {item.status === 'PENDING' && (
                <Button
                  size="small"
                  variant="outlined"
                  color="error"
                  disabled={isCancelling}
                  onClick={() => void handleCancel(item.id)}
                  sx={{ borderRadius: 2, fontWeight: 700, px: 1.5, textTransform: 'none' }}
                >
                  {isCancelling ? <CircularProgress size={16} color="inherit" /> : 'Hủy yêu cầu'}
                </Button>
              )}
            </Stack>
          </Box>
        );
      })}

      {/* Pagination component */}
      {totalPages > 1 && onPageChange && (
        <Box sx={{ mt: 2, display: 'flex', justifyContent: 'center' }}>
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => onPageChange(value - 1)}
            sx={{
              '& .MuiPaginationItem-root': {
                borderRadius: 2,
                fontWeight: 700,
                color: '#475569',
                border: '1px solid #E2E8F0',
                bgcolor: '#FFFFFF',
                '&.Mui-selected': {
                  bgcolor: '#C41E3A !important',
                  color: '#FFFFFF !important',
                  borderColor: '#C41E3A',
                },
                '&:hover': {
                  bgcolor: 'rgba(196, 30, 58, 0.08)',
                },
              },
            }}
          />
        </Box>
      )}
    </Stack>
  );
}
