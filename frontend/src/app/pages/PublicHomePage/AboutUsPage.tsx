import React from 'react';
import { Box, Container, Typography, Grid, Paper } from '@mui/material';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import LocalPhoneOutlinedIcon from '@mui/icons-material/LocalPhoneOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import { getAsset } from '../../../shared/utils/assets';

const CONTACT_INFO = [
  {
    icon: <EmailOutlinedIcon sx={{ fontSize: 28 }} />,
    title: 'Email Liên Hệ',
    value: 'hungkthe181698@fpt.edu.vn',
    link: 'mailto:hungkthe181698@fpt.edu.vn',
    color: '#3b82f6',
    bg: 'rgba(59, 130, 246, 0.1)',
  },
  {
    icon: <LocalPhoneOutlinedIcon sx={{ fontSize: 28 }} />,
    title: 'Hotline Hỗ Trợ',
    value: '0378.297.919',
    link: 'tel:0378297919',
    color: '#10b981',
    bg: 'rgba(16, 185, 129, 0.1)',
  },
  {
    icon: <PlaceOutlinedIcon sx={{ fontSize: 28 }} />,
    title: 'Trụ Sở Chính',
    value: 'Khu Công nghệ cao Hòa Lạc, Thạch Thất, Hà Nội',
    link: 'https://maps.google.com/?q=Đại+học+FPT+Hà+Nội',
    color: '#f43f5e',
    bg: 'rgba(244, 63, 94, 0.1)',
  }
];

