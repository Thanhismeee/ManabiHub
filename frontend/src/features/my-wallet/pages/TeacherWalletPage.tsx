import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
  Link,
} from '@mui/material';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { WalletBalanceCards } from '../components/WalletBalanceCards';
import { WalletTransactionHistory } from '../components/WalletTransactionHistory';
import { WithdrawalHistoryTable } from '../components/WithdrawalHistoryTable';
import { WithdrawalRequestModal } from '../components/WithdrawalRequestModal';
import { EscrowLedgerTable } from '../components/EscrowLedgerTable';
import { useTeacherWallet } from '../hooks/useTeacherWallet';
import { useTeacherWithdrawals } from '../hooks/useTeacherWithdrawals';
import { useTeacherEscrowLedger } from '../hooks/useTeacherEscrowLedger';
import { useTeacherRevenueSummary } from '../hooks/useTeacherRevenueSummary';
import { TeacherRevenueSummary } from '../components/TeacherRevenueSummary';
import { getMyTeacherProfile } from '../../profile/profileApi';

export function TeacherWalletPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const walletQuery = useTeacherWallet();
  const withdrawalsQuery = useTeacherWithdrawals();
  const escrowLedgerQuery = useTeacherEscrowLedger();
  const revenueQuery = useTeacherRevenueSummary();
  const profileQuery = useQuery({
    queryKey: ['teacher-profile'],
    queryFn: getMyTeacherProfile,
  });

  if (walletQuery.isLoading) {
    return (
      <Box>
        <Skeleton variant="text" width={280} height={52} />
        <Skeleton variant="text" width={420} height={26} sx={{ mb: 3 }} />
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          {[0, 1, 2].map((item) => (
            <Skeleton key={item} variant="rounded" height={164} sx={{ flex: 1 }} />
          ))}
        </Stack>
      </Box>
    );
  }

  if (walletQuery.isError || !walletQuery.data) {
    const messageCode = (walletQuery.error as {
      response?: { data?: { messageCode?: string } };
    })?.response?.data?.messageCode;

    if (messageCode === 'WALLET_NOT_FOUND') {
      return (
        <Box>
          <PageHeader
            title="Ví doanh thu"
            breadcrumbs={[
              { label: 'Giảng viên' },
              { label: 'Tài chính' },
            ]}
          />
          <Paper
            elevation={0}
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              px: 3,
              py: { xs: 7, md: 10 },
              textAlign: 'center',
            }}
          >
            <Box
              sx={{
                alignItems: 'center',
                bgcolor: '#fef2f2',
                borderRadius: '50%',
                color: 'primary.main',
                display: 'inline-flex',
                height: 80,
                justifyContent: 'center',
                mb: 2.5,
                width: 80,
              }}
            >
              <AccountBalanceWalletOutlinedIcon sx={{ fontSize: 40 }} />
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>
              Bạn chưa kích hoạt Ví doanh thu
            </Typography>
            <Typography color="text.secondary" sx={{ maxWidth: 520, mx: 'auto', mt: 1 }}>
              Hoàn tất xác minh giáo viên để nhận doanh thu khóa học và tạo yêu cầu rút tiền.
            </Typography>
            <Button
              variant="contained"
              onClick={() => { window.location.href = '/teacher/kyc'; }}
              sx={{ fontWeight: 700, mt: 3, textTransform: 'none' }}
            >
              Kích hoạt ví ngay
            </Button>
          </Paper>
        </Box>
      );
    }

    return (
      <Alert
        severity="error"
        action={(
          <Button color="inherit" onClick={() => void walletQuery.refetch()} sx={{ fontWeight: 700 }}>
            Thử lại
          </Button>
        )}
      >
        Không thể tải thông tin ví doanh thu. Vui lòng kiểm tra kết nối và thử lại.
      </Alert>
    );
  }

  const wallet = walletQuery.data;
  const phoneVerificationPending = profileQuery.isLoading;
  const phoneVerified = profileQuery.data?.phoneVerified === true;
  const phoneProfileUnavailable = profileQuery.isError;
  const phoneVerificationRequired = !phoneVerificationPending
    && !phoneProfileUnavailable
    && !phoneVerified;
  const isWithdrawalDisabled = wallet.walletFrozen
    || wallet.availableBalance < wallet.minimumPayoutAmount
    || phoneVerificationPending
    || phoneProfileUnavailable
    || phoneVerificationRequired;
  const disableReason = wallet.walletFrozen
    ? 'Ví doanh thu đang bị khóa'
    : phoneProfileUnavailable
      ? 'Không tải được trạng thái số điện thoại'
      : phoneVerificationPending
        ? 'Đang tải trạng thái số điện thoại'
        : phoneVerificationRequired
          ? 'Cần xác thực số điện thoại'
          : `Số dư khả dụng phải từ ${formatCurrency(wallet.minimumPayoutAmount)}`;

  return (
    <Box>
      <PageHeader
        title="Ví doanh thu"
        subtitle="Quản lý thu nhập và yêu cầu rút tiền"
        breadcrumbs={[
          { label: 'Giảng viên' },
          { label: 'Tài chính' },
        ]}
        action={(
          <Tooltip title={isWithdrawalDisabled ? disableReason : ''}>
            <span>
              <Button
                variant="contained"
                startIcon={<PaymentsOutlinedIcon />}
                disabled={isWithdrawalDisabled}
                onClick={() => setIsModalOpen(true)}
                sx={{ fontWeight: 700, textTransform: 'none' }}
              >
                Yêu cầu rút tiền
              </Button>
            </span>
          </Tooltip>
        )}
      />

      {wallet.walletFrozen && (
        <Alert severity="error" sx={{ mb: 2.5 }}>
          <strong>Ví đang bị khóa.</strong> Bạn chưa thể tạo yêu cầu rút tiền. Vui lòng liên hệ bộ phận hỗ trợ.
        </Alert>
      )}

      {phoneVerificationRequired && (
        <Alert
          severity="warning"
          sx={{ mb: 2.5 }}
          action={(
            <Button component={RouterLink} to="/teacher/profile" color="inherit" sx={{ fontWeight: 700 }}>
              Xác thực ngay
            </Button>
          )}
        >
          Giáo viên chỉ cần xác thực số điện thoại để rút hoa hồng; luồng này không yêu cầu xác thực CCCD lại.
        </Alert>
      )}

      {phoneProfileUnavailable && (
        <Alert
          severity="error"
          sx={{ mb: 2.5 }}
          action={(
            <Button color="inherit" onClick={() => void profileQuery.refetch()} sx={{ fontWeight: 700 }}>
              Thử lại
            </Button>
          )}
        >
          Không tải được trạng thái số điện thoại. Vui lòng thử lại trước khi rút hoa hồng.
        </Alert>
      )}

      <WalletBalanceCards wallet={wallet} />

      {revenueQuery.isError && (
        <Alert severity="warning" sx={{ mb: 2.5 }}>
          Không thể tải bảng tổng hợp doanh thu theo khóa học. Các số dư ví bên dưới vẫn hoạt động bình thường.
        </Alert>
      )}
      {revenueQuery.data && <TeacherRevenueSummary summary={revenueQuery.data} />}

      <Alert severity="info" sx={{ mb: 2.5 }}>
        Doanh thu được đối soát trong {wallet.clearingPeriodDays} ngày. Mức rút tối thiểu là{' '}
        <strong>{formatCurrency(wallet.minimumPayoutAmount)}</strong>
        {wallet.nextPayoutDate
          ? `; kỳ thanh toán kế tiếp dự kiến ${formatDate(wallet.nextPayoutDate)}.`
          : '.'}{' '}
        <Link
          component={RouterLink}
          to="/help/instructors/escrow-and-payouts"
          sx={{ fontWeight: 600, ml: 0.5 }}
        >
          Xem chi tiết chính sách
        </Link>
      </Alert>

      <WalletTransactionHistory />

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          mb: 4,
          overflow: 'hidden',
        }}
      >
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{
            alignItems: { xs: 'stretch', sm: 'center' },
            borderBottom: '1px solid',
            borderColor: 'divider',
            justifyContent: 'space-between',
            p: { xs: 2, md: 3 },
          }}
        >
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Sổ cái doanh thu
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Chi tiết các khoản doanh thu từ khóa học, phí nền tảng và thời gian đối soát.
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={escrowLedgerQuery.isFetching
              ? <CircularProgress size={16} color="inherit" />
              : <RefreshIcon />}
            disabled={escrowLedgerQuery.isFetching}
            onClick={() => void escrowLedgerQuery.refetch()}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Tải lại
          </Button>
        </Stack>

        {escrowLedgerQuery.isError ? (
          <Alert
            severity="error"
            action={(
              <Button color="inherit" onClick={() => void escrowLedgerQuery.refetch()}>
                Thử lại
              </Button>
            )}
            sx={{ m: 3 }}
          >
            Không thể tải lịch sử doanh thu.
          </Alert>
        ) : escrowLedgerQuery.isLoading ? (
          <Box sx={{ p: 3 }}>
            {[0, 1, 2].map((item) => (
              <Skeleton key={item} height={58} sx={{ mb: 1 }} />
            ))}
          </Box>
        ) : (
          <EscrowLedgerTable items={escrowLedgerQuery.data?.content ?? []} />
        )}
      </Paper>

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          overflow: 'hidden',
        }}
      >
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{
            alignItems: { xs: 'stretch', sm: 'center' },
            borderBottom: '1px solid',
            borderColor: 'divider',
            justifyContent: 'space-between',
            p: { xs: 2, md: 3 },
          }}
        >
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Lịch sử rút tiền
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Theo dõi tiến trình duyệt, quyết toán và khoản tiền đã nhận.
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={withdrawalsQuery.isFetching
              ? <CircularProgress size={16} color="inherit" />
              : <RefreshIcon />}
            disabled={withdrawalsQuery.isFetching}
            onClick={() => void withdrawalsQuery.refetch()}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Tải lại
          </Button>
        </Stack>

        {withdrawalsQuery.isError ? (
          <Alert
            severity="error"
            action={(
              <Button color="inherit" onClick={() => void withdrawalsQuery.refetch()}>
                Thử lại
              </Button>
            )}
            sx={{ m: 3 }}
          >
            Không thể tải lịch sử rút tiền.
          </Alert>
        ) : withdrawalsQuery.isLoading ? (
          <Box sx={{ p: 3 }}>
            {[0, 1, 2].map((item) => (
              <Skeleton key={item} height={58} />
            ))}
          </Box>
        ) : (
          <WithdrawalHistoryTable withdrawals={withdrawalsQuery.data?.content ?? []} />
        )}
      </Paper>

      <WithdrawalRequestModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        wallet={wallet}
      />
    </Box>
  );
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    currency: 'VND',
    maximumFractionDigits: 0,
    style: 'currency',
  }).format(value);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'long' }).format(new Date(value));
}
