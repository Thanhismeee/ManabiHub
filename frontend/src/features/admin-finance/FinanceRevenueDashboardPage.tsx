import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  LinearProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useNavigate } from 'react-router-dom';
import { adminFinanceApi } from './adminFinanceApi';
import { getRevenueLoadErrorMessage } from './financeRevenueError';
import { RevenueChart } from './RevenueChart';
import {
  FINANCE_TIME_ZONE,
  type QuickRange,
  formatDateRange,
  formatFinanceDateTime,
  formatMoney,
  formatPercentage,
  formatRevenueBucket,
  parseFinanceTimestamp,
  quickDateRange,
  reportingDateInputValue,
  reportingMonthRange,
  toExclusiveReportingRange,
} from './financeDisplay';
import type { MoneyValue, RevenueDashboard, RevenueGranularity, RevenuePoint } from './types';

interface DateRange {
  from: string;
  to: string;
}

interface MetricDefinition {
  label: string;
  value: MoneyValue;
  helper?: string;
  formula: string;
  route?: string;
}

export function FinanceRevenueDashboardPage() {
  const navigate = useNavigate();
  const defaultRange = useMemo(() => reportingMonthRange(new Date()), []);
  const [draftRange, setDraftRange] = useState<DateRange>(defaultRange);
  const [range, setRange] = useState<DateRange>(defaultRange);
  const [quickRange, setQuickRange] = useState<QuickRange | 'CUSTOM'>('MONTH');
  const [granularity, setGranularity] = useState<RevenueGranularity>('DAY');
  const [dashboard, setDashboard] = useState<RevenueDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    let instants: ReturnType<typeof toExclusiveReportingRange>;
    try {
      instants = toExclusiveReportingRange(range.from, range.to);
    } catch (rangeError) {
      setError(rangeError instanceof Error ? rangeError.message : 'Khoảng thời gian không hợp lệ.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setDashboard(await adminFinanceApi.getRevenueDashboard({ ...instants, granularity }));
    } catch (requestError) {
      setError(getRevenueLoadErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [granularity, range]);

  useEffect(() => { void load(); }, [load]);

  const applyQuickRange = (value: QuickRange) => {
    const next = quickDateRange(value, new Date());
    setQuickRange(value);
    setDraftRange(next);
    setRange(next);
  };

  const applyCustomRange = () => {
    try {
      toExclusiveReportingRange(draftRange.from, draftRange.to);
      setQuickRange('CUSTOM');
      setRange(draftRange);
    } catch (rangeError) {
      setError(rangeError instanceof Error ? rangeError.message : 'Khoảng thời gian không hợp lệ.');
    }
  };

  const summary = dashboard?.summary;
  const displayedRange = dashboard ? dashboardReportingRange(dashboard) : range;
  const metrics: MetricDefinition[] = summary ? [
    {
      label: 'Tổng tiền thanh toán',
      value: summary.grossSales,
      helper: `${summary.successfulOrders.toLocaleString('vi-VN')} đơn thành công`,
      formula: 'Tổng tiền trước khấu trừ của các đơn khóa học đã thanh toán thành công hoặc đã hoàn tiền trong kỳ.',
    },
    {
      label: 'Hoàn tiền',
      value: summary.refundAmount,
      helper: `${summary.refundCount.toLocaleString('vi-VN')} yêu cầu · ${formatPercentage(summary.refundRate)}`,
      formula: 'Tổng số tiền của các yêu cầu hoàn đã hoàn tất; thời điểm ghi nhận ưu tiên lúc hoàn tất, quyết định, rồi lần cập nhật cuối.',
      route: '/admin/refunds',
    },
    {
      label: 'Tiền thu ròng',
      value: summary.netCollected,
      formula: 'Tổng tiền thanh toán − hoàn tiền.',
    },
    {
      label: 'Doanh thu nền tảng',
      value: summary.platformRevenue,
      helper: `Ghi nhận ${formatMoney(summary.commissionRecognized)} · đảo ${formatMoney(summary.commissionReversed)}`,
      formula: 'Hoa hồng đã ghi nhận − hoa hồng đã đảo trên platform commission ledger.',
    },
    {
      label: 'Phí thanh toán',
      value: summary.paymentFees,
      formula: 'Các dòng chứng từ đã xác nhận hoặc đã thanh toán thuộc nhóm phí cổng thanh toán, hoàn tiền và truy thu.',
      route: '/admin/finance/expenses',
    },
    {
      label: 'Chi phí vận hành',
      value: summary.operatingExpenses,
      formula: 'Các dòng chứng từ đã xác nhận hoặc đã thanh toán không thuộc nhóm phí thanh toán.',
      route: '/admin/finance/expenses',
    },
    {
      label: 'Lợi nhuận vận hành',
      value: summary.netOperatingResult,
      formula: 'Doanh thu nền tảng − phí thanh toán − chi phí vận hành.',
    },
  ] : [];

  return (
    <Box sx={{ p: { xs: 1, md: 2 } }}>
      <Stack direction={{ xs: 'column', md: 'row' }} sx={{ gap: 2, justifyContent: 'space-between', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>Báo cáo doanh thu và vận hành</Typography>
          <Typography color="text.secondary">
            Đối chiếu thanh toán, hoàn tiền, commission ledger và chứng từ chi phí đã duyệt.
          </Typography>
        </Box>
        <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Button
            variant="outlined"
            startIcon={<DownloadOutlinedIcon />}
            onClick={() => dashboard && exportRevenueCsv(dashboard)}
            disabled={!dashboard || loading}
          >
            Xuất CSV
          </Button>
          <Button variant="contained" startIcon={<RefreshIcon />} onClick={() => void load()} disabled={loading}>
            Làm mới
          </Button>
        </Stack>
      </Stack>

      <Card variant="outlined" sx={{ mb: 3 }}>
        {loading && dashboard && <LinearProgress aria-label="Đang cập nhật báo cáo" />}
        <CardContent>
          <Stack sx={{ gap: 2 }}>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={quickRange}
              onChange={(_, value: QuickRange | 'CUSTOM' | null) => {
                if (value && value !== 'CUSTOM') applyQuickRange(value);
              }}
              aria-label="Khoảng ngày nhanh"
              sx={{ flexWrap: 'wrap' }}
            >
              <ToggleButton value="TODAY">Hôm nay</ToggleButton>
              <ToggleButton value="WEEK">Tuần này</ToggleButton>
              <ToggleButton value="MONTH">Tháng này</ToggleButton>
              <ToggleButton value="QUARTER">Quý này</ToggleButton>
              <ToggleButton value="CUSTOM" disabled>Tùy chọn</ToggleButton>
            </ToggleButtonGroup>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 2, alignItems: { sm: 'center' } }}>
              <TextField
                label="Từ ngày"
                type="date"
                value={draftRange.from}
                onChange={(event) => setDraftRange((current) => ({ ...current, from: event.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Đến ngày"
                type="date"
                value={draftRange.to}
                onChange={(event) => setDraftRange((current) => ({ ...current, to: event.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                select
                label="Nhóm theo"
                value={granularity}
                onChange={(event) => setGranularity(event.target.value as RevenueGranularity)}
                sx={{ minWidth: 160 }}
              >
                <MenuItem value="DAY">Ngày</MenuItem>
                <MenuItem value="WEEK">Tuần</MenuItem>
                <MenuItem value="MONTH">Tháng</MenuItem>
              </TextField>
              <Button variant="outlined" onClick={applyCustomRange}>Áp dụng</Button>
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} sx={{ gap: 1, alignItems: { md: 'center' } }}>
              <Chip label={`Kỳ dữ liệu: ${formatDateRange(displayedRange.from, displayedRange.to)}`} variant="outlined" />
              <Chip label={`Múi giờ: ${dashboard?.timezone ?? FINANCE_TIME_ZONE}`} variant="outlined" />
              <Typography variant="caption" color="text.secondary">
                Cập nhật cuối: {dashboard ? formatFinanceDateTime(dashboard.generatedAt) : 'Chưa đồng bộ'} · So sánh kỳ trước: API chưa hỗ trợ
              </Typography>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {error && (
        <Alert severity={dashboard ? 'warning' : 'error'} sx={{ mb: 3 }} action={<Button color="inherit" onClick={() => void load()}>Thử lại</Button>}>
          {error}{dashboard ? ' Dữ liệu bên dưới là lần tải thành công gần nhất.' : ''}
        </Alert>
      )}

      {loading && !dashboard ? (
        <Box sx={{ display: 'grid', placeItems: 'center', minHeight: 320 }} role="status">
          <Stack sx={{ alignItems: 'center', gap: 2 }}>
            <CircularProgress />
            <Typography color="text.secondary">Đang tổng hợp dữ liệu tài chính…</Typography>
          </Stack>
        </Box>
      ) : dashboard ? (
        <>
          <Grid container spacing={2} sx={{ mb: 3 }}>
            {metrics.map((metric) => (
              <Grid key={metric.label} size={{ xs: 12, sm: 6, lg: 3 }}>
                <Card
                  variant="outlined"
                  role={metric.route ? 'link' : undefined}
                  tabIndex={metric.route ? 0 : undefined}
                  sx={{ height: '100%', cursor: metric.route ? 'pointer' : 'default' }}
                  onClick={() => metric.route && navigate(metric.route)}
                  onKeyDown={(event) => {
                    if (metric.route && (event.key === 'Enter' || event.key === ' ')) {
                      event.preventDefault();
                      navigate(metric.route);
                    }
                  }}
                >
                  <CardContent>
                    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
                      <Typography variant="body2" color="text.secondary">{metric.label}</Typography>
                      <Tooltip title={metric.formula} arrow>
                        <InfoOutlinedIcon color="action" fontSize="small" aria-label={`Công thức ${metric.label}`} />
                      </Tooltip>
                    </Stack>
                    <Typography variant="h6" sx={{ mt: 1, fontWeight: 800 }}>{formatMoney(metric.value)}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {metric.helper ?? formatDateRange(displayedRange.from, displayedRange.to)}
                    </Typography>
                    {metric.route && <Typography variant="caption" color="primary" sx={{ display: 'block', mt: 1 }}>Mở dữ liệu liên quan →</Typography>}
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>

          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Xu hướng tài chính</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Di chuột hoặc tập trung vào từng điểm để xem giá trị; số âm được giữ nguyên để phục vụ đối soát.
              </Typography>
              <RevenueChart points={dashboard.points} granularity={dashboard.granularity} />
              <Alert severity="info" sx={{ mt: 2 }}>
                API hiện mới cung cấp phân rã theo thời gian. Phân rã theo khóa học và payment provider chưa được đồng bộ, nên báo cáo không tự suy diễn từ dữ liệu trang hiện tại.
              </Alert>
              <Box component="details" sx={{ mt: 3 }}>
                <Typography component="summary" sx={{ cursor: 'pointer', fontWeight: 700 }}>
                  Xem bảng dữ liệu theo {dashboard.granularity === 'DAY' ? 'ngày' : dashboard.granularity === 'WEEK' ? 'tuần' : 'tháng'}
                </Typography>
                <RevenueDataTable points={dashboard.points} granularity={dashboard.granularity} />
              </Box>
            </CardContent>
          </Card>
        </>
      ) : (
        <Alert severity="info">Dữ liệu chưa đồng bộ. Hãy làm mới báo cáo.</Alert>
      )}
    </Box>
  );
}

function RevenueDataTable({ points, granularity }: { points: RevenuePoint[]; granularity: RevenueGranularity }) {
  return (
    <TableContainer sx={{ mt: 2, maxHeight: 440 }}>
      <Table stickyHeader size="small" aria-label="Dữ liệu tài chính theo thời gian">
        <TableHead>
          <TableRow>
            <TableCell>Kỳ</TableCell>
            <TableCell align="right">Thanh toán</TableCell>
            <TableCell align="right">Hoàn tiền</TableCell>
            <TableCell align="right">Doanh thu nền tảng</TableCell>
            <TableCell align="right">Phí thanh toán</TableCell>
            <TableCell align="right">Chi phí vận hành</TableCell>
            <TableCell align="right">Lợi nhuận vận hành</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {points.map((point) => (
            <TableRow key={point.bucket}>
              <TableCell>{formatRevenueBucket(point.bucket, granularity)}</TableCell>
              <TableCell align="right">{formatMoney(point.grossSales)}</TableCell>
              <TableCell align="right">{formatMoney(point.refundAmount)}</TableCell>
              <TableCell align="right">{formatMoney(point.platformRevenue)}</TableCell>
              <TableCell align="right">{formatMoney(point.paymentFees)}</TableCell>
              <TableCell align="right">{formatMoney(point.operatingExpenses)}</TableCell>
              <TableCell align="right">{formatMoney(Number(point.platformRevenue) - Number(point.paymentFees) - Number(point.operatingExpenses))}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function exportRevenueCsv(dashboard: RevenueDashboard) {
  const headers = [
    'Kỳ', 'Tổng thanh toán (VND)', 'Đơn thành công', 'Hoàn tiền (VND)', 'Số refund',
    'Hoa hồng ghi nhận (VND)', 'Hoa hồng đảo (VND)', 'Doanh thu nền tảng (VND)',
    'Phí thanh toán (VND)', 'Chi phí vận hành (VND)', 'Lợi nhuận vận hành (VND)',
  ];
  const rows = dashboard.points.map((point) => [
    point.bucket,
    point.grossSales,
    point.successfulOrders,
    point.refundAmount,
    point.refundCount,
    point.commissionRecognized,
    point.commissionReversed,
    point.platformRevenue,
    point.paymentFees,
    point.operatingExpenses,
    Number(point.platformRevenue) - Number(point.paymentFees) - Number(point.operatingExpenses),
  ]);
  const csv = [headers, ...rows]
    .map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(','))
    .join('\r\n');
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  const range = dashboardReportingRange(dashboard);
  anchor.download = `manabihub-revenue-${range.from}-${range.to}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function dashboardReportingRange(dashboard: RevenueDashboard): DateRange {
  const from = parseFinanceTimestamp(dashboard.from);
  const toExclusive = parseFinanceTimestamp(dashboard.to);
  const inclusiveEnd = new Date(toExclusive.getTime() - 1);
  return {
    from: reportingDateInputValue(from),
    to: reportingDateInputValue(inclusiveEnd),
  };
}
