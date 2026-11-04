# ManabiHub System Readiness Audit

Date: 2026-07-27

## Executive verdict

ManabiHub has a credible Iteration 1-4 core: authentication, teacher KYC,
course authoring and approval, marketplace purchase, learning blocks, AI
learning assistance, wallet, withdrawal, and payout operations have real code
paths. It is not yet council-ready as a complete marketplace because several
SRS use cases are absent or represented only by database placeholders.

The highest risk is inconsistent state propagation across KYC, moderation,
courses, learner access, escrow, wallet, refund, and payout.

## External benchmark

The target behavior follows these mature-platform principles:

* A public course page exposes the instructor and links to a public instructor
  profile containing that instructor's other courses.
* Ratings come from real learners, are editable, and are protected from
  manipulation. Aggregate reputation is not fabricated.
* Identity verification is a prerequisite for publishing, not a queue where
  staff re-approve every successful automated verification.
* Unpublishing stops new enrollment but normally preserves access for existing
  learners.
* Trust and safety uses evidence, progressive restrictions, auditability, and
  explicit notifications instead of deleting records.
* Refund, escrow, and payout decisions are coordinated state transitions.

Reference material:

* https://support.udemy.com/hc/en-us/articles/229231027-How-to-preview-and-compare-courses
* https://support.udemy.com/hc/en-us/articles/229605048-Udemy-review-system-guidelines
* https://support.udemy.com/hc/en-us/articles/229604268-Submit-a-Course-for-Review
* https://support.udemy.com/hc/en-us/articles/229604968-Instructors-How-to-Delete-Unpublish-or-Republish-Your-Course
* https://support.udemy.com/hc/en-us/articles/360005564733-Trust-Safety-FAQ
* https://docs.moodle.org/28/en/Enrolment_FAQ

## Use-case coverage

Status is based on reachable frontend routes, backend controllers/services,
Flyway migrations, and Jira as of the audit date.

| UC | Capability | Status | Required action |
|---|---|---|---|
| 01-04 | Onboarding, login, profile | Implemented | Regression and deployed OAuth evidence |
| 05-09 | Search, detail, wishlist, purchase, history | Implemented | Add real teacher/review links |
| 10-16 | Lessons, progress, quiz, flashcards, writing, AI | Implemented | Preserve AI-as-suggestion language |
| 17 | My Wallet | Partial | Decide Teacher revenue wallet vs Student top-up scope |
| 18 | Course refund | Missing UI/API | Complete MHB-41 and MHB-42 |
| 19 | Purchased-course review | Missing | Complete MHB-68 |
| 20 | Violation report | Missing | Complete MHB-43 |
| 21 | Notifications | Implemented | SRS must describe polling, not nonexistent WebSocket |
| 22-23 | KYC and course structure | Implemented | Use exception review semantics |
| 24 | Course analytics | Missing Jira and code | Create dedicated Iteration 5 story |
| 25-27 | Publish, writing override, withdrawal | Implemented/partial | Finish MHB-38 and MHB-39 rework |
| 28 | Review Teacher KYC | Semantics incorrect | Refocus MHB-13 on exceptions and investigations |
| 29 | Approve course publication | Implemented | Regression evidence |
| 30 | Resolve violation | Missing | Complete MHB-44 |
| 31 | Configure settings/admin roles | Missing UI/API | Complete MHB-52 |
| 32 | Approve refund | Missing | Complete MHB-42 |
| 33 | Payout settlement | Implemented, under review | Complete PR review and evidence |
| 34 | Configure lesson blocks | Implemented | Regression evidence |

Supporting product gaps:

* MHB-67: public teacher profile and instructor discovery.
* MHB-68: verified ratings and reviews.
* Unified operational task queue and general finance overview are out of scope for the current demo phase.
  System settings, user administration, refunds, and violations are redirects or absent.

## Visible surface review

