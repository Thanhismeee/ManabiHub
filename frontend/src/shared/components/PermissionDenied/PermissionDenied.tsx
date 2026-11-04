import React from 'react';
import { Box, Button, Typography } from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import { getAuthSession, getDefaultRoute } from '../../auth/authSession';

interface PermissionDeniedProps {
  /** Optional explanation of which role is required */
  requiredRole?: string;
}

export const PermissionDenied: React.FC<PermissionDeniedProps> = ({ requiredRole }) => {
  const navigate = useNavigate();
  const publicSession = getAuthSession('public');
  const adminSession = getAuthSession('admin');
  const session = adminSession ?? publicSession;
  const backRoute = session ? getDefaultRoute(session) : '/';

  return (
    <Box
      role="alert"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        p: 4,
        textAlign: 'center',
      }}
    >
      <BlockIcon sx={{ fontSize: 72, color: 'warning.main', mb: 3 }} />
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
        Không có quyền truy cập
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 440, mb: 1 }}>
        Bạn không có quyền truy cập trang này.
      </Typography>
      {requiredRole && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Quyền yêu cầu: <strong>{requiredRole}</strong>
        </Typography>
      )}
      {!requiredRole && <Box sx={{ mb: 3 }} />}
      <Button
        variant="outlined"
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate(backRoute, { replace: true })}
      >
        Quay lại trang chính
      </Button>
    </Box>
  );
};
