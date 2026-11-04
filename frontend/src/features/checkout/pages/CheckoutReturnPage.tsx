import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { confirmPaymentReturn, getOrder } from '../services/checkoutService';
import type { OrderResponse } from '../types';
import { ROUTES } from '../../../shared/constants/routes';

type PollState = 'polling' | 'paid' | 'cancelled' | 'failed' | 'timeout';

const POLL_INTERVAL_MS = 2000;
const MAX_ATTEMPTS = 20; // ~40s

export const CheckoutReturnPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const orderId = searchParams.get('orderId') ?? '';

  const [state, setState] = useState<PollState>('polling');
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (!orderId) {
      setState('timeout');
      return;
    }

    let active = true;
    let timer: ReturnType<typeof setTimeout>;

    const hasSignedProviderReturn = searchParams.has('vnp_TxnRef')
      && searchParams.has('vnp_SecureHash');

    const poll = async () => {
      try {
        const data = await getOrder(orderId);
        if (!active) return;
        setOrder(data);
        if (data.status === 'PAID') {
          setState('paid');
          return;
        }
        if (data.status === 'CANCELLED') {
          setState('cancelled');
          return;
        }
        if (data.status === 'FAILED') {
          setState('failed');
          return;
        }
      } catch {
        // transient — keep polling until attempts exhausted
      }

      attemptsRef.current += 1;
      if (attemptsRef.current >= MAX_ATTEMPTS) {
        if (active) setState('timeout');
        return;
      }
      timer = setTimeout(poll, POLL_INTERVAL_MS);
    };

    const processReturnAndPoll = async () => {
      if (hasSignedProviderReturn) {
        try {
          const acknowledgement = await confirmPaymentReturn(searchParams);
          if (!active) return;
          if (acknowledgement.RspCode !== '00') {
            setState('failed');
            return;
          }
        } catch {
          // The return endpoint may be temporarily unavailable. Polling remains
          // useful because the authoritative IPN can still arrive independently.
        }
      }
      if (active) {
        poll();
      }
    };

    processReturnAndPoll();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [orderId, searchParams]);

  const firstCourseId = order?.items[0]?.courseId;
  const isTopUp = order?.type === 'WALLET_TOPUP';

  return (
    <div className="max-w-xl mx-auto px-4 py-16 text-center">
      {state === 'polling' && (
        <>
          <Spinner />
          <h1 className="text-xl font-bold text-slate-900 mt-6">Đang xác nhận thanh toán…</h1>
          <p className="text-sm text-slate-500 mt-2">
            Thanh toán đang được xử lý. Vui lòng chờ xác nhận. Đừng đóng trang này.
          </p>
        </>
      )}

      {state === 'paid' && (
        <>
          <StatusIcon variant="success" />
          <h1 className="text-2xl font-extrabold text-slate-900 mt-6">
            {isTopUp ? 'Nạp ví thành công!' : 'Thanh toán thành công!'}
          </h1>
          <p className="text-sm text-slate-500 mt-2">
            {isTopUp
              ? 'Nạp ví thành công. Số dư của bạn đã được cập nhật.'
              : 'Bạn đã sở hữu khóa học này. Bạn có muốn bắt đầu học ngay không?'}
          </p>
          <div className="mt-8 flex flex-col sm:flex-row gap-3 justify-center">
            {isTopUp ? (
              <button
                onClick={() => navigate(ROUTES.STUDENT.PAYMENTS)}
                className="bg-gradient-to-r from-red-600 to-rose-700 hover:from-red-500 hover:to-rose-600 text-white font-bold py-3 px-6 rounded-xl shadow-lg"
              >
                Về lịch sử thanh toán
              </button>
            ) : (
              <>
                {firstCourseId && (
                  <button
                    onClick={() => navigate(ROUTES.STUDENT.COURSE_LEARN(firstCourseId))}
                    className="bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold py-3 px-6 rounded-xl shadow-lg shadow-indigo-500/30"
                  >
                    Học ngay
                  </button>
                )}
                <button
                  onClick={() => navigate(ROUTES.STUDENT.MY_COURSES)}
                  className="bg-slate-100 hover:bg-slate-200 text-slate-800 font-bold py-3 px-6 rounded-xl"
                >
                  Để sau
                </button>
              </>
            )}
          </div>
        </>
      )}

      {state === 'cancelled' && (
        <>
          <StatusIcon variant="pending" />
          <h1 className="text-2xl font-extrabold text-slate-900 mt-6">Thanh toán đã được hủy</h1>
          <p className="text-sm text-slate-500 mt-2">
            Bạn đã hủy giao dịch. Đơn hàng vẫn được lưu trong lịch sử thanh toán để đối soát.
          </p>
          <button
            onClick={() => navigate(ROUTES.STUDENT.PAYMENTS)}
            className="mt-8 bg-slate-900 hover:bg-slate-800 text-white font-bold py-3 px-6 rounded-xl"
          >
            Xem lịch sử thanh toán
          </button>
        </>
      )}

      {state === 'failed' && (
        <>
          <StatusIcon variant="error" />
          <h1 className="text-2xl font-extrabold text-slate-900 mt-6">Thanh toán không thành công</h1>
          <p className="text-sm text-slate-500 mt-2">
            Thanh toán không thành công. Vui lòng thử lại.
          </p>
          <button
            onClick={() => navigate(ROUTES.PUBLIC.COURSE_BROWSE)}
            className="mt-8 bg-slate-900 hover:bg-slate-800 text-white font-bold py-3 px-6 rounded-xl"
          >
            Quay lại danh sách khóa học
          </button>
        </>
      )}

      {state === 'timeout' && (
        <>
          <StatusIcon variant="pending" />
          <h1 className="text-2xl font-extrabold text-slate-900 mt-6">Chưa nhận được xác nhận</h1>
          <p className="text-sm text-slate-500 mt-2">
            Thanh toán có thể vẫn đang được xử lý. Hãy kiểm tra lại trong mục "Khóa học của tôi" sau ít phút.
          </p>
          <button
            onClick={() => navigate(ROUTES.STUDENT.MY_COURSES)}
            className="mt-8 bg-slate-900 hover:bg-slate-800 text-white font-bold py-3 px-6 rounded-xl"
          >
            Khóa học của tôi
          </button>
        </>
      )}
    </div>
  );
};

const Spinner = () => (
  <div className="mx-auto w-12 h-12 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
);

const StatusIcon = ({ variant }: { variant: 'success' | 'error' | 'pending' }) => {
  const styles = {
    success: 'bg-emerald-100 text-emerald-600',
    error: 'bg-red-100 text-red-600',
    pending: 'bg-amber-100 text-amber-600',
  }[variant];
  const symbol = { success: '✓', error: '✕', pending: '…' }[variant];
  return (
    <div className={`mx-auto w-16 h-16 rounded-full flex items-center justify-center text-3xl font-bold ${styles}`}>
      {symbol}
    </div>
  );
};

export default CheckoutReturnPage;