| Surface | What is credible now | Missing or misleading behavior |
|---|---|---|
| Public | Landing, catalog search/filter, course detail, cart and Google sign-in have real routes | Teacher names are not yet navigable; rating/review values must stay hidden until MHB-67/68; no dead violation control |
| Student | Dashboard, My Learning, lesson blocks, progress, quiz, flashcards, writing, final test, certificate, profile, wishlist, purchase history and notifications are represented | Refund, review, violation report and Student wallet claims are missing or out of scope; session/redirect behavior requires deployed MHB-54 evidence |
| Teacher | Dashboard, course list/builder, KYC, writing feedback, profile and revenue-wallet paths exist | UC-24 analytics is missing; student-question/review counters are unsupported; suspension/reinstatement needs the linked trust lifecycle |
| Course Manager | Course approval and KYC APIs/screens exist | KYC queue semantics were wrong; unified task queue is out of scope |
| Finance Manager | Withdrawal/payout components and RBAC paths exist | Refund queue/decision and a coherent finance overview remain incomplete |
| System Admin | Authentication shell and role model exist | Settings, user/role management and audit viewer are incomplete; visible routes currently redirect |

For every visible control, the demo rule should be: it performs a complete,
authorized workflow against real data, or it is removed/disabled with honest
scope. Placeholder destinations, fabricated counts, and controls that end at a
dead route are not council-ready.

## KYC and trust-state policy

The canonical flow must be:

1. Valid provider and registry match: auto-approve and grant Teacher role.
2. Deterministic mismatch or bad evidence: reject the operation and ask the
   applicant to retry; do not create admin work.
3. Provider uncertainty or risk signal: create a PENDING exception requiring a
   Course Manager decision.
4. Complaint against an approved teacher: create a violation/investigation
   case linked to immutable KYC evidence. Do not silently rewrite history.
5. Confirmed severe issue: revoke Teacher capability, stop new sales by moving
   PUBLISHED/APPROVED/PENDING courses to FORCED_DRAFT, freeze withdrawals, log
   the impact, and notify the teacher.
6. Existing ACTIVE/COMPLETED enrollments remain accessible unless a separate
   legal/safety decision removes content. Refunds are not automatic merely
   because a teacher is suspended.
7. Reinstatement and permanent removal require explicit decisions and audit
   evidence. These are moderation states, not overloaded KYC submission states.

## Context diagram corrections

The current context diagram should be redrawn before submission:

* Replace generic Admin with System Admin, Course Manager, and Finance Manager,
  or show Admin as a generalized actor with those specializations.
* Remove MockAPI. A mock is an implementation detail, not an external production
  actor.
* Rename AI Assessment/Evaluation to AI Learning Suggestions/Chat Response.
* Show VNPT eKYC and JLPT/National Registry as external verification providers.
* Show Payment Gateway data flows separately for payment confirmation, refund,
  and payout.
* Show an external notification provider only if email/SMS is integrated.
  In-app notifications belong inside the ManabiHub boundary.
* Add the actual media/object storage provider if production uses one.
* Remove crossed arrows and ensure every flow has an unambiguous direction.
* Keep the diagram at context level. Internal tables, queues, and mock adapters
  belong in lower-level architecture diagrams.

## SRS corrections

Report 3 currently overclaims or contradicts the product:

* My Learning claims wallet, streaks, achievements, and gamification not present
  in the current dashboard.
* Teacher Dashboard claims revenue, ratings, questions, and reviews while the
  implemented dashboard reports course statuses and recent courses.
* Course Builder is described as a student-submission review screen.
* Writing Submission still says AI-assisted grading.
* Task Queue is out of scope for the demo. Operational tasks are handled directly via their specific domain routes.
* Cloudinary, email delivery, WebSocket/STOMP, long-polling fallback, placement
  test, deferral, AI content generation, coupon/referral attribution, and
  Student AI-credit top-up are not supported by current code.
* BR-REP-03 assigns violation resolution to System Admin while the actor model
  and Jira assign it to Course Manager.
* BR-PAYOUT-01 assigns payout to System Admin while Jira and RBAC assign it to
  Finance Manager.
* KYC messages and UC-28 describe universal manual approval despite strict
  auto-verification.
* UC-01 contains duplicated description, trigger, precondition, and
  postcondition text.

