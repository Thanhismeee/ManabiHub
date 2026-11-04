import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  Grid,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import { PageHeader } from '../../shared/components/PageHeader/PageHeader';
import { LoadingState } from '../../shared/components/LoadingState/LoadingState';
import AvatarUpload from '../../shared/components/AvatarUpload/AvatarUpload';
import {
  avatarUploadErrorMessage,
  confirmTeacherPhoneVerification,
  getMyTeacherProfile,
  requestTeacherPhoneVerification,
  updateMyTeacherProfile,
  uploadAvatar,
} from './profileApi';
import { resolvePublicAssetUrl } from '../../shared/utils/assetUtils';
import { PHONE_PATTERN, sanitizeOtpInput, sanitizePhoneInput } from './phoneValidation';
import {
  firebasePhoneErrorMessage,
  startFirebasePhoneOtp,
  type FirebasePhoneOtpSession,
} from '../../shared/auth/firebasePhoneAuth';

const JLPT_LEVELS = [
  { level: 'N5', label: 'N5 • 初級' },
  { level: 'N4', label: 'N4 • 初中級' },
  { level: 'N3', label: 'N3 • 中級' },
  { level: 'N2', label: 'N2 • 準上級' },
  { level: 'N1', label: 'N1 • 上級' },
];

const PHONE_OTP_RESEND_COOLDOWN_SECONDS = 60;

interface TeacherProfileForm {
  avatarUrl: string;
  fullName: string;
  email: string;
  phoneNumber: string;
  displayName: string;
  jlptGoal: string;
  bio: string;
}

const EMPTY_FORM: TeacherProfileForm = {
  avatarUrl: '',
  fullName: '',
  email: '',
  phoneNumber: '',
  displayName: '',
  jlptGoal: '',
  bio: '',
};

