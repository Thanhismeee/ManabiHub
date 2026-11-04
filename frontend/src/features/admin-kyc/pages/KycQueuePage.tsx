import { useCallback, useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { Link, useNavigate } from 'react-router-dom';
import { 
  Box, 
  Button, 
  Typography, 
  Paper, 
  Table, 
  TableBody, 
  TableCell, 
  TableContainer, 
  TableHead, 
  TableRow,
  Chip,
  Alert,
  Stack,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import { adminKycService, KYC_STATUS_LABELS } from '../services/adminKycService';
import type { KycRequestResponse, TeacherQualificationResponse } from '../services/adminKycService';
import { PageSkeleton } from '../../../shared/components/PageSkeleton/PageSkeleton';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { languageLabel, proficiencyLabel } from '../../../shared/learning/languageProfiles';

const getStatusColor = (status: string) => {
  switch (status) {
    case 'APPROVED': return 'success';
    case 'PENDING': return 'warning';
    case 'REJECTED': return 'error';
    case 'CORRECTION_REQUIRED': return 'warning';
    case 'REVOKED': return 'default';
    default: return 'default';
  }
};

const getRiskColor = (risk: string | undefined | null) => {
  if (!risk) return 'default';
  if (risk === 'LOW') return 'success';
  if (risk === 'MEDIUM') return 'warning';
  return 'error';
};

const getRiskLabel = (risk: string | undefined | null) => {
  if (!risk) return 'Chưa đánh giá';
  if (risk === 'LOW') return 'Thấp';
  if (risk === 'MEDIUM') return 'Trung bình';
  return 'Cao';
};

export function KycQueuePage() {
  const [queue, setQueue] = useState<KycRequestResponse[]>([]);
  const [qualifications, setQualifications] = useState<TeacherQualificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const navigate = useNavigate();

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [legacyQueue, qualificationQueue] = await Promise.all([
        adminKycService.getPendingKycQueue(),
        adminKycService.getPendingQualifications(),
      ]);
      setQueue(legacyQueue);
      setQualifications(qualificationQueue);
    } catch (requestError) {
      if (isAxiosError(requestError) && requestError.response?.status === 401) {
        setError('UNAUTHORIZED');
      } else if (isAxiosError(requestError) && requestError.response?.status === 403) {
        setError('ACCESS_DENIED');
      } else {
        setError('ERROR');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  async function openQualificationEvidence(path: string) {
    try {
      const objectUrl = await adminKycService.getQualificationEvidenceObjectUrl(path);
      window.open(objectUrl, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch {
      setActionMessage({ severity: 'error', text: 'Không thể mở tài liệu xác minh.' });
    }
  }

  async function decideQualification(item: TeacherQualificationResponse, approved: boolean) {
    let reviewNote: string | undefined;
    if (approved) {
      if (!window.confirm(`Duyệt năng lực ${proficiencyLabel(item.proficiencyFramework, item.credentialLevel)} cho ${item.teacherName ?? 'giáo viên này'}?`)) {
        return;
      }
    } else {
      const reason = window.prompt('Nhập lý do từ chối hoặc nội dung giáo viên cần bổ sung:');
      if (!reason?.trim()) return;
      reviewNote = reason.trim();
    }
    try {
      setProcessingId(item.id);
      await adminKycService.decideQualification(item.id, approved, reviewNote);
      setActionMessage({
        severity: 'success',
        text: approved ? 'Đã duyệt năng lực giảng dạy.' : 'Đã gửi yêu cầu bổ sung hồ sơ.',
      });
      await loadQueue();
    } catch (requestError) {
      const text = isAxiosError(requestError)
        ? requestError.response?.data?.message ?? 'Không thể cập nhật hồ sơ.'
        : 'Không thể cập nhật hồ sơ.';
      setActionMessage({ severity: 'error', text });
    } finally {
      setProcessingId(null);
    }
  }

  useEffect(() => {
    void loadQueue();
  }, [loadQueue]);

  if (loading) {
    return <PageSkeleton variant="table" />;
  }

  if (error) {
    if (error === 'UNAUTHORIZED') {
      return (
        <ErrorState 
          title="Phiên đăng nhập hết hạn" 
          message="Vui lòng đăng nhập lại để tiếp tục."
          retryLabel="Về trang chủ"
          onRetry={() => navigate('/')}
        />
      );
    }
    if (error === 'ACCESS_DENIED') {
      return (
        <ErrorState 
          title="Truy cập bị từ chối" 
          message="Bạn cần quyền Course Manager để xem danh sách này."
          retryLabel="Về trang chủ"
          onRetry={() => navigate('/')}
        />
      );
    }
    return (
      <ErrorState 
        message="Không thể tải hàng đợi KYC. Vui lòng kiểm tra lại kết nối backend."
        onRetry={loadQueue}
      />
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      {actionMessage && (
        <Alert severity={actionMessage.severity} onClose={() => setActionMessage(null)} sx={{ mb: 2 }}>
          {actionMessage.text}
        </Alert>
      )}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden', mb: 3 }}>
        <Box sx={{ p: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.default' }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Năng lực Hàn, Trung, Nhật chờ duyệt</Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
              Kiểm tra tài liệu, đơn vị cấp/xác nhận và chỉ duyệt đúng ngôn ngữ cùng khung năng lực mà giáo viên được phép giảng dạy.
            </Typography>
          </Box>
          <Chip label={`Tổng ${qualifications.length} hồ sơ`} color="warning" variant="outlined" size="small" sx={{ fontWeight: 'bold' }} />
        </Box>

        {qualifications.length === 0 ? (
          <Box sx={{ py: 5 }}>
            <EmptyState
              icon={<FactCheckIcon />}
              title="Không có hồ sơ năng lực đang chờ"
              description="Hồ sơ TOPIK, HSK, JLPT và xác nhận nội bộ SOFL sẽ xuất hiện tại đây."
            />
          </Box>
        ) : (
          <TableContainer>
            <Table sx={{ minWidth: 1000 }}>
              <TableHead sx={{ bgcolor: 'background.default' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>Giáo viên</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Phạm vi xin duyệt</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Loại hồ sơ</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Đơn vị xác nhận</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Ngày gửi</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {qualifications.map((item) => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography sx={{ fontWeight: 600 }}>{item.teacherName ?? 'Chưa cập nhật tên'}</Typography>
                      <Typography variant="body2" color="text.secondary">{item.teacherEmail}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography sx={{ fontWeight: 600 }}>{languageLabel(item.learningLanguage)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {proficiencyLabel(item.proficiencyFramework, item.credentialLevel)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {item.credentialType.replaceAll('_', ' ')}
                      {item.maskedCredentialCode && (
                        <Typography variant="body2" color="text.secondary">Mã: {item.maskedCredentialCode}</Typography>
                      )}
                    </TableCell>
                    <TableCell>{item.issuer}</TableCell>
                    <TableCell>{new Date(item.submittedAt).toLocaleString('vi-VN')}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                        {item.evidenceUrl && (
                          <Button size="small" variant="outlined" onClick={() => { void openQualificationEvidence(item.evidenceUrl as string); }}>
                            Xem tài liệu
                          </Button>
                        )}
                        <Button
                          size="small"
                          color="error"
                          disabled={processingId === item.id}
                          onClick={() => { void decideQualification(item, false); }}
                        >
                          Yêu cầu bổ sung
                        </Button>
                        <Button
                          size="small"
                          variant="contained"
                          color="success"
                          disabled={processingId === item.id}
                          onClick={() => { void decideQualification(item, true); }}
                        >
                          Duyệt
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
        <Box sx={{ p: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.default' }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Luồng JLPT cũ chờ xác minh</Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
              CCCD, OCR, đối chiếu danh tính và kiểm tra trùng đã đạt. Course Manager chỉ xác minh tính xác thực của chứng chỉ theo hướng dẫn chính thức của Japan Foundation.
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Chip label={`Tổng ${queue.length} hồ sơ`} color="warning" variant="outlined" size="small" sx={{ fontWeight: 'bold' }} />
            <Button
              startIcon={<RefreshIcon />}
              onClick={loadQueue}
              disabled={loading}
              sx={{ textTransform: 'none', color: 'common.white' }}
            >
              Làm mới
            </Button>
          </Box>
        </Box>

        {queue.length === 0 ? (
          <Box sx={{ py: 8 }}>
            <EmptyState 
              icon={<FactCheckIcon />}
              title="Không có hồ sơ cần xử lý"
              description="Hậu kiểm hồ sơ đã duyệt phải bắt đầu từ báo cáo vi phạm hoặc tín hiệu rủi ro có căn cứ."
            />
          </Box>
        ) : (
          <TableContainer>
            <Table sx={{ minWidth: 800 }}>
              <TableHead sx={{ bgcolor: 'background.default' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>Họ và tên hiển thị</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Email giáo viên</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Ngày gửi</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Xác thực VNPT</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Mức độ rủi ro</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {queue.map((req) => (
                  <TableRow key={req.id} hover>
                    <TableCell sx={{ fontWeight: 500 }}>{req.displayName}</TableCell>
                    <TableCell sx={{ color: 'text.secondary' }}>{req.teacherEmail}</TableCell>
                    <TableCell sx={{ color: 'text.secondary' }}>
                      {new Date(req.createdAt).toLocaleDateString('vi-VN', {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </TableCell>
                    <TableCell>
                      <Chip 
                        size="small"
                        label={KYC_STATUS_LABELS[req.status] || req.status} 
                        color={getStatusColor(req.status)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip 
                        size="small"
                        label={req.vnptVerificationStatus === 'SDK_VERIFIED' ? 'Đã xác minh' : 'Cần kiểm tra'} 
                        color={req.vnptVerificationStatus === 'SDK_VERIFIED' ? 'success' : 'error'}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip 
                        size="small"
                        label={getRiskLabel(req.riskLevel)} 
                        color={getRiskColor(req.riskLevel)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        component={Link}
                        to={`/admin/kyc/${req.id}`}
                        variant="contained"
                        color="error"
                        size="small"
                        disableElevation
                        sx={{ textTransform: 'none' }}
                      >
                        Xem chi tiết
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
}
