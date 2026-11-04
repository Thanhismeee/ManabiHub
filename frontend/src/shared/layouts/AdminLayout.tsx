import React from 'react';
import { useLocation } from 'react-router-dom';
import { ROLES } from '../constants/roles';
import { ADMIN_MENU } from '../navigation/adminMenu';
import { DashboardLayout } from './DashboardLayout';

const INTERNAL_ROLES = [ROLES.SYSTEM_ADMIN, ROLES.COURSE_MANAGER, ROLES.FINANCE_MANAGER];

export const AdminLayout: React.FC = () => {
  const location = useLocation();
  const matchingMenuItem = [...ADMIN_MENU]
    .sort((left, right) => right.path.length - left.path.length)
    .find((item) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`));

  return (
    <DashboardLayout
      allowedRoles={matchingMenuItem?.roles ?? INTERNAL_ROLES}
      menuItems={ADMIN_MENU}
      sessionKind="admin"
    />
  );
};
