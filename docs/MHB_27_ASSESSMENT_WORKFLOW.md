# MHB-27 Quiz and Final Test Workflow

## Business Rules

- Student identity and enrollment are derived from the authenticated JWT. The
  client never supplies a student or enrollment ID.
- A lesson Quiz accepts one selected option per configured question. Attempts are
  persisted and a score of at least 80% completes the Quiz lesson block.
- Correct Quiz answers are returned only after submission.
- Final Test eligibility requires every course lesson block to be completed.
- Total Final Test attempts equal the initial attempt plus the configured
  `maxRetakes`.
- Starting a Final Test is idempotent while a non-expired attempt is active.
  Enrollment locking prevents concurrent requests from consuming extra attempts.
- Final Test questions returned at start contain no correctness metadata.
- Submission locks the owned attempt, rejects duplicate/expired submissions, and
  persists score, answers, pass/fail state, and feedback.
- A failed Final Test keeps certificate eligibility blocked. A passed Final Test
  completes the enrollment.

## API

- `POST /api/v1/student/lessons/{lessonBlockId}/quiz-submissions`
- `GET /api/v1/student/courses/{courseId}/final-test/eligibility`
- `POST /api/v1/student/courses/{courseId}/final-test/attempts`
- `POST /api/v1/student/courses/{courseId}/final-test/submissions`

## Traceability

- UC-12
- BR-QUIZ, BR-FTEST
- MSG-QUIZ
