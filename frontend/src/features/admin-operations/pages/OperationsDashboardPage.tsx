import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  FormControl,
  InputLabel,
  LinearProgress,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import StorageOutlinedIcon from '@mui/icons-material/StorageOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import { Link as RouterLink } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { operationsService } from '../services/operationsService';
import type {
  OperationsHealthStatus,
  OperationsLogFilters,
  OperationsLogLevel,
  OperationsLogPage,
  OperationsOverview,
  RuntimeConfiguration,
  RuntimeComponent,
  RuntimeComponentStatus,
} from '../types/operationsTypes';

const DEFAULT_LOG_LIMIT = 100;
const LIVE_LOG_REFRESH_INTERVAL_MS = 3_000;
const LOG_LEVELS: Array<{ value: '' | OperationsLogLevel; label: string }> = [
  { value: '', label: 'Tất cả mức độ' },
  { value: 'ERROR', label: 'Lỗi (ERROR)' },
  { value: 'WARN', label: 'Cảnh báo (WARN)' },
  { value: 'INFO', label: 'Thông tin (INFO)' },
];

interface LogFilterDraft {
  level: '' | OperationsLogLevel;
  query: string;
  correlationId: string;
}

const EMPTY_LOG_FILTERS: LogFilterDraft = {
  level: '',
  query: '',
  correlationId: '',
};

const RUNTIME_COMPONENT_LABELS: Record<RuntimeComponent, string> = {
  DATABASE: 'Cơ sở dữ liệu',
  MAIL: 'Email',
  GOOGLE_OAUTH: 'Google OAuth',
  VNPAY: 'VNPay',
  VNPT_EKYC: 'VNPT eKYC',
  AI_PROVIDER: 'Nhà cung cấp AI',
  SMS_PROVIDER: 'Nhà cung cấp SMS',
  JWT_SIGNING: 'Ký JWT',
  PAYOUT_SECURITY: 'Bảo mật chi trả',
  CORS_POLICY: 'Chính sách CORS',
};

function formatDateTime(value: string | null | undefined) {
  if (!value) return 'Chưa có dữ liệu';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return 'Chưa có dữ liệu';
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(parsed);
}

function formatBytes(value: number | null | undefined) {
  if (value === null || value === undefined || value < 0 || !Number.isFinite(value)) {
    return 'Chưa có dữ liệu';
  }
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let amount = value / 1024;
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index += 1;
  }
  return `${amount.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ${units[index]}`;
}

function formatDuration(totalSeconds: number | null | undefined) {
  if (totalSeconds === null || totalSeconds === undefined || totalSeconds < 0) {
    return 'Chưa có dữ liệu';
  }
  const seconds = Math.floor(totalSeconds);
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const parts = [
    days > 0 ? `${days} ngày` : '',
    hours > 0 ? `${hours} giờ` : '',
    `${minutes} phút`,
  ].filter(Boolean);
  return parts.join(' ');
}

function healthPresentation(status: OperationsHealthStatus | null | undefined) {
  const normalized = status?.toUpperCase() ?? 'UNKNOWN';
  if (normalized === 'UP') {
    return { label: 'Hoạt động', color: 'success' as const, icon: <CheckCircleOutlineIcon /> };
  }
  if (normalized === 'DOWN') {
    return { label: 'Gián đoạn', color: 'error' as const, icon: <ErrorOutlineIcon /> };
  }
  if (normalized === 'DEGRADED' || normalized === 'WARNING') {
    return { label: 'Suy giảm', color: 'warning' as const, icon: <WarningAmberOutlinedIcon /> };
  }
  return { label: 'Chưa xác định', color: 'default' as const, icon: <InfoOutlinedIcon /> };
}

function logLevelColor(level: string) {
  switch (level.toUpperCase()) {
    case 'ERROR':
      return 'error' as const;
    case 'WARN':
      return 'warning' as const;
    case 'INFO':
      return 'info' as const;
    default:
      return 'default' as const;
  }
}

