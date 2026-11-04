import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import HourglassTopOutlinedIcon from '@mui/icons-material/HourglassTopOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import { Chip } from '@mui/material';
import type { ReactElement } from 'react';
import type { WithdrawalStatus } from '../types/wallet.types';

interface WithdrawalStatusBadgeProps {
  status: WithdrawalStatus;
}

const STATUS_CONFIG: Record<WithdrawalStatus, {
  background: string;
  color: string;
  icon: ReactElement;
  label: string;
}> = {
  PENDING: {
    background: '#fffbeb',
    color: '#b45309',
    icon: <HourglassTopOutlinedIcon />,
    label: 'Chờ duyệt',
  },
  APPROVED: {
    background: '#eff6ff',
    color: '#1d4ed8',
    icon: <CheckCircleOutlineIcon />,
    label: 'Đã duyệt',
  },
  REJECTED: {
    background: '#fef2f2',
    color: '#b91c1c',
    icon: <CancelOutlinedIcon />,
    label: 'Từ chối',
  },
  EXECUTED: {
    background: '#f0fdf4',
    color: '#15803d',
    icon: <PaymentsOutlinedIcon />,
    label: 'Đã chuyển khoản',
  },
  FAILED: {
    background: '#fef2f2',
    color: '#b91c1c',
    icon: <ReportProblemOutlinedIcon />,
    label: 'Thất bại',
  },
  CANCELLED: {
    background: '#f1f5f9',
    color: '#475569',
    icon: <CancelOutlinedIcon />,
    label: 'Đã hủy',
  },
};

export function WithdrawalStatusBadge({ status }: WithdrawalStatusBadgeProps) {
  const config = STATUS_CONFIG[status];

  return (
    <Chip
      size="small"
      icon={config.icon}
      label={config.label}
      sx={{
        bgcolor: config.background,
        border: '1px solid',
        borderColor: `${config.color}33`,
        color: config.color,
        fontWeight: 700,
        '& .MuiChip-icon': { color: 'inherit', fontSize: 16 },
      }}
    />
  );
}
