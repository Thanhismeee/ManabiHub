import { useCallback, useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { useNavigate, useParams, Link as RouterLink } from 'react-router-dom';
import type { ApiResponse } from '../../../shared/types/api';
import { adminRefundApi } from '../api/adminRefundApi';
import type {
  RefundDecisionRequest,
  RefundDetailResponse,
  RefundMoneyValue,
  RefundStatus,
} from '../types';
import { RefundDecisionForm } from './RefundDecisionForm';
import {
  REFUND_DECISION_REASON_LABELS,
  REFUND_STATUS_LABELS,
  eligibilityReasonLabel,
  refundEligibilityLabel,
  refundPaymentStatusLabel,
  refundProviderStatusLabel,
} from '../refundDisplay';
import {
  formatFinanceDateTime,
  formatMoney as formatFinanceMoney,
  waitingCalendarDays,
} from '../../admin-finance/financeDisplay';

const STATUS_META: Record<RefundStatus, { label: string; className: string }> = {
  PENDING: {
    label: REFUND_STATUS_LABELS.PENDING,
    className: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  },
  PROCESSING: {
    label: REFUND_STATUS_LABELS.PROCESSING,
    className: 'bg-blue-50 text-blue-700 border-blue-200',
  },
  APPROVED: {
    label: REFUND_STATUS_LABELS.APPROVED,
    className: 'bg-green-50 text-green-700 border-green-200',
  },
  REJECTED: {
    label: REFUND_STATUS_LABELS.REJECTED,
    className: 'bg-red-50 text-red-700 border-red-200',
  },
  RECONCILIATION_REQUIRED: {
    label: REFUND_STATUS_LABELS.RECONCILIATION_REQUIRED,
    className: 'bg-orange-50 text-orange-800 border-orange-200',
  },
  CANCELLED: {
    label: REFUND_STATUS_LABELS.CANCELLED,
    className: 'bg-gray-50 text-gray-700 border-gray-200',
  },
};

const EVIDENCE_LABELS: Record<string, string> = {
  eligible: 'Đủ điều kiện',
  eligibilityResult: 'Kết quả điều kiện',
  result: 'Kết quả điều kiện',
  refundWindowDays: 'Thời hạn chính sách (ngày)',
  elapsedCalendarDays: 'Số ngày từ khi thanh toán',
  daysSincePurchase: 'Số ngày từ khi thanh toán',
  progressPercent: 'Tiến độ học',
  measuredProgressPercent: 'Tiến độ học',
  progressCompleted: 'Nội dung đã hoàn thành',
  progressTotal: 'Tổng nội dung dùng tính tiến độ',
  progressLimitPercent: 'Ngưỡng tiến độ chính sách',
  progressThresholdPercent: 'Ngưỡng tiến độ chính sách',
  protectedContentConsumed: 'Đã dùng nội dung được bảo vệ',
  protectedMaterialsFullyDownloaded: 'Đã tải toàn bộ tài liệu được bảo vệ',
  protectedMaterialsFullyDownloadedAt: 'Thời điểm tải đủ tài liệu được bảo vệ',
  actuallyPaidAmount: 'Số tiền thực trả',
  paymentSucceededAt: 'Thanh toán thành công lúc',
  requestedAt: 'Đánh giá điều kiện lúc',
  reasonCodes: 'Căn cứ đánh giá',
  manualReviewReason: 'Lý do chuyển duyệt thủ công',
  evaluatedAt: 'Thời điểm đánh giá',
};

const TECHNICAL_EVIDENCE_KEYS = new Set([
  'snapshotVersion', 'policyVersion', 'refundType', 'timezone', 'currency',
  'orderId', 'orderItemId', 'courseId', 'exceptionReasonCode',
]);

interface DecisionNotice {
  tone: 'success' | 'error';
  message: string;
}

function formatMoney(value: RefundMoneyValue | null | undefined, currency?: string | null) {
  return formatFinanceMoney(value, currency || 'VND', 'Chưa ghi nhận');
}

function formatDate(value?: string | null) {
  return formatFinanceDateTime(value);
}

function formatEvidenceValue(key: string, value: unknown) {
  if (value === null || value === undefined || value === '') return 'Chưa ghi nhận';
  if (typeof value === 'boolean') return value ? 'Có' : 'Không';
  if (key === 'eligibilityResult' || key === 'result') return refundEligibilityLabel(value);
  if (key === 'reasonCodes' && Array.isArray(value)) {
    return value.map((code) => eligibilityReasonLabel(String(code))).join(' · ');
  }
  if (['paymentSucceededAt', 'requestedAt', 'evaluatedAt', 'protectedMaterialsFullyDownloadedAt'].includes(key)) {
    return formatFinanceDateTime(String(value));
  }
  if (['progressPercent', 'measuredProgressPercent', 'progressLimitPercent', 'progressThresholdPercent'].includes(key)) {
    return `${Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%`;
  }
  if (key === 'actuallyPaidAmount') return formatMoney(value as RefundMoneyValue);
  if (typeof value === 'object') return 'Có dữ liệu kỹ thuật';
  return String(value);
}

function EvidenceSnapshot({ snapshot }: { snapshot?: Record<string, unknown> | null }) {
  const entries = snapshot ? Object.entries(snapshot) : [];
  const visibleEntries = entries.filter(([key]) => EVIDENCE_LABELS[key] && !TECHNICAL_EVIDENCE_KEYS.has(key));
  const technicalEntries = entries.filter(([key]) => TECHNICAL_EVIDENCE_KEYS.has(key) || !EVIDENCE_LABELS[key]);

  if (entries.length === 0) {
    return (
      <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
        Chưa có bản chụp điều kiện hoàn tiền. Không nên chấp thuận trước khi backend cung cấp
        bằng chứng bất biến.
      </p>
    );
  }

  return (
    <dl className="grid grid-cols-1 gap-3 md:grid-cols-2">
      {visibleEntries.map(([key, value]) => (
        <div key={key} className="rounded-lg border border-gray-100 bg-gray-50 p-3">
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
            {EVIDENCE_LABELS[key]}
          </dt>
          <dd className="mt-1 break-words text-sm text-gray-900">{formatEvidenceValue(key, value)}</dd>
        </div>
      ))}
      {technicalEntries.length > 0 && (
        <details className="rounded-lg border border-gray-200 bg-white p-3 md:col-span-2">
          <summary className="cursor-pointer text-sm font-semibold text-gray-700">Chi tiết kỹ thuật của snapshot</summary>
          <dl className="mt-3 space-y-2 text-xs">
            {technicalEntries.map(([key, value]) => (
              <div key={key} className="grid gap-1 sm:grid-cols-[180px_1fr]">
                <dt className="font-mono text-gray-500">{key}</dt>
                <dd className="break-all text-gray-700">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '')}</dd>
              </div>
            ))}
          </dl>
        </details>
      )}
    </dl>
  );
}

