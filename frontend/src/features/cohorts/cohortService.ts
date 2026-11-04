import { axiosClient } from '../../shared/api/axiosClient';
import { ENDPOINTS } from '../../shared/api/endpoints';
import type { LearningLanguage, ProficiencyFramework } from '../../shared/learning/languageProfiles';

export type CohortStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
export type CohortMemberStatus = 'ACTIVE' | 'REMOVED' | 'COMPLETED';

export interface CohortMember {
  id: string;
  studentId: string;
  fullName: string;
  email: string;
  status: CohortMemberStatus;
  enrollmentStatus?: string | null;
  joinedAt: string;
  accessExpiresAt?: string | null;
}

export interface LearningCohort {
  id: string;
  courseId: string;
  courseTitle: string;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  proficiencyLevel: string;
  name: string;
  code: string;
  organizationName: string;
  branchName?: string | null;
  startDate: string;
  endDate: string;
  maxStudents: number;
  status: CohortStatus;
  activeStudentCount: number;
  members: CohortMember[];
  createdAt: string;
  updatedAt?: string | null;
}

export interface CreateCohortPayload {
  courseId: string;
  name: string;
  code?: string;
  organizationName: string;
  branchName?: string;
  startDate: string;
  endDate: string;
  maxStudents: number;
}

interface ApiResponse<T> {
  success: boolean;
  messageCode: string;
  message: string;
  data: T;
}

export async function listTeacherCohorts() {
  const response = await axiosClient.get<ApiResponse<LearningCohort[]>>(ENDPOINTS.teacherCohorts.list);
  return response.data.data;
}

export async function createTeacherCohort(payload: CreateCohortPayload) {
  const response = await axiosClient.post<ApiResponse<LearningCohort>>(ENDPOINTS.teacherCohorts.list, payload);
  return response.data.data;
}

export async function updateTeacherCohortStatus(id: string, status: CohortStatus) {
  const response = await axiosClient.patch<ApiResponse<LearningCohort>>(
    ENDPOINTS.teacherCohorts.status(id),
    { status },
  );
  return response.data.data;
}

export async function addTeacherCohortMember(id: string, studentEmail: string) {
  const response = await axiosClient.post<ApiResponse<LearningCohort>>(
    ENDPOINTS.teacherCohorts.members(id),
    { studentEmail },
  );
  return response.data.data;
}

export async function removeTeacherCohortMember(cohortId: string, memberId: string) {
  const response = await axiosClient.delete<ApiResponse<LearningCohort>>(
    ENDPOINTS.teacherCohorts.member(cohortId, memberId),
  );
  return response.data.data;
}
