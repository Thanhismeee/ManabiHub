import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { finalTestService, type FinalTestConfig } from '../services/finalTestService';
import { FinalTestConfigPage } from './FinalTestConfigPage';

vi.mock('../services/finalTestService', () => ({
  finalTestService: {
    getFinalTest: vi.fn(),
    updateFinalTest: vi.fn(),
  },
}));

vi.mock('./FinalTestQuestionsEditor', () => ({
  FinalTestQuestionsEditor: () => <div>Danh sách câu hỏi hợp lệ</div>,
}));

const initialConfig = createConfig(60);

beforeEach(() => {
  vi.mocked(finalTestService.getFinalTest).mockResolvedValue(initialConfig);
  vi.mocked(finalTestService.updateFinalTest).mockImplementation(async (courseId, request) => ({
    ...request,
    courseId,
    id: 'final-test-1',
  }));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('FinalTestConfigPage save flow', () => {
  it('does not send a second PUT when Save & Continue already persisted every change', async () => {
    renderPage();

    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Thời gian làm bài (phút)' }), {
      target: { value: '75' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu & Tiếp tục' }));

    expect(await screen.findByText('Lưu cấu hình thành công!')).toBeInTheDocument();
    await waitFor(() => expect(finalTestService.updateFinalTest).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'Lưu & Thoát' }));

    expect(await screen.findByText('Trang danh sách khóa học')).toBeInTheDocument();
    expect(finalTestService.updateFinalTest).toHaveBeenCalledTimes(1);
  });

  it('confirms persisted data and exits when the save response is lost after the commit', async () => {
    const persistedConfig = createConfig(75);
    vi.mocked(finalTestService.getFinalTest)
      .mockResolvedValueOnce(initialConfig)
      .mockResolvedValueOnce(persistedConfig);
    vi.mocked(finalTestService.updateFinalTest).mockRejectedValueOnce(new Error('Network Error'));

    renderPage();

    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Thời gian làm bài (phút)' }), {
      target: { value: '75' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu & Thoát' }));

    expect(await screen.findByText('Trang danh sách khóa học')).toBeInTheDocument();
    expect(finalTestService.updateFinalTest).toHaveBeenCalledTimes(1);
    expect(finalTestService.getFinalTest).toHaveBeenCalledTimes(2);
  });

  it('keeps the editor open when the backend explicitly rejects the save', async () => {
    vi.mocked(finalTestService.updateFinalTest).mockRejectedValueOnce({
      response: { data: { message: 'Khóa học không còn ở trạng thái cho phép chỉnh sửa.' } },
    });

    renderPage();

    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Thời gian làm bài (phút)' }), {
      target: { value: '75' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu & Thoát' }));

    expect(await screen.findByText('Khóa học không còn ở trạng thái cho phép chỉnh sửa.')).toBeInTheDocument();
    expect(screen.queryByText('Trang danh sách khóa học')).not.toBeInTheDocument();
    expect(finalTestService.getFinalTest).toHaveBeenCalledTimes(1);
  });

  it('rejects a zero passing score before calling the backend', async () => {
    renderPage();

    fireEvent.change(await screen.findByRole('spinbutton', { name: 'Điểm đạt (%)' }), {
      target: { value: '0' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu & Tiếp tục' }));

    expect(await screen.findByText('Vui lòng nhập điểm đạt (từ 1 đến 100%).')).toBeInTheDocument();
    expect(finalTestService.updateFinalTest).not.toHaveBeenCalled();
  });
});

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/teacher/courses/course-1/final-test']}>
      <Routes>
        <Route path="/teacher/courses/:courseId/final-test" element={<FinalTestConfigPage />} />
        <Route path="/teacher/courses" element={<div>Trang danh sách khóa học</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function createConfig(timeLimitMinutes: number): FinalTestConfig {
  return {
    id: 'final-test-1',
    courseId: 'course-1',
    timeLimitMinutes,
    passingScore: 70,
    maxRetakes: 2,
    jlptLevel: 'N3',
    skillFocus: 'Tổng hợp',
    questions: Array.from({ length: 20 }, (_, index) => ({
      id: `question-${index + 1}`,
      content: `Câu hỏi ${index + 1}`,
      explanation: `Giải thích ${index + 1}`,
      choices: [
        { id: `choice-${index + 1}-1`, content: 'Đáp án đúng', isCorrect: true },
        { id: `choice-${index + 1}-2`, content: 'Đáp án sai', isCorrect: false },
      ],
    })),
  };
}
