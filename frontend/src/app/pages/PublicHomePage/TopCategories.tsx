import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Button,
  Container,
  Grid,
  Paper,
  Typography,
} from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { getAsset } from '../../../shared/utils/assets';
import { SKILL_CATEGORIES } from './courseCategoryLinks';

/* ─── JLPT Levels with vivid progression colors ─── */
const JLPT_LEVELS = [
  { id: 'jlptLevel=N5', name: 'N5', kanji: '初', subtitle: '初級', color: '#4CAF50', gradient: 'linear-gradient(135deg, #4CAF50, #8BC34A)', label: 'Sơ cấp', emoji: '🌱' },
  { id: 'jlptLevel=N4', name: 'N4', kanji: '基', subtitle: '初中級', color: '#8BC34A', gradient: 'linear-gradient(135deg, #8BC34A, #CDDC39)', label: 'Sơ-Trung cấp', emoji: '🌿' },
  { id: 'jlptLevel=N3', name: 'N3', kanji: '中', subtitle: '中級', color: '#FFB300', gradient: 'linear-gradient(135deg, #FFB300, #FF9800)', label: 'Trung cấp', emoji: '⚡' },
  { id: 'jlptLevel=N2', name: 'N2', kanji: '上', subtitle: '上級', color: '#F57C00', gradient: 'linear-gradient(135deg, #FF9800, #F57C00)', label: 'Cao cấp', emoji: '🔥' },
  { id: 'jlptLevel=N1', name: 'N1', kanji: '極', subtitle: '最上級', color: '#E53935', gradient: 'linear-gradient(135deg, #F57C00, #E53935)', label: 'Thành thạo', emoji: '👑' },
];

