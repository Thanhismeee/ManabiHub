import {
  Avatar,
  Box,
  Chip,
  Divider,
  ListItemIcon,
  Menu,
  MenuItem,
  Stack,
  Typography,
} from '@mui/material';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import AccountCircleOutlinedIcon from '@mui/icons-material/AccountCircleOutlined';
import ArrowForwardIosOutlinedIcon from '@mui/icons-material/ArrowForwardIosOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined';
import PasswordOutlinedIcon from '@mui/icons-material/PasswordOutlined';
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { AuthSession } from '../auth/authSession';
import { getDefaultRoute } from '../auth/authSession';
import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';
import { getStudentWallet } from '../../features/wallet/services/studentWalletService';
import { walletService } from '../../features/my-wallet/services/walletService';
import type { StudentWalletResponse } from '../../features/wallet/types';
import type { TeacherWallet } from '../../features/my-wallet/types/wallet.types';
import { useLocale } from '../i18n/locale';

interface AccountMenuProps {
  session: AuthSession;
  anchorEl: HTMLElement | null;
  onClose: () => void;
  onLogout: () => void;
}

const ROLE_LABELS: Record<string, string> = {
  [ROLES.STUDENT]: 'Học viên',
  [ROLES.TEACHER]: 'Giảng viên',
  [ROLES.SYSTEM_ADMIN]: 'Quản trị hệ thống',
  [ROLES.COURSE_MANAGER]: 'Quản lý khóa học',
  [ROLES.FINANCE_MANAGER]: 'Quản lý tài chính',
};

