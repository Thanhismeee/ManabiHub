import { Chip } from '@mui/material';
import type { ViolationReportStatus } from '../types/violation.types';

const statusMap: Record<
  ViolationReportStatus,
  { label: string; color: string; background: string }
> = {
  PENDING_REVIEW: { label: 'Chờ duyệt', color: '#b45309', background: '#fef3c7' },
  IN_REVIEW: { label: 'Đang xem xét', color: '#1d4ed8', background: '#dbeafe' },
  PENDING_EVIDENCE: { label: 'Chờ bằng chứng', color: '#6d28d9', background: '#ede9fe' },
  CORRECTION_REQUIRED: { label: 'Yêu cầu chỉnh sửa', color: '#047857', background: '#d1fae5' },
  RESOLVED_UPHELD: { label: 'Đã xác nhận vi phạm', color: '#b91c1c', background: '#fee2e2' },
  RESOLVED_NO_VIOLATION: { label: 'Không vi phạm', color: '#15803d', background: '#dcfce7' },
  INVALID: { label: 'Không hợp lệ', color: '#475569', background: '#e2e8f0' },
  CANCELLED: { label: 'Đã hủy', color: '#475569', background: '#e2e8f0' },
};

export function ViolationStatusBadge({ status }: { status: ViolationReportStatus }) {
  const config = statusMap[status];
  return (
    <Chip
      size="small"
      label={config.label}
      sx={{
        bgcolor: config.background,
        color: config.color,
        fontWeight: 700,
        borderRadius: 1,
      }}
    />
  );
}
