import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  Container,
  Grid,
  Paper,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import VerifiedIcon from '@mui/icons-material/Verified';
import StarRoundedIcon from '@mui/icons-material/StarRounded';
import { Helmet } from 'react-helmet-async';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { ROUTES } from '../../../shared/constants/routes';
import { publicTeacherService } from '../services/publicTeacherService';
import type {
  PublicTeacherCourse,
  PublicTeacherProfile,
} from '../types/publicTeacherTypes';

type LoadState =
  | { status: 'loading' }
  | { status: 'success'; teacher: PublicTeacherProfile }
  | { status: 'error' };

function formatPrice(price: number, currency: string): string {
  if (price === 0) return 'Miễn phí';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency || 'VND',
    maximumFractionDigits: 0,
  }).format(price);
}

function TeacherCourseCard({ course }: { course: PublicTeacherCourse }) {
  const thumbnailUrl = useMemo(
    () => resolvePublicAssetUrl(course.thumbnailUrl),
    [course.thumbnailUrl],
  );

  return (
    <Paper
      component={Link}
      to={`/courses/${course.slug || course.id}`}
      elevation={0}
      sx={{
        display: 'block',
        height: '100%',
        overflow: 'hidden',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 3,
        color: 'inherit',
        textDecoration: 'none',
        transition: 'transform 180ms ease, box-shadow 180ms ease',
        '&:hover': {
          transform: 'translateY(-3px)',
          boxShadow: '0 14px 30px rgba(15, 23, 42, 0.10)',
        },
      }}
    >
      <Box
        sx={{
          aspectRatio: '16 / 9',
          bgcolor: 'grey.100',
          backgroundImage: thumbnailUrl ? `url(${thumbnailUrl})` : 'none',
          backgroundPosition: 'center',
          backgroundSize: 'cover',
        }}
      />
      <Stack spacing={1.25} sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
          {course.jlptLevel && <Chip size="small" label={course.jlptLevel} />}
          {course.category && <Chip size="small" variant="outlined" label={course.category} />}
        </Stack>
        <Typography variant="h6" component="h3" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
          {course.title}
        </Typography>
        {(course.reviewCount ?? 0) > 0 && course.averageRating != null && (
          <Stack direction="row" spacing={0.4} sx={{ alignItems: 'center' }}>
            <StarRoundedIcon sx={{ color: '#F59E0B', fontSize: 19 }} />
            <Typography variant="body2" sx={{ fontWeight: 800 }}>
              {course.averageRating.toFixed(1)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ({course.reviewCount} đánh giá)
            </Typography>
          </Stack>
        )}
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center' }}
        >
          <Typography variant="body2" color="text.secondary">
            {course.totalLessons} bài học
          </Typography>
          <Typography sx={{ fontWeight: 800, color: '#C41E3A' }}>
            {formatPrice(course.price, course.currency)}
          </Typography>
        </Stack>
      </Stack>
    </Paper>
  );
}

export const PublicTeacherProfilePage = () => {
  const { teacherId = '' } = useParams<{ teacherId: string }>();
  const [state, setState] = useState<LoadState>({ status: 'loading' });

  useEffect(() => {
    let active = true;
    setState({ status: 'loading' });

    publicTeacherService.getProfile(teacherId)
      .then((teacher) => {
        if (active) setState({ status: 'success', teacher });
      })
      .catch(() => {
        if (active) setState({ status: 'error' });
      });

    return () => {
      active = false;
    };
  }, [teacherId]);

  if (state.status === 'loading') {
    return (
      <Container component="main" maxWidth="lg" sx={{ py: { xs: 5, md: 8 } }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={3}
          sx={{ alignItems: { sm: 'center' } }}
        >
          <Skeleton variant="circular" width={112} height={112} />
          <Box sx={{ flex: 1 }}>
            <Skeleton width="45%" height={46} />
            <Skeleton width="70%" />
            <Skeleton width="55%" />
          </Box>
        </Stack>
        <Grid container spacing={3} sx={{ mt: 4 }}>
          {[0, 1, 2].map((item) => (
            <Grid key={item} size={{ xs: 12, sm: 6, md: 4 }}>
              <Skeleton variant="rounded" height={280} />
            </Grid>
          ))}
        </Grid>
      </Container>
    );
  }

  if (state.status === 'error') {
    return (
      <Container component="main" maxWidth="sm" sx={{ py: { xs: 8, md: 12 } }}>
        <Helmet>
          <title>Không tìm thấy giảng viên | ManabiHub</title>
        </Helmet>
        <Alert severity="info" sx={{ mb: 3 }}>
          Hồ sơ giảng viên không tồn tại hoặc hiện không được phép công khai.
        </Alert>
        <Button
          component={Link}
          to={ROUTES.PUBLIC.COURSE_BROWSE}
          startIcon={<ArrowBackIcon />}
        >
          Quay lại danh sách khóa học
        </Button>
      </Container>
    );
  }

  const { teacher } = state;
  const avatarUrl = resolvePublicAssetUrl(teacher.avatarUrl);

  return (
    <Box component="main" sx={{ minHeight: '100vh', bgcolor: '#FAF9F6', py: { xs: 5, md: 8 } }}>
      <Helmet>
        <title>{teacher.displayName} | Giảng viên ManabiHub</title>
        <meta
          name="description"
          content={teacher.bio || `Xem các khóa học đã xuất bản của ${teacher.displayName}.`}
        />
      </Helmet>

      <Container maxWidth="lg">
        <Paper
          elevation={0}
          sx={{
            p: { xs: 3, md: 5 },
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 4,
          }}
        >
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={3}
            sx={{ alignItems: { xs: 'center', sm: 'flex-start' } }}
          >
            <Avatar
              src={avatarUrl}
              alt={teacher.displayName}
              sx={{ width: { xs: 96, md: 120 }, height: { xs: 96, md: 120 }, fontSize: 40 }}
            >
              {teacher.displayName.charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1, textAlign: { xs: 'center', sm: 'left' } }}>
              <Stack
                direction="row"
                spacing={1.5}
                useFlexGap
                sx={{
                  justifyContent: { xs: 'center', sm: 'flex-start' },
                  alignItems: 'center',
                  flexWrap: 'wrap',
                }}
              >
                <Typography component="h1" variant="h3" sx={{ fontWeight: 900 }}>
                  {teacher.displayName}
                </Typography>
                {teacher.verified && (
                  <Chip
                    icon={<VerifiedIcon />}
                    label="Đã xác minh"
                    color="success"
                    size="small"
                  />
                )}
              </Stack>
              <Stack
                direction="row"
                spacing={1}
                sx={{
                  mt: 1.5,
                  color: 'text.secondary',
                  justifyContent: { xs: 'center', sm: 'flex-start' },
                  alignItems: 'center',
                }}
              >
                <MenuBookIcon fontSize="small" />
                <Typography variant="body2">
                  {teacher.publishedCourseCount} khóa học đã xuất bản
                </Typography>
              </Stack>
              <Typography sx={{ mt: 2.5, color: 'text.secondary', whiteSpace: 'pre-line', lineHeight: 1.75 }}>
                {teacher.bio || 'Giảng viên chưa cập nhật phần giới thiệu công khai.'}
              </Typography>
            </Box>
          </Stack>
        </Paper>

        <Typography component="h2" variant="h4" sx={{ mt: 6, mb: 3, fontWeight: 900 }}>
          Khóa học của giảng viên
        </Typography>

        {teacher.courses.length === 0 ? (
          <Alert severity="info">
            Giảng viên chưa có khóa học công khai.
          </Alert>
        ) : (
          <Grid container spacing={3}>
            {teacher.courses.map((course) => (
              <Grid key={course.id} size={{ xs: 12, sm: 6, md: 4 }}>
                <TeacherCourseCard course={course} />
              </Grid>
            ))}
          </Grid>
        )}
      </Container>
    </Box>
  );
};
