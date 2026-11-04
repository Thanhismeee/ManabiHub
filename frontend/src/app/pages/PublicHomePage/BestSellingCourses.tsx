import React from 'react';
import { Box, Button, Container, Grid, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AutoStoriesOutlinedIcon from '@mui/icons-material/AutoStoriesOutlined';
import { useNavigate } from 'react-router-dom';
import { CourseCatalogCard } from '../../../features/catalog/components/CourseCatalogCard';
import { useCourseCatalog } from '../../../features/catalog/hooks/useCourseCatalog';
import { ROUTES } from '../../../shared/constants/routes';

export const BestSellingCourses: React.FC = () => {
  const navigate = useNavigate();
  const { data, isLoading, isError } = useCourseCatalog({
    page: 0,
    size: 4,
    sort: 'publishedAt,desc',
  });

  const courses = data?.content ?? [];

  return (
    <Box component="section" sx={{ py: { xs: 7, md: 9 }, bgcolor: '#FFFFFF' }}>
      <Container maxWidth="xl">
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          sx={{
            mb: 4,
            alignItems: { xs: 'flex-start', md: 'flex-end' },
            justifyContent: 'space-between',
          }}
        >
          <Box>
            <Stack
              direction="row"
              spacing={1}
              sx={{ alignItems: 'center', color: '#C41E3A' }}
            >
              <AutoStoriesOutlinedIcon fontSize="small" />
              <Typography variant="overline" sx={{ fontWeight: 700, color: '#C41E3A' }}>
                NỘI DUNG MỚI
              </Typography>
            </Stack>
            <Typography component="h2" variant="h3" sx={{ mt: 0.5, fontWeight: 800, color: '#1A1A2E' }}>
              Khóa học mới xuất bản
            </Typography>
          </Box>
          <Button
            variant="contained"
            endIcon={<ArrowForwardIcon />}
            onClick={() => navigate(ROUTES.PUBLIC.COURSE_BROWSE)}
            sx={{
              background: 'linear-gradient(135deg, #C41E3A, #E8432A)',
              fontWeight: 700,
              borderRadius: '10px',
              px: 3,
              py: 1.2,
              boxShadow: '0 4px 16px rgba(196, 30, 58, 0.25)',
              transition: 'all 0.3s ease',
              '&:hover': {
                background: 'linear-gradient(135deg, #A8182F, #D13A24)',
                transform: 'translateY(-2px)',
                boxShadow: '0 8px 24px rgba(196,30,58,0.4)',
              }
            }}
          >
            Xem tất cả khóa học
          </Button>
        </Stack>

        {isLoading && (
          <Typography color="text.secondary" sx={{ py: 5 }}>
            Đang tải khóa học...
          </Typography>
        )}

        {(isError || (!isLoading && courses.length === 0)) && (
          <Box sx={{ py: 8, textAlign: 'center' }}>
            <Typography sx={{ fontSize: '4rem', mb: 2, opacity: 0.3 }}>📚</Typography>
            <Typography sx={{ color: '#64748b', fontWeight: 600, fontSize: '1.1rem', mb: 1 }}>
              Các khóa học đang được chuẩn bị
            </Typography>
            <Typography sx={{ color: '#94a3b8', fontSize: '0.9rem' }}>
              Giảng viên đang hoàn thiện nội dung. Hãy quay lại sớm nhé!
            </Typography>
          </Box>
        )}

        {courses.length > 0 && (
          <Grid container spacing={2.5}>
            {courses.map((course) => (
              <Grid key={course.id} size={{ xs: 12, sm: 6, lg: 3 }}>
                <CourseCatalogCard course={course} />
              </Grid>
            ))}
          </Grid>
        )}
      </Container>
    </Box>
  );
};
