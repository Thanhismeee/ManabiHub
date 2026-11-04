import { useQuery } from '@tanstack/react-query';
import { adminPayoutService } from '../services/adminPayoutService';

export function usePayoutDetail(withdrawalRequestId: string | undefined) {
  return useQuery({
    queryKey: ['admin-payout', withdrawalRequestId],
    queryFn: () => adminPayoutService.getPayoutDetail(withdrawalRequestId!),
    enabled: Boolean(withdrawalRequestId),
  });
}
