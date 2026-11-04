import { ROLES } from '../constants/roles';
import { ROUTES } from '../constants/routes';
import { clearPrivateDrafts } from '../storage/privateDrafts';

export type AuthSessionKind = 'public' | 'admin';

interface JwtPayload {
  sub?: string;
  email?: string;
  exp?: number;
  role?: unknown;
}

export interface AuthSession {
  kind: AuthSessionKind;
  token: string;
  subject: string | null;
  email: string | null;
  roles: string[];
  expiresAt: number;
}

const TOKEN_KEYS: Record<AuthSessionKind, string> = {
  public: 'auth_token',
  admin: 'admin_token',
};

const ADMIN_CSRF_KEY = 'admin_csrf_token';
const ADMIN_REFRESH_EXPECTED_KEY = 'admin_refresh_expected';
const ADMIN_REMEMBERED_KEY = 'admin_remembered';
const LEGACY_PUBLIC_TOKEN_KEYS = ['auth_session'];
const AUTH_SESSION_CHANGED_EVENT = 'manabihub:auth-session-changed';
const ADMIN_SESSION_CHANNEL = 'manabihub-admin-session';
let adminAccessToken: string | null = null;
let adminSessionRevision = 0;

const RETURN_TO_KEYS: Record<AuthSessionKind, string> = {
  public: 'auth_return_to',
  admin: 'admin_return_to',
};

const INTERNAL_ROLES = [
  ROLES.SYSTEM_ADMIN,
  ROLES.COURSE_MANAGER,
  ROLES.FINANCE_MANAGER,
];

export function parseAuthToken(token: string, kind: AuthSessionKind): AuthSession | null {
  const payload = decodeJwtPayload(token);
  if (!payload || typeof payload.exp !== 'number' || payload.exp * 1000 <= Date.now()) {
    return null;
  }

  const roles = normalizeRoles(payload.role);
  if (roles.length === 0) {
    return null;
  }

  return {
    kind,
    token,
    subject: typeof payload.sub === 'string' ? payload.sub : null,
    email: typeof payload.email === 'string' ? payload.email : null,
    roles,
    expiresAt: payload.exp * 1000,
  };
}

export function getAuthSession(kind: AuthSessionKind): AuthSession | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const token = kind === 'admin'
    ? adminAccessToken
    : window.localStorage.getItem(TOKEN_KEYS.public);
  if (!token) {
    return null;
  }

  const session = parseAuthToken(token, kind);
  if (!session) {
    if (kind === 'admin') {
      adminAccessToken = null;
    } else {
      const previousOwner = decodeJwtPayload(token)?.sub;
      if (typeof previousOwner === 'string') void clearPrivateDrafts(previousOwner).catch(() => undefined);
      window.localStorage.removeItem(TOKEN_KEYS.public);
    }
  }

  return session;
}

export function storeAuthToken(kind: AuthSessionKind, token: string): AuthSession | null {
  const session = parseAuthToken(token, kind);
  if (!session || typeof window === 'undefined') {
    return null;
  }

  if (kind === 'admin') {
    adminAccessToken = token;
    window.sessionStorage.removeItem(TOKEN_KEYS.admin);
  } else {
    const previous = getAuthSession('public');
    if (previous?.subject && previous.subject !== session.subject) void clearPrivateDrafts(previous.subject).catch(() => undefined);
    window.localStorage.setItem(TOKEN_KEYS.public, token);
  }
  notifySessionChanged(kind);
  return session;
}

export function clearAuthSession(kind: AuthSessionKind) {
  if (typeof window !== 'undefined') {
    if (kind === 'admin') {
      adminAccessToken = null;
      adminSessionRevision += 1;
      window.sessionStorage.removeItem(TOKEN_KEYS.admin);
      window.localStorage.removeItem(ADMIN_CSRF_KEY);
      window.localStorage.removeItem(ADMIN_REFRESH_EXPECTED_KEY);
      window.localStorage.removeItem(ADMIN_REMEMBERED_KEY);
      broadcastAdminSession({ type: 'SIGNED_OUT' });
    } else {
      const previous = getAuthSession('public');
      if (previous?.subject) void clearPrivateDrafts(previous.subject).catch(() => undefined);
      window.localStorage.removeItem(TOKEN_KEYS.public);
      LEGACY_PUBLIC_TOKEN_KEYS.forEach((key) => window.localStorage.removeItem(key));
      window.sessionStorage.removeItem(RETURN_TO_KEYS.public);
    }
    notifySessionChanged(kind);
  }
}

