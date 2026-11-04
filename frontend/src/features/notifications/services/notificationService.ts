import { axiosClient } from '../../../shared/api/axiosClient';
import type { NotificationResponse, NotificationQueryParams, PageResponse } from '../types';
import type { ApiResponse } from '../../../shared/types/api';

const BASE_URL = '/v1/notifications';

export const notificationService = {
  async fetchNotifications(params?: NotificationQueryParams): Promise<PageResponse<NotificationResponse>> {
    const queryParams: Record<string, string | number | boolean> = {};
    if (params?.type) queryParams.type = params.type;
    if (params?.isRead !== undefined) queryParams.isRead = params.isRead;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;

    const { data } = await axiosClient.get<ApiResponse<PageResponse<NotificationResponse>>>(BASE_URL, {
      params: queryParams,
    });
    return data.data;
  },

  async fetchUnreadCount(): Promise<number> {
    const { data } = await axiosClient.get<ApiResponse<{ unreadCount: number }>>(`${BASE_URL}/unread-count`);
    return data.data.unreadCount;
  },

  async markAsRead(notificationId: string): Promise<NotificationResponse> {
    const { data } = await axiosClient.patch<ApiResponse<NotificationResponse>>(
      `${BASE_URL}/${notificationId}/read`,
      null
    );
    return data.data;
  },

  async markAsUnread(notificationId: string): Promise<NotificationResponse> {
    const { data } = await axiosClient.patch<ApiResponse<NotificationResponse>>(
      `${BASE_URL}/${notificationId}/unread`,
      null
    );
    return data.data;
  },

  async markAllAsRead(): Promise<number> {
    const { data } = await axiosClient.patch<ApiResponse<{ updatedCount: number }>>(
      `${BASE_URL}/read-all`,
      null
    );
    return data.data.updatedCount;
  },
};
