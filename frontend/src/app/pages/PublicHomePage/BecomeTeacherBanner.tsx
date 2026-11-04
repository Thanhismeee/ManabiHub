import React from 'react';
import { Box, Container, Typography, Button } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremium';

// Japanese wave pattern for banner background
const WAVE_PATTERN = `url("data:image/svg+xml,%3Csvg width='100' height='20' viewBox='0 0 100 20' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M21.184 20c.357-.13.72-.264 1.088-.402l1.768-.661C33.64 15.347 39.647 14 50 14c10.271 0 15.362 1.222 24.629 4.928.955.383 1.869.74 2.75 1.072h6.225c-2.51-.73-5.139-1.691-8.233-2.928C65.888 13.278 60.562 12 50 12c-10.626 0-16.855 1.397-26.66 5.063l-1.767.662c-2.475.923-4.66 1.674-6.724 2.275h6.335zm0-20C13.258 2.892 8.077 4 0 4V2c5.744 0 9.951-.574 14.85-2h6.334zM77.38 0C85.239 2.966 90.502 4 100 4V2c-6.842 0-11.386-.542-16.396-2h-6.225zM0 14c10.271 0 15.362 1.222 24.629 4.928.955.383 1.869.74 2.75 1.072H21.18c-.358-.13-.72-.264-1.088-.402l-1.768-.661C9.73 15.347 3.723 14 0 14v0z' fill='%23ffffff' fill-opacity='0.04' fill-rule='evenodd'/%3E%3C/svg%3E")`;

export const BecomeTeacherBanner: React.FC = () => {
  return (
    <Box
      sx={{
        background: 'linear-gradient(135deg, #1B2A4A 0%, #0F1D36 100%)',
        py: { xs: 8, md: 12 },
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Wave pattern overlay */}
      <Box
        sx={{
          position: 'absolute',
          top: 0, left: 0,
          width: '100%', height: '100%',
          backgroundImage: WAVE_PATTERN,
          backgroundRepeat: 'repeat',
          zIndex: 0,
        }}
      />

      {/* Ambient glow */}
      <Box
        sx={{
          position: 'absolute',
          top: '-30%', left: '50%',
          transform: 'translateX(-50%)',
          width: '60%', height: '80%',
          background: 'radial-gradient(circle, rgba(196, 30, 58, 0.1) 0%, rgba(0,0,0,0) 70%)',
          zIndex: 0,
        }}
      />

      <Container maxWidth="md" sx={{ position: 'relative', zIndex: 1 }}>
        {/* Japanese accent text */}
        <Typography sx={{ fontFamily: '"Noto Sans JP", sans-serif', color: '#F59E0B', fontWeight: 700, fontSize: '0.85rem', letterSpacing: '0.15em', mb: 2 }}>
          先生になろう — Trở Thành Sensei
        </Typography>

        <Typography variant="h3" sx={{ fontWeight: 800, color: '#ffffff', mb: 3, fontSize: { xs: '2rem', md: '2.5rem' } }}>
          {'Trở thành Giảng viên ManabiHub'}
        </Typography>

        <Typography variant="h6" sx={{ color: '#b0bdd0', fontWeight: 400, mb: 1, lineHeight: 1.6, px: { md: 4 } }}>
          Chia sẻ kiến thức và xây dựng nguồn thu từ nội dung của bạn. Tỷ lệ doanh thu
          hiện hành được công bố minh bạch và được lưu cho từng giao dịch tại thời điểm
          thanh toán thành công.
        </Typography>
        <Typography
          component={RouterLink}
          to={ROUTES.PUBLIC.INSTRUCTOR_REVENUE_SHARE}
          sx={{
            color: '#F59E0B',
            display: 'inline-block',
            fontWeight: 700,
            mb: 2,
            textDecoration: 'none',
            '&:hover': { textDecoration: 'underline' },
          }}
        >
          Xem chính sách chia sẻ doanh thu hiện hành
        </Typography>
        <Typography variant="body1" sx={{ color: '#7a8ba8', fontStyle: 'italic', mb: 5 }}>
          {'* Giảng viên phải hoàn tất các bước xác thực hồ sơ theo yêu cầu trước khi được xuất bản khóa học.'}
        </Typography>

        <Button
          component={RouterLink}
          to={ROUTES.TEACHER.KYC}
          variant="contained"
          size="large"
          startIcon={<WorkspacePremiumIcon />}
          sx={{
            py: 1.5, px: 5, borderRadius: '12px',
            background: 'linear-gradient(135deg, #C41E3A, #E8432A)',
            color: '#ffffff',
            fontWeight: 700, textTransform: 'none', fontSize: '1.1rem',
            boxShadow: '0 8px 24px rgba(196, 30, 58, 0.35)',
            transition: 'all 0.3s ease',
            '&:hover': {
              background: 'linear-gradient(135deg, #A8182F, #D13A24)',
              boxShadow: '0 16px 32px rgba(196, 30, 58, 0.45)',
              transform: 'translateY(-3px)',
            }
          }}
        >
          {'Bắt đầu giảng dạy ngay'}
        </Button>
      </Container>
    </Box>
  );
};
