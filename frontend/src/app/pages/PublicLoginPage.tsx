import { Alert, Box, Typography, Button, keyframes } from '@mui/material';
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { getAsset } from '../../shared/utils/assets';
import {
  getAuthSession,
  getDefaultRoute,
  rememberPostLoginRoute,
} from '../../shared/auth/authSession';
import { ROUTES } from '../../shared/constants/routes';
import { useLocale } from '../../shared/i18n/locale';
import { LocaleSelect } from '../../shared/i18n/LocaleSelect';

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
`;

const GoogleIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
  </svg>
);

export function PublicLoginPage() {
  const { t, locale } = useLocale();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const reason = searchParams.get('reason');
  const error = searchParams.get('error');
  const session = getAuthSession('public');

  if (session) {
    return <Navigate to={getDefaultRoute(session)} replace />;
  }

  const handleGoogleLogin = () => {
    const returnTo = (location.state as { from?: unknown } | null)?.from;
    if (typeof returnTo === 'string') {
      rememberPostLoginRoute('public', returnTo);
    }

    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api';
    const baseUrl = apiBaseUrl.replace(/\/api\/?$/, '');
    window.location.href = `${baseUrl}/oauth2/authorization/google`;
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#0f172a', fontFamily: '"Inter", "Roboto", sans-serif' }}>

      {/* Left Panel - Branding */}
      <Box
        sx={{
          display: { xs: 'none', lg: 'flex' },
          flex: 6,
          position: 'relative',
          overflow: 'hidden',
          '&::before': {
            content: '""', position: 'absolute', inset: 0,
            background: 'linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0.3) 30%, rgba(0,0,0,0.7) 100%)',
            zIndex: 1
          }
        }}
      >
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <img src={getAsset('hero.png')} alt="Japan Study" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        </Box>

        <Box sx={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', p: 8, height: '100%' }}>
          {/* Logo at top */}
          <Box
            component={Link}
            to={ROUTES.PUBLIC.HOME}
            aria-label="Về trang chủ ManabiHub"
            sx={{
              alignSelf: 'flex-start',
              borderRadius: 2,
              display: 'inline-flex',
              flexShrink: 0,
              textDecoration: 'none',
            }}
          >
            <Box
              component="img"
              src="/manabihub-header-logo.png"
              alt="ManabiHub"
              sx={{
                display: 'block',
                filter: 'drop-shadow(0 0 1px rgba(255, 255, 255, 0.95))',
                height: 58,
                width: 'auto',
              }}
            />
          </Box>

          {/* Content shifted slightly up for vertical balance */}
          <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', pb: '15vh' }}>
            <Box sx={{ maxWidth: 500, animation: `${fadeIn} 1s ease-out` }}>
              <Typography sx={{ fontFamily: '"Noto Sans JP", sans-serif', color: 'rgba(255,255,255,0.7)', fontWeight: 700, fontSize: '0.85rem', letterSpacing: '0.15em', mb: 2 }}>
                ManabiHub · TOPIK · HSK · JLPT
              </Typography>
              <Typography variant="h2" sx={{ fontWeight: 800, color: 'white', mb: 3, lineHeight: 1.1, fontSize: '3.5rem' }}>
                {t('Bắt đầu hành trình học ngôn ngữ của bạn')}
              </Typography>
              <Typography variant="h6" sx={{ color: '#cbd5e1', mb: 4, fontWeight: 400, lineHeight: 1.6 }}>
                {t('Học tiếng Hàn, tiếng Trung và tiếng Nhật cùng giáo viên trên ManabiHub.')}
              </Typography>

              <Box sx={{ display: 'inline-block', background: 'rgba(0,0,0,0.3)', backdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '50px', px: 3, py: 1.5 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, color: 'white' }}>
                  {t('Nội dung nhiều cấp độ • Theo dõi tiến độ • AI hỗ trợ')}
                </Typography>
              </Box>
            </Box>
          </Box>
        </Box>
      </Box>

      {/* Right Panel - Auth Action */}
      <Box
        sx={{
          flex: { xs: 1, lg: 4 },
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          p: { xs: 3, sm: 6, md: 8 },
          position: 'relative',
          bgcolor: '#FAF9F6',
        }}
      >
        <Box sx={{
          width: '100%', maxWidth: 420, animation: `${fadeIn} 0.6s ease-out`,
          bgcolor: '#ffffff',
          p: { xs: 4, sm: 5 },
          borderRadius: '24px',
          boxShadow: '0 20px 40px -10px rgba(0,0,0,0.05)',
          border: '1px solid #f1f5f9'
        }}>
          {/* Mobile Header Logo */}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}><LocaleSelect /></Box>
          <Box
            component={Link}
            to={ROUTES.PUBLIC.HOME}
            aria-label="Về trang chủ ManabiHub"
            sx={{ display: { xs: 'flex', lg: 'none' }, justifyContent: 'center', mb: 6, textDecoration: 'none' }}
          >
            <Box
              component="img"
              src="/manabihub-header-logo.png"
              alt="ManabiHub"
              sx={{ display: 'block', height: 64, width: 'auto' }}
            />
          </Box>

          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Typography sx={{ fontFamily: '"Noto Sans JP", sans-serif', color: '#C41E3A', fontWeight: 700, fontSize: '0.85rem', letterSpacing: '0.1em', mb: 1 }}>
              {locale === 'ko' ? '환영합니다!' : locale === 'zh-CN' ? '欢迎！' : 'ManabiHub'}
            </Typography>
            <Typography variant="h4" sx={{ fontWeight: 800, color: '#0f172a', mb: 1.5, letterSpacing: '-0.5px' }}>
              {t('Chào mừng bạn!')} 👋
            </Typography>
            <Typography variant="body1" sx={{ color: '#64748b', lineHeight: 1.6 }}>
              {t('Đăng nhập nhanh chóng và bảo mật chỉ với một chạm.')}
            </Typography>
          </Box>

          {reason === 'session-expired' && (
            <Alert severity="warning" sx={{ mb: 3 }}>
              {t('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.')}
            </Alert>
          )}
          {reason === 'session-revoked' && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {t('Phiên đăng nhập của bạn đã bị thu hồi từ một thiết bị khác. Vui lòng đăng nhập lại.')}
            </Alert>
          )}
          {reason === 'account-in-use' && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {t('Tài khoản của bạn đang được sử dụng để học trên một thiết bị khác. Vui lòng đăng nhập lại để tiếp tục học tại đây.')}
            </Alert>
          )}
          {error === 'PUBLIC_DEVICE_LIMIT_REACHED' && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {t('Bạn đã đạt giới hạn thiết bị tối đa. Vui lòng đăng xuất từ thiết bị khác hoặc quản lý thiết bị của bạn.')}
            </Alert>
          )}

          {/* Primary Action */}
          <Button
            variant="outlined"
            fullWidth
            onClick={handleGoogleLogin}
            startIcon={<GoogleIcon />}
            sx={{
              py: 1.5,
              mb: 3,
              borderRadius: '12px',
              textTransform: 'none',
              fontWeight: 700,
              fontSize: '1.05rem',
              bgcolor: '#ffffff',
              color: '#334155',
              borderColor: '#e2e8f0',
              borderWidth: '1.5px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
              transition: 'all 0.3s ease',
              '&:hover': {
                bgcolor: '#fcfcfc',
                borderColor: '#C41E3A',
                color: '#C41E3A',
                transform: 'translateY(-2px)',
                boxShadow: '0 8px 16px rgba(196, 30, 58, 0.12)',
              },
              '&:active': {
                transform: 'translateY(0)',
              }
            }}
          >
            {t('Đăng nhập với Google')}
          </Button>

          <Typography variant="caption" sx={{ color: '#94a3b8', display: 'block', textAlign: 'center', lineHeight: 1.6 }}>
            {t('Trước khi tiếp tục, vui lòng đọc')}{' '}
            <Box
              component={Link}
              to={ROUTES.PUBLIC.TERMS}
              sx={{ color: '#C41E3A', textDecoration: 'underline', fontWeight: 600, '&:hover': { color: '#E8432A' } }}
            >
              {t('Điều khoản sử dụng')}
            </Box>{' '}
            {t('và')}{' '}
            <Box
              component={Link}
              to={ROUTES.PUBLIC.PRIVACY}
              sx={{ color: '#C41E3A', textDecoration: 'underline', fontWeight: 600, '&:hover': { color: '#E8432A' } }}
            >
              {t('Chính sách bảo mật')}
            </Box>.
          </Typography>

        </Box>
      </Box>
    </Box>
  );
}
