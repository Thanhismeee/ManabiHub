import { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import PauseCircleOutlineOutlinedIcon from '@mui/icons-material/PauseCircleOutlineOutlined';
import PlayCircleOutlineOutlinedIcon from '@mui/icons-material/PlayCircleOutlineOutlined';
import SkipNextOutlinedIcon from '@mui/icons-material/SkipNextOutlined';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';

const STUDY_PLAN_STORAGE_KEY = 'manabihub.student.study-plan.v1';
const BREAK_MINUTES = 5;
const DURATION_OPTIONS = [25, 50, 60] as const;
const SKILLS = ['Kanji & Từ vựng', 'Ngữ pháp', 'Đọc hiểu & Nghe'] as const;

type Skill = (typeof SKILLS)[number];

interface FocusTotal {
  minutes: number;
  sessions: number;
}

interface StoredPlan {
  weekKey?: string;
  focusTotals?: Record<string, FocusTotal>;
  attendance?: Record<string, string[]>;
  [key: string]: unknown;
}

interface TimerState {
  mode: 'focus' | 'break';
  secondsLeft: number;
  initialSeconds: number;
  running: boolean;
}

interface CoursePomodoroPanelProps {
  courseTitle: string;
}

function todayKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function getWeekKey(date = new Date()) {
  const monday = new Date(date);
  const daysSinceMonday = (monday.getDay() + 6) % 7;
  monday.setDate(monday.getDate() - daysSinceMonday);
  return todayKey(monday);
}

function readPlan(): StoredPlan {
  if (typeof window === 'undefined') return { weekKey: getWeekKey(), focusTotals: {}, attendance: {} };
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STUDY_PLAN_STORAGE_KEY) ?? 'null') as StoredPlan | null;
    if (!parsed) return { weekKey: getWeekKey(), focusTotals: {}, attendance: {} };
    return {
      ...parsed,
      weekKey: getWeekKey(),
      focusTotals: parsed.weekKey === getWeekKey() ? parsed.focusTotals ?? {} : {},
      attendance: parsed.attendance ?? {},
    };
  } catch {
    return { weekKey: getWeekKey(), focusTotals: {}, attendance: {} };
  }
}

