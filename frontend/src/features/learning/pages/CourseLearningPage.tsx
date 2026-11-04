import axios from 'axios';
import { useLocale } from '../../../shared/i18n/locale';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  LinearProgress,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Radio,
  Stack,
  Typography,
  TextField,
  ListItem,
  IconButton,
  Tooltip
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined';
import AssignmentTurnedInOutlinedIcon from '@mui/icons-material/AssignmentTurnedInOutlined';
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined';
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { isAxiosError } from 'axios';
import ReactPlayer from 'react-player';
import { sanitizeRichText } from '../../../shared/security/sanitizeRichText';
import { aiChatService } from '../../ai-chat/services/aiChatService';
import { learningService } from '../services/learningService';
import type {
  CourseLearning,
  CourseProgressSummary,
  FinalTestAttempt,
  FinalTestEligibility,
  FinalTestSubmissionResult,
  LearningLessonBlock,
  LearningModule,
  LearningCertificate,
  QuizQuestion,
  QuizSubmissionResult,
} from '../types';
import { ROUTES } from '../../../shared/constants/routes';
import { getAuthSession, hasAnyRole } from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import { ReportViolationModal } from '../../violation/components/ReportViolationModal';
import { AiChatPanel } from '../../ai-chat/components/AiChatPanel';
import {
  isSingleCharacterMutation,
  readLocalStorageValue,
  removeLocalStorageValue,
  writeLocalStorageValue,
} from '../utils/learningInputGuard';
import {
  FINAL_TEST_MAX_VIOLATIONS,
  isClipboardShortcut,
  isScreenshotShortcut,
  violationLabel,
  type FinalTestViolationType,
} from '../utils/finalTestProctoring';
import { downloadCertificatePdf, formatCertificateDate } from '../utils/certificatePdf';
import {
  formatVideoTime,
  normalizeWatchedSecondsAtVideoEnd,
  resolveVideoResumePosition,
  type VideoProgressCheckpoint,
} from '../utils/videoProgress';
import { CoursePomodoroPanel } from '../components/CoursePomodoroPanel';

const VIDEO_SAVE_INTERVAL_SECONDS = 10;
const WATCHED_DELTA_MAX_SECONDS = 2;
const VIDEO_PROGRESS_STORAGE_PREFIX = 'manabihub:video-progress:';
const COURSE_SELECTION_STORAGE_PREFIX = 'manabihub:course-selection:';
const WRITING_DRAFT_STORAGE_PREFIX = 'manabihub:writing-draft:';
const QUIZ_ANSWERS_STORAGE_PREFIX = 'manabihub:quiz-answers:';
const FINAL_TEST_STORAGE_PREFIX = 'manabihub:final-test:';

interface LocalWritingDraft {
  content: string;
  savedAt: number;
}

function publicStorageScope() {
  return getAuthSession('public')?.subject ?? 'anonymous';
}

function courseSelectionStorageKey(courseId: string) {
  return `${COURSE_SELECTION_STORAGE_PREFIX}${publicStorageScope()}:${courseId}`;
}

function writingDraftStorageKey(blockId: string) {
  return `${WRITING_DRAFT_STORAGE_PREFIX}${publicStorageScope()}:${blockId}`;
}

function quizAnswersStorageKey(blockId: string) {
  return `${QUIZ_ANSWERS_STORAGE_PREFIX}${publicStorageScope()}:${blockId}`;
}

function finalTestStorageKey(courseId: string) {
  return `${FINAL_TEST_STORAGE_PREFIX}${publicStorageScope()}:${courseId}`;
}

function videoProgressStorageKey(blockId: string) {
  return `${VIDEO_PROGRESS_STORAGE_PREFIX}${publicStorageScope()}:${blockId}`;
}

interface LocalFinalTestState {
  attempt: FinalTestAttempt;
  answers: Record<string, string[]>;
}

