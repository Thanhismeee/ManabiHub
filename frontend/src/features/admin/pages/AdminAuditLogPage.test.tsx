import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminAuditApi } from '../api/adminAuditApi';
import { normalizeAuditRoleFilter } from '../utils/auditRoleFilter';
import { AdminAuditLogPage } from './AdminAuditLogPage';

vi.mock('../../../shared/auth/authSession', () => ({
  getAuthSession: vi.fn(() => ({ roles: ['SYSTEM_ADMIN'] })),
  hasAnyRole: vi.fn(() => true),
}));

vi.mock('../api/adminAuditApi', () => ({
  adminAuditApi: {
    getAuditLogs: vi.fn(),
    getAuditLogDetail: vi.fn(),
  },
}));

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminAuditLogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AdminAuditLogPage', () => {
  beforeEach(() => {
    vi.mocked(adminAuditApi.getAuditLogs).mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 15,
      totalPages: 2,
      first: true,
      last: false,
    });
  });

  it('suggests active roles while typing and applies the canonical role code', async () => {
    renderPage();
    await waitFor(() => expect(adminAuditApi.getAuditLogs).toHaveBeenCalled());

    fireEvent.change(screen.getByRole('combobox', { name: 'Vai trò' }), {
      target: { value: 'finance' },
    });
    fireEvent.click(await screen.findByRole('option', {
      name: /Quản lý tài chính.*FINANCE_MANAGER/i,
    }));
    fireEvent.click(screen.getByRole('button', { name: 'Lọc' }));

    await waitFor(() => {
      expect(adminAuditApi.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({
        page: 0,
        role: 'FINANCE_MANAGER',
        size: 10,
      }));
    });
  });

  it('uses the same compact pagination pattern as payout and violation queues', async () => {
    renderPage();

    expect(await screen.findByText('15 nhật ký · Trang 1/2')).toBeInTheDocument();
    expect(screen.queryByText('Số dòng/trang')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /page 2/i }));

    await waitFor(() => {
      expect(adminAuditApi.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({
        page: 1,
        size: 10,
      }));
    });
    expect(screen.getByText('15 nhật ký · Trang 2/2')).toBeInTheDocument();
  });
});

describe('normalizeAuditRoleFilter', () => {
  it('resolves unique code and Vietnamese label fragments', () => {
    expect(normalizeAuditRoleFilter('fin')).toBe('FINANCE_MANAGER');
    expect(normalizeAuditRoleFilter('quản lý khóa học')).toBe('COURSE_MANAGER');
  });

  it('keeps a custom historical role searchable', () => {
    expect(normalizeAuditRoleFilter('teacher candidate')).toBe('TEACHER_CANDIDATE');
  });
});
