# MHB-33 Teacher Writing Review

Last reviewed: 2026-07-18

## Business Contract

- A Teacher can list and open writing submissions only for courses they own.
- AI output is optional, preliminary reference material and is always returned with `official: false`.
- Teacher feedback is the authoritative review record and is returned with `official: true`.
- Saving feedback updates the latest Teacher feedback for the submission, moves the submission to `TEACHER_FEEDBACK_READY`, and creates an in-app notification for the Student.

## Teacher API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/teacher/writing-submissions` | Search and filter the Teacher's review queue. |
| `GET` | `/api/v1/teacher/writing-submissions/{submissionId}` | Read submission, AI suggestion, and Teacher feedback separately. |
| `PUT` | `/api/v1/teacher/writing-submissions/{submissionId}/feedback` | Create or update the authoritative Teacher feedback. |

All endpoints require the `TEACHER` role. The current Teacher is resolved from the authenticated user; no Teacher identifier is accepted from the client. A non-owned submission is returned as not found to avoid exposing its existence.

## Course Content Reference

Legacy writing submissions reference `lessons.id` through `writing_submissions.lesson_id`. The current Course Builder stores content in `course_lesson_blocks`, so migration `V021` adds `writing_submissions.lesson_block_id` without rewriting legacy data.

Exactly one content reference must be present:

- Existing legacy rows keep `lesson_id` and leave `lesson_block_id` null.
- New submissions created by MHB-30 must set `lesson_block_id` and leave `lesson_id` null.

MHB-30 and MHB-31 must preserve the separation between AI suggestions and Teacher feedback when writing or displaying this contract.
