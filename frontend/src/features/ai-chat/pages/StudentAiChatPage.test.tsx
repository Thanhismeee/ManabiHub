import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { StudentAiChatPage } from './StudentAiChatPage';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}{location.search}</span>;
}

describe('StudentAiChatPage', () => {
  it('redirects legacy links into the learning workspace with the lesson selected', () => {
    render(
      <MemoryRouter initialEntries={['/student/courses/course-1/lesson-blocks/block-1/ai-chat']}>
        <Routes>
          <Route
            path="/student/courses/:courseId/lesson-blocks/:lessonBlockId/ai-chat"
            element={<StudentAiChatPage />}
          />
          <Route path="/student/courses/:courseId/learn" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/student/courses/course-1/learn?aiLessonBlockId=block-1',
    );
  });
});
