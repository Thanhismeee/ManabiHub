import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import StarRoundedIcon from '@mui/icons-material/StarRounded';
import RefreshIcon from '@mui/icons-material/Refresh';
import { getAuthSession } from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { courseReviewService } from '../services/courseReviewService';
import type {
  CourseReview,
  CourseReviewPage,
} from '../types/courseReviewTypes';

interface CourseReviewsSectionProps {
  courseId: string;
  courseIdentifier: string;
  isEnrolled: boolean;
  canTeacherReply?: boolean;
  averageRating?: number;
  reviewCount?: number;
  onReviewChanged?: () => Promise<unknown> | unknown;
}

const emptyPage: CourseReviewPage = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

export const CourseReviewsSection = ({
  courseId,
  courseIdentifier,
  isEnrolled,
  canTeacherReply = false,
  averageRating,
  reviewCount = 0,
  onReviewChanged,
}: CourseReviewsSectionProps) => {
  const [page, setPage] = useState<CourseReviewPage>(emptyPage);
  const [pageIndex, setPageIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [myReview, setMyReview] = useState<CourseReview | null>(null);
  const [rating, setRating] = useState(5);
  const [reviewText, setReviewText] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [replyingReviewId, setReplyingReviewId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');
  const [replySaving, setReplySaving] = useState(false);
  const [replyError, setReplyError] = useState<string | null>(null);

  const canReview = useMemo(() => {
    const session = getAuthSession('public');
    return Boolean(
      isEnrolled
      && session
      && session.roles.includes(ROLES.STUDENT),
    );
  }, [isEnrolled]);

  const loadReviews = useCallback(async (targetPage: number) => {
    setLoading(true);
    setLoadError(false);
    try {
      const response = await courseReviewService.getPublicReviews(
        courseIdentifier,
        targetPage,
        10,
      );
      setPage(response);
      setPageIndex(targetPage);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [courseIdentifier]);

  useEffect(() => {
    void loadReviews(0);
  }, [loadReviews]);

  useEffect(() => {
    if (!canReview) return;
    let active = true;
    courseReviewService.getMyReview(courseId)
      .then((review) => {
        if (!active || !review) return;
        setMyReview(review);
        setRating(review.rating);
        setReviewText(review.reviewText);
      })
      .catch(() => {
        // The editor remains hidden from error details. Eligibility is checked
        // again by the backend when the student saves.
      });
    return () => {
      active = false;
    };
  }, [canReview, courseId]);

  const handleSave = async () => {
    const normalizedText = reviewText.replace(/\s+/g, ' ').trim();
    if (normalizedText.length < 10 || normalizedText.length > 2000) {
      setSaveError('Nội dung đánh giá phải có từ 10 đến 2.000 ký tự.');
      return;
    }

    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      const review = await courseReviewService.upsertMyReview(courseId, {
        rating,
        reviewText: normalizedText,
      });
      setMyReview(review);
      setReviewText(review.reviewText);
      setSaved(true);
      await loadReviews(0);
      try {
        await onReviewChanged?.();
      } catch {
        // The review is already persisted; a later manual refresh can update
        // the course aggregate if the detail request is temporarily unavailable.
      }
    } catch {
      setSaveError(
        'Không thể lưu đánh giá. Hãy kiểm tra trạng thái ghi danh và thử lại.',
      );
    } finally {
      setSaving(false);
    }
  };

  const startReply = (review: CourseReview) => {
    setReplyingReviewId(review.id);
    setReplyText(review.teacherReplyText ?? '');
    setReplyError(null);
  };

  const handleReply = async (reviewId: string) => {
    const normalizedReply = replyText.replace(/\s+/g, ' ').trim();
    if (normalizedReply.length < 2 || normalizedReply.length > 2000) {
      setReplyError('Nội dung phản hồi phải có từ 2 đến 2.000 ký tự.');
      return;
    }

    setReplySaving(true);
    setReplyError(null);
    try {
      const updatedReview = await courseReviewService.replyToReview(reviewId, {
        replyText: normalizedReply,
      });
      setPage((current) => ({
        ...current,
        content: current.content.map((review) => (
          review.id === reviewId ? updatedReview : review
        )),
      }));
      setReplyingReviewId(null);
      setReplyText('');
    } catch {
      setReplyError('Không thể lưu phản hồi. Vui lòng thử lại.');
    } finally {
      setReplySaving(false);
    }
  };

  return (
    <Box id="course-reviews" component="section" aria-labelledby="course-reviews-title" sx={{ mt: 6 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 2.5 }}
      >
        <Box>
          <Typography id="course-reviews-title" component="h2" variant="h4" sx={{ fontWeight: 900 }}>
            Đánh giá từ học viên
          </Typography>
          {reviewCount > 0 && averageRating != null && (
            <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mt: 0.75 }}>
              <StarRoundedIcon sx={{ color: '#F59E0B' }} />
              <Typography sx={{ fontWeight: 800 }}>
                {averageRating.toFixed(1)}
              </Typography>
              <Typography color="text.secondary">
                ({reviewCount} đánh giá đã xác minh)
              </Typography>
            </Stack>
          )}
        </Box>
        <Button
          variant="outlined"
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
          disabled={loading}
          onClick={() => {
            void Promise.all([
              loadReviews(pageIndex),
              onReviewChanged?.(),
            ]);
          }}
          sx={{ textTransform: 'none', fontWeight: 700, alignSelf: { xs: 'flex-start', sm: 'center' } }}
        >
          Tải lại đánh giá
        </Button>
      </Stack>

      {canReview && (
        <Paper
          elevation={0}
          sx={{ p: { xs: 2, sm: 3 }, mb: 3, border: '1px solid', borderColor: 'divider', borderRadius: 3 }}
        >
          <Typography component="h3" variant="h6" sx={{ fontWeight: 800 }}>
            {myReview ? 'Cập nhật đánh giá của bạn' : 'Viết đánh giá'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Chỉ học viên đang học hoặc đã hoàn thành khóa học mới có thể đánh giá.
          </Typography>
          <Stack direction="row" spacing={0.25} sx={{ my: 2 }}>
            {[1, 2, 3, 4, 5].map((value) => (
              <Button
                key={value}
                type="button"
                aria-label={`${value} sao`}
                aria-pressed={rating === value}
                onClick={() => setRating(value)}
                sx={{ minWidth: 40, p: 0.5 }}
              >
                <StarRoundedIcon
                  sx={{ color: value <= rating ? '#F59E0B' : 'grey.300', fontSize: 30 }}
                />
              </Button>
            ))}
          </Stack>
          <TextField
            label="Nội dung đánh giá"
            value={reviewText}
            onChange={(event) => {
              setReviewText(event.target.value);
              setSaved(false);
            }}
            multiline
            minRows={4}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 2000 } }}
            helperText={`${reviewText.trim().length}/2000 ký tự`}
          />
          {saveError && <Alert severity="error" sx={{ mt: 2 }}>{saveError}</Alert>}
          {saved && <Alert severity="success" sx={{ mt: 2 }}>Đã lưu đánh giá của bạn.</Alert>}
          <Button
            variant="contained"
            onClick={() => void handleSave()}
            disabled={saving}
            sx={{ mt: 2, minWidth: 150 }}
          >
            {saving ? 'Đang lưu…' : myReview ? 'Cập nhật' : 'Gửi đánh giá'}
          </Button>
        </Paper>
      )}

      {loading ? (
        <Stack sx={{ alignItems: 'center', py: 5 }}>
          <CircularProgress size={30} aria-label="Đang tải đánh giá" />
        </Stack>
      ) : loadError ? (
        <Alert
          severity="warning"
          action={<Button onClick={() => void loadReviews(pageIndex)}>Thử lại</Button>}
        >
          Chưa thể tải danh sách đánh giá.
        </Alert>
      ) : page.content.length === 0 ? (
        <Paper
          elevation={0}
          sx={{ p: 3, textAlign: 'center', bgcolor: 'grey.50', borderRadius: 3 }}
        >
          <Typography color="text.secondary">
            Khóa học chưa có đánh giá đã xác minh.
          </Typography>
        </Paper>
      ) : (
        <Paper
          elevation={0}
          sx={{ px: { xs: 2, sm: 3 }, border: '1px solid', borderColor: 'divider', borderRadius: 3 }}
        >
          {page.content.map((review, index) => (
            <Box key={review.id}>
              {index > 0 && <Divider />}
              <Stack direction="row" spacing={2} sx={{ py: 3, alignItems: 'flex-start' }}>
                <Avatar
                  src={resolvePublicAssetUrl(review.authorAvatarUrl)}
                  alt={review.authorDisplayName}
                >
                  {review.authorDisplayName.charAt(0).toUpperCase()}
                </Avatar>
                <Box sx={{ minWidth: 0, flex: 1 }}>
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={{ xs: 0.5, sm: 1 }}
                    sx={{ justifyContent: 'space-between' }}
                  >
                    <Typography sx={{ fontWeight: 800 }}>
                      {review.authorDisplayName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {new Date(review.updatedAt).toLocaleDateString('vi-VN')}
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={0.1} aria-label={`${review.rating} trên 5 sao`}>
                    {[1, 2, 3, 4, 5].map((value) => (
                      <StarRoundedIcon
                        key={value}
                        sx={{
                          color: value <= review.rating ? '#F59E0B' : 'grey.300',
                          fontSize: 19,
                        }}
                      />
                    ))}
                  </Stack>
                  <Typography sx={{ mt: 1.25, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {review.reviewText}
                  </Typography>
                  {review.teacherReplyText && (
                    <Box
                      sx={{
                        mt: 2,
                        ml: { sm: 2 },
                        p: 2,
                        bgcolor: 'grey.50',
                        borderLeft: '3px solid',
                        borderColor: 'primary.main',
                        borderRadius: 1.5,
                      }}
                    >
                      <Stack
                        direction={{ xs: 'column', sm: 'row' }}
                        spacing={0.5}
                        sx={{ justifyContent: 'space-between', mb: 0.5 }}
                      >
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                          Phản hồi từ giảng viên
                        </Typography>
                        {review.teacherRepliedAt && (
                          <Typography variant="caption" color="text.secondary">
                            {new Date(review.teacherRepliedAt).toLocaleDateString('vi-VN')}
                          </Typography>
                        )}
                      </Stack>
                      <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                        {review.teacherReplyText}
                      </Typography>
                    </Box>
                  )}
                  {canTeacherReply && (
                    replyingReviewId === review.id ? (
                      <Stack spacing={1.25} sx={{ mt: 2 }}>
                        <TextField
                          label="Phản hồi học viên"
                          value={replyText}
                          onChange={(event) => setReplyText(event.target.value)}
                          multiline
                          minRows={3}
                          fullWidth
                          autoFocus
                          slotProps={{ htmlInput: { maxLength: 2000 } }}
                          helperText={`${replyText.trim().length}/2000 ký tự`}
                        />
                        {replyError && <Alert severity="error">{replyError}</Alert>}
                        <Stack direction="row" spacing={1}>
                          <Button
                            variant="contained"
                            onClick={() => void handleReply(review.id)}
                            disabled={replySaving}
                          >
                            {replySaving ? 'Đang lưu…' : 'Gửi phản hồi'}
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setReplyingReviewId(null);
                              setReplyText('');
                              setReplyError(null);
                            }}
                            disabled={replySaving}
                          >
                            Hủy
                          </Button>
                        </Stack>
                      </Stack>
                    ) : (
                      <Button
                        size="small"
                        variant="text"
                        onClick={() => startReply(review)}
                        sx={{ mt: 1.25 }}
                      >
                        {review.teacherReplyText ? 'Chỉnh sửa phản hồi' : 'Phản hồi bình luận'}
                      </Button>
                    )
                  )}
                </Box>
              </Stack>
            </Box>
          ))}
        </Paper>
      )}

      {page.totalPages > 1 && !loading && !loadError && (
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'center', mt: 2.5 }}>
          <Button
            variant="outlined"
            disabled={page.first}
            onClick={() => void loadReviews(pageIndex - 1)}
          >
            Trang trước
          </Button>
          <Typography sx={{ alignSelf: 'center' }}>
            {page.page + 1}/{page.totalPages}
          </Typography>
          <Button
            variant="outlined"
            disabled={page.last}
            onClick={() => void loadReviews(pageIndex + 1)}
          >
            Trang sau
          </Button>
        </Stack>
      )}
    </Box>
  );
};
