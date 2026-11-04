import { describe, expect, it } from 'vitest';
import { getRevenueLoadErrorMessage } from './financeRevenueError';

function axiosError(status?: number, message?: string) {
  return {
    isAxiosError: true,
    response: status === undefined ? undefined : {
      status,
      data: { message },
    },
  };
}

describe('getRevenueLoadErrorMessage', () => {
  it('distinguishes permission errors from backend failures', () => {
    expect(getRevenueLoadErrorMessage(axiosError(403))).toContain('không có quyền');
    expect(getRevenueLoadErrorMessage(axiosError(500))).toContain('Máy chủ gặp lỗi');
  });

  it('reports a connection failure when no response is available', () => {
    expect(getRevenueLoadErrorMessage(axiosError())).toContain('Không thể kết nối backend');
  });

  it('uses a safe fallback for non-Axios errors', () => {
    expect(getRevenueLoadErrorMessage(new Error('unexpected'))).toBe(
      'Không thể tải dữ liệu doanh thu. Vui lòng thử lại.',
    );
  });
});
