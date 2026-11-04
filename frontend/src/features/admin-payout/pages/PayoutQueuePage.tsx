import FilterListIcon from '@mui/icons-material/FilterList';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  InputAdornment,
  Grid,
  MenuItem,
  Pagination,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { PayoutStatusBadge } from '../components/PayoutStatusBadge';
import { usePayoutQueue } from '../hooks/usePayoutQueue';
import { getPayoutErrorMessage } from '../services/payoutError';
import type {
  PayoutQueueParams,
  PayoutStatus,
  ReconciliationStatus,
  WithdrawalStatus,
} from '../types/payout.types';
import {
  formatFinanceDateTime,
  formatMoney,
  toExclusiveLocalDateTimeRange,
  waitingCalendarDays,
} from '../../admin-finance/financeDisplay';

const PAGE_SIZE = 10;

interface FilterValues {
  payoutId: string;
  walletId: string;
  teacherKeyword: string;
  status: '' | WithdrawalStatus;
  settlementStatus: '' | PayoutStatus;
  reconciliationStatus: '' | ReconciliationStatus;
  provider: string;
  providerReference: string;
  minAmount: string;
  maxAmount: string;
  requestedFrom: string;
  requestedTo: string;
}

const EMPTY_FILTERS: FilterValues = {
  payoutId: '',
  walletId: '',
  reconciliationStatus: '',
  requestedFrom: '',
  requestedTo: '',
  status: '',
  settlementStatus: '',
  teacherKeyword: '',
  provider: '',
  providerReference: '',
  minAmount: '',
  maxAmount: '',
};

