import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RefundDecisionForm } from './RefundDecisionForm';

afterEach(cleanup);

describe('RefundDecisionForm', () => {
  it('keeps long decision content scrollable while actions remain outside the scroll region', () => {
    const previousOverflow = document.body.style.overflow;
    const { unmount } = render(
      <RefundDecisionForm
        action="approve"
        amount="99.000 ₫"
        provider="VNPAY"
        platformImpact="19.800 ₫"
        teacherImpact="79.200 ₫"
        escrowImpact="79.200 ₫"
        onConfirm={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
        errorMessage={'Thông báo lỗi dài. '.repeat(30)}
      />,
    );

    const dialog = screen.getByRole('dialog');
    const scrollRegion = screen.getByRole('region', {
      name: 'Nội dung quyết định hoàn tiền',
    });
    const cancelButton = screen.getByRole('button', { name: 'Hủy' });
    const approveButton = screen.getByRole('button', { name: 'Chấp thuận' });

    expect(dialog.parentElement).toHaveClass('z-[1300]');
    expect(dialog).toHaveClass('max-h-[calc(100dvh-2rem)]', 'flex', 'flex-col');
    expect(scrollRegion).toHaveClass('min-h-0', 'flex-1', 'overflow-y-auto');
    expect(scrollRegion).toContainElement(screen.getByRole('alert'));
    expect(scrollRegion).not.toContainElement(cancelButton);
    expect(scrollRegion).not.toContainElement(approveButton);
    expect(dialog).toContainElement(cancelButton);
    expect(dialog).toContainElement(approveButton);
    expect(document.body.style.overflow).toBe('hidden');

    unmount();
    expect(document.body.style.overflow).toBe(previousOverflow);
  });

  it('requires an approval reason and a non-blank audit note', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);

    render(
      <RefundDecisionForm
        action="approve"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    const submitButton = screen.getByRole('button', { name: 'Chấp thuận' });
    expect(submitButton).toBeDisabled();
    expect(
      screen.getByRole('option', { name: 'Đủ điều kiện theo chính sách hoàn tiền' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('option', { name: 'Đã quá thời hạn hoàn tiền' }),
    ).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Mã lý do/), {
      target: { value: 'STANDARD_ELIGIBLE' },
    });
    fireEvent.change(screen.getByLabelText(/Căn cứ quyết định/), {
      target: { value: '   ' },
    });
    expect(submitButton).toBeDisabled();

    const note = screen.getByLabelText(/Căn cứ quyết định/);
    expect(note).toHaveAttribute('maxLength', '2000');
    fireEvent.change(note, {
      target: { value: '  Đã đối chiếu payment và eligibility snapshot.  ' },
    });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledWith({
        reasonCode: 'STANDARD_ELIGIBLE',
        note: 'Đã đối chiếu payment và eligibility snapshot.',
      });
    });
  });

  it('only offers rejection reason codes for a rejection', () => {
    render(
      <RefundDecisionForm
        action="reject"
        onConfirm={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
        errorMessage="Yêu cầu đã được người khác xử lý."
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Yêu cầu đã được người khác xử lý.',
    );
    expect(
      screen.getByRole('option', { name: 'Đã quá thời hạn hoàn tiền' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('option', { name: 'Đủ điều kiện theo chính sách hoàn tiền' }),
    ).not.toBeInTheDocument();
  });
});