export function CourseLearningPage() {
  const { t } = useLocale();
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedAiBlockId = searchParams.get('aiLessonBlockId');
  const [learning, setLearning] = useState<CourseLearning | null>(null);
  const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
  const [aiChatOpen, setAiChatOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [completing, setCompleting] = useState(false);
  const [selectedContentLoading, setSelectedContentLoading] = useState(false);
  const [reportModalOpen, setReportModalOpen] = useState(false);
  const [aiChatAvailable, setAiChatAvailable] = useState<boolean | null>(null);

  const session = getAuthSession('public');
  const canReport = session ? hasAnyRole(session, [ROLES.STUDENT, ROLES.TEACHER]) : false;

  useEffect(() => {
    if (!courseId || !selectedBlockId) {
      setAiChatAvailable(false);
      return;
    }

    let active = true;
    setAiChatAvailable(null);
    void aiChatService
      .getEligibility(courseId, selectedBlockId)
      .then((eligibility) => {
        if (active) setAiChatAvailable(eligibility.eligible);
      })
      .catch(() => {
        if (active) setAiChatAvailable(false);
      });

    return () => {
      active = false;
    };
  }, [courseId, selectedBlockId]);

  useEffect(() => {
    if (!courseId) return;
    let active = true;

    learningService
      .openCourse(courseId)
      .then((data) => {
        if (!active) return;
        // Derive sequential access locally as a defensive guard for older
        // deployments or cached responses that omit the `locked` flag.
        const modules = applySequentialLocks(data.modules);
        const availableBlockIds = new Set(modules.flatMap((module) => module.blocks.map((block) => block.id)));
        const savedBlockId = readLocalStorageValue<string>(courseSelectionStorageKey(data.courseId));
        const requestedBlock = requestedAiBlockId
          ? modules.flatMap((module) => module.blocks).find((block) => block.id === requestedAiBlockId)
          : undefined;
        const requestedBlockIsAvailable = Boolean(requestedBlock && !requestedBlock.locked);
        const initialBlockId =
          requestedBlockIsAvailable
            ? requestedAiBlockId
            : savedBlockId && availableBlockIds.has(savedBlockId)
              ? savedBlockId
              : data.currentLessonBlockId ?? modules[0]?.blocks[0]?.id ?? null;
        const initialBlock = modules.flatMap((module) => module.blocks).find((block) => block.id === initialBlockId);
        setLearning({ ...data, modules });
        setSelectedBlockId(initialBlockId);
        setAiChatOpen(requestedBlockIsAvailable);
        setSelectedContentLoading(initialBlock?.type === 'VIDEO');
      })
      .catch((err) => {
        if (!active) return;
        if (isAxiosError(err) && err.response?.status === 403) {
          const code = err.response.data?.messageCode;
          if (code === 'LEARNING_ACCESS_EXPIRED' || code === 'LEARNING_REFUND_PENDING') {
            setError(err.response.data?.message || 'Khóa học hiện chưa thể truy cập.');
          } else {
            // SRS 3a: no active enrollment -> redirect to course detail
            navigate(`/courses/${courseId}`, { replace: true });
          }
          return;
        }
        setError('Không thể tải nội dung khoá học. Vui lòng thử lại.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [courseId, navigate, requestedAiBlockId]);

  useEffect(() => {
    if (!courseId || !selectedBlockId) return;
    writeLocalStorageValue(courseSelectionStorageKey(courseId), selectedBlockId);
  }, [courseId, selectedBlockId]);

  const allBlocks = useMemo(
    () => (learning ? learning.modules.flatMap((module) => module.blocks) : []),
    [learning],
  );

  const selectedBlock = useMemo(
    () => allBlocks.find((block) => block.id === selectedBlockId) ?? null,
    [allBlocks, selectedBlockId],
  );

  const selectedIndex = selectedBlock ? allBlocks.findIndex((block) => block.id === selectedBlock.id) : -1;

  const updateBlock = useCallback((blockId: string, patch: Partial<LearningLessonBlock>) => {
    setLearning((prev) => {
      if (!prev) return prev;
      const modules = prev.modules.map((module) => ({
        ...module,
        blocks: module.blocks.map((block) => (block.id === blockId ? { ...block, ...patch } : block)),
      }));
      const unlockedModules = applySequentialLocks(modules);
      const blocks = unlockedModules.flatMap((module) => module.blocks);
      const completedLessons = blocks.filter((block) => block.progressStatus === 'COMPLETED').length;
      const totalLessons = blocks.length;
      return {
        ...prev,
        modules: unlockedModules,
        completedLessons,
        progressPercent: totalLessons === 0 ? 0 : Math.round((completedLessons * 10000) / totalLessons) / 100,
        courseCompleted: totalLessons > 0 && completedLessons === totalLessons,
      };
    });
  }, []);

  const syncedCompletedBlocksRef = useRef(new Set<string>());

  const refreshLearningAfterCompletion = useCallback(async (
    completedBlockId: string,
    preferredBlockId: string = completedBlockId,
  ) => {
    if (!courseId || syncedCompletedBlocksRef.current.has(completedBlockId)) return;

    syncedCompletedBlocksRef.current.add(completedBlockId);
    setSelectedContentLoading(true);
    try {
      const data = await learningService.openCourse(courseId);
      const modules = applySequentialLocks(data.modules);
      const blocks = modules.flatMap((module) => module.blocks);
      const availableBlockIds = new Set(blocks.map((block) => block.id));
      const nextSelectedBlockId = availableBlockIds.has(preferredBlockId)
        ? preferredBlockId
        : data.currentLessonBlockId && availableBlockIds.has(data.currentLessonBlockId)
          ? data.currentLessonBlockId
          : blocks[0]?.id ?? null;

      setLearning({ ...data, modules });
      setSelectedBlockId(nextSelectedBlockId);
      setError(null);
    } catch {
      syncedCompletedBlocksRef.current.delete(completedBlockId);
      setError('Đã lưu kết quả bài học nhưng chưa thể tải nội dung bài tiếp theo. Vui lòng thử lại.');
    } finally {
      setSelectedContentLoading(false);
    }
  }, [courseId]);

  const handleMarkComplete = async () => {
    if (!selectedBlock || completing || selectedContentLoading || selectedBlock.locked) return;
    if (!selectedBlock.contentAvailable) {
      setError('Nội dung bài học chưa được tải đầy đủ. Vui lòng tải lại trước khi hoàn thành bài.');
      return;
    }
    setCompleting(true);
    try {
      const progress = await learningService.markLessonComplete(selectedBlock.id);
      updateBlock(selectedBlock.id, {
        progressStatus: progress.status,
        completedAt: progress.completedAt,
      });
      const next = allBlocks[selectedIndex + 1];
      await refreshLearningAfterCompletion(selectedBlock.id, next?.id ?? selectedBlock.id);
    } catch {
      setError('Không thể lưu trạng thái hoàn thành. Vui lòng thử lại.');
    } finally {
      setCompleting(false);
    }
  };

  const handleSelectBlock = useCallback((block: LearningLessonBlock) => {
    if (completing || selectedContentLoading || block.locked) return;
    setSelectedContentLoading(block.type === 'VIDEO');
    setSelectedBlockId(block.id);
  }, [completing, selectedContentLoading]);

  const handleVideoProgressSaved = useCallback(
    (
      blockId: string,
      positionSeconds: number,
      status: LearningLessonBlock['progressStatus'],
      watchedVideoSeconds: number,
    ) => {
      updateBlock(blockId, {
        lastVideoPositionSeconds: positionSeconds,
        watchedVideoSeconds,
        progressStatus: status,
      });
      if (status === 'COMPLETED') {
        void refreshLearningAfterCompletion(blockId);
      }
    },
    [refreshLearningAfterCompletion, updateBlock],
  );

  const handleVideoProgressError = useCallback(() => {
    setError('Không thể lưu tiến độ video. Vui lòng kiểm tra kết nối và thử lại.');
  }, []);

  const handleFlashcardProgressSaved = useCallback(
    (blockId: string, cardIndex: number, status: 'REMEMBERED' | 'NEEDS_REVIEW', progressStatus: LearningLessonBlock['progressStatus']) => {
      setLearning((prev) => {
        if (!prev) return prev;
        const modules = prev.modules.map((module) => ({
          ...module,
          blocks: module.blocks.map((block) => {
            if (block.id !== blockId) return block;
            const newStatuses = [...(block.flashcardStatuses || [])];
            newStatuses[cardIndex] = status;
            return { ...block, flashcardStatuses: newStatuses, progressStatus };
          }),
        }));
        const unlockedModules = applySequentialLocks(modules);

        const blocks = unlockedModules.flatMap((module) => module.blocks);
        const completedLessons = blocks.filter((block) => block.progressStatus === 'COMPLETED').length;
        const totalLessons = blocks.length;
        return {
          ...prev,
          modules: unlockedModules,
          completedLessons,
          progressPercent: totalLessons === 0 ? 0 : Math.round((completedLessons * 10000) / totalLessons) / 100,
          courseCompleted: totalLessons > 0 && completedLessons === totalLessons,
        };
      });
      if (progressStatus === 'COMPLETED') {
        void refreshLearningAfterCompletion(blockId);
      }
    },
    [refreshLearningAfterCompletion],
  );

  const handleWritingProgressSaved = useCallback((blockId: string) => {
    setLearning((prev) => {
      if (!prev) return prev;
      const newModules = prev.modules.map((m) => ({
        ...m,
        blocks: m.blocks.map((b) => {
          if (b.id !== blockId) return b;
          return {
            ...b,
            progressStatus: 'COMPLETED' as const,
          };
        }),
      }));
      const unlockedModules = applySequentialLocks(newModules);
      const newCompleted = unlockedModules
        .flatMap((m) => m.blocks)
        .filter((b) => b.progressStatus === 'COMPLETED').length;
      const newTotal = unlockedModules.flatMap((m) => m.blocks).length;
      return {
        ...prev,
        modules: unlockedModules,
        completedLessons: newCompleted,
        progressPercent: newTotal > 0 ? Math.round((newCompleted * 10000) / newTotal) / 100 : 0,
        courseCompleted: newTotal > 0 && newCompleted === newTotal,
      };
    });
    void refreshLearningAfterCompletion(blockId);
  }, [refreshLearningAfterCompletion]);

  const handleQuizProgressSaved = useCallback(
    (blockId: string, status: LearningLessonBlock['progressStatus']) => {
      updateBlock(blockId, { progressStatus: status });
      if (status === 'COMPLETED') {
        void refreshLearningAfterCompletion(blockId);
      }
    },
    [refreshLearningAfterCompletion, updateBlock],
  );

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error && !learning) {
    return (
      <Box sx={{ p: 3, maxWidth: 720, mx: 'auto' }}>
        <Alert severity="error">{error}</Alert>
        <Button sx={{ mt: 2 }} variant="outlined" onClick={() => navigate(`/courses/${courseId}`)}>
          {t('Xem khóa học / gia hạn')}
        </Button>
      </Box>
    );
  }

  if (!learning) return null;

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(ROUTES.STUDENT.MY_COURSES)}>
          {t('Khóa học của tôi')}
        </Button>
        <Typography variant="h5" noWrap sx={{ fontWeight: 700, flexGrow: 1 }}>
          {learning.courseTitle}
        </Typography>
        {learning.courseCompleted && <Chip icon={<DoneAllIcon />} label={t('Đã hoàn thành khoá học')} color="success" />}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <LinearProgress
          variant="determinate"
          value={learning.progressPercent}
          color={learning.courseCompleted ? 'success' : 'primary'}
          sx={{ flexGrow: 1, height: 10, borderRadius: 5 }}
        />
        <Typography variant="body2" color="text.secondary" sx={{ minWidth: 110 }}>
          {learning.completedLessons}/{learning.totalLessons} bài · {learning.progressPercent.toFixed(0)}%
        </Typography>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {learning.warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {learning.warnings.map((warning) => (
            <div key={warning}>{warning}</div>
          ))}
        </Alert>
      )}

      <FinalTestPanel
        courseId={learning.courseId}
        completedLessons={learning.completedLessons}
      />

      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
        <Paper variant="outlined" sx={{ width: { xs: '100%', md: 360 }, flexShrink: 0, alignSelf: 'flex-start' }}>
          {learning.modules.map((module) => (
            <Box key={module.id}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, px: 2, pt: 2, pb: 1, color: '#1F2937' }}>
                {module.orderIndex}. {module.title}
              </Typography>
              <List dense disablePadding>
                {module.blocks.map((block) => (
                  <ListItemButton
                    key={block.id}
                    selected={block.id === selectedBlockId}
                    disabled={block.locked || completing || selectedContentLoading}
                    onClick={() => handleSelectBlock(block)}
                    sx={{
                      alignItems: 'flex-start',
                      py: 1.25,
                      '&.Mui-disabled': { opacity: 0.82 },
                      '&.Mui-disabled .MuiListItemText-primary, &.Mui-disabled .MuiListItemText-secondary': {
                        color: '#4B5563',
                      },
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: 34 }}>
                      {block.locked ? (
                        <LockOutlinedIcon color="disabled" fontSize="small" />
                      ) : block.progressStatus === 'COMPLETED' ? (
                        <CheckCircleIcon color="success" fontSize="small" />
                      ) : block.progressStatus === 'IN_PROGRESS' ? (
                        <PlayCircleOutlineIcon color="primary" fontSize="small" />
                      ) : (
                        <RadioButtonUncheckedIcon fontSize="small" sx={{ color: 'text.disabled' }} />
                      )}
                    </ListItemIcon>
                    <ListItemText
                      disableTypography
                      primary={
                        <Tooltip title={block.title} placement="top-start">
                          <Typography variant="body2" sx={{ color: '#1F2937', whiteSpace: 'normal', overflowWrap: 'anywhere', lineHeight: 1.3 }}>
                            {block.title}
                          </Typography>
                        </Tooltip>
                      }
                      secondary={
                        <Typography variant="caption" sx={{ color: '#4B5563' }} component="div">
                          {blockTypeLabel(block, t)}
                        </Typography>
                      }
                    />
                  </ListItemButton>
                ))}
              </List>
              <Divider />
            </Box>
          ))}
        </Paper>

        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Box
            sx={{
              display: 'flex',
              gap: 2,
              alignItems: 'flex-start',
              flexDirection: { xs: 'column', lg: 'row' },
            }}
          >
            <Box sx={{ flex: 1, minWidth: 0, width: '100%' }}>
          {selectedBlock ? (
            <Card variant="outlined">
              <CardContent>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
                  <Chip size="small" label={blockTypeLabel(selectedBlock, t)} color={blockTypeColor(selectedBlock)} />
                  <Typography variant="h6" sx={{ fontWeight: 700, flexGrow: 1, overflowWrap: 'anywhere' }}>
                    {selectedBlock.title}
                  </Typography>
                  {canReport && (
                    <Tooltip title={t('Báo cáo nội dung này')}>
                      <IconButton
                        size="small"
                        color="warning"
                        onClick={() => setReportModalOpen(true)}
                      >
                        <ReportProblemOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  {aiChatAvailable && (
                    <Tooltip title={t('Hỏi AI về bài học đang chọn')}>
                      <Button
                        size="small"
                        variant={aiChatOpen ? 'contained' : 'outlined'}
                        startIcon={<SmartToyOutlinedIcon fontSize="small" />}
                        aria-label={t('Hỏi AI về bài học đang chọn')}
                        onClick={() => setAiChatOpen((open) => !open)}
                      >
                        {t(aiChatOpen ? 'Ẩn AI' : 'Hỏi AI')}
                      </Button>
                    </Tooltip>
                  )}
                  {selectedBlock.progressStatus === 'COMPLETED' && (
                    <Chip size="small" icon={<CheckCircleIcon />} label={t('Đã hoàn thành')} color="success" />
                  )}
                </Stack>

                {!selectedBlock.contentAvailable ? (
                  <Alert severity="warning">{t('Nội dung bài học hiện chưa sẵn sàng. Vui lòng quay lại sau.')}</Alert>
                ) : selectedBlock.type === 'WRITING' ? (
                  // Keep the writing prompt, editor, autosave status and submit action
                  // in the page flow; only lesson content gets a bounded scroll area.
                  <BlockContent
                    block={selectedBlock}
                    onContentLoadingChange={setSelectedContentLoading}
                    onVideoProgressSaved={handleVideoProgressSaved}
                    onVideoProgressError={handleVideoProgressError}
                    onQuizProgressSaved={handleQuizProgressSaved}
                    onFlashcardProgressSaved={handleFlashcardProgressSaved}
                    onWritingProgressSaved={handleWritingProgressSaved}
                  />
                ) : (
                  <Box
                    data-testid="lesson-content-scroll"
                    sx={{
                      maxHeight: { xs: '60vh', md: 'calc(100vh - 330px)' },
                      overflowY: 'auto',
                      overflowX: 'hidden',
                      pr: { xs: 0.5, md: 1.5 },
                      overscrollBehavior: 'contain',
                      scrollbarGutter: 'stable',
                    }}
                  >
                    <BlockContent
                      block={selectedBlock}
                      onContentLoadingChange={setSelectedContentLoading}
                      onVideoProgressSaved={handleVideoProgressSaved}
                      onVideoProgressError={handleVideoProgressError}
                      onQuizProgressSaved={handleQuizProgressSaved}
                      onFlashcardProgressSaved={handleFlashcardProgressSaved}
                      onWritingProgressSaved={handleWritingProgressSaved}
                    />
                  </Box>
                )}

                <Divider sx={{ my: 2 }} />
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{
                    justifyContent: 'space-between',
                    position: 'sticky',
                    bottom: 0,
                    zIndex: 2,
                    py: 1,
                    backgroundColor: 'background.paper',
                  }}
                >
                  <Button
                    startIcon={<ArrowBackIcon />}
                    disabled={selectedIndex <= 0 || completing || selectedContentLoading}
                    onClick={() => handleSelectBlock(allBlocks[selectedIndex - 1])}
                  >
                    {t('Bài trước')}
                  </Button>
                  {selectedBlock.type === 'VIDEO' && selectedBlock.progressStatus !== 'COMPLETED' ? (
                    <Chip color="warning" variant="outlined" label={t('Video chưa xem đủ')} />
                  ) : (
                    <Button
                      variant="contained"
                      color="success"
                      startIcon={<CheckCircleIcon />}
                      disabled={
                        completing ||
                        selectedContentLoading ||
                        selectedBlock.progressStatus === 'COMPLETED' ||
                        !selectedBlock.contentAvailable ||
                        ['VIDEO', 'QUIZ', 'FLASHCARD', 'WRITING'].includes(selectedBlock.type)
                      }
                      onClick={handleMarkComplete}
                    >
                      {t(selectedBlock.progressStatus === 'COMPLETED'
                        ? 'Đã hoàn thành'
                        : selectedBlock.type === 'VIDEO'
                        ? 'Xem hết video để hoàn thành'
                        : 'Hoàn thành bài học')}
                    </Button>
                  )}
                  <Button
                    endIcon={<ArrowForwardIcon />}
                    disabled={
                      selectedIndex < 0
                      || selectedIndex >= allBlocks.length - 1
                      || allBlocks[selectedIndex + 1].locked
                      || completing
                      || selectedContentLoading
                    }
                    onClick={() => handleSelectBlock(allBlocks[selectedIndex + 1])}
                  >
                    {t('Bài sau')}
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          ) : learning.modules.length === 0 ? (
            <Alert severity="info">{t('Khoá học chưa có nội dung bài học.')}</Alert>
          ) : null}
            </Box>

            {selectedBlock && (
              <Box
                component="aside"
                aria-label="Trợ lý AI cho bài học"
                sx={{
                  width: { xs: '100%', lg: 360 },
                  flexShrink: 0,
                  display: aiChatOpen ? 'block' : 'none',
                  position: { lg: 'sticky' },
                  top: { lg: 16 },
                }}
              >
                <AiChatPanel
                  courseId={learning.courseId}
                  lessonBlockId={selectedBlock.id}
                  lessonTitle={selectedBlock.title}
                  onClose={() => setAiChatOpen(false)}
                />
              </Box>
            )}
          </Box>
          <Box sx={{ mt: 2 }}>
            <CoursePomodoroPanel courseTitle={learning.courseTitle} />
          </Box>
        </Box>
      </Box>

      {selectedBlock && (
        <ReportViolationModal
          open={reportModalOpen}
          onClose={() => setReportModalOpen(false)}
          targetType="LESSON_BLOCK"
          targetId={selectedBlock.id}
        />
      )}
    </Box>
  );
}

function blockTypeLabel(block: LearningLessonBlock, t: (text: string) => string): string {
  switch (block.type) {
    case 'VIDEO':
      return block.durationMinutes ? `Video · ${block.durationMinutes} ${t('phút')}` : 'Video';
    case 'TEXT':
      return t('Bài đọc');
    case 'QUIZ':
      return t('Trắc nghiệm');
    case 'FLASHCARD':
      return 'Flashcard';
    case 'WRITING':
      return t('Bài viết');
    default:
      return block.type;
  }
}

function blockTypeColor(block: LearningLessonBlock): 'primary' | 'success' | 'warning' | 'info' | 'secondary' {
  switch (block.type) {
    case 'VIDEO':
      return 'primary';
    case 'TEXT':
      return 'info';
    case 'QUIZ':
      return 'warning';
    case 'FLASHCARD':
      return 'success';
    case 'WRITING':
      return 'secondary';
    default:
      return 'primary';
  }
}

export function applySequentialLocks(modules: LearningModule[]): LearningModule[] {
  let waitingForCompletion = false;
  return modules.map((module) => ({
    ...module,
    blocks: module.blocks.map((block) => {
      const locked = waitingForCompletion && block.progressStatus !== 'COMPLETED';
      if (block.progressStatus !== 'COMPLETED') waitingForCompletion = true;
      return { ...block, locked };
    }),
  }));
}

interface BlockContentProps {
  block: LearningLessonBlock;
  onContentLoadingChange: (loading: boolean) => void;
  onVideoProgressSaved: (
    blockId: string,
    positionSeconds: number,
    status: LearningLessonBlock['progressStatus'],
    watchedVideoSeconds: number,
  ) => void;
  onVideoProgressError: () => void;
  onFlashcardProgressSaved: (
    blockId: string,
    cardIndex: number,
    status: 'REMEMBERED' | 'NEEDS_REVIEW',
    progressStatus: LearningLessonBlock['progressStatus'],
  ) => void;
  onQuizProgressSaved: (
    blockId: string,
    progressStatus: LearningLessonBlock['progressStatus'],
  ) => void;
  onWritingProgressSaved: (blockId: string) => void;
}

function BlockContent({
  block,
  onContentLoadingChange,
  onVideoProgressSaved,
  onVideoProgressError,
  onQuizProgressSaved,
  onFlashcardProgressSaved,
  onWritingProgressSaved,
}: BlockContentProps) {
  switch (block.type) {
    case 'VIDEO':
      return (
        <VideoBlock
          key={block.id}
          block={block}
          onProgressSaved={onVideoProgressSaved}
          onProgressSaveError={onVideoProgressError}
          onLoadingChange={onContentLoadingChange}
        />
      );
    case 'TEXT':
      return (
        <Box
          sx={{ whiteSpace: 'pre-wrap', typography: 'body1' }}
          dangerouslySetInnerHTML={{ __html: sanitizeRichText(block.content) }}
        />
      );
    case 'QUIZ':
      return (
        <QuizBlock
          key={block.id}
          blockId={block.id}
          questions={block.quizItems}
          onProgressSaved={onQuizProgressSaved}
        />
      );
    case 'FLASHCARD':
      return <FlashcardBlock key={block.id} block={block} onProgressSaved={onFlashcardProgressSaved} />;
    case 'WRITING':
      return <WritingBlock key={block.id} block={block} onProgressSaved={() => onWritingProgressSaved(block.id)} />;
    default:
      return null;
  }
}

export function VideoBlock({
  block,
  onProgressSaved,
  onProgressSaveError,
  onLoadingChange,
}: {
  block: LearningLessonBlock;
  onProgressSaved: BlockContentProps['onVideoProgressSaved'];
  onProgressSaveError: BlockContentProps['onVideoProgressError'];
  onLoadingChange: BlockContentProps['onContentLoadingChange'];
}) {
  const [checkpoint] = useState(
    () => readLocalStorageValue<VideoProgressCheckpoint>(videoProgressStorageKey(block.id)),
  );
  const [initialPosition] = useState(
    () => resolveVideoResumePosition(block.lastVideoPositionSeconds, checkpoint),
  );
  const initialWatchedSeconds = Math.max(block.watchedVideoSeconds ?? 0, checkpoint?.watchedSeconds ?? 0);
  const [playerLoading, setPlayerLoading] = useState(true);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [currentPosition, setCurrentPosition] = useState(initialPosition);
  const [mediaDuration, setMediaDuration] = useState(
    checkpoint?.mediaDurationSeconds ?? (block.durationMinutes ? block.durationMinutes * 60 : 0),
  );
  const playerRef = useRef<HTMLVideoElement>(null);
  const currentPositionRef = useRef(initialPosition);
  const lastSavedRef = useRef(block.lastVideoPositionSeconds ?? 0);
  const watchedSecondsRef = useRef(initialWatchedSeconds);
  const lastSavedWatchedRef = useRef(block.watchedVideoSeconds ?? 0);
  const mediaDurationRef = useRef<number | null>(null);
  const lastObservedTimeRef = useRef<number | null>(null);
  const isSeekingRef = useRef(false);
  const isReadyRef = useRef(false);

  const pendingPositionRef = useRef<number | null>(null);
  const isSavingRef = useRef(false);

  const storeCheckpoint = useCallback((positionSeconds: number) => {
    const normalizedPosition = Math.max(0, Math.floor(positionSeconds));
    const checkpointToStore: VideoProgressCheckpoint = {
      positionSeconds: normalizedPosition,
      watchedSeconds: Math.max(0, Math.floor(watchedSecondsRef.current)),
      ...(mediaDurationRef.current && mediaDurationRef.current > 0
        ? { mediaDurationSeconds: Math.floor(mediaDurationRef.current) }
        : {}),
      savedAt: Date.now(),
    };
    writeLocalStorageValue(videoProgressStorageKey(block.id), checkpointToStore);
  }, [block.id]);

  useEffect(() => {
    setPlayerLoading(true);
    onLoadingChange(true);
    return () => onLoadingChange(false);
  }, [block.id, onLoadingChange]);

  const savePosition = useCallback(
    (positionSeconds: number) => {
      storeCheckpoint(positionSeconds);
      pendingPositionRef.current = positionSeconds;

      const flush = () => {
        if (isSavingRef.current || pendingPositionRef.current === null) return;

        const positionToSave = pendingPositionRef.current;
        if (
          positionToSave === lastSavedRef.current
          && watchedSecondsRef.current === lastSavedWatchedRef.current
        ) {
          pendingPositionRef.current = null;
          return;
        }

        isSavingRef.current = true;
        pendingPositionRef.current = null;
        let success = false;

        learningService
          .saveVideoProgress(
            block.id,
            positionToSave,
            Math.floor(watchedSecondsRef.current),
            mediaDurationRef.current ?? undefined,
          )
          .then((progress) => {
            lastSavedRef.current = positionToSave;
            lastSavedWatchedRef.current = progress.watchedVideoSeconds ?? watchedSecondsRef.current;
            watchedSecondsRef.current = Math.max(watchedSecondsRef.current, lastSavedWatchedRef.current);
            storeCheckpoint(positionToSave);
            onProgressSaved(
              block.id,
              positionToSave,
              progress.status,
              watchedSecondsRef.current,
            );
            success = true;
          })
          .catch(() => {
            // Non-fatal: keep playing; next tick will retry.
            if (pendingPositionRef.current === null) {
              pendingPositionRef.current = positionToSave;
            }
            onProgressSaveError();
          })
          .finally(() => {
            isSavingRef.current = false;
            if (
              success
              && pendingPositionRef.current !== null
              && (
                pendingPositionRef.current !== lastSavedRef.current
                || Math.floor(watchedSecondsRef.current) !== lastSavedWatchedRef.current
              )
            ) {
              flush();
            }
          });
      };

      flush();
    },
    [block.id, onProgressSaveError, onProgressSaved, storeCheckpoint],
  );

  useEffect(() => {
    const persistBeforeLeaving = () => {
      const position = Math.max(0, Math.floor(currentPositionRef.current));
      storeCheckpoint(position);
      learningService.saveVideoProgressKeepalive(
        block.id,
        position,
        watchedSecondsRef.current,
        mediaDurationRef.current ?? undefined,
      );
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') persistBeforeLeaving();
    };

    window.addEventListener('pagehide', persistBeforeLeaving);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.removeEventListener('pagehide', persistBeforeLeaving);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      persistBeforeLeaving();
    };
  }, [block.id, storeCheckpoint]);

  const handleReady = () => {
    const video = playerRef.current;
    if (video && Number.isFinite(video.duration) && video.duration > 0) {
      mediaDurationRef.current = video.duration;
      setMediaDuration(video.duration);
    }
    if (video && !isReadyRef.current) {
      isReadyRef.current = true;
      const resumePosition = resolveVideoResumePosition(
        block.lastVideoPositionSeconds,
        checkpoint,
        video.duration,
      );
      if (resumePosition > 0) {
        video.currentTime = resumePosition;
        currentPositionRef.current = resumePosition;
        setCurrentPosition(resumePosition);
      }
    }
    if (video) lastObservedTimeRef.current = video.currentTime;
    setPlayerLoading(false);
    onLoadingChange(false);
  };

  const handleWaiting = () => {
    setPlayerLoading(true);
    onLoadingChange(true);
  };

  const handlePlaying = () => {
    setPlayerLoading(false);
    onLoadingChange(false);
  };

  const handleError = () => {
    setPlayerLoading(false);
    onLoadingChange(false);
  };

  const handlePlay = () => {
    lastObservedTimeRef.current = playerRef.current?.currentTime ?? null;
  };

  const handleSeeking = () => {
    isSeekingRef.current = true;
  };

  const handleSeeked = () => {
    isSeekingRef.current = false;
    lastObservedTimeRef.current = playerRef.current?.currentTime ?? null;
  };

  const handleVolumeChange = (event: React.SyntheticEvent<HTMLVideoElement>) => {
    const video = event.currentTarget;
    // ReactPlayer reapplies audio props on every render, including progress updates.
    if (Number.isFinite(video.volume) && video.volume >= 0 && video.volume <= 1) {
      setVolume(video.volume);
    }
    setMuted(video.muted);
  };

  const handleTimeUpdate = (e: React.SyntheticEvent<HTMLVideoElement>) => {
    const video = e.currentTarget;
    if (Number.isFinite(video.duration) && video.duration > 0) {
      mediaDurationRef.current = video.duration;
    }
    if (video.paused) {
      lastObservedTimeRef.current = video.currentTime;
      return;
    }
    const position = Math.floor(video.currentTime);
    currentPositionRef.current = video.currentTime;
    setCurrentPosition(video.currentTime);
    const previousPosition = lastObservedTimeRef.current;
    if (!isSeekingRef.current && previousPosition != null) {
      const delta = video.currentTime - previousPosition;
      if (delta > 0 && delta <= WATCHED_DELTA_MAX_SECONDS) {
        watchedSecondsRef.current += delta;
      }
    }
    lastObservedTimeRef.current = video.currentTime;
    if (
      position - lastSavedRef.current >= VIDEO_SAVE_INTERVAL_SECONDS
      || watchedSecondsRef.current - lastSavedWatchedRef.current >= VIDEO_SAVE_INTERVAL_SECONDS
    ) {
      savePosition(position);
    }
  };

  const handlePause = () => {
    const player = playerRef.current;
    if (!player || player.ended) return;
    const position = Math.floor(player.currentTime);
    currentPositionRef.current = player.currentTime;
    setCurrentPosition(player.currentTime);
    if (
      position !== lastSavedRef.current
      || watchedSecondsRef.current !== lastSavedWatchedRef.current
    ) {
      savePosition(position);
    }
  };

  const handleEnded = () => {
    const video = playerRef.current;
    if (!video) return;
    if (Number.isFinite(video.duration) && video.duration > 0) {
      mediaDurationRef.current = video.duration;
      setMediaDuration(video.duration);
    }
    watchedSecondsRef.current = normalizeWatchedSecondsAtVideoEnd(
      watchedSecondsRef.current,
      video.duration,
    );
    savePosition(Math.floor(video.currentTime));
  };

  const progressPercent = mediaDuration > 0
    ? Math.min(100, Math.max(0, (currentPosition / mediaDuration) * 100))
    : 0;

  return (
    <Box>
      <Box
        sx={{ position: 'relative', paddingTop: '56.25%', background: '#000', borderRadius: 2, overflow: 'hidden', userSelect: 'none' }}
        onContextMenu={(event) => event.preventDefault()}
        onDragStart={(event) => event.preventDefault()}
      >
        {playerLoading && (
          <Stack
            spacing={1}
            sx={{
              position: 'absolute',
              inset: 0,
              zIndex: 1,
              alignItems: 'center',
              justifyContent: 'center',
              color: 'common.white',
              bgcolor: 'rgba(0, 0, 0, 0.55)',
              pointerEvents: 'all',
            }}
          >
            <CircularProgress color="inherit" size={28} aria-label="Đang tải video" />
            <Typography variant="body2">Đang tải bài học…</Typography>
          </Stack>
        )}
        <ReactPlayer
          ref={playerRef}
          src={block.videoUrl}
          controls
          volume={volume}
          muted={muted}
          width="100%"
          height="100%"
          style={{ position: 'absolute', top: 0, left: 0 }}
          onReady={handleReady}
          onLoadedMetadata={handleReady}
          onWaiting={handleWaiting}
          onPlaying={handlePlaying}
          onError={handleError}
          onPlay={handlePlay}
          onSeeking={handleSeeking}
          onSeeked={handleSeeked}
          onVolumeChange={handleVolumeChange}
          onTimeUpdate={handleTimeUpdate}
          onPause={handlePause}
          onEnded={handleEnded}
        />
      </Box>
      <Stack spacing={0.75} sx={{ mt: 1.5 }}>
        <LinearProgress
          variant="determinate"
          value={progressPercent}
          aria-label="Tiến trình xem video"
          sx={{ height: 7, borderRadius: 999 }}
        />
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            Đã xem {formatVideoTime(currentPosition)} / {mediaDuration > 0 ? formatVideoTime(mediaDuration) : '--:--'}
          </Typography>
          {initialPosition > 0 && (
            <Typography variant="caption" color="text.secondary">
              Tiếp tục từ {formatVideoTime(initialPosition)}
            </Typography>
          )}
        </Stack>
      </Stack>
      {block.content && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
          {block.content}
        </Typography>
      )}
    </Box>
  );
}

