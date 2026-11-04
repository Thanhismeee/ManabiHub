import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminLayout } from './AdminLayout';
import { ROLES } from '../constants/roles';
import { ADMIN_MENU } from '../navigation/adminMenu';

// Mock DashboardLayout to inspect the props passed to it
vi.mock('./DashboardLayout', () => ({
  DashboardLayout: vi.fn(() => <div data-testid="mock-dashboard-layout" />)
}));
import { DashboardLayout } from './DashboardLayout';

function renderAdminLayout(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AdminLayout />
    </MemoryRouter>
  );
}

describe('AdminLayout Navigation & RBAC', () => {
  it('passes SYSTEM_ADMIN to allowedRoles for /admin/users', () => {
    renderAdminLayout('/admin/users');
    expect(DashboardLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedRoles: [ROLES.SYSTEM_ADMIN],
        sessionKind: 'admin',
        menuItems: ADMIN_MENU
      }),
      undefined
    );
  });

  it('passes only SYSTEM_ADMIN to allowedRoles for /admin/operations', () => {
    renderAdminLayout('/admin/operations');
    expect(DashboardLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedRoles: [ROLES.SYSTEM_ADMIN],
        sessionKind: 'admin',
        menuItems: ADMIN_MENU,
      }),
      undefined,
    );
  });

  it('passes FINANCE_MANAGER to allowedRoles for /admin/payouts', () => {
    renderAdminLayout('/admin/payouts');
    expect(DashboardLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedRoles: [ROLES.FINANCE_MANAGER],
        sessionKind: 'admin',
        menuItems: ADMIN_MENU
      }),
      undefined
    );
  });

  it('passes COURSE_MANAGER to allowedRoles for /admin/courses/approvals', () => {
    renderAdminLayout('/admin/courses/approvals');
    expect(DashboardLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedRoles: [ROLES.COURSE_MANAGER],
        sessionKind: 'admin',
        menuItems: ADMIN_MENU
      }),
      undefined
    );
  });

  it('passes INTERNAL_ROLES to allowedRoles for unknown routes', () => {
    renderAdminLayout('/admin/unknown-path');
    expect(DashboardLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedRoles: [ROLES.SYSTEM_ADMIN, ROLES.COURSE_MANAGER, ROLES.FINANCE_MANAGER],
        sessionKind: 'admin',
        menuItems: ADMIN_MENU
      }),
      undefined
    );
  });
});
