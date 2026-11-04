import { describe, expect, it } from 'vitest';
import { isClipboardShortcut, isScreenshotShortcut, violationLabel } from './finalTestProctoring';

describe('final test proctoring helpers', () => {
  it('recognizes clipboard shortcuts', () => {
    expect(isClipboardShortcut({ key: 'v', ctrlKey: true })).toBe(true);
    expect(isClipboardShortcut({ key: 'c', metaKey: true })).toBe(true);
    expect(isClipboardShortcut({ key: 'a', ctrlKey: true })).toBe(false);
  });

  it('recognizes common screenshot shortcuts without claiming OS-level certainty', () => {
    expect(isScreenshotShortcut({ key: 'PrintScreen', code: 'PrintScreen' })).toBe(true);
    expect(isScreenshotShortcut({ key: '4', code: 'Digit4', metaKey: true, shiftKey: true })).toBe(true);
    expect(isScreenshotShortcut({ key: 's', code: 'KeyS', metaKey: true, shiftKey: true })).toBe(true);
    expect(isScreenshotShortcut({ key: 's', code: 'KeyS', ctrlKey: true, shiftKey: true })).toBe(true);
    expect(isScreenshotShortcut({ key: 's', code: 'KeyS', ctrlKey: true })).toBe(false);
  });

  it('provides a human-readable violation label', () => {
    expect(violationLabel('TAB_SWITCH')).toBe('Chuyển tab hoặc ẩn trang thi');
  });
});
