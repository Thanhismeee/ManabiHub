import {
  Box,
  Button,
  CircularProgress,
  Divider,
  ListItemIcon,
  Menu,
  MenuItem,
  Typography,
} from '@mui/material';
import ArrowForwardIosOutlinedIcon from '@mui/icons-material/ArrowForwardIosOutlined';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { notificationService } from '../../features/notifications/services/notificationService';
import { NOTIFICATION_TYPES } from '../../features/notifications/types';
import type { NotificationResponse } from '../../features/notifications/types';
import { useMarkAsRead } from '../../features/notifications/hooks/useNotifications';
import { getSafeNotificationActionPath } from '../../features/notifications/utils/notificationActionUrl';

interface NotificationMenuProps {
  anchorEl: HTMLElement | null;
  onClose: () => void;
  onViewAll: () => void;
  scopeKey: string | null;
}

export function NotificationMenu({ anchorEl, onClose, onViewAll, scopeKey }: NotificationMenuProps) {
  const navigate = useNavigate();
  const markAsRead = useMarkAsRead();
  const previewQuery = useQuery({
    queryKey: ['notifications-preview', scopeKey],
    queryFn: () => notificationService.fetchNotifications({ page: 0, size: 5 }),
    enabled: Boolean(anchorEl && scopeKey),
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    retry: false,
  });

  const handleNotificationClick = (notification: NotificationResponse) => {
    if (!notification.read) {
      markAsRead.mutate(notification.id);
    }

    const actionPath = getSafeNotificationActionPath(notification.actionUrl);
    onClose();
    if (actionPath) navigate(actionPath);
  };

  return (
    <Menu
      id="notification-menu"
      anchorEl={anchorEl}
      open={Boolean(anchorEl)}
      onClose={onClose}
      anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
      transformOrigin={{ horizontal: 'right', vertical: 'top' }}
      slotProps={{
        paper: {
          sx: {
            mt: 1,
            width: { xs: 'calc(100vw - 24px)', sm: 390 },
            maxWidth: 'calc(100vw - 24px)',
            border: '1px solid',
            borderColor: 'grey.200',
            borderRadius: 2,
            boxShadow: '0 14px 36px rgba(15, 23, 42, 0.14)',
            overflow: 'hidden',
          },
        },
      }}
    >
      <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <NotificationsNoneOutlinedIcon sx={{ color: '#C41E3A' }} fontSize="small" />
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
            Thông báo gần đây
          </Typography>
        </Box>
        {previewQuery.data && previewQuery.data.totalElements > 5 && (
          <Typography variant="caption" color="text.secondary">
            5 mới nhất
          </Typography>
        )}
      </Box>
      <Divider />

      {previewQuery.isLoading && (
        <Box sx={{ px: 2, py: 3, display: 'flex', justifyContent: 'center' }}>
          <CircularProgress size={24} />
        </Box>
      )}
      {previewQuery.isError && (
        <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2.5 }}>
          Chưa tải được thông báo. Bạn có thể mở trang thông báo để thử lại.
        </Typography>
      )}
      {!previewQuery.isLoading && !previewQuery.isError && previewQuery.data?.content.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2.5 }}>
          Bạn chưa có thông báo mới.
        </Typography>
      )}
      {!previewQuery.isLoading && !previewQuery.isError && previewQuery.data?.content.map((notification) => (
        <MenuItem
          key={notification.id}
          onClick={() => handleNotificationClick(notification)}
          sx={{
            alignItems: 'flex-start',
            gap: 1.25,
            px: 2,
            py: 1.25,
            whiteSpace: 'normal',
            bgcolor: notification.read ? 'transparent' : 'rgba(196, 30, 58, 0.04)',
            '&:hover': { bgcolor: 'rgba(196, 30, 58, 0.08)' },
          }}
        >
          <ListItemIcon sx={{ minWidth: 32, mt: 0.25 }}>
            <Box
              sx={{
                width: 28,
                height: 28,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 1.25,
                bgcolor: NOTIFICATION_TYPES[notification.notificationType]?.bgColor ?? '#F3F4F6',
                fontSize: '0.9rem',
              }}
            >
              {NOTIFICATION_TYPES[notification.notificationType]?.icon ?? '🔔'}
            </Box>
          </ListItemIcon>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography
              variant="body2"
              sx={{ fontWeight: notification.read ? 600 : 800, lineHeight: 1.35 }}
              noWrap
            >
              {notification.title}
            </Typography>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                display: '-webkit-box',
                WebkitBoxOrient: 'vertical',
                WebkitLineClamp: 2,
                overflow: 'hidden',
                lineHeight: 1.35,
                mt: 0.25,
              }}
            >
              {notification.message}
            </Typography>
            <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.5 }}>
              {formatRelativeTime(notification.createdAt)}
            </Typography>
          </Box>
          {!notification.read && (
            <Box sx={{ width: 7, height: 7, mt: 0.75, borderRadius: '50%', bgcolor: '#C41E3A', flexShrink: 0 }} />
          )}
        </MenuItem>
      ))}

      <Divider />
      <Box sx={{ px: 1.5, py: 1 }}>
        <Button
          fullWidth
          size="small"
          variant="text"
          onClick={onViewAll}
          endIcon={<ArrowForwardIosOutlinedIcon sx={{ fontSize: '0.7rem !important' }} />}
          sx={{
            justifyContent: 'space-between',
            px: 1.25,
            textTransform: 'none',
            color: '#C41E3A',
            bgcolor: '#FFF1F2',
            fontWeight: 800,
            '& .MuiButton-endIcon': { color: 'inherit' },
            '&:hover': {
              color: '#9D182E',
              bgcolor: '#FDE2E6',
            },
          }}
        >
          Xem tất cả thông báo
        </Button>
      </Box>
    </Menu>
  );
}

function formatRelativeTime(value: string): string {
  const date = new Date(value);
  const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60_000));
  if (minutes < 1) return 'Vừa xong';
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} ngày trước`;
  return date.toLocaleDateString('vi-VN');
}
