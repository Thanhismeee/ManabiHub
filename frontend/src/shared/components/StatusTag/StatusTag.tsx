import React from 'react';
import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';

type StatusType = 'success' | 'warning' | 'error' | 'info' | 'default';

interface StatusTagProps extends Omit<ChipProps, 'color'> {
  status: StatusType;
  label: string;
}

const statusColorMap: Record<StatusType, ChipProps['color']> = {
  success: 'success',
  warning: 'warning',
  error: 'error',
  info: 'info',
  default: 'default',
};

export const StatusTag: React.FC<StatusTagProps> = ({ status, label, ...props }) => {
  return (
    <Chip
      label={label}
      color={statusColorMap[status]}
      size="small"
      {...props}
      sx={{
        fontWeight: 600,
        ...props.sx,
      }}
    />
  );
};
