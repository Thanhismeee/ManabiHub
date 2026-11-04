import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Pagination,
  Stack,
  Tab,
  Tabs,
  Typography,
  Tooltip,
} from '@mui/material';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { DecorativeKanjiWatermark } from '../../../shared/components/DecorativeKanjiWatermark/DecorativeKanjiWatermark';
import { getAuthSession, subscribeToAuthSessionChanges } from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import { ROUTES } from '../../../shared/constants/routes';
import type { OrderItemResponse, OrderResponse } from '../../checkout/types';
import { RefundRequestDialog } from '../../refunds/components/RefundRequestDialog';
import { StudentRefundHistory } from '../../refunds/components/StudentRefundHistory';
import { useStudentRefunds } from '../../refunds/hooks/useStudentRefunds';
import type { StudentRefundResponse } from '../../refunds/types';
import { useOrderHistory } from '../hooks/useOrderHistory';
import type { OrderStatus } from '../services/orderHistoryService';
import { getStudentWallet } from '../../wallet/services/studentWalletService';
import type { StudentWalletResponse } from '../../wallet/types';
import { useCommercialPolicy } from '../../help-center/hooks/useCommercialPolicy';
import { getStudentIdentityVerificationStatus } from '../../wallet/services/studentIdentityVerificationService';
import { walletService } from '../../my-wallet/services/walletService';
import { WithdrawalHistoryTable } from '../../my-wallet/components/WithdrawalHistoryTable';
import type { TeacherWallet, WithdrawalRequest } from '../../my-wallet/types/wallet.types';

interface FilterOption {
  label: string;
  status?: OrderStatus;
}

const FILTERS: FilterOption[] = [
  { label: 'Tất cả' },
  { label: 'Thành công', status: 'PAID' },
  { label: 'Chờ thanh toán', status: 'PENDING' },
  { label: 'Thất bại', status: 'FAILED' },
  { label: 'Đã hủy', status: 'CANCELLED' },
  { label: 'Đã hoàn tiền', status: 'REFUNDED' },
];

const ORDER_STATUS_CONFIG: Record<
  OrderStatus,
  { label: string; bgcolor: string; color: string }
> = {
  PAID: { label: 'Đã thanh toán', bgcolor: '#ECFDF5', color: '#047857' },
  REFUNDED: { label: 'Đã hoàn tiền', bgcolor: '#EFF6FF', color: '#1D4ED8' },
  PENDING: { label: 'Chờ thanh toán', bgcolor: '#FFFBEB', color: '#B45309' },
  FAILED: { label: 'Thất bại', bgcolor: '#FEF2F2', color: '#B91C1C' },
  CANCELLED: { label: 'Đã hủy', bgcolor: '#F3F4F6', color: '#4B5563' },
};

const REFUND_ACTION_BUTTON_STYLE = {
  borderColor: '#C41E3A',
  color: '#C41E3A',
  borderRadius: 2,
  fontWeight: 700,
  fontSize: '0.85rem',
  textTransform: 'none',
  px: 2,
  py: 0.4,
  lineHeight: 1.5,
  whiteSpace: 'nowrap',
  transition: 'all 0.15s ease',
  '&:hover': {
    borderColor: '#9D182E',
    bgcolor: '#FEF2F2',
    color: '#9D182E',
  },
};

