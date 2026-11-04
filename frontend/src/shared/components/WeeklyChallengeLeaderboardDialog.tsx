import {
  Alert, Avatar, Box, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, IconButton, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Typography, Button,
} from '@mui/material';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined';
import type {
  WeeklyChallengeLeaderboard,
  WeeklyChallengeLeaderboardEntry,
} from '../types/weeklyChallengeLeaderboard';

interface WeeklyChallengeLeaderboardDialogProps {
  open: boolean;
  loading: boolean;
  error: string | null;
  data: WeeklyChallengeLeaderboard | null;
  onClose: () => void;
  onRetry: () => void;
}

function formatMilliseconds(value: number) {
  const seconds = Math.max(0, value) / 1000;
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(Math.floor(seconds % 60)).padStart(2, '0')}.${String(Math.floor((value % 1000) / 10)).padStart(2, '0')}`;
}

function formatVnd(value: number) {
  return `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
}

function rankLabel(rank: number) {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return `#${rank}`;
}

function LeaderboardRow({ entry, ownRankOnly = false }: {
  entry: WeeklyChallengeLeaderboardEntry;
  ownRankOnly?: boolean;
}) {
  return <TableRow sx={{ bgcolor: entry.currentStudent ? '#FFF1F3' : undefined }}>
    <TableCell sx={{ fontWeight: 900, width: 76 }}>{rankLabel(entry.rank)}</TableCell>
    <TableCell>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
        <Avatar src={entry.avatarUrl ?? undefined} sx={{ width: 34, height: 34, bgcolor: '#C41E3A' }}>
          {entry.displayName.slice(0, 1).toUpperCase()}
        </Avatar>
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 800 }}>{entry.displayName}</Typography>
          {entry.currentStudent && <Typography variant="caption" sx={{ color: '#C41E3A', fontWeight: 800 }}>
            {ownRankOnly ? 'Thứ hạng của bạn' : 'Bạn'}
          </Typography>}
        </Box>
      </Stack>
    </TableCell>
    <TableCell align="right" sx={{ fontWeight: 800, whiteSpace: 'nowrap' }}>
      {formatMilliseconds(entry.bestMillis)}
    </TableCell>
    <TableCell align="right" sx={{ color: entry.rewardAmount > 0 ? '#15803D' : '#667085', fontWeight: 800, whiteSpace: 'nowrap' }}>
      {entry.rewardAmount > 0 ? formatVnd(entry.rewardAmount) : '—'}
    </TableCell>
  </TableRow>;
}

export function WeeklyChallengeLeaderboardDialog({
  open, loading, error, data, onClose, onRetry,
}: WeeklyChallengeLeaderboardDialogProps) {
  const currentOutsideList = data?.currentStudent
    && !data.entries.some((entry) => entry.rank === data.currentStudent?.rank);

  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
        <EmojiEventsOutlinedIcon sx={{ color: '#B7791F' }} />
        <Box>
          <Typography sx={{ fontWeight: 900 }}>Bảng xếp hạng thử thách tuần</Typography>
          {data && <Typography variant="caption" color="text.secondary">
            {data.weekStart} → {data.weekEnd}
          </Typography>}
        </Box>
      </Stack>
      <IconButton aria-label="Đóng bảng xếp hạng" onClick={onClose}><CloseRoundedIcon /></IconButton>
    </DialogTitle>
    <DialogContent dividers sx={{ minHeight: 300, bgcolor: '#FAF9F6' }}>
      {loading ? <Box sx={{ minHeight: 260, display: 'grid', placeItems: 'center' }}>
        <CircularProgress aria-label="Đang tải bảng xếp hạng" />
      </Box> : error ? <Alert severity="error" action={<Button color="inherit" onClick={onRetry}>Thử lại</Button>}>
        {error}
      </Alert> : data ? <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 1 }}>
          <Box>
            <Typography sx={{ fontWeight: 900 }}>{data.challengeTitle}</Typography>
            <Typography variant="body2" color="text.secondary">
              {data.totalParticipants} học viên đã hoàn thành lượt xếp hạng.
            </Typography>
          </Box>
          <Chip size="small" color={data.settled ? 'success' : 'warning'}
            label={data.settled ? 'Đã chốt thưởng' : 'Đang cập nhật'} />
        </Stack>
        {data.entries.length === 0 ? <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <EmojiEventsOutlinedIcon sx={{ fontSize: 44, color: '#98A2B3' }} />
          <Typography sx={{ mt: 1, fontWeight: 900 }}>Chưa có thành tích xếp hạng</Typography>
          <Typography variant="body2" color="text.secondary">Hoàn thành một lượt tính hạng để xuất hiện tại đây.</Typography>
        </Paper> : <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Bảng xếp hạng thử thách tuần">
            <TableHead><TableRow sx={{ bgcolor: '#F2F4F7' }}>
              <TableCell>Hạng</TableCell><TableCell>Học viên</TableCell>
              <TableCell align="right">Thành tích</TableCell><TableCell align="right">Thưởng dự kiến</TableCell>
            </TableRow></TableHead>
            <TableBody>{data.entries.map((entry) => <LeaderboardRow key={`${entry.rank}-${entry.displayName}`} entry={entry} />)}</TableBody>
          </Table>
        </TableContainer>}
        {currentOutsideList && data.currentStudent && <Box>
          <Typography variant="caption" sx={{ color: '#667085', fontWeight: 800 }}>THỨ HẠNG CỦA BẠN</Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ mt: 0.75 }}>
            <Table size="small"><TableBody><LeaderboardRow entry={data.currentStudent} ownRankOnly /></TableBody></Table>
          </TableContainer>
        </Box>}
        {!data.settled && <Alert severity="info">Bảng xếp hạng cập nhật theo thành tích tốt nhất. Thưởng chính thức được chốt sau khi tuần kết thúc.</Alert>}
      </Stack> : null}
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Đóng</Button></DialogActions>
  </Dialog>;
}
