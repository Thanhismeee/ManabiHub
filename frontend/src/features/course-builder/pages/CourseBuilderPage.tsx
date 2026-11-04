import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import DeleteIcon from '@mui/icons-material/Delete';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import OndemandVideoIcon from '@mui/icons-material/OndemandVideo';
import QuizOutlinedIcon from '@mui/icons-material/QuizOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import StyleOutlinedIcon from '@mui/icons-material/StyleOutlined';
import TextFieldsIcon from '@mui/icons-material/TextFields';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  FormHelperText,
  IconButton,
  MenuItem,
  Paper,
  Radio,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import Quill from 'quill';
import 'quill/dist/quill.snow.css';
import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { ROUTES } from '../../../shared/constants/routes';
import { sanitizeRichText } from '../../../shared/security/sanitizeRichText';
import {
  courseDraftApiError,
  createCourseModule,
  createLessonBlock,
  deleteCourseModule,
  deleteLessonBlock,
  fetchCourseBuilder,
  reorderCourseModules,
  reorderLessonBlocks,
  updateCourseModule,
  updateLessonBlock,
  type CourseBuilderResponse,
  type CourseModuleResponse,
  type FlashcardItemPayload,
  type LessonBlockPayload,
  type LessonBlockResponse,
  type LessonBlockType,
  type QuizQuestionPayload,
} from '../services/courseDraftService';

interface ModuleForm {
  title: string;
  description: string;
  editingId: string | null;
}

interface BlockForm {
  type: LessonBlockType;
  title: string;
  content: string;
  videoUrl: string;
  durationMinutes: string;
  quizQuestion: string;
  quizOptions: string[];
  quizAnswer: string;
  quizItems: QuizQuestionPayload[];
  flashcards: FlashcardItemPayload[];
  writingPrompt: string;
  rubric: string;
}

type Feedback = {
  severity: 'success' | 'error';
  message: string;
} | null;

const blockTypes: Array<{ value: LessonBlockType; label: string; description: string }> = [
  { value: 'VIDEO', label: 'Video bài giảng', description: 'Bài giảng hoặc video minh họa' },
  { value: 'TEXT', label: 'Bài đọc', description: 'Nội dung đọc, ghi chú, văn bản phụ đề/kịch bản' },
  { value: 'QUIZ', label: 'Trắc nghiệm', description: 'Bộ câu hỏi tương tác có đáp án đúng' },
  { value: 'FLASHCARD', label: 'Thẻ ghi nhớ', description: 'Thẻ từ vựng hoặc Kanji' },
  { value: 'WRITING', label: 'Bài tập viết', description: 'Đề tự luận kèm tiêu chí chấm điểm' },
];

const blockTitleMaxLength = 80;
const minimumMeaningfulLength = 5;

const emptyModuleForm: ModuleForm = {
  title: '',
  description: '',
  editingId: null,
};

