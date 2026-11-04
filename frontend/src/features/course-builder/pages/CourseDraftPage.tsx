import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormHelperText,
  IconButton,
  MenuItem,
  Paper,
  Slider,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Quill from 'quill';
import 'quill/dist/quill.snow.css';
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react';
import Cropper, { type Area } from 'react-easy-crop';
import { useLocation, useNavigate } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { ROUTES } from '../../../shared/constants/routes';
import { sanitizeRichText } from '../../../shared/security/sanitizeRichText';
import {
  type CourseCategory,
  type CourseDraftResponse,
  type JlptLevel,
  courseDraftApiError,
  createCourseDraft,
  fetchCourseCategories,
  updateCourseDraft,
  uploadCourseThumbnail,
} from '../services/courseDraftService';
import {
  LEARNING_LANGUAGES,
  defaultFramework,
  defaultLevel,
  frameworkOptions,
  levelOptions,
  type LearningLanguage,
  type ProficiencyFramework,
} from '../../../shared/learning/languageProfiles';

interface CourseDraftForm {
  title: string;
  introduction: string;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  proficiencyLevel: string;
  category: string;
  thumbnailUrl: string;
  thumbnailPreviewUrl: string;
  thumbnailFileName: string;
  outcomes: string;
  price: string;
  prerequisites: string;
  targetStudents: string;
  learningGoals: string[];
  accessDurationDays: string;
  accessExpiresAt: string;
}

interface RichTextEditorProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  error?: string;
}

interface ImageUploadProps {
  imagePreviewUrl: string;
  fileName: string;
  error?: string;
  onChange: (thumbnailUrl: string, previewUrl: string, fileName: string) => void;
  onError: (message: string) => void;
}

interface CropDraft {
  dataUrl: string;
  fileName: string;
  mimeType: string;
}

interface CourseDraftRouteState {
  draftToEdit?: CourseDraftResponse;
}

const steps = ['Thông tin chung', 'Ảnh & mô tả', 'Mục tiêu & yêu cầu'];
const maxImageSize = 5 * 1024 * 1024;
const maxGoalLength = 160;
const priceFormatter = new Intl.NumberFormat('vi-VN');
const initialForm: CourseDraftForm = {
  title: '',
  introduction: '',
  learningLanguage: 'KOREAN',
  proficiencyFramework: 'TOPIK',
  frameworkVersion: 'CURRENT',
  proficiencyLevel: '1',
  category: '',
  thumbnailUrl: '',
  thumbnailPreviewUrl: '',
  thumbnailFileName: '',
  outcomes: '',
  price: '',
  prerequisites: '',
  targetStudents: '',
  learningGoals: ['', '', '', ''],
  accessDurationDays: '180',
  accessExpiresAt: '',
};

function buildInitialForm(draft?: CourseDraftResponse): CourseDraftForm {
  if (!draft) {
    return {
      ...initialForm,
      learningGoals: [...initialForm.learningGoals],
    };
  }

  return {
    title: draft.title || '',
    introduction: sanitizeRichText(draft.introduction),
    learningLanguage: draft.learningLanguage ?? 'JAPANESE',
    proficiencyFramework: draft.proficiencyFramework ?? 'JLPT',
    frameworkVersion: draft.frameworkVersion ?? 'CURRENT',
    proficiencyLevel: draft.proficiencyLevel ?? draft.jlptLevel ?? 'N5',
    category: draft.category || '',
    thumbnailUrl: draft.thumbnailUrl || '',
    thumbnailPreviewUrl: resolveCourseAssetUrl(draft.thumbnailUrl) || '',
    thumbnailFileName: draft.thumbnailUrl ? 'Ảnh bìa hiện tại' : '',
    outcomes: sanitizeRichText(draft.outcomes),
    price: String(Number(draft.price || 0)),
    prerequisites: sanitizeRichText(draft.prerequisites),
    targetStudents: sanitizeRichText(draft.targetStudents),
    learningGoals: withMinimumGoals(draft.learningGoals),
    accessDurationDays: String(draft.accessDurationDays ?? 180),
    accessExpiresAt: draft.accessExpiresAt ? draft.accessExpiresAt.slice(0, 10) : '',
  };
}

function withMinimumGoals(goals?: string[]) {
  const normalized = [...(goals || [])];

  while (normalized.length < 4) {
    normalized.push('');
  }

  return normalized;
}

