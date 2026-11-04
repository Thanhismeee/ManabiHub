import { lazy, Suspense } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';

const TeacherKycPage = lazy(() =>
  import('./TeacherKycPage').then((module) => ({ default: module.TeacherKycPage })),
);

export function TeacherKycRoute() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <TeacherKycPage />
    </Suspense>
  );
}

function RouteFallback() {
  return (
    <Box sx={{ alignItems: 'center', color: 'text.secondary', display: 'flex', gap: 1.5, justifyContent: 'center', minHeight: 240 }}>
      <CircularProgress size={24} />
      <Typography>Đang tải màn hình...</Typography>
    </Box>
  );
}
