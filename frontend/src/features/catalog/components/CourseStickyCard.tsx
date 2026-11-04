import { forwardRef, useCallback, useEffect, useImperativeHandle, useState } from 'react';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import { CheckCircle2, PlayCircle, Target, BookOpen, Infinity as InfinityIcon, Clock3, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { WishlistToggleButton } from '../../wishlist/components/WishlistToggleButton';
import { createCheckout } from '../../checkout/services/checkoutService';
import { getAuthSession } from '../../../shared/auth/authSession';
import { getStudentWallet } from '../../wallet/services/studentWalletService';
import { ROUTES } from '../../../shared/constants/routes';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { hasCourseAccessPeriodEnded } from '../utils/courseAccess';

interface CourseStickyCardProps {
  course: PublicCourseDetail;
}

export interface CourseStickyCardHandle {
  openPurchaseOptions: () => void;
}

type EnrollmentSuccess = {
  payment: 'FREE' | 'PAID';
  renewed: boolean;
};

export const CourseStickyCard = forwardRef<CourseStickyCardHandle, CourseStickyCardProps>(({ course }, ref) => {
  const [buying, setBuying] = useState(false);
  const [buyError, setBuyError] = useState<string | null>(null);
  const [showPaymentOptions, setShowPaymentOptions] = useState(false);
  const [showCombinedPaymentOption, setShowCombinedPaymentOption] = useState(false);
  const [enrollmentSuccess, setEnrollmentSuccess] = useState<EnrollmentSuccess | null>(null);
  const [locallyEnrolled, setLocallyEnrolled] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const [walletAvailableBalance, setWalletAvailableBalance] = useState<number | null>(null);
  const navigate = useNavigate();
  const thumbnailUrl = resolvePublicAssetUrl(course.thumbnailUrl);
  const accessPeriodEnded = hasCourseAccessPeriodEnded(course.accessExpiresAt);
  const isRenewal = Boolean(course.hasExpiredEnrollment);
  const hasCurrentAccess = !accessPeriodEnded && (course.isEnrolled || locallyEnrolled);

  useEffect(() => {
    setWalletAvailableBalance(null);
    if (course.price <= 0 || !getAuthSession('public')) return;

    let active = true;
    void getStudentWallet()
      .then((wallet) => {
        if (active) setWalletAvailableBalance(wallet.availableBalance ?? 0);
      })
      .catch(() => {
        // The checkout API remains authoritative when this advisory lookup fails.
        if (active) setWalletAvailableBalance(null);
      });

    return () => {
      active = false;
    };
  }, [course.id, course.price]);

  const handleContinueLearning = () => navigate(ROUTES.STUDENT.COURSE_LEARN(course.id));

  const handleBuy = useCallback(async (paymentMethod: 'VNPAY' | 'WALLET' | 'WALLET_VNPAY' = 'VNPAY') => {
    if (accessPeriodEnded) {
      setBuyError('Khóa học đã kết thúc thời hạn truy cập và hiện không thể mua hoặc gia hạn.');
      setShowPaymentOptions(false);
      return;
    }
    if (!getAuthSession('public')) {
      navigate(ROUTES.PUBLIC.LOGIN);
      return;
    }
    setBuying(true);
    setBuyError(null);
    try {
      const checkout = await createCheckout(course.id, paymentMethod);
      if (!checkout.paymentUrl) {
        // Free course OR paid instantly from wallet — enrollment is complete, let the student choose when to learn.
        setLocallyEnrolled(true);
        setEnrollmentSuccess({
          payment: course.price === 0 ? 'FREE' : 'PAID',
          renewed: isRenewal,
        });
        setShowPaymentOptions(false);
        setBuying(false);
        return;
      }
      navigate(`/checkout/${checkout.orderId}`, { state: { paymentUrl: checkout.paymentUrl } });
    } catch (err) {
      const code = (err as { response?: { data?: { messageCode?: string } } })?.response?.data?.messageCode;
      if (code === 'WALLET_INSUFFICIENT_BALANCE') {
        // Only reveal the combined method after the wallet-only attempt is rejected.
        setShowPaymentOptions(true);
        setShowCombinedPaymentOption(true);
        setBuyError('Số dư ví không đủ. Bạn có thể chọn ví + VNPay phần còn lại hoặc hủy.');
      } else {
        const message = code === 'ORDER_ALREADY_ENROLLED'
          ? 'Bạn đã sở hữu khóa học này.'
          : code === 'ORDER_COURSE_ACCESS_ENDED'
            ? 'Khóa học đã kết thúc thời hạn truy cập và hiện không thể mua hoặc gia hạn.'
            : code === 'COMMON_INTERNAL_ERROR'
              ? 'Thanh toán chưa hoàn tất và số dư ví chưa bị trừ. Vui lòng thử lại.'
              : 'Không thể tạo đơn hàng. Vui lòng thử lại.';
        setBuyError(message);
      }
      setBuying(false);
    }
  }, [accessPeriodEnded, course.id, course.price, isRenewal, navigate]);

  const handlePurchaseClick = useCallback(() => {
    if (accessPeriodEnded) {
      setBuyError('Khóa học đã kết thúc thời hạn truy cập và hiện không thể mua hoặc gia hạn.');
      setShowPaymentOptions(false);
      return;
    }
    if (course.price === 0) {
      void handleBuy('VNPAY');
      return;
    }
    setBuyError(null);
    setShowCombinedPaymentOption(false);
    setShowPaymentOptions(true);
  }, [accessPeriodEnded, course.price, handleBuy]);

  useImperativeHandle(ref, () => ({ openPurchaseOptions: handlePurchaseClick }), [handlePurchaseClick]);

  // Calculate course stats dynamically (partially offloaded to backend)
  let totalReadingBlocks = 0;
  let hasQuiz = false;

  course.modules.forEach((module) => {
    module.blocks.forEach((block) => {
      if (block.type === 'TEXT' || block.type === 'WRITING' || block.type === 'FLASHCARD') {
        totalReadingBlocks++;
      } else if (block.type === 'QUIZ') {
        hasQuiz = true;
      }
    });
  });

  const totalVideoHours = Math.round(((course.totalDurationMinutes || 0) / 60) * 10) / 10;

  return (
    <>
      <div className="bg-white text-slate-800 shadow-2xl rounded-2xl overflow-hidden border border-slate-200/60 backdrop-blur-xl transform transition-all duration-300 hover:shadow-slate-500/10">
        {/* Thumbnail Image */}
        <div className="relative aspect-video bg-slate-100 flex items-center justify-center p-1">
          {thumbnailUrl && !imageFailed ? (
            <img
              src={thumbnailUrl}
              alt={`Ảnh bìa khóa học ${course.title}`}
              className="w-full h-full object-cover rounded-xl"
              onError={() => setImageFailed(true)}
            />
          ) : (
            <div
              role="img"
              aria-label={`Khóa học ${course.title} chưa có ảnh bìa`}
              className="flex flex-col items-center justify-center text-rose-200/40 w-full h-full bg-gradient-to-br from-rose-950 via-rose-900 to-slate-900 rounded-xl relative overflow-hidden"
            >
              <span className="text-8xl font-black absolute opacity-20 transform -rotate-12 translate-x-4 translate-y-4">作文</span>
              <span className="text-sm font-semibold uppercase tracking-wider opacity-90 z-10 text-rose-100">ManabiHub</span>
            </div>
          )}
          {/* Wishlist Button Overlay */}
          <div className="absolute top-3 right-3 p-1 bg-white/80 backdrop-blur-sm rounded-full shadow z-20 flex items-center justify-center">
            <WishlistToggleButton courseId={course.id} variant="icon" />
          </div>
        </div>

      <div className="p-7">
        <div className="mb-6 flex items-baseline gap-2">
          <span className="text-2xl font-bold text-slate-900">
            {course.price === 0 ? (
              <span className="text-emerald-500">Miễn phí</span>
            ) : (
              `${course.price.toLocaleString('vi-VN')} ₫`
            )}
          </span>
        </div>

        {hasCurrentAccess ? (
          <button
            onClick={handleContinueLearning}
            className="w-full bg-slate-900 hover:bg-slate-800 text-white font-bold py-3.5 px-4 rounded-xl transition-all shadow-md hover:shadow-xl hover:-translate-y-0.5 mb-4"
          >
            Tiếp tục học
          </button>
        ) : accessPeriodEnded ? (
          <div className="mb-4">
            <button
              type="button"
              disabled
              className="w-full cursor-not-allowed rounded-xl bg-slate-200 px-4 py-3.5 font-bold text-slate-500"
            >
              Đã hết hạn truy cập
            </button>
            <p className="mt-3 text-center text-xs font-medium leading-5 text-red-600">
              Khóa học đã kết thúc thời hạn truy cập. Vui lòng chờ giảng viên mở lại thời hạn trước khi gia hạn.
            </p>
          </div>
        ) : (
          <>
            <button
              onClick={handlePurchaseClick}
              disabled={buying}
              className="bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white w-full py-3 rounded-xl font-semibold mb-3 transition-colors"
            >
              {buying
                ? 'Đang xử lý…'
                : isRenewal
                  ? course.price === 0 ? 'Gia hạn miễn phí' : 'Gia hạn khóa học'
                  : course.price === 0 ? 'Ghi danh ngay' : 'Mua ngay'}
            </button>
            {buyError && !showPaymentOptions && <p className="text-center text-xs text-red-600 font-medium mb-3">{buyError}</p>}
          </>
        )}

        <div className="text-sm border-t border-slate-100 pt-6 mt-2">
          <h4 className="font-bold mb-4 text-slate-900 text-base">Khóa học này bao gồm:</h4>
          <ul className="space-y-3 text-slate-600">
            {totalVideoHours > 0 && (
              <li className="flex items-center group">
                <PlayCircle className="w-4 h-4 mr-3 text-slate-500 group-hover:text-red-600 transition-colors" />
                <span className="group-hover:text-slate-900 transition-colors">{totalVideoHours} giờ video theo yêu cầu</span>
              </li>
            )}
            {totalReadingBlocks > 0 && (
              <li className="flex items-center group">
                <BookOpen className="w-4 h-4 mr-3 text-slate-500 group-hover:text-red-600 transition-colors" />
                <span className="group-hover:text-slate-900 transition-colors">{totalReadingBlocks} bài đọc và tài liệu</span>
              </li>
            )}
            {hasQuiz && (
              <li className="flex items-center group">
                <Target className="w-4 h-4 mr-3 text-slate-500 group-hover:text-red-600 transition-colors" />
                <span className="group-hover:text-slate-900 transition-colors">Bài tập thực hành & Trắc nghiệm</span>
              </li>
            )}
            <li className="flex items-center group">
              <Clock3 className="w-4 h-4 mr-3 text-slate-500 group-hover:text-red-600 transition-colors" />
              <span className="group-hover:text-slate-900 transition-colors">
                Thời hạn truy cập: {course.accessExpiresAt
                  ? `đến ${new Date(course.accessExpiresAt).toLocaleDateString('vi-VN')}`
                  : `${course.accessDurationDays ?? 180} ngày kể từ ngày ghi danh`}
              </span>
            </li>
            <li className="flex items-center group">
              <InfinityIcon className="w-4 h-4 mr-3 text-slate-500 group-hover:text-red-600 transition-colors" />
              <span className="group-hover:text-slate-900 transition-colors">Truy cập nội dung sau khi ghi danh</span>
            </li>
          </ul>
        </div>
      </div>
      </div>

      {showPaymentOptions && course.price > 0 && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="payment-method-title"
        >
          <div className="w-full max-w-md rounded-3xl bg-white p-7 shadow-2xl">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 id="payment-method-title" className="text-xl font-extrabold text-slate-900">
                  Chọn phương thức thanh toán
                </h2>
                <p className="mt-2 text-sm text-slate-600">
                  Tổng tiền: <span className="font-bold text-slate-900">{course.price.toLocaleString('vi-VN')} {course.currency}</span>
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  setShowPaymentOptions(false);
                  setShowCombinedPaymentOption(false);
                  setBuyError(null);
                }}
                disabled={buying}
                aria-label="Đóng lựa chọn thanh toán"
                title="Đóng lựa chọn thanh toán"
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border-2 border-slate-300 bg-slate-100 text-slate-700 transition-colors hover:border-red-500 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <X className="h-6 w-6" strokeWidth={3} aria-hidden="true" />
              </button>
            </div>

            {buyError && (
              <p className="mt-4 rounded-xl bg-rose-50 px-3 py-2 text-sm font-medium text-red-600" role="alert">
                {buyError}
              </p>
            )}

            <div className="mt-6 space-y-3">
              <button
                type="button"
                onClick={() => void handleBuy('VNPAY')}
                disabled={buying}
                className="w-full rounded-xl bg-red-600 px-4 py-3 text-left font-semibold text-white transition-colors hover:bg-red-700 disabled:opacity-60"
              >
                <span className="block">Thanh toán toàn bộ qua VNPay</span>
                <span className="mt-1 block text-xs font-normal text-red-100">Chuyển sang cổng thanh toán VNPay</span>
              </button>
              <button
                type="button"
                onClick={() => void handleBuy('WALLET')}
                disabled={buying}
                className="w-full rounded-xl border border-red-600 px-4 py-3 text-left font-semibold text-red-600 transition-colors hover:bg-red-50 disabled:opacity-60"
              >
                <span className="block">Thanh toán toàn bộ bằng ví</span>
                <span className="mt-1 block text-xs font-normal text-slate-500">Dùng số dư ví ManabiHub</span>
              </button>
              {(showCombinedPaymentOption || (walletAvailableBalance !== null && walletAvailableBalance > 0 && walletAvailableBalance < course.price)) && (
                <>
                  <button
                    type="button"
                    onClick={() => void handleBuy('WALLET_VNPAY')}
                    disabled={buying}
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 text-left font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-60"
                  >
                    <span className="block">
                      {walletAvailableBalance !== null && walletAvailableBalance > 0 && walletAvailableBalance < course.price
                        ? `Dùng ví ${walletAvailableBalance.toLocaleString('vi-VN')} ₫ + VNPay phần còn lại`
                        : 'Ví + VNPay phần còn lại'}
                    </span>
                    <span className="mt-1 block text-xs font-normal text-slate-500">Ưu tiên dùng số dư ví trước</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setShowPaymentOptions(false);
                      setShowCombinedPaymentOption(false);
                      setBuyError(null);
                    }}
                    disabled={buying}
                    className="w-full rounded-xl border border-slate-300 px-4 py-2.5 font-semibold text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Hủy
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {enrollmentSuccess && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="course-access-success-title"
          aria-describedby="course-access-success-description"
        >
          <div className="w-full max-w-md rounded-3xl bg-white p-7 text-center shadow-2xl">
            <CheckCircle2 className="mx-auto h-16 w-16 text-emerald-500" aria-hidden="true" />
            <h2 id="course-access-success-title" className="mt-5 text-2xl font-extrabold text-slate-900">
              {enrollmentSuccess.renewed
                ? 'Gia hạn khóa học thành công'
                : enrollmentSuccess.payment === 'FREE'
                  ? 'Đăng ký khóa học thành công'
                  : 'Thanh toán thành công'}
            </h2>
            <p id="course-access-success-description" className="mt-3 text-sm leading-6 text-slate-600">
              {enrollmentSuccess.renewed
                ? 'Quyền truy cập khóa học đã được gia hạn. Bạn có muốn học ngay không?'
                : enrollmentSuccess.payment === 'FREE'
                  ? 'Bạn đã tham gia khóa học này. Bạn có muốn bắt đầu học ngay không?'
                  : 'Bạn đã sở hữu khóa học này. Bạn có muốn bắt đầu học ngay không?'}
            </p>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <button
                type="button"
                onClick={handleContinueLearning}
                className="flex-1 rounded-xl bg-slate-900 px-5 py-3 font-bold text-white transition-colors hover:bg-slate-800"
              >
                Học ngay
              </button>
              <button
                type="button"
                onClick={() => setEnrollmentSuccess(null)}
                className="flex-1 rounded-xl bg-slate-100 px-5 py-3 font-bold text-slate-700 transition-colors hover:bg-slate-200"
              >
                Để sau
              </button>
            </div>
          </div>
        </div>
      )}

    </>
  );
});

CourseStickyCard.displayName = 'CourseStickyCard';