export function CourseDraftPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const editingDraft = (location.state as CourseDraftRouteState | null)?.draftToEdit;
  const isEditing = Boolean(editingDraft);
  const [activeStep, setActiveStep] = useState(0);
  const [form, setForm] = useState<CourseDraftForm>(() => buildInitialForm(editingDraft));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<CourseCategory[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [categoryLoadError, setCategoryLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    setCategoryLoading(true);
    fetchCourseCategories()
      .then((items) => {
        if (items.length === 0) {
          throw new Error('Empty category list');
        }

        setCategories(items);
        setCategoryLoadError(null);
      })
      .catch(() => {
        setCategories([]);
        setForm((current) => ({ ...current, category: '' }));
        setCategoryLoadError('Không tải được danh mục khóa học từ hệ thống. Vui lòng thử lại sau khi kết nối backend ổn định.');
      })
      .finally(() => setCategoryLoading(false));
  }, []);

  const currentStepReady = useMemo(
    () => Object.keys(collectStepErrors(activeStep, form, categoryLoadError)).length === 0,
    [activeStep, categoryLoadError, form],
  );

  function updateField(field: keyof CourseDraftForm) {
    return (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setForm((current) => ({ ...current, [field]: event.target.value }));
      clearFieldError(field);
    };
  }

  function updateLearningLanguage(event: ChangeEvent<HTMLInputElement>) {
    const learningLanguage = event.target.value as LearningLanguage;
    const proficiencyFramework = defaultFramework(learningLanguage);
    setForm((current) => ({
      ...current,
      learningLanguage,
      proficiencyFramework,
      frameworkVersion: 'CURRENT',
      proficiencyLevel: defaultLevel(proficiencyFramework),
    }));
    setErrors((current) => ({
      ...current,
      learningLanguage: '',
      proficiencyFramework: '',
      proficiencyLevel: '',
    }));
  }

  function updateProficiencyFramework(event: ChangeEvent<HTMLInputElement>) {
    const proficiencyFramework = event.target.value as ProficiencyFramework;
    setForm((current) => ({
      ...current,
      proficiencyFramework,
      frameworkVersion: proficiencyFramework === 'SOFL_INTERNAL' ? 'SOFL' : 'CURRENT',
      proficiencyLevel: defaultLevel(proficiencyFramework),
    }));
    setErrors((current) => ({ ...current, proficiencyFramework: '', proficiencyLevel: '' }));
  }

  function updatePrice(event: ChangeEvent<HTMLInputElement>) {
    setForm((current) => ({ ...current, price: normalizePriceInput(event.target.value) }));
    clearFieldError('price');
  }

  function preventInvalidPriceKey(event: KeyboardEvent<HTMLInputElement>) {
    const allowedKeys = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'Enter'];
    const isShortcut = event.ctrlKey || event.metaKey;

    if (isShortcut || allowedKeys.includes(event.key) || /^\d$/.test(event.key)) {
      return;
    }

    event.preventDefault();
  }

  function updateRichText(field: 'introduction' | 'outcomes' | 'prerequisites' | 'targetStudents', value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    clearFieldError(field);
  }

  function updateGoal(index: number, value: string) {
    const nextValue = value.slice(0, maxGoalLength);

    setForm((current) => {
      const learningGoals = [...current.learningGoals];
      learningGoals[index] = nextValue;
      return { ...current, learningGoals };
    });
    clearGoalErrors(index);
  }

  function addGoal() {
    setForm((current) => ({ ...current, learningGoals: [...current.learningGoals, ''] }));
  }

  function removeGoal(index: number) {
    if (form.learningGoals.length <= 4) {
      return;
    }

    setForm((current) => ({
      ...current,
      learningGoals: current.learningGoals.filter((_, currentIndex) => currentIndex !== index),
    }));
    clearAllGoalErrors();
  }

  function setThumbnail(thumbnailUrl: string, previewUrl: string, fileName: string) {
    setForm((current) => ({
      ...current,
      thumbnailUrl,
      thumbnailPreviewUrl: previewUrl,
      thumbnailFileName: fileName,
    }));
    clearFieldError('thumbnailUrl');
  }

  function clearFieldError(field: string) {
    setErrors((current) => ({ ...current, [field]: '' }));
    setSubmitError(null);
  }

  function clearGoalErrors(index: number) {
    setErrors((current) => ({ ...current, learningGoals: '', [`goal-${index}`]: '' }));
    setSubmitError(null);
  }

  function clearAllGoalErrors() {
    setErrors((current) => {
      const next: Record<string, string> = { ...current, learningGoals: '' };
      Object.keys(next).forEach((key) => {
        if (key.startsWith('goal-')) {
          delete next[key];
        }
      });
      return next;
    });
  }

  function validateStep(step: number) {
    const nextErrors = collectStepErrors(step, form, categoryLoadError);
    setErrors((current) => ({ ...current, ...clearStepErrors(step, form.learningGoals), ...nextErrors }));
    return Object.keys(nextErrors).length === 0;
  }

  function validateAll() {
    const allErrors = {
      ...collectStepErrors(0, form, categoryLoadError),
      ...collectStepErrors(1, form, categoryLoadError),
      ...collectStepErrors(2, form, categoryLoadError),
    };

    setErrors((current) => ({
      ...current,
      ...clearStepErrors(0, form.learningGoals),
      ...clearStepErrors(1, form.learningGoals),
      ...clearStepErrors(2, form.learningGoals),
      ...allErrors,
    }));

    const firstInvalidStep = [0, 1, 2].find((step) => Object.keys(collectStepErrors(step, form, categoryLoadError)).length > 0);
    if (firstInvalidStep !== undefined) {
      setActiveStep(firstInvalidStep);
      return false;
    }

    return true;
  }

  function handleNext() {
    if (validateStep(activeStep)) {
      setSubmitError(null);
      setActiveStep((current) => Math.min(current + 1, steps.length - 1));
    }
  }

  function handleBack() {
    setSubmitError(null);
    setActiveStep((current) => Math.max(current - 1, 0));
  }

  async function handleSubmit() {
    setSubmitError(null);

    if (!validateAll()) {
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title: form.title.trim(),
        introduction: sanitizeRichText(form.introduction),
        jlptLevel: form.learningLanguage === 'JAPANESE' && form.proficiencyFramework === 'JLPT'
          ? form.proficiencyLevel as JlptLevel
          : null,
        learningLanguage: form.learningLanguage,
        proficiencyFramework: form.proficiencyFramework,
        frameworkVersion: form.frameworkVersion.trim(),
        proficiencyLevel: form.proficiencyLevel.trim(),
        category: form.category,
        thumbnailUrl: form.thumbnailUrl || null,
        outcomes: sanitizeRichText(form.outcomes),
        price: Number(form.price),
        prerequisites: sanitizeRichText(form.prerequisites),
        targetStudents: sanitizeRichText(form.targetStudents),
        learningGoals: form.learningGoals.map((goal) => goal.trim()).filter(Boolean),
        accessDurationDays: Number(form.accessDurationDays || 180),
        accessExpiresAt: form.accessExpiresAt ? `${form.accessExpiresAt}T23:59:59Z` : null,
      };
      const draft = editingDraft
        ? await updateCourseDraft(editingDraft.id, payload)
        : await createCourseDraft(payload);

      navigate(ROUTES.TEACHER.COURSES, {
        state: {
          draftSaved: true,
          draftId: draft.id,
          draftTitle: draft.title,
        },
      });
    } catch (error) {
      const apiError = courseDraftApiError(error);
      applyApiError(apiError.messageCode, apiError.message);
    } finally {
      setSaving(false);
    }
  }

  function applyApiError(messageCode: string | undefined, message: string) {
    if (messageCode === 'MSG-GOAL-001') {
      setErrors((current) => ({ ...current, learningGoals: 'Cần tối thiểu 4 mục tiêu học tập hợp lệ.' }));
      setActiveStep(2);
      return;
    }
    if (messageCode === 'MSG-GOAL-002') {
      setErrors((current) => ({ ...current, learningGoals: 'Mỗi mục tiêu không được vượt quá 160 ký tự.' }));
      setActiveStep(2);
      return;
    }
    if (messageCode === 'MSG-GOAL-003') {
      setErrors((current) => ({ ...current, prerequisites: 'Vui lòng nhập yêu cầu đầu vào.' }));
      setActiveStep(2);
      return;
    }
    if (messageCode === 'MSG-GOAL-004') {
      setErrors((current) => ({ ...current, targetStudents: 'Vui lòng nhập đối tượng học viên phù hợp.' }));
      setActiveStep(2);
      return;
    }
    if (messageCode === 'MSG-COURSE-002') {
      setErrors((current) => ({ ...current, title: 'Tên khóa học không hợp lệ.' }));
      setActiveStep(0);
      return;
    }
    if (messageCode === 'MSG-COURSE-003') {
      setErrors((current) => ({ ...current, price: 'Giá khóa học không hợp lệ.' }));
      setActiveStep(0);
      return;
    }
    if (messageCode === 'MSG-COURSE-004') {
      setErrors((current) => ({ ...current, category: 'Danh mục khóa học không hợp lệ.' }));
      setActiveStep(0);
      return;
    }
    if (messageCode === 'MSG-COURSE-005') {
      setErrors((current) => ({ ...current, thumbnailUrl: 'Ảnh đại diện khóa học không hợp lệ hoặc tải lên thất bại.' }));
      setActiveStep(1);
      return;
    }
    if (messageCode === 'MSG-KYC-010') {
      setSubmitError('Tài khoản giáo viên cần được duyệt KYC trước khi tạo khóa học.');
      return;
    }

    setSubmitError(message);
  }

  return (
    <Box>
      <PageHeader
        title={isEditing ? 'Tiếp tục soạn khóa học' : 'Tạo khóa học nháp'}
        breadcrumbs={[
          { label: 'Giảng viên' },
          { label: 'Khóa học của tôi', href: ROUTES.TEACHER.COURSES },
          { label: isEditing ? 'Tiếp tục soạn' : 'Tạo bản nháp' },
        ]}
        action={(
          <Button
            variant="outlined"
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate(ROUTES.TEACHER.COURSES)}
            sx={{ textTransform: 'none', fontWeight: 700 }}
          >
            Quay lại
          </Button>
        )}
      />

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          p: { xs: 2, md: 4 },
          pb: { xs: 4, md: 5 },
        }}
      >
        <Stack spacing={3}>
          <Stepper activeStep={activeStep} alternativeLabel>
            {steps.map((step) => (
              <Step key={step}>
                <StepLabel>{step}</StepLabel>
              </Step>
            ))}
          </Stepper>

          {activeStep === 0 && categoryLoadError && <Alert severity="error">{categoryLoadError}</Alert>}
          {submitError && <Alert severity="error">{submitError}</Alert>}

          <Divider />

          {activeStep === 0 && (
            <Stack spacing={2.5}>
              <TextField
                fullWidth
                label="Tên khóa học"
                placeholder="Ví dụ: JLPT N5 nền tảng cho người mới bắt đầu"
                value={form.title}
                onChange={updateField('title')}
                error={Boolean(errors.title)}
                helperText={errors.title || 'Có thể để trống khi lưu nháp; hệ thống sẽ tự đặt tên theo ngày tạo.'}
              />

              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField
                  required
                  select
                  label="Ngôn ngữ học"
                  value={form.learningLanguage}
                  onChange={updateLearningLanguage}
                  sx={{ minWidth: { xs: '100%', md: 180 } }}
                  helperText="Tách biệt với ngôn ngữ hiển thị của ứng dụng."
                >
                  {LEARNING_LANGUAGES.map((language) => (
                    <MenuItem key={language.value} value={language.value}>
                      {language.label}
                    </MenuItem>
                  ))}
                </TextField>

                <TextField
                  required
                  select
                  label="Khung trình độ"
                  value={form.proficiencyFramework}
                  onChange={updateProficiencyFramework}
                  sx={{ minWidth: { xs: '100%', md: 180 } }}
                >
                  {frameworkOptions(form.learningLanguage).map((framework) => (
                    <MenuItem key={framework.value} value={framework.value}>{framework.label}</MenuItem>
                  ))}
                </TextField>

                {form.proficiencyFramework === 'SOFL_INTERNAL' ? (
                  <TextField
                    required
                    label="Mã cấp độ nội bộ"
                    value={form.proficiencyLevel}
                    onChange={updateField('proficiencyLevel')}
                    error={Boolean(errors.proficiencyLevel)}
                    helperText={errors.proficiencyLevel || 'Ví dụ: SO_CAP_1 hoặc GIAO_TIEP_1.'}
                    sx={{ minWidth: { xs: '100%', md: 190 } }}
                  />
                ) : (
                  <TextField
                    required
                    select
                    label="Cấp độ"
                    value={form.proficiencyLevel}
                    onChange={updateField('proficiencyLevel')}
                    error={Boolean(errors.proficiencyLevel)}
                    sx={{ minWidth: { xs: '100%', md: 150 } }}
                  >
                    {levelOptions(form.proficiencyFramework).map((level) => (
                      <MenuItem key={level.value} value={level.value}>{level.label}</MenuItem>
                    ))}
                  </TextField>
                )}

                <TextField
                  required
                  label="Phiên bản"
                  value={form.frameworkVersion}
                  onChange={updateField('frameworkVersion')}
                  error={Boolean(errors.frameworkVersion)}
                  helperText={errors.frameworkVersion || 'Ví dụ: CURRENT hoặc SOFL.'}
                  sx={{ minWidth: { xs: '100%', md: 150 } }}
                />
              </Stack>

              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField
                  fullWidth
                  required
                  select
                  label="Danh mục"
                  value={form.category}
                  onChange={updateField('category')}
                  disabled={categoryLoading || Boolean(categoryLoadError)}
                  error={Boolean(errors.category)}
                  helperText={
                    errors.category
                    || (categoryLoading ? 'Đang tải danh mục chuẩn từ hệ thống...' : 'Chọn một danh mục chuẩn để khóa học được lọc và tìm kiếm chính xác.')
                  }
                >
                  <MenuItem value="" disabled>
                    Chọn danh mục khóa học
                  </MenuItem>
                  {categories.map((category) => (
                    <MenuItem key={category.code} value={category.code}>
                      {category.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>

              <Box sx={{ maxWidth: 420 }}>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 76px' }}>
                  <TextField
                    required
                    label="Giá khóa học"
                    placeholder="Ví dụ: 1.000.000"
                    value={formatPriceInput(form.price)}
                    onChange={updatePrice}
                    onKeyDown={preventInvalidPriceKey}
                    error={Boolean(errors.price)}
                    sx={{
                      '& .MuiOutlinedInput-root': {
                        borderTopRightRadius: 0,
                        borderBottomRightRadius: 0,
                        '& .MuiOutlinedInput-notchedOutline': { borderRightWidth: 0 },
                      },
                      '& .MuiInputBase-input': {
                        color: 'text.primary',
                        fontVariantNumeric: 'tabular-nums',
                      },
                    }}
                    slotProps={{
                      htmlInput: {
                        inputMode: 'numeric',
                        pattern: '[0-9]*',
                        'aria-label': 'Giá khóa học bằng VND',
                      },
                    }}
                  />
                  <Box
                    sx={{
                      border: '1px solid',
                      borderColor: errors.price ? 'error.main' : 'divider',
                      borderTopRightRadius: 4,
                      borderBottomRightRadius: 4,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      bgcolor: 'grey.50',
                      color: 'text.secondary',
                      fontWeight: 700,
                    }}
                  >
                    VND
                  </Box>
                </Box>
                <FormHelperText error={Boolean(errors.price)}>
                  {errors.price || 'Nhập 0 nếu đây là khóa học miễn phí.'}
                </FormHelperText>
              </Box>
            </Stack>
          )}

          {activeStep === 1 && (
            <Stack spacing={2.5}>
              <ImageUpload
                imagePreviewUrl={form.thumbnailPreviewUrl}
                fileName={form.thumbnailFileName}
                error={errors.thumbnailUrl}
                onChange={setThumbnail}
                onError={(message) => setErrors((current) => ({ ...current, thumbnailUrl: message }))}
              />

              <RichTextEditor
                label="Giới thiệu khóa học"
                value={form.introduction}
                onChange={(value) => updateRichText('introduction', value)}
                placeholder="Mô tả điểm nổi bật nhất của khóa học, ví dụ: Nắm vững 50 cấu trúc ngữ pháp N5 trong 30 ngày qua video ngắn và bài luyện tập sau mỗi chủ đề."
                error={errors.introduction}
              />

              <RichTextEditor
                label="Kết quả học viên đạt được"
                value={form.outcomes}
                onChange={(value) => updateRichText('outcomes', value)}
                placeholder="Ví dụ: Sau khóa học, học viên có thể đọc hiểu đoạn văn N5, dùng mẫu câu cơ bản và tự tin bước vào luyện đề."
                error={errors.outcomes}
              />
            </Stack>
          )}

          {activeStep === 2 && (
            <Stack spacing={2.5}>
              <RichTextEditor
                label="Yêu cầu đầu vào"
                value={form.prerequisites}
                onChange={(value) => updateRichText('prerequisites', value)}
                placeholder="Ví dụ: Không yêu cầu đầu vào, chỉ cần học viên biết sử dụng máy tính và dành 30 phút mỗi ngày để luyện tập."
                error={errors.prerequisites}
              />

              <RichTextEditor
                label="Đối tượng học viên phù hợp"
                value={form.targetStudents}
                onChange={(value) => updateRichText('targetStudents', value)}
                placeholder="Ví dụ: Người mới bắt đầu học tiếng Nhật, sinh viên chuẩn bị thi JLPT N5 hoặc người cần nền tảng giao tiếp cơ bản."
                error={errors.targetStudents}
              />

              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Thời hạn truy cập khóa học
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Học viên được truy cập theo số ngày kể từ khi ghi danh; có thể đặt hạn cố định cho khóa luyện thi.
                </Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField
                    fullWidth
                    type="number"
                    label="Số ngày truy cập"
                    value={form.accessDurationDays}
                    onChange={(event) => updateField('accessDurationDays')(event)}
                    slotProps={{ htmlInput: { min: 1 } }}
                    error={Boolean(errors.accessDurationDays)}
                    helperText={errors.accessDurationDays || 'Mặc định 180 ngày'}
                  />
                  <TextField
                    fullWidth
                    type="date"
                    label="Hạn cố định (tùy chọn)"
                    value={form.accessExpiresAt}
                    onChange={(event) => updateField('accessExpiresAt')(event)}
                    slotProps={{ inputLabel: { shrink: true } }}
                    error={Boolean(errors.accessExpiresAt)}
                    helperText={errors.accessExpiresAt || 'Để trống nếu dùng số ngày'}
                  />
                </Stack>
              </Paper>

              <Box>
                <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Mục tiêu học tập
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Cần tối thiểu 4 mục tiêu, mỗi mục tiêu tối đa 160 ký tự.
                    </Typography>
                  </Box>
                  <Button variant="outlined" startIcon={<AddIcon />} onClick={addGoal} sx={{ textTransform: 'none', fontWeight: 700 }}>
                    Thêm mục tiêu
                  </Button>
                </Stack>

                <Stack spacing={1.5}>
                  {form.learningGoals.map((goal, index) => {
                    const charCount = goal.trim().length;
                    const atLimit = goal.length >= maxGoalLength;

                    return (
                      <Stack key={index} direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                        <TextField
                          fullWidth
                          required={index < 4}
                          label={`Mục tiêu ${index + 1}`}
                          placeholder="Ví dụ: Hiểu và áp dụng mẫu câu ～たいです trong hội thoại hằng ngày"
                          value={goal}
                          onChange={(event) => updateGoal(index, event.target.value)}
                          error={Boolean(errors[`goal-${index}`])}
                          helperText={errors[`goal-${index}`] || ' '}
                          sx={{
                            '& .MuiInputBase-input': {
                              color: 'text.primary',
                              pr: 8,
                            },
                          }}
                          slotProps={{
                            htmlInput: {
                              maxLength: maxGoalLength,
                            },
                            input: {
                              endAdornment: (
                                <Typography
                                  variant="caption"
                                  sx={{
                                    color: atLimit ? 'error.main' : 'text.secondary',
                                    fontVariantNumeric: 'tabular-nums',
                                    whiteSpace: 'nowrap',
                                  }}
                                >
                                  {charCount}/{maxGoalLength}
                                </Typography>
                              ),
                            },
                          }}
                        />
                        {form.learningGoals.length > 4 && (
                          index >= 4 ? (
                            <Tooltip title="Xóa mục tiêu">
                              <IconButton aria-label="Xóa mục tiêu" onClick={() => removeGoal(index)} sx={{ mt: 1 }}>
                                <DeleteIcon />
                              </IconButton>
                            </Tooltip>
                          ) : (
                            <Box sx={{ width: 40, flexShrink: 0 }} />
                          )
                        )}
                      </Stack>
                    );
                  })}
                </Stack>

                {errors.learningGoals && <FormHelperText error>{errors.learningGoals}</FormHelperText>}
              </Box>
            </Stack>
          )}

          <Divider />

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between' }}>
            <Button
              variant="outlined"
              startIcon={<ChevronLeftIcon />}
              disabled={activeStep === 0}
              onClick={handleBack}
              sx={{ textTransform: 'none', fontWeight: 700 }}
            >
              Bước trước
            </Button>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              {activeStep < steps.length - 1 ? (
                <Button
                  variant="contained"
                  endIcon={<ChevronRightIcon />}
                  disabled={!currentStepReady}
                  onClick={handleNext}
                  sx={{ textTransform: 'none', fontWeight: 700 }}
                >
                  Tiếp tục
                </Button>
              ) : (
                <Button
                  variant="contained"
                  size="large"
                  startIcon={<SaveOutlinedIcon />}
                  disabled={saving || !currentStepReady}
                  onClick={handleSubmit}
                  sx={{ minWidth: 180, textTransform: 'none', fontWeight: 700 }}
                >
                  {saving ? 'Đang lưu...' : (isEditing ? 'Cập nhật bản nháp' : 'Lưu bản nháp')}
                </Button>
              )}
            </Stack>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
}

function RichTextEditor({ label, value, onChange, placeholder, error }: RichTextEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const quillRef = useRef<Quill | null>(null);
  const onChangeRef = useRef(onChange);
  const initialValueRef = useRef(value);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || quillRef.current) {
      return;
    }

    const editorElement = document.createElement('div');
    container.appendChild(editorElement);

    const quill = new Quill(editorElement, {
      theme: 'snow',
      placeholder,
      modules: {
        toolbar: [
          ['bold', 'italic'],
          [{ list: 'bullet' }, { list: 'ordered' }],
          ['clean'],
        ],
      },
    });

    quill.clipboard.dangerouslyPasteHTML(sanitizeRichText(initialValueRef.current));
    const handleTextChange = () => {
      const html = sanitizeRichText(quill.root.innerHTML);
      onChangeRef.current(stripHtml(html).length === 0 ? '' : html);
    };

    quill.on('text-change', handleTextChange);
    quillRef.current = quill;

    return () => {
      quill.off('text-change', handleTextChange);
      quillRef.current = null;
      container.replaceChildren();
    };
  }, [placeholder]);

  useEffect(() => {
    const quill = quillRef.current;
    const sanitizedValue = sanitizeRichText(value);
    if (
      !quill
      || document.activeElement === quill.root
      || quill.root.innerHTML === sanitizedValue
    ) {
      return;
    }

    quill.clipboard.dangerouslyPasteHTML(sanitizedValue);
  }, [value]);

  return (
    <Box>
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mb: 0.75 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          {label}
        </Typography>
        <Tooltip title={placeholder}>
          <InfoOutlinedIcon color="action" fontSize="small" />
        </Tooltip>
      </Stack>

      <Box
        sx={{
          border: '1px solid',
          borderColor: error ? 'error.main' : 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
          overflow: 'hidden',
          transition: 'border-color 160ms ease, box-shadow 160ms ease',
          '&:focus-within': {
            borderColor: error ? 'error.main' : 'primary.main',
            boxShadow: error ? '0 0 0 1px rgba(211, 47, 47, 0.18)' : '0 0 0 1px rgba(85, 74, 240, 0.18)',
          },
          '& .ql-toolbar.ql-snow': {
            bgcolor: 'grey.50',
            border: '0 !important',
            borderBottom: '1px solid',
            borderColor: 'divider',
            px: 1,
            py: 0.75,
          },
          '& .ql-container.ql-snow': {
            border: '0 !important',
            fontFamily: 'inherit',
            minHeight: 168,
          },
          '& .ql-editor': {
            color: 'text.primary',
            fontSize: 15,
            minHeight: 168,
          },
          '& .ql-editor.ql-blank::before': {
            color: 'text.secondary',
            fontStyle: 'normal',
          },
        }}
      >
        <Box ref={containerRef} />
      </Box>

      {error && <FormHelperText error>{error}</FormHelperText>}
    </Box>
  );
}

