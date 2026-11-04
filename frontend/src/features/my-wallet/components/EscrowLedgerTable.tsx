import { HelpCircle } from 'lucide-react';
import {
  Box,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import type { EscrowLedgerItem } from '../types/wallet.types';

interface EscrowLedgerTableProps {
  items: EscrowLedgerItem[];
}

const STATUS_MAP = {
  HELD: { color: 'warning', label: 'Đang giữ' },
  RELEASED: { color: 'success', label: 'Đã cộng ví' },
  REFUNDED: { color: 'error', label: 'Đã hoàn tiền' },
  CANCELLED: { color: 'default', label: 'Đã hủy' },
} as const;

export function EscrowLedgerTable({ items }: EscrowLedgerTableProps) {
  if (!items.length) {
    return (
      <EmptyState
        title="Chưa có giao dịch nào"
        description="Lịch sử doanh thu của bạn sẽ xuất hiện tại đây."
      />
    );
  }

  return (
    <TableContainer>
      <Table sx={{ minWidth: 800 }}>
        <TableHead>
          <TableRow>
            <TableCell>Khóa học</TableCell>
            <TableCell align="right">
              <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                Doanh thu gộp
                <Tooltip title="Tổng số tiền học viên đã thanh toán.">
                  <Box sx={{ color: 'text.secondary', display: 'flex' }}>
                    <HelpCircle size={16} />
                  </Box>
                </Tooltip>
              </Box>
            </TableCell>
            <TableCell align="right">
              <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                Phí nền tảng
                <Tooltip title="Phí duy trì và vận hành nền tảng ManabiHub. Chi tiết xem tại Trung tâm trợ giúp.">
                  <Box sx={{ color: 'text.secondary', display: 'flex' }}>
                    <HelpCircle size={16} />
                  </Box>
                </Tooltip>
              </Box>
            </TableCell>
            <TableCell align="right">
              <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                Thực nhận
                <Tooltip title="Số tiền thực tế cộng vào ví doanh thu của bạn.">
                  <Box sx={{ color: 'text.secondary', display: 'flex' }}>
                    <HelpCircle size={16} />
                  </Box>
                </Tooltip>
              </Box>
            </TableCell>
            <TableCell>Trạng thái</TableCell>
            <TableCell>Ngày dự kiến rút</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((item) => {
            const statusInfo = STATUS_MAP[item.status];
            return (
              <TableRow key={item.id}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {item.courseName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Mã đơn: {item.orderId.split('-')[0]}
                  </Typography>
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 500 }}>
                  {formatCurrency(item.grossAmount)}
                </TableCell>
                <TableCell align="right" sx={{ color: 'error.main' }}>
                  -{formatCurrency(item.platformCommissionAmount)}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main' }}>
                  {formatCurrency(item.teacherNetAmount)}
                </TableCell>
                <TableCell>
                  <Chip
                    label={statusInfo.label}
                    color={statusInfo.color}
                    size="small"
                    sx={{ fontWeight: 600 }}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="body2">
                    {new Intl.DateTimeFormat('vi-VN', {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                      timeZone: 'Asia/Ho_Chi_Minh'
                    }).format(new Date(item.releaseAt))}
                  </Typography>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
