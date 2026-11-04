import type { WalletTransactionFilter } from '../types';

/**
 * Maps the UI filter object onto query params the backend can bind.
 * Arrays are sent comma-joined because Spring converts a comma-delimited string
 * straight into {@code List<WalletTransactionType>}.
 *
 * Shared by the Student money wallet and the Teacher revenue wallet (UC-17).
 */
export function toTransactionParams(filter: WalletTransactionFilter = {}) {
  const params: Record<string, string | number> = {
    page: filter.page ?? 0,
    size: filter.size ?? 10,
  };
  if (filter.types?.length) params.types = filter.types.join(',');
  if (filter.direction) params.direction = filter.direction;
  if (filter.fromDate) params.fromDate = filter.fromDate;
  if (filter.toDate) params.toDate = filter.toDate;
  if (filter.referenceCode?.trim()) params.referenceCode = filter.referenceCode.trim();
  return params;
}
