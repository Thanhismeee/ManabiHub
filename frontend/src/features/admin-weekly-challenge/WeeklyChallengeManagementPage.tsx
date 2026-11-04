import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, CircularProgress, Dialog,
  DialogActions, DialogContent, DialogTitle, Divider, Grid, IconButton, MenuItem,
  Pagination, Paper, Stack, TextField, Typography,
} from '@mui/material';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import PublicRoundedIcon from '@mui/icons-material/PublicRounded';
import SportsEsportsOutlinedIcon from '@mui/icons-material/SportsEsportsOutlined';
import LeaderboardOutlinedIcon from '@mui/icons-material/LeaderboardOutlined';
import {
  weeklyChallengeAdminService, type ChallengePair, type ManagedWeeklyChallenge,
  type WeeklyChallengePayload,
} from './weeklyChallengeAdminService';
import { mondayOfCurrentWeek, mondayOfWeek } from './weeklyChallengeDate';
import { WeeklyChallengeLeaderboardDialog } from '../../shared/components/WeeklyChallengeLeaderboardDialog';
import type { WeeklyChallengeLeaderboard } from '../../shared/types/weeklyChallengeLeaderboard';

const PAGE_SIZE = 6;

const emptyPayload = (): WeeklyChallengePayload => ({
  weekStart: mondayOfCurrentWeek(),
  title: 'Manabi Match · Thử thách tuần',
  description: 'Ghép thuật ngữ với cách đọc và ý nghĩa. Chọn sai sẽ bị cộng thời gian.',
  jlptLevel: 'N5', dailyRankedLimit: 3, wrongPenaltySeconds: 2,
  dailyAttendanceReward: 1000, firstPrize: 30000, secondPrize: 20000, thirdPrize: 10000,
  pairs: Array.from({ length: 6 }, () => ({ prompt: '', answer: '' })),
});

function toPayload(item: ManagedWeeklyChallenge): WeeklyChallengePayload {
  return { weekStart: item.weekStart, title: item.title, description: item.description,
    jlptLevel: item.jlptLevel, dailyRankedLimit: item.dailyRankedLimit,
    wrongPenaltySeconds: item.wrongPenaltySeconds, dailyAttendanceReward: item.dailyAttendanceReward,
    firstPrize: item.firstPrize, secondPrize: item.secondPrize, thirdPrize: item.thirdPrize,
    pairs: item.pairs.map(({ prompt, answer }) => ({ prompt, answer })) };
}

function gameplayValidationMessage(form: WeeklyChallengePayload): string | null {
  if (form.dailyRankedLimit < 1 || form.dailyRankedLimit > 10) return 'Số lượt xếp hạng mỗi ngày phải từ 1 đến 10.';
  if (form.wrongPenaltySeconds < 0 || form.wrongPenaltySeconds > 30) return 'Thời gian phạt phải từ 0 đến 30 giây.';
  if (form.dailyAttendanceReward < 0 || form.dailyAttendanceReward > 10000) return 'Thưởng điểm danh phải từ 0 đến 10.000 đồng.';
  if ([form.firstPrize, form.secondPrize, form.thirdPrize].some((prize) => prize < 0 || prize > 500000)) return 'Mỗi giải phải từ 0 đến 500.000 đồng.';
  if (form.firstPrize < form.secondPrize || form.secondPrize < form.thirdPrize) return 'Mức thưởng phải giảm dần theo hạng 1, 2, 3.';
  return null;
}

