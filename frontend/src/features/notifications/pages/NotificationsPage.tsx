import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  CircularProgress,
  Alert,
  Pagination,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import { NotificationFilter } from '../components/NotificationFilter';
import { NotificationItem } from '../components/NotificationItem';
import { useNotifications, useUnreadCount, useMarkAsRead, useMarkAsUnread, useMarkAllAsRead } from '../hooks/useNotifications';
import type { ReadFilter } from '../types';

export const NotificationsPage: React.FC = () => {
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [readFilter, setReadFilter] = useState<ReadFilter>('ALL');
  const [page, setPage] = useState(0);
  const pageSize = 10;

  // Build query params
  const queryParams = {
    type: selectedType ?? undefined,
    isRead: readFilter === 'ALL' ? undefined : readFilter === 'READ',
    page,
    size: pageSize,
  };

  const { data: notificationsPage, isLoading, isError, error } = useNotifications(queryParams);
  const { data: unreadCount } = useUnreadCount();
  const markAsRead = useMarkAsRead();
  const markAsUnread = useMarkAsUnread();
  const markAllAsRead = useMarkAllAsRead();

  const handleMarkAsRead = (id: string) => {
    markAsRead.mutate(id);
  };

  const handleMarkAsUnread = (id: string) => {
    markAsUnread.mutate(id);
  };

  const handleMarkAllAsRead = () => {
    markAllAsRead.mutate();
  };

  const handlePageChange = (_: React.ChangeEvent<unknown>, value: number) => {
    setPage(value - 1); // MUI Pagination is 1-indexed, API is 0-indexed
  };

  return (
    <Box>
      {/* Header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 3,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <NotificationsIcon sx={{ fontSize: 28, color: 'text.primary' }} />
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 700, color: 'text.primary' }}>
              Thông báo
            </Typography>
            {unreadCount !== undefined && unreadCount > 0 && (
              <Typography variant="body2" sx={{ color: 'primary.main', fontWeight: 500 }}>
                {unreadCount} thông báo chưa đọc
              </Typography>
            )}
          </Box>
        </Box>

        <Button
          variant="outlined"
          startIcon={<DoneAllIcon />}
          onClick={handleMarkAllAsRead}
          disabled={markAllAsRead.isPending || unreadCount === 0}
          sx={{
            borderRadius: 2,
            textTransform: 'none',
            fontWeight: 600,
            borderColor: 'divider',
            color: 'text.primary',
            '&:hover': {
              borderColor: 'primary.main',
              bgcolor: 'rgba(79, 70, 229, 0.04)',
            },
          }}
        >
          Đánh dấu tất cả đã đọc
        </Button>
      </Box>

      {/* Filters */}
      <Box sx={{ mb: 3 }}>
        <NotificationFilter
          selectedType={selectedType}
          onTypeChange={(type) => {
            setSelectedType(type);
            setPage(0);
          }}
          readFilter={readFilter}
          onReadFilterChange={(filter) => {
            setReadFilter(filter);
            setPage(0);
          }}
        />
      </Box>

      {/* Content */}
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && (
        <Alert severity="error" sx={{ borderRadius: 2 }}>
          Không thể tải thông báo. Vui lòng thử lại sau.
          {error instanceof Error && `: ${error.message}`}
        </Alert>
      )}

      {!isLoading && !isError && notificationsPage && (
        <>
          {notificationsPage.content.length === 0 ? (
            <Box
              sx={{
                textAlign: 'center',
                py: 8,
                bgcolor: 'background.paper',
                borderRadius: 3,
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <NotificationsIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 2 }} />
              <Typography variant="h6" sx={{ color: 'text.secondary', fontWeight: 500 }}>
                Không có thông báo nào
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.disabled', mt: 0.5 }}>
                Bạn sẽ nhận thông báo khi có cập nhật mới
              </Typography>
            </Box>
          ) : (
            <>
              <Box>
                {notificationsPage.content.map((notification) => (
                  <NotificationItem
                    key={notification.id}
                    notification={notification}
                    onMarkAsRead={handleMarkAsRead}
                    onMarkAsUnread={handleMarkAsUnread}
                  />
                ))}
              </Box>

              {/* Pagination */}
              {notificationsPage.totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
                  <Pagination
                    count={notificationsPage.totalPages}
                    page={page + 1}
                    onChange={handlePageChange}
                    color="primary"
                    shape="rounded"
                    sx={{
                      '& .MuiPaginationItem-root': {
                        borderRadius: 2,
                      },
                    }}
                  />
                </Box>
              )}
            </>
          )}
        </>
      )}
    </Box>
  );
};