function QuizBlock({
  blockId,
  questions,
  onProgressSaved,
}: {
  blockId: string;
  questions: QuizQuestion[];
  onProgressSaved: BlockContentProps['onQuizProgressSaved'];
}) {
  const [answers, setAnswers] = useState<string[]>(() => {
    const savedAnswers = readLocalStorageValue<unknown>(quizAnswersStorageKey(blockId));
    if (Array.isArray(savedAnswers) && savedAnswers.length === questions.length) {
      return savedAnswers.map((answer) => typeof answer === 'string' ? answer : '');
    }
    return questions.map(() => '');
  });
  const [result, setResult] = useState<QuizSubmissionResult | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (result) return;
    writeLocalStorageValue(quizAnswersStorageKey(blockId), answers);
  }, [answers, blockId, result]);

  if (questions.length === 0) {
    return <Alert severity="info">Bài trắc nghiệm chưa có câu hỏi.</Alert>;
  }

  const handleSubmit = async () => {
    if (answers.some((answer) => !answer) || submitting) return;
    setSubmitting(true);
    setErrorMsg(null);
    try {
      const nextResult = await learningService.submitQuiz(blockId, answers);
      setResult(nextResult);
      removeLocalStorageValue(quizAnswersStorageKey(blockId));
      onProgressSaved(blockId, nextResult.progressStatus);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        setErrorMsg(error.response?.data?.message || 'Không thể nộp bài trắc nghiệm.');
      } else {
        setErrorMsg('Không thể nộp bài trắc nghiệm.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      {result && (
        <Alert severity={result.passed ? 'success' : 'warning'}>
          Kết quả: {result.score.toFixed(0)}% ({result.correctCount}/{result.totalQuestions} câu đúng).
          {result.passed ? ' Bài học đã hoàn thành.' : ' Cần đạt tối thiểu 80% để hoàn thành bài học.'}
        </Alert>
      )}
      {errorMsg && <Alert severity="error">{errorMsg}</Alert>}
      {questions.map((question, index) => (
        <Paper key={index} variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle1" gutterBottom sx={{ fontWeight: 600 }}>
            Câu {index + 1}: {question.question}
          </Typography>
          <Stack>
            {question.options.map((option) => {
              const feedback = result?.feedback.find((item) => item.questionIndex === index);
              return (
                <Stack
                  key={option}
                  direction="row"
                  onClick={() => {
                    if (!submitting) {
                      setAnswers((current) => current.map((value, answerIndex) => (
                        answerIndex === index ? option : value
                      )));
                      setResult(null);
                    }
                  }}
                  sx={{
                    alignItems: 'center',
                    borderRadius: 1,
                    px: 1,
                    cursor: submitting ? 'default' : 'pointer',
                    bgcolor: feedback && option === feedback.correctAnswer ? 'success.50' : 'transparent',
                  }}
                >
                  <Radio
                    checked={answers[index] === option}
                    disabled={submitting}
                    size="small"
                    slotProps={{ input: { 'aria-label': option } }}
                  />
                  <Typography variant="body2" color="text.secondary">
                    {option}
                  </Typography>
                </Stack>
              );
            })}
          </Stack>
          {result?.feedback[index] && (
            <Typography
              variant="caption"
              color={result.feedback[index].correct ? 'success.main' : 'warning.main'}
            >
              {result.feedback[index].correct
                ? 'Trả lời đúng.'
                : `Đáp án đúng: ${result.feedback[index].correctAnswer}`}
            </Typography>
          )}
        </Paper>
      ))}
      <Box>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={submitting || answers.some((answer) => !answer)}
        >
          {submitting ? 'Đang chấm...' : 'Nộp bài trắc nghiệm'}
        </Button>
      </Box>
    </Stack>
  );
}

