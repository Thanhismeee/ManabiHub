import { describe, expect, it } from 'vitest';
import { getSafeNotificationActionPath } from './notificationActionUrl';

describe('getSafeNotificationActionPath', () => {
  it('keeps valid internal routes, queries and fragments', () => {
    expect(getSafeNotificationActionPath('/admin/violations/report-1?tab=evidence#latest'))
      .toBe('/admin/violations/report-1?tab=evidence#latest');
  });

  it.each([
    undefined,
    '',
    'admin/violations/report-1',
    '//malicious.example/path',
    'https://malicious.example/path',
    'javascript:alert(1)',
    '/\\malicious.example/path',
    '/admin/violations\n/report-1',
  ])('rejects unsafe or malformed action URL %s', (value) => {
    expect(getSafeNotificationActionPath(value)).toBeNull();
  });
});
