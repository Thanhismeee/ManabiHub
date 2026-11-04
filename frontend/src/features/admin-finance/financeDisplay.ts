import type { MoneyValue, RevenueGranularity } from './types';

export const FINANCE_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export type QuickRange = 'TODAY' | 'WEEK' | 'MONTH' | 'QUARTER';

export function formatMoney(
  value: MoneyValue | null | undefined,
  currency = 'VND',
  missingLabel = 'Chưa ghi nhận',
) {
  if (value === null || value === undefined || value === '') return missingLabel;
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) return 'Dữ liệu không hợp lệ';
  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency,
      maximumFractionDigits: currency === 'VND' ? 0 : 2,
    }).format(numericValue);
  } catch {
    return `${numericValue.toLocaleString('vi-VN')} ${currency}`;
  }
}

export function formatPercentage(value: MoneyValue | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Chưa ghi nhận';
  const numericValue = Number(value);
  return Number.isFinite(numericValue)
    ? `${numericValue.toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%`
    : 'Dữ liệu không hợp lệ';
}

export function parseFinanceTimestamp(value: string) {
  const hasExplicitZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);
  return new Date(hasExplicitZone ? value : `${value}+07:00`);
}

export function formatFinanceDateTime(value?: string | null, missingLabel = 'Chưa ghi nhận') {
  if (!value) return missingLabel;
  const date = parseFinanceTimestamp(value);
  if (Number.isNaN(date.getTime())) return 'Dữ liệu ngày giờ không hợp lệ';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: FINANCE_TIME_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const part = Object.fromEntries(parts.map((item) => [item.type, item.value]));
  return `${part.day}/${part.month}/${part.year} ${part.hour}:${part.minute}`;
}

export function formatFinanceDate(value?: string | null, missingLabel = 'Chưa ghi nhận') {
  if (!value) return missingLabel;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? new Date(`${value}T00:00:00+07:00`)
    : parseFinanceTimestamp(value);
  if (Number.isNaN(date.getTime())) return 'Dữ liệu ngày không hợp lệ';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: FINANCE_TIME_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).formatToParts(date);
  const part = Object.fromEntries(parts.map((item) => [item.type, item.value]));
  return `${part.day}/${part.month}/${part.year}`;
}

export function reportingDateInputValue(date: Date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: FINANCE_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function reportingMonthRange(now: Date) {
  const to = reportingDateInputValue(now);
  return { from: `${to.slice(0, 8)}01`, to };
}

export function quickDateRange(kind: QuickRange, now: Date) {
  const today = reportingDateInputValue(now);
  const [year, month, day] = today.split('-').map(Number);
  const cursor = new Date(Date.UTC(year, month - 1, day));
  if (kind === 'TODAY') return { from: today, to: today };
  if (kind === 'WEEK') {
    const weekday = cursor.getUTCDay() || 7;
    cursor.setUTCDate(cursor.getUTCDate() - weekday + 1);
  } else if (kind === 'MONTH') {
    cursor.setUTCDate(1);
  } else {
    cursor.setUTCMonth(Math.floor((month - 1) / 3) * 3, 1);
  }
  return { from: utcDateInput(cursor), to: today };
}

export function toExclusiveReportingRange(from: string, to: string) {
  if (!isDateInput(from) || !isDateInput(to) || from > to) {
    throw new Error('Khoảng thời gian không hợp lệ.');
  }
  const [year, month, day] = to.split('-').map(Number);
  const nextDate = new Date(Date.UTC(year, month - 1, day + 1));
  const toExclusive = utcDateInput(nextDate);
  return {
    from: new Date(`${from}T00:00:00+07:00`).toISOString(),
    to: new Date(`${toExclusive}T00:00:00+07:00`).toISOString(),
  };
}

export function toExclusiveLocalDateTimeRange(from: string, to: string) {
  if (!isDateInput(from) || !isDateInput(to) || from > to) {
    throw new Error('Khoảng thời gian không hợp lệ.');
  }
  const [year, month, day] = to.split('-').map(Number);
  const nextDate = utcDateInput(new Date(Date.UTC(year, month - 1, day + 1)));
  return { from: `${from}T00:00:00`, to: `${nextDate}T00:00:00` };
}

export function waitingCalendarDays(createdAt: string, now = new Date()) {
  const created = parseFinanceTimestamp(createdAt);
  if (Number.isNaN(created.getTime())) return null;
  const createdDate = reportingDateInputValue(created);
  const currentDate = reportingDateInputValue(now);
  return Math.max(0, dateInputDayNumber(currentDate) - dateInputDayNumber(createdDate));
}

export function formatDateRange(from: string, to: string) {
  return `${formatFinanceDate(from)} – ${formatFinanceDate(to)}`;
}

export function formatRevenueBucket(bucket: string, granularity: RevenueGranularity) {
  if (granularity === 'MONTH') return formatFinanceDate(bucket).slice(3);
  if (granularity === 'WEEK') return `Tuần từ ${formatFinanceDate(bucket)}`;
  return formatFinanceDate(bucket);
}

function utcDateInput(date: Date) {
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, '0'),
    String(date.getUTCDate()).padStart(2, '0'),
  ].join('-');
}

function dateInputDayNumber(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return Math.floor(Date.UTC(year, month - 1, day) / 86_400_000);
}

function isDateInput(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year
    && parsed.getUTCMonth() === month - 1
    && parsed.getUTCDate() === day;
}