export function WeeklyChallengeManagementPage() {
  const [items, setItems] = useState<ManagedWeeklyChallenge[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<WeeklyChallengePayload>(emptyPayload);
  const [page, setPage] = useState(1);
  const [leaderboardOpen, setLeaderboardOpen] = useState(false);
  const [leaderboardLoading, setLeaderboardLoading] = useState(false);
  const [leaderboardError, setLeaderboardError] = useState<string | null>(null);
  const [leaderboard, setLeaderboard] = useState<WeeklyChallengeLeaderboard | null>(null);
  const [leaderboardChallengeId, setLeaderboardChallengeId] = useState<string | null>(null);
  const formValidationError = gameplayValidationMessage(form);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const nextItems = await weeklyChallengeAdminService.list();
      setItems(nextItems);
      setPage((current) => Math.min(current, Math.max(1, Math.ceil(nextItems.length / PAGE_SIZE))));
    }
    catch { setError('Không thể tải danh sách thử thách tuần.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const openCreate = () => { setEditingId(null); setForm(emptyPayload()); setOpen(true); };
  const openEdit = (item: ManagedWeeklyChallenge) => { setEditingId(item.id); setForm(toPayload(item)); setOpen(true); };
  const updatePair = (index: number, field: keyof ChallengePair, value: string) => setForm((current) => ({
    ...current, pairs: current.pairs.map((pair, pairIndex) => pairIndex === index ? { ...pair, [field]: value } : pair),
  }));
  const save = async () => {
    if (formValidationError) {
      setError(formValidationError);
      return;
    }
    setBusy(true); setError(null);
    try {
      if (editingId) await weeklyChallengeAdminService.update(editingId, form);
      else await weeklyChallengeAdminService.create(form);
      setOpen(false); await load();
    } catch (err: unknown) {
      setError((err as { response?: { data?: { message?: string } } }).response?.data?.message || 'Không thể lưu thử thách.');
    } finally { setBusy(false); }
  };
  const act = async (action: () => Promise<void>) => {
    setBusy(true); setError(null);
    try { await action(); await load(); }
    catch (err: unknown) { setError((err as { response?: { data?: { message?: string } } }).response?.data?.message || 'Thao tác thất bại.'); }
    finally { setBusy(false); }
  };
  const loadLeaderboard = async (challengeId: string) => {
    setLeaderboardLoading(true); setLeaderboardError(null);
    try { setLeaderboard(await weeklyChallengeAdminService.leaderboard(challengeId)); }
    catch { setLeaderboardError('Không thể tải bảng xếp hạng. Vui lòng thử lại.'); }
    finally { setLeaderboardLoading(false); }
  };
  const openLeaderboard = (challengeId: string) => {
    setLeaderboardChallengeId(challengeId); setLeaderboard(null); setLeaderboardOpen(true);
    void loadLeaderboard(challengeId);
  };
  const pageCount = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const visibleItems = items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return <Box sx={{ p: { xs: 2, md: 3 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 2, mb: 3 }}>
      <Box><Typography variant="h5" sx={{ fontWeight: 900 }}>Trò chơi & thưởng học tập</Typography>
        <Typography color="text.secondary">Course Manager thay nội dung theo tuần; hệ thống tự xáo thẻ, giới hạn lượt và chốt thưởng.</Typography></Box>
      <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>Tạo tuần mới</Button>
    </Stack>
    <Alert severity="info" sx={{ mb: 3 }}>Điểm danh được chốt mỗi ngày từ hoạt động học hoàn thành. Xếp hạng trò chơi chỉ chốt sau Chủ Nhật. Tiền thưởng là số dư khuyến mại không thể rút.</Alert>
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    {loading ? <CircularProgress /> : items.length === 0 ? (
      <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
        <SportsEsportsOutlinedIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
        <Typography variant="h6" sx={{ mt: 1, fontWeight: 800 }}>Chưa có thử thách tuần nào</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.75, mb: 2 }}>
          Tạo nội dung cho tuần hiện tại, kiểm tra mức thưởng rồi công khai để học viên nhìn thấy trò chơi.
        </Typography>
        <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>Tạo thử thách tuần này</Button>
      </Paper>
    ) : <>
      <Grid container spacing={2}>{visibleItems.map((item) => <Grid size={{ xs: 12, lg: 6 }} key={item.id}>
      <Card variant="outlined"><CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1 }}>
          <Box><Typography variant="h6" sx={{ fontWeight: 800 }}>{item.title}</Typography>
            <Typography variant="body2" color="text.secondary">{item.weekStart} → {item.weekEnd} · {item.pairs.length} cặp thẻ</Typography></Box>
          <Chip label={item.status === 'DRAFT' ? 'Bản nháp' : item.status === 'PUBLISHED' ? 'Đang công khai' : 'Đã chốt'} color={item.status === 'PUBLISHED' ? 'success' : 'default'} />
        </Stack><Divider sx={{ my: 2 }} />
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
          <Chip label={`Điểm danh ${item.dailyAttendanceReward.toLocaleString('vi-VN')} ₫/ngày`} />
          <Chip label={`Hạng 1: ${item.firstPrize.toLocaleString('vi-VN')} ₫`} />
          <Chip label={`Hạng 2: ${item.secondPrize.toLocaleString('vi-VN')} ₫`} />
          <Chip label={`Hạng 3: ${item.thirdPrize.toLocaleString('vi-VN')} ₫`} />
        </Stack>
      </CardContent><CardActions>
        <Button startIcon={<LeaderboardOutlinedIcon />} onClick={() => openLeaderboard(item.id)}>Bảng xếp hạng</Button>
        {item.status === 'DRAFT' && <Button startIcon={<EditOutlinedIcon />} onClick={() => openEdit(item)}>Sửa nội dung</Button>}
        {item.status === 'DRAFT' && <Button startIcon={<PublicRoundedIcon />} onClick={() => act(() => weeklyChallengeAdminService.publish(item.id))}>Công khai</Button>}
        {item.status === 'PUBLISHED' && <Button color="warning" onClick={() => act(() => weeklyChallengeAdminService.unpublish(item.id))}>Ẩn</Button>}
        {item.status === 'DRAFT' && <Button color="error" startIcon={<DeleteOutlineRoundedIcon />} onClick={() => act(() => weeklyChallengeAdminService.remove(item.id))}>Xóa</Button>}
      </CardActions></Card>
      </Grid>)}</Grid>
      {pageCount > 1 && <Stack sx={{ alignItems: 'center', mt: 3 }}>
        <Pagination count={pageCount} page={page} onChange={(_, nextPage) => setPage(nextPage)} color="primary" />
      </Stack>}
    </>}

    <Dialog open={open} onClose={() => !busy && setOpen(false)} fullWidth maxWidth="md">
      <DialogTitle><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><SportsEsportsOutlinedIcon /><span>{editingId ? 'Sửa thử thách tuần' : 'Tạo thử thách tuần'}</span></Stack></DialogTitle>
      <DialogContent dividers><Grid container spacing={2}>
        {formValidationError && <Grid size={{ xs: 12 }}><Alert severity="error">{formValidationError}</Alert></Grid>}
        <Grid size={{ xs: 12, sm: 4 }}><TextField fullWidth type="date" label="Tuần bắt đầu (Thứ Hai)" slotProps={{ inputLabel: { shrink: true } }} value={form.weekStart} onChange={(e) => setForm({ ...form, weekStart: mondayOfWeek(e.target.value) })} helperText="Chọn ngày bất kỳ trong tuần — hệ thống tự lùi về Thứ Hai." /></Grid>        <Grid size={{ xs: 12, sm: 4 }}><TextField fullWidth select label="Cấp độ" value={form.jlptLevel} onChange={(e) => setForm({ ...form, jlptLevel: e.target.value })}>{['N5','N4','N3','N2','N1'].map((level) => <MenuItem key={level} value={level}>{level}</MenuItem>)}</TextField></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><TextField fullWidth type="number" label="Lượt xếp hạng/ngày" value={form.dailyRankedLimit} onChange={(e) => setForm({ ...form, dailyRankedLimit: Number(e.target.value) })} slotProps={{ htmlInput: { min: 1, max: 10 } }} error={form.dailyRankedLimit < 1 || form.dailyRankedLimit > 10} helperText="Từ 1 đến 10 lượt." /></Grid>
        <Grid size={{ xs: 12 }}><TextField fullWidth label="Tên thử thách" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Grid>
        <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} label="Mô tả luật chơi" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><TextField fullWidth type="number" label="Phạt sai (giây)" value={form.wrongPenaltySeconds} onChange={(e) => setForm({ ...form, wrongPenaltySeconds: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: 30 } }} error={form.wrongPenaltySeconds < 0 || form.wrongPenaltySeconds > 30} helperText="Từ 0 đến 30 giây." /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><TextField fullWidth type="number" label="Thưởng điểm danh/ngày" value={form.dailyAttendanceReward} onChange={(e) => setForm({ ...form, dailyAttendanceReward: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: 10000 } }} error={form.dailyAttendanceReward < 0 || form.dailyAttendanceReward > 10000} helperText="Từ 0 đến 10.000 đồng." /></Grid>
        <Grid size={{ xs: 4, sm: 2 }}><TextField fullWidth type="number" label="Hạng 1" value={form.firstPrize} onChange={(e) => setForm({ ...form, firstPrize: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: 500000 } }} error={form.firstPrize < form.secondPrize || form.firstPrize > 500000 || form.firstPrize < 0} /></Grid>
        <Grid size={{ xs: 4, sm: 2 }}><TextField fullWidth type="number" label="Hạng 2" value={form.secondPrize} onChange={(e) => setForm({ ...form, secondPrize: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: 500000 } }} error={form.firstPrize < form.secondPrize || form.secondPrize < form.thirdPrize || form.secondPrize > 500000 || form.secondPrize < 0} /></Grid>
        <Grid size={{ xs: 4, sm: 2 }}><TextField fullWidth type="number" label="Hạng 3" value={form.thirdPrize} onChange={(e) => setForm({ ...form, thirdPrize: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: 500000 } }} error={form.secondPrize < form.thirdPrize || form.thirdPrize > 500000 || form.thirdPrize < 0} /></Grid>
        <Grid size={{ xs: 12 }}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Nội dung ghép cặp</Typography></Grid>
        {form.pairs.map((pair, index) => <Grid size={{ xs: 12 }} key={index}><Stack direction="row" spacing={1}>
          <TextField fullWidth label={`Thuật ngữ ${index + 1}`} value={pair.prompt} onChange={(e) => updatePair(index, 'prompt', e.target.value)} />
          <TextField fullWidth label="Cách đọc / nghĩa" value={pair.answer} onChange={(e) => updatePair(index, 'answer', e.target.value)} />
          <IconButton aria-label="Xóa cặp" disabled={form.pairs.length <= 4} onClick={() => setForm({ ...form, pairs: form.pairs.filter((_, pairIndex) => pairIndex !== index) })}><DeleteOutlineRoundedIcon /></IconButton>
        </Stack></Grid>)}
        <Grid size={{ xs: 12 }}><Button variant="outlined" disabled={form.pairs.length >= 12} onClick={() => setForm({ ...form, pairs: [...form.pairs, { prompt: '', answer: '' }] })}>Thêm cặp thẻ</Button></Grid>
      </Grid></DialogContent>
      <DialogActions><Button onClick={() => setOpen(false)} disabled={busy}>Hủy</Button><Button variant="contained" onClick={save} disabled={busy || Boolean(formValidationError)}>{busy ? 'Đang lưu...' : 'Lưu bản nháp'}</Button></DialogActions>
    </Dialog>
    <WeeklyChallengeLeaderboardDialog open={leaderboardOpen} loading={leaderboardLoading}
      error={leaderboardError} data={leaderboard}
      onRetry={() => leaderboardChallengeId && void loadLeaderboard(leaderboardChallengeId)}
      onClose={() => setLeaderboardOpen(false)} />
  </Box>;
}
