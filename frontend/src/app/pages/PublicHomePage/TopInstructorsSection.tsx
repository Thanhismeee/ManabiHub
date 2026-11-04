import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Avatar,
  Box,
  Button,
  Container,
  Grid,
  Paper,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import VerifiedIcon from '@mui/icons-material/Verified';
import { ROUTES } from '../../../shared/constants/routes';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { publicTeacherService } from '../../../features/teacher-discovery/services/publicTeacherService';
import type { PublicTeacherSummary } from '../../../features/teacher-discovery/types/publicTeacherTypes';

type InstructorState =
  | { status: 'loading' }
  | { status: 'success'; teachers: PublicTeacherSummary[] }
  | { status: 'hidden' };

export const TopInstructorsSection = () => {
  const [state, setState] = useState<InstructorState>({ status: 'loading' });
  const [isVisible, setIsVisible] = useState(false);
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;

    publicTeacherService.listFeatured(4)
      .then((teachers) => {
        if (!active) return;
        setState(teachers.length > 0 ? { status: 'success', teachers } : { status: 'hidden' });
      })
      .catch(() => {
        if (active) setState({ status: 'hidden' });
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) setIsVisible(true);
      },
      { threshold: 0.1, rootMargin: '0px' },
    );
    const section = sectionRef.current;
    if (section) observer.observe(section);
    return () => observer.disconnect();
  }, []);

  if (state.status === 'hidden') {
    return null;
  }

  return (
    <Box ref={sectionRef} component="section" sx={{ py: { xs: 8, md: 12 }, bgcolor: '#FBF9F5' }}>
      <Container
        disableGutters
        sx={{ maxWidth: { md: '1157px' }, px: { xs: 3, md: 0 }, margin: '0 auto' }}
      >
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={{ xs: 5, md: 6 }}>
          <Box
            sx={{
              width: { xs: '100%', md: '35%' },
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              opacity: isVisible ? 1 : 0,
              transform: isVisible ? 'translateX(0)' : 'translateX(-40px)',
              transition: 'all 700ms ease',
            }}
          >
            <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
              <Box sx={{ width: 4, height: 20, bgcolor: '#C41E3A', borderRadius: 2 }} />
              <Typography
                variant="overline"
                sx={{ color: '#C41E3A', fontWeight: 800, letterSpacing: '0.08em' }}
              >
                GIẢNG VIÊN ĐÃ XÁC MINH
              </Typography>
            </Stack>
            <Typography
              variant="h2"
              sx={{
                fontWeight: 900,
                color: '#1A1A2E',
                mb: 3,
                lineHeight: 1.15,
                fontSize: { xs: '2.2rem', md: '2.8rem' },
              }}
            >
              Học cùng giảng viên thật
            </Typography>
            <Typography sx={{ color: '#475569', lineHeight: 1.75, mb: 4 }}>
              Chỉ những giảng viên đang hoạt động, đã hoàn tất xác minh và có khóa học công khai
              mới xuất hiện tại đây.
            </Typography>
            <Button
              component={Link}
              to={ROUTES.PUBLIC.COURSE_BROWSE}
              variant="contained"
              endIcon={<ArrowForwardIcon />}
              sx={{
                alignSelf: 'flex-start',
                background: 'linear-gradient(135deg, #C41E3A, #E8432A)',
                py: 1.5,
                px: 4,
                fontWeight: 800,
                borderRadius: 2.5,
              }}
            >
              Khám phá khóa học
            </Button>
          </Box>

          <Box sx={{ width: { xs: '100%', md: '65%' } }}>
            <Grid container spacing={3}>
              {state.status === 'loading'
                ? [0, 1, 2, 3].map((item) => (
                    <Grid key={item} size={{ xs: 12, sm: 6 }}>
                      <Skeleton variant="rounded" height={210} sx={{ borderRadius: 4 }} />
                    </Grid>
                  ))
                : state.teachers.map((teacher, index) => (
                    <Grid key={teacher.id} size={{ xs: 12, sm: 6 }}>
                      <Paper
                        component={Link}
                        to={ROUTES.PUBLIC.TEACHER_PROFILE(teacher.id)}
                        elevation={0}
                        sx={{
                          display: 'flex',
                          flexDirection: 'column',
                          height: '100%',
                          p: 3,
                          color: 'inherit',
                          textDecoration: 'none',
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 4,
                          opacity: isVisible ? 1 : 0,
                          transform: isVisible ? 'translateY(0)' : 'translateY(35px)',
                          transition: `all 600ms ease ${index * 100}ms`,
                          '&:hover': {
                            transform: 'translateY(-5px)',
                            boxShadow: '0 16px 32px rgba(15, 23, 42, 0.08)',
                            borderColor: '#fecdd3',
                          },
                        }}
                      >
                        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                          <Avatar
                            src={resolvePublicAssetUrl(teacher.avatarUrl)}
                            alt={teacher.displayName}
                            sx={{ width: 64, height: 64, fontSize: 24 }}
                          >
                            {teacher.displayName.charAt(0).toUpperCase()}
                          </Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                              <Typography sx={{ fontWeight: 900 }} noWrap>
                                {teacher.displayName}
                              </Typography>
                              {teacher.verified && (
                                <VerifiedIcon color="success" sx={{ fontSize: 18 }} />
                              )}
                            </Stack>
                            <Stack
                              direction="row"
                              spacing={0.75}
                              sx={{ mt: 0.5, color: 'text.secondary', alignItems: 'center' }}
                            >
                              <MenuBookIcon sx={{ fontSize: 17 }} />
                              <Typography variant="caption" sx={{ fontWeight: 700 }}>
                                {teacher.publishedCourseCount} khóa học
                              </Typography>
                            </Stack>
                          </Box>
                        </Stack>
                        <Typography
                          variant="body2"
                          sx={{
                            mt: 2.5,
                            color: 'text.secondary',
                            lineHeight: 1.65,
                            display: '-webkit-box',
                            WebkitLineClamp: 3,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                          }}
                        >
                          {teacher.bio || 'Xem hồ sơ và các khóa học đã xuất bản của giảng viên.'}
                        </Typography>
                      </Paper>
                    </Grid>
                  ))}
            </Grid>
          </Box>
        </Stack>
      </Container>
    </Box>
  );
};
