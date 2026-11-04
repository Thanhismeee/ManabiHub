import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { launchVnptIdentitySdk, resetVnptIdentitySdkRuntime } from '../../kyc/vnptIdentitySdk';
import { getStudentIdentityVerificationStatus, verifyStudentIdentity } from '../services/studentIdentityVerificationService';

type IdentityStatus = Awaited<ReturnType<typeof getStudentIdentityVerificationStatus>>;

export function StudentIdentityVerificationPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = readSafeReturnTo(searchParams.get('returnTo'));
  const [status, setStatus] = useState<IdentityStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [launching, setLaunching] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [verificationError, setVerificationError] = useState<string | null>(null);
  const launchToken = useRef(0);

  const loadStatus = useCallback(async () => {
    setLoading(true);
    setStatusError(null);
    try {
      setStatus(await getStudentIdentityVerificationStatus());
    } catch (error) {
      setStatusError(readErrorMessage(error, 'Không tải được trạng thái xác minh. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStatus();
    return () => {
      launchToken.current += 1;
      clearSdkContainer();
      resetVnptIdentitySdkRuntime();
    };
  }, [loadStatus]);

  const finishLaunch = (token: number) => {
    if (token !== launchToken.current) return;
    launchToken.current += 1;
    setLaunching(false);
    setSubmitting(false);
    clearSdkContainer();
    resetVnptIdentitySdkRuntime();
  };

  const startVerification = async () => {
    if (status?.verified || launching) return;

    const token = ++launchToken.current;
    setVerificationError(null);
    clearSdkContainer();
    resetVnptIdentitySdkRuntime();
    setLaunching(true);
    setSubmitting(false);

    try {
      await waitForSdkContainer();
      if (token !== launchToken.current) return;

      await launchVnptIdentitySdk(
        async (result) => {
          if (token !== launchToken.current) return;
          setSubmitting(true);
          const verified = await verifyStudentIdentity(result);
          if (token !== launchToken.current) return;
          setStatus(verified);
          finishLaunch(token);
        },
        {
          onError: (error) => {
            if (token !== launchToken.current) return;
            setVerificationError(readErrorMessage(error, 'Xác minh CCCD thất bại. Vui lòng thử lại.'));
            finishLaunch(token);
          },
        },
      );
    } catch (error) {
      if (token === launchToken.current) {
        setVerificationError(readErrorMessage(error, 'Xác minh CCCD thất bại. Vui lòng thử lại.'));
        finishLaunch(token);
      }
    }
  };

  const cancelVerification = () => {
    if (submitting) return;
    finishLaunch(launchToken.current);
  };

  return (
    <Box component="main" sx={{ minHeight: '100%', bgcolor: '#FAF9F6', px: { xs: 2, md: 4 }, py: { xs: 3, md: 5 } }}>
      <Box sx={{ maxWidth: 760, mx: 'auto' }}>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Xác minh CCCD để rút tiền</Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          VNPT eKYC đọc giấy tờ và khuôn mặt; ManabiHub chỉ gửi kết quả đã lược bỏ ảnh và thông tin xác thực
          tới backend để đối chiếu với dữ liệu CCCD mô phỏng của bản demo.
        </Typography>
        <Paper sx={{ p: { xs: 2.5, md: 4 }, borderRadius: 3 }}>
          {statusError && (
            <Alert
              severity="error"
              sx={{ mb: 2 }}
              action={<Button color="inherit" size="small" onClick={() => void loadStatus()}>Thử lại</Button>}
            >
              {statusError}
            </Alert>
          )}
          {verificationError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setVerificationError(null)} aria-live="assertive">
              {verificationError}
            </Alert>
          )}
          {loading ? <CircularProgress aria-label="Đang tải trạng thái xác minh" /> : (
            <Stack spacing={2}>
              <Alert severity={status?.verified ? 'success' : 'info'}>
                {status?.verified
                  ? `Đã xác minh${status.fullName ? `: ${status.fullName}` : ''}. Bạn có thể quay lại Ví & Thanh toán để tạo yêu cầu rút tiền.`
                  : 'Chưa xác minh. Hãy chuẩn bị CCCD gốc và thực hiện VNPT eKYC.'}
              </Alert>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <Button
                  variant="contained"
                  onClick={() => void startVerification()}
                  disabled={launching || status?.verified || Boolean(statusError)}
                >
                  {submitting
                      ? 'Đang ghi nhận kết quả…'
                      : launching
                        ? 'Đang mở VNPT eKYC…'
                        : status?.verified
                        ? 'Đã xác thực CCCD thành công'
                        : 'Bắt đầu xác minh'}
                </Button>
                {launching && (
                  <Button variant="outlined" color="inherit" onClick={cancelVerification} disabled={submitting}>
                    {submitting ? 'Không thể hủy khi đang ghi nhận' : 'Hủy'}
                  </Button>
                )}
                <Button variant="outlined" onClick={() => navigate(returnTo)} disabled={submitting}>
                  Quay lại Ví & Thanh toán
                </Button>
              </Stack>
              {launching && (
                <Box
                  id="ekyc_sdk_intergrated"
                  aria-label="Cửa sổ xác minh VNPT eKYC"
                  sx={{ minHeight: 520, bgcolor: '#111827', borderRadius: 2, overflow: 'hidden' }}
                />
              )}
            </Stack>
          )}
        </Paper>
      </Box>
    </Box>
  );
}

function clearSdkContainer() {
  document.getElementById('ekyc_sdk_intergrated')?.replaceChildren();
}

async function waitForSdkContainer() {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    if (document.getElementById('ekyc_sdk_intergrated')?.isConnected) return;
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
  }
  throw new Error('Không tìm thấy vùng hiển thị VNPT eKYC. Vui lòng thử lại.');
}

function readSafeReturnTo(value: string | null) {
  const allowed = new Set([
    ROUTES.STUDENT.PAYMENTS,
    ROUTES.STUDENT.DASHBOARD,
    ROUTES.STUDENT.MY_COURSES,
    ROUTES.STUDENT.BROWSE_COURSES,
    ROUTES.STUDENT.PROFILE,
  ]);
  if (value && allowed.has(value)) {
    return value;
  }
  return ROUTES.STUDENT.PAYMENTS;
}

function readErrorMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { message?: string; messageCode?: string } } })?.response?.data;
  if (data?.messageCode === 'MSG-KYC-002') {
    return 'Kết quả xác minh CCCD chưa hợp lệ. Vui lòng thực hiện lại đầy đủ các bước VNPT eKYC.';
  }
  if (data?.messageCode === 'MSG-KYC-008') {
    return 'CCCD này đã được liên kết hoặc lượt xác minh đang xung đột. Vui lòng tải lại trạng thái.';
  }
  if (data?.messageCode === 'MSG-IDV-002') {
    return 'CCCD này đã được liên kết với một tài khoản khác và không thể dùng để xác minh.';
  }
  const message = data?.message;
  return message || (error instanceof Error ? error.message : fallback);
}

export default StudentIdentityVerificationPage;
