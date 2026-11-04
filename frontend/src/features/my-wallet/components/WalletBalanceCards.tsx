import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import HourglassTopOutlinedIcon from '@mui/icons-material/HourglassTopOutlined';
import LockClockOutlinedIcon from '@mui/icons-material/LockClockOutlined';
import { Box, Paper, Stack, Typography } from '@mui/material';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import type { TeacherWallet } from '../types/wallet.types';

interface WalletBalanceCardsProps {
  wallet: TeacherWallet;
}

const METRIC_STYLES = {
  available: { background: '#f0fdf4', color: '#15803d' },
  pending: { background: '#fff7ed', color: '#c2410c' },
  reserved: { background: '#fef2f2', color: '#C41E3A' },
};

export function WalletBalanceCards({ wallet }: WalletBalanceCardsProps) {
  const metrics = [
    {
      caption: 'Sẵn sàng để tạo yêu cầu rút',
      icon: AccountBalanceWalletOutlinedIcon,
      label: 'Số dư khả dụng',
      style: METRIC_STYLES.available,
      value: wallet.availableBalance,
    },
    {
      caption: `Khả dụng sau ${wallet.clearingPeriodDays} ngày đối soát`,
      icon: HourglassTopOutlinedIcon,
      label: 'Doanh thu chờ đối soát',
      style: METRIC_STYLES.pending,
      value: wallet.pendingBalance,
    },
    {
      caption: 'Đã giữ cho yêu cầu đang xử lý',
      icon: LockClockOutlinedIcon,
      label: 'Đang chờ rút',
      style: METRIC_STYLES.reserved,
      value: wallet.reservedBalance ?? 0,
    },
  ];

  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2.5 }}>
      {metrics.map(({ caption, icon: Icon, label, style, value }) => (
        <Paper
          key={label}
          elevation={0}
          sx={{
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2,
            flex: 1,
            p: { xs: 2.5, md: 3 },
          }}
        >
          <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
            <Box
              sx={{
                alignItems: 'center',
                bgcolor: style.background,
                borderRadius: 2,
                color: style.color,
                display: 'flex',
                height: 44,
                justifyContent: 'center',
                width: 44,
              }}
            >
              <Icon />
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                {label}
              </Typography>
              <Typography
                sx={{
                  color: 'text.primary',
                  fontSize: { xs: '1.5rem', lg: '1.75rem' },
                  fontWeight: 800,
                  letterSpacing: '-0.03em',
                  mt: 0.5,
                }}
              >
                {formatCurrency(value)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {caption}
              </Typography>
            </Box>
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}
