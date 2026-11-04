import { cleanup, fireEvent, render, screen, within, act, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useOrderHistory } from '../hooks/useOrderHistory';
import { useStudentRefunds } from '../../refunds/hooks/useStudentRefunds';
import { ROLES } from '../../../shared/constants/roles';
import { StudentPaymentsPage } from './StudentPaymentsPage';

const mocks = vi.hoisted(() => ({
  getAuthSession: vi.fn(),
  subscribeToAuthSessionChanges: vi.fn(),
  getTeacherWallet: vi.fn(),
  getTeacherWithdrawals: vi.fn(),
  getStudentIdentityVerificationStatus: vi.fn(),
}));

let authSessionListener: (() => void) | null = null;

vi.mock('../../../shared/auth/authSession', async () => {
  const actual = await vi.importActual<typeof import('../../../shared/auth/authSession')>(
    '../../../shared/auth/authSession',
  );
  return {
    ...actual,
    getAuthSession: mocks.getAuthSession,
    subscribeToAuthSessionChanges: (listener: () => void) => {
      authSessionListener = listener;
      return () => {
        authSessionListener = null;
      };
    },
  };
});

vi.mock('../hooks/useOrderHistory', () => ({
  useOrderHistory: vi.fn(),
}));

vi.mock('../../refunds/hooks/useStudentRefunds', () => ({
  useStudentRefunds: vi.fn(),
  useStudentRefundDetail: vi.fn(() => ({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  })),
  useCreateStudentRefund: vi.fn(() => ({
    reset: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  })),
  useCancelStudentRefund: vi.fn(() => ({
    reset: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  })),
}));

vi.mock('../../wallet/services/studentWalletService', () => ({
  getStudentWallet: vi.fn(() => Promise.resolve({
    availableBalance: 500000,
    availableWithdrawableBalance: 500000,
    escrowBalance: 0,
    currency: 'VND',
  })),
}));

vi.mock('../../wallet/services/studentIdentityVerificationService', () => ({
  getStudentIdentityVerificationStatus: mocks.getStudentIdentityVerificationStatus,
}));

vi.mock('../../my-wallet/services/walletService', () => ({
  walletService: {
    getTeacherWallet: mocks.getTeacherWallet,
    getTeacherWithdrawals: mocks.getTeacherWithdrawals,
  },
}));

vi.mock('../../help-center/hooks/useCommercialPolicy', () => ({
  useCommercialPolicy: vi.fn(() => ({
    data: {
      refundWindowDays: 14,
      refundProgressLimitPercent: 20,
      payoutThreshold: 100000,
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  })),
}));

function renderWithProviders(ui: React.ReactElement, { initialEntries = ['/student/payments'] } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  authSessionListener = null;
});

