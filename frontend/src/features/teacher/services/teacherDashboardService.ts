import { axiosClient } from '../../../shared/api/axiosClient';

import { ENDPOINTS } from '../../../shared/api/endpoints';

export interface TeacherDashboardCourse {
  id: string;
  title: string;
  slug: string;
  jlptLevel?: string;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'FORCED_DRAFT' | 'ARCHIVED';
  createdAt: string;
}

export interface TeacherDashboardResponse {
  totalCourses: number;
  draftOrCorrection: number;
  pendingApproval: number;
  published: number;
  recentCourses: TeacherDashboardCourse[];
}

export const fetchTeacherDashboardStats = async (): Promise<TeacherDashboardResponse> => {
  const response = await axiosClient.get(ENDPOINTS.teacherDashboard.stats);
  return response.data.data;
};
