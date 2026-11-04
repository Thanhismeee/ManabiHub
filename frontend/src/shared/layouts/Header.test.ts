import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ROLES } from '../constants/roles';
import { getHeaderBrand } from './headerBrand';
import { Header } from './Header';
import { getStudentWallet } from '../../features/wallet/services/studentWalletService';

vi.mock('../../features/notifications/hooks/useNotifications', () => ({
  useUnreadCount: () => ({ data: 0 }),
  useMarkAsRead: () => ({ mutate: vi.fn() }),
}));

vi.mock('../../features/wallet/services/studentWalletService', () => ({
  getStudentWallet: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderHeader(session: Parameters<typeof Header>[0]['session']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(
        MemoryRouter,
        { initialEntries: ['/'] },
        createElement(Header, { session }),
      ),
    ),
  );
}

describe('getHeaderBrand', () => {
  it('uses role-specific branding for teacher and admin sessions', () => {
    expect(getHeaderBrand({ kind: 'public', roles: [ROLES.TEACHER] })).toBe('ManabiTeacher');
    expect(getHeaderBrand({ kind: 'admin', roles: [ROLES.COURSE_MANAGER] })).toBe('ManabiAdmin');
  });

  it('keeps the public brand for students and guests', () => {
    expect(getHeaderBrand({ kind: 'public', roles: [ROLES.STUDENT] })).toBe('ManabiHub');
    expect(getHeaderBrand()).toBe('ManabiHub');
  });

  it('keeps the Admin Portal brand static instead of linking to the public landing page', () => {
    renderHeader({
      kind: 'admin',
      token: 'token',
      subject: 'admin-1',
      email: 'admin@example.com',
      roles: [ROLES.SYSTEM_ADMIN],
      expiresAt: Date.now() + 60_000,
    });

    const brand = screen.getByLabelText('ManabiAdmin');
    expect(brand.tagName).toBe('DIV');
    expect(brand).not.toHaveAttribute('href');
  });

  it('shows an actionable student wallet summary and clear destinations', async () => {
    vi.mocked(getStudentWallet).mockResolvedValue({
      balance: 250_000,
      frozenBalance: 0,
      availableBalance: 250_000,
      withdrawableBalance: 100_000,
      availableWithdrawableBalance: 100_000,
      currency: 'VND',
    });

    renderHeader({
      kind: 'public',
      token: 'token',
      subject: 'student-1',
      email: 'student@example.com',
      roles: [ROLES.STUDENT],
      expiresAt: Date.now() + 60_000,
    });

    fireEvent.click(screen.getByLabelText('Mở menu tài khoản'));

    expect(screen.getByText('Khóa học của tôi')).toBeInTheDocument();
    expect(screen.getByText('Hồ sơ cá nhân')).toBeInTheDocument();
    expect(screen.getByText('Ví học viên')).toBeInTheDocument();
    expect(screen.getByText('Trở thành giảng viên')).toBeInTheDocument();
    expect(screen.getByText('Thông báo')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/Số dư mua khóa học:/)).toBeInTheDocument());
    expect(screen.getByText(/250\.000/)).toBeInTheDocument();
    expect(screen.getByText('Mở ví')).toBeInTheDocument();
  });
});
