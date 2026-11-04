import { describe, expect, it } from 'vitest';
import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';
import { ADMIN_MENU } from './adminMenu';

describe('ADMIN_MENU route permissions', () => {
  it.each([
    ROUTES.ADMIN.KYC_REVIEW,
    ROUTES.ADMIN.COURSE_APPROVAL,
    ROUTES.ADMIN.VIOLATIONS,
  ])('allows only Course Manager to open %s', (path) => {
    const item = ADMIN_MENU.find((candidate) => candidate.path === path);

    expect(item?.roles).toEqual([ROLES.COURSE_MANAGER]);
  });

  it.each([
    ROUTES.ADMIN.REFUND_REVIEW,
    ROUTES.ADMIN.PAYOUTS,
    ROUTES.ADMIN.FINANCE_REVENUE,
    ROUTES.ADMIN.FINANCE_EXPENSES,
  ])('allows only Finance Manager to open %s', (path) => {
    const item = ADMIN_MENU.find((candidate) => candidate.path === path);

    expect(item?.roles).toEqual([ROLES.FINANCE_MANAGER]);
  });

  it('exposes operational review to System Admin without operational decision routes', () => {
    expect(ADMIN_MENU.find((item) => item.path === ROUTES.ADMIN.DECISION_REVIEWS)?.roles)
      .toEqual([ROLES.SYSTEM_ADMIN]);
    expect(ADMIN_MENU.filter((item) => item.roles.includes(ROLES.SYSTEM_ADMIN)).map((item) => item.path))
      .not.toEqual(expect.arrayContaining([
        ROUTES.ADMIN.KYC_REVIEW,
        ROUTES.ADMIN.COURSE_APPROVAL,
        ROUTES.ADMIN.VIOLATIONS,
        ROUTES.ADMIN.REFUND_REVIEW,
        ROUTES.ADMIN.PAYOUTS,
      ]));
  });

  it('exposes the read-only operations dashboard only to System Admin', () => {
    const item = ADMIN_MENU.find((candidate) => candidate.path === ROUTES.ADMIN.OPERATIONS);

    expect(item).toEqual(expect.objectContaining({
      title: 'Vận hành hệ thống',
      path: '/admin/operations',
      roles: [ROLES.SYSTEM_ADMIN],
    }));
  });
});
