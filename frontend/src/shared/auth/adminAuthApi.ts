import axios from 'axios';
import {
  clearAuthSession,
  getAdminCsrfToken,
  getAdminSessionRevision,
  getAuthSession,
  hasAdminRefreshSession,
  storeAdminRefreshMetadata,
  storeAdminSession,
  type AdminSessionCredentials,
  type AuthSession,
} from './authSession';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api';
const refreshClient = axios.create({
  baseURL: apiBaseUrl,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
  withCredentials: true,
});

const REFRESH_LOCK_KEY = 'manabihub_admin_refresh_lock';
const REFRESH_LOCK_LEASE_MS = 20_000;
const REFRESH_LOCK_WAIT_MS = 25_000;
export type AdminRefreshResult =
  | { status: 'authenticated'; session: AuthSession }
  | { status: 'invalid-session'; session: null }
  | { status: 'transient-error'; session: null }
  | { status: 'unauthenticated'; session: null };

let refreshInFlight: Promise<AdminRefreshResult> | null = null;
let logoutInProgress = false;

export async function refreshAdminSession(force = false) {
  const result = await refreshAdminSessionWithStatus(force);
  return result.session;
}

export async function refreshAdminSessionWithStatus(
  force = false,
): Promise<AdminRefreshResult> {
  if (logoutInProgress || !hasAdminRefreshSession()) {
    return { status: 'unauthenticated', session: null };
  }

  if (refreshInFlight) {
    return refreshInFlight;
  }

  const initialToken = getAuthSession('admin')?.token ?? null;
  const refreshPromise = withCrossTabRefreshLock<AdminRefreshResult>(async () => {
    if (logoutInProgress || !hasAdminRefreshSession()) {
      return { status: 'unauthenticated', session: null };
    }

    const currentSession = getAuthSession('admin');
    if (
      currentSession
      && (
        currentSession.token !== initialToken
        || (!force && currentSession.expiresAt - Date.now() > 60_000)
      )
    ) {
      return {
        status: 'authenticated',
        session: currentSession,
      };
    }

    const csrfToken = getAdminCsrfToken();
    if (!csrfToken) {
      clearAuthSession('admin');
      return { status: 'invalid-session', session: null };
    }

    const requestRevision = getAdminSessionRevision();
    try {
      const response = await refreshClient.post(
        '/admin/auth/refresh',
        {},
        { headers: { 'X-Admin-CSRF': csrfToken } },
      );
      const credentials = response.data?.data as AdminSessionCredentials | undefined;
      if (!credentials?.token || !credentials.csrfToken) {
        return { status: 'transient-error', session: null };
      }
      if (getAdminSessionRevision() !== requestRevision || !hasAdminRefreshSession()) {
        const current = getAuthSession('admin');
        return current
          ? { status: 'authenticated', session: current }
          : { status: 'unauthenticated', session: null };
      }
      if (logoutInProgress) {
        storeAdminRefreshMetadata(credentials);
        return { status: 'unauthenticated', session: null };
      }
      const session = storeAdminSession(credentials);
      return session
        ? { status: 'authenticated', session }
        : { status: 'transient-error', session: null };
    } catch (error: unknown) {
      if (
        isConfirmedInvalidSession(error)
        && getAdminSessionRevision() === requestRevision
      ) {
        clearAuthSession('admin');
        return { status: 'invalid-session', session: null };
      }
      return { status: 'transient-error', session: null };
    }
  })
    .catch((): AdminRefreshResult => ({ status: 'transient-error', session: null }))
    .finally(() => {
      refreshInFlight = null;
    });
  refreshInFlight = refreshPromise;

  return refreshPromise;
}

export async function logoutAdminSession() {
  let serverSessionRevoked = !hasAdminRefreshSession();
  logoutInProgress = true;
  try {
    await withCrossTabRefreshLock(async () => {
      const csrfToken = getAdminCsrfToken();
      if (!csrfToken) {
        return;
      }
      await refreshClient.post(
        '/admin/auth/logout',
        {},
        { headers: { 'X-Admin-CSRF': csrfToken } },
      );
      serverSessionRevoked = true;
    });
  } catch {
    serverSessionRevoked = false;
  } finally {
    clearAuthSession('admin');
    logoutInProgress = false;
  }
  return serverSessionRevoked;
}

async function withCrossTabRefreshLock<T>(operation: () => Promise<T>): Promise<T> {
  const lockManager = (
    navigator as Navigator & {
      locks?: {
        request: <R>(name: string, callback: () => Promise<R>) => Promise<R>;
      };
    }
  ).locks;

  return lockManager
    ? lockManager.request('manabihub-admin-refresh', operation)
    : withStorageLease(operation);
}

async function withStorageLease<T>(operation: () => Promise<T>): Promise<T> {
  const owner = createLockOwner();
  const deadline = Date.now() + REFRESH_LOCK_WAIT_MS;

  while (Date.now() < deadline) {
    const now = Date.now();
    const existing = readStorageLease();
    if (!existing || existing.expiresAt <= now) {
      window.localStorage.setItem(REFRESH_LOCK_KEY, JSON.stringify({
        owner,
        expiresAt: now + REFRESH_LOCK_LEASE_MS,
      }));
      await delay(30);
      if (readStorageLease()?.owner === owner) {
        try {
          return await operation();
        } finally {
          if (readStorageLease()?.owner === owner) {
            window.localStorage.removeItem(REFRESH_LOCK_KEY);
          }
        }
      }
    }
    await delay(80 + Math.floor(Math.random() * 40));
  }

  throw new Error('Timed out waiting for the admin refresh lock');
}

function readStorageLease(): { owner: string; expiresAt: number } | null {
  try {
    const parsed = JSON.parse(
      window.localStorage.getItem(REFRESH_LOCK_KEY) ?? 'null',
    ) as { owner?: unknown; expiresAt?: unknown } | null;
    return parsed
      && typeof parsed.owner === 'string'
      && typeof parsed.expiresAt === 'number'
      ? { owner: parsed.owner, expiresAt: parsed.expiresAt }
      : null;
  } catch {
    return null;
  }
}

function createLockOwner() {
  return typeof window.crypto?.randomUUID === 'function'
    ? window.crypto.randomUUID()
    : `${Date.now()}-${Math.random()}`;
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function isConfirmedInvalidSession(error: unknown) {
  if (!axios.isAxiosError(error) || !error.response) {
    return false;
  }
  return [400, 401, 403].includes(error.response.status);
}
