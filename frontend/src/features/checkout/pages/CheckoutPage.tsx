import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams, Link as RouterLink } from 'react-router-dom';
import { cancelOrder, getOrder, simulatePayment } from '../services/checkoutService';
import type { OrderResponse } from '../types';
import { ROUTES } from '../../../shared/constants/routes';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import fallbackCourseCover from '../../../assets/course1.png';

interface CheckoutLocationState {
  paymentUrl?: string;
}

const PAYMENT_EXPIRY_MINUTES = 15;
const PAYMENT_EXPIRY_SECONDS = PAYMENT_EXPIRY_MINUTES * 60;

export const CheckoutPage = () => {
  const { orderId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const paymentUrl = (location.state as CheckoutLocationState | null)?.paymentUrl;
  const devSimulatorEnabled = import.meta.env.VITE_PAYMENT_DEV_SIMULATOR_ENABLED === 'true';

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    getOrder(orderId)
      .then((data) => {
        if (!active) return;
        setOrder(data);
        if (data.status === 'PAID') {
          navigate(`/checkout/return?orderId=${orderId}`, { replace: true });
        }
      })
      .catch(() => active && setError('Không tải được thông tin đơn hàng.'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [orderId, navigate]);

  const pendingOrderId = order?.status === 'PENDING' ? order.id : undefined;
  const pendingOrderCreatedAt = order?.status === 'PENDING' ? order.createdAt : undefined;

  useEffect(() => {
    if (!pendingOrderId || !pendingOrderCreatedAt) {
      setRemainingSeconds(null);
      return;
    }

    const createdAt = Date.parse(pendingOrderCreatedAt);
    if (!Number.isFinite(createdAt)) {
      setRemainingSeconds(null);
      return;
    }

    const expiresAt = createdAt + PAYMENT_EXPIRY_SECONDS * 1000;
    let refreshedAfterExpiry = false;

    const updateRemainingTime = () => {
      const seconds = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
      setRemainingSeconds(seconds);

      if (seconds === 0 && !refreshedAfterExpiry) {
        refreshedAfterExpiry = true;
        getOrder(pendingOrderId).then(setOrder).catch(() => undefined);
      }
    };

    updateRemainingTime();
    const timer = window.setInterval(updateRemainingTime, 1000);
    return () => window.clearInterval(timer);
  }, [pendingOrderId, pendingOrderCreatedAt]);

  const handlePayWithVnPay = () => {
    if (paymentUrl) {
      window.location.href = paymentUrl;
    }
  };

  const handleSimulate = async (success: boolean) => {
    if (!order) return;
    setProcessing(true);
    setError(null);
    try {
      await simulatePayment(order.orderCode, success);
      navigate(`/checkout/return?orderId=${orderId}`);
    } catch {
      setError('Không thể giả lập thanh toán. Vui lòng thử lại.');
      setProcessing(false);
    }
  };

  const handleCancelOrder = async () => {
    if (!order || order.status !== 'PENDING') return;

    setProcessing(true);
    setError(null);
    try {
      const cancelledOrder = await cancelOrder(order.id);
      setOrder(cancelledOrder);
    } catch {
      setError('Không thể hủy đơn hàng. Vui lòng thử lại.');
    } finally {
      setProcessing(false);
    }
  };

  const formatRemainingTime = (seconds: number | null) => {
    if (seconds === null) return 'đang xác định thời hạn';
    if (seconds === 0) return 'đã hết hạn';
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${minutes}:${remaining.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return <CenteredMessage text="Đang tải đơn hàng…" />;
  }

  if (error && !order) {
    return <CenteredMessage text={error} />;
  }

  if (!order) {
    return null;
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-10">
      <h1 className="text-2xl font-extrabold text-slate-900 mb-1">Thanh toán đơn hàng</h1>
      <p className="text-sm text-slate-500 mb-6">Mã đơn hàng: <span className="font-mono">{order.orderCode}</span></p>

      <div className="bg-white rounded-2xl shadow-lg border border-slate-200/60 overflow-hidden">
        <div className="p-6 border-b border-slate-100">
          {order.items.map((item) => (
            <div key={item.courseId} className="flex items-center gap-4">
              <CheckoutCourseThumbnail
                thumbnailUrl={item.courseThumbnailUrl}
                courseTitle={item.courseTitle}
              />
              <div className="flex-1">
                <p className="font-semibold text-slate-900">{item.courseTitle}</p>
              </div>
              <p className="font-bold text-slate-900">
                {item.price.toLocaleString('vi-VN')} {order.currency}
              </p>
            </div>
          ))}
        </div>

        <div className="p-6 space-y-2">
          <div className="flex items-center justify-between text-slate-600">
            <span>Tổng cộng</span>
            <span className="font-semibold">{order.totalAmount.toLocaleString('vi-VN')} {order.currency}</span>
          </div>
          {(order.walletAmount ?? 0) > 0 && (
            <>
              <div className="flex items-center justify-between text-emerald-600">
                <span>Thanh toán từ ví</span>
                <span className="font-semibold">− {(order.walletAmount ?? 0).toLocaleString('vi-VN')} {order.currency}</span>
              </div>
              <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                <span className="text-slate-800 font-medium">Còn phải trả qua VNPay</span>
                <span className="text-2xl font-extrabold text-red-600">
                  {(order.totalAmount - (order.walletAmount ?? 0)).toLocaleString('vi-VN')} {order.currency}
                </span>
              </div>
            </>
          )}
          {(order.walletAmount ?? 0) === 0 && (
            <div className="flex items-center justify-between pt-2 border-t border-slate-100">
              <span className="text-slate-800 font-medium">Cần thanh toán</span>
              <span className="text-2xl font-extrabold text-slate-900">
                {order.totalAmount.toLocaleString('vi-VN')} {order.currency}
              </span>
            </div>
          )}
        </div>
      </div>

      {order.status === 'PENDING' && (
        <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900" role="status">
          <p className="font-bold">Chờ thanh toán VNPay</p>
          <p className="mt-1 text-sm">
            Đơn hàng được giữ trong {PAYMENT_EXPIRY_MINUTES} phút. Nếu bạn không hoàn tất thanh toán,
            hệ thống sẽ tự động hủy đơn.
          </p>
          <p className="mt-2 text-sm font-semibold">
            Thời gian còn lại: {formatRemainingTime(remainingSeconds)}
          </p>
        </div>
      )}

      {order.status === 'CANCELLED' && (
        <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-700" role="status">
          <p className="font-bold">Đơn hàng đã được hủy</p>
          <p className="mt-1 text-sm">Đơn vẫn được lưu trong lịch sử thanh toán để đối soát.</p>
        </div>
      )}

      {order.status === 'FAILED' && (
        <div className="mt-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-red-800" role="status">
          <p className="font-bold">Thanh toán không thành công</p>
          <p className="mt-1 text-sm">Bạn có thể quay lại trang khóa học để tạo một đơn mới.</p>
        </div>
      )}

      {error && <p className="text-red-600 text-sm mt-4">{error}</p>}

      <div className="mt-6 space-y-3">
        {order.status === 'PENDING' ? (
          <>
            <button
              onClick={handlePayWithVnPay}
              disabled={!paymentUrl || processing || remainingSeconds === 0}
              className="w-full bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 disabled:opacity-50 text-white font-bold py-3.5 px-4 rounded-xl transition-all shadow-lg shadow-indigo-500/30"
            >
              Thanh toán qua VNPay
            </button>
            {!paymentUrl && (
              <p className="text-center text-xs text-amber-600">
                Liên kết VNPay đã hết hiệu lực ở phiên này. Hãy tạo lại đơn từ trang khóa học.
              </p>
            )}
            <button
              type="button"
              onClick={handleCancelOrder}
              disabled={processing}
              className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 font-semibold text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {processing ? 'Đang xử lý…' : 'Hủy đơn hàng'}
            </button>

            {devSimulatorEnabled && (
          <>
            <div className="relative py-2">
              <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-slate-200" /></div>
              <div className="relative flex justify-center"><span className="bg-white px-3 text-xs text-slate-400">Chế độ Sandbox / Dev</span></div>
            </div>

            <button
              onClick={() => handleSimulate(true)}
              disabled={processing}
              className="w-full bg-emerald-50 hover:bg-emerald-100 disabled:opacity-50 text-emerald-700 font-bold py-3 px-4 rounded-xl transition-all border border-emerald-200"
            >
              {processing ? 'Đang xử lý…' : 'Giả lập thanh toán thành công (IPN)'}
            </button>
            <button
              onClick={() => handleSimulate(false)}
              disabled={processing}
              className="w-full bg-slate-50 hover:bg-slate-100 disabled:opacity-50 text-slate-600 font-medium py-2.5 px-4 rounded-xl transition-all border border-slate-200 text-sm"
            >
              Giả lập thanh toán thất bại
            </button>
          </>
            )}
          </>
        ) : (
          <RouterLink
            to={ROUTES.STUDENT.PAYMENTS}
            className="block w-full rounded-xl bg-slate-900 px-4 py-3.5 text-center font-bold text-white transition-colors hover:bg-slate-800"
          >
            Xem lịch sử thanh toán
          </RouterLink>
        )}
      </div>

      <p className="text-center text-xs text-slate-400 mt-6">
        Trước khi thanh toán, vui lòng tham khảo{' '}
        <RouterLink to={ROUTES.PUBLIC.TERMS} target="_blank" className="text-indigo-600 hover:underline">
          Điều khoản sử dụng
        </RouterLink>{' '}
        và{' '}
        <RouterLink to={ROUTES.PUBLIC.REFUND_POLICY} target="_blank" className="text-indigo-600 hover:underline">
          Chính sách hoàn tiền
        </RouterLink>.
        <br />
        Thanh toán chỉ được xác nhận qua webhook (IPN) từ phía máy chủ, không qua trình duyệt.
      </p>
    </div>
  );
};

const CenteredMessage = ({ text }: { text: string }) => (
  <div className="max-w-2xl mx-auto px-4 py-20 text-center text-slate-500">{text}</div>
);

const CheckoutCourseThumbnail = ({
  thumbnailUrl,
  courseTitle,
}: {
  thumbnailUrl?: string | null;
  courseTitle: string;
}) => {
  const [imageFailed, setImageFailed] = useState(false);
  const resolvedThumbnailUrl = resolvePublicAssetUrl(thumbnailUrl);
  const useFallback = !resolvedThumbnailUrl || imageFailed;

  return (
    <img
      data-testid={useFallback ? 'checkout-course-cover-fallback' : 'checkout-course-cover'}
      src={useFallback ? fallbackCourseCover : resolvedThumbnailUrl}
      alt={useFallback ? `Ảnh mặc định cho ${courseTitle}` : courseTitle}
      onError={() => setImageFailed(true)}
      className="w-20 h-14 shrink-0 object-cover rounded-lg bg-slate-100"
    />
  );
};

export default CheckoutPage;
