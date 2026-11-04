import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-hot-toast';
import { adminPayoutService } from '../services/adminPayoutService';
import { getPayoutErrorMessage } from '../services/payoutError';
import type { ManualTransferPayload } from '../types/payout.types';

interface ManualTransferVariables {
  withdrawalRequestId: string;
  payload: ManualTransferPayload;
  proof: File;
}
export function useConfirmManualTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ withdrawalRequestId, payload, proof }: ManualTransferVariables) =>
      adminPayoutService.confirmManualTransfer(withdrawalRequestId, payload, proof),
    onSuccess: () => toast.success('Đã xác nhận chuyển khoản thủ công và lưu chứng từ.'),
    onError: (error: unknown) => toast.error(getPayoutErrorMessage(error)),
    onSettled: (_data, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-payouts'] });
      void queryClient.invalidateQueries({
        queryKey: ['admin-payout', variables.withdrawalRequestId],
      });
    },
  });
}
