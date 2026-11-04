import { useState, useEffect } from 'react';
import {
  Alert, Box, Typography, Button, CircularProgress, Stack, Card,
  TextField, Stepper, Step, StepLabel, MenuItem,
  Container, InputAdornment, FormHelperText
} from '@mui/material';
import { isAxiosError } from 'axios';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import PersonIcon from '@mui/icons-material/Person';
import PhoneIcon from '@mui/icons-material/Phone';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  clearAuthSession,
  consumePostLoginRoute,
  getAuthSession,
  hasAnyRole,
  peekPostLoginRoute,
  storeAuthToken,
} from '../../shared/auth/authSession';
import { ROLES } from '../../shared/constants/roles';
import { ROUTES } from '../../shared/constants/routes';
import { updateMyStudentProfile } from '../../features/profile/profileApi';
import { PHONE_PATTERN, sanitizePhoneInput } from '../../features/profile/phoneValidation';
import { LEARNING_LANGUAGES, defaultFramework, defaultLevel, levelOptions, type LearningLanguage } from '../../shared/learning/languageProfiles';
import { useLocale } from '../../shared/i18n/locale';
import { LocaleSelect } from '../../shared/i18n/LocaleSelect';

// [CODE NOTE]: Giao diện Onboarding cho Học viên mới lần đầu login bằng Google (UC-01/02).
// User bắt buộc phải điền đủ thông tin (Tên) mới được vào hệ thống.
export function StudentOnboardingPage() {
  const { t } = useLocale();
  const [activeStep, setActiveStep] = useState(0);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // Form states
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [language, setLanguage] = useState<LearningLanguage>('KOREAN');
  const [goal, setGoal] = useState('1');
  const [avatarIndex, setAvatarIndex] = useState(0);
  const [saving, setSaving] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Validation errors
  const [errors, setErrors] = useState<{ name?: string; phone?: string }>({});
  const session = getAuthSession('public');
  const completionDestination = session ? peekPostLoginRoute('public', session) : '/login';
  const continuesToTeacherKyc = completionDestination.startsWith(ROUTES.TEACHER.KYC);

  useEffect(() => {
    const token = searchParams.get('token');
    const session = token
      ? storeAuthToken('public', token)
      : getAuthSession('public');

    if (!session || !hasAnyRole(session, [ROLES.STUDENT])) {
      navigate('/login', { replace: true });
      return;
    }

    if (token) {
      navigate('/onboarding/student', { replace: true });
    }
  }, [navigate, searchParams]);

  const steps = ['Cập nhật thông tin Học viên', 'Hoàn tất'];

  const validateStep0 = () => {
    const newErrors: { name?: string; phone?: string } = {};
    if (!name.trim()) {
      newErrors.name = 'Vui lòng nhập họ và tên';
    }
    if (phone.trim() && !PHONE_PATTERN.test(phone.trim())) {
      newErrors.phone = 'Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleNext = async () => {
    if (activeStep === 1) {
      const session = getAuthSession('public');
      navigate(session ? consumePostLoginRoute('public', session) : '/login', { replace: true });
      return;
    }

    if (!validateStep0()) return;

    setSaving(true);
    setSubmitError(null);

    try {
      await updateMyStudentProfile({
        displayName: name.trim(),
        fullName: name.trim(),
        jlptGoal: language === 'JAPANESE' ? goal : '',
        learningGoals: [{ learningLanguage: language, proficiencyFramework: defaultFramework(language), frameworkVersion: 'CURRENT', proficiencyLevel: goal, primaryGoal: true }],
        phoneNumber: phone.trim() || null,
        // (In a real app, we would send the avatar index or URL)
      });
      setActiveStep(1);
    } catch (requestError) {
      const responseMessage = isAxiosError<{ message?: string }>(requestError)
        ? requestError.response?.data?.message
        : null;
      setSubmitError(responseMessage || 'Không thể lưu hồ sơ. Vui lòng kiểm tra kết nối và thử lại.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#F8FAFC', backgroundImage: 'radial-gradient(at 100% 0%, rgba(254, 226, 226, 0.4) 0px, transparent 50%), radial-gradient(at 0% 100%, rgba(255, 237, 213, 0.4) 0px, transparent 50%)', display: 'flex', flexDirection: 'column' }}>
      {/* Minimal Header */}
      <Box sx={{ p: 2, px: 4, bgcolor: 'white', borderBottom: '1px solid', borderColor: 'grey.200', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box
          component="img"
          src="/manabihub-header-logo.png"
          alt="ManabiHub"
          sx={{ display: 'block', height: 52, width: 'auto' }}
        />
        <LocaleSelect />
        <Button
          variant="text"
          color="inherit"
          onClick={() => {
            clearAuthSession('public');
            navigate('/login');
          }}
          sx={{ textTransform: 'none', fontWeight: 600, color: 'text.secondary', '&:hover': { color: 'grey.900' } }}
        >
          {t('Đăng xuất')}
        </Button>
      </Box>

      <Box sx={{ flexGrow: 1, py: { xs: 4, md: 6 }, display: 'flex', alignItems: 'center' }}>
        <Container maxWidth="md">
          {/* Header */}
          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Box
              sx={{
                width: 48, height: 48, bgcolor: '#C41E3A', borderRadius: 2,
                display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 2,
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
              }}
            >
              <MenuBookIcon sx={{ color: 'white', fontSize: 28 }} />
            </Box>
          <Typography variant="h4" sx={{ fontWeight: 800, mb: 1, color: 'text.primary' }}>
            Chào mừng bạn mới!
          </Typography>
          <Typography variant="body1" sx={{ color: 'text.secondary', maxWidth: 600, mx: 'auto', lineHeight: 1.6 }}>
            Xin chào hãy hoàn tất một số thông tin cơ bản để có trải nghiệm học tập tốt nhất trên ManabiHub.
          </Typography>
        </Box>

        {/* Stepper */}
        <Stepper activeStep={activeStep} alternativeLabel sx={{ mb: 4, maxWidth: 500, mx: 'auto' }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {/* Content Box */}
        <Card sx={{ borderRadius: 4, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)', border: '1px solid #f1f5f9', bgcolor: 'white' }}>
          <Box sx={{ p: { xs: 3, md: 4 } }}>

            {/* STEP 0: STUDENT PROFILE SETUP */}
            {activeStep === 0 && (
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 3, color: 'grey.900' }}>
                  Cập nhật thông tin cá nhân
                </Typography>

                <Stack spacing={3}>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1.5 }}>
                      Chọn một nhân vật yêu thích
                    </Typography>
                    <Stack direction="row" spacing={{ xs: 1.5, sm: 3 }} sx={{ maxWidth: 400, justifyContent: 'space-between' }}>
                      {[
                        { emoji: '🐕', name: 'Shiba-kun', bg: '#fef08a' },
                        { emoji: '🥷', name: 'Ninja', bg: '#e2e8f0' },
                        { emoji: '👨‍🏫', name: 'Sensei', bg: '#bfdbfe' },
                        { emoji: '🐱', name: 'Maneki', bg: '#fecdd3' }
                      ].map((item, idx) => (
                        <Box key={idx} sx={{ textAlign: 'center' }}>
                          <Box
                            onClick={() => setAvatarIndex(idx)}
                            sx={{
                              width: { xs: 64, sm: 72 }, height: { xs: 64, sm: 72 }, borderRadius: '50%',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              cursor: 'pointer', transition: 'all 0.2s',
                              border: '3px solid',
                              borderColor: avatarIndex === idx ? '#C41E3A' : 'transparent',
                              bgcolor: item.bg,
                              fontSize: { xs: '2rem', sm: '2.5rem' },
                              mb: 1,
                              '&:hover': {
                                transform: 'scale(1.05)'
                              }
                            }}
                          >
                            {item.emoji}
                          </Box>
                          <Typography variant="caption" sx={{ fontWeight: avatarIndex === idx ? 700 : 500, color: avatarIndex === idx ? '#C41E3A' : 'text.secondary' }}>
                            {item.name}
                          </Typography>
                        </Box>
                      ))}
                    </Stack>
                  </Box>

                  <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                    <TextField
                      label="Họ và tên *"
                      placeholder="Tên đầy đủ"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      fullWidth
                      variant="outlined"
                      error={!!errors.name}
                      helperText={errors.name}
                      slotProps={{ input: { startAdornment: <InputAdornment position="start" sx={{ mr: 0.5 }}><PersonIcon sx={{ color: 'grey.400' }} /></InputAdornment> } }}
                    />
                    <TextField
                      label="Số điện thoại"
                      placeholder="Ví dụ: 0912345678"
                      value={phone}
                      onChange={(e) => setPhone(sanitizePhoneInput(e.target.value))}
                      type="tel"
                      fullWidth
                      variant="outlined"
                      error={!!errors.phone}
                      helperText={errors.phone || 'Dùng để nhận thông báo khóa học (Không bắt buộc)'}
                      slotProps={{
                        input: { startAdornment: <InputAdornment position="start" sx={{ mr: 0.5 }}><PhoneIcon sx={{ color: 'grey.400' }} /></InputAdornment> },
                        htmlInput: { inputMode: 'tel', maxLength: 12, autoComplete: 'tel' },
                      }}
                    />
                  </Box>

                  <Box>
                    <TextField select fullWidth label={t('Ngôn ngữ')} value={language} sx={{ mb: 2 }} onChange={event => {
                      const next = event.target.value as LearningLanguage; setLanguage(next); setGoal(defaultLevel(defaultFramework(next)));
                    }}>{LEARNING_LANGUAGES.map(item => <MenuItem key={item.value} value={item.value}>{t(item.label)}</MenuItem>)}</TextField>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.secondary', mb: 1.5 }}>
                      {t('Mục tiêu')}: {defaultFramework(language)}
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ overflowX: 'auto', pb: 1, '&::-webkit-scrollbar': { height: 6 }, '&::-webkit-scrollbar-thumb': { bgcolor: 'grey.300', borderRadius: 3 } }}>
                      {levelOptions(defaultFramework(language)).map(level => ({ id: level.value, label: level.label, sub: '' })).map(item => (
                        <Box
                          key={item.id}
                          onClick={() => setGoal(item.id)}
                          sx={{
                            flex: 1, minWidth: 80,
                            p: 1.5,
                            borderRadius: 2,
                            border: '1px solid',
                            borderColor: goal === item.id ? '#C41E3A' : 'grey.300',
                            bgcolor: goal === item.id ? '#fff1f2' : 'white',
                            cursor: 'pointer',
                            textAlign: 'center',
                            transition: 'all 0.2s',
                            '&:hover': {
                              borderColor: goal === item.id ? '#C41E3A' : 'grey.400',
                              bgcolor: goal === item.id ? '#fff1f2' : 'grey.50'
                            }
                          }}
                        >
                          <Typography variant="subtitle1" sx={{ fontWeight: 700, color: goal === item.id ? '#C41E3A' : 'text.primary' }}>
                            {item.label}
                          </Typography>
                          <Typography variant="caption" sx={{ color: goal === item.id ? '#C41E3A' : 'text.secondary', display: 'block' }}>
                            {item.sub}
                          </Typography>
                        </Box>
                      ))}
                    </Stack>
                    <FormHelperText sx={{ mt: 3, mx: 0, fontSize: '0.85rem' }}>Giúp chúng tôi đề xuất lộ trình và khóa học phù hợp nhất với bạn.</FormHelperText>
                  </Box>



                  {submitError && <Alert severity="error">{submitError}</Alert>}

                </Stack>
              </Box>
            )}

            {/* STEP 1: FINISH */}
            {activeStep === 1 && (
              <Box sx={{ textAlign: 'center', py: 6 }}>
                <TaskAltIcon sx={{ fontSize: 80, color: '#22c55e', mb: 3 }} />
                <Typography variant="h4" sx={{ fontWeight: 800, mb: 2, color: 'grey.900' }}>
                  Hoàn tất thiết lập!
                </Typography>
                <Typography variant="body1" sx={{ color: 'grey.600', maxWidth: 400, mx: 'auto', lineHeight: 1.6 }}>
                  {continuesToTeacherKyc
                    ? 'Tài khoản Học viên của bạn đã sẵn sàng. Tiếp theo, hãy xác minh danh tính và chứng chỉ để đăng ký trở thành Giảng viên.'
                    : t('Tài khoản đã sẵn sàng. Hãy bắt đầu học ngôn ngữ cùng ManabiHub!')}
                </Typography>
              </Box>
            )}

          </Box>

          {/* Footer Actions */}
          <Box sx={{ p: { xs: 3, md: 4 }, bgcolor: 'grey.50', borderTop: '1px solid', borderColor: 'grey.200', display: 'flex', justifyContent: activeStep === 0 ? 'flex-end' : 'flex-end', alignItems: 'center', borderBottomLeftRadius: 16, borderBottomRightRadius: 16 }}>
            <Button
              variant="contained"
              onClick={handleNext}
              disabled={saving}
              startIcon={saving ? <CircularProgress color="inherit" size={18} /> : undefined}
              sx={{ px: 4, py: 1.5, borderRadius: 2, textTransform: 'none', fontWeight: 700, boxShadow: '0 4px 6px -1px rgba(37, 99, 235, 0.2)' }}
            >
              {saving
                ? 'Đang lưu...'
                : activeStep === steps.length - 1
                  ? continuesToTeacherKyc
                    ? 'Tiếp tục xác minh Giảng viên'
                    : 'Đến trang của tôi'
                  : 'Tiếp tục'}
            </Button>
          </Box>
        </Card>

        </Container>
      </Box>
    </Box>
  );
}
