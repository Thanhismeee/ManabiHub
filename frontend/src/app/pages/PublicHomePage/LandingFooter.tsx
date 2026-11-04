import React from 'react';
import { Box, Container, Grid, Typography, Stack, Divider } from '@mui/material';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';

const FooterLink: React.FC<{ to: string; children: React.ReactNode }> = ({ to, children }) => (
  <Typography
    variant="body2"
    component={Link}
    to={to}
    sx={{
      color: '#94a3b8',
      textDecoration: 'none',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 0.5,
      transition: 'all 0.3s ease',
      position: 'relative',
      cursor: 'pointer',
      '&::after': {
        content: '""',
        position: 'absolute',
        width: '0',
        height: '1px',
        bottom: '-2px',
        left: 0,
        backgroundColor: '#C41E3A',
        transition: 'width 0.3s ease',
      },
      '&:hover': {
        color: '#ffffff',
        transform: 'translateX(4px)',
        '&::after': {
          width: '100%',
        }
      }
    }}
  >
    {children}
  </Typography>
);

export const LandingFooter: React.FC = () => {
  return (
    <Box sx={{ bgcolor: '#0A1628', pt: { xs: 10, md: 14 }, pb: 6, borderTop: '1px solid rgba(255,255,255,0.05)', position: 'relative', overflow: 'hidden' }}>
      {/* Background Glow Effects */}
      <Box sx={{ position: 'absolute', top: '-20%', left: '-10%', width: '40%', height: '50%', background: 'radial-gradient(circle, rgba(196,30,58,0.06) 0%, rgba(0,0,0,0) 70%)', zIndex: 0 }} />
      <Box sx={{ position: 'absolute', bottom: '-20%', right: '-10%', width: '30%', height: '50%', background: 'radial-gradient(circle, rgba(91,140,90,0.04) 0%, rgba(0,0,0,0) 70%)', zIndex: 0 }} />

      <Container maxWidth="lg" sx={{ position: 'relative', zIndex: 1 }}>
        <Grid container spacing={8} sx={{ mb: 10 }}>
          {/* Logo & Description */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Box
              component={Link}
              to={ROUTES.PUBLIC.HOME}
              aria-label="Về trang chủ ManabiHub"
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                mb: 3,
                borderRadius: 2,
                textDecoration: 'none',
                transition: 'opacity 0.2s ease, transform 0.2s ease',
                '&:hover': {
                  opacity: 0.9,
                  transform: 'translateY(-2px)',
                },
              }}
            >
              <Box
                component="img"
                src="/manabihub-header-logo.png"
                alt="ManabiHub"
                sx={{
                  display: 'block',
                  filter: 'drop-shadow(0 0 1px rgba(255, 255, 255, 0.95))',
                  height: 64,
                  width: 'auto',
                }}
              />
            </Box>
            <Typography variant="body1" sx={{ color: '#94a3b8', lineHeight: 1.8, pr: { md: 4 }, mb: 4, fontWeight: 300 }}>
              Nền tảng học tiếng Nhật trực tuyến, kết nối học viên với giảng viên và
              các khóa học theo nhiều cấp độ JLPT.
            </Typography>
          </Grid>

          {/* Links Column 1 */}
          <Grid size={{ xs: 12, sm: 4, md: 2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#ffffff', mb: 3, letterSpacing: '0.5px', textTransform: 'uppercase', fontSize: '0.85rem' }}>
              ManabiHub
            </Typography>
            <Stack spacing={2}>
              <FooterLink to={ROUTES.PUBLIC.ABOUT}>Về chúng tôi</FooterLink>
              <FooterLink to={ROUTES.PUBLIC.COURSE_BROWSE}>Khám phá khóa học</FooterLink>
            </Stack>
          </Grid>

          {/* Links Column 2 */}
          <Grid size={{ xs: 12, sm: 4, md: 3 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#ffffff', mb: 3, letterSpacing: '0.5px', textTransform: 'uppercase', fontSize: '0.85rem' }}>
              Hỗ Trợ
            </Typography>
            <Stack spacing={2}>
              <FooterLink to={ROUTES.PUBLIC.TERMS}>Điều khoản sử dụng</FooterLink>
              <FooterLink to={ROUTES.PUBLIC.PRIVACY}>Chính sách bảo mật</FooterLink>
              <FooterLink to={ROUTES.PUBLIC.HELP}>Trung tâm trợ giúp</FooterLink>
            </Stack>
          </Grid>

          {/* Links Column 3 */}
          <Grid size={{ xs: 12, sm: 4, md: 3 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#ffffff', mb: 3, letterSpacing: '0.5px', textTransform: 'uppercase', fontSize: '0.85rem' }}>
              Giảng Dạy
            </Typography>
            <Stack spacing={2}>
              <FooterLink to={ROUTES.TEACHER.KYC}>Trở thành giảng viên</FooterLink>
              <FooterLink to={ROUTES.PUBLIC.INSTRUCTOR_TERMS}>Điều khoản giảng viên</FooterLink>
              <FooterLink to={ROUTES.PUBLIC.INSTRUCTOR_REVENUE_SHARE}>Chính sách chia sẻ doanh thu</FooterLink>
            </Stack>
          </Grid>
        </Grid>

        <Divider sx={{ mb: 4, borderColor: 'rgba(255,255,255,0.08)' }} />

        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
          <Typography variant="body2" sx={{ color: '#4a5568', fontWeight: 300 }}>
            © {new Date().getFullYear()} ManabiHub. All rights reserved.
          </Typography>
          <Box sx={{ display: 'flex', gap: 3 }}>
            <FooterLink to={ROUTES.PUBLIC.PRIVACY}>Quyền riêng tư</FooterLink>
            <FooterLink to={ROUTES.PUBLIC.TERMS}>Điều khoản</FooterLink>
          </Box>
        </Box>
      </Container>
    </Box>
  );
};
