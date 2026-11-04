import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import FilterListIcon from '@mui/icons-material/FilterList';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import PublishIcon from '@mui/icons-material/Publish';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import SortIcon from '@mui/icons-material/Sort';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import AssessmentIcon from '@mui/icons-material/Assessment';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Pagination,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState/LoadingState';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { ROUTES } from '../../../shared/constants/routes';
import { CourseAnalyticsDialog } from '../components/CourseAnalyticsDialog';
import {
  deleteCourseDraft,
  fetchCourseCategories,
  fetchCourseDrafts,
  publishCourse,
  unpublishCourse,
  type CourseCategory,
  type CourseDraftResponse,
  type JlptLevel,
  submitCourseForReview,
  validateCourseDraft
} from '../services/courseDraftService';
import { courseStatusColor, courseStatusLabel } from '../utils/courseStatus';

interface CourseDraftSavedState {
  draftSaved?: boolean;
  draftId?: string;
  draftTitle?: string;
  finalTestSaved?: boolean;
}

type Feedback = {
  message: string;
  severity: 'success' | 'error';
} | null;

type CourseSortOrder = 'UPDATED_DESC' | 'UPDATED_ASC';

const allFilterValue = 'ALL';
const defaultSortOrder: CourseSortOrder = 'UPDATED_DESC';
const draftPageSize = 6;
const jlptLevels: JlptLevel[] = ['N5', 'N4', 'N3', 'N2', 'N1'];


const priceFormatter = new Intl.NumberFormat('vi-VN', {
  currency: 'VND',
  maximumFractionDigits: 0,
  style: 'currency',
});

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

