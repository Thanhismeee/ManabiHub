import React, { useEffect, useState } from 'react';
import { AppBar, Toolbar, Typography, Box, Button, IconButton, Avatar, Badge, Tooltip } from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import { Link, useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import {
  clearAuthSession,
  getAuthSession,
  subscribeToAuthSessionChanges,
} from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import { AccountMenu } from '../../../shared/layouts/AccountMenu';
import { NotificationMenu } from '../../../shared/layouts/NotificationMenu';
import { useUnreadCount } from '../../../features/notifications/hooks/useNotifications';

export const LandingHeader: React.FC = () => {
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);
  const [profileAnchorEl, setProfileAnchorEl] = useState<null | HTMLElement>(null);
  const [notificationAnchorEl, setNotificationAnchorEl] = useState<null | HTMLElement>(null);
  const [session, setSession] = useState(() => getAuthSession('public'));
  const avatarLabel = session?.email?.trim().charAt(0).toUpperCase() || 'U';
  const notificationPath = session?.roles.includes(ROLES.TEACHER)
    ? ROUTES.TEACHER.NOTIFICATIONS
    : ROUTES.STUDENT.NOTIFICATIONS;
  const { data: unreadCount = 0 } = useUnreadCount(Boolean(session));

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 60);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(
    () => subscribeToAuthSessionChanges(() => setSession(getAuthSession('public'))),
    [],
  );

  const handleLogout = () => {
    setProfileAnchorEl(null);
    setNotificationAnchorEl(null);
    setSession(null);
    clearAuthSession('public');
    window.location.replace(ROUTES.PUBLIC.HOME);
  };

  return (
    <AppBar
      position="sticky"
      sx={{
        position: { xs: 'relative', md: 'sticky' },
        zIndex: 1100,
        width: '100%',
        overflow: 'hidden',
        bgcolor: scrolled ? 'rgba(255, 255, 255, 0.97)' : 'rgba(255, 255, 255, 0.92)',
        color: '#1A1A2E',
        borderBottom: scrolled ? '1px solid #e8e0d8' : '1px solid transparent',
        boxShadow: scrolled ? '0 2px 20px rgba(0,0,0,0.06)' : 'none',
        backdropFilter: 'blur(12px)',
        transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      <Toolbar sx={{ display: 'flex', justifyContent: 'space-between', minWidth: 0, minHeight: { xs: 56, sm: 64 }, px: { xs: 1, sm: 1.5, md: 4 }, py: { xs: 0.5, sm: 1 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <Box
            component={Link}
            to={ROUTES.PUBLIC.HOME}
            sx={{ display: 'flex', alignItems: 'center', gap: 1, textDecoration: 'none', color: 'inherit' }}
          >
            <Box
              sx={{
                height: { xs: 40, sm: 54 },
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'transform 0.3s ease',
                '&:hover': { transform: 'scale(1.08)' },
              }}
            >
              <Box
                component="img"
                src="/manabihub-header-logo.png"
                alt="ManabiHub"
                sx={{ display: 'block', height: { xs: 40, sm: 54 }, width: 'auto', maxWidth: '100%' }}
              />
            </Box>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 } }}>
          {!session && (
            <Typography
              component={Link}
              to={ROUTES.TEACHER.KYC}
              sx={{
                textDecoration: 'none',
                color: '#475569',
                fontWeight: 600,
                fontSize: '0.9rem',
                display: { xs: 'none', lg: 'block' },
                transition: 'color 0.3s ease',
                '&:hover': { color: '#C41E3A' },
              }}
            >
              Trở thành giảng viên
            </Typography>
          )}

          {session ? (
            <>
              <Tooltip title="Thông báo">
                <IconButton
                  onClick={(event) => setNotificationAnchorEl(event.currentTarget)}
                  aria-label="Mở thông báo"
                  aria-controls={notificationAnchorEl ? 'notification-menu' : undefined}
                  aria-haspopup="true"
                  aria-expanded={Boolean(notificationAnchorEl)}
                  sx={{ color: '#475569' }}
                >
                  <Badge badgeContent={unreadCount} color="error" max={99}>
                    <NotificationsIcon />
                  </Badge>
                </IconButton>
              </Tooltip>
              <NotificationMenu
                anchorEl={notificationAnchorEl}
                onClose={() => setNotificationAnchorEl(null)}
                onViewAll={() => {
                  setNotificationAnchorEl(null);
                  navigate(notificationPath);
                }}
                scopeKey={session.subject}
              />
              <IconButton
                onClick={(event) => setProfileAnchorEl(event.currentTarget)}
                aria-label="Mở menu tài khoản"
                aria-controls={profileAnchorEl ? 'account-menu' : undefined}
                aria-haspopup="true"
                aria-expanded={Boolean(profileAnchorEl)}
                sx={{
                  ml: 1,
                  p: 0.5,
                  border: '2px solid transparent',
                  transition: 'border 0.2s',
                  '&:hover': { borderColor: '#e2e8f0' },
                }}
              >
                <Avatar sx={{ width: 36, height: 36, bgcolor: '#C41E3A', fontSize: '1rem', fontWeight: 700 }}>
                  {avatarLabel}
                </Avatar>
              </IconButton>
              <AccountMenu
                session={session}
                anchorEl={profileAnchorEl}
                onClose={() => setProfileAnchorEl(null)}
                onLogout={handleLogout}
              />
            </>
          ) : (
            <Button
              variant="contained"
              onClick={() => navigate(ROUTES.PUBLIC.LOGIN)}
              sx={{
                textTransform: 'none',
                fontWeight: 600,
                whiteSpace: 'nowrap',
                minWidth: 0,
                maxWidth: { xs: 'calc(100vw - 72px)', sm: 'none' },
                bgcolor: '#1B2A4A',
                color: 'white',
                borderRadius: 2,
                px: { xs: 1, sm: 3 },
                py: { xs: 1, sm: 1.25 },
                fontSize: { xs: '0.72rem', sm: '0.875rem' },
                transition: 'all 0.3s ease',
                '&:hover': {
                  bgcolor: '#2A3F6A',
                  transform: 'translateY(-1px)',
                  boxShadow: '0 4px 12px rgba(27, 42, 74, 0.3)',
                },
              }}
            >
              Đăng nhập / Đăng ký
            </Button>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
};
