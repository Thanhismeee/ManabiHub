import React from 'react';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { theme } from '../theme/theme';

interface AppShellProps {
  children: React.ReactNode;
}

export const AppShell: React.FC<AppShellProps> = ({ children }) => {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  );
};
