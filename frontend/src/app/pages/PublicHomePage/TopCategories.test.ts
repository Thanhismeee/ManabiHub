import { describe, expect, it } from 'vitest';
import { SKILL_CATEGORIES } from './courseCategoryLinks';

describe('TopCategories links', () => {
  it('uses category codes that exist in the course category catalog', () => {
    expect(SKILL_CATEGORIES.map((category) => category.id)).toEqual([
      'category=VOCABULARY',
      'category=GRAMMAR',
      'category=KANJI',
      'category=SPEAKING',
      'category=JLPT_PRACTICE',
      'category=LISTENING',
    ]);
  });
});
