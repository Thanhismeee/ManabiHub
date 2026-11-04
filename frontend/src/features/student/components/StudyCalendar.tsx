import { useEffect, useMemo, useRef, useState } from 'react';
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
  FormControlLabel,
  IconButton,
  InputAdornment,

  Paper,
  Popover,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import ArrowBackIosNewOutlinedIcon from '@mui/icons-material/ArrowBackIosNewOutlined';
import ArrowForwardIosOutlinedIcon from '@mui/icons-material/ArrowForwardIosOutlined';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import EditCalendarOutlinedIcon from '@mui/icons-material/EditCalendarOutlined';
import ExpandLessRoundedIcon from '@mui/icons-material/ExpandLessRounded';
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded';

import PlayArrowOutlinedIcon from '@mui/icons-material/PlayArrowOutlined';
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import {
  readPlan,
  STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT,
  STUDY_PLAN_OPEN_SCHEDULE_EVENT,
  STUDY_PLAN_UPDATED_EVENT,
  type StudyCourseOption,
  type StudySlot,
} from './StudyGoalsWidget';
import { CALENDAR_PINS_UPDATED_EVENT, readPinnedCourseIds } from './studyCalendarPreferences';
import { isCourseAvailableOnDate, isSlotAvailableOnDate } from './studyScheduleAvailability';

type CalendarView = 'month' | 'week' | 'day';

interface StudyCalendarProps {
  courses?: StudyCourseOption[];
}

interface CalendarEvent {
  key: string;
  slot: StudySlot;
  date: Date;
  dateKey: string;
  color: string;
  startMinutes: number;
  endMinutes: number;
  conflict: boolean;
}

const WEEKDAY_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const LEVEL_COLORS: Record<string, string> = {
  N5: '#D97706',
  N4: '#2563EB',
  N3: '#2F855A',
  N2: '#9333EA',
  N1: '#C41E3A',
};
const UNASSIGNED_COURSE_KEY = '__unassigned__';
const MAX_DAY_EVENTS = 2;

function dateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, amount: number) {
  const result = new Date(date);
  result.setDate(result.getDate() + amount);
  return result;
}

function formatDate(date: Date, options: Intl.DateTimeFormatOptions = {}) {
  return new Intl.DateTimeFormat('vi-VN', options).format(date);
}

function slotCourseKey(slot: StudySlot) {
  return slot.courseId || (slot.courseTitle ? `title:${slot.courseTitle}` : UNASSIGNED_COURSE_KEY);
}

function slotMinutes(slot: StudySlot) {
  const [hour, minute] = slot.startTime.split(':').map(Number);
  return (hour || 0) * 60 + (minute || 0);
}

function courseColor(title: string) {
  const match = title.toUpperCase().match(/\bN([1-5])\b/);
  return match ? LEVEL_COLORS[`N${match[1]}`] : '#64748B';
}

function buildVisibleDates(cursor: Date, view: CalendarView) {
  const day = startOfDay(cursor);
  if (view === 'day') return [day];

  const mondayOffset = (day.getDay() + 6) % 7;
  const weekStart = view === 'week'
    ? addDays(day, -mondayOffset)
    : new Date(day.getFullYear(), day.getMonth(), 1);
  const firstDate = view === 'week'
    ? weekStart
    : addDays(weekStart, -((weekStart.getDay() + 6) % 7));
  const length = view === 'week' ? 7 : 42;
  return Array.from({ length }, (_, index) => addDays(firstDate, index));
}

