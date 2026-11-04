import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-hot-toast';
import { adminPayoutService } from '../services/adminPayoutService';
import { getPayoutErrorMessage } from '../services/payoutError';

export function useRetryPayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (withdrawalRequestId: string) =>
      adminPayoutService.retryPayout(withdrawalRequestId),
    onSuccess: () => toast.success('Đã thử lại payout theo mã giao dịch cũ.'),
    onError: (error: unknown) => toast.error(getPayoutErrorMessage(error)),
    onSettled: (_data, _error, withdrawalRequestId) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-payouts'] });
      void queryClient.invalidateQueries({
        queryKey: ['admin-payout', withdrawalRequestId],
      });
    },
  });
}
