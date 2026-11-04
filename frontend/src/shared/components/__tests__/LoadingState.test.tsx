import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingState } from '../LoadingState/LoadingState';

describe('LoadingState', () => {
  it('renders with default Vietnamese message', () => {
    render(<LoadingState />);
    expect(screen.getByText('Đang tải...')).toBeInTheDocument();
  });

  it('renders with custom message', () => {
    render(<LoadingState message="Đang tải khóa học..." />);
    expect(screen.getByText('Đang tải khóa học...')).toBeInTheDocument();
  });

  it('has role="status" for accessibility', () => {
    const { container } = render(<LoadingState />);
    expect(container.querySelector('[role="status"]')).not.toBeNull();
  });

  it('applies aria-label matching the message', () => {
    const { container } = render(<LoadingState message="Đang xử lý" />);
    const statusEl = container.querySelector('[role="status"]');
    expect(statusEl).not.toBeNull();
    expect(statusEl!.getAttribute('aria-label')).toBe('Đang xử lý');
  });

  it('renders spinner', () => {
    const { container } = render(<LoadingState />);
    expect(container.querySelector('[role="progressbar"]')).not.toBeNull();
  });
});
