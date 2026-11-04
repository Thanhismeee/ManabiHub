import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import RuleIcon from '@mui/icons-material/Rule';
import MoneyOffIcon from '@mui/icons-material/MoneyOff';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import SettingsApplicationsOutlinedIcon from '@mui/icons-material/SettingsApplicationsOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import SportsEsportsOutlinedIcon from '@mui/icons-material/SportsEsportsOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import PolicyOutlinedIcon from '@mui/icons-material/PolicyOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import MonitorHeartOutlinedIcon from '@mui/icons-material/MonitorHeartOutlined';
import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';

export const ADMIN_MENU = [
  { title: 'Báo cáo học vụ', path: ROUTES.ADMIN.COHORT_REPORTS, icon: SpaceDashboardIcon, roles: [ROLES.COURSE_MANAGER, ROLES.SYSTEM_ADMIN] },
  {
    title: 'Tổng quan',
    path: ROUTES.ADMIN.DASHBOARD,
    icon: SpaceDashboardIcon,
    roles: [ROLES.SYSTEM_ADMIN, ROLES.COURSE_MANAGER, ROLES.FINANCE_MANAGER],
  },
  {
    title: 'Cấu hình hệ thống',
    path: ROUTES.ADMIN.SYSTEM_SETTINGS,
    icon: SettingsApplicationsOutlinedIcon,
    roles: [ROLES.SYSTEM_ADMIN],
  },
  {
    title: 'Vận hành hệ thống',
    path: ROUTES.ADMIN.OPERATIONS,
    icon: MonitorHeartOutlinedIcon,
    roles: [ROLES.SYSTEM_ADMIN],
  },
  {
    title: 'Phân quyền nội bộ',
    path: ROUTES.ADMIN.USERS,
    icon: ManageAccountsOutlinedIcon,
    roles: [ROLES.SYSTEM_ADMIN],
  },
  {
    title: 'Duyệt xác minh giảng viên',
    path: ROUTES.ADMIN.KYC_REVIEW,
    icon: FactCheckIcon,
    roles: [ROLES.COURSE_MANAGER],
  },
  {
    title: 'Duyệt khóa học',
    path: ROUTES.ADMIN.COURSE_APPROVAL,
    icon: RuleIcon,
    roles: [ROLES.COURSE_MANAGER],
  },
  {
    title: 'Duyệt hoàn tiền',
    path: ROUTES.ADMIN.REFUND_REVIEW,
    icon: MoneyOffIcon,
    roles: [ROLES.FINANCE_MANAGER],
  },
  {
    title: 'Báo cáo vi phạm',
    path: ROUTES.ADMIN.VIOLATIONS,
    icon: ReportProblemOutlinedIcon,
    roles: [ROLES.COURSE_MANAGER],
  },
  {
    title: 'Đối soát chi trả',
    path: ROUTES.ADMIN.PAYOUTS,
    icon: AccountBalanceOutlinedIcon,
    roles: [ROLES.FINANCE_MANAGER],
  },
  {
    title: 'Trò chơi & thưởng tuần',
    path: ROUTES.ADMIN.WEEKLY_CHALLENGES,
    icon: SportsEsportsOutlinedIcon,
    roles: [ROLES.COURSE_MANAGER],
  },
  {
    title: 'Doanh thu hệ thống',
    path: ROUTES.ADMIN.FINANCE_REVENUE,
    icon: TrendingUpOutlinedIcon,
    roles: [ROLES.FINANCE_MANAGER],
  },
  {
    title: 'Chi phí vận hành',
    path: ROUTES.ADMIN.FINANCE_EXPENSES,
    icon: ReceiptLongOutlinedIcon,
    roles: [ROLES.FINANCE_MANAGER],
  },
  {
    title: 'Hậu kiểm quyết định',
    path: ROUTES.ADMIN.DECISION_REVIEWS,
    icon: PolicyOutlinedIcon,
    roles: [ROLES.SYSTEM_ADMIN],
  },
  {
    title: 'Nhật ký hệ thống',
    path: ROUTES.ADMIN.AUDIT_LOGS,
    icon: HistoryOutlinedIcon,
    roles: [ROLES.SYSTEM_ADMIN],
  },
];