describe('StudentPaymentsPage', () => {
  beforeEach(() => {
    mocks.getAuthSession.mockReturnValue({
      kind: 'public',
      token: 'student-token',
      subject: 'student-1',
      email: 'student@example.com',
      roles: [ROLES.STUDENT],
      expiresAt: Date.now() + 3600000,
    });

    mocks.getStudentIdentityVerificationStatus.mockResolvedValue({
      verified: false,
      status: 'NOT_VERIFIED',
    });

    mocks.getTeacherWallet.mockResolvedValue({
      code: '200',
      message: 'Success',
      data: {
        walletId: 'tw-1',
        availableBalance: 1200000,
        pendingBalance: 300000,
        totalWithdrawn: 500000,
        currency: 'VND',
        status: 'ACTIVE',
      },
    });

    mocks.getTeacherWithdrawals.mockResolvedValue({
      code: '200',
      message: 'Success',
      data: {
        content: [
          {
            id: 'wth-req-001',
            teacherWalletId: 'tw-1',
            requestedAmount: 200000,
            netAmount: 200000,
            currency: 'VND',
            bankName: 'Vietcombank',
            accountNumberMasked: '****1234',
            accountHolderName: 'NGUYEN VAN A',
            status: 'PENDING',
            requestedAt: '2026-08-01T10:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      },
    });

    vi.mocked(useStudentRefunds).mockReturnValue({
      data: {
        content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useStudentRefunds>);

    vi.mocked(useOrderHistory).mockReturnValue({
      data: {
        content: [
          {
            id: 'order-1',
            orderCode: 'MHB-20260729-001',
            totalAmount: 799000,
            currency: 'VND',
            status: 'PAID',
            createdAt: '2026-07-29T08:00:00Z',
            items: [
              {
                id: 'order-item-1',
                courseId: 'course-1',
                courseTitle: 'JLPT N2 chuyên sâu',
                price: 799000,
              },
            ],
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      },
      isLoading: false,
      isFetching: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useOrderHistory>);
  });

  describe('STUDENT role', () => {
    it('renders student wallet, order history, and refund history without teacher revenue card or withdrawal controls', () => {
      renderWithProviders(<StudentPaymentsPage />);

      expect(screen.getByText('MHB-20260729-001')).toBeInTheDocument();
      expect(screen.getByTestId('decorative-kanji-watermark')).toHaveTextContent('履歴');
      expect(screen.getByText('JLPT N2 chuyên sâu')).toBeInTheDocument();
      expect(screen.getAllByText(/799\.000/).length).toBeGreaterThan(0);
      expect(screen.getByText('Đã thanh toán')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Yêu cầu hoàn tiền/i })).toBeInTheDocument();
      expect(screen.getByText('Ví học viên')).toBeInTheDocument();
      expect(screen.getByText('Lịch sử hoàn tiền')).toBeInTheDocument();
      expect(screen.getByText('Tiền nạp, hoàn tiền và thưởng học tập')).toBeInTheDocument();

      // Assert teacher-specific elements are completely hidden
      expect(screen.queryByText('Ví doanh thu giảng viên')).not.toBeInTheDocument();
      expect(screen.queryByText('Lịch sử rút hoa hồng')).not.toBeInTheDocument();
      expect(screen.queryByText('Trở thành giảng viên để rút hoa hồng')).not.toBeInTheDocument();
      expect(screen.queryByText(/Trở thành giảng viên/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/hoa hồng/i)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /rút hoa hồng/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Xác thực CCCD' })).not.toBeInTheDocument();
      expect(screen.queryByRole('tab')).not.toBeInTheDocument();

      // Assert teacher APIs were not called
      expect(mocks.getTeacherWallet).not.toHaveBeenCalled();
      expect(mocks.getTeacherWithdrawals).not.toHaveBeenCalled();
    });

    it('filters out top-up orders from course order history', () => {
      vi.mocked(useOrderHistory).mockReturnValue({
        data: {
          content: [
            {
              id: 'top-up-order-1',
              orderCode: 'MHB-TOPUP-001',
              totalAmount: 100000,
              currency: 'VND',
              status: 'PAID',
              type: 'WALLET_TOPUP',
              createdAt: '2026-08-02T08:00:00Z',
              items: [],
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
        isLoading: false,
        isFetching: false,
        isError: false,
        refetch: vi.fn(),
      } as unknown as ReturnType<typeof useOrderHistory>);

      renderWithProviders(<StudentPaymentsPage />);

      expect(screen.getByText('Chưa có đơn hàng nào')).toBeInTheDocument();
      expect(screen.queryByText('MHB-TOPUP-001')).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Vào học/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Yêu cầu hoàn tiền/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Xem chi tiết hoàn tiền/i })).not.toBeInTheDocument();
    });

    it('does not offer refund actions for a free course', () => {
      vi.mocked(useOrderHistory).mockReturnValue({
        data: {
          content: [
            {
              id: 'free-order-1',
              orderCode: 'MHB-FREE-001',
              totalAmount: 0,
              currency: 'VND',
              status: 'PAID',
              type: 'COURSE',
              createdAt: '2026-08-03T08:00:00Z',
              items: [
                {
                  id: 'free-order-item-1',
                  courseId: 'free-course-1',
                  courseTitle: 'Free Japanese Course',
                  price: 0,
                },
              ],
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        },
        isLoading: false,
        isFetching: false,
        isError: false,
        refetch: vi.fn(),
      } as unknown as ReturnType<typeof useOrderHistory>);

      renderWithProviders(<StudentPaymentsPage />);

      const courseTitle = screen.getByText('Free Japanese Course');
      const freeCourseRow = courseTitle.closest('.MuiBox-root');

      expect(screen.getByText('MHB-FREE-001')).toBeInTheDocument();
      expect(freeCourseRow).not.toBeNull();
      expect(within(freeCourseRow as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    });
  });

  describe('TEACHER role', () => {
    beforeEach(() => {
      mocks.getAuthSession.mockReturnValue({
        kind: 'public',
        token: 'teacher-token',
        subject: 'teacher-1',
        email: 'teacher@example.com',
        roles: [ROLES.TEACHER],
        expiresAt: Date.now() + 3600000,
      });
    });

    it('renders teacher revenue wallet card and tabs when user is a teacher', async () => {
      renderWithProviders(<StudentPaymentsPage />);

      expect(screen.getByText('Ví học viên')).toBeInTheDocument();
      expect(screen.getByText('Ví doanh thu giảng viên')).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Lịch sử đơn hàng' })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Lịch sử rút hoa hồng' })).toBeInTheDocument();

      expect(await screen.findByText(/1\.200\.000/)).toBeInTheDocument();
      expect(screen.getByText(/300\.000/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Xác thực CCCD' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Xác minh SĐT & CCCD để rút hoa hồng' })).toBeInTheDocument();

      expect(mocks.getTeacherWallet).toHaveBeenCalled();
      expect(mocks.getTeacherWithdrawals).toHaveBeenCalled();
    });

    it('switches between order history and withdrawal history tabs', async () => {
      renderWithProviders(<StudentPaymentsPage />);

      expect(screen.getByText('MHB-20260729-001')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử rút hoa hồng' }));

      const bankNames = await screen.findAllByText('Vietcombank');
      expect(bankNames.length).toBeGreaterThan(0);
      expect(screen.getAllByText('****1234').length).toBeGreaterThan(0);
      expect(screen.queryByText('MHB-20260729-001')).not.toBeInTheDocument();
    });

    it('navigates to CCCD verification when unverified teacher clicks verification button', async () => {
      renderWithProviders(
        <>
          <StudentPaymentsPage />
          <LocationProbe />
        </>,
        { initialEntries: ['/student/payments'] },
      );

      const btn = await screen.findByRole('button', { name: 'Xác minh SĐT & CCCD để rút hoa hồng' });
      await waitFor(() => expect(btn).not.toBeDisabled());
      fireEvent.click(btn);

      await waitFor(() => {
        expect(screen.getByTestId('current-location')).toHaveTextContent(
          '/student/identity-verification',
        );
      });
    });

    it('navigates to teacher wallet when verified teacher clicks withdraw button', async () => {
      mocks.getStudentIdentityVerificationStatus.mockResolvedValue({
        verified: true,
        status: 'VERIFIED',
      });

      renderWithProviders(
        <>
          <StudentPaymentsPage />
          <LocationProbe />
        </>,
        { initialEntries: ['/student/payments'] },
      );

      const withdrawBtn = await screen.findByRole('button', { name: 'Mở Ví doanh thu để rút hoa hồng' });
      await waitFor(() => expect(withdrawBtn).not.toBeDisabled());
      fireEvent.click(withdrawBtn);

      await waitFor(() => {
        expect(screen.getByTestId('current-location')).toHaveTextContent(
          '/teacher/wallet',
        );
      });
    });
  });

  describe('Session changes', () => {
    it('resets active tab to order history if user role changes from TEACHER to STUDENT while page is open', async () => {
      mocks.getAuthSession.mockReturnValue({
        kind: 'public',
        token: 'teacher-token',
        subject: 'teacher-1',
        email: 'teacher@example.com',
        roles: [ROLES.TEACHER],
        expiresAt: Date.now() + 3600000,
      });

      renderWithProviders(<StudentPaymentsPage />);

      // As teacher, navigate to withdrawal tab
      fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử rút hoa hồng' }));
      const bankNames = await screen.findAllByText('Vietcombank');
      expect(bankNames.length).toBeGreaterThan(0);

      // Now session changes to student
      mocks.getAuthSession.mockReturnValue({
        kind: 'public',
        token: 'student-token',
        subject: 'student-1',
        email: 'student@example.com',
        roles: [ROLES.STUDENT],
        expiresAt: Date.now() + 3600000,
      });

      act(() => {
        authSessionListener?.();
      });

      // Assert teacher elements are gone and order history is back
      expect(screen.queryByText('Ví doanh thu giảng viên')).not.toBeInTheDocument();
      expect(screen.queryByText('Vietcombank')).not.toBeInTheDocument();
      expect(screen.getByText('MHB-20260729-001')).toBeInTheDocument();
    });
  });
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="current-location">{location.pathname}{location.search}</span>;
}
