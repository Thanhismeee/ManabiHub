import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { isAxiosError } from 'axios';
import { Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField, Typography } from '@mui/material';
import { Link, useParams } from 'react-router-dom';
import { useLocale } from '../../shared/i18n/locale';
import { AudioRecorder, type Recording } from './AudioRecorder';
import { PrivateAudioPlayer } from './PrivateAudioPlayer';
import { getStudentClasses, getWorkspace, submitAssignment, type Assignment, type Workspace, type ClassSummary } from './workspaceApi';
import { getAuthSession, subscribeToAuthSessionChanges } from '../../shared/auth/authSession';
import { loadPrivateDraft, savePrivateDraft, deletePrivateDraft } from '../../shared/storage/privateDrafts';

export function StudentCohortsPage() {
  const { cohortId } = useParams(); const { t, date, locale } = useLocale();
  const statusLabel = (status: Workspace['status']) => t({ PLANNED: 'Sắp khai giảng', ACTIVE: 'Đang học', COMPLETED: 'Đã kết thúc', ARCHIVED: 'Đã lưu trữ' }[status]);
  const calendarDate = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(locale === 'vi' ? 'vi-VN' : locale === 'ko' ? 'ko-KR' : 'zh-CN');
  const [items, setItems] = useState<ClassSummary[]>([]); const [workspace, setWorkspace] = useState<Workspace>();
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [selected, setSelected] = useState<Assignment>(); const [content, setContent] = useState('');
  const [recording, setRecording] = useState<Recording>(); const [saving, setSaving] = useState(false);
  const owner = useSyncExternalStore(subscribeToAuthSessionChanges, () => getAuthSession('public')?.subject ?? null, () => null);
  const [draftLoaded, setDraftLoaded] = useState(false); const [draftError, setDraftError] = useState('');
  const [audioBusy, setAudioBusy] = useState(false); const [draftSaved, setDraftSaved] = useState(true);
  const editorRequest = useRef(0);
  useEffect(() => {
    if (!selected || !workspace || !owner || !draftLoaded || saving) return;
    setDraftSaved(false);
    const timer = setTimeout(() => {
      if (getAuthSession('public')?.subject !== owner) return;
      void savePrivateDraft(owner, workspace.cohortId, selected.id, { content, recording })
        .then(() => { setDraftSaved(true); setDraftError(''); }).catch(() => setDraftError('Chưa lưu được bản nháp trên thiết bị. Giữ trang này mở hoặc sao chép bài trước khi rời trang.'));
    }, 400);
    return () => clearTimeout(timer);
  }, [owner, workspace?.cohortId, selected?.id, content, recording, draftLoaded, saving]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (selected && (!draftSaved || audioBusy)) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [selected, draftSaved, audioBusy]);
  async function openAssignment(assignment: Assignment) {
    const request = ++editorRequest.current;
    setSelected(assignment); setContent(''); setRecording(undefined); setDraftLoaded(false); setDraftError(''); setError('');
    try {
      const draft = owner && workspace ? await loadPrivateDraft(owner, workspace.cohortId, assignment.id) : undefined;
      if (request === editorRequest.current) { setContent(draft?.content ?? ''); setRecording(draft?.recording); }
    } catch { if (request === editorRequest.current) setDraftError('Không khôi phục được bản nháp trên thiết bị.'); }
    finally { if (request === editorRequest.current) { setDraftLoaded(true); setDraftSaved(true); } }
  }
  async function closeEditor() {
    if (saving || audioBusy) return;
    if (selected && owner && workspace && draftLoaded) {
      try { await savePrivateDraft(owner, workspace.cohortId, selected.id, { content, recording }); }
      catch { if (!window.confirm('Bản nháp chưa lưu được. Đóng sẽ có thể mất bài đang soạn. Bạn vẫn muốn đóng?')) return; }
    }
    ++editorRequest.current; setSelected(undefined); setDraftLoaded(false);
  }
  useEffect(() => {
    let alive = true; setLoading(true); setError(''); setWorkspace(undefined); setSelected(undefined);
    ++editorRequest.current; setAudioBusy(false); setDraftLoaded(false);
    const load = cohortId ? getWorkspace(cohortId, false).then(data => { if (alive) setWorkspace(data); })
      : getStudentClasses().then(data => { if (alive) setItems(data); });
    load.catch(() => { if (alive) setError('Không tải được dữ liệu.'); }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [cohortId, owner]);
  async function submit() {
    if (!workspace || !selected) return;
    setSaving(true); setError('');
    try {
      const updated = await submitAssignment(workspace.cohortId, selected.id, content, recording);
      if (owner) await deletePrivateDraft(owner, workspace.cohortId, selected.id).catch(() => undefined);
      setWorkspace(updated); setSelected(undefined); setDraftLoaded(false); setContent(''); setRecording(undefined);
    }
    catch (err) { setError(isAxiosError(err) ? err.response?.data?.message ?? 'Thao tác chưa thành công.' : 'Thao tác chưa thành công.'); }
    finally { setSaving(false); }
  }
  const history = workspace?.submissions.filter(item => item.assignmentId === selected?.id).sort((a, b) => b.attempt - a.attempt) ?? [];
  const latest = history[0];
  const canSubmit = workspace?.submissionAllowed && selected && !selected.closed && new Date(selected.acceptUntil).getTime() >= Date.now()
    && (!latest || Boolean(latest.reviewedAt && latest.revisionRequested)) && history.length < selected.maxAttempts
    && ((workspace?.feedbackRemaining ?? 0) > 0 || Boolean(latest?.revisionRequested));
  return <Box sx={{ p: { xs: 2, md: 3 } }}>
    <Typography variant="h4" sx={{ mb: 2, fontWeight: 700 }}>{workspace?.name ?? t('Lớp học & bài tập')}</Typography>
    {loading && <Typography>{t('Đang tải...')}</Typography>}
    {error && <Alert severity="error" sx={{ mb: 2 }}>{t(error)}</Alert>}
    {!cohortId && !loading && <Stack spacing={2}>
      {!items.length && <Alert severity="info">{t('Chưa có lớp học')}. {t('Liên hệ trung tâm để được thêm vào lớp.')}</Alert>}
      {items.map(item => <Card key={item.cohortId} variant="outlined"><CardContent>
        <Typography variant="h6">{item.name}</Typography><Typography color="text.secondary">{item.code}</Typography>
        <Chip size="small" label={statusLabel(item.status)} sx={{ my: 1 }} />
        <Typography variant="body2">{t('Ngày bắt đầu')}: {calendarDate(item.startDate)} · {t('Ngày kết thúc')}: {calendarDate(item.endDate)}</Typography>
        <Typography sx={{ my: 1 }}>{t('Bài tập')}: {item.assignmentCount} · {t('Lượt chữa còn lại')}: {item.feedbackRemaining}</Typography>
        <Button component={Link} to={`/student/cohorts/${item.cohortId}`}>{t('Mở lớp học')}</Button>
      </CardContent></Card>)}
    </Stack>}
    {workspace && <>
      <Stack spacing={1} sx={{ mb: 2 }}><Chip size="small" label={statusLabel(workspace.status)} sx={{ alignSelf: 'flex-start' }} />
        <Typography variant="body2">{t('Ngày bắt đầu')}: {calendarDate(workspace.startDate)} · {t('Ngày kết thúc')}: {calendarDate(workspace.endDate)}</Typography></Stack>
      {!workspace.submissionAllowed && <Alert severity="info" sx={{ mb: 2 }}>{t(workspace.submissionBlockReason ?? 'Lớp chưa nhận bài.')}</Alert>}
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}><Button component={Link} to="/student/cohorts">{t('Quay lại')}</Button>
        <Button component={Link} to={`/student/courses/${workspace.courseId}/learn`}>{t('Mở khóa học')}</Button></Stack>
      <Alert severity="info" sx={{ mb: 3 }}>{t('Lượt chữa còn lại')}: {workspace.feedbackRemaining} / {workspace.feedbackQuotaPerStudent} · {t('Giờ cam kết phản hồi')}: {workspace.reviewSlaHours}</Alert>
      {(workspace.members[0]?.feedbackReserved ?? 0) > 0 && <Typography sx={{ mb: 2 }}>{t('Lượt đã giữ cho bài cần sửa')}: {workspace.members[0]?.feedbackReserved}</Typography>}
      <Stack spacing={2}>
        {!workspace.assignments.length && <Typography>{t('Chưa có bài tập.')}</Typography>}
        {workspace.assignments.map(assignment => {
          const submission = workspace.submissions.filter(s => s.assignmentId === assignment.id).sort((a, b) => b.attempt - a.attempt)[0];
          const status = assignment.closed ? 'Đã đóng' : !submission ? (new Date(assignment.dueAt).getTime() < Date.now() ? 'Quá hạn' : 'Chưa nộp')
            : !submission.reviewedAt ? 'Chờ chữa' : submission.revisionRequested ? 'Cần nộp lại' : 'Đã chữa';
          return <Card key={assignment.id} variant="outlined"><CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1 }}><Typography variant="h6">{assignment.title}</Typography><Chip label={t(status)} size="small" /></Stack>
            <Typography>{t(assignment.type === 'WRITING' ? 'Bài viết' : 'Bài nói')} · {t('Hạn nộp')}: {date(assignment.dueAt)}</Typography>
            <Typography variant="body2" color="text.secondary">{t('Nhận bài đến')}: {date(assignment.acceptUntil)}</Typography>
            <Button sx={{ mt: 1 }} onClick={() => void openAssignment(assignment)}>{t('Xem bài / nộp bài')}</Button>
          </CardContent></Card>;
        })}
      </Stack>
    </>}
    <Dialog open={Boolean(selected)} onClose={() => void closeEditor()} fullWidth maxWidth="md">
      <DialogTitle>{selected?.title}</DialogTitle><DialogContent>
        {selected && <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{t(error)}</Alert>}
          {draftError && <Alert severity="warning">{t(draftError)}</Alert>}
          {!workspace?.submissionAllowed && <Alert severity="info">{t(workspace?.submissionBlockReason ?? 'Lớp chưa nhận bài.')}</Alert>}
          {canSubmit && <Typography variant="caption">{t(draftLoaded ? draftSaved ? 'Bản nháp được giữ trên thiết bị này trong 7 ngày; xóa khi nộp thành công hoặc đăng xuất.' : 'Đang lưu bản nháp...' : 'Đang tải bản nháp...')}</Typography>}
          <Typography sx={{ whiteSpace: 'pre-wrap' }}>{selected.instructions}</Typography>
          <Typography variant="body2">{t('Số lần nộp tối đa')}: {selected.maxAttempts} · {t('Nhận bài đến')}: {date(selected.acceptUntil)}</Typography>
          {canSubmit && <>
            <TextField multiline minRows={selected.type === 'WRITING' ? 6 : 2} fullWidth label={t(selected.type === 'WRITING' ? 'Bài làm của bạn' : 'Ghi chú (không bắt buộc)')}
              value={content} disabled={saving || !draftLoaded} onChange={event => { setDraftSaved(false); setContent(event.target.value); }} slotProps={{ htmlInput: { maxLength: selected.type === 'WRITING' ? 10000 : 2000 } }} />
            {selected.type === 'SPEAKING' && <AudioRecorder key={selected.id} maxSeconds={selected.maxAudioSeconds} disabled={saving || !draftLoaded} value={recording} onChange={setRecording} onBusyChange={setAudioBusy} />}
          </>}
          <Divider /><Typography variant="h6">{t('Lịch sử bài nộp')}</Typography>
          {history.map(submission => <Box key={submission.id} sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2 }}>
            <Typography sx={{ fontWeight: 700 }}>{t('Lần nộp')} {submission.attempt} · {date(submission.submittedAt)}</Typography>
            {submission.content && <Typography sx={{ whiteSpace: 'pre-wrap', my: 1 }}>{submission.content}</Typography>}
            {submission.hasAudio && <PrivateAudioPlayer submissionId={submission.id} teacher={false} />}
            {submission.reviewedAt ? <><Typography sx={{ mt: 1 }}>{t('Điểm')}: {submission.score}/100 · {t(submission.revisionRequested ? 'Cần nộp lại' : 'Đã chữa')}</Typography>
              <Typography sx={{ fontWeight: 700 }}>{t('Phản hồi của giáo viên')}</Typography><Typography sx={{ whiteSpace: 'pre-wrap' }}>{submission.feedback}</Typography></>
              : <Typography sx={{ mt: 1 }}>{t('Chờ chữa')} · {date(submission.reviewDueAt)}</Typography>}
          </Box>)}
        </Stack>}
      </DialogContent><DialogActions><Button disabled={saving || audioBusy} onClick={() => void closeEditor()}>{t('Đóng')}</Button>
        {canSubmit && <Button variant="contained" disabled={saving || audioBusy || !draftLoaded || (selected?.type === 'WRITING' ? !content.trim() : !recording)} onClick={() => void submit()}>{t(saving ? 'Đang lưu...' : 'Nộp bài')}</Button>}
      </DialogActions>
    </Dialog>
  </Box>;
}
