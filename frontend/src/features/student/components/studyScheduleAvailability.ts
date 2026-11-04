import type { StudyCourseOption, StudySlot } from './StudyGoalsWidget';

function instant(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function isCourseAvailableOnDate(course: StudyCourseOption | undefined, date: Date) {
  if (!course) return false;
  if (course.enrollmentStatus && course.enrollmentStatus !== 'ACTIVE') return false;

  const target = instant(date);
  const enrolledAt = course.enrolledAt ? instant(course.enrolledAt) : null;
  const expiresAt = course.expiresAt ? instant(course.expiresAt) : null;
  if (!target) return false;
  if (enrolledAt && target < enrolledAt) return false;
  if (expiresAt && target >= expiresAt) return false;
  return true;
}

export function isSlotAvailableOnDate(slot: StudySlot, date: Date, courses: StudyCourseOption[]) {
  if (!slot.courseId) return true;
  if (courses.length === 0) return true;
  return isCourseAvailableOnDate(courses.find((course) => course.id === slot.courseId), date);
}
