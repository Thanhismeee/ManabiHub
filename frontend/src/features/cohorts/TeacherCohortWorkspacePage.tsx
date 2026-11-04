import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { Link, useParams } from 'react-router-dom';
import { useLocale } from '../../shared/i18n/locale';
import { PrivateAudioPlayer } from './PrivateAudioPlayer';
import { closeAssignment, configurePackage, createAssignment, getWorkspace, remindMember, reviewSubmission, type AssignmentType, type Submission, type Workspace } from './workspaceApi';

function localDate(offset: number) {
  const date = new Date(); date.setDate(date.getDate() + offset); date.setHours(20, 0, 0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
export function TeacherCohortWorkspacePage() {
  const { cohortId = '' } = useParams(); const { t, date } = useLocale();
  const [workspace, setWorkspace] = useState<Workspace>(); const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ error: boolean; text: string }>();
  const [createOpen, setCreateOpen] = useState(false); const [selected, setSelected] = useState<Submission>();
  const [quota, setQuota] = useState(12); const [sla, setSla] = useState(48);
  const [filter, setFilter] = useState('pending');
  const [form, setForm] = useState({ title: '', instructions: '', type: 'WRITING' as AssignmentType, dueAt: localDate(7), acceptUntil: localDate(14), maxAttempts: 3, maxAudioSeconds: 180 });
  const [review, setReview] = useState({ feedback: '', score: '', revisionRequested: false, reviewMinutes: '' });
  useEffect(() => {
    let alive = true; setLoading(true); setWorkspace(undefined); setMessage(undefined);
    getWorkspace(cohortId, true).then(value => { if (alive) { setWorkspace(value); setQuota(value.feedbackQuotaPerStudent); setSla(value.reviewSlaHours); } })
      .catch(() => { if (alive) setMessage({ error: true, text: 'Không tải được dữ liệu.' }); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [cohortId]);
  async function mutate(action: () => Promise<Workspace>, after?: () => void) {
    setSaving(true); setMessage(undefined);
    try { const value = await action(); setWorkspace(value); setQuota(value.feedbackQuotaPerStudent); setSla(value.reviewSlaHours); setMessage({ error: false, text: 'Đã lưu.' }); after?.(); }
    catch (err) { setMessage({ error: true, text: isAxiosError(err) ? err.response?.data?.message ?? 'Thao tác chưa thành công.' : 'Thao tác chưa thành công.' }); }
    finally { setSaving(false); }
  }
  function openReview(submission: Submission) {
    setSelected(submission); setReview({ feedback: submission.feedback ?? '', score: submission.score?.toString() ?? '', revisionRequested: submission.revisionRequested, reviewMinutes: submission.reviewMinutes?.toString() ?? '' });
  }
  const atRisk = workspace?.members.filter(m => m.overdueAssignments + m.awaitingRevision > 0) ?? [];
  const pending = workspace?.submissions.filter(s => !s.reviewedAt) ?? [];
  const overdue = pending.filter(s => new Date(s.reviewDueAt).getTime() < Date.now());
  return <Box sx={{ p: { xs: 2, md: 3 } }}>
    <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}><Button component={Link} to="/teacher/cohorts">{t('Quay lại')}</Button>
      <Button component={Link} to="/teacher/cohort-reports">{t('Báo cáo học vụ')}</Button></Stack>
    <Typography variant="h4" sx={{ mb: 2, fontWeight: 700 }}>{workspace?.name ?? t('Lớp học SOFL')}</Typography>
    {message && <Alert severity={message.error ? 'error' : 'success'} sx={{ mb: 2 }}>{t(message.text)}</Alert>}
    {loading && <Typography>{t('Đang tải...')}</Typography>}
    {workspace && <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3 }}>
        {[['Chờ chữa', pending.length], ['Bài chờ chữa quá hạn', overdue.length], ['Học viên cần hỗ trợ', atRisk.length], ['Lượt đã dùng', workspace.feedbackUsed]].map(([label, value]) =>
          <Card key={String(label)} variant="outlined" sx={{ flex: 1 }}><CardContent><Typography color="text.secondary">{t(String(label))}</Typography><Typography variant="h4">{value}</Typography></CardContent></Card>)}
      </Stack>
      <Tabs value={tab} onChange={(_, value: number) => setTab(value)} variant="scrollable" scrollButtons="auto" sx={{ mb: 3 }}>
        {['Bài tập', 'Bài nộp', 'Học viên cần hỗ trợ', 'Gói chữa bài'].map(label => <Tab key={label} label={t(label)} />)}
      </Tabs>
      {tab === 0 && <Stack spacing={2}>
        <Button variant="contained" sx={{ alignSelf: 'flex-start' }} onClick={() => setCreateOpen(true)}>{t('Giao bài mới')}</Button>
        {!workspace.assignments.length && <Typography>{t('Chưa có bài tập.')}</Typography>}
        {workspace.assignments.map(task => <Card key={task.id} variant="outlined"><CardContent>
          <Stack direction="row" sx={{ gap: 1, justifyContent: 'space-between' }}><Typography variant="h6">{task.title}</Typography><Chip size="small" label={t(task.closed ? 'Đã đóng' : task.type === 'WRITING' ? 'Bài viết' : 'Bài nói')} /></Stack>
          <Typography sx={{ whiteSpace: 'pre-wrap', my: 1 }}>{task.instructions}</Typography>
          <Typography variant="body2">{t('Hạn nộp')}: {date(task.dueAt)} · {t('Nhận bài đến')}: {date(task.acceptUntil)}</Typography>
          <Typography variant="body2">{t('Số lần nộp tối đa')}: {task.maxAttempts}{task.type === 'SPEAKING' ? ` · ${task.maxAudioSeconds}s` : ''}</Typography>
          {!task.closed && <Button color="warning" disabled={saving} onClick={() => { if (window.confirm(t('Đóng nhận bài? Lịch sử và bài chờ chữa vẫn được giữ.'))) void mutate(() => closeAssignment(cohortId, task.id)); }}>{t('Đóng')}</Button>}
        </CardContent></Card>)}
      </Stack>}
      {tab === 1 && <Stack spacing={2}>
        <TextField select size="small" value={filter} onChange={event => setFilter(event.target.value)} sx={{ maxWidth: 250 }}>
          <MenuItem value="pending">{t('Chờ chữa')}</MenuItem><MenuItem value="reviewed">{t('Đã chữa')}</MenuItem><MenuItem value="all">{t('Tất cả')}</MenuItem>
        </TextField>
        {workspace.submissions.filter(s => filter === 'all' || (filter === 'pending' ? !s.reviewedAt : Boolean(s.reviewedAt))).map(s => <Card key={s.id} variant="outlined"><CardContent>
          <Typography variant="h6">{s.studentName} · {workspace.assignments.find(a => a.id === s.assignmentId)?.title}</Typography>
          <Typography variant="body2">{t('Lần nộp')} {s.attempt} · {date(s.submittedAt)}</Typography>
          <Typography variant="body2" color={!s.reviewedAt && new Date(s.reviewDueAt).getTime() < Date.now() ? 'error.main' : 'text.secondary'}>{t('Giờ cam kết phản hồi')}: {date(s.reviewDueAt)}</Typography>
          <Button onClick={() => openReview(s)}>{t(s.reviewedAt ? 'Đã chữa' : 'Chữa bài')}{s.score !== null ? ` · ${s.score}/100` : ''}</Button>
        </CardContent></Card>)}
        {!workspace.submissions.length && <Typography>{t('Không có bài nộp.')}</Typography>}
      </Stack>}
      {tab === 2 && <Stack spacing={2}>
        <Typography color="text.secondary">{t('Nhắc tối đa một lần mỗi 24 giờ.')}</Typography>
        {!atRisk.length && <Alert severity="success">{t('Không có học viên cần hỗ trợ.')}</Alert>}
        {atRisk.map(m => <Card key={m.memberId} variant="outlined"><CardContent>
          <Typography variant="h6">{m.fullName}</Typography><Typography color="text.secondary">{m.email}</Typography>
          <Typography sx={{ my: 1 }}>{t('Bài quá hạn')}: {m.overdueAssignments} · {t('Cần nộp lại')}: {m.awaitingRevision} · {t('Lượt chữa còn lại')}: {m.feedbackRemaining}</Typography>
          <Typography variant="body2">{t('Lượt đã giữ cho bài cần sửa')}: {m.feedbackReserved}</Typography>
          <Button disabled={saving || Boolean(m.lastReminderAt && new Date(m.lastReminderAt).getTime() + 86400000 > Date.now())} onClick={() => void mutate(() => remindMember(cohortId, m.memberId))}>{t('Nhắc học viên')}</Button>
        </CardContent></Card>)}
      </Stack>}
      {tab === 3 && <Stack spacing={2} sx={{ maxWidth: 620 }}>
        <Alert severity="info">{t('Mỗi lần nộp sử dụng một lượt. Hạn chữa được tính khi nộp; đổi cấu hình áp dụng cho bài nộp mới.')}</Alert>
        <TextField type="number" label={t('Lượt chữa / học viên / lớp')} value={quota} onChange={event => setQuota(Number(event.target.value))} slotProps={{ htmlInput: { min: 1, max: 200 } }} />
        <TextField type="number" label={t('Giờ cam kết phản hồi')} value={sla} onChange={event => setSla(Number(event.target.value))} slotProps={{ htmlInput: { min: 1, max: 168 } }} />
        <Button variant="contained" disabled={saving || quota < 1 || quota > 200 || sla < 1 || sla > 168} onClick={() => void mutate(() => configurePackage(cohortId, quota, sla))}>{t('Lưu cấu hình')}</Button>
      </Stack>}
    </>}
    <Dialog open={createOpen} onClose={() => !saving && setCreateOpen(false)} fullWidth maxWidth="sm"><DialogTitle>{t('Giao bài mới')}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {message?.error && <Alert severity="error">{t(message.text)}</Alert>}
        <TextField label={t('Tên bài tập')} value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} slotProps={{ htmlInput: { maxLength: 160 } }} />
        <TextField multiline minRows={5} label={t('Hướng dẫn / đề bài')} value={form.instructions} onChange={event => setForm({ ...form, instructions: event.target.value })} slotProps={{ htmlInput: { maxLength: 10000 } }} />
        <TextField select label={t('Loại bài')} value={form.type} onChange={event => setForm({ ...form, type: event.target.value as AssignmentType })}>
          <MenuItem value="WRITING">{t('Bài viết')}</MenuItem><MenuItem value="SPEAKING">{t('Bài nói')}</MenuItem></TextField>
        <TextField type="datetime-local" label={t('Hạn nộp')} value={form.dueAt} onChange={event => setForm({ ...form, dueAt: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField type="datetime-local" label={t('Nhận bài đến')} value={form.acceptUntil} onChange={event => setForm({ ...form, acceptUntil: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField type="number" label={t('Số lần nộp tối đa')} value={form.maxAttempts} onChange={event => setForm({ ...form, maxAttempts: Number(event.target.value) })} slotProps={{ htmlInput: { min: 1, max: 10 } }} />
        {form.type === 'SPEAKING' && <TextField type="number" label={t('Giây tối đa')} value={form.maxAudioSeconds} onChange={event => setForm({ ...form, maxAudioSeconds: Number(event.target.value) })} slotProps={{ htmlInput: { min: 10, max: 600 } }} />}
      </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => setCreateOpen(false)}>{t('Đóng')}</Button>
        <Button variant="contained" disabled={saving || !form.title.trim() || !form.instructions.trim() || !form.dueAt || !form.acceptUntil || form.maxAttempts < 1 || form.maxAttempts > 10 || form.maxAudioSeconds < 10 || form.maxAudioSeconds > 600}
          onClick={() => void mutate(() => createAssignment(cohortId, { ...form, dueAt: new Date(form.dueAt).toISOString(), acceptUntil: new Date(form.acceptUntil).toISOString() }), () => { setCreateOpen(false); setForm({ ...form, title: '', instructions: '' }); })}>{t('Giao bài mới')}</Button>
      </DialogActions></Dialog>
    <Dialog open={Boolean(selected)} onClose={() => !saving && setSelected(undefined)} fullWidth maxWidth="md"><DialogTitle>{selected?.studentName} · {t('Chữa bài')}</DialogTitle>
      <DialogContent>{selected && <Stack spacing={2} sx={{ pt: 1 }}>
        {message?.error && <Alert severity="error">{t(message.text)}</Alert>}
        <Typography sx={{ whiteSpace: 'pre-wrap' }}>{workspace?.assignments.find(a => a.id === selected.assignmentId)?.instructions}</Typography>
        {selected.content && <Box sx={{ p: 2, bgcolor: 'action.hover', whiteSpace: 'pre-wrap' }}>{selected.content}</Box>}
        {selected.hasAudio && <PrivateAudioPlayer key={selected.id} submissionId={selected.id} teacher />}
        <TextField multiline minRows={5} label={t('Phản hồi và hướng dẫn sửa')} value={review.feedback} disabled={Boolean(selected.reviewedAt) || saving} onChange={event => setReview({ ...review, feedback: event.target.value })} slotProps={{ htmlInput: { maxLength: 10000 } }} />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField type="number" label={`${t('Điểm')} / 100`} value={review.score} disabled={Boolean(selected.reviewedAt) || saving} onChange={event => setReview({ ...review, score: event.target.value })} slotProps={{ htmlInput: { min: 0, max: 100 } }} />
          <TextField type="number" label={t('Phút thực tế đã dùng')} value={review.reviewMinutes} disabled={Boolean(selected.reviewedAt) || saving} onChange={event => setReview({ ...review, reviewMinutes: event.target.value })} slotProps={{ htmlInput: { min: 1, max: 180 } }} />
        </Stack>
        <FormControlLabel control={<Checkbox checked={review.revisionRequested} disabled={Boolean(selected.reviewedAt) || saving} onChange={event => setReview({ ...review, revisionRequested: event.target.checked })} />} label={t('Yêu cầu học viên nộp lại')} />
      </Stack>}</DialogContent><DialogActions><Button disabled={saving} onClick={() => setSelected(undefined)}>{t('Đóng')}</Button>
        {!selected?.reviewedAt && <Button variant="contained" disabled={saving || !review.feedback.trim() || review.score === '' || Number(review.score) < 0 || Number(review.score) > 100 || !review.reviewMinutes || Number(review.reviewMinutes) < 1 || Number(review.reviewMinutes) > 180}
          onClick={() => { if (selected) void mutate(() => reviewSubmission(cohortId, selected.id, { ...review, score: Number(review.score), reviewMinutes: Number(review.reviewMinutes) }), () => setSelected(undefined)); }}>{t('Gửi phản hồi')}</Button>}
      </DialogActions></Dialog>
  </Box>;
}
