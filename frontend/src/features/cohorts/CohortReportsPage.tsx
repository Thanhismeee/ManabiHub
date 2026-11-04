import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, MenuItem, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TablePagination, TextField, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { useLocale } from '../../shared/i18n/locale';
import { getReports, type CohortReport } from './workspaceApi';

function percentage(numerator: number, denominator: number) { return denominator ? `${(100 * numerator / denominator).toFixed(1)}%` : '—'; }
function csvCell(value: unknown) {
  const text = String(value ?? '');
  const safe = /^[\s]*[=+@-]/.test(text) ? `'${text}` : text;
  return `"${safe.replace(/"/g, '""')}"`;
}
export function CohortReportsPage({ admin = false }: { admin?: boolean }) {
  const { t, locale } = useLocale();
  const [reports, setReports] = useState<CohortReport[]>([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(true);
  const [organization, setOrganization] = useState(''); const [branch, setBranch] = useState(''); const [hourlyCost, setHourlyCost] = useState('');
  const [organizations, setOrganizations] = useState<string[]>([]); const [branches, setBranches] = useState<string[]>([]);
  const [page, setPage] = useState(0); const [size, setSize] = useState(25); const [total, setTotal] = useState(0);
  const [from, setFrom] = useState(''); const [to, setTo] = useState(''); const [exporting, setExporting] = useState(false);
  const request = useRef(0);
  const load = useCallback(async () => {
    const revision = ++request.current;
    setLoading(true); setError('');
    try {
      const response = await getReports(admin, { organization: organization || undefined, branch: branch || undefined, from: from || undefined, to: to || undefined, page, size });
      if (revision === request.current) { setReports(response.items); setTotal(response.totalElements); setOrganizations(response.organizations); setBranches(response.branches); }
    } catch { if (revision === request.current) { setReports([]); setError('Không tải được dữ liệu. Kiểm tra khoảng ngày và thử lại.'); } }
    finally { if (revision === request.current) setLoading(false); }
  }, [admin, organization, branch, from, to, page, size]);
  useEffect(() => { void load(); }, [load]);
  const visible = reports;
  const totals = useMemo(() => visible.reduce((sum, r) => ({ students: sum.students + r.activeStudents, risk: sum.risk + r.studentsAtRisk,
    overdue: sum.overdue + r.overdueReviews, minutes: sum.minutes + r.reviewMinutes }), { students: 0, risk: 0, overdue: 0, minutes: 0 }), [visible]);
  const costProvided = hourlyCost.trim() !== '' && Number.isFinite(Number(hourlyCost)) && Number(hourlyCost) >= 0;
  const estimate = costProvided ? totals.minutes / 60 * Number(hourlyCost) : null;
  async function exportCsv() {
    setExporting(true); setError('');
    try {
    const exported: CohortReport[] = [];
    for (let current = 0; ; current++) {
      const response = await getReports(admin, { organization: organization || undefined, branch: branch || undefined, from: from || undefined, to: to || undefined, page: current, size: 100 });
      exported.push(...response.items);
      if (exported.length >= response.totalElements || !response.items.length) break;
      if (current >= 199) throw new Error('Export limit');
    }
    const header = ['Lớp', 'Mã lớp', 'Đơn vị', 'Chi nhánh', 'Giáo viên', 'Ngôn ngữ', 'Học viên đang trong lớp', 'Bài đến hạn cần nộp', 'Bài đã nộp trong số đến hạn',
      'Bài chưa nộp quá hạn', 'Học viên cần hỗ trợ', 'Chờ chữa', 'Chờ chữa quá hạn SLA', 'Lượt đã chữa', 'Lượt chữa đúng SLA', 'Phút chữa do giáo viên nhập', 'Lượt đã dùng', 'Định mức/học viên', 'Điểm trung bình', 'Đơn giá giờ chữa (VND)', 'Chi phí chữa ước tính (VND)', 'Thời điểm xuất'];
    const at = new Date().toISOString();
    const rows = exported.map(r => [r.name, r.code, r.organizationName, r.branchName, r.teacherName, r.learningLanguage, r.activeStudents, r.expectedDueSubmissions,
      r.submittedDueAssignments, r.overdueAssignments, r.studentsAtRisk, r.pendingReviews, r.overdueReviews, r.reviewedAttempts, r.reviewedWithinSla, r.reviewMinutes,
      r.feedbackUsed, r.feedbackQuotaPerStudent, r.averageScore?.toFixed(2), costProvided ? Number(hourlyCost) : '', costProvided ? Math.round(r.reviewMinutes / 60 * Number(hourlyCost)) : '', at]);
    const blob = new Blob(['\uFEFF', [header, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `bao-cao-hoc-vu-${at.slice(0, 10)}.csv`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setError('Không xuất được báo cáo. Thu hẹp khoảng ngày hoặc thử lại.'); }
    finally { setExporting(false); }
  }
  return <Box sx={{ p: { xs: 2, md: 3 } }}>
    <Typography variant="h4" sx={{ mb: 2, fontWeight: 700 }}>{t('Báo cáo học vụ')}</Typography>
    {error && <Alert severity="error" sx={{ mb: 2 }}>{t(error)}</Alert>}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3 }}>
      <TextField select label={t('Đơn vị')} size="small" sx={{ minWidth: 180 }} value={organization} onChange={e => { setOrganization(e.target.value); setBranch(''); setPage(0); }}>
        <MenuItem value="">{t('Tất cả')}</MenuItem>{organizations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
      <TextField select label={t('Cơ sở/chi nhánh')} size="small" sx={{ minWidth: 180 }} value={branch} onChange={e => { setBranch(e.target.value); setPage(0); }}>
        <MenuItem value="">{t('Tất cả')}</MenuItem>{branches.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
      <TextField type="date" label={t('Từ ngày')} size="small" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} slotProps={{ inputLabel: { shrink: true } }} />
      <TextField type="date" label={t('Đến ngày')} size="small" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} slotProps={{ inputLabel: { shrink: true } }} />
      <Button disabled={loading || exporting} onClick={() => void load()}>{t('Làm mới')}</Button><Button disabled={loading || exporting || !total} onClick={() => void exportCsv()}>{t(exporting ? 'Đang xuất...' : 'Xuất CSV')}</Button>
    </Stack>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3 }}>
      {[['Số lớp', visible.length], ['Số lượt học viên', totals.students], ['Lượt cần hỗ trợ', totals.risk], ['Bài chờ chữa quá hạn', totals.overdue]].map(([label, value]) =>
        <Card variant="outlined" key={String(label)} sx={{ flex: 1 }}><CardContent><Typography color="text.secondary">{t(String(label))}</Typography><Typography variant="h4">{value}</Typography></CardContent></Card>)}
    </Stack>
    <Alert severity="info" sx={{ mb: 3 }}>{t('Tỷ lệ nộp tính trên bài đã đến hạn của học viên đang trong lớp; một học viên có thể thuộc nhiều lớp.')} {t('Đóng nhận bài vẫn giữ các bài chưa nộp trong báo cáo. Thẻ tổng hợp đang tính trên trang hiện tại; CSV gồm toàn bộ lớp theo bộ lọc.')} {t('Khoảng ngày lọc các lớp diễn ra trong kỳ; số liệu là hiện trạng, không phải kết quả chỉ phát sinh trong kỳ.')}</Alert>
    <Card variant="outlined" sx={{ mb: 3 }}><CardContent><Stack spacing={2}>
      <TextField type="number" size="small" label={t('Đơn giá giờ chữa (VND)')} value={hourlyCost} onChange={e => setHourlyCost(e.target.value)} sx={{ maxWidth: 330 }} slotProps={{ htmlInput: { min: 0 } }} />
      <Typography>{t('Giờ chữa thực tế')}: {(totals.minutes / 60).toFixed(2)} · {t('Chi phí chữa ước tính')}: {estimate === null ? '—' : new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : locale === 'ko' ? 'ko-KR' : 'zh-CN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(estimate)}</Typography>
      <Typography variant="body2" color="text.secondary">{t('Ước tính từ phút giáo viên nhập và đơn giá bạn chọn; chưa gồm hạ tầng, thuế hay chi phí khác.')}</Typography>
      <Typography variant="body2" color="text.secondary">{t('Thời gian chữa bao gồm cả lịch sử của học viên đã rời lớp. Đây là chi phí ước tính, chưa phải bảng lương được phê duyệt.')}</Typography>
    </Stack></CardContent></Card>
    {loading ? <Typography>{t('Đang tải...')}</Typography> : <TableContainer><Table size="small"><TableHead><TableRow>
      {['Lớp', 'Giáo viên', 'Học viên', 'Tỷ lệ nộp bài', 'Học viên cần hỗ trợ', 'Chờ chữa', 'Bài chờ chữa quá hạn', 'Tỷ lệ chữa đúng hạn', 'Giờ chữa thực tế'].map(label => <TableCell key={label}>{t(label)}</TableCell>)}
    </TableRow></TableHead><TableBody>
      {visible.map(r => <TableRow key={r.cohortId}>
        <TableCell>{admin ? r.name : <Link to={`/teacher/cohorts/${r.cohortId}/workspace`}>{r.name}</Link>}<Typography variant="caption" sx={{ display: 'block' }}>{r.code} · {r.branchName ?? r.organizationName}</Typography></TableCell>
        <TableCell>{r.teacherName}</TableCell><TableCell>{r.activeStudents}</TableCell><TableCell>{percentage(r.submittedDueAssignments, r.expectedDueSubmissions)}</TableCell>
        <TableCell>{r.studentsAtRisk}</TableCell><TableCell>{r.pendingReviews}</TableCell><TableCell>{r.overdueReviews}</TableCell>
        <TableCell>{percentage(r.reviewedWithinSla, r.reviewedAttempts)}</TableCell><TableCell>{(r.reviewMinutes / 60).toFixed(2)}</TableCell>
      </TableRow>)}
      {!visible.length && <TableRow><TableCell colSpan={9}>{t('Chưa có dữ liệu')}</TableCell></TableRow>}
    </TableBody></Table></TableContainer>}
    <TablePagination component="div" count={total} page={page} rowsPerPage={size} rowsPerPageOptions={[25, 50, 100]}
      onPageChange={(_, next) => setPage(next)} onRowsPerPageChange={e => { setSize(Number(e.target.value)); setPage(0); }} labelRowsPerPage={t('Số lớp mỗi trang')} />
  </Box>;
}
