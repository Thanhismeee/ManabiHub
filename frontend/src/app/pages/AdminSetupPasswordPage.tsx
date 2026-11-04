import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  IconButton,
  InputAdornment,
  LinearProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { useNavigate } from 'react-router-dom';
import { axiosClient } from '../../shared/api/axiosClient';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { evaluateAdminPassword } from '../../features/system-administration/utils/adminPasswordPolicy';

const POLICY_LABELS = {
  length: 'Từ 12 đến 72 byte',
  uppercase: 'Có chữ hoa',
  lowercase: 'Có chữ thường',
  digit: 'Có chữ số',
  special: 'Có ký tự đặc biệt',
  noWhitespace: 'Không có khoảng trắng',
} as const;

export function AdminSetupPasswordPage() {
  const navigate = useNavigate();
  const [token] = useState(readInvitationToken);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const policy = useMemo(() => evaluateAdminPassword(password), [password]);
  const matches = password.length > 0 && password === confirmation;

  useEffect(() => {
    if (token && typeof window !== 'undefined') {
      window.history.replaceState(
        window.history.state,
        document.title,
        '/admin/setup-password',
      );
    }
  }, [token]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!token || !policy.valid || !matches || submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await axiosClient.post(ENDPOINTS.ADMIN_SETUP_PASSWORD, { token, password });
      setPassword('');
      setConfirmation('');
      setCompleted(true);
    } catch {
      setError(
        'Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. '
          + 'Hãy liên hệ Quản trị hệ thống để nhận lời mời mới.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        bgcolor: '#f6f8fb',
        display: 'grid',
        placeItems: 'center',
        px: 2,
        py: 5,
      }}
    >
      <Box sx={{ width: '100%', maxWidth: 520 }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', mb: 3 }}>
          <Box
            component="img"
            src="/manabihub-header-logo.png"
            alt="ManabiHub"
            sx={{ display: 'block', height: 58, width: 'auto' }}
          />
          <Box>
            <Typography variant="body2" color="text.secondary">Cổng quản trị nội bộ</Typography>
          </Box>
        </Stack>

        <Box
          sx={{
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            overflow: 'hidden',
          }}
        >
          {submitting && <LinearProgress />}
          <Box sx={{ p: { xs: 3, sm: 4 } }}>
            {completed ? (
              <Stack spacing={2.5} sx={{ alignItems: 'flex-start' }}>
                <CheckCircleOutlineIcon color="success" sx={{ fontSize: 48 }} />
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                    Mật khẩu đã được thiết lập
                  </Typography>
                  <Typography color="text.secondary">
                    Tài khoản quản trị đã được kích hoạt. Bạn có thể đăng nhập bằng email
                    nhận lời mời và mật khẩu vừa tạo.
                  </Typography>
                </Box>
                <Button
                  variant="contained"
                  onClick={() => navigate('/admin/login', { replace: true })}
                >
                  Đến trang đăng nhập
                </Button>
              </Stack>
            ) : (
              <form onSubmit={submit}>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  Thiết lập mật khẩu
                </Typography>
                <Typography color="text.secondary" sx={{ mb: 3 }}>
                  Liên kết này chỉ dùng được một lần. ManabiHub không gửi mật khẩu qua email.
                </Typography>

                {!token && (
                  <Alert severity="error" sx={{ mb: 2.5 }}>
                    Thiếu mã lời mời. Hãy mở đúng liên kết trong email.
                  </Alert>
                )}
                {error && <Alert severity="error" sx={{ mb: 2.5 }}>{error}</Alert>}

                <TextField
                  fullWidth
                  label="Mật khẩu mới"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={submitting || !token}
                  autoComplete="new-password"
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start"><LockOutlinedIcon /></InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                            onClick={() => setShowPassword((current) => !current)}
                            edge="end"
                          >
                            {showPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    },
                    htmlInput: { maxLength: 72 },
                  }}
                />

                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
                    gap: 0.75,
                    my: 2,
                  }}
                >
                  {Object.entries(POLICY_LABELS).map(([key, label]) => {
                    const passed = policy.checks[key as keyof typeof policy.checks];
                    return (
                      <Typography
                        key={key}
                        variant="caption"
                        color={passed ? 'success.main' : 'text.secondary'}
                      >
                        {passed ? '✓' : '○'} {label}
                      </Typography>
                    );
                  })}
                </Box>

                <TextField
                  fullWidth
                  label="Nhập lại mật khẩu"
                  type={showPassword ? 'text' : 'password'}
                  value={confirmation}
                  onChange={(event) => setConfirmation(event.target.value)}
                  disabled={submitting || !token}
                  autoComplete="new-password"
                  error={confirmation.length > 0 && !matches}
                  helperText={
                    confirmation.length > 0 && !matches ? 'Hai mật khẩu chưa trùng nhau.' : ' '
                  }
                  slotProps={{ htmlInput: { maxLength: 72 } }}
                />

                <Button
                  fullWidth
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={!token || !policy.valid || !matches || submitting}
                  sx={{ mt: 1 }}
                >
                  {submitting ? 'Đang kích hoạt…' : 'Đặt mật khẩu và kích hoạt'}
                </Button>
              </form>
            )}
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

function readInvitationToken() {
  if (typeof window === 'undefined') return '';

  const fragmentToken = new URLSearchParams(
    window.location.hash.replace(/^#/, ''),
  ).get('token');
  return fragmentToken ?? '';
}
