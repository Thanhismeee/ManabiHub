import axios from 'axios';
import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';

export type JlptLevel = 'N5' | 'N4' | 'N3' | 'N2' | 'N1';
export type CourseStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'FORCED_DRAFT' | 'ARCHIVED';
export type LessonBlockType = 'VIDEO' | 'TEXT' | 'QUIZ' | 'FLASHCARD' | 'WRITING';

export interface CreateCourseDraftPayload {
  title: string;
  introduction: string;
  jlptLevel?: JlptLevel | null;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  proficiencyLevel: string;
  category: string;
  thumbnailUrl?: string | null;
  outcomes: string;
  price: number;
  prerequisites: string;
  targetStudents: string;
  learningGoals: string[];
  accessDurationDays?: number | null;
  accessExpiresAt?: string | null;
}

export interface CourseDraftResponse {
  id: string;
  teacherId: string;
  title: string;
  slug: string;
  introduction: string;
  jlptLevel?: JlptLevel | null;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  proficiencyLevel: string;
  category: string;
  thumbnailUrl?: string | null;
  outcomes: string;
  price: number;
  currency: string;
  prerequisites: string;
  targetStudents: string;
  status: CourseStatus;
  learningGoals: string[];
  accessDurationDays?: number | null;
  accessExpiresAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  srsTrace: Record<string, unknown>;
}

export interface CourseCategory {
  id: string;
  code: string;
  name: string;
  description?: string | null;
}

export interface CourseThumbnailUploadResponse {
  publicUrl: string;
  fileName: string;
  contentType: string;
  size: number;
}

export interface CourseModulePayload {
  title: string;
  description?: string | null;
}

export interface FlashcardItemPayload {
  front: string;
  back: string;
}

export interface QuizQuestionPayload {
  question: string;
  options: string[];
  answer: string;
}

export interface LessonBlockPayload {
  type: LessonBlockType;
  title: string;
  content?: string | null;
  videoUrl?: string | null;
  durationMinutes?: number | null;
  quizQuestion?: string | null;
  quizOptions?: string[] | null;
  quizAnswer?: string | null;
  quizItems?: QuizQuestionPayload[] | null;
  flashcards?: FlashcardItemPayload[] | null;
  writingPrompt?: string | null;
  rubric?: string | null;
}

export interface LessonBlockResponse extends LessonBlockPayload {
  id: string;
  orderIndex: number;
  interactionRequiredAfter: boolean;
  interactionSatisfied: boolean;
  validationMessage?: string | null;
  quizOptions: string[];
  quizItems: QuizQuestionPayload[];
  flashcards: FlashcardItemPayload[];
}

export interface CourseModuleResponse {
  id: string;
  title: string;
  description?: string | null;
  orderIndex: number;
  blocks: LessonBlockResponse[];
}

export interface CourseBuilderResponse {
  draftId: string;
  courseTitle: string;
  modules: CourseModuleResponse[];
  validationWarnings: string[];
  srsTrace: Record<string, unknown>;
}

export interface ValidationError {
  code: string;
  message: string;
  severity: string;
}

export interface ValidationResultResponse {
  isValid: boolean;
  errors: ValidationError[];
}

interface ApiResponse<T> {
  success: boolean;
  messageCode?: string;
  message?: string;
  data: T;
}

export async function createCourseDraft(payload: CreateCourseDraftPayload) {
  const response = await axiosClient.post<ApiResponse<CourseDraftResponse>>(
    ENDPOINTS.teacherCourses.drafts,
    payload,
  );

  return response.data.data;
}

export async function updateCourseDraft(id: string, payload: CreateCourseDraftPayload) {
  const response = await axiosClient.put<ApiResponse<CourseDraftResponse>>(
    ENDPOINTS.teacherCourses.draftDetail(id),
    payload,
  );

  return response.data.data;
}

export async function fetchCourseDrafts() {
  const response = await axiosClient.get<ApiResponse<CourseDraftResponse[]>>(ENDPOINTS.teacherCourses.list);

  return response.data.data;
}

