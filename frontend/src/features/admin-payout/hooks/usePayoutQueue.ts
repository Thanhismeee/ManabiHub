import { useQuery } from '@tanstack/react-query';
import { adminPayoutService } from '../services/adminPayoutService';
import type { PayoutQueueParams } from '../types/payout.types';

export function usePayoutQueue(params: PayoutQueueParams) {
  return useQuery({
    queryKey: ['admin-payouts', params],
    queryFn: () => adminPayoutService.getPayoutQueue(params),
  });
}
