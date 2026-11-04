import axios from 'axios';
import {
  clearAuthSession,
  getAuthSession,
  getLoginRoute,
  hasAdminRefreshSession,
  rememberPostLoginRoute,
  type AuthSessionKind,
} from '../auth/authSession';
import { refreshAdminSession } from '../auth/adminAuthApi';

export const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

axiosClient.interceptors.request.use(async (config) => {
  if (isPublicAdminAuthRequest(config.url)) {
    return config;
  }

  const kind = resolveSessionKind(config.url);
  let session = getAuthSession(kind);
  if (!session && kind === 'admin' && hasAdminRefreshSession()) {
    session = await refreshAdminSession();
  }
  if (session) {
    config.headers.Authorization = `Bearer ${session.token}`;
  }
  if (config.data instanceof FormData) {
    config.headers['Content-Type'] = 'multipart/form-data';
  }

  return config;
});

let redirectingToLogin = false;

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url as string | undefined;
    const requestConfig = error.config as (typeof error.config & {
      adminRefreshAttempted?: boolean;
    }) | undefined;

    if (
      status === 401
      && !isPublicAdminAuthRequest(requestUrl)
      && !isPublicApiRequest(requestUrl)
    ) {
      const kind = resolveSessionKind(requestUrl);
      if (
        kind === 'admin'
        && requestConfig
        && !requestConfig.adminRefreshAttempted
        && hasAdminRefreshSession()
      ) {
        requestConfig.adminRefreshAttempted = true;
        const refreshedSession = await refreshAdminSession(true);
        if (refreshedSession) {
          requestConfig.headers.Authorization = `Bearer ${refreshedSession.token}`;
          return axiosClient.request(requestConfig);
        }
        if (hasAdminRefreshSession()) {
          return Promise.reject(error);
        }
      }

      if (getAuthSession(kind) || isProtectedScreen(kind)) {
        clearAuthSession(kind);
        const code = error.response?.data?.code;
        if (code === 'AUTH_SESSION_REVOKED') {
          redirectToLogin(kind, 'session-revoked');
        } else {
          redirectToLogin(kind, 'session-expired');
        }
      }
    }

    if (status === 409 && error.response?.data?.code === 'ACCOUNT_IN_USE_ELSEWHERE') {
      const kind = resolveSessionKind(requestUrl);
      clearAuthSession(kind);
      redirectToLogin(kind, 'account-in-use');
    }

    return Promise.reject(error);
  },
);

function resolveSessionKind(requestUrl?: string): AuthSessionKind {
  const path = requestUrl ?? '';
  const isAdminEndpoint = path.startsWith('/admin/') || path.startsWith('/v1/admin/');
  const isAdminScreen = typeof window !== 'undefined' && window.location.pathname.startsWith('/admin');

  return isAdminEndpoint || isAdminScreen ? 'admin' : 'public';
}

function isPublicAdminAuthRequest(requestUrl?: string) {
  return requestUrl?.includes('/admin/auth/login')
    || requestUrl?.includes('/admin/auth/setup-password')
    || requestUrl?.includes('/admin/auth/refresh')
    || requestUrl?.includes('/admin/auth/logout')
    || requestUrl?.includes('/admin/auth/password/forgot')
    || requestUrl?.includes('/admin/auth/password/reset')
    || false;
}

function isPublicApiRequest(requestUrl?: string) {
  const path = requestUrl ?? '';
  return path.includes('/v1/public/')
    || path.includes('/v1/course-categories');
}

function isProtectedScreen(kind: AuthSessionKind) {
  if (typeof window === 'undefined') {
    return false;
  }

  const path = window.location.pathname;
  return kind === 'admin'
    ? path.startsWith('/admin')
      && path !== '/admin/login'
      && path !== '/admin/setup-password'
      && path !== '/admin/forgot-password'
      && path !== '/admin/reset-password'
    : path.startsWith('/student') || path.startsWith('/teacher');
}

function redirectToLogin(kind: AuthSessionKind, reason: string = 'session-expired') {
  if (typeof window === 'undefined' || redirectingToLogin) {
    return;
  }

  const loginRoute = getLoginRoute(kind);
  if (window.location.pathname === loginRoute) {
    return;
  }

  redirectingToLogin = true;
  rememberPostLoginRoute(kind, `${window.location.pathname}${window.location.search}`);
  window.location.assign(`${loginRoute}?reason=${reason}`);
}
