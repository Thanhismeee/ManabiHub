import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Link,
  List,
  ListItem,
  ListItemIcon,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import EditNoteIcon from '@mui/icons-material/EditNote';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import QuizOutlinedIcon from '@mui/icons-material/QuizOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { ROUTES } from '../../../shared/constants/routes';
import { RichTextContent } from '../../../shared/components/RichTextContent/RichTextContent';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import {
  getCourseApprovalStatusLabel,
  localizePolicyEvidence,
} from '../courseApprovalLocalization';
import { courseApprovalService } from '../services/courseApprovalService';
import type {
  CourseApprovalBlock,
  CourseApprovalCriterion,
  CourseApprovalDetail,
} from '../types';

const BLOCK_LABELS: Record<CourseApprovalBlock['type'], string> = {
  TEXT: 'Bài đọc',
  VIDEO: 'Video',
  QUIZ: 'Trắc nghiệm',
  FLASHCARD: 'Thẻ ghi nhớ',
  WRITING: 'Bài tập viết',
};

const cardSx = {
  border: '1px solid #e2e8f0',
  borderRadius: 3,
  boxShadow: '0 1px 3px rgb(15 23 42 / 0.06)',
  overflow: 'hidden',
};

function formatMoney(value?: number | null, currency = 'VND') {
  if (value == null) return 'Chưa cấu hình';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value);
}

function ReviewCriterionCard({ criterion }: { criterion: CourseApprovalCriterion }) {
  return (
    <Box
      sx={{
        p: 2,
        border: '1px solid',
        borderColor: criterion.passed ? '#bbf7d0' : '#fecaca',
        bgcolor: criterion.passed ? '#f0fdf4' : '#fff7f7',
        borderRadius: 2,
      }}
    >
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
        {criterion.passed ? (
          <CheckCircleIcon sx={{ color: '#16a34a', mt: 0.15 }} fontSize="small" />
        ) : (
          <ErrorOutlineIcon sx={{ color: '#dc2626', mt: 0.15 }} fontSize="small" />
        )}
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
            {criterion.title}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            {criterion.description}
          </Typography>
          {!criterion.passed && criterion.reasons.length > 0 && (
            <List dense disablePadding sx={{ mt: 0.75 }}>
              {criterion.reasons.map((reason) => (
                <ListItem key={reason} disableGutters sx={{ py: 0.2, alignItems: 'flex-start' }}>
                  <ListItemIcon sx={{ minWidth: 18, mt: 0.75 }}>
                    <Box sx={{ width: 5, height: 5, borderRadius: '50%', bgcolor: '#dc2626' }} />
                  </ListItemIcon>
                  <Typography variant="body2" sx={{ color: '#991b1b' }}>{reason}</Typography>
                </ListItem>
              ))}
            </List>
          )}
        </Box>
      </Stack>
    </Box>
  );
}

function RichTextOrFallback({ value, fallback = 'Chưa cung cấp.' }: { value?: string | null; fallback?: string }) {
  if (!value?.trim()) {
    return <Typography variant="body2" color="text.secondary">{fallback}</Typography>;
  }
  return <RichTextContent value={value} className="text-sm text-slate-800" />;
}

