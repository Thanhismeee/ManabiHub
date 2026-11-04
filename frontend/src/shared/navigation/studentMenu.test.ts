import { describe, expect, it } from 'vitest';
import { ROUTES } from '../constants/routes';
import { STUDENT_MENU } from './studentMenu';

describe('STUDENT_MENU', () => {
  it('keeps payment history as the single student money destination', () => {
    expect(STUDENT_MENU.some((item) => item.path === ROUTES.STUDENT.WALLET)).toBe(false);
    expect(STUDENT_MENU).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: ROUTES.STUDENT.PAYMENTS, title: 'Ví & Thanh toán' }),
    ]));
  });
});
