/**
 * Returns true when the next value represents at most one logical character
 * inserted or removed from the previous value. This is intentionally a
 * client-side anti-paste guard; the backend remains the source of truth for
 * submission validation.
 */
export function isSingleCharacterMutation(previous: string, next: string): boolean {
  if (previous === next) return true;

  const previousCharacters = Array.from(previous);
  const nextCharacters = Array.from(next);
  const lengthDelta = nextCharacters.length - previousCharacters.length;
  if (Math.abs(lengthDelta) !== 1) return false;

  const shorter = lengthDelta > 0 ? previousCharacters : nextCharacters;
  const longer = lengthDelta > 0 ? nextCharacters : previousCharacters;

  let prefixLength = 0;
  while (
    prefixLength < shorter.length
    && shorter[prefixLength] === longer[prefixLength]
  ) {
    prefixLength += 1;
  }

  let suffixLength = 0;
  while (
    suffixLength < shorter.length - prefixLength
    && shorter[shorter.length - 1 - suffixLength]
      === longer[longer.length - 1 - suffixLength]
  ) {
    suffixLength += 1;
  }

  return prefixLength + suffixLength === shorter.length;
}

export function isClipboardShortcut(event: { key: string; ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean }) {
  const key = event.key.toLowerCase();
  const modifier = Boolean(event.ctrlKey || event.metaKey);
  return Boolean(
    (modifier && (key === 'c' || key === 'x' || key === 'v'))
      || (event.shiftKey && key === 'insert'),
  );
}

export function readLocalStorageValue<T>(key: string): T | null {
  if (typeof window === 'undefined') return null;

  try {
    const value = window.localStorage.getItem(key);
    return value ? JSON.parse(value) as T : null;
  } catch {
    return null;
  }
}

export function writeLocalStorageValue(key: string, value: unknown) {
  if (typeof window === 'undefined') return;

  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage may be unavailable in private browsing or with a full quota.
  }
}

export function removeLocalStorageValue(key: string) {
  if (typeof window === 'undefined') return;

  try {
    window.localStorage.removeItem(key);
  } catch {
    // Ignore storage cleanup failures; the server copy remains authoritative.
  }
}
