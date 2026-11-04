import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { StudentCourseCard } from './StudentCourseCard';
import type { StudentCourseSummary } from '../types/studentTypes';

const course: StudentCourseSummary = {
  enrollmentId: 'enrollment-1',
  courseId: 'course-1',
  courseTitle: 'Kanji N5 nền tảng',
  thumbnailUrl: null,
  teacherName: 'Sato Sensei',
  enrollmentStatus: 'ACTIVE',
  enrolledAt: '2026-08-10T00:00:00Z',
  progressPercentage: 25,
};

describe('StudentCourseCard', () => {
  afterEach(cleanup);
  beforeEach(() => window.localStorage.clear());

  it('uses a real fallback cover when a course has no thumbnail', () => {
    render(<MemoryRouter><StudentCourseCard course={course} /></MemoryRouter>);

    const cover = screen.getByTestId('course-cover-fallback');
    expect(cover).toHaveAttribute('src');
    expect(cover).toHaveAttribute('alt', 'Ảnh mặc định cho Kanji N5 nền tảng');
  });

  it('falls back when the remote thumbnail cannot load', () => {
    render(<MemoryRouter><StudentCourseCard course={{ ...course, thumbnailUrl: '/missing-cover.png' }} /></MemoryRouter>);

    fireEvent.error(screen.getByTestId('course-cover'));
    expect(screen.getByTestId('course-cover-fallback')).toBeInTheDocument();
  });

  it('lets the student pin an active course to the overview calendar', () => {
    render(<MemoryRouter><StudentCourseCard course={course} showCalendarPin /></MemoryRouter>);

    fireEvent.click(screen.getByRole('switch', { name: 'Ghim lịch Kanji N5 nền tảng' }));
    expect(JSON.parse(window.localStorage.getItem('manabihub.student.calendar-pins.v1') ?? '[]')).toContain('course-1');
    expect(screen.getByText('Đã ghim lên lịch')).toBeInTheDocument();
  });
});
