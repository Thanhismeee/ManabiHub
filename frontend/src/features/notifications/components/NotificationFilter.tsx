import React from 'react';
import { Box, Chip, FormControl, InputLabel, MenuItem, Select, Stack } from '@mui/material';
import { NOTIFICATION_TYPES, NOTIFICATION_TYPE_KEYS } from '../types';
import type { ReadFilter } from '../types';

interface NotificationFilterProps {
  selectedType: string | null;
  onTypeChange: (type: string | null) => void;
  readFilter: ReadFilter;
  onReadFilterChange: (filter: ReadFilter) => void;
}

export const NotificationFilter: React.FC<NotificationFilterProps> = ({
  selectedType,
  onTypeChange,
  readFilter,
  onReadFilterChange,
}) => {
  return (
    <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: 2, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
      <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 260 } }}>
        <InputLabel id="notification-type-label">Loại thông báo</InputLabel>
        <Select
          labelId="notification-type-label"
          value={selectedType ?? ''}
          label="Loại thông báo"
          onChange={(event) => onTypeChange(event.target.value || null)}
        >
          <MenuItem value="">Tất cả loại</MenuItem>
          {NOTIFICATION_TYPE_KEYS.map((typeKey) => (
            <MenuItem key={typeKey} value={typeKey}>
              {NOTIFICATION_TYPES[typeKey].icon} {NOTIFICATION_TYPES[typeKey].label}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Read status filter */}
      <Stack direction="row" spacing={1}>
        {([
          { key: 'ALL' as ReadFilter, label: 'Tất cả' },
          { key: 'UNREAD' as ReadFilter, label: 'Chưa đọc' },
          { key: 'READ' as ReadFilter, label: 'Đã đọc' },
        ]).map(({ key, label }) => (
          <Chip
            key={key}
            label={label}
            onClick={() => onReadFilterChange(key)}
            variant={readFilter === key ? 'filled' : 'outlined'}
            color={readFilter === key ? 'primary' : 'default'}
            sx={{
              fontWeight: readFilter === key ? 600 : 400,
              borderRadius: '8px',
              px: 0.5,
              ...(readFilter !== key && {
                borderColor: '#D1D5DB',
                color: 'text.secondary',
              }),
            }}
          />
        ))}
      </Stack>
    </Box>
  );
};
