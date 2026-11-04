import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { systemAdministrationService } from '../services/systemAdministrationService';
import { SystemSettingsPage } from './SystemSettingsPage';

vi.mock('../services/systemAdministrationService', () => ({
  systemAdministrationService: {
    listSettings: vi.fn(),
    updateSetting: vi.fn(),
  },
}));

const listSettingsMock = vi.mocked(systemAdministrationService.listSettings);
const updateSettingMock = vi.mocked(systemAdministrationService.updateSetting);

const commissionSetting = {
  id: 'setting-1',
  key: 'COMMISSION_RATE',
  value: '0.20',
  valueType: 'NUMBER' as const,
  description: 'Platform commission',
  editable: true,
  updatedBy: null,
  updatedAt: null,
};

afterEach(cleanup);

describe('SystemSettingsPage', () => {
  beforeEach(() => {
    listSettingsMock.mockResolvedValue([commissionSetting]);
    updateSettingMock.mockResolvedValue({ ...commissionSetting, value: '0.25' });
  });

  it('loads real settings and requires an audit reason before saving', async () => {
    render(<SystemSettingsPage />);

    const input = await screen.findByLabelText('Tỷ lệ hoa hồng nền tảng (0–1)');
    expect(input).toHaveValue(0.2);

    fireEvent.change(input, { target: { value: '0.25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));

    const confirmButton = screen.getByRole('button', { name: 'Xác nhận' });
    expect(confirmButton).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Lý do thay đổi'), {
      target: { value: 'Council-approved pricing' },
    });
    fireEvent.click(confirmButton);

    await waitFor(() => expect(updateSettingMock).toHaveBeenCalledWith(
      'COMMISSION_RATE',
      { value: '0.25', reason: 'Council-approved pricing' },
    ));
    expect(await screen.findByText(
      'Đã cập nhật Tỷ lệ hoa hồng nền tảng (0–1).',
    )).toBeInTheDocument();
  });

  it('shows a retryable error without fabricating values', async () => {
    listSettingsMock.mockRejectedValueOnce(new Error('offline'));

    render(<SystemSettingsPage />);

    expect(await screen.findByText(
      'Không thể tải cấu hình hệ thống. Vui lòng thử lại.',
    )).toBeInTheDocument();
    expect(screen.queryByDisplayValue('0.20')).not.toBeInTheDocument();
  });
});
