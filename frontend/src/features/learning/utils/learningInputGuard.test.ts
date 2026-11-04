import { describe, expect, it } from 'vitest';
import { isClipboardShortcut, isSingleCharacterMutation } from './learningInputGuard';

describe('learning input guard', () => {
  it('allows one-character insertion and deletion anywhere in the text', () => {
    expect(isSingleCharacterMutation('abc', 'abXc')).toBe(true);
    expect(isSingleCharacterMutation('abc', 'ac')).toBe(true);
  });

  it('rejects multi-character insertion such as paste', () => {
    expect(isSingleCharacterMutation('abc', 'abc pasted')).toBe(false);
    expect(isSingleCharacterMutation('', 'hello')).toBe(false);
  });

  it('recognizes clipboard keyboard shortcuts', () => {
    expect(isClipboardShortcut({ key: 'v', ctrlKey: true })).toBe(true);
    expect(isClipboardShortcut({ key: 'c', metaKey: true })).toBe(true);
    expect(isClipboardShortcut({ key: 'Insert', shiftKey: true })).toBe(true);
    expect(isClipboardShortcut({ key: 'a', ctrlKey: true })).toBe(false);
  });
});
