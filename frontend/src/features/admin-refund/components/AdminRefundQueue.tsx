import { useCallback, useEffect, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import { Link } from 'react-router-dom';
import { adminRefundApi } from '../api/adminRefundApi';
import {
  REFUND_PROVIDER_STATUS_LABELS,
  REFUND_STATUS_LABELS,
  refundPaymentStatusLabel,
  refundProviderStatusLabel,
} from '../refundDisplay';
import type {
  RefundProviderStatus,
  RefundQueueFilters,
  RefundQueueResponse,
  RefundStatus,
} from '../types';
import {
  formatFinanceDateTime,
  formatMoney,
  waitingCalendarDays,
} from '../../admin-finance/financeDisplay';

const DEFAULT_FILTERS: RefundQueueFilters = { status: 'PENDING' };

export function AdminRefundQueue() {
  const [queue, setQueue] = useState<RefundQueueResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [size, setSize] = useState(10);
  const [filters, setFilters] = useState<RefundQueueFilters>(DEFAULT_FILTERS);
  const [draftFilters, setDraftFilters] = useState<RefundQueueFilters>(DEFAULT_FILTERS);

  const loadQueue = useCallback(async (currentPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminRefundApi.getPendingRefunds(currentPage, size, filters);
      setQueue(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
      setLastUpdatedAt(new Date().toISOString());
    } catch (requestError) {
      setError(
        isAxiosError(requestError) && requestError.response?.status === 401
          ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.'
          : 'Không thể tải hàng đợi hoàn tiền. Dữ liệu đang hiển thị, nếu có, là lần tải gần nhất.',
      );
    } finally {
      setLoading(false);
    }
  }, [filters, size]);

  useEffect(() => { void loadQueue(page); }, [loadQueue, page]);

  const pageAmount = useMemo(() => summarizeRefundPageAmount(queue), [queue]);
  const oldestWait = useMemo(() => queue.reduce<number | null>((oldest, request) => {
    const days = waitingCalendarDays(request.createdAt);
    return days === null ? oldest : Math.max(oldest ?? 0, days);
  }, null), [queue]);
  const hasFilters = Object.entries(filters).some(([key, value]) => key !== 'status'
    ? value !== '' && value !== undefined
    : value !== 'PENDING');
  const filterError = useMemo(() => validateRefundFilters(draftFilters), [draftFilters]);

  return (
    <div className="space-y-4">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-end">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Duyệt và đối soát hoàn tiền</h1>
          <p className="mt-1 text-sm text-gray-500">
            Trạng thái quyết định nội bộ và trạng thái thực thi tại provider được hiển thị riêng.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
          <span className="rounded-full border bg-white px-3 py-1.5">Múi giờ: Asia/Ho_Chi_Minh</span>
          <span className="rounded-full border bg-white px-3 py-1.5">Cập nhật: {formatFinanceDateTime(lastUpdatedAt)}</span>
          <button onClick={() => void loadQueue(page)} disabled={loading} className="rounded-lg border border-gray-300 bg-white px-3 py-2 font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50">Làm mới</button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Tổng yêu cầu" value={lastUpdatedAt ? totalElements.toLocaleString('vi-VN') : 'Chưa đồng bộ'} helper="Toàn bộ kết quả khớp bộ lọc" />
        <Metric label="Số tiền yêu cầu (trang)" value={!lastUpdatedAt || pageAmount.total === null ? 'Chưa đồng bộ' : formatMoney(pageAmount.total, pageAmount.currency)} helper={pageAmount.complete ? 'Chỉ tổng trên trang hiện tại; API chưa có tổng toàn hàng đợi' : 'Có yêu cầu thiếu số tiền hoặc khác tiền tệ; không cộng thành số sai'} />
        <Metric label="Yêu cầu lâu nhất" value={!lastUpdatedAt ? 'Chưa đồng bộ' : oldestWait === null ? 'Không áp dụng' : `${oldestWait} ngày`} helper="Tính theo lịch Việt Nam trên trang hiện tại" />
        <Metric label="SLA vận hành" value="Chưa cấu hình" helper="Không tự suy đoán từ thời hạn đủ điều kiện hoàn tiền" />
      </div>

      {error && (
        <div role="alert" className={`rounded-lg border px-4 py-3 shadow-sm ${queue.length ? 'border-amber-200 bg-amber-50 text-amber-800' : 'border-red-200 bg-red-50 text-red-700'}`}>
          <p className="font-semibold">{error}</p>
          <button onClick={() => void loadQueue(page)} className="mt-2 text-sm underline">Thử lại</button>
        </div>
      )}

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 bg-gray-50/60 p-4">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <input aria-label="Refund ID" placeholder="Refund ID chính xác" value={draftFilters.refundId ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, refundId: event.target.value })} className="rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            <input aria-label="Mã đơn hàng" placeholder="Mã đơn hàng" value={draftFilters.orderCode ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, orderCode: event.target.value })} className="rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            <input aria-label="Học viên" placeholder="Tên hoặc email học viên" value={draftFilters.student ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, student: event.target.value })} className="rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            <input aria-label="Khóa học" placeholder="Tên khóa học" value={draftFilters.course ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, course: event.target.value })} className="rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            <input aria-label="Nhà cung cấp thanh toán" placeholder="Provider thanh toán, ví dụ VNPAY" value={draftFilters.paymentProvider ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, paymentProvider: event.target.value })} className="rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            <select aria-label="Trạng thái nội bộ" value={draftFilters.status ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, status: event.target.value as RefundStatus | '' })} className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm">
              <option value="">Tất cả trạng thái nội bộ</option>
              {Object.entries(REFUND_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            <select aria-label="Trạng thái provider" value={draftFilters.providerStatus ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, providerStatus: event.target.value as RefundProviderStatus | '' })} className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm">
              <option value="">Tất cả trạng thái provider</option>
              {Object.entries(REFUND_PROVIDER_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            <select aria-label="Trạng thái đối soát" value={String(draftFilters.reconciliationRequired ?? '')} onChange={(event) => setDraftFilters({ ...draftFilters, reconciliationRequired: event.target.value === '' ? '' : event.target.value === 'true' })} className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm">
              <option value="">Tất cả đối soát</option><option value="true">Cần đối soát</option><option value="false">Không cần đối soát</option>
            </select>
            <div className="flex gap-2"><input aria-label="Số tiền tối thiểu" type="number" min="0" placeholder="Tiền từ" value={draftFilters.minAmount ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, minAmount: event.target.value })} className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm" /><input aria-label="Số tiền tối đa" type="number" min="0" placeholder="Tiền đến" value={draftFilters.maxAmount ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, maxAmount: event.target.value })} className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm" /></div>
            <div className="flex gap-2"><input aria-label="Từ ngày" type="date" value={draftFilters.createdFrom ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, createdFrom: event.target.value })} className="min-w-0 flex-1 rounded-lg border border-gray-300 px-2 py-2 text-sm" /><input aria-label="Đến ngày" type="date" value={draftFilters.createdTo ?? ''} onChange={(event) => setDraftFilters({ ...draftFilters, createdTo: event.target.value })} className="min-w-0 flex-1 rounded-lg border border-gray-300 px-2 py-2 text-sm" /></div>
          </div>
          <div className="mt-3 flex gap-2">
            <button disabled={Boolean(filterError)} onClick={() => { setPage(0); setFilters(draftFilters); }} className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50">Áp dụng bộ lọc</button>
            <button onClick={() => { setDraftFilters(DEFAULT_FILTERS); setFilters(DEFAULT_FILTERS); setPage(0); }} className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium">Xóa lọc</button>
          </div>
          {filterError && <p role="alert" className="mt-2 text-sm font-semibold text-amber-700">{filterError}</p>}
        </div>

        {loading && queue.length === 0 ? (
          <div className="flex min-h-[300px] items-center justify-center" role="status"><div className="h-10 w-10 animate-spin rounded-full border-b-2 border-red-600" /><span className="ml-3 text-gray-600">Đang tải hàng đợi…</span></div>
        ) : queue.length === 0 ? (
          <div className="p-12 text-center">
            <p className="font-semibold text-gray-700">{hasFilters ? 'Không có yêu cầu khớp bộ lọc' : 'Không có yêu cầu hoàn tiền chờ quyết định'}</p>
            <p className="mt-1 text-sm text-gray-500">{hasFilters ? 'Hãy thay đổi hoặc xóa điều kiện lọc.' : 'Đây là trạng thái rỗng thực sự của hàng đợi mặc định.'}</p>
          </div>
        ) : (
          <div className="relative overflow-x-auto">
            {loading && <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/60" role="status"><div className="h-8 w-8 animate-spin rounded-full border-b-2 border-red-600" /></div>}
            <table className="min-w-[1450px] w-full border-collapse text-left">
              <thead><tr className="border-b border-gray-200 bg-gray-100/70 text-xs font-bold uppercase tracking-wider text-gray-600">
                <th className="px-4 py-3">Order / refund</th><th className="px-4 py-3">Học viên / khóa học</th><th className="px-4 py-3">Lý do</th><th className="px-4 py-3">Ngày / thời gian chờ</th><th className="px-4 py-3 text-right">Yêu cầu / được duyệt</th><th className="px-4 py-3">Thanh toán</th><th className="px-4 py-3">Nội bộ</th><th className="px-4 py-3">Provider</th><th className="px-4 py-3">Người xử lý</th><th className="px-4 py-3 text-right">Thao tác</th>
              </tr></thead>
              <tbody className="divide-y divide-gray-100 text-sm text-gray-700">
                {queue.map((request) => {
                  const waitingDays = waitingCalendarDays(request.createdAt);
                  return <tr key={request.id} className="hover:bg-gray-50/70">
                    <td className="px-4 py-4"><div className="font-semibold text-gray-900">{request.orderCode}</div><div className="font-mono text-xs text-gray-500" title={request.id}>{shortId(request.id)}</div></td>
                    <td className="px-4 py-4"><div className="font-medium text-gray-900">{request.studentName}</div><div className="text-xs text-gray-500">{request.studentEmail}</div><div className="mt-1 max-w-[220px] truncate text-xs" title={request.courseTitle ?? ''}>{request.courseTitle || 'Chưa ghi nhận khóa học'}</div></td>
                    <td className="max-w-[220px] px-4 py-4"><p className="line-clamp-3" title={request.reason}>{request.reason}</p></td>
                    <td className="whitespace-nowrap px-4 py-4"><div>{formatFinanceDateTime(request.createdAt)}</div><div className="mt-1 font-semibold text-amber-700">{waitingDays === null ? 'Chưa tính được' : `Đã chờ ${waitingDays} ngày`}</div><div className="text-xs text-gray-500">Hạn SLA: Chưa cấu hình</div></td>
                    <td className="whitespace-nowrap px-4 py-4 text-right"><div className="font-semibold">{formatMoney(request.grossAmount ?? request.paymentAmount, request.currency || 'VND')}</div><div className="text-xs text-gray-500">Được duyệt: {approvedRefundAmount(request)}</div></td>
                    <td className="px-4 py-4"><div className="font-medium">{refundPaymentStatusLabel(request.paymentStatus)}</div><div className="text-xs text-gray-500">{request.paymentProvider || 'Chưa xác định provider'}</div></td>
                    <td className="px-4 py-4"><span className="inline-flex rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs font-semibold">{REFUND_STATUS_LABELS[request.status]}</span></td>
                    <td className="px-4 py-4"><div className="font-medium">{refundProviderStatusLabel(request.providerStatus)}</div>{request.reconciliationReasonCode && <div className="mt-1 text-xs font-semibold text-orange-700">Có cảnh báo đối soát</div>}</td>
                    <td className="px-4 py-4"><div>{request.decidedBy ? shortId(request.decidedBy) : 'Chưa phân công'}</div><div className="text-xs text-gray-500">{formatFinanceDateTime(request.decidedAt)}</div></td>
                    <td className="px-4 py-4 text-right"><Link to={`/admin/refunds/${request.id}`} className="inline-flex rounded-lg bg-red-600 px-4 py-2 text-xs font-semibold text-white hover:bg-red-700">Xem chi tiết</Link></td>
                  </tr>;
                })}
              </tbody>
            </table>
            <div className="flex items-center justify-between border-t border-gray-200 bg-gray-50/50 p-4">
              <div className="flex items-center gap-3"><span className="text-sm">Trang <b>{page + 1}</b> / <b>{Math.max(totalPages, 1)}</b></span><select aria-label="Số dòng mỗi trang" value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }} className="rounded border border-gray-300 bg-white px-2 py-1 text-sm"><option value="10">10 dòng</option><option value="20">20 dòng</option><option value="50">50 dòng</option></select></div>
              <div className="flex gap-2"><button onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page === 0 || loading} className="rounded-lg border bg-white px-3 py-2 text-sm disabled:opacity-50">Trước</button><button onClick={() => setPage((value) => Math.min(value + 1, totalPages - 1))} disabled={page >= totalPages - 1 || loading} className="rounded-lg border bg-white px-3 py-2 text-sm disabled:opacity-50">Sau</button></div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

