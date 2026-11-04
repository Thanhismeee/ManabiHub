import type { ReactNode } from 'react';
import { useCommercialPolicy } from '../hooks/useCommercialPolicy';
import { LoadingState } from '../../../shared/components/LoadingState/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { Box } from '@mui/material';
interface PolicyBoundaryProps {
  children: (policy: NonNullable<ReturnType<typeof useCommercialPolicy>['data']>) => ReactNode;
}

export const PolicyBoundary = ({ children }: PolicyBoundaryProps) => {
  const { data: policy, isLoading, isError, refetch } = useCommercialPolicy();

  if (isLoading) {
    return (
      <Box sx={{ minHeight: 112, display: 'flex', alignItems: 'center', justifyContent: 'center', borderTop: 1, borderBottom: 1, borderColor: 'divider', bgcolor: 'grey.50', p: 2 }}>
        <LoadingState message="Đang tải điều khoản hiện hành..." />
      </Box>
    );
  }

  if (isError || !policy) {
    return (
      <Box sx={{ mt: 2, mb: 2 }}>
        <ErrorState 
          message="Không thể tải điều khoản hiện hành"
          onRetry={() => void refetch()}
        />
      </Box>
    );
  }

  return <>{children(policy)}</>;
};
