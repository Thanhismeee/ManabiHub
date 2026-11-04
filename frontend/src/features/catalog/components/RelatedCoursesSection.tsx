import { useMemo } from 'react';
import { Box, Button, Grid, Stack, Typography } from '@mui/material';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import { Link } from 'react-router-dom';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import { useCourseCatalog } from '../hooks/useCourseCatalog';
import { CourseCatalogCard } from './CourseCatalogCard';
import { getRelatedCourses } from '../utils/relatedCourseUtils';
import { ROUTES } from '../../../shared/constants/routes';

interface RelatedCoursesSectionProps {
  course: PublicCourseDetail;
}

export const RelatedCoursesSection = ({ course }: RelatedCoursesSectionProps) => {
  const { data, isLoading, isError } = useCourseCatalog({
    page: 0,
    size: 50,
    sort: 'publishedAt,desc',
  });
  const relatedCourses = useMemo(
    () => (data ? getRelatedCourses(course, data.content) : []),
    [course, data],
  );

  if (isError || (!isLoading && relatedCourses.length === 0)) return null;

  return (
    <Box component="section" aria-labelledby="related-courses-heading" sx={{ mt: 2, mb: 12 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 3, alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between' }}
      >
        <Box>
          <Typography id="related-courses-heading" variant="h4" sx={{ fontWeight: 800, color: 'text.primary' }}>
            Khóa học liên quan
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
            Gợi ý phù hợp theo danh mục, giảng viên, chủ đề và trình độ của khóa học này.
          </Typography>
        </Box>
        <Button
          component={Link}
          to={course.category
            ? `${ROUTES.PUBLIC.COURSE_BROWSE}?category=${encodeURIComponent(course.category)}`
            : ROUTES.PUBLIC.COURSE_BROWSE}
          variant="outlined"
          endIcon={<ArrowForwardRoundedIcon />}
          sx={{ flexShrink: 0, fontWeight: 700, textTransform: 'none' }}
        >
          Xem tất cả khóa học
        </Button>
      </Stack>

      {isLoading ? (
        <Grid container spacing={3} aria-label="Đang tải khóa học liên quan">
          {Array.from({ length: 4 }).map((_, index) => (
            <Grid key={index} size={{ xs: 12, sm: 6, lg: 3 }}>
              <Box sx={{ height: 340, borderRadius: 2, bgcolor: 'grey.100', animation: 'pulse 1.5s infinite ease-in-out' }} />
            </Grid>
          ))}
        </Grid>
      ) : (
        <Grid container spacing={3}>
          {relatedCourses.map((relatedCourse) => (
            <Grid key={relatedCourse.id} size={{ xs: 12, sm: 6, lg: 3 }}>
              <CourseCatalogCard course={relatedCourse} />
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  );
};
