import { useQuery } from '@tanstack/react-query';
import { getCommercialPolicy } from '../api/commercialPolicyApi';
import type { CommercialPolicy } from '../types';

export const useCommercialPolicy = () => {
  return useQuery<CommercialPolicy, Error>({
    queryKey: ['commercial-policy'],
    queryFn: getCommercialPolicy,
    staleTime: 1000 * 60 * 15,
    retry: 1,
  });
};
