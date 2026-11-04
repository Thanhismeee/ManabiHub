import React from 'react';
import { Box, Skeleton, Stack } from '@mui/material';

interface PageSkeletonProps {
  /** Layout variant: 'dashboard' shows stat cards + list, 'detail' shows header + content, 'table' shows toolbar + rows */
  variant?: 'dashboard' | 'detail' | 'table';
}

export const PageSkeleton: React.FC<PageSkeletonProps> = ({ variant = 'dashboard' }) => {
  return (
    <Box role="status" aria-label="Đang tải..." sx={{ p: { xs: 2, md: 0 } }}>
      {/* Page title area */}
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Skeleton variant="text" width={220} height={36} />
          <Skeleton variant="text" width={160} height={20} sx={{ mt: 0.5 }} />
        </Box>
        <Skeleton variant="rounded" width={120} height={40} />
      </Stack>

      {variant === 'dashboard' && <DashboardSkeleton />}
      {variant === 'detail' && <DetailSkeleton />}
      {variant === 'table' && <TableSkeleton />}
    </Box>
  );
};

function DashboardSkeleton() {
  return (
    <>
      {/* Stat cards */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 4 }}>
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} variant="rounded" height={88} sx={{ flex: 1 }} />
        ))}
      </Stack>
      {/* Content cards */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} variant="rounded" height={200} sx={{ flex: 1 }} />
        ))}
      </Stack>
    </>
  );
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton variant="rounded" height={200} sx={{ mb: 3 }} />
      <Stack spacing={2}>
        <Skeleton variant="text" width="80%" height={24} />
        <Skeleton variant="text" width="60%" height={24} />
        <Skeleton variant="text" width="70%" height={24} />
        <Skeleton variant="rounded" height={120} sx={{ mt: 2 }} />
      </Stack>
    </Box>
  );
}

function TableSkeleton() {
  return (
    <Box>
      {/* Toolbar */}
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <Skeleton variant="rounded" width={240} height={40} />
        <Skeleton variant="rounded" width={120} height={40} />
      </Stack>
      {/* Table rows */}
      <Stack spacing={1}>
        <Skeleton variant="rounded" height={48} />
        {[1, 2, 3, 4, 5].map((i) => (
          <Skeleton key={i} variant="rounded" height={56} />
        ))}
      </Stack>
    </Box>
  );
}