function ImageUpload({ imagePreviewUrl, fileName, error, onChange, onError }: ImageUploadProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [cropDraft, setCropDraft] = useState<CropDraft | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  async function handleFile(file: File | undefined) {
    if (!file) {
      return;
    }

    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      onError('Ảnh đại diện chỉ hỗ trợ PNG hoặc JPG.');
      return;
    }

    if (file.size > maxImageSize) {
      onError('Dung lượng ảnh không được vượt quá 5MB.');
      return;
    }

    const dataUrl = await readFileAsDataUrl(file);
    setCropDraft({ dataUrl, fileName: file.name, mimeType: file.type });
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setCroppedAreaPixels(null);
    setUploadError(null);
  }

  function handleCropComplete(_area: Area, areaPixels: Area) {
    setCroppedAreaPixels(areaPixels);
  }

  async function confirmCrop() {
    if (!cropDraft || !croppedAreaPixels) {
      setUploadError('Vui lòng chọn vùng ảnh 16:9 trước khi lưu.');
      return;
    }

    setUploading(true);
    setUploadError(null);
    try {
      const croppedBlob = await cropImageToBlob(cropDraft.dataUrl, croppedAreaPixels, cropDraft.mimeType);
      const croppedFile = new File([croppedBlob], normalizeImageFileName(cropDraft.fileName, cropDraft.mimeType), {
        type: cropDraft.mimeType,
      });
      const previewUrl = await blobToDataUrl(croppedBlob);
      const uploaded = await uploadCourseThumbnail(croppedFile);

      onChange(uploaded.publicUrl, previewUrl, uploaded.fileName);
      setCropDraft(null);
    } catch {
      setUploadError('Không thể tải ảnh lên hệ thống. Vui lòng thử lại sau khi backend hoạt động ổn định.');
      onError('Không thể tải ảnh lên hệ thống. Vui lòng thử lại sau khi backend hoạt động ổn định.');
    } finally {
      setUploading(false);
    }
  }

  return (
    <Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.75 }}>
        Ảnh bìa khóa học
      </Typography>

      <Paper
        variant="outlined"
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          void handleFile(event.dataTransfer.files[0]);
        }}
        sx={{
          borderColor: error ? 'error.main' : 'divider',
          borderStyle: 'dashed',
          p: 2,
          minHeight: 190,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'grey.50',
        }}
      >
        {imagePreviewUrl ? (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'center', width: '100%' }}>
            <Box
              component="img"
              src={imagePreviewUrl}
              alt="Ảnh đại diện khóa học"
              sx={{
                width: { xs: '100%', sm: 240 },
                aspectRatio: '16 / 9',
                objectFit: 'cover',
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'divider',
              }}
            />
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography sx={{ fontWeight: 700 }} title={fileName}>
                {truncateFileName(fileName)}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                Ảnh đã được cắt theo tỷ lệ 16:9 trước khi lưu vào bản nháp.
              </Typography>
              <Button variant="outlined" startIcon={<CloudUploadIcon />} onClick={() => inputRef.current?.click()} sx={{ textTransform: 'none', fontWeight: 700 }}>
                Chọn ảnh khác
              </Button>
            </Box>
          </Stack>
        ) : (
          <Stack spacing={1.25} sx={{ alignItems: 'center', textAlign: 'center' }}>
            <Box
              sx={{
                alignItems: 'center',
                aspectRatio: '16 / 9',
                background: 'linear-gradient(135deg, #eef7ff 0%, #f7f1ff 48%, #fff6e8 100%)',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                maxWidth: 280,
                px: 2,
                width: '100%',
              }}
            >
              <MenuBookIcon color="primary" sx={{ fontSize: 42, mb: 0.5 }} />
              <Typography sx={{ fontWeight: 800 }}>Chưa có ảnh bìa</Typography>
            </Box>
            <Typography sx={{ fontWeight: 700 }}>Kéo thả ảnh vào đây để thay ảnh mặc định</Typography>
            <Typography variant="body2" color="text.secondary">
              Hỗ trợ PNG/JPG, tối đa 5MB. Nếu chưa chọn ảnh, hệ thống sẽ dùng ảnh bìa mặc định.
            </Typography>
            <Button variant="contained" onClick={() => inputRef.current?.click()} sx={{ textTransform: 'none', fontWeight: 700 }}>
              Chọn ảnh từ máy
            </Button>
          </Stack>
        )}
      </Paper>

      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg"
        hidden
        onChange={(event) => void handleFile(event.target.files?.[0])}
      />

      {error && <FormHelperText error>{error}</FormHelperText>}

      <Dialog open={Boolean(cropDraft)} onClose={() => !uploading && setCropDraft(null)} fullWidth maxWidth="md">
        <DialogTitle>Cắt ảnh đại diện theo tỷ lệ 16:9</DialogTitle>
        <DialogContent>
          <Box sx={{ position: 'relative', height: { xs: 300, sm: 420 }, bgcolor: 'grey.900', borderRadius: 1, overflow: 'hidden' }}>
            {cropDraft && (
              <Cropper
                image={cropDraft.dataUrl}
                crop={crop}
                zoom={zoom}
                aspect={16 / 9}
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={handleCropComplete}
              />
            )}
          </Box>
          <Stack spacing={1} sx={{ mt: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Phóng to/thu nhỏ để đặt nội dung chính vào khung ảnh ngang.
            </Typography>
            <Slider min={1} max={3} step={0.1} value={zoom} onChange={(_, value) => setZoom(Number(value))} aria-label="Phóng to ảnh" />
            {uploadError && (
              <Alert severity="error" sx={{ mt: 1 }}>
                {uploadError}
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCropDraft(null)} disabled={uploading} sx={{ textTransform: 'none', fontWeight: 700 }}>
            Hủy
          </Button>
          <Button variant="contained" onClick={() => void confirmCrop()} disabled={uploading} sx={{ textTransform: 'none', fontWeight: 700 }}>
            {uploading ? 'Đang tải ảnh...' : (uploadError ? 'Thử lại' : 'Dùng ảnh này')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function collectStepErrors(step: number, form: CourseDraftForm, categoryLoadError: string | null) {
  const nextErrors: Record<string, string> = {};

  if (step === 0) {
    if (categoryLoadError) {
      nextErrors.category = 'Chưa tải được danh mục chuẩn từ hệ thống.';
    } else if (!form.category) {
      nextErrors.category = 'Vui lòng chọn danh mục khóa học.';
    }
    if (!form.learningLanguage) {
      nextErrors.learningLanguage = 'Vui lòng chọn ngôn ngữ học.';
    }
    if (!form.proficiencyFramework) {
      nextErrors.proficiencyFramework = 'Vui lòng chọn khung trình độ.';
    }
    if (!form.proficiencyLevel.trim()) {
      nextErrors.proficiencyLevel = 'Vui lòng nhập hoặc chọn cấp độ.';
    } else if (form.proficiencyLevel.trim().length > 32) {
      nextErrors.proficiencyLevel = 'Cấp độ không được vượt quá 32 ký tự.';
    }
    if (!form.frameworkVersion.trim()) {
      nextErrors.frameworkVersion = 'Vui lòng nhập phiên bản chương trình.';
    } else if (form.frameworkVersion.trim().length > 32) {
      nextErrors.frameworkVersion = 'Phiên bản không được vượt quá 32 ký tự.';
    }
    if (!form.price) {
      nextErrors.price = 'Giá khóa học không được để trống.';
    }
  }

  if (step === 1) {
    if (!hasRichText(form.introduction)) {
      nextErrors.introduction = 'Vui lòng nhập giới thiệu khóa học.';
    }
    if (!hasRichText(form.outcomes)) {
      nextErrors.outcomes = 'Vui lòng nhập kết quả học viên đạt được.';
    }
  }

  if (step === 2) {
    if (!hasRichText(form.prerequisites)) {
      nextErrors.prerequisites = 'Vui lòng nhập yêu cầu đầu vào.';
    }
    if (!hasRichText(form.targetStudents)) {
      nextErrors.targetStudents = 'Vui lòng nhập đối tượng học viên phù hợp.';
    }

    const accessDays = Number(form.accessDurationDays);
    if (!Number.isInteger(accessDays) || accessDays < 1) {
      nextErrors.accessDurationDays = 'Thời hạn truy cập phải từ 1 ngày trở lên.';
    }
    if (form.accessExpiresAt && new Date(`${form.accessExpiresAt}T23:59:59Z`) <= new Date()) {
      nextErrors.accessExpiresAt = 'Hạn cố định phải nằm trong tương lai.';
    }

    const validGoals = form.learningGoals
      .map((goal) => goal.trim())
      .filter((goal) => goal.length > 0 && goal.length <= maxGoalLength);

    form.learningGoals.forEach((goal, index) => {
      const trimmedGoal = goal.trim();
      if (index < 4 || trimmedGoal.length > 0) {
        if (!trimmedGoal) {
          nextErrors[`goal-${index}`] = 'Mục tiêu học tập không được để trống.';
        } else if (trimmedGoal.length > maxGoalLength) {
          nextErrors[`goal-${index}`] = 'Mỗi mục tiêu không được vượt quá 160 ký tự.';
        }
      }
    });

    if (validGoals.length < 4) {
      nextErrors.learningGoals = 'Cần tối thiểu 4 mục tiêu học tập hợp lệ.';
    }
  }

  return nextErrors;
}

function clearStepErrors(step: number, learningGoals: string[]) {
  const keysByStep: Record<number, string[]> = {
    0: ['title', 'learningLanguage', 'proficiencyFramework', 'proficiencyLevel', 'frameworkVersion', 'category', 'price'],
    1: ['thumbnailUrl', 'introduction', 'outcomes'],
    2: ['prerequisites', 'targetStudents', 'accessDurationDays', 'accessExpiresAt', 'learningGoals', ...learningGoals.map((_, index) => `goal-${index}`)],
  };

  return Object.fromEntries(keysByStep[step].map((key) => [key, '']));
}

function resolveCourseAssetUrl(url?: string | null) {
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

function hasRichText(value: string) {
  return stripHtml(value).length > 0;
}

function stripHtml(value: string) {
  const document = new DOMParser().parseFromString(value || '', 'text/html');
  return (document.body.textContent || '').replace(/\s+/g, ' ').trim();
}

function normalizePriceInput(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 12);
  return digits.replace(/^0+(?=\d)/, '');
}

function formatPriceInput(value: string) {
  if (!value) {
    return '';
  }

  return priceFormatter.format(Number(value));
}

function truncateFileName(fileName: string) {
  if (fileName.length <= 28) {
    return fileName;
  }

  const dotIndex = fileName.lastIndexOf('.');
  const extension = dotIndex >= 0 ? fileName.slice(dotIndex) : '';
  const name = dotIndex >= 0 ? fileName.slice(0, dotIndex) : fileName;
  return `${name.slice(0, 10)}...${name.slice(-6)}${extension}`;
}

function normalizeImageFileName(fileName: string, mimeType: string) {
  const dotIndex = fileName.lastIndexOf('.');
  const baseName = dotIndex >= 0 ? fileName.slice(0, dotIndex) : fileName;
  const extension = mimeType === 'image/png' ? 'png' : 'jpg';
  const safeBaseName = baseName
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
    .slice(0, 40);

  return `${safeBaseName || 'course-thumbnail'}-16x9.${extension}`;
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function cropImageToBlob(imageSrc: string, cropArea: Area, mimeType: string) {
  return new Promise<Blob>((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = cropArea.width;
      canvas.height = cropArea.height;
      const context = canvas.getContext('2d');

      if (!context) {
        reject(new Error('Cannot initialize image crop canvas'));
        return;
      }

      context.drawImage(
        image,
        cropArea.x,
        cropArea.y,
        cropArea.width,
        cropArea.height,
        0,
        0,
        cropArea.width,
        cropArea.height,
      );

      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(blob);
          } else {
            reject(new Error('Cannot crop selected image'));
          }
        },
        mimeType,
        0.92,
      );
    };
    image.onerror = () => reject(new Error('Cannot load selected image'));
    image.src = imageSrc;
  });
}
