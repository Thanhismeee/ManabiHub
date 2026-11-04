import { describe, expect, it } from 'vitest';
import {
  formatSubmittedTime,
  getCourseApprovalStatusLabel,
  localizePolicyEvidence,
} from './courseApprovalLocalization';

describe('course approval localization', () => {
  it('uses Vietnamese status and relative-time labels', () => {
    expect(getCourseApprovalStatusLabel('PENDING')).toBe('Chờ phê duyệt');
    expect(
      formatSubmittedTime('2026-08-01T09:00:00Z', new Date('2026-08-01T11:00:00Z')),
    ).toBe('2 giờ trước');
  });

  it('localizes the copyright agreement evidence emitted by the backend', () => {
    expect(
      localizePolicyEvidence(
        'Digital Copyright Liability Agreement accepted upon course submission at 2026-08-01T14:00:57Z',
      ),
    ).toMatch(/^Giảng viên đã chấp nhận cam kết trách nhiệm bản quyền số/);
  });
});