function BlockPreview({ block, position }: { block: CourseApprovalBlock; position: number }) {
  const videoUrl = resolvePublicAssetUrl(block.videoUrl);

  return (
    <Box sx={{ p: 2, border: '1px solid #e2e8f0', borderRadius: 2, bgcolor: '#fff' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
        <Box>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip label={`${position}. ${BLOCK_LABELS[block.type]}`} size="small" variant="outlined" />
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{block.title}</Typography>
          </Stack>
        </Box>
        {block.type === 'VIDEO' && (
          <Chip
            size="small"
            icon={<TimerOutlinedIcon />}
            label={`${block.durationMinutes ?? 0} phút`}
          />
        )}
      </Stack>

      <Box sx={{ mt: 1.5 }}>
        {block.type === 'TEXT' && <RichTextOrFallback value={block.content} />}

        {block.type === 'VIDEO' && (
          <Stack spacing={1}>
            <RichTextOrFallback value={block.content} fallback="Video không có mô tả." />
            {videoUrl ? (
              <Button
                component="a"
                href={videoUrl}
                target="_blank"
                rel="noreferrer"
                variant="outlined"
                size="small"
                startIcon={<PlayCircleOutlineIcon />}
                endIcon={<OpenInNewIcon />}
                sx={{ alignSelf: 'flex-start', textTransform: 'none' }}
              >
                Mở video để kiểm tra
              </Button>
            ) : (
              <Alert severity="error">Bài học chưa có đường dẫn video.</Alert>
            )}
          </Stack>
        )}

        {block.type === 'QUIZ' && (
          <Stack spacing={1.5}>
            {(block.quizItems.length > 0
              ? block.quizItems
              : [{ question: block.quizQuestion ?? '', options: block.quizOptions, answer: block.quizAnswer ?? '' }]
            ).map((item, questionIndex) => (
              <Box key={`${item.question}-${questionIndex}`} sx={{ bgcolor: '#f8fafc', p: 1.5, borderRadius: 1.5 }}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  Câu {questionIndex + 1}: {item.question || 'Chưa có nội dung câu hỏi'}
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 1 }}>
                  {item.options.map((option) => (
                    <Stack key={option} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Box
                        sx={{
                          width: 7,
                          height: 7,
                          borderRadius: '50%',
                          bgcolor: option === item.answer ? '#16a34a' : '#94a3b8',
                        }}
                      />
                      <Typography variant="body2">{option}</Typography>
                      {option === item.answer && <Chip label="Đáp án đúng" color="success" size="small" />}
                    </Stack>
                  ))}
                </Stack>
              </Box>
            ))}
          </Stack>
        )}

        {block.type === 'FLASHCARD' && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' }, gap: 1 }}>
            {block.flashcards.map((card, index) => (
              <Box key={`${card.front}-${index}`} sx={{ p: 1.5, bgcolor: '#f8fafc', borderRadius: 1.5 }}>
                <Typography variant="caption" color="text.secondary">Mặt trước</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{card.front}</Typography>
                <Divider sx={{ my: 1 }} />
                <Typography variant="caption" color="text.secondary">Mặt sau</Typography>
                <Typography variant="body2">{card.back}</Typography>
              </Box>
            ))}
            {block.flashcards.length === 0 && <Alert severity="error">Chưa có thẻ ghi nhớ.</Alert>}
          </Box>
        )}

        {block.type === 'WRITING' && (
          <Stack spacing={1.5}>
            <Box sx={{ p: 1.5, bgcolor: '#f8fafc', borderRadius: 1.5 }}>
              <Typography variant="caption" color="text.secondary">Đề bài</Typography>
              <RichTextOrFallback value={block.writingPrompt} fallback="Chưa có đề bài." />
            </Box>
            <Box sx={{ p: 1.5, bgcolor: '#f8fafc', borderRadius: 1.5 }}>
              <Typography variant="caption" color="text.secondary">Tiêu chí chấm</Typography>
              <RichTextOrFallback value={block.rubric} fallback="Chưa có tiêu chí chấm." />
            </Box>
          </Stack>
        )}
      </Box>

      {block.validationMessage && (
        <Alert severity="warning" sx={{ mt: 1.5 }}>{block.validationMessage}</Alert>
      )}
    </Box>
  );
}

