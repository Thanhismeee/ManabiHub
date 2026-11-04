import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddAlarmOutlinedIcon from '@mui/icons-material/AddAlarmOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import HelpOutlineOutlinedIcon from '@mui/icons-material/HelpOutlineOutlined';
import HeadphonesOutlinedIcon from '@mui/icons-material/HeadphonesOutlined';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import NotificationsActiveOutlinedIcon from '@mui/icons-material/NotificationsActiveOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import TranslateOutlinedIcon from '@mui/icons-material/TranslateOutlined';
import { ROUTES } from '../../../shared/constants/routes';
import { isCourseAvailableOnDate, isSlotAvailableOnDate } from './studyScheduleAvailability';

export const STORAGE_KEY = 'manabihub.student.study-plan.v1';
export const STUDY_PLAN_UPDATED_EVENT = 'manabihub:study-plan-updated';
export const STUDY_PLAN_OPEN_SCHEDULE_EVENT = 'manabihub:study-plan-open-schedule';
export const STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT = 'manabihub:study-plan-open-bulk-schedule';
const WEEKLY_TARGET_MINUTES = 150;

const DAYS = [
  { value: 1, label: 'Thứ 2' },
  { value: 2, label: 'Thứ 3' },
  { value: 3, label: 'Thứ 4' },
  { value: 4, label: 'Thứ 5' },
  { value: 5, label: 'Thứ 6' },
  { value: 6, label: 'Thứ 7' },
  { value: 0, label: 'Chủ nhật' },
];

const SKILLS = [
  { label: 'Từ vựng', target: 60, color: '#D97706', Icon: MenuBookOutlinedIcon },
  { label: 'Ngữ pháp', target: 60, color: '#2563EB', Icon: TranslateOutlinedIcon },
  { label: 'Đọc hiểu & Nghe', target: 30, color: '#2F855A', Icon: HeadphonesOutlinedIcon },
] as const;

const PRESETS = [
  { id: 'gentle', label: 'Nhẹ nhàng', icon: '🌱', days: [1, 3, 5], time: '20:00', duration: 25, summary: '3 buổi/tuần · T2, T4, T6 · 20:00 · 25 phút' },
  { id: 'standard', label: 'Tiêu chuẩn', icon: '🔥', days: [1, 2, 3, 4, 5], time: '21:00', duration: 25, summary: '5 buổi/tuần · T2–T6 · 21:00 · 25 phút' },
] as const;

type PresetId = (typeof PRESETS)[number]['id'] | 'custom';
type DurationChoice = 25 | 50 | 60 | 'custom';
type BulkTimeAction = 'keep' | 'set' | 'shift';
type BulkDurationChoice = 25 | 50 | 60 | 'custom' | 'keep';

export interface StudyCourseOption {
  id: string;
  title: string;
  enrollmentStatus?: string;
  enrolledAt?: string | null;
  expiresAt?: string | null;
}

export interface StudySlot {
  id: string;
  dayOfWeek: number;
  startTime: string;
  durationMinutes: number;
  skill: string;
  courseId?: string;
  courseTitle?: string;
  lessonTitle?: string;
  enabled: boolean;
}

interface ScheduleOpenDetail {
  dayOfWeek?: number;
  dateKey?: string;
  slotId?: string;
}

interface FocusTotal {
  minutes: number;
  sessions: number;
}

export interface StudyPlan {
  weekKey: string;
  weeklyTargetMinutes: number;
  slots: StudySlot[];
  focusTotals: Record<string, FocusTotal>;
  attendance: Record<string, string[]>;
}

interface StudyGoalsWidgetProps {
  jlptGoal?: string | null;
  learningGoalLabel?: string;
  courses?: StudyCourseOption[];
}



export function todayKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function getWeekKey(date = new Date()) {
  const monday = new Date(date);
  const daysSinceMonday = (monday.getDay() + 6) % 7;
  monday.setDate(monday.getDate() - daysSinceMonday);
  return todayKey(monday);
}

function createInitialPlan(): StudyPlan {
  return { weekKey: getWeekKey(), weeklyTargetMinutes: WEEKLY_TARGET_MINUTES, slots: [], focusTotals: {}, attendance: {} };
}

export function readPlan(): StudyPlan {
  if (typeof window === 'undefined') return createInitialPlan();
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<StudyPlan> | null;
    if (!value) return createInitialPlan();
    const currentWeek = getWeekKey();
    return {
      weekKey: currentWeek,
      weeklyTargetMinutes: Number(value.weeklyTargetMinutes) || WEEKLY_TARGET_MINUTES,
      slots: Array.isArray(value.slots) ? value.slots : [],
      focusTotals: value.weekKey === currentWeek ? value.focusTotals ?? {} : {},
      attendance: value.attendance ?? {},
    };
  } catch {
    return createInitialPlan();
  }
}

function getUpcomingSlot(slots: StudySlot[], courses: StudyCourseOption[], now = new Date()) {
  return slots
    .filter((slot) => slot.enabled)
    .map((slot) => {
      const candidate = new Date(now);
      const offset = (slot.dayOfWeek - now.getDay() + 7) % 7;
      candidate.setDate(now.getDate() + offset);
      const [hour, minute] = slot.startTime.split(':').map(Number);
      candidate.setHours(hour, minute, 0, 0);
      if (candidate <= now) candidate.setDate(candidate.getDate() + 7);
      return { slot, date: candidate };
    })
    .filter(({ slot, date }) => isSlotAvailableOnDate(slot, date, courses))
    .sort((a, b) => a.date.getTime() - b.date.getTime())[0] ?? null;
}