export function AccountMenu({ session, anchorEl, onClose, onLogout }: AccountMenuProps) {
  const { t } = useLocale();
  const navigate = useNavigate();
  const primaryRole = session.roles[0];
  const isStudent = session.kind === 'public' && session.roles.includes(ROLES.STUDENT);
  const isTeacher = session.kind === 'public' && session.roles.includes(ROLES.TEACHER);
  const profilePath = isTeacher ? ROUTES.TEACHER.PROFILE : isStudent ? ROUTES.STUDENT.PROFILE : null;
  const notificationPath = session.kind === 'admin'
    ? ROUTES.ADMIN.NOTIFICATIONS
    : isTeacher
      ? ROUTES.TEACHER.NOTIFICATIONS
      : ROUTES.STUDENT.NOTIFICATIONS;
  const studentWalletQuery = useQuery<StudentWalletResponse>({
    queryKey: ['account-menu-wallet', session.subject, 'student'],
    queryFn: getStudentWallet,
    enabled: Boolean(anchorEl) && isStudent,
    staleTime: 60_000,
    retry: false,
  });
  const teacherWalletQuery = useQuery<TeacherWallet>({
    queryKey: ['account-menu-wallet', session.subject, 'teacher'],
    queryFn: async () => (await walletService.getTeacherWallet()).data,
    enabled: Boolean(anchorEl) && isTeacher,
    staleTime: 60_000,
    retry: false,
  });

  const displayName = session.email?.split('@')[0] || 'Tài khoản';
  const avatarLabel = displayName.trim().charAt(0).toUpperCase() || 'U';
  const roleLabel = primaryRole ? ROLE_LABELS[primaryRole] || primaryRole : 'Tài khoản';
  const navigateTo = (path: string) => {
    onClose();
    navigate(path);
  };

  return (
    <Menu
      id="account-menu"
      anchorEl={anchorEl}
      open={Boolean(anchorEl)}
      onClose={onClose}
      anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
      transformOrigin={{ horizontal: 'right', vertical: 'top' }}
      slotProps={{
        paper: {
          sx: {
            mt: 1,
            minWidth: { xs: 260, sm: 300 },
            maxWidth: 'calc(100vw - 24px)',
            border: '1px solid',
            borderColor: 'grey.200',
            borderRadius: 2,
            boxShadow: '0 14px 36px rgba(15, 23, 42, 0.14)',
            overflow: 'hidden',
          },
        },
      }}
    >
      <Box sx={{ px: 2, py: 1.75 }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Avatar sx={{ width: 40, height: 40, bgcolor: '#C41E3A', fontWeight: 800 }}>
            {avatarLabel}
          </Avatar>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="subtitle2" noWrap sx={{ fontWeight: 800, color: 'text.primary' }}>
              {displayName}
            </Typography>
            <Typography variant="caption" noWrap sx={{ display: 'block', color: 'text.secondary' }}>
              {session.email || 'Chưa cập nhật email'}
            </Typography>
          </Box>
          <Chip
            label={t(roleLabel)}
            size="small"
            sx={{
              height: 24,
              bgcolor: 'rgba(196, 30, 58, 0.10)',
              color: '#A01830',
              fontSize: '0.7rem',
              fontWeight: 700,
            }}
          />
        </Stack>
      </Box>

      {(isStudent || isTeacher) && (
        <>
          <Divider />
          {isStudent && (
            <AccountWalletSummary
              kind="student"
              loading={studentWalletQuery.isLoading}
              error={studentWalletQuery.isError}
              wallet={studentWalletQuery.data}
              onOpen={() => navigateTo(ROUTES.STUDENT.PAYMENTS)}
            />
          )}
          {isStudent && isTeacher && <Divider />}
          {isTeacher && (
            <AccountWalletSummary
              kind="teacher"
              loading={teacherWalletQuery.isLoading}
              error={teacherWalletQuery.isError}
              wallet={teacherWalletQuery.data}
              onOpen={() => navigateTo(ROUTES.TEACHER.WALLET)}
            />
          )}
        </>
      )}

      <Divider />
      <Box sx={{ p: 0.75 }}>
        <MenuItem onClick={() => navigateTo(getDefaultRoute(session))} sx={{ borderRadius: 1.25, py: 1 }}>
          <ListItemIcon><MenuBookOutlinedIcon fontSize="small" /></ListItemIcon>
          {t(session.kind === 'admin' ? 'Tổng quan quản trị' : 'Khóa học của tôi')}
        </MenuItem>
        {profilePath && (
          <MenuItem onClick={() => navigateTo(profilePath)} sx={{ borderRadius: 1.25, py: 1 }}>
            <ListItemIcon><AccountCircleOutlinedIcon fontSize="small" /></ListItemIcon>
            {t('Hồ sơ cá nhân')}
          </MenuItem>
        )}
        {isStudent && (
          <MenuItem onClick={() => navigateTo(ROUTES.TEACHER.KYC)} sx={{ borderRadius: 1.25, py: 1 }}>
            <ListItemIcon><SchoolOutlinedIcon fontSize="small" /></ListItemIcon>
            {t('Trở thành giảng viên')}
          </MenuItem>
        )}
        <MenuItem onClick={() => navigateTo(notificationPath)} sx={{ borderRadius: 1.25, py: 1 }}>
          <ListItemIcon><NotificationsNoneOutlinedIcon fontSize="small" /></ListItemIcon>
          {t('Thông báo')}
        </MenuItem>
        {session.kind === 'admin' && (
          <MenuItem onClick={() => navigateTo(ROUTES.ADMIN.CHANGE_PASSWORD)} sx={{ borderRadius: 1.25, py: 1 }}>
            <ListItemIcon><PasswordOutlinedIcon fontSize="small" /></ListItemIcon>
            {t('Đổi mật khẩu')}
          </MenuItem>
        )}
      </Box>
      <Divider />
      <Box sx={{ p: 0.75 }}>
        <MenuItem
          onClick={onLogout}
          sx={{
            borderRadius: 1.25,
            py: 1,
            color: '#C41E3A',
            '&:hover': { bgcolor: 'rgba(196, 30, 58, 0.08)' },
          }}
        >
          <ListItemIcon sx={{ color: 'inherit' }}><LogoutOutlinedIcon fontSize="small" /></ListItemIcon>
          {t('Đăng xuất')}
        </MenuItem>
      </Box>
    </Menu>
  );
}

type AccountWalletSummaryProps = {
  kind: 'student' | 'teacher';
  loading: boolean;
  error: boolean;
  wallet?: StudentWalletResponse | TeacherWallet;
  onOpen: () => void;
};

function AccountWalletSummary({ kind, loading, error, wallet, onOpen }: AccountWalletSummaryProps) {
  const { t, locale } = useLocale();
  const isStudent = kind === 'student';
  const studentWallet = isStudent ? wallet as StudentWalletResponse | undefined : undefined;
  const teacherWallet = !isStudent ? wallet as TeacherWallet | undefined : undefined;
  const amount = isStudent ? studentWallet?.availableBalance : teacherWallet?.availableBalance;
  const currency = amount == null
    ? '—'
    : new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : locale === 'ko' ? 'ko-KR' : 'zh-CN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

  return (
    <Box sx={{ px: 2, py: 1.25, bgcolor: '#F8FAFC' }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
          <AccountBalanceWalletOutlinedIcon fontSize="small" sx={{ color: '#C41E3A' }} />
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" sx={{ display: 'block', fontWeight: 800, color: '#475467' }}>
              {t(isStudent ? 'Ví học viên' : 'Ví doanh thu')}
            </Typography>
            <Typography variant="body2" noWrap sx={{ fontWeight: 800 }}>
              {loading ? t('Đang cập nhật...') : error ? t('Chưa tải được số dư') : `${t(isStudent ? 'Số dư mua khóa học' : 'Số dư')}: ${currency}`}
            </Typography>
          </Box>
        </Stack>
        <Typography
          component="button"
          type="button"
          onClick={onOpen}
          sx={{
            border: 0,
            bgcolor: 'transparent',
            color: '#C41E3A',
            cursor: 'pointer',
            fontSize: '0.78rem',
            fontWeight: 800,
            whiteSpace: 'nowrap',
            '&:hover': { color: '#9D182E' },
          }}
        >
          {t('Mở ví')} <ArrowForwardIosOutlinedIcon sx={{ fontSize: '0.65rem', verticalAlign: 'middle' }} />
        </Typography>
      </Stack>
    </Box>
  );
}
