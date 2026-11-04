# MHB-24 Student Wishlist

## Business Rules

- Only authenticated students can access wishlist endpoints.
- Student ownership is derived from the authenticated user; no student identifier is
  accepted from the client.
- Only published courses can be added.
- A course can appear at most once in a student's wishlist.
- Students cannot list or remove another student's wishlist entries.

## API

- `GET /api/v1/student/wishlist`
- `POST /api/v1/student/wishlist/{courseId}`
- `DELETE /api/v1/student/wishlist/{courseId}`

The database unique constraint
`uq_student_wishlist_student_course` is the final concurrency boundary. Only a
violation of that constraint is mapped to the duplicate-wishlist response.

## Frontend

The catalog card, course detail card, and My Wishlist page use one React Query
cache. Adding or removing a course updates all three entry points immediately.
Unauthenticated users are sent to public login with their current route stored
for post-login return.

## Verification

- Service tests cover owner scoping, published-course validation, duplicate
  pre-checks, database races, and unrelated database failures.
- Controller tests cover student access, wrong-role rejection, and unauthenticated
  requests.
- CI validates Flyway migration `V029` against PostgreSQL.
