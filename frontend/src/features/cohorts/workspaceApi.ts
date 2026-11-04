import { axiosClient } from '../../shared/api/axiosClient';

export type AssignmentType = 'WRITING' | 'SPEAKING';
export interface Assignment {
  id: string; title: string; instructions: string; type: AssignmentType;
  dueAt: string; acceptUntil: string; maxAttempts: number; maxAudioSeconds: number; closed: boolean;
}
export interface Submission {
  id: string; assignmentId: string; memberId: string; studentName: string; attempt: number;
  content: string | null; hasAudio: boolean; reportedAudioSeconds: number | null; verifiedAudioSeconds: number | null;
  submittedAt: string; reviewDueAt: string; feedback: string | null; score: number | null;
  revisionRequested: boolean; reviewMinutes: number | null; reviewedAt: string | null;
}
export interface MemberProgress {
  memberId: string; fullName: string; email: string; overdueAssignments: number; awaitingRevision: number;
  submittedAssignments: number; feedbackUsed: number; feedbackRemaining: number; feedbackReserved: number;
  averageScore: number | null; lastSubmissionAt: string | null; lastReminderAt: string | null;
}
export interface Workspace {
  cohortId: string; name: string; code: string; courseId: string;
  learningLanguage: 'JAPANESE' | 'KOREAN' | 'CHINESE_SIMPLIFIED';
  status: 'PLANNED' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'; startDate: string; endDate: string;
  submissionAllowed: boolean; submissionBlockReason: string | null;
  feedbackQuotaPerStudent: number; reviewSlaHours: number; feedbackUsed: number; feedbackRemaining: number;
  assignments: Assignment[]; submissions: Submission[]; members: MemberProgress[];
}
export interface CohortReport {
  cohortId: string; name: string; code: string; organizationName: string; branchName: string | null;
  teacherName: string; learningLanguage: Workspace['learningLanguage']; activeStudents: number; assignments: number;
  expectedDueSubmissions: number; submittedDueAssignments: number; overdueAssignments: number; studentsAtRisk: number;
  pendingReviews: number; overdueReviews: number; reviewedAttempts: number; reviewedWithinSla: number;
  reviewMinutes: number; feedbackUsed: number; feedbackQuotaPerStudent: number; averageScore: number | null;
}
interface Envelope<T> { data: T }
const teacherRoot = '/v1/teacher/cohorts';
const studentRoot = '/v1/student/cohorts';
export async function getWorkspace(id: string, teacher: boolean) {
  const { data } = await axiosClient.get<Envelope<Workspace>>(teacher ? `${teacherRoot}/${id}/workspace` : `${studentRoot}/${id}`);
  return data.data;
}
export interface ClassSummary {
  cohortId: string; name: string; code: string; courseId: string; status: Workspace['status']; startDate: string; endDate: string;
  assignmentCount: number; feedbackRemaining: number;
}
export interface ReportPage { items: CohortReport[]; page: number; size: number; totalElements: number; organizations: string[]; branches: string[] }
export async function getStudentClasses() {
  const { data } = await axiosClient.get<Envelope<ClassSummary[]>>(studentRoot);
  return data.data;
}
export async function createAssignment(id: string, payload: Omit<Assignment, 'id' | 'closed'>) {
  const { data } = await axiosClient.post<Envelope<Workspace>>(`${teacherRoot}/${id}/assignments`, payload);
  return data.data;
}
export async function closeAssignment(id: string, assignmentId: string) {
  const { data } = await axiosClient.post<Envelope<Workspace>>(`${teacherRoot}/${id}/assignments/${assignmentId}/close`);
  return data.data;
}
export async function configurePackage(id: string, feedbackQuotaPerStudent: number, reviewSlaHours: number) {
  const { data } = await axiosClient.patch<Envelope<Workspace>>(`${teacherRoot}/${id}/feedback-package`, { feedbackQuotaPerStudent, reviewSlaHours });
  return data.data;
}
export async function reviewSubmission(id: string, submissionId: string, payload: { feedback: string; score: number; revisionRequested: boolean; reviewMinutes: number }) {
  const { data } = await axiosClient.post<Envelope<Workspace>>(`${teacherRoot}/${id}/submissions/${submissionId}/feedback`, payload);
  return data.data;
}
export async function submitAssignment(id: string, assignmentId: string, content: string, recording?: { file: File; seconds: number }) {
  const form = new FormData();
  form.append('content', content);
  if (recording) { form.append('audio', recording.file); form.append('reportedAudioSeconds', String(recording.seconds)); }
  const { data } = await axiosClient.post<Envelope<Workspace>>(`${studentRoot}/${id}/assignments/${assignmentId}/submissions`, form);
  return data.data;
}
export async function remindMember(id: string, memberId: string) {
  const { data } = await axiosClient.post<Envelope<Workspace>>(`${teacherRoot}/${id}/members/${memberId}/remind`);
  return data.data;
}
export async function fetchAudio(submissionId: string, teacher: boolean) {
  const { data } = await axiosClient.get<Blob>(`${teacher ? teacherRoot : studentRoot}/submissions/${submissionId}/audio`, { responseType: 'blob' });
  return data;
}
export async function getReports(admin: boolean, params: { organization?: string; branch?: string; from?: string; to?: string; page?: number; size?: number } = {}) {
  const { data } = await axiosClient.get<Envelope<ReportPage>>(admin ? '/v1/admin/cohort-reports' : `${teacherRoot}/reports`, { params });
  return data.data;
}