function redactLogMessage(message: string) {
  return message
    .replace(/\bAuthorization\s*[:=]\s*(?:Bearer\s+)?[^\s,;]+/gi, 'Authorization: [ĐÃ CHE]')
    .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [ĐÃ CHE]')
    .replace(
      /\b(password|passwd|secret|token|api[_-]?key|cookie)(\s*[:=]\s*)([^\s,;]+)/gi,
      '$1$2[ĐÃ CHE]',
    )
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[EMAIL ĐÃ CHE]')
    .replace(/(?<!\d)(?:\+?84|0)(?:\d[ .-]?){8,10}(?!\d)/g, '[SĐT ĐÃ CHE]')
    .replace(/(?<!\d)\d{9,12}(?!\d)/g, '[DỮ LIỆU SỐ ĐÃ CHE]');
}

function buildLogFilters(draft: LogFilterDraft): OperationsLogFilters {
  return {
    ...(draft.level ? { level: draft.level } : {}),
    ...(draft.query.trim() ? { query: draft.query.trim() } : {}),
    ...(draft.correlationId.trim() ? { correlationId: draft.correlationId.trim() } : {}),
    limit: DEFAULT_LOG_LIMIT,
  };
}

export function OperationsDashboardPage() {
  const [activeTab, setActiveTab] = useState(0);
  const [overview, setOverview] = useState<OperationsOverview | null>(null);
  const [runtimeConfig, setRuntimeConfig] = useState<RuntimeConfiguration | null>(null);
  const [logs, setLogs] = useState<OperationsLogPage | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [runtimeLoading, setRuntimeLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(true);
  const [overviewError, setOverviewError] = useState(false);
  const [runtimeError, setRuntimeError] = useState(false);
  const [logsError, setLogsError] = useState(false);
  const [liveLogsEnabled, setLiveLogsEnabled] = useState(true);
  const [logsUpdatedAt, setLogsUpdatedAt] = useState<Date | null>(null);
  const [filterDraft, setFilterDraft] = useState<LogFilterDraft>(EMPTY_LOG_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<OperationsLogFilters>({
    limit: DEFAULT_LOG_LIMIT,
  });

  const loadOverview = useCallback(async () => {
    setOverviewLoading(true);
    setOverviewError(false);
    try {
      setOverview(await operationsService.getOverview());
    } catch {
      setOverviewError(true);
    } finally {
      setOverviewLoading(false);
    }
  }, []);

  const loadRuntimeConfig = useCallback(async () => {
    setRuntimeLoading(true);
    setRuntimeError(false);
    try {
      setRuntimeConfig(await operationsService.getRuntimeConfiguration());
    } catch {
      setRuntimeError(true);
    } finally {
      setRuntimeLoading(false);
    }
  }, []);

  const loadLogs = useCallback(async (
    filters: OperationsLogFilters,
    options: { background?: boolean } = {},
  ) => {
    if (!options.background) {
      setLogsLoading(true);
      setLogsError(false);
    }
    try {
      setLogs(await operationsService.getRecentLogs(filters));
      setLogsUpdatedAt(new Date());
      setLogsError(false);
    } catch {
      setLogsError(true);
    } finally {
      if (!options.background) {
        setLogsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadOverview();
    void loadRuntimeConfig();
    void loadLogs({ limit: DEFAULT_LOG_LIMIT });
  }, [loadLogs, loadOverview, loadRuntimeConfig]);

  useEffect(() => {
    if (activeTab !== 2 || !liveLogsEnabled) return undefined;

    let cancelled = false;
    let timer: number | undefined;

    const scheduleNextRefresh = () => {
      if (cancelled) return;
      timer = window.setTimeout(async () => {
        if (document.visibilityState === 'visible') {
          await loadLogs(appliedFilters, { background: true });
        }
        scheduleNextRefresh();
      }, LIVE_LOG_REFRESH_INTERVAL_MS);
    };

    const refreshWhenVisible = () => {
      if (document.visibilityState !== 'visible') return;
      if (timer !== undefined) window.clearTimeout(timer);
      void loadLogs(appliedFilters, { background: true }).finally(scheduleNextRefresh);
    };

    document.addEventListener('visibilitychange', refreshWhenVisible);
    scheduleNextRefresh();

    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, [activeTab, appliedFilters, liveLogsEnabled, loadLogs]);

  const refreshCurrentTab = () => {
    if (activeTab === 0) void loadOverview();
    if (activeTab === 1) void loadRuntimeConfig();
    if (activeTab === 2) void loadLogs(appliedFilters);
  };

  const currentTabLoading = activeTab === 0
    ? overviewLoading
    : activeTab === 1
      ? runtimeLoading
      : logsLoading;

  const applyLogFilters = (event: FormEvent) => {
    event.preventDefault();
    const filters = buildLogFilters(filterDraft);
    setAppliedFilters(filters);
    void loadLogs(filters);
  };

  const clearLogFilters = () => {
    setFilterDraft(EMPTY_LOG_FILTERS);
    const filters = { limit: DEFAULT_LOG_LIMIT };
    setAppliedFilters(filters);
    void loadLogs(filters);
  };

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        sx={{ alignItems: { md: 'flex-start' }, justifyContent: 'space-between', gap: 2, mb: 3 }}
      >
        <Box>
          <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1, mb: 0.5 }}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 800 }}>
              Vận hành hệ thống
            </Typography>
            <Chip icon={<LockOutlinedIcon />} color="info" label="Chỉ đọc" size="small" />
          </Stack>
          <Typography color="text.secondary" sx={{ maxWidth: 760 }}>
            Theo dõi sức khỏe ứng dụng, cấu hình runtime an toàn và log gần đây để hỗ trợ chẩn đoán.
            Trang này không cho phép thay đổi cấu hình hoặc điều khiển hạ tầng.
          </Typography>
        </Box>
        <Button
          aria-label="Làm mới dữ liệu vận hành"
          disabled={currentTabLoading}
          onClick={refreshCurrentTab}
          startIcon={currentTabLoading ? <CircularProgress size={16} /> : <RefreshIcon />}
          variant="outlined"
        >
          Làm mới
        </Button>
      </Stack>

      <Alert icon={<LockOutlinedIcon />} severity="info" sx={{ mb: 3 }}>
        Chế độ chỉ đọc — bí mật, mật khẩu, token và khóa truy cập không được trả về hoặc hiển thị tại đây.
      </Alert>

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Tabs
          aria-label="Các nhóm dữ liệu vận hành"
          onChange={(_, value: number) => setActiveTab(value)}
          value={activeTab}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ borderBottom: 1, borderColor: 'divider', px: { xs: 1, sm: 2 } }}
        >
          <Tab id="operations-tab-0" aria-controls="operations-panel-0" label="Tổng quan" />
          <Tab id="operations-tab-1" aria-controls="operations-panel-1" label="Cấu hình runtime" />
          <Tab id="operations-tab-2" aria-controls="operations-panel-2" label="Log gần đây" />
        </Tabs>

        <Box
          aria-labelledby={`operations-tab-${activeTab}`}
          id={`operations-panel-${activeTab}`}
          role="tabpanel"
          sx={{ p: { xs: 2, sm: 3 } }}
        >
          {activeTab === 0 && (
            <OverviewPanel
              data={overview}
              error={overviewError}
              loading={overviewLoading}
              onRetry={() => void loadOverview()}
            />
          )}
          {activeTab === 1 && (
            <RuntimeConfigPanel
              data={runtimeConfig}
              error={runtimeError}
              loading={runtimeLoading}
              onRetry={() => void loadRuntimeConfig()}
            />
          )}
          {activeTab === 2 && (
            <LogsPanel
              data={logs}
              draft={filterDraft}
              error={logsError}
              loading={logsLoading}
              liveEnabled={liveLogsEnabled}
              lastUpdatedAt={logsUpdatedAt}
              onApply={applyLogFilters}
              onClear={clearLogFilters}
              onDraftChange={setFilterDraft}
              onRetry={() => void loadLogs(appliedFilters)}
              onToggleLive={setLiveLogsEnabled}
            />
          )}
        </Box>
      </Paper>
    </Box>
  );
}

