import React from 'react';
import { Box, Container, Typography, Button } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import AutoStoriesIcon from '@mui/icons-material/AutoStories';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { getAsset } from '../../../shared/utils/assets';

// Japanese wave pattern as inline SVG data URI
const WAVE_PATTERN = `url("data:image/svg+xml,%3Csvg width='100' height='20' viewBox='0 0 100 20' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M21.184 20c.357-.13.72-.264 1.088-.402l1.768-.661C33.64 15.347 39.647 14 50 14c10.271 0 15.362 1.222 24.629 4.928.955.383 1.869.74 2.75 1.072h6.225c-2.51-.73-5.139-1.691-8.233-2.928C65.888 13.278 60.562 12 50 12c-10.626 0-16.855 1.397-26.66 5.063l-1.767.662c-2.475.923-4.66 1.674-6.724 2.275h6.335zm0-20C13.258 2.892 8.077 4 0 4V2c5.744 0 9.951-.574 14.85-2h6.334zM77.38 0C85.239 2.966 90.502 4 100 4V2c-6.842 0-11.386-.542-16.396-2h-6.225zM0 14c10.271 0 15.362 1.222 24.629 4.928.955.383 1.869.74 2.75 1.072H21.18c-.358-.13-.72-.264-1.088-.402l-1.768-.661C9.73 15.347 3.723 14 0 14v0z' fill='%23ffffff' fill-opacity='0.03' fill-rule='evenodd'/%3E%3C/svg%3E")`;

// Shared geometry for the two hero CTAs so they always render at the same size —
// they differ only in colour and hover treatment. Width comes from the grid column
// they sit in (see HERO_CTA_GROUP_SX), never from their own label, so the two stay
// equal even though one label is longer and only the other carries an icon.
const HERO_BUTTON_BASE_SX: SxProps<Theme> = {
  py: { xs: 1.35, sm: 1.8 },
  px: { xs: 2, sm: 5 },
  width: '100%',
  borderRadius: '12px',
  color: '#ffffff',
  fontWeight: 700,
  textTransform: 'none',
  fontSize: { xs: '0.98rem', sm: '1.1rem' },
  transition: 'all 0.3s ease',
};

// A single grid column sized to the widest button: `max-content` measures the longest
// label, then both buttons stretch to fill that column, so they match without hard-coding
// a width that would break the moment a label changes. The minmax(0, …) lower bound lets
// the column shrink on narrow screens instead of overflowing the viewport.
const HERO_CTA_GROUP_SX: SxProps<Theme> = {
  display: 'grid',
  gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'minmax(0, max-content)' },
  width: { xs: '100%', sm: 'auto' },
  maxWidth: { xs: 420, sm: 'none' },
  gap: { xs: 1.5, sm: 2 },
};

