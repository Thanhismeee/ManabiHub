export type LearningLanguage = 'JAPANESE' | 'KOREAN' | 'CHINESE_SIMPLIFIED';
export type ProficiencyFramework = 'JLPT' | 'TOPIK' | 'HSK' | 'SOFL_INTERNAL';

export interface LearningLanguageOption {
  value: LearningLanguage;
  label: string;
  languageTag: string;
}

export interface ProficiencyOption {
  value: string;
  label: string;
}

export const LEARNING_LANGUAGES: LearningLanguageOption[] = [
  { value: 'KOREAN', label: 'Tiếng Hàn', languageTag: 'ko' },
  { value: 'CHINESE_SIMPLIFIED', label: 'Tiếng Trung giản thể', languageTag: 'zh-Hans' },
  { value: 'JAPANESE', label: 'Tiếng Nhật', languageTag: 'ja' },
];

const DEFAULT_FRAMEWORK: Record<LearningLanguage, ProficiencyFramework> = {
  JAPANESE: 'JLPT',
  KOREAN: 'TOPIK',
  CHINESE_SIMPLIFIED: 'HSK',
};

const FRAMEWORK_LABELS: Record<ProficiencyFramework, string> = {
  JLPT: 'JLPT',
  TOPIK: 'TOPIK',
  HSK: 'HSK',
  SOFL_INTERNAL: 'Lộ trình nội bộ SOFL',
};

const FRAMEWORK_LEVELS: Record<Exclude<ProficiencyFramework, 'SOFL_INTERNAL'>, ProficiencyOption[]> = {
  JLPT: ['N5', 'N4', 'N3', 'N2', 'N1'].map((value) => ({ value, label: value })),
  TOPIK: ['1', '2', '3', '4', '5', '6'].map((value) => ({ value, label: `TOPIK ${value}` })),
  HSK: ['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((value) => ({ value, label: `HSK ${value}` })),
};

export function defaultFramework(language: LearningLanguage): ProficiencyFramework {
  return DEFAULT_FRAMEWORK[language];
}

export function frameworkOptions(language: LearningLanguage) {
  return [DEFAULT_FRAMEWORK[language], 'SOFL_INTERNAL' as const].map((value) => ({
    value,
    label: FRAMEWORK_LABELS[value],
  }));
}

export function levelOptions(framework: ProficiencyFramework): ProficiencyOption[] {
  return framework === 'SOFL_INTERNAL' ? [] : FRAMEWORK_LEVELS[framework];
}

export function defaultLevel(framework: ProficiencyFramework): string {
  if (framework === 'SOFL_INTERNAL') return 'SO_CAP_1';
  return levelOptions(framework)[0]?.value ?? '';
}

export function languageLabel(language?: LearningLanguage | null): string {
  return LEARNING_LANGUAGES.find((item) => item.value === language)?.label ?? 'Ngôn ngữ';
}

export function proficiencyLabel(framework?: ProficiencyFramework | null, level?: string | null): string {
  if (!framework || !level) return 'Chưa chọn cấp độ';
  if (framework === 'SOFL_INTERNAL') return level;
  return `${framework} ${level}`.replace('JLPT N', 'JLPT N');
}
