import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type {
  OperationsLogFilters,
  OperationsLogPage,
  OperationsOverview,
  RuntimeConfiguration,
} from '../types/operationsTypes';

const MAX_LOG_LIMIT = 200;

function normalizedLogParams(filters: OperationsLogFilters) {
  const level = filters.level?.trim();
  const query = filters.query?.trim().slice(0, 120);
  const correlationId = filters.correlationId?.trim().slice(0, 128);
  const requestedLimit = Number.isFinite(filters.limit) ? Math.floor(filters.limit ?? 100) : 100;

  return {
    ...(level ? { level } : {}),
    ...(query ? { query } : {}),
    ...(correlationId ? { correlationId } : {}),
    limit: Math.min(MAX_LOG_LIMIT, Math.max(1, requestedLimit)),
  };
}

export const operationsService = {
  async getOverview() {
    const response = await axiosClient.get<ApiResponse<OperationsOverview>>(
      ENDPOINTS.ADMIN_OPERATIONS.OVERVIEW,
    );
    return response.data.data;
  },

  async getRuntimeConfiguration() {
    const response = await axiosClient.get<ApiResponse<RuntimeConfiguration>>(
      ENDPOINTS.ADMIN_OPERATIONS.RUNTIME_CONFIG,
    );
    return response.data.data;
  },

  async getRecentLogs(filters: OperationsLogFilters = {}) {
    const response = await axiosClient.get<ApiResponse<OperationsLogPage>>(
      ENDPOINTS.ADMIN_OPERATIONS.LOGS,
      { params: normalizedLogParams(filters) },
    );
    return response.data.data;
  },
};
