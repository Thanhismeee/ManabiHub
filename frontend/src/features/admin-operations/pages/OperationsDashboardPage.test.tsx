import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { operationsService } from '../services/operationsService';
import type {
  OperationsLogPage,
  OperationsOverview,
  RuntimeConfiguration,
} from '../types/operationsTypes';
import { OperationsDashboardPage } from './OperationsDashboardPage';

vi.mock('../services/operationsService', () => ({
  operationsService: {
    getOverview: vi.fn(),
    getRuntimeConfiguration: vi.fn(),
    getRecentLogs: vi.fn(),
  },
}));

const overviewMock = vi.mocked(operationsService.getOverview);
const runtimeMock = vi.mocked(operationsService.getRuntimeConfiguration);
const logsMock = vi.mocked(operationsService.getRecentLogs);

const overview: OperationsOverview = {
  applicationStatus: 'UP',
  databaseStatus: 'UP',
  startedAt: '2026-08-26T08:00:00Z',
  uptimeSeconds: 7_500,
  activeProfiles: ['prod'],
  timezone: 'UTC',
  businessTimezone: 'Asia/Ho_Chi_Minh',
  javaVersion: '25',
  jvmName: 'OpenJDK 64-Bit Server VM',
  memory: {
    heapUsedBytes: 256 * 1024 * 1024,
    heapCommittedBytes: 512 * 1024 * 1024,
    heapMaxBytes: 1024 * 1024 * 1024,
    nonHeapUsedBytes: 64 * 1024 * 1024,
  },
  threads: { live: 42, daemon: 35, peak: 51 },
  build: {
    applicationName: 'ManabiHub',
    version: '1.11.0',
    buildTime: '2026-08-26T07:30:00Z',
    gitCommit: 'abc1234',
  },
};

const runtime: RuntimeConfiguration = {
  components: [
    { component: 'DATABASE', configured: true, enabled: true },
    { component: 'VNPAY', configured: true, enabled: false },
    { component: 'JWT_SIGNING', configured: true, enabled: true },
  ],
};

const logs: OperationsLogPage = {
  logSource: 'CURRENT_INSTANCE_MEMORY',
  currentProcessStartedAt: '2026-08-26T08:00:00Z',
  capacity: 500,
  returned: 1,
  items: [{
    timestamp: '2026-08-26T09:00:00Z',
    level: 'ERROR',
    logger: 'com.manabihub.FinalTestService',
    message: 'Save failed password=hunter2 for dev@example.com',
    correlationId: 'req-final-test-1',
    exception: 'Authorization: Bearer secret-token',
  }],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <OperationsDashboardPage />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('OperationsDashboardPage', () => {
  beforeEach(() => {
    overviewMock.mockResolvedValue(overview);
    runtimeMock.mockResolvedValue(runtime);
    logsMock.mockResolvedValue(logs);
  });

  it('renders real health and JVM snapshots with a prominent read-only boundary', async () => {
    renderPage();

    expect(await screen.findByText('ManabiHub')).toBeInTheDocument();
    expect(screen.getAllByText('Hoạt động')).toHaveLength(2);
    expect(screen.getByText('256 MB / 1 GB')).toBeInTheDocument();
    expect(screen.getByText('Múi giờ JVM')).toBeInTheDocument();
    expect(screen.getByText('Múi giờ nghiệp vụ')).toBeInTheDocument();
    expect(screen.getByText('UTC')).toBeInTheDocument();
    expect(screen.getByText('Asia/Ho_Chi_Minh')).toBeInTheDocument();
    expect(screen.getByText(/ảnh chụp tại thời điểm truy vấn/i)).toBeInTheDocument();
    expect(screen.getAllByText(/chỉ đọc/i).length).toBeGreaterThan(0);

    for (const forbiddenAction of [/lưu/i, /chỉnh sửa/i, /restart/i, /deploy/i, /tải.*env/i]) {
      expect(screen.queryByRole('button', { name: forbiddenAction })).not.toBeInTheDocument();
    }
  });

  it('renders only configured/enabled component states without credential values', async () => {
    renderPage();
    await screen.findByText('ManabiHub');
    fireEvent.click(screen.getByRole('tab', { name: 'Cấu hình runtime' }));

    expect(screen.getByText('Cơ sở dữ liệu')).toBeInTheDocument();
    expect(screen.getByText('VNPay')).toBeInTheDocument();
    expect(screen.getByText('Ký JWT')).toBeInTheDocument();
    expect(screen.getAllByText('Đã cấu hình')).toHaveLength(3);
    expect(screen.queryByText(/secret|password|access.?key/i)).not.toBeInTheDocument();
  });

  it('applies level, query and correlation ID filters using a GET-only service', async () => {
    renderPage();
    await screen.findByText('ManabiHub');
    fireEvent.click(screen.getByRole('tab', { name: 'Log gần đây' }));

    fireEvent.mouseDown(screen.getByLabelText('Mức độ'));
    fireEvent.click(await screen.findByRole('option', { name: 'Lỗi (ERROR)' }));
    fireEvent.change(screen.getByLabelText('Tìm trong nội dung'), { target: { value: ' final-test ' } });
    fireEvent.change(screen.getByLabelText('Correlation ID'), { target: { value: ' req-42 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lọc log' }));

    await waitFor(() => expect(logsMock).toHaveBeenLastCalledWith({
      level: 'ERROR',
      query: 'final-test',
      correlationId: 'req-42',
      limit: 100,
    }));
    expect(screen.getByText(/technical log gần thời gian thực của tiến trình backend hiện tại/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mở Audit Log nghiệp vụ' })).toHaveAttribute('href', '/admin/audit-logs');
  });

  it('defensively redacts sensitive content in messages and exception details', async () => {
    renderPage();
    await screen.findByText('ManabiHub');
    fireEvent.click(screen.getByRole('tab', { name: 'Log gần đây' }));

    const table = await screen.findByRole('table', { name: 'Log gần đây của tiến trình backend' });
    expect(within(table).queryByText(/hunter2|dev@example\.com|secret-token/i)).not.toBeInTheDocument();
    expect(within(table).getByText(/Save failed password=\[ĐÃ CHE\]/)).toBeInTheDocument();
    fireEvent.click(within(table).getByText('Chi tiết ngoại lệ đã làm sạch'));
    expect(within(table).getByText('Authorization: [ĐÃ CHE]')).toBeInTheDocument();
  });

  it('refreshes technical logs near real-time only while the live log tab is active', async () => {
    renderPage();
    await screen.findByText('ManabiHub');
    const callsBeforeOpeningLogs = logsMock.mock.calls.length;

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('tab', { name: 'Log gần đây' }));

    expect(screen.getByText('LIVE')).toBeInTheDocument();
    expect(screen.getByText(/Theo dõi trực tiếp \(mỗi 3 giây\)/)).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
    });

    expect(logsMock.mock.calls.length).toBeGreaterThan(callsBeforeOpeningLogs);

    fireEvent.click(screen.getByRole('switch', { name: 'Tự động cập nhật technical log' }));
    const callsAfterPausing = logsMock.mock.calls.length;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(6_000);
    });

    expect(logsMock).toHaveBeenCalledTimes(callsAfterPausing);
    expect(screen.getByText('Đã tạm dừng')).toBeInTheDocument();
  });

  it('shows a retryable error without inventing health values', async () => {
    overviewMock.mockRejectedValueOnce(new Error('offline'));
    renderPage();

    expect(await screen.findByText(/Không thể tải tổng quan vận hành/)).toBeInTheDocument();
    expect(screen.queryByText('Hoạt động')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });
});
