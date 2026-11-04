import { useQuery } from '@tanstack/react-query';
import { adminViolationService } from '../services/adminViolationService';

export function useViolationDetail(reportId: string) {
  return useQuery({
    queryKey: ['admin-violation-detail', reportId],
    queryFn: () => adminViolationService.getViolationDetail(reportId),
    enabled: !!reportId,
    staleTime: 30000,
  });
}
