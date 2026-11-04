import { describe, expect, it } from 'vitest';
import {
  filterHelpArticles,
  HELP_ARTICLES,
  HELP_CATEGORIES,
} from './articleRegistry';

const requiredPaths = [
  '/help/instructors/verification',
  '/help/instructors/revenue-share',
  '/help/instructors/escrow-and-payouts',
  '/help/instructors/course-review-and-unpublishing',
  '/help/learners/payments-refunds-access',
  '/help/trust-safety/reporting-and-actions',
  '/help/ai-and-data',
  '/legal/terms',
  '/legal/privacy',
  '/legal/instructor-terms',
  '/legal/refund-policy',
  '/legal/ai-notice',
];

describe('help article registry', () => {
  it('contains every required route exactly once', () => {
    const paths = HELP_ARTICLES.map((article) => article.path);

    expect(new Set(paths).size).toBe(paths.length);
    expect(new Set(HELP_ARTICLES.map((article) => article.id)).size).toBe(
      HELP_ARTICLES.length,
    );
    expect(paths).toEqual(expect.arrayContaining(requiredPaths));
  });

  it('contains no broken category or related-article references', () => {
    const categories = new Set(HELP_CATEGORIES.map((category) => category.id));
    const paths = new Set(HELP_ARTICLES.map((article) => article.path));

    for (const article of HELP_ARTICLES) {
      expect(categories.has(article.category)).toBe(true);
      for (const relatedPath of article.relatedPaths) {
        expect(paths.has(relatedPath)).toBe(true);
        expect(relatedPath).not.toBe(article.path);
      }
    }
  });

  it('searches every discoverable help and legal article', () => {
    expect(filterHelpArticles('hoàn tiền').map((article) => article.id)).toContain(
      'learner-payments-refunds',
    );
    expect(filterHelpArticles('', 'legal').map((article) => article.id)).toEqual([
      'terms',
      'privacy',
      'instructor-terms',
      'refund-policy',
      'ai-notice',
    ]);
    expect(filterHelpArticles('').every((article) => article.discoverable)).toBe(true);
  });
});
