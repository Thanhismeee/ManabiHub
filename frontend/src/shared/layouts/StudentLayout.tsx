import React from 'react';
import { ROLES } from '../constants/roles';
import { STUDENT_MENU } from '../navigation/studentMenu';
import { DashboardLayout } from './DashboardLayout';

export const StudentLayout: React.FC = () => {
  return (
    <DashboardLayout
      allowedRoles={[ROLES.STUDENT]}
      menuItems={STUDENT_MENU}
      sessionKind="public"
    />
  );
};
