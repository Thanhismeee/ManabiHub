import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { HelpCenterLayout } from './HelpCenterLayout';

describe('HelpCenterLayout', () => {
  it('uses the system logo as the home link across help and legal pages', () => {
    render(
      <MemoryRouter initialEntries={['/help']}>
        <Routes>
          <Route element={<HelpCenterLayout />}>
            <Route path="/help" element={<div>Nội dung trợ giúp</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Về trang chủ ManabiHub' }).getAttribute('href'))
      .toBe('/');
    expect(screen.getByRole('img', { name: 'ManabiHub' }).getAttribute('src'))
      .toBe('/manabihub-header-logo.png');
  });
});
