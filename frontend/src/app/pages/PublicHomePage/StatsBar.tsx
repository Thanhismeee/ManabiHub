import React, { useRef, useState, useEffect } from 'react';
import { Box, Container, Typography, Paper } from '@mui/material';
import SchoolIcon from '@mui/icons-material/School';
import AutoStoriesIcon from '@mui/icons-material/AutoStories';
import GroupsIcon from '@mui/icons-material/Groups';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';

const HIGHLIGHTS = [
  {
    icon: <EmojiEventsIcon sx={{ fontSize: 28 }} />,
    value: 'N5 → N1',
    label: 'Lộ trình JLPT',
    color: '#C41E3A',
  },
  {
    icon: <SchoolIcon sx={{ fontSize: 28 }} />,
    value: 'Xác minh',
    label: 'Hồ sơ giảng viên',
    color: '#D4A017',
  },
  {
    icon: <AutoStoriesIcon sx={{ fontSize: 28 }} />,
    value: 'Thực hành',
    label: 'Video, quiz, flashcard và viết',
    color: '#5B8C5A',
  },
  {
    icon: <GroupsIcon sx={{ fontSize: 28 }} />,
    value: 'Minh bạch',
    label: 'Thanh toán và chính sách',
    color: '#1B2A4A',
  },
];

export const StatsBar: React.FC = () => {
  const [isVisible, setIsVisible] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) setIsVisible(true); },
      { threshold: 0.3 }
    );
    if (ref.current) observer.observe(ref.current);
    return () => observer.disconnect();
  }, []);

  return (
    <Box
      ref={ref}
      sx={{
        position: 'relative',
        zIndex: 10,
        mt: { xs: -6, md: -8 }, // Negative margin to overlap Hero
        px: { xs: 2, md: 0 },
      }}
    >
      <Container maxWidth="lg">
        <Paper
          elevation={0}
          sx={{
            bgcolor: '#ffffff',
            py: { xs: 4, md: 5 },
            px: { xs: 1.5, sm: 2, md: 4 },
            borderRadius: '24px',
            boxShadow: '0 16px 40px rgba(0,0,0,0.08)',
            border: '1px solid rgba(0,0,0,0.04)',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Subtle top border accent inside the card */}
          <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, height: '4px', background: 'linear-gradient(90deg, #C41E3A, #D4A017, #5B8C5A, #1B2A4A)' }} />

          <Box
            sx={{
              display: { xs: 'grid', sm: 'flex' },
              gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'none' },
              flexDirection: { sm: 'row' },
              justifyContent: 'space-around',
              alignItems: { xs: 'stretch', sm: 'center' },
              gap: { xs: 2, sm: 2 },
            }}
          >
            {HIGHLIGHTS.map((stat, index) => (
              <Box
                key={stat.label}
                sx={{
                  display: 'flex',
                  alignItems: { xs: 'flex-start', sm: 'center' },
                  gap: { xs: 1, sm: 2 },
                  minWidth: 0,
                  opacity: isVisible ? 1 : 0,
                  transform: isVisible ? 'translateY(0)' : 'translateY(20px)',
                  transition: `all 0.8s cubic-bezier(0.16, 1, 0.3, 1) ${index * 0.15}s`,
                }}
              >
                <Box
                  sx={{
                    width: { xs: 40, sm: 52 }, height: { xs: 40, sm: 52 },
                    flexShrink: 0,
                    borderRadius: '14px',
                    bgcolor: `${stat.color}18`,
                    color: stat.color,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: `1.5px solid ${stat.color}30`,
                  }}
                >
                  {stat.icon}
                </Box>
                <Box sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', minWidth: 0 }}>
                  <Typography sx={{ color: '#1A1A2E', fontWeight: 800, fontSize: { xs: '1rem', sm: '1.5rem' }, lineHeight: 1.2, wordBreak: 'break-word' }}>
                    {stat.value}
                  </Typography>
                  <Typography sx={{ color: '#64748b', fontSize: { xs: '0.66rem', sm: '0.8rem' }, fontWeight: 600, lineHeight: 1.35 }}>
                    {stat.label}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        </Paper>
      </Container>
    </Box>
  );
};
