import { describe, expect, it } from 'vitest';
import { EXPENSE_CATEGORIES } from './types';
import { EXPENSE_CATEGORY_META, EXPENSE_CATEGORY_OPTIONS } from './expenseCatalog';

describe('expense category catalog', () => {
  it('maps every backend enum to a Vietnamese label and a visible group', () => {
    expect(EXPENSE_CATEGORY_OPTIONS).toHaveLength(EXPENSE_CATEGORIES.length);
    for (const category of EXPENSE_CATEGORIES) {
      expect(EXPENSE_CATEGORY_META[category].label).not.toBe(category);
      expect(EXPENSE_CATEGORY_META[category].group.length).toBeGreaterThan(0);
    }
  });

  it('keeps OTHER_OPERATIONAL available without making it a user-facing raw label', () => {
    expect(EXPENSE_CATEGORY_META.OTHER_OPERATIONAL.label).toBe('Chi phí vận hành khác');
  });
});
