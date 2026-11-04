import type { Components, Theme } from '@mui/material';

export const components: Components<Theme> = {
  MuiButton: {
    defaultProps: {
      disableElevation: true,
      variant: 'contained',
    },
    styleOverrides: {
      root: {
        borderRadius: 8,
        padding: '8px 16px',
        minHeight: 40,
      },
      contained: {
        color: '#FFFFFF',
        '& .MuiButton-startIcon, & .MuiButton-endIcon': {
          color: 'inherit',
        },
      },
      sizeLarge: {
        padding: '12px 24px',
        minHeight: 48,
      },
      sizeSmall: {
        minHeight: 32,
      },
    },
  },
  MuiCard: {
    defaultProps: {
      elevation: 0,
    },
    styleOverrides: {
      root: {
        borderRadius: 8,
        border: '1px solid #E5E7EB', // matching palette.divider
      },
    },
  },
  MuiTextField: {
    defaultProps: {
      variant: 'outlined',
      size: 'small',
      fullWidth: true,
    },
    styleOverrides: {
      root: {
        '& .MuiOutlinedInput-root': {
          borderRadius: 8,
        },
      },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: {
        borderRadius: 12,
      },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: {
        borderRadius: 6,
        fontWeight: 500,
      },
    },
  },
};