export const HeroSection: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Box
      sx={{
        position: 'relative',
        minHeight: { xs: 'auto', md: '85vh' },
        // StatsBar deliberately overlaps this section by 48px (xs) / 64px (md) via a
        // negative margin. Reserve more than that here so hero content — especially the
        // CTA row once it wraps — never ends up underneath (and unclickable behind) it.
        pb: { xs: 10, md: 14 },
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
        background: 'linear-gradient(135deg, #1B2A4A 0%, #0F1D36 60%, #0A1628 100%)',
      }}
    >
      {/* Wave pattern overlay */}
      <Box
        sx={{
          position: 'absolute', top: 0, left: 0,
          width: '100%', height: '100%',
          backgroundImage: WAVE_PATTERN,
          backgroundRepeat: 'repeat',
          zIndex: 0,
        }}
      />

      {/* Ambient glow - vermilion */}
      <Box
        sx={{
          position: 'absolute', top: '10%', right: '20%',
          width: '40%', height: '60%',
          background: 'radial-gradient(circle, rgba(196, 30, 58, 0.07) 0%, rgba(0,0,0,0) 70%)',
          zIndex: 0,
        }}
      />

      {/* Floating sakura-inspired particles */}
      {[...Array(5)].map((_, i) => (
        <Box
          key={i}
          sx={{
            position: 'absolute',
            width: { xs: 6, md: 8 },
            height: { xs: 6, md: 8 },
            borderRadius: '50%',
            bgcolor: i % 2 === 0 ? 'rgba(196, 30, 58, 0.15)' : 'rgba(255, 183, 197, 0.12)',
            left: `${15 + i * 18}%`,
            top: `${20 + (i % 3) * 25}%`,
            zIndex: 0,
            animation: `sakuraFloat${i} ${6 + i * 2}s ease-in-out infinite`,
            [`@keyframes sakuraFloat${i}`]: {
              '0%, 100%': { transform: 'translateY(0) rotate(0deg)', opacity: 0.6 },
              '50%': { transform: `translateY(-${15 + i * 5}px) rotate(${45 + i * 30}deg)`, opacity: 1 },
            },
          }}
        />
      ))}

      <Container maxWidth="lg" sx={{ position: 'relative', zIndex: 2 }}>
        <Box
          sx={{
            display: 'flex',
            flexDirection: { xs: 'column', md: 'row' },
            alignItems: 'center',
            gap: { xs: 5, md: 6 },
            py: { xs: 6, md: 0 },
          }}
        >
          {/* Left Content (Text + Buttons) - 50% */}
          <Box sx={{ width: { xs: '100%', md: '50%' } }}>
            <Box
              sx={{
                animation: 'heroFadeIn 1s cubic-bezier(0.22, 1, 0.36, 1) forwards',
                '@keyframes heroFadeIn': {
                  '0%': { opacity: 0, transform: 'translateY(24px)' },
                  '100%': { opacity: 1, transform: 'translateY(0)' }
                }
              }}
            >
              {/* Japanese accent */}
              <Typography
                sx={{
                  fontFamily: '"Noto Sans JP", sans-serif',
                  color: 'rgba(196, 30, 58, 0.75)',
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  letterSpacing: '0.12em',
                  mb: 2,
                }}
              >
                学ぶ ・ 成長する ・ 繋がる
              </Typography>

              <Typography
                variant="h1"
                sx={{
                  fontWeight: 800,
                  color: '#ffffff',
                  lineHeight: 1.15,
                  mb: 3,
                  fontSize: { xs: '2rem', sm: '2.4rem', md: '3.5rem' },
                  letterSpacing: '-1px'
                }}
              >
                Hành trình chinh phục{' '}
                <Box
                  component="span"
                  sx={{
                    background: 'linear-gradient(135deg, #C41E3A, #E8432A)',
                    backgroundClip: 'text',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                  }}
                >
                  tiếng Nhật
                </Box>
                {' '}bắt đầu tại đây
              </Typography>

              <Typography
                variant="h6"
                sx={{
                  color: '#b0bdd0',
                  fontWeight: 400,
                  lineHeight: 1.7,
                  mb: { xs: 3.5, md: 5 },
                  fontSize: { xs: '1rem', md: '1.1rem' },
                  pr: { md: 2 },
                  maxWidth: 520,
                }}
              >
                Nền tảng học tiếng Nhật trực tuyến với nội dung theo nhiều cấp độ JLPT,
                công cụ AI hỗ trợ học tập và quy trình xác thực giảng viên trước khi xuất bản.
              </Typography>

              <Box sx={HERO_CTA_GROUP_SX}>
                <Button
                  variant="contained"
                  size="large"
                  onClick={() => navigate(ROUTES.PUBLIC.COURSE_BROWSE)}
                  endIcon={<AutoStoriesIcon />}
                  sx={[
                    HERO_BUTTON_BASE_SX,
                    {
                      background: 'linear-gradient(135deg, #C41E3A 0%, #E8432A 100%)',
                      boxShadow: '0 8px 24px rgba(196, 30, 58, 0.35)',
                      '&:hover': {
                        background: 'linear-gradient(135deg, #A8182F 0%, #D13A24 100%)',
                        transform: 'translateY(-3px)',
                        boxShadow: '0 16px 32px rgba(196, 30, 58, 0.45)'
                      }
                    }
                  ]}
                >
                  Khám phá khóa học
                </Button>

                <Button
                  variant="outlined"
                  size="large"
                  onClick={() => navigate(ROUTES.TEACHER.KYC)}
                  sx={[
                    HERO_BUTTON_BASE_SX,
                    {
                      borderColor: '#ffffff',
                      borderWidth: '2px',
                      '&:hover': {
                        borderColor: 'rgba(196, 30, 58, 0.6)',
                        bgcolor: 'rgba(196, 30, 58, 0.08)',
                        transform: 'translateY(-3px)'
                      }
                    }
                  ]}
                >
                  Trở thành giảng viên
                </Button>
              </Box>
            </Box>
          </Box>

          {/* Right Content (Illustration) - 50% */}
          <Box
            sx={{
              width: { xs: '100%', md: '50%' },
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              position: 'relative',
              animation: 'heroImgIn 1.2s cubic-bezier(0.22, 1, 0.36, 1) 0.2s both',
              '@keyframes heroImgIn': {
                '0%': { opacity: 0, transform: 'translateY(30px) scale(0.95)' },
                '100%': { opacity: 1, transform: 'translateY(0) scale(1)' }
              }
            }}
          >
            {/* Kanji watermark behind illustration */}
            <Typography
              sx={{
                position: 'absolute',
                top: { xs: -10, md: -20 },
                right: { xs: 0, md: 20 },
                fontSize: { xs: '7rem', md: '10rem' },
                fontWeight: 900,
                fontFamily: '"Noto Sans JP", serif',
                color: 'rgba(255,255,255,0.025)',
                lineHeight: 1,
                zIndex: 0,
                userSelect: 'none',
                pointerEvents: 'none',
              }}
            >
              学
            </Typography>

            {/* Illustration image */}
            <Box
              component="img"
              src={getAsset('hero_illustration.png')}
              alt="Học tiếng Nhật cùng ManabiHub"
              sx={{
                width: '100%',
                maxWidth: { xs: 320, sm: 360, md: 520 },
                height: 'auto',
                objectFit: 'contain',
                position: 'relative',
                zIndex: 1,
                filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.3))',
                transition: 'transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
                '&:hover': {
                  transform: 'scale(1.03) translateY(-4px)',
                }
              }}
            />

            {/* Floating JLPT badge */}
            <Box
              sx={{
                position: 'absolute',
                bottom: { xs: 10, md: 30 },
                left: { xs: 0, md: -10 },
                bgcolor: '#FFF8E1',
                color: '#1A1A2E',
                display: 'flex', alignItems: 'center', gap: 1,
                px: 2.5, py: 1.2,
                borderRadius: '12px',
                fontWeight: 700,
                fontSize: '0.85rem',
                letterSpacing: '0.02em',
                boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
                zIndex: 10,
                animation: 'floatBadge 4s ease-in-out infinite',
                '@keyframes floatBadge': {
                  '0%, 100%': { transform: 'translateY(0)' },
                  '50%': { transform: 'translateY(-8px)' },
                }
              }}
            >
              <MenuBookIcon sx={{ color: '#D4A017', fontSize: 18 }} />
              JLPT N5 → N1
            </Box>

            {/* Floating sakura badge */}
            <Box
              sx={{
                position: 'absolute',
                top: { xs: 20, md: 40 },
                right: { xs: 10, md: 0 },
                bgcolor: 'rgba(255, 255, 255, 0.95)',
                color: '#1A1A2E',
                display: 'flex', alignItems: 'center', gap: 1,
                px: 2.5, py: 1.2,
                borderRadius: '12px',
                fontWeight: 700,
                fontSize: '0.85rem',
                boxShadow: '0 8px 24px rgba(0,0,0,0.1)',
                zIndex: 10,
                animation: 'floatBadge2 5s ease-in-out 1s infinite',
                '@keyframes floatBadge2': {
                  '0%, 100%': { transform: 'translateY(0)' },
                  '50%': { transform: 'translateY(-6px)' },
                }
              }}
            >
              <AutoAwesomeIcon sx={{ color: '#C41E3A', fontSize: 18 }} />
              AI Trợ lý học tập
            </Box>
          </Box>
        </Box>
      </Container>
    </Box>
  );
};
