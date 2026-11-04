import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import { CourseStickyHeader } from './CourseStickyHeader';

describe('CourseStickyHeader', () => {
  it('forwards the purchase action to the shared checkout flow', () => {
    const onPurchase = vi.fn();

    render(
      <CourseStickyHeader
        course={{
          title: 'Kanji N5',
          price: 250_000,
          currency: 'VND',
          isEnrolled: false,
        } as unknown as PublicCourseDetail}
        onPurchase={onPurchase}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));

    expect(onPurchase).toHaveBeenCalledTimes(1);
  });

  it('uses the renewal action for a previous enrollment that has expired', () => {
    const onPurchase = vi.fn();

    render(
      <CourseStickyHeader
        course={{
          title: 'Kanji N5',
          price: 250_000,
          currency: 'VND',
          isEnrolled: false,
          hasExpiredEnrollment: true,
        } as unknown as PublicCourseDetail}
        onPurchase={onPurchase}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Gia hạn khóa học' }));

    expect(onPurchase).toHaveBeenCalledTimes(1);
  });

  it('disables the action when the fixed access period has ended', () => {
    const onContinueLearning = vi.fn();

    render(
      <CourseStickyHeader
        course={{
          title: 'Kanji N5',
          price: 250_000,
          currency: 'VND',
          isEnrolled: true,
          accessExpiresAt: '2000-01-01T00:00:00Z',
        } as unknown as PublicCourseDetail}
        onContinueLearning={onContinueLearning}
      />,
    );

    expect(screen.getByRole('button', { name: 'Đã hết hạn' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Tiếp tục học' })).not.toBeInTheDocument();
    expect(onContinueLearning).not.toHaveBeenCalled();
  });
});
