import { matchRoutes, Navigate } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ROUTES } from '../shared/constants/routes';
import { router } from './router';

function expectConcreteRoute(url: string, expectedRoutePath: string) {
  const matches = matchRoutes(router.routes, url);

  expect(matches, `No route matched ${url}`).not.toBeNull();
  expect(matches?.some(({ route }) => route.path === expectedRoutePath)).toBe(true);
  expect(matches?.at(-1)?.route.path).not.toBe('*');
}

describe('router contract for policy and notification links', () => {
  it.each([
    [ROUTES.PUBLIC.HELP, '/help'],
    [ROUTES.PUBLIC.TERMS, 'terms'],
    [ROUTES.PUBLIC.PRIVACY, 'privacy'],
    [ROUTES.PUBLIC.INSTRUCTOR_TERMS, 'instructor-terms'],
    [ROUTES.PUBLIC.REFUND_POLICY, 'refund-policy'],
    [ROUTES.PUBLIC.AI_NOTICE, 'ai-notice'],
    [ROUTES.PUBLIC.INSTRUCTOR_REVENUE_SHARE, 'instructors/revenue-share'],
    [ROUTES.PUBLIC.INSTRUCTOR_ESCROW_PAYOUTS, 'instructors/escrow-and-payouts'],
    [ROUTES.TEACHER.COURSES, 'courses'],
    ['/admin/violations/report-1', 'violations/:id'],
    [ROUTES.ADMIN.FINANCE_REVENUE, 'finance/revenue'],
    [ROUTES.ADMIN.FINANCE_EXPENSES, 'finance/expenses'],
    [`${ROUTES.ADMIN.FINANCE_EXPENSES}/expense-1`, 'finance/expenses/:id'],
    [ROUTES.ADMIN.DECISION_REVIEWS, 'decision-reviews'],
  ])('matches %s to a concrete route', (url, expectedRoutePath) => {
    expectConcreteRoute(url, expectedRoutePath);
  });

  it('redirects the legacy wallet URL to payment history', () => {
    const matches = matchRoutes(router.routes, ROUTES.STUDENT.WALLET);
    const walletElement = matches?.at(-1)?.route.element;

    expect(matches).not.toBeNull();
    expect(walletElement).toMatchObject({ type: Navigate, props: { to: ROUTES.STUDENT.PAYMENTS, replace: true } });
  });
});