export default function TeacherProfilePage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [phoneCode, setPhoneCode] = useState('');
  const [phoneOtpSent, setPhoneOtpSent] = useState(false);
  const [phoneOtpCooldownSeconds, setPhoneOtpCooldownSeconds] = useState(0);
  const [firebasePhoneSession, setFirebasePhoneSession] = useState<FirebasePhoneOtpSession | null>(null);
  const [sendingPhoneOtp, setSendingPhoneOtp] = useState(false);
  const [confirmingPhoneOtp, setConfirmingPhoneOtp] = useState(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: '',
    severity: 'success' as 'success' | 'error',
  });
  const [errors, setErrors] = useState({ fullName: '', phoneNumber: '' });
  const [form, setForm] = useState<TeacherProfileForm>(EMPTY_FORM);
  const [initialForm, setInitialForm] = useState<TeacherProfileForm>(EMPTY_FORM);

  useEffect(() => {
    void loadProfile();
  }, []);

  useEffect(() => () => {
    void firebasePhoneSession?.cancel();
  }, [firebasePhoneSession]);

  useEffect(() => {
    if (phoneOtpCooldownSeconds <= 0) return;
    const timer = window.setTimeout(() => {
      setPhoneOtpCooldownSeconds((seconds) => Math.max(0, seconds - 1));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [phoneOtpCooldownSeconds]);

  async function loadProfile() {
    try {
      const profile = await getMyTeacherProfile();
      const profileData: TeacherProfileForm = {
        avatarUrl: profile.avatarUrl ?? '',
        fullName: profile.fullName ?? '',
        email: profile.email ?? '',
        phoneNumber: profile.phoneNumber ?? '',
        displayName: profile.displayName ?? '',
        jlptGoal: profile.jlptGoal ?? '',
        bio: profile.bio ?? '',
      };
      setForm(profileData);
      setInitialForm(profileData);
      setPhoneVerified(profile.phoneVerified === true);
      setPhoneCode('');
      setPhoneOtpSent(false);
      setPhoneOtpCooldownSeconds(0);
      setFirebasePhoneSession(null);
    } catch (error) {
      console.error(error);
      setSnackbar({
        open: true,
        message: 'Không thể tải hồ sơ. Vui lòng thử lại.',
        severity: 'error',
      });
    } finally {
      setLoading(false);
    }
  }

  const isDirty = JSON.stringify(form) !== JSON.stringify(initialForm);

  function validate() {
    const nextErrors = { fullName: '', phoneNumber: '' };
    let valid = true;
    if (!form.fullName.trim()) {
      nextErrors.fullName = 'Vui lòng nhập họ và tên.';
      valid = false;
    }
    if (form.phoneNumber && !PHONE_PATTERN.test(form.phoneNumber)) {
      nextErrors.phoneNumber = 'Số điện thoại không hợp lệ.';
      valid = false;
    }
    setErrors(nextErrors);
    return valid;
  }

  async function handleSave() {
    if (!validate()) return;
    if (!phoneVerified && form.phoneNumber !== initialForm.phoneNumber) {
      setSnackbar({
        open: true,
        message: 'Hãy xác thực số điện thoại bằng mã SMS trước khi lưu.',
        severity: 'error',
      });
      return;
    }

    try {
      setSaving(true);
      await updateMyTeacherProfile({
        fullName: form.fullName,
        phoneNumber: form.phoneNumber || null,
        displayName: form.displayName,
        jlptGoal: form.jlptGoal,
        bio: form.bio,
      });
      await loadProfile();
      setSnackbar({ open: true, message: 'Cập nhật hồ sơ thành công.', severity: 'success' });
    } catch (error: any) {
      console.error(error);
      const response = error.response?.data;
      if (response?.messageCode === 'MSG-PRO-002') {
        setErrors((current) => ({ ...current, phoneNumber: response.message }));
      }
      setSnackbar({
        open: true,
        message: response?.message ?? 'Không thể cập nhật hồ sơ. Vui lòng thử lại.',
        severity: 'error',
      });
    } finally {
      setSaving(false);
    }
  }

  function validatePhone() {
    if (!PHONE_PATTERN.test(form.phoneNumber)) {
      setErrors((current) => ({ ...current, phoneNumber: 'Số điện thoại không hợp lệ.' }));
      return false;
    }
    return true;
  }

  async function handleRequestPhoneOtp() {
    if (phoneVerified || phoneOtpCooldownSeconds > 0 || !validatePhone()) return;
    try {
      setSendingPhoneOtp(true);
      const challenge = await requestTeacherPhoneVerification(form.phoneNumber);
      let nextFirebaseSession: FirebasePhoneOtpSession | null = null;
      if (challenge.verificationMethod === 'FIREBASE') {
        if (!challenge.challengeId || !challenge.phoneNumberE164) {
          throw new Error('Backend không trả về challenge Firebase hợp lệ.');
        }
        nextFirebaseSession = await startFirebasePhoneOtp({
          challengeId: challenge.challengeId,
          phoneNumberE164: challenge.phoneNumberE164,
        });
      }
      setFirebasePhoneSession(nextFirebaseSession);
      setPhoneOtpSent(true);
      setPhoneOtpCooldownSeconds(PHONE_OTP_RESEND_COOLDOWN_SECONDS);
      setSnackbar({
        open: true,
        message: challenge.verificationMethod === 'FIREBASE'
          ? 'Firebase đã gửi SMS thật tới số điện thoại của bạn.'
          : 'Đã gửi mã xác thực SMS.',
        severity: 'success',
      });
    } catch (error: any) {
      setSnackbar({
        open: true,
        message: error.response?.data?.message ?? firebasePhoneErrorMessage(error),
        severity: 'error',
      });
    } finally {
      setSendingPhoneOtp(false);
    }
  }

  async function handleConfirmPhoneOtp() {
    if (phoneVerified || !validatePhone() || !/^\d{6}$/.test(phoneCode)) {
      setSnackbar({ open: true, message: 'Mã xác thực phải gồm 6 chữ số.', severity: 'error' });
      return;
    }
    try {
      setConfirmingPhoneOtp(true);
      const proof = firebasePhoneSession
        ? await firebasePhoneSession.confirm(phoneCode).then((firebaseProof) => ({
          challengeId: firebaseProof.phoneAuthChallengeId,
          firebaseIdToken: firebaseProof.firebaseIdToken,
        }))
        : { code: phoneCode };
      await confirmTeacherPhoneVerification(form.phoneNumber, proof);
      setFirebasePhoneSession(null);
      await loadProfile();
      setSnackbar({
        open: true,
        message: 'Số điện thoại đã được xác thực và khóa thay đổi.',
        severity: 'success',
      });
    } catch (error: any) {
      setSnackbar({
        open: true,
        message: error.response?.data?.message ?? firebasePhoneErrorMessage(error),
        severity: 'error',
      });
    } finally {
      setConfirmingPhoneOtp(false);
    }
  }

  function handleChange(field: keyof TeacherProfileForm) {
    return (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = field === 'phoneNumber'
        ? sanitizePhoneInput(event.target.value)
        : event.target.value;
      setForm((current) => ({ ...current, [field]: value }));
      if (field === 'fullName') {
        setErrors((current) => ({ ...current, fullName: '' }));
      }
      if (field === 'phoneNumber') {
        setErrors((current) => ({ ...current, phoneNumber: '' }));
        void firebasePhoneSession?.cancel();
        setFirebasePhoneSession(null);
        setPhoneCode('');
        setPhoneOtpSent(false);
        setPhoneOtpCooldownSeconds(0);
      }
    };
  }

  async function handleAvatar(file: File) {
    if (file.size > 2 * 1024 * 1024) {
      setSnackbar({ open: true, message: 'Dung lượng ảnh không được vượt quá 2 MB.', severity: 'error' });
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setSnackbar({
        open: true,
        message: 'Định dạng không hợp lệ. Chỉ hỗ trợ ảnh JPEG, PNG và WebP.',
        severity: 'error',
      });
      return;
    }

    const previousAvatarUrl = form.avatarUrl;
    const previewUrl = URL.createObjectURL(file);
    setForm((current) => ({ ...current, avatarUrl: previewUrl }));
    try {
      setSaving(true);
      const serverUrl = await uploadAvatar(file);
      setForm((current) => ({ ...current, avatarUrl: serverUrl }));
      setInitialForm((current) => ({ ...current, avatarUrl: serverUrl }));
      setSnackbar({ open: true, message: 'Cập nhật ảnh đại diện thành công.', severity: 'success' });
    } catch (error) {
      console.error('Avatar upload failed:', error);
      setForm((current) => ({ ...current, avatarUrl: previousAvatarUrl }));
      setSnackbar({ open: true, message: avatarUploadErrorMessage(error), severity: 'error' });
    } finally {
      setSaving(false);
      URL.revokeObjectURL(previewUrl);
    }
  }

  function handleReset() {
    setForm(initialForm);
    setPhoneCode('');
    setPhoneOtpSent(false);
    setErrors({ fullName: '', phoneNumber: '' });
  }

  if (loading) return <LoadingState fullHeight message="Đang tải hồ sơ..." />;

  return (
    <>
      <Box
        component="main"
        sx={{
          bgcolor: '#FAF9F6',
          minHeight: '100vh',
          py: { xs: 3, md: 5 },
          px: { xs: 2, sm: 3 },
        }}
      >
        <Box sx={{ maxWidth: 1280, mx: 'auto', width: '100%', position: 'relative' }}>
          <Typography
            variant="h1"
            aria-hidden="true"
            sx={{
              position: 'absolute',
              top: -40,
              right: -20,
              fontSize: '15rem',
              fontWeight: 900,
              color: 'rgba(0,0,0,0.025)',
              userSelect: 'none',
              pointerEvents: 'none',
              zIndex: 0,
              writingMode: 'vertical-rl',
            }}
          >
            講師
          </Typography>

          <PageHeader
            title="Hồ sơ giảng viên"
            subtitle="講師プロフィール"
            watermark="講師"
            breadcrumbs={[{ label: 'Giảng viên' }, { label: 'Hồ sơ' }]}
          />
          <Typography color="text.secondary" sx={{ mb: 4, mt: -2 }}>
            Cập nhật thông tin tài khoản và hồ sơ chuyên môn của bạn.
          </Typography>

          <Card
            elevation={0}
            sx={{
              position: 'relative',
              zIndex: 1,
              borderRadius: 4,
              border: '1px solid',
              borderColor: 'divider',
              boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
              bgcolor: '#FFFFFF',
            }}
          >
            <Box
              sx={{
                p: { xs: 3, md: 5 },
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                alignItems: { xs: 'center', sm: 'flex-start' },
                justifyContent: 'space-between',
                gap: 3,
              }}
            >
              <Box
                sx={{
                  display: 'flex',
                  flexDirection: { xs: 'column', sm: 'row' },
                  alignItems: 'center',
                  gap: { xs: 2, sm: 4 },
                  textAlign: { xs: 'center', sm: 'left' },
                }}
              >
                <AvatarUpload
                  avatarUrl={resolvePublicAssetUrl(form.avatarUrl) || ''}
                  onSelect={handleAvatar}
                  disabled={saving}
                  size={80}
                />
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800, color: 'grey.900', mb: 0.5 }}>
                    {form.displayName || form.fullName || 'Giảng viên ManabiHub'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    {form.email}
                  </Typography>
                  <Typography
                    variant="caption"
                    sx={{ color: 'grey.600', fontWeight: 700, bgcolor: 'grey.50', px: 1.5, py: 0.5, borderRadius: 1 }}
                  >
                    Hồ sơ giảng viên
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: { xs: 'center', sm: 'flex-end' }, gap: 1 }}>
                <Typography
                  variant="caption"
                  sx={{ textTransform: 'uppercase', fontWeight: 700, color: 'grey.500', letterSpacing: 0.5 }}
                >
                  MỤC TIÊU JLPT
                </Typography>
                <Box
                  sx={{
                    px: 2.5,
                    py: 0.75,
                    bgcolor: '#fee2e2',
                    color: '#C41E3A',
                    borderRadius: '9999px',
                    fontWeight: 800,
                    fontSize: '1rem',
                    boxShadow: '0 2px 4px rgba(196, 30, 58, 0.1)',
                  }}
                >
                  {initialForm.jlptGoal || 'Chưa thiết lập'}
                </Box>
              </Box>
            </Box>

            <Divider sx={{ mx: { xs: 3, md: 5 }, borderColor: 'grey.100' }} />

            <CardContent sx={{ p: { xs: 3, md: 5 } }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Thông tin giảng viên
                </Typography>
                {isDirty && (
                  <Typography variant="caption" sx={{ color: '#C41E3A', fontWeight: 700, textAlign: 'right' }}>
                    * BẠN CÓ THAY ĐỔI CHƯA LƯU
                  </Typography>
                )}
              </Box>

              <Grid container spacing={3}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Họ và tên (*)"
                    value={form.fullName}
                    onChange={handleChange('fullName')}
                    error={Boolean(errors.fullName)}
                    helperText={errors.fullName}
                    sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Tên hiển thị"
                    value={form.displayName}
                    onChange={handleChange('displayName')}
                    placeholder="Tên hiển thị với học viên..."
                    sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Email"
                    value={form.email}
                    disabled
                    helperText="Email đăng nhập - Không thể thay đổi"
                    sx={{ bgcolor: 'grey.50' }}
                    slotProps={{
                      input: { startAdornment: <Typography sx={{ mr: 1, opacity: 0.5 }}>🔒</Typography> },
                    }}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <TextField
                      fullWidth
                      label="Số điện thoại"
                      value={form.phoneNumber}
                      disabled={phoneVerified}
                      type="tel"
                      onChange={handleChange('phoneNumber')}
                      error={Boolean(errors.phoneNumber)}
                      helperText={errors.phoneNumber || (phoneVerified
                        ? 'Đã xác thực — không thể thay đổi số điện thoại.'
                        : 'Bạn cần xác thực số điện thoại bằng SMS.')}
                      slotProps={{
                        ...(phoneVerified ? { input: { endAdornment: <LockOutlinedIcon fontSize="small" /> } } : {}),
                        htmlInput: { inputMode: 'tel', maxLength: 12, autoComplete: 'tel' },
                      }}
                      sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                    />
                    {!phoneVerified && (
                      <Button
                        variant="outlined"
                        onClick={handleRequestPhoneOtp}
                        disabled={saving || sendingPhoneOtp || phoneOtpCooldownSeconds > 0 || !form.phoneNumber}
                        sx={{ minWidth: 150, height: 56, whiteSpace: 'nowrap', borderColor: '#C41E3A', color: '#C41E3A' }}
                      >
                        {sendingPhoneOtp
                          ? 'Đang gửi...'
                          : phoneOtpCooldownSeconds > 0
                            ? `Gửi lại sau ${phoneOtpCooldownSeconds}s`
                            : phoneOtpSent ? 'Gửi lại mã SMS' : 'Gửi mã SMS'}
                      </Button>
                    )}
                  </Stack>
                  {!phoneVerified && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
                      Khi gửi mã, số điện thoại được chuyển tới Google Firebase để chống lạm dụng và gửi SMS; cước SMS tiêu chuẩn có thể áp dụng.
                    </Typography>
                  )}
                  {!phoneVerified && phoneOtpSent && (
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1 }}>
                      <TextField
                        size="small"
                        label="Mã SMS 6 số"
                        value={phoneCode}
                        onChange={(event) => setPhoneCode(sanitizeOtpInput(event.target.value))}
                        slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 6 } }}
                      />
                      <Button
                        variant="contained"
                        onClick={handleConfirmPhoneOtp}
                        disabled={confirmingPhoneOtp || phoneCode.length !== 6}
                        startIcon={<VerifiedUserOutlinedIcon />}
                        sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#a01830' } }}
                      >
                        {confirmingPhoneOtp ? 'Đang xác thực...' : 'Xác thực'}
                      </Button>
                    </Stack>
                  )}
                </Grid>
              </Grid>

              <Divider sx={{ my: 5 }} />

              <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                Thông tin chuyên môn
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Chọn cấp độ JLPT và giới thiệu ngắn gọn về kinh nghiệm giảng dạy của bạn.
              </Typography>

              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 3 }}>
                {JLPT_LEVELS.map(({ level, label }) => {
                  const selected = form.jlptGoal === level;
                  return (
                    <Box
                      key={level}
                      role="button"
                      tabIndex={0}
                      aria-pressed={selected}
                      onClick={() => setForm((current) => ({ ...current, jlptGoal: level }))}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          setForm((current) => ({ ...current, jlptGoal: level }));
                        }
                      }}
                      sx={{
                        px: 4,
                        py: 2,
                        borderRadius: 3,
                        border: '2px solid',
                        borderColor: selected ? '#C41E3A' : 'grey.200',
                        bgcolor: selected ? '#fff1f2' : 'transparent',
                        color: selected ? '#C41E3A' : 'text.secondary',
                        fontWeight: selected ? 800 : 500,
                        cursor: 'pointer',
                        transition: 'all 0.2s',
                        '&:hover': { borderColor: selected ? '#C41E3A' : 'grey.400', transform: 'translateY(-2px)' },
                        '&:focus-visible': { outline: '3px solid rgba(196, 30, 58, 0.25)', outlineOffset: 2 },
                      }}
                    >
                      {label}
                    </Box>
                  );
                })}
              </Box>

              <TextField
                fullWidth
                multiline
                minRows={5}
                label="Tiểu sử giảng viên"
                value={form.bio}
                onChange={handleChange('bio')}
                placeholder="Chia sẻ kinh nghiệm, phương pháp giảng dạy và chuyên môn của bạn..."
                sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
              />

              <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 5 }}>
                <Button
                  variant="outlined"
                  size="large"
                  disabled={saving || !isDirty}
                  onClick={handleReset}
                  sx={{
                    px: 4,
                    borderRadius: 2,
                    textTransform: 'none',
                    fontWeight: 600,
                    color: 'text.secondary',
                    borderColor: 'grey.300',
                    '&:hover': { bgcolor: 'grey.50', borderColor: 'grey.400' },
                  }}
                >
                  Hủy
                </Button>
                <Button
                  variant="contained"
                  size="large"
                  disabled={saving || !isDirty}
                  startIcon={<SaveOutlinedIcon />}
                  onClick={handleSave}
                  sx={{
                    px: 4,
                    borderRadius: 2,
                    textTransform: 'none',
                    fontWeight: 600,
                    bgcolor: '#C41E3A',
                    '&:hover': { bgcolor: '#a01830' },
                    '&.Mui-disabled': { bgcolor: 'grey.300', color: 'grey.500' },
                  }}
                >
                  {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Box>
      </Box>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar((current) => ({ ...current, open: false }))}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert severity={snackbar.severity} variant="filled" sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
}