export function PayoutQueuePage() {
  const [page, setPage] = useState(0);
  const [draftFilters, setDraftFilters] = useState<FilterValues>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<FilterValues>(EMPTY_FILTERS);
  const params = useMemo<PayoutQueueParams>(() => ({
    page,
    size: PAGE_SIZE,
    sort: 'requestedAt,desc',
    ...(filters.payoutId.trim() && { payoutId: filters.payoutId.trim() }),
    ...(filters.walletId.trim() && { walletId: filters.walletId.trim() }),
    ...(filters.teacherKeyword.trim() && { teacherKeyword: filters.teacherKeyword.trim() }),
    ...(filters.status && { status: filters.status }),
    ...(filters.settlementStatus && { settlementStatus: filters.settlementStatus }),
    ...(filters.reconciliationStatus && {
      reconciliationStatus: filters.reconciliationStatus,
    }),
    ...(filters.provider.trim() && { provider: filters.provider.trim() }),
    ...(filters.providerReference.trim() && { providerReference: filters.providerReference.trim() }),
    ...(filters.minAmount && { minAmount: filters.minAmount }),
    ...(filters.maxAmount && { maxAmount: filters.maxAmount }),
    ...(filters.requestedFrom && { requestedFrom: toExclusiveLocalDateTimeRange(filters.requestedFrom, filters.requestedFrom).from }),
    ...(filters.requestedTo && { requestedTo: toExclusiveLocalDateTimeRange(filters.requestedTo, filters.requestedTo).to }),
  }), [filters, page]);
  const queue = usePayoutQueue(params);
  const activeFilterCount = Object.values(filters).filter(Boolean).length;
  const invalidDateRange = Boolean(
    draftFilters.requestedFrom
      && draftFilters.requestedTo
      && draftFilters.requestedFrom > draftFilters.requestedTo,
  );
  const invalidAmountRange = Boolean(
    draftFilters.minAmount
      && draftFilters.maxAmount
      && Number(draftFilters.minAmount) > Number(draftFilters.maxAmount),
  );
  const pageAmount = useMemo(() => queue.data?.content.reduce((sum, item) => sum + Number(item.requestedAmount || 0), 0) ?? null, [queue.data?.content]);
  const oldestWait = useMemo(() => queue.data?.content.reduce<number | null>((oldest, item) => {
    const days = waitingCalendarDays(item.requestedAt);
    return days === null ? oldest : Math.max(oldest ?? 0, days);
  }, null) ?? null, [queue.data?.content]);

  const applyFilters = () => {
    setPage(0);
    setFilters(draftFilters);
  };

  const clearFilters = () => {
    setPage(0);
    setDraftFilters(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  };

  return (
    <Box>
      <PageHeader
        title="Chi trả cho giáo viên và học viên"
        subtitle="Duyệt yêu cầu rút tiền, theo dõi provider và thực hiện đối soát chi trả"
        breadcrumbs={[
          { label: 'Tài chính' },
          { label: 'Chi trả' },
        ]}
      />

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><PayoutMetric loading={queue.isLoading} label="Tổng yêu cầu" value={queue.data ? queue.data.totalElements.toLocaleString('vi-VN') : null} helper="Toàn bộ kết quả khớp bộ lọc" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><PayoutMetric loading={queue.isLoading} label="Tiền yêu cầu" value={pageAmount === null ? null : formatMoney(pageAmount)} helper="Chỉ tổng trang hiện tại; API chưa có tổng toàn hàng đợi" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><PayoutMetric loading={queue.isLoading} label="Yêu cầu lâu nhất" value={oldestWait === null ? null : `${oldestWait} ngày`} helper="Tính theo lịch Việt Nam trên trang hiện tại" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><PayoutMetric loading={false} label="SLA / phí chi trả" value={null} helper="Backend chưa cung cấp SLA và fee cho queue" /></Grid>
      </Grid>

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
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <PaymentsOutlinedIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Hàng đợi chi trả
              </Typography>
              {!queue.isLoading && (
                <Chip
                  size="small"
                  label={queue.data ? `${queue.data.totalElements} yêu cầu` : 'Chưa đồng bộ'}
                  sx={{ bgcolor: '#fef2f2', color: 'primary.main', fontWeight: 700 }}
                />
              )}
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Kiểm tra đối soát trước khi phê duyệt, thử lại hoặc ghi nhận chuyển khoản.
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={queue.isFetching
              ? <CircularProgress size={16} color="inherit" />
              : <RefreshIcon />}
            disabled={queue.isFetching}
            onClick={() => void queue.refetch()}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Tải lại
          </Button>
        </Stack>

        <Box sx={{ borderBottom: '1px solid', borderColor: 'divider', p: { xs: 2, md: 3 } }}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
              <TextField
                fullWidth
                label="Mã yêu cầu chi trả"
                placeholder="UUID"
                size="small"
                value={draftFilters.payoutId}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  payoutId: event.target.value,
                }))}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
              <TextField
                fullWidth
                label="Mã ví"
                placeholder="UUID"
                size="small"
                value={draftFilters.walletId}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  walletId: event.target.value,
                }))}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <TextField
              fullWidth
              label="Tìm chủ ví"
              placeholder="Tên hoặc email giáo viên/học viên"
              size="small"
              value={draftFilters.teacherKeyword}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                teacherKeyword: event.target.value,
              }))}
              onKeyDown={(event) => {
                if (event.key === 'Enter') applyFilters();
              }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
            />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.5 }}>
            <TextField
              fullWidth
              select
              label="Trạng thái yêu cầu"
              size="small"
              value={draftFilters.status}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                status: event.target.value as FilterValues['status'],
              }))}
            >
              <MenuItem value="">Tất cả trạng thái</MenuItem>
              <MenuItem value="PENDING">Chờ xử lý</MenuItem>
              <MenuItem value="APPROVED">Đã duyệt</MenuItem>
              <MenuItem value="FAILED">Thất bại</MenuItem>
              <MenuItem value="EXECUTED">Đã thanh toán</MenuItem>
              <MenuItem value="REJECTED">Đã từ chối</MenuItem>
              <MenuItem value="CANCELLED">Đã hủy</MenuItem>
            </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.5 }}>
              <TextField
                fullWidth
                select
                label="Trạng thái provider"
                size="small"
                value={draftFilters.settlementStatus}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  settlementStatus: event.target.value as FilterValues['settlementStatus'],
                }))}
              >
                <MenuItem value="">Tất cả trạng thái</MenuItem>
                <MenuItem value="PROCESSING">Đang xử lý</MenuItem>
                <MenuItem value="SUCCEEDED">Thành công</MenuItem>
                <MenuItem value="FAILED">Thất bại</MenuItem>
                <MenuItem value="PENDING_RETRY">Chờ thử lại</MenuItem>
                <MenuItem value="REJECTED">Đã từ chối</MenuItem>
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.5 }}>
            <TextField
              fullWidth
              select
              label="Đối soát"
              size="small"
              value={draftFilters.reconciliationStatus}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                reconciliationStatus: event.target.value as FilterValues['reconciliationStatus'],
              }))}
            >
              <MenuItem value="">Tất cả đối soát</MenuItem>
              <MenuItem value="MATCHED">Khớp</MenuItem>
              <MenuItem value="WARNING">Có cảnh báo</MenuItem>
              <MenuItem value="CRITICAL_MISMATCH">Sai lệch nghiêm trọng</MenuItem>
              <MenuItem value="RESOLVED">Đã xử lý</MenuItem>
            </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.5 }}>
              <TextField
                fullWidth
                label="Provider"
                placeholder="Tên kênh chi trả"
                size="small"
                value={draftFilters.provider}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  provider: event.target.value,
                }))}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.5 }}>
              <TextField
                fullWidth
                label="Mã giao dịch provider"
                size="small"
                value={draftFilters.providerReference}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  providerReference: event.target.value,
                }))}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.25 }}>
              <TextField
                fullWidth
                type="number"
                label="Số tiền từ"
                size="small"
                value={draftFilters.minAmount}
                error={invalidAmountRange}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  minAmount: event.target.value,
                }))}
                slotProps={{ htmlInput: { min: 0, step: 1000 } }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2.25 }}>
              <TextField
                fullWidth
                type="number"
                label="Số tiền đến"
                size="small"
                value={draftFilters.maxAmount}
                error={invalidAmountRange}
                helperText={invalidAmountRange ? 'Phải lớn hơn hoặc bằng số tiền từ' : undefined}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  maxAmount: event.target.value,
                }))}
                slotProps={{ htmlInput: { min: 0, step: 1000 } }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
            <TextField
              fullWidth
              type="date"
              label="Từ ngày"
              size="small"
              value={draftFilters.requestedFrom}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                requestedFrom: event.target.value,
              }))}
              slotProps={{ inputLabel: { shrink: true } }}
              error={invalidDateRange}
            />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
            <TextField
              fullWidth
              type="date"
              label="Đến ngày"
              size="small"
              value={draftFilters.requestedTo}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                requestedTo: event.target.value,
              }))}
              slotProps={{ inputLabel: { shrink: true } }}
              error={invalidDateRange}
              helperText={invalidDateRange ? 'Ngày kết thúc phải từ ngày bắt đầu trở đi' : undefined}
            />
            </Grid>
          </Grid>
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', mt: 2 }}>
            <Button
              color="inherit"
              disabled={!activeFilterCount && !Object.values(draftFilters).some(Boolean)}
              onClick={clearFilters}
              sx={{ fontWeight: 700, textTransform: 'none' }}
            >
              Xóa lọc{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
            </Button>
            <Button
              variant="contained"
              startIcon={<FilterListIcon />}
              onClick={applyFilters}
              disabled={invalidDateRange || invalidAmountRange}
              sx={{ fontWeight: 700, textTransform: 'none' }}
            >
              Áp dụng
            </Button>
          </Stack>
        </Box>

        {queue.isError && !queue.data ? (
          <Alert
            severity="error"
            action={(
              <Button color="inherit" onClick={() => void queue.refetch()}>
                Thử lại
              </Button>
            )}
            sx={{ m: 3 }}
          >
            {getPayoutErrorMessage(queue.error)}
          </Alert>
        ) : (
          <>
          {queue.isError && queue.data && (
            <Alert
              severity="warning"
              action={<Button color="inherit" onClick={() => void queue.refetch()}>Tải lại</Button>}
              sx={{ mx: 3, mt: 3 }}
            >
              Không thể cập nhật dữ liệu mới. Bảng bên dưới là dữ liệu gần nhất đã tải được.
            </Alert>
          )}
          <TableContainer>
            <Table sx={{ minWidth: 1580 }}>
              <TableHead>
                <TableRow sx={{ bgcolor: '#f8fafc' }}>
                  <TableCell sx={headerCellSx}>Người nhận</TableCell>
                  <TableCell sx={headerCellSx}>Mã tham chiếu</TableCell>
                  <TableCell sx={headerCellSx}>Số tiền</TableCell>
                  <TableCell sx={headerCellSx}>Thời gian chờ</TableCell>
                  <TableCell sx={headerCellSx}>Nội bộ</TableCell>
                  <TableCell sx={headerCellSx}>Provider</TableCell>
                  <TableCell sx={headerCellSx}>Đối soát</TableCell>
                  <TableCell sx={headerCellSx}>Cập nhật</TableCell>
                  <TableCell align="right" sx={headerCellSx}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {queue.isLoading
                  ? Array.from({ length: 5 }, (_, index) => (
                      <TableRow key={index}>
                        {Array.from({ length: 9 }, (__, cellIndex) => (
                          <TableCell key={cellIndex}><Skeleton /></TableCell>
                        ))}
                      </TableRow>
                    ))
                  : queue.data?.content.length === 0
                    ? (
                        <TableRow>
                          <TableCell colSpan={9}>
                            <Box sx={{ py: 7, textAlign: 'center' }}>
                              <PaymentsOutlinedIcon sx={{ color: 'text.disabled', fontSize: 42 }} />
                              <Typography sx={{ fontWeight: 700, mt: 1 }}>
                                {activeFilterCount ? 'Không có yêu cầu phù hợp' : 'Chưa có yêu cầu chi trả'}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {activeFilterCount
                                  ? 'Hãy thay đổi hoặc xóa bớt điều kiện lọc.'
                                  : 'Yêu cầu rút tiền mới sẽ xuất hiện tại đây.'}
                              </Typography>
                            </Box>
                          </TableCell>
                        </TableRow>
                      )
                    : queue.data?.content.map((item) => (
                        <TableRow key={item.withdrawalRequestId} hover>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {item.ownerName ?? item.teacherName}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                              sx={{ display: 'block', maxWidth: 180 }}
                              noWrap
                            >
                              {item.ownerType === 'STUDENT' ? 'Học viên' : 'Giáo viên'} · {item.ownerId ?? item.teacherId}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                              {item.bankName || 'Chưa ghi nhận ngân hàng'} · {item.accountNumberMasked || 'Chưa ghi nhận tài khoản'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" sx={{ display: 'block', fontFamily: 'monospace' }}>
                              Chi trả: {item.withdrawalRequestId}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontFamily: 'monospace' }}>
                              Ví: {item.walletId || 'Chưa đồng bộ'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>
                              {formatMoney(item.requestedAmount)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Phí / thực nhận: Chưa đồng bộ
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{formatFinanceDateTime(item.requestedAt)}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {waitingCalendarDays(item.requestedAt) === null
                                ? 'Chưa xác định thời gian chờ'
                                : `Đang chờ ${waitingCalendarDays(item.requestedAt)} ngày lịch`}
                            </Typography>
                          </TableCell>
                          <TableCell><PayoutStatusBadge status={item.status} /></TableCell>
                          <TableCell>
                            {item.settlementStatus
                              ? <PayoutStatusBadge status={item.settlementStatus} />
                              : <Typography variant="body2" color="text.secondary">Chưa tạo</Typography>}
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                              {item.provider || 'Chưa đồng bộ provider'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontFamily: 'monospace' }}>
                              {item.providerReference || 'Chưa có mã giao dịch'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <PayoutStatusBadge status={item.reconciliationStatus} />
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                              Đã thử {item.retryCount} lần
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {formatFinanceDateTime(item.settlementUpdatedAt || item.updatedAt)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {item.decidedBy ? `Người duyệt: ${item.decidedBy}` : 'Chưa ghi nhận người duyệt'}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">
                            <Button
                              component={RouterLink}
                              to={`/admin/payouts/${item.withdrawalRequestId}`}
                              variant="outlined"
                              size="small"
                              startIcon={<VisibilityOutlinedIcon />}
                              sx={{ fontWeight: 700, textTransform: 'none', whiteSpace: 'nowrap' }}
                            >
                              Chi tiết
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
              </TableBody>
            </Table>
          </TableContainer>
          </>
        )}

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{
            alignItems: 'center',
            borderTop: '1px solid',
            borderColor: 'divider',
            justifyContent: 'space-between',
            px: 3,
            py: 2,
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {queue.data
              ? `${queue.data.totalElements} yêu cầu · Trang ${page + 1}/${Math.max(queue.data.totalPages, 1)}`
              : 'Chưa đồng bộ số lượng yêu cầu'}
          </Typography>
          <Pagination
            color="primary"
            count={Math.max(queue.data?.totalPages ?? 1, 1)}
            page={page + 1}
            onChange={(_, value) => setPage(value - 1)}
            disabled={queue.isFetching}
            size="small"
          />
        </Stack>
      </Paper>
    </Box>
  );
}

const headerCellSx = {
  color: 'text.secondary',
  fontSize: 12,
  fontWeight: 800,
  letterSpacing: '0.04em',
  textTransform: 'uppercase',
};

function PayoutMetric({
  helper,
  label,
  loading,
  value,
}: {
  helper: string;
  label: string;
  loading: boolean;
  value: string | null;
}) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        {loading
          ? <Skeleton width="65%" sx={{ my: 0.5 }} />
          : (
              <Typography variant="h6" sx={{ fontWeight: 800, my: 0.5 }}>
                {value ?? 'Chưa đồng bộ'}
              </Typography>
            )}
        <Typography variant="caption" color="text.secondary">{helper}</Typography>
      </CardContent>
    </Card>
  );
}
