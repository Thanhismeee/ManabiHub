import React from 'react';
import { Box, Button, Typography } from '@mui/material';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import HomeIcon from '@mui/icons-material/Home';
import { useNavigate } from 'react-router-dom';
import { getAuthSession, getDefaultRoute } from '../../auth/authSession';

export const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();
  const publicSession = getAuthSession('public');
  const adminSession = getAuthSession('admin');
  const session = adminSession ?? publicSession;
  const homeRoute = session ? getDefaultRoute(session) : '/';

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '80vh',
        p: 4,
        textAlign: 'center',
      }}
    >
      <SearchOffIcon sx={{ fontSize: 80, color: 'text.disabled', mb: 3 }} />
      <Typography variant="h4" sx={{ fontWeight: 800, mb: 1 }}>
        404
      </Typography>
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>
        Không tìm thấy trang
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 420, mb: 4 }}>
        Trang bạn đang tìm kiếm không tồn tại hoặc đã được di chuyển.
      </Typography>
      <Button
        variant="contained"
        startIcon={<HomeIcon />}
        onClick={() => navigate(homeRoute, { replace: true })}
        size="large"
      >
        Về trang chủ
      </Button>
    </Box>
  );
};
