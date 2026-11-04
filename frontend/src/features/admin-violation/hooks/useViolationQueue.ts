import { useQuery } from '@tanstack/react-query';
import { adminViolationService } from '../services/adminViolationService';

export function useViolationQueue(params?: { page?: number; size?: number; status?: string }) {
  return useQuery({
    queryKey: ['admin-violations', params],
    queryFn: () => adminViolationService.getViolationQueue(params),
    staleTime: 30000,
  });
}
