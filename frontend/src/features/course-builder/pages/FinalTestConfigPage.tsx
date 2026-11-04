import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Button, Stack, Typography, TextField, MenuItem, Alert, Snackbar, InputAdornment, Paper } from '@mui/material';
import { finalTestService } from '../services/finalTestService';
import type { FinalTestConfig, UpdateFinalTestRequest } from '../services/finalTestService';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState/LoadingState';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { FinalTestQuestionsEditor } from './FinalTestQuestionsEditor';
import {
  LEARNING_LANGUAGES,
  defaultFramework,
  defaultLevel,
  frameworkOptions,
  levelOptions,
  type LearningLanguage,
  type ProficiencyFramework,
} from '../../../shared/learning/languageProfiles';

type FinalTestFormState = Omit<UpdateFinalTestRequest, 'timeLimitMinutes' | 'passingScore' | 'maxRetakes'> & {
  timeLimitMinutes: number | '';
  passingScore: number | '';
  maxRetakes: number | '';
  skillFocus: string;
};

type FinalTestSaveError = {
  response?: {
    data?: {
      errors?: Array<{ message?: string }>;
      message?: string;
    };
  };
};

const createEmptyForm = (): FinalTestFormState => ({
  timeLimitMinutes: '',
  passingScore: '',
  maxRetakes: '',
  jlptLevel: null,
  learningLanguage: 'KOREAN',
  proficiencyFramework: 'TOPIK',
  frameworkVersion: 'CURRENT',
  proficiencyLevel: '1',
  assessmentFormat: 'COURSE',
  skillFocus: 'Tổng hợp',
  questions: [],
});

const toFormState = (config: FinalTestConfig): FinalTestFormState => ({
  timeLimitMinutes: config.timeLimitMinutes ?? '',
  passingScore: config.passingScore ?? '',
  maxRetakes: config.maxRetakes ?? '',
  jlptLevel: config.jlptLevel || null,
  learningLanguage: config.learningLanguage ?? 'JAPANESE',
  proficiencyFramework: config.proficiencyFramework ?? 'JLPT',
  frameworkVersion: config.frameworkVersion ?? 'CURRENT',
  proficiencyLevel: config.proficiencyLevel ?? config.jlptLevel ?? 'N5',
  assessmentFormat: config.assessmentFormat ?? 'COURSE',
  skillFocus: config.skillFocus || '',
  questions: config.questions || [],
});

const comparableForm = (value: FinalTestFormState) => JSON.stringify({
  timeLimitMinutes: value.timeLimitMinutes === '' ? null : Number(value.timeLimitMinutes),
  passingScore: value.passingScore === '' ? null : Number(value.passingScore),
  maxRetakes: value.maxRetakes === '' ? null : Number(value.maxRetakes),
  jlptLevel: value.jlptLevel,
  learningLanguage: value.learningLanguage,
  proficiencyFramework: value.proficiencyFramework,
  frameworkVersion: value.frameworkVersion,
  proficiencyLevel: value.proficiencyLevel,
  assessmentFormat: value.assessmentFormat,
  skillFocus: value.skillFocus,
  questions: value.questions.map((question) => ({
    content: question.content,
    explanation: question.explanation,
    choices: question.choices.map((choice) => ({
      content: choice.content,
      isCorrect: choice.isCorrect,
    })),
  })),
});

