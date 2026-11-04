import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CheckoutPage } from './CheckoutPage';

const mocks = vi.hoisted(() => ({
  getOrder: vi.fn(),
  cancelOrder: vi.fn(),
  simulatePayment: vi.fn(),
}));

vi.mock('../services/checkoutService', () => ({
  getOrder: mocks.getOrder,
  cancelOrder: mocks.cancelOrder,
  simulatePayment: mocks.simulatePayment,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('CheckoutPage', () => {
  it('explains the VNPay deadline and lets the student cancel a pending order', async () => {
    const pendingOrder = {
      id: 'order-1',
      orderCode: 'MHB-001',
      totalAmount: 250_000,
      currency: 'VND',
      status: 'PENDING' as const,
      type: 'COURSE' as const,
      createdAt: new Date(Date.now() - 60_000).toISOString(),
      items: [{
        id: 'item-1',
        courseId: 'course-1',
        courseTitle: 'Kanji N5',
        price: 250_000,
      }],
    };
    const cancelledOrder = { ...pendingOrder, status: 'CANCELLED' as const };

    mocks.getOrder.mockResolvedValue(pendingOrder);
    mocks.cancelOrder.mockResolvedValue(cancelledOrder);

    render(
      <MemoryRouter initialEntries={['/checkout/order-1']}>
        <Routes>
          <Route path="/checkout/:orderId" element={<CheckoutPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Chờ thanh toán VNPay')).toBeInTheDocument();
    expect(screen.getByText(/Đơn hàng được giữ trong 15 phút/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Hủy đơn hàng' }));

    expect(await screen.findByText('Đơn hàng đã được hủy')).toBeInTheDocument();
    expect(mocks.cancelOrder).toHaveBeenCalledWith('order-1');
    expect(screen.getByRole('link', { name: 'Xem lịch sử thanh toán' })).toBeInTheDocument();
  });

  it('resolves a relative course thumbnail and falls back when the image cannot load', async () => {
    mocks.getOrder.mockResolvedValue({
      id: 'order-with-cover',
      orderCode: 'MHB-COVER',
      totalAmount: 99_000,
      currency: 'VND',
      status: 'CANCELLED',
      type: 'COURSE',
      createdAt: new Date().toISOString(),
      items: [{
        id: 'item-cover',
        courseId: 'course-cover',
        courseTitle: 'Tiếng Nhật du lịch',
        courseThumbnailUrl: '/uploads/courses/travel.webp',
        price: 99_000,
      }],
    });

    render(
      <MemoryRouter initialEntries={['/checkout/order-with-cover']}>
        <Routes>
          <Route path="/checkout/:orderId" element={<CheckoutPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const cover = await screen.findByTestId('checkout-course-cover');
    expect(cover).toHaveAttribute('src', expect.stringContaining('/uploads/courses/travel.webp'));

    fireEvent.error(cover);
    expect(screen.getByTestId('checkout-course-cover-fallback')).toHaveAttribute(
      'alt',
      'Ảnh mặc định cho Tiếng Nhật du lịch',
    );
  });
});
