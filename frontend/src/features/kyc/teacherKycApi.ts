import { axiosClient } from '../../shared/api/axiosClient';
import { ENDPOINTS } from '../../shared/api/endpoints';
import type { LearningLanguage, ProficiencyFramework } from '../../shared/learning/languageProfiles';

export interface ApiEnvelope<T> {
  success: boolean;
  messageCode: string;
  message: string;
  data: T;
  timestamp: string;
  path?: string;
}

export interface KycDocumentResponse {
  id: string;
  documentType: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  fileHash: string;
  createdAt: string;
}

export interface KycModuleStatusResponse {
  status: string;
  statusLabel: string;
  canInteract: boolean;
  completedAt?: string | null;
  detail?: string | null;
}

export interface KycRequestResponse {
  requestId: string;
  status: string;
  statusLabel: string;
  submittedAt: string;
  ekycProvider?: string | null;
  ekycReferenceId?: string | null;
  providerSessionId?: string | null;
  providerTransactionId?: string | null;
  identityStatus: string;
  identityStatusLabel: string;
  identityVerifiedAt?: string | null;
  certificateStatus: string;
  certificateStatusLabel: string;
  certificateSubmittedAt?: string | null;
  riskLevel?: string | null;
  certificateCode?: string | null;
  copyrightAgreed: boolean;
  verificationPayload: Record<string, unknown>;
  documents: KycDocumentResponse[];
}

export interface KycStatusResponse {
  teacherId: string;
  userId: string;
  teacherKycStatus: string;
  teacherKycStatusLabel: string;
  canPublishCourse: boolean;
  identityVerification: KycModuleStatusResponse;
  certificateVerification: KycModuleStatusResponse;
  latestRequest?: KycRequestResponse | null;
  srsTrace: Record<string, unknown>;
}

export interface KycIdentityVerificationPayload {
  providerSessionId?: string | null;
  providerTransactionId?: string | null;
  sdkResult: Record<string, unknown>;
}

export interface KycIdentityVerificationResponse {
  teacherId: string;
  teacherKycStatus: string;
  request: KycRequestResponse;
  identityVerification: KycModuleStatusResponse;
  certificateVerification: KycModuleStatusResponse;
  auditLogged: boolean;
  srsTrace: Record<string, unknown>;
}

export interface KycCertificateSubmissionResponse {
  teacherId: string;
  teacherKycStatus: string;
  canPublishCourse: boolean;
  request: KycRequestResponse;
  identityVerification: KycModuleStatusResponse;
  certificateVerification: KycModuleStatusResponse;
  adminNotificationCreated: boolean;
  auditLogged: boolean;
  teacherWorkspaceAvailable: boolean;
  reviewEta: string;
  sessionToken: string;
  srsTrace: Record<string, unknown>;
}

export interface KycRestartVerificationResponse {
  teacherId: string;
  teacherKycStatus: string;
  canPublishCourse: boolean;
  request: KycRequestResponse;
  identityVerification: KycModuleStatusResponse;
  certificateVerification: KycModuleStatusResponse;
  auditLogged: boolean;
  srsTrace: Record<string, unknown>;
}

export interface KycCertificateSubmissionPayload {
  certificate: File;
  certificateCode: string;
  certificateHolderName: string;
  certificateDateOfBirth: string;
  certificateLevel: string;
  certificateOcrText: string;
  copyrightAgreementAccepted: boolean;
}

export type TeacherCredentialType =
  | 'OFFICIAL_CERTIFICATE'
  | 'UNIVERSITY_DEGREE'
  | 'SOFL_INTERNAL_APPROVAL'
  | 'EMPLOYMENT_RECORD';

export type TeacherQualificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface TeacherQualificationResponse {
  id: string;
  teacherId: string;
  teacherName?: string | null;
  teacherEmail?: string | null;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  credentialLevel: string;
  credentialType: TeacherCredentialType;
  maskedCredentialCode?: string | null;
  issuer: string;
  evidenceFileName: string;
  evidenceUrl?: string | null;
  status: TeacherQualificationStatus;
  reviewNote?: string | null;
  submittedAt: string;
  reviewedAt?: string | null;
}

export interface TeacherQualificationSubmissionPayload {
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  proficiencyLevel: string;
  credentialType: TeacherCredentialType;
  credentialCode: string;
  issuer: string;
  evidence: File;
  copyrightAgreementAccepted: boolean;
}

export async function getTeacherKycStatus() {
  const response = await axiosClient.get<ApiEnvelope<KycStatusResponse>>(ENDPOINTS.teacherKyc.status);

  return response.data.data;
}

export async function verifyTeacherIdentity(payload: KycIdentityVerificationPayload) {
  const response = await axiosClient.post<ApiEnvelope<KycIdentityVerificationResponse>>(
    ENDPOINTS.teacherKyc.identityVerifications,
    payload,
  );

  return response.data;
}

export async function restartTeacherVerification() {
  const response = await axiosClient.post<ApiEnvelope<KycRestartVerificationResponse>>(
    ENDPOINTS.teacherKyc.restartVerification,
    undefined,
  );

  return response.data;
}

export async function submitTeacherCertificate(payload: KycCertificateSubmissionPayload) {
  const formData = new FormData();
  formData.append('certificate', payload.certificate);
  formData.append('certificateCode', payload.certificateCode.trim());
  formData.append('certificateHolderName', payload.certificateHolderName.trim());
  formData.append('certificateDateOfBirth', payload.certificateDateOfBirth);
  formData.append('certificateLevel', payload.certificateLevel);
  formData.append('certificateOcrText', payload.certificateOcrText);
  formData.append('copyrightAgreementAccepted', String(payload.copyrightAgreementAccepted));

  const response = await axiosClient.post<ApiEnvelope<KycCertificateSubmissionResponse>>(
    ENDPOINTS.teacherKyc.certificateSubmissions,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );

  return response.data;
}

export async function getTeacherQualifications() {
  const response = await axiosClient.get<ApiEnvelope<TeacherQualificationResponse[]>>(
    ENDPOINTS.teacherKyc.qualifications,
  );
  return response.data.data;
}

export async function submitTeacherQualification(payload: TeacherQualificationSubmissionPayload) {
  const formData = new FormData();
  formData.append('learningLanguage', payload.learningLanguage);
  formData.append('proficiencyFramework', payload.proficiencyFramework);
  formData.append('frameworkVersion', payload.frameworkVersion);
  formData.append('proficiencyLevel', payload.proficiencyLevel);
  formData.append('credentialType', payload.credentialType);
  formData.append('credentialCode', payload.credentialCode.trim());
  formData.append('issuer', payload.issuer.trim());
  formData.append('copyrightAgreementAccepted', String(payload.copyrightAgreementAccepted));
  formData.append('evidence', payload.evidence);

  const response = await axiosClient.post<ApiEnvelope<TeacherQualificationResponse>>(
    ENDPOINTS.teacherKyc.qualifications,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data.data;
}

export async function getTeacherQualificationEvidenceObjectUrl(path: string) {
  const response = await axiosClient.get<Blob>(path, { responseType: 'blob' });
  return URL.createObjectURL(response.data);
}
