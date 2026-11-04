import { createFilterOptions } from '@mui/material/Autocomplete';
import { ROLES } from '../../../shared/constants/roles';

export const AUDIT_ROLE_LABELS: Record<string, string> = {
  [ROLES.STUDENT]: 'Học viên',
  [ROLES.TEACHER]: 'Giáo viên',
  [ROLES.SYSTEM_ADMIN]: 'Quản trị hệ thống',
  [ROLES.COURSE_MANAGER]: 'Quản lý khóa học',
  [ROLES.FINANCE_MANAGER]: 'Quản lý tài chính',
};

export const ACTIVE_AUDIT_ROLE_CODES = Object.values(ROLES);

export const filterAuditRoleOptions = createFilterOptions<string>({
  stringify: (roleCode) => `${roleCode} ${AUDIT_ROLE_LABELS[roleCode] ?? ''}`,
  trim: true,
});

function normalizeSearchText(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/gi, 'd')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

export function normalizeAuditRoleFilter(value?: string) {
  if (!value?.trim()) return undefined;

  const normalizedInput = normalizeSearchText(value);
  if (!normalizedInput) return undefined;
  const matchingRoles = ACTIVE_AUDIT_ROLE_CODES.filter((roleCode) => {
    const normalizedLabel = normalizeSearchText(AUDIT_ROLE_LABELS[roleCode] ?? '');
    return roleCode === normalizedInput
      || roleCode.includes(normalizedInput)
      || normalizedLabel.includes(normalizedInput);
  });

  return matchingRoles.length === 1 ? matchingRoles[0] : normalizedInput;
}