export interface AdminSessionCredentials {
  token: string;
  csrfToken: string;
  remembered: boolean;
}

export function storeAdminSession(
  credentials: AdminSessionCredentials,
  broadcast = true,
): AuthSession | null {
  if (typeof window === 'undefined' || !credentials.csrfToken) {
    return null;
  }

  const session = parseAuthToken(credentials.token, 'admin');
  if (!session) {
    return null;
  }

  adminAccessToken = credentials.token;
  adminSessionRevision += 1;
  window.sessionStorage.removeItem(TOKEN_KEYS.admin);
  storeAdminRefreshMetadata(credentials);
  notifySessionChanged('admin');

  if (broadcast) {
    broadcastAdminSession({
      type: 'SESSION_UPDATED',
      token: credentials.token,
    });
  }
  return session;
}

export function synchronizeAdminAccessToken(token: string) {
  if (typeof window === 'undefined') {
    return null;
  }

  const session = parseAuthToken(token, 'admin');
  if (!session) {
    return null;
  }

  adminAccessToken = token;
  adminSessionRevision += 1;
  window.sessionStorage.removeItem(TOKEN_KEYS.admin);
  notifySessionChanged('admin');
  return session;
}

export function storeAdminRefreshMetadata(
  credentials: Pick<AdminSessionCredentials, 'csrfToken' | 'remembered'>,
) {
  if (typeof window === 'undefined' || !credentials.csrfToken) {
    return;
  }
  window.localStorage.setItem(ADMIN_CSRF_KEY, credentials.csrfToken);
  window.localStorage.setItem(ADMIN_REFRESH_EXPECTED_KEY, 'true');
  window.localStorage.setItem(ADMIN_REMEMBERED_KEY, String(credentials.remembered));
}

export function getAdminSessionRevision() {
  return adminSessionRevision;
}

export function hasAdminRefreshSession() {
  return typeof window !== 'undefined'
    && window.localStorage.getItem(ADMIN_REFRESH_EXPECTED_KEY) === 'true'
    && Boolean(window.localStorage.getItem(ADMIN_CSRF_KEY));
}

export function getAdminCsrfToken() {
  return typeof window === 'undefined'
    ? null
    : window.localStorage.getItem(ADMIN_CSRF_KEY);
}

export function subscribeToAuthSessionChanges(listener: () => void) {
  if (typeof window === 'undefined') {
    return () => undefined;
  }

  const handleStorage = (event: StorageEvent) => {
    if (event.key === TOKEN_KEYS.public || LEGACY_PUBLIC_TOKEN_KEYS.includes(event.key ?? '')) {
      listener();
    }
  };

  window.addEventListener(AUTH_SESSION_CHANGED_EVENT, listener);
  window.addEventListener('storage', handleStorage);
  return () => {
    window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, listener);
    window.removeEventListener('storage', handleStorage);
  };
}

export function hasAnyRole(session: AuthSession, allowedRoles: readonly string[]) {
  return allowedRoles.some((role) => session.roles.includes(role));
}

export function getDefaultRoute(session: AuthSession) {
  if (session.kind === 'admin') {
    return hasAnyRole(session, INTERNAL_ROLES) ? ROUTES.ADMIN.DASHBOARD : '/admin/login';
  }

  if (session.roles.includes(ROLES.TEACHER)) {
    return ROUTES.TEACHER.DASHBOARD;
  }

  if (session.roles.includes(ROLES.STUDENT)) {
    return ROUTES.STUDENT.DASHBOARD;
  }

  return ROUTES.PUBLIC.HOME;
}

