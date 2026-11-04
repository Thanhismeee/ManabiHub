import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { systemAdministrationService } from '../services/systemAdministrationService';
import { InternalAdminAccountsPage } from './InternalAdminAccountsPage';

vi.mock('../services/systemAdministrationService', () => ({
  systemAdministrationService: {
    listInternalAdmins: vi.fn(),
    updateInternalAdminRole: vi.fn(),
    inviteInternalAdmin: vi.fn(),
    resendInternalAdminInvitation: vi.fn(),
    updateInternalAdminStatus: vi.fn(),
  },
}));

const listInternalAdminsMock = vi.mocked(
  systemAdministrationService.listInternalAdmins,
);

afterEach(cleanup);

describe('InternalAdminAccountsPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    listInternalAdminsMock.mockResolvedValue([
      {
        id: 'admin-1',
        email: 'course.manager@manabihub.local',
        fullName: 'Course Manager',
        status: 'ACTIVE',
        role: 'COURSE_MANAGER',
        lastLoginAt: null,
        updatedAt: null,
        invitationStatus: 'NONE',
        invitationExpiresAt: null,
      },
    ]);
  });

  it('renders safe account fields and invitation guidance', async () => {
    render(<InternalAdminAccountsPage />);

    expect(await screen.findByText('Course Manager')).toBeInTheDocument();
    expect(screen.getByText('course.manager@manabihub.local')).toBeInTheDocument();
    expect(screen.getByText(/Người nhận tự đặt mật khẩu/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mời tài khoản' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Danh sách tài khoản nội bộ' }))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Tìm theo tên hoặc email')).toBeInTheDocument();
    expect(screen.getByLabelText('Trạng thái')).toBeInTheDocument();
    expect(screen.getAllByLabelText('Vai trò').length).toBeGreaterThan(0);
    expect(screen.queryByText(/password_hash/i)).not.toBeInTheDocument();
  });

  it('filters a large account table by name or email', async () => {
    listInternalAdminsMock.mockResolvedValueOnce([
      {
        id: 'admin-1',
        email: 'course@example.com',
        fullName: 'Course Manager',
        status: 'ACTIVE',
        role: 'COURSE_MANAGER',
        lastLoginAt: null,
        updatedAt: null,
        invitationStatus: 'ACCEPTED',
        invitationExpiresAt: null,
      },
      {
        id: 'admin-2',
        email: 'finance@example.com',
        fullName: 'Finance Manager',
        status: 'DISABLED',
        role: 'FINANCE_MANAGER',
        lastLoginAt: null,
        updatedAt: null,
        invitationStatus: 'ACCEPTED',
        invitationExpiresAt: null,
      },
    ]);

    render(<InternalAdminAccountsPage />);
    expect(await screen.findByText('Finance Manager')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Tìm theo tên hoặc email'), {
      target: { value: 'course@' },
    });

    expect(screen.getByText('Course Manager')).toBeInTheDocument();
    expect(screen.queryByText('Finance Manager')).not.toBeInTheDocument();
  });

  it('requires a reason before disabling access', async () => {
    const updateStatusMock = vi.mocked(
      systemAdministrationService.updateInternalAdminStatus,
    );
    updateStatusMock.mockResolvedValue({
      id: 'admin-1',
      email: 'course.manager@manabihub.local',
      fullName: 'Course Manager',
      status: 'DISABLED',
      role: 'COURSE_MANAGER',
      lastLoginAt: null,
      updatedAt: null,
      invitationStatus: 'NONE',
      invitationExpiresAt: null,
    });

    render(<InternalAdminAccountsPage />);
    const disableButton = await screen.findByRole('button', { name: 'Vô hiệu hóa' });
    fireEvent.click(disableButton);

    const dialog = screen.getByRole('dialog');
    const confirmButton = within(dialog).getByRole('button', { name: 'Vô hiệu hóa' });
    expect(confirmButton).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText('Lý do thay đổi'), {
      target: { value: 'Nhân sự đã rời nhóm' },
    });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(updateStatusMock).toHaveBeenCalledWith('admin-1', {
        status: 'DISABLED',
        reason: 'Nhân sự đã rời nhóm',
      });
    });
  });

  it('shows a retryable error state', async () => {
    listInternalAdminsMock.mockRejectedValueOnce(new Error('offline'));

    render(<InternalAdminAccountsPage />);

    expect(await screen.findByText(
      'Không thể tải danh sách quản trị viên. Vui lòng thử lại.',
    )).toBeInTheDocument();
    expect(screen.queryByText('Course Manager')).not.toBeInTheDocument();
  });
});
