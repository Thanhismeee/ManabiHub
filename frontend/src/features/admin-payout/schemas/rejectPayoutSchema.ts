import { z } from 'zod';

export const rejectPayoutSchema = z.object({
  reason: z.string()
    .trim()
    .min(1, 'Vui lòng nhập lý do từ chối.')
    .max(500, 'Lý do không được vượt quá 500 ký tự.'),
});

export type RejectPayoutFormValues = z.infer<typeof rejectPayoutSchema>;
