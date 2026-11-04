import React, { useEffect, useState } from 'react';
import { Box, Typography, Grid, CircularProgress, Alert, Button, Stack, Card, CardContent, Divider, Chip, Tooltip, Skeleton } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import RuleIcon from '@mui/icons-material/Rule';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import SettingsApplicationsOutlinedIcon from '@mui/icons-material/SettingsApplicationsOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import SportsEsportsOutlinedIcon from '@mui/icons-material/SportsEsportsOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import PolicyOutlinedIcon from '@mui/icons-material/PolicyOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import { adminKycService } from '../../admin-kyc/services/adminKycService';
import type { KycRequestResponse } from '../../admin-kyc/services/adminKycService';
import { courseApprovalService } from '../../admin-course-approval/services/courseApprovalService';
import type { CourseApproval } from '../../admin-course-approval/types';
import { getAuthSession, hasAnyRole } from '../../../shared/auth/authSession';
import { ROUTES } from '../../../shared/constants/routes';
import { ROLES } from '../../../shared/constants/roles';
import { adminPayoutService } from '../../admin-payout/services/adminPayoutService';
import { adminRefundApi } from '../../admin-refund/api/adminRefundApi';
import { adminViolationService } from '../../admin-violation/services/adminViolationService';
import { adminFinanceApi } from '../../admin-finance/adminFinanceApi';
import { RevenueChart } from '../../admin-finance/RevenueChart';
import { formatDateRange, formatFinanceDateTime, formatMoney, reportingMonthRange, toExclusiveReportingRange } from '../../admin-finance/financeDisplay';
import type { MoneyValue, RevenueDashboard } from '../../admin-finance/types';

