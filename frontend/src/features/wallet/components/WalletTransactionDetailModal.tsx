import type { ReactNode } from 'react';
import {
  formatWalletDateTime,
  referenceTypeLabel,
  transactionTypeLabel,
} from '../constants/transactionLabels';
import { useStudentWalletTransactionDetail } from '../hooks/useStudentWalletTransactions';

interface WalletTransactionDetailModalProps {
  transactionId: string | null;
  onClose: () => void;
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2 border-b border-slate-100 last:border-0">
      <span className="text-sm text-slate-500">{label}</span>
      <span className="text-sm font-semibold text-slate-800 text-right break-all">{value}</span>
    </div>
  );
}

/**
 * UC-17 alternative flow 6a: shows one transaction plus the related order / refund / payout
 * record when the backend allows the caller to see it.
 */
export function WalletTransactionDetailModal({
  transactionId,
  onClose,
}: WalletTransactionDetailModalProps) {
  const { data, isLoading, isError, refetch } = useStudentWalletTransactionDetail(transactionId);

  if (!transactionId) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="bg-white w-full max-w-lg rounded-2xl shadow-xl p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between mb-4">
          <h3 className="text-lg font-extrabold text-slate-900">Chi tiết giao dịch</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-xl leading-none"
            aria-label="Đóng"
          >
            ×
          </button>
        </div>

        {isLoading && (
          <div className="space-y-3">
            {[0, 1, 2, 3, 4].map((row) => (
              <div key={row} className="h-8 rounded-lg bg-slate-100 animate-pulse" />
            ))}
          </div>
        )}

        {isError && (
          <div className="text-center py-6">
            <p className="text-sm text-slate-600 mb-3">Không tải được chi tiết giao dịch.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="bg-red-600 hover:bg-red-700 text-white font-semibold px-4 py-2 rounded-xl"
            >
              Thử lại
            </button>
          </div>
        )}

        {data && (
          <>
            <div className="rounded-xl bg-slate-50 p-4 mb-4">
              <p className="text-xs text-slate-500">{transactionTypeLabel(data.transactionType)}</p>
              <p
                className={`text-2xl font-extrabold mt-1 ${
                  data.direction === 'IN' ? 'text-emerald-600' : 'text-slate-900'
                }`}
              >
                {data.direction === 'IN' ? '+' : '−'}
                {data.amount.toLocaleString('vi-VN')} {data.currency}
              </p>
            </div>

            <Row label="Mã giao dịch" value={data.id} />
            <Row label="Thời gian" value={formatWalletDateTime(data.createdAt)} />
            <Row label="Chiều tiền" value={data.direction === 'IN' ? 'Tiền vào' : 'Tiền ra'} />
            <Row label="Loại tham chiếu" value={referenceTypeLabel(data.referenceType)} />
            <Row label="Mã tham chiếu" value={data.referenceCode ?? '—'} />
            {data.note && <Row label="Ghi chú" value={data.note} />}

            {data.relatedRecord && (
              <div className="mt-5">
                <h4 className="text-sm font-bold text-slate-900 mb-1">
                  {referenceTypeLabel(data.relatedRecord.kind)} liên quan
                </h4>
                <Row label="Mã" value={data.relatedRecord.code ?? '—'} />
                {data.relatedRecord.title && (
                  <Row label="Khóa học" value={data.relatedRecord.title} />
                )}
                <Row label="Trạng thái" value={data.relatedRecord.status ?? '—'} />
                <Row
                  label="Giá trị"
                  value={
                    data.relatedRecord.amount != null
                      ? `${data.relatedRecord.amount.toLocaleString('vi-VN')} ${data.currency}`
                      : '—'
                  }
                />
                <Row label="Ngày tạo" value={formatWalletDateTime(data.relatedRecord.occurredAt)} />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default WalletTransactionDetailModal;
