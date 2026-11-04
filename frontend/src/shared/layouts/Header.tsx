import React, { useState } from 'react';
import {
  AppBar,
  Avatar,
  Badge,
  Box,
  Button,
  IconButton,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import NotificationsIcon from '@mui/icons-material/Notifications';
import LoginIcon from '@mui/icons-material/Login';
import { Link, useNavigate } from 'react-router-dom';
import {
  clearAuthSession,
  getLoginRoute,
  type AuthSession,
} from '../auth/authSession';
import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';
import { logoutAdminSession } from '../auth/adminAuthApi';
import { getHeaderBrand } from './headerBrand';
import { AccountMenu } from './AccountMenu';
import { NotificationMenu } from './NotificationMenu';
import { useUnreadCount } from '../../features/notifications/hooks/useNotifications';
import { LocaleSelect } from '../i18n/LocaleSelect';
import { useLocale } from '../i18n/locale';

interface HeaderProps {
  menuExpanded?: boolean;
  onMenuClick?: () => void;
  session?: AuthSession;
  showMenuIcon?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  menuExpanded = false,
  onMenuClick,
  session,
  showMenuIcon = false,
}) => {
  const navigate = useNavigate();
  const { t } = useLocale();
  const [accountAnchor, setAccountAnchor] = useState<HTMLElement | null>(null);
  const [notificationAnchor, setNotificationAnchor] = useState<HTMLElement | null>(null);
  const notificationPath = session ? getNotificationPath(session) : null;
  const avatarLabel = session?.email?.trim().charAt(0).toUpperCase() || 'U';
  const brandLabel = getHeaderBrand(session);
  const isAdminPortal = session?.kind === 'admin';
  const { data: unreadCount = 0 } = useUnreadCount(Boolean(session));

  const handleLogout = async () => {
    if (!session) return;

    setAccountAnchor(null);
    if (session.kind === 'admin') {
      const serverSessionRevoked = await logoutAdminSession();
      navigate(
        serverSessionRevoked
          ? getLoginRoute(session.kind)
          : `${getLoginRoute(session.kind)}?reason=logout-local-only`,
        { replace: true },
      );
      return;
    }

    clearAuthSession(session.kind);
    navigate(getLoginRoute(session.kind), { replace: true });
  };

  const brandContent = (
    <>
      <Box
        component="img"
        src="/manabihub-header-logo.png"
        alt="ManabiHub"
        sx={{ display: 'block', flexShrink: 0, height: { xs: 40, sm: 48 }, width: 'auto' }}
      />
      <Box
        component="span"
        sx={{
          display: { xs: 'none', sm: 'inline' },
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {brandLabel}
      </Box>
    </>
  );

  return (
    <AppBar
      position="fixed"
      elevation={1}
      sx={{
        zIndex: (theme) => theme.zIndex.drawer + 1,
        bgcolor: 'background.paper',
        color: 'text.primary',
      }}
    >
      <Toolbar sx={{ minWidth: 0, px: { xs: 1.5, sm: 2 } }}>
        {showMenuIcon && onMenuClick && (
          <Tooltip title={t(menuExpanded ? 'Thu gọn menu' : 'Mở menu')}>
            <IconButton
              edge="start"
              color="inherit"
              aria-label={t(menuExpanded ? 'Thu gọn menu' : 'Mở menu')}
              aria-expanded={menuExpanded}
              onClick={onMenuClick}
              sx={{ mr: { xs: 0.5, sm: 2 } }}
            >
              <MenuIcon />
            </IconButton>
          </Tooltip>
        )}

        {isAdminPortal ? (
          <Typography
            variant="h6"
            component="div"
            aria-label="ManabiAdmin"
            sx={{
              alignItems: 'center',
              display: 'flex',
              flexGrow: 1,
              flexShrink: 1,
              minWidth: 0,
              gap: 1,
              color: 'primary.main',
              fontWeight: 900,
              letterSpacing: '-0.5px',
            }}
          >
            {brandContent}
          </Typography>
        ) : (
          <Typography
            variant="h6"
            component={Link}
            to={ROUTES.PUBLIC.HOME}
            sx={{
              alignItems: 'center',
              display: 'flex',
              flexGrow: 1,
              flexShrink: 1,
              minWidth: 0,
              gap: 1,
              textDecoration: 'none',
              color: 'primary.main',
              fontWeight: 900,
              letterSpacing: '-0.5px',
            }}
          >
            {brandContent}
          </Typography>
        )}

        <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0, gap: { xs: 0.25, sm: 2 } }}>
          <LocaleSelect />
          {!session && (
            <Button
              component={Link}
              to={ROUTES.PUBLIC.HOME}
              color="inherit"
              sx={{
                display: { xs: 'none', md: 'flex' },
                textTransform: 'none',
                fontWeight: 600,
                color: 'text.secondary',
              }}
            >
              {t('Trang chủ')}
            </Button>
          )}

          {session && notificationPath ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Tooltip title={t('Thông báo')}>
                <IconButton
                  color="inherit"
                  aria-label={t('Mở thông báo')}
                  aria-controls={notificationAnchor ? 'notification-menu' : undefined}
                  aria-haspopup="true"
                  aria-expanded={Boolean(notificationAnchor)}
                  onClick={(event) => setNotificationAnchor(event.currentTarget)}
                  sx={{ color: 'text.secondary' }}
                >
                  <Badge badgeContent={unreadCount} color="error" max={99}>
                    <NotificationsIcon />
                  </Badge>
                </IconButton>
              </Tooltip>
              <Button
                color="inherit"
                aria-label={t('Mở menu tài khoản')}
                aria-controls={accountAnchor ? 'account-menu' : undefined}
                aria-haspopup="true"
                aria-expanded={Boolean(accountAnchor)}
                onClick={(event) => setAccountAnchor(event.currentTarget)}
                sx={{
                  textTransform: 'none',
                  color: 'text.primary',
                  borderRadius: 8,
                  minWidth: { xs: 40, sm: 'auto' },
                  pl: { xs: 0.5, sm: 0.5 },
                  pr: { xs: 0.5, sm: 1.5 },
                  py: 0.5,
                  '&:hover': { bgcolor: 'grey.100' },
                }}
              >
                <Avatar
                  sx={{
                    bgcolor: '#C41E3A',
                    width: 32,
                    height: 32,
                    mr: { xs: 0, sm: 1 },
                    fontSize: '0.875rem',
                    fontWeight: 700,
                  }}
                >
                  {avatarLabel}
                </Avatar>
                <Typography variant="body2" sx={{ fontWeight: 600, display: { xs: 'none', sm: 'block' } }}>
                  {session.email?.split('@')[0] || t('Tài khoản')}
                </Typography>
                <Box component="span" sx={{ display: { xs: 'none', sm: 'inline-flex' }, ml: 0.5, fontSize: '0.7rem', color: 'grey.500' }}>
                  ▼
                </Box>
              </Button>
              <NotificationMenu
                anchorEl={notificationAnchor}
                onClose={() => setNotificationAnchor(null)}
                onViewAll={() => {
                  setNotificationAnchor(null);
                  navigate(notificationPath);
                }}
                scopeKey={session.subject}
              />
            </Box>
          ) : (
            <Button startIcon={<LoginIcon />} onClick={() => navigate(ROUTES.PUBLIC.LOGIN)} sx={{ fontWeight: 600 }}>
              {t('Đăng nhập')}
            </Button>
          )}
        </Box>
      </Toolbar>

      {session && (
        <AccountMenu
          session={session}
          anchorEl={accountAnchor}
          onClose={() => setAccountAnchor(null)}
          onLogout={() => void handleLogout()}
        />
      )}
    </AppBar>
  );
};

function getNotificationPath(session: AuthSession) {
  if (session.kind === 'admin') return ROUTES.ADMIN.NOTIFICATIONS;
  if (session.roles.includes(ROLES.TEACHER)) return ROUTES.TEACHER.NOTIFICATIONS;
  return ROUTES.STUDENT.NOTIFICATIONS;
}
