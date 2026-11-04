# MHB-68 — Verified Course Ratings and Reviews

## Business outcome

ManabiHub no longer needs fabricated course reputation data. A rating now
comes from a real student enrollment and remains public only while that
enrollment is `ACTIVE` or `COMPLETED`.

The business rules are:

| Rule | Enforcement |
|---|---|
| One review per student/course | One enrollment per student/course plus `uq_course_reviews_enrollment` |
| Who can review | Authenticated `STUDENT` with an `ACTIVE` or `COMPLETED` enrollment |
| Refunded/revoked access | Write is rejected; existing review is excluded from public lists and aggregates |
| Course visibility | Only `PUBLISHED` courses expose public reviews |
| Rating | Integer from 1 through 5, checked by request validation and PostgreSQL |
| Text | Required plain text, normalized and limited to 10–2,000 characters |
| Update/IDOR | The route contains the course ID, never an arbitrary review ID; ownership comes from the current user's enrollment |
| Concurrent writes | The enrollment row is locked during `PUT`; the database unique constraint is the final guarantee |
| Idempotency | Repeating the same `PUT` returns the existing review without rewriting it |
| Moderation | Public queries include `APPROVED` only; editing a `HIDDEN` review cannot self-approve it |

## API

### Public

- `GET /api/v1/public/courses/{courseId-or-slug}/reviews?page=0&size=10`
  - public, maximum page size 20;
  - returns approved reviews from active/completed enrollments;
  - author allowlist: display name and avatar only;
  - never returns student ID, email, phone or legal name.
- Existing public course and teacher-profile responses now include
  `averageRating` and `reviewCount`. The average is rounded to one decimal.

### Student

- `GET /api/v1/student/courses/{courseId}/review`
  - returns the current student's review or `null`;
  - rejects users without an eligible enrollment.
- `PUT /api/v1/student/courses/{courseId}/review`
  - creates or updates the current student's one review;
  - request: `{ "rating": 1..5, "reviewText": "10..2000 chars" }`.

## Frontend flow

- Catalog cards, course hero and public teacher courses show reputation only
  when `reviewCount > 0`.
- Course detail loads real paginated reviews.
- The editor appears only to a signed-in student whose course response says
  they are enrolled.
- The backend rechecks enrollment on every read/write; hiding the editor is
  convenience, not authorization.
- Review text is rendered as React text, never with raw HTML.
- Loading, empty, error/retry, mobile and pagination states are explicit.

There is intentionally no dead **Report** button. MHB-43 owns report creation
and can use the public review ID returned here; MHB-44 owns moderation. Until
that real route exists, the UI does not pretend reporting works.

## Code map

- `backend/.../review/entity/CourseReview.java`: persisted review.
- `backend/.../review/service/impl/CourseReviewServiceImpl.java`: eligibility,
  ownership, locking, normalization and public mapping.
- `backend/.../review/repository/CourseReviewRepository.java`: approved-review
  pagination and bulk aggregate queries.
- `frontend/.../course-reviews/components/CourseReviewsSection.tsx`: review
  list/editor/states.
- `V041__create_verified_course_reviews.sql`: table and database constraints.

## Migration and deployment

This change is integrated as Flyway migration **V041** in the release
candidate. Deploy in this order:

1. Apply `V038` certificate claims and `V039` escrow default from `develop`.
2. Apply `V040` secure withdrawals.
3. Apply `V041` verified reviews.
4. Deploy the backend and frontend from the same release revision.
5. Smoke-test a paid enrollment, a completed enrollment and a refunded
   enrollment before announcing the feature.

No environment variable or secret is added.

Rollback must not delete `course_reviews`. Roll back application binaries to
the previous revision and leave V041/data in place; the previous application
does not reference the table. A destructive migration is unnecessary.

## Verification

- Unit tests cover owner scoping, active/refunded eligibility, locked upsert,
  idempotent repeated writes and hidden-review behavior.
- MVC tests cover student-only authorization, validation, bounded public
  pagination and privacy-safe serialization.
- PostgreSQL Testcontainers runs all migrations and verifies:
  - only approved reviews from eligible enrollments are public/countable;
  - concurrent inserts cannot create two reviews for one enrollment.
- Frontend tests cover real public rendering, inert HTML, enrolled-student
  submission and error/retry behavior.
