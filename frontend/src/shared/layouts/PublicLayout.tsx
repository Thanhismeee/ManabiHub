import React from 'react';
import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { LandingHeader } from '../../app/pages/PublicHomePage/LandingHeader';
import { LandingFooter } from '../../app/pages/PublicHomePage/LandingFooter';

export const PublicLayout: React.FC = () => {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: '#FBF9F5' }}>
      <LandingHeader />
      <Box component="main" sx={{ flexGrow: 1 }}>
        <Outlet />
      </Box>
      <LandingFooter />
    </Box>
  );
};
