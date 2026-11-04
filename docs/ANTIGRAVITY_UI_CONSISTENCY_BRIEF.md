# Antigravity Brief: Product UI Consistency and Workflow Completion

## Working Agreement

Start this work only after the admin-auth PR is merged into `develop`.

For every PR:

1. Pull `origin/develop` with `--ff-only`.
2. Create one feature branch for one scope below.
3. Do not use or modify another agent's active branch/worktree.
4. Preserve the current landing page and student experience as the visual
   baseline. Do not redesign them.
5. Use live backend data. Do not add demo values, placeholder actions, dead
   routes, fake counts, or client-only business decisions.
6. Stop after push and PR creation. Đức Long and Codex review and merge.

Each PR must pass:

```text
frontend: npm run lint
frontend: npm run build
backend:  ./mvnw clean test (when backend changes)
repo:     git diff --check origin/develop...HEAD
```

Browser QA is required at `1440x900`, `1024x768`, and `390x844`. Check keyboard
navigation, loading, empty, error, success, disabled, and permission-denied states.

## Shared Rules

- Use the repository MUI theme and Lucide/MUI icons already installed.
- Use an icon for familiar actions; include a tooltip when its meaning is not
  obvious.
- Keep cards at 8px radius or less. Do not place cards inside cards.
- Operational screens should be dense, quiet, and scan-friendly.
- Use page-level sections instead of floating decorative panels.
- Do not scale font size with viewport width or use negative letter spacing.
- No purple/blue gradient-dominated screens, decorative orbs, or placeholder
  illustrations.
- Buttons and status actions must not move layout when loading.
- Mobile drawers are temporary overlays and close after navigation. Desktop
  drawers collapse to icons and persist the preference.
- Every API-backed screen needs a stable skeleton/loading state, actionable error
  state, true empty state, and retry where safe.
- Status labels and available actions come from backend state. Never infer a
  state transition from button text or stale local state.
- Destructive/financial/approval actions require confirmation, disable while in
  flight, and handle idempotent retry.
- Public links, teacher links, Help Center links, footer links, and breadcrumbs
  must resolve to real routes.

## PR 1: Shared Design Foundation

Branch suggestion: `feature/MHB-ui-shared-foundation`

Scope:

- Extract common page title, breadcrumb, loading, error, empty, confirmation, and
  status-chip components from existing repeated implementations.
- Standardize content widths, page padding, table toolbar density, dialog width,
  form spacing, focus states, button heights, and responsive breakpoints.
- Consolidate dashboard shell behavior across student, teacher, and admin without
  changing the approved landing/student visual identity.
- Add a real global 404 page with safe navigation to home/default dashboard.
- Replace dead `href="#"` footer links with Help Center, Terms, and Privacy.
- Add component tests for common states and responsive shell behavior.

Do not move feature-specific business logic into shared UI components.

## PR 2: Admin Course Approval Integrity

Branch suggestion: `feature/MHB-admin-course-approval-ux`

Blocking rules:

- Render approval/correction/rejection controls only when backend state permits
  the transition, normally `PENDING`.
- Never show an active decision panel for `APPROVED`, `REJECTED`,
  `FORCED_DRAFT`, or already-decided records.
- Disable every decision action while one request is in flight.
- Prevent double submit and handle idempotency/conflict responses explicitly.
- After success, refetch queue and detail from the server before rendering the
  next state.
- Show reviewer, decision time, reason, validation evidence, teacher identity,
  and course version being reviewed.
- Add tests for every terminal state and rapid double click.

## PR 3: KYC and JLPT Review Workflows

Branch suggestion: `feature/MHB-kyc-review-ux`

Rules:

- Student/teacher OCR output is read-only evidence, not editable profile input.
- CCCD remains VNPT automated verification with duplicate identity protection.
- JLPT extraction may validate readable fields and matching name/date of birth,
  but authenticity remains manual Course Manager review.
- Clearly separate `identity verified`, `certificate pending manual review`,
  `correction required`, `approved`, and `rejected`.
