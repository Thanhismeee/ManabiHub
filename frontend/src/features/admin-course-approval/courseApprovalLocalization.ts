import type { CourseApproval } from './types';

const STATUS_LABELS: Record<CourseApproval['status'], string> = {
  DRAFT: 'Cần chỉnh sửa',
  PENDING: 'Chờ phê duyệt',
  APPROVED: 'Đã phê duyệt',
  PUBLISHED: 'Đã xuất bản',
  REJECTED: 'Đã từ chối',
  REQUEST_CORRECTION: 'Yêu cầu chỉnh sửa',
};

export function getCourseApprovalStatusLabel(status: CourseApproval['status']) {
  return STATUS_LABELS[status];
}

export function formatSubmittedTime(dateString: string, now = new Date()) {
  const submittedAt = new Date(dateString);
  if (Number.isNaN(submittedAt.getTime())) return 'Không xác định';

  const diffInSeconds = Math.max(
    0,
    Math.floor((now.getTime() - submittedAt.getTime()) / 1000),
  );

  if (diffInSeconds < 60) return `${diffInSeconds} giây trước`;
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) return `${diffInMinutes} phút trước`;
  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) return `${diffInHours} giờ trước`;
  return `${Math.floor(diffInHours / 24)} ngày trước`;
}

export function localizePolicyEvidence(value?: string | null) {
  if (!value?.trim()) return 'Không có minh chứng.';

  const acceptedAgreement = value.trim().match(
    /^Digital Copyright Liability Agreement accepted upon course submission at\s+(.+)$/i,
  );

  if (!acceptedAgreement) return value.trim();

  const acceptedAt = new Date(acceptedAgreement[1]);
  const acceptedAtLabel = Number.isNaN(acceptedAt.getTime())
    ? acceptedAgreement[1]
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short',
      }).format(acceptedAt);

  return `Giảng viên đã chấp nhận cam kết trách nhiệm bản quyền số khi gửi khóa học vào lúc ${acceptedAtLabel}.`;
}