export function TeacherCoursesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const draftState = location.state as CourseDraftSavedState | null;
  const recentlySaved = Boolean(draftState?.draftSaved || draftState?.finalTestSaved);
  const [drafts, setDrafts] = useState<CourseDraftResponse[]>([]);
  const [categories, setCategories] = useState<CourseCategory[]>([]);
  const [query, setQuery] = useState('');
  const [levelFilter, setLevelFilter] = useState(allFilterValue);
  const [categoryFilter, setCategoryFilter] = useState(allFilterValue);
  const [sortOrder, setSortOrder] = useState<CourseSortOrder>(defaultSortOrder);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [publishingId, setPublishingId] = useState<string | null>(null);
  const [publishCandidate, setPublishCandidate] = useState<CourseDraftResponse | null>(null);
  const [unpublishingId, setUnpublishingId] = useState<string | null>(null);
  const [unpublishCandidate, setUnpublishCandidate] = useState<CourseDraftResponse | null>(null);
  const [analyticsCourse, setAnalyticsCourse] = useState<{ id: string; title: string } | null>(null);

  const categoryNames = useMemo(
    () => new Map(categories.map((category) => [category.code, category.name])),
    [categories],
  );

  const filteredDrafts = useMemo(() => {
    const normalizedQuery = normalizeSearch(query);

    return drafts.filter((course) => {
      const categoryName = categoryNames.get(course.category) || course.category;
      const searchText = normalizeSearch([
        displayDraftTitle(course),
        toPlainText(course.introduction),
        course.jlptLevel,
        categoryName,
      ].join(' '));

      return (!normalizedQuery || searchText.includes(normalizedQuery))
        && (levelFilter === allFilterValue || course.jlptLevel === levelFilter)
        && (categoryFilter === allFilterValue || course.category === categoryFilter);
    }).sort((first, second) => {
      const difference = courseLastModifiedAt(first) - courseLastModifiedAt(second);
      return sortOrder === 'UPDATED_DESC' ? -difference : difference;
    });
  }, [categoryFilter, categoryNames, drafts, levelFilter, query, sortOrder]);

  const pageCount = Math.max(1, Math.ceil(filteredDrafts.length / draftPageSize));
  const pagedDrafts = useMemo(() => {
    const startIndex = (currentPage - 1) * draftPageSize;
    return filteredDrafts.slice(startIndex, startIndex + draftPageSize);
  }, [currentPage, filteredDrafts]);

  const loadDrafts = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);

    try {
      const [draftList, categoryList] = await Promise.all([
        fetchCourseDrafts(),
        fetchCourseCategories().catch(() => []),
      ]);

      setDrafts(draftList);
      setCategories(categoryList);
    } catch {
      setLoadError('Không thể tải danh sách bản nháp. Vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDrafts();
  }, [loadDrafts]);

  useEffect(() => {
    setCurrentPage(1);
  }, [categoryFilter, levelFilter, query, sortOrder]);

  useEffect(() => {
    if (currentPage > pageCount) {
      setCurrentPage(pageCount);
    }
  }, [currentPage, pageCount]);

  function clearFilters() {
    setQuery('');
    setLevelFilter(allFilterValue);
    setCategoryFilter(allFilterValue);
    setSortOrder(defaultSortOrder);
  }

  function editDraft(course: CourseDraftResponse) {
    navigate(ROUTES.TEACHER.COURSE_CREATE, {
      state: {
        draftToEdit: course,
      },
    });
  }

  function buildCourseContent(course: CourseDraftResponse) {
    navigate(ROUTES.TEACHER.COURSE_BUILDER(course.id));
  }

  async function deleteDraft(course: CourseDraftResponse) {
    const title = displayDraftTitle(course);
    const confirmed = window.confirm(`Xóa bản nháp "${title}"? Thao tác này không thể hoàn tác.`);

    if (!confirmed) {
      return;
    }

    setDeletingId(course.id);
    setFeedback(null);

    try {
      await deleteCourseDraft(course.id);
      setDrafts((current) => current.filter((draft) => draft.id !== course.id));
      setFeedback({ severity: 'success', message: `Đã xóa bản nháp "${title}".` });
    } catch {
      setFeedback({ severity: 'error', message: 'Không thể xóa bản nháp. Vui lòng thử lại.' });
    } finally {
      setDeletingId(null);
    }
  }

  async function submitDraft(course: CourseDraftResponse) {
    setSubmittingId(course.id);

    try {

      const validation =
          await validateCourseDraft(course.id);

      if (!validation.isValid) {

        const errorMessage = [
          "Không thể gửi khóa học để xét duyệt.",
          "",
          ...validation.errors.map(error => `• ${error.message}`),
        ].join("\n");

        setFeedback({
          severity: "error",
          message: errorMessage,
        });

        return;
      }

      await submitCourseForReview(course.id);

      setFeedback({
        severity: "success",
        message: "Đã gửi khóa học để xét duyệt.",
      });

      await loadDrafts();

    } catch {

      setFeedback({
        severity: "error",
        message: "Không thể gửi khóa học.",
      });

    } finally {

      setSubmittingId(null);

    }
  }

  async function publishApprovedCourse() {
    if (!publishCandidate) {
      return;
    }

    const course = publishCandidate;
    setPublishingId(course.id);
    setFeedback(null);

    try {
      const response = await publishCourse(course.id);
      setFeedback({
        severity: 'success',
        message: response.message || 'Sản phẩm đã được xuất bản và hiển thị trên danh mục.',
      });
      await loadDrafts();
    } catch {
      setFeedback({
        severity: 'error',
        message: 'Không thể xuất bản khóa học. Vui lòng tải lại và thử lại.',
      });
    } finally {
      setPublishingId(null);
      setPublishCandidate(null);
    }
  }

  async function unpublishPublishedCourse() {
    if (!unpublishCandidate) {
      return;
    }

    const course = unpublishCandidate;
    setUnpublishingId(course.id);
    setFeedback(null);

    try {
      const response = await unpublishCourse(course.id);
      setFeedback({
        severity: 'success',
        message: response.message || 'Khóa học đã được ẩn khỏi danh mục. Học viên hiện tại vẫn học phiên bản đã duyệt trong khi bạn chuẩn bị bản cập nhật.',
      });
      await loadDrafts();
      setUnpublishCandidate(null);

      if (response.data) {
        editDraft(response.data);
      }
    } catch {
      setFeedback({
        severity: 'error',
        message: 'Không thể ẩn khóa học để chỉnh sửa. Vui lòng tải lại và thử lại.',
      });
    } finally {
      setUnpublishingId(null);
    }
  }

  return (
    <Box>
      <PageHeader
        title="Khóa học của tôi"
        breadcrumbs={[
          { label: 'Giảng viên' },
          { label: 'Khóa học' },
        ]}
        action={(
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate(ROUTES.TEACHER.COURSE_CREATE)}
            sx={{ textTransform: 'none', fontWeight: 700 }}
          >
            Tạo bản nháp
          </Button>
        )}
      />

      {recentlySaved && (
        <Alert
          severity="success"
          action={(
            <Button
              color="inherit"
              size="small"
              onClick={() => navigate(ROUTES.TEACHER.COURSES, { replace: true })}
              sx={{ textTransform: 'none', fontWeight: 700 }}
            >
              Ẩn thông báo
            </Button>
          )}
          sx={{ mb: 2 }}
        >
          {draftState?.finalTestSaved
            ? 'Đã lưu cấu hình Final Test. Bạn có thể tiếp tục hoàn thiện hoặc gửi khóa học để xét duyệt.'
            : `Đã lưu bản nháp “${draftState?.draftTitle || 'khóa học mới'}”. Bạn có thể xem lại khóa học trong danh sách bản nháp bên dưới.`}
        </Alert>
      )}

      {feedback && (
        <Alert severity={feedback.severity} sx={{ mb: 2, whiteSpace: "pre-line" }} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          p: { xs: 2, md: 3 },
          pb: { xs: 3, md: 4 },
        }}
      >
        <Stack spacing={2.5}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={2}
            sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' } }}
          >
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Danh sách khóa học
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Theo dõi trạng thái, hoàn thiện và xuất bản các khóa học đã được phê duyệt.
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              disabled={isLoading}
              onClick={() => void loadDrafts()}
              sx={{ textTransform: 'none', fontWeight: 700 }}
            >
              Tải lại
            </Button>
          </Stack>

          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={1.5}
            sx={{ alignItems: { xs: 'stretch', md: 'center' } }}
          >
            <TextField
              fullWidth
              size="small"
              label="Tìm kiếm khóa học"
              placeholder="Nhập tên khóa học, mô tả hoặc danh mục"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
            />
            <TextField
              select
              size="small"
              label="Trình độ"
              value={levelFilter}
              onChange={(event) => setLevelFilter(event.target.value)}
              sx={{ minWidth: { xs: '100%', md: 150 } }}
            >
              <MenuItem value={allFilterValue}>Tất cả</MenuItem>
              {jlptLevels.map((level) => (
                <MenuItem key={level} value={level}>{level}</MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Danh mục"
              value={categoryFilter}
              onChange={(event) => setCategoryFilter(event.target.value)}
              sx={{ minWidth: { xs: '100%', md: 210 } }}
            >
              <MenuItem value={allFilterValue}>Tất cả danh mục</MenuItem>
              {categories.map((category) => (
                <MenuItem key={category.code} value={category.code}>
                  {category.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Sắp xếp"
              value={sortOrder}
              onChange={(event) => setSortOrder(event.target.value as CourseSortOrder)}
              sx={{ minWidth: { xs: '100%', md: 190 } }}
              slotProps={{
                select: {
                  IconComponent: SortIcon,
                },
              }}
            >
              <MenuItem value="UPDATED_DESC">Mới cập nhật</MenuItem>
              <MenuItem value="UPDATED_ASC">Cũ cập nhật</MenuItem>
            </TextField>
            <Button
              variant="text"
              startIcon={<FilterListIcon />}
              onClick={clearFilters}
              sx={{ alignSelf: { xs: 'flex-start', md: 'center' }, textTransform: 'none', fontWeight: 700 }}
            >
              Xóa lọc
            </Button>
          </Stack>

          {isLoading && <LoadingState message="Đang tải danh sách bản nháp..." />}

          {!isLoading && loadError && (
            <Alert
              severity="error"
              action={(
                <Button color="inherit" size="small" onClick={() => void loadDrafts()} sx={{ textTransform: 'none' }}>
                  Thử lại
                </Button>
              )}
            >
              {loadError}
            </Alert>
          )}

          {!isLoading && !loadError && drafts.length === 0 && (
            <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <EmptyState
                title="Chưa có khóa học nào"
                description="Khóa học mới, đang chờ duyệt, đã duyệt và đã xuất bản sẽ xuất hiện tại đây."
                actionLabel="Tạo bản nháp đầu tiên"
                onAction={() => navigate(ROUTES.TEACHER.COURSE_CREATE)}
              />
            </Box>
          )}

          {!isLoading && !loadError && drafts.length > 0 && filteredDrafts.length === 0 && (
            <DraftNoResultsState onClear={clearFilters} />
          )}

          {!isLoading && !loadError && filteredDrafts.length > 0 && (
            <Stack spacing={2}>
              <Stack divider={<Divider flexItem />} spacing={0}>
                {pagedDrafts.map((course) => (
                  <CourseDraftRow
                    key={course.id}
                    categoryName={categoryNames.get(course.category)}
                    course={course}
                    deleting={deletingId === course.id}
                    highlighted={course.id === draftState?.draftId}
                    publishing={publishingId === course.id}
                    submitting={submittingId === course.id}
                    onBuild={() => buildCourseContent(course)}
                    onConfigureFinalTest={() => navigate(`/teacher/courses/${course.id}/final-test`)}
                    onDelete={() => void deleteDraft(course)}
                    onEdit={() => editDraft(course)}
                    onPublish={() => setPublishCandidate(course)}
                    onUnpublish={() => setUnpublishCandidate(course)}
                    onSubmit={() => void submitDraft(course)}
                    onView={() => navigate(ROUTES.PUBLIC.COURSE_DETAIL.replace(':id', course.slug || course.id))}
                    onAnalytics={() => setAnalyticsCourse({ id: course.id, title: displayDraftTitle(course) })}
                  />
                ))}
              </Stack>

              {pageCount > 1 && (
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center', justifyContent: 'space-between', pt: 1 }}>
                  <Typography variant="body2" color="text.secondary">
                    Hiển thị {pagedDrafts.length} trong {filteredDrafts.length} khóa học
                  </Typography>
                  <Pagination
                    color="primary"
                    count={pageCount}
                    page={currentPage}
                    onChange={(_, page) => setCurrentPage(page)}
                    shape="rounded"
                  />
                </Stack>
              )}
            </Stack>
          )}
        </Stack>
      </Paper>

      <Dialog
        open={Boolean(publishCandidate)}
        onClose={() => {
          if (!publishingId) {
            setPublishCandidate(null);
          }
        }}
        maxWidth="sm"
        fullWidth
        aria-labelledby="publish-course-dialog-title"
      >
        <DialogTitle
          id="publish-course-dialog-title"
          component="div"
          sx={{ borderBottom: '1px solid', borderColor: 'divider', px: 3, py: 2.5 }}
        >
          <Stack direction="row" spacing={1.75} sx={{ alignItems: 'center' }}>
            <Box
              sx={{
                alignItems: 'center',
                bgcolor: 'success.light',
                borderRadius: 2,
                color: 'success.dark',
                display: 'flex',
                height: 44,
                justifyContent: 'center',
                width: 44,
              }}
            >
              <PublishIcon />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Xuất bản khóa học?
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Khóa học sẽ được hiển thị công khai trên ManabiHub.
              </Typography>
            </Box>
          </Stack>
        </DialogTitle>

        <DialogContent sx={{ px: 3, py: '24px !important' }}>
          <Stack spacing={2}>
            <Box
              sx={{
                bgcolor: 'action.hover',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                px: 2,
                py: 1.75,
              }}
            >
              <Typography variant="caption" color="text.secondary">
                Khóa học được xuất bản
              </Typography>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mt: 0.25 }}>
                {publishCandidate ? displayDraftTitle(publishCandidate) : ''}
              </Typography>
            </Box>

            <Typography variant="body2" color="text.secondary">
              Trạng thái khóa học sẽ chuyển từ <strong>Đã duyệt</strong> sang{' '}
              <strong>Đã xuất bản</strong>. Học viên có thể tìm kiếm và xem thông tin
              khóa học ngay sau khi xuất bản.
            </Typography>

            <Alert severity="info">
              Vui lòng kiểm tra lần cuối ảnh bìa, học phí và nội dung khóa học trước khi tiếp tục.
            </Alert>
          </Stack>
        </DialogContent>

        <DialogActions sx={{ borderTop: '1px solid', borderColor: 'divider', gap: 1, px: 3, py: 2 }}>
          <Button
            color="inherit"
            disabled={Boolean(publishingId)}
            onClick={() => setPublishCandidate(null)}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Để sau
          </Button>
          <Button
            variant="contained"
            color="success"
            disabled={Boolean(publishingId)}
            onClick={() => void publishApprovedCourse()}
            startIcon={publishingId ? <CircularProgress color="inherit" size={16} /> : <PublishIcon />}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            {publishingId ? 'Đang xuất bản...' : 'Xuất bản ngay'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(unpublishCandidate)}
        onClose={() => {
          if (!unpublishingId) {
            setUnpublishCandidate(null);
          }
        }}
        maxWidth="sm"
        fullWidth
        aria-labelledby="unpublish-course-dialog-title"
      >
        <DialogTitle id="unpublish-course-dialog-title" sx={{ fontWeight: 800 }}>
          Ẩn khóa học để chỉnh sửa?
        </DialogTitle>
        <DialogContent>
          <Stack spacing={1.5}>
            <Typography variant="body1" sx={{ fontWeight: 700 }}>
              {unpublishCandidate ? displayDraftTitle(unpublishCandidate) : ''}
            </Typography>
            <Alert severity="info">
              Khóa học sẽ tạm thời không xuất hiện trong danh mục. Học viên đã ghi danh vẫn học nguyên phiên bản đã duyệt; các thay đổi của bạn chỉ được chuyển tới học viên sau khi Course Manager phê duyệt. Sau đó, bạn có thể xuất bản lại khóa học trên danh mục.
            </Alert>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ gap: 1, px: 3, py: 2 }}>
          <Button
            color="inherit"
            disabled={Boolean(unpublishingId)}
            onClick={() => setUnpublishCandidate(null)}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Hủy
          </Button>
          <Button
            variant="contained"
            color="warning"
            disabled={Boolean(unpublishingId)}
            onClick={() => void unpublishPublishedCourse()}
            startIcon={unpublishingId ? <CircularProgress color="inherit" size={16} /> : <VisibilityOffOutlinedIcon />}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            {unpublishingId ? 'Đang ẩn...' : 'Ẩn & chỉnh sửa'}
          </Button>
        </DialogActions>
      </Dialog>
      
      <CourseAnalyticsDialog
        courseId={analyticsCourse?.id ?? null}
        courseTitle={analyticsCourse?.title ?? null}
        onClose={() => setAnalyticsCourse(null)}
      />
    </Box>
  );
}

interface CourseDraftRowProps {
  categoryName?: string;
  course: CourseDraftResponse;
  deleting: boolean;
  highlighted: boolean;
  onBuild: () => void;
  onConfigureFinalTest: () => void;
  onDelete: () => void;
  onEdit: () => void;
  onPublish: () => void;
  onUnpublish: () => void;
  onView: () => void;
  onAnalytics: () => void;
  publishing: boolean;
  submitting: boolean;
  onSubmit: () => void;
}

function CourseDraftRow({
  categoryName,
  course,
  deleting,
  highlighted,
  publishing,
  submitting,
  onBuild,
  onConfigureFinalTest,
  onDelete,
  onEdit,
  onPublish,
  onUnpublish,
  onSubmit,
  onView,
  onAnalytics,
}: CourseDraftRowProps) {
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const thumbnailSrc = resolveAssetUrl(course.thumbnailUrl);
  const summary = toPlainText(course.introduction) || 'Chưa có mô tả ngắn cho bản nháp này.';
  const title = displayDraftTitle(course);
  const menuOpen = Boolean(menuAnchor);

  function closeMenu() {
    setMenuAnchor(null);
  }

  function handleDelete() {
    closeMenu();
    onDelete();
  }

  return (
    <Box
      sx={{
        bgcolor: highlighted ? 'action.hover' : 'transparent',
        borderLeft: '4px solid',
        borderLeftColor: highlighted ? 'primary.main' : 'transparent',
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', md: '168px minmax(0, 1fr)' },
        px: { xs: 0, md: 1.5 },
        py: 2.5,
      }}
    >
      <Box
        sx={{
          aspectRatio: '16 / 9',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          minHeight: { xs: 170, md: 94 },
          overflow: 'hidden',
        }}
      >
        {thumbnailSrc ? (
          <Box
            component="img"
            src={thumbnailSrc}
            alt={title}
            sx={{ height: '100%', objectFit: 'cover', width: '100%' }}
          />
        ) : (
          <CourseCoverPlaceholder />
        )}
      </Box>

      <Stack spacing={1.25} sx={{ minWidth: 0 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1}
          sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' } }}
        >
          <Typography
            variant="subtitle1"
            title={title}
            sx={{
              fontWeight: 800,
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {title}
          </Typography>
          <Chip
            color={courseStatusColor(course.status)}
            label={courseStatusLabel(course.status)}
            size="small"
          />
        </Stack>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            display: '-webkit-box',
            minHeight: 40,
            overflow: 'hidden',
            WebkitBoxOrient: 'vertical',
            WebkitLineClamp: 2,
          }}
        >
          {summary}
        </Typography>

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
          <Chip color="primary" label={course.jlptLevel} size="small" variant="outlined" />
          <Chip label={categoryName || course.category} size="small" variant="outlined" />
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {formatPrice(course.price)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Cập nhật lúc {formatDate(course.updatedAt || course.createdAt)}
          </Typography>
        </Stack>

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'flex-end', pt: 0.5 }}>
          {isEditableCourse(course.status) && (
            <>
              <Button
                variant="outlined"
                size="small"
                startIcon={<EditOutlinedIcon />}
                onClick={onEdit}
                sx={{ textTransform: 'none', fontWeight: 700 }}
              >
                Tiếp tục soạn
              </Button>
              <Button
                variant="contained"
                color="secondary"
                size="small"
                startIcon={<MenuBookIcon />}
                onClick={onConfigureFinalTest}
                sx={{ textTransform: 'none', fontWeight: 700 }}
              >
                Cấu hình Final Test
              </Button>
              <Button
                variant="outlined"
                size="small"
                startIcon={<ViewModuleIcon />}
                onClick={onBuild}
                sx={{ textTransform: 'none', fontWeight: 700 }}
              >
                Xây nội dung
              </Button>
              <Tooltip
                title="Khóa học phải đạt toàn bộ checklist: tối thiểu 5 bài học, 30 phút nội dung và Final Test hợp lệ."
                placement="top"
              >
                <span>
                  <Button
                    variant="contained"
                    size="small"
                    startIcon={submitting ? <CircularProgress color="inherit" size={16} /> : <SendOutlinedIcon />}
                    disabled={deleting || submitting}
                    onClick={onSubmit}
                    sx={{ textTransform: 'none', fontWeight: 700 }}
                  >
                    {submitting ? 'Đang gửi...' : 'Gửi duyệt'}
                  </Button>
                </span>
              </Tooltip>
              <Tooltip title="Tác vụ khác">
                <IconButton
                  aria-controls={menuOpen ? `course-draft-${course.id}-menu` : undefined}
                  aria-haspopup="menu"
                  aria-expanded={menuOpen ? 'true' : undefined}
                  onClick={(event) => setMenuAnchor(event.currentTarget)}
                  size="small"
                  sx={{ border: '1px solid', borderColor: 'divider' }}
                >
                  <MoreHorizIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
          {course.status === 'APPROVED' && (
            <Button
              variant="contained"
              color="success"
              size="small"
              startIcon={publishing ? <CircularProgress color="inherit" size={16} /> : <PublishIcon />}
              disabled={publishing}
              onClick={onPublish}
              sx={{ textTransform: 'none', fontWeight: 700 }}
            >
              {publishing ? 'Đang xuất bản...' : 'Xuất bản khóa học'}
            </Button>
          )}
          {course.status === 'PUBLISHED' && (
            <>
              <Button
                variant="outlined"
                color="warning"
                size="small"
                onClick={onUnpublish}
                sx={{ textTransform: 'none', fontWeight: 700 }}
                startIcon={<VisibilityOffOutlinedIcon />}
              >
                Ẩn & chỉnh sửa
              </Button>
              <Button
                variant="outlined"
                color="info"
                size="small"
                onClick={onAnalytics}
                sx={{ textTransform: 'none', fontWeight: 700 }}
                startIcon={<AssessmentIcon />}
              >
                Doanh thu & Analytics
              </Button>
              <Button
                variant="outlined"
                color="success"
                size="small"
                onClick={onView}
                sx={{ textTransform: 'none', fontWeight: 700 }}
              >
                Xem trên danh mục
              </Button>
            </>
          )}
          <Menu
            id={`course-draft-${course.id}-menu`}
            anchorEl={menuAnchor}
            open={menuOpen}
            onClose={closeMenu}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
          >
            <MenuItem disabled={deleting} onClick={handleDelete}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <DeleteIcon color="error" fontSize="small" />
                <Typography variant="body2">{deleting ? 'Đang xóa...' : 'Xóa bản nháp'}</Typography>
              </Stack>
            </MenuItem>
          </Menu>
        </Stack>
      </Stack>
    </Box>
  );
}

function CourseCoverPlaceholder() {
  return (
    <Stack
      spacing={0.75}
      sx={{
        alignItems: 'center',
        background: 'linear-gradient(135deg, #eef7ff 0%, #f7f1ff 48%, #fff6e8 100%)',
        color: 'text.secondary',
        height: '100%',
        justifyContent: 'center',
        px: 2,
        textAlign: 'center',
        width: '100%',
      }}
    >
      <MenuBookIcon color="primary" sx={{ fontSize: 34 }} />
      <Typography variant="caption" sx={{ color: 'text.primary', fontWeight: 800 }}>
        Chưa có ảnh bìa
      </Typography>
    </Stack>
  );
}



interface DraftNoResultsStateProps {
  onClear: () => void;
}

function DraftNoResultsState({ onClear }: DraftNoResultsStateProps) {
  return (
    <Stack spacing={1.5} sx={{ alignItems: 'center', py: 5, textAlign: 'center' }}>
      <SearchIcon color="primary" sx={{ fontSize: 40 }} />
      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
        Không tìm thấy khóa học phù hợp
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Thử đổi từ khóa hoặc bỏ bớt bộ lọc để xem thêm bản nháp.
      </Typography>
      <Button variant="outlined" onClick={onClear} sx={{ textTransform: 'none', fontWeight: 700 }}>
        Xóa bộ lọc
      </Button>
    </Stack>
  );
}

function isEditableCourse(status: CourseDraftResponse['status']) {
  return status === 'DRAFT' || status === 'REJECTED' || status === 'FORCED_DRAFT';
}

function displayDraftTitle(course: CourseDraftResponse) {
  if (course.title?.trim()) {
    return course.title.trim();
  }

  return `[Bản nháp] Khóa học chưa đặt tên - ${formatDate(course.createdAt)}`;
}

function formatPrice(price: number) {
  if (!price) {
    return 'Miễn phí';
  }

  return priceFormatter.format(price);
}

function formatDate(value?: string | null) {
  if (!value) {
    return 'chưa rõ thời gian';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'chưa rõ thời gian';
  }

  return dateFormatter.format(date);
}

function resolveAssetUrl(url?: string | null) {
  if (!url) {
    return undefined;
  }

  if (/^(https?:|blob:|data:)/i.test(url)) {
    return url;
  }

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api';
  const assetOrigin = apiBaseUrl.replace(/\/api\/?$/, '').replace(/\/$/, '');

  return `${assetOrigin}${url.startsWith('/') ? '' : '/'}${url}`;
}

function toPlainText(value?: string | null) {
  if (!value) {
    return '';
  }

  const document = new DOMParser().parseFromString(value, 'text/html');
  return document.body.textContent?.replace(/\s+/g, ' ').trim() || '';
}

function normalizeSearch(value: string) {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

function courseLastModifiedAt(course: CourseDraftResponse) {
  const timestamp = Date.parse(course.updatedAt || course.createdAt || '');
  return Number.isNaN(timestamp) ? 0 : timestamp;
}
