import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseStickyCard } from './CourseStickyCard';

const mocks = vi.hoisted(() => ({
  createCheckout: vi.fn(),
  getAuthSession: vi.fn(),
  getStudentWallet: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
  };
});

vi.mock('../../checkout/services/checkoutService', () => ({
  createCheckout: mocks.createCheckout,
}));

vi.mock('../../../shared/auth/authSession', () => ({
  getAuthSession: mocks.getAuthSession,
}));

vi.mock('../../wallet/services/studentWalletService', () => ({
  getStudentWallet: mocks.getStudentWallet,
}));

vi.mock('../../wishlist/components/WishlistToggleButton', () => ({
  WishlistToggleButton: () => null,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  mocks.getStudentWallet.mockResolvedValue({ availableBalance: 250_000 });
});

const course = {
  id: 'course-1',
  slug: 'kanji-n5',
  title: 'Kanji N5',
  thumbnailUrl: '/uploads/courses/kanji-n5.webp',
  price: 0,
  currency: 'VND',
  aiSupported: false,
  teacher: { id: 'teacher-1', name: 'An', verified: true },
  isEnrolled: false,
  totalDurationMinutes: 0,
  totalLessons: 0,
  modules: [],
};

describe('CourseStickyCard', () => {
  it('shows one purchase trigger and opens payment methods on demand', () => {
    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 250_000 }} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: 'Mua ngay' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thanh toán bằng ví' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));

    expect(screen.getByRole('dialog', { name: 'Chọn phương thức thanh toán' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thanh toán toàn bộ qua VNPay/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thanh toán toàn bộ bằng ví/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Ví \+ VNPay phần còn lại/ })).not.toBeInTheDocument();
  });

  it('offers renewal when the previous enrollment expired and the course still accepts access', () => {
    render(
      <MemoryRouter>
        <CourseStickyCard
          course={{
            ...course,
            price: 250_000,
            hasExpiredEnrollment: true,
            accessDurationDays: 180,
          }}
        />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Gia hạn khóa học' }));

    expect(screen.getByRole('dialog', { name: 'Chọn phương thức thanh toán' })).toBeInTheDocument();
  });

  it('does not offer learning or checkout after the fixed course access period ended', () => {
    render(
      <MemoryRouter>
        <CourseStickyCard
          course={{
            ...course,
            price: 250_000,
            isEnrolled: true,
            hasExpiredEnrollment: true,
            accessExpiresAt: '2000-01-01T00:00:00Z',
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: 'Đã hết hạn truy cập' })).toBeDisabled();
    expect(screen.getByText(/Vui lòng chờ giảng viên mở lại thời hạn/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tiếp tục học' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mua ngay' })).not.toBeInTheDocument();
    expect(mocks.createCheckout).not.toHaveBeenCalled();
  });

  it('resolves relative thumbnail URLs through the backend origin', () => {
    render(
      <MemoryRouter>
        <CourseStickyCard course={course} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('img', { name: 'Ảnh bìa khóa học Kanji N5' }))
      .toHaveAttribute('src', 'http://localhost:8081/uploads/courses/kanji-n5.webp');
  });

  it('shows an accessible fallback when the thumbnail cannot be loaded', () => {
    render(
      <MemoryRouter>
        <CourseStickyCard course={course} />
      </MemoryRouter>,
    );

    fireEvent.error(screen.getByRole('img', { name: 'Ảnh bìa khóa học Kanji N5' }));

    expect(screen.getByRole('img', { name: 'Khóa học Kanji N5 chưa có ảnh bìa' }))
      .toBeInTheDocument();
  });

  it('explains that an internal wallet-payment failure did not debit the balance', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockRejectedValue({
      response: { data: { messageCode: 'COMMON_INTERNAL_ERROR' } },
    });

    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 250_000 }} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ bằng ví/ }));

    expect(await screen.findByText(
      'Thanh toán chưa hoàn tất và số dư ví chưa bị trừ. Vui lòng thử lại.',
    )).toBeInTheDocument();
    expect(mocks.createCheckout).toHaveBeenCalledWith('course-1', 'WALLET');
  });

  it('explains a server-side course deadline rejection without starting payment', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockRejectedValue({
      response: { data: { messageCode: 'ORDER_COURSE_ACCESS_ENDED' } },
    });

    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 250_000 }} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ bằng ví/ }));

    expect(await screen.findByText(
      'Khóa học đã kết thúc thời hạn truy cập và hiện không thể mua hoặc gia hạn.',
    )).toBeInTheDocument();
  });

  it('reveals combined payment and cancel only after wallet balance is insufficient', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockRejectedValue({
      response: { data: { messageCode: 'WALLET_INSUFFICIENT_BALANCE' } },
    });

    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 250_000 }} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ bằng ví/ }));

    expect(await screen.findByText('Số dư ví không đủ. Bạn có thể chọn ví + VNPay phần còn lại hoặc hủy.'))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Ví \+ VNPay phần còn lại/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }));

    expect(screen.queryByRole('dialog', { name: 'Chọn phương thức thanh toán' })).not.toBeInTheDocument();
  });

  it('confirms free enrollment without calling it a payment or navigating automatically', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockResolvedValue({ orderId: 'free-order' });

    render(
      <MemoryRouter>
        <CourseStickyCard course={course} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Ghi danh ngay' }));

    expect(await screen.findByRole('dialog', { name: 'Đăng ký khóa học thành công' }))
      .toBeInTheDocument();
    expect(screen.getByText('Bạn đã tham gia khóa học này. Bạn có muốn bắt đầu học ngay không?'))
      .toBeInTheDocument();
    expect(mocks.navigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Để sau' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tiếp tục học' })).toBeInTheDocument();
  });

  it('confirms a completed wallet payment and navigates only after choosing to learn', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockResolvedValue({ orderId: 'paid-order' });

    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 250_000 }} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ bằng ví/ }));

    expect(await screen.findByRole('dialog', { name: 'Thanh toán thành công' }))
      .toBeInTheDocument();
    expect(screen.getByText('Bạn đã sở hữu khóa học này. Bạn có muốn bắt đầu học ngay không?'))
      .toBeInTheDocument();
    expect(mocks.navigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Học ngay' }));

    expect(mocks.navigate).toHaveBeenCalledWith('/student/courses/course-1/learn');
  });

  it('confirms renewal after an expired enrollment is paid from the wallet', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.createCheckout.mockResolvedValue({ orderId: 'renewal-order' });

    render(
      <MemoryRouter>
        <CourseStickyCard
          course={{
            ...course,
            price: 250_000,
            hasExpiredEnrollment: true,
          }}
        />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Gia hạn khóa học' }));
    fireEvent.click(await screen.findByRole('button', { name: /Thanh toán toàn bộ bằng ví/ }));

    expect(await screen.findByRole('dialog', { name: 'Gia hạn khóa học thành công' }))
      .toBeInTheDocument();
    expect(screen.getByText('Quyền truy cập khóa học đã được gia hạn. Bạn có muốn học ngay không?'))
      .toBeInTheDocument();
  });

  it('offers a split wallet and VNPay checkout when the wallet only covers part of the price', async () => {
    mocks.getAuthSession.mockReturnValue({ token: 'student-token' });
    mocks.getStudentWallet.mockResolvedValue({ availableBalance: 250_000 });
    mocks.createCheckout.mockResolvedValue({ orderId: 'split-order', paymentUrl: 'https://vnpay.test' });

    render(
      <MemoryRouter>
        <CourseStickyCard course={{ ...course, price: 500_000 }} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Mua ngay' }));
    const splitButton = await screen.findByRole('button', {
      name: /Dùng ví 250\.000 ₫ \+ VNPay phần còn lại/,
    });
    fireEvent.click(splitButton);

    expect(mocks.createCheckout).toHaveBeenCalledWith('course-1', 'WALLET_VNPAY');
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/checkout/split-order', {
      state: { paymentUrl: 'https://vnpay.test' },
    }));
  });
});