- Tell the candidate that final teacher approval usually takes 1-2 working days,
  excluding weekends and public holidays.
- A pending candidate may use permitted teacher preparation features, but a
  course cannot become publicly visible until teacher eligibility is approved.
- Queue only cases that actually require manual action. Do not place ordinary
  auto-approved CCCD results into an approval queue.
- Mask CCCD and sensitive OCR data in all nonessential views.
- Add duplicate certificate, duplicate identity, stale JWT, wrong-role, and
  terminal-state UI tests.

## PR 4: Wallet, Escrow, and Payout

Branch suggestion: `feature/MHB-wallet-payout-ux`

Scope:

- Use the immutable order snapshot and ledger values from backend APIs.
- Distinguish gross sale, refund reserve, platform commission, teacher net,
  escrow release date, available balance, pending withdrawal, and paid amount.
- Never calculate authoritative money or commission in the browser.
- Explain each number with contextual Help Center links, without exposing private
  platform unit economics to learners.
- Make payout proof, reconciliation, rejection, retry, and manual-transfer states
  explicit.
- Format VND consistently and include transaction timestamps/time zone.
- Test insufficient balance, duplicate request, pending escrow, failed transfer,
  retry, and terminal settlement.

## PR 5: Teacher Operations

Branch suggestion: `feature/MHB-teacher-operations-ux`

Scope:

- Align dashboard, course list, writing review, wallet, KYC, profile, and
  notifications with the shared foundation.
- Use one canonical course status vocabulary from backend enums.
- Provide clear next action only when valid: continue draft, correct submission,
  view published course, or inspect decision.
- Link teacher identity to the public teacher profile and published course list.
- Ensure no draft or unapproved course is reachable from public discovery.
- Verify responsive tables become usable lists or horizontally constrained data
  grids without hiding actions.

## PR 6: Course Builder and Final Test Safety

Branch suggestion: `feature/MHB-course-builder-ux`

Blocking rules:

- A failed GET for final-test configuration must show an error/retry state. It
  must never silently render a blank create form that could overwrite data.
- Clearly distinguish not configured (`404` contract) from load failure.
- Preserve unsaved edits across safe navigation or warn before discarding.
- Keep module/block ordering stable while saving and prevent duplicate actions.
- Validation errors link to the exact module/block/field.
- Keep upload progress, cancellation, retry, and failed asset states visible.
- Test stale version conflict, network error, duplicate submit, and mobile
  builder navigation.

## PR 7: Help Center and Policy UI

Branch suggestion: `feature/MHB-help-center-policy-ux`

Scope:

- Replace remaining shell/placeholder copy with reviewed, versioned policy
  content from the commercial-policy API.
- Keep public explanations clear while separating confidential internal unit
  economics.
- Add effective date, version, owner, and contextual links from KYC, course
  approval, wallet, checkout, refunds, and teacher revenue pages.
- If policy API fails, show a safe unavailable state. Do not fall back to
  hardcoded financial percentages.

## PR 8: Admin Operations and Release Polish

Branch suggestion: `feature/MHB-admin-operations-polish`

Scope:

- Align System Admin account invitation, role assignment, system settings,
  Course Manager queues, Finance queues, notifications, and audit views.
- Never reveal temporary passwords because none should exist.
- Show invitation pending/expired/revoked/accepted state and resend action.
- Require reason text for role changes and destructive decisions.
- Ensure permission-denied states explain the required role without leaking data.
- Remove console errors, dead menu entries, redirects masking missing features,
  and untranslated/mojibake text.
- Run an end-to-end role matrix across public user, student, teacher candidate,
  teacher, Course Manager, Finance Manager, and System Admin.

## Required Review Report

Each PR description must include:

- exact business states covered;
- routes and APIs changed;
- screenshots for all three viewports;
- loading/empty/error/permission/terminal-state evidence;
- automated commands and result counts;
- known limitations;
- conflict notes against active Iteration branches.
