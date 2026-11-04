export function hasCourseAccessPeriodEnded(
  accessExpiresAt?: string | null,
  now = Date.now(),
): boolean {
  if (!accessExpiresAt) return false;

  const deadline = Date.parse(accessExpiresAt);
  return Number.isFinite(deadline) && deadline <= now;
}
