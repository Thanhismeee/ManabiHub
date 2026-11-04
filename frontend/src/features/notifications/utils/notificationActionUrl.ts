const hasControlCharacter = (value: string) => Array.from(value).some((character) => {
  const codePoint = character.codePointAt(0) ?? 0;
  return codePoint <= 0x1F || codePoint === 0x7F;
});

/**
 * Notification actions are an internal navigation contract from the backend.
 * Reject absolute, protocol-relative and malformed values so a stored notification
 * cannot become an open redirect or a javascript: link in the UI.
 */
export function getSafeNotificationActionPath(actionUrl?: string): string | null {
  const candidate = actionUrl?.trim();
  if (
    !candidate
    || !candidate.startsWith('/')
    || candidate.startsWith('//')
    || candidate.includes('\\')
    || hasControlCharacter(candidate)
  ) {
    return null;
  }

  try {
    const parsed = new URL(candidate, 'https://manabihub.local');
    if (parsed.origin !== 'https://manabihub.local') {
      return null;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}
