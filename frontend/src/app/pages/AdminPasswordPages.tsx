import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react';
import {
  Alert,
  Box,
  Button,
  IconButton,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import axios from 'axios';
import {
  Link as RouterLink,
  useNavigate,
} from 'react-router-dom';
import { axiosClient } from '../../shared/api/axiosClient';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { clearAuthSession } from '../../shared/auth/authSession';
import { ROUTES } from '../../shared/constants/routes';
import { evaluateAdminPassword } from '../../features/system-administration/utils/adminPasswordPolicy';

const POLICY_LABELS = {
  length: 'Từ 12 đến 72 byte',
  uppercase: 'Có chữ hoa',
  lowercase: 'Có chữ thường',
  digit: 'Có chữ số',
  special: 'Có ký tự đặc biệt',
  noWhitespace: 'Không có khoảng trắng',
} as const;

export function AdminForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await axiosClient.post(ENDPOINTS.ADMIN_FORGOT_PASSWORD, { email });
      setCompleted(true);
    } catch {
      setError('Không thể gửi yêu cầu lúc này. Vui lòng thử lại sau.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AdminAuthPageShell
      title="Quên mật khẩu"
      description="Nhập email tài khoản nội bộ để nhận liên kết đặt lại mật khẩu."
    >
      {completed ? (
        <Stack spacing={3}>
          <Alert severity="success">
            Nếu tài khoản đủ điều kiện, hướng dẫn đặt lại mật khẩu sẽ được gửi tới email.
          </Alert>
          <Button component={RouterLink} to={ROUTES.ADMIN.LOGIN} variant="contained">
            Trở lại đăng nhập
          </Button>
        </Stack>
      ) : (
        <Box component="form" onSubmit={submit}>
          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
          <TextField
            autoComplete="email"
            disabled={submitting}
            fullWidth
            label="Email nội bộ"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailOutlinedIcon />
                  </InputAdornment>
                ),
              },
            }}
          />
          <Button
            disabled={submitting}
            fullWidth
            sx={{ mt: 3 }}
            type="submit"
            variant="contained"
          >
            {submitting ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}
          </Button>
          <Button component={RouterLink} fullWidth sx={{ mt: 1 }} to={ROUTES.ADMIN.LOGIN}>
            Trở lại đăng nhập
          </Button>
        </Box>
      )}
    </AdminAuthPageShell>
  );
}

export function AdminResetPasswordPage() {
  const [token] = useState(readFragmentToken);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const policy = useMemo(() => evaluateAdminPassword(password), [password]);
  const matches = password.length > 0 && password === confirmation;

  useEffect(() => {
    if (token) {
      window.history.replaceState(
        window.history.state,
        document.title,
        ROUTES.ADMIN.RESET_PASSWORD,
      );
    }
  }, [token]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !policy.valid || !matches || submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await axiosClient.post(ENDPOINTS.ADMIN_RESET_PASSWORD, { token, password });
      clearAuthSession('admin');
      setPassword('');
      setConfirmation('');
      setCompleted(true);
    } catch (requestError: unknown) {
      setError(getAdminPasswordError(requestError, 'reset'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AdminAuthPageShell
      title="Đặt lại mật khẩu"
      description="Sau khi đổi mật khẩu, tất cả phiên quản trị cũ sẽ bị thu hồi."
    >
      {!token && (
        <Alert severity="error">
          Liên kết đặt lại mật khẩu không hợp lệ.
        </Alert>
      )}
      {completed ? (
        <Stack spacing={3}>
          <Alert severity="success">
            Mật khẩu đã được thay đổi. Hãy đăng nhập lại.
          </Alert>
          <Button component={RouterLink} to={ROUTES.ADMIN.LOGIN} variant="contained">
            Đăng nhập
          </Button>
        </Stack>
      ) : token ? (
        <PasswordForm
          confirmation={confirmation}
          error={error}
          matches={matches}
          onConfirmationChange={setConfirmation}
          onPasswordChange={setPassword}
          onSubmit={submit}
          password={password}
          policy={policy}
          submitting={submitting}
          submitLabel="Đặt lại mật khẩu"
        />
      ) : null}
    </AdminAuthPageShell>
  );
}

export function AdminChangePasswordPage() {
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const policy = useMemo(() => evaluateAdminPassword(newPassword), [newPassword]);
  const matches = newPassword.length > 0 && newPassword === confirmation;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!policy.valid || !matches || submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await axiosClient.post(ENDPOINTS.ADMIN_CHANGE_PASSWORD, {
        currentPassword,
        newPassword,
      });
      clearAuthSession('admin');
      navigate(`${ROUTES.ADMIN.LOGIN}?reason=password-changed`, { replace: true });
    } catch (requestError: unknown) {
      setError(getAdminPasswordError(requestError, 'change'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ maxWidth: 640 }}>
      <Typography component="h1" sx={{ fontSize: '1.75rem', fontWeight: 800, mb: 1 }}>
        Đổi mật khẩu
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 4 }}>
        Tất cả phiên đang đăng nhập sẽ bị thu hồi sau khi đổi thành công.
      </Typography>
      <Box component="form" onSubmit={submit}>
        {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
        <PasswordField
          autoComplete="current-password"
          label="Mật khẩu hiện tại"
          onChange={setCurrentPassword}
          value={currentPassword}
        />
        <Box sx={{ mt: 3 }}>
          <PasswordFields
            confirmation={confirmation}
            matches={matches}
            onConfirmationChange={setConfirmation}
            onPasswordChange={setNewPassword}
            password={newPassword}
            policy={policy}
          />
        </Box>
        <Button
          disabled={!currentPassword || !policy.valid || !matches || submitting}
          sx={{ mt: 3 }}
          type="submit"
          variant="contained"
        >
          {submitting ? 'Đang đổi...' : 'Đổi mật khẩu'}
        </Button>
      </Box>
    </Box>
  );
}

