import { cleanup, render, screen } from '@testing-library/react';
import { HelmetProvider } from 'react-helmet-async';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { ROUTES } from '../../../shared/constants/routes';
import { HelpCenterIndexPage } from './HelpCenterIndexPage';

afterEach(cleanup);

describe('HelpCenterIndexPage', () => {
  it('makes draft legal documents visibly discoverable with their real routes', () => {
    render(
      <HelmetProvider>
        <MemoryRouter>
          <HelpCenterIndexPage />
        </MemoryRouter>
      </HelmetProvider>,
    );

    expect(screen.getByRole('button', { name: 'Pháp lý' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Điều khoản sử dụng/ }).getAttribute('href'))
      .toBe(ROUTES.PUBLIC.TERMS);
    expect(screen.getByRole('link', { name: /Chính sách bảo mật/ }).getAttribute('href'))
      .toBe(ROUTES.PUBLIC.PRIVACY);
  });
});
