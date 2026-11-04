# MHB-26 Learning Progress and Eligibility

## Progress

- Progress is scoped to the authenticated student's enrollment.
- Every course lesson block contributes equally to progress.
- A course with no lesson blocks is not considered complete.
- The next lesson is the first incomplete block in configured module/block order.

## Final Test indicator

The progress response includes the same server-side Final Test eligibility used by the
assessment start endpoint. It reports configuration, lesson completion, attempts used,
attempts allowed, pass state, and a stable reason code.

## Certificate indicator

The certificate preview is eligible only when all of these rules pass:

1. Every lesson block is complete.
2. Every Writing block is complete.
3. The average of the best score for each Quiz block is at least 85 percent.
4. The Final Test has a passing attempt.

If a course contains no Quiz blocks, the exercise-score rule is not applicable and does
not block eligibility. Missing attempts for a configured Quiz count as zero.

The API returns explicit reason codes so the frontend can explain what remains:
`PROGRESS_INCOMPLETE`, `ASSIGNMENTS_INCOMPLETE`,
`EXERCISE_AVERAGE_BELOW_85`, and `FINAL_TEST_NOT_PASSED`.

MHB-29 must reuse this evaluator when creating a certificate record.