export async function deleteCourseDraft(id: string) {
  await axiosClient.delete<ApiResponse<void>>(ENDPOINTS.teacherCourses.draftDetail(id));
}

export async function submitCourseForReview(draftId: string) {
  await axiosClient.post<ApiResponse<void>>(
      ENDPOINTS.teacherCourses.submitReview(draftId),
  );
}

export async function publishCourse(courseId: string) {
  const response = await axiosClient.post<ApiResponse<void>>(
    ENDPOINTS.teacherCourses.publish(courseId),
  );

  return response.data;
}

export async function unpublishCourse(courseId: string) {
  const response = await axiosClient.post<ApiResponse<CourseDraftResponse>>(
    ENDPOINTS.teacherCourses.unpublish(courseId),
  );

  return response.data;
}

export async function fetchCourseBuilder(draftId: string) {
  const response = await axiosClient.get<ApiResponse<CourseBuilderResponse>>(ENDPOINTS.teacherCourses.builder(draftId));

  return response.data.data;
}

export async function validateCourseDraft(draftId: string) {
  const response =
      await axiosClient.get<ApiResponse<ValidationResultResponse>>(
          ENDPOINTS.teacherCourses.validate(draftId),
      );

  return response.data.data;
}

export async function createCourseModule(draftId: string, payload: CourseModulePayload) {
  const response = await axiosClient.post<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderModules(draftId),
    payload,
  );

  return response.data.data;
}

export async function updateCourseModule(draftId: string, moduleId: string, payload: CourseModulePayload) {
  const response = await axiosClient.put<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderModuleDetail(draftId, moduleId),
    payload,
  );

  return response.data.data;
}

export async function deleteCourseModule(draftId: string, moduleId: string) {
  const response = await axiosClient.delete<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderModuleDetail(draftId, moduleId),
  );

  return response.data.data;
}

export async function reorderCourseModules(draftId: string, orderedIds: string[]) {
  const response = await axiosClient.put<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderModuleOrder(draftId),
    { orderedIds },
  );

  return response.data.data;
}

export async function createLessonBlock(draftId: string, moduleId: string, payload: LessonBlockPayload) {
  const response = await axiosClient.post<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderBlocks(draftId, moduleId),
    payload,
  );

  return response.data.data;
}

export async function updateLessonBlock(draftId: string, moduleId: string, blockId: string, payload: LessonBlockPayload) {
  const response = await axiosClient.put<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderBlockDetail(draftId, moduleId, blockId),
    payload,
  );

  return response.data.data;
}

export async function deleteLessonBlock(draftId: string, moduleId: string, blockId: string) {
  const response = await axiosClient.delete<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderBlockDetail(draftId, moduleId, blockId),
  );

  return response.data.data;
}

export async function reorderLessonBlocks(draftId: string, moduleId: string, orderedIds: string[]) {
  const response = await axiosClient.put<ApiResponse<CourseBuilderResponse>>(
    ENDPOINTS.teacherCourses.builderBlockOrder(draftId, moduleId),
    { orderedIds },
  );

  return response.data.data;
}

export async function fetchCourseCategories() {
  const response = await axiosClient.get<ApiResponse<CourseCategory[]>>(ENDPOINTS.courseCategories.list);

  return response.data.data;
}

export async function uploadCourseThumbnail(file: File) {
  const formData = new FormData();
  formData.append('thumbnail', file);

  const response = await axiosClient.post<ApiResponse<CourseThumbnailUploadResponse>>(
    ENDPOINTS.teacherCourseAssets.thumbnails,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );

  return response.data.data;
}

export function courseDraftApiError(error: unknown) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return {
      messageCode: error.response?.data?.messageCode,
      message: error.response?.data?.message ?? 'Không thể lưu bản nháp khóa học.',
    };
  }

  return {
    messageCode: undefined,
    message: 'Không thể lưu bản nháp khóa học.',
  };
}
