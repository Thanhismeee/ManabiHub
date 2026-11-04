import { describe, expect, it } from 'vitest';
import {
  createResolveViolationSchema,
  parseTargetIds,
  resolveViolationSchema,
} from './resolveViolationSchema';

describe('resolveViolationSchema', () => {
  it('accepts a documented upheld decision with an enforcement action', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'UPHELD',
      decisionNote: '  Bằng chứng bản quyền đã được xác minh.  ',
      actions: ['FORCE_DRAFT'],
      targetIdsText: '',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.decisionNote).toBe('Bằng chứng bản quyền đã được xác minh.');
    }
  });

  it('rejects an upheld decision without an action', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'UPHELD',
      decisionNote: 'Có vi phạm.',
      actions: [],
      targetIdsText: '',
    });

    expect(result.success).toBe(false);
  });

  it('rejects severe actions attached to a dismissed report', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'DISMISSED',
      decisionNote: 'Không đủ căn cứ.',
      actions: ['BAN_ACCOUNT'],
      targetIdsText: '',
    });

    expect(result.success).toBe(false);
  });

  it('requires a non-blank decision note', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'PENDING_EVIDENCE',
      decisionNote: '   ',
      actions: [],
      targetIdsText: '',
    });

    expect(result.success).toBe(false);
  });

  it('requires an explicit evidence provider for a pending-evidence decision', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'PENDING_EVIDENCE',
      decisionNote: 'Cần ảnh chụp chứng minh quyền sở hữu.',
      actions: [],
      targetIdsText: '',
    });

    expect(result.success).toBe(false);
  });

  it('accepts a pending-evidence decision addressed to both parties', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'PENDING_EVIDENCE',
      decisionNote: 'Hai bên cần bổ sung tài liệu đối chiếu.',
      evidenceRequestedFrom: 'BOTH',
      actions: [],
      targetIdsText: '',
    });

    expect(result.success).toBe(true);
  });

  it('rejects malformed removal target identifiers', () => {
    const result = resolveViolationSchema.safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn nội dung.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: 'not-a-uuid',
    });

    expect(result.success).toBe(false);
  });

  it('requires at least one removal target for a course report', () => {
    const result = createResolveViolationSchema('COURSE').safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn nội dung vi phạm.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: '   ',
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ path: ['targetIdsText'] }),
        ]),
      );
    }
  });

  it('rejects a commas-only removal target for a course report', () => {
    const result = createResolveViolationSchema('COURSE').safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn nội dung vi phạm.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: ' , , ',
    });

    expect(result.success).toBe(false);
  });

  it('accepts a valid removal target for a course report', () => {
    const result = createResolveViolationSchema('COURSE').safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn nội dung vi phạm.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: '47ab7801-4f35-4677-8c13-598f297f9911',
    });

    expect(result.success).toBe(true);
  });

  it('allows an empty removal target for a review report', () => {
    const result = createResolveViolationSchema('REVIEW').safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn review bị báo cáo.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: '',
    });

    expect(result.success).toBe(true);
  });

  it('rejects a UUID-shaped string that is not a valid UUID', () => {
    const result = createResolveViolationSchema('COURSE').safeParse({
      decision: 'UPHELD',
      decisionNote: 'Ẩn nội dung vi phạm.',
      actions: ['REMOVE_CONTENT'],
      targetIdsText: '------------------------------------',
    });

    expect(result.success).toBe(false);
  });

  it('parses a comma-separated target list without empty values', () => {
    expect(
      parseTargetIds(
        '47ab7801-4f35-4677-8c13-598f297f9911, e00ae92c-6396-48ce-b73e-6b78200161b5',
      ),
    ).toEqual([
      '47ab7801-4f35-4677-8c13-598f297f9911',
      'e00ae92c-6396-48ce-b73e-6b78200161b5',
    ]);
  });
});