function currentWeekDates(now = new Date()) {
  const day = startOfDay(now);
  const monday = addDays(day, -((day.getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
}

function buildEvents(slots: StudySlot[], dates: Date[], courses: StudyCourseOption[], colors: Record<string, string>) {
  if (dates.length === 0) return [];
  const first = dates[0];
  const last = dates[dates.length - 1];
  const events: CalendarEvent[] = [];
  for (let current = startOfDay(first); current <= last; current = addDays(current, 1)) {
    for (const slot of slots) {
      if (!slot.enabled || slot.dayOfWeek !== current.getDay()) continue;
      const startMinutes = slotMinutes(slot);
      const eventDate = startOfDay(current);
      eventDate.setHours(Math.floor(startMinutes / 60), startMinutes % 60, 0, 0);
      if (!isSlotAvailableOnDate(slot, eventDate, courses)) continue;
      events.push({
        key: `${slot.id}-${dateKey(eventDate)}`,
        slot,
        date: eventDate,
        dateKey: dateKey(eventDate),
        color: colors[slotCourseKey(slot)] || '#64748B',
        startMinutes,
        endMinutes: startMinutes + slot.durationMinutes,
        conflict: false,
      });
    }
  }
  return events.map((event) => ({
    ...event,
    conflict: events.some((other) => (
      other.key !== event.key
      && other.dateKey === event.dateKey
      && event.startMinutes < other.endMinutes
      && other.startMinutes < event.endMinutes
    )),
  }));
}

function formatTimeRange(event: CalendarEvent) {
  const endHour = Math.floor(event.endMinutes / 60) % 24;
  const endMinute = event.endMinutes % 60;
  return `${event.slot.startTime}–${String(endHour).padStart(2, '0')}:${String(endMinute).padStart(2, '0')}`;
}

export function StudyCalendar({ courses = [] }: StudyCalendarProps) {
  const navigate = useNavigate();
  const selectionInitialized = useRef(false);
  const [plan, setPlan] = useState(readPlan);
  const [view, setView] = useState<CalendarView>('week');
  const [expanded, setExpanded] = useState(true);
  const [cursor, setCursor] = useState(() => startOfDay(new Date()));
  const [selectedDay, setSelectedDay] = useState<Date | null>(null);
  const [selectedCourses, setSelectedCourses] = useState<Record<string, boolean>>({});
  const [courseFilterAnchorEl, setCourseFilterAnchorEl] = useState<HTMLElement | null>(null);
  const [courseSearch, setCourseSearch] = useState('');
  const [pinnedCourseIds, setPinnedCourseIds] = useState(readPinnedCourseIds);

  useEffect(() => {
    const refresh = () => setPlan(readPlan());
    window.addEventListener(STUDY_PLAN_UPDATED_EVENT, refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener(STUDY_PLAN_UPDATED_EVENT, refresh);
      window.removeEventListener('storage', refresh);
    };
  }, []);

  useEffect(() => {
    const refreshPins = () => setPinnedCourseIds(readPinnedCourseIds());
    window.addEventListener(CALENDAR_PINS_UPDATED_EVENT, refreshPins);
    window.addEventListener('storage', refreshPins);
    return () => {
      window.removeEventListener(CALENDAR_PINS_UPDATED_EVENT, refreshPins);
      window.removeEventListener('storage', refreshPins);
    };
  }, []);

  const courseOptions = useMemo(() => {
    const options = new Map<string, StudyCourseOption>();
    courses.forEach((course) => options.set(course.id, course));
    plan.slots.forEach((slot) => {
      const key = slotCourseKey(slot);
      if (!options.has(key)) options.set(key, { id: key, title: slot.courseTitle || 'Khóa học chưa chọn' });
    });
    return Array.from(options.values());
  }, [courses, plan.slots]);

  const thisWeekCourseIds = useMemo(() => {
    const ids = new Set<string>();
    for (const date of currentWeekDates()) {
      plan.slots.forEach((slot) => {
        const occurrence = new Date(date);
        const minutes = slotMinutes(slot);
        occurrence.setHours(Math.floor(minutes / 60), minutes % 60, 0, 0);
        if (slot.enabled && slot.dayOfWeek === date.getDay() && isSlotAvailableOnDate(slot, occurrence, courses)) {
          ids.add(slotCourseKey(slot));
        }
      });
    }
    return ids;
  }, [courses, plan.slots]);

  useEffect(() => {
    if (selectionInitialized.current || courseOptions.length === 0) return;
    const validPins = courseOptions.filter((course) => pinnedCourseIds.has(course.id) && isCourseAvailableOnDate(course, new Date()));
    const defaultIds = validPins.length > 0
      ? new Set(validPins.map((course) => course.id))
      : thisWeekCourseIds.size > 0
        ? thisWeekCourseIds
        : new Set(courseOptions.filter((course) => isCourseAvailableOnDate(course, new Date())).map((course) => course.id));
    setSelectedCourses(Object.fromEntries(courseOptions.map((course) => [course.id, defaultIds.has(course.id)])));
    selectionInitialized.current = true;
  }, [courseOptions, pinnedCourseIds, thisWeekCourseIds]);

  const colors = useMemo(() => Object.fromEntries(
    courseOptions.map((option) => [option.id, courseColor(option.title)]),
  ), [courseOptions]);
  const filteredCourseOptions = courseOptions.filter((option) => option.title.toLocaleLowerCase('vi-VN').includes(courseSearch.trim().toLocaleLowerCase('vi-VN')));
  const levelLegend = ['N5', 'N4', 'N3', 'N2', 'N1'].filter((level) => courseOptions.some((option) => new RegExp(`\\b${level}\\b`, 'i').test(option.title)));
  const selectedCourseCount = courseOptions.filter((option) => selectedCourses[option.id] !== false).length;
  const visibleDates = useMemo(() => buildVisibleDates(cursor, view), [cursor, view]);
  const events = useMemo(() => buildEvents(plan.slots, visibleDates, courses, colors), [courses, plan.slots, visibleDates, colors]);
  const filteredEvents = events.filter((event) => selectedCourses[slotCourseKey(event.slot)] === true);
  const selectedDayKey = selectedDay ? dateKey(selectedDay) : null;
  const selectedDayEvents = filteredEvents.filter((event) => event.dateKey === selectedDayKey)
    .sort((first, second) => first.startMinutes - second.startMinutes);
  const title = view === 'day'
    ? formatDate(cursor, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
    : view === 'week'
      ? `${formatDate(visibleDates[0], { day: 'numeric', month: 'short' })} – ${formatDate(visibleDates[6], { day: 'numeric', month: 'short', year: 'numeric' })}`
      : formatDate(cursor, { month: 'long', year: 'numeric' });


  const moveCursor = (amount: number) => {
    const next = new Date(cursor);
    if (view === 'month') next.setMonth(next.getMonth() + amount);
    else next.setDate(next.getDate() + (view === 'week' ? amount * 7 : amount));
    setCursor(startOfDay(next));
  };

  const openSchedule = () => {
    const targetDay = selectedDay;
    const dayOfWeek = targetDay?.getDay() ?? new Date().getDay();
    setSelectedDay(null);
    window.dispatchEvent(new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, {
      detail: {
        dayOfWeek,
        dateKey: targetDay ? dateKey(targetDay) : undefined,
      },
    }));
  };

  const editSchedule = (event: CalendarEvent) => {
    setSelectedDay(null);
    window.dispatchEvent(new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, { detail: { slotId: event.slot.id } }));
  };

  const startLesson = (event: CalendarEvent) => {
    if (!event.slot.courseId) return;
    setSelectedDay(null);
    navigate(ROUTES.STUDENT.COURSE_LEARN(event.slot.courseId));
  };

  const renderEvent = (event: CalendarEvent, compact = false) => (
    <Box
      key={event.key}
      data-testid={`calendar-event-${event.key}`}
      sx={{
        px: compact ? 0.65 : 0.75,
        py: compact ? 0.35 : 0.5,
        borderRadius: compact ? 999 : 1,
        bgcolor: `${event.color}18`,
        border: `1px solid ${event.color}35`,
        borderLeft: compact ? undefined : `3px solid ${event.color}`,
        minWidth: 0,
        display: compact ? 'flex' : 'block',
        alignItems: compact ? 'center' : undefined,
        gap: compact ? 0.5 : undefined,
      }}
    >
      {compact && <Box aria-hidden="true" sx={{ width: 7, height: 7, flexShrink: 0, borderRadius: '50%', bgcolor: event.color }} />}
      <Typography variant="caption" sx={{ display: 'block', color: event.color, fontWeight: 900, lineHeight: 1.2 }}>
        {event.slot.startTime} {event.conflict && '⚠'}
      </Typography>
      {!compact && <Typography variant="caption" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#344054' }}>
        {event.slot.courseTitle || event.slot.skill}
      </Typography>}
      {compact && <Typography variant="caption" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#344054', fontWeight: 700 }}>
        {event.slot.courseTitle || event.slot.skill}
      </Typography>}
    </Box>
  );

  return (
    <Paper data-testid="study-calendar" elevation={0} sx={{ p: { xs: 1.5, sm: 2.5 }, border: '1px solid #E4E7EC', borderRadius: '8px', bgcolor: '#fff' }}>
      <Stack direction={{ xs: 'column', md: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 1.5 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <CalendarMonthOutlinedIcon sx={{ color: '#C41E3A' }} />
          <Box>
            <Typography variant="h6" sx={{ color: '#172033', fontWeight: 900 }}>Lịch học của bạn</Typography>
            <Typography variant="caption" color="text.secondary">Lịch chỉ xuất hiện trong thời gian bạn còn quyền truy cập khóa học.</Typography>
          </Box>
        </Stack>
        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
          <Button size="small" variant="outlined" startIcon={<EditCalendarOutlinedIcon />} onClick={() => window.dispatchEvent(new Event(STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT))} sx={{ textTransform: 'none', borderColor: '#CBD5E1', color: '#475569' }}>Chỉnh lịch tổng</Button>
          <Button size="small" variant="contained" startIcon={<AddOutlinedIcon />} onClick={openSchedule} sx={{ textTransform: 'none', bgcolor: '#C41E3A', '&:hover': { bgcolor: '#A71931' } }}>Thêm suất học</Button>
          <IconButton
            size="small"
            aria-label={expanded ? 'Thu gọn lịch học' : 'Mở rộng lịch học'}
            onClick={() => setExpanded((current) => !current)}
            sx={{ ml: 0.5, color: '#667085' }}
          >
            {expanded ? <ExpandLessRoundedIcon /> : <ExpandMoreRoundedIcon />}
          </IconButton>
        </Stack>
      </Stack>

      {expanded && <>
      <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1, mt: 2 }}>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', minWidth: 0 }}>
          <IconButton size="small" aria-label="Lịch trước" onClick={() => moveCursor(-1)}><ArrowBackIosNewOutlinedIcon sx={{ fontSize: 15 }} /></IconButton>
          <Typography variant="subtitle1" sx={{ minWidth: { xs: 0, sm: 190 }, flex: { xs: 1, sm: 'initial' }, textAlign: 'center', fontWeight: 900, textTransform: 'capitalize' }}>{title}</Typography>
          <IconButton size="small" aria-label="Lịch sau" onClick={() => moveCursor(1)}><ArrowForwardIosOutlinedIcon sx={{ fontSize: 15 }} /></IconButton>
          <Button size="small" onClick={() => { setCursor(startOfDay(new Date())); setView('day'); }} sx={{ textTransform: 'none', whiteSpace: 'nowrap' }}>Hôm nay</Button>
        </Stack>
        <ToggleButtonGroup
          exclusive
          size="small"
          color="primary"
          value={view}
          onChange={(_, nextView: CalendarView | null) => { if (nextView) setView(nextView); }}
          aria-label="Chế độ xem lịch"
          sx={{ alignSelf: { xs: 'stretch', sm: 'auto' }, '& .MuiToggleButton-root': { textTransform: 'none', px: 1.5, borderColor: '#CBD5E1', color: '#475569' }, '& .Mui-selected': { color: '#fff !important', bgcolor: '#C41E3A !important' } }}
        >
          <ToggleButton value="month">Tháng</ToggleButton>
          <ToggleButton value="week">Tuần</ToggleButton>
          <ToggleButton value="day">Ngày</ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {courseOptions.length > 0 && (
        <>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.5, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
            <Button
              variant="outlined"
              onClick={(event) => setCourseFilterAnchorEl(event.currentTarget)}
              aria-haspopup="dialog"
              aria-expanded={Boolean(courseFilterAnchorEl)}
              data-testid="calendar-course-filter"
              sx={{ textTransform: 'none', borderColor: '#CBD5E1', color: '#334155', justifyContent: 'space-between', minWidth: { sm: 260 } }}
            >
              📚 Lọc khóa học ({selectedCourseCount}/{courseOptions.length}) ▾
            </Button>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
              <Typography variant="caption" color="text.secondary" sx={{ mr: 0.25 }}>Màu theo cấp độ:</Typography>
              {levelLegend.map((level) => <Chip key={level} size="small" label={level} sx={{ height: 22, color: LEVEL_COLORS[level], bgcolor: `${LEVEL_COLORS[level]}15`, border: `1px solid ${LEVEL_COLORS[level]}45`, fontWeight: 800 }} />)}
            </Stack>
          </Stack>
          <Popover
            open={Boolean(courseFilterAnchorEl)}
            anchorEl={courseFilterAnchorEl}
            onClose={() => { setCourseFilterAnchorEl(null); setCourseSearch(''); }}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
            slotProps={{ paper: { sx: { p: 1.5, width: { xs: 300, sm: 380 }, maxWidth: 'calc(100vw - 32px)' } } }}
          >
            <TextField
              autoFocus
              fullWidth
              size="small"
              value={courseSearch}
              onChange={(event) => setCourseSearch(event.target.value)}
              placeholder="Tìm khóa học..."
              slotProps={{
                input: { startAdornment: <InputAdornment position="start"><SearchOutlinedIcon fontSize="small" /></InputAdornment> },
                htmlInput: { 'aria-label': 'Tìm khóa học' },
              }}
            />
            <Stack direction="row" spacing={0.5} sx={{ mt: 1, mb: 0.75 }}>
              <Button size="small" onClick={() => setSelectedCourses(Object.fromEntries(courseOptions.map((option) => [option.id, true])))} sx={{ textTransform: 'none' }}>Chọn tất cả</Button>
              <Button size="small" onClick={() => setSelectedCourses(Object.fromEntries(courseOptions.map((option) => [option.id, false])))} sx={{ textTransform: 'none' }}>Bỏ chọn hết</Button>
            </Stack>
            <Divider />
            <Box sx={{ maxHeight: 280, overflowY: 'auto', mt: 0.5 }}>
              {filteredCourseOptions.map((option) => {
                const isPinned = pinnedCourseIds.has(option.id);
                return (
                  <FormControlLabel
                    key={option.id}
                    control={<Checkbox size="small" slotProps={{ input: { 'aria-label': option.title } }} checked={selectedCourses[option.id] !== false} onChange={(event) => setSelectedCourses((previous) => ({ ...previous, [option.id]: event.target.checked }))} sx={{ color: colors[option.id], '&.Mui-checked': { color: colors[option.id] } }} />}
                    label={
                      <Box sx={{ display: 'flex', alignItems: 'center', minWidth: 0, flex: 1 }}>
                        <Typography variant="body2" noWrap sx={{ color: '#475467', flex: 1, minWidth: 0 }}>{option.title}</Typography>
                        {isPinned && <Chip size="small" label="Đã ghim" sx={{ ml: 1, height: 22 }} />}
                      </Box>
                    }
                    sx={{ display: 'flex', mr: 0, my: 0.15 }}
                  />
                );
              })}
              {filteredCourseOptions.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>Không tìm thấy khóa học.</Typography>}
            </Box>
          </Popover>
        </>
      )}

      {view === 'day' ? (
        <Stack spacing={1} sx={{ mt: 2 }}>
          {filteredEvents.filter((event) => event.dateKey === dateKey(cursor)).sort((first, second) => first.startMinutes - second.startMinutes).map((event) => (
            <Box key={event.key} sx={{ p: 1.25, border: '1px solid #E4E7EC', borderLeft: `4px solid ${event.color}`, borderRadius: 1 }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 1 }}>
                <Box><Typography variant="body2" sx={{ fontWeight: 900 }}>{formatTimeRange(event)} · {event.slot.skill}</Typography><Typography variant="caption" color="text.secondary">{event.slot.courseTitle || 'Khóa học chưa chọn'}</Typography></Box>
                <Button size="small" variant="contained" startIcon={<PlayArrowOutlinedIcon />} disabled={!event.slot.courseId} onClick={() => startLesson(event)} sx={{ alignSelf: { sm: 'center' }, bgcolor: '#C41E3A', textTransform: 'none' }}>Vào học ngay</Button>
              </Stack>
            </Box>
          ))}
          {filteredEvents.filter((event) => event.dateKey === dateKey(cursor)).length === 0 && <Alert severity="info" sx={{ mt: 1 }}>Ngày này chưa có suất học. Bạn có thể thêm lịch ngay.</Alert>}
        </Stack>
      ) : (
        <Box sx={{ mt: 2, overflowX: 'auto' }}>
          <Box sx={{ minWidth: view === 'month' ? 650 : 760 }}>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, minmax(0, 1fr))', borderTop: '1px solid #E4E7EC', borderLeft: '1px solid #E4E7EC' }}>
              {WEEKDAY_LABELS.map((label) => <Typography key={label} variant="caption" sx={{ p: 0.75, textAlign: 'center', color: '#667085', fontWeight: 900, borderRight: '1px solid #E4E7EC', borderBottom: '1px solid #E4E7EC' }}>{label}</Typography>)}
              {visibleDates.map((date) => {
                const dayEvents = filteredEvents.filter((event) => event.dateKey === dateKey(date));
                const isCurrentMonth = date.getMonth() === cursor.getMonth();
                const isToday = dateKey(date) === dateKey(new Date());
                return (
                  <Box
                    component="button"
                    type="button"
                    key={dateKey(date)}
                    data-testid={`calendar-day-${dateKey(date)}`}
                    onClick={() => setSelectedDay(date)}
                    sx={{ minHeight: view === 'month' ? 86 : 150, p: 0.75, textAlign: 'left', verticalAlign: 'top', border: 0, borderRight: '1px solid #E4E7EC', borderBottom: '1px solid #E4E7EC', bgcolor: isToday ? '#FFF7F8' : '#fff', opacity: view === 'month' && !isCurrentMonth ? 0.5 : 1, cursor: 'pointer', '&:hover': { bgcolor: '#FFF7F8' } }}
                  >
                    <Typography variant="caption" sx={{ display: 'inline-flex', width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: '50%', bgcolor: isToday ? '#C41E3A' : 'transparent', color: isToday ? '#fff' : '#344054', fontWeight: 900 }}>{date.getDate()}</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {dayEvents.slice(0, MAX_DAY_EVENTS).map((event) => renderEvent(event, view === 'month'))}
                      {dayEvents.length > MAX_DAY_EVENTS && <Typography variant="caption" color="text.secondary">+{dayEvents.length - MAX_DAY_EVENTS} ca khác</Typography>}
                    </Stack>
                  </Box>
                );
              })}
            </Box>
          </Box>
        </Box>
      )}

        {filteredEvents.some((event) => event.conflict) && <Alert severity="warning" icon={<WarningAmberOutlinedIcon />} sx={{ mt: 1.5, py: 0.25 }}>Có suất học bị trùng giờ. Bấm vào ngày, chọn “Sửa” để đổi giờ hoặc xóa suất.</Alert>}
      </>}

      <Dialog open={Boolean(selectedDay)} onClose={() => setSelectedDay(null)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
          Lịch học ngày {selectedDay ? formatDate(selectedDay, { day: '2-digit', month: '2-digit', year: 'numeric' }) : ''}
          <IconButton aria-label="Đóng" size="small" onClick={() => setSelectedDay(null)}><CloseRoundedIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {selectedDayEvents.length === 0 ? <Alert severity="info">Ngày này chưa có suất học trong thời hạn khóa.</Alert> : <Stack spacing={1.25}>{selectedDayEvents.map((event) => <Box key={event.key} sx={{ p: 1.25, border: '1px solid #E4E7EC', borderLeft: `4px solid ${event.color}`, borderRadius: 1 }}><Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 1 }}><Box><Typography variant="body2" sx={{ fontWeight: 900 }}>{formatTimeRange(event)} · {event.slot.skill}</Typography><Typography variant="caption" color="text.secondary">{event.slot.courseTitle || 'Khóa học chưa chọn'}</Typography><Typography variant="caption" sx={{ display: 'block', mt: 0.35, color: '#475467', fontWeight: 700 }}>Dự kiến: {event.slot.lessonTitle || 'tiếp tục bài học gần nhất'}</Typography></Box><Stack direction="row" spacing={0.5} sx={{ alignSelf: { sm: 'center' } }}><Button size="small" startIcon={<EditOutlinedIcon />} onClick={() => editSchedule(event)}>Sửa</Button><Button size="small" variant="contained" startIcon={<PlayArrowOutlinedIcon />} disabled={!event.slot.courseId} onClick={() => startLesson(event)} sx={{ bgcolor: '#C41E3A', textTransform: 'none' }}>Vào học ngay</Button></Stack></Stack>{event.conflict && <Chip size="small" color="warning" icon={<WarningAmberOutlinedIcon />} label="Trùng lịch" sx={{ mt: 0.75 }} />}</Box>)}</Stack>}
        </DialogContent>
        <DialogActions><Button variant="outlined" startIcon={<AddOutlinedIcon />} onClick={openSchedule} sx={{ textTransform: 'none', borderColor: '#CBD5E1', color: '#475569' }}>Thêm suất học ngày này</Button></DialogActions>
      </Dialog>
    </Paper>
  );
}
