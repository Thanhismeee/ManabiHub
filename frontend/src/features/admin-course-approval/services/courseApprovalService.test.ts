import { describe, expect, it } from 'vitest';
import type { CourseApprovalDetail } from '../types';
import { normalizeCourseApprovalDetail } from './courseApprovalService';

describe('normalizeCourseApprovalDetail', () => {
  it('supplies safe defaults for an older backend response', () => {
    const legacyResponse = {
      id: 'course-1',
      courseName: 'Legacy course',
      teacherName: 'Teacher',
      teacherEmail: 'teacher@example.com',
      submittedAt: '2026-08-12T01:00:00Z',
      status: 'PENDING',
      curriculumSummary: 'Summary',
      lessonBlocksCount: 3,
      finalTestIncluded: true,
    } as CourseApprovalDetail;

    const normalized = normalizeCourseApprovalDetail(legacyResponse);

    expect(normalized.validationErrors).toEqual([
      expect.objectContaining({ code: 'REVIEW_DATA_UNAVAILABLE' }),
    ]);
    expect(normalized.reviewCriteria).toEqual([
      expect.objectContaining({ code: 'SYSTEM', passed: false }),
    ]);
    expect(normalized.learningGoals).toEqual([]);
    expect(normalized.modules).toEqual([]);
    expect(normalized.finalTest).toBeNull();
    expect(normalized.approvalReady).toBe(false);
    expect(normalized.reviewDataAvailable).toBe(false);
  });

  it('normalizes missing nested content collections', () => {
    const response = {
      id: 'course-2',
      courseName: 'Course',
      teacherName: 'Teacher',
      teacherEmail: 'teacher@example.com',
      submittedAt: '2026-08-12T01:00:00Z',
      status: 'PENDING',
      curriculumSummary: 'Summary',
      lessonBlocksCount: 1,
      finalTestIncluded: false,
      approvalReady: false,
      reviewCriteria: [{
        code: 'CONTENT',
        title: 'Content',
        description: 'Check content',
        passed: false,
      }],
      modules: [{
        id: 'module-1',
        title: 'Module',
        orderIndex: 0,
        blocks: [{
          id: 'block-1',
          type: 'QUIZ',
          title: 'Quiz',
          orderIndex: 0,
          interactionRequiredAfter: false,
          interactionSatisfied: true,
        }],
      }],
    } as CourseApprovalDetail;

    const normalized = normalizeCourseApprovalDetail(response);

    expect(normalized.reviewCriteria[0].reasons).toEqual([]);
    expect(normalized.reviewDataAvailable).toBe(true);
    expect(normalized.modules[0].blocks[0].quizOptions).toEqual([]);
    expect(normalized.modules[0].blocks[0].quizItems).toEqual([]);
    expect(normalized.modules[0].blocks[0].flashcards).toEqual([]);
  });
});