function CurriculumReview({ detail }: { detail: CourseApprovalDetail }) {
  return (
    <Paper elevation={0} sx={cardSx}>
      <Box sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <MenuBookIcon color="action" />
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Nội dung chương trình học</Typography>
            <Typography variant="body2" color="text.secondary">
              {detail.moduleCount} học phần · {detail.lessonBlocksCount} nội dung · {detail.totalVideoDurationMinutes} phút video
            </Typography>
          </Box>
        </Stack>
      </Box>
      <Divider />
      {detail.modules.length === 0 ? (
        <Alert severity="error" sx={{ m: 2.5 }}>Khóa học chưa có học phần nào.</Alert>
      ) : (
        <Box sx={{ p: 2 }}>
          {detail.modules.map((module, moduleIndex) => (
            <Accordion
              key={module.id || `${module.title}-${moduleIndex}`}
              disableGutters
              elevation={0}
              sx={{ border: '1px solid #e2e8f0', mb: 1.25, borderRadius: '10px !important', '&::before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 700 }}>Học phần {moduleIndex + 1}: {module.title}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {module.blocks.length} nội dung
                  </Typography>
                </Box>
              </AccordionSummary>
              <AccordionDetails sx={{ bgcolor: '#f8fafc', borderTop: '1px solid #e2e8f0' }}>
                {module.description && (
                  <Box sx={{ mb: 2 }}><RichTextOrFallback value={module.description} /></Box>
                )}
                <Stack spacing={1.5}>
                  {module.blocks.map((block, index) => (
                    <BlockPreview key={block.id || `${block.title}-${index}`} block={block} position={index + 1} />
                  ))}
                  {module.blocks.length === 0 && <Alert severity="error">Học phần chưa có nội dung.</Alert>}
                </Stack>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      )}
    </Paper>
  );
}

function FinalTestReview({ detail }: { detail: CourseApprovalDetail }) {
  const finalTest = detail.finalTest;
  return (
    <Paper elevation={0} sx={cardSx}>
      <Box sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <QuizOutlinedIcon color="action" />
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Bài kiểm tra cuối khóa</Typography>
            <Typography variant="body2" color="text.secondary">Kiểm tra cấu hình, câu hỏi và đáp án.</Typography>
          </Box>
        </Stack>
      </Box>
      <Divider />
      {!finalTest ? (
        <Alert severity="error" sx={{ m: 2.5 }}>Chưa cấu hình bài kiểm tra cuối khóa.</Alert>
      ) : (
        <Box sx={{ p: 2.5 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' }, gap: 1.25, mb: 2.5 }}>
            {[
              ['Cấp độ', finalTest.jlptLevel],
              ['Kỹ năng', finalTest.skillFocus],
              ['Thời gian', `${finalTest.timeLimitMinutes} phút`],
              ['Điểm đạt', `${finalTest.passingScore}%`],
              ['Số câu hỏi', `${finalTest.questions.length} câu`],
            ].map(([label, value]) => (
              <Box key={label} sx={{ p: 1.5, bgcolor: '#f8fafc', borderRadius: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{value}</Typography>
              </Box>
            ))}
          </Box>
          {finalTest.questions.map((question, questionIndex) => (
            <Accordion key={question.id || questionIndex} disableGutters elevation={0} sx={{ borderTop: '1px solid #e2e8f0', '&::before': { display: 'none' } }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>Câu {questionIndex + 1}: {question.content}</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ bgcolor: '#f8fafc' }}>
                <Stack spacing={1}>
                  {question.choices.map((choice, choiceIndex) => (
                    <Stack key={choice.id || choiceIndex} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      {choice.isCorrect ? <CheckCircleIcon color="success" fontSize="small" /> : <Box sx={{ width: 20 }} />}
                      <Typography variant="body2">{choice.content}</Typography>
                      {choice.isCorrect && <Chip size="small" color="success" label="Đáp án đúng" />}
                    </Stack>
                  ))}
                  <Box sx={{ mt: 1, p: 1.5, bgcolor: '#fff', borderRadius: 1 }}>
                    <Typography variant="caption" color="text.secondary">Giải thích đáp án</Typography>
                    <Typography variant="body2">{question.explanation || 'Chưa có giải thích.'}</Typography>
                  </Box>
                </Stack>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      )}
    </Paper>
  );
}

export const CourseApprovalDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<CourseApprovalDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState<{ message: string; severity: 'success' | 'error' | 'warning' } | null>(null);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(false);
    try {
      setDetail(await courseApprovalService.getDetail(id));
    } catch {
      setDetail(null);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const suggestedReason = useMemo(
    () => detail?.validationErrors?.map((error) => `- ${error.message}`).join('\n') ?? '',
    [detail],
  );
  const isReviewable = detail?.status === 'PENDING';

  const handleAction = async (action: 'APPROVE' | 'REJECT' | 'REQUEST_CORRECTION') => {
    if (!detail || !id || submitting) return;
    if (!isReviewable) {
      setFeedback({
        message: 'Yêu cầu xét duyệt này đã được xử lý. Không thể gửi thêm quyết định.',
        severity: 'warning',
      });
      return;
    }
    if (action === 'APPROVE' && !detail.approvalReady) {
      setFeedback({ message: 'Khóa học còn điều kiện chưa đạt nên chưa thể phê duyệt.', severity: 'warning' });
      return;
    }
    if ((action === 'REJECT' || action === 'REQUEST_CORRECTION') && !reason.trim()) {
      setFeedback({ message: 'Vui lòng nhập lý do để giảng viên biết nội dung cần xử lý.', severity: 'warning' });
      return;
    }

    setSubmitting(true);
    try {
      await courseApprovalService.reviewCourse(id, { action, reason: reason.trim() || undefined });
      setFeedback({ message: 'Đã lưu quyết định xét duyệt.', severity: 'success' });
      setTimeout(() => navigate(ROUTES.ADMIN.COURSE_APPROVAL), 700);
    } catch (error: any) {
      const serverErrors = error.response?.data?.errors as Array<{ message?: string }> | undefined;
      const message = serverErrors?.map((item) => item.message).filter(Boolean).join(' ') ||
        error.response?.data?.message || error.message || 'Không thể xử lý yêu cầu.';
      setFeedback({ message, severity: 'error' });
      if (action === 'APPROVE') void loadDetail();
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Stack spacing={2} sx={{ minHeight: '55vh', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress size={36} />
        <Typography color="text.secondary">Đang tải hồ sơ xét duyệt...</Typography>
      </Stack>
    );
  }

  if (loadError || !detail) {
    return (
      <Box sx={{ p: { xs: 2, md: 4 } }}>
        <Alert
          severity="error"
          action={<Button color="inherit" startIcon={<RefreshIcon />} onClick={() => void loadDetail()}>Thử lại</Button>}
        >
          Không thể tải hồ sơ khóa học. Vui lòng kiểm tra kết nối và thử lại.
        </Alert>
      </Box>
    );
  }

  const thumbnailUrl = resolvePublicAssetUrl(detail.thumbnailUrl);

  return (
    <Box sx={{ p: { xs: 2, md: 3.5 }, bgcolor: '#f8fafc', minHeight: '100vh' }}>
      <Box sx={{ maxWidth: 1480, mx: 'auto' }}>
        <Button
          component={RouterLink}
          to={ROUTES.ADMIN.COURSE_APPROVAL}
          variant="contained"
          color="primary"
          startIcon={<ArrowBackIcon />}
          sx={{
            mb: 1.5,
            borderRadius: 2,
            px: 2,
            py: 1,
            textTransform: 'none',
            fontWeight: 700,
            color: 'primary.contrastText',
            boxShadow: 'none',
            '&:hover': {
              bgcolor: 'primary.dark',
              color: 'primary.contrastText',
              boxShadow: 'none',
            },
            '&:focus, &:active, &:visited': {
              color: 'primary.contrastText',
            },
            '& .MuiButton-startIcon': {
              color: 'inherit',
            },
          }}
        >
          Quay lại hàng chờ duyệt
        </Button>

        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3, justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 700 }}>Duyệt khóa học</Typography>
            <Typography variant="h4" sx={{ fontWeight: 800, fontSize: { xs: '1.55rem', md: '2rem' } }}>
              {detail.courseName}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Kiểm tra nội dung giáo trình và điều kiện xuất bản trước khi đưa ra quyết định.
            </Typography>
          </Box>
          <Chip
            label={getCourseApprovalStatusLabel(detail.status)}
            sx={{ alignSelf: 'flex-start', bgcolor: '#fef3c7', color: '#a16207', fontWeight: 700 }}
          />
        </Stack>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 340px' }, gap: 3, alignItems: 'start' }}>
          <Stack spacing={3} sx={{ minWidth: 0 }}>
            <Paper elevation={0} sx={cardSx}>
              <Box sx={{ p: 2.5 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Thông tin tổng quan</Typography>
              </Box>
              <Divider />
              <Box sx={{ p: 2.5 }}>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '240px minmax(0, 1fr)' }, gap: 2.5 }}>
                  <Box
                    sx={{
                      minHeight: 150,
                      borderRadius: 2,
                      overflow: 'hidden',
                      bgcolor: '#f1f5f9',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {thumbnailUrl ? (
                      <Box component="img" src={thumbnailUrl} alt={`Ảnh bìa ${detail.courseName}`} sx={{ width: '100%', height: '100%', minHeight: 150, objectFit: 'cover' }} />
                    ) : (
                      <Stack spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}><MenuBookIcon /><Typography variant="body2">Chưa có ảnh bìa</Typography></Stack>
                    )}
                  </Box>
                  <Box>
                    <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
                      <Avatar sx={{ bgcolor: '#0f172a' }}>{detail.teacherName.charAt(0).toUpperCase()}</Avatar>
                      <Box>
                        <Typography sx={{ fontWeight: 700 }}>{detail.teacherName}</Typography>
                        <Typography variant="body2" color="text.secondary">{detail.teacherEmail}</Typography>
                      </Box>
                    </Stack>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, gap: 1 }}>
                      {[
                        ['JLPT', detail.jlptLevel || 'Chưa chọn'],
                        ['Danh mục', detail.category || 'Chưa chọn'],
                        ['Học phần', detail.moduleCount.toString()],
                        ['Giá bán', formatMoney(detail.price, detail.currency || 'VND')],
                      ].map(([label, value]) => (
                        <Box key={label} sx={{ p: 1.25, bgcolor: '#f8fafc', borderRadius: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">{label}</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{value}</Typography>
                        </Box>
                      ))}
                    </Box>
                  </Box>
                </Box>

                <Divider sx={{ my: 2.5 }} />
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 2.5 }}>
                  <Box><Typography variant="subtitle2" sx={{ mb: 0.75, fontWeight: 700 }}>Giới thiệu</Typography><RichTextOrFallback value={detail.introduction} /></Box>
                  <Box><Typography variant="subtitle2" sx={{ mb: 0.75, fontWeight: 700 }}>Kết quả đạt được</Typography><RichTextOrFallback value={detail.outcomes} /></Box>
                  <Box><Typography variant="subtitle2" sx={{ mb: 0.75, fontWeight: 700 }}>Yêu cầu đầu vào</Typography><RichTextOrFallback value={detail.prerequisites} /></Box>
                  <Box><Typography variant="subtitle2" sx={{ mb: 0.75, fontWeight: 700 }}>Đối tượng học viên</Typography><RichTextOrFallback value={detail.targetStudents} /></Box>
                </Box>

                <Box sx={{ mt: 2.5 }}>
                  <Typography variant="subtitle2" sx={{ mb: 0.75, fontWeight: 700 }}>Mục tiêu học tập</Typography>
                  {detail.learningGoals.length > 0 ? (
                    <List dense disablePadding>
                      {detail.learningGoals.map((goal, index) => (
                        <ListItem key={`${goal}-${index}`} disableGutters>
                          <ListItemIcon sx={{ minWidth: 30 }}><CheckCircleOutlineIcon color="success" fontSize="small" /></ListItemIcon>
                          <Typography variant="body2">{goal}</Typography>
                        </ListItem>
                      ))}
                    </List>
                  ) : <Typography variant="body2" color="text.secondary">Chưa có mục tiêu học tập.</Typography>}
                </Box>
              </Box>
            </Paper>

            <Paper elevation={0} sx={cardSx}>
              <Box sx={{ p: 2.5 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Kết quả kiểm tra điều kiện</Typography>
                <Typography variant="body2" color="text.secondary">Checklist được tính trực tiếp từ phiên bản khóa học đang chờ duyệt.</Typography>
              </Box>
              <Divider />
              <Box sx={{ p: 2.5 }}>
                <Alert
                  severity={detail.reviewDataAvailable === false ? 'warning' : !isReviewable ? 'info' : detail.approvalReady ? 'success' : 'error'}
                  sx={{ mb: 2 }}
                >
                  {detail.reviewDataAvailable === false
                    ? 'Chưa nhận được dữ liệu xét duyệt từ backend. Vui lòng tải lại trang sau khi backend đã cập nhật.'
                    : !isReviewable
                    ? `Yêu cầu đã được xử lý với trạng thái ${getCourseApprovalStatusLabel(detail.status)}. Checklist bên dưới chỉ dùng để đối chiếu.`
                    : detail.approvalReady
                    ? 'Khóa học đã đáp ứng các điều kiện kỹ thuật để phê duyệt.'
                    : `Khóa học còn ${detail.validationErrors.length} vấn đề cần xử lý trước khi có thể phê duyệt.`}
                </Alert>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 1.5 }}>
                  {detail.reviewCriteria.map((criterion) => <ReviewCriterionCard key={criterion.code} criterion={criterion} />)}
                </Box>
              </Box>
            </Paper>

            <CurriculumReview detail={detail} />
            <FinalTestReview detail={detail} />

            <Paper elevation={0} sx={{ ...cardSx, p: 2.5 }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Minh chứng tuân thủ</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, whiteSpace: 'pre-line' }}>
                    {localizePolicyEvidence(detail.policyEvidence)}
                  </Typography>
                </Box>
                <Link component={RouterLink} to="/help/instructors/course-review-and-unpublishing" target="_blank" sx={{ whiteSpace: 'nowrap' }}>
                  Xem quy định xuất bản
                </Link>
              </Stack>
            </Paper>
          </Stack>

          <Paper elevation={0} sx={{ ...cardSx, position: { lg: 'sticky' }, top: { lg: 20 } }}>
            <Box sx={{ p: 2.5 }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Quyết định xét duyệt</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Lý do là bắt buộc khi từ chối hoặc yêu cầu chỉnh sửa.
              </Typography>
            </Box>
            <Divider />
            <Box sx={{ p: 2.5 }}>
              {!isReviewable && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  Yêu cầu này đã được xử lý với trạng thái <strong>{getCourseApprovalStatusLabel(detail.status)}</strong>.
                  Không thể thay đổi quyết định từ màn hình này.
                </Alert>
              )}
              {isReviewable && !detail.approvalReady && (
                <Alert severity="warning" icon={<WarningAmberIcon />} sx={{ mb: 2 }}>
                  Không thể phê duyệt khi checklist còn mục chưa đạt.
                </Alert>
              )}
              {detail.previousDecisionReason && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  <strong>Lý do lần trước:</strong> {detail.previousDecisionReason}
                </Alert>
              )}
              <TextField
                multiline
                minRows={5}
                fullWidth
                placeholder="Lý do / Ghi chú"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                disabled={!isReviewable || submitting}
                slotProps={{ htmlInput: { maxLength: 2000, 'aria-label': 'Lý do / Ghi chú' } }}
                helperText={`${reason.length}/2.000 ký tự`}
              />
              {isReviewable && !detail.approvalReady && suggestedReason && (
                <Button
                  fullWidth
                  variant="text"
                  onClick={() => setReason(suggestedReason)}
                  sx={{ mt: 0.5, textTransform: 'none' }}
                >
                  Dùng các lỗi trên làm lý do chỉnh sửa
                </Button>
              )}
              <Stack spacing={1.25} sx={{ mt: 2 }}>
                <Button
                  fullWidth
                  variant="contained"
                  color="success"
                  startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : <CheckCircleOutlineIcon />}
                  disabled={!isReviewable || !detail.approvalReady || submitting}
                  onClick={() => void handleAction('APPROVE')}
                  sx={{ py: 1.25, textTransform: 'none', fontWeight: 700 }}
                >
                  Phê duyệt khóa học
                </Button>
                <Button
                  fullWidth
                  variant="contained"
                  startIcon={<EditNoteIcon />}
                  disabled={!isReviewable || submitting || !reason.trim()}
                  onClick={() => void handleAction('REQUEST_CORRECTION')}
                  sx={{ py: 1.25, textTransform: 'none', fontWeight: 700, bgcolor: '#eab308', '&:hover': { bgcolor: '#ca8a04' } }}
                >
                  Yêu cầu chỉnh sửa
                </Button>
                <Button
                  fullWidth
                  variant="outlined"
                  color="error"
                  startIcon={<HighlightOffIcon />}
                  disabled={!isReviewable || submitting || !reason.trim()}
                  onClick={() => void handleAction('REJECT')}
                  sx={{ py: 1.25, textTransform: 'none', fontWeight: 700 }}
                >
                  Từ chối khóa học
                </Button>
              </Stack>
            </Box>
          </Paper>
        </Box>
      </Box>

      <Snackbar open={Boolean(feedback)} autoHideDuration={5000} onClose={() => setFeedback(null)} anchorOrigin={{ vertical: 'top', horizontal: 'right' }}>
        <Alert severity={feedback?.severity} onClose={() => setFeedback(null)} variant="filled" sx={{ width: '100%' }}>
          {feedback?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};
