import type { PublicCourseSummary } from '../types/catalogTypes';
import type { PublicCourseDetail } from '../types/courseDetailTypes';

const LEVEL_RANK: Record<string, number> = {
  N1: 1,
  N2: 2,
  N3: 3,
  N4: 4,
  N5: 5,
};

const STOP_WORDS = new Set([
  'cho',
  'cua',
  'va',
  'voi',
  'nguoi',
  'moi',
  'bat',
  'dau',
  'trong',
  'theo',
  'nhung',
  'nhat',
  'hoc',
  'khoa',
  'co',
  'ban',
  'cap',
  'toc',
  'nen',
  'tang',
]);

function normalize(value: string | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function terms(value: string | undefined): Set<string> {
  return new Set(
    normalize(value)
      .split(/[^a-z0-9]+/)
      .filter((term) => term.length >= 3 && !STOP_WORDS.has(term)),
  );
}

function levelDistance(left: string | undefined, right: string | undefined): number | null {
  const leftRank = left ? LEVEL_RANK[left.toUpperCase()] : undefined;
  const rightRank = right ? LEVEL_RANK[right.toUpperCase()] : undefined;
  if (leftRank === undefined || rightRank === undefined) return null;
  return Math.abs(leftRank - rightRank);
}

/**
 * Selects courses that are useful alternatives, rather than merely popular.
 * Courses more than one JLPT level away are excluded so a beginner does not
 * get an advanced recommendation just because the teacher/category matches.
 */
export function getRelatedCourses(
  course: PublicCourseDetail,
  candidates: PublicCourseSummary[],
  limit = 4,
): PublicCourseSummary[] {
  const currentCategory = normalize(course.category);
  const currentTeacherId = course.teacher?.id;
  const currentTopics = terms(
    [course.title, course.introduction, course.description].filter(Boolean).join(' '),
  );

  return candidates
    .filter((candidate) => candidate.id !== course.id)
    .map((candidate) => {
      const categoryMatch = Boolean(
        currentCategory && normalize(candidate.category) === currentCategory,
      );
      const teacherMatch = Boolean(
        currentTeacherId && candidate.teacherId && candidate.teacherId === currentTeacherId,
      );
      const distance = levelDistance(course.jlptLevel, candidate.jlptLevel);

      // Keep recommendations within the same level or an immediately adjacent level.
      if (distance !== null && distance > 1) return null;

      const candidateTopics = terms(candidate.title);
      const topicOverlap = [...currentTopics].filter((term) => candidateTopics.has(term)).length;
      const sameLevel = distance === 0;
      const adjacentLevel = distance === 1;
      const score =
        (categoryMatch ? 50 : 0) +
        (teacherMatch ? 40 : 0) +
        (sameLevel ? 25 : adjacentLevel ? 14 : 0) +
        Math.min(topicOverlap * 8, 24) +
        Math.min(candidate.reviewCount ?? 0, 10) * 0.1;

      // At least one meaningful relationship is required; do not show random catalog items.
      if (!categoryMatch && !teacherMatch && topicOverlap === 0) return null;

      return { candidate, score };
    })
    .filter((item): item is { candidate: PublicCourseSummary; score: number } => item !== null)
    .sort((left, right) => {
      if (right.score !== left.score) return right.score - left.score;
      return (right.candidate.averageRating ?? 0) - (left.candidate.averageRating ?? 0);
    })
    .slice(0, limit)
    .map(({ candidate }) => candidate);
}