function OverviewPanel({
  data,
  error,
  loading,
  onRetry,
}: {
  data: OperationsOverview | null;
  error: boolean;
  loading: boolean;
  onRetry: () => void;
}) {
  if (loading && !data) return <OverviewSkeleton />;
  if (error && !data) return <LoadError message="Không thể tải tổng quan vận hành." onRetry={onRetry} />;
  if (!data) return <EmptyState message="Chưa có dữ liệu tổng quan từ backend." />;

  const heapPercent = data.memory.heapMaxBytes > 0
    ? Math.min(100, Math.max(0, (data.memory.heapUsedBytes / data.memory.heapMaxBytes) * 100))
    : null;

  return (
    <Stack sx={{ gap: 3 }}>
      {error && <Alert severity="warning">Lần làm mới gần nhất thất bại. Dữ liệu bên dưới có thể đã cũ.</Alert>}
      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
        }}
      >
        <HealthCard icon={<CheckCircleOutlineIcon />} label="Ứng dụng" status={data.applicationStatus} />
        <HealthCard icon={<StorageOutlinedIcon />} label="Cơ sở dữ liệu" status={data.databaseStatus} />
      </Box>

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.25fr) minmax(300px, 0.75fr)' },
        }}
      >
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 750, mb: 2 }}>Phiên bản đang chạy</Typography>
            <InfoGrid items={[
              ['Ứng dụng', data.build.applicationName || 'Chưa có dữ liệu'],
              ['Phiên bản', data.build.version || 'Chưa có dữ liệu'],
              ['Git commit', data.build.gitCommit || 'Chưa có dữ liệu'],
              ['Thời điểm build', formatDateTime(data.build.buildTime)],
              ['Khởi động lúc', formatDateTime(data.startedAt)],
              ['Uptime', formatDuration(data.uptimeSeconds)],
              ['Profile', data.activeProfiles.length > 0 ? data.activeProfiles.join(', ') : 'Chưa có dữ liệu'],
              ['Múi giờ JVM', data.timezone || 'Chưa có dữ liệu'],
              ['Múi giờ nghiệp vụ', data.businessTimezone || 'Chưa có dữ liệu'],
            ]} />
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>Bộ nhớ JVM</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Ảnh chụp tại thời điểm truy vấn; không phải biểu đồ lịch sử hay cảnh báo CloudWatch.
            </Typography>
            <Stack sx={{ gap: 1 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}>
                <Typography variant="body2">Heap đã dùng</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  {formatBytes(data.memory.heapUsedBytes)} / {formatBytes(data.memory.heapMaxBytes)}
                </Typography>
              </Stack>
              <LinearProgress
                aria-label="Tỷ lệ heap JVM đã sử dụng"
                color={heapPercent !== null && heapPercent >= 85 ? 'warning' : 'primary'}
                value={heapPercent ?? 0}
                variant="determinate"
                sx={{ borderRadius: 999, height: 8 }}
              />
              <Typography variant="caption" color="text.secondary">
                {heapPercent === null ? 'Không có giới hạn heap để tính tỷ lệ.' : `${heapPercent.toFixed(1)}% heap tối đa tại thời điểm đo.`}
              </Typography>
              <Divider sx={{ my: 1 }} />
              <InfoGrid items={[
                ['Heap đã commit', formatBytes(data.memory.heapCommittedBytes)],
                ['Non-heap đã dùng', formatBytes(data.memory.nonHeapUsedBytes)],
                ['Thread đang sống', data.threads.live.toLocaleString('vi-VN')],
                ['Thread daemon', data.threads.daemon.toLocaleString('vi-VN')],
                ['Đỉnh thread', data.threads.peak.toLocaleString('vi-VN')],
                ['Java', data.javaVersion || 'Chưa có dữ liệu'],
                ['JVM', data.jvmName || 'Chưa có dữ liệu'],
              ]} />
            </Stack>
          </CardContent>
        </Card>
      </Box>
    </Stack>
  );
}

