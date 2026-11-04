# MHB-31 Student Writing Suggestions and Feedback

## Contract

`GET /api/v1/student/lessons/{lessonBlockId}/writing-submissions/me` resolves the
authenticated student's enrollment before loading a submission. The client never
supplies an enrollment or student identifier.

The response keeps the two feedback sources separate:

- `aiSuggestion.official` is always `false`. AI output is preliminary learning
  support and is not an authoritative grade.
- `teacherFeedback.official` is `true`. It contains the latest official score and
  comment submitted by the owning course teacher.

The student learning screen presents these values in separate sections and does
not treat AI suggestions as teacher feedback.

## Traceability

- UC-15: View Writing Suggestions and Feedback
- BR-AI-01, BR-AI-03, BR-AI-04, BR-FEEDBACK, BR-RBAC
- AI_SUGGESTION_NOT_OFFICIAL, TEACHER_FEEDBACK_REQUIRED,
  TEACHER_FEEDBACK_SUBMITTED