interface PasswordFormProps {
  confirmation: string;
  error: string | null;
  matches: boolean;
  onConfirmationChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  password: string;
  policy: ReturnType<typeof evaluateAdminPassword>;
  submitting: boolean;
  submitLabel: string;
}

function PasswordForm(props: PasswordFormProps) {
  return (
    <Box component="form" onSubmit={props.onSubmit}>
      {props.error && <Alert severity="error" sx={{ mb: 3 }}>{props.error}</Alert>}
      <PasswordFields
        confirmation={props.confirmation}
        matches={props.matches}
        onConfirmationChange={props.onConfirmationChange}
        onPasswordChange={props.onPasswordChange}
        password={props.password}
        policy={props.policy}
      />
      <Button
        disabled={!props.policy.valid || !props.matches || props.submitting}
        fullWidth
        sx={{ mt: 3 }}
        type="submit"
        variant="contained"
      >
        {props.submitting ? 'Đang xử lý...' : props.submitLabel}
      </Button>
    </Box>
  );
}

interface PasswordFieldsProps {
  confirmation: string;
  matches: boolean;
  onConfirmationChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  password: string;
  policy: ReturnType<typeof evaluateAdminPassword>;
}

function PasswordFields(props: PasswordFieldsProps) {
  return (
    <Stack spacing={2}>
      <PasswordField
        autoComplete="new-password"
        label="Mật khẩu mới"
        onChange={props.onPasswordChange}
        value={props.password}
      />
      <PasswordField
        autoComplete="new-password"
        error={props.confirmation.length > 0 && !props.matches}
        helperText={
          props.confirmation.length > 0 && !props.matches
            ? 'Mật khẩu xác nhận chưa khớp.'
            : undefined
        }
        label="Xác nhận mật khẩu mới"
        onChange={props.onConfirmationChange}
        value={props.confirmation}
      />
      <Box
        aria-label="Yêu cầu mật khẩu"
        aria-live="polite"
        component="ul"
        sx={{
          display: 'grid',
          gap: 1,
          gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
          listStyle: 'none',
          m: 0,
          p: 0,
        }}
      >
        {Object.entries(POLICY_LABELS).map(([key, label]) => {
          const passed = props.policy.checks[key as keyof typeof props.policy.checks];
          return (
            <Box
              component="li"
              key={key}
              sx={{ alignItems: 'center', display: 'flex', gap: 1 }}
            >
              <CheckCircleOutlineIcon
                aria-hidden
                color={passed ? 'success' : 'disabled'}
                fontSize="small"
              />
              <Typography
                color={passed ? 'success.main' : 'text.secondary'}
                component="span"
                variant="body2"
              >
                <Box component="span" sx={VISUALLY_HIDDEN}>
                  {passed ? 'Đạt: ' : 'Chưa đạt: '}
                </Box>
                {label}
              </Typography>
            </Box>
          );
        })}
      </Box>
    </Stack>
  );
}

