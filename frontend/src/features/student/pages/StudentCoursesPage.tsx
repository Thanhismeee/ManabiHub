import React, { useState } from 'react';
import { Box, Typography, Grid, CircularProgress, Alert, Pagination, Paper, Button } from '@mui/material';
import SchoolIcon from '@mui/icons-material/School';
import { useNavigate } from 'react-router-dom';
import { useStudentCourses } from '../hooks/useStudentCourses';
import { StudentCourseCard } from '../components/StudentCourseCard';
import type { StudentCourseSummary } from '../types/studentTypes';

export const StudentCoursesPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const pageSize = 12;

  const { data, isLoading, isError, error } = useStudentCourses(page, pageSize);

  const handlePageChange = (_event: React.ChangeEvent<unknown>, value: number) => {
    setPage(value - 1);
  };

  if (isLoading && !data) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">
          Error loading enrolled courses: {(error as Error).message}
        </Alert>
      </Box>
    );
  }

  const courses = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <Box sx={{ p: 3, maxWidth: '1280px', width: '100%', mx: 'auto' }}>
      <Typography variant="h4" sx={{ mb: 4, fontWeight: 'bold' }}>
        My Learning
      </Typography>

      {courses.length === 0 ? (
        <Paper sx={{ p: 6, textAlign: 'center', borderRadius: 3, bgcolor: 'background.default' }} elevation={0}>
          <SchoolIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2, opacity: 0.5 }} />
          <Typography variant="h5" color="text.secondary" gutterBottom>
            You haven't enrolled in any courses yet
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 4 }}>
            Start your learning journey by exploring our wide range of courses.
          </Typography>
          <Button
            variant="contained"
            size="large"
            color="primary"
            onClick={() => navigate('/courses')}
            sx={{ px: 4, py: 1.5, borderRadius: 2, fontWeight: 'bold' }}
          >
            Explore Courses
          </Button>
        </Paper>
      ) : (
        <>
          <Grid container spacing={3}>
            {courses.map((course: StudentCourseSummary) => (
              <Grid size={{ xs: 12, sm: 6, md: 4 }} key={course.enrollmentId}>
                <StudentCourseCard course={course} showCalendarPin />
              </Grid>
            ))}
          </Grid>

          {totalPages > 1 && (
            <Box sx={{ mt: 6, display: 'flex', justifyContent: 'center' }}>
              <Pagination
                count={totalPages}
                page={page + 1}
                onChange={handlePageChange}
                color="primary"
                size="large"
              />
            </Box>
          )}
        </>
      )}
    </Box>
  );
};
