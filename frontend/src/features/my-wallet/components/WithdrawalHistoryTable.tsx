import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import toast from 'react-hot-toast';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import { useCancelWithdrawal } from '../hooks/useCancelWithdrawal';
import type { WithdrawalRequest } from '../types/wallet.types';
import { WithdrawalStatusBadge } from './WithdrawalStatusBadge';

interface WithdrawalHistoryTableProps {
  withdrawals: WithdrawalRequest[];
}

export function WithdrawalHistoryTable({ withdrawals }: WithdrawalHistoryTableProps) {
  const cancelMutation = useCancelWithdrawal();
  const [selected, setSelected] = useState<WithdrawalRequest | null>(null);

  const handleConfirmCancel = () => {
    if (!selected) return;

    cancelMutation.mutate(selected.id, {
      onSuccess: () => {
        toast.success('Đã hủy yêu cầu rút tiền');
        setSelected(null);
      },
      onError: () => {
        toast.error('Không thể hủy yêu cầu. Vui lòng thử lại.');
      },
    });
  };

  if (withdrawals.length === 0) {
    return (
      <Box sx={{ px: 3, py: { xs: 6, md: 8 }, textAlign: 'center' }}>
        <Box
          sx={{
            alignItems: 'center',
            bgcolor: '#fef2f2',
            borderRadius: '50%',
            color: 'primary.main',
            display: 'inline-flex',
            height: 56,
            justifyContent: 'center',
            mb: 2,
            width: 56,
          }}
        >
          <PaymentsOutlinedIcon />
        </Box>
        <Typography sx={{ fontWeight: 700 }}>Chưa có yêu cầu rút tiền</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Yêu cầu mới sẽ xuất hiện tại đây để bạn theo dõi tiến trình.
        </Typography>
      </Box>
    );
  }

  return (
    <>
      <Box sx={{ display: { xs: 'block', md: 'none' }, p: 2 }}>
        <Stack spacing={1.5}>
          {withdrawals.map((item) => (
            <Box
              key={item.id}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                p: 2,
              }}
            >
              <Stack direction="row" sx={{ alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <Box>
                  <Typography sx={{ fontWeight: 800 }}>
                    {formatCurrency(item.requestedAmount)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDate(item.requestedAt)}
                  </Typography>
                </Box>
                <WithdrawalStatusBadge status={item.status} />
              </Stack>
              <Stack spacing={0.75} sx={{ mt: 2 }}>
                <DetailLine label="Ngân hàng" value={item.bankName} />
                <DetailLine label="Tài khoản" value={item.accountNumberMasked} />
                <DetailLine label="Mã yêu cầu" value={shortId(item.id)} />
              </Stack>
              {item.status === 'PENDING' && (
                <Button
                  color="error"
                  startIcon={<CancelOutlinedIcon />}
                  onClick={() => setSelected(item)}
                  sx={{ fontWeight: 700, mt: 1.5, textTransform: 'none' }}
                >
                  Hủy yêu cầu
                </Button>
              )}
            </Box>
          ))}
        </Stack>
      </Box>

      <TableContainer sx={{ display: { xs: 'none', md: 'block' } }}>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: '#f8fafc' }}>
              <TableCell sx={headerCellSx}>Mã yêu cầu</TableCell>
              <TableCell sx={headerCellSx}>Ngày yêu cầu</TableCell>
              <TableCell sx={headerCellSx}>Số tiền</TableCell>
              <TableCell sx={headerCellSx}>Tài khoản nhận</TableCell>
              <TableCell sx={headerCellSx}>Trạng thái</TableCell>
              <TableCell align="right" sx={headerCellSx}>Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {withdrawals.map((item) => (
              <TableRow key={item.id} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                    {shortId(item.id)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{formatDate(item.requestedAt)}</Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                    {formatCurrency(item.requestedAmount)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {item.bankName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {item.accountNumberMasked}
                  </Typography>
                </TableCell>
                <TableCell><WithdrawalStatusBadge status={item.status} /></TableCell>
                <TableCell align="right">
                  {item.status === 'PENDING' ? (
                    <Tooltip title="Hủy yêu cầu đang chờ duyệt">
                      <IconButton
                        color="error"
                        aria-label={`Hủy yêu cầu ${shortId(item.id)}`}
                        onClick={() => setSelected(item)}
                      >
                        <CancelOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  ) : (
                    <Typography variant="caption" color="text.disabled">—</Typography>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 800 }}>Hủy yêu cầu rút tiền?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {selected && (
              <>
                Yêu cầu <strong>{formatCurrency(selected.requestedAmount)}</strong> sẽ bị hủy.
                Số tiền đang giữ được hoàn lại vào số dư khả dụng.
              </>
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button color="inherit" onClick={() => setSelected(null)} sx={{ textTransform: 'none' }}>
            Giữ yêu cầu
          </Button>
          <Button
            color="error"
            variant="contained"
            disabled={cancelMutation.isPending}
            onClick={handleConfirmCancel}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            {cancelMutation.isPending ? 'Đang hủy...' : 'Xác nhận hủy'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

const headerCellSx = {
  color: 'text.secondary',
  fontSize: 12,
  fontWeight: 800,
  letterSpacing: '0.04em',
  textTransform: 'uppercase',
};

function DetailLine({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between' }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 600, textAlign: 'right' }}>{value}</Typography>
    </Stack>
  );
}

function shortId(value: string) {
  return `${value.slice(0, 8).toUpperCase()}…`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
