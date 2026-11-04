import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelStudentRefund,
  createStudentRefund,
  fetchStudentRefunds,
  fetchStudentRefundDetail,
} from '../api/studentRefundApi';

export const STUDENT_REFUNDS_QUERY_KEY = ['student-refunds'] as const;

export function useStudentRefunds(page = 0, size = 10) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: [...STUDENT_REFUNDS_QUERY_KEY, page, size],
    queryFn: () => fetchStudentRefunds(page, size),
    refetchInterval: (query) => query.state.data?.content.some(
      (refund) => refund.automaticRefundPending || refund.status === 'PROCESSING',
    ) ? 2000 : false,
  });
  const completed = query.data?.content.filter((refund) => refund.status === 'APPROVED')
    .map((refund) => refund.id).sort().join(',');
  useEffect(() => {
    if (completed) {
      void queryClient.invalidateQueries({ queryKey: ['student-order-history'] });
      void queryClient.invalidateQueries({ queryKey: ['student-wallet-transactions'] });
    }
  }, [completed, queryClient]);
  return query;
}

export function useStudentRefundDetail(id?: string, enabled = true) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: [...STUDENT_REFUNDS_QUERY_KEY, 'detail', id],
    queryFn: () => fetchStudentRefundDetail(id!),
    enabled: enabled && Boolean(id),
    refetchInterval: (current) => current.state.data?.automaticRefundPending
      || current.state.data?.status === 'PROCESSING' ? 2000 : false,
  });
  const status = query.data?.status;
  useEffect(() => {
    if (status && status !== 'PENDING' && status !== 'PROCESSING') {
      void queryClient.invalidateQueries({ queryKey: STUDENT_REFUNDS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ['student-order-history'] });
      void queryClient.invalidateQueries({ queryKey: ['student-wallet-transactions'] });
    }
  }, [status, queryClient]);
  return query;
}

function useInvalidateRefundData() {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: STUDENT_REFUNDS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ['student-order-history'] }),
    ]);
  };
}

export function useCreateStudentRefund() {
  const invalidate = useInvalidateRefundData();
  return useMutation({
    mutationFn: createStudentRefund,
    onSuccess: invalidate,
  });
}

export function useCancelStudentRefund() {
  const invalidate = useInvalidateRefundData();
  return useMutation({
    mutationFn: cancelStudentRefund,
    onSuccess: invalidate,
  });
}
