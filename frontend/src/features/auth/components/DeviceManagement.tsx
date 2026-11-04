import { useState, useEffect } from 'react';
import { Box, Typography, Button, Paper, List, ListItem, ListItemText, ListItemSecondaryAction, CircularProgress, Alert } from '@mui/material';
import { axiosClient } from '../../../shared/api/axiosClient';

interface Device {
  id: string;
  displayName: string;
  userAgent: string;
  lastSeenAt: string;
}

export function DeviceManagement() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDevices = async () => {
    setLoading(true);
    try {
      const response = await axiosClient.get('/public/devices');
      setDevices(response.data.data);
      setError(null);
    } catch (err) {
      setError('Lỗi khi tải danh sách thiết bị. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, []);

  const handleRevoke = async (deviceId: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn đăng xuất thiết bị này?')) return;
    
    try {
      await axiosClient.post(`/public/devices/${deviceId}/revoke`);
      setDevices(devices.filter(d => d.id !== deviceId));
    } catch (err) {
      alert('Không thể đăng xuất thiết bị lúc này.');
    }
  };

  if (loading) {
    return <CircularProgress />;
  }

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Quản lý thiết bị
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Bạn có thể đăng nhập tối đa 2 thiết bị. Hãy đăng xuất các thiết bị không sử dụng.
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Paper variant="outlined">
        <List>
          {devices.map((device, index) => (
            <ListItem divider={index < devices.length - 1} key={device.id}>
              <ListItemText
                primary={device.displayName}
                secondary={`Hoạt động lần cuối: ${new Date(device.lastSeenAt).toLocaleString()}`}
              />
              <ListItemSecondaryAction>
                <Button 
                  variant="outlined" 
                  color="error" 
                  size="small"
                  onClick={() => handleRevoke(device.id)}
                >
                  Đăng xuất
                </Button>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
          {devices.length === 0 && (
            <ListItem>
              <ListItemText primary="Không có thiết bị nào." />
            </ListItem>
          )}
        </List>
      </Paper>
    </Box>
  );
}