function Metric({ label, value, helper }: { label: string; value: string; helper: string }) {
  return <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm"><p className="text-sm text-gray-500">{label}</p><p className="mt-1 text-xl font-bold text-gray-900">{value}</p><p className="mt-1 text-xs text-gray-500">{helper}</p></div>;
}

function shortId(value: string) {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value;
}

function summarizeRefundPageAmount(queue: RefundQueueResponse[]) {
  if (queue.length === 0) return { total: 0, currency: 'VND', complete: true };
  let total = 0;
  let currency: string | null = null;
  for (const request of queue) {
    const amountValue = request.grossAmount ?? request.paymentAmount;
    const amount = Number(amountValue);
    const requestCurrency = request.currency || 'VND';
    if (amountValue === null || amountValue === undefined || !Number.isFinite(amount)
        || (currency !== null && currency !== requestCurrency)) {
      return { total: null, currency: currency ?? requestCurrency, complete: false };
    }
    currency = requestCurrency;
    total += amount;
  }
  return { total, currency: currency ?? 'VND', complete: true };
}

function approvedRefundAmount(request: RefundQueueResponse) {
  if (request.status === 'REJECTED' || request.status === 'CANCELLED') return 'Không áp dụng';
  if (request.status !== 'PROCESSING' && request.status !== 'APPROVED') return 'Chưa ghi nhận';
  return formatMoney(request.grossAmount ?? request.paymentAmount, request.currency || 'VND');
}

function validateRefundFilters(filters: RefundQueueFilters) {
  if (filters.createdFrom && filters.createdTo && filters.createdFrom > filters.createdTo) {
    return 'Khoảng ngày không hợp lệ: ngày kết thúc phải từ ngày bắt đầu trở đi.';
  }
  const minimum = filters.minAmount === '' || filters.minAmount === undefined
    ? null
    : Number(filters.minAmount);
  const maximum = filters.maxAmount === '' || filters.maxAmount === undefined
    ? null
    : Number(filters.maxAmount);
  if ((minimum !== null && (!Number.isFinite(minimum) || minimum < 0))
      || (maximum !== null && (!Number.isFinite(maximum) || maximum < 0))) {
    return 'Khoảng tiền phải là số không âm.';
  }
  if (minimum !== null && maximum !== null && minimum > maximum) {
    return 'Số tiền tối đa phải lớn hơn hoặc bằng số tiền tối thiểu.';
  }
  return null;
}