export function getLoginRoute(kind: AuthSessionKind) {
  return kind === 'admin' ? '/admin/login' : ROUTES.PUBLIC.LOGIN;
}

export function rememberPostLoginRoute(kind: AuthSessionKind, path: string) {
  if (typeof window !== 'undefined' && isSafeInternalPath(path)) {
    window.sessionStorage.setItem(RETURN_TO_KEYS[kind], path);
  }
}

export function consumePostLoginRoute(kind: AuthSessionKind, session: AuthSession) {
  if (typeof window === 'undefined') {
    return getDefaultRoute(session);
  }

  const key = RETURN_TO_KEYS[kind];
  const destination = peekPostLoginRoute(kind, session);
  window.sessionStorage.removeItem(key);

  return destination;
}

export function peekPostLoginRoute(kind: AuthSessionKind, session: AuthSession) {
  if (typeof window === 'undefined') {
    return getDefaultRoute(session);
  }

  const path = window.sessionStorage.getItem(RETURN_TO_KEYS[kind]);
  return path && canAccessPath(session, path) ? path : getDefaultRoute(session);
}

export function canAccessPath(session: AuthSession, path: string) {
  if (!isSafeInternalPath(path)) {
    return false;
  }

  if (path.startsWith('/admin')) {
    return session.kind === 'admin' && hasAnyRole(session, INTERNAL_ROLES);
  }

  if (session.kind !== 'public') {
    return false;
  }

  if (path.startsWith(ROUTES.TEACHER.KYC)) {
    return hasAnyRole(session, [ROLES.STUDENT, ROLES.TEACHER]);
  }

  if (path.startsWith('/teacher')) {
    return session.roles.includes(ROLES.TEACHER);
  }

  if (path.startsWith('/student')) {
    return session.roles.includes(ROLES.STUDENT);
  }

  return true;
}

function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) {
    return null;
  }

  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const bytes = Uint8Array.from(window.atob(padded), (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
  } catch {
    return null;
  }
}

function normalizeRoles(value: unknown) {
  const values = Array.isArray(value) ? value : [value];
  const roles = values.flatMap((item) =>
    typeof item === 'string' ? item.split(/[\s,]+/) : [],
  );

  return [...new Set(roles.map((role) => role.trim().toUpperCase()).filter(Boolean))];
}

function isSafeInternalPath(path: string) {
  return path.startsWith('/') && !path.startsWith('//');
}

function notifySessionChanged(kind: AuthSessionKind) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_SESSION_CHANGED_EVENT, {
      detail: { kind },
    }));
  }
}

type AdminSessionMessage =
  | { type: 'SESSION_UPDATED'; token: string }
  | { type: 'SIGNED_OUT' };

let adminSessionChannel: BroadcastChannel | null = null;

function getAdminSessionChannel() {
  if (typeof window === 'undefined' || typeof BroadcastChannel === 'undefined') {
    return null;
  }
  if (!adminSessionChannel) {
    adminSessionChannel = new BroadcastChannel(ADMIN_SESSION_CHANNEL);
    adminSessionChannel.addEventListener('message', (event: MessageEvent<AdminSessionMessage>) => {
      if (event.data.type === 'SESSION_UPDATED') {
        synchronizeAdminAccessToken(event.data.token);
        return;
      }
      if (event.data.type === 'SIGNED_OUT') {
        adminAccessToken = null;
        adminSessionRevision += 1;
        window.sessionStorage.removeItem(TOKEN_KEYS.admin);
        window.localStorage.removeItem(ADMIN_CSRF_KEY);
        window.localStorage.removeItem(ADMIN_REFRESH_EXPECTED_KEY);
        window.localStorage.removeItem(ADMIN_REMEMBERED_KEY);
        notifySessionChanged('admin');
      }
    });
  }
  return adminSessionChannel;
}

function broadcastAdminSession(message: AdminSessionMessage) {
  getAdminSessionChannel()?.postMessage(message);
}

getAdminSessionChannel();
