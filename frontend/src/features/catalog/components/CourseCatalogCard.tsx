import React, { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Stack,
  Typography,
} from '@mui/material';
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined';
import StarRoundedIcon from '@mui/icons-material/StarRounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { ROUTES } from '../../../shared/constants/routes';
import { WishlistToggleButton } from '../../wishlist/components/WishlistToggleButton';
import type { PublicCourseSummary } from '../types/catalogTypes';
import { proficiencyLabel } from '../../../shared/learning/languageProfiles';

interface CourseCatalogCardProps {
  course: PublicCourseSummary;
}

function formatPrice(price: number, currency: string): string {
  if (price === 0) return 'Miễn phí';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency || 'VND',
    maximumFractionDigits: 0,
  }).format(price);
}

export const CourseCatalogCard: React.FC<CourseCatalogCardProps> = ({ course }) => {
  const [imageFailed, setImageFailed] = useState(false);
  const thumbnailUrl = useMemo(
    () => resolvePublicAssetUrl(course.thumbnailUrl),
    [course.thumbnailUrl],
  );

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        overflow: 'hidden',
        transition: 'all 300ms cubic-bezier(0.4, 0, 0.2, 1)',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          '& .card-title': { color: '#C41E3A' },
        },
      }}
    >
      <WishlistToggleButton courseId={course.id} variant="icon" />
      <CardActionArea
        component={Link}
        to={`/courses/${course.slug || course.id}`}
        aria-label={`Xem khóa học ${course.title}`}
      >
        <Box
          sx={{
            position: 'relative',
            width: '100%',
            aspectRatio: '16 / 9',
            bgcolor: 'grey.100',
            overflow: 'hidden',
          }}
        >
          {thumbnailUrl && !imageFailed ? (
            <Box
              component="img"
              src={thumbnailUrl}
              alt={`Ảnh khóa học ${course.title}`}
              onError={() => setImageFailed(true)}
              sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <Box
              role="img"
              aria-label={`Khóa học ${course.title} chưa có ảnh bìa`}
              sx={{
                width: '100%',
                height: '100%',
                display: 'grid',
                placeItems: 'center',
                color: 'text.disabled',
              }}
            >
              <ImageNotSupportedOutlinedIcon sx={{ fontSize: 44 }} />
            </Box>
          )}
          {(course.proficiencyLevel || course.jlptLevel) && (
            <Chip
              label={proficiencyLabel(
                course.proficiencyFramework ?? (course.jlptLevel ? 'JLPT' : undefined),
                course.proficiencyLevel ?? course.jlptLevel,
              )}
              size="small"
              sx={{
                position: 'absolute',
                top: 12,
                left: 12,
                fontWeight: 700,
                bgcolor: '#C41E3A',
                color: 'white',
              }}
            />
          )}
        </Box>

        <CardContent sx={{ pb: 1 }}>
          {course.category && (
            <Typography variant="caption" color="primary.main" sx={{ mb: 0.5, fontWeight: 700 }}>
              {course.category}
            </Typography>
          )}
          <Typography
            className="card-title"
            component="h3"
            variant="subtitle1"
            sx={{
              fontWeight: 700,
              lineHeight: 1.4,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              transition: 'color 0.2s',
            }}
          >
            {course.title}
          </Typography>
        </CardContent>
      </CardActionArea>

      <CardContent
        sx={{
          pt: 0,
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          '&:last-child': { pb: 2 },
        }}
      >
        {course.teacherId ? (
          <Typography
            component={Link}
            to={ROUTES.PUBLIC.TEACHER_PROFILE(course.teacherId)}
            variant="body2"
            color="text.secondary"
            sx={{
              alignSelf: 'flex-start',
              textDecoration: 'none',
              fontWeight: 600,
              '&:hover': { color: '#C41E3A', textDecoration: 'underline' },
            }}
          >
            {course.teacherName || 'Giảng viên ManabiHub'}
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {course.teacherName || 'Giảng viên ManabiHub'}
          </Typography>
        )}

        {(course.reviewCount ?? 0) > 0 && course.averageRating != null && (
          <Stack
            direction="row"
            spacing={0.4}
            sx={{ mt: 1, alignItems: 'center' }}
            aria-label={`${course.averageRating.toFixed(1)} trên 5 sao từ ${course.reviewCount} đánh giá`}
          >
            <StarRoundedIcon sx={{ color: '#F59E0B', fontSize: 19 }} />
            <Typography variant="body2" sx={{ fontWeight: 800 }}>
              {course.averageRating.toFixed(1)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ({course.reviewCount})
            </Typography>
          </Stack>
        )}

        {(course.enrollmentCount ?? 0) > 0 && (
          <Stack direction="row" spacing={0.5} sx={{ mt: 0.75, alignItems: 'center' }}>
            <PeopleAltRoundedIcon sx={{ color: '#64748B', fontSize: 17 }} />
            <Typography variant="caption" color="text.secondary">
              {(course.enrollmentCount ?? 0).toLocaleString('vi-VN')} học viên
            </Typography>
          </Stack>
        )}

        <Stack
          direction="row"
          spacing={1}
          sx={{ mt: 'auto', pt: 2, justifyContent: 'space-between', alignItems: 'center' }}
        >
          <Typography
            variant="subtitle1"
            sx={{ fontWeight: 800, color: course.price === 0 ? 'success.main' : '#C41E3A' }}
          >
            {formatPrice(course.price, course.currency)}
          </Typography>
          <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
            {course.totalLessons} bài học
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
};
