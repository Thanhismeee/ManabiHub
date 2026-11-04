import { beforeEach, describe, expect, it, vi } from 'vitest';

const axiosMocks = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock('axios', () => ({
  default: {
    create: () => ({
      post: axiosMocks.post,
    }),
    isAxiosError: (error: unknown) =>
      Boolean((error as { isAxiosError?: boolean } | null)?.isAxiosError),
  },
}));

import {
  logoutAdminSession,
  refreshAdminSessionWithStatus,
} from './adminAuthApi';
import {
  clearAuthSession,
  getAuthSession,
  hasAdminRefreshSession,
} from './authSession';
import { ROLES } from '../constants/roles';

describe('adminAuthApi', () => {
  beforeEach(() => {
    clearAuthSession('admin');
    window.localStorage.clear();
    window.sessionStorage.clear();
    axiosMocks.post.mockReset();
  });

  it('preserves refresh metadata when session restore fails transiently', async () => {
    seedRefreshMetadata();
    axiosMocks.post.mockRejectedValue({ isAxiosError: true });

    const result = await refreshAdminSessionWithStatus(true);

    expect(result.status).toBe('transient-error');
    expect(hasAdminRefreshSession()).toBe(true);
  });

  it('clears refresh metadata when the server confirms the session is invalid', async () => {
    seedRefreshMetadata();
    axiosMocks.post.mockRejectedValue({
      isAxiosError: true,
      response: { status: 401 },
    });

    const result = await refreshAdminSessionWithStatus(true);

    expect(result.status).toBe('invalid-session');
    expect(hasAdminRefreshSession()).toBe(false);
  });

  it('stores a refreshed access token only in memory', async () => {
    seedRefreshMetadata();
    const token = createToken(ROLES.SYSTEM_ADMIN);
    axiosMocks.post.mockResolvedValue({
      data: {
        data: {
          csrfToken: 'rotated-csrf',
          remembered: true,
          token,
        },
      },
    });

    const result = await refreshAdminSessionWithStatus(true);

    expect(result.status).toBe('authenticated');
    expect(getAuthSession('admin')?.token).toBe(token);
    expect(window.localStorage.getItem('admin_token')).toBeNull();
    expect(window.sessionStorage.getItem('admin_token')).toBeNull();
  });

  it('reports local-only logout when server revocation cannot be confirmed', async () => {
    seedRefreshMetadata();
    axiosMocks.post.mockRejectedValue({ isAxiosError: true });

    await expect(logoutAdminSession()).resolves.toBe(false);
    expect(hasAdminRefreshSession()).toBe(false);
  });
});

function seedRefreshMetadata() {
  window.localStorage.setItem('admin_csrf_token', 'csrf-token');
  window.localStorage.setItem('admin_refresh_expected', 'true');
  window.localStorage.setItem('admin_remembered', 'true');
}

function createToken(role: string) {
  const header = encodeBase64Url({ alg: 'none', typ: 'JWT' });
  const payload = encodeBase64Url({
    email: 'admin@example.com',
    exp: Math.floor(Date.now() / 1000) + 3600,
    role,
    sub: '00000000-0000-0000-0000-000000000001',
  });
  return `${header}.${payload}.test-signature`;
}

function encodeBase64Url(value: object) {
  return window.btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
