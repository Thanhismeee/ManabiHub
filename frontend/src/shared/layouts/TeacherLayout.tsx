import React from 'react';
import { useLocation } from 'react-router-dom';
import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';
import { TEACHER_MENU } from '../navigation/teacherMenu';
import { DashboardLayout } from './DashboardLayout';

export const TeacherLayout: React.FC = () => {
  const location = useLocation();
  const isKycRoute = location.pathname.startsWith(ROUTES.TEACHER.KYC);

  return (
    <DashboardLayout
      allowedRoles={isKycRoute ? [ROLES.STUDENT, ROLES.TEACHER] : [ROLES.TEACHER]}
      menuItems={TEACHER_MENU}
      sessionKind="public"
    />
  );
};
