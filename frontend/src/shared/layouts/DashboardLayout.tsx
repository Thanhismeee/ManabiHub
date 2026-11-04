import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Toolbar,
  useMediaQuery,
  useTheme,
  Typography,
} from '@mui/material';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import {
  getAuthSession,
  getDefaultRoute,
  getLoginRoute,
  hasAdminRefreshSession,
  hasAnyRole,
  subscribeToAuthSessionChanges,
  type AuthSession,
  type AuthSessionKind,
} from '../auth/authSession';
import { refreshAdminSessionWithStatus } from '../auth/adminAuthApi';
import { Header } from './Header';
import { ROUTES } from '../constants/routes';
import {
  COLLAPSED_DRAWER_WIDTH,
  DRAWER_WIDTH,
  Sidebar,
  type MenuItem,
} from './Sidebar';

interface DashboardLayoutProps {
  allowedRoles: readonly string[];
  menuItems: MenuItem[];
  sessionKind: AuthSessionKind;
}

const SIDEBAR_PREFERENCE_KEY = 'dashboard_sidebar_collapsed';

export function DashboardLayout({ allowedRoles, menuItems, sessionKind }: DashboardLayoutProps) {
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [desktopCollapsed, setDesktopCollapsed] = useState(() =>
    typeof window !== 'undefined'
      && window.localStorage.getItem(SIDEBAR_PREFERENCE_KEY) === 'true',
  );
  const [session, setSession] = useState<AuthSession | null>(
    () => getAuthSession(sessionKind),
  );
  const [restoringSession, setRestoringSession] = useState(
    () => sessionKind === 'admin'
      && !getAuthSession('admin')
      && hasAdminRefreshSession(),
  );
  const [restoreFailed, setRestoreFailed] = useState(false);
  const [restoreAttempt, setRestoreAttempt] = useState(0);

  useEffect(() => {
    setMobileOpen(false);
  }, [isMobile, location.pathname]);

  useEffect(() => {
    window.localStorage.setItem(SIDEBAR_PREFERENCE_KEY, String(desktopCollapsed));
  }, [desktopCollapsed]);

  useEffect(() => {
    let active = true;
    let restoreInProgress = false;
    const syncSession = () => {
      const currentSession = getAuthSession(sessionKind);
      if (currentSession || sessionKind !== 'admin' || !hasAdminRefreshSession()) {
        if (active) {
          setSession(currentSession);
          setRestoreFailed(false);
          setRestoringSession(false);
        }
        return;
      }

      if (restoreInProgress) {
        return;
      }
      restoreInProgress = true;
      setRestoreFailed(false);
      setRestoringSession(true);
      void refreshAdminSessionWithStatus()
        .then((result) => {
          if (active) {
            setSession(result.session);
            setRestoreFailed(result.status === 'transient-error');
          }
        })
        .finally(() => {
          restoreInProgress = false;
          if (active) {
            setRestoringSession(false);
          }
        });
    };
    const unsubscribe = subscribeToAuthSessionChanges(syncSession);
    syncSession();

    return () => {
      active = false;
      unsubscribe();
    };
  }, [restoreAttempt, sessionKind]);

  if (restoringSession) {
    return (
      <Box
        aria-label="Đang khôi phục phiên quản trị"
        sx={{
          alignItems: 'center',
          display: 'flex',
          justifyContent: 'center',
          minHeight: '100vh',
        }}
      >
        <CircularProgress size={32} />
      </Box>
    );
  }

  if (restoreFailed && !session) {
    return (
      <Box
        sx={{
          alignItems: 'center',
          display: 'flex',
          justifyContent: 'center',
          minHeight: '100vh',
          p: 2,
        }}
      >
        <Alert
          action={(
            <Button
              color="inherit"
              onClick={() => setRestoreAttempt((attempt) => attempt + 1)}
              size="small"
            >
              Thử lại
            </Button>
          )}
          severity="warning"
          sx={{ maxWidth: 560 }}
        >
          Chưa thể xác minh phiên quản trị do kết nối tạm thời gián đoạn.
          Phiên của bạn chưa bị đăng xuất.
        </Alert>
      </Box>
    );
  }

  if (!session) {
    return (
      <Navigate
        to={getLoginRoute(sessionKind)}
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }

  if (!hasAnyRole(session, allowedRoles)) {
    return <Navigate to={getDefaultRoute(session)} replace state={{ accessDenied: true }} />;
  }

  const collapsed = !isMobile && desktopCollapsed;
  const drawerWidth = collapsed ? COLLAPSED_DRAWER_WIDTH : DRAWER_WIDTH;

  const handleMenuClick = () => {
    if (isMobile) {
      setMobileOpen((open) => !open);
      return;
    }

    setDesktopCollapsed((value) => !value);
  };

  return (
    <Box sx={{ bgcolor: 'background.default', display: 'flex', minHeight: '100vh' }}>
      <Header
        menuExpanded={isMobile ? mobileOpen : !desktopCollapsed}
        onMenuClick={handleMenuClick}
        session={session}
        showMenuIcon
      />

      <Sidebar
        collapsed={collapsed}
        menuItems={menuItems}
        onClose={() => setMobileOpen(false)}
        open={isMobile ? mobileOpen : true}
        userRoles={session.roles}
        variant={isMobile ? 'temporary' : 'permanent'}
      />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          minWidth: 0,
          overflowX: 'hidden',
          p: { xs: 2, sm: 3 },
          pb: 0,
          transition: theme.transitions.create('width', {
            duration: theme.transitions.duration.shorter,
            easing: theme.transitions.easing.sharp,
          }),
          width: isMobile ? '100%' : `calc(100% - ${drawerWidth}px)`,
        }}
      >
        <Toolbar sx={{ mb: 1 }} />
        <Box sx={{ maxWidth: 1440, mx: 'auto', width: '100%', flexGrow: 1, pb: 4 }}>
          <Outlet />
        </Box>
        
        {/* Mini Footer */}
        <Box sx={{ mt: 'auto', py: 3, borderTop: '1px solid', borderColor: 'divider', display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between', gap: 2, minWidth: 0, color: 'text.secondary', fontSize: '0.85rem' }}>
          <Typography variant="body2" sx={{ maxWidth: '100%', overflowWrap: 'anywhere' }}>© 2026 ManabiHub. Bảo lưu mọi quyền.</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: { xs: 1.5, sm: 3 } }}>
            <Box component={Link} to={ROUTES.PUBLIC.HELP} sx={{ color: 'inherit', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}>Hỗ trợ</Box>
            <Box component={Link} to={ROUTES.PUBLIC.TERMS} sx={{ color: 'inherit', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}>Điều khoản</Box>
            <Box component={Link} to={ROUTES.PUBLIC.PRIVACY} sx={{ color: 'inherit', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}>Quyền riêng tư</Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
