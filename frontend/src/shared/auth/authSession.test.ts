import { beforeEach, describe, expect, it } from 'vitest';
import { ROLES } from '../constants/roles';
import {
  canAccessPath,
  clearAuthSession,
  getAuthSession,
  hasAdminRefreshSession,
  storeAdminSession,
  storeAuthToken,
  synchronizeAdminAccessToken,
} from './authSession';

describe('authSession', () => {
  beforeEach(() => {
    clearAuthSession('admin');
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('keeps public and internal-admin tokens in separate storage boundaries', () => {
    const publicToken = createToken(ROLES.STUDENT);
    const adminToken = createToken(ROLES.SYSTEM_ADMIN);

    expect(storeAuthToken('public', publicToken)?.kind).toBe('public');
    expect(storeAdminSession({
      token: adminToken,
      csrfToken: 'csrf-token',
      remembered: true,
    })?.kind).toBe('admin');

    expect(window.localStorage.getItem('auth_token')).toBe(publicToken);
    expect(window.localStorage.getItem('admin_token')).toBeNull();
    expect(window.sessionStorage.getItem('admin_token')).toBeNull();
    expect(getAuthSession('admin')?.token).toBe(adminToken);
    expect(hasAdminRefreshSession()).toBe(true);
  });

  it('clears only the requested session and all admin refresh metadata', () => {
    const publicToken = createToken(ROLES.STUDENT);
    storeAuthToken('public', publicToken);
    storeAdminSession({
      token: createToken(ROLES.COURSE_MANAGER),
      csrfToken: 'csrf-token',
      remembered: false,
    });

    clearAuthSession('admin');

    expect(getAuthSession('public')?.token).toBe(publicToken);
    expect(getAuthSession('admin')).toBeNull();
    expect(hasAdminRefreshSession()).toBe(false);
    expect(window.localStorage.getItem('admin_csrf_token')).toBeNull();
  });

  it('clears current and legacy public session state without touching admin refresh state', () => {
    storeAuthToken('public', createToken(ROLES.STUDENT));
    window.localStorage.setItem('auth_session', 'legacy-token');
    window.sessionStorage.setItem('auth_return_to', '/student/dashboard');
    storeAdminSession({
      token: createToken(ROLES.SYSTEM_ADMIN),
      csrfToken: 'csrf-token',
      remembered: true,
    });

    clearAuthSession('public');

    expect(getAuthSession('public')).toBeNull();
    expect(window.localStorage.getItem('auth_token')).toBeNull();
    expect(window.localStorage.getItem('auth_session')).toBeNull();
    expect(window.sessionStorage.getItem('auth_return_to')).toBeNull();
    expect(getAuthSession('admin')).not.toBeNull();
    expect(hasAdminRefreshSession()).toBe(true);
  });

  it('rejects unsafe and cross-portal return paths', () => {
    const student = getSession('public', ROLES.STUDENT);
    const admin = getSession('admin', ROLES.FINANCE_MANAGER);

    expect(canAccessPath(student, '/student/dashboard')).toBe(true);
    expect(canAccessPath(student, '/admin/dashboard')).toBe(false);
    expect(canAccessPath(admin, '/admin/dashboard')).toBe(true);
    expect(canAccessPath(admin, '/student/dashboard')).toBe(false);
    expect(canAccessPath(admin, '//example.com')).toBe(false);
  });

  it('accepts a refreshed token from another tab without persisting it', () => {
    const firstToken = createToken(ROLES.SYSTEM_ADMIN, 3600);
    const refreshedToken = createToken(ROLES.SYSTEM_ADMIN, 7200);
    storeAdminSession({
      token: firstToken,
      csrfToken: 'csrf-token',
      remembered: true,
    }, false);

    expect(synchronizeAdminAccessToken(refreshedToken)?.token).toBe(refreshedToken);
    expect(getAuthSession('admin')?.token).toBe(refreshedToken);
    expect(window.localStorage.getItem('admin_token')).toBeNull();
    expect(window.sessionStorage.getItem('admin_token')).toBeNull();
  });
});

function getSession(kind: 'public' | 'admin', role: string) {
  const token = createToken(role);
  const session = kind === 'public'
    ? storeAuthToken(kind, token)
    : storeAdminSession({
      token,
      csrfToken: 'csrf-token',
      remembered: false,
    });

  if (!session) {
    throw new Error('Test token did not create a session');
  }
  return session;
}

function createToken(role: string, expiresInSeconds = 3600) {
  const header = encodeBase64Url({ alg: 'none', typ: 'JWT' });
  const payload = encodeBase64Url({
    sub: '00000000-0000-0000-0000-000000000001',
    email: 'test@example.com',
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
    role,
  });
  return `${header}.${payload}.test-signature`;
}

function encodeBase64Url(value: object) {
  return window.btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
