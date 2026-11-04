import { useEffect, useRef, useState } from 'react';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Grid, IconButton, LinearProgress, Paper, Stack, Typography,
} from '@mui/material';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined';
import SportsEsportsOutlinedIcon from '@mui/icons-material/SportsEsportsOutlined';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import LeaderboardOutlinedIcon from '@mui/icons-material/LeaderboardOutlined';
import { weeklyChallengeService, type ChallengeAttempt, type WeeklyChallenge } from '../services/weeklyChallengeService';
import { WeeklyChallengeLeaderboardDialog } from '../../../shared/components/WeeklyChallengeLeaderboardDialog';
import type { WeeklyChallengeLeaderboard } from '../../../shared/types/weeklyChallengeLeaderboard';

interface LearningChallengeWidgetProps { accountKey?: string | null }

function formatMilliseconds(value: number | null) {
  if (value === null) return '--:--.--';
  const seconds = Math.max(0, value) / 1000;
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(Math.floor(seconds % 60)).padStart(2, '0')}.${String(Math.floor((value % 1000) / 10)).padStart(2, '0')}`;
}

function formatVnd(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
}

export function LearningChallengeWidget(_props: LearningChallengeWidgetProps) {
  const [challenge, setChallenge] = useState<WeeklyChallenge | null>(null);
  const [attempt, setAttempt] = useState<ChallengeAttempt | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [flipped, setFlipped] = useState<string[]>([]);
  const [elapsed, setElapsed] = useState(0);
  const [leaderboardOpen, setLeaderboardOpen] = useState(false);
  const [leaderboardLoading, setLeaderboardLoading] = useState(false);
  const [leaderboardError, setLeaderboardError] = useState<string | null>(null);
  const [leaderboard, setLeaderboard] = useState<WeeklyChallengeLeaderboard | null>(null);
  const startedAt = useRef(0);
  const matchInFlight = useRef(false);

  useEffect(() => {
    weeklyChallengeService.current()
      .then(setChallenge)
      .catch(() => setError('Không thể tải thử thách tuần.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!open || !attempt || attempt.completed) return undefined;
    const timer = window.setInterval(() => setElapsed(Date.now() - startedAt.current), 100);
    return () => window.clearInterval(timer);
  }, [attempt, open]);

  const start = async () => {
    if (!challenge) return;
    setBusy(true);
    setError(null);
    try {
      const next = await weeklyChallengeService.start(challenge.id);
      setAttempt(next);
      setChallenge((current) => current ? {
        ...current,
        rankedAttemptsToday: next.ranked
          ? current.rankedAttemptsToday + 1
          : current.rankedAttemptsToday,
      } : current);
      setFlipped([]);
      setElapsed(0);
      startedAt.current = Date.now();
      setOpen(true);
    } catch {
      setError('Không thể bắt đầu lượt chơi. Vui lòng tải lại và thử lại.');
    } finally {
      setBusy(false);
    }
  };

  const loadLeaderboard = async () => {
    if (!challenge) return;
    setLeaderboardLoading(true);
    setLeaderboardError(null);
    try {
      setLeaderboard(await weeklyChallengeService.leaderboard(challenge.id));
    } catch {
      setLeaderboardError('Không thể tải bảng xếp hạng. Vui lòng thử lại.');
    } finally {
      setLeaderboardLoading(false);
    }
  };

  const openLeaderboard = () => {
    setLeaderboardOpen(true);
    void loadLeaderboard();
  };

  const selectCard = async (cardId: string) => {
    if (!attempt || busy || matchInFlight.current || attempt.completed || flipped.includes(cardId)) return;
    const card = attempt.cards.find((item) => item.id === cardId);
    if (!card || card.matched) return;
    if (flipped.length === 0) {
      setFlipped([cardId]);
      return;
    }
    const firstId = flipped[0];
    matchInFlight.current = true;
    setFlipped([firstId, cardId]);
    setBusy(true);
    setError(null);
    const previousMatched = attempt.matchedPairs;
    try {
      const next = await weeklyChallengeService.match(attempt.attemptId, firstId, cardId);
      setAttempt(next);
      if (next.completed) {
        weeklyChallengeService.current()
          .then((refreshedChallenge) => {
            if (refreshedChallenge) setChallenge(refreshedChallenge);
          })
          .catch(() => undefined);
      }
      if (next.matchedPairs > previousMatched) {
        window.setTimeout(() => setFlipped([]), 220);
      } else {
        window.setTimeout(() => setFlipped([]), 800);
      }
    } catch {
      setFlipped([]);
      setError('Không thể xác nhận cặp thẻ. Lượt chơi đã được giữ an toàn trên máy chủ.');
    } finally {
      matchInFlight.current = false;
      setBusy(false);
    }
  };

  if (loading) {
    return <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}><CircularProgress size={28} /></Paper>;
  }
  if (!challenge) {
    return <Paper elevation={0} sx={{ p: 2.25, border: '1px dashed #D0D5DD', borderRadius: 2, bgcolor: '#FCFCFD' }}>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
        <SportsEsportsOutlinedIcon sx={{ color: '#98A2B3', mt: 0.25 }} />
        <Box>
          <Typography variant="subtitle1" sx={{ color: '#344054', fontWeight: 900 }}>Thử thách tuần đang được chuẩn bị</Typography>
          <Typography variant="body2" sx={{ mt: 0.5, color: '#667085' }}>
            Khi nội dung tuần này được công khai, trò chơi ghép thẻ và mức thưởng sẽ xuất hiện tại đây.
          </Typography>
          {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}
        </Box>
      </Stack>
    </Paper>;
  }
  const remaining = Math.max(0, challenge.dailyRankedLimit - challenge.rankedAttemptsToday);
  const displayedTime = attempt?.totalMillis ?? elapsed + (attempt?.penaltyMillis ?? 0);

  return (
    <Paper elevation={0} sx={{ p: 2.25, border: '1px solid #E4E7EC', borderRadius: 2, bgcolor: '#fff' }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 1 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <SportsEsportsOutlinedIcon sx={{ color: '#C41E3A' }} />
          <Typography variant="subtitle1" sx={{ color: '#172033', fontWeight: 900 }}>{challenge.title}</Typography>
        </Stack>
        <Chip size="small" label={challenge.jlptLevel} sx={{ bgcolor: '#FFF1F3', color: '#C41E3A', fontWeight: 900 }} />
      </Stack>
      <Typography variant="body2" sx={{ mt: 1.25, color: '#667085' }}>{challenge.description}</Typography>
      <Alert severity="info" sx={{ mt: 1.5 }}>
        Xếp hạng chốt sau Chủ Nhật. Thưởng: {formatVnd(challenge.firstPrize)} / {formatVnd(challenge.secondPrize)} / {formatVnd(challenge.thirdPrize)}.
        Điểm danh học tập hợp lệ: {formatVnd(challenge.dailyAttendanceReward)}/ngày.
      </Alert>
      <Stack direction="row" spacing={1.25} sx={{ mt: 1.5 }}>
        <Box sx={{ flex: 1, p: 1.25, borderRadius: 1.5, bgcolor: '#FFF8E7' }}>
          <Typography variant="caption" color="text.secondary">Kỷ lục tuần</Typography>
          <Typography sx={{ mt: 0.25, color: '#A16207', fontWeight: 900 }}>{formatMilliseconds(challenge.personalBestMillis)}</Typography>
        </Box>
        <Box sx={{ flex: 1, p: 1.25, borderRadius: 1.5, bgcolor: '#EEF4FF' }}>
          <Typography variant="caption" color="text.secondary">Lượt xếp hạng</Typography>
          <Typography sx={{ mt: 0.25, color: '#1D4ED8', fontWeight: 900 }}>{remaining}/{challenge.dailyRankedLimit} hôm nay</Typography>
        </Box>
      </Stack>
      {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}
      <Stack spacing={1} sx={{ mt: 1.5 }}>
        <Button fullWidth variant="contained" disabled={busy} startIcon={<SportsEsportsOutlinedIcon />}
          onClick={start} sx={{ bgcolor: '#C41E3A', fontWeight: 900, '&:hover': { bgcolor: '#A71931' } }}>
          {remaining > 0 ? 'Vào chơi ngay' : 'Luyện tập tự do'}
        </Button>
        <Button fullWidth variant="outlined" startIcon={<LeaderboardOutlinedIcon />} onClick={openLeaderboard}
          sx={{ borderColor: '#CBD5E1', color: '#1B2A4A', fontWeight: 900 }}>
          Xem bảng xếp hạng
        </Button>
      </Stack>
      <Typography variant="caption" sx={{ display: 'block', mt: 1, color: '#98A2B3' }}>
        Nội dung và lượt chơi do máy chủ quản lý; tiền thưởng là số dư khuyến mại dùng mua khóa học và không thể rút.
      </Typography>

      <Dialog open={open} onClose={() => !busy && setOpen(false)} fullWidth maxWidth="md">
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Box><Typography sx={{ fontWeight: 900 }}>{challenge.title}</Typography>
            <Typography variant="caption" color="text.secondary">{attempt?.ranked ? 'Lượt tính xếp hạng' : 'Chế độ luyện tập tự do'}</Typography></Box>
          <IconButton aria-label="Đóng trò chơi" disabled={busy} onClick={() => setOpen(false)}><CloseRoundedIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers sx={{ bgcolor: '#FAF9F6' }}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 2 }}>
            <Chip icon={<TimerOutlinedIcon />} label={`Thời gian ${formatMilliseconds(displayedTime)}`} />
            <Chip color={(attempt?.penaltyMillis ?? 0) > 0 ? 'warning' : 'default'} label={`Phạt +${(attempt?.penaltyMillis ?? 0) / 1000} giây`} />
          </Stack>
          <LinearProgress variant="determinate" value={attempt ? (attempt.matchedPairs / attempt.totalPairs) * 100 : 0} sx={{ mb: 2, height: 7, borderRadius: 999 }} />
          <Grid container spacing={1.25}>
            {attempt?.cards.map((card) => {
              const visible = flipped.includes(card.id) || card.matched;
              return <Grid size={{ xs: 4, sm: 3 }} key={card.id}><Button fullWidth
                aria-label={visible ? card.value : 'Thẻ đang úp'} disabled={card.matched || busy}
                onClick={() => selectCard(card.id)} sx={{ minHeight: { xs: 82, sm: 108 }, p: 1, border: '1px solid',
                  borderColor: card.matched ? '#86EFAC' : visible ? '#F2A4B1' : '#CBD5E1',
                  bgcolor: card.matched ? '#F0FDF4' : visible ? '#FFF1F3' : '#1B2A4A',
                  color: visible ? '#172033' : '#fff', fontSize: visible ? '1rem' : '2rem', fontWeight: 900,
                  textTransform: 'none', '&.Mui-disabled': { color: card.matched ? '#15803D' : undefined } }}>
                {visible ? card.value : '学'}
              </Button></Grid>;
            })}
          </Grid>
          {attempt?.completed && <Alert severity="success" icon={<EmojiEventsOutlinedIcon />} sx={{ mt: 2 }}>
            Hoàn thành trong <strong>{formatMilliseconds(attempt.totalMillis)}</strong>. Kết quả đã được máy chủ ghi nhận.
          </Alert>}
        </DialogContent>
        <DialogActions><Button onClick={() => setOpen(false)} disabled={busy}>Đóng</Button></DialogActions>
      </Dialog>
      <WeeklyChallengeLeaderboardDialog open={leaderboardOpen} loading={leaderboardLoading}
        error={leaderboardError} data={leaderboard} onRetry={() => void loadLeaderboard()}
        onClose={() => setLeaderboardOpen(false)} />
    </Paper>
  );
}
