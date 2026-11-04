import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { CourseApproval, CourseApprovalDetail, ReviewActionPayload } from '../types';

const asArray = <T>(value: T[] | null | undefined): T[] => (
  Array.isArray(value) ? value : []
);

/**
 * Keep the approval screen renderable while the backend is being restarted or
 * during a rolling deployment where an older response shape can still arrive.
 * Approval stays disabled until the server supplies the new validation result.
 */
export const normalizeCourseApprovalDetail = (
  data: CourseApprovalDetail,
): CourseApprovalDetail => {
  const hasReviewResult = Array.isArray(data.reviewCriteria) && typeof data.approvalReady === 'boolean';
  const unavailableMessage = 'Chưa nhận được dữ liệu kiểm tra điều kiện từ backend. Vui lòng tải lại trang.';
  const modules = asArray(data.modules).map((module) => ({
    ...module,
    blocks: asArray(module.blocks).map((block) => ({
      ...block,
      quizOptions: asArray(block.quizOptions),
      quizItems: asArray(block.quizItems).map((item) => ({
        ...item,
        options: asArray(item.options),
      })),
      flashcards: asArray(block.flashcards),
    })),
  }));

  return {
    ...data,
    moduleCount: data.moduleCount ?? modules.length,
    lessonBlocksCount: data.lessonBlocksCount ?? modules.reduce((total, module) => total + module.blocks.length, 0),
    totalVideoDurationMinutes: data.totalVideoDurationMinutes ?? 0,
    teacherCanPublish: data.teacherCanPublish ?? false,
    approvalReady: hasReviewResult ? data.approvalReady : false,
    reviewDataAvailable: hasReviewResult,
    learningGoals: asArray(data.learningGoals),
    modules,
    validationErrors: hasReviewResult
      ? asArray(data.validationErrors)
      : [{ code: 'REVIEW_DATA_UNAVAILABLE', message: unavailableMessage, severity: 'error' }],
    reviewCriteria: hasReviewResult
      ? asArray(data.reviewCriteria).map((criterion) => ({
          ...criterion,
          reasons: asArray(criterion.reasons),
        }))
      : [{
          code: 'SYSTEM',
          title: 'Dữ liệu xét duyệt',
          description: 'Hệ thống cần tải đủ dữ liệu khóa học trước khi ra quyết định.',
          passed: false,
          reasons: [unavailableMessage],
        }],
    finalTest: data.finalTest
      ? {
          ...data.finalTest,
          questions: asArray(data.finalTest.questions).map((question) => ({
            ...question,
            choices: asArray(question.choices),
          })),
        }
      : null,
  };
};

export const courseApprovalService = {
  getQueue: async (): Promise<CourseApproval[]> => {
    const res = await axiosClient.get(ENDPOINTS.ADMIN_COURSE_APPROVAL.QUEUE);
    return res.data.data;
  },

  getDetail: async (id: string): Promise<CourseApprovalDetail> => {
    const res = await axiosClient.get(ENDPOINTS.ADMIN_COURSE_APPROVAL.DETAIL(id));
    return normalizeCourseApprovalDetail(res.data.data);
  },

  reviewCourse: async (id: string, payload: ReviewActionPayload): Promise<void> => {
    await axiosClient.post(ENDPOINTS.ADMIN_COURSE_APPROVAL.REVIEW(id), payload);
  }
};
