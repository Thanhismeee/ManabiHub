import React, { useState, useEffect } from 'react';
import { Box, Typography, Fade } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { getAsset } from '../../../shared/utils/assets';

const MESSAGES = [
  { jp: "頑張って!", vi: "Cố lên nhé!", emoji: "🐾" },
  { jp: "今日も勉強しよう!", vi: "Hôm nay cũng học thôi!", emoji: "🎌" },
  { jp: "すごい!", vi: "Tuyệt vời!", emoji: "🌟" },
  { jp: "一緒に頑張ろう!", vi: "Cùng cố gắng nào!", emoji: "🌸" },
];

export const FloatingMascot: React.FC = () => {
  const [messageIndex, setMessageIndex] = useState(0);
  const [showMessage, setShowMessage] = useState(true);
  const [isMinimized, setIsMinimized] = useState(false);
  const [hasInteracted, setHasInteracted] = useState(false);

  // Show message after a delay on first load
  useEffect(() => {
    const timer = setTimeout(() => setShowMessage(true), 2000);
    return () => clearTimeout(timer);
  }, []);

  // Rotate messages every 10 seconds
  useEffect(() => {
    if (isMinimized) return;
    const interval = setInterval(() => {
      setShowMessage(false);
      setTimeout(() => {
        setMessageIndex((prev) => (prev + 1) % MESSAGES.length);
        setShowMessage(true);
      }, 400);
    }, 10000);
    return () => clearInterval(interval);
  }, [isMinimized]);

  const currentMsg = MESSAGES[messageIndex];

  if (isMinimized) {
    return (
      <Box
        onClick={() => { setIsMinimized(false); setHasInteracted(true); }}
        sx={{
          position: 'fixed',
          bottom: { xs: 16, md: 32 },
          left: { xs: 16, md: 24 },
          zIndex: 9999,
          cursor: 'pointer',
          width: 48, height: 48,
          borderRadius: '50%',
          bgcolor: '#ffffff',
          boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
          border: '2px solid #FFF0F0',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          overflow: 'hidden',
          transition: 'all 0.3s ease',
          opacity: 0.75,
          '&:hover': { opacity: 1, transform: 'scale(1.1)' },
        }}
      >
        <Box
          component="img"
          src={getAsset('shiba_mascot.png')}
          alt="Shiba"
          sx={{ width: '110%', height: '110%', objectFit: 'cover' }}
        />
      </Box>
    );
  }

  return (
    <Box
      sx={{
        position: 'fixed',
        bottom: { xs: 16, md: 32 },
        left: { xs: 16, md: 24 },
        zIndex: 9999,
        display: 'flex',
        alignItems: 'flex-end',
        gap: 1.5,
      }}
    >
      {/* Mascot avatar */}
      <Box
        onClick={() => {
          setShowMessage(false);
          setHasInteracted(true);
          setTimeout(() => {
            setMessageIndex((prev) => (prev + 1) % MESSAGES.length);
            setShowMessage(true);
          }, 300);
        }}
        sx={{
          width: 56, height: 56,
          borderRadius: '50%',
          bgcolor: '#ffffff',
          boxShadow: '0 4px 16px rgba(196, 30, 58, 0.12)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          overflow: 'hidden',
          border: '2.5px solid #FFF0F0',
          cursor: 'pointer',
          flexShrink: 0,
          animation: hasInteracted ? 'none' : 'gentleFloat 4s ease-in-out infinite',
          transition: 'transform 0.3s ease',
          '&:hover': { transform: 'scale(1.08)' },
          '@keyframes gentleFloat': {
            '0%, 100%': { transform: 'translateY(0)' },
            '50%': { transform: 'translateY(-5px)' },
          }
        }}
      >
        <Box
          component="img"
          src={getAsset('shiba_mascot.png')}
          alt="Shiba Mascot"
          sx={{ width: '115%', height: '115%', objectFit: 'cover' }}
        />
      </Box>

      {/* Speech bubble */}
      <Fade in={showMessage} timeout={400}>
        <Box
          sx={{
            bgcolor: '#ffffff',
            borderRadius: '14px',
            borderBottomLeftRadius: '4px',
            boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
            border: '1.5px solid #FFF0F0',
            p: 1.5,
            maxWidth: 200,
            position: 'relative',
          }}
        >
          {/* Close / minimize */}
          <Box
            onClick={() => setIsMinimized(true)}
            sx={{
              position: 'absolute',
              top: 4, right: 4,
              width: 18, height: 18,
              borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer',
              color: '#94a3b8',
              transition: 'color 0.2s',
              '&:hover': { color: '#C41E3A' },
            }}
          >
            <CloseIcon sx={{ fontSize: 12 }} />
          </Box>

          <Typography
            sx={{
              fontFamily: '"Noto Sans JP", sans-serif',
              fontWeight: 700,
              color: '#C41E3A',
              fontSize: '0.85rem',
              lineHeight: 1.3,
              mb: 0.3,
            }}
          >
            {currentMsg.jp} {currentMsg.emoji}
          </Typography>
          <Typography sx={{ color: '#64748b', fontSize: '0.72rem', fontWeight: 500 }}>
            {currentMsg.vi}
          </Typography>
        </Box>
      </Fade>
    </Box>
  );
};
