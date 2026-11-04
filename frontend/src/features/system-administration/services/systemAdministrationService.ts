import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type {
  InternalAdminAccount,
  InviteInternalAdminPayload,
  ResendInternalAdminInvitationPayload,
  UpdateInternalAdminRolePayload,
  UpdateInternalAdminStatusPayload,
  UpdateSystemSettingPayload,
  SystemSetting,
} from '../types/systemAdministrationTypes';

export const systemAdministrationService = {
  async listSettings() {
    const response = await axiosClient.get<ApiResponse<SystemSetting[]>>(
      ENDPOINTS.SYSTEM_ADMIN.SETTINGS,
    );
    return response.data.data;
  },

  async updateSetting(key: string, payload: UpdateSystemSettingPayload) {
    const response = await axiosClient.put<ApiResponse<SystemSetting>>(
      ENDPOINTS.SYSTEM_ADMIN.SETTING(key),
      payload,
    );
    return response.data.data;
  },

  async listInternalAdmins() {
    const response = await axiosClient.get<ApiResponse<InternalAdminAccount[]>>(
      ENDPOINTS.SYSTEM_ADMIN.INTERNAL_ACCOUNTS,
    );
    return response.data.data;
  },

  async updateInternalAdminRole(
    adminId: string,
    payload: UpdateInternalAdminRolePayload,
  ) {
    const response = await axiosClient.patch<ApiResponse<InternalAdminAccount>>(
      ENDPOINTS.SYSTEM_ADMIN.INTERNAL_ACCOUNT_ROLE(adminId),
      payload,
    );
    return response.data.data;
  },

  async updateInternalAdminStatus(
    adminId: string,
    payload: UpdateInternalAdminStatusPayload,
  ) {
    const response = await axiosClient.patch<ApiResponse<InternalAdminAccount>>(
      ENDPOINTS.SYSTEM_ADMIN.INTERNAL_ACCOUNT_STATUS(adminId),
      payload,
    );
    return response.data.data;
  },

  async inviteInternalAdmin(payload: InviteInternalAdminPayload) {
    const response = await axiosClient.post<ApiResponse<InternalAdminAccount>>(
      ENDPOINTS.SYSTEM_ADMIN.INTERNAL_ACCOUNT_INVITATIONS,
      payload,
    );
    return response.data.data;
  },

  async resendInternalAdminInvitation(
    adminId: string,
    payload: ResendInternalAdminInvitationPayload,
  ) {
    const response = await axiosClient.post<ApiResponse<InternalAdminAccount>>(
      ENDPOINTS.SYSTEM_ADMIN.INTERNAL_ACCOUNT_INVITATION_RESEND(adminId),
      payload,
    );
    return response.data.data;
  },
};
