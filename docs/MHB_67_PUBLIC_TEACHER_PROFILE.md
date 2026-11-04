# MHB-67 Public Teacher Profile

## Business purpose

Students need a real identity behind every published course. MHB-67 replaces
hard-coded landing-page instructors with public profiles backed by approved
teacher data.

This feature does not create a new teacher identity model. It reads the same
`teacher_profiles` records already referenced by `courses`.

## Public eligibility rule

A profile is public only when all three conditions are true:

| Source | Required value | Why |
| --- | --- | --- |
| `teacher_profiles.kyc_status` | `APPROVED` | Identity verification is valid |
| `teacher_profiles.can_publish_course` | `true` | Publishing permission has not been revoked |
| `app_users.user_status` | `ACTIVE` | The account is not locked or deleted |

The featured-instructor endpoint adds one more rule: the teacher must have at
least one `PUBLISHED` course.

Locked, deleted, rejected, correction-required, or revoked teachers receive the
same generic `404` as an unknown ID. This avoids revealing whether a private
identity exists.

## API

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/public/teachers?limit=4` | Featured eligible teachers; limit is 1–12 |
| `GET /api/v1/public/teachers/{teacherId}` | One eligible teacher and published courses |

Both endpoints are available without login.

The response is an allowlist. It contains only:

- Teacher ID, display name, avatar, public bio, verified flag.
- Published course count.
- Public course card fields.

It never serializes email, phone number, legal/KYC documents, identity numbers,
review notes, account status, KYC status, or the internal publishing flag.

## Frontend flow

1. `/teachers/:teacherId` loads the public profile.
2. Course-detail teacher sections link to that route.
3. Catalog cards expose a separate teacher link without changing the course
   card action.
4. The landing page calls the featured endpoint.
5. If the featured response is empty or fails, the instructor section stays
   hidden instead of displaying fake people or fake statistics.

The profile page has explicit loading, not-found/private, empty-course, and
responsive states.

## Code map

| Area | Main files |
| --- | --- |
| Eligibility query | `TeacherProfileRepository` |
| Published-course query | `CourseRepository` |
| Privacy-safe mapping | `PublicTeacherProfileServiceImpl` |
| Public HTTP endpoints | `PublicTeacherProfileController` |
| Public route/page | `PublicTeacherProfilePage.tsx` |
| Landing discovery | `TopInstructorsSection.tsx` |
| Course links | `TeacherProfile.tsx`, `CourseCatalogCard.tsx` |

## Verification

Backend:

```powershell
cd backend
.\mvnw.cmd clean test
```

The PostgreSQL integration test applies every Flyway migration and proves:

- Approved/active/allowed profiles are discoverable.
- Revoked and locked profiles are not discoverable.
- Draft courses are not returned.
- Featured results exclude private teachers.

Frontend:

```powershell
cd frontend
npm run test
npm run lint
npm run build
```

Frontend tests verify the public page route behavior and the teacher links from
course detail and catalog cards.

## Deployment and rollback

There is no database migration and no new environment variable.

Deploy backend and frontend from the same merged revision because the frontend
expects the new teacher endpoints and `teacherId`/`verified` course fields.

Rollback is code-only: redeploy the previous backend and frontend versions. No
data rollback is required.

## Out of scope

- Verified student reviews and ratings are MHB-68.
- Teacher analytics are MHB-69.
- Public student counts are intentionally absent until a reviewed aggregation
  rule exists; the landing page must not invent them.