interface PasswordFieldProps {
  autoComplete: string;
  error?: boolean;
  helperText?: string;
  label: string;
  onChange: (value: string) => void;
  value: string;
}

function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      autoComplete={props.autoComplete}
      error={props.error}
      fullWidth
      helperText={props.helperText}
      label={props.label}
      onChange={(event) => props.onChange(event.target.value)}
      required
      type={visible ? 'text' : 'password'}
      value={props.value}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <LockOutlinedIcon />
            </InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                edge="end"
                onClick={() => setVisible((current) => !current)}
              >
                {visible ? <VisibilityOffIcon /> : <VisibilityIcon />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}

function AdminAuthPageShell({
  children,
  description,
  title,
}: {
  children: ReactNode;
  description: string;
  title: string;
}) {
  return (
    <Box
      component="main"
      sx={{
        bgcolor: 'background.default',
        display: 'grid',
        minHeight: '100vh',
        p: 2,
        placeItems: 'center',
      }}
    >
      <Box
        sx={{
          bgcolor: 'background.paper',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          maxWidth: 520,
          p: { xs: 3, sm: 5 },
          width: '100%',
        }}
      >
        <Link
          component={RouterLink}
          sx={{
            alignItems: 'center',
            display: 'inline-flex',
            fontSize: '1.1rem',
            fontWeight: 800,
            gap: 1,
            mb: 4,
            textDecoration: 'none',
          }}
          to={ROUTES.PUBLIC.HOME}
        >
          <Box
            component="img"
            src="/manabihub-header-logo.png"
            alt="ManabiHub"
            sx={{ display: 'block', height: 58, width: 'auto' }}
          />
        </Link>
        <Typography component="h1" sx={{ fontSize: '1.75rem', fontWeight: 800, mb: 1 }}>
          {title}
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 4 }}>
          {description}
        </Typography>
        {children}
      </Box>
    </Box>
  );
}

function readFragmentToken() {
  if (typeof window === 'undefined') return '';
  return new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token') ?? '';
}

type AdminPasswordOperation = 'change' | 'reset';

function getAdminPasswordError(
  requestError: unknown,
  operation: AdminPasswordOperation,
) {
  const unavailable = 'Dịch vụ tạm thời không khả dụng. Dữ liệu bạn nhập vẫn được giữ; vui lòng thử lại.';
  if (!axios.isAxiosError(requestError) || !requestError.response) {
    return unavailable;
  }

  const status = requestError.response.status;
  const responseData = requestError.response.data as {
    errorCode?: string;
    messageCode?: string;
  } | undefined;
  const code = responseData?.messageCode ?? responseData?.errorCode;

  if (status >= 500) {
    return unavailable;
  }
  if (status === 429 || code === 'ADMIN_PASSWORD_CHANGE_RATE_LIMITED') {
    return 'Bạn đã thử quá nhiều lần. Vui lòng đợi trước khi thử lại.';
  }
  if (code === 'ADMIN_PASSWORD_REUSE_FORBIDDEN') {
    return 'Mật khẩu mới không được trùng với mật khẩu hiện tại.';
  }
  if (code === 'INTERNAL_ADMIN_PASSWORD_INVALID') {
    return 'Mật khẩu mới chưa đáp ứng đầy đủ chính sách bảo mật.';
  }
  if (
    operation === 'reset'
    && (
      code === 'ADMIN_PASSWORD_RESET_INVALID'
      || status === 404
      || status === 410
    )
  ) {
    return 'Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng.';
  }
  if (operation === 'change' && code === 'ADMIN_CURRENT_PASSWORD_INVALID') {
    return 'Mật khẩu hiện tại không chính xác.';
  }

  return operation === 'reset'
    ? 'Không thể đặt lại mật khẩu với yêu cầu này. Vui lòng kiểm tra thông tin và thử lại.'
    : 'Không thể đổi mật khẩu với yêu cầu này. Vui lòng kiểm tra thông tin và thử lại.';
}

const VISUALLY_HIDDEN = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  height: 1,
  m: -1,
  overflow: 'hidden',
  p: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
  width: 1,
} as const;
