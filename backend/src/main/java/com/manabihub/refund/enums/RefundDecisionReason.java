package com.manabihub.refund.enums;

public enum RefundDecisionReason {
    STANDARD_ELIGIBLE(true, false),
    DUPLICATE_CHARGE(true, false),
    CONFIRMED_PAYMENT_ERROR(true, false),
    PLATFORM_ACCESS_FAILURE(true, false),
    OUTSIDE_REFUND_WINDOW(false, true),
    PROGRESS_LIMIT_REACHED(false, true),
    PROTECTED_CONTENT_CONSUMED(false, true),
    PAYMENT_NOT_CONFIRMED(false, true),
    DUPLICATE_REQUEST(false, true),
    OTHER(false, true);

    private final boolean approvalReason;
    private final boolean rejectionReason;

    RefundDecisionReason(boolean approvalReason, boolean rejectionReason) {
        this.approvalReason = approvalReason;
        this.rejectionReason = rejectionReason;
    }

    public boolean isApprovalReason() {
        return approvalReason;
    }

    public boolean isRejectionReason() {
        return rejectionReason;
    }

    public boolean isManualException() {
        return this == DUPLICATE_CHARGE
                || this == CONFIRMED_PAYMENT_ERROR
                || this == PLATFORM_ACCESS_FAILURE;
    }
}