export const AdminDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const session = getAuthSession('admin');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [kycQueue, setKycQueue] = useState<KycRequestResponse[]>([]);
  const [courseQueue, setCourseQueue] = useState<CourseApproval[]>([]);
  const [pendingViolations, setPendingViolations] = useState(0);
  const [pendingPayouts, setPendingPayouts] = useState<number | null>(null);
  const [pendingRefunds, setPendingRefunds] = useState<number | null>(null);
  const [reconciliationAlerts, setReconciliationAlerts] = useState<number | null>(null);
  const [draftExpenses, setDraftExpenses] = useState<number | null>(null);
  const [financeDashboard, setFinanceDashboard] = useState<RevenueDashboard | null>(null);

  const isCourseManager = session ? hasAnyRole(session, [ROLES.COURSE_MANAGER]) : false;
  const isFinanceManager = session ? hasAnyRole(session, [ROLES.FINANCE_MANAGER]) : false;
  const isSystemAdmin = session ? hasAnyRole(session, [ROLES.SYSTEM_ADMIN]) : false;
  const hasFinanceData = Boolean(
    financeDashboard
    || pendingPayouts !== null
    || pendingRefunds !== null
    || reconciliationAlerts !== null
    || draftExpenses !== null,
  );

  const loadData = React.useCallback(async () => {
    if (!isCourseManager && !isFinanceManager) return;

    setLoading(true);
    setError(null);
    try {
      if (isCourseManager) {
        const [kycData, courseData, violationData] = await Promise.all([
          adminKycService.getPendingKycQueue(),
          courseApprovalService.getQueue(),
          adminViolationService.getViolationQueue({ page: 0, size: 1, status: 'PENDING_REVIEW' }),
        ]);
        setKycQueue(kycData);
        setCourseQueue(courseData);
        setPendingViolations(violationData.totalElements);
      }

      if (isFinanceManager) {
        const period = reportingMonthRange(new Date());
        const instants = toExclusiveReportingRange(period.from, period.to);
        const results = await Promise.allSettled([
          adminPayoutService.getPayoutQueue({ page: 0, size: 1, status: 'PENDING' }),
          adminPayoutService.getPayoutQueue({
            page: 0,
            size: 1,
            reconciliationStatus: 'CRITICAL_MISMATCH',
          }),
          adminRefundApi.getPendingRefunds(0, 1, { status: 'PENDING' }),
          adminFinanceApi.searchExpenses({ page: 0, size: 1, status: 'DRAFT' }),
          adminFinanceApi.getRevenueDashboard({ ...instants, granularity: 'DAY' }),
        ]);
        if (results[0].status === 'fulfilled') setPendingPayouts(results[0].value.totalElements);
        if (results[1].status === 'fulfilled') setReconciliationAlerts(results[1].value.totalElements);
        if (results[2].status === 'fulfilled') setPendingRefunds(results[2].value.totalElements);
        if (results[3].status === 'fulfilled') setDraftExpenses(results[3].value.totalElements);
        if (results[4].status === 'fulfilled') setFinanceDashboard(results[4].value);
        if (results.some((result) => result.status === 'rejected')) {
          setError('Một phần dữ liệu tài chính chưa đồng bộ. Ô chưa từng tải giữ trạng thái “Chưa đồng bộ”; dữ liệu cũ, nếu có, giữ nguyên thời điểm cập nhật và không bị quy đổi thành 0.');
        }
      }
    } catch {
      setError('Không thể tải dữ liệu vận hành. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, [isCourseManager, isFinanceManager]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (!session) {
    return null;
  }

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 'bold' }}>{isFinanceManager ? 'Bảng điều hành tài chính' : 'Trung tâm Quản trị viên'}</Typography>
          {isFinanceManager && <Typography variant="body2" color="text.secondary">Tiền, hàng đợi xử lý và trạng thái đồng bộ trong một màn hình.</Typography>}
        </Box>
        {isFinanceManager && <Button variant="outlined" startIcon={<RefreshIcon />} onClick={loadData} disabled={loading}>Làm mới</Button>}
      </Stack>

      {error && (
        <Alert
          severity={isFinanceManager && hasFinanceData ? 'warning' : 'error'}
          sx={{ mb: 3 }}
          action={<Button color="inherit" size="small" onClick={loadData}>Thử lại</Button>}
        >
          {error}
        </Alert>
      )}

      {isSystemAdmin ? (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Cấu hình hệ thống"
              subtitle="Giá, hoàn tiền, escrow, AI, kiểm tra khóa học và bảo mật đăng nhập"
              icon={<SettingsApplicationsOutlinedIcon />}
              actionLabel="Mở cấu hình"
              onAction={() => navigate(ROUTES.ADMIN.SYSTEM_SETTINGS)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Phân quyền nội bộ"
              subtitle="Kiểm tra tài khoản và gán đúng một vai trò quản trị"
              icon={<ManageAccountsOutlinedIcon />}
              actionLabel="Quản lý vai trò"
              onAction={() => navigate(ROUTES.ADMIN.USERS)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Nhật ký hệ thống"
              subtitle="Theo dõi và kiểm tra các thao tác nhạy cảm của người dùng và quản trị viên"
              icon={<FactCheckIcon />}
              actionLabel="Xem nhật ký"
              onAction={() => navigate(ROUTES.ADMIN.AUDIT_LOGS)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Hậu kiểm quyết định"
              subtitle="Xem quyết định của Course Manager và Finance Manager; cảnh báo mà không thay đổi luồng xử lý"
              icon={<PolicyOutlinedIcon />}
              actionLabel="Mở danh sách hậu kiểm"
              onAction={() => navigate(ROUTES.ADMIN.DECISION_REVIEWS)}
            />
          </Grid>
        </Grid>
      ) : isFinanceManager ? (
        <>
          <Stack direction={{ xs: 'column', md: 'row' }} sx={{ gap: 1, alignItems: { md: 'center' }, mb: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Tổng quan tháng này</Typography>
            <Chip size="small" variant="outlined" label={`Kỳ xem: ${formatDateRange(reportingMonthRange(new Date()).from, reportingMonthRange(new Date()).to)}`} />
            <Typography variant="caption" color="text.secondary">Múi giờ Asia/Ho_Chi_Minh · Cập nhật: {financeDashboard ? formatFinanceDateTime(financeDashboard.generatedAt) : 'Chưa đồng bộ'}</Typography>
          </Stack>
          <Grid container spacing={2} sx={{ mb: 4 }}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Tổng tiền thanh toán" value={financeDashboard?.summary.grossSales} formula="Tổng tiền trước khấu trừ của các đơn khóa học đã thanh toán thành công hoặc đã hoàn tiền trong tháng." onClick={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Hoàn tiền" value={financeDashboard?.summary.refundAmount} formula="Tổng số tiền của các yêu cầu đã hoàn tất; thời điểm ghi nhận ưu tiên lúc hoàn tất, quyết định, rồi lần cập nhật cuối." onClick={() => navigate(ROUTES.ADMIN.REFUND_REVIEW)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Tiền thu ròng" value={financeDashboard?.summary.netCollected} formula="Tổng tiền thanh toán − hoàn tiền." onClick={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Doanh thu nền tảng" value={financeDashboard?.summary.platformRevenue} formula="Hoa hồng ghi nhận − hoa hồng đảo trên ledger." onClick={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Phí thanh toán" value={financeDashboard?.summary.paymentFees} formula="Dòng chứng từ đã duyệt thuộc nhóm phí thanh toán." onClick={() => navigate(ROUTES.ADMIN.FINANCE_EXPENSES)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Chi phí vận hành" value={financeDashboard?.summary.operatingExpenses} formula="Dòng chứng từ đã duyệt ngoài nhóm phí thanh toán." onClick={() => navigate(ROUTES.ADMIN.FINANCE_EXPENSES)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={loading && !financeDashboard} label="Lợi nhuận vận hành" value={financeDashboard?.summary.netOperatingResult} formula="Doanh thu nền tảng − phí thanh toán − chi phí vận hành." onClick={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><FinanceKpiCard loading={false} label="Tiền đang chờ chi trả" value={null} formula="API hàng đợi hiện chưa cung cấp tổng tiền toàn bộ các yêu cầu chờ; không suy ra từ một trang dữ liệu." /></Grid>
          </Grid>

          <Card variant="outlined" sx={{ mb: 4 }}>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1, mb: 2 }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Xu hướng tài chính tháng này</Typography>
                  <Typography variant="body2" color="text.secondary">Doanh số, hoàn tiền, doanh thu nền tảng, chi phí và lợi nhuận từ cùng nguồn dữ liệu báo cáo.</Typography>
                </Box>
                <Button variant="outlined" onClick={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)}>Mở báo cáo chi tiết</Button>
              </Stack>
              {loading && !financeDashboard
                ? <Skeleton variant="rounded" height={320} />
                : financeDashboard
                  ? <RevenueChart points={financeDashboard.points} granularity={financeDashboard.granularity} />
                  : <Alert severity="info">Biểu đồ chưa đồng bộ. Hãy làm mới dữ liệu tài chính.</Alert>}
            </CardContent>
          </Card>

          <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>Hàng đợi cần xử lý</Typography>
          <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 4 }}>
            <OperationalQueueCard
              title="Yêu cầu chi trả chờ xử lý"
              subtitle="Các yêu cầu rút tiền cần Finance Manager xem xét"
              value={pendingPayouts}
              loading={loading}
              icon={<AccountBalanceOutlinedIcon />}
              actionLabel="Mở hàng đợi chi trả"
              onAction={() => navigate(ROUTES.ADMIN.PAYOUTS)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <OperationalQueueCard
              title="Cảnh báo đối soát nghiêm trọng"
              subtitle="Sai lệch đang chặn việc thực hiện chi trả"
              value={reconciliationAlerts}
              loading={loading}
              icon={<WarningAmberOutlinedIcon />}
              actionLabel="Kiểm tra đối soát"
              onAction={() => navigate(ROUTES.ADMIN.PAYOUTS)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <OperationalQueueCard
              title="Yêu cầu hoàn tiền chờ duyệt"
              subtitle="Học viên yêu cầu hoàn tiền khóa học"
              value={pendingRefunds}
              loading={loading}
              icon={<FactCheckIcon />}
              actionLabel="Mở hàng đợi hoàn tiền"
              onAction={() => navigate(ROUTES.ADMIN.REFUND_REVIEW)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <OperationalQueueCard
              title="Chứng từ nháp"
              subtitle="Chứng từ chưa được duyệt; tổng tiền và tuổi lâu nhất chưa có trong API hàng đợi"
              value={draftExpenses}
              loading={loading}
              icon={<ReceiptLongOutlinedIcon />}
              actionLabel="Kiểm tra chứng từ"
              onAction={() => navigate(ROUTES.ADMIN.FINANCE_EXPENSES)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Doanh thu hệ thống"
              subtitle="Doanh số, hoàn tiền, doanh thu nền tảng, chi phí và kết quả vận hành theo thời gian"
              icon={<TrendingUpOutlinedIcon />}
              actionLabel="Mở báo cáo doanh thu"
              onAction={() => navigate(ROUTES.ADMIN.FINANCE_REVENUE)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <AdminActionCard
              title="Chi phí vận hành"
              subtitle="Nhập hóa đơn thực tế và tách các dòng AWS, SMS, AI, KYC theo từng thành phần"
              icon={<ReceiptLongOutlinedIcon />}
              actionLabel="Quản lý chi phí"
              onAction={() => navigate(ROUTES.ADMIN.FINANCE_EXPENSES)}
            />
          </Grid>
          </Grid>
        </>
      ) : (
        <>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flexGrow: 1, pb: 0 }}>
                  <Stack direction="row" sx={{ alignItems: 'center', gap: 2, mb: 2 }}>
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'primary.light', color: 'primary.dark', display: 'flex' }}>
                      <FactCheckIcon />
                    </Box>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Kiểm duyệt KYC Giáo viên</Typography>
                      <Typography variant="body2" color="text.secondary">Hồ sơ chờ xác minh</Typography>
                    </Box>
                  </Stack>
                  <Box sx={{ my: 4, textAlign: 'center' }}>
                    {loading ? (
                      <CircularProgress />
                    ) : (
                      <Typography variant="h2" color="primary.main" sx={{ fontWeight: 'bold' }}>
                        {kycQueue.filter(k => k.status === 'PENDING').length}
                      </Typography>
                    )}
                  </Box>
                </CardContent>
                <Divider />
                <Box sx={{ p: 2 }}>
                  <Button
                    fullWidth
                    variant="outlined"
                    endIcon={<ArrowForwardIcon />}
                    onClick={() => navigate(ROUTES.ADMIN.KYC_REVIEW)}
                  >
                    Đến hàng đợi KYC
                  </Button>
                </Box>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flexGrow: 1, pb: 0 }}>
                  <Stack direction="row" sx={{ alignItems: 'center', gap: 2, mb: 2 }}>
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'secondary.light', color: 'secondary.dark', display: 'flex' }}>
                      <RuleIcon />
                    </Box>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Phê duyệt Khóa học</Typography>
                      <Typography variant="body2" color="text.secondary">Khóa học chờ duyệt xuất bản</Typography>
                    </Box>
                  </Stack>
                  <Box sx={{ my: 4, textAlign: 'center' }}>
                    {loading ? (
                      <CircularProgress />
                    ) : (
                      <Typography variant="h2" color="secondary.main" sx={{ fontWeight: 'bold' }}>
                        {courseQueue.filter(c => c.status === 'PENDING').length}
                      </Typography>
                    )}
                  </Box>
                </CardContent>
                <Divider />
                <Box sx={{ p: 2 }}>
                  <Button
                    fullWidth
                    variant="outlined"
                    color="secondary"
                    endIcon={<ArrowForwardIcon />}
                    onClick={() => navigate(ROUTES.ADMIN.COURSE_APPROVAL)}
                  >
                    Đến hàng đợi Khóa học
                  </Button>
                </Box>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flexGrow: 1, pb: 0 }}>
                  <Stack direction="row" sx={{ alignItems: 'center', gap: 2, mb: 2 }}>
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: '#fee2e2', color: '#b91c1c', display: 'flex' }}>
                      <ReportProblemOutlinedIcon />
                    </Box>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Báo cáo vi phạm</Typography>
                      <Typography variant="body2" color="text.secondary">Báo cáo chờ xem xét</Typography>
                    </Box>
                  </Stack>
                  <Box sx={{ my: 4, textAlign: 'center' }}>
                    {loading ? (
                      <CircularProgress />
                    ) : (
                      <Typography variant="h2" color="error.main" sx={{ fontWeight: 'bold' }}>
                        {pendingViolations}
                      </Typography>
                    )}
                  </Box>
                </CardContent>
                <Divider />
                <Box sx={{ p: 2 }}>
                  <Button
                    fullWidth
                    variant="outlined"
                    color="error"
                    endIcon={<ArrowForwardIcon />}
                    onClick={() => navigate(ROUTES.ADMIN.VIOLATIONS)}
                  >
                    Đến hàng đợi Vi phạm
                  </Button>
                </Box>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <AdminActionCard
                title="Trò chơi & thưởng tuần"
                subtitle="Soạn nội dung, cấu hình phần thưởng và công khai thử thách học tập theo tuần"
                icon={<SportsEsportsOutlinedIcon />}
                actionLabel="Quản lý trò chơi tuần"
                onAction={() => navigate(ROUTES.ADMIN.WEEKLY_CHALLENGES)}
              />
            </Grid>
          </Grid>
        </>
      )}
    </Box>
  );
};

