import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { CourseAnalyticsDialog } from './CourseAnalyticsDialog';
import { fetchCourseAnalytics } from '../services/courseAnalyticsService';

vi.mock('../services/courseAnalyticsService', () => ({
  fetchCourseAnalytics: vi.fn(),
}));

const mockAnalytics = {
  totalEnrollment: 100,
  activeLearners: 50,
  completedLearners: 45,
  completionRate: 75.5,
  grossRevenue: 10000000,
  netRevenue: 8000000,
  refundRate: 5.0,
  averageRating: 4.5,
  totalReviews: 50,
};

describe('CourseAnalyticsDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders loading state initially', async () => {
    vi.mocked(fetchCourseAnalytics).mockImplementation(() => new Promise(() => {}));
    
    render(<CourseAnalyticsDialog courseId="123" courseTitle="Test Course" onClose={() => {}} />);
    
    expect(screen.getByText('Đang tải dữ liệu...')).toBeInTheDocument();
  });

  it('renders analytics data correctly', async () => {
    vi.mocked(fetchCourseAnalytics).mockResolvedValue(mockAnalytics);
    
    render(<CourseAnalyticsDialog courseId="123" courseTitle="Test Course" onClose={() => {}} />);
    
    await waitFor(() => {
      expect(screen.getByText('100')).toBeInTheDocument(); // totalEnrollment
      expect(screen.getByText('75.5%')).toBeInTheDocument(); // completionRate
      expect(screen.getByText('5.0%')).toBeInTheDocument(); // refundRate
      expect(screen.getByText('10.000.000 ₫')).toBeInTheDocument(); // grossRevenue (assuming vi-VN locale formats like this)
      expect(screen.getByText('8.000.000 ₫')).toBeInTheDocument(); // netRevenue
      expect(screen.getByText(/4\.5/)).toBeInTheDocument(); // averageRating
    });
  });

  it('fetches data with date range when dates are selected', async () => {
    vi.mocked(fetchCourseAnalytics).mockResolvedValue(mockAnalytics);
    
    render(<CourseAnalyticsDialog courseId="123" courseTitle="Test Course" onClose={() => {}} />);
    
    await waitFor(() => {
      expect(screen.getByText('100')).toBeInTheDocument();
    });

    const startDateInput = screen.getByLabelText('Từ ngày');
    const endDateInput = screen.getByLabelText('Đến ngày');

    fireEvent.change(startDateInput, { target: { value: '2023-01-01' } });
    fireEvent.change(endDateInput, { target: { value: '2023-12-31' } });

    await waitFor(() => {
      // The first call was without dates, the last call should be with dates
      const lastCallArgs = vi.mocked(fetchCourseAnalytics).mock.lastCall;
      expect(lastCallArgs?.[0]).toBe('123');
      const submittedStartDate = new Date(lastCallArgs![1]!);
      expect(submittedStartDate.getFullYear()).toBe(2023);
      expect(submittedStartDate.getMonth()).toBe(0);
      expect(submittedStartDate.getDate()).toBe(1);

      const submittedEndDate = new Date(lastCallArgs![2]!);
      expect(submittedEndDate.getFullYear()).toBe(2023);
      expect(submittedEndDate.getMonth()).toBe(11);
      expect(submittedEndDate.getDate()).toBe(31);
    });
  });

  it('clamps today end dates to current time', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    const mockNow = new Date('2023-08-03T10:00:00.000Z');
    vi.setSystemTime(mockNow);

    vi.mocked(fetchCourseAnalytics).mockResolvedValue(mockAnalytics);
    
    render(<CourseAnalyticsDialog courseId="123" courseTitle="Test Course" onClose={() => {}} />);
    
    const endDateInput = screen.getByLabelText('Đến ngày');
    
    // Set to today's date
    const todayStr = '2023-08-03';
    fireEvent.change(endDateInput, { target: { value: todayStr } });
    
    await waitFor(() => {
      const lastCallArgs = vi.mocked(fetchCourseAnalytics).mock.lastCall;
      expect(lastCallArgs?.[2]).toBeDefined();
      const submittedEndDate = new Date(lastCallArgs![2]!);
      expect(submittedEndDate.getTime()).toBeLessThanOrEqual(mockNow.getTime());
    });

    vi.useRealTimers();
  });
});
