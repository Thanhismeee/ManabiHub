import React from 'react';
import { Box, Typography, CircularProgress, Alert, Grid, Paper } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { adminAuditApi } from '../api/adminAuditApi';

export const AdminAuditLogDetailPage: React.FC<{ id: string }> = ({ id }) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['audit-log-detail', id],
    queryFn: () => adminAuditApi.getAuditLogDetail(id),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !data) {
    return <Alert severity="error">Không thể tải thông tin chi tiết nhật ký.</Alert>;
  }

  return (
    <Box>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6 }}>
          <Typography variant="caption" color="textSecondary">Người thực hiện</Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {data.actorDisplayName || 'N/A'} {data.actorEmail ? `(${data.actorEmail})` : ''}
          </Typography>

          <Typography variant="caption" color="textSecondary">Vai trò</Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {data.actorRoleCode || 'N/A'}
          </Typography>

          <Typography variant="caption" color="textSecondary">Thời gian</Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {new Date(data.createdAt).toLocaleString('vi-VN')}
          </Typography>
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <Typography variant="caption" color="textSecondary">Hành động</Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {data.action}
          </Typography>

          <Typography variant="caption" color="textSecondary">Loại đối tượng</Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {data.targetType}
          </Typography>

          <Typography variant="caption" color="textSecondary">ID đối tượng</Typography>
          <Typography variant="body2" sx={{ mb: 1, wordBreak: 'break-all' }}>
            {data.targetId || 'N/A'}
          </Typography>
        </Grid>
      </Grid>

      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>Dữ liệu Metadata</Typography>
      <Paper variant="outlined" sx={{ p: 2, bgcolor: '#f8f9fa', overflowX: 'auto' }}>
        <pre style={{ margin: 0, fontSize: '0.85rem' }}>
          {JSON.stringify(data.metadata || {}, null, 2)}
        </pre>
      </Paper>

      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>Thay đổi dữ liệu (Before / After)</Typography>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6 }}>
          <Paper variant="outlined" sx={{ p: 2, bgcolor: '#fef2f2', overflowX: 'auto', height: '100%' }}>
            <Typography variant="caption" color="error">Before Value</Typography>
            <pre style={{ margin: 0, fontSize: '0.85rem' }}>
              {JSON.stringify(data.beforeValue || {}, null, 2)}
            </pre>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <Paper variant="outlined" sx={{ p: 2, bgcolor: '#f0fdf4', overflowX: 'auto', height: '100%' }}>
            <Typography variant="caption" color="success.main">After Value</Typography>
            <pre style={{ margin: 0, fontSize: '0.85rem' }}>
              {JSON.stringify(data.afterValue || {}, null, 2)}
            </pre>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};
