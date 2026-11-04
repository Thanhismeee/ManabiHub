import { z } from 'zod';

const uuidSchema = z.string().uuid();

const resolveViolationFormSchema = z
  .object({
    decision: z.enum([
      'UPHELD',
      'DISMISSED',
      'PENDING_EVIDENCE',
      'CORRECTION_REQUIRED',
    ]),
    decisionNote: z.string().trim().min(1, 'Vui lòng nhập ghi chú quyết định.').max(
      2000,
      'Ghi chú không được vượt quá 2.000 ký tự.',
    ),
    actions: z.array(
      z.enum([
        'FORCE_DRAFT',
        'REMOVE_CONTENT',
        'HIDE_COURSE',
        'BAN_ACCOUNT',
        'FREEZE_BALANCE',
      ]),
    ),
    evidenceRequestedFrom: z
      .enum(['REPORTER', 'CREATOR', 'BOTH'])
      .optional(),
    targetIdsText: z.string().trim(),
  });

export function createResolveViolationSchema(targetType?: string) {
  return resolveViolationFormSchema.superRefine((value, context) => {
    if (value.decision === 'UPHELD' && value.actions.length === 0) {
      context.addIssue({
        code: 'custom',
        path: ['actions'],
        message: 'Chọn ít nhất một biện pháp xử lý.',
      });
    }
    if (value.decision !== 'UPHELD' && value.actions.length > 0) {
      context.addIssue({
        code: 'custom',
        path: ['actions'],
        message: 'Chỉ quyết định xác nhận vi phạm mới được kèm biện pháp xử lý.',
      });
    }
    if (
      value.decision === 'PENDING_EVIDENCE' &&
      !value.evidenceRequestedFrom
    ) {
      context.addIssue({
        code: 'custom',
        path: ['evidenceRequestedFrom'],
        message: 'Chọn người cần bổ sung bằng chứng.',
      });
    }
    const removalTargetIds = parseTargetIds(value.targetIdsText);
    if (
      value.actions.includes('REMOVE_CONTENT') &&
      targetType?.toUpperCase() === 'COURSE' &&
      !removalTargetIds?.length
    ) {
      context.addIssue({
        code: 'custom',
        path: ['targetIdsText'],
        message: 'Nhập ít nhất một ID lesson/review cần ẩn.',
      });
    }
    if (
      value.actions.includes('REMOVE_CONTENT') &&
      removalTargetIds?.some((targetId) => !uuidSchema.safeParse(targetId).success)
    ) {
      context.addIssue({
        code: 'custom',
        path: ['targetIdsText'],
        message: 'Danh sách ID nội dung không hợp lệ.',
      });
    }
  });
}

export const resolveViolationSchema = createResolveViolationSchema();

export type ResolveViolationFormValues = z.infer<typeof resolveViolationFormSchema>;

export function parseTargetIds(value: string) {
  if (!value.trim()) {
    return undefined;
  }
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}
