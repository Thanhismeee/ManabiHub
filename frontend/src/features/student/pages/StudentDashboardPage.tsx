import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import TaskAltOutlinedIcon from '@mui/icons-material/TaskAltOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { getMyStudentProfile } from '../../profile/profileApi';
import { StudentCourseCard } from '../components/StudentCourseCard';
import { useStudentCourses } from '../hooks/useStudentCourses';
import { useStudentStats } from '../hooks/useStudentStats';
import { StudyGoalsWidget } from '../components/StudyGoalsWidget';
import { StudyCalendar } from '../components/StudyCalendar';
import { LearningChallengeWidget } from '../components/LearningChallengeWidget';
import { OnboardingGuide, type OnboardingStep } from '../../../shared/components/OnboardingGuide/OnboardingGuide';
import { levelOptions, proficiencyLabel } from '../../../shared/learning/languageProfiles';
import { useLocale } from '../../../shared/i18n/locale';

const BRAND_COLORS = {
  red: '#C41E3A',
  gold: '#B7791F',
  green: '#2F855A',
  navy: '#1B2A4A',
};

const STUDENT_DASHBOARD_GUIDE: OnboardingStep[] = [
  {
    id: 'roadmap',
    title: 'Bắt đầu từ mục tiêu của bạn',
    description: 'Xem cấp độ khóa đang học và mục tiêu ngôn ngữ đã chọn trong hồ sơ.',
    targetId: 'student-roadmap',
  },
  {
    id: 'progress',
    title: 'Theo dõi tiến độ mỗi ngày',
    description: 'Ba thẻ này tóm tắt khóa đã ghi danh, đang học và đã hoàn thành.',
    targetId: 'student-stats',
  },
  {
    id: 'calendar',
    title: 'Xem lịch học tổng quát',
    description: 'Gom lịch tất cả khóa học vào một nơi. Bấm vào buổi học để Vào học ngay.',
    targetId: 'student-calendar',
  },
  {
    id: 'focus',
    title: 'Đặt lịch và tích điểm tập trung',
    description: 'Đặt lịch cố định, bật Pomodoro trong bài học và tích điểm theo kỹ năng.',
    targetId: 'student-goals',
  },
  {
    id: 'payments',
    title: 'Khi cần mua khóa học hoặc xem hoàn tiền',
    description: 'Ví & Thanh toán lưu đơn hàng, thanh toán và các khoản hoàn hợp lệ.',
  },
];