function FinalTestPanel({
  courseId,
  completedLessons,
}: {
  courseId: string;
  completedLessons: number;
}) {
  const [eligibility, setEligibility] = useState<FinalTestEligibility | null>(null);
  const [progressSummary, setProgressSummary] = useState<CourseProgressSummary | null>(null);
  const [certificate, setCertificate] = useState<LearningCertificate | null>(null);
  const [attempt, setAttempt] = useState<FinalTestAttempt | null>(() => {
    const saved = readLocalStorageValue<LocalFinalTestState>(finalTestStorageKey(courseId));
    return saved?.attempt ?? null;
  });
  const [answers, setAnswers] = useState<Record<string, string[]>>(() => {
    const saved = readLocalStorageValue<LocalFinalTestState>(finalTestStorageKey(courseId));
    return saved?.answers ?? {};
  });
  const [result, setResult] = useState<FinalTestSubmissionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [issuingCertificate, setIssuingCertificate] = useState(false);
  const [downloadingCertificate, setDownloadingCertificate] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [violations, setViolations] = useState<FinalTestViolationType[]>([]);
  const [proctoringTerminated, setProctoringTerminated] = useState(false);
  const violationCountRef = useRef(0);
  const lastViolationAtRef = useRef(0);
  const terminatingForViolationRef = useRef(false);
  const certificateIssuanceInFlightRef = useRef(false);

  const handleDownloadCertificate = async () => {
    if (!certificate || downloadingCertificate) return;
    setDownloadingCertificate(true);
    setErrorMsg(null);
    try {
      await downloadCertificatePdf(certificate);
    } catch {
      setErrorMsg('Không thể tạo tệp chứng chỉ PDF. Vui lòng thử lại.');
    } finally {
      setDownloadingCertificate(false);
    }
  };

  const loadEligibility = useCallback(async () => {
    try {
      const [finalTestValue, progressValue, certificateValue] = await Promise.all([
        learningService.getFinalTestEligibility(courseId),
        learningService.getCourseProgress(courseId),
        learningService.getCertificate(courseId),
      ]);
      setEligibility(finalTestValue);
      setProgressSummary(progressValue);
      setCertificate(certificateValue);
      if (!finalTestValue.eligible || finalTestValue.passed) {
        setAttempt(null);
        setAnswers({});
        removeLocalStorageValue(finalTestStorageKey(courseId));
      }
    } catch {
      setErrorMsg('Không thể kiểm tra điều kiện làm Final Test.');
    } finally {
      setLoading(false);
    }
  }, [courseId]);

  useEffect(() => {
    setLoading(true);
    void loadEligibility();
  }, [completedLessons, loadEligibility]);

  useEffect(() => {
    if (!attempt || result) return;
    const updateTimer = () => {
      setSecondsLeft(Math.max(0, Math.ceil((new Date(attempt.expiresAt).getTime() - Date.now()) / 1000)));
    };
    updateTimer();
    const timer = window.setInterval(updateTimer, 1000);
    return () => window.clearInterval(timer);
  }, [attempt, result]);

  useEffect(() => {
    if (!attempt || result || secondsLeft !== 0) return;
    if (new Date(attempt.expiresAt).getTime() > Date.now()) return;

    setErrorMsg('Lượt thi đã hết thời gian. Hãy bắt đầu lượt tiếp theo nếu vẫn còn lượt.');
    setAttempt(null);
    setAnswers({});
    removeLocalStorageValue(finalTestStorageKey(courseId));
    void loadEligibility();
  }, [attempt, courseId, loadEligibility, result, secondsLeft]);

  useEffect(() => {
    if (!attempt || result) {
      removeLocalStorageValue(finalTestStorageKey(courseId));
      return;
    }
    if (new Date(attempt.expiresAt).getTime() <= Date.now()) return;
    writeLocalStorageValue(finalTestStorageKey(courseId), { attempt, answers } satisfies LocalFinalTestState);
  }, [answers, attempt, courseId, result]);

  useEffect(() => {
    if (!attempt?.attemptId) return;
    violationCountRef.current = 0;
    lastViolationAtRef.current = 0;
    terminatingForViolationRef.current = false;
    setViolations([]);
    setProctoringTerminated(false);
  }, [attempt?.attemptId]);

  const terminateForViolations = useCallback(async () => {
    if (!attempt || terminatingForViolationRef.current) return;
    terminatingForViolationRef.current = true;
    setWorking(true);
    try {
      await learningService.terminateFinalTest(courseId, attempt.attemptId);
      setAttempt(null);
      setAnswers({});
      setProctoringTerminated(true);
      setErrorMsg('Bài thi đã dừng vì phát hiện quá nhiều thao tác không hợp lệ. Lượt thi này đã được tính.');
      await loadEligibility();
    } catch (error) {
      terminatingForViolationRef.current = false;
      setErrorMsg(
        axios.isAxiosError(error)
          ? error.response?.data?.message || 'Không thể khóa lượt thi sau cảnh báo.'
          : 'Không thể khóa lượt thi sau cảnh báo.',
      );
    } finally {
      setWorking(false);
    }
  }, [attempt, courseId, loadEligibility]);

  const recordViolation = useCallback((type: FinalTestViolationType) => {
    if (!attempt || result || terminatingForViolationRef.current) return;
    const now = Date.now();
    // One tab switch can fire both blur and visibilitychange; count it once.
    if (now - lastViolationAtRef.current < 1000) return;
    lastViolationAtRef.current = now;
    violationCountRef.current += 1;
    setViolations((current) => [...current, type]);
    if (violationCountRef.current >= FINAL_TEST_MAX_VIOLATIONS) {
      void terminateForViolations();
    }
  }, [attempt, result, terminateForViolations]);

  useEffect(() => {
    if (!attempt || result) return;

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') recordViolation('TAB_SWITCH');
    };
    const handleWindowBlur = () => recordViolation('WINDOW_BLUR');
    const handleShortcut = (event: KeyboardEvent) => {
      if (isScreenshotShortcut(event)) {
        event.preventDefault();
        recordViolation('SCREENSHOT_SHORTCUT');
      } else if (isClipboardShortcut(event)) {
        event.preventDefault();
        recordViolation('CLIPBOARD');
      }
    };
    const handlePrint = () => recordViolation('PRINT_ATTEMPT');

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('blur', handleWindowBlur);
    document.addEventListener('keydown', handleShortcut, true);
    window.addEventListener('beforeprint', handlePrint);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('blur', handleWindowBlur);
      document.removeEventListener('keydown', handleShortcut, true);
      window.removeEventListener('beforeprint', handlePrint);
    };
  }, [attempt, recordViolation, result]);

  const handleStart = async () => {
    setWorking(true);
    setErrorMsg(null);
    try {
      const value = await learningService.startFinalTest(courseId);
      setSecondsLeft(Math.max(0, Math.ceil((new Date(value.expiresAt).getTime() - Date.now()) / 1000)));
      setAttempt(value);
      setAnswers({});
      setResult(null);
      setProctoringTerminated(false);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        setErrorMsg(error.response?.data?.message || 'Không thể bắt đầu Final Test.');
      } else {
        setErrorMsg('Không thể bắt đầu Final Test.');
      }
      await loadEligibility();
    } finally {
      setWorking(false);
    }
  };

  const toggleChoice = (questionId: string, choiceId: string) => {
    setAnswers((current) => {
      const selected = current[questionId] || [];
      return {
        ...current,
        [questionId]: selected.includes(choiceId)
          ? selected.filter((value) => value !== choiceId)
          : [...selected, choiceId],
      };
    });
  };

  const handleSubmit = async () => {
    if (!attempt || working || secondsLeft <= 0) return;
    setWorking(true);
    setErrorMsg(null);
    try {
      const value = await learningService.submitFinalTest(
        courseId,
        attempt.attemptId,
        attempt.questions.map((question) => ({
          questionId: question.id,
          selectedChoiceIds: answers[question.id] || [],
        })),
      );
      setResult(value);
      removeLocalStorageValue(finalTestStorageKey(courseId));
      await loadEligibility();
    } catch (error) {
      if (axios.isAxiosError(error)) {
        setErrorMsg(error.response?.data?.message || 'Không thể nộp Final Test.');
      } else {
        setErrorMsg('Không thể nộp Final Test.');
      }
    } finally {
      setWorking(false);
    }
  };

  const handleGenerateCertificate = useCallback(async () => {
    if (certificate || certificateIssuanceInFlightRef.current) return;
    certificateIssuanceInFlightRef.current = true;
    setIssuingCertificate(true);
    setErrorMsg(null);
    try {
      const value = await learningService.generateCertificate(courseId);
      setCertificate(value);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        setErrorMsg(error.response?.data?.message || 'Không thể phát hành chứng chỉ.');
      } else {
        setErrorMsg('Không thể phát hành chứng chỉ.');
      }
    } finally {
      certificateIssuanceInFlightRef.current = false;
      setIssuingCertificate(false);
    }
  }, [certificate, courseId]);

  useEffect(() => {
    if (!progressSummary?.certificateEligibility.eligible || certificate) return;
    void handleGenerateCertificate();
  }, [certificate, handleGenerateCertificate, progressSummary?.certificateEligibility.eligible]);

  const allAnswered = attempt?.questions.every((question) => (answers[question.id]?.length || 0) > 0) ?? false;
  const reasonText: Record<string, string> = {
    FINAL_TEST_NOT_CONFIGURED: 'Khoá học chưa cấu hình Final Test.',
    FINAL_TEST_ALREADY_PASSED: 'Bạn đã vượt qua Final Test.',
    LESSONS_INCOMPLETE: 'Hoàn thành tất cả bài học để mở Final Test.',
    ATTEMPTS_EXHAUSTED: 'Bạn đã sử dụng hết số lần thi.',
  };
  const certificateReasonText: Record<string, string> = {
    PROGRESS_INCOMPLETE: 'Hoàn thành toàn bộ nội dung khoá học.',
    ASSIGNMENTS_INCOMPLETE: 'Nộp đầy đủ các bài Writing bắt buộc.',
    EXERCISE_AVERAGE_BELOW_85: 'Điểm Quiz trung bình cần đạt tối thiểu 85%.',
    FINAL_TEST_NOT_PASSED: 'Vượt qua Final Test.',
  };

  if (loading) {
    return <LinearProgress sx={{ mb: 2 }} />;
  }

  return (
    <Box sx={{ borderBlock: '1px solid', borderColor: 'divider', py: 2, mb: 3 }}>
      <Stack spacing={2}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1}
          sx={{ alignItems: { xs: 'flex-start', sm: 'center' } }}
        >
          <AssignmentTurnedInOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 700, flexGrow: 1 }}>
            Final Test
          </Typography>
          {eligibility?.configured && (
            <Chip
              size="small"
              label={`${eligibility.attemptsUsed}/${eligibility.attemptsAllowed} lượt đã dùng`}
              variant="outlined"
            />
          )}
          {progressSummary && (
            <Chip
              size="small"
              color={progressSummary.certificateEligibility.eligible ? 'success' : 'default'}
              label={progressSummary.certificateEligibility.eligible
                ? 'Đủ điều kiện chứng chỉ'
                : 'Chứng chỉ đang khoá'}
              variant={progressSummary.certificateEligibility.eligible ? 'filled' : 'outlined'}
            />
          )}
        </Stack>

        {errorMsg && <Alert severity="error">{errorMsg}</Alert>}

        {attempt && !result && (
          <Alert severity={violations.length === 0 ? 'info' : 'warning'}>
            <strong>Chế độ giám sát bài thi:</strong> copy/cut/paste, kéo-thả, menu chuột phải,
            chuyển tab, rời cửa sổ và các phím chụp màn hình phổ biến đều bị chặn hoặc ghi nhận.
            Cảnh báo: {violations.length}/{FINAL_TEST_MAX_VIOLATIONS}.
            {violations.length > 0 && (
              <Typography component="div" variant="body2" sx={{ mt: 0.5 }}>
                Gần nhất: {violationLabel(violations[violations.length - 1])}.
              </Typography>
            )}
            <Typography component="div" variant="caption" sx={{ display: 'block', mt: 0.5 }}>
              Trình duyệt không thể phát hiện tuyệt đối ảnh chụp bằng công cụ hệ điều hành; hệ thống chỉ
              nhận diện phím tắt và tín hiệu rời trang.
            </Typography>
          </Alert>
        )}

        {proctoringTerminated && (
          <Alert severity="error">
            Bài thi đã bị dừng và lượt thi đã được tính do vượt quá giới hạn cảnh báo integrity.
          </Alert>
        )}

        {progressSummary && !progressSummary.certificateEligibility.eligible && (
          <Alert severity="info">
            Điều kiện chứng chỉ còn thiếu:{' '}
            {progressSummary.certificateEligibility.reasons
              .map((reason) => certificateReasonText[reason] || reason)
              .join(' ')}
          </Alert>
        )}

        {progressSummary?.certificateEligibility.eligible && !certificate && (
          <Alert
            severity="success"
            icon={<WorkspacePremiumOutlinedIcon />}
            action={
            <Button
              color="inherit"
              onClick={() => void handleGenerateCertificate()}
              disabled={issuingCertificate}
            >
              {issuingCertificate ? 'Đang tạo...' : 'Thử lại'}
            </Button>
            }
          >
            {issuingCertificate
              ? 'Bạn đã hoàn thành khóa học. Chứng chỉ đang được phát hành tự động theo thời gian thực.'
              : 'Bạn đã hoàn thành khóa học. Hệ thống đang chuẩn bị chứng chỉ của bạn.'}
          </Alert>
        )}

        {certificate && (
          <Paper
            variant="outlined"
            sx={{
              p: { xs: 2, sm: 3 },
              textAlign: 'center',
              borderColor: '#c8a45a',
              borderWidth: 2,
              background: 'linear-gradient(135deg, #fffdf7 0%, #ffffff 52%, #fff8eb 100%)',
            }}
          >
            <WorkspacePremiumOutlinedIcon sx={{ fontSize: 48, color: '#c91f3d' }} />
            <Typography variant="overline" sx={{ color: '#8f1028', fontWeight: 700, letterSpacing: 1.5 }}>
              Chứng chỉ hoàn thành ManabiHub
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 700, my: 1 }}>
              {certificate.studentName}
            </Typography>
            <Typography variant="body1">
              Đã hoàn thành khoá học <strong>{certificate.courseTitle}</strong>
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Hoàn thành ngày {formatCertificateDate(certificate.completedAt, certificate.issuedAt)} · {certificate.certificateNumber}
            </Typography>
            <Button
              sx={{ mt: 2 }}
              variant="contained"
              startIcon={<DownloadOutlinedIcon />}
              onClick={() => void handleDownloadCertificate()}
              disabled={downloadingCertificate}
            >
              {downloadingCertificate ? 'Đang tạo PDF...' : 'Tải chứng chỉ PDF'}
            </Button>
          </Paper>
        )}

        {!attempt && eligibility && (
          <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
            <Alert severity={eligibility.passed ? 'success' : eligibility.eligible ? 'info' : 'warning'} sx={{ width: '100%' }}>
              {eligibility.eligible
                ? 'Bạn đã hoàn thành nội dung bắt buộc và có thể bắt đầu Final Test.'
                : reasonText[eligibility.reason || ''] || 'Bạn chưa đủ điều kiện làm Final Test.'}
            </Alert>
            {eligibility.eligible && (
              <Button variant="contained" onClick={handleStart} disabled={working}>
                {working ? 'Đang bắt đầu...' : 'Bắt đầu Final Test'}
              </Button>
            )}
          </Stack>
        )}

        {attempt && !result && (
          <Stack
            spacing={2}
            sx={{ userSelect: 'none' }}
            onCopy={(event) => { event.preventDefault(); recordViolation('CLIPBOARD'); }}
            onCut={(event) => { event.preventDefault(); recordViolation('CLIPBOARD'); }}
            onPaste={(event) => { event.preventDefault(); recordViolation('CLIPBOARD'); }}
            onDragStart={(event) => { event.preventDefault(); recordViolation('DRAG_DROP'); }}
            onDragOver={(event) => { event.preventDefault(); }}
            onDrop={(event) => { event.preventDefault(); recordViolation('DRAG_DROP'); }}
            onContextMenu={(event) => { event.preventDefault(); recordViolation('CONTEXT_MENU'); }}
          >
            <Alert severity={secondsLeft > 0 ? 'info' : 'error'}>
              Điểm đạt: {attempt.passingScore}% · Thời gian còn lại: {Math.floor(secondsLeft / 60)}:
              {String(secondsLeft % 60).padStart(2, '0')}
            </Alert>
            {attempt.questions.map((question, questionIndex) => (
              <Paper key={question.id} variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
                  Câu {questionIndex + 1}: {question.content}
                </Typography>
                <Stack>
                  {question.choices.map((choice) => (
                    <Stack
                      key={choice.id}
                      direction="row"
                      onClick={() => !working && toggleChoice(question.id, choice.id)}
                      sx={{ alignItems: 'center', cursor: working ? 'default' : 'pointer' }}
                    >
                      <Checkbox
                        checked={(answers[question.id] || []).includes(choice.id)}
                        disabled={working}
                        slotProps={{ input: { 'aria-label': choice.content } }}
                      />
                      <Typography variant="body2">{choice.content}</Typography>
                    </Stack>
                  ))}
                </Stack>
              </Paper>
            ))}
            <Box>
              <Button
                variant="contained"
                color="success"
                onClick={handleSubmit}
                disabled={working || !allAnswered || secondsLeft <= 0}
              >
                {working ? 'Đang chấm...' : 'Nộp Final Test'}
              </Button>
            </Box>
          </Stack>
        )}

        {result && (
          <Stack spacing={2}>
            <Alert severity={result.passed ? 'success' : 'error'}>
              Kết quả: {result.score.toFixed(0)}% ({result.correctCount}/{result.totalQuestions} câu đúng).
              {result.passed
                ? ' Bạn đã vượt qua Final Test.'
                : ' Chưa đạt. Chứng chỉ vẫn bị khoá.'}
            </Alert>
            {result.feedback.map((item, index) => (
              <Box key={item.questionId}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Câu {index + 1}: {item.correct ? 'Đúng' : 'Chưa đúng'}
                </Typography>
                {item.explanation && (
                  <Typography variant="body2" color="text.secondary">
                    {item.explanation}
                  </Typography>
                )}
              </Box>
            ))}
            {!result.passed && eligibility?.eligible && (
              <Box>
                <Button variant="outlined" onClick={handleStart} disabled={working}>
                  Thi lại
                </Button>
              </Box>
            )}
          </Stack>
        )}
      </Stack>
    </Box>
  );
}

