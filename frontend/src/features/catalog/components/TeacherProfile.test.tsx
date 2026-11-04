import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { TeacherProfile } from './TeacherProfile';

describe('TeacherProfile', () => {
  it('links the teacher identity and call to action to the public profile', () => {
    render(
      <MemoryRouter>
        <TeacherProfile
          teacher={{
            id: 'teacher-123',
            name: 'Sensei An',
            bio: 'N5 grammar teacher',
            verified: true,
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Sensei An' }))
      .toHaveAttribute('href', '/teachers/teacher-123');
    expect(screen.getByRole('link', { name: 'Xem hồ sơ và các khóa học' }))
      .toHaveAttribute('href', '/teachers/teacher-123');
    expect(screen.getByText('Đã xác minh')).toBeInTheDocument();
  });
});
