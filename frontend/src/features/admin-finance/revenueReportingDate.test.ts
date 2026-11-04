import { describe, expect, it } from 'vitest';
import { reportingDateInputValue, reportingMonthRange } from './revenueReportingDate';

describe('revenue reporting dates', () => {
  it('uses the Vietnam business date across a UTC date boundary', () => {
    expect(reportingDateInputValue(new Date('2026-07-31T18:30:00Z'))).toBe('2026-08-01');
  });

  it('starts the default range on the first Vietnam business day of the month', () => {
    expect(reportingMonthRange(new Date('2026-08-16T18:00:00Z'))).toEqual({
      from: '2026-08-01',
      to: '2026-08-17',
    });
  });
});