export function CourseBuilderPage() {
  const navigate = useNavigate();
  const { draftId } = useParams<{ draftId: string }>();
  const [builder, setBuilder] = useState<CourseBuilderResponse | null>(null);
  const [selectedModuleId, setSelectedModuleId] = useState<string | null>(null);
  const [moduleForm, setModuleForm] = useState<ModuleForm>(emptyModuleForm);
  const [moduleError, setModuleError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingModule, setSavingModule] = useState(false);
  const [savingBlock, setSavingBlock] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [blockDialogOpen, setBlockDialogOpen] = useState(false);
  const [editingBlock, setEditingBlock] = useState<LessonBlockResponse | null>(null);
  const [blockForm, setBlockForm] = useState<BlockForm>(() => createEmptyBlockForm());
  const [blockErrors, setBlockErrors] = useState<Record<string, string>>({});
  const [draggingBlockId, setDraggingBlockId] = useState<string | null>(null);

  const selectedModule = useMemo(
    () => builder?.modules.find((module) => module.id === selectedModuleId) || null,
    [builder, selectedModuleId],
  );

  const selectedModuleHasKnowledgeBlock = useMemo(
    () => selectedModule?.blocks.some((block) => block.type === 'VIDEO' || block.type === 'TEXT') ?? false,
    [selectedModule],
  );

  const loadBuilder = useCallback(async () => {
    if (!draftId) {
      setFeedback({ severity: 'error', message: 'Không xác định được bản nháp cần xây nội dung.' });
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const response = await fetchCourseBuilder(draftId);
      setBuilder(response);
      setSelectedModuleId((current) => {
        if (current && response.modules.some((module) => module.id === current)) {
          return current;
        }
        return response.modules[0]?.id || null;
      });
      setFeedback(null);
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    } finally {
      setLoading(false);
    }
  }, [draftId]);

  useEffect(() => {
    void loadBuilder();
  }, [loadBuilder]);

  function updateModuleField(field: 'title' | 'description') {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setModuleForm((current) => ({ ...current, [field]: event.target.value }));
      setModuleError(null);
    };
  }

  function editModule(module: CourseModuleResponse) {
    setModuleForm({
      title: module.title,
      description: module.description || '',
      editingId: module.id,
    });
    setModuleError(null);
  }

  function selectModule(module: CourseModuleResponse) {
    setSelectedModuleId(module.id);
    editModule(module);
  }

  function resetModuleForm() {
    setModuleForm(emptyModuleForm);
    setModuleError(null);
  }

  async function saveModule() {
    if (!draftId) {
      return;
    }

    const title = moduleForm.title.trim();
    if (!title) {
      setModuleError('Vui lòng nhập tên học phần.');
      return;
    }
    const titleQualityError = validateMeaningfulText(title, 'Tên học phần');
    if (titleQualityError) {
      setModuleError(titleQualityError);
      return;
    }

    setSavingModule(true);
    try {
      const payload = { title, description: moduleForm.description.trim() || null };
      const response = moduleForm.editingId
        ? await updateCourseModule(draftId, moduleForm.editingId, payload)
        : await createCourseModule(draftId, payload);

      applyBuilder(response, moduleForm.editingId || response.modules.at(-1)?.id || null);
      setFeedback({ severity: 'success', message: moduleForm.editingId ? 'Đã cập nhật học phần.' : 'Đã thêm học phần mới.' });
      resetModuleForm();
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    } finally {
      setSavingModule(false);
    }
  }

  async function removeModule(module: CourseModuleResponse) {
    if (!draftId || !window.confirm(`Xóa học phần "${module.title}" và toàn bộ nội dung bên trong?`)) {
      return;
    }

    try {
      const response = await deleteCourseModule(draftId, module.id);
      applyBuilder(response, response.modules[0]?.id || null);
      setFeedback({ severity: 'success', message: 'Đã xóa học phần.' });
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    }
  }

  async function moveModule(moduleId: string, direction: -1 | 1) {
    if (!draftId || !builder) {
      return;
    }

    const ids = builder.modules.map((module) => module.id);
    const index = ids.indexOf(moduleId);
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= ids.length) {
      return;
    }

    [ids[index], ids[nextIndex]] = [ids[nextIndex], ids[index]];
    try {
      const response = await reorderCourseModules(draftId, ids);
      applyBuilder(response, moduleId);
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    }
  }

  function openCreateBlock(type: LessonBlockType = 'VIDEO') {
    if (isPracticeBlockType(type) && !selectedModuleHasKnowledgeBlock) {
      setFeedback({
        severity: 'error',
        message: 'Hãy thêm Video bài giảng hoặc Bài đọc trước khi tạo bài tập tương tác.',
      });
      return;
    }

    setFeedback(null);
    setEditingBlock(null);
    setBlockForm(createEmptyBlockForm(type));
    setBlockErrors({});
    setBlockDialogOpen(true);
  }

  function openEditBlock(block: LessonBlockResponse) {
    setFeedback(null);
    setEditingBlock(block);
    setBlockForm(createBlockFormFromResponse(block));
    setBlockErrors({});
    setBlockDialogOpen(true);
  }

  function updateBlockField(field: keyof BlockForm) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setBlockForm((current) => ({ ...current, [field]: event.target.value }));
      setBlockErrors((current) => ({ ...current, [field]: '' }));
    };
  }

  function changeBlockType(event: ChangeEvent<HTMLInputElement>) {
    const type = event.target.value as LessonBlockType;
    if (isPracticeBlockType(type) && !selectedModuleHasKnowledgeBlock && !editingBlock) {
      setBlockErrors((current) => ({
        ...current,
        type: 'Hãy thêm Video bài giảng hoặc Bài đọc trước khi tạo bài tập tương tác.',
      }));
      return;
    }

    setBlockForm((current) => ({
      ...createEmptyBlockForm(type),
      title: current.title,
    }));
    setBlockErrors({});
  }

  function updateBlockValue(field: keyof BlockForm, value: string) {
    setBlockForm((current) => ({ ...current, [field]: value }));
    setBlockErrors((current) => ({ ...current, [field]: '' }));
  }

  function updateQuizQuestion(index: number, value: string) {
    setBlockForm((current) => {
      const quizItems = [...current.quizItems];
      quizItems[index] = { ...quizItems[index], question: value };
      return { ...current, quizItems };
    });
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function addQuizQuestion() {
    setBlockForm((current) => ({ ...current, quizItems: [...current.quizItems, createEmptyQuizQuestion()] }));
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function removeQuizQuestion(index: number) {
    setBlockForm((current) => {
      if (current.quizItems.length <= 1) {
        return current;
      }

      return {
        ...current,
        quizItems: current.quizItems.filter((_, currentIndex) => currentIndex !== index),
      };
    });
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function updateQuizOption(questionIndex: number, optionIndex: number, value: string) {
    setBlockForm((current) => {
      const quizItems = [...current.quizItems];
      const quizItem = quizItems[questionIndex];
      const previousOption = quizItem.options[optionIndex];
      const options = [...quizItem.options];
      options[optionIndex] = value;
      quizItems[questionIndex] = {
        ...quizItem,
        options,
        answer: normalizeKey(quizItem.answer) === normalizeKey(previousOption) ? value : quizItem.answer,
      };
      return { ...current, quizItems };
    });
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function addQuizOption(questionIndex: number) {
    setBlockForm((current) => {
      const quizItems = [...current.quizItems];
      const quizItem = quizItems[questionIndex];
      quizItems[questionIndex] = { ...quizItem, options: [...quizItem.options, ''] };
      return { ...current, quizItems };
    });
  }

  function removeQuizOption(questionIndex: number, optionIndex: number) {
    setBlockForm((current) => {
      const quizItems = [...current.quizItems];
      const quizItem = quizItems[questionIndex];
      if (quizItem.options.length <= 2) {
        return current;
      }

      const removedOption = quizItem.options[optionIndex];
      const options = quizItem.options.filter((_, currentIndex) => currentIndex !== optionIndex);
      quizItems[questionIndex] = {
        ...quizItem,
        options,
        answer: normalizeKey(quizItem.answer) === normalizeKey(removedOption) ? '' : quizItem.answer,
      };
      return { ...current, quizItems };
    });
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function selectQuizAnswer(questionIndex: number, optionIndex: number) {
    setBlockForm((current) => {
      const quizItems = [...current.quizItems];
      const quizItem = quizItems[questionIndex];
      quizItems[questionIndex] = { ...quizItem, answer: quizItem.options[optionIndex] };
      return { ...current, quizItems };
    });
    setBlockErrors((current) => ({ ...current, quizItems: '' }));
  }

  function updateFlashcard(index: number, field: keyof FlashcardItemPayload, value: string) {
    setBlockForm((current) => {
      const flashcards = [...current.flashcards];
      flashcards[index] = { ...flashcards[index], [field]: value };
      return { ...current, flashcards };
    });
    setBlockErrors((current) => ({ ...current, flashcards: '' }));
  }

  function addFlashcard() {
    setBlockForm((current) => ({ ...current, flashcards: [...current.flashcards, { front: '', back: '' }] }));
  }

  function removeFlashcard(index: number) {
    setBlockForm((current) => ({
      ...current,
      flashcards: current.flashcards.filter((_, currentIndex) => currentIndex !== index),
    }));
  }

  async function saveBlock() {
    if (!draftId || !selectedModule) {
      return;
    }

    const nextErrors = validateBlockForm(blockForm);
    setBlockErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setSavingBlock(true);
    try {
      const payload = buildBlockPayload(blockForm);
      const response = editingBlock
        ? await updateLessonBlock(draftId, selectedModule.id, editingBlock.id, payload)
        : await createLessonBlock(draftId, selectedModule.id, payload);

      applyBuilder(response, selectedModule.id);
      setBlockDialogOpen(false);
      setFeedback({ severity: 'success', message: editingBlock ? 'Đã cập nhật khối nội dung.' : 'Đã thêm khối nội dung mới.' });
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    } finally {
      setSavingBlock(false);
    }
  }

  async function removeBlock(block: LessonBlockResponse) {
    if (!draftId || !selectedModule || !window.confirm(`Xóa khối nội dung "${block.title}"?`)) {
      return;
    }

    try {
      const response = await deleteLessonBlock(draftId, selectedModule.id, block.id);
      applyBuilder(response, selectedModule.id);
      setFeedback({ severity: 'success', message: 'Đã xóa khối nội dung.' });
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    }
  }

  async function reorderBlockByDrop(draggedBlockId: string | null, targetBlockId: string) {
    if (!draftId || !selectedModule || !draggedBlockId || draggedBlockId === targetBlockId) {
      setDraggingBlockId(null);
      return;
    }

    const ids = selectedModule.blocks.map((block) => block.id);
    const fromIndex = ids.indexOf(draggedBlockId);
    const toIndex = ids.indexOf(targetBlockId);
    if (fromIndex < 0 || toIndex < 0) {
      setDraggingBlockId(null);
      return;
    }

    const [movedId] = ids.splice(fromIndex, 1);
    ids.splice(toIndex, 0, movedId);

    try {
      const response = await reorderLessonBlocks(draftId, selectedModule.id, ids);
      applyBuilder(response, selectedModule.id);
    } catch (error) {
      setFeedback({ severity: 'error', message: courseDraftApiError(error).message });
    } finally {
      setDraggingBlockId(null);
    }
  }

  function applyBuilder(response: CourseBuilderResponse, preferredModuleId: string | null) {
    setBuilder(response);
    setSelectedModuleId(() => {
      if (preferredModuleId && response.modules.some((module) => module.id === preferredModuleId)) {
        return preferredModuleId;
      }
      return response.modules[0]?.id || null;
    });
  }

  return (
    <Box>
      <PageHeader
        title="Xây nội dung khóa học"
        breadcrumbs={[
          { label: 'Giảng viên' },
          { label: 'Khóa học của tôi', href: ROUTES.TEACHER.COURSES },
          { label: 'Xây nội dung' },
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

      {feedback && (
        <Alert severity={feedback.severity} onClose={() => setFeedback(null)} sx={{ mb: 2 }}>
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
        }}
      >
        {loading && (
          <Stack spacing={1.5} sx={{ alignItems: 'center', py: 8 }}>
            <CircularProgress size={28} />
            <Typography variant="body2" color="text.secondary">
              Đang tải Course Builder...
            </Typography>
          </Stack>
        )}

        {!loading && builder && (
          <Stack spacing={3}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {builder.courseTitle}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Tạo các học phần và thêm nội dung bài học để khóa học sẵn sàng gửi duyệt.
                </Typography>
              </Box>
              <Chip
                icon={<ViewModuleIcon />}
                label={`${builder.modules.length} học phần`}
                color="primary"
                variant="outlined"
                sx={{ alignSelf: { xs: 'flex-start', md: 'center' }, fontWeight: 700 }}
              />
            </Stack>

            {builder.validationWarnings.length > 0 ? (
              <Alert severity="warning" icon={<WarningAmberIcon />}>
                <Typography variant="body2" sx={{ fontWeight: 800, mb: 1 }}>
                  Khóa học cần hoàn thiện các mục sau trước khi gửi duyệt:
                </Typography>
                <Box component="ul" sx={{ m: 0, pl: 2 }}>
                  {builder.validationWarnings.map((warning, index) => (
                    <Typography component="li" key={index} variant="body2" sx={{ mb: 0.5 }}>
                      {warning}
                    </Typography>
                  ))}
                </Box>
              </Alert>
            ) : (
              <Alert severity="success" icon={<CheckCircleOutlinedIcon />}>
                <Typography variant="body2" sx={{ fontWeight: 800 }}>
                  Tất cả các điều kiện đã thỏa mãn! Khóa học đã sẵn sàng để gửi duyệt.
                </Typography>
              </Alert>
            )}

            <Box
              sx={{
                display: 'grid',
                gap: 3,
                gridTemplateColumns: { xs: '1fr', lg: '360px minmax(0, 1fr)' },
              }}
            >
              <Stack spacing={2}>
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                    Học phần
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Mỗi học phần nên bắt đầu bằng Video bài giảng hoặc Bài đọc, rồi thêm bài tập tương tác phía sau.
                  </Typography>
                </Box>

                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={1} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                      {moduleForm.editingId ? 'Sửa học phần đang chọn' : 'Tạo học phần mới'}
                    </Typography>
                    {moduleForm.editingId && (
                      <Button
                        variant="outlined"
                        size="small"
                        startIcon={<AddIcon />}
                        onClick={resetModuleForm}
                        sx={{ textTransform: 'none', fontWeight: 700 }}
                      >
                        Tạo học phần mới
                      </Button>
                    )}
                  </Stack>
                  <TextField
                    fullWidth
                    size="small"
                    label="Tên học phần"
                    placeholder="Ví dụ: Bài 1 - Chào hỏi cơ bản"
                    value={moduleForm.title}
                    onChange={updateModuleField('title')}
                    error={Boolean(moduleError)}
                    helperText={moduleError || 'Đặt tên ngắn, rõ nội dung chính của học phần.'}
                  />
                  <TextField
                    fullWidth
                    multiline
                    minRows={2}
                    size="small"
                    label="Mô tả ngắn"
                    placeholder="Ví dụ: Làm quen mẫu câu tự giới thiệu, chào hỏi và phát âm."
                    value={moduleForm.description}
                    onChange={updateModuleField('description')}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      startIcon={moduleForm.editingId ? <SaveOutlinedIcon /> : <AddIcon />}
                      disabled={savingModule}
                      onClick={() => void saveModule()}
                      sx={{ textTransform: 'none', fontWeight: 700 }}
                    >
                      {moduleForm.editingId ? 'Lưu học phần' : 'Thêm học phần'}
                    </Button>
                    {moduleForm.editingId && (
                      <Button variant="text" onClick={resetModuleForm} sx={{ textTransform: 'none', fontWeight: 700 }}>
                        Hủy
                      </Button>
                    )}
                  </Stack>
                </Stack>

                <Divider />

                {builder.modules.length === 0 ? (
                  <Stack spacing={1} sx={{ alignItems: 'center', py: 4, textAlign: 'center' }}>
                    <ViewModuleIcon color="primary" sx={{ fontSize: 40 }} />
                    <Typography sx={{ fontWeight: 800 }}>Chưa có học phần nào</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Tạo học phần đầu tiên để bắt đầu thêm video, quiz và flashcard.
                    </Typography>
                  </Stack>
                ) : (
                  <Stack spacing={1}>
                    {builder.modules.map((module, index) => (
                      <ModuleListItem
                        key={module.id}
                        active={module.id === selectedModuleId}
                        index={index}
                        module={module}
                        total={builder.modules.length}
                        disabled={savingModule || savingBlock}
                        onDelete={() => void removeModule(module)}
                        onMove={(direction) => void moveModule(module.id, direction)}
                        onSelect={() => selectModule(module)}
                      />
                    ))}
                  </Stack>
                )}
              </Stack>

              <Stack spacing={2} sx={{ minWidth: 0 }}>
                {selectedModule ? (
                  <>
                    <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      spacing={1.5}
                      sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'center' } }}
                    >
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                          {selectedModule.title}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {selectedModule.description || 'Chưa có mô tả học phần.'}
                        </Typography>
                      </Box>
                    </Stack>

                    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                      {blockTypes.map((type) => {
                        const disabled = isPracticeBlockType(type.value) && !selectedModuleHasKnowledgeBlock;

                        return (
                          <Tooltip key={type.value} title={disabled ? 'Thêm Video bài giảng hoặc Bài đọc trước khi tạo bài tập tương tác.' : type.description}>
                            <span>
                              <Button
                                variant="outlined"
                                size="small"
                                startIcon={blockTypeIcon(type.value)}
                                disabled={disabled}
                                onClick={() => openCreateBlock(type.value)}
                                sx={{ textTransform: 'none', fontWeight: 700 }}
                              >
                                {type.label}
                              </Button>
                            </span>
                          </Tooltip>
                        );
                      })}
                    </Stack>

                    {selectedModule.blocks.length === 0 ? (
                      <Stack spacing={1} sx={{ alignItems: 'center', border: '1px dashed', borderColor: 'divider', borderRadius: 1, py: 6, textAlign: 'center' }}>
                        <AddIcon color="primary" sx={{ fontSize: 38 }} />
                        <Typography sx={{ fontWeight: 800 }}>Học phần này chưa có nội dung</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 520 }}>
                          Thêm Video bài giảng hoặc Bài đọc trước. Sau đó hệ thống sẽ mở Trắc nghiệm, Thẻ ghi nhớ và Bài tập viết.
                        </Typography>
                      </Stack>
                    ) : (
                      <Stack spacing={1.5}>
                        {selectedModule.blocks.map((block, index) => (
                          <LessonBlockItem
                            key={block.id}
                            block={block}
                            dragging={draggingBlockId === block.id}
                            index={index}
                            disabled={savingModule || savingBlock}
                            onDelete={() => void removeBlock(block)}
                            onDragEnd={() => setDraggingBlockId(null)}
                            onDragStart={() => setDraggingBlockId(block.id)}
                            onDropOnBlock={() => void reorderBlockByDrop(draggingBlockId, block.id)}
                            onEdit={() => openEditBlock(block)}
                          />
                        ))}
                      </Stack>
                    )}
                  </>
                ) : (
                  <Stack spacing={1} sx={{ alignItems: 'center', border: '1px dashed', borderColor: 'divider', borderRadius: 1, py: 8, textAlign: 'center' }}>
                    <ViewModuleIcon color="primary" sx={{ fontSize: 44 }} />
                    <Typography sx={{ fontWeight: 800 }}>Chọn hoặc tạo học phần để thêm nội dung</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Cần có ít nhất một học phần trước khi thêm nội dung bài học.
                    </Typography>
                  </Stack>
                )}
              </Stack>
            </Box>
          </Stack>
        )}
      </Paper>

      <BlockDialog
        errors={blockErrors}
        form={blockForm}
        isEditing={Boolean(editingBlock)}
        open={blockDialogOpen}
        saving={savingBlock}
        onAddFlashcard={addFlashcard}
        onAddQuizOption={addQuizOption}
        onAddQuizQuestion={addQuizQuestion}
        onChangeField={updateBlockField}
        onChangeFlashcard={updateFlashcard}
        onChangeQuizOption={updateQuizOption}
        onChangeQuizQuestion={updateQuizQuestion}
        onChangeType={changeBlockType}
        onChangeValue={updateBlockValue}
        onClose={() => !savingBlock && setBlockDialogOpen(false)}
        onRemoveFlashcard={removeFlashcard}
        onRemoveQuizOption={removeQuizOption}
        onRemoveQuizQuestion={removeQuizQuestion}
        onSave={() => void saveBlock()}
        onSelectQuizAnswer={selectQuizAnswer}
        practiceTypesEnabled={selectedModuleHasKnowledgeBlock || Boolean(editingBlock)}
      />
    </Box>
  );
}

interface ModuleListItemProps {
  active: boolean;
  index: number;
  module: CourseModuleResponse;
  total: number;
  disabled?: boolean;
  onDelete: () => void;
  onMove: (direction: -1 | 1) => void;
  onSelect: () => void;
}

function ModuleListItem({ active, index, module, total, disabled, onDelete, onMove, onSelect }: ModuleListItemProps) {
  function handleActionClick(callback: () => void) {
    return (event: MouseEvent<HTMLButtonElement>) => {
      event.stopPropagation();
      callback();
    };
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onSelect();
    }
  }

  return (
    <Box
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={handleKeyDown}
      sx={{
        alignItems: 'center',
        bgcolor: active ? 'rgba(84, 73, 245, 0.08)' : 'background.paper',
        border: '1px solid',
        borderColor: active ? 'primary.main' : 'divider',
        borderRadius: 1,
        cursor: 'pointer',
        display: 'grid',
        gap: 1,
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        p: 1.5,
        transition: 'background-color 120ms ease, border-color 120ms ease',
        '&:focus-visible': {
          boxShadow: (theme) => `0 0 0 3px ${theme.palette.primary.main}22`,
          outline: 'none',
        },
        '&:hover': {
          bgcolor: active ? 'rgba(84, 73, 245, 0.12)' : 'action.hover',
          borderColor: active ? 'primary.main' : 'primary.light',
        },
      }}
    >
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', minWidth: 0 }}>
        <Box
          sx={{
            alignItems: 'center',
            bgcolor: active ? 'primary.main' : 'action.hover',
            borderRadius: 1,
            color: active ? 'primary.contrastText' : 'text.secondary',
            display: 'flex',
            flexShrink: 0,
            fontSize: 13,
            fontWeight: 800,
            height: 32,
            justifyContent: 'center',
            width: 32,
          }}
        >
          {index + 1}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography title={module.title} sx={{ fontWeight: 800, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {module.title}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {module.blocks.length} khối nội dung
          </Typography>
        </Box>
      </Stack>

      <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center' }}>
        <Tooltip title="Đưa lên">
          <span>
            <IconButton size="small" disabled={disabled || index === 0} onClick={handleActionClick(() => onMove(-1))}>
              <ArrowUpwardIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Đưa xuống">
          <span>
            <IconButton size="small" disabled={disabled || index === total - 1} onClick={handleActionClick(() => onMove(1))}>
              <ArrowDownwardIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Xóa học phần">
          <span>
            <IconButton color="error" size="small" disabled={disabled} onClick={handleActionClick(onDelete)}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        </Stack>
    </Box>
  );
}

interface LessonBlockItemProps {
  block: LessonBlockResponse;
  dragging: boolean;
  index: number;
  disabled?: boolean;
  onDelete: () => void;
  onDragEnd: () => void;
  onDragStart: () => void;
  onDropOnBlock: () => void;
  onEdit: () => void;
}

function LessonBlockItem({
  block,
  dragging,
  index,
  disabled,
  onDelete,
  onDragEnd,
  onDragStart,
  onDropOnBlock,
  onEdit,
}: LessonBlockItemProps) {
  return (
    <Box
      draggable={!disabled}
      onDragStart={(event) => {
        if (disabled) {
          event.preventDefault();
          return;
        }
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', block.id);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      onDragOver={(event) => {
        if (!disabled) {
          event.preventDefault();
          event.dataTransfer.dropEffect = 'move';
        }
      }}
      onDrop={(event) => {
        if (!disabled) {
          event.preventDefault();
          onDropOnBlock();
        }
      }}
      sx={{
        border: '1px solid',
        borderColor: block.validationMessage ? 'warning.main' : 'divider',
        borderRadius: 1,
        cursor: disabled ? 'default' : (dragging ? 'grabbing' : 'grab'),
        opacity: dragging ? 0.58 : 1,
        p: 1.5,
        transition: 'border-color 120ms ease, opacity 120ms ease, transform 120ms ease',
        '&:hover': {
          borderColor: block.validationMessage ? 'warning.main' : 'primary.light',
        },
      }}
    >
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1} sx={{ minWidth: 0 }}>
          <Tooltip title="Kéo để đổi thứ tự">
            <DragIndicatorIcon color="action" sx={{ flexShrink: 0, mt: 0.25 }} />
          </Tooltip>
          <Stack spacing={0.75} sx={{ minWidth: 0 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
              <Chip icon={blockTypeIcon(block.type)} label={blockTypeLabel(block.type)} size="small" color={block.validationMessage ? 'warning' : 'primary'} variant="outlined" />
              <Typography sx={{ fontWeight: 800, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {index + 1}. {block.title}
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {describeBlock(block)}
            </Typography>
            {block.validationMessage && (
              <Alert severity="warning" sx={{ py: 0.25 }}>
                {block.validationMessage}
              </Alert>
            )}
          </Stack>
        </Stack>

        <Stack direction="row" spacing={0.5} sx={{ alignSelf: { xs: 'flex-start', md: 'center' } }}>
          <Tooltip title="Sửa khối nội dung">
            <span>
              <IconButton size="small" disabled={disabled} onClick={onEdit}>
                <EditOutlinedIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Xóa khối nội dung">
            <span>
              <IconButton color="error" size="small" disabled={disabled} onClick={onDelete}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
      </Stack>
    </Box>
  );
}

interface BlockDialogProps {
  errors: Record<string, string>;
  form: BlockForm;
  isEditing: boolean;
  open: boolean;
  saving: boolean;
  onAddFlashcard: () => void;
  onAddQuizOption: (questionIndex: number) => void;
  onAddQuizQuestion: () => void;
  onChangeField: (field: keyof BlockForm) => (event: ChangeEvent<HTMLInputElement>) => void;
  onChangeFlashcard: (index: number, field: keyof FlashcardItemPayload, value: string) => void;
  onChangeQuizOption: (questionIndex: number, optionIndex: number, value: string) => void;
  onChangeQuizQuestion: (index: number, value: string) => void;
  onChangeType: (event: ChangeEvent<HTMLInputElement>) => void;
  onChangeValue: (field: keyof BlockForm, value: string) => void;
  onClose: () => void;
  onRemoveFlashcard: (index: number) => void;
  onRemoveQuizOption: (questionIndex: number, optionIndex: number) => void;
  onRemoveQuizQuestion: (index: number) => void;
  onSave: () => void;
  onSelectQuizAnswer: (questionIndex: number, optionIndex: number) => void;
  practiceTypesEnabled: boolean;
}

function BlockDialog({
  errors,
  form,
  isEditing,
  open,
  saving,
  onAddFlashcard,
  onAddQuizOption,
  onAddQuizQuestion,
  onChangeField,
  onChangeFlashcard,
  onChangeQuizOption,
  onChangeQuizQuestion,
  onChangeType,
  onChangeValue,
  onClose,
  onRemoveFlashcard,
  onRemoveQuizOption,
  onRemoveQuizQuestion,
  onSave,
  onSelectQuizAnswer,
  practiceTypesEnabled,
}: BlockDialogProps) {
  const longVideo = form.type === 'VIDEO' && Number(form.durationMinutes) > 15;
  const blockTitleLength = form.title.length;

  function updateDurationMinutes(event: ChangeEvent<HTMLInputElement>) {
    const digitsOnly = event.target.value.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
    onChangeValue('durationMinutes', digitsOnly);
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="md"
      slotProps={{
        paper: {
          sx: { borderRadius: 2 },
        },
      }}
    >
      <DialogTitle
        sx={{
          alignItems: 'center',
          display: 'flex',
          gap: 2,
          justifyContent: 'space-between',
          pr: 1.25,
        }}
      >
        <Typography component="span" variant="h6" sx={{ fontWeight: 800 }}>
          {isEditing ? 'Sửa khối nội dung' : 'Thêm khối nội dung'}
        </Typography>
        <Tooltip title="Đóng">
          <span>
            <IconButton aria-label="Đóng modal" disabled={saving} onClick={onClose} size="small">
              <CloseIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2.25} sx={{ pt: 1 }}>
          <TextField
            select
            label="Loại bài học"
            value={form.type}
            onChange={onChangeType}
            error={Boolean(errors.type)}
            helperText={errors.type}
          >
            {blockTypes.map((type) => (
              <MenuItem
                key={type.value}
                value={type.value}
                disabled={isPracticeBlockType(type.value) && !practiceTypesEnabled}
              >
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  {blockTypeIcon(type.value)}
                  <Box>
                    <Typography sx={{ fontWeight: 700 }}>{type.label}</Typography>
                    <Typography variant="caption" color="text.secondary">{type.description}</Typography>
                  </Box>
                </Stack>
              </MenuItem>
            ))}
          </TextField>

          <TextField
            required
            label="Tiêu đề bài học"
            placeholder={blockTitlePlaceholder(form.type)}
            value={form.title}
            onChange={onChangeField('title')}
            error={Boolean(errors.title)}
            helperText={(
              <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between' }}>
                <span>{errors.title || 'Tên ngắn giúp giáo viên và học viên quét nội dung nhanh hơn.'}</span>
                <span>{blockTitleLength}/{blockTitleMaxLength}</span>
              </Stack>
            )}
            slotProps={{ htmlInput: { maxLength: blockTitleMaxLength } }}
          />

          {form.type === 'VIDEO' && (
            <Stack spacing={2}>
              <TextField
                required
                label="Link video"
                placeholder="Dán link YouTube, Vimeo hoặc URL video đã upload"
                value={form.videoUrl}
                onChange={onChangeField('videoUrl')}
                error={Boolean(errors.videoUrl)}
                helperText={errors.videoUrl || 'Chấp nhận đường dẫn từ YouTube, Vimeo hoặc URL video trực tiếp.'}
              />
              <TextField
                required
                label="Thời lượng video (phút)"
                type="text"
                value={form.durationMinutes}
                onChange={updateDurationMinutes}
                onKeyDown={preventNonPositiveIntegerKey}
                error={Boolean(errors.durationMinutes)}
                helperText={errors.durationMinutes || 'Chỉ nhập số nguyên dương. Video trên 15 phút nên có Trắc nghiệm, Thẻ ghi nhớ hoặc Bài tập viết ngay sau.'}
                slotProps={{
                  htmlInput: {
                    autoComplete: 'off',
                    inputMode: 'numeric',
                    pattern: '[0-9]*',
                  },
                }}
              />
              <TextField
                multiline
                minRows={3}
                label="Ghi chú hoặc nội dung video ngắn"
                value={form.content}
                onChange={onChangeField('content')}
              />
              {longVideo && (
                <Alert severity="warning">
                  Sau khi lưu video dài hơn 15 phút, hãy thêm Trắc nghiệm, Thẻ ghi nhớ hoặc Bài tập viết ngay bên dưới để học viên ôn lại kiến thức.
                </Alert>
              )}
            </Stack>
          )}

          {form.type === 'TEXT' && (
            <RichTextEditor
              required
              label="Nội dung văn bản"
              placeholder="Nhập phần đọc, giải thích ngữ pháp hoặc văn bản phụ đề cho học viên."
              value={form.content}
              onChange={(value) => onChangeValue('content', value)}
              error={Boolean(errors.content)}
              helperText={errors.content}
            />
          )}

          {form.type === 'QUIZ' && (
            <Stack spacing={2}>
              <Alert severity="info">
                Một khối trắc nghiệm có thể chứa nhiều câu hỏi. Chọn đáp án đúng bằng nút tròn ngay cạnh từng đáp án.
              </Alert>
              <Stack spacing={1.5}>
                {form.quizItems.map((quizItem, questionIndex) => (
                  <Accordion key={`quiz-question-${questionIndex}`} defaultExpanded={questionIndex === 0} disableGutters>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Stack
                        direction={{ xs: 'column', sm: 'row' }}
                        spacing={1}
                        sx={{ alignItems: { xs: 'flex-start', sm: 'center' }, width: '100%' }}
                      >
                        <Typography sx={{ fontWeight: 800 }}>
                          Câu hỏi {questionIndex + 1}
                        </Typography>
                        <Typography color="text.secondary" variant="body2" sx={{ flex: 1 }}>
                          {quizItem.question.trim() || 'Chưa nhập nội dung câu hỏi'}
                        </Typography>
                        {form.quizItems.length > 1 && (
                          <Tooltip title="Xóa câu hỏi">
                            <IconButton
                              color="error"
                              onClick={(event) => {
                                event.stopPropagation();
                                onRemoveQuizQuestion(questionIndex);
                              }}
                              onFocus={(event) => event.stopPropagation()}
                              size="small"
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Stack>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1.5}>
                        <TextField
                          required
                          multiline
                          minRows={2}
                          label={`Nội dung câu hỏi ${questionIndex + 1}`}
                          placeholder="Ví dụ: 「はじめまして」 dùng trong tình huống nào?"
                          value={quizItem.question}
                          onChange={(event) => onChangeQuizQuestion(questionIndex, event.target.value)}
                        />
                        <Stack spacing={1}>
                          {quizItem.options.map((option, optionIndex) => {
                            const optionSelected = Boolean(option.trim())
                              && normalizeKey(option) === normalizeKey(quizItem.answer);

                            return (
                              <Stack
                                key={`quiz-question-${questionIndex}-option-${optionIndex}`}
                                direction="row"
                                spacing={1}
                                sx={{ alignItems: 'center' }}
                              >
                                <Tooltip title={option.trim() ? 'Chọn làm đáp án đúng' : 'Nhập đáp án trước khi chọn'}>
                                  <span>
                                    <Radio
                                      checked={optionSelected}
                                      disabled={!option.trim()}
                                      onChange={() => onSelectQuizAnswer(questionIndex, optionIndex)}
                                      slotProps={{
                                        input: { 'aria-label': `Chọn đáp án ${optionIndex + 1} là đáp án đúng` },
                                      }}
                                    />
                                  </span>
                                </Tooltip>
                                <TextField
                                  fullWidth
                                  label={`Đáp án ${optionIndex + 1}`}
                                  value={option}
                                  onChange={(event) => onChangeQuizOption(questionIndex, optionIndex, event.target.value)}
                                />
                                {quizItem.options.length > 2 && (
                                  <Tooltip title="Xóa đáp án">
                                    <IconButton color="error" onClick={() => onRemoveQuizOption(questionIndex, optionIndex)}>
                                      <DeleteIcon />
                                    </IconButton>
                                  </Tooltip>
                                )}
                              </Stack>
                            );
                          })}
                          <Button
                            variant="outlined"
                            startIcon={<AddIcon />}
                            onClick={() => onAddQuizOption(questionIndex)}
                            sx={{ alignSelf: 'flex-start', textTransform: 'none', fontWeight: 700 }}
                          >
                            Thêm đáp án
                          </Button>
                        </Stack>
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Stack>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={onAddQuizQuestion}
                sx={{ alignSelf: 'flex-start', textTransform: 'none', fontWeight: 800 }}
              >
                Thêm câu hỏi
              </Button>
              {errors.quizItems && <FormHelperText error>{errors.quizItems}</FormHelperText>}
            </Stack>
          )}

          {form.type === 'FLASHCARD' && (
            <Stack spacing={1.5}>
              {form.flashcards.map((flashcard, index) => (
                <Stack key={`flashcard-${index}`} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { xs: 'stretch', sm: 'center' } }}>
                  <TextField
                    fullWidth
                    label={`Mặt trước ${index + 1}`}
                    placeholder="Ví dụ: こんにちは"
                    value={flashcard.front}
                    onChange={(event) => onChangeFlashcard(index, 'front', event.target.value)}
                  />
                  <TextField
                    fullWidth
                    label={`Mặt sau ${index + 1}`}
                    placeholder="Ví dụ: Xin chào"
                    value={flashcard.back}
                    onChange={(event) => onChangeFlashcard(index, 'back', event.target.value)}
                  />
                  {form.flashcards.length > 1 && (
                    <IconButton color="error" onClick={() => onRemoveFlashcard(index)}>
                      <DeleteIcon />
                    </IconButton>
                  )}
                </Stack>
              ))}
              <Button variant="outlined" startIcon={<AddIcon />} onClick={onAddFlashcard} sx={{ alignSelf: 'flex-start', textTransform: 'none', fontWeight: 700 }}>
                Thêm thẻ ghi nhớ
              </Button>
              {errors.flashcards && <FormHelperText error>{errors.flashcards}</FormHelperText>}
            </Stack>
          )}

          {form.type === 'WRITING' && (
            <Stack spacing={2}>
              <TextField
                required
                multiline
                minRows={4}
                label="Đề bài tập viết"
                placeholder="Ví dụ: Viết 5 câu tự giới thiệu bản thân bằng mẫu câu đã học."
                value={form.writingPrompt}
                onChange={onChangeField('writingPrompt')}
                error={Boolean(errors.writingPrompt)}
                helperText={errors.writingPrompt}
              />
              <TextField
                required
                multiline
                minRows={4}
                label="Tiêu chí chấm điểm"
                placeholder="Ví dụ: Đúng mẫu câu 40%, từ vựng 30%, chính tả 20%, trình bày 10%."
                value={form.rubric}
                onChange={onChangeField('rubric')}
                error={Boolean(errors.rubric)}
                helperText={errors.rubric || 'Tiêu chí chấm điểm giúp giáo viên đánh giá bài viết rõ ràng và nhất quán.'}
              />
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button variant="outlined" disabled={saving} onClick={onClose} sx={{ textTransform: 'none', fontWeight: 700 }}>
          Hủy
        </Button>
        <Button variant="contained" disabled={saving} startIcon={<SaveOutlinedIcon />} onClick={onSave} sx={{ textTransform: 'none', fontWeight: 700 }}>
          {saving ? 'Đang lưu...' : 'Lưu khối nội dung'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface RichTextEditorProps {
  error?: boolean;
  helperText?: string;
  label: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  value: string;
}

function RichTextEditor({ error, helperText, label, onChange, placeholder, required, value }: RichTextEditorProps) {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const quillRef = useRef<Quill | null>(null);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!editorRef.current || quillRef.current) {
      return;
    }

    const editor = document.createElement('div');
    editorRef.current.appendChild(editor);

    const quill = new Quill(editor, {
      modules: {
        toolbar: [
          ['bold', 'italic'],
          [{ list: 'bullet' }, { list: 'ordered' }],
          ['clean'],
        ],
      },
      placeholder,
      theme: 'snow',
    });

    const sanitizedInitialValue = sanitizeRichText(value);
    quill.clipboard.dangerouslyPasteHTML(sanitizedInitialValue);
    valueRef.current = sanitizedInitialValue;
    quill.on('text-change', () => {
      const html = quill.root.innerHTML === '<p><br></p>'
        ? ''
        : sanitizeRichText(quill.root.innerHTML);
      valueRef.current = html;
      onChangeRef.current(html);
    });

    quillRef.current = quill;
  }, [placeholder, value]);

  useEffect(() => {
    const quill = quillRef.current;
    if (!quill || value === valueRef.current) {
      return;
    }

    const selection = quill.getSelection();
    const sanitizedValue = sanitizeRichText(value);
    quill.clipboard.dangerouslyPasteHTML(sanitizedValue);
    valueRef.current = sanitizedValue;
    if (selection) {
      quill.setSelection(selection);
    }
  }, [value]);

  return (
    <Box>
      <Typography
        component="label"
        sx={{
          color: error ? 'error.main' : 'text.primary',
          display: 'block',
          fontSize: 14,
          fontWeight: 700,
          mb: 0.75,
        }}
      >
        {label}{required ? ' *' : ''}
      </Typography>
      <Box
        ref={editorRef}
        sx={{
          '& .ql-container': {
            borderBottomLeftRadius: 1,
            borderBottomRightRadius: 1,
            minHeight: 180,
          },
          '& .ql-editor': {
            minHeight: 180,
          },
          '& .ql-toolbar': {
            borderTopLeftRadius: 1,
            borderTopRightRadius: 1,
          },
          borderColor: error ? 'error.main' : 'divider',
        }}
      />
      {helperText && <FormHelperText error={error}>{helperText}</FormHelperText>}
    </Box>
  );
}

function createEmptyBlockForm(type: LessonBlockType = 'VIDEO'): BlockForm {
  return {
    type,
    title: '',
    content: '',
    videoUrl: '',
    durationMinutes: '',
    quizQuestion: '',
    quizOptions: ['', ''],
    quizAnswer: '',
    quizItems: [createEmptyQuizQuestion()],
    flashcards: [{ front: '', back: '' }],
    writingPrompt: '',
    rubric: '',
  };
}

function createBlockFormFromResponse(block: LessonBlockResponse): BlockForm {
  const quizItems = block.quizItems?.length
    ? block.quizItems
    : [{
      question: block.quizQuestion || '',
      options: block.quizOptions?.length ? block.quizOptions : ['', ''],
      answer: block.quizAnswer || '',
    }];

  return {
    type: block.type,
    title: block.title || '',
    content: sanitizeRichText(block.content),
    videoUrl: block.videoUrl || '',
    durationMinutes: block.durationMinutes ? String(block.durationMinutes) : '',
    quizQuestion: block.quizQuestion || '',
    quizOptions: block.quizOptions?.length ? block.quizOptions : ['', ''],
    quizAnswer: block.quizAnswer || '',
    quizItems,
    flashcards: block.flashcards?.length ? block.flashcards : [{ front: '', back: '' }],
    writingPrompt: block.writingPrompt || '',
    rubric: block.rubric || '',
  };
}

function createEmptyQuizQuestion(): QuizQuestionPayload {
  return {
    question: '',
    options: ['', ''],
    answer: '',
  };
}

function validateBlockForm(form: BlockForm) {
  const errors: Record<string, string> = {};
  if (!form.title.trim()) {
    errors.title = 'Vui lòng nhập tiêu đề bài học.';
  } else if (form.title.trim().length > blockTitleMaxLength) {
    errors.title = `Tiêu đề bài học không được vượt quá ${blockTitleMaxLength} ký tự.`;
  } else {
    const titleQualityError = validateMeaningfulText(form.title, 'Tiêu đề bài học');
    if (titleQualityError) {
      errors.title = titleQualityError;
    }
  }

  if (form.type === 'VIDEO') {
    if (!form.videoUrl.trim()) {
      errors.videoUrl = 'Vui lòng nhập link video.';
    }
    if (!form.durationMinutes.trim()) {
      errors.durationMinutes = 'Vui lòng nhập thời lượng video.';
    } else if (!/^[1-9]\d*$/.test(form.durationMinutes.trim())) {
      errors.durationMinutes = 'Thời lượng video phải là số nguyên dương.';
    }
  }

  if (form.type === 'TEXT') {
    const contentText = toPlainText(form.content);
    if (!contentText) {
      errors.content = 'Vui lòng nhập nội dung văn bản.';
    } else {
      const contentQualityError = validateMeaningfulText(contentText, 'Nội dung bài đọc');
      if (contentQualityError) {
        errors.content = contentQualityError;
      }
    }
  }

  if (form.type === 'QUIZ') {
    if (form.quizItems.length === 0) {
      errors.quizItems = 'Bài trắc nghiệm cần ít nhất 1 câu hỏi.';
    }

    for (const [index, quizItem] of form.quizItems.entries()) {
      const options = quizItem.options.map((option) => option.trim()).filter(Boolean);
      if (!quizItem.question.trim()) {
        errors.quizItems = `Vui lòng nhập nội dung cho câu hỏi ${index + 1}.`;
        break;
      }
      const questionQualityError = validateMeaningfulText(quizItem.question, `Câu hỏi ${index + 1}`);
      if (questionQualityError) {
        errors.quizItems = questionQualityError;
        break;
      }
      if (options.length < 2) {
        errors.quizItems = `Câu hỏi ${index + 1} cần ít nhất 2 đáp án.`;
        break;
      }
      if (new Set(options.map(normalizeKey)).size !== options.length) {
        errors.quizItems = `Các đáp án của câu hỏi ${index + 1} không được trùng nhau.`;
        break;
      }
      if (!quizItem.answer.trim()) {
        errors.quizItems = `Vui lòng chọn đáp án đúng cho câu hỏi ${index + 1}.`;
        break;
      }
      if (!options.map(normalizeKey).includes(normalizeKey(quizItem.answer))) {
        errors.quizItems = `Đáp án đúng của câu hỏi ${index + 1} phải nằm trong danh sách đáp án.`;
        break;
      }
    }
  }

  if (form.type === 'FLASHCARD') {
    const cards = form.flashcards.map((card) => ({ front: card.front.trim(), back: card.back.trim() }));
    if (cards.length === 0 || cards.some((card) => !card.front || !card.back)) {
      errors.flashcards = 'Mỗi flashcard cần đủ mặt trước và mặt sau.';
    }
    if (new Set(cards.map((card) => normalizeKey(card.front))).size !== cards.length) {
      errors.flashcards = 'Mặt trước của flashcard không được trùng nhau.';
    }
  }

  if (form.type === 'WRITING') {
    if (!form.writingPrompt.trim()) {
      errors.writingPrompt = 'Vui lòng nhập đề bài writing.';
    } else {
      const promptQualityError = validateMeaningfulText(form.writingPrompt, 'Đề bài writing');
      if (promptQualityError) {
        errors.writingPrompt = promptQualityError;
      }
    }
    if (!form.rubric.trim()) {
      errors.rubric = 'Bài tập viết bắt buộc phải có tiêu chí chấm điểm.';
    } else {
      const rubricQualityError = validateMeaningfulText(form.rubric, 'Tiêu chí chấm điểm');
      if (rubricQualityError) {
        errors.rubric = rubricQualityError;
      }
    }
  }

  return errors;
}

function validateMeaningfulText(value: string, label: string) {
  const plainText = toPlainText(value);
  if (plainText.length < minimumMeaningfulLength) {
    return `${label} cần ít nhất ${minimumMeaningfulLength} ký tự có nghĩa.`;
  }

  if (looksLikePlaceholderGarbage(plainText)) {
    return `${label} đang giống dữ liệu nhập thử. Vui lòng nhập nội dung rõ nghĩa hơn.`;
  }

  return null;
}

function looksLikePlaceholderGarbage(value: string) {
  const compact = value.toLowerCase().replace(/\s+/g, '');
  const lettersAndNumbers = [...compact].filter((character) => /[\p{L}\p{N}]/u.test(character)).join('');
  if (lettersAndNumbers.length < minimumMeaningfulLength) {
    return false;
  }

  const latinBase = lettersAndNumbers.normalize('NFD').replace(/\p{M}/gu, '');
  if (/^(.)\1{4,}$/u.test(latinBase) || /^(.{1,3})\1{2,}$/u.test(latinBase)) {
    return true;
  }

  if (latinBase.length >= 6 && new Set([...latinBase]).size <= 2) {
    return true;
  }

  const lettersOnly = latinBase.replace(/[0-9]/g, '');
  return lettersOnly.length >= minimumMeaningfulLength && /^[adwsqexzcvfr]+$/i.test(lettersOnly);
}

function buildBlockPayload(form: BlockForm): LessonBlockPayload {
  const base = {
    type: form.type,
    title: form.title.trim(),
  };

  if (form.type === 'VIDEO') {
    return {
      ...base,
      content: sanitizeRichText(form.content).trim() || null,
      videoUrl: form.videoUrl.trim(),
      durationMinutes: Number(form.durationMinutes),
    };
  }

  if (form.type === 'TEXT') {
    return {
      ...base,
      content: sanitizeRichText(form.content).trim(),
    };
  }

  if (form.type === 'QUIZ') {
    const quizItems = form.quizItems.map((quizItem) => ({
      question: quizItem.question.trim(),
      options: quizItem.options.map((option) => option.trim()).filter(Boolean),
      answer: quizItem.answer.trim(),
    }));
    const firstQuestion = quizItems[0] || createEmptyQuizQuestion();

    return {
      ...base,
      quizAnswer: firstQuestion.answer,
      quizItems,
      quizOptions: firstQuestion.options,
      quizQuestion: firstQuestion.question,
    };
  }

  if (form.type === 'FLASHCARD') {
    return {
      ...base,
      flashcards: form.flashcards.map((card) => ({
        front: card.front.trim(),
        back: card.back.trim(),
      })),
    };
  }

  return {
    ...base,
    rubric: form.rubric.trim(),
    writingPrompt: form.writingPrompt.trim(),
  };
}

function blockTypeLabel(type: LessonBlockType) {
  return blockTypes.find((item) => item.value === type)?.label || type;
}

function blockTitlePlaceholder(type: LessonBlockType) {
  if (type === 'VIDEO') {
    return 'Ví dụ: Video giới thiệu bảng chữ cái Hiragana';
  }
  if (type === 'TEXT') {
    return 'Ví dụ: Bài đọc về cách chào hỏi trong lớp học';
  }
  if (type === 'QUIZ') {
    return 'Ví dụ: Trắc nghiệm kiểm tra mẫu câu chào hỏi';
  }
  if (type === 'FLASHCARD') {
    return 'Ví dụ: Thẻ ghi nhớ từ vựng chào hỏi N5';
  }
  return 'Ví dụ: Bài viết luận ngắn tự giới thiệu bản thân bằng tiếng Nhật';
}

function isPracticeBlockType(type: LessonBlockType) {
  return type === 'QUIZ' || type === 'FLASHCARD' || type === 'WRITING';
}

function preventNonPositiveIntegerKey(event: KeyboardEvent<HTMLInputElement>) {
  const allowedKeys = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight', 'Home', 'End'];
  if (allowedKeys.includes(event.key) || event.ctrlKey || event.metaKey) {
    return;
  }

  if (!/^\d$/.test(event.key)) {
    event.preventDefault();
  }
}

function blockTypeIcon(type: LessonBlockType) {
  if (type === 'VIDEO') {
    return <OndemandVideoIcon fontSize="small" />;
  }
  if (type === 'TEXT') {
    return <TextFieldsIcon fontSize="small" />;
  }
  if (type === 'QUIZ') {
    return <QuizOutlinedIcon fontSize="small" />;
  }
  if (type === 'FLASHCARD') {
    return <StyleOutlinedIcon fontSize="small" />;
  }
  return <AssignmentOutlinedIcon fontSize="small" />;
}

function describeBlock(block: LessonBlockResponse) {
  if (block.type === 'VIDEO') {
    return `${block.durationMinutes || 0} phút · ${block.videoUrl || 'Chưa có link video'}`;
  }
  if (block.type === 'TEXT') {
    return truncate(toPlainText(block.content) || 'Nội dung văn bản', 120);
  }
  if (block.type === 'QUIZ') {
    const questionCount = block.quizItems?.length || (block.quizQuestion ? 1 : 0);
    return `${questionCount} câu hỏi trắc nghiệm`;
  }
  if (block.type === 'FLASHCARD') {
    return `${block.flashcards.length} thẻ ghi nhớ`;
  }
  return truncate(block.writingPrompt || 'Bài viết', 120);
}

function toPlainText(value?: string | null) {
  if (!value) {
    return '';
  }

  const document = new DOMParser().parseFromString(value, 'text/html');
  return document.body.textContent?.replace(/\s+/g, ' ').trim() || value;
}

function truncate(value: string, maxLength: number) {
  return value.length > maxLength ? `${value.slice(0, maxLength - 3)}...` : value;
}

function normalizeKey(value: string) {
  return value.trim().toLowerCase();
}