export const FinalTestConfigPage = () => {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [initialForm, setInitialForm] = useState<FinalTestFormState | null>(null);
  const [hasPersistedConfig, setHasPersistedConfig] = useState(false);
  const [expanded, setExpanded] = useState<number | false>(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: '',
    severity: 'success' as 'success' | 'error' | 'warning' | 'info',
  });

  const notify = (msg: string, severity: 'success' | 'error' | 'warning' | 'info' = 'success') => {
    setSnackbar({ open: true, message: msg, severity });
  };

  const [form, setForm] = useState<FinalTestFormState>(createEmptyForm);

  const loadData = () => {
    if (!courseId) return;

    setLoading(true);
    setLoadError(false);

    finalTestService.getFinalTest(courseId)
      .then((config) => {
        if (config) {
          const loadedForm = toFormState(config);
          setForm(loadedForm);
          setInitialForm(loadedForm);
          setHasPersistedConfig(true);
        } else {
          const emptyForm = createEmptyForm();
          setForm(emptyForm);
          setInitialForm(emptyForm);
          setHasPersistedConfig(false);
        }
      })
      .catch((err) => {
        console.error(err);
        setLoadError(true);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId]);

  const isDirty = initialForm !== null && comparableForm(form) !== comparableForm(initialForm);

  const handleSave = async (shouldExit: boolean = false) => {
    if (!courseId) return;

    if (shouldExit && hasPersistedConfig && !isDirty) {
      navigate('/teacher/courses', { state: { finalTestSaved: true } });
      return;
    }

    const showError = (msg: string) => {
      setSnackbar({ open: true, message: msg, severity: 'error' });
    };

    if (form.questions.length < 20) {
      showError('Final Test cần có tối thiểu 20 câu hỏi đang hoạt động');
      return;
    }

    if (form.timeLimitMinutes === '' || Number(form.timeLimitMinutes) < 1 || Number(form.timeLimitMinutes) > 180) {
      showError('Vui lòng nhập thời gian làm bài (từ 1 đến 180 phút).');
      return;
    }

    if (form.passingScore === '' || Number(form.passingScore) < 1 || Number(form.passingScore) > 100) {
      showError('Vui lòng nhập điểm đạt (từ 1 đến 100%).');
      return;
    }

    if (form.maxRetakes === '' || Number(form.maxRetakes) < 1 || Number(form.maxRetakes) > 10) {
      showError('Vui lòng nhập số lần thi lại (từ 1 đến 10 lần).');
      return;
    }

    if (!form.learningLanguage || !form.proficiencyFramework || !form.proficiencyLevel.trim()) {
      showError('Vui lòng chọn ngôn ngữ, khung và cấp độ bài thi.');
      return;
    }

    if (!form.frameworkVersion.trim()) {
      showError('Vui lòng nhập phiên bản chương trình.');
      return;
    }

    if (!form.skillFocus || !form.skillFocus.trim()) {
      showError('Vui lòng nhập kỹ năng tập trung.');
      return;
    }

    if (form.skillFocus.length > 50) {
      showError('Kỹ năng tập trung không được vượt quá 50 ký tự.');
      return;
    }

    // Validate empty fields in questions
    for (let i = 0; i < form.questions.length; i++) {
      const q = form.questions[i];
      const validateError = (msg: string) => {
        showError(msg);
        setExpanded(i);
        setTimeout(() => {
          document.getElementById(`question-accordion-${i}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }, 100);
      };

      if (!q.content.trim()) { validateError(`Lỗi ở Câu ${i + 1}: Nội dung câu hỏi không được để trống.`); return; }
      if (!q.explanation.trim()) { validateError(`Lỗi ở Câu ${i + 1}: Vui lòng điền giải thích đáp án.`); return; }
      for (let j = 0; j < q.choices.length; j++) {
        if (!q.choices[j].content.trim()) { validateError(`Lỗi ở Câu ${i + 1}: Lựa chọn số ${j + 1} đang bị bỏ trống.`); return; }
      }
      const correctChoiceCount = q.choices.filter((choice) => choice.isCorrect).length;
      if (correctChoiceCount !== 1) { validateError(`Lỗi ở Câu ${i + 1}: Phải chọn đúng 1 đáp án đúng.`); return; }
    }

    setSaving(true);

    try {
      const request: UpdateFinalTestRequest = {
        ...form,
        jlptLevel: form.learningLanguage === 'JAPANESE' && form.proficiencyFramework === 'JLPT'
          ? form.proficiencyLevel
          : null,
        timeLimitMinutes: Number(form.timeLimitMinutes),
        passingScore: Number(form.passingScore),
        maxRetakes: Number(form.maxRetakes),
      };

      const savedConfig = await finalTestService.updateFinalTest(courseId, request);
      const savedForm = toFormState(savedConfig);
      setForm(savedForm);
      setInitialForm(savedForm);
      setHasPersistedConfig(true);
      setSnackbar({
        open: true,
        message: 'Lưu cấu hình thành công!',
        severity: 'success',
      });
      if (shouldExit) {
        navigate('/teacher/courses', { state: { finalTestSaved: true } });
      }
    } catch (unknownError: unknown) {
      const err = unknownError as FinalTestSaveError;

      // A dropped connection can hide a successful commit. Confirm the
      // persisted state before telling the teacher that the save failed.
      if (!err.response) {
        try {
          const persistedConfig = await finalTestService.getFinalTest(courseId);
          if (persistedConfig) {
            const persistedForm = toFormState(persistedConfig);
            if (comparableForm(persistedForm) === comparableForm(form)) {
              setForm(persistedForm);
              setInitialForm(persistedForm);
              setHasPersistedConfig(true);
              if (shouldExit) {
                navigate('/teacher/courses', { state: { finalTestSaved: true } });
              } else {
                notify('Kết nối bị gián đoạn nhưng hệ thống đã xác nhận cấu hình được lưu.', 'warning');
              }
              return;
            }
          }
        } catch {
          // The verification request can fail on the same interrupted network.
        }

        showError('Kết nối bị gián đoạn nên chưa thể xác nhận trạng thái lưu. Dữ liệu có thể đã được lưu; vui lòng giữ trang và thử lại khi mạng ổn định.');
      } else if (err.response.data?.errors?.length) {
        showError(err.response.data.errors[0].message || 'Dữ liệu Final Test không hợp lệ.');
      } else if (err.response.data?.message) {
        showError(err.response.data.message);
      } else {
        showError('Có lỗi xảy ra, vui lòng thử lại.');
      }
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <LoadingState message="Đang tải cấu hình..." />;
  }

  if (loadError) {
    return (
      <Box sx={{ pb: 10 }}>
        <PageHeader
          title="Cấu hình Bài thi cuối khóa"
          breadcrumbs={[
            { label: 'Khóa học của tôi', href: '/teacher/courses' },
            { label: 'Bài thi cuối khóa' },
          ]}
        />
        <Box sx={{ mt: 3 }}>
          <ErrorState message="Không thể tải cấu hình bài thi cuối khóa." onRetry={loadData} />
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ pb: 10 }}>
      <PageHeader
        title="Cấu hình Bài thi cuối khóa"
        breadcrumbs={[
          { label: 'Khóa học của tôi', href: '/teacher/courses' },
          { label: 'Bài thi cuối khóa' },
        ]}
      />

      <Box sx={{ mt: 3, maxWidth: 800 }}>
        <Stack spacing={3}>
          <Typography variant="h6">Cấu hình chung</Typography>

          <Stack direction="row" spacing={2}>
            <TextField
              variant="outlined"
              label="Thời gian làm bài (phút)"
              type="number"
              fullWidth
              value={form.timeLimitMinutes as number}
              onChange={(e) => setForm({ ...form, timeLimitMinutes: e.target.value === '' ? '' : Number(e.target.value) })}
              slotProps={{ htmlInput: { min: 1, max: 180 } }}
            />
            <TextField
              variant="outlined"
              label="Điểm đạt (%)"
              type="number"
              fullWidth
              value={form.passingScore as number}
              onChange={(e) => setForm({ ...form, passingScore: e.target.value === '' ? '' : Number(e.target.value) })}
              slotProps={{
                input: { endAdornment: <InputAdornment position="end">%</InputAdornment> },
                htmlInput: { min: 1, max: 100 }
              }}
            />
            <TextField
              variant="outlined"
              label="Số lần thi lại tối đa"
              type="number"
              fullWidth
              value={form.maxRetakes as number}
              onChange={(e) => setForm({ ...form, maxRetakes: e.target.value === '' ? '' : Number(e.target.value) })}
              slotProps={{ htmlInput: { min: 1, max: 10 } }}
            />
          </Stack>

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField
              variant="outlined"
              select
              required
              label="Ngôn ngữ"
              fullWidth
              value={form.learningLanguage}
              onChange={(e) => {
                const learningLanguage = e.target.value as LearningLanguage;
                const proficiencyFramework = defaultFramework(learningLanguage);
                setForm({
                  ...form,
                  learningLanguage,
                  proficiencyFramework,
                  frameworkVersion: 'CURRENT',
                  proficiencyLevel: defaultLevel(proficiencyFramework),
                  jlptLevel: null,
                });
              }}
            >
              {LEARNING_LANGUAGES.map((language) => (
                <MenuItem key={language.value} value={language.value}>{language.label}</MenuItem>
              ))}
            </TextField>
            <TextField
              variant="outlined"
              select
              required
              label="Khung trình độ"
              fullWidth
              value={form.proficiencyFramework}
              onChange={(e) => {
                const proficiencyFramework = e.target.value as ProficiencyFramework;
                setForm({
                  ...form,
                  proficiencyFramework,
                  frameworkVersion: proficiencyFramework === 'SOFL_INTERNAL' ? 'SOFL' : 'CURRENT',
                  proficiencyLevel: defaultLevel(proficiencyFramework),
                  jlptLevel: null,
                });
              }}
            >
              {frameworkOptions(form.learningLanguage).map((framework) => (
                <MenuItem key={framework.value} value={framework.value}>{framework.label}</MenuItem>
              ))}
            </TextField>
            {form.proficiencyFramework === 'SOFL_INTERNAL' ? (
              <TextField
                variant="outlined"
                required
                label="Mã cấp độ"
                fullWidth
                value={form.proficiencyLevel}
                onChange={(e) => setForm({ ...form, proficiencyLevel: e.target.value })}
              />
            ) : (
              <TextField
                variant="outlined"
                select
                required
                label="Cấp độ"
                fullWidth
                value={form.proficiencyLevel}
                onChange={(e) => setForm({ ...form, proficiencyLevel: e.target.value })}
              >
                {levelOptions(form.proficiencyFramework).map((level) => (
                  <MenuItem key={level.value} value={level.value}>{level.label}</MenuItem>
                ))}
              </TextField>
            )}
          </Stack>

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField
              variant="outlined"
              required
              label="Phiên bản chương trình"
              fullWidth
              value={form.frameworkVersion}
              onChange={(e) => setForm({ ...form, frameworkVersion: e.target.value })}
            />
            <TextField
              variant="outlined"
              select
              required
              label="Hình thức đánh giá"
              fullWidth
              value={form.assessmentFormat}
              onChange={(e) => setForm({ ...form, assessmentFormat: e.target.value })}
            >
              <MenuItem value="COURSE">Bài cuối khóa SOFL</MenuItem>
              <MenuItem value="PBT">Mô phỏng trên giấy (PBT)</MenuItem>
              <MenuItem value="IBT">Mô phỏng trên máy (IBT)</MenuItem>
            </TextField>
            <TextField
              variant="outlined"
              label="Kỹ năng tập trung"
              fullWidth
              value={form.skillFocus}
              onChange={(e) => setForm({ ...form, skillFocus: e.target.value })}
              placeholder="VD: Từ vựng, Ngữ pháp, Nghe hiểu..."
            />
          </Stack>

          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mt: 4 }}>
            <Typography variant="h6">Danh sách câu hỏi ({form.questions.length}/20 tối thiểu)</Typography>
          </Stack>

          <FinalTestQuestionsEditor
            questions={form.questions}
            onChange={(q) => setForm({ ...form, questions: q })}
            expanded={expanded}
            setExpanded={setExpanded}
            onNotify={notify}
          />
        </Stack>
      </Box>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={1500}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert severity={snackbar.severity} variant="filled" sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>

      <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, p: 2, zIndex: 1000, display: 'flex', justifyContent: 'flex-end', gap: 2, borderTop: '1px solid #e0e0e0' }} elevation={3}>
        <Button
          variant="outlined"
          color="inherit"
          onClick={() => {
            if (isDirty) {
              if (window.confirm("Bạn có những thay đổi chưa được lưu. Bạn có chắc chắn muốn rời đi không?")) {
                navigate('/teacher/courses');
              }
            } else {
              navigate('/teacher/courses');
            }
          }}
        >
          Hủy
        </Button>
        <Button
          variant="outlined"
          color="primary"
          onClick={() => handleSave(false)}
          disabled={saving}
        >
          {saving ? 'Đang lưu...' : 'Lưu & Tiếp tục'}
        </Button>
        <Button
          variant="contained"
          color="success"
          onClick={() => handleSave(true)}
          disabled={saving}
        >
          {saving ? 'Đang lưu...' : 'Lưu & Thoát'}
        </Button>
      </Paper>
    </Box>
  );
};
