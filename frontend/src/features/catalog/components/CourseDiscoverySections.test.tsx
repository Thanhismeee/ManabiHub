import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CourseDiscoverySections } from './CourseDiscoverySections';
import type { PublicCourseSummary } from '../types/catalogTypes';

vi.mock('./CourseCatalogCard', () => ({
  CourseCatalogCard: ({ course }: { course: PublicCourseSummary }) => (
    <div data-testid="course-card">{course.title}</div>
  ),
}));

afterEach(cleanup);

const course = (
  id: string,
  title: string,
  overrides: Partial<PublicCourseSummary> = {},
): PublicCourseSummary => ({
  id,
  title,
  slug: id,
  price: 100_000,
  currency: 'VND',
  totalLessons: 10,
  jlptLevel: 'N5',
  publishedAt: '2026-08-01T00:00:00Z',
  ...overrides,
});

describe('CourseDiscoverySections', () => {
  const courses = [
    course('a', 'Kanji căn bản', { enrollmentCount: 12, averageRating: 4.6, reviewCount: 5 }),
    course('b', 'Kaiwa thực hành', { enrollmentCount: 80, averageRating: 4.8, reviewCount: 2 }),
    course('c', 'Ngữ pháp N5', { enrollmentCount: 30, averageRating: 5, reviewCount: 1 }),
    course('d', 'Đọc hiểu N5', { enrollmentCount: 20, averageRating: 4.8, reviewCount: 8 }),
    course('e', 'Luyện nghe N5', { enrollmentCount: 5 }),
    course('f', 'Ngữ pháp N4', { jlptLevel: 'N4', enrollmentCount: 100, averageRating: 5, reviewCount: 20 }),
  ];

  it('giữ đúng thứ tự xếp hạng toàn hệ thống do backend trả về', () => {
    render(
      <CourseDiscoverySections
        latestCourses={courses}
        bestSellingCourses={[courses[5], courses[1], courses[2], courses[3]]}
        topRatedCourses={[courses[5], courses[2], courses[3], courses[1]]}
        onLevelChange={vi.fn()}
        onViewAll={vi.fn()}
      />,
    );

    const bestSelling = within(screen.getByRole('region', { name: 'Bán chạy nhất' }))
      .getAllByTestId('course-card')
      .map((item) => item.textContent);
    expect(bestSelling).toEqual([
      'Ngữ pháp N4',
      'Kaiwa thực hành',
      'Ngữ pháp N5',
      'Đọc hiểu N5',
    ]);

    const topRated = within(screen.getByRole('region', { name: 'Được đánh giá cao' }))
      .getAllByTestId('course-card')
      .map((item) => item.textContent);
    expect(topRated).toEqual([
      'Ngữ pháp N4',
      'Ngữ pháp N5',
      'Đọc hiểu N5',
      'Kaiwa thực hành',
    ]);
  });

  it('lọc discovery theo JLPT và chuyển tới danh sách đầy đủ bằng callback riêng', () => {
    const onLevelChange = vi.fn();
    const onViewAll = vi.fn();
    render(
      <CourseDiscoverySections
        latestCourses={[courses[5]]}
        bestSellingCourses={[courses[5]]}
        topRatedCourses={[courses[5]]}
        selectedLevel="N4"
        onLevelChange={onLevelChange}
        onViewAll={onViewAll}
      />,
    );

    expect(screen.getAllByTestId('course-card').every((item) => item.textContent === 'Ngữ pháp N4')).toBe(true);
    fireEvent.click(screen.getByText('N5'));
    expect(onLevelChange).toHaveBeenCalledWith('N5');

    fireEvent.click(screen.getAllByRole('button', { name: /Xem tất cả/i })[0]);
    expect(onViewAll).toHaveBeenCalledTimes(1);
  });
});
