import axios from 'axios';
import type { ApiResponse } from '../../shared/types/api';

const FALLBACK_MESSAGE = 'Không thể tải dữ liệu doanh thu. Vui lòng thử lại.';

export function getRevenueLoadErrorMessage(error: unknown) {
  if (!axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return FALLBACK_MESSAGE;
  }

  if (!error.response) {
    return 'Không thể kết nối backend. Vui lòng kiểm tra dịch vụ đang chạy.';
  }

  switch (error.response.status) {
    case 401:
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    case 403:
      return 'Tài khoản không có quyền xem doanh thu hệ thống.';
    default:
      if (error.response.status >= 500) {
        return 'Máy chủ gặp lỗi khi tổng hợp dữ liệu doanh thu. Vui lòng thử lại hoặc kiểm tra nhật ký backend.';
      }
      return error.response.data?.message || FALLBACK_MESSAGE;
  }
}