function FlashcardBlock({
  block,
  onProgressSaved,
}: {
  block: LearningLessonBlock;
  onProgressSaved: (
    blockId: string,
    cardIndex: number,
    status: 'REMEMBERED' | 'NEEDS_REVIEW',
    progressStatus: LearningLessonBlock['progressStatus'],
  ) => void;
}) {
  const cards = block.flashcards || [];
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (cards.length === 0) {
    return <Alert severity="info">Bộ flashcard chưa có thẻ nào.</Alert>;
  }

  const reviewedCount = (block.flashcardStatuses || []).filter((s) => s !== null && s !== undefined).length;

  const card = cards[index];
  const currentStatus = block.flashcardStatuses?.[index];

  const handleReview = async (status: 'REMEMBERED' | 'NEEDS_REVIEW') => {
    if (saving) return;
    setSaving(true);
    setErrorMsg(null);
    try {
      const progress = await learningService.reviewFlashcard(block.id, index, status);
      onProgressSaved(block.id, index, status, progress.status);
      if (index < cards.length - 1) {
        setIndex((prev) => prev + 1);
        setFlipped(false);
      }
    } catch (error) {
      console.error('Failed to save flashcard review', error);
      setErrorMsg('Lỗi khi lưu kết quả. Vui lòng thử lại.');
    } finally {
      setSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      setFlipped((prev) => !prev);
    }
  };

  return (
    <Stack spacing={2} sx={{ alignItems: 'center' }}>
      <Paper
        variant="outlined"
        onClick={() => setFlipped((prev) => !prev)}
        onKeyDown={handleKeyDown}
        tabIndex={0}
        sx={{
          width: '100%',
          maxWidth: 480,
          minHeight: 240,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          p: 3,
          bgcolor: flipped ? 'primary.50' : 'background.paper',
          position: 'relative',
          '&:focus': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 }
        }}
      >
        {currentStatus && (
          <Box sx={{ position: 'absolute', top: 8, right: 8 }}>
            <Typography
              variant="caption"
              sx={{
                fontWeight: 600,
                color: currentStatus === 'REMEMBERED' ? 'success.main' : 'warning.main',
              }}
            >
              {currentStatus === 'REMEMBERED' ? 'Đã thuộc' : 'Cần ôn lại'}
            </Typography>
          </Box>
        )}
        <Typography variant="h5" sx={{ textAlign: 'center', whiteSpace: 'pre-wrap', mb: 2 }}>
          {flipped ? card.back : card.front}
        </Typography>
      </Paper>

      <Typography variant="caption" color="text.secondary">
        Nhấn vào thẻ, Space, hoặc Enter để lật · Thẻ {index + 1}/{cards.length} (Đã ôn: {reviewedCount}/{cards.length})
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          disabled={index === 0 || saving}
          onClick={() => {
            setIndex((prev) => prev - 1);
            setFlipped(false);
          }}
        >
          Thẻ trước
        </Button>
        <Button
          endIcon={<ArrowForwardIcon />}
          disabled={index === cards.length - 1 || saving}
          onClick={() => {
            setIndex((prev) => prev + 1);
            setFlipped(false);
          }}
        >
          Thẻ sau
        </Button>
      </Stack>

      {flipped && (
        <Stack direction="row" spacing={2} sx={{ mt: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
          <Button
            variant="contained"
            color="warning"
            disabled={saving}
            onClick={() => handleReview('NEEDS_REVIEW')}
            startIcon={saving && <CircularProgress size={16} color="inherit" />}
          >
            Cần ôn lại
          </Button>
          <Button
            variant="contained"
            color="success"
            disabled={saving}
            onClick={() => handleReview('REMEMBERED')}
            startIcon={saving && <CircularProgress size={16} color="inherit" />}
          >
            Đã thuộc
          </Button>
        </Stack>
      )}

      {errorMsg && (
        <Alert severity="error" sx={{ width: '100%', maxWidth: 480 }}>
          {errorMsg}
        </Alert>
      )}
    </Stack>
  );
}

function WritingBlock({ block, onProgressSaved }: { block: LearningLessonBlock; onProgressSaved: () => void }) {
  const [submission, setSubmission] = useState<import('../types').WritingSubmissionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [aiRequesting, setAiRequesting] = useState(false);
  const [draftReady, setDraftReady] = useState(false);
  const [draftSaveState, setDraftSaveState] = useState<'idle' | 'saving' | 'saved' | 'failed'>('idle');
  const acceptedContentRef = useRef('');
  const latestContentRef = useRef('');
  const draftSaveQueueRef = useRef(Promise.resolve());
  const composingRef = useRef(false);

  const draftKey = writingDraftStorageKey(block.id);
  const submissionStatus = submission?.status;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setDraftReady(false);
    setSubmission(null);
    setContent('');
    acceptedContentRef.current = '';
    latestContentRef.current = '';
    setDraftSaveState('idle');
    learningService
      .getWritingSubmission(block.id)
      .then((data) => {
        if (!active) return;
        const localDraft = readLocalStorageValue<LocalWritingDraft>(draftKey);

        if (data && data.status !== 'DRAFT') {
          removeLocalStorageValue(draftKey);
          setSubmission(data);
          return;
        }

        const restoredContent = localDraft?.content ?? data?.content ?? '';
        setSubmission(data);
        setContent(restoredContent);
        acceptedContentRef.current = restoredContent;
        latestContentRef.current = restoredContent;
      })
      .catch((err) => {
        if (!active) return;
        console.error('Fetch submission error', err);
        const localDraft = readLocalStorageValue<LocalWritingDraft>(draftKey);
        const restoredContent = localDraft?.content ?? '';
        setContent(restoredContent);
        acceptedContentRef.current = restoredContent;
        latestContentRef.current = restoredContent;
      })
      .finally(() => {
        if (active) {
          setDraftReady(true);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [block.id, draftKey]);

  useEffect(() => {
    latestContentRef.current = content;
    if (!draftReady || submission?.status !== 'DRAFT' && submission?.status !== undefined) return;

    if (content.trim()) {
      writeLocalStorageValue(draftKey, { content, savedAt: Date.now() } satisfies LocalWritingDraft);
    } else {
      removeLocalStorageValue(draftKey);
    }
  }, [content, draftKey, draftReady, submission?.status]);

  useEffect(() => {
    if (!draftReady || !content.trim() || (submissionStatus && submissionStatus !== 'DRAFT')) return;

    setDraftSaveState('saving');
    const contentToSave = content;
    const timer = window.setTimeout(() => {
      draftSaveQueueRef.current = draftSaveQueueRef.current
        .catch(() => undefined)
        .then(async () => {
          if (latestContentRef.current !== contentToSave) return;
          try {
            const savedDraft = await learningService.saveWritingDraft(block.id, contentToSave);
            if (latestContentRef.current === contentToSave) {
              setSubmission(savedDraft);
              setDraftSaveState('saved');
            }
          } catch {
            if (latestContentRef.current === contentToSave) setDraftSaveState('failed');
          }
        });
    }, 700);

    return () => window.clearTimeout(timer);
  }, [block.id, content, draftReady, submissionStatus]);

  const handleContentChange = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const nextContent = event.target.value;
    if (composingRef.current || isSingleCharacterMutation(acceptedContentRef.current, nextContent)) {
      acceptedContentRef.current = nextContent;
      setContent(nextContent);
    }
  };

  const blockClipboardAction = (event: React.ClipboardEvent | React.DragEvent | React.MouseEvent) => {
    event.preventDefault();
  };

  const handleWritingKeyDown = (event: React.KeyboardEvent) => {
    if (isClipboardShortcut(event)) event.preventDefault();
  };

  const handleSubmit = async () => {
    if (!content.trim() || content.length > 10000) return;
    setSubmitting(true);
    setErrorMsg(null);
    try {
      const data = await learningService.submitWriting(block.id, content);
      setSubmission(data);
      removeLocalStorageValue(draftKey);
      setDraftSaveState('saved');
      onProgressSaved(); // mark COMPLETED
    } catch (err) {
      if (axios.isAxiosError(err)) {
        setErrorMsg(err.response?.data?.message || 'Có lỗi xảy ra khi nộp bài.');
      } else {
        setErrorMsg('Có lỗi xảy ra khi nộp bài.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submittedSubmission = submission && submission.status !== 'DRAFT' ? submission : null;

  const handleRequestAi = async () => {
    if (!submission) return;
    setAiRequesting(true);
    setErrorMsg(null);
    try {
      const data = await learningService.requestAiWritingAssistance(block.id, submission.id);
      setSubmission(data);
    } catch (err) {
      if (axios.isAxiosError(err)) {
        setErrorMsg(err.response?.data?.message || 'Có lỗi xảy ra khi yêu cầu AI.');
      } else {
        setErrorMsg('Có lỗi xảy ra khi yêu cầu AI.');
      }
      try {
        const data = await learningService.getWritingSubmission(block.id);
        setSubmission(data);
      } catch {
        // Ignore refetch errors
      }
    } finally {
      setAiRequesting(false);
    }
  };

  if (loading) {
    return <CircularProgress size={24} />;
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap', mb: 1, fontWeight: 500 }}>
          {block.writingPrompt}
        </Typography>
        {block.rubric && (
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
            Tiêu chí chấm: {block.rubric}
          </Typography>
        )}
      </Box>

      {errorMsg && <Alert severity="error">{errorMsg}</Alert>}

      {!submittedSubmission ? (
        <Stack spacing={2}>
          <TextField
            multiline
            minRows={5}
            maxRows={15}
            fullWidth
            placeholder="Viết câu trả lời của bạn ở đây..."
            value={content}
            onChange={handleContentChange}
            onKeyDown={handleWritingKeyDown}
            onCopy={blockClipboardAction}
            onCut={blockClipboardAction}
            onPaste={blockClipboardAction}
            onDrop={blockClipboardAction}
            onDragStart={blockClipboardAction}
            onContextMenu={blockClipboardAction}
            onCompositionStart={() => { composingRef.current = true; }}
            onCompositionEnd={(event) => {
              composingRef.current = false;
              const composedContent = (event.target as HTMLTextAreaElement).value;
              acceptedContentRef.current = composedContent;
              setContent(composedContent);
            }}
            disabled={submitting}
            error={content.length > 10000}
            helperText={content.length > 10000 ? 'Bài viết quá dài (tối đa 10,000 ký tự).' : `${content.length}/10,000`}
          />
          <Typography variant="caption" color={draftSaveState === 'failed' ? 'error.main' : 'text.secondary'}>
            {draftSaveState === 'saving' && 'Đang tự động lưu nháp...'}
            {draftSaveState === 'saved' && 'Đã lưu nháp an toàn.'}
            {draftSaveState === 'failed' && 'Chưa đồng bộ được lên máy chủ; bản lưu trên thiết bị vẫn còn.'}
          </Typography>
          <Box>
            <Button
              variant="contained"
              onClick={handleSubmit}
              disabled={submitting || !content.trim() || content.length > 10000}
            >
              {submitting ? 'Đang nộp...' : 'Nộp bài'}
            </Button>
          </Box>
        </Stack>
      ) : (
        <Stack spacing={3}>
          <Card variant="outlined" sx={{ bgcolor: 'action.hover' }}>
            <CardContent>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Bài làm của bạn (đã nộp)
              </Typography>
              <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                {submittedSubmission.content}
              </Typography>
            </CardContent>
          </Card>

          {/* AI Assistance Section */}
          <Box>
            <Typography variant="h6" gutterBottom>
              Gợi ý từ AI
            </Typography>

            {submittedSubmission.status === 'SUGGESTION_PROCESSING' ? (
              <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
                <Typography variant="body2" color="text.secondary">
                  AI đang phân tích bài viết của bạn. Vui lòng đợi trong giây lát...
                </Typography>
                <CircularProgress size={24} />
              </Stack>
            ) : !submittedSubmission.aiSuggestion ? (
              <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
                <Typography variant="body2" color="text.secondary">
                  Bạn có thể yêu cầu AI hỗ trợ nhận xét sơ bộ và đưa ra gợi ý cải thiện cho bài viết của mình.
                </Typography>
                <Button
                  variant="outlined"
                  onClick={handleRequestAi}
                  disabled={aiRequesting}
                >
                  {aiRequesting ? 'Đang phân tích...' : 'Yêu cầu AI hỗ trợ'}
                </Button>
              </Stack>
            ) : submittedSubmission.aiSuggestion.status === 'FAILED' ? (
              <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
                <Alert severity="error">Phân tích bằng AI thất bại. Bạn có muốn thử lại?</Alert>
                <Button variant="outlined" onClick={handleRequestAi} disabled={aiRequesting}>
                  {aiRequesting ? 'Đang thử lại...' : 'Thử lại'}
                </Button>
              </Stack>
            ) : (
              <Stack spacing={3}>
                <Alert severity="info" icon={false}>
                  <Typography variant="body2" sx={{ fontStyle: 'italic' }}>
                    *Gợi ý sơ bộ từ AI, không phải đánh giá chính thức.
                  </Typography>
                </Alert>

                {submittedSubmission.aiSuggestion.revisionGuidance && (
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>Nhận xét sơ bộ</Typography>
                    <Typography variant="body2">{submittedSubmission.aiSuggestion.revisionGuidance}</Typography>
                  </Box>
                )}

                {submittedSubmission.aiSuggestion.grammarSuggestions?.length > 0 && (
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>Ngữ pháp</Typography>
                    <List dense>
                      {submittedSubmission.aiSuggestion.grammarSuggestions.map((item, i) => (
                        <ListItem key={i} disableGutters>
                          <ListItemText
                            primary={
                              <Typography variant="body2">
                                <span style={{ textDecoration: 'line-through', color: 'red' }}>{item.error}</span>{' '}
                                &rarr; <span style={{ color: 'green' }}>{item.correction}</span>
                              </Typography>
                            }
                            secondary={item.explanation}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                )}

                {submittedSubmission.aiSuggestion.vocabularySuggestions?.length > 0 && (
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>Từ vựng</Typography>
                    <List dense>
                      {submittedSubmission.aiSuggestion.vocabularySuggestions.map((item, i) => (
                        <ListItem key={i} disableGutters>
                          <ListItemText
                            primary={
                              <Typography variant="body2">
                                Thay thế <strong>{item.word}</strong> bằng <strong>{item.suggestion}</strong>
                              </Typography>
                            }
                            secondary={item.explanation}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                )}

                {submittedSubmission.aiSuggestion.structureSuggestions?.length > 0 && (
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>Cấu trúc & Mạch văn</Typography>
                    <List dense>
                      {submittedSubmission.aiSuggestion.structureSuggestions.map((item, i) => (
                        <ListItem key={i} disableGutters>
                          <ListItemText
                            primary={<Typography variant="body2">Vấn đề: {item.issue}</Typography>}
                            secondary={`Gợi ý: ${item.suggestion}`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                )}
              </Stack>
            )}
          </Box>

          <Divider />

          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <SchoolOutlinedIcon color="primary" />
              <Typography variant="h6">Đánh giá chính thức từ giáo viên</Typography>
              {submittedSubmission.teacherFeedback?.official && (
                <Chip label="Chính thức" size="small" color="primary" variant="outlined" />
              )}
            </Stack>

            {!submittedSubmission.teacherFeedback ? (
              <Typography variant="body2" color="text.secondary">
                Giáo viên chưa gửi đánh giá chính thức cho bài viết này.
              </Typography>
            ) : (
              <Paper variant="outlined" sx={{ p: 2, borderLeft: 4, borderLeftColor: 'primary.main' }}>
                {submittedSubmission.teacherFeedback.score != null && (
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
                    Điểm: {submittedSubmission.teacherFeedback.score}
                  </Typography>
                )}
                <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                  {submittedSubmission.teacherFeedback.comment || 'Giáo viên chưa để lại nhận xét.'}
                </Typography>
              </Paper>
            )}
          </Box>
        </Stack>
      )}
    </Stack>
  );
}