function formatMoney(amount: number, currency: string = 'VND') {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

export function StudentPaymentsPage() {
  const navigate = useNavigate();
  const [session, setSession] = useState(() => getAuthSession('public'));
  const isTeacher = session?.roles.includes(ROLES.TEACHER) ?? false;

  const [mainTab, setMainTab] = useState(0); // 0 = Lịch sử đơn hàng, 1 = Lịch sử rút hoa hồng

  const [filterIndex, setFilterIndex] = useState(0);
  const [page, setPage] = useState(0);
  const [refundPage, setRefundPage] = useState(0);
  const [dialogItem, setDialogItem] = useState<OrderItemResponse | null>(null);
  const [dialogRefund, setDialogRefund] = useState<StudentRefundResponse | null>(null);

  // Wallet state
  const [wallet, setWallet] = useState<StudentWalletResponse | null>(null);
  const [teacherWallet, setTeacherWallet] = useState<TeacherWallet | null>(null);
  const [loadingWallet, setLoadingWallet] = useState(true);
  const [loadingTeacherWallet, setLoadingTeacherWallet] = useState(false);
  const [identityVerified, setIdentityVerified] = useState(false);

  // Withdrawals history state
  const [withdrawals, setWithdrawals] = useState<WithdrawalRequest[]>([]);
  const [loadingWithdrawals, setLoadingWithdrawals] = useState(true);
  const [errorWithdrawals, setErrorWithdrawals] = useState(false);

  const policyQuery = useCommercialPolicy();
  const minimumAmount = policyQuery.data?.payoutThreshold ?? 100000;

  const selectedFilter = FILTERS[filterIndex];
  const ordersQuery = useOrderHistory({ page, size: 10, status: selectedFilter.status });
  const refundsQuery = useStudentRefunds(refundPage, 10);

  // Filter out WALLET_TOPUP transactions from student order history
  const rawOrders = ordersQuery.data?.content ?? [];
  const courseOrders = rawOrders.filter((order) => order.type !== 'WALLET_TOPUP');
  const refunds = refundsQuery.data?.content ?? [];

  // Listen to auth session changes
  useEffect(() => {
    const unsubscribe = subscribeToAuthSessionChanges(() => {
      setSession(getAuthSession('public'));
    });
    return () => {
      unsubscribe();
    };
  }, []);

  // Force tab to order history if user is no longer a teacher
  useEffect(() => {
    if (!isTeacher && mainTab !== 0) {
      setMainTab(0);
    }
  }, [isTeacher, mainTab]);

  const loadWalletData = useCallback(async () => {
    setLoadingWallet(true);
    setLoadingTeacherWallet(isTeacher);
    try {
      const studentWalletPromise = getStudentWallet();
      const identityPromise = isTeacher
        ? getStudentIdentityVerificationStatus()
        : Promise.resolve(null);
      const [studentWallet, identity] = await Promise.all([
        studentWalletPromise,
        identityPromise,
      ]);
      setWallet(studentWallet);
      if (identity) {
        setIdentityVerified(identity.verified);
      }
    } catch {
      // A transient wallet failure must not break the payment history.
    } finally {
      setLoadingWallet(false);
    }

    if (!isTeacher) {
      setTeacherWallet(null);
      setLoadingTeacherWallet(false);
      return;
    }

    try {
      const response = await walletService.getTeacherWallet();
      setTeacherWallet(response.data);
    } catch {
      // The teacher wallet page owns the detailed activation/error state.
      setTeacherWallet(null);
    } finally {
      setLoadingTeacherWallet(false);
    }
  }, [isTeacher]);

  const loadWithdrawalData = useCallback(async () => {
    if (!isTeacher) {
      setWithdrawals([]);
      setLoadingWithdrawals(false);
      return;
    }
    setLoadingWithdrawals(true);
    setErrorWithdrawals(false);
    try {
      const response = await walletService.getTeacherWithdrawals({ page: 0, size: 20, sort: 'requestedAt,desc' });
      setWithdrawals(response.data?.content ?? []);
    } catch {
      setErrorWithdrawals(true);
    } finally {
      setLoadingWithdrawals(false);
    }
  }, [isTeacher]);

  useEffect(() => {
    void loadWalletData();
    void loadWithdrawalData();
  }, [loadWalletData, loadWithdrawalData]);

  const approvedRefunds = refunds.filter((refund) => refund.status === 'APPROVED')
    .map((refund) => refund.id).sort().join(',');
  useEffect(() => {
    if (approvedRefunds) void loadWalletData();
  }, [approvedRefunds, loadWalletData]);

  const latestRefundByItem = new Map<string, StudentRefundResponse>();
  refunds.forEach((refund) => {
    if (!latestRefundByItem.has(refund.orderItemId)) {
      latestRefundByItem.set(refund.orderItemId, refund);
    }
  });

  const openNewRefund = (item: OrderItemResponse) => {
    setDialogItem(item);
    setDialogRefund(null);
  };

  const openRefundDetail = (refund: StudentRefundResponse) => {
    const item = courseOrders.flatMap((order) => order.items)
      .find((candidate) => candidate.id === refund.orderItemId) ?? {
        id: refund.orderItemId,
        courseId: refund.courseId,
        courseTitle: refund.courseTitle,
        price: refund.eligibilitySnapshot.actuallyPaidAmount,
      };
    setDialogItem(item);
    setDialogRefund(refund);
  };

  const currency = wallet?.currency ?? 'VND';
  const studentPurchaseBalance = wallet?.availableBalance ?? 0;
  const commissionAvailableBalance = teacherWallet?.availableBalance ?? 0;
  const commissionPendingBalance = teacherWallet?.pendingBalance ?? 0;
  const withdrawalLoading = loadingWallet || loadingTeacherWallet;

  return (
    <Box component="main" sx={{ minHeight: '100%', bgcolor: '#FAF9F6', px: { xs: 2, md: 4 }, py: { xs: 3, md: 5 } }}>
      <Box sx={{ maxWidth: 1180, mx: 'auto', position: 'relative', overflow: 'hidden' }}>
        <DecorativeKanjiWatermark text="履歴" />
        <Box sx={{ position: 'relative', zIndex: 1 }}>
        <PageHeader
          title="Ví & Thanh toán"
          subtitle="Thanh toán & Số dư ví"
          breadcrumbs={[{ label: 'Học viên' }, { label: 'Ví & Thanh toán' }]}
        />

        {/* Keep the student purchase wallet separate from the teacher revenue wallet. */}
        <Box
          sx={{
            display: isTeacher ? 'grid' : 'block',
            ...(isTeacher && {
              gap: 2,
              gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1fr) minmax(0, 1.25fr)' },
            }),
            mb: 2.5,
          }}
        >
          <Box
            sx={{
              bgcolor: '#C41E3A',
              background: 'linear-gradient(135deg, #C41E3A 0%, #9D182E 100%)',
              borderRadius: 3,
              boxShadow: '0 10px 24px rgba(157, 24, 46, 0.22)',
              color: '#fff',
              minHeight: { md: 242 },
              overflow: 'hidden',
              p: { xs: 2.5, sm: 3 },
              position: 'relative',
            }}
          >
            <AccountBalanceWalletIcon
              aria-hidden="true"
              sx={{
                bottom: -24,
                fontSize: 180,
                opacity: 0.12,
                position: 'absolute',
                right: -18,
                transform: 'rotate(-10deg)',
              }}
            />
            <Stack spacing={2.25} sx={{ position: 'relative', zIndex: 1 }}>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <Box sx={{ bgcolor: 'rgba(255,255,255,0.16)', borderRadius: 2, display: 'grid', p: 1.1, placeItems: 'center' }}>
                  <AccountBalanceWalletIcon />
                </Box>
                <Box>
                  <Typography sx={{ fontWeight: 900 }}>Ví học viên</Typography>
                  <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.78)' }}>
                    Tiền nạp, hoàn tiền và thưởng học tập
                  </Typography>
                </Box>
              </Stack>
              <Box>
                <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.78)', mb: 0.5 }}>
                  Số dư mua khóa học
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 900 }}>
                  {loadingWallet ? '…' : formatMoney(studentPurchaseBalance, currency)}
                </Typography>
              </Box>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.72)' }}>
                Tiền nạp, hoàn tiền và thưởng học tập được cộng vào ví học viên sau khi hệ thống quyết toán.
              </Typography>
            </Stack>
          </Box>

          {isTeacher && (
            <Box
              sx={{
                bgcolor: '#fff',
                border: '1px solid #E1E5EA',
                borderRadius: 3,
                boxShadow: '0 4px 16px rgba(15, 23, 42, 0.05)',
                p: { xs: 2.5, sm: 3 },
              }}
            >
              <Stack spacing={2} sx={{ height: '100%', justifyContent: 'center' }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                  <Box sx={{ bgcolor: '#FFF1F2', borderRadius: 2, color: '#C41E3A', display: 'grid', p: 1.1, placeItems: 'center' }}>
                    <AccountBalanceIcon />
                  </Box>
                  <Box>
                    <Typography sx={{ fontWeight: 900 }}>Ví doanh thu giảng viên</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Hoa hồng khóa học được đối soát và quản lý tách biệt với tiền mua khóa học.
                    </Typography>
                  </Box>
                </Stack>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                  <Box sx={{ flex: 1, bgcolor: '#F8FAFC', borderRadius: 2, p: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Hoa hồng có thể rút</Typography>
                    <Typography sx={{ fontWeight: 900 }}>
                      {loadingTeacherWallet ? '…' : formatMoney(commissionAvailableBalance, currency)}
                    </Typography>
                  </Box>
                  <Box sx={{ flex: 1, bgcolor: '#F8FAFC', borderRadius: 2, p: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Hoa hồng đang đối soát</Typography>
                    <Typography sx={{ fontWeight: 900 }}>
                      {loadingTeacherWallet ? '…' : formatMoney(commissionPendingBalance, currency)}
                    </Typography>
                  </Box>
                </Stack>
                <Alert severity="info" sx={{ borderRadius: 2 }}>
                  Số dư hoàn tiền chỉ dùng để mua khóa học. Chỉ hoa hồng đã đối soát trong Ví doanh thu mới được rút; tài khoản cần xác thực số điện thoại và CCCD.
                </Alert>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Tối thiểu {formatMoney(minimumAmount, currency)}
                  </Typography>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ width: { xs: '100%', sm: 'auto' } }}>
                    <Button
                      variant="outlined"
                      disabled={withdrawalLoading || identityVerified}
                      onClick={() => navigate(ROUTES.STUDENT.IDENTITY_VERIFICATION)}
                      sx={{ borderColor: '#C41E3A', color: '#C41E3A', borderRadius: 10, fontWeight: 800, px: 2, textTransform: 'none' }}
                    >
                      {identityVerified ? 'Đã xác thực CCCD thành công' : 'Xác thực CCCD'}
                    </Button>
                    <Button
                      variant="contained"
                      disabled={withdrawalLoading}
                      onClick={() => {
                        if (!identityVerified) {
                          navigate(ROUTES.STUDENT.IDENTITY_VERIFICATION);
                          return;
                        }
                        navigate(ROUTES.TEACHER.WALLET);
                      }}
                      sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#9D182E' }, borderRadius: 10, fontWeight: 800, px: 3, textTransform: 'none' }}
                    >
                      {!identityVerified
                        ? 'Xác minh SĐT & CCCD để rút hoa hồng'
                        : 'Mở Ví doanh thu để rút hoa hồng'}
                    </Button>
                  </Stack>
                </Stack>
              </Stack>
            </Box>
          )}
        </Box>

        {/* Sub-tabs Navigation - only shown if user has TEACHER role */}
        {isTeacher && (
          <Box sx={{ mb: 2.5, borderBottom: 1, borderColor: 'divider' }}>
            <Tabs
              value={mainTab}
              onChange={(_, newValue) => setMainTab(newValue)}
              sx={{
                '& .MuiTab-root': {
                  fontWeight: 700,
                  fontSize: '0.95rem',
                  textTransform: 'none',
                  minHeight: 48,
                  px: 3,
                  minWidth: 160,
                },
                '& .Mui-selected': {
                  color: '#C41E3A !important',
                },
                '& .MuiTabs-indicator': {
                  backgroundColor: '#C41E3A',
                  height: 3,
                  borderRadius: '3px 3px 0 0',
                },
              }}
            >
              <Tab value={0} icon={<ShoppingBagOutlinedIcon fontSize="small" />} iconPosition="start" label="Lịch sử đơn hàng" />
              <Tab value={1} icon={<AccountBalanceIcon fontSize="small" />} iconPosition="start" label="Lịch sử rút hoa hồng" />
            </Tabs>
          </Box>
        )}

        {/* SUBTAB 1: Lịch sử đơn hàng & Hoàn tiền (always visible for non-teacher, or when mainTab === 0 for teacher) */}
        {(!isTeacher || mainTab === 0) && (
          <>
            {/* Filter Pills Header (Directly on page background) */}
            <Box sx={{ mb: 2.5 }}>
              <Stack
                direction="row"
                spacing={1.5}
                sx={{
                  overflowX: 'auto',
                  py: 0.5,
                  '&::-webkit-scrollbar': { display: 'none' },
                  scrollbarWidth: 'none',
                  msOverflowStyle: 'none',
                }}
              >
                {FILTERS.map((filter, index) => {
                  const isActive = filterIndex === index;
                  return (
                    <Button
                      key={filter.label}
                      variant="text"
                      aria-pressed={isActive}
                      onClick={() => { setFilterIndex(index); setPage(0); }}
                      sx={{
                        minWidth: 'max-content',
                        borderRadius: 10,
                        px: 2.5,
                        py: 0.75,
                        fontWeight: isActive ? 800 : 600,
                        fontSize: '0.875rem',
                        textTransform: 'none',
                        bgcolor: isActive ? '#C41E3A' : '#FFFFFF',
                        color: isActive ? '#FFFFFF' : '#475569',
                        border: isActive ? '1px solid #C41E3A' : '1px solid #E2E8F0',
                        boxShadow: isActive ? '0 2px 6px rgba(196, 30, 58, 0.2)' : '0 1px 2px rgba(0,0,0,0.02)',
                        transition: 'all 0.15s ease',
                        '&:hover': {
                          bgcolor: isActive ? '#9D182E' : '#F8FAFC',
                          borderColor: isActive ? '#9D182E' : '#CBD5E1',
                          color: isActive ? '#FFFFFF' : '#0F172A',
                        },
                      }}
                    >
                      {filter.label}
                    </Button>
                  );
                })}
              </Stack>
            </Box>

            {ordersQuery.isError && (
              <Alert severity="error" sx={{ mb: 2 }} action={<Button onClick={() => void ordersQuery.refetch()}>Thử lại</Button>}>
                Không thể tải lịch sử thanh toán.
              </Alert>
            )}

            {(ordersQuery.isLoading || ordersQuery.isFetching) && (
              <Stack spacing={1.5} sx={{ minHeight: 260, alignItems: 'center', justifyContent: 'center', bgcolor: '#FFF', borderRadius: 3, border: '1px solid #E1E5EA' }}>
                <CircularProgress aria-label="Đang tải lịch sử thanh toán" sx={{ color: '#C41E3A' }} />
                <Typography color="text.secondary">Đang tải giao dịch…</Typography>
              </Stack>
            )}

            {!ordersQuery.isLoading && !ordersQuery.isFetching && !ordersQuery.isError && courseOrders.length === 0 && (
              <Stack spacing={1.5} sx={{ minHeight: 280, alignItems: 'center', justifyContent: 'center', p: 3, textAlign: 'center', bgcolor: '#FFF', borderRadius: 3, border: '1px solid #E1E5EA' }}>
                <ReceiptLongOutlinedIcon sx={{ fontSize: 56, color: 'text.disabled' }} />
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Chưa có đơn hàng nào</Typography>
                <Button
                  variant="contained"
                  onClick={() => navigate(ROUTES.STUDENT.BROWSE_COURSES)}
                  sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#9D182E' }, borderRadius: 10, fontWeight: 700, textTransform: 'none', px: 3 }}
                >
                  Khám phá khóa học
                </Button>
              </Stack>
            )}

            {/* Standalone elevated cards with clean spacing */}
            {!ordersQuery.isFetching && <Stack spacing={2}>
              {courseOrders.map((order) => (
                <Box
                  key={order.id}
                  sx={{
                    bgcolor: '#FFFFFF',
                    borderRadius: 3,
                    border: '1px solid #E2E8F0',
                    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.03)',
                    transition: 'all 0.2s ease-in-out',
                    '&:hover': {
                      boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
                      borderColor: '#CBD5E1',
                    },
                  }}
                >
                  <OrderCard
                    order={order}
                    latestRefundByItem={latestRefundByItem}
                    onRequestRefund={openNewRefund}
                    onOpenRefund={openRefundDetail}
                  />
                </Box>
              ))}
            </Stack>}

            {/* Polished Pagination */}
            {(ordersQuery.data?.totalPages ?? 0) > 1 && (
              <Box sx={{ mt: 3, display: 'flex', justifyContent: 'center' }}>
                <Pagination
                  count={ordersQuery.data?.totalPages ?? 1}
                  page={page + 1}
                  onChange={(_, value) => setPage(value - 1)}
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

            {/* Lịch sử hoàn tiền */}
            <Box sx={{ mt: 4 }}>
              <Stack spacing={0.5} sx={{ mb: 1.5 }}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Lịch sử hoàn tiền</Typography>
                <Typography variant="body2" color="text.secondary">
                  Các yêu cầu hoàn tiền được tách khỏi lịch sử đơn hàng để dễ theo dõi trạng thái xử lý.
                </Typography>
              </Stack>
              <StudentRefundHistory
                refunds={refunds}
                loading={refundsQuery.isLoading}
                error={refundsQuery.isError}
                onRetry={() => void refundsQuery.refetch()}
                onOpen={openRefundDetail}
                page={refundPage}
                totalPages={refundsQuery.data?.totalPages ?? 1}
                onPageChange={setRefundPage}
              />
            </Box>
          </>
        )}

        {/* SUBTAB 2: Lịch sử rút hoa hồng (only rendered if user has TEACHER role and selected tab 1) */}
        {isTeacher && mainTab === 1 && (
          <Box
            sx={{
              p: { xs: 2, md: 2.5 },
              bgcolor: '#FFFFFF',
              borderRadius: 3,
              border: '1px solid #E1E5EA',
              boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
            }}
          >
            {loadingWithdrawals ? (
              <Stack spacing={1.5} sx={{ py: 6, alignItems: 'center' }}>
                <CircularProgress aria-label="Đang tải lịch sử rút hoa hồng" sx={{ color: '#C41E3A' }} />
                <Typography color="text.secondary">Đang tải lịch sử rút hoa hồng…</Typography>
              </Stack>
            ) : errorWithdrawals ? (
              <Alert severity="error" action={<Button onClick={() => void loadWithdrawalData()}>Thử lại</Button>}>
                Không thể tải lịch sử rút hoa hồng.
              </Alert>
            ) : (
              <WithdrawalHistoryTable withdrawals={withdrawals} />
            )}
          </Box>
        )}
        </Box>
      </Box>

      {/* Refund request dialog */}
      <RefundRequestDialog
        open={dialogItem !== null}
        orderItem={dialogItem}
        existingRefund={dialogRefund}
        onClose={() => { setDialogItem(null); setDialogRefund(null); }}
      />

    </Box>
  );
}

function OrderCard({
  order,
  latestRefundByItem,
  onRequestRefund,
  onOpenRefund,
}: {
  order: OrderResponse;
  latestRefundByItem: Map<string, StudentRefundResponse>;
  onRequestRefund: (item: OrderItemResponse) => void;
  onOpenRefund: (refund: StudentRefundResponse) => void;
}) {
  const [copied, setCopied] = useState(false);
  const presentation = ORDER_STATUS_CONFIG[order.status] ?? {
    label: order.status,
    bgcolor: '#F3F4F6',
    color: '#4B5563',
  };

  return (
    <Box sx={{ p: { xs: 2.5, md: 3 } }}>
      {/* Header: Secondary Order code & Date on left, Soft Status Badge aligned to right edge */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 2 }}>
        <Box>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', minWidth: 0 }}>
            <Tooltip title={`Mã đơn đầy đủ: ${order.orderCode}`}>
              <Typography
                component="span"
                sx={{
                  maxWidth: { xs: 180, sm: 280 },
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  fontWeight: 600,
                  color: '#64748B',
                  fontSize: '0.85rem',
                  fontFamily: 'monospace',
                  letterSpacing: '0.2px',
                }}
              >
                {order.orderCode}
              </Typography>
            </Tooltip>
            <Tooltip title={copied ? 'Đã sao chép' : 'Sao chép mã đơn hàng'}>
              <IconButton
                size="small"
                aria-label="Sao chép mã đơn hàng"
                onClick={() => {
                  if (navigator.clipboard?.writeText) {
                    void navigator.clipboard.writeText(order.orderCode);
                  }
                  setCopied(true);
                  window.setTimeout(() => setCopied(false), 1500);
                }}
                sx={{ color: copied ? '#047857' : '#64748B', p: 0.35 }}
              >
                <ContentCopyOutlinedIcon sx={{ fontSize: 15 }} />
              </IconButton>
            </Tooltip>
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
            Ngày mua: {new Date(order.createdAt).toLocaleDateString('vi-VN')}
          </Typography>
        </Box>
        <Chip
          label={presentation.label}
          size="small"
          sx={{
            fontWeight: 700,
            fontSize: '0.75rem',
            bgcolor: presentation.bgcolor,
            color: presentation.color,
            border: 'none',
            px: 0.75,
            alignSelf: { xs: 'flex-start', sm: 'center' },
            mr: 0,
          }}
        />
      </Stack>

      {/* Items: Clear hierarchy - Course title is primary focus */}
      <Stack spacing={1.5}>
        {order.items.map((item, index) => {
          const refund = latestRefundByItem.get(item.id);

          const isRejected = refund?.status === 'REJECTED';
          const isPendingOrActive = refund && ['PENDING', 'PROCESSING', 'RECONCILIATION_REQUIRED', 'APPROVED', 'PROCESSED', 'COMPLETED'].includes(refund.status);

          return (
            <Box
              key={item.id}
              sx={{
                pt: index > 0 ? 1.5 : 0,
                borderTop: index > 0 ? '1px solid #F1F5F9' : 'none',
              }}
            >
              {/* Primary focus: Course Title */}
              <Typography sx={{ fontWeight: 800, fontSize: '1rem', color: '#0F172A', mb: 0.75 }}>
                {item.courseTitle}
              </Typography>

              {/* Bottom row: Price on Left, Unified Action Buttons on Right */}
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                sx={{
                  justifyContent: 'space-between',
                  alignItems: { sm: 'center' },
                }}
              >
                <Typography variant="body2" sx={{ fontWeight: 700, color: '#475569', fontSize: '0.9rem' }}>
                  {formatMoney(item.price, order.currency)}
                </Typography>

                <Box sx={{ alignSelf: { xs: 'flex-start', sm: 'center' }, display: 'flex', justifyContent: 'flex-end' }}>
                  {isPendingOrActive ? (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => onOpenRefund(refund)}
                      sx={REFUND_ACTION_BUTTON_STYLE}
                    >
                      Xem chi tiết hoàn tiền
                    </Button>
                  ) : order.status === 'PAID' && item.price > 0 ? (
                    isRejected ? (
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => onRequestRefund(item)}
                        sx={REFUND_ACTION_BUTTON_STYLE}
                      >
                        Yêu cầu hoàn tiền lại
                      </Button>
                    ) : (
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => onRequestRefund(item)}
                        sx={REFUND_ACTION_BUTTON_STYLE}
                      >
                        Yêu cầu hoàn tiền
                      </Button>
                    )
                  ) : order.status === 'PENDING' ? (
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontStyle: 'italic', fontWeight: 500 }}>
                      Đơn hàng đang chờ thanh toán VNPay
                    </Typography>
                  ) : order.status === 'FAILED' ? (
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontStyle: 'italic', fontWeight: 500 }}>
                      Thanh toán không thành công
                    </Typography>
                  ) : order.status === 'CANCELLED' ? (
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontStyle: 'italic', fontWeight: 500 }}>
                      Thanh toán đã được hủy
                    </Typography>
                  ) : null}
                </Box>
              </Stack>
            </Box>
          );
        })}
      </Stack>
    </Box>
  );
}
