import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  Button,
  TextField,
  Alert,
  CircularProgress,
  Stack,
  Breadcrumbs
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ShieldIcon from '@mui/icons-material/Shield';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';

import { adminKycService, KYC_STATUS_LABELS } from '../services/adminKycService';
import type { KycRequestResponse, KycReviewRequest } from '../services/adminKycService';
import { PageSkeleton } from '../../../shared/components/PageSkeleton/PageSkeleton';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog/ConfirmDialog';
import { StatusTag } from '../../../shared/components/StatusTag/StatusTag';

const JLPT_AUTHENTICITY_GUIDE = 'https://www.jlpt.jp/e/faq/';

interface SafeVnptDetails {
  provider?: string | null;
  providerStatus?: string | null;
  identityOcr?: {
    fullName?: string | null;
    dateOfBirth?: string | null;
    idNumber?: string | null;
  };
  failureReasons?: unknown;
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'APPROVED': return 'success';
    case 'PENDING': return 'warning';
    case 'REJECTED': return 'error';
    case 'CORRECTION_REQUIRED': return 'warning';
    case 'REVOKED': return 'default';
    default: return 'default';
  }
};

export function KycDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const [detail, setDetail] = useState<KycRequestResponse | null>(null);
  const [certificateImageUrl, setCertificateImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [documentLoading, setDocumentLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [decisionNote, setDecisionNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmState, setConfirmState] = useState<{ open: boolean; status: KycReviewRequest['status'] | null }>({ open: false, status: null });

  useEffect(() => {
    if (!id) {
      setError('Thiếu mã hồ sơ KYC.');
      setLoading(false);
      return;
    }
    adminKycService
      .getKycDetail(id)
      .then(setDetail)
      .catch(() => setError('Không thể tải hồ sơ KYC. Vui lòng kiểm tra phiên đăng nhập và backend.'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    if (!detail?.certificateUrl) {
      return undefined;
    }
    let disposed = false;
    let objectUrl: string | null = null;
    setDocumentLoading(true);
    adminKycService
      .getDocumentObjectUrl(detail.certificateUrl)
      .then((url) => {
        objectUrl = url;
        if (!disposed) {
          setCertificateImageUrl(url);
        }
      })
      .catch(() => {
        if (!disposed) {
          setError('Không thể tải ảnh chứng chỉ được bảo vệ.');
        }
      })
      .finally(() => {
        if (!disposed) {
          setDocumentLoading(false);
        }
      });
    return () => {
      disposed = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [detail?.certificateUrl]);

  const vnptDetails = useMemo(
    () => parseSafeVnptDetails(detail?.vnptResponseDetails),
    [detail?.vnptResponseDetails],
  );
  
  const reviewReady =
    detail?.status === 'PENDING'
    && detail.vnptVerificationStatus === 'SDK_VERIFIED'
    && detail.exceptionStage === 'CERTIFICATE'
    && detail.exceptionType === 'JLPT_AUTHENTICITY_CHECK'
    && Boolean(detail.certificateCode)
    && Boolean(detail.certificateHolderName)
    && Boolean(detail.certificateDateOfBirth)
    && Boolean(detail.certificateLevel)
    && Boolean(detail.certificateOcrText)
    && Boolean(certificateImageUrl);

  const handleReviewAction = (status: KycReviewRequest['status']) => {
    setNoteError(null);
    if (status !== 'APPROVED' && !decisionNote.trim()) {
      setNoteError('Vui lòng nhập lý do cụ thể.');
      return;
    }
    setConfirmState({ open: true, status });
  };

  const handleConfirmReview = async () => {
    if (!id || !detail || !confirmState.status) {
      return;
    }
    setSubmitting(true);
    try {
      await adminKycService.reviewKyc(id, {
        status: confirmState.status,
        decisionNote: decisionNote.trim() || undefined,
      });
      navigate('/admin/kyc');
    } catch {
      setError('Không thể lưu quyết định. Hồ sơ có thể đã được người khác xử lý hoặc chưa đủ điều kiện.');
      setSubmitting(false);
      setConfirmState({ open: false, status: null });
    }
  };

  if (loading) {
    return <PageSkeleton variant="detail" />;
  }

  if (!detail || error) {
    return (
      <ErrorState 
        title={!detail ? "Không tìm thấy hồ sơ" : "Lỗi tải hồ sơ"}
        message={error ?? 'Hồ sơ KYC không tồn tại.'}
        retryLabel="Quay lại hàng đợi"
        onRetry={() => navigate('/admin/kyc')}
      />
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1200, mx: 'auto' }}>
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Breadcrumbs aria-label="breadcrumb">
          <Button
            component={Link}
            to="/admin/kyc"
            startIcon={<ArrowBackIcon fontSize="small" />}
            color="inherit"
            sx={{ textTransform: 'none', color: 'text.secondary' }}
          >
            Hàng đợi JLPT
          </Button>
          <Typography color="text.primary" variant="body2" sx={{ fontFamily: 'monospace' }}>
            {detail.id}
          </Typography>
        </Breadcrumbs>
      </Box>

      <Paper elevation={0} sx={{ p: 3, mb: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 'bold' }}>Ứng viên giảng viên</Typography>
            <Typography variant="h5" sx={{ fontWeight: 'bold' }}>{detail.displayName || detail.teacherFullName}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{detail.teacherEmail}</Typography>
          </Box>
          <Box sx={{ textAlign: 'right' }}>
            <StatusTag status={getStatusType(detail.status)} label={KYC_STATUS_LABELS[detail.status] ?? detail.status} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Nộp lúc {new Date(detail.createdAt).toLocaleString('vi-VN')}
            </Typography>
          </Box>
        </Box>
      </Paper>

      <Box sx={{ mb: 3, display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 2 }}>
        <Gate label="CCCD qua VNPT" passed={detail.vnptVerificationStatus === 'SDK_VERIFIED'} />
        <Gate label="OCR đọc thành công" passed={Boolean(detail.certificateOcrText)} />
        <Gate label="Tên và ngày sinh khớp" passed={detail.exceptionType === 'JLPT_AUTHENTICITY_CHECK'} />
        <Gate label="Không trùng chứng chỉ" passed={detail.exceptionStage === 'CERTIFICATE'} />
      </Box>

      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 3 }}>
        <Box sx={{ flex: { md: 2 } }}>
          <Stack spacing={3}>
            <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 1 }}>
                <ShieldIcon color="success" />
                <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Danh tính đã xác minh qua VNPT</Typography>
              </Box>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 2 }}>
                <Evidence label="Họ tên CCCD" value={vnptDetails?.identityOcr?.fullName} />
                <Evidence label="Ngày sinh CCCD" value={vnptDetails?.identityOcr?.dateOfBirth} />
                <Evidence label="Số CCCD" value={vnptDetails?.identityOcr?.idNumber ? '***' + vnptDetails.identityOcr.idNumber.slice(-3) : null} />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
                Số CCCD được che trong giao diện quản trị. Dữ liệu thô của VNPT không được trả về trình duyệt.
              </Typography>
            </Paper>

            <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <FactCheckIcon color="error" />
                  <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Ảnh chứng chỉ JLPT</Typography>
                </Box>
                <Button 
                  href={JLPT_AUTHENTICITY_GUIDE} 
                  target="_blank" 
                  rel="noreferrer" 
                  size="small" 
                  color="primary"
                  sx={{ textTransform: 'none', fontWeight: 'bold' }}
                >
                  Hướng dẫn xác minh chính thức
                </Button>
              </Box>
              <Box sx={{ display: 'flex', minHeight: 300, alignItems: 'center', justifyContent: 'center', bgcolor: 'grey.50', borderRadius: 1, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
                {documentLoading ? (
                  <CircularProgress color="inherit" />
                ) : certificateImageUrl ? (
                  <img
                    alt="Chứng chỉ JLPT do ứng viên cung cấp"
                    style={{ maxHeight: 680, width: '100%', objectFit: 'contain' }}
                    src={certificateImageUrl}
                  />
                ) : (
                  <Typography color="error" variant="body2">Không có ảnh chứng chỉ để đối chiếu.</Typography>
                )}
              </Box>
            </Paper>

            <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>Dữ liệu OCR để đối chiếu</Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                <Evidence label="Họ tên chứng chỉ" value={detail.certificateHolderName} />
                <Evidence label="Ngày sinh chứng chỉ" value={detail.certificateDateOfBirth} />
                <Evidence label="Cấp độ" value={detail.certificateLevel} />
                <Evidence label="Mã chứng chỉ" value={detail.certificateCode} />
              </Box>
              <Box sx={{ mt: 3, p: 2, bgcolor: 'grey.900', borderRadius: 1, maxHeight: 200, overflow: 'auto' }}>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'grey.100', whiteSpace: 'pre-wrap' }}>
                  {detail.certificateOcrText ?? 'Không có dữ liệu OCR'}
                </Typography>
              </Box>
            </Paper>
          </Stack>
        </Box>

        <Box sx={{ flex: { md: 1 } }}>
          <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2, position: { md: 'sticky' }, top: { md: 24 } }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold' }} gutterBottom>Quyết định xác minh JLPT</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Hệ thống đã hoàn tất kiểm tra kỹ thuật. Hãy xác minh chứng chỉ không bị giả hoặc sửa đổi theo hướng dẫn của Japan Foundation trước khi phê duyệt.
            </Typography>

            {detail.status !== 'PENDING' ? (
              <Alert severity="info" sx={{ mt: 2 }}>
                <Typography variant="body2">Hồ sơ đã được xử lý bởi <strong>{detail.processedByEmail ?? 'quản trị viên'}</strong>.</Typography>
                {detail.decisionNote && <Typography variant="body2" sx={{ mt: 1 }}>Ghi chú: {detail.decisionNote}</Typography>}
                {detail.status === 'APPROVED' && (
                  <Typography variant="caption" sx={{ mt: 1, display: 'block' }}>
                    Thu hồi sau duyệt chỉ được thực hiện từ trust case đã xác minh, không nằm trong màn hình KYC.
                  </Typography>
                )}
              </Alert>
            ) : (
              <Box sx={{ mt: 2 }}>
                {!reviewReady && (
                  <Alert severity="warning" sx={{ mb: 2 }}>
                    Thiếu bằng chứng bắt buộc. Không phê duyệt cho tới khi VNPT, OCR, đối chiếu danh tính, kiểm tra trùng và ảnh chứng chỉ đều sẵn sàng.
                  </Alert>
                )}
                
                <TextField
                  fullWidth
                  multiline
                  rows={4}
                  label="Ghi chú quyết định"
                  placeholder="Nguồn kiểm tra, kết quả đối chiếu hoặc lý do yêu cầu bổ sung..."
                  value={decisionNote}
                  onChange={(e) => {
                    setDecisionNote(e.target.value);
                    setNoteError(null);
                  }}
                  error={Boolean(noteError)}
                  helperText={noteError}
                  sx={{ mb: 2 }}
                />

                <Stack spacing={1.5}>
                  <Button
                    variant="contained"
                    color="success"
                    startIcon={<CheckCircleIcon />}
                    onClick={() => handleReviewAction('APPROVED')}
                    disabled={!reviewReady || submitting}
                    disableElevation
                  >
                    Xác nhận chứng chỉ thật
                  </Button>
                  <Button
                    variant="contained"
                    color="warning"
                    onClick={() => handleReviewAction('CORRECTION_REQUIRED')}
                    disabled={submitting}
                    disableElevation
                  >
                    Yêu cầu nộp lại
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    onClick={() => handleReviewAction('REJECTED')}
                    disabled={submitting}
                  >
                    Từ chối hồ sơ
                  </Button>
                </Stack>
              </Box>
            )}
          </Paper>
        </Box>
      </Box>

      <ConfirmDialog
        open={confirmState.open}
        title="Xác nhận quyết định"
        message={`Bạn có chắc chắn muốn ${
          confirmState.status === 'APPROVED' ? 'PHÊ DUYỆT' : 
          confirmState.status === 'REJECTED' ? 'TỪ CHỐI' : 'YÊU CẦU NỘP LẠI'
        } hồ sơ này? Hành động này sẽ gửi thông báo đến giảng viên.`}
        confirmLabel="Xác nhận"
        cancelLabel="Hủy"
        confirmColor={confirmState.status === 'APPROVED' ? 'success' : confirmState.status === 'REJECTED' ? 'error' : 'warning'}
        loading={submitting}
        onConfirm={handleConfirmReview}
        onCancel={() => setConfirmState({ open: false, status: null })}
      />
    </Box>
  );
}

function Gate({ label, passed }: { label: string; passed: boolean }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        p: 1.5,
        borderRadius: 1,
        border: '1px solid',
        borderColor: passed ? 'success.light' : 'error.light',
        bgcolor: passed ? 'success.50' : 'error.50',
        color: passed ? 'success.main' : 'error.main'
      }}
    >
      {passed ? <CheckCircleIcon fontSize="small" /> : <HighlightOffIcon fontSize="small" />}
      <Typography variant="body2" sx={{ fontWeight: 'bold' }}>{label}</Typography>
    </Box>
  );
}

function Evidence({ label, value }: { label: string; value?: string | null }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 'medium', mt: 0.5, wordBreak: 'break-word' }}>
        {value || 'Không có dữ liệu'}
      </Typography>
    </Box>
  );
}

function parseSafeVnptDetails(value?: string | null): SafeVnptDetails | null {
  if (!value) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(value);
    if (parsed && typeof parsed === 'object') {
      return parsed as SafeVnptDetails;
    }
  } catch {
    return null;
  }
  return null;
}
