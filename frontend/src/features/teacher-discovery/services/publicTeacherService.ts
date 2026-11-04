import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type {
  PublicTeacherProfile,
  PublicTeacherSummary,
} from '../types/publicTeacherTypes';

export const publicTeacherService = {
  getProfile: async (teacherId: string): Promise<PublicTeacherProfile> => {
    const response = await axiosClient.get<ApiResponse<PublicTeacherProfile>>(
      ENDPOINTS.publicTeachers.detail(teacherId),
    );
    return response.data.data;
  },

  listFeatured: async (limit = 4): Promise<PublicTeacherSummary[]> => {
    const response = await axiosClient.get<ApiResponse<PublicTeacherSummary[]>>(
      ENDPOINTS.publicTeachers.list,
      { params: { limit } },
    );
    return response.data.data;
  },
};
