import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import { operationsService } from './operationsService';

vi.mock('../../../shared/api/axiosClient', () => ({
  axiosClient: {
    get: vi.fn(),
  },
}));

const getMock = vi.mocked(axiosClient.get);

describe('operationsService', () => {
  beforeEach(() => {
    getMock.mockResolvedValue({ data: { data: {} } });
  });

  it('uses only the canonical GET endpoints', async () => {
    await operationsService.getOverview();
    await operationsService.getRuntimeConfiguration();

    expect(getMock).toHaveBeenNthCalledWith(1, ENDPOINTS.ADMIN_OPERATIONS.OVERVIEW);
    expect(getMock).toHaveBeenNthCalledWith(2, ENDPOINTS.ADMIN_OPERATIONS.RUNTIME_CONFIG);
  });

  it('trims safe log filters and enforces server limits', async () => {
    await operationsService.getRecentLogs({
      level: 'ERROR',
      query: `  ${'x'.repeat(140)}  `,
      correlationId: `  ${'c'.repeat(140)}  `,
      limit: 999,
    });

    expect(getMock).toHaveBeenCalledWith(ENDPOINTS.ADMIN_OPERATIONS.LOGS, {
      params: {
        level: 'ERROR',
        query: 'x'.repeat(120),
        correlationId: 'c'.repeat(128),
        limit: 200,
      },
    });
  });

  it('omits empty filters and applies the default limit', async () => {
    await operationsService.getRecentLogs({ query: '   ', correlationId: '' });

    expect(getMock).toHaveBeenCalledWith(ENDPOINTS.ADMIN_OPERATIONS.LOGS, {
      params: { limit: 100 },
    });
  });
});
