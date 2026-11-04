package com.manabihub.payout.enums;

/** Local/test-only payout outcomes used while the real PSP is unavailable. */
public enum MockPayoutScenario {
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
