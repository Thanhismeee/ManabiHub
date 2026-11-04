import { z } from 'zod';

export const withdrawalSchema = z.object({
  amount: z.coerce.number().min(1, 'Số tiền phải lớn hơn 0'),
  useNewAccount: z.boolean().default(true),
  bankAccountId: z.string().optional(),
  bankCode: z.string().optional(),
  bankName: z.string().optional(),
  accountNumber: z.string().optional(),
  accountHolderName: z.string().optional(),
  branch: z.string().optional(),
  bankQrDataUrl: z.string().optional(),
}).superRefine((data, ctx) => {
  if (data.useNewAccount) {
    if (!data.bankCode) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Vui lòng chọn ngân hàng',
        path: ['bankCode'],
      });
    }
    if (!data.accountNumber) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Vui lòng nhập số tài khoản',
        path: ['accountNumber'],
      });
    }
    if (!data.accountHolderName) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Vui lòng nhập tên chủ tài khoản',
        path: ['accountHolderName'],
      });
    }
  } else {
    if (!data.bankAccountId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Vui lòng chọn tài khoản đã lưu',
        path: ['bankAccountId'],
      });
    }
  }
});

export type WithdrawalFormValues = z.infer<typeof withdrawalSchema>;
