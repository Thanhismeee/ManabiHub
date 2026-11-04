import { Alert } from '@mui/material';
import { Navigate, useParams } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';

export function StudentAiChatPage() {
  const { courseId, lessonBlockId } = useParams<{ courseId: string; lessonBlockId: string }>();

  if (!courseId || !lessonBlockId) {
    return <Alert severity="error">The course and lesson context are required for AI chat.</Alert>;
  }

  // Keep old deep links working while ensuring the chat always lives in the
  // learning workspace next to the selected lesson.
  return (
    <Navigate
      to={`${ROUTES.STUDENT.COURSE_LEARN(courseId)}?aiLessonBlockId=${encodeURIComponent(lessonBlockId)}`}
      replace
    />
  );
}