/* ─── Skill Categories with unique color accents ─── */
export const TopCategories: React.FC = () => {
  const navigate = useNavigate();
  const [isVisible, setIsVisible] = useState(false);
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) setIsVisible(true); },
      { threshold: 0.08, rootMargin: '0px' }
    );
    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <Box ref={sectionRef} sx={{ py: { xs: 8, md: 12 }, bgcolor: '#FBF9F5', overflowX: 'hidden' }}>
      <Container disableGutters sx={{ maxWidth: { md: '1157px' }, px: { xs: 3, md: 0 }, margin: '0 auto' }}>

        {/* ══════════ SECTION 1: JLPT PROGRESSION PATH ══════════ */}
        <Box sx={{ mb: { xs: 8, md: 12 } }}>
          <Box
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', md: 'row' },
              alignItems: { xs: 'flex-start', md: 'flex-end' },
              justifyContent: 'space-between',
              gap: 3, mb: 5,
            }}
          >
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 1.5 }}>
                <Box sx={{ width: 4, height: 20, bgcolor: '#C41E3A', borderRadius: 2, mr: 1.5 }} />
                <Typography variant="overline" sx={{ color: '#C41E3A', fontWeight: 700, letterSpacing: '0.08em', fontSize: '0.85rem' }}>
                  LỘ TRÌNH JLPT
                </Typography>
              </Box>
              <Typography variant="h3" sx={{ fontWeight: 800, color: '#1A1A2E', letterSpacing: '-0.02em', lineHeight: 1.3, fontSize: { xs: '2rem', md: '2.5rem' } }}>
                Chinh phục từng cấp độ
              </Typography>
              <Typography sx={{ color: '#64748b', mt: 1, fontSize: '1rem' }}>
                Lộ trình học tập từ cơ bản đến nâng cao — mỗi bước là một thành tựu mới
              </Typography>
            </Box>

            <Button
              variant="contained"
              endIcon={<ArrowForwardIcon />}
              onClick={() => navigate(ROUTES.PUBLIC.COURSE_BROWSE)}
              sx={{
                background: 'linear-gradient(135deg, #C41E3A, #E8432A)',
                py: 1.5, px: 3, fontWeight: 700,
                borderRadius: '10px',
                boxShadow: '0 4px 16px rgba(196, 30, 58, 0.25)',
                textTransform: 'uppercase', fontSize: '0.85rem',
                transition: 'all 0.3s ease',
                '&:hover': {
                  background: 'linear-gradient(135deg, #A8182F, #D13A24)',
                  transform: 'translateY(-2px)',
                  boxShadow: '0 8px 24px rgba(196,30,58,0.4)',
                }
              }}
            >
              XEM TẤT CẢ
            </Button>
          </Box>

          {/* JLPT Progression Path */}
          <Box
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', md: 'row' },
              alignItems: { xs: 'stretch', md: 'stretch' },
              gap: { xs: 2, md: 0 },
              position: 'relative',
            }}
          >
            {/* Connecting gradient line (desktop) */}
            <Box
              sx={{
                display: { xs: 'none', md: 'block' },
                position: 'absolute',
                bottom: '32px', left: '8%', right: '8%',
                height: '4px',
                background: 'linear-gradient(to right, #4CAF50, #8BC34A, #FFB300, #F57C00, #E53935)',
                borderRadius: 4,
                opacity: isVisible ? 0.35 : 0,
                transition: 'opacity 1.5s ease 0.5s',
                zIndex: 0,
              }}
            />

            {JLPT_LEVELS.map((level, index) => (
              <Box
                key={level.id}
                sx={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  position: 'relative',
                  zIndex: 1,
                  px: { xs: 0, md: 1.5 },
                  opacity: isVisible ? 1 : 0,
                  transform: isVisible ? 'translateY(0) scale(1)' : 'translateY(40px) scale(0.9)',
                  transition: `all 0.8s cubic-bezier(0.16, 1, 0.3, 1) ${index * 0.12}s`,
                }}
              >
                {/* No node dots here */}

                <Paper
                  elevation={0}
                  onClick={() => navigate(`${ROUTES.PUBLIC.COURSE_BROWSE}?${level.id}`)}
                  sx={{
                    p: { xs: 3, md: 4 },
                    width: '100%',
                    textAlign: 'center',
                    borderRadius: '20px',
                    border: '2px solid transparent',
                    bgcolor: '#FFFFFF',
                    cursor: 'pointer',
                    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
                    position: 'relative',
                    overflow: 'hidden',
                    mb: { xs: 0, md: 8 },
                    '&:hover': {
                      transform: 'translateY(-8px) scale(1.02)',
                      boxShadow: `0 16px 40px ${level.color}20`,
                      borderColor: level.color,
                    },
                    // Top colored bar
                    '&::before': {
                      content: '""',
                      position: 'absolute',
                      top: 0, left: 0, right: 0,
                      height: '5px',
                      background: level.gradient,
                      borderRadius: '20px 20px 0 0',
                    }
                  }}
                >
                  {/* Large Kanji watermark */}
                  <Typography
                    sx={{
                      position: 'absolute',
                      top: '50%', left: '50%',
                      transform: 'translate(-50%, -50%)',
                      fontSize: { xs: '5.5rem', md: '6.5rem' },
                      fontWeight: 900,
                      fontFamily: '"Noto Sans JP", serif',
                      color: `${level.color}`,
                      opacity: 0.05,
                      lineHeight: 1,
                      userSelect: 'none',
                      pointerEvents: 'none',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {level.subtitle}
                  </Typography>

                  {/* Emoji */}
                  <Typography sx={{ fontSize: '1.8rem', mb: 1.5, position: 'relative', zIndex: 2 }}>{level.emoji}</Typography>

                  {/* Level name with color */}
                  <Typography
                    sx={{
                      fontWeight: 900,
                      fontSize: '1.8rem',
                      background: level.gradient,
                      backgroundClip: 'text',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      mb: 0.5,
                      position: 'relative',
                      zIndex: 2,
                    }}
                  >
                    {level.name}
                  </Typography>
                  <Typography sx={{ fontSize: '0.7rem', color: '#94a3b8', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    {level.label}
                  </Typography>
                </Paper>
              </Box>
            ))}
          </Box>
        </Box>

        {/* ══════════ SECTION 2: SKILL CATEGORIES (Colorful) ══════════ */}
        <Box sx={{ mb: { xs: 8, md: 12 } }}>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1.5 }}>
            <Box sx={{ width: 4, height: 20, bgcolor: '#D4A017', borderRadius: 2, mr: 1.5 }} />
            <Typography variant="overline" sx={{ color: '#D4A017', fontWeight: 700, letterSpacing: '0.08em', fontSize: '0.85rem' }}>
              KỸ NĂNG ỨNG DỤNG
            </Typography>
          </Box>
          <Typography variant="h3" sx={{ fontWeight: 800, color: '#1A1A2E', letterSpacing: '-0.02em', lineHeight: 1.3, mb: 4, fontSize: { xs: '2rem', md: '2.5rem' } }}>
            Luyện tập theo kỹ năng
          </Typography>

          <Grid container spacing={2.5}>
            {SKILL_CATEGORIES.map((category, index) => (
              <Grid key={category.id} size={{ xs: 6, sm: 4, md: 2 }}>
                <Paper
                  elevation={0}
                  onClick={() => navigate(`${ROUTES.PUBLIC.COURSE_BROWSE}?${category.id}`)}
                  sx={{
                    p: 3,
                    textAlign: 'center',
                    borderRadius: '16px',
                    border: '1px solid #e8e0d8',
                    bgcolor: '#FFFFFF',
                    cursor: 'pointer',
                    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
                    position: 'relative',
                    overflow: 'hidden',
                    opacity: isVisible ? 1 : 0,
                    transform: isVisible ? 'translateY(0)' : 'translateY(30px)',
                    transitionDelay: `${0.6 + index * 0.1}s`,
                    '&:hover': {
                      transform: 'translateY(-6px)',
                      borderColor: '#C41E3A',
                      boxShadow: '0 12px 28px rgba(196, 30, 58, 0.08)',
                    },
                    '&:hover .category-kanji': {
                      color: '#C41E3A',
                      transform: 'scale(1.1)',
                    }
                  }}
                >
                  {/* Large Kanji as icon */}
                  <Typography
                    className="category-kanji"
                    sx={{
                      fontSize: '2.8rem',
                      fontWeight: 800,
                      fontFamily: '"Noto Sans JP", serif',
                      color: '#1A1A2E',
                      mb: 1,
                      lineHeight: 1.1,
                      transition: 'all 0.3s ease',
                    }}
                  >
                    {category.kanji}
                  </Typography>
                  <Typography
                    sx={{
                      fontWeight: 700,
                      color: '#1A1A2E',
                      fontSize: '0.9rem',
                    }}
                  >
                    {category.name}
                  </Typography>
                </Paper>
              </Grid>
            ))}
          </Grid>
        </Box>

        {/* ══════════ SECTION 3: INTRODUCTION BLOCK ══════════ */}
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 0 }}>

          {/* LEFT: Image */}
          <Box
            sx={{
              width: { xs: '100%', md: '578.5px' },
              height: { xs: 'auto', md: '583.67px' },
              opacity: isVisible ? 1 : 0,
              transform: isVisible ? 'translateX(0)' : 'translateX(-40px)',
              transition: 'all 1s cubic-bezier(0.16, 1, 0.3, 1) 0.2s',
            }}
          >
            <Box
              component="img"
              src={getAsset('hero.png')}
              alt="ManabiHub"
              sx={{
                width: '100%', height: '100%',
                objectFit: 'cover',
                display: 'block',
                borderRadius: '12px',
              }}
            />
          </Box>

          {/* RIGHT: Content */}
          <Box
            sx={{
              width: { xs: '100%', md: '578.5px' },
              height: { xs: 'auto', md: '583.67px' },
              pl: { xs: 0, md: 6 },
              pt: { xs: 5, md: 0 },
              display: 'flex', flexDirection: 'column', justifyContent: 'center',
              opacity: isVisible ? 1 : 0,
              transform: isVisible ? 'translateX(0)' : 'translateX(40px)',
              transition: 'all 1s cubic-bezier(0.16, 1, 0.3, 1) 0.4s',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <Box sx={{ width: 4, height: 20, bgcolor: '#C41E3A', borderRadius: 2, mr: 1.5 }} />
              <Typography variant="overline" sx={{ color: '#C41E3A', fontWeight: 700, letterSpacing: '0.08em', fontSize: '0.85rem' }}>
                GIỚI THIỆU
              </Typography>
            </Box>

            <Typography variant="h2" sx={{ fontWeight: 800, color: '#1A1A2E', mb: 3, letterSpacing: '-0.02em', lineHeight: 1.3, fontSize: { xs: '2rem', md: '2.5rem', lg: '2.8rem' } }}>
              Chào Mừng Đến Với ManabiHub
            </Typography>

            <Typography variant="body1" sx={{ color: '#475569', fontSize: '0.95rem', lineHeight: 1.9, mb: 2.5 }}>
              Bạn muốn nâng cao kiến thức và kỹ năng của mình? Bạn muốn học những khóa học chất lượng
              từ các giảng viên tiếng Nhật? Hãy khám phá các khóa học đang có trên ManabiHub.
            </Typography>

            <Typography variant="body1" sx={{ color: '#475569', fontSize: '0.95rem', lineHeight: 1.9, mb: 4.5 }}>
              ManabiHub cung cấp các khóa học tiếng Nhật theo nhiều cấp độ JLPT và kỹ năng.
              Nội dung do giảng viên xây dựng và đi qua quy trình xét duyệt trước khi được
              xuất bản trên nền tảng.
            </Typography>

            {/* Checklist */}
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: { xs: 2, sm: 3 }, alignItems: { xs: 'stretch', sm: 'center' } }}>
              <Box
                component="img"
                src={getAsset('course2.png')}
                sx={{ width: { xs: '100%', sm: '160px' }, maxWidth: { xs: 280, sm: 'none' }, height: { xs: 'auto', sm: '110px' }, objectFit: 'cover', borderRadius: 3 }}
              />
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, minWidth: 0 }}>
                {[
                  'Tìm khóa học phù hợp với trình độ và mục tiêu',
                  'Học theo nội dung và lộ trình của từng khóa học',
                  'Tìm người hướng dẫn phù hợp cho bạn'
                ].map((item, i) => (
                  <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start' }}>
                    <CheckCircleIcon sx={{ color: '#C41E3A', mr: 1.5, fontSize: 18, mt: 0.3 }} />
                    <Typography variant="body2" sx={{ fontWeight: 700, color: '#1A1A2E', fontSize: '0.9rem' }}>
                      {item}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          </Box>
        </Box>
      </Container>
    </Box>
  );
};
