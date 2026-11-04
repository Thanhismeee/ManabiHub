
import {
  Box,
  Grid,
  Typography,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { CourseCatalogCard } from '../../catalog/components/CourseCatalogCard';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState/LoadingState';
import { ROUTES } from '../../../shared/constants/routes';
import { useWishlist } from '../hooks/useWishlist';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';

export function StudentWishlistPage() {
  const navigate = useNavigate();
  const { data = [], isLoading, isError, refetch } = useWishlist();

  return (
    <Box component="main" sx={{ minHeight: '100vh', bgcolor: '#FAF9F6', py: { xs: 3, md: 5 }, px: { xs: 2, sm: 3 } }}>
      <Box sx={{ maxWidth: '1280px', mx: 'auto', width: '100%', position: 'relative' }}>
        {/* Background Watermark */}
        <Typography variant="h1" sx={{ position: 'absolute', top: -40, right: -20, fontSize: '15rem', fontWeight: 900, color: 'rgba(0,0,0,0.025)', userSelect: 'none', pointerEvents: 'none', zIndex: 0, writingMode: 'vertical-rl' }}>
          好
        </Typography>
        <PageHeader
          title={
            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.5 }}>
              <span>Danh sách yêu thích</span>
              {!isLoading && !isError && (
                <Box component="span" sx={{ px: 1.5, py: 0.25, bgcolor: '#fee2e2', color: '#C41E3A', borderRadius: '9999px', fontSize: '0.8rem', fontWeight: 700, lineHeight: 1.5, minWidth: 24, textAlign: 'center' }}>
                  {data.length}
                </Box>
              )}
            </Box>
          }
          subtitle="お気に入り"
          watermark="好"
          breadcrumbs={[
            { label: 'Học viên' },
            { label: 'Yêu thích' },
          ]}
        />

        <Box sx={{ position: 'relative', zIndex: 1 }}>
          {isLoading && <LoadingState message="Đang tải danh sách yêu thích..." />}

          {isError && (
            <ErrorState
              title="Không thể tải danh sách"
              message="Vui lòng kiểm tra kết nối và thử lại."
              onRetry={() => refetch()}
            />
          )}

          {!isLoading && !isError && data.length === 0 && (
            <Box
              sx={{
                py: 8,
                bgcolor: 'background.paper',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 4,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <EmptyState
                title="Danh sách yêu thích của bạn đang trống!"
                description="Hãy khám phá thêm nhiều khóa học thú vị nhé."
                icon={<Box sx={{ fontSize: '5rem', mb: 2, filter: 'drop-shadow(0 4px 6px rgba(0,0,0,0.05))', transform: 'rotate(-5deg)' }}>🐕</Box>}
                actionLabel="Khám phá khóa học ngay ➔"
                onAction={() => navigate(ROUTES.STUDENT.BROWSE_COURSES || '/student/browse')}
              />
            </Box>
          )}

          {!isLoading && !isError && data.length > 0 && (
            <Grid container spacing={2.5}>
              {data.map((item) => (
                <Grid key={item.id} size={{ xs: 12, sm: 6, lg: 4, xl: 3 }}>
                  <CourseCatalogCard
                    course={{
                      id: item.courseId,
                      title: item.title,
                      slug: item.slug,
                      thumbnailUrl: item.thumbnailUrl,
                      jlptLevel: item.jlptLevel,
                      category: item.category,
                      price: item.price,
                      currency: item.currency,
                      teacherName: item.teacherName,
                      totalLessons: item.totalLessons,
                    }}
                  />
                </Grid>
              ))}
            </Grid>
          )}
        </Box>
      </Box>
    </Box>
  );
}
