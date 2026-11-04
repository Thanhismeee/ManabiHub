import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchOrderHistory } from '../services/orderHistoryService';
import type { OrderHistoryParams } from '../services/orderHistoryService';

export function useOrderHistory(params: OrderHistoryParams) {
  return useQuery({
    queryKey: ['student-order-history', params],
    queryFn: () => fetchOrderHistory(params),
    placeholderData: keepPreviousData,
  });
}