function formatSeconds(seconds: number) {
  const safe = Math.max(0, seconds);
  return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`;
}

function targetKey(skill: Skill, courseTitle: string) {
  return `${skill} · ${courseTitle}`;
}

export function CoursePomodoroPanel({ courseTitle }: CoursePomodoroPanelProps) {
  const [plan, setPlan] = useState<StoredPlan>(readPlan);
  const [skill, setSkill] = useState<Skill>(SKILLS[0]);
  const [duration, setDuration] = useState<number>(25);
  const [timer, setTimer] = useState<TimerState | null>(null);

  useEffect(() => {
    window.localStorage.setItem(STUDY_PLAN_STORAGE_KEY, JSON.stringify(plan));
  }, [plan]);

  useEffect(() => {
    if (!timer?.running) return undefined;
    const interval = window.setInterval(() => {
      setTimer((current) => (current ? { ...current, secondsLeft: Math.max(0, current.secondsLeft - 1) } : null));
    }, 1000);
    return () => window.clearInterval(interval);
  }, [timer?.running]);

  const recordFocusSession = (elapsedSeconds: number) => {
    const elapsedMinutes = Math.max(1, Math.round(elapsedSeconds / 60));
    setPlan((previous) => {
      const focusTotals = { ...(previous.focusTotals ?? {}) };
      for (const key of [skill, targetKey(skill, courseTitle)]) {
        const current = focusTotals[key] ?? { minutes: 0, sessions: 0 };
        focusTotals[key] = {
          minutes: current.minutes + elapsedMinutes,
          sessions: current.sessions + 1,
        };
      }
      return {
        ...previous,
        weekKey: getWeekKey(),
        focusTotals,
        attendance: previous.attendance ?? {},
      };
    });
  };

  useEffect(() => {
    if (!timer || timer.secondsLeft > 0) return;
    if (timer.mode === 'focus') {
      recordFocusSession(timer.initialSeconds);
      setTimer({ mode: 'break', secondsLeft: BREAK_MINUTES * 60, initialSeconds: BREAK_MINUTES * 60, running: false });
    } else {
      setTimer(null);
    }
    // The timer state is intentionally the only trigger: skill/course are captured by the active session.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timer]);

  const courseTotal = useMemo(
    () => plan.focusTotals?.[targetKey(skill, courseTitle)] ?? { minutes: 0, sessions: 0 },
    [courseTitle, plan.focusTotals, skill],
  );

  const startOrResume = () => {
    if (timer?.mode === 'focus') {
      setTimer({ ...timer, running: true });
      return;
    }
    setTimer({ mode: 'focus', secondsLeft: duration * 60, initialSeconds: duration * 60, running: true });
  };

  const finishEarly = () => {
    if (!timer || timer.mode !== 'focus') return;
    const elapsedSeconds = timer.initialSeconds - timer.secondsLeft;
    if (elapsedSeconds > 0) recordFocusSession(elapsedSeconds);
    setTimer(null);
  };

  const progress = timer && timer.mode === 'focus'
    ? ((timer.initialSeconds - timer.secondsLeft) / timer.initialSeconds) * 100
    : timer?.mode === 'break' ? 100 : 0;

  return (
    <Paper
      data-testid="course-pomodoro-panel"
      variant="outlined"
      sx={{ p: { xs: 2, sm: 2.5 }, mb: 2, borderRadius: 2, bgcolor: '#FFFBFC', borderColor: '#F1C5CC' }}
    >
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
        <Box sx={{ minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <TimerOutlinedIcon sx={{ color: '#C41E3A' }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Pomodoro trong bài học</Typography>
          </Stack>
          <Typography variant="caption" color="text.secondary" noWrap>
            Tập trung cho: {courseTitle}
          </Typography>
        </Box>
        {!timer && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel id="course-pomodoro-skill-label">Kỹ năng</InputLabel>
              <Select
                labelId="course-pomodoro-skill-label"
                value={skill}
                label="Kỹ năng"
                onChange={(event) => setSkill(event.target.value as Skill)}
              >
                {SKILLS.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
              </Select>
            </FormControl>
            <Stack direction="row" spacing={0.5}>
              {DURATION_OPTIONS.map((option) => (
                <Button
                  key={option}
                  size="small"
                  variant={duration === option ? 'contained' : 'outlined'}
                  onClick={() => setDuration(option)}
                  sx={{ minWidth: 54, textTransform: 'none' }}
                >
                  {option}'
                </Button>
              ))}
            </Stack>
          </Stack>
        )}
      </Stack>

      <Box sx={{ mt: 2 }}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
          <Typography variant="h4" sx={{ fontWeight: 900, color: '#172033', letterSpacing: 1 }}>
            {timer ? formatSeconds(timer.secondsLeft) : `${duration}:00`}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {timer?.mode === 'break' ? 'Nghỉ giải lao' : timer ? (timer.running ? 'Đang tập trung' : 'Tạm dừng') : 'Sẵn sàng bắt đầu'}
          </Typography>
        </Stack>
        <LinearProgress
          variant="determinate"
          value={Math.min(100, Math.max(0, progress))}
          sx={{ mt: 0.75, height: 8, borderRadius: 4, bgcolor: '#F5E5E8', '& .MuiLinearProgress-bar': { bgcolor: '#C41E3A' } }}
        />
      </Box>

      <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: 'wrap' }}>
        {timer?.mode === 'break' ? (
          <Button variant="outlined" startIcon={<SkipNextOutlinedIcon />} onClick={() => setTimer(null)}>Bỏ qua nghỉ</Button>
        ) : (
          <Button variant="contained" startIcon={timer?.running ? <PauseCircleOutlineOutlinedIcon /> : <PlayCircleOutlineOutlinedIcon />} onClick={() => timer?.running ? setTimer({ ...timer, running: false }) : startOrResume()} sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#A71931' } }}>
            {timer ? (timer.running ? 'Tạm dừng' : 'Tiếp tục') : 'Bắt đầu'}
          </Button>
        )}
        {timer?.mode === 'focus' && (
          <Button variant="outlined" onClick={finishEarly}>Kết thúc & lưu phiên</Button>
        )}
        <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center', ml: 'auto' }}>
          Khóa học: {courseTotal.minutes} phút · {courseTotal.sessions} phiên
        </Typography>
      </Stack>
    </Paper>
  );
}
