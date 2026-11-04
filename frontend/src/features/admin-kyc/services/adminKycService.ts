import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';

export interface KycRequestResponse {
  id: string;
  teacherId: string;
  teacherEmail: string;
  teacherFullName: string;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CORRECTION_REQUIRED' | 'REVOKED';
  displayName: string;
  idCardFrontUrl: string | null;
  idCardBackUrl: string | null;
  certificateUrl: string | null;
  selfieUrl: string | null;
  copyrightAccepted: boolean;
  vnptVerificationStatus: string | null;
  vnptResponseDetails: string | null;
  riskLevel: string | null;
  exceptionStage: string | null;
  exceptionType: string | null;
  exceptionReason: string | null;
  certificateCode: string | null;
  certificateHolderName: string | null;
  certificateDateOfBirth: string | null;
  certificateLevel: string | null;
  certificateOcrText: string | null;
  decisionNote: string | null;
  createdAt: string;
  updatedAt: string;
  processedByEmail: string | null;
  processedAt: string | null;
}

export interface ApiResponse<T> {
  success: boolean;
  messageCode: string;
  message: string;
  data: T;
  errors?: unknown;
}

export interface KycReviewRequest {
  status: 'APPROVED' | 'REJECTED' | 'CORRECTION_REQUIRED';
  decisionNote?: string;
}

export interface TeacherQualificationResponse {
  id: string;
  teacherId: string;
  teacherName?: string | null;
  teacherEmail?: string | null;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  credentialLevel: string;
  credentialType: string;
  maskedCredentialCode?: string | null;
  issuer: string;
  evidenceFileName: string;
  evidenceUrl?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewNote?: string | null;
  submittedAt: string;
  reviewedAt?: string | null;
}

// Map database statuses to human-friendly labels
export const KYC_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  CORRECTION_REQUIRED: 'Yêu cầu sửa đổi',
  DRAFT: 'Bản nháp',
  REVOKED: 'Đã thu hồi',
};

export const adminKycService = {
  getPendingKycQueue: async (): Promise<KycRequestResponse[]> => {
    const response = await axiosClient.get<ApiResponse<KycRequestResponse[]>>(
      ENDPOINTS.ADMIN_KYC.QUEUE
    );
    return response.data.data;
  },

  getKycDetail: async (id: string): Promise<KycRequestResponse> => {
    const response = await axiosClient.get<ApiResponse<KycRequestResponse>>(
      ENDPOINTS.ADMIN_KYC.DETAIL(id)
    );
    return response.data.data;
  },

  reviewKyc: async (id: string, request: KycReviewRequest): Promise<KycRequestResponse> => {
    const response = await axiosClient.post<ApiResponse<KycRequestResponse>>(
      ENDPOINTS.ADMIN_KYC.REVIEW(id),
      request
    );
    return response.data.data;
  },

  getDocumentObjectUrl: async (path: string): Promise<string> => {
    const response = await axiosClient.get<Blob>(path, { responseType: 'blob' });
    return URL.createObjectURL(response.data);
  },

  getPendingQualifications: async (): Promise<TeacherQualificationResponse[]> => {
    const response = await axiosClient.get<ApiResponse<TeacherQualificationResponse[]>>(
      ENDPOINTS.ADMIN_TEACHER_QUALIFICATIONS.QUEUE,
      { params: { status: 'PENDING' } },
    );
    return response.data.data;
  },

  decideQualification: async (
    id: string,
    approved: boolean,
    reviewNote?: string,
  ): Promise<TeacherQualificationResponse> => {
    const response = await axiosClient.post<ApiResponse<TeacherQualificationResponse>>(
      ENDPOINTS.ADMIN_TEACHER_QUALIFICATIONS.DECISION(id),
      { approved, reviewNote },
    );
    return response.data.data;
  },

  getQualificationEvidenceObjectUrl: async (path: string): Promise<string> => {
    const response = await axiosClient.get<Blob>(path, { responseType: 'blob' });
    return URL.createObjectURL(response.data);
  },
};
