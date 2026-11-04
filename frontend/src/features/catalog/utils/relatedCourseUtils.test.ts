import { describe, expect, it } from 'vitest';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import type { PublicCourseSummary } from '../types/catalogTypes';
import { getRelatedCourses } from './relatedCourseUtils';

const currentCourse = {
  id: 'current',
  title: 'Tiếng Nhật du lịch cấp tốc',
  introduction: 'Mẫu câu giao tiếp và hội thoại khi đi du lịch Nhật Bản',
  jlptLevel: 'N5',
  category: 'communication',
  teacher: { id: 'teacher-1' },
} as PublicCourseDetail;

const candidate = (overrides: Partial<PublicCourseSummary>): PublicCourseSummary => ({
  id: 'candidate',
  title: 'Khóa học liên quan',
  slug: 'candidate',
  price: 0,
  currency: 'VND',
  totalLessons: 5,
  ...overrides,
});

describe('getRelatedCourses', () => {
  it('prioritizes same teacher/category and excludes distant JLPT levels', () => {
    const sameTeacher = candidate({
      id: 'same-teacher',
      title: 'Giao tiếp tiếng Nhật cơ bản',
      teacherId: 'teacher-1',
      jlptLevel: 'N5',
    });
    const sameCategory = candidate({
      id: 'same-category',
      title: 'Hội thoại tiếng Nhật N4',
      category: 'communication',
      jlptLevel: 'N4',
    });
    const tooAdvanced = candidate({
      id: 'too-advanced',
      title: 'Luyện thi N3 nâng cao',
      category: 'communication',
      jlptLevel: 'N3',
      teacherId: 'teacher-1',
    });

    expect(getRelatedCourses(currentCourse, [tooAdvanced, sameCategory, sameTeacher])).toEqual([
      sameTeacher,
      sameCategory,
    ]);
  });

  it('does not show unrelated courses when no relationship exists', () => {
    const unrelated = candidate({
      id: 'unrelated',
      title: 'Kanji N2 chuyên sâu',
      category: 'kanji',
      jlptLevel: 'N2',
      teacherId: 'another-teacher',
    });

    expect(getRelatedCourses(currentCourse, [unrelated])).toEqual([]);
  });
});
