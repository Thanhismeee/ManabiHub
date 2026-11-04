import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type { CreateStudentRefundRequest, StudentRefundResponse } from '../types';

export async function fetchStudentRefunds(
  page = 0,
  size = 100,
): Promise<PageResponse<StudentRefundResponse>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<StudentRefundResponse>>>(
    ENDPOINTS.studentRefunds.list,
    { params: { page, size, sort: 'createdAt,desc' } },
  );
  return response.data.data;
}

export async function createStudentRefund(
  request: CreateStudentRefundRequest,
): Promise<StudentRefundResponse> {
  const response = await axiosClient.post<ApiResponse<StudentRefundResponse>>(
    ENDPOINTS.studentRefunds.create,
    request,
  );
  return response.data.data;
}

export async function cancelStudentRefund(id: string): Promise<StudentRefundResponse> {
  const response = await axiosClient.post<ApiResponse<StudentRefundResponse>>(
    ENDPOINTS.studentRefunds.cancel(id),
  );
  return response.data.data;
}

export async function fetchStudentRefundDetail(id: string): Promise<StudentRefundResponse> {
  const response = await axiosClient.get<ApiResponse<StudentRefundResponse>>(
    ENDPOINTS.studentRefunds.detail(id),
  );
  return response.data.data;
}
