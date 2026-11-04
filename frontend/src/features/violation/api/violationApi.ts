import { axiosClient } from '../../../shared/api/axiosClient';

export interface ViolationReportRequest {
  targetType: 'COURSE' | 'LESSON' | 'LESSON_BLOCK' | 'REVIEW' | 'USER';
  targetId: string;
  reason: string;
  description: string;
}

export const submitViolationReport = async (
  data: ViolationReportRequest,
  evidenceFiles: File[] = [],
) => {
  const formData = new FormData();
  formData.append(
    'metadata',
    new Blob([JSON.stringify(data)], { type: 'application/json' }),
  );
  evidenceFiles.forEach((file) => formData.append('evidence', file));

  const response = await axiosClient.post('/v1/violations', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};
