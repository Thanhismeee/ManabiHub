import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT, STUDY_PLAN_OPEN_SCHEDULE_EVENT, STORAGE_KEY, StudyGoalsWidget, todayKey } from './StudyGoalsWidget';

describe('StudyGoalsWidget', () => {
  afterEach(cleanup);

  beforeEach(() => {
    window.localStorage.clear();
  });

  it('shows the goal progress and opens a schedule form', () => {
    render(<StudyGoalsWidget jlptGoal="N3" courses={[{ id: 'course-1', title: 'JLPT N3 thực chiến' }]} />);

    expect(screen.getByTestId('study-goals-widget')).toBeInTheDocument();
    expect(screen.getByText('JLPT N3')).toBeInTheDocument();
    expect(screen.getByText('Bạn chưa có lịch học cố định. Hãy thêm buổi học đầu tiên.')).toBeInTheDocument();
    expect(screen.getByText('Kanji & Từ vựng')).toBeInTheDocument();
    expect(screen.getAllByText('0/60 phút')).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: 'Thêm lịch học' }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Thêm lịch học', { selector: 'h2' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Tùy chỉnh/ }));
    expect(screen.getByText('Khóa học đang học: JLPT N3 thực chiến')).toBeInTheDocument();
  });

  it('supports inline weekly target editing and preset schedules', () => {
    render(<StudyGoalsWidget jlptGoal="N3" courses={[]} />);

    fireEvent.click(screen.getByRole('button', { name: 'Sửa mục tiêu tuần' }));
    const input = screen.getByDisplayValue('150');
    fireEvent.change(input, { target: { value: '180' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mục tiêu tuần' }));
    expect(screen.getByText('0/180 phút/tuần')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Thêm lịch học' }));
    fireEvent.click(screen.getByRole('button', { name: /Tiêu chuẩn/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu lịch' }));
    expect(JSON.parse(window.localStorage.getItem('manabihub.student.study-plan.v1') ?? '{}').slots).toHaveLength(5);
  });

  it('keeps the focus timer inside the learning page', () => {
    render(<StudyGoalsWidget jlptGoal="N3" courses={[]} />);

    expect(screen.queryByRole('button', { name: /Bắt đầu Pomodoro/i })).not.toBeInTheDocument();
    expect(screen.queryByTestId('pomodoro-timer')).not.toBeInTheDocument();
  });

  it('edits and deletes an existing conflicting schedule slot', () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today), weeklyTargetMinutes: 150,
      slots: [{ id: 'slot-existing', dayOfWeek: today.getDay(), startTime: '20:00', durationMinutes: 25, skill: 'Kanji & Từ vựng', courseId: 'course-1', courseTitle: 'JLPT N3 thực chiến', enabled: true }],
      focusTotals: {}, attendance: {},
    }));
    render(<StudyGoalsWidget jlptGoal="N3" courses={[{ id: 'course-1', title: 'JLPT N3 thực chiến' }]} />);

    fireEvent(window, new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, { detail: { slotId: 'slot-existing' } }));
    expect(screen.getByText('Sửa suất học', { selector: 'h2' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Giờ bắt đầu'), { target: { value: '21:15' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots[0].startTime).toBe('21:15');

    fireEvent(window, new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, { detail: { slotId: 'slot-existing' } }));
    fireEvent.click(screen.getByRole('button', { name: 'Xóa suất' }));
    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots).toHaveLength(0);
  });

  it('opens a selected-day editor and persists edits and deletions', async () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [{
        id: 'slot-1', dayOfWeek: today.getDay(), startTime: '19:00', durationMinutes: 25,
        skill: 'Kanji & Từ vựng', courseId: 'course-1', courseTitle: 'JLPT N3 thực chiến', enabled: true,
      }],
      focusTotals: {}, attendance: {},
    }));

    render(<StudyGoalsWidget courses={[{ id: 'course-1', title: 'JLPT N3 thực chiến' }]} />);
    window.dispatchEvent(new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, {
      detail: { dayOfWeek: today.getDay(), dateKey: todayKey(today) },
    }));

    await waitFor(() => expect(screen.getByTestId('schedule-day-editor')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Sửa 19:00' }));
    fireEvent.change(screen.getByLabelText('Giờ bắt đầu'), { target: { value: '21:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots[0].startTime).toBe('21:00');
    fireEvent.click(screen.getByRole('button', { name: 'Xóa 21:00' }));
    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots).toHaveLength(0);
  });

  it('blocks a new slot when the selected day already has an overlapping slot', async () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [{
        id: 'slot-1', dayOfWeek: today.getDay(), startTime: '19:00', durationMinutes: 25,
        skill: 'Kanji & Từ vựng', enabled: true,
      }],
      focusTotals: {}, attendance: {},
    }));

    render(<StudyGoalsWidget courses={[]} />);
    window.dispatchEvent(new CustomEvent(STUDY_PLAN_OPEN_SCHEDULE_EVENT, {
      detail: { dayOfWeek: today.getDay(), dateKey: todayKey(today) },
    }));
    await waitFor(() => expect(screen.getByTestId('schedule-day-editor')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Lưu lịch' }));

    expect(screen.getByText('Khung giờ này bị trùng với một ca khác. Hãy chọn giờ khác hoặc bấm Sửa ở ca đang có.')).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots).toHaveLength(1);
  });

  it('applies a time change to many selected slots at once', async () => {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(today.getDate() + 1);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-1', dayOfWeek: today.getDay(), startTime: '19:00', durationMinutes: 25, skill: 'Kanji & Từ vựng', enabled: true },
        { id: 'slot-2', dayOfWeek: tomorrow.getDay(), startTime: '20:00', durationMinutes: 25, skill: 'Ngữ pháp', enabled: true },
      ],
      focusTotals: {}, attendance: {},
    }));

    render(<StudyGoalsWidget courses={[]} />);
    window.dispatchEvent(new Event(STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Thao tác giờ' }));
    fireEvent.click(screen.getByRole('option', { name: 'Đặt cùng một giờ' }));
    fireEvent.change(screen.getByLabelText('Giờ mới'), { target: { value: '21:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng cho 2 ca' }));

    const slots = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots;
    expect(slots.map((slot: { startTime: string }) => slot.startTime)).toEqual(['21:00', '21:00']);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('deletes many selected slots after confirmation', async () => {
    const today = new Date();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
      weekKey: todayKey(today),
      weeklyTargetMinutes: 150,
      slots: [
        { id: 'slot-1', dayOfWeek: today.getDay(), startTime: '19:00', durationMinutes: 25, skill: 'Kanji & Từ vựng', enabled: true },
        { id: 'slot-2', dayOfWeek: (today.getDay() + 1) % 7, startTime: '20:00', durationMinutes: 25, skill: 'Ngữ pháp', enabled: true },
      ],
      focusTotals: {}, attendance: {},
    }));

    render(<StudyGoalsWidget courses={[]} />);
    window.dispatchEvent(new Event(STUDY_PLAN_OPEN_BULK_SCHEDULE_EVENT));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Xóa 2 ca' })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Xóa 2 ca' }));
    fireEvent.click(screen.getByRole('button', { name: 'Xóa ca học' }));

    await waitFor(() => expect(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}').slots).toHaveLength(0));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
