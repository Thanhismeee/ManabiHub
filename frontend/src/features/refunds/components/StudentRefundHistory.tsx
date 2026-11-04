import { Alert, Box, Button, Chip, Pagination, Stack, Typography } from '@mui/material';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import type { StudentRefundResponse } from '../types';

const REFUND_STATUS_MAP: Record<string, { label: string; bgcolor: string; color: string }> = {
  PENDING: { label: 'Đang chờ', bgcolor: '#FFFBEB', color: '#B45309' },
  PROCESSING: { label: 'Đang xử lý hoàn tiền', bgcolor: '#EFF6FF', color: '#1D4ED8' },
  RECONCILIATION_REQUIRED: { label: 'Cần đối soát', bgcolor: '#FFFBEB', color: '#B45309' },
  APPROVED: { label: 'Đã duyệt', bgcolor: '#ECFDF5', color: '#047857' },
  REJECTED: { label: 'Từ chối', bgcolor: '#FEF2F2', color: '#B91C1C' },
  CANCELLED: { label: 'Đã hủy', bgcolor: '#F3F4F6', color: '#4B5563' },
  PROCESSED: { label: 'Đã hoàn tiền', bgcolor: '#EFF6FF', color: '#1D4ED8' },
  COMPLETED: { label: 'Hoàn tất', bgcolor: '#ECFDF5', color: '#047857' },
};

function formatMoney(amount: number, currency: string = 'VND') {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

interface StudentRefundHistoryProps {
  refunds: StudentRefundResponse[];
  loading: boolean;
  error: boolean;
  onRetry: () => void;
  onOpen: (refund: StudentRefundResponse) => void;
  page?: number;
  totalPages?: number;
  onPageChange?: (page: number) => void;
}

export function StudentRefundHistory({
  refunds,
  loading,
  error,
  onRetry,
  onOpen,
  page = 0,
  totalPages = 1,
  onPageChange,
}: StudentRefundHistoryProps) {
  if (loading) return <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Đang tải danh sách yêu cầu hoàn tiền…</Typography>;

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={onRetry}>Thử lại</Button>}>
        Không thể tải lịch sử yêu cầu hoàn tiền.
      </Alert>
    );
  }

  if (refunds.length === 0) {
    return (
      <Stack spacing={1.5} sx={{ py: 6, alignItems: 'center', textAlign: 'center' }}>
        <ReceiptLongIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
        <Typography color="text.secondary" sx={{ fontWeight: 600 }}>Chưa có yêu cầu hoàn tiền khóa học nào.</Typography>
      </Stack>
    );
  }

  return (
    <Stack spacing={1.5}>
      {refunds.map((refund) => {
        const badge = REFUND_STATUS_MAP[refund.status] ?? { label: refund.status, bgcolor: '#F3F4F6', color: '#4B5563' };
        return (
          <Box
            key={refund.id}
            onClick={() => onOpen(refund)}
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
              cursor: 'pointer',
              transition: 'all 0.15s ease',
              '&:hover': {
                borderColor: '#C41E3A',
                bgcolor: '#FAFAFA',
                boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
              },
            }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box
                sx={{
                  bgcolor: 'rgba(59, 130, 246, 0.1)',
                  color: '#2563EB',
                  p: 1,
                  borderRadius: 2,
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <ReceiptLongIcon fontSize="small" />
              </Box>
              <Box>
                <Typography sx={{ fontWeight: 800, fontSize: '0.95rem' }}>{refund.courseTitle}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Mã đơn: {refund.orderCode} · {new Date(refund.createdAt).toLocaleDateString('vi-VN')} · {formatMoney(refund.eligibilitySnapshot.actuallyPaidAmount)}
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip
                label={refund.automaticRefundPending ? 'Đang tự động hoàn tiền' : badge.label}
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
            </Stack>
          </Box>
        );
      })}
      {totalPages > 1 && onPageChange && (
        <Box sx={{ display: 'flex', justifyContent: 'center', pt: 1 }}>
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => onPageChange(value - 1)}
            aria-label="Phân trang lịch sử hoàn tiền"
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
              },
            }}
          />
        </Box>
      )}
    </Stack>
  );
}
