import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { CourseCatalogCard } from './CourseCatalogCard';

vi.mock('../../wishlist/components/WishlistToggleButton', () => ({
  WishlistToggleButton: () => null,
}));

describe('CourseCatalogCard', () => {
  it('links the course and its teacher to separate public pages', () => {
    render(
      <MemoryRouter>
        <CourseCatalogCard
          course={{
            id: 'course-123',
            slug: 'n5-foundations',
            title: 'N5 Foundations',
            price: 299000,
            currency: 'VND',
            teacherId: 'teacher-123',
            teacherName: 'Sensei An',
            totalLessons: 12,
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Xem khóa học N5 Foundations' }))
      .toHaveAttribute('href', '/courses/n5-foundations');
    expect(screen.getByRole('link', { name: 'Sensei An' }))
      .toHaveAttribute('href', '/teachers/teacher-123');
  });
});
