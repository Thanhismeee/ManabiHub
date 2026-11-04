import React, { useEffect, useState } from 'react';
import { Card, CardContent, Typography, Box, Chip, Button, LinearProgress, Switch, FormControlLabel } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import type { StudentCourseSummary } from '../types/studentTypes';
import PersonIcon from '@mui/icons-material/Person';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { ROUTES } from '../../../shared/constants/routes';
import fallbackCourseCover from '../../../assets/course1.png';
import { CALENDAR_PINS_UPDATED_EVENT, readPinnedCourseIds, setCoursePinned } from './studyCalendarPreferences';

interface StudentCourseCardProps {
  course: StudentCourseSummary;
  showCalendarPin?: boolean;
}

const statusLabelFor = (status: StudentCourseSummary['enrollmentStatus']) => {
  switch (status) {
    case 'COMPLETED': return 'Đã hoàn thành';
    case 'REFUND_PENDING': return 'Đang chờ hoàn tiền';
    case 'EXPIRED': return 'Đã hết hạn';
    default: return 'Đang học';
  }
};

export const StudentCourseCard: React.FC<StudentCourseCardProps> = ({ course, showCalendarPin = false }) => {
  const navigate = useNavigate();
  const [imageFailed, setImageFailed] = useState(false);
  const [calendarPinned, setCalendarPinned] = useState(() => readPinnedCourseIds().has(course.courseId));

  useEffect(() => {
    const refresh = () => setCalendarPinned(readPinnedCourseIds().has(course.courseId));
    window.addEventListener(CALENDAR_PINS_UPDATED_EVENT, refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener(CALENDAR_PINS_UPDATED_EVENT, refresh);
      window.removeEventListener('storage', refresh);
    };
  }, [course.courseId]);

  const getStatusColor = (status: StudentCourseSummary['enrollmentStatus']): ChipProps['color'] => {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'COMPLETED':
        return 'primary';
      case 'REFUND_PENDING':
        return 'warning';
      case 'EXPIRED':
        return 'error';
      case 'REFUNDED':
      case 'REVOKED':
        return 'error';
      default:
        return 'default';
    }
  };

  const handleViewCourse = () => {
    navigate(ROUTES.PUBLIC.COURSE_DETAIL.replace(':id', course.courseId));
  };

  const handleStudyCourse = () => {
    if (course.enrollmentStatus === 'EXPIRED' || course.enrollmentStatus === 'REFUND_PENDING') {
      handleViewCourse();
      return;
    }
    navigate(ROUTES.STUDENT.COURSE_LEARN(course.courseId));
  };

  const progress = Math.min(100, Math.max(0, course.progressPercentage || 0));
  const expiryLabel = course.daysRemaining === undefined || course.daysRemaining < 0
    ? null
    : course.daysRemaining === 0
      ? 'Đã hết hạn'
      : course.daysRemaining < 15
        ? `Còn ${course.daysRemaining} ngày hết hạn`
        : `Hạn dùng: ${course.daysRemaining} ngày`;
  const statusLabel = course.enrollmentStatus === 'COMPLETED' ? 'Đã hoàn thành' : 'Đang học';

  return (
    <Card
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        border: '1px solid #E4E7EC',
        borderRadius: '8px',
        boxShadow: '0 8px 24px rgba(15, 23, 42, 0.045)',
        overflow: 'hidden',
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: '0 12px 28px rgba(15, 23, 42, 0.08)',
        },
      }}
    >
      <Box
        component="img"
        data-testid={!course.thumbnailUrl || imageFailed ? 'course-cover-fallback' : 'course-cover'}
        src={course.thumbnailUrl && !imageFailed ? resolvePublicAssetUrl(course.thumbnailUrl) : fallbackCourseCover}
        alt={course.thumbnailUrl && !imageFailed ? course.courseTitle : `Ảnh mặc định cho ${course.courseTitle}`}
        onError={() => setImageFailed(true)}
        sx={{ width: '100%', height: 180, objectFit: 'cover', bgcolor: '#F2F4F7' }}
      />
      <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
          <Typography gutterBottom variant="h6" component="h2" sx={{
            color: '#172033',
            fontWeight: 900,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            lineHeight: 1.3,
            minHeight: '2.6em',
          }}>
            {course.courseTitle}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1, color: 'text.secondary' }}>
          <PersonIcon sx={{ fontSize: 16, mr: 0.5 }} />
          <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
            {course.teacherName || 'Chưa cập nhật giảng viên'}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, color: 'text.secondary' }}>
          <AccessTimeIcon sx={{ fontSize: 16, mr: 0.5 }} />
          <Typography variant="body2">
            Ghi danh: {new Date(course.enrolledAt).toLocaleDateString('vi-VN')}
          </Typography>
        </Box>

        <Box sx={{ mb: 2 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
            <Typography variant="caption" color="text.secondary">Tiến độ</Typography>
            <Typography variant="caption" sx={{ fontWeight: 700 }}>{Math.round(progress)}%</Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={progress}
            aria-label={`Tiến độ ${Math.round(progress)}%`}
            sx={{
              height: 7,
              borderRadius: '4px',
              bgcolor: '#EEF0F3',
              '& .MuiLinearProgress-bar': { bgcolor: '#C41E3A' },
            }}
          />
        </Box>

        {expiryLabel && (
          <Typography
            variant="caption"
            sx={{
              mb: 2,
              color: course.enrollmentStatus === 'EXPIRED' || (course.daysRemaining ?? 99) < 15
                ? 'warning.dark'
                : 'text.secondary',
              fontWeight: 700,
            }}
          >
            {expiryLabel}
          </Typography>
        )}

        {showCalendarPin && (
          <FormControlLabel
            control={(
              <Switch
                size="small"
                checked={calendarPinned}
                onChange={(event) => setCoursePinned(course.courseId, event.target.checked)}
                disabled={course.enrollmentStatus !== 'ACTIVE'}
                slotProps={{ input: { 'aria-label': `Ghim lịch ${course.courseTitle}` } }}
              />
            )}
            label={<Typography variant="caption" sx={{ fontWeight: 800 }}>{calendarPinned ? 'Đã ghim lên lịch' : 'Ghim lên lịch'}</Typography>}
            sx={{ mb: 1, mx: 0 }}
          />
        )}

        <Box sx={{ mt: 'auto', pt: 2 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Chip
              label={statusLabelFor(course.enrollmentStatus) || statusLabel}
              size="small"
              color={getStatusColor(course.enrollmentStatus)}
              variant="outlined"
            />
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="outlined"
                size="small"
                onClick={handleViewCourse}
                sx={{
                  borderColor: '#CBD2DC',
                  borderRadius: '6px',
                  color: '#1B2A4A',
                  textTransform: 'none',
                  fontWeight: 700,
                  '&:hover': { borderColor: '#C41E3A', color: '#C41E3A' },
                }}
              >
                Chi tiết
              </Button>
              <Button
                variant="contained"
                size="small"
                onClick={handleStudyCourse}
                sx={{
                  borderRadius: '6px',
                  bgcolor: '#C41E3A',
                  textTransform: 'none',
                  fontWeight: 800,
                  boxShadow: 'none',
                  '&:hover': { bgcolor: '#A71931', boxShadow: 'none' },
                }}
              >
                {course.enrollmentStatus === 'COMPLETED' ? 'Xem lại' : 'Học tiếp'}
              </Button>
            </Box>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};