function RuntimeConfigPanel({
  data,
  error,
  loading,
  onRetry,
}: {
  data: RuntimeConfiguration | null;
  error: boolean;
  loading: boolean;
  onRetry: () => void;
}) {
  if (loading && !data) return <OverviewSkeleton />;
  if (error && !data) return <LoadError message="Không thể tải cấu hình runtime an toàn." onRetry={onRetry} />;
  if (!data) return <EmptyState message="Backend chưa cung cấp cấu hình runtime an toàn." />;

  const components = Array.isArray(data.components) ? data.components : [];
  return (
    <Stack sx={{ gap: 3 }}>
      {error && <Alert severity="warning">Lần làm mới gần nhất thất bại. Dữ liệu bên dưới có thể đã cũ.</Alert>}
      <Alert severity="success">
        Chỉ hiển thị allowlist cấu hình không nhạy cảm. Trạng thái “Đã cấu hình” không tiết lộ giá trị credential.
      </Alert>
      <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>Thành phần runtime</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              API chỉ trả trạng thái bật và đã cấu hình; không trả giá trị ENV, endpoint riêng tư hoặc credential.
            </Typography>
            {components.length === 0 ? (
              <EmptyState message="Chưa có trạng thái thành phần an toàn để hiển thị." compact />
            ) : (
              <Box
                sx={{
                  display: 'grid',
                  gap: 1.5,
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                }}
              >
                {components.map((component) => (
                  <RuntimeComponentCard key={component.component} item={component} />
                ))}
              </Box>
            )}
          </CardContent>
      </Card>
    </Stack>
  );
}

