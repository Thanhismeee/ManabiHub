import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type { OrderResponse } from '../../checkout/types';

export type OrderStatus = OrderResponse['status'];

export interface OrderHistoryParams {
  page: number;
  size: number;
  status?: OrderStatus;
}

export async function fetchOrderHistory(
  params: OrderHistoryParams,
): Promise<PageResponse<OrderResponse>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<OrderResponse>>>(
    ENDPOINTS.orders.list,
    { params },
  );
  return response.data.data;
}
