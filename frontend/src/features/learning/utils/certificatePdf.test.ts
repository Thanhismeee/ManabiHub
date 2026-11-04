import { describe, expect, it } from 'vitest';
import { parseCertificateTimestamp } from './certificatePdf';

describe('parseCertificateTimestamp', () => {
  const expected = Date.parse('2026-08-10T08:01:04.590Z');

  it('parses ISO-8601 timestamps returned by the certificate API', () => {
    expect(parseCertificateTimestamp('2026-08-10T08:01:04.590Z')?.getTime()).toBe(expected);
  });

  it('normalizes epoch seconds without accidentally producing a 1970 date', () => {
    expect(parseCertificateTimestamp(expected / 1000)?.getTime()).toBe(expected);
    expect(parseCertificateTimestamp(String(expected / 1000))?.getTime()).toBe(expected);
  });

  it('keeps epoch milliseconds unchanged', () => {
    expect(parseCertificateTimestamp(expected)?.getTime()).toBe(expected);
  });

  it('rejects zero and other invalid epoch defaults', () => {
    expect(parseCertificateTimestamp(0)).toBeNull();
    expect(parseCertificateTimestamp('1970-01-01T00:00:00Z')).toBeNull();
    expect(parseCertificateTimestamp('not-a-date')).toBeNull();
  });
});
