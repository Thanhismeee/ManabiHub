import {
  formatWalletDateTime,
  transactionTypeLabel,
} from '../constants/transactionLabels';
import type { WalletTransaction } from '../types';

interface WalletTransactionTableProps {
  transactions: WalletTransaction[];
  loading?: boolean;
  onSelect: (transaction: WalletTransaction) => void;
}

function formatSignedAmount(transaction: WalletTransaction) {
  const sign = transaction.direction === 'IN' ? '+' : '−';
  return `${sign}${transaction.amount.toLocaleString('vi-VN')} ${transaction.currency}`;
}

/** Transaction history table (UC-17 step 3/7). Rows open the detail dialog (flow 6a). */
export function WalletTransactionTable({
  transactions,
  loading,
  onSelect,
}: WalletTransactionTableProps) {
  if (loading) {
    return (
      <div className="p-5 space-y-3">
        {[0, 1, 2, 3].map((row) => (
          <div key={row} className="h-12 rounded-lg bg-slate-100 animate-pulse" />
        ))}
      </div>
    );
  }

  if (!transactions.length) {
    return (
      <div className="px-5 py-12 text-center">
        <p className="font-semibold text-slate-700">Không có giao dịch nào</p>
        <p className="text-sm text-slate-400 mt-1">
          Thử thay đổi bộ lọc hoặc quay lại sau khi bạn phát sinh giao dịch.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
            <th className="px-5 py-3 font-semibold">Thời gian</th>
            <th className="px-5 py-3 font-semibold">Loại giao dịch</th>
            <th className="px-5 py-3 font-semibold">Mã tham chiếu</th>
            <th className="px-5 py-3 font-semibold text-right">Số tiền</th>
            <th className="px-5 py-3 font-semibold text-right">Chi tiết</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => (
            <tr
              key={transaction.id}
              className="border-b border-slate-100 last:border-0 hover:bg-slate-50 cursor-pointer"
              onClick={() => onSelect(transaction)}
            >
              <td className="px-5 py-3 whitespace-nowrap text-slate-600">
                {formatWalletDateTime(transaction.createdAt)}
              </td>
              <td className="px-5 py-3">
                <span className="font-semibold text-slate-800">
                  {transactionTypeLabel(transaction.transactionType)}
                </span>
                {transaction.note && (
                  <span className="block text-xs text-slate-400 line-clamp-1">{transaction.note}</span>
                )}
              </td>
              <td className="px-5 py-3 text-slate-600">{transaction.referenceCode ?? '—'}</td>
              <td
                className={`px-5 py-3 text-right font-bold whitespace-nowrap ${
                  transaction.direction === 'IN' ? 'text-emerald-600' : 'text-slate-800'
                }`}
              >
                {formatSignedAmount(transaction)}
              </td>
              <td className="px-5 py-3 text-right">
                <button
                  type="button"
                  className="text-red-600 font-semibold hover:text-red-700"
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelect(transaction);
                  }}
                >
                  Xem
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default WalletTransactionTable;
