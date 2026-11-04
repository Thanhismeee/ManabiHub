import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { CoursePomodoroPanel } from './CoursePomodoroPanel';

describe('CoursePomodoroPanel', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('starts a compact focus session in the active course', () => {
    render(<CoursePomodoroPanel courseTitle="Kanji N5 nền tảng" />);

    expect(screen.getByTestId('course-pomodoro-panel')).toBeInTheDocument();
    expect(screen.getByText('Pomodoro trong bài học')).toBeInTheDocument();
    expect(screen.getByText('Tập trung cho: Kanji N5 nền tảng')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }));

    expect(screen.getByRole('button', { name: 'Tạm dừng' })).toBeInTheDocument();
    expect(screen.getByText('Đang tập trung')).toBeInTheDocument();
  });
});
