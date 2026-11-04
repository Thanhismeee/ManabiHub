import type { AuthSession } from '../auth/authSession';
import { ROLES } from '../constants/roles';

export function getHeaderBrand(session?: Pick<AuthSession, 'kind' | 'roles'>) {
  if (session?.kind === 'admin') return 'ManabiAdmin';
  if (session?.roles.includes(ROLES.TEACHER)) return 'ManabiTeacher';
  return 'ManabiHub';
}
