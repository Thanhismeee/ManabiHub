import { Chip } from '@mui/material';
import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  RotateCw,
  XCircle,
} from 'lucide-react';
import type {
  PayoutStatus,
  ReconciliationStatus,
  WithdrawalStatus,
} from '../types/payout.types';

type BadgeStatus = PayoutStatus | ReconciliationStatus | WithdrawalStatus;

const BADGE_CONFIG: Record<BadgeStatus, {
  background: string;
  color: string;
  Icon: typeof CheckCircle2;
  label: string;
}> = {
  PENDING: { label: 'Chờ xử lý', background: '#fffbeb', color: '#b45309', Icon: Clock3 },
  APPROVED: { label: 'Đã duyệt', background: '#eff6ff', color: '#1d4ed8', Icon: CheckCircle2 },
  EXECUTED: { label: 'Đã thanh toán', background: '#f0fdf4', color: '#15803d', Icon: CheckCircle2 },
  CANCELLED: { label: 'Đã hủy', background: '#f1f5f9', color: '#475569', Icon: Ban },
  PROCESSING: { label: 'Đang xử lý', background: '#eff6ff', color: '#1d4ed8', Icon: LoaderCircle },
  SUCCEEDED: { label: 'Thành công', background: '#f0fdf4', color: '#15803d', Icon: CheckCircle2 },
  FAILED: { label: 'Thất bại', background: '#fef2f2', color: '#b91c1c', Icon: XCircle },
  PENDING_RETRY: { label: 'Chờ thử lại', background: '#fff7ed', color: '#c2410c', Icon: RotateCw },
  REJECTED: { label: 'Đã từ chối', background: '#fef2f2', color: '#b91c1c', Icon: XCircle },
  MATCHED: { label: 'Đối soát khớp', background: '#f0fdf4', color: '#15803d', Icon: CheckCircle2 },
  WARNING: { label: 'Có cảnh báo', background: '#fffbeb', color: '#b45309', Icon: AlertTriangle },
  CRITICAL_MISMATCH: {
    label: 'Sai lệch nghiêm trọng',
    background: '#fef2f2',
    color: '#b91c1c',
    Icon: XCircle,
  },
  RESOLVED: { label: 'Đã xử lý sai lệch', background: '#eef2ff', color: '#4338ca', Icon: CheckCircle2 },
};

export function PayoutStatusBadge({ status }: { status: BadgeStatus }) {
  const config = BADGE_CONFIG[status];
  const Icon = config.Icon;

  return (
    <Chip
      size="small"
      icon={<Icon size={15} className={status === 'PROCESSING' ? 'animate-spin' : ''} />}
      label={config.label}
      sx={{
        bgcolor: config.background,
        border: '1px solid',
        borderColor: `${config.color}33`,
        color: config.color,
        fontWeight: 700,
        '& .MuiChip-icon': { color: 'inherit' },
      }}
    />
  );
}
