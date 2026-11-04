import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type { WishlistItem } from '../types';

export const wishlistService = {
  list: async (): Promise<WishlistItem[]> => {
    const response = await axiosClient.get<ApiResponse<WishlistItem[]>>(
      ENDPOINTS.student.wishlist,
    );
    return response.data.data;
  },

  add: async (courseId: string): Promise<WishlistItem> => {
    const response = await axiosClient.post<ApiResponse<WishlistItem>>(
      ENDPOINTS.student.wishlistCourse(courseId),
    );
    return response.data.data;
  },

  remove: async (courseId: string): Promise<void> => {
    await axiosClient.delete(ENDPOINTS.student.wishlistCourse(courseId));
  },
};
