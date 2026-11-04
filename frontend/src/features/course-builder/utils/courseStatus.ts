export function courseStatusLabel(status: string) {
  switch (status) {
    case 'PENDING':
      return 'Chờ duyệt';
    case 'APPROVED':
      return 'Đã duyệt';
    case 'PUBLISHED':
      return 'Đã xuất bản';
    case 'REJECTED':
      return 'Bị từ chối';
    case 'FORCED_DRAFT':
      return 'Cần chỉnh sửa';
    case 'ARCHIVED':
      return 'Đã lưu trữ';
    default:
      return 'Bản nháp';
  }
}

export function courseStatusColor(
  status: string,
): 'default' | 'warning' | 'info' | 'success' | 'error' {
  switch (status) {
    case 'PENDING':
    case 'FORCED_DRAFT':
      return 'warning';
    case 'APPROVED':
      return 'info';
    case 'PUBLISHED':
      return 'success';
    case 'REJECTED':
      return 'error';
    default:
      return 'default';
  }
}
