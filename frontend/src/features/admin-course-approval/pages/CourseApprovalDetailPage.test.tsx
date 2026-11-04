import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseApprovalDetailPage } from './CourseApprovalDetailPage';
import { courseApprovalService } from '../services/courseApprovalService';
import type { CourseApprovalDetail } from '../types';

vi.mock('../services/courseApprovalService', () => ({
  courseApprovalService: {
    getDetail: vi.fn(),
    reviewCourse: vi.fn(),
  },
}));

const detail: CourseApprovalDetail = {
  id: 'course-1',
  courseName: 'Tiếng Nhật giao tiếp N5',
  teacherName: 'Nguyễn Văn A',
  teacherEmail: 'teacher@example.com',
  submittedAt: '2026-08-12T01:00:00Z',
  status: 'PENDING',
  curriculumSummary: '<p>Khóa học giao tiếp căn bản.</p>',
  introduction: '<p>Dành cho người mới bắt đầu.</p>',
  jlptLevel: 'N5',
  category: 'Giao tiếp',
  thumbnailUrl: null,
  outcomes: '<p>Có thể giao tiếp trong tình huống quen thuộc.</p>',
  price: 199000,
  currency: 'VND',
  prerequisites: '<p>Không yêu cầu.</p>',
  targetStudents: '<p>Người mới học.</p>',
  moduleCount: 1,
  lessonBlocksCount: 1,
  totalVideoDurationMinutes: 10,
  finalTestIncluded: false,
  policyEvidence: null,
  teacherKycStatus: 'APPROVED',
  teacherCanPublish: true,
  approvalReady: false,
  learningGoals: ['Chào hỏi bằng tiếng Nhật'],
  modules: [{
    id: 'module-1',
    title: 'Làm quen',
    description: 'Nội dung nhập môn',
    orderIndex: 0,
    blocks: [{
      id: 'block-1',
      type: 'VIDEO',
      title: 'Chào hỏi lần đầu',
      videoUrl: 'https://video.example.com/lesson.mp4',
      durationMinutes: 10,
      quizOptions: [],
      quizItems: [],
      flashcards: [],
      orderIndex: 0,
      interactionRequiredAfter: false,
      interactionSatisfied: true,
    }],
  }],
  finalTest: null,
  validationErrors: [{ code: 'MSG-FINAL-001', message: 'Chưa cấu hình bài kiểm tra cuối khóa.', severity: 'error' }],
  reviewCriteria: [{
    code: 'FINAL_TEST',
    title: 'Bài kiểm tra cuối khóa',
    description: 'Cần có bài kiểm tra hợp lệ.',
    passed: false,
    reasons: ['Chưa cấu hình bài kiểm tra cuối khóa.'],
  }],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/courses/approvals/course-1']}>
      <Routes>
        <Route path="/admin/courses/approvals/:id" element={<CourseApprovalDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CourseApprovalDetailPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(courseApprovalService.getDetail).mockResolvedValue(detail);
  });

  it('hiển thị nội dung khóa học và lý do chưa đủ điều kiện duyệt', async () => {
    renderPage();

    expect(await screen.findByText('Tiếng Nhật giao tiếp N5')).toBeInTheDocument();
    expect(screen.getAllByText('Chưa cấu hình bài kiểm tra cuối khóa.').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Phê duyệt khóa học' })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Lý do / Ghi chú' })).toHaveAttribute(
      'placeholder',
      'Lý do / Ghi chú',
    );

    fireEvent.click(screen.getByText('Học phần 1: Làm quen'));
    expect(await screen.findByText('Chào hỏi lần đầu')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Mở video để kiểm tra/ })).toHaveAttribute(
      'href',
      'https://video.example.com/lesson.mp4',
    );
  });

  it('cho phép phê duyệt khi tất cả điều kiện đều đạt', async () => {
    vi.mocked(courseApprovalService.getDetail).mockResolvedValue({
      ...detail,
      approvalReady: true,
      validationErrors: [],
      reviewCriteria: detail.reviewCriteria.map((criterion) => ({ ...criterion, passed: true, reasons: [] })),
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Phê duyệt khóa học' })).toBeEnabled();
    });
  });

  it('khóa toàn bộ quyết định khi yêu cầu đã được phê duyệt', async () => {
    vi.mocked(courseApprovalService.getDetail).mockResolvedValue({
      ...detail,
      status: 'APPROVED',
      approvalReady: true,
      validationErrors: [],
    });

    renderPage();

    expect(await screen.findByText(/Yêu cầu này đã được xử lý với trạng thái/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Phê duyệt khóa học' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Yêu cầu chỉnh sửa' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Từ chối khóa học' })).toBeDisabled();
    expect(courseApprovalService.reviewCourse).not.toHaveBeenCalled();
  });
});