function LogsPanel({
  data,
  draft,
  error,
  loading,
  liveEnabled,
  lastUpdatedAt,
  onApply,
  onClear,
  onDraftChange,
  onRetry,
  onToggleLive,
}: {
  data: OperationsLogPage | null;
  draft: LogFilterDraft;
  error: boolean;
  loading: boolean;
  liveEnabled: boolean;
  lastUpdatedAt: Date | null;
  onApply: (event: FormEvent) => void;
  onClear: () => void;
  onDraftChange: (draft: LogFilterDraft) => void;
  onRetry: () => void;
  onToggleLive: (enabled: boolean) => void;
}) {
  return (
    <Stack sx={{ gap: 2.5 }}>
      <Alert severity="warning">
        Đây là technical log gần thời gian thực của tiến trình backend hiện tại, không phải CloudWatch và không phải Audit Log.
        Dữ liệu sẽ mất khi restart/deploy và không bảo đảm đầy đủ lịch sử.
        {' '}
        <Link component={RouterLink} to={ROUTES.ADMIN.AUDIT_LOGS}>Mở Audit Log nghiệp vụ</Link>
      </Alert>

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', gap: 1 }}
      >
        <FormControlLabel
          control={(
            <Switch
              checked={liveEnabled}
              onChange={(event) => onToggleLive(event.target.checked)}
              slotProps={{ input: { 'aria-label': 'Tự động cập nhật technical log' } }}
            />
          )}
          label={`Theo dõi trực tiếp (mỗi ${LIVE_LOG_REFRESH_INTERVAL_MS / 1_000} giây)`}
        />
        <Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}>
          <Chip
            color={liveEnabled ? 'success' : 'default'}
            label={liveEnabled ? 'LIVE' : 'Đã tạm dừng'}
            size="small"
            variant={liveEnabled ? 'filled' : 'outlined'}
          />
          <Typography aria-live="polite" color="text.secondary" variant="caption">
            Cập nhật gần nhất: {lastUpdatedAt ? formatDateTime(lastUpdatedAt.toISOString()) : 'Chưa có dữ liệu'}
          </Typography>
        </Stack>
      </Stack>

      <Box
        component="form"
        onSubmit={onApply}
        sx={{
          alignItems: { md: 'flex-start' },
          display: 'grid',
          gap: 1.5,
          gridTemplateColumns: { xs: '1fr', md: '180px minmax(180px, 1fr) minmax(180px, 1fr) auto' },
        }}
      >
        <FormControl size="small">
          <InputLabel id="log-level-label">Mức độ</InputLabel>
          <Select
            label="Mức độ"
            labelId="log-level-label"
            onChange={(event) => onDraftChange({ ...draft, level: event.target.value as LogFilterDraft['level'] })}
            value={draft.level}
          >
            {LOG_LEVELS.map((option) => (
              <MenuItem key={option.value || 'all'} value={option.value}>{option.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField
          slotProps={{ htmlInput: { maxLength: 120 } }}
          label="Tìm trong nội dung"
          onChange={(event) => onDraftChange({ ...draft, query: event.target.value })}
          placeholder="Ví dụ: final-test"
          size="small"
          value={draft.query}
        />
        <TextField
          slotProps={{ htmlInput: { maxLength: 128 } }}
          label="Correlation ID"
          onChange={(event) => onDraftChange({ ...draft, correlationId: event.target.value })}
          placeholder="Theo dõi một request"
          size="small"
          value={draft.correlationId}
        />
        <Stack direction="row" sx={{ gap: 1 }}>
          <Button disabled={loading} type="submit" variant="contained">Lọc log</Button>
          <Button disabled={loading} onClick={onClear} type="button" variant="text">Xóa lọc</Button>
        </Stack>
      </Box>

      <Typography variant="caption" color="text.secondary">
        Nội dung log được backend làm sạch và tiếp tục được che dữ liệu nhạy cảm tại giao diện. Tối đa {DEFAULT_LOG_LIMIT} bản ghi mỗi lần truy vấn.
      </Typography>

      {loading && !data ? (
        <Box aria-label="Đang tải log" sx={{ display: 'grid', placeItems: 'center', minHeight: 220 }}>
          <CircularProgress />
        </Box>
      ) : error && !data ? (
        <LoadError message="Không thể tải log gần đây." onRetry={onRetry} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState message="Không có log phù hợp với bộ lọc hiện tại." />
      ) : (
        <>
          {error && <Alert severity="warning">Lần làm mới gần nhất thất bại. Danh sách bên dưới có thể đã cũ.</Alert>}
          <Typography aria-live="polite" variant="body2" color="text.secondary">
            Hiển thị {data.returned.toLocaleString('vi-VN')} bản ghi từ bộ đệm tối đa {data.capacity.toLocaleString('vi-VN')} bản ghi.
            {' '}Tiến trình hiện tại khởi động lúc {formatDateTime(data.currentProcessStartedAt)}.
          </Typography>
          <TableContainer component={Paper} variant="outlined">
            <Table aria-label="Log gần đây của tiến trình backend" size="small" sx={{ minWidth: 820 }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 180 }}>Thời gian</TableCell>
                  <TableCell sx={{ width: 100 }}>Mức độ</TableCell>
                  <TableCell sx={{ width: 200 }}>Logger</TableCell>
                  <TableCell>Nội dung đã làm sạch</TableCell>
                  <TableCell sx={{ width: 180 }}>Correlation ID</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((entry, index) => (
                  <TableRow key={`${entry.timestamp}-${entry.correlationId ?? 'none'}-${index}`} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(entry.timestamp)}</TableCell>
                    <TableCell><Chip color={logLevelColor(entry.level)} label={entry.level} size="small" /></TableCell>
                    <TableCell sx={{ maxWidth: 220, overflowWrap: 'anywhere' }}>{entry.logger || '—'}</TableCell>
                    <TableCell sx={{ minWidth: 320, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
                      {redactLogMessage(entry.message)}
                      {entry.exception && (
                        <Box component="details" sx={{ mt: 1 }}>
                          <Typography component="summary" variant="caption" sx={{ cursor: 'pointer', fontWeight: 700 }}>
                            Chi tiết ngoại lệ đã làm sạch
                          </Typography>
                          <Typography
                            component="pre"
                            variant="caption"
                            sx={{ m: 0, mt: 1, maxHeight: 220, overflow: 'auto', whiteSpace: 'pre-wrap' }}
                          >
                            {redactLogMessage(entry.exception)}
                          </Typography>
                        </Box>
                      )}
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{entry.correlationId || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Stack>
  );
}

function HealthCard({
  icon,
  label,
  status,
}: {
  icon: React.ReactNode;
  label: string;
  status: OperationsHealthStatus;
}) {
  const presentation = healthPresentation(status);
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
          <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
            <Box sx={{ color: `${presentation.color}.main`, display: 'flex' }}>{icon}</Box>
            <Box>
              <Typography variant="body2" color="text.secondary">{label}</Typography>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>{presentation.label}</Typography>
            </Box>
          </Stack>
          <Chip color={presentation.color} icon={presentation.icon} label={status || 'UNKNOWN'} size="small" />
        </Stack>
      </CardContent>
    </Card>
  );
}

function RuntimeComponentCard({ item }: { item: RuntimeComponentStatus }) {
  return (
    <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 1.5 }}>
      <Typography sx={{ fontWeight: 700, mb: 1 }}>
        {RUNTIME_COMPONENT_LABELS[item.component] ?? 'Thành phần hệ thống'}
      </Typography>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75 }}>
        <Chip
          color={item.enabled ? 'success' : 'default'}
          label={item.enabled ? 'Đang bật' : 'Đang tắt'}
          size="small"
          variant={item.enabled ? 'filled' : 'outlined'}
        />
        <Chip
          color={item.configured ? 'info' : 'warning'}
          label={item.configured ? 'Đã cấu hình' : 'Chưa cấu hình'}
          size="small"
          variant="outlined"
        />
      </Stack>
    </Box>
  );
}

function InfoGrid({ items }: { items: Array<[string, string]> }) {
  return (
    <Box component="dl" sx={{ display: 'grid', gap: 1.25, m: 0 }}>
      {items.map(([label, value]) => (
        <Box
          key={label}
          sx={{
            display: 'grid',
            gap: 1,
            gridTemplateColumns: { xs: '1fr', sm: 'minmax(130px, 0.45fr) minmax(0, 1fr)' },
          }}
        >
          <Typography component="dt" variant="body2" color="text.secondary">{label}</Typography>
          <Typography component="dd" variant="body2" sx={{ fontWeight: 650, m: 0, overflowWrap: 'anywhere' }}>
            {value}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function OverviewSkeleton() {
  return (
    <Box aria-label="Đang tải dữ liệu vận hành" sx={{ display: 'grid', gap: 2 }}>
      <Skeleton height={110} variant="rounded" />
      <Skeleton height={280} variant="rounded" />
    </Box>
  );
}

function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Alert
      action={<Button color="inherit" onClick={onRetry} size="small">Thử lại</Button>}
      severity="error"
    >
      {message} Vui lòng kiểm tra kết nối và thử lại.
    </Alert>
  );
}

function EmptyState({ message, compact = false }: { message: string; compact?: boolean }) {
  return (
    <Box sx={{ display: 'grid', minHeight: compact ? 100 : 220, placeItems: 'center', textAlign: 'center', p: 2 }}>
      <Box>
        <InfoOutlinedIcon color="disabled" sx={{ fontSize: 36, mb: 0.5 }} />
        <Typography color="text.secondary">{message}</Typography>
      </Box>
    </Box>
  );
}
