import { describe, it, expect } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotFoundPage } from '../NotFoundPage/NotFoundPage';

function renderNotFound() {
  return render(
    <MemoryRouter initialEntries={['/does-not-exist']}>
      <NotFoundPage />
    </MemoryRouter>
  );
}

describe('NotFoundPage', () => {
  it('renders 404 heading', () => {
    const { container } = renderNotFound();
    expect(container.textContent).toContain('404');
    cleanup();
  });

  it('renders Vietnamese not-found message', () => {
    const { container } = renderNotFound();
    expect(container.textContent).toContain('Không tìm thấy trang');
    cleanup();
  });

  it('renders description', () => {
    const { container } = renderNotFound();
    expect(container.textContent).toContain('Trang bạn đang tìm kiếm không tồn tại hoặc đã được di chuyển.');
    cleanup();
  });

  it('renders home navigation button', () => {
    const { container } = renderNotFound();
    const btn = container.querySelector('button');
    expect(btn).not.toBeNull();
    expect(btn!.textContent).toContain('Về trang chủ');
    cleanup();
  });
});