export function AdminRefundDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<RefundDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decisionAction, setDecisionAction] = useState<'approve' | 'reject' | null>(null);
  const [decisionNotice, setDecisionNotice] = useState<DecisionNotice | null>(null);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const data = await adminRefundApi.getRefundDetail(id);
      setDetail(data);
    } catch (requestError) {
      setError(
        isAxiosError(requestError) && requestError.response?.data?.message
          ? requestError.response.data.message
          : 'Không thể tải thông tin chi tiết hoàn tiền.',
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const handleDecision = async (request: RefundDecisionRequest) => {
    if (!id || !decisionAction) return;
    setDecisionNotice(null);

    try {
      if (decisionAction === 'approve') {
        await adminRefundApi.approveRefund(id, request);
      } else {
        await adminRefundApi.rejectRefund(id, request);
      }

      const completedAction = decisionAction;
      setDecisionAction(null);
      setDecisionNotice({
        tone: 'success',
        message:
          completedAction === 'approve'
            ? 'Đã ghi có khoản hoàn tiền vào ví học viên và khóa quyền truy cập khóa học.'
            : 'Đã ghi nhận quyết định từ chối.',
      });
      await loadDetail();
    } catch (requestError) {
      const apiError = isAxiosError<ApiResponse<unknown>>(requestError)
        ? requestError.response
        : undefined;
      const reconciliationRequired =
        apiError?.data?.messageCode === 'REFUND_RECONCILIATION_REQUIRED';
      if (reconciliationRequired) {
        setDecisionAction(null);
        setDecisionNotice({
          tone: 'error',
          message:
            'Chưa thể hoàn tất tự động. Yêu cầu đã được chuyển sang đối soát và không bị ghi nhận hoàn tiền thành công.',
        });
        await loadDetail();
        return;
      }

      let message = 'Không thể ghi nhận quyết định. Không có thay đổi nào được xác nhận.';
      if (apiError?.status === 409) {
        message = 'Yêu cầu đã được người khác xử lý hoặc trạng thái đã thay đổi. Hãy tải lại dữ liệu trước khi quyết định.';
      } else if (apiError?.data?.messageCode === 'COMMON_INTERNAL_ERROR' || (apiError && apiError.status >= 500)) {
        let correlationId: string | undefined;
        if (apiError?.headers) {
          const getHeader = (key: string): string | undefined => {
            const headers = apiError.headers as any;
            if (typeof headers.get === 'function') {
              const val = headers.get(key);
              return typeof val === 'string' ? val : undefined;
            }
            return headers[key] as string | undefined;
          };
          correlationId = getHeader('x-correlation-id') || getHeader('x-amzn-trace-id');
        }

        message = correlationId
          ? `Hệ thống gặp sự cố không mong muốn (Mã theo dõi: ${correlationId}). Vui lòng liên hệ quản trị viên với mã theo dõi này.`
          : 'Hệ thống gặp sự cố không mong muốn. Vui lòng thử lại hoặc liên hệ quản trị viên.';
      } else if (apiError?.data?.message) {
        message = apiError.data.message;
      }

      setDecisionNotice({ tone: 'error', message });
      if (apiError?.status === 409) {
        setDecisionAction(null);
        await loadDetail();
      }
    }
  };

  if (loading && !detail) {
    return (
      <div className="flex min-h-[300px] items-center justify-center" role="status">
        <div className="h-10 w-10 animate-spin rounded-full border-b-2 border-red-600" />
        <span className="sr-only">Đang tải chi tiết hoàn tiền</span>
      </div>
    );
  }

  if (error || !detail) {
    return (
      <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">
        <p>{error || 'Không tìm thấy yêu cầu hoàn tiền.'}</p>
        <button
          onClick={() => navigate('/admin/refunds')}
          className="mt-2 text-sm text-red-600 underline"
        >
          Quay lại danh sách
        </button>
      </div>
    );
  }

  const statusMeta = STATUS_META[detail.status];
  const hasEligibilityEvidence =
    Boolean(detail.eligibilitySnapshot) &&
    Object.keys(detail.eligibilitySnapshot || {}).length > 0;
  const retryableReconciliation =
    detail.status === 'RECONCILIATION_REQUIRED' &&
    detail.providerStatus !== 'SUCCESS';
  const canDecide =
    detail.status === 'PENDING' || retryableReconciliation;
  const canApprove =
    canDecide &&
    hasEligibilityEvidence &&
    detail.paymentStatus === 'SUCCESS';
  const snapshot = detail.eligibilitySnapshot ?? {};
  const waitingDays = waitingCalendarDays(detail.createdAt);
  const elapsedDays = numberOrNull(snapshot.elapsedCalendarDays ?? snapshot.daysSincePurchase);
  const refundWindowDays = numberOrNull(snapshot.refundWindowDays);
  const policyDaysRemaining = elapsedDays === null || refundWindowDays === null
    ? null
    : refundWindowDays - elapsedDays;
  const progressPercent = numberOrNull(snapshot.measuredProgressPercent ?? snapshot.progressPercent);
  const progressThreshold = numberOrNull(snapshot.progressThresholdPercent ?? snapshot.progressLimitPercent);
  const eligibility = snapshot.eligibilityResult ?? snapshot.result ?? snapshot.eligible;

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
        <div>
          <button
            onClick={() => navigate('/admin/refunds')}
            className="mb-2 flex items-center text-sm text-gray-500 hover:text-gray-700"
          >
            ← Quay lại
          </button>
          <h2 className="text-2xl font-bold text-gray-900">Chi tiết hoàn tiền: {detail.orderCode}</h2>
        </div>
        {canDecide && (
          <div className="flex space-x-3">
            <button
              onClick={() => {
                setDecisionNotice(null);
                setDecisionAction('reject');
              }}
              className="rounded-lg bg-red-100 px-4 py-2 font-medium text-red-700 transition hover:bg-red-200"
            >
              Từ chối
            </button>
            <button
              onClick={() => {
                setDecisionNotice(null);
                setDecisionAction('approve');
              }}
              disabled={!canApprove}
              title={
                canApprove
                  ? undefined
                  : 'Cần giao dịch thanh toán thành công và bản chụp điều kiện trước khi chấp thuận'
              }
              className="rounded-lg bg-green-600 px-4 py-2 font-medium text-white transition hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {retryableReconciliation ? 'Thử lại an toàn' : 'Chấp thuận'}
            </button>
          </div>
        )}
      </div>

      {decisionNotice && (
        <div
          role={decisionNotice.tone === 'error' ? 'alert' : 'status'}
          className={`rounded-lg border p-4 text-sm ${
            decisionNotice.tone === 'error'
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-green-200 bg-green-50 text-green-800'
          }`}
        >
          {decisionNotice.message}
        </div>
      )}

      {detail.status === 'PROCESSING' && (
        <div role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
          Provider đang xử lý yêu cầu. Chưa thu hồi quyền học hoặc kết luận hoàn tiền cho đến khi
          nhận được kết quả đã xác minh.
        </div>
      )}

      {detail.status === 'RECONCILIATION_REQUIRED' && (
        <div role="alert" className="rounded-lg border border-orange-300 bg-orange-50 p-4 text-sm text-orange-900">
          <p className="font-semibold">Cần đối soát thủ công với provider.</p>
          <p className="mt-1">
            Hệ thống đã lưu mã nguyên nhân trong phần “Chi tiết kỹ thuật”.{' '}
            {detail.providerStatus === 'SUCCESS'
              ? 'Provider đã báo thành công; không được gọi hoàn tiền lại. Cần hoàn tất đối soát kế toán.'
              : 'Có thể thử lại bằng nút trên sau khi kiểm tra provider; hệ thống giữ nguyên idempotency key để chống hoàn tiền hai lần.'}
          </p>
        </div>
      )}

      {canDecide && !canApprove && (
        <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          Nút chấp thuận đang khóa vì giao dịch thanh toán chưa thành công hoặc chưa có bản chụp điều
          kiện. Finance Manager vẫn có thể từ chối yêu cầu với mã lý do phù hợp.
        </div>
      )}

      <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-lg font-bold text-gray-900">Tóm tắt trước khi quyết định</h3>
          <span className="rounded-full border border-gray-200 bg-gray-50 px-3 py-1 text-xs text-gray-600">SLA vận hành: Chưa được backend cấu hình</span>
        </div>
        <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryItem label="Điều kiện hoàn tiền" value={refundEligibilityLabel(eligibility)} />
          <SummaryItem label="Thời gian đã chờ" value={waitingDays === null ? 'Chưa tính được' : `${waitingDays} ngày theo lịch Việt Nam`} />
          <SummaryItem label="Cửa sổ chính sách tại lúc gửi" value={policyDaysRemaining === null ? 'Chưa ghi nhận' : policyDaysRemaining >= 0 ? `Còn ${policyDaysRemaining} ngày` : `Đã vượt ${Math.abs(policyDaysRemaining)} ngày`} />
          <SummaryItem label="Tiến độ sử dụng" value={progressPercent === null ? 'Chưa ghi nhận' : `${progressPercent.toLocaleString('vi-VN')}%${progressThreshold === null ? '' : ` / ngưỡng ${progressThreshold.toLocaleString('vi-VN')}%`}`} />
          <SummaryItem label="Học viên sẽ nhận" value={formatMoney(detail.grossAmount ?? detail.paymentAmount, detail.currency)} />
          <SummaryItem label="Ảnh hưởng nền tảng" value={`Đảo tối đa ${formatMoney(detail.commissionAmount, detail.currency)}`} />
          <SummaryItem label="Ảnh hưởng giảng viên" value={`Thu hồi tối đa ${formatMoney(detail.teacherNetAmount, detail.currency)}`} />
          <SummaryItem label="Escrow / ledger" value={detail.escrowAmount == null ? 'Chưa ghi nhận' : `${formatMoney(detail.escrowAmount, detail.currency)} · ${escrowStatusLabel(detail.escrowStatus)}`} />
        </dl>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-100 p-6">
          <h3 className="mb-4 text-lg font-bold text-gray-900">Thông tin yêu cầu</h3>
          <dl className="grid grid-cols-1 gap-x-4 gap-y-6 md:grid-cols-2">
            <div>
              <dt className="text-sm font-medium text-gray-500">Mã đơn hàng</dt>
              <dd className="mt-1 text-sm font-semibold text-gray-900">{detail.orderCode}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Trạng thái</dt>
              <dd className="mt-1">
                <span
                  className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusMeta.className}`}
                >
                  {statusMeta.label}
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Học viên</dt>
              <dd className="mt-1 text-sm text-gray-900">
                {detail.studentName} ({detail.studentEmail})
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Ngày yêu cầu</dt>
              <dd className="mt-1 text-sm text-gray-900">{formatDate(detail.createdAt)}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Khóa học</dt>
              <dd className="mt-1 text-sm text-gray-900">{detail.courseTitle || 'Chưa có'}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Order item</dt>
              <dd className="mt-1 break-all text-sm text-gray-900">{detail.orderItemId || 'Chưa có'}</dd>
            </div>
            <div className="md:col-span-2">
              <dt className="text-sm font-medium text-gray-500">Lý do yêu cầu</dt>
              <dd className="mt-1 rounded-lg border border-gray-100 bg-gray-50 p-3 text-sm text-gray-900">
                {detail.reason}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="p-6">
          <h3 className="mb-4 text-lg font-bold text-gray-900">Số tiền và phân bổ</h3>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt className="text-sm text-gray-500">Tổng tiền item</dt>
              <dd className="mt-1 font-semibold text-gray-900">
                {formatMoney(detail.grossAmount, detail.currency)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Hoa hồng nền tảng</dt>
              <dd className="mt-1 font-semibold text-gray-900">
                {formatMoney(detail.commissionAmount, detail.currency)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Phần của giảng viên</dt>
              <dd className="mt-1 font-semibold text-gray-900">
                {formatMoney(detail.teacherNetAmount, detail.currency)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Số tiền provider ghi nhận</dt>
              <dd className="mt-1 font-semibold text-gray-900">
                {formatMoney(detail.paymentAmount, detail.currency)}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="p-6">
          <h3 className="mb-4 text-lg font-bold text-gray-900">Bằng chứng thanh toán và provider</h3>
          <dl className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div>
              <dt className="text-sm text-gray-500">Trạng thái thanh toán</dt>
              <dd className="mt-1 font-semibold text-gray-900">{refundPaymentStatusLabel(detail.paymentStatus)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Provider thanh toán</dt>
              <dd className="mt-1 text-gray-900">{detail.paymentProvider || 'Chưa có'}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Mã giao dịch thanh toán</dt>
              <dd className="mt-1 break-all text-gray-900">
                {detail.paymentProviderTransactionId || 'Chưa có'}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Trạng thái refund provider</dt>
              <dd className="mt-1 font-semibold text-gray-900">{refundProviderStatusLabel(detail.providerStatus)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Provider thực thi</dt>
              <dd className="mt-1 text-gray-900">{detail.providerName || 'Chưa có'}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Mã tham chiếu provider</dt>
              <dd className="mt-1 break-all text-gray-900">{detail.providerReference || 'Chưa ghi nhận'}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Phản hồi provider</dt>
              <dd className="mt-1 text-gray-900">{detail.providerResultCode ? 'Đã lưu mã phản hồi kỹ thuật' : 'Chưa ghi nhận phản hồi'}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Số lần gửi provider</dt>
              <dd className="mt-1 text-gray-900">{detail.providerAttemptCount ?? 'Chưa ghi nhận'}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="p-6">
          <h3 className="mb-4 text-lg font-bold text-gray-900">Dòng thời gian nghiệp vụ</h3>
          <ol className="relative ml-2 border-l border-gray-200 pl-6">
            <TimelineItem label="Thanh toán thành công" value={snapshot.paymentSucceededAt as string | undefined} detail={refundPaymentStatusLabel(detail.paymentStatus)} />
            <TimelineItem label="Tạo yêu cầu hoàn tiền" value={detail.createdAt} detail={`Refund ${shortId(detail.id)}`} />
            <TimelineItem label="Đánh giá điều kiện" value={(snapshot.requestedAt ?? snapshot.evaluatedAt) as string | undefined} detail={refundEligibilityLabel(eligibility)} />
            {detail.decidedAt && <TimelineItem label={detail.status === 'REJECTED' ? 'Finance từ chối' : 'Finance ghi nhận quyết định'} value={detail.decidedAt} detail={detail.decisionReasonCode ? REFUND_DECISION_REASON_LABELS[detail.decisionReasonCode] : 'Đã lưu quyết định'} />}
            {detail.providerStatus && detail.providerStatus !== 'NOT_REQUESTED' && <TimelineItem label="Gửi và nhận trạng thái provider" value={detail.updatedAt} detail={refundProviderStatusLabel(detail.providerStatus)} />}
            {detail.status === 'APPROVED' && <TimelineItem label="Cập nhật ví / ledger và hoàn tất" value={detail.updatedAt} detail="Hoàn tiền đã được hệ thống xác nhận" />}
            {detail.status === 'RECONCILIATION_REQUIRED' && <TimelineItem label="Chuyển sang đối soát" value={detail.updatedAt} detail="Chưa được kết luận hoàn tất" />}
          </ol>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="p-6">
          <h3 className="mb-4 text-lg font-bold text-gray-900">Escrow</h3>
          <dl className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div>
              <dt className="text-sm text-gray-500">Trạng thái escrow</dt>
              <dd className="mt-1 font-semibold text-gray-900">{escrowStatusLabel(detail.escrowStatus)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Số tiền escrow</dt>
              <dd className="mt-1 text-gray-900">{formatMoney(detail.escrowAmount, detail.currency)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-500">Ngày dự kiến giải ngân</dt>
              <dd className="mt-1 text-gray-900">{formatDate(detail.escrowReleaseAt)}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="p-6">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-bold text-gray-900">Bản chụp điều kiện tại thời điểm yêu cầu</h3>
            <RouterLink 
              to="/help/learners/payments-refunds-access"
              className="text-sm font-medium text-blue-600 hover:underline"
              target="_blank"
            >
              Xem chính sách hoàn tiền
            </RouterLink>
          </div>
          <EvidenceSnapshot snapshot={detail.eligibilitySnapshot} />
        </div>
      </section>

      {(detail.decisionReasonCode || detail.decisionNote || detail.decidedAt) && (
        <section className="overflow-hidden rounded-xl border border-blue-200 bg-blue-50 shadow-sm">
          <div className="p-6">
            <h3 className="mb-4 text-lg font-bold text-gray-900">Quyết định kiểm toán</h3>
            <dl className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <dt className="text-sm text-gray-500">Lý do quyết định</dt>
                <dd className="mt-1 font-semibold text-gray-900">
                  {detail.decisionReasonCode
                    ? REFUND_DECISION_REASON_LABELS[detail.decisionReasonCode]
                    : 'Chưa ghi nhận'}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-gray-500">Thời điểm quyết định</dt>
                <dd className="mt-1 text-gray-900">{formatDate(detail.decidedAt)}</dd>
              </div>
              <div className="md:col-span-2">
                <dt className="text-sm text-gray-500">Ghi chú quyết định</dt>
                <dd className="mt-1 rounded-lg border border-blue-100 bg-white p-3 text-sm text-gray-900">
                  {detail.decisionNote || 'Chưa có'}
                </dd>
              </div>
            </dl>
          </div>
        </section>
      )}

      <details className="rounded-xl border border-gray-200 bg-gray-50 p-5 text-sm">
        <summary className="cursor-pointer font-bold text-gray-800">Chi tiết kỹ thuật và mã đối soát</summary>
        <dl className="mt-4 grid gap-3 md:grid-cols-2">
          <TechnicalItem label="Refund ID" value={detail.id} />
          <TechnicalItem label="Order ID" value={detail.orderId} />
          <TechnicalItem label="Order item ID" value={detail.orderItemId} />
          <TechnicalItem label="Payment transaction ID" value={detail.paymentProviderTransactionId} />
          <TechnicalItem label="Provider result code" value={detail.providerResultCode} />
          <TechnicalItem label="Reconciliation reason code" value={detail.reconciliationReasonCode} />
          <TechnicalItem label="Số lần xử lý hoàn tiền tự động" value={String(detail.autoRefundAttemptCount ?? 0)} />
          <TechnicalItem label="Mã lỗi tự động gần nhất" value={detail.autoRefundLastErrorCode} />
          <TechnicalItem label="Lần thử tiếp theo" value={detail.autoRefundNextAttemptAt ? formatDate(detail.autoRefundNextAttemptAt) : null} />
        </dl>
      </details>

      {decisionAction && (
        <RefundDecisionForm
          action={decisionAction}
          amount={formatMoney(detail.grossAmount ?? detail.paymentAmount, detail.currency)}
          provider={detail.paymentProvider || detail.providerName || 'Chưa xác định'}
          platformImpact={formatMoney(detail.commissionAmount, detail.currency)}
          teacherImpact={formatMoney(detail.teacherNetAmount, detail.currency)}
          escrowImpact={formatMoney(detail.escrowAmount, detail.currency)}
          onConfirm={handleDecision}
          onCancel={() => {
            setDecisionAction(null);
            setDecisionNotice(null);
          }}
          errorMessage={decisionNotice?.tone === 'error' ? decisionNotice.message : null}
        />
      )}
    </div>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-gray-100 bg-gray-50 p-3">
      <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-gray-900">{value}</dd>
    </div>
  );
}

function TimelineItem({ label, value, detail }: { label: string; value?: string | null; detail: string }) {
  return (
    <li className="relative pb-5 last:pb-0">
      <span className="absolute -left-[31px] top-1 h-3 w-3 rounded-full border-2 border-white bg-red-600 ring-1 ring-red-200" />
      <p className="font-semibold text-gray-900">{label}</p>
      <p className="text-xs text-gray-500">{formatFinanceDateTime(value)}</p>
      <p className="mt-0.5 text-sm text-gray-700">{detail}</p>
    </li>
  );
}

function TechnicalItem({ label, value }: { label: string; value?: string | null }) {
  return <div><dt className="text-xs text-gray-500">{label}</dt><dd className="mt-0.5 break-all font-mono text-xs text-gray-800">{value || 'Chưa ghi nhận'}</dd></div>;
}

function numberOrNull(value: unknown) {
  const numeric = Number(value);
  return value === null || value === undefined || value === '' || !Number.isFinite(numeric) ? null : numeric;
}

function escrowStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    HELD: 'Đang giữ trong escrow',
    PENDING: 'Đang chờ ghi nhận escrow',
    RELEASED: 'Đã giải ngân cho giảng viên',
    REFUNDED: 'Đã thu hồi để hoàn tiền',
    FROZEN: 'Đang khóa để đối soát',
  };
  return status ? labels[status] ?? 'Trạng thái escrow chưa được ánh xạ' : 'Chưa ghi nhận';
}

function shortId(value: string) {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value;
}
