import { describe, expect, it, vi } from 'vitest';
import { adminAuditApi } from './adminAuditApi';
import { axiosClient } from '../../../shared/api/axiosClient';

vi.mock('../../../shared/api/axiosClient', () => ({
  axiosClient: {
    get: vi.fn(),
  },
}));

describe('adminAuditApi', () => {
  it('calls the versioned paginated audit endpoint', async () => {
    vi.mocked(axiosClient.get).mockResolvedValueOnce({
      data: {
        data: {
          content: [],
          page: 2,
          size: 20,
          totalElements: 42,
          totalPages: 3,
          first: false,
          last: true,
        },
      },
    });

    const result = await adminAuditApi.getAuditLogs({ page: 2, size: 20, action: 'COURSE_UPDATE' });

    expect(axiosClient.get).toHaveBeenCalledWith('/v1/admin/audit-logs', {
      params: {
        page: 2,
        size: 20,
        action: 'COURSE_UPDATE',
        sort: 'createdAt,desc',
      },
    });
    expect(result.totalElements).toBe(42);
  });

  it('uses the same versioned route for audit details', async () => {
    vi.mocked(axiosClient.get).mockResolvedValueOnce({ data: { data: { id: 'audit-1' } } });

    await adminAuditApi.getAuditLogDetail('audit-1');

    expect(axiosClient.get).toHaveBeenCalledWith('/v1/admin/audit-logs/audit-1');
  });
});
