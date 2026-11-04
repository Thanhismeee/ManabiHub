import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../services/notificationService';
import type { NotificationQueryParams } from '../types';

const QUERY_KEYS = {
  notifications: 'notifications',
  unreadCount: 'unreadCount',
} as const;

export function useNotifications(params?: NotificationQueryParams) {
  return useQuery({
    queryKey: [QUERY_KEYS.notifications, params],
    queryFn: () => notificationService.fetchNotifications(params),
    refetchInterval: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useUnreadCount(enabled = true) {
  return useQuery({
    queryKey: [QUERY_KEYS.unreadCount],
    queryFn: () => notificationService.fetchUnreadCount(),
    enabled,
    refetchInterval: enabled ? 30000 : false,
    refetchOnWindowFocus: true,
  });
}

export function useMarkAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationId: string) => notificationService.markAsRead(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.notifications] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.unreadCount] });
    },
  });
}

export function useMarkAsUnread() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationId: string) => notificationService.markAsUnread(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.notifications] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.unreadCount] });
    },
  });
}

export function useMarkAllAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => notificationService.markAllAsRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.notifications] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.unreadCount] });
    },
  });
}
