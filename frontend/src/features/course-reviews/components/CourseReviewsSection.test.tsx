import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { courseReviewService } from '../services/courseReviewService';
import { CourseReviewsSection } from './CourseReviewsSection';

vi.mock('../services/courseReviewService', () => ({
  courseReviewService: {
    getPublicReviews: vi.fn(),
    getMyReview: vi.fn(),
    upsertMyReview: vi.fn(),
    replyToReview: vi.fn(),
  },
}));

const getPublicReviewsMock = vi.mocked(courseReviewService.getPublicReviews);
const getMyReviewMock = vi.mocked(courseReviewService.getMyReview);
const upsertMyReviewMock = vi.mocked(courseReviewService.upsertMyReview);
const replyToReviewMock = vi.mocked(courseReviewService.replyToReview);

const publicReview = {
  id: 'review-1',
  rating: 5,
  reviewText: '<img src=x onerror=alert(1)> Nội dung vẫn là văn bản.',
  authorDisplayName: 'Học viên An',
  updatedAt: '2026-07-27T00:00:00Z',
};

function page(content = [publicReview]) {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
  };
}

function studentToken() {
  const encode = (value: object) => btoa(JSON.stringify(value))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '');
  return `${encode({ alg: 'none' })}.${encode({
    sub: 'student-user',
    role: ['STUDENT'],
    exp: Math.floor(Date.now() / 1000) + 3600,
  })}.signature`;
}

describe('CourseReviewsSection', () => {
  beforeEach(() => {
    window.localStorage.clear();
    getPublicReviewsMock.mockResolvedValue(page());
    getMyReviewMock.mockResolvedValue(null);
    upsertMyReviewMock.mockResolvedValue({
      id: 'review-new',
      rating: 5,
      reviewText: 'Khóa học có nội dung rõ ràng và hữu ích.',
      authorDisplayName: 'Học viên hiện tại',
      updatedAt: '2026-07-28T00:00:00Z',
    });
    replyToReviewMock.mockResolvedValue({
      ...publicReview,
      teacherReplyText: 'Cảm ơn em đã chia sẻ nhận xét.',
      teacherRepliedAt: '2026-08-10T10:05:00Z',
    });
  });

  it('renders only real public data and keeps review HTML inert', async () => {
    const { container } = render(
      <CourseReviewsSection
        courseId="course-1"
        courseIdentifier="course-slug"
        isEnrolled={false}
        averageRating={5}
        reviewCount={1}
      />,
    );

    expect(await screen.findByText('Học viên An')).toBeInTheDocument();
    expect(screen.getByText(
      '<img src=x onerror=alert(1)> Nội dung vẫn là văn bản.',
    )).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
    expect(screen.queryByText('Viết đánh giá')).not.toBeInTheDocument();
    expect(getMyReviewMock).not.toHaveBeenCalled();
  });

  it('allows an enrolled student to idempotently save their own review', async () => {
    window.localStorage.setItem('auth_token', studentToken());
    const refreshAggregateMock = vi.fn().mockResolvedValue(undefined);
    getPublicReviewsMock
      .mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([{
        ...publicReview,
        id: 'review-new',
        reviewText: 'Khóa học có nội dung rõ ràng và hữu ích.',
      }]));

    render(
      <CourseReviewsSection
        courseId="course-1"
        courseIdentifier="course-slug"
        isEnrolled
        onReviewChanged={refreshAggregateMock}
      />,
    );

    expect(await screen.findByText('Viết đánh giá')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Nội dung đánh giá'), {
      target: { value: 'Khóa học có nội dung rõ ràng và hữu ích.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }));

    await waitFor(() => expect(upsertMyReviewMock).toHaveBeenCalledWith(
      'course-1',
      {
        rating: 5,
        reviewText: 'Khóa học có nội dung rõ ràng và hữu ích.',
      },
    ));
    expect(await screen.findByText('Đã lưu đánh giá của bạn.')).toBeInTheDocument();
    expect(getPublicReviewsMock).toHaveBeenLastCalledWith('course-slug', 0, 10);
    expect(refreshAggregateMock).toHaveBeenCalledOnce();
  });

  it('supports an explicit refresh without polling the review endpoint', async () => {
    render(
      <CourseReviewsSection
        courseId="course-1"
        courseIdentifier="course-slug"
        isEnrolled={false}
      />,
    );

    await screen.findAllByText('Học viên An');
    const initialCalls = getPublicReviewsMock.mock.calls.length;
    const refreshButtons = screen.getAllByRole('button', { name: 'Tải lại đánh giá' });
    const refreshButton = refreshButtons[refreshButtons.length - 1];
    await waitFor(() => expect(refreshButton).toBeEnabled());
    fireEvent.click(refreshButton);

    await waitFor(() => expect(getPublicReviewsMock.mock.calls.length).toBe(initialCalls + 1));
    expect(getPublicReviewsMock).toHaveBeenLastCalledWith('course-slug', 0, 10);
  });

  it('shows intentional retry state when public reviews cannot load', async () => {
    getPublicReviewsMock.mockRejectedValueOnce(new Error('network'));

    render(
      <CourseReviewsSection
        courseId="course-1"
        courseIdentifier="course-slug"
        isEnrolled={false}
      />,
    );

    expect(await screen.findByText('Chưa thể tải danh sách đánh giá.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });

  it('allows the course owner to reply and renders the teacher response', async () => {
    render(
      <CourseReviewsSection
        courseId="course-1"
        courseIdentifier="course-slug"
        isEnrolled={false}
        canTeacherReply
      />,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Phản hồi bình luận' }));
    fireEvent.change(screen.getByLabelText('Phản hồi học viên'), {
      target: { value: 'Cảm ơn em đã chia sẻ nhận xét.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi phản hồi' }));

    await waitFor(() => expect(replyToReviewMock).toHaveBeenCalledWith(
      'review-1',
      { replyText: 'Cảm ơn em đã chia sẻ nhận xét.' },
    ));
    expect(await screen.findByText('Phản hồi từ giảng viên')).toBeInTheDocument();
    expect(screen.getByText('Cảm ơn em đã chia sẻ nhận xét.')).toBeInTheDocument();
  });
});
