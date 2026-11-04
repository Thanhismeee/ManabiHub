import { describe, expect, it } from 'vitest';
import { sanitizeRichText } from './sanitizeRichText';

describe('sanitizeRichText', () => {
  it('keeps the bounded formatting used by course editors', () => {
    const sanitized = sanitizeRichText(
      '<p><strong>日本語</strong></p><ol><li data-list="bullet">Mục tiêu</li></ol>',
    );

    expect(sanitized).toContain('<strong>日本語</strong>');
    expect(sanitized).toContain('data-list="bullet"');
  });

  it('removes executable markup and unsafe attributes', () => {
    const sanitized = sanitizeRichText(
      '<p style="color:red" onclick="alert(1)">Nội dung</p>'
        + '<script>alert(1)</script>'
        + '<a href="javascript:alert(1)" target="_blank">link</a>'
        + '<img src=x onerror=alert(1)>',
    );

    expect(sanitized).toContain('Nội dung');
    expect(sanitized).not.toMatch(/script|onclick|javascript:|target|style=|onerror|<img/i);
  });
});
