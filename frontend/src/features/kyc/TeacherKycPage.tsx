import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogContent,
  FormControlLabel,
  IconButton,
  Link,
  Paper,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import PermContactCalendarIcon from '@mui/icons-material/PermContactCalendar';
import GppGoodIcon from '@mui/icons-material/GppGood';
import LockIcon from '@mui/icons-material/Lock';
import RefreshIcon from '@mui/icons-material/Refresh';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SchoolIcon from '@mui/icons-material/School';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import { X } from 'lucide-react';
import React, { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { storeAuthToken } from '../../shared/auth/authSession';
import { ROUTES } from '../../shared/constants/routes';
import { recognizeJlptCertificate } from './certificateOcr';
import { TeacherQualificationsPanel } from './TeacherQualificationsPanel';
import {
  getTeacherKycStatus,
  restartTeacherVerification,
  submitTeacherCertificate,
  verifyTeacherIdentity,
  type ApiEnvelope,
  type KycCertificateSubmissionResponse,
  type KycIdentityVerificationResponse,
  type KycModuleStatusResponse,
  type KycRestartVerificationResponse,
  type KycStatusResponse,
} from './teacherKycApi';
import { launchVnptIdentitySdk, resetVnptIdentitySdkRuntime } from './vnptIdentitySdk';

const KYC_COLORS = {
  primaryTint: '#EEF2FF',
  primaryBorder: '#C7D2FE',
  surfaceMuted: '#F8FAFC',
  sdkShell: '#0F172A',
};

type CertificateErrors = Partial<
  Record<
    'certificate' | 'certificateCode' | 'holderName' | 'dateOfBirth' | 'level' | 'ocr' | 'agreement',
    string
  >
>;
type IdentitySummary = {
  fullName?: string;
  idNumber?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  hasData: boolean;
};
type IdentityDiagnostics = {
  providerStatus?: string;
  failureReasons: string[];
  validationHints: string[];
  hasData: boolean;
};

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const CERTIFICATE_TYPES = new Set(['image/jpeg', 'image/png']);
const IDENTITY_LAUNCH_COOLDOWN_MS = 60 * 1000;
const IDENTITY_LAUNCH_COOLDOWN_STORAGE_KEY = 'manabihub_kyc_identity_launch_cooldown_until';
const INSTRUCTOR_VERIFICATION_PATH = `${ROUTES.PUBLIC.HELP}/instructors/verification`;

class TeacherKycErrorBoundary extends React.Component<{ children: ReactNode }, { hasError: boolean; error: Error | null }> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      return (
        <Alert severity="error" sx={{ mt: 2 }}>
          Đã xảy ra lỗi giao diện khi tải màn hình này. Vui lòng thử tải lại trang hoặc liên hệ hỗ trợ.
          <br />
          <Typography variant="caption" sx={{ mt: 1, display: 'block' }}>
            {this.state.error?.message}
          </Typography>
        </Alert>
      );
    }
    return this.props.children;
  }
}

export function TeacherKycPage() {
  return (
    <TeacherKycErrorBoundary>
      <TeacherKycPageContent />
    </TeacherKycErrorBoundary>
  );
}

