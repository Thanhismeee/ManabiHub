import React, { useState } from 'react';
import { Box, Typography, Chip, IconButton, Paper, Collapse, Button } from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import CheckIcon from '@mui/icons-material/Check';
import { NOTIFICATION_TYPES } from '../types';
import type { NotificationResponse } from '../types';
import { Link } from 'react-router-dom';
import { getSafeNotificationActionPath } from '../utils/notificationActionUrl';

interface NotificationItemProps {
  notification: NotificationResponse;
  onMarkAsRead: (id: string) => void;
  onMarkAsUnread: (id: string) => void;
}

function getRelativeTime(dateStr: string): string {
  const now = new Date();
  const date = new Date(dateStr);
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMinutes < 1) return 'Vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;
  if (diffHours < 24) return `${diffHours} giờ trước`;
  if (diffDays < 30) return `${diffDays} ngày trước`;
  return date.toLocaleDateString('vi-VN');
}

// Map notification type to an icon component
function getTypeIcon(type: string): string {
  return NOTIFICATION_TYPES[type]?.icon ?? '🔔';
}

export const NotificationItem: React.FC<NotificationItemProps> = ({
  notification,
  onMarkAsRead,
  onMarkAsUnread
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const typeConfig = NOTIFICATION_TYPES[notification.notificationType];
  const isUnread = !notification.read;
  const actionPath = getSafeNotificationActionPath(notification.actionUrl);

  const handleToggleExpand = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent bubbling if needed
    if (!isExpanded && isUnread) {
      onMarkAsRead(notification.id);
    }
    setIsExpanded(!isExpanded);
  };

  const handleMarkAsUnread = (e: React.MouseEvent) => {
    e.stopPropagation();
    onMarkAsUnread(notification.id);
    setIsExpanded(false); // Optionally close when marked as unread
  };

  return (
    <Paper
      elevation={0}
      onClick={handleToggleExpand}
      sx={{
        p: 2.5,
        mb: 1.5,
        borderRadius: 3,
        border: '1px solid',
        borderColor: isUnread ? 'primary.light' : 'divider',
        bgcolor: isUnread ? 'rgba(79, 70, 229, 0.03)' : 'background.paper',
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        '&:hover': {
          borderColor: isUnread ? 'primary.main' : '#C5CAD3',
          transform: 'translateY(-1px)',
          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
        {/* Icon */}
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: typeConfig?.bgColor ?? '#F3F4F6',
            fontSize: '1.3rem',
            flexShrink: 0,
            mt: 0.25,
          }}
        >
          {getTypeIcon(notification.notificationType)}
        </Box>

        {/* Content */}
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            {/* Unread dot */}
            {isUnread && (
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  bgcolor: 'primary.main',
                  flexShrink: 0,
                }}
              />
            )}
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: isUnread ? 700 : 500,
                color: 'text.primary',
                lineHeight: 1.4,
              }}
            >
              {notification.title}
            </Typography>
            {/* QUAN TRỌNG badge for certain types */}
            {typeConfig?.important && (
              <Chip
                label="QUAN TRỌNG"
                size="small"
                sx={{
                  height: 20,
                  fontSize: '0.65rem',
                  fontWeight: 700,
                  bgcolor: '#FEF3C7',
                  color: '#D97706',
                  borderRadius: '4px',
                }}
              />
            )}
          </Box>

          {/* Collapsed view message snippet */}
          {!isExpanded && (
            <Typography
              variant="body2"
              sx={{
                color: 'text.secondary',
                lineHeight: 1.6,
                mb: 1,
                display: '-webkit-box',
                WebkitBoxOrient: 'vertical',
                WebkitLineClamp: 2,
                overflow: 'hidden',
              }}
            >
              {notification.message}
            </Typography>
          )}

          {/* Tags and Time */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: isExpanded ? 1 : 0 }}>
            {typeConfig && (
              <Chip
                label={typeConfig.label}
                size="small"
                sx={{
                  height: 22,
                  fontSize: '0.7rem',
                  fontWeight: 600,
                  bgcolor: typeConfig.bgColor,
                  color: typeConfig.color,
                  borderRadius: '6px',
                }}
              />
            )}
            <Typography variant="caption" sx={{ color: 'text.disabled' }}>
              {getRelativeTime(notification.createdAt)}
            </Typography>
          </Box>

          {/* Expanded Content Area */}
          <Collapse in={isExpanded} timeout="auto" unmountOnExit>
            <Box
              sx={{
                mt: 2,
                p: 2,
                bgcolor: '#F9FAFB',
                borderRadius: 2,
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6, mb: 2 }}>
                {notification.message}
              </Typography>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                {actionPath && (
                  <Button
                    size="small"
                    variant="contained"
                    component={Link}
                    to={actionPath}
                    onClick={(e) => e.stopPropagation()}
                    endIcon={<ArrowForwardIcon sx={{ fontSize: 16 }} />}
                    sx={{
                      textTransform: 'none',
                      fontWeight: 600,
                      borderRadius: '8px',
                      px: 2,
                      py: 0.5,
                      boxShadow: 'none',
                      '&:hover': { boxShadow: '0 2px 8px rgba(79, 70, 229, 0.3)' }
                    }}
                  >
                    Xem chi tiết
                  </Button>
                )}

                {!isUnread && (
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={handleMarkAsUnread}
                    startIcon={<CheckIcon sx={{ fontSize: 16 }} />}
                    sx={{
                      textTransform: 'none',
                      fontWeight: 600,
                      color: 'text.secondary',
                      borderColor: 'divider',
                      borderRadius: '8px',
                      px: 2,
                      py: 0.5,
                      '&:hover': {
                        bgcolor: 'background.paper',
                        color: 'text.primary',
                        borderColor: 'text.secondary'
                      }
                    }}
                  >
                    Đánh dấu là chưa đọc
                  </Button>
                )}
              </Box>
            </Box>
          </Collapse>
        </Box>

        {/* Expand icon */}
        <IconButton
          size="small"
          onClick={handleToggleExpand}
          sx={{ color: 'text.disabled', mt: 0.5 }}
        >
          {isExpanded ? <KeyboardArrowUpIcon fontSize="small" /> : <KeyboardArrowDownIcon fontSize="small" />}
        </IconButton>
      </Box>
    </Paper>
  );
};
