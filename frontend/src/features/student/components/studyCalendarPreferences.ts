export const CALENDAR_PINS_STORAGE_KEY = 'manabihub.student.calendar-pins.v1';
export const CALENDAR_PINS_UPDATED_EVENT = 'manabihub:calendar-pins-updated';

export function readPinnedCourseIds(): Set<string> {
  if (typeof window === 'undefined') return new Set();
  try {
    const stored = JSON.parse(window.localStorage.getItem(CALENDAR_PINS_STORAGE_KEY) ?? '[]') as unknown;
    return new Set(Array.isArray(stored) ? stored.filter((value): value is string => typeof value === 'string') : []);
  } catch {
    return new Set();
  }
}

export function setCoursePinned(courseId: string, pinned: boolean) {
  const next = readPinnedCourseIds();
  if (pinned) next.add(courseId);
  else next.delete(courseId);
  window.localStorage.setItem(CALENDAR_PINS_STORAGE_KEY, JSON.stringify(Array.from(next)));
  window.dispatchEvent(new Event(CALENDAR_PINS_UPDATED_EVENT));
}
