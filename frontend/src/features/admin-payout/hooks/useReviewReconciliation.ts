import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-hot-toast';
import { adminPayoutService } from '../services/adminPayoutService';
import { getPayoutErrorMessage } from '../services/payoutError';

export function useReviewReconciliation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (withdrawalRequestId: string) =>
      adminPayoutService.reviewReconciliation(withdrawalRequestId),
    onSuccess: (_data, withdrawalRequestId) => {
      toast.success('Đã lưu kết quả đối soát vào lịch sử.');
      void queryClient.invalidateQueries({
        queryKey: ['admin-payout', withdrawalRequestId],
      });
    },
    onError: (error: unknown) => toast.error(getPayoutErrorMessage(error)),
  });
}