interface OperationalQueueCardProps {
  title: string;
  subtitle: string;
  value: number | null;
  loading: boolean;
  icon: React.ReactNode;
  actionLabel: string;
  onAction: () => void;
}

function OperationalQueueCard({
  title,
  subtitle,
  value,
  loading,
  icon,
  actionLabel,
  onAction,
}: OperationalQueueCardProps) {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flexGrow: 1 }}>
        <Stack direction="row" sx={{ alignItems: 'center', gap: 2, mb: 3 }}>
          <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', display: 'flex' }}>
            {icon}
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{title}</Typography>
            <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
          </Box>
        </Stack>
        <Box sx={{ minHeight: 72, display: 'grid', placeItems: 'center' }}>
          {loading ? <CircularProgress size={36} /> : value === null ? (
            <Typography variant="h6" color="text.secondary" sx={{ fontWeight: 750 }}>Chưa đồng bộ</Typography>
          ) : <Typography variant="h2" sx={{ fontWeight: 800 }}>{value}</Typography>}
        </Box>
      </CardContent>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Button fullWidth variant="outlined" endIcon={<ArrowForwardIcon />} onClick={onAction}>
          {actionLabel}
        </Button>
      </Box>
    </Card>
  );
}

function FinanceKpiCard({ label, value, formula, onClick, loading }: {
  label: string;
  value: MoneyValue | null | undefined;
  formula: string;
  onClick?: () => void;
  loading: boolean;
}) {
  return (
    <Tooltip title={formula} arrow>
      <Card
        variant="outlined"
        role={onClick ? 'link' : undefined}
        tabIndex={onClick ? 0 : undefined}
        onClick={onClick}
        onKeyDown={(event) => {
          if (onClick && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault();
            onClick();
          }
        }}
        sx={{ height: '100%', cursor: onClick ? 'pointer' : 'default' }}
      >
        <CardContent>
          <Typography variant="body2" color="text.secondary">{label}</Typography>
          {loading
            ? <Skeleton width="70%" height={36} />
            : <Typography variant="h6" sx={{ fontWeight: 800, mt: 1 }}>{value === null || value === undefined ? 'Chưa đồng bộ' : formatMoney(value)}</Typography>}
          {onClick && <Typography variant="caption" color="primary">Mở dữ liệu liên quan →</Typography>}
        </CardContent>
      </Card>
    </Tooltip>
  );
}

function AdminActionCard({
  title,
  subtitle,
  icon,
  actionLabel,
  onAction,
}: Omit<OperationalQueueCardProps, 'loading' | 'value'>) {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flexGrow: 1 }}>
        <Stack direction="row" sx={{ alignItems: 'flex-start', gap: 2 }}>
          <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', display: 'flex' }}>
            {icon}
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{title}</Typography>
            <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
          </Box>
        </Stack>
      </CardContent>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Button fullWidth variant="outlined" endIcon={<ArrowForwardIcon />} onClick={onAction}>
          {actionLabel}
        </Button>
      </Box>
    </Card>
  );
}
