import { describe, expect, it } from 'vitest';
import {
  formatFinanceDateTime,
  formatMoney,
  quickDateRange,
  toExclusiveReportingRange,
  waitingCalendarDays,
} from './financeDisplay';

describe('finance display rules', () => {
  it('uses an exclusive next-day boundary so the complete final day is included', () => {
    expect(toExclusiveReportingRange('2026-08-01', '2026-08-31')).toEqual({
      from: '2026-07-31T17:00:00.000Z',
      to: '2026-08-31T17:00:00.000Z',
    });
  });

  it('rejects impossible calendar dates instead of letting JavaScript normalize them', () => {
    expect(() => toExclusiveReportingRange('2026-02-30', '2026-03-01'))
      .toThrow('Khoảng thời gian không hợp lệ.');
    expect(() => toExclusiveReportingRange('2026-03-01', '2026-02-30'))
      .toThrow('Khoảng thời gian không hợp lệ.');
  });

  it('builds week and quarter ranges using Vietnam business dates', () => {
    const now = new Date('2026-08-23T18:30:00Z');
    expect(quickDateRange('WEEK', now)).toEqual({ from: '2026-08-24', to: '2026-08-24' });
    expect(quickDateRange('QUARTER', now)).toEqual({ from: '2026-07-01', to: '2026-08-24' });
  });

  it('calculates calendar waiting days in Asia/Ho_Chi_Minh', () => {
    expect(waitingCalendarDays(
      '2026-08-11T16:59:00Z',
      new Date('2026-08-14T17:01:00Z'),
    )).toBe(4);
  });

  it('does not turn missing money into a false zero', () => {
    expect(formatMoney(undefined)).toBe('Chưa ghi nhận');
    expect(formatMoney(0)).toContain('0');
  });

  it('formats local backend date-times as Vietnam time explicitly', () => {
    expect(formatFinanceDateTime('2026-08-25T09:05:00')).toBe('25/08/2026 09:05');
  });
});
