export type SystemSettingValueType = 'NUMBER' | 'BOOLEAN' | 'STRING' | 'JSON';
export type InternalAdminRole = 'SYSTEM_ADMIN' | 'COURSE_MANAGER' | 'FINANCE_MANAGER';
export type InternalAdminStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';
export type InternalAdminInvitationStatus =
  | 'NONE'
  | 'PENDING'
  | 'EXPIRED'
  | 'ACCEPTED'
  | 'REVOKED';

export interface SystemSetting {
  id: string;
  key: string;
  value: string;
  valueType: SystemSettingValueType;
  description: string | null;
  editable: boolean;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface InternalAdminAccount {
  id: string;
  email: string;
  fullName: string;
  status: InternalAdminStatus;
  role: InternalAdminRole;
  lastLoginAt: string | null;
  updatedAt: string | null;
  invitationStatus: InternalAdminInvitationStatus;
  invitationExpiresAt: string | null;
}

export interface UpdateSystemSettingPayload {
  value: string;
  reason: string;
}

export interface UpdateInternalAdminRolePayload {
  roleCode: InternalAdminRole;
  reason: string;
}

export interface UpdateInternalAdminStatusPayload {
  status: Extract<InternalAdminStatus, 'ACTIVE' | 'DISABLED'>;
  reason: string;
}

export interface InviteInternalAdminPayload {
  email: string;
  fullName: string;
  roleCode: InternalAdminRole;
  reason: string;
}

export interface ResendInternalAdminInvitationPayload {
  reason: string;
}
