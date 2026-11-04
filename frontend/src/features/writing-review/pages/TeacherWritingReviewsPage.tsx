import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import SearchIcon from '@mui/icons-material/Search';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { ROUTES } from '../../../shared/constants/routes';
import {
  useWritingReviewFacets,
  useWritingReviewOverview,
  useWritingReviews,
} from '../hooks/useWritingReviews';
import type {
  WritingSubmissionStatus,
  WritingSubmissionSummary,
} from '../types/writingReviewTypes';

const PAGE_SIZE = 10;

type ReviewFilter = 'ALL' | 'PENDING' | 'REVIEWED';

const SUBMISSION_STATUS_OPTIONS: Array<{
  value: WritingSubmissionStatus;
  label: string;
}> = [
  { value: 'SUBMITTED', label: 'Đã nộp' },
  { value: 'SUGGESTION_PROCESSING', label: 'AI đang xử lý' },
  { value: 'SUGGESTION_READY', label: 'Có gợi ý AI' },
  { value: 'SUGGESTION_FAILED', label: 'Gợi ý AI lỗi' },
  { value: 'TEACHER_FEEDBACK_READY', label: 'Đã chấm' },
];

export function TeacherWritingReviewsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [searchDraft, setSearchDraft] = useState('');
  const [query, setQuery] = useState('');
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>('ALL');
  const [courseId, setCourseId] = useState('');
  const [lessonId, setLessonId] = useState('');
  const [submissionStatus, setSubmissionStatus] = useState<WritingSubmissionStatus | ''>('');

  const reviewed = reviewFilter === 'ALL' ? undefined : reviewFilter === 'REVIEWED';
  const filters = {
    query: query || undefined,
    courseId: courseId || undefined,
    lessonId: lessonId || undefined,
    status: submissionStatus || undefined,
  };
  const reviews = useWritingReviews({
    page,
    size: PAGE_SIZE,
    reviewed,
    ...filters,
  });
  const facets = useWritingReviewFacets();
  const overview = useWritingReviewOverview(filters);

  const selectedCourse = useMemo(
    () => facets.data?.courses.find((course) => course.id === courseId),
    [courseId, facets.data?.courses],
  );

  const submitSearch = () => {
    setPage(0);
    setQuery(searchDraft.trim());
  };

  const resetFilters = () => {
    setPage(0);
    setSearchDraft('');
    setQuery('');
    setReviewFilter('ALL');
    setCourseId('');
    setLessonId('');
    setSubmissionStatus('');
  };

  const openSubmission = (submissionId: string) => {
    navigate(ROUTES.TEACHER.WRITING_REVIEW_DETAIL(submissionId));
  };

  return (
    <Box sx={{ pb: 6 }}>
      <PageHeader
        title="Chấm bài Writing"
        subtitle="Theo dõi, lọc và phản hồi bài viết theo từng khóa học, bài học."
        breadcrumbs={[{ label: 'Giảng viên' }, { label: 'Chấm bài Writing' }]}
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 6, md: 3 }}>
          <OverviewCard label="Tổng bài nộp" value={overview.data?.totalSubmissions} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <OverviewCard label="Chờ chấm" value={overview.data?.pendingSubmissions} tone="warning" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <OverviewCard label="Đã chấm" value={overview.data?.reviewedSubmissions} tone="success" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <OverviewCard
            label="Điểm trung bình"
            value={overview.data?.averageScore == null ? '—' : `${overview.data.averageScore}/10`}
          />
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2}>
            <TextField
              fullWidth
              size="small"
              label="Tìm học viên, khóa học hoặc bài học"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') submitSearch();
              }}
            />
            <Button
              variant="contained"
              startIcon={<SearchIcon />}
              onClick={submitSearch}
              sx={{ minWidth: 124, flexShrink: 0 }}
            >
              Tìm kiếm
            </Button>
          </Stack>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="writing-course-filter-label">Khóa học</InputLabel>
                <Select
                  labelId="writing-course-filter-label"
                  label="Khóa học"
                  value={courseId}
                  onChange={(event) => {
                    setPage(0);
                    setCourseId(event.target.value);
                    setLessonId('');
                  }}
                >
                  <MenuItem value="">Tất cả khóa học</MenuItem>
                  {facets.data?.courses.map((course) => (
                    <MenuItem key={course.id} value={course.id}>{course.title}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <FormControl fullWidth size="small" disabled={!courseId}>
                <InputLabel id="writing-lesson-filter-label">Bài học</InputLabel>
                <Select
                  labelId="writing-lesson-filter-label"
                  label="Bài học"
                  value={lessonId}
                  onChange={(event) => {
                    setPage(0);
                    setLessonId(event.target.value);
                  }}
                >
                  <MenuItem value="">Tất cả bài học</MenuItem>
                  {selectedCourse?.lessons.map((lesson) => (
                    <MenuItem key={lesson.id} value={lesson.id}>{lesson.title}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="writing-review-filter-label">Chấm điểm</InputLabel>
                <Select
                  labelId="writing-review-filter-label"
                  label="Chấm điểm"
                  value={reviewFilter}
                  onChange={(event) => {
                    setPage(0);
                    setReviewFilter(event.target.value as ReviewFilter);
                  }}
                >
                  <MenuItem value="ALL">Tất cả</MenuItem>
                  <MenuItem value="PENDING">Chờ chấm</MenuItem>
                  <MenuItem value="REVIEWED">Đã chấm</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="writing-submission-status-label">Trạng thái bài</InputLabel>
                <Select
                  labelId="writing-submission-status-label"
                  label="Trạng thái bài"
                  value={submissionStatus}
                  onChange={(event) => {
                    setPage(0);
                    setSubmissionStatus(event.target.value as WritingSubmissionStatus | '');
                  }}
                >
                  <MenuItem value="">Tất cả</MenuItem>
                  {SUBMISSION_STATUS_OPTIONS.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, lg: 2 }}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<RestartAltIcon />}
                onClick={resetFilters}
              >
                Xóa bộ lọc
              </Button>
            </Grid>
          </Grid>
        </Stack>
      </Paper>

      {reviews.isPending && <LoadingState message="Đang tải danh sách bài viết..." />}

      {reviews.isError && (
        <ErrorState message="Không thể tải danh sách bài viết." onRetry={() => reviews.refetch()} />
      )}

      {reviews.data && reviews.data.content.length === 0 && (
        <Paper variant="outlined">
          <EmptyState
            title="Không có bài viết phù hợp"
            description="Hãy thay đổi từ khóa hoặc bộ lọc để xem các bài nộp khác."
            icon={<AssignmentOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
          />
        </Paper>
      )}

      {reviews.data && reviews.data.content.length > 0 && (
        <>
          <TableContainer component={Paper} variant="outlined" sx={{ display: { xs: 'none', md: 'block' } }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Học viên</TableCell>
                  <TableCell>Khóa học / bài học</TableCell>
                  <TableCell>Ngày nộp</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Điểm</TableCell>
                  <TableCell align="right">Hành động</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {reviews.data.content.map((submission) => (
                  <TableRow hover key={submission.id}>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{submission.studentName}</Typography>
                      <Typography variant="caption" color="text.secondary">{submission.studentEmail}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{submission.courseTitle}</Typography>
                      <Typography variant="caption" color="text.secondary">{submission.lessonTitle}</Typography>
                    </TableCell>
                    <TableCell>{formatDateTime(submission.submittedAt)}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                        {renderReviewChip(submission)}
                        {submission.hasAiSuggestion && <Chip size="small" variant="outlined" color="info" label="Có gợi ý AI" />}
                      </Stack>
                    </TableCell>
                    <TableCell>{submission.score == null ? '—' : `${submission.score}/10`}</TableCell>
                    <TableCell align="right">
                      <Button
                        variant={submission.hasTeacherFeedback ? 'outlined' : 'contained'}
                        size="small"
                        onClick={() => openSubmission(submission.id)}
                        sx={{ textTransform: 'none', fontWeight: 700 }}
                      >
                        {submission.hasTeacherFeedback ? 'Xem / sửa' : 'Chấm bài'}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <Stack spacing={1.5} sx={{ display: { xs: 'flex', md: 'none' } }}>
            {reviews.data.content.map((submission) => (
              <Paper
                variant="outlined"
                key={submission.id}
                sx={{ p: 2, cursor: 'pointer' }}
                onClick={() => openSubmission(submission.id)}
              >
                <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography noWrap sx={{ fontWeight: 700 }}>{submission.studentName}</Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>{submission.courseTitle}</Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                      {submission.lessonTitle}
                    </Typography>
                  </Box>
                  {renderReviewChip(submission)}
                </Stack>
                <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mt: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    {formatDateTime(submission.submittedAt)}
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {submission.score == null ? 'Chưa có điểm' : `${submission.score}/10`}
                  </Typography>
                </Stack>
              </Paper>
            ))}
          </Stack>

          {reviews.data.totalPages > 1 && (
            <Stack sx={{ alignItems: 'center', mt: 3 }}>
              <Pagination
                page={page + 1}
                count={reviews.data.totalPages}
                onChange={(_, nextPage) => setPage(nextPage - 1)}
                color="primary"
              />
            </Stack>
          )}
        </>
      )}
    </Box>
  );
}

function OverviewCard({
  label,
  value,
  tone = 'default',
}: {
  label: string;
  value: number | string | undefined;
  tone?: 'default' | 'warning' | 'success';
}) {
  const color = tone === 'warning' ? 'warning.main' : tone === 'success' ? 'success.main' : 'text.primary';
  return (
    <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h5" sx={{ mt: 0.5, fontWeight: 800, color }}>
        {value ?? '—'}
      </Typography>
    </Paper>
  );
}

function renderReviewChip(submission: WritingSubmissionSummary) {
  return submission.hasTeacherFeedback ? (
    <Chip size="small" color="success" label="Đã chấm" />
  ) : (
    <Chip size="small" color="warning" label="Chờ chấm" />
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
