import { describe, expect, it } from 'vitest';
import {
  getViolationErrorMessage,
  isViolationConflict,
} from './violationMessages';

function axiosError(messageCode: string, status = 400) {
  return {
    isAxiosError: true,
    response: {
      status,
      data: { messageCode },
    },
  };
}

describe('violationMessages', () => {
  it('maps concurrency conflicts to a professional message', () => {
    expect(
      getViolationErrorMessage(
        axiosError('MODERATION_ALREADY_RESOLVED', 409),
      ),
    ).toContain('quản trị viên khác');
  });

  it('does not expose a raw backend message for unknown errors', () => {
    expect(getViolationErrorMessage(axiosError('UNKNOWN'))).toBe(
      'Không thể xử lý báo cáo vi phạm. Vui lòng thử lại.',
    );
  });

  it('identifies an HTTP 409 response', () => {
    expect(isViolationConflict(axiosError('MODERATION_ALREADY_RESOLVED', 409))).toBe(
      true,
    );
  });
});
