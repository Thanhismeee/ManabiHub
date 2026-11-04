import { describe, it, expect } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PermissionDenied } from '../PermissionDenied/PermissionDenied';

function renderPermissionDenied(props = {}) {
  return render(
    <MemoryRouter>
      <PermissionDenied {...props} />
    </MemoryRouter>
  );
}

describe('PermissionDenied', () => {
  it('renders denied message', () => {
    const { container } = renderPermissionDenied();
    expect(container.textContent).toContain('Không có quyền truy cập');
    expect(container.textContent).toContain('Bạn không có quyền truy cập trang này.');
    cleanup();
  });

  it('has role="alert" for accessibility', () => {
    const { container } = renderPermissionDenied();
    expect(container.querySelector('[role="alert"]')).not.toBeNull();
    cleanup();
  });

  it('shows required role when provided', () => {
    const { container } = renderPermissionDenied({ requiredRole: 'COURSE_MANAGER' });
    expect(container.textContent).toContain('COURSE_MANAGER');
    cleanup();
  });

  it('renders back navigation button', () => {
    const { container } = renderPermissionDenied();
    const btn = container.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn!.textContent).toContain('Quay lại trang chính');
    cleanup();
  });
});
