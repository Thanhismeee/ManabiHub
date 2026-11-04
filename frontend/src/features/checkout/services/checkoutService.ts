import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type { CheckoutResponse, IpnAck, OrderResponse } from '../types';

/**
 * Creates an order for a course and initiates payment.
 * - {@code VNPAY} (default): returns a payment URL to redirect to.
 * - {@code WALLET}: pays instantly from wallet balance; returns paymentUrl null + status PAID.
 */
export async function createCheckout(
  courseId: string,
  paymentMethod: 'VNPAY' | 'WALLET' | 'WALLET_VNPAY' = 'VNPAY',
): Promise<CheckoutResponse> {
  const response = await axiosClient.post<ApiResponse<CheckoutResponse>>(
    ENDPOINTS.orders.create,
    { courseId, paymentMethod },
  );
  return response.data.data;
}

/** Fetches an order's current status — used to poll after returning from the payment provider. */
export async function getOrder(orderId: string): Promise<OrderResponse> {
  const response = await axiosClient.get<ApiResponse<OrderResponse>>(
    ENDPOINTS.orders.detail(orderId),
  );
  return response.data.data;
}

/** Cancels the authenticated student's pending order and releases any wallet reservation. */
export async function cancelOrder(orderId: string): Promise<OrderResponse> {
  const response = await axiosClient.post<ApiResponse<OrderResponse>>(
    ENDPOINTS.orders.cancel(orderId),
  );
  return response.data.data;
}

/**
 * Local-only helper: asks the backend to simulate a signed VNPay IPN callback for an order,
 * so the full confirmation flow can be exercised without a public tunnel to VNPay.
 */
export async function simulatePayment(orderCode: string, success = true): Promise<IpnAck> {
  const response = await axiosClient.post<IpnAck>(
    ENDPOINTS.payments.devIpn,
    null,
    { params: { orderCode, success } },
  );
  return response.data;
}

/**
 * Sends the VNPay browser-return parameters to the backend. The backend verifies
 * the checksum, amount and provider status, then idempotently confirms successful
 * payments when the server-to-server IPN is unavailable in sandbox/demo.
 */
export async function confirmPaymentReturn(params: URLSearchParams): Promise<IpnAck> {
  const vnpParams: Record<string, string> = {};
  params.forEach((value, key) => {
    if (key.startsWith('vnp_')) {
      vnpParams[key] = value;
    }
  });

  const response = await axiosClient.get<IpnAck>(
    ENDPOINTS.payments.vnpayReturn,
    { params: vnpParams },
  );
  return response.data;
}
