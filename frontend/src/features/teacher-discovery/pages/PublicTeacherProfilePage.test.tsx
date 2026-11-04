import { render, screen } from '@testing-library/react';
import { HelmetProvider } from 'react-helmet-async';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { publicTeacherService } from '../services/publicTeacherService';
import { PublicTeacherProfilePage } from './PublicTeacherProfilePage';

vi.mock('../services/publicTeacherService', () => ({
  publicTeacherService: {
    getProfile: vi.fn(),
  },
}));

const getProfileMock = vi.mocked(publicTeacherService.getProfile);

function renderPage() {
  return render(
    <HelmetProvider>
      <MemoryRouter initialEntries={['/teachers/teacher-123']}>
        <Routes>
          <Route path="/teachers/:teacherId" element={<PublicTeacherProfilePage />} />
        </Routes>
      </MemoryRouter>
    </HelmetProvider>,
  );
}

describe('PublicTeacherProfilePage', () => {
  beforeEach(() => {
    getProfileMock.mockResolvedValue({
      id: 'teacher-123',
      displayName: 'Sensei An',
      bio: 'N5 grammar teacher',
      verified: true,
      publishedCourseCount: 1,
      courses: [
        {
          id: 'course-123',
          slug: 'n5-foundations',
          title: 'N5 Foundations',
          price: 299000,
          currency: 'VND',
          totalLessons: 12,
        },
      ],
    });
  });

  it('loads the public route and links published courses', async () => {
    renderPage();

    expect(await screen.findByText('Sensei An', { selector: 'h1' })).toBeInTheDocument();
    expect(getProfileMock).toHaveBeenCalledWith('teacher-123');
    expect(screen.getByText('N5 Foundations').closest('a'))
      .toHaveAttribute('href', '/courses/n5-foundations');
    expect(screen.getByText('Đã xác minh')).toBeInTheDocument();
  });

  it('shows a privacy-safe not-found state when the API rejects discovery', async () => {
    getProfileMock.mockRejectedValueOnce(new Error('not found'));

    renderPage();

    expect(await screen.findByText(
      'Hồ sơ giảng viên không tồn tại hoặc hiện không được phép công khai.',
    )).toBeInTheDocument();
    expect(screen.queryByText('not found')).not.toBeInTheDocument();
  });
});
