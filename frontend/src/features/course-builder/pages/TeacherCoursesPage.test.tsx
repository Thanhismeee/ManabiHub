import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchCourseCategories,
  fetchCourseDrafts,
  type CourseDraftResponse,
} from '../services/courseDraftService';
import { TeacherCoursesPage } from './TeacherCoursesPage';

vi.mock('../services/courseDraftService', () => ({
  deleteCourseDraft: vi.fn(),
  fetchCourseCategories: vi.fn(),
  fetchCourseDrafts: vi.fn(),
  publishCourse: vi.fn(),
  submitCourseForReview: vi.fn(),
  unpublishCourse: vi.fn(),
  validateCourseDraft: vi.fn(),
}));

const courses: CourseDraftResponse[] = [
  {
    id: 'course-n5',
    teacherId: 'teacher-1',
    title: 'Giao tiếp tiếng Nhật N5',
    slug: 'giao-tiep-n5',
    introduction: 'Khóa học giao tiếp dành cho người mới bắt đầu.',
    jlptLevel: 'N5',
    category: 'VOCABULARY',
    outcomes: 'Giao tiếp cơ bản',
    price: 0,
    currency: 'VND',
    prerequisites: 'Không',
    targetStudents: 'Người mới học',
    status: 'PUBLISHED',
    learningGoals: [],
    createdAt: '2026-08-01T08:00:00Z',
    updatedAt: '2026-08-02T08:00:00Z',
    srsTrace: {},
  },
  {
    id: 'course-n3',
    teacherId: 'teacher-1',
    title: 'Ngữ pháp tiếng Nhật N3',
    slug: 'ngu-phap-n3',
    introduction: 'Hệ thống hóa ngữ pháp trung cấp.',
    jlptLevel: 'N3',
    category: 'GRAMMAR',
    outcomes: 'Nắm vững ngữ pháp N3',
    price: 250000,
    currency: 'VND',
    prerequisites: 'Đã học N4',
    targetStudents: 'Học viên trung cấp',
    status: 'APPROVED',
    learningGoals: [],
    createdAt: '2026-08-03T08:00:00Z',
    updatedAt: '2026-08-10T08:00:00Z',
    srsTrace: {},
  },
];

beforeEach(() => {
  vi.mocked(fetchCourseDrafts).mockResolvedValue(courses);
  vi.mocked(fetchCourseCategories).mockResolvedValue([
    { id: 'category-1', code: 'VOCABULARY', name: 'Từ vựng' },
    { id: 'category-2', code: 'GRAMMAR', name: 'Ngữ pháp' },
  ]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('TeacherCoursesPage filters', () => {
  it('filters by level, category and search text, then clears every filter', async () => {
    render(
      <MemoryRouter>
        <TeacherCoursesPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Giao tiếp tiếng Nhật N5')).toBeInTheDocument();
    expect(screen.getByText('Ngữ pháp tiếng Nhật N3')).toBeInTheDocument();
    expect(courseTitles()).toEqual([
      'Ngữ pháp tiếng Nhật N3',
      'Giao tiếp tiếng Nhật N5',
    ]);

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Sắp xếp' }));
    fireEvent.click(screen.getByRole('option', { name: 'Cũ cập nhật' }));

    await waitFor(() => {
      expect(courseTitles()).toEqual([
        'Giao tiếp tiếng Nhật N5',
        'Ngữ pháp tiếng Nhật N3',
      ]);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    expect(courseTitles()).toEqual([
      'Ngữ pháp tiếng Nhật N3',
      'Giao tiếp tiếng Nhật N5',
    ]);

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Trình độ' }));
    fireEvent.click(screen.getByRole('option', { name: 'N3' }));

    await waitFor(() => {
      expect(screen.queryByText('Giao tiếp tiếng Nhật N5')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Ngữ pháp tiếng Nhật N3')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    expect(screen.getByText('Giao tiếp tiếng Nhật N5')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Danh mục' }));
    fireEvent.click(screen.getByRole('option', { name: 'Từ vựng' }));

    await waitFor(() => {
      expect(screen.queryByText('Ngữ pháp tiếng Nhật N3')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Giao tiếp tiếng Nhật N5')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Tìm kiếm khóa học' }), {
      target: { value: 'giao tiep' },
    });

    await waitFor(() => {
      expect(screen.queryByText('Ngữ pháp tiếng Nhật N3')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Giao tiếp tiếng Nhật N5')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Xóa lọc' }));
    expect(screen.getByText('Giao tiếp tiếng Nhật N5')).toBeInTheDocument();
    expect(screen.getByText('Ngữ pháp tiếng Nhật N3')).toBeInTheDocument();
  });
});

function courseTitles() {
  return screen.getAllByText(/^(Giao tiếp|Ngữ pháp) tiếng Nhật N[35]$/)
    .map((element) => element.textContent);
}
