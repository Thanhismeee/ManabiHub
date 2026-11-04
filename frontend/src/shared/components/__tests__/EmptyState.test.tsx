import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, cleanup } from '@testing-library/react';
import { EmptyState } from '../EmptyState/EmptyState';

describe('EmptyState', () => {
  it('renders with Vietnamese defaults', () => {
    const { container } = render(<EmptyState />);
    expect(container.textContent).toContain('Không có dữ liệu');
    expect(container.textContent).toContain('Hiện không có dữ liệu để hiển thị.');
    cleanup();
  });

  it('renders custom title and description', () => {
    const { container } = render(<EmptyState title="Chưa có khóa học" description="Bắt đầu tạo khóa học đầu tiên." />);
    expect(container.textContent).toContain('Chưa có khóa học');
    expect(container.textContent).toContain('Bắt đầu tạo khóa học đầu tiên.');
    cleanup();
  });

  it('renders action button when both actionLabel and onAction are provided', () => {
    const onAction = vi.fn();
    const { container } = render(<EmptyState actionLabel="Tạo mới" onAction={onAction} />);
    const btn = container.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn!.textContent).toContain('Tạo mới');
    fireEvent.click(btn!);
    expect(onAction).toHaveBeenCalledOnce();
    cleanup();
  });

  it('does not render action button without actionLabel', () => {
    const { container } = render(<EmptyState />);
    expect(container.querySelector('button')).toBeNull();
    cleanup();
  });

  it('renders custom icon', () => {
    const { container } = render(<EmptyState icon={<span data-testid="custom-icon">🎓</span>} />);
    expect(container.querySelector('[data-testid="custom-icon"]')).not.toBeNull();
    cleanup();
  });
});
