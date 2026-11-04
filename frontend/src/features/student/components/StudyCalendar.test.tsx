import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { StudyCalendar } from './StudyCalendar';
import { STORAGE_KEY, todayKey, type StudyCourseOption } from './StudyGoalsWidget';

function renderCalendar(courses: StudyCourseOption[] = [{ id: 'course-kanji', title: 'Kanji N5 nền tảng' }, { id: 'course-kaiwa', title: 'Giao tiếp tiếng Nhật N4' }]) {
  return render(
    <MemoryRouter initialEntries={['/student/dashboard']}>
      <StudyCalendar courses={courses} />
    </MemoryRouter>,
  );
}

describe('StudyCalendar', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    window.localStorage.clear();
  });

  it('renders month/week/day controls and opens a day agenda', () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [{
        id: 'slot-kanji', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 25,
        skill: 'Kanji & Từ vựng', courseId: 'course-kanji', courseTitle: 'Kanji N5 nền tảng',
        lessonTitle: 'Bài 3: Bộ thủ cơ bản', enabled: true,
      }],
      focusTotals: {}, attendance: {},
    }));

    renderCalendar();

    expect(screen.getByTestId('study-calendar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tháng' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tuần' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Hôm nay' }).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByTestId('calendar-course-filter'));
    expect(screen.getByRole('checkbox', { name: 'Kanji N5 nền tảng' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Giao tiếp tiếng Nhật N4' })).not.toBeChecked();
    expect(screen.getByPlaceholderText('Tìm khóa học...')).toBeInTheDocument();
    expect(screen.getByTestId(`calendar-event-slot-kanji-${todayKey(today)}`)).toHaveTextContent('20:00');

    fireEvent.click(screen.getByTestId(`calendar-day-${todayKey(today)}`));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByText('Kanji N5 nền tảng')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByText('Dự kiến: Bài 3: Bộ thủ cơ bản')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Vào học ngay' })).toBeInTheDocument();
  });

  it('marks overlapping slots as conflicts and supports course filters', async () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-kanji', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 50, skill: 'Kanji & Từ vựng', courseId: 'course-kanji', courseTitle: 'Kanji N5 nền tảng', enabled: true },
        { id: 'slot-kaiwa', dayOfWeek: today.getDay(), startTime: '20:25', durationMinutes: 25, skill: 'Ngữ pháp', courseId: 'course-kaiwa', courseTitle: 'Giao tiếp tiếng Nhật', enabled: true },
      ],
      focusTotals: {}, attendance: {},
    }));

    renderCalendar();
    fireEvent.click(screen.getByTestId(`calendar-day-${todayKey(today)}`));
    expect(screen.getAllByText('Trùng lịch').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByTestId('calendar-course-filter'));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Kanji N5 nền tảng' }));
    fireEvent.click(screen.getByTestId(`calendar-day-${todayKey(today)}`));
    expect(within(screen.getByRole('dialog')).getByText('Giao tiếp tiếng Nhật')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).queryByText('Kanji N5 nền tảng')).not.toBeInTheDocument();
  });

  it('limits each calendar cell to two tags and groups the rest', () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today), weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-1', dayOfWeek: today.getDay(), startTime: '18:00', durationMinutes: 25, skill: 'Kanji', courseId: 'course-kanji', courseTitle: 'Kanji N5 nền tảng', enabled: true },
        { id: 'slot-2', dayOfWeek: today.getDay(), startTime: '19:00', durationMinutes: 25, skill: 'Ngữ pháp', courseId: 'course-kaiwa', courseTitle: 'Giao tiếp tiếng Nhật N4', enabled: true },
        { id: 'slot-3', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 25, skill: 'Đọc hiểu', courseId: 'course-third', courseTitle: 'Luyện đề JLPT N3', enabled: true },
      ], focusTotals: {}, attendance: {},
    }));

    renderCalendar([
      { id: 'course-kanji', title: 'Kanji N5 nền tảng' },
      { id: 'course-kaiwa', title: 'Giao tiếp tiếng Nhật N4' },
      { id: 'course-third', title: 'Luyện đề JLPT N3' },
    ]);

    expect(screen.getByTestId(`calendar-event-slot-1-${todayKey(today)}`)).toBeInTheDocument();
    expect(screen.getByTestId(`calendar-event-slot-2-${todayKey(today)}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`calendar-event-slot-3-${todayKey(today)}`)).not.toBeInTheDocument();
    expect(screen.getByText('+1 ca khác')).toBeInTheDocument();
  });

  it('does not repeat a schedule before enrollment or after course expiry', () => {
    const today = new Date();
    const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
    const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
    const nextWeek = new Date(today); nextWeek.setDate(today.getDate() + 7);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today), weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-expired', dayOfWeek: today.getDay(), startTime: '18:00', durationMinutes: 25, skill: 'Kanji', courseId: 'expired', courseTitle: 'Kanji N5 đã hết hạn', enabled: true },
        { id: 'slot-active', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 25, skill: 'Kanji', courseId: 'active', courseTitle: 'Kanji N5 đang học', enabled: true },
        { id: 'slot-future', dayOfWeek: today.getDay(), startTime: '21:00', durationMinutes: 25, skill: 'Kanji', courseId: 'future', courseTitle: 'Kanji N5 chưa bắt đầu', enabled: true },
      ], focusTotals: {}, attendance: {},
    }));

    renderCalendar([
      { id: 'expired', title: 'Kanji N5 đã hết hạn', enrollmentStatus: 'EXPIRED', enrolledAt: '2026-01-01T00:00:00Z', expiresAt: yesterday.toISOString() },
      { id: 'active', title: 'Kanji N5 đang học', enrollmentStatus: 'ACTIVE', enrolledAt: yesterday.toISOString(), expiresAt: tomorrow.toISOString() },
      { id: 'future', title: 'Kanji N5 chưa bắt đầu', enrollmentStatus: 'ACTIVE', enrolledAt: nextWeek.toISOString(), expiresAt: null },
    ]);

    expect(screen.queryByTestId(`calendar-event-slot-expired-${todayKey(today)}`)).not.toBeInTheDocument();
    expect(screen.getByTestId(`calendar-event-slot-active-${todayKey(today)}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`calendar-event-slot-future-${todayKey(today)}`)).not.toBeInTheDocument();
  });

  it('ignores expired pinned courses and falls back to active week courses', () => {
    const today = new Date();
    const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
    const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
    window.localStorage.setItem('manabihub.student.calendar-pins.v1', JSON.stringify(['expired']));
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today), weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-active', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 25, skill: 'Kanji', courseId: 'active', courseTitle: 'Kanji N5 đang học', enabled: true },
      ], focusTotals: {}, attendance: {},
    }));

    renderCalendar([
      { id: 'expired', title: 'Kanji N5 đã hết hạn', enrollmentStatus: 'EXPIRED', enrolledAt: '2026-01-01T00:00:00Z', expiresAt: yesterday.toISOString() },
      { id: 'active', title: 'Kanji N5 đang học', enrollmentStatus: 'ACTIVE', enrolledAt: yesterday.toISOString(), expiresAt: tomorrow.toISOString() },
    ]);

    expect(screen.getByTestId(`calendar-event-slot-active-${todayKey(today)}`)).toBeInTheDocument();
  });
});
