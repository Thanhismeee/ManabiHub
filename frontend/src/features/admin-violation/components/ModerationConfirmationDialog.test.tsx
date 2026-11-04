import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ModerationConfirmationDialog } from './ModerationConfirmationDialog';

describe('ModerationConfirmationDialog', () => {
  it('describes every selected consequence before applying severe actions', () => {
    const onConfirm = vi.fn();
    const payload = {
      decision: 'UPHELD' as const,
      decisionNote: 'Confirmed severe copyright abuse.',
      actions: ['BAN_ACCOUNT', 'FREEZE_BALANCE'] as const,
    };

    render(
      <ModerationConfirmationDialog
        payload={{ ...payload, actions: [...payload.actions] }}
        isPending={false}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />,
    );

    expect(screen.getByText('Khóa tài khoản')).toBeInTheDocument();
    expect(screen.getByText('Đóng băng ví')).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', { name: 'Xác nhận và áp dụng' }),
    );
    expect(onConfirm).toHaveBeenCalledWith({
      ...payload,
      actions: [...payload.actions],
    });
  });

  it('prevents confirmation and dismissal while the mutation is pending', () => {
    render(
      <ModerationConfirmationDialog
        payload={{
          decision: 'UPHELD',
          decisionNote: 'Confirmed violation.',
          actions: ['FORCE_DRAFT'],
        }}
        isPending
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Đang xử lý…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Kiểm tra lại' })).toBeDisabled();
  });
});
