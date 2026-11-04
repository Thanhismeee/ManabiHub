import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';

export interface StudentIdentityVerificationStatus {
  verified: boolean;
  status: 'VERIFIED' | 'NOT_VERIFIED';
  provider?: string | null;
  maskedIdNumber?: string | null;
  fullName?: string | null;
  dateOfBirth?: string | null;
  verifiedAt?: string | null;
}

export interface StudentIdentityVerificationPayload {
  providerSessionId?: string | null;
  providerTransactionId?: string | null;
  sdkResult: Record<string, unknown>;
}

export async function getStudentIdentityVerificationStatus() {
  const response = await axiosClient.get<ApiResponse<StudentIdentityVerificationStatus>>(
    ENDPOINTS.student.identityVerificationStatus,
  );
  return response.data.data;
}

export async function verifyStudentIdentity(payload: StudentIdentityVerificationPayload) {
  const response = await axiosClient.post<ApiResponse<StudentIdentityVerificationStatus>>(
    ENDPOINTS.student.identityVerification,
    payload,
  );
  return response.data.data;
}
