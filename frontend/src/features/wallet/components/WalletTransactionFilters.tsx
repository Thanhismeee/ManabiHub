import { useState } from 'react';
import { TRANSACTION_TYPE_LABELS } from '../constants/transactionLabels';
import type { WalletDirection, WalletTransactionFilter, WalletTransactionType } from '../types';

interface WalletTransactionFiltersProps {
  /** Types offered in the dropdown — differs for Student vs Teacher wallets. */
  availableTypes: WalletTransactionType[];
  value: WalletTransactionFilter;
  onChange: (next: WalletTransactionFilter) => void;
  disabled?: boolean;
}

const DIRECTION_OPTIONS: { value: WalletDirection | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'IN', label: 'Tiền vào' },
  { value: 'OUT', label: 'Tiền ra' },
];

const inputClass =
  'w-full border border-slate-300 rounded-xl px-3 py-2 text-sm focus:outline-none ' +
  'focus:ring-2 focus:ring-red-500/30 focus:border-red-500 disabled:bg-slate-50';

/**
 * Filter bar for the wallet transaction history (UC-17 step 6).
 * Changing any filter resets pagination back to the first page.
 */
export function WalletTransactionFilters({
  availableTypes,
  value,
  onChange,
  disabled,
}: WalletTransactionFiltersProps) {
  const [referenceCode, setReferenceCode] = useState(value.referenceCode ?? '');
  const [dateError, setDateError] = useState<string | null>(null);

  const patch = (next: Partial<WalletTransactionFilter>) => {
    onChange({ ...value, ...next, page: 0 });
  };

  const handleDateChange = (key: 'fromDate' | 'toDate', raw: string) => {
    const next = { ...value, [key]: raw || undefined };
    if (next.fromDate && next.toDate && next.fromDate > next.toDate) {
      setDateError('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
      return;
    }
    setDateError(null);
    patch({ [key]: raw || undefined });
  };

  const handleReset = () => {
    setReferenceCode('');
    setDateError(null);
    onChange({ page: 0, size: value.size });
  };

  const hasActiveFilter = Boolean(
    value.types?.length || value.direction || value.fromDate || value.toDate || value.referenceCode,
  );

  return (
    <div className="border-b border-slate-200 p-4 sm:p-5">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">Loại giao dịch</label>
          <select
            className={inputClass}
            disabled={disabled}
            value={value.types?.[0] ?? ''}
            onChange={(e) =>
              patch({ types: e.target.value ? [e.target.value as WalletTransactionType] : undefined })
            }
          >
            <option value="">Tất cả</option>
            {availableTypes.map((type) => (
              <option key={type} value={type}>
                {TRANSACTION_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">Chiều tiền</label>
          <select
            className={inputClass}
            disabled={disabled}
            value={value.direction ?? ''}
            onChange={(e) =>
              patch({ direction: (e.target.value || undefined) as WalletDirection | undefined })
            }
          >
            {DIRECTION_OPTIONS.map((option) => (
              <option key={option.label} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">Từ ngày</label>
          <input
            type="date"
            className={inputClass}
            disabled={disabled}
            value={value.fromDate ?? ''}
            max={value.toDate}
            onChange={(e) => handleDateChange('fromDate', e.target.value)}
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">Đến ngày</label>
          <input
            type="date"
            className={inputClass}
            disabled={disabled}
            value={value.toDate ?? ''}
            min={value.fromDate}
            onChange={(e) => handleDateChange('toDate', e.target.value)}
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">Mã tham chiếu</label>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              patch({ referenceCode: referenceCode || undefined });
            }}
          >
            <input
              type="search"
              placeholder="Mã đơn hàng…"
              className={inputClass}
              disabled={disabled}
              value={referenceCode}
              onChange={(e) => setReferenceCode(e.target.value)}
              onBlur={() => patch({ referenceCode: referenceCode || undefined })}
            />
          </form>
        </div>
      </div>

      {dateError && <p className="text-red-600 text-xs mt-2">{dateError}</p>}

      {hasActiveFilter && (
        <button
          type="button"
          onClick={handleReset}
          disabled={disabled}
          className="mt-3 text-sm font-semibold text-red-600 hover:text-red-700 disabled:opacity-60"
        >
          Xóa bộ lọc
        </button>
      )}
    </div>
  );
}

export default WalletTransactionFilters;
