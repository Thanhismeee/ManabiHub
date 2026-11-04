import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { adminDecisionReviewApi } from './adminDecisionReviewApi';
import type {
  DecisionDomain,
  DecisionReviewDetail,
  DecisionReviewFilters,
  DecisionReviewStatus,
  DecisionReviewSummary,
  DecisionWarningLevel,
} from './types';

const DOMAINS: DecisionDomain[] = ['KYC', 'COURSE', 'VIOLATION', 'REFUND', 'PAYOUT', 'EXPENSE'];
const REVIEW_STATUS: Record<DecisionReviewStatus, string> = {
  UNREVIEWED: 'Chưa xem', REVIEWED: 'Đã xem', WARNING_SENT: 'Đã cảnh báo',
};
const initialFilters: DecisionReviewFilters = { page: 0, size: 10, domain: '', decisionRole: '', reviewStatus: '', warningLevel: '', actor: '' };

export function DecisionReviewPage() {
  const [filters, setFilters] = useState(initialFilters);
  const [draft, setDraft] = useState(initialFilters);
  const [items, setItems] = useState<DecisionReviewSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [detail, setDetail] = useState<DecisionReviewDetail | null>(null);
  const [warningOpen, setWarningOpen] = useState(false);
  const [warningLevel, setWarningLevel] = useState<DecisionWarningLevel>('WARNING');
  const [warningNote, setWarningNote] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminDecisionReviewApi.search(filters);
      setItems(response.content);
      setTotal(response.totalElements);
    } catch {
      setError('Không thể tải danh sách quyết định. Tài khoản cần có vai trò System Admin.');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { void load(); }, [load]);

  const openDetail = async (id: string) => {
    setError(null);
    try { setDetail(await adminDecisionReviewApi.get(id)); }
    catch { setError('Không thể tải bằng chứng của quyết định.'); }
  };

  const markReviewed = async () => {
    if (!detail) return;
    setSaving(true);
    try {
      setDetail(await adminDecisionReviewApi.markReviewed(detail.auditLogId));
      setMessage('Đã ghi nhận hậu kiểm. Quyết định nghiệp vụ gốc không bị thay đổi.');
      await load();
    } catch { setError('Không thể ghi nhận hậu kiểm.'); }
    finally { setSaving(false); }
  };

  const sendWarning = async () => {
    if (!detail || warningNote.trim().length < 5) {
      setError('Nội dung cảnh báo cần ít nhất 5 ký tự.');
      return;
    }
    setSaving(true);
    try {
      setDetail(await adminDecisionReviewApi.sendWarning(detail.auditLogId, { level: warningLevel, note: warningNote.trim() }));
      setWarningOpen(false);
      setWarningNote('');
      setMessage('Đã gửi cảnh báo tới manager. Cảnh báo không đảo ngược hoặc khóa quyết định gốc.');
      await load();
    } catch { setError('Không thể gửi cảnh báo. Hệ thống đã ngăn gửi trùng cho cùng một quyết định.'); }
    finally { setSaving(false); }
  };

  return <Box sx={{ p: 2 }}>
    <Typography variant="h5" sx={{ fontWeight: 800 }}>Hậu kiểm quyết định vận hành</Typography>
    <Typography color="text.secondary" sx={{ mb: 2 }}>System Admin chỉ xem quyết định của Course Manager và Finance Manager, sau đó đánh dấu đã xem hoặc gửi cảnh báo.</Typography>
    <Alert severity="info" sx={{ mb: 2 }}>Trang này không có thao tác phê duyệt lại, từ chối lại hay sửa dữ liệu nghiệp vụ. Cảnh báo chỉ nhằm nhắc manager và không ảnh hưởng luồng xử lý.</Alert>
    {error && <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>{error}</Alert>}
    {message && <Alert severity="success" onClose={() => setMessage(null)} sx={{ mb: 2 }}>{message}</Alert>}

    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <Stack direction={{ xs: 'column', lg: 'row' }} sx={{ gap: 2 }}>
        <TextField select label="Vai trò ra quyết định" value={draft.decisionRole} onChange={(event) => setDraft({ ...draft, decisionRole: event.target.value as DecisionReviewFilters['decisionRole'] })} sx={{ minWidth: 220 }}>
          <MenuItem value="">Tất cả</MenuItem><MenuItem value="COURSE_MANAGER">Course Manager</MenuItem><MenuItem value="FINANCE_MANAGER">Finance Manager</MenuItem>
        </TextField>
        <TextField select label="Nghiệp vụ" value={draft.domain} onChange={(event) => setDraft({ ...draft, domain: event.target.value as DecisionDomain | '' })} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>{DOMAINS.map((domain) => <MenuItem value={domain} key={domain}>{domain}</MenuItem>)}
        </TextField>
        <TextField select label="Hậu kiểm" value={draft.reviewStatus} onChange={(event) => setDraft({ ...draft, reviewStatus: event.target.value as DecisionReviewStatus | '' })} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>{Object.entries(REVIEW_STATUS).map(([value, label]) => <MenuItem value={value} key={value}>{label}</MenuItem>)}
        </TextField>
        <TextField label="Tên / email manager" value={draft.actor} onChange={(event) => setDraft({ ...draft, actor: event.target.value })} fullWidth />
        <Button variant="outlined" onClick={() => setFilters({ ...draft, page: 0 })}>Lọc</Button>
        <Button onClick={() => { setDraft(initialFilters); setFilters(initialFilters); }}>Xóa lọc</Button>
      </Stack>
    </Paper>

    <TableContainer component={Paper} variant="outlined">
      <Table>
        <TableHead><TableRow><TableCell>Thời điểm</TableCell><TableCell>Vai trò / người xử lý</TableCell><TableCell>Nghiệp vụ</TableCell><TableCell>Hành động</TableCell><TableCell>Đối tượng</TableCell><TableCell>Hậu kiểm</TableCell></TableRow></TableHead>
        <TableBody>
          {loading && items.length === 0 ? <TableRow><TableCell colSpan={6} align="center"><CircularProgress /></TableCell></TableRow> : items.map((item) =>
            <TableRow hover key={item.auditLogId} onClick={() => void openDetail(item.auditLogId)} sx={{ cursor: 'pointer' }}>
              <TableCell>{new Date(item.decisionAt).toLocaleString('vi-VN')}</TableCell>
              <TableCell><b>{item.decisionRole}</b><Typography variant="caption" sx={{ display: 'block' }}>{item.decisionActorName || item.decisionActorEmail}</Typography></TableCell>
              <TableCell>{item.domain}</TableCell><TableCell>{item.action}</TableCell>
              <TableCell>{item.targetType}<Typography variant="caption" sx={{ display: 'block' }}>{item.targetId}</Typography></TableCell>
              <TableCell><Chip size="small" label={REVIEW_STATUS[item.reviewStatus]} color={item.reviewStatus === 'WARNING_SENT' ? 'warning' : item.reviewStatus === 'REVIEWED' ? 'success' : 'default'} /></TableCell>
            </TableRow>)}
          {!loading && items.length === 0 && <TableRow><TableCell colSpan={6} align="center">Chưa có quyết định phù hợp.</TableCell></TableRow>}
        </TableBody>
      </Table>
      <TablePagination component="div" count={total} page={filters.page} rowsPerPage={filters.size}
        onPageChange={(_, page) => setFilters({ ...filters, page })}
        onRowsPerPageChange={(event) => setFilters({ ...filters, page: 0, size: Number(event.target.value) })}
        rowsPerPageOptions={[10, 20, 50]} labelRowsPerPage="Số dòng" />
    </TableContainer>

    <Dialog open={Boolean(detail)} onClose={() => setDetail(null)} fullWidth maxWidth="md">
      {detail && <>
        <DialogTitle>{detail.domain} · {detail.action}</DialogTitle>
        <DialogContent dividers>
          <Stack sx={{ gap: 1, mb: 2 }}>
            <Typography>Người quyết định: <b>{detail.decisionActorName}</b> ({detail.decisionRole})</Typography>
            <Typography>Thời điểm: {new Date(detail.decisionAt).toLocaleString('vi-VN')}</Typography>
            <Typography>Đối tượng: {detail.targetType} / {detail.targetId || '—'}</Typography>
            <Typography>Trạng thái hậu kiểm: <b>{REVIEW_STATUS[detail.reviewStatus]}</b>{detail.warningLevel ? ` · ${detail.warningLevel}` : ''}</Typography>
            {detail.reviewNote && <Alert severity="warning">{detail.reviewNote}</Alert>}
          </Stack>
          <Evidence title="Trước quyết định" value={detail.beforeValue} />
          <Evidence title="Sau quyết định" value={detail.afterValue} />
          <Evidence title="Dữ liệu bổ sung" value={detail.metadata} />
        </DialogContent>
        <DialogActions><Button onClick={() => setDetail(null)}>Đóng</Button><Button onClick={() => void markReviewed()} disabled={saving}>Đánh dấu đã xem</Button><Button variant="contained" color="warning" onClick={() => setWarningOpen(true)} disabled={saving}>Gửi cảnh báo</Button></DialogActions>
      </>}
    </Dialog>

    <Dialog open={warningOpen} onClose={() => setWarningOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>Gửi cảnh báo tới manager</DialogTitle>
      <DialogContent><Stack sx={{ gap: 2, mt: 1 }}>
        <Alert severity="info">Cảnh báo là thông báo nội bộ; quyết định và luồng nghiệp vụ hiện tại vẫn giữ nguyên.</Alert>
        <TextField select label="Mức cảnh báo" value={warningLevel} onChange={(event) => setWarningLevel(event.target.value as DecisionWarningLevel)}><MenuItem value="INFO">Thông tin</MenuItem><MenuItem value="WARNING">Cảnh báo</MenuItem><MenuItem value="HIGH">Cao</MenuItem></TextField>
        <TextField required label="Nội dung" value={warningNote} onChange={(event) => setWarningNote(event.target.value)} multiline minRows={4} slotProps={{ htmlInput: { maxLength: 2000 } }} />
      </Stack></DialogContent>
      <DialogActions><Button onClick={() => setWarningOpen(false)}>Hủy</Button><Button variant="contained" color="warning" onClick={() => void sendWarning()} disabled={saving}>Gửi</Button></DialogActions>
    </Dialog>
  </Box>;
}

function Evidence({ title, value }: { title: string; value?: Record<string, unknown> }) {
  return <Box sx={{ mb: 2 }}><Typography variant="subtitle2" sx={{ fontWeight: 750 }}>{title}</Typography><Box component="pre" sx={{ m: 0, mt: 0.5, p: 1.5, bgcolor: 'grey.100', borderRadius: 1, overflowX: 'auto', fontSize: 12 }}>{JSON.stringify(value ?? {}, null, 2)}</Box></Box>;
}