function TeacherKycPageContent() {
  const navigate = useNavigate();
  const [certificateFile, setCertificateFile] = useState<File | null>(null);
  const [certificateCode, setCertificateCode] = useState('');
  const [certificateHolderName, setCertificateHolderName] = useState('');
  const [certificateDateOfBirth, setCertificateDateOfBirth] = useState('');
  const [certificateLevel, setCertificateLevel] = useState('');
  const [certificateOcrText, setCertificateOcrText] = useState('');
  const [certificateOcrProgress, setCertificateOcrProgress] = useState(0);
  const [certificateOcrProcessing, setCertificateOcrProcessing] = useState(false);
  const [agreementAccepted, setAgreementAccepted] = useState(false);
  const [errors, setErrors] = useState<CertificateErrors>({});
  const [status, setStatus] = useState<KycStatusResponse | null>(null);
  const [identityEnvelope, setIdentityEnvelope] = useState<ApiEnvelope<KycIdentityVerificationResponse> | null>(null);
  const [certificateEnvelope, setCertificateEnvelope] = useState<ApiEnvelope<KycCertificateSubmissionResponse> | null>(null);
  const [restartEnvelope, setRestartEnvelope] = useState<ApiEnvelope<KycRestartVerificationResponse> | null>(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [identityLaunching, setIdentityLaunching] = useState(false);
  const [identitySubmitting, setIdentitySubmitting] = useState(false);
  const [certificateSubmitting, setCertificateSubmitting] = useState(false);
  const [restartSubmitting, setRestartSubmitting] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [identityLaunchKey, setIdentityLaunchKey] = useState(0);
  const [identityCooldownUntil, setIdentityCooldownUntil] = useState(readIdentityCooldownUntil);
  const [identityCooldownNow, setIdentityCooldownNow] = useState(() => Date.now());
  const identityLaunchTokenRef = useRef(0);

  useEffect(() => {
    void refreshStatus({ showLoading: true });
    return () => {
      identityLaunchTokenRef.current += 1;
      clearVnptSdkContainer();
      resetVnptIdentitySdkRuntime();
    };
  }, []);

  useEffect(() => {
    if (identityCooldownUntil <= Date.now()) {
      return undefined;
    }

    const intervalId = window.setInterval(() => {
      setIdentityCooldownNow(Date.now());
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [identityCooldownUntil]);

  const statusLoadFailed = !loadingStatus && Boolean(statusError) && !status;
  const identityStatus = status?.identityVerification ?? fallbackIdentityStatus(statusLoadFailed);
  const certificateStatus = status?.certificateVerification ?? fallbackCertificateStatus(statusLoadFailed);
  const latestRequest = restartEnvelope?.data.request ?? certificateEnvelope?.data.request ?? identityEnvelope?.data.request ?? status?.latestRequest ?? null;
  const identityVerified = identityStatus.status === 'VERIFIED';
  const identitySummary = useMemo(() => extractIdentitySummary(latestRequest?.verificationPayload), [latestRequest?.verificationPayload]);
  const identityDiagnostics = useMemo(() => extractIdentityDiagnostics(latestRequest?.verificationPayload), [latestRequest?.verificationPayload]);
  
  const showRestartVerification = Boolean(status && ['REJECTED', 'CORRECTION_REQUIRED'].includes(status.teacherKycStatus));
  const canRestartVerification = showRestartVerification && !restartSubmitting && !identityLaunching && !certificateSubmitting;
  
  const hasNetworkError = Boolean(statusError);
  const identityCooldownRemainingSeconds = Math.max(0, Math.ceil((identityCooldownUntil - identityCooldownNow) / 1000));
  const identityLaunchOnCooldown = identityCooldownRemainingSeconds > 0;
  const canStartIdentity =
    !identityLaunching
    && !hasNetworkError
    && !identityLaunchOnCooldown
    && status?.teacherKycStatus !== 'APPROVED'
    && certificateStatus.status !== 'PENDING_REVIEW'
    && (identityStatus.canInteract || ['NOT_STARTED', 'FAILED'].includes(identityStatus.status));
  const canSubmitCertificate =
    identityVerified
    && certificateStatus.canInteract
    && !certificateSubmitting
    && !certificateOcrProcessing;
  const shouldShowStatusChips = !statusLoadFailed;
  const pageStatus = status?.teacherKycStatus ?? 'UNKNOWN';
  const pageStatusLabel = status?.teacherKycStatusLabel ?? 'Đang tải...';

  async function refreshStatus(options: { showLoading?: boolean } = {}) {
    if (options.showLoading) {
      setLoadingStatus(true);
    }

    try {
      setStatusError(null);
      const response = await getTeacherKycStatus();
      setStatus(response);
    } catch (error) {
      setStatusError(readErrorMessage(error));
    } finally {
      setLoadingStatus(false);
    }
  }

  function clearVnptSdkContainer() {
    document.getElementById('ekyc_sdk_intergrated')?.replaceChildren();
  }

  function startIdentityLaunchCooldown() {
    const cooldownUntil = Date.now() + IDENTITY_LAUNCH_COOLDOWN_MS;
    setIdentityCooldownUntil(cooldownUntil);
    setIdentityCooldownNow(Date.now());
    try {
      localStorage.setItem(IDENTITY_LAUNCH_COOLDOWN_STORAGE_KEY, String(cooldownUntil));
    } catch {
      // Cooldown storage is best-effort; in-memory state still protects the current tab.
    }
  }

  function finishIdentityLaunch() {
    identityLaunchTokenRef.current += 1;
    setIdentityLaunching(false);
    setIdentitySubmitting(false);
    clearVnptSdkContainer();
    resetVnptIdentitySdkRuntime();
  }

  function clearIdentityLaunchCooldown() {
    setIdentityCooldownUntil(0);
    setIdentityCooldownNow(Date.now());
    try {
      localStorage.removeItem(IDENTITY_LAUNCH_COOLDOWN_STORAGE_KEY);
    } catch {
      // best effort
    }
  }

  function handleCloseIdentityDialog() {
    if (identitySubmitting) {
      return;
    }
    finishIdentityLaunch();
  }

  async function handleStartIdentity() {
    if (identityLaunchOnCooldown) {
      return;
    }

    const launchToken = identityLaunchTokenRef.current + 1;
    identityLaunchTokenRef.current = launchToken;

    setActionError(null);
    setIdentityEnvelope(null);
    setIdentityLaunchKey((current) => current + 1);
    clearVnptSdkContainer();
    resetVnptIdentitySdkRuntime();
    setIdentityLaunching(true);
    setIdentitySubmitting(false);

    window.setTimeout(async () => {
      try {
        await waitForVnptContainer();
        if (identityLaunchTokenRef.current !== launchToken) {
          return;
        }

        clearVnptSdkContainer();
        await launchVnptIdentitySdk(
          async (result) => {
            if (identityLaunchTokenRef.current !== launchToken) {
              return;
            }

            setIdentitySubmitting(true);
            const response = await verifyTeacherIdentity(result);
            if (identityLaunchTokenRef.current !== launchToken) {
              return;
            }
            setIdentityEnvelope(response);
            finishIdentityLaunch();
            await refreshStatus();
          },
          {
            onError: (error) => {
              if (identityLaunchTokenRef.current !== launchToken) {
                return;
              }
              setActionError(readErrorMessage(error));
              clearIdentityLaunchCooldown();
              finishIdentityLaunch();
            },
          },
        );
        if (identityLaunchTokenRef.current === launchToken) {
          startIdentityLaunchCooldown();
        }
      } catch (error) {
        if (identityLaunchTokenRef.current !== launchToken) {
          return;
        }

        setActionError(readErrorMessage(error));
        finishIdentityLaunch();
      }
    }, 150);
  }

  async function handleRestartVerification() {
    setActionError(null);
    setIdentityEnvelope(null);
    setCertificateEnvelope(null);
    setRestartEnvelope(null);
    setRestartSubmitting(true);

    try {
      const response = await restartTeacherVerification();
      setRestartEnvelope(response);
      setCertificateFile(null);
      setCertificateCode('');
      setCertificateHolderName('');
      setCertificateDateOfBirth('');
      setCertificateLevel('');
      setCertificateOcrText('');
      setCertificateOcrProgress(0);
      setAgreementAccepted(false);
      setErrors({});
      await refreshStatus();
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      setRestartSubmitting(false);
    }
  }

  async function handleCertificateChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setCertificateFile(file);
    setCertificateCode('');
    setCertificateHolderName('');
    setCertificateDateOfBirth('');
    setCertificateLevel('');
    setCertificateOcrText('');
    setCertificateOcrProgress(0);
    setErrors((current) => ({ ...current, certificate: undefined, ocr: undefined }));
    setCertificateEnvelope(null);

    if (!file) {
      return;
    }
    if (file.size > MAX_FILE_SIZE || !CERTIFICATE_TYPES.has(file.type)) {
      setErrors((current) => ({
        ...current,
        certificate:
          file.size > MAX_FILE_SIZE
            ? 'Ảnh chứng chỉ không được vượt quá 5MB.'
            : 'Chỉ chấp nhận ảnh JPG hoặc PNG của chứng chỉ JLPT.',
      }));
      return;
    }

    setCertificateOcrProcessing(true);
    try {
      const result = await recognizeJlptCertificate(file, setCertificateOcrProgress);
      setCertificateOcrText(result.rawText);
      setCertificateHolderName(result.holderName);
      setCertificateDateOfBirth(result.dateOfBirth);
      setCertificateLevel(result.level);
      if (result.certificateCode) {
        setCertificateCode(result.certificateCode);
      }
      if (
        !result.rawText
        || !result.holderName
        || !result.dateOfBirth
        || !result.level
        || !result.certificateCode
      ) {
        setErrors((current) => ({
          ...current,
          ocr: 'OCR chưa đọc đủ họ tên, ngày sinh, cấp độ JLPT và mã chứng chỉ. Hãy dùng ảnh rõ, thẳng, đủ sáng và không cắt mất mép dưới.',
        }));
      }
    } catch (error) {
      setErrors((current) => ({
        ...current,
        ocr: `Không thể đọc ảnh chứng chỉ: ${readErrorMessage(error)}`,
      }));
    } finally {
      setCertificateOcrProcessing(false);
    }
  }

  async function handleCertificateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (certificateSubmitting) {
      return;
    }
    setActionError(null);
    setCertificateEnvelope(null);

    const nextErrors = validateCertificateForm(
      certificateFile,
      certificateCode,
      certificateHolderName,
      certificateDateOfBirth,
      certificateLevel,
      certificateOcrText,
      agreementAccepted,
    );
    setErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setCertificateSubmitting(true);

    try {
      const response = await submitTeacherCertificate({
        certificate: certificateFile as File,
        certificateCode,
        certificateHolderName,
        certificateDateOfBirth,
        certificateLevel,
        certificateOcrText,
        copyrightAgreementAccepted: agreementAccepted,
      });
      storeAuthToken('public', response.data.sessionToken);
      setCertificateEnvelope(response);
      await refreshStatus();
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      setCertificateSubmitting(false);
    }
  }

  const activeStep = useMemo(() => {
    if (certificateStatus.status === 'PENDING_REVIEW' || certificateStatus.status === 'APPROVED') return 2;
    if (identityVerified) return 1;
    return 0;
  }, [identityVerified, certificateStatus.status]);

  const steps = ['Xác minh danh tính', 'Cung cấp hồ sơ chuyên môn', 'Hoàn tất'];
  const identityActionLabel = identityLaunching
    ? 'Đang mở xác thực...'
    : identityLaunchOnCooldown
      ? `Thử lại sau ${identityCooldownRemainingSeconds}s`
      : identityStatus.status === 'FAILED'
        ? 'Thực hiện lại'
        : 'Bắt đầu xác minh danh tính';

  return (
    <Stack spacing={3} sx={{ minWidth: 0 }}>
      {/* Page header */}
      <Paper
        elevation={0}
        sx={{
          bgcolor: 'background.paper',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          p: { xs: 2.5, md: 3.5 },
        }}
      >
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between', minWidth: 0 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h2" sx={{ color: 'text.primary', fontSize: { xs: 24, md: 30 }, fontWeight: 700, letterSpacing: '0.005em', mt: 0.5 }}>
              Xác minh giáo viên
            </Typography>
            <Typography sx={{ color: 'text.secondary', maxWidth: 720, mt: 1, fontSize: 15, lineHeight: 1.65 }}>
              Để trở thành giáo viên, vui lòng hoàn tất 2 bước: Xác minh danh tính và Cung cấp chứng chỉ chuyên môn.
              Quá trình này giúp bảo vệ tài khoản và chứng thực chuyên môn của bạn trên hệ thống.{' '}
              <Link
                component={RouterLink}
                to={INSTRUCTOR_VERIFICATION_PATH}
                sx={{
                  color: 'primary.main',
                  textDecoration: 'none',
                  fontWeight: 500,
                  '&:hover': { textDecoration: 'underline' }
                }}
              >
                Tìm hiểu thêm về chính sách KYC
              </Link>
            </Typography>
          </Box>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25} sx={{ alignItems: { sm: 'center' }, flexShrink: 0 }}>
            {shouldShowStatusChips && <StatusChip status={pageStatus} label={pageStatusLabel} />}
            {showRestartVerification && (
              <Button
                disabled={!canRestartVerification}
                onClick={handleRestartVerification}
                startIcon={<RestartAltIcon />}
                variant="outlined"
                sx={{
                  borderColor: 'primary.main',
                  color: 'primary.main',
                  '&:hover': { borderColor: 'primary.dark', bgcolor: KYC_COLORS.primaryTint },
                }}
              >
                {restartSubmitting ? 'Đang tạo lượt mới...' : 'Xác thực lại từ đầu'}
              </Button>
            )}
          </Stack>
        </Stack>
      </Paper>

      {/* Loading and error states */}
      {loadingStatus && !statusError && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
          <CircularProgress color="primary" size={32} />
          <Typography sx={{ ml: 2, color: 'text.secondary' }}>Đang tải thông tin xác minh...</Typography>
        </Box>
      )}
      {statusError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => { void refreshStatus({ showLoading: true }); }}>
              Thử lại
            </Button>
          }
        >
          {statusError === 'Network Error' ? 'Lỗi kết nối. Vui lòng kiểm tra mạng và thử lại.' : statusError}
        </Alert>
      )}
      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)} aria-live="assertive">
          {actionError === 'Network Error' ? 'Lỗi kết nối. Vui lòng kiểm tra mạng và thử lại.' : actionError}
        </Alert>
      )}

      {/* Progress stepper */}
      <Box sx={{ py: 1 }}>
        <Stepper
          activeStep={activeStep}
          alternativeLabel
          sx={{
            '& .MuiStepIcon-root.Mui-active': { color: 'primary.main' },
            '& .MuiStepIcon-root.Mui-completed': { color: 'primary.main' },
          }}
        >
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      </Box>

      {/* Contextual alerts */}
      {identityEnvelope && (
        <Alert severity={identityEnvelope.data.identityVerification.status === 'VERIFIED' ? 'success' : 'warning'} icon={<VerifiedUserIcon />}>
          {identityEnvelope.data.identityVerification.statusLabel}
        </Alert>
      )}
      {certificateEnvelope && (
        <Alert
          severity="success"
          icon={<FactCheckIcon />}
          action={
            <Button color="inherit" onClick={() => navigate('/teacher/dashboard')} size="small">
              Vào trang giảng viên
            </Button>
          }
        >
          Đã nhận và đọc được chứng chỉ JLPT. Họ tên, ngày sinh đã khớp với CCCD và kiểm tra trùng
          đã đạt. Course Manager sẽ xác minh tính xác thực trong 1-2 ngày làm việc, không tính thứ
          Bảy, Chủ nhật và ngày nghỉ lễ. Bạn có thể sử dụng các tính năng giảng viên, nhưng khóa học
          chưa được hiển thị trên nền tảng cho tới khi hồ sơ được duyệt.
        </Alert>
      )}
      {restartEnvelope && (
        <Alert severity="success" icon={<RestartAltIcon />}>
          Đã tạo lượt xác thực mới. Bạn có thể bắt đầu lại từ Bước 1.
        </Alert>
      )}

      {/* Identity step */}
      <Stack spacing={3}>
        <ModuleCard
          icon={identityVerified ? <VerifiedUserIcon sx={{ color: 'primary.main' }} /> : <PermContactCalendarIcon sx={{ color: 'primary.main' }} />}
          index="Bước 1"
          hideStatus={!shouldShowStatusChips}
          status={identityStatus}
          title="Xác minh danh tính"
        >
          <Typography sx={{ color: 'text.secondary', fontSize: 14, mt: 1, lineHeight: 1.6 }}>
            Chuẩn bị CCCD gốc, cho phép camera và làm theo hướng dẫn trên màn hình.
          </Typography>
          <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1, fontStyle: 'italic' }}>
            Lưu ý: Màn hình chụp ảnh bảo mật sẽ hiển thị ở chế độ nền tối. Vui lòng chuẩn bị CCCD và ngồi ở nơi đủ ánh sáng.
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 2 }}>
            <Button
              disabled={!canStartIdentity}
              onClick={handleStartIdentity}
              startIcon={identityStatus.status === 'FAILED' || identityLaunchOnCooldown ? <RefreshIcon /> : <GppGoodIcon />}
              variant="contained"
              sx={{
                bgcolor: 'primary.main',
                '&:hover': { bgcolor: 'primary.dark' },
                '&.Mui-disabled': { bgcolor: 'action.disabledBackground', color: 'action.disabled' },
              }}
            >
              {identityActionLabel}
            </Button>
          </Stack>
          {identityLaunchOnCooldown && (
            <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1.25 }}>
              Tạm khóa thao tác để tránh gửi quá nhiều yêu cầu VNPT trong thời gian ngắn.
            </Typography>
          )}
          {identityStatus.status === 'FAILED' && !statusLoadFailed && <IdentityFailureDiagnosticsCard diagnostics={identityDiagnostics} />}
          {identityVerified && <IdentityOcrSummaryCard summary={identitySummary} />}
        </ModuleCard>

        <TeacherQualificationsPanel identityVerified={identityVerified} />

        {/* Certificate step */}
        {!identityVerified ? (
          /* Collapsed/locked Module 2 */
          <Card
            variant="outlined"
            sx={{
              opacity: 0.6,
              borderStyle: 'dashed',
              borderColor: 'grey.300',
            }}
          >
            <CardContent sx={{ p: { xs: 2, md: 2.5 }, '&:last-child': { pb: 2 } }}>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <LockIcon color="disabled" />
                <Box>
                  <Typography sx={{ color: 'text.disabled', fontSize: 12, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                    Bước 2
                  </Typography>
                  <Typography component="h3" sx={{ fontSize: 18, fontWeight: 600, color: 'text.disabled' }}>
                    Cung cấp chứng chỉ chuyên môn (Khóa)
                  </Typography>
                </Box>
              </Stack>
              <Typography sx={{ color: 'text.disabled', fontSize: 13, mt: 1 }}>
                Hoàn tất xác minh danh tính trước khi nộp chứng chỉ.
              </Typography>
            </CardContent>
          </Card>
        ) : (
          <Box component="form" onSubmit={handleCertificateSubmit}>
            <Stack spacing={3}>
              <ModuleCard
                icon={<SchoolIcon sx={{ color: 'primary.main' }} />}
                index="Bước 2"
                hideStatus={!shouldShowStatusChips}
                status={certificateStatus}
                title="Xác minh tự động JLPT (tùy chọn cho tiếng Nhật)"
              >
                <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>
                  Luồng này dành riêng cho chứng chỉ JLPT. Hệ thống sẽ đọc ảnh, đối chiếu họ tên và ngày sinh với
                  CCCD đã xác minh, đồng thời kiểm tra mã chứng chỉ có bị dùng trùng hay không.
                </Typography>

                <Paper elevation={0} sx={{ bgcolor: KYC_COLORS.surfaceMuted, border: '1px solid', borderColor: errors.certificate ? 'error.light' : KYC_COLORS.primaryBorder, borderRadius: 2, mt: 2, p: 2 }}>
                  <Stack spacing={1.25}>
                    <Typography sx={{ fontSize: 14, fontWeight: 600 }}>Ảnh chứng chỉ JLPT</Typography>
                    <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>
                      JPG hoặc PNG, tối đa 5MB. Chụp thẳng, đủ sáng và không cắt mất nội dung.
                    </Typography>
                    <Button
                      component="label"
                      disabled={!canSubmitCertificate}
                      startIcon={<UploadFileIcon />}
                      variant="outlined"
                      sx={{ borderColor: 'primary.main', color: 'primary.main', '&:hover': { borderColor: 'primary.dark', bgcolor: KYC_COLORS.primaryTint } }}
                    >
                      Tải chứng chỉ
                      <input hidden accept="image/jpeg,image/png" type="file" onChange={handleCertificateChange} />
                    </Button>
                    {certificateFile && (
                      <Typography sx={{ color: 'primary.main', fontSize: 13, fontWeight: 700, overflowWrap: 'anywhere' }}>
                        {certificateFile.name}
                      </Typography>
                    )}
                    {certificateOcrProcessing && (
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <CircularProgress size={18} />
                        <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>
                          Đang đọc chứng chỉ... {certificateOcrProgress}%
                        </Typography>
                      </Stack>
                    )}
                    {errors.certificate && <FieldError>{errors.certificate}</FieldError>}
                    {errors.ocr && <FieldError>{errors.ocr}</FieldError>}
                  </Stack>
                </Paper>

                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mt: 2 }}>
                  <TextField
                    disabled={!canSubmitCertificate}
                    error={Boolean(errors.holderName)}
                    fullWidth
                    helperText={errors.holderName}
                    label="Họ tên đọc từ chứng chỉ"
                    required
                    slotProps={{ input: { readOnly: true } }}
                    value={certificateHolderName}
                  />
                  <TextField
                    disabled={!canSubmitCertificate}
                    error={Boolean(errors.dateOfBirth)}
                    fullWidth
                    helperText={errors.dateOfBirth}
                    label="Ngày sinh đọc từ chứng chỉ"
                    required
                    slotProps={{ inputLabel: { shrink: true }, input: { readOnly: true } }}
                    type="date"
                    value={certificateDateOfBirth}
                  />
                  <TextField
                    disabled={!canSubmitCertificate}
                    error={Boolean(errors.level)}
                    fullWidth
                    helperText={errors.level}
                    label="Cấp độ JLPT"
                    required
                    slotProps={{ input: { readOnly: true } }}
                    value={certificateLevel}
                  />
                </Stack>

                <TextField
                  fullWidth
                  disabled={!canSubmitCertificate}
                  error={Boolean(errors.certificateCode)}
                  helperText={errors.certificateCode}
                  label="Mã chứng chỉ"
                  placeholder="Hệ thống tự động đọc mã số từ chứng chỉ"
                  margin="normal"
                  required
                  slotProps={{ input: { readOnly: true } }}
                  value={certificateCode}
                  sx={{
                    '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: 'primary.main' },
                    '& .MuiInputLabel-root.Mui-focused': { color: 'primary.main' },
                  }}
                />
                <TextField
                  fullWidth
                  label="Dữ liệu OCR đã chuẩn hóa để đối chiếu"
                  margin="normal"
                  minRows={4}
                  multiline
                  slotProps={{ input: { readOnly: true } }}
                  value={certificateOcrText}
                />
                <Typography sx={{ color: 'text.secondary', fontSize: 12 }}>
                  Hệ thống chỉ gửi các trường đọc được chắc chắn (họ tên, ngày sinh, cấp độ và mã
                  chứng chỉ), không gửi toàn bộ nền bảo an/tiếng Nhật bị nhiễu. Nếu còn thiếu trường,
                  hãy chụp lại ảnh thẳng, đủ sáng và không cắt mất nội dung.
                </Typography>
              </ModuleCard>

              <Card variant="outlined">
                <CardContent sx={{ p: { xs: 2, md: 3 } }}>
                  <Stack spacing={1.5}>
                    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                      <FactCheckIcon sx={{ color: 'primary.main' }} />
                      <Box>
                        <Typography sx={{ color: 'text.secondary', fontSize: 12, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                          Cam kết bản quyền
                        </Typography>
                        <Typography component="h3" sx={{ fontSize: 20, fontWeight: 700, letterSpacing: '0.004em' }}>
                          Thỏa thuận trách nhiệm bản quyền nội dung số
                        </Typography>
                      </Box>
                    </Stack>
                    <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>
                      Cam kết này áp dụng cho toàn bộ nội dung bạn gửi hoặc xuất bản với vai trò Giáo viên.
                      Trước khi xác nhận, vui lòng đọc{' '}
                      <Link
                        component={RouterLink}
                        onClick={(event) => event.stopPropagation()}
                        rel="noreferrer"
                        target="_blank"
                        to={ROUTES.PUBLIC.INSTRUCTOR_TERMS}
                      >
                        Điều khoản dành cho giảng viên
                      </Link>{' '}
                      và{' '}
                      <Link
                        component={RouterLink}
                        onClick={(event) => event.stopPropagation()}
                        rel="noreferrer"
                        target="_blank"
                        to={ROUTES.PUBLIC.TERMS}
                      >
                        Điều khoản sử dụng
                      </Link>.
                    </Typography>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={agreementAccepted}
                          disabled={!canSubmitCertificate}
                          onChange={(event) => {
                            setAgreementAccepted(event.target.checked);
                            setErrors((current) => ({ ...current, agreement: undefined }));
                          }}
                          sx={{ '&.Mui-checked': { color: 'primary.main' } }}
                        />
                      }
                      label="Tôi đã đọc Điều khoản dành cho giảng viên và chấp nhận cam kết trách nhiệm bản quyền: tôi có quyền sử dụng hợp lệ đối với nội dung số mình gửi lên ManabiHub."
                      sx={{ alignItems: 'flex-start' }}
                    />
                    {errors.agreement && <FieldError>{errors.agreement}</FieldError>}
                  </Stack>
                </CardContent>
              </Card>

              <Button
                disabled={!canSubmitCertificate || !certificateOcrText}
                fullWidth
                size="large"
                sx={{
                  py: 1.5,
                  fontWeight: 600,
                  bgcolor: 'primary.main',
                  '&:hover': { bgcolor: 'primary.dark' },
                  '&.Mui-disabled': { bgcolor: 'action.disabledBackground', color: 'action.disabled' },
                }}
                type="submit"
                variant="contained"
              >
                {certificateOcrProcessing
                  ? 'Đang đọc chứng chỉ...'
                  : certificateSubmitting
                    ? 'Đang nộp chứng chỉ...'
                    : 'Nộp chứng chỉ'}
              </Button>
            </Stack>
          </Box>
        )}
      </Stack>

      {/* VNPT SDK dialog */}
      <Dialog 
        key={identityLaunchKey}
        open={identityLaunching} 
        maxWidth="md" 
        fullWidth 
        onClose={(_, reason) => {
          if (!identitySubmitting && reason !== 'backdropClick') {
            handleCloseIdentityDialog();
          }
        }}
        sx={{
          '& .MuiDialog-paper': {
            bgcolor: KYC_COLORS.sdkShell,
            height: '90vh',
            p: 0,
            m: 2,
            position: 'relative',
            overflow: 'visible',
            borderRadius: 2,
          },
        }}
      >
        {/* Keep the close button above SDK controls. */}
        <IconButton
          aria-label="Đóng xác minh VNPT"
          onClick={handleCloseIdentityDialog}
          disabled={identitySubmitting}
          sx={{
            position: 'absolute',
            top: { xs: 10, sm: -18 },
            right: { xs: 10, sm: -18 },
            zIndex: 9999,
            color: 'white',
            bgcolor: '#111827',
            border: '2px solid rgba(255,255,255,0.9)',
            boxShadow: 4,
            '&:hover': { bgcolor: '#0F172A' },
            width: 44,
            height: 44,
          }}
        >
          <X size={20} />
        </IconButton>
        <DialogContent sx={{ borderRadius: 'inherit', display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden', p: 0, position: 'relative' }}>
          {identitySubmitting && (
            <Stack
              role="status"
              spacing={1.5}
              sx={{
                position: 'absolute',
                inset: 0,
                zIndex: 9998,
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'rgba(15, 23, 42, 0.92)',
                color: 'white',
              }}
            >
              <CircularProgress color="inherit" />
              <Typography sx={{ fontWeight: 700 }}>
                Đang ghi nhận kết quả xác minh. Không đóng cửa sổ này.
              </Typography>
            </Stack>
          )}
          <Box
            key={`vnpt-sdk-${identityLaunchKey}`}
            id="ekyc_sdk_intergrated"
            sx={{ 
              flexGrow: 1, 
              minWidth: 0,
              minHeight: 0,
              width: '100%', 
              position: 'relative',
              /* Push SDK language dropdown away from close button */
              '& select, & [class*="language"], & [class*="lang"]': {
                marginRight: '48px !important',
              },
              '& video': {
                objectFit: 'cover',
                width: '100% !important',
                height: '100% !important',
                maxWidth: 'none !important',
              },
            }}
          />
        </DialogContent>
      </Dialog>
    </Stack>
  );
}

/* Reusable components */

function ModuleCard({
  children,
  hideStatus = false,
  icon,
  index,
  status,
  title,
}: {
  children: ReactNode;
  hideStatus?: boolean;
  icon: ReactNode;
  index: string;
  status: KycModuleStatusResponse;
  title: string;
}) {
  return (
    <Card variant="outlined" sx={{ borderColor: KYC_COLORS.primaryBorder, borderRadius: 2 }}>
      <CardContent sx={{ p: { xs: 2, md: 3 } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', mb: 2, minWidth: 0 }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
            {icon}
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ color: 'primary.main', fontSize: 12, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                {index}
              </Typography>
              <Typography component="h3" sx={{ color: 'text.primary', fontSize: 20, fontWeight: 700, letterSpacing: '0.004em', overflowWrap: 'anywhere' }}>
                {title}
              </Typography>
            </Box>
          </Stack>
          {!hideStatus && <StatusChip status={status.status} label={status.statusLabel} />}
        </Stack>
        {status.detail && (
          <Typography sx={{ color: 'text.secondary', fontSize: 14, lineHeight: 1.6, mb: 2 }}>
            {status.detail}
          </Typography>
        )}
        {children}
      </CardContent>
    </Card>
  );
}

function FieldError({ children }: { children: string }) {
  return <Typography sx={{ color: 'error.main', fontSize: 13, fontWeight: 600 }}>{children}</Typography>;
}

function StatusChip({ status, label }: { status: string; label: string }) {
  return <Chip color={statusChipColor(status)} label={label} sx={{ borderRadius: 1.5, fontWeight: 500 }} />;
}

function IdentityOcrSummaryCard({ summary }: { summary: IdentitySummary }) {
  return (
    <Paper elevation={0} sx={{ bgcolor: KYC_COLORS.surfaceMuted, border: '1px solid', borderColor: KYC_COLORS.primaryBorder, borderRadius: 2, mt: 2, p: 2 }}>
      <Stack spacing={1.5}>
        <Typography sx={{ fontSize: 14, fontWeight: 600 }}>Thông tin đã xác minh từ CCCD</Typography>
        {summary.hasData ? (
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' } }}>
            <SummaryField label="Họ và tên" value={summary.fullName} />
            <SummaryField label="Số CCCD" value={summary.idNumber} />
            <SummaryField label="Ngày sinh" value={summary.dateOfBirth} />
            <SummaryField label="Giới tính" value={summary.gender} />
            <SummaryField label="Nơi thường trú" value={summary.address} wide />
          </Box>
        ) : (
          <Alert severity="warning">
            Hệ thống chưa nhận được đủ dữ liệu từ giấy tờ tùy thân.
            Bạn có thể bấm "Xác thực lại từ đầu" để thử lại.
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}

function IdentityFailureDiagnosticsCard({ diagnostics }: { diagnostics: IdentityDiagnostics }) {
  if (!diagnostics.hasData) {
    return (
      <Alert severity="warning" sx={{ mt: 2 }}>
        Hệ thống chưa nhận được kết quả xác minh. Vui lòng đóng cửa sổ và thực hiện lại.
      </Alert>
    );
  }

  return (
    <Paper elevation={0} sx={{ bgcolor: 'rgba(211, 47, 47, 0.04)', border: '1px solid', borderColor: 'error.light', borderRadius: 2, mt: 2, p: 2 }}>
      <Stack spacing={1.25}>
        <Typography sx={{ color: 'error.dark', fontSize: 14, fontWeight: 600 }}>
          Nguyên nhân chưa xác minh thành công
        </Typography>
        {diagnostics.providerStatus && <SummaryField label="Trạng thái" value={diagnostics.providerStatus} />}
        {diagnostics.failureReasons.length > 0 && (
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {diagnostics.failureReasons.map((reason) => (
              <Typography component="li" key={reason} sx={{ color: 'error.dark', fontSize: 14 }}>
                {reason}
              </Typography>
            ))}
          </Box>
        )}
        {diagnostics.validationHints.length > 0 && (
          <Collapse in>
            <Box>
              <Typography sx={{ color: 'text.secondary', fontSize: 12, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                Chi tiết kỹ thuật
              </Typography>
              <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>
                {diagnostics.validationHints.join(' | ')}
              </Typography>
            </Box>
          </Collapse>
        )}
      </Stack>
    </Paper>
  );
}

function SummaryField({ label, value, wide = false }: { label: string; value?: string; wide?: boolean }) {
  return (
    <Box sx={{ minWidth: 0, gridColumn: { sm: wide ? '1 / -1' : 'auto' } }}>
      <Typography sx={{ color: 'text.secondary', fontSize: 12, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
        {label}
      </Typography>
      <Typography sx={{ color: 'text.primary', fontSize: 15, fontWeight: 600, overflowWrap: 'anywhere' }}>{value || 'N/A'}</Typography>
    </Box>
  );
}

function statusChipColor(status: string): 'default' | 'success' | 'warning' | 'error' {
  if (['APPROVED', 'VERIFIED'].includes(status)) {
    return 'success';
  }

  if (['PENDING', 'PENDING_REVIEW', 'PROCESSING'].includes(status)) {
    return 'warning';
  }

  if (['REJECTED', 'CORRECTION_REQUIRED', 'FAILED'].includes(status)) {
    return 'error';
  }

  return 'default';
}

function validateCertificateForm(
  certificateFile: File | null,
  certificateCode: string,
  certificateHolderName: string,
  certificateDateOfBirth: string,
  certificateLevel: string,
  certificateOcrText: string,
  agreementAccepted: boolean,
) {
  const nextErrors: CertificateErrors = {};

  if (!certificateFile) {
    nextErrors.certificate = 'Vui lòng tải lên ảnh chứng chỉ JLPT.';
  } else if (certificateFile.size > MAX_FILE_SIZE) {
    nextErrors.certificate = 'Ảnh chứng chỉ không được vượt quá 5MB.';
  } else if (!CERTIFICATE_TYPES.has(certificateFile.type)) {
    nextErrors.certificate = 'Chỉ chấp nhận ảnh JPG hoặc PNG.';
  }

  if (!certificateCode.trim()) {
    nextErrors.certificateCode = 'Vui lòng nhập mã chứng chỉ JLPT.';
  }
  if (!certificateHolderName.trim()) {
    nextErrors.holderName = 'OCR phải đọc được họ tên trên chứng chỉ.';
  }
  if (!certificateDateOfBirth) {
    nextErrors.dateOfBirth = 'OCR phải đọc được ngày sinh trên chứng chỉ.';
  }
  if (!/^N[1-5]$/.test(certificateLevel)) {
    nextErrors.level = 'Chỉ chấp nhận cấp độ JLPT từ N1 đến N5.';
  }
  if (!certificateOcrText.trim()) {
    nextErrors.ocr = 'Không có kết quả OCR để đối chiếu. Vui lòng tải lại ảnh rõ hơn.';
  }

  if (!agreementAccepted) {
    nextErrors.agreement = 'Vui lòng đọc và chấp nhận cam kết trách nhiệm bản quyền nội dung số.';
  }

  return nextErrors;
}

function extractIdentitySummary(payload?: Record<string, unknown> | null): IdentitySummary {
  const entries = flattenPayloadEntries(payload);
  const rawDob = findPayloadValue(entries, ['dateOfBirth', 'birthDate', 'birthday', 'dob', 'ngaySinh']);
  const summary = {
    fullName: findPayloadValue(entries, ['fullName', 'full_name', 'hoTen', 'ho_ten', 'customerName', 'name']),
    idNumber: findPayloadValue(entries, ['idNumber', 'idNo', 'identityNumber', 'documentNumber', 'cardNumber', 'soCccd', 'cccd', 'id']),
    dateOfBirth: formatDateOfBirth(rawDob),
    gender: findPayloadValue(entries, ['gender', 'sex', 'gioiTinh']),
    address: findPayloadValue(entries, ['address', 'residentAddress', 'permanentAddress', 'noiThuongTru', 'thuongTru']),
  };

  return {
    ...summary,
    hasData: Object.values(summary).some(Boolean),
  };
}

/** Normalize raw OCR date formats to dd/MM/yyyy for display */
function formatDateOfBirth(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const trimmed = raw.trim();

  // Format: ddMMyyyy (8 digits, no separator) → dd/MM/yyyy
  if (/^\d{8}$/.test(trimmed)) {
    return `${trimmed.slice(0, 2)}/${trimmed.slice(2, 4)}/${trimmed.slice(4)}`;
  }

  // Format: yyyy-MM-dd → dd/MM/yyyy
  const isoMatch = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoMatch) {
    return `${isoMatch[3]}/${isoMatch[2]}/${isoMatch[1]}`;
  }

  // Already dd/MM/yyyy or other format → keep as-is
  return trimmed;
}

function extractIdentityDiagnostics(payload?: Record<string, unknown> | null): IdentityDiagnostics {
  const entries = flattenPayloadEntries(payload);
  const providerStatus = findPayloadValue(entries, ['providerStatus']);
  const failureReasons = extractStringList(findRawPayloadValue(entries, ['failureReasons'])).map(localizeIdentityFailureReason);
  const validationHints = uniqueStrings(
    entries
      .map((entry) => toDisplayValue(entry.value))
      .filter((value): value is string => Boolean(value))
      .filter((value) => isVnptValidationHint(value))
      .map((value) => value.trim()),
  ).slice(0, 6);

  return {
    providerStatus,
    failureReasons,
    validationHints,
    hasData: Boolean(providerStatus) || failureReasons.length > 0 || validationHints.length > 0,
  };
}

function flattenPayloadEntries(value: unknown) {
  const entries: Array<{ path: string; key: string; value: unknown }> = [];

  function visit(current: unknown, path: string, depth: number) {
    if (current == null || depth > 8) {
      return;
    }

    if (Array.isArray(current)) {
      current.forEach((item, index) => visit(item, `${path}.${index}`, depth + 1));
      return;
    }

    if (typeof current === 'object') {
      Object.entries(current as Record<string, unknown>).forEach(([key, nestedValue]) => {
        const nextPath = path ? `${path}.${key}` : key;
        entries.push({ path: nextPath, key, value: nestedValue });
        visit(nestedValue, nextPath, depth + 1);
      });
    }
  }

  visit(value, '', 0);
  return entries;
}

function findRawPayloadValue(entries: Array<{ path: string; key: string; value: unknown }>, aliases: string[]) {
  const normalizedAliases = aliases.map(normalizePayloadKey);
  return entries.find((entry) => normalizedAliases.includes(normalizePayloadKey(entry.key)))?.value;
}

function findPayloadValue(entries: Array<{ path: string; key: string; value: unknown }>, aliases: string[]) {
  const normalizedAliases = aliases.map(normalizePayloadKey);
  const exact = entries.find((entry) => normalizedAliases.includes(normalizePayloadKey(entry.key)));
  const relaxed = exact ?? entries.find((entry) => {
    const normalizedPath = normalizePayloadKey(entry.path);
    return normalizedAliases.some((alias) => alias.length > 2 && normalizedPath.endsWith(alias));
  });

  return toDisplayValue(relaxed?.value);
}

function extractStringList(value: unknown) {
  if (Array.isArray(value)) {
    return value.map(toDisplayValue).filter((item): item is string => Boolean(item));
  }

  const scalar = toDisplayValue(value);
  return scalar ? [scalar] : [];
}

function localizeIdentityFailureReason(reason: string) {
  const normalized = normalizePayloadKey(reason);

  if (normalized.includes('terminalflowdidnotcomplete')) {
    return 'Phiên xác minh VNPT chưa hoàn tất. Vui lòng thực hiện lại từ đầu.';
  }

  if (normalized.includes('ocrreportedaninvaliddocument')) {
    return 'VNPT không xác nhận được ảnh CCCD. Hãy chụp rõ cả hai mặt giấy tờ gốc.';
  }

  if (normalized.includes('documentlivenessfailure')) {
    return 'VNPT không xác nhận được giấy tờ gốc. Hãy đặt CCCD trên nền sáng và thực hiện lại.';
  }

  if (normalized.includes('antispoofingfailure')) {
    return 'VNPT phát hiện tín hiệu an toàn không đạt trên giấy tờ. Hãy dùng CCCD gốc và thực hiện lại.';
  }

  if (normalized.includes('illegaldocumenttampering')) {
    return 'VNPT phát hiện dấu hiệu chỉnh sửa trên giấy tờ. Hãy kiểm tra lại CCCD gốc.';
  }

  if (normalized.includes('maskorfacelivenessfailure')) {
    return 'VNPT chưa xác nhận được khuôn mặt sống. Hãy bỏ khẩu trang, đủ ánh sáng và thực hiện lại.';
  }

  if (normalized.includes('facecomparisondidnotmatch')) {
    return 'Khuôn mặt chưa khớp với ảnh trên CCCD theo kết quả VNPT.';
  }

  if (normalized.includes('invaliddocument') || normalized.includes('mismatch') || normalized.includes('nullresult')) {
    return 'Giấy tờ không hợp lệ hoặc mặt trước/sau không cùng loại.';
  }

  if (normalized.includes('ocr') && normalized.includes('cccd')) {
    return 'Chưa đọc được đầy đủ số CCCD và họ tên từ ảnh.';
  }

  if (normalized.includes('liveness') || normalized.includes('facecompare')) {
    return 'Chưa xác nhận được khuôn mặt khớp với ảnh trên giấy tờ.';
  }

  if (normalized.includes('didnotreturn') && normalized.includes('payload')) {
    return 'Hệ thống chưa nhận được kết quả xác minh.';
  }

  return reason;
}

function isVnptValidationHint(value: string) {
  const normalized = normalizePayloadKey(value);
  return normalized.includes('khonghople')
    || normalized.includes('khongcungloai')
    || normalized.includes('khongkhop')
    || normalized.includes('thatbai')
    || normalized.includes('invalid')
    || normalized.includes('mismatch')
    || normalized.includes('failed')
    || normalized.includes('failure')
    || normalized.includes('null');
}

function uniqueStrings(values: string[]) {
  return Array.from(new Set(values));
}

function normalizePayloadKey(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toLowerCase();
}

function toDisplayValue(value: unknown) {
  if (typeof value === 'string') {
    const text = value.trim();
    return text.length > 0 && text.length <= 240 ? text : undefined;
  }

  if (typeof value === 'number') {
    return String(value);
  }

  return undefined;
}

async function waitForVnptContainer() {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const container = document.getElementById('ekyc_sdk_intergrated');
    if (container?.isConnected) {
      return;
    }

    await new Promise<void>((resolve) => {
      window.requestAnimationFrame(() => resolve());
    });
  }

  throw new Error('Không tìm thấy vùng hiển thị VNPT eKYC. Vui lòng thử lại.');
}

function readIdentityCooldownUntil() {
  try {
    const rawValue = localStorage.getItem(IDENTITY_LAUNCH_COOLDOWN_STORAGE_KEY);
    const parsedValue = rawValue ? Number(rawValue) : 0;
    return Number.isFinite(parsedValue) && parsedValue > Date.now() ? parsedValue : 0;
  } catch {
    return 0;
  }
}

function fallbackIdentityStatus(loadFailed: boolean): KycModuleStatusResponse {
  if (loadFailed) {
    return {
      status: 'UNAVAILABLE',
      statusLabel: 'Không tải được',
      canInteract: false,
      completedAt: null,
      detail: 'Không tải được trạng thái xác minh danh tính. Vui lòng thử lại sau khi backend sẵn sàng.',
    };
  }

  return {
    status: 'NOT_STARTED',
    statusLabel: 'Chưa xác minh danh tính',
    canInteract: false,
    completedAt: null,
    detail: 'Đang tải thông tin...',
  };
}

function fallbackCertificateStatus(loadFailed: boolean): KycModuleStatusResponse {
  if (loadFailed) {
    return {
      status: 'UNAVAILABLE',
      statusLabel: 'Không tải được',
      canInteract: false,
      completedAt: null,
      detail: 'Không tải được trạng thái chứng chỉ. Vui lòng kiểm tra kết nối và thử lại.',
    };
  }

  return {
    status: 'LOCKED',
    statusLabel: 'Chưa mở khóa',
    canInteract: false,
    completedAt: null,
    detail: 'Hoàn tất xác minh danh tính trước khi nộp chứng chỉ.',
  };
}

function readErrorMessage(error: unknown) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as {
      response?: {
        data?: {
          message?: string;
          messageCode?: string;
          message_code?: string;
          code?: string;
        };
      };
    }).response;
    const messageCode =
      response?.data?.messageCode ||
      response?.data?.code ||
      response?.data?.message_code;
    const message = response?.data?.message;

    if (messageCode === 'MSG-KYC-008') {
      return localizeKycConflict(message);
    }

    if (messageCode && KYC_ERROR_MESSAGES[messageCode]) {
      return KYC_ERROR_MESSAGES[messageCode];
    }

    return message || 'Đã xảy ra lỗi. Vui lòng thử lại.';
  }

  return error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.';
}

function localizeKycConflict(message?: string) {
  const normalized = (message ?? '').toLowerCase();
  if (normalized.includes('missing provider identifiers')) {
    return 'VNPT chưa trả về đủ mã phiên và mã giao dịch. Vui lòng chụp lại CCCD và thử lại.';
  }
  if (normalized.includes('maximum verification attempts')) {
    return 'Lượt xác minh hiện tại đã vượt số lần kiểm tra cho phép. Vui lòng bắt đầu lại lượt xác minh.';
  }
  if (normalized.includes('duplicate provider transaction')) {
    return 'Kết quả VNPT này đã được tiếp nhận. Vui lòng tải lại trạng thái trước khi thử lại.';
  }
  if (normalized.includes('claimed') || normalized.includes('another teacher')) {
    return 'Thông tin định danh này đã được sử dụng cho một tài khoản Giảng viên khác.';
  }
  return message || 'Kết quả xác minh đang xung đột với lượt hiện tại. Vui lòng tải lại trạng thái và thử lại.';
}

const KYC_ERROR_MESSAGES: Record<string, string> = {
  KYC_TEACHER_NOT_FOUND: 'Không thể khởi tạo hồ sơ xác minh. Vui lòng thử lại hoặc liên hệ bộ phận hỗ trợ.',
  KYC_ALREADY_PENDING: 'Hồ sơ của bạn đang được xét duyệt. Vui lòng chờ kết quả trước khi gửi lại.',
  KYC_ALREADY_APPROVED: 'Hồ sơ Giảng viên của bạn đã được phê duyệt.',
  'MSG-KYC-002': 'Thông tin xác minh chưa hợp lệ. Vui lòng kiểm tra và thực hiện lại.',
  'MSG-KYC-009': 'Không thể xác minh danh tính qua VNPT eKYC. Vui lòng thử lại hoặc gửi lại tài liệu.',
  'MSG-KYC-006': 'Họ tên hoặc ngày sinh trên chứng chỉ không khớp với thông tin CCCD đã xác minh.',
  KYC_CERTIFICATE_OCR_MISMATCH: 'Họ tên hoặc ngày sinh trên chứng chỉ không khớp với thông tin CCCD đã xác minh.',
  CERTIFICATE_IDENTITY_MISMATCH: 'Họ tên hoặc ngày sinh trên chứng chỉ không khớp với thông tin CCCD đã xác minh.',
  KYC_CERTIFICATE_ALREADY_CLAIMED: 'Chứng chỉ JLPT này đã được liên kết với một tài khoản Giảng viên khác.',
};