export function StudentDashboardPage() {
  const { t, locale } = useLocale();
  const navigate = useNavigate();
  const statsQuery = useStudentStats();
  const coursesQuery = useStudentCourses(0, 50);
  const profileQuery = useQuery({
    queryKey: ['student-profile'],
    queryFn: getMyStudentProfile,
  });

  const isLoading = statsQuery.isLoading || coursesQuery.isLoading || profileQuery.isLoading;
  const hasError = statsQuery.isError || coursesQuery.isError || profileQuery.isError;

  if (isLoading) {
    return (
      <Box sx={{ minHeight: 420, display: 'grid', placeItems: 'center', bgcolor: '#FAF9F6' }}>
        <Stack spacing={2} sx={{ alignItems: 'center' }}>
          <CircularProgress aria-label="Đang tải tổng quan học tập" sx={{ color: BRAND_COLORS.red }} />
          <Typography color="text.secondary">Đang chuẩn bị không gian học tập của bạn...</Typography>
        </Stack>
      </Box>
    );
  }

  if (hasError) {
    return (
      <Box sx={{ minHeight: '100%', bgcolor: '#FAF9F6', p: { xs: 2, md: 4 } }}>
        <Alert
          severity="error"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => {
                statsQuery.refetch();
                coursesQuery.refetch();
                profileQuery.refetch();
              }}
            >
              Thử lại
            </Button>
          }
        >
          Không thể tải tổng quan học tập. Vui lòng thử lại.
        </Alert>
      </Box>
    );
  }

  const stats = statsQuery.data;
  const courses = coursesQuery.data?.content ?? [];
  const profile = profileQuery.data;
  const primaryGoal = profile?.learningGoals?.find(goal => goal.primaryGoal) ?? profile?.learningGoals?.[0];
  const legacyGoal = profile?.jlptGoal?.match(/\bN[1-5]\b/i)?.[0].toUpperCase();
  const framework = primaryGoal?.proficiencyFramework ?? (legacyGoal ? 'JLPT' : undefined);
  const goalLevel = primaryGoal?.proficiencyLevel ?? legacyGoal ?? '';
  const currentCourse = courses.find(course => course.enrollmentStatus === 'ACTIVE' && course.proficiencyFramework === framework
    && course.learningLanguage === (primaryGoal?.learningLanguage ?? 'JAPANESE')
    && (!primaryGoal?.frameworkVersion || course.frameworkVersion === primaryGoal.frameworkVersion));
  const currentLevel = currentCourse?.proficiencyLevel ?? '';
  const orderedLevels = framework ? levelOptions(framework).map(level => level.value) : [];
  const currentIndex = orderedLevels.indexOf(currentLevel), targetIndex = orderedLevels.indexOf(goalLevel);
  const roadmapLevels = currentIndex >= 0 && targetIndex >= 0
    ? currentIndex <= targetIndex ? orderedLevels.slice(currentIndex, targetIndex + 1) : orderedLevels.slice(targetIndex, currentIndex + 1).reverse()
    : [...new Set([currentLevel, goalLevel].filter(Boolean))];
  const statCards = [
    {
      label: 'Khóa học đã ghi danh',
      value: stats?.totalEnrolledCourses ?? 0,
      helper: 'Tổng số khóa học của bạn',
      icon: MenuBookOutlinedIcon,
      color: BRAND_COLORS.navy,
      tint: '#EEF2F7',
    },
    {
      label: 'Đang học',
      value: stats?.activeCourses ?? 0,
      helper: 'Có thể tiếp tục ngay',
      icon: PlayCircleOutlineIcon,
      color: BRAND_COLORS.gold,
      tint: '#FFF8E7',
    },
    {
      label: 'Đã hoàn thành',
      value: stats?.completedCourses ?? 0,
      helper: 'Khóa học đã hoàn tất',
      icon: TaskAltOutlinedIcon,
      color: BRAND_COLORS.green,
      tint: '#ECF8F1',
    },
  ];

  return (
    <Box
      component="main"
      sx={{
        minHeight: '100%',
        bgcolor: '#FAF9F6',
        px: { xs: 2, sm: 3, lg: 4 },
        py: { xs: 3, md: 5 },
        overflow: 'hidden',
      }}
    >
      <Box sx={{ maxWidth: 1280, mx: 'auto', position: 'relative' }}>
        <Typography
          aria-hidden="true"
          sx={{
            position: 'absolute',
            top: -62,
            right: -24,
            color: 'rgba(27, 42, 74, 0.035)',
            fontSize: { xs: '8rem', md: '13rem' },
            fontWeight: 900,
            lineHeight: 1,
            pointerEvents: 'none',
            userSelect: 'none',
          }}
        >
          目標
        </Typography>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{
            position: 'relative',
            justifyContent: 'space-between',
            alignItems: { sm: 'flex-end' },
            gap: 2,
            mb: 4,
          }}
        >
          <Box>
            <Typography
              variant="overline"
              sx={{ color: BRAND_COLORS.red, fontWeight: 800, letterSpacing: '0.08em' }}
            >
              {locale === 'ko' ? '다시 오신 것을 환영합니다' : locale === 'zh-CN' ? '欢迎回来' : 'ManabiHub'}
            </Typography>
            <Typography
              variant="h4"
              sx={{ mt: 0.5, mb: 1, color: '#172033', fontWeight: 900 }}
            >
              Chào {profile?.displayName || 'bạn'}
            </Typography>
            <Typography sx={{ color: '#5B6472', fontSize: { xs: '0.95rem', md: '1.05rem' } }}>
              {t('Mục tiêu')}: {goalLevel ? proficiencyLabel(framework, goalLevel) : t('Chưa chọn cấp độ')}
            </Typography>
          </Box>
          <Button
            variant="outlined"
            endIcon={<ArrowForwardIcon />}
            onClick={() => navigate(ROUTES.STUDENT.MY_COURSES)}
            sx={{
              alignSelf: { xs: 'flex-start', sm: 'auto' },
              borderColor: '#CBD2DC',
              color: BRAND_COLORS.navy,
              fontWeight: 800,
              bgcolor: 'rgba(255,255,255,0.7)',
              '&:hover': { borderColor: BRAND_COLORS.red, color: BRAND_COLORS.red },
            }}
          >
            Xem tất cả khóa học
          </Button>
        </Stack>

        <Paper
          data-testid="mini-roadmap"
          data-onboarding-target="student-roadmap"
          elevation={0}
          sx={{ p: { xs: 2, sm: 2.5 }, mb: 4, border: '1px solid #E4E7EC', borderRadius: '8px', bgcolor: '#FFFFFF' }}
        >
          <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 1, mb: 1.5 }}>
            <Typography variant="subtitle2" sx={{ color: '#172033', fontWeight: 900 }}>{t('Lộ trình ngôn ngữ của bạn')}</Typography>
            <Typography variant="caption" sx={{ color: '#667085' }}>{t('Cấp độ khóa đang học')}: {currentLevel || '—'} · {t('Mục tiêu')}: {goalLevel || '—'}</Typography>
          </Stack>
          <Stack direction="row" sx={{ alignItems: 'center', width: '100%' }}>
            {roadmapLevels.map((level, index) => {
              const isCurrent = Boolean(currentLevel) && index === 0;
              const isTarget = index === roadmapLevels.length - 1;
              return (
                <Box key={`${level}-${index}`} sx={{ display: 'flex', alignItems: 'center', flex: index === roadmapLevels.length - 1 ? '0 0 auto' : 1 }}>
                  <Stack spacing={0.35} sx={{ alignItems: 'center', minWidth: { xs: 42, sm: 64 } }}>
                    <Box sx={{ width: 30, height: 30, borderRadius: '50%', display: 'grid', placeItems: 'center', bgcolor: isCurrent ? '#DCFCE7' : isTarget ? '#FFF1C2' : '#F1F5F9', color: isCurrent ? '#15803D' : isTarget ? '#A16207' : '#94A3B8', fontWeight: 900, fontSize: 13 }}>
                      {isTarget ? '🏆' : isCurrent ? '●' : '○'}
                    </Box>
                    <Typography variant="caption" sx={{ fontWeight: isCurrent || isTarget ? 900 : 700, color: isCurrent ? '#15803D' : isTarget ? '#A16207' : '#667085' }}>{level}{isCurrent ? ` (${t('Đang học')})` : isTarget ? ` (${t('Mục tiêu')})` : ''}</Typography>
                  </Stack>
                  {index < roadmapLevels.length - 1 && <Box sx={{ height: 2, flex: 1, mx: { xs: 0.5, sm: 1 }, bgcolor: '#D7DEE8' }} />}
                </Box>
              );
            })}
          </Stack>
        </Paper>

        <Grid container spacing={2.5} sx={{ position: 'relative', mb: 5 }} data-testid="student-stats" data-onboarding-target="student-stats">
          {statCards.map(({ label, value, helper, icon: Icon, color, tint }) => (
            <Grid size={{ xs: 12, sm: 4 }} key={label}>
              <Paper
                elevation={0}
                sx={{
                  minHeight: 132,
                  p: 2.5,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 2,
                  border: '1px solid #E4E7EC',
                  borderRadius: '8px',
                  bgcolor: '#FFFFFF',
                  boxShadow: '0 8px 24px rgba(15, 23, 42, 0.045)',
                  transition: 'transform 180ms ease, box-shadow 180ms ease',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: '0 12px 28px rgba(15, 23, 42, 0.08)',
                  },
                }}
              >
                <Box
                  sx={{
                    width: 52,
                    height: 52,
                    flexShrink: 0,
                    display: 'grid',
                    placeItems: 'center',
                    borderRadius: '8px',
                    color,
                    bgcolor: tint,
                  }}
                >
                  <Icon sx={{ fontSize: 30 }} />
                </Box>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="h4" sx={{ color: '#172033', fontWeight: 900, lineHeight: 1 }}>
                    {value}
                  </Typography>
                  <Typography sx={{ mt: 0.75, color: '#303846', fontWeight: 800 }}>
                    {label}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#7A8391' }}>
                    {helper}
                  </Typography>
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>

        <Grid container spacing={3.5} sx={{ alignItems: 'flex-start' }}>
          <Grid size={{ xs: 12, lg: 8.5 }}>
            <Stack spacing={4}>
              <Box data-onboarding-target="student-calendar">
                <StudyCalendar
                  courses={courses.map((course) => ({
                    id: course.courseId,
                    title: course.courseTitle,
                    enrollmentStatus: course.enrollmentStatus,
                    enrolledAt: course.enrolledAt,
                    expiresAt: course.expiresAt,
                  }))}
                />
              </Box>

              <Box>
                <Stack
                  direction="row"
                  sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
                >
                  <Box>
                    <Typography variant="h6" sx={{ color: '#172033', fontWeight: 900 }}>
                      Khóa học gần đây
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#7A8391', letterSpacing: '0.06em' }}>
                      最近のコース
                    </Typography>
                  </Box>
                  {courses.length > 0 && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => navigate(ROUTES.STUDENT.MY_COURSES)}
                      endIcon={<ArrowForwardIcon />}
                      sx={{ borderColor: '#CBD5E1', color: BRAND_COLORS.navy, fontWeight: 800 }}
                    >
                      Xem tất cả
                    </Button>
                  )}
                </Stack>

                {courses.length === 0 ? (
                  <Box
                    sx={{
                      minHeight: 330,
                      display: 'grid',
                      placeItems: 'center',
                      textAlign: 'center',
                      p: 4,
                      border: '1px dashed #CCD2DB',
                      borderRadius: '8px',
                      bgcolor: '#FFFFFF',
                    }}
                  >
                    <Box>
                      <MenuBookOutlinedIcon sx={{ fontSize: 54, color: '#A6AFBC', mb: 1.5 }} />
                      <Typography variant="h6" sx={{ color: '#172033', fontWeight: 900, mb: 1 }}>
                        {t('Bắt đầu hành trình học ngôn ngữ của bạn')}
                      </Typography>
                      <Typography sx={{ color: '#667085', maxWidth: 430, mx: 'auto', mb: 3 }}>
                        Khám phá các khóa học đã xuất bản và chọn lộ trình phù hợp với mục tiêu của bạn.
                      </Typography>
                      <Button
                        variant="contained"
                        onClick={() => navigate(ROUTES.STUDENT.BROWSE_COURSES)}
                        sx={{
                          bgcolor: BRAND_COLORS.red,
                          fontWeight: 800,
                          '&:hover': { bgcolor: '#A71931' },
                        }}
                      >
                        Khám phá khóa học
                      </Button>
                    </Box>
                  </Box>
                ) : (
                  <Box
                    data-testid="recent-courses-list"
                    sx={{
                      display: 'flex',
                      gap: 2.5,
                      overflowX: 'auto',
                      pb: 1,
                      px: 0.25,
                      scrollSnapType: 'x mandatory',
                      '& > *': {
                        flex: { xs: '0 0 min(88vw, 360px)', md: '0 0 min(320px, 45%)' },
                        scrollSnapAlign: 'start',
                      },
                    }}
                  >
                    {courses.slice(0, 3).map((course) => (
                      <Box key={course.enrollmentId} sx={{ minWidth: 0 }}>
                        <StudentCourseCard course={course} />
                      </Box>
                    ))}
                  </Box>
                )}
              </Box>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, lg: 3.5 }}>
            <Stack spacing={2.5} sx={{ position: { lg: 'sticky' }, top: { lg: 24 } }}>
              <StudyGoalsWidget
                learningGoalLabel={goalLevel ? proficiencyLabel(framework, goalLevel) : undefined}
                courses={courses.map((course) => ({
                  id: course.courseId,
                  title: course.courseTitle,
                  enrollmentStatus: course.enrollmentStatus,
                  enrolledAt: course.enrolledAt,
                  expiresAt: course.expiresAt,
                }))}
              />
              <LearningChallengeWidget accountKey={profile?.id ?? profile?.email} />
            </Stack>
          </Grid>
        </Grid>

        <OnboardingGuide
          scope="student-dashboard"
          title="Làm quen với bảng điều khiển học viên"
          intro="Một vòng nhanh để bạn biết mỗi khu vực trên bảng điều khiển dùng làm gì."
          steps={STUDENT_DASHBOARD_GUIDE}
          accountKey={profile?.id ?? profile?.email}
        />
      </Box>
    </Box>
  );
}
