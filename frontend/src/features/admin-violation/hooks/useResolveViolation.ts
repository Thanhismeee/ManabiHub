import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminViolationService } from '../services/adminViolationService';
import type { ResolveViolationRequest } from '../types/violation.types';

export function useResolveViolation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: ResolveViolationRequest }) =>
      adminViolationService.resolveViolation(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin-violation-detail', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['admin-violations'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard', 'pending-violation-count'] });
    },
  });
}