export const AboutUsPage: React.FC = () => {
  return (
    <Box sx={{ bgcolor: '#fafafa', minHeight: '100vh', pb: 12 }}>
      {/* Hero Section with Premium Background Image */}
      <Box
        sx={{
          backgroundImage: `linear-gradient(to right, rgba(15, 23, 42, 0.95) 0%, rgba(15, 23, 42, 0.7) 100%), url(${getAsset('hero.png')})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          backgroundRepeat: 'no-repeat',
          pt: { xs: 15, md: 20 },
          pb: { xs: 20, md: 28 },
          position: 'relative',
          overflow: 'hidden'
        }}
      >
        {/* Abstract shapes for background */}
        <Box sx={{ position: 'absolute', top: '-20%', right: '-10%', width: '50%', height: '100%', background: 'radial-gradient(circle, rgba(59,130,246,0.15) 0%, rgba(0,0,0,0) 70%)', zIndex: 0 }} />
        <Box sx={{ position: 'absolute', bottom: '-20%', left: '-10%', width: '40%', height: '100%', background: 'radial-gradient(circle, rgba(16,185,129,0.1) 0%, rgba(0,0,0,0) 70%)', zIndex: 0 }} />

        <Container maxWidth="lg" sx={{ position: 'relative', zIndex: 1, textAlign: 'center' }}>
          <Typography
            variant="h1"
            sx={{
              fontWeight: 900,
              color: '#ffffff',
              fontSize: { xs: '3rem', md: '4.5rem' },
              letterSpacing: '-1.5px',
              mb: 3,
              textShadow: '0 10px 30px rgba(0,0,0,0.5)'
            }}
          >
            Về Chúng Tôi
          </Typography>
          <Typography
            variant="h6"
            sx={{
              color: '#94a3b8',
              maxWidth: 700,
              mx: 'auto',
              fontWeight: 400,
              lineHeight: 1.8,
              fontSize: { xs: '1.1rem', md: '1.25rem' }
            }}
          >
            ManabiHub được xây dựng để kết nối người học với giảng viên tiếng Nhật và
            nội dung học trực tuyến phù hợp với nhiều mục tiêu học tập.
          </Typography>
        </Container>
      </Box>

      {/* Floating Contact Cards Section */}
      <Container maxWidth="lg" sx={{ mt: { xs: -10, md: -14 }, position: 'relative', zIndex: 2 }}>
        <Grid container spacing={4}>
          {CONTACT_INFO.map((info, index) => (
            <Grid size={{ xs: 12, md: 4 }} key={index}>
              <Paper
                component="a"
                href={info.link}
                target={info.title === 'Trụ Sở Chính' ? '_blank' : '_self'}
                elevation={0}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  p: 4,
                  height: '100%',
                  borderRadius: 4,
                  bgcolor: '#ffffff',
                  textDecoration: 'none',
                  border: '1px solid rgba(0,0,0,0.04)',
                  boxShadow: '0 4px 20px rgba(0, 0, 0, 0.03)',
                  transition: 'all 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
                  position: 'relative',
                  overflow: 'hidden',
                  cursor: 'pointer',
                  '&:hover': {
                    transform: 'translateY(-8px)',
                    boxShadow: '0 20px 40px rgba(0, 0, 0, 0.08)',
                    borderColor: 'rgba(0,0,0,0.08)',
                    '& .arrow-icon': {
                      transform: 'translateX(4px)',
                      color: info.color
                    },
                    '& .icon-container': {
                      transform: 'scale(1.1)'
                    }
                  }
                }}
              >
                <Box
                  className="icon-container"
                  sx={{
                    width: 64, height: 64, borderRadius: 3, bgcolor: info.bg, color: info.color,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', mb: 4,
                    transition: 'transform 0.4s ease'
                  }}
                >
                  {info.icon}
                </Box>

                <Typography variant="body2" sx={{ color: '#64748b', fontWeight: 600, letterSpacing: '1px', textTransform: 'uppercase', mb: 1 }}>
                  {info.title}
                </Typography>
                <Typography variant="h6" sx={{ color: '#0f172a', fontWeight: 700, mb: 3 }}>
                  {info.value}
                </Typography>

                <Box sx={{ mt: 'auto', display: 'flex', alignItems: 'center', color: '#94a3b8' }}>
                  <Typography variant="body2" sx={{ fontWeight: 600, mr: 1, transition: 'color 0.3s ease' }}>
                    Chi tiết
                  </Typography>
                  <ArrowForwardIosIcon className="arrow-icon" sx={{ fontSize: 12, transition: 'all 0.3s ease' }} />
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>

        {/* Cinematic Map Section */}
        <Box sx={{ mt: 10 }}>
          <Typography variant="h3" sx={{ fontWeight: 800, color: '#0f172a', mb: 2, textAlign: 'center', letterSpacing: '-1px' }}>
            Vị Trí Của Chúng Tôi
          </Typography>
          <Typography variant="body1" sx={{ color: '#64748b', mb: 6, textAlign: 'center', maxWidth: 600, mx: 'auto' }}>
            Ghé thăm văn phòng của ManabiHub tại khuôn viên Đại học FPT, nơi nuôi dưỡng những ý tưởng sáng tạo và công nghệ đột phá.
          </Typography>

          <Paper
            elevation={0}
            sx={{
              p: 1.5,
              borderRadius: 6,
              bgcolor: '#ffffff',
              border: '1px solid rgba(0,0,0,0.05)',
              boxShadow: '0 20px 40px rgba(0, 0, 0, 0.04)',
              transition: 'transform 0.5s ease',
              '&:hover': {
                transform: 'scale(1.01)',
                boxShadow: '0 30px 60px rgba(0, 0, 0, 0.08)',
              }
            }}
          >
            <Box
              sx={{
                width: '100%',
                height: { xs: 400, md: 550 },
                borderRadius: 5,
                overflow: 'hidden',
                position: 'relative'
              }}
            >
              <iframe
                src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3724.485534608307!2d105.52471961540209!3d21.013249093685957!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x31345b465a4e65fb%3A0xaae6040cfabe8fe!2zVHLGsOG7nW5nIMSQ4bqhaSBI4buNYyBGUFQgSMOgIE7hu5lp!5e0!3m2!1svi!2s!4v1689230554504!5m2!1svi!2s"
                width="100%"
                height="100%"
                style={{ border: 0 }}
                allowFullScreen={true}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
                title="Google Map Đại học FPT"
              />
              {/* Overlay gradient to blend map edges slightly */}
              <Box sx={{ position: 'absolute', inset: 0, pointerEvents: 'none', boxShadow: 'inset 0 0 20px rgba(0,0,0,0.1)', borderRadius: 5 }} />
            </Box>
          </Paper>
        </Box>
      </Container>
    </Box>
  );
};