SRS claims should be reduced to implemented MVP scope or backed by Iteration 5
stories and test evidence.

## SDS and database corrections

Report 4 is materially out of sync with Flyway and JPA:

* The cover date says August 2019.
* It lists `users`, `admin_accounts`, `sessions`, and
  `system_configurations`; actual names include `app_users`,
  `internal_admin_accounts`, and `system_settings`, with no session table.
* It lists split content tables (`video_blocks`, `text_blocks`, `quiz_blocks`,
  `flashcard_sets`, `flashcards`, `writing_assignments`) while current content
  is primarily stored in `course_lesson_blocks`.
* It lists `certificate_verification_results`,
  `course_validation_results`, `learning_progress`, `reviews`, and
  `reconciliation_logs` as implemented tables even though the current model
  differs or the capability is not implemented.
* Current Flyway contains legacy `lessons`, `lesson_blocks`, and
  `lesson_progress` alongside newer `course_lesson_blocks` and
  `lesson_block_progress`; ownership and retirement policy is undocumented.
* Multiple JPA entities map the same physical tables (`app_users`,
  `internal_admin_accounts`, and `wallets`), increasing consistency risk.
* Package descriptions include `content`, `marketplace`, `refund`,
  `moderation`, `admin`, and `file` modules that do not exist as described.
* Deployment architecture does not document the actual AWS Amplify, API
  Gateway, Elastic Beanstalk, PostgreSQL, OAuth, VNPT, VNPay, and AI topology.
* Detailed design still contains placeholder headings.

The SDS must be generated from the actual Flyway/JPA/API inventory, not from a
planned schema.

## Council-ready answers

* Why can a KYC-approved teacher appear in Admin? Only when an automated
  exception or a linked trust-and-safety investigation requires action.
* What happens to courses after teacher suspension? New sales stop; current
  learner access remains; legal/safety removal is a separate moderation action.
* What happens to money? Pending and available funds are frozen from payout
  while evidence is reviewed. Refund and escrow adjustments use their own
  auditable workflows.
* Why does a teacher need a public profile? Course reputation needs a
  navigable, privacy-safe identity and a list of that teacher's published
  courses.
* Are ratings trustworthy? They must be tied to eligible enrollment, unique per
  student/course, editable by the owner, and moderation-aware.
* Is AI grading students? No. AI produces preliminary learning suggestions;
  Teacher feedback remains authoritative where formal review is required.

## Release priority

P0 before council demo:

* Correct MHB-13 semantics and trust-state propagation.
* Complete refund consistency or explicitly remove refund from the demo/SRS.
* Complete violation report/moderation or remove dead controls.
* Reconcile SRS/SDS/context diagram with actual scope.
* Run the MHB-54 end-to-end regression script on deployed AWS.

P1 Iteration 5:

* MHB-67 public teacher profile.
* MHB-68 verified ratings/reviews.
* MHB-69 / UC-24 Teacher Course Analytics.
* MHB-52 settings/internal roles and MHB-53 audit viewer.
* MHB-71 duplicate JPA ownership and legacy schema retirement.

## Tracking and review evidence

The audit was converted into actionable project records rather than left as
informal findings:

* MHB-13 was rewritten as KYC exception and trust-case handling and returned to
  Rework.
* MHB-66 was returned to Rework to resolve dead aliases and explicitly remove out-of-scope unified task-queue and finance overview routes from the navigation menu.
* MHB-69 was created for the missing UC-24 Teacher Course Analytics.
* MHB-70 was created for SRS and context-diagram reconciliation.
* MHB-71 was created for Flyway/JPA ownership and legacy learning-schema work.
* MHB-36, MHB-41, MHB-42, MHB-43, MHB-44, MHB-54, MHB-55, and MHB-58 received
  targeted review comments and dependency corrections.
* Anchored review comments were added to the SRS and SDS source files in
  Google Drive.
* PR #97 contains the immediate KYC queue and suspension-propagation
  corrections. Server-side enforcement of a linked trust case before
  post-approval revocation remains part of MHB-13/MHB-43/MHB-44.