function newId() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function timeToMinutes(value: string) {
  const [hour, minute] = value.split(':').map(Number);
  return (hour || 0) * 60 + (minute || 0);
}

function formatDateKey(value: string | null) {
  if (!value) return '';
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
}

export function StudyGoalsWidget({ jlptGoal, learningGoalLabel, courses = [] }: StudyGoalsWidgetProps) {
  const schedulableCourses = useMemo(() => courses.filter((course) => isCourseAvailableOnDate(course, new Date())), [courses]);
  const [plan, setPlan] = useState<StudyPlan>(readPlan);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [scheduleMode, setScheduleMode] = useState<'preset' | 'custom'>('preset');
  const [selectedPreset, setSelectedPreset] = useState<PresetId>('gentle');
  const [reminderPermission, setReminderPermission] = useState<NotificationPermission | 'unsupported'>(
    typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'unsupported',
  );
  const [editingTarget, setEditingTarget] = useState(false);
  const [targetInput, setTargetInput] = useState(WEEKLY_TARGET_MINUTES);
  const [durationChoice, setDurationChoice] = useState<DurationChoice>(25);
  const [customDuration, setCustomDuration] = useState(25);
  const [newSlot, setNewSlot] = useState<{ dayOfWeek: number; startTime: string; skill: string; courseId: string }>({ dayOfWeek: 1, startTime: '19:00', skill: SKILLS[0].label, courseId: schedulableCourses[0]?.id ?? '' });
  const [scheduleDayOfWeek, setScheduleDayOfWeek] = useState<number | null>(null);
  const [scheduleDateKey, setScheduleDateKey] = useState<string | null>(null);
  const [editingSlotId, setEditingSlotId] = useState<string | null>(null);
  const [scheduleError, setScheduleError] = useState('');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkSelectedIds, setBulkSelectedIds] = useState<Record<string, boolean>>({});
  const [bulkCourseSearch, setBulkCourseSearch] = useState('');
  const [bulkDayFilter, setBulkDayFilter] = useState<number | 'all'>('all');
  const [bulkTimeAction, setBulkTimeAction] = useState<BulkTimeAction>('keep');
  const [bulkStartTime, setBulkStartTime] = useState('19:00');
  const [bulkShiftMinutes, setBulkShiftMinutes] = useState(15);
  const [bulkDuration, setBulkDuration] = useState<BulkDurationChoice>('keep');
  const [bulkCustomDuration, setBulkCustomDuration] = useState(25);
  const [bulkError, setBulkError] = useState('');
  const [bulkDeleteConfirmOpen, setBulkDeleteConfirmOpen] = useState(false);
  const notifiedRef = useRef(new Set<string>());

  const openBulkSchedule = useCallback(() => {
    setBulkSelectedIds(Object.fromEntries(plan.slots.filter((slot) => slot.enabled).map((slot) => [slot.id, true])));
    setBulkCourseSearch('');
    setBulkDayFilter('all');
    setBulkTimeAction('keep');
    setBulkStartTime('19:00');
    setBulkShiftMinutes(15);
    setBulkDuration('keep');
    setBulkCustomDuration(25);
    setBulkError('');
    setBulkDeleteConfirmOpen(false);
    setBulkOpen(true);
  }, [plan.slots]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(plan));
    window.dispatchEvent(new Event(STUDY_PLAN_UPDATED_EVENT));
  }, [plan]);

  useEffect(() => {
    const handleOpenSchedule = (event: Event) => {
      const detail = (event as CustomEvent<ScheduleOpenDetail>).detail;
      const requestedSlotId = detail?.slotId;
      if (requestedSlotId) {
        const slot = plan.slots.find((s) => s.id === requestedSlotId);
        if (slot) {
          setScheduleMode('custom');
          setSelectedPreset('custom');
          setScheduleDayOfWeek(null);
          setScheduleDateKey(null);
          setEditingSlotId(slot.id);
          setScheduleError('');
          setNewSlot({
            dayOfWeek: slot.dayOfWeek,
            startTime: slot.startTime,
            skill: slot.skill,
            courseId: slot.courseId ?? '',
          });
          if (slot.durationMinutes === 25 || slot.durationMinutes === 50 || slot.durationMinutes === 60) {
            setDurationChoice(slot.durationMinutes);
            setCustomDuration(slot.durationMinutes);
          } else {
            setDurationChoice('custom');
            setCustomDuration(slot.durationMinutes);
          }
          setScheduleOpen(true);
          return;
        }
      }
      const requestedDay = detail?.dayOfWeek;
      setScheduleMode('custom');
      setSelectedPreset('custom');
      setScheduleDayOfWeek(typeof requestedDay === 'number' && requestedDay >= 0 && requestedDay <= 6 ? requestedDay : null);
      setScheduleDateKey(detail?.dateKey ?? null);
      setEditingSlotId(null);
      setScheduleError('');
      setNewSlot((current) => ({
        ...current,
        dayOfWeek: typeof requestedDay === 'number' && requestedDay >= 0 && requestedDay <= 6
          ? requestedDay
          : new Date().getDay(),
      }));
      setScheduleOpen(true);
    };
    window.addEventListener(STUDY_PLAN_OPEN_SCHEDULE_EVENT, handleOpenSchedule);
    return () => window.removeEventListener(STUDY_PLAN_OPEN_SCHEDULE_EVENT, handleOpenSchedule);
  }, [plan.slots]);

  useEffect(() => {
    window.addEventListener(STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT, openBulkSchedule);
    return () => window.removeEventListener(STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT, openBulkSchedule);
  }, [openBulkSchedule]);

  useEffect(() => {
    if (!newSlot.courseId && schedulableCourses.length > 0) {
      setNewSlot((current) => ({ ...current, courseId: schedulableCourses[0].id }));
    }
  }, [newSlot.courseId, schedulableCourses]);

  const upcoming = useMemo(() => getUpcomingSlot(plan.slots, courses), [courses, plan.slots]);
  const weeklyMinutes = SKILLS.reduce((sum, skill) => sum + (plan.focusTotals[skill.label]?.minutes ?? 0), 0);
  const progress = Math.min(100, Math.round((weeklyMinutes / Math.max(1, plan.weeklyTargetMinutes)) * 100));
  const courseTotals = Object.entries(plan.focusTotals)
    .filter(([key]) => key.includes(' · '))
    .sort(([, first], [, second]) => second.minutes - first.minutes);

  useEffect(() => {
    if (reminderPermission !== 'granted') return undefined;
    const check = () => {
      const now = new Date();
      for (const slot of plan.slots) {
        if (!slot.enabled || slot.dayOfWeek !== now.getDay()) continue;
        if (!isSlotAvailableOnDate(slot, now, courses)) continue;
        const [hour, minute] = slot.startTime.split(':').map(Number);
        const scheduled = new Date(now);
        scheduled.setHours(hour, minute, 0, 0);
        const difference = scheduled.getTime() - now.getTime();
        const key = `${todayKey(now)}:${slot.id}`;
        const reminderLeadMs = 15 * 60_000;
        if (
          difference <= reminderLeadMs
          && difference > reminderLeadMs - 60_000
          && !notifiedRef.current.has(key)
        ) {
          notifiedRef.current.add(key);
          const notification = new Notification('Còn 15 phút nữa là đến giờ học', {
            body: `${slot.skill} · ${slot.durationMinutes} phút${slot.courseTitle ? ` · ${slot.courseTitle}` : ''}. Mở ManabiHub để chuẩn bị vào học.`,
            icon: '/favicon.ico',
          });
          notification.onclick = () => {
            window.focus();
            if (slot.courseId) window.location.assign(ROUTES.STUDENT.COURSE_LEARN(slot.courseId));
            notification.close();
          };
        }
      }
    };
    check();
    const interval = window.setInterval(check, 30_000);
    return () => window.clearInterval(interval);
  }, [courses, plan.slots, reminderPermission]);

  const requestReminders = async () => {
    if (!('Notification' in window)) return;
    setReminderPermission(await Notification.requestPermission());
  };

  const saveWeeklyTarget = () => {
    setPlan((previous) => ({ ...previous, weeklyTargetMinutes: Math.min(2000, Math.max(30, Number(targetInput) || WEEKLY_TARGET_MINUTES)) }));
    setEditingTarget(false);
  };

  const openSchedule = () => {
    setEditingSlotId(null);
    setScheduleMode('preset');
    setSelectedPreset('gentle');
    setDurationChoice(25);
    setScheduleDayOfWeek(null);
    setScheduleDateKey(null);
    setEditingSlotId(null);
    setScheduleError('');
    setScheduleOpen(true);
  };

  const selectedCourse = schedulableCourses.find((course) => course.id === newSlot.courseId);
  const durationMinutes = durationChoice === 'custom' ? Math.min(180, Math.max(5, Number(customDuration) || 25)) : durationChoice;

  const daySlots = scheduleDayOfWeek === null
    ? []
    : plan.slots
      .filter((slot) => slot.enabled && slot.dayOfWeek === scheduleDayOfWeek)
      .sort((first, second) => timeToMinutes(first.startTime) - timeToMinutes(second.startTime));

  const bulkVisibleSlots = plan.slots
    .filter((slot) => slot.enabled)
    .filter((slot) => bulkDayFilter === 'all' || slot.dayOfWeek === bulkDayFilter)
    .filter((slot) => {
      const query = bulkCourseSearch.trim().toLocaleLowerCase('vi-VN');
      return !query || (slot.courseTitle || slot.skill).toLocaleLowerCase('vi-VN').includes(query);
    })
    .sort((first, second) => first.dayOfWeek - second.dayOfWeek || timeToMinutes(first.startTime) - timeToMinutes(second.startTime));
  const bulkSelectedCount = Object.values(bulkSelectedIds).filter(Boolean).length;
  const bulkHasChanges = bulkTimeAction !== 'keep' || bulkDuration !== 'keep';

  const applyBulkSchedule = () => {
    const selectedIds = new Set(Object.entries(bulkSelectedIds).filter(([, selected]) => selected).map(([id]) => id));
    if (selectedIds.size === 0) {
      setBulkError('Hãy chọn ít nhất một ca học để thay đổi.');
      return;
    }
    if (!bulkHasChanges) {
      setBulkError('Hãy chọn thao tác đổi giờ hoặc thời lượng trước khi áp dụng.');
      return;
    }
    const nextDuration = bulkDuration === 'custom'
      ? Math.min(180, Math.max(5, Number(bulkCustomDuration) || 25))
      : bulkDuration === 'keep' ? null : bulkDuration;
    const updatedSlots = plan.slots.map((slot) => {
      if (!selectedIds.has(slot.id)) return slot;
      let startMinutes = timeToMinutes(slot.startTime);
      if (bulkTimeAction === 'set') startMinutes = timeToMinutes(bulkStartTime);
      if (bulkTimeAction === 'shift') startMinutes = Math.min(1439, Math.max(0, startMinutes + bulkShiftMinutes));
      const hour = Math.floor(startMinutes / 60);
      const minute = startMinutes % 60;
      return {
        ...slot,
        startTime: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`,
        durationMinutes: nextDuration ?? slot.durationMinutes,
      };
    });
    const overlaps = (first: StudySlot, second: StudySlot) => first.enabled
      && second.enabled
      && first.dayOfWeek === second.dayOfWeek
      && timeToMinutes(first.startTime) < timeToMinutes(second.startTime) + second.durationMinutes
      && timeToMinutes(second.startTime) < timeToMinutes(first.startTime) + first.durationMinutes;
    const hasNewConflict = updatedSlots.some((slot, index) => updatedSlots.some((other, otherIndex) => {
      if (index >= otherIndex || !overlaps(slot, other)) return false;
      const previousSlot = plan.slots.find((candidate) => candidate.id === slot.id);
      const previousOther = plan.slots.find((candidate) => candidate.id === other.id);
      return !previousSlot || !previousOther || !overlaps(previousSlot, previousOther);
    }));
    if (hasNewConflict) {
      setBulkError('Thay đổi này tạo ra ca trùng giờ. Hãy lọc/chọn ít ca hơn hoặc chọn giờ khác.');
      return;
    }
    setPlan((previous) => ({ ...previous, slots: updatedSlots }));
    setBulkOpen(false);
  };

  const requestBulkDelete = () => {
    if (bulkSelectedCount === 0) {
      setBulkError('Hãy chọn ít nhất một ca học để xóa.');
      return;
    }
    setBulkDeleteConfirmOpen(true);
  };

  const confirmBulkDelete = () => {
    const selectedIds = new Set(Object.entries(bulkSelectedIds).filter(([, selected]) => selected).map(([id]) => id));
    setPlan((previous) => ({ ...previous, slots: previous.slots.filter((slot) => !selectedIds.has(slot.id)) }));
    setBulkDeleteConfirmOpen(false);
    setBulkOpen(false);
    setBulkError('');
  };

  const resetSlotEditor = () => {
    setEditingSlotId(null);
    setScheduleError('');
    setDurationChoice(25);
    setCustomDuration(25);
    setNewSlot((current) => ({
      ...current,
      dayOfWeek: scheduleDayOfWeek ?? current.dayOfWeek,
      startTime: '19:00',
      skill: SKILLS[0].label,
    }));
  };

  const editSlot = (slot: StudySlot) => {
    setScheduleMode('custom');
    setSelectedPreset('custom');
    setEditingSlotId(slot.id);
    setScheduleError('');
    setNewSlot({
      dayOfWeek: slot.dayOfWeek,
      startTime: slot.startTime,
      skill: slot.skill,
      courseId: slot.courseId ?? '',
    });
    if (slot.durationMinutes === 25 || slot.durationMinutes === 50 || slot.durationMinutes === 60) {
      setDurationChoice(slot.durationMinutes);
      setCustomDuration(slot.durationMinutes);
    } else {
      setDurationChoice('custom');
      setCustomDuration(slot.durationMinutes);
    }
  };

  const deleteSlot = (slotId: string) => {
    setPlan((previous) => ({ ...previous, slots: previous.slots.filter((slot) => slot.id !== slotId) }));
    if (editingSlotId === slotId) resetSlotEditor();
  };

  const addSchedule = () => {
    const preset = PRESETS.find((item) => item.id === selectedPreset);
    const days = scheduleMode === 'preset' && preset ? preset.days : [newSlot.dayOfWeek];
    const time = scheduleMode === 'preset' && preset ? preset.time : newSlot.startTime;
    const duration = scheduleMode === 'preset' && preset ? preset.duration : durationMinutes;
    const candidateSlots = days.map((dayOfWeek) => ({ dayOfWeek, startTime: time, durationMinutes: duration }));
    const hasConflict = plan.slots.some((slot) => {
      if (!slot.enabled || slot.id === editingSlotId || !candidateSlots.some((candidate) => candidate.dayOfWeek === slot.dayOfWeek)) return false;
      const candidate = candidateSlots.find((item) => item.dayOfWeek === slot.dayOfWeek);
      if (!candidate) return false;
      const candidateStart = timeToMinutes(candidate.startTime);
      const existingStart = timeToMinutes(slot.startTime);
      return candidateStart < existingStart + slot.durationMinutes
        && existingStart < candidateStart + candidate.durationMinutes;
    });
    if (hasConflict) {
      setScheduleError('Khung giờ này bị trùng với một ca khác. Hãy chọn giờ khác hoặc bấm Sửa ở ca đang có.');
      return;
    }
    const existingSlot = editingSlotId ? plan.slots.find((slot) => slot.id === editingSlotId) : undefined;
    const courseId = selectedCourse?.id ?? (newSlot.courseId || existingSlot?.courseId);
    const courseTitle = selectedCourse?.title ?? existingSlot?.courseTitle;
    const created = days.map((dayOfWeek) => ({
      id: editingSlotId ?? newId(),
      dayOfWeek,
      startTime: time,
      durationMinutes: duration,
      skill: newSlot.skill,
      courseId,
      courseTitle,
      lessonTitle: existingSlot?.lessonTitle,
      enabled: true,
    }));
    setPlan((previous) => ({
      ...previous,
      slots: editingSlotId
        ? previous.slots.map((slot) => slot.id === editingSlotId ? created[0] : slot)
        : [...previous.slots, ...created],
    }));
    if (scheduleDayOfWeek !== null) {
      resetSlotEditor();
    } else {
      setScheduleOpen(false);
    }
  };

  const applySuggestion = () => {
    setScheduleMode('preset');
    setSelectedPreset('gentle');
  };

  return (
    <Paper data-testid="study-goals-widget" data-onboarding-target="student-goals" elevation={0} sx={{ p: { xs: 2, sm: 2.5 }, border: '1px solid #E4E7EC', borderRadius: '8px', bgcolor: '#fff', position: 'relative' }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 1 }} data-tour="goal-badge">
        <Typography variant="h6" sx={{ color: '#172033', fontWeight: 900 }}>Mục tiêu học tập</Typography>
        <Chip label={learningGoalLabel || (jlptGoal ? `JLPT ${jlptGoal}` : 'Chưa thiết lập')} size="small" sx={{ bgcolor: learningGoalLabel || jlptGoal ? '#C41E3A' : '#EEF2F6', color: learningGoalLabel || jlptGoal ? '#fff' : '#475467', fontWeight: 900 }} />
      </Stack>
      <Typography variant="caption" sx={{ display: 'block', mt: 1, color: '#667085' }}>Lịch học và điểm tập trung được lưu trên thiết bị này; mục tiêu tự làm mới theo tuần.</Typography>

      <Box sx={{ mt: 2 }}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <Typography variant="body2" sx={{ fontWeight: 800 }}>Mục tiêu tuần</Typography>
            <Tooltip title="Mục tiêu tuần khuyến nghị là 150 phút (tương đương 6 phiên tập trung 25 phút)." arrow>
              <HelpOutlineOutlinedIcon sx={{ fontSize: 16, color: '#98A2B3' }} />
            </Tooltip>
          </Stack>
          {editingTarget ? (
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <TextField size="small" type="number" value={targetInput} onChange={(event) => setTargetInput(Number(event.target.value))} slotProps={{ htmlInput: { min: 30, max: 2000, step: 15, 'aria-label': 'Mục tiêu tuần (phút)' } }} sx={{ width: 108 }} />
              <IconButton size="small" aria-label="Lưu mục tiêu tuần" onClick={saveWeeklyTarget}><SaveOutlinedIcon fontSize="small" /></IconButton>
            </Stack>
          ) : (
            <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center' }}>
              <Typography variant="caption" sx={{ color: '#667085', fontWeight: 800 }}>{weeklyMinutes}/{plan.weeklyTargetMinutes} phút/tuần</Typography>
              <IconButton size="small" aria-label="Sửa mục tiêu tuần" onClick={() => { setTargetInput(plan.weeklyTargetMinutes); setEditingTarget(true); }}><EditOutlinedIcon sx={{ fontSize: 16 }} /></IconButton>
            </Stack>
          )}
        </Stack>
        <LinearProgress variant="determinate" value={progress} sx={{ mt: 0.75, height: 8, borderRadius: 4, bgcolor: '#F1F5F9', '& .MuiLinearProgress-bar': { bgcolor: '#C41E3A' } }} />
      </Box>

      {upcoming ? (
        <Alert icon={<AddAlarmOutlinedIcon />} severity="info" sx={{ mt: 2, py: 0.5 }}>
          <Typography variant="body2" sx={{ fontWeight: 800 }}>Buổi tiếp theo: {DAYS.find((day) => day.value === upcoming.slot.dayOfWeek)?.label} {upcoming.slot.startTime}</Typography>
          <Typography variant="caption">{upcoming.slot.skill} · {upcoming.slot.durationMinutes} phút{upcoming.slot.courseTitle ? ` · ${upcoming.slot.courseTitle}` : ''}</Typography>
        </Alert>
      ) : <Alert severity="info" sx={{ mt: 2, py: 0.5 }}>Bạn chưa có lịch học cố định. Hãy thêm buổi học đầu tiên.</Alert>}

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.5 }} data-tour="schedule">
        <Button size="small" variant="outlined" startIcon={<AddAlarmOutlinedIcon />} onClick={openSchedule} sx={{ borderColor: '#CBD5E1', color: '#475569', '&:hover': { borderColor: '#C41E3A', color: '#C41E3A', bgcolor: '#FFF7F8' } }}>Thêm lịch học</Button>
        <Button size="small" variant="outlined" startIcon={<NotificationsActiveOutlinedIcon />} onClick={() => void requestReminders()} disabled={reminderPermission === 'granted' || reminderPermission === 'unsupported'} sx={{ borderColor: '#CBD5E1', color: '#475569', '&:hover': { borderColor: '#C41E3A', color: '#C41E3A', bgcolor: '#FFF7F8' } }}>
          {reminderPermission === 'granted' ? 'Nhắc trước 15 phút' : reminderPermission === 'unsupported' ? 'Trình duyệt không hỗ trợ' : 'Bật nhắc trước 15 phút'}
        </Button>
      </Stack>

      <Divider sx={{ my: 2 }} />
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Điểm tập trung theo kỹ năng</Typography>
          <Tooltip title="Mỗi 1 phút tập trung khi học kỹ năng tương ứng sẽ được cộng 1 điểm." arrow><HelpOutlineOutlinedIcon sx={{ fontSize: 16, color: '#98A2B3' }} /></Tooltip>
        </Stack>
        <TimerOutlinedIcon sx={{ color: '#C41E3A' }} />
      </Stack>
      <Stack spacing={1.25} sx={{ mt: 1.25 }}>
        {SKILLS.map(({ label, target, color, Icon }) => {
          const total = plan.focusTotals[label] ?? { minutes: 0, sessions: 0 };
          return (
            <Box key={label}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Icon sx={{ fontSize: 18, color, flexShrink: 0 }} />
                <Typography variant="caption" sx={{ color: '#475467', flex: 1 }}>{label}</Typography>
                <Typography variant="caption" sx={{ fontWeight: 800, color }}>{total.minutes}/{target} phút</Typography>
              </Stack>
              <LinearProgress variant="determinate" value={Math.min(100, (total.minutes / target) * 100)} sx={{ mt: 0.45, height: 7, borderRadius: 4, bgcolor: `${color}1A`, '& .MuiLinearProgress-bar': { bgcolor: color } }} />
              <Typography variant="caption" sx={{ color: '#98A2B3', fontSize: '0.68rem' }}>{total.sessions} phiên tập trung</Typography>
            </Box>
          );
        })}
      </Stack>

      {courseTotals.length > 0 && <Box sx={{ mt: 1.5 }}><Typography variant="caption" sx={{ fontWeight: 900, color: '#475467' }}>Điểm theo khóa học</Typography>{courseTotals.slice(0, 3).map(([key, total]) => <Stack key={key} direction="row" sx={{ justifyContent: 'space-between', mt: 0.5 }}><Typography variant="caption" sx={{ color: '#667085', maxWidth: '75%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{key.split(' · ').slice(1).join(' · ')}</Typography><Typography variant="caption" sx={{ fontWeight: 800 }}>{total.minutes} điểm</Typography></Stack>)}</Box>}
      <Typography variant="caption" sx={{ display: 'block', mt: 1.5, color: '#667085' }}>Mỗi phút tập trung hoàn thành được tính là một điểm cho kỹ năng và khóa học đã chọn.</Typography>

      <Dialog open={scheduleOpen} onClose={() => setScheduleOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editingSlotId ? 'Sửa suất học' : scheduleDateKey ? `Sửa lịch ngày ${formatDateKey(scheduleDateKey)}` : 'Thêm lịch học'}</DialogTitle>
        <DialogContent>
          {scheduleDayOfWeek !== null && <Box data-testid="schedule-day-editor" sx={{ mb: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 900, mb: 0.75 }}>Các ca đang có trong ngày</Typography>
            {daySlots.length === 0 ? <Alert severity="info" sx={{ py: 0.5 }}>Chưa có ca học trong ngày này. Bạn có thể thêm ca đầu tiên bên dưới.</Alert> : <Stack spacing={0.75}>{daySlots.map((slot) => <Paper key={slot.id} variant="outlined" sx={{ p: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}><Box sx={{ minWidth: 0 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>{slot.startTime} · {slot.durationMinutes} phút</Typography><Typography variant="caption" color="text.secondary" noWrap>{slot.courseTitle || slot.skill}</Typography></Box><Stack direction="row" spacing={0.5}><Button size="small" onClick={() => editSlot(slot)} aria-label={`Sửa ${slot.startTime}`} sx={{ textTransform: 'none' }}>Sửa</Button><Button size="small" color="error" onClick={() => deleteSlot(slot.id)} aria-label={`Xóa ${slot.startTime}`} sx={{ textTransform: 'none' }}>Xóa</Button></Stack></Paper>)}</Stack>}
          </Box>}
          {scheduleDayOfWeek === null && <>
            <Typography variant="body2" sx={{ mb: 1.25, color: '#667085' }}>Chọn lịch mẫu để bắt đầu nhanh hoặc chuyển sang tùy chỉnh.</Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              {[...PRESETS, { id: 'custom' as const, label: 'Tùy chỉnh', icon: '⚙️', summary: 'Tự chọn khóa học, kỹ năng, ngày và giờ' }].map((preset) => <Button key={preset.id} variant={selectedPreset === preset.id ? 'contained' : 'outlined'} onClick={() => { setSelectedPreset(preset.id); setScheduleMode(preset.id === 'custom' ? 'custom' : 'preset'); }} sx={{ flex: 1, minHeight: 74, textTransform: 'none', justifyContent: 'flex-start', alignItems: 'flex-start', flexDirection: 'column', bgcolor: selectedPreset === preset.id ? '#FFF1F2' : '#fff', color: '#172033', borderColor: selectedPreset === preset.id ? '#C41E3A' : '#CBD5E1' }}><Typography sx={{ fontWeight: 900 }}>{preset.icon} {preset.label}</Typography><Typography variant="caption" sx={{ textAlign: 'left', mt: 0.25 }}>{preset.summary}</Typography></Button>)}
            </Stack>
          </>}
          {scheduleMode === 'custom' && <Stack spacing={2} sx={{ pt: 2 }}>
            {schedulableCourses.length > 1 ? <FormControl fullWidth size="small"><InputLabel id="study-course-label">Khóa học</InputLabel><Select labelId="study-course-label" label="Khóa học" value={newSlot.courseId} onChange={(event) => setNewSlot((current) => ({ ...current, courseId: event.target.value }))}>{schedulableCourses.map((course) => <MenuItem key={course.id} value={course.id}>{course.title}</MenuItem>)}</Select></FormControl> : <Alert severity="info" sx={{ py: 0.25 }}>{selectedCourse ? `Khóa học đang học: ${selectedCourse.title}` : 'Bạn chưa có khóa học còn thời hạn.'}</Alert>}
            <FormControl fullWidth size="small"><InputLabel id="study-skill-label">Kỹ năng</InputLabel><Select labelId="study-skill-label" label="Kỹ năng" value={newSlot.skill} onChange={(event) => setNewSlot((current) => ({ ...current, skill: event.target.value }))}>{SKILLS.map((skill) => <MenuItem key={skill.label} value={skill.label}>{skill.label}</MenuItem>)}</Select></FormControl>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormControl fullWidth size="small"><InputLabel id="study-day-label">Ngày</InputLabel><Select labelId="study-day-label" label="Ngày" value={newSlot.dayOfWeek} disabled={scheduleDayOfWeek !== null} onChange={(event) => setNewSlot((current) => ({ ...current, dayOfWeek: Number(event.target.value) }))}>{DAYS.map((day) => <MenuItem key={day.value} value={day.value}>{day.label}</MenuItem>)}</Select></FormControl><TextField size="small" label="Giờ bắt đầu" type="time" value={newSlot.startTime} onChange={(event) => setNewSlot((current) => ({ ...current, startTime: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} fullWidth /></Stack>
            <Box><Typography variant="caption" sx={{ color: '#667085', fontWeight: 800 }}>Thời lượng</Typography><Stack direction="row" spacing={1} sx={{ mt: 0.75, flexWrap: 'wrap' }}>{([25, 50, 60] as const).map((value) => <Button key={value} size="small" variant={durationChoice === value ? 'contained' : 'outlined'} aria-pressed={durationChoice === value} onClick={() => setDurationChoice(value)}>{value} phút{value === 25 ? ' (1 phiên)' : ''}</Button>)}<Button size="small" variant={durationChoice === 'custom' ? 'contained' : 'outlined'} aria-pressed={durationChoice === 'custom'} onClick={() => setDurationChoice('custom')}>Tùy chỉnh</Button></Stack>{durationChoice === 'custom' && <TextField size="small" type="number" label="Số phút" value={customDuration} onChange={(event) => setCustomDuration(Number(event.target.value))} slotProps={{ htmlInput: { min: 5, max: 180, step: 5 } }} sx={{ mt: 1, width: 140 }} />}</Box>
            {scheduleError && <Alert severity="warning" sx={{ py: 0.5 }}>{scheduleError}</Alert>}
          </Stack>}
          {scheduleDayOfWeek === null && <Alert severity="info" sx={{ mt: 2 }} icon={<TimerOutlinedIcon />}><Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}><Box><Typography variant="body2" sx={{ fontWeight: 800 }}>Gợi ý từ hệ thống</Typography><Typography variant="caption">Bạn có thể bắt đầu với 3 buổi/tuần, mỗi buổi 25 phút, rồi điều chỉnh theo lịch học và mục tiêu của mình.</Typography></Box><Button size="small" onClick={applySuggestion}>Áp dụng gợi ý này</Button></Stack></Alert>}
        </DialogContent>
        <DialogActions>{editingSlotId && <Button color="error" onClick={() => { deleteSlot(editingSlotId); setScheduleOpen(false); }} sx={{ mr: 'auto' }}>Xóa suất</Button>}<Button onClick={() => { setScheduleOpen(false); resetSlotEditor(); }}>Hủy</Button><Button variant="contained" onClick={addSchedule} sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#A71931' } }}>{editingSlotId ? 'Lưu thay đổi' : 'Lưu lịch'}</Button></DialogActions>
      </Dialog>

      <Dialog open={bulkOpen} onClose={() => setBulkOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Chỉnh lịch tổng</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: '#667085', mb: 1.5 }}>Chọn nhiều ca rồi đổi giờ hoặc thời lượng một lần. Các ngày và khóa học không bị thay đổi.</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
            <TextField fullWidth size="small" label="Tìm theo khóa học" value={bulkCourseSearch} onChange={(event) => setBulkCourseSearch(event.target.value)} />
            <FormControl fullWidth size="small"><InputLabel id="bulk-day-label">Ngày</InputLabel><Select labelId="bulk-day-label" label="Ngày" value={bulkDayFilter} onChange={(event) => setBulkDayFilter(event.target.value === 'all' ? 'all' : Number(event.target.value))}><MenuItem value="all">Tất cả các ngày</MenuItem>{DAYS.map((day) => <MenuItem key={day.value} value={day.value}>{day.label}</MenuItem>)}</Select></FormControl>
          </Stack>
          <Stack direction="row" spacing={0.75} sx={{ mt: 1, mb: 0.75, flexWrap: 'wrap' }}>
            <Button size="small" onClick={() => setBulkSelectedIds((previous) => ({ ...previous, ...Object.fromEntries(bulkVisibleSlots.map((slot) => [slot.id, true])) }))} sx={{ textTransform: 'none' }}>Chọn tất cả đang lọc</Button>
            <Button size="small" onClick={() => setBulkSelectedIds((previous) => ({ ...previous, ...Object.fromEntries(bulkVisibleSlots.map((slot) => [slot.id, false])) }))} sx={{ textTransform: 'none' }}>Bỏ chọn đang lọc</Button>
            <Typography variant="caption" sx={{ alignSelf: 'center', color: '#475467', fontWeight: 800 }}>{bulkSelectedCount}/{plan.slots.filter((slot) => slot.enabled).length} ca được chọn</Typography>
          </Stack>
          <Paper variant="outlined" sx={{ maxHeight: 260, overflowY: 'auto', p: 1 }}>
            {bulkVisibleSlots.length === 0 ? <Typography variant="body2" color="text.secondary" sx={{ p: 1, textAlign: 'center' }}>Không có ca phù hợp.</Typography> : <Stack spacing={0.25}>{bulkVisibleSlots.map((slot) => <FormControlLabel key={slot.id} control={<Checkbox size="small" checked={bulkSelectedIds[slot.id] === true} onChange={(event) => setBulkSelectedIds((previous) => ({ ...previous, [slot.id]: event.target.checked }))} />} label={<Typography variant="body2" noWrap>{DAYS.find((day) => day.value === slot.dayOfWeek)?.label} · {slot.startTime} · {slot.courseTitle || slot.skill}</Typography>} sx={{ mr: 0 }} />)}</Stack>}
          </Paper>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 900, mb: 1 }}>Thay đổi áp dụng cho các ca đã chọn</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
            <FormControl fullWidth size="small"><InputLabel id="bulk-time-action-label">Thao tác giờ</InputLabel><Select labelId="bulk-time-action-label" label="Thao tác giờ" value={bulkTimeAction} onChange={(event) => setBulkTimeAction(event.target.value as BulkTimeAction)}><MenuItem value="keep">Giữ nguyên giờ</MenuItem><MenuItem value="set">Đặt cùng một giờ</MenuItem><MenuItem value="shift">Dịch giờ hiện tại</MenuItem></Select></FormControl>
            {bulkTimeAction === 'set' && <TextField fullWidth size="small" label="Giờ mới" type="time" value={bulkStartTime} onChange={(event) => setBulkStartTime(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />}
            {bulkTimeAction === 'shift' && <FormControl fullWidth size="small"><InputLabel id="bulk-shift-label">Dịch bao nhiêu</InputLabel><Select labelId="bulk-shift-label" label="Dịch bao nhiêu" value={bulkShiftMinutes} onChange={(event) => setBulkShiftMinutes(Number(event.target.value))}><MenuItem value={-60}>Lùi 60 phút</MenuItem><MenuItem value={-30}>Lùi 30 phút</MenuItem><MenuItem value={-15}>Lùi 15 phút</MenuItem><MenuItem value={15}>Tiến 15 phút</MenuItem><MenuItem value={30}>Tiến 30 phút</MenuItem><MenuItem value={60}>Tiến 60 phút</MenuItem></Select></FormControl>}
            <FormControl fullWidth size="small"><InputLabel id="bulk-duration-label">Thời lượng</InputLabel><Select labelId="bulk-duration-label" label="Thời lượng" value={bulkDuration} onChange={(event) => setBulkDuration(event.target.value as BulkDurationChoice)}><MenuItem value="keep">Giữ nguyên thời lượng</MenuItem><MenuItem value={25}>25 phút</MenuItem><MenuItem value={50}>50 phút</MenuItem><MenuItem value={60}>60 phút</MenuItem><MenuItem value="custom">Tùy chỉnh</MenuItem></Select></FormControl>
          </Stack>
          {bulkDuration === 'custom' && <TextField size="small" type="number" label="Thời lượng mới (phút)" value={bulkCustomDuration} onChange={(event) => setBulkCustomDuration(Number(event.target.value))} slotProps={{ htmlInput: { min: 5, max: 180, step: 5 } }} sx={{ mt: 1.25, width: 220 }} />}
          {bulkError && <Alert severity="warning" sx={{ mt: 1.5 }}>{bulkError}</Alert>}
        </DialogContent>
        <DialogActions sx={{ justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
          <Button color="error" variant="outlined" disabled={bulkSelectedCount === 0} onClick={requestBulkDelete} sx={{ textTransform: 'none' }}>Xóa {bulkSelectedCount} ca</Button>
          <Stack direction="row" spacing={1}>
            <Button onClick={() => setBulkOpen(false)}>Hủy</Button>
            <Button variant="contained" disabled={bulkSelectedCount === 0 || !bulkHasChanges} onClick={applyBulkSchedule} sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#A71931' } }}>Áp dụng cho {bulkSelectedCount} ca</Button>
          </Stack>
        </DialogActions>
      </Dialog>

      <Dialog open={bulkDeleteConfirmOpen} onClose={() => setBulkDeleteConfirmOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Xóa ca học đã chọn?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">Bạn sắp xóa {bulkSelectedCount} ca học khỏi lịch cá nhân. Thao tác này không thể hoàn tác.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkDeleteConfirmOpen(false)}>Giữ lại</Button>
          <Button color="error" variant="contained" onClick={confirmBulkDelete}>Xóa ca học</Button>

        </DialogActions>
      </Dialog>
    </Paper>
  );
}
