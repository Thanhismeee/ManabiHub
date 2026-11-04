import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, cleanup } from '@testing-library/react';
import { ErrorState } from '../ErrorState/ErrorState';

describe('ErrorState', () => {
  it('renders with Vietnamese defaults', () => {
    const { container } = render(<ErrorState />);
    expect(container.textContent).toContain('Đã xảy ra lỗi');
    expect(container.textContent).toContain('Không thể tải nội dung. Vui lòng thử lại.');
    cleanup();
  });

  it('renders custom title and message', () => {
    const { container } = render(<ErrorState title="Lỗi mạng" message="Kiểm tra kết nối internet." />);
    expect(container.textContent).toContain('Lỗi mạng');
    expect(container.textContent).toContain('Kiểm tra kết nối internet.');
    cleanup();
  });

  it('shows retry button when onRetry is provided', () => {
    const onRetry = vi.fn();
    const { container } = render(<ErrorState onRetry={onRetry} />);
    const btn = container.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn!.textContent).toContain('Thử lại');
    fireEvent.click(btn!);
    expect(onRetry).toHaveBeenCalledOnce();
    cleanup();
  });

  it('does not show retry button without onRetry', () => {
    const { container } = render(<ErrorState />);
    expect(container.querySelector('button')).toBeNull();
    cleanup();
  });

  it('supports custom retry label', () => {
    const { container } = render(<ErrorState onRetry={() => {}} retryLabel="Tải lại" />);
    const btn = container.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn!.textContent).toContain('Tải lại');
    cleanup();
  });

  it('has role="alert" for accessibility', () => {
    const { container } = render(<ErrorState />);
    expect(container.querySelector('[role="alert"]')).not.toBeNull();
    cleanup();
  });
});
