import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-hot-toast';
import { adminPayoutService } from '../services/adminPayoutService';
import { getPayoutErrorMessage } from '../services/payoutError';
import type { RejectPayoutPayload } from '../types/payout.types';

interface RejectPayoutVariables {
  withdrawalRequestId: string;
  payload: RejectPayoutPayload;
}

export function useRejectPayout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ withdrawalRequestId, payload }: RejectPayoutVariables) =>
      adminPayoutService.rejectPayout(withdrawalRequestId, payload),
    onSuccess: () => {
      toast.success('Đã từ chối yêu cầu và hoàn lại số dư đang giữ.');
    },
    onError: (error: unknown) => {
      toast.error(getPayoutErrorMessage(error));
    },
    onSettled: (_data, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-payouts'] });
      void queryClient.invalidateQueries({
        queryKey: ['admin-payout', variables.withdrawalRequestId],
      });
    },
  });
}
