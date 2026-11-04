import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, cleanup } from '@testing-library/react';
import { ConfirmDialog } from '../ConfirmDialog/ConfirmDialog';

const defaultProps = {
  open: true,
  title: 'Xác nhận xóa',
  message: 'Bạn có chắc chắn muốn xóa?',
  onConfirm: vi.fn(),
  onCancel: vi.fn(),
};

describe('ConfirmDialog', () => {
  it('renders title and message when open', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} />);
    expect(baseElement.textContent).toContain('Xác nhận xóa');
    expect(baseElement.textContent).toContain('Bạn có chắc chắn muốn xóa?');
    cleanup();
  });

  it('uses Vietnamese default button labels', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} />);
    const buttons = baseElement.querySelectorAll('button');
    const texts = Array.from(buttons).map(b => b.textContent);
    expect(texts.some(t => t?.includes('Xác nhận'))).toBe(true);
    expect(texts.some(t => t?.includes('Hủy'))).toBe(true);
    cleanup();
  });

  it('fires onConfirm when confirm button is clicked', () => {
    const onConfirm = vi.fn();
    const { baseElement } = render(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />);
    const buttons = baseElement.querySelectorAll('button');
    const confirmBtn = Array.from(buttons).find(b => b.textContent?.includes('Xác nhận'));
    expect(confirmBtn).toBeDefined();
    fireEvent.click(confirmBtn!);
    expect(onConfirm).toHaveBeenCalledOnce();
    cleanup();
  });

  it('fires onCancel when cancel button is clicked', () => {
    const onCancel = vi.fn();
    const { baseElement } = render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);
    const buttons = baseElement.querySelectorAll('button');
    const cancelBtn = Array.from(buttons).find(b => b.textContent?.includes('Hủy'));
    expect(cancelBtn).toBeDefined();
    fireEvent.click(cancelBtn!);
    expect(onCancel).toHaveBeenCalledOnce();
    cleanup();
  });

  it('disables confirm button when loading', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} loading />);
    const buttons = baseElement.querySelectorAll('button');
    const confirmBtn = Array.from(buttons).find(b => b.textContent?.includes('Xác nhận'));
    expect(confirmBtn).toBeDefined();
    expect(confirmBtn!.hasAttribute('disabled') || confirmBtn!.getAttribute('aria-disabled') === 'true').toBe(true);
    cleanup();
  });

  it('disables cancel button when loading', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} loading />);
    const buttons = baseElement.querySelectorAll('button');
    const cancelBtn = Array.from(buttons).find(b => b.textContent?.includes('Hủy'));
    expect(cancelBtn).toBeDefined();
    expect(cancelBtn!.hasAttribute('disabled') || cancelBtn!.getAttribute('aria-disabled') === 'true').toBe(true);
    cleanup();
  });

  it('shows spinner on confirm button when loading', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} loading />);
    expect(baseElement.querySelector('[role="progressbar"]')).not.toBeNull();
    cleanup();
  });

  it('uses custom button labels', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} confirmLabel="Đồng ý" cancelLabel="Quay lại" />);
    const buttons = baseElement.querySelectorAll('button');
    const texts = Array.from(buttons).map(b => b.textContent);
    expect(texts.some(t => t?.includes('Đồng ý'))).toBe(true);
    expect(texts.some(t => t?.includes('Quay lại'))).toBe(true);
    cleanup();
  });

  it('does not render dialog content when closed', () => {
    const { baseElement } = render(<ConfirmDialog {...defaultProps} open={false} />);
    // MUI Dialog with open=false should not render content in the DOM
    const dialog = baseElement.querySelector('[role="dialog"]');
    expect(dialog).toBeNull();
    cleanup();
  });
});
