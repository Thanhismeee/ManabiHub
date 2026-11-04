package com.manabihub.common.constants;

/**
 * Centralized message code constants for the ManabiHub API response convention.
 * <p>
 * <b>Naming convention:</b> {@code DOMAIN_ACTION_OR_STATE}
 * <ul>
 *   <li>Domain prefix groups codes by business module.</li>
 *   <li>Suffix describes the outcome or condition.</li>
 *   <li>All codes are UPPER_SNAKE_CASE.</li>
 * </ul>
 * <p>
 * <b>Frontend rule:</b> The frontend must use these codes for i18n display
 * mapping. Never parse raw {@code message} text for branching logic.
 * <p>
 * Codes defined here are the initial skeleton set. Each business module
 * should add its own codes as features are implemented.
 */
public final class MessageCodes {

    private MessageCodes() {
        // Prevent instantiation
    }

    // ──────────────────────────────────────────────
    // COMMON — generic cross-cutting codes
    // ──────────────────────────────────────────────
    public static final String COMMON_SUCCESS = "COMMON_SUCCESS";
    public static final String COMMON_CREATED = "COMMON_CREATED";
    public static final String COMMON_UPDATED = "COMMON_UPDATED";
    public static final String COMMON_DELETED = "COMMON_DELETED";
    public static final String COMMON_BAD_REQUEST = "COMMON_BAD_REQUEST";
    public static final String COMMON_NOT_FOUND = "COMMON_NOT_FOUND";
    public static final String COMMON_METHOD_NOT_ALLOWED = "COMMON_METHOD_NOT_ALLOWED";
    public static final String COMMON_CONFLICT = "COMMON_CONFLICT";
    public static final String COMMON_INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
    public static final String MSG_COM_002 = "MSG-COM-002"; // Required field is empty
    public static final String MSG_COM_004 = "MSG-COM-004";

    // ──────────────────────────────────────────────
    // VALIDATION — input validation errors
    // ──────────────────────────────────────────────
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    // ──────────────────────────────────────────────
    // WEEKLY CHALLENGE — gameplay and administration errors
    // ──────────────────────────────────────────────
    public static final String WEEKLY_CHALLENGE_INVALID = "WEEKLY_CHALLENGE_INVALID";
    public static final String WEEKLY_CHALLENGE_CONFLICT = "WEEKLY_CHALLENGE_CONFLICT";
    public static final String WEEKLY_CHALLENGE_NOT_FOUND = "WEEKLY_CHALLENGE_NOT_FOUND";
    public static final String WEEKLY_CHALLENGE_NOT_AVAILABLE = "WEEKLY_CHALLENGE_NOT_AVAILABLE";
    public static final String WEEKLY_CHALLENGE_ATTEMPT_NOT_FOUND = "WEEKLY_CHALLENGE_ATTEMPT_NOT_FOUND";
    public static final String WEEKLY_CHALLENGE_NOT_PUBLISHED = "WEEKLY_CHALLENGE_NOT_PUBLISHED";
    public static final String WEEKLY_CHALLENGE_NOT_CURRENT_WEEK = "WEEKLY_CHALLENGE_NOT_CURRENT_WEEK";
    public static final String WEEKLY_CHALLENGE_COURSE_MANAGER_REQUIRED = "WEEKLY_CHALLENGE_COURSE_MANAGER_REQUIRED";

    // ──────────────────────────────────────────────
    // REPORT — report/violation errors
    // ──────────────────────────────────────────────
    public static final String MSG_REP_001 = "MSG-REP-001";
    public static final String MSG_REP_002 = "MSG-REP-002";

    // ──────────────────────────────────────────────
    // AUTH — authentication & authorization
    // ──────────────────────────────────────────────
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    /** §5.2 MSG-ADM-006: thao tác yêu cầu quyền System Admin. */
    public static final String MSG_ADM_006 = "MSG-ADM-006";
    /** §5.2 MSG-ADM-007: thao tác yêu cầu quyền Course Manager. */
    public static final String MSG_ADM_007 = "MSG-ADM-007";
    /** §5.2 MSG-ADM-008: thao tác yêu cầu quyền Finance Manager. */
    public static final String MSG_ADM_008 = "MSG-ADM-008";
    /** §5.2 MSG-EXP-001: chứng từ chi phí đã được xác nhận. */
    public static final String MSG_EXP_001 = "MSG-EXP-001";
    /** §5.2 MSG-EXP-002: bắt buộc nhập lý do khi hủy chứng từ chi phí. */
    public static final String MSG_EXP_002 = "MSG-EXP-002";
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
    public static final String AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID";
    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGOUT_SUCCESS = "AUTH_LOGOUT_SUCCESS";
    public static final String ADMIN_SESSION_INVALID = "ADMIN_SESSION_INVALID";
    public static final String ADMIN_PASSWORD_RESET_REQUEST_ACCEPTED =
            "ADMIN_PASSWORD_RESET_REQUEST_ACCEPTED";
    public static final String ADMIN_PASSWORD_RESET_COMPLETED =
            "ADMIN_PASSWORD_RESET_COMPLETED";
    public static final String ADMIN_PASSWORD_RESET_INVALID =
            "ADMIN_PASSWORD_RESET_INVALID";
    public static final String ADMIN_PASSWORD_CHANGED = "ADMIN_PASSWORD_CHANGED";
    public static final String ADMIN_PASSWORD_REUSE_FORBIDDEN =
            "ADMIN_PASSWORD_REUSE_FORBIDDEN";
    public static final String ADMIN_CURRENT_PASSWORD_INVALID =
            "ADMIN_CURRENT_PASSWORD_INVALID";
    public static final String ADMIN_PASSWORD_CHANGE_RATE_LIMITED =
            "ADMIN_PASSWORD_CHANGE_RATE_LIMITED";
    public static final String MSG_AUTH_007 = "MSG-AUTH-007"; // Admin login failed
    public static final String MSG_AUTH_008 = "MSG-AUTH-008"; // Admin account locked
    public static final String MSG_AUTH_009 = "MSG-AUTH-009";
    public static final String AUTH_SESSION_INVALID = "AUTH_SESSION_INVALID";
    public static final String AUTH_SESSION_REVOKED = "AUTH_SESSION_REVOKED";
    public static final String PUBLIC_DEVICE_LIMIT_REACHED = "PUBLIC_DEVICE_LIMIT_REACHED";
    public static final String ACCOUNT_IN_USE_ELSEWHERE = "ACCOUNT_IN_USE_ELSEWHERE";

    // ──────────────────────────────────────────────
    // PROFILE — user profile management
    // ──────────────────────────────────────────────
    public static final String MSG_PRO_001 = "MSG-PRO-001";
    public static final String MSG_PRO_002 = "MSG-PRO-002";


    // ──────────────────────────────────────────────
    // KYC — know your customer / teacher verification
    // ──────────────────────────────────────────────
    public static final String KYC_NOT_SUBMITTED = "KYC_NOT_SUBMITTED";
    public static final String KYC_PENDING = "KYC_PENDING";
    public static final String KYC_APPROVED = "KYC_APPROVED";
    public static final String KYC_REJECTED = "KYC_REJECTED";
    public static final String KYC_NOT_APPROVED = "KYC_NOT_APPROVED";
    public static final String KYC_RESUBMISSION_REQUIRED = "KYC_RESUBMISSION_REQUIRED";
    public static final String KYC_TEACHER_NOT_FOUND = "KYC_TEACHER_NOT_FOUND";
    public static final String KYC_ALREADY_PENDING = "KYC_ALREADY_PENDING";
    public static final String KYC_ALREADY_APPROVED = "KYC_ALREADY_APPROVED";
    public static final String KYC_SUBMISSION_NOT_ALLOWED = "KYC_SUBMISSION_NOT_ALLOWED";
    public static final String KYC_CERTIFICATE_ALREADY_CLAIMED = "KYC_CERTIFICATE_ALREADY_CLAIMED";
    public static final String KYC_CERTIFICATE_OCR_MISMATCH = "KYC_CERTIFICATE_OCR_MISMATCH";
    public static final String KYC_TRUST_CASE_REQUIRED = "KYC_TRUST_CASE_REQUIRED";
    public static final String MSG_KYC_002 = "MSG-KYC-002";
    public static final String MSG_KYC_003 = "MSG-KYC-003";
    public static final String MSG_KYC_006 = "MSG-KYC-006";
    public static final String MSG_KYC_008 = "MSG-KYC-008";
    public static final String MSG_KYC_009 = "MSG-KYC-009";
    public static final String MSG_KYC_010 = "MSG-KYC-010";
    public static final String MSG_IDV_002 = "MSG-IDV-002";
    public static final String MSG_ADM_002 = "MSG-ADM-002";

    // ──────────────────────────────────────────────
    // COURSE — course management
    // ──────────────────────────────────────────────
    public static final String COURSE_NOT_FOUND = "COURSE_NOT_FOUND";
    public static final String COURSE_CREATED = "COURSE_CREATED";
    public static final String COURSE_UPDATED = "COURSE_UPDATED";
    public static final String COURSE_DELETED = "COURSE_DELETED";
    public static final String COURSE_NOT_EDITABLE = "COURSE_NOT_EDITABLE";
    public static final String COURSE_NOT_PUBLISHED = "COURSE_NOT_PUBLISHED";
    public static final String COURSE_ALREADY_PUBLISHED = "COURSE_ALREADY_PUBLISHED";
    public static final String MSG_PROD_001 = "MSG-PROD-001";
    public static final String MSG_PROD_002 = "MSG-PROD-002";
    public static final String MSG_COURSE_001 = "MSG-COURSE-001";
    public static final String MSG_COURSE_002 = "MSG-COURSE-002";
    public static final String MSG_COURSE_003 = "MSG-COURSE-003";
    public static final String MSG_COURSE_004 = "MSG-COURSE-004";
    public static final String MSG_COURSE_005 = "MSG-COURSE-005";
    public static final String MSG_COURSE_006 = "MSG-COURSE-006";
    public static final String MSG_COURSE_007 = "MSG-COURSE-007";
    public static final String MSG_COURSE_008 = "MSG-COURSE-008";
    public static final String MSG_COURSE_009 = "MSG-COURSE-009";
    public static final String MSG_COURSE_010 = "MSG-COURSE-010";
    public static final String MSG_COURSE_011 = "MSG-COURSE-011";
    public static final String MSG_COURSE_012 = "MSG-COURSE-012";
    public static final String MSG_COURSE_013 = "MSG-COURSE-013";
    public static final String MSG_COURSE_014 = "MSG-COURSE-014";
    public static final String MSG_COURSE_015 = "MSG-COURSE-015";
    public static final String MSG_COURSE_016 = "MSG-COURSE-016";
    public static final String MSG_COURSE_017 = "MSG-COURSE-017";
    public static final String MSG_COURSE_018 = "MSG-COURSE-018";
    public static final String MSG_COURSE_019 = "MSG-COURSE-019";
    public static final String MSG_GOAL_001 = "MSG-GOAL-001";
    public static final String MSG_GOAL_002 = "MSG-GOAL-002";
    public static final String MSG_GOAL_003 = "MSG-GOAL-003";
    public static final String MSG_GOAL_004 = "MSG-GOAL-004";
    public static final String MSG_CATALOG_001 = "MSG-CATALOG-001";
    public static final String MSG_WRITE_005 = "MSG-WRITE-005";
    public static final String WRITING_SUBMISSION_NOT_FOUND = "WRITING_SUBMISSION_NOT_FOUND";
    public static final String TEACHER_FEEDBACK_SUBMITTED = "TEACHER_FEEDBACK_SUBMITTED";
    // ──────────────────────────────────────────────
    // CONTENT — lesson / content management
    // ──────────────────────────────────────────────
    public static final String CONTENT_NOT_FOUND = "CONTENT_NOT_FOUND";
    public static final String CONTENT_CREATED = "CONTENT_CREATED";
    public static final String CONTENT_UPDATED = "CONTENT_UPDATED";
    public static final String CONTENT_DELETED = "CONTENT_DELETED";

    // ──────────────────────────────────────────────
    // FINAL_TEST — final test management
    // ──────────────────────────────────────────────
    public static final String FINAL_TEST_NOT_FOUND = "FINAL_TEST_NOT_FOUND";
    public static final String FINAL_TEST_CREATED = "FINAL_TEST_CREATED";
    public static final String FINAL_TEST_ALREADY_PASSED = "FINAL_TEST_ALREADY_PASSED";
    public static final String MSG_FTEST_001 = "MSG-FTEST-001";
    public static final String MSG_FTEST_002 = "MSG-FTEST-002";
    public static final String MSG_FTEST_003 = "MSG-FTEST-003";
    public static final String MSG_FTEST_004 = "MSG-FTEST-004";

    // ──────────────────────────────────────────────
    // LEARNING — student learning progress
    // ──────────────────────────────────────────────
    public static final String LEARNING_NOT_ENROLLED = "LEARNING_NOT_ENROLLED";
    public static final String LEARNING_ACCESS_EXPIRED = "LEARNING_ACCESS_EXPIRED";
    public static final String LEARNING_REFUND_PENDING = "LEARNING_REFUND_PENDING";
    public static final String LEARNING_ALREADY_ENROLLED = "LEARNING_ALREADY_ENROLLED";
    public static final String LEARNING_PROGRESS_UPDATED = "LEARNING_PROGRESS_UPDATED";
    public static final String LEARNING_LESSON_COMPLETED = "LEARNING_LESSON_COMPLETED";
    public static final String LEARNING_LESSON_CONTENT_UNAVAILABLE = "LEARNING_LESSON_CONTENT_UNAVAILABLE";
    public static final String LEARNING_INVALID_BLOCK_TYPE = "LEARNING_INVALID_BLOCK_TYPE";
    public static final String LEARNING_INVALID_VIDEO_POSITION = "LEARNING_INVALID_VIDEO_POSITION";
    public static final String LEARNING_INVALID_FLASHCARD_INDEX = "LEARNING_INVALID_FLASHCARD_INDEX";
    public static final String LEARNING_INVALID_QUIZ_ANSWERS = "LEARNING_INVALID_QUIZ_ANSWERS";
    public static final String LEARNING_QUIZ_SUBMITTED = "LEARNING_QUIZ_SUBMITTED";
    public static final String LEARNING_FINAL_TEST_NOT_ELIGIBLE = "LEARNING_FINAL_TEST_NOT_ELIGIBLE";
    public static final String LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND = "LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND";
    public static final String LEARNING_FINAL_TEST_ATTEMPT_EXPIRED = "LEARNING_FINAL_TEST_ATTEMPT_EXPIRED";
    public static final String LEARNING_FINAL_TEST_STARTED = "LEARNING_FINAL_TEST_STARTED";
    public static final String LEARNING_FINAL_TEST_SUBMITTED = "LEARNING_FINAL_TEST_SUBMITTED";
    public static final String LEARNING_CERTIFICATE_NOT_FOUND = "LEARNING_CERTIFICATE_NOT_FOUND";
    public static final String LEARNING_CERTIFICATE_NOT_ELIGIBLE = "LEARNING_CERTIFICATE_NOT_ELIGIBLE";
    public static final String LEARNING_CERTIFICATE_ISSUED = "LEARNING_CERTIFICATE_ISSUED";
    public static final String LEARNING_WISHLIST_ADDED = "LEARNING_WISHLIST_ADDED";
    public static final String LEARNING_WISHLIST_REMOVED = "LEARNING_WISHLIST_REMOVED";
    public static final String LEARNING_WISHLIST_DUPLICATE = "LEARNING_WISHLIST_DUPLICATE";
    public static final String LEARNING_WISHLIST_ITEM_NOT_FOUND = "LEARNING_WISHLIST_ITEM_NOT_FOUND";
    public static final String COURSE_REVIEW_SAVED = "COURSE_REVIEW_SAVED";
    public static final String COURSE_REVIEW_NOT_ELIGIBLE = "COURSE_REVIEW_NOT_ELIGIBLE";
    public static final String COURSE_REVIEW_INVALID = "COURSE_REVIEW_INVALID";

    // SYSTEM CONFIGURATION / INTERNAL ADMIN RBAC — UC-31
    public static final String SYSTEM_SETTING_UPDATED = "SYSTEM_SETTING_UPDATED";
    public static final String SYSTEM_SETTING_INVALID = "SYSTEM_SETTING_INVALID";
    public static final String SYSTEM_SETTING_NOT_EDITABLE = "SYSTEM_SETTING_NOT_EDITABLE";
    public static final String SYSTEM_ADMIN_REQUIRED = "SYSTEM_ADMIN_REQUIRED";
    public static final String INTERNAL_ROLE_UPDATED = "INTERNAL_ROLE_UPDATED";
    public static final String INTERNAL_ROLE_INVALID = "INTERNAL_ROLE_INVALID";
    public static final String INTERNAL_ADMIN_STATUS_UPDATED =
            "INTERNAL_ADMIN_STATUS_UPDATED";
    public static final String INTERNAL_ADMIN_STATUS_INVALID =
            "INTERNAL_ADMIN_STATUS_INVALID";
    public static final String INTERNAL_ADMIN_SELF_DISABLE_FORBIDDEN =
            "INTERNAL_ADMIN_SELF_DISABLE_FORBIDDEN";
    public static final String INTERNAL_ROLE_SELF_ASSIGNMENT_FORBIDDEN =
            "INTERNAL_ROLE_SELF_ASSIGNMENT_FORBIDDEN";
    public static final String LAST_SYSTEM_ADMIN_REQUIRED = "LAST_SYSTEM_ADMIN_REQUIRED";
    public static final String ADMIN_SESSION_STALE = "ADMIN_SESSION_STALE";
    public static final String INTERNAL_ADMIN_INVITATION_CREATED =
            "INTERNAL_ADMIN_INVITATION_CREATED";
    public static final String INTERNAL_ADMIN_INVITATION_RESENT =
            "INTERNAL_ADMIN_INVITATION_RESENT";
    public static final String INTERNAL_ADMIN_INVITATION_CONFLICT =
            "INTERNAL_ADMIN_INVITATION_CONFLICT";
    public static final String INTERNAL_ADMIN_INVITATION_INVALID =
            "INTERNAL_ADMIN_INVITATION_INVALID";
    public static final String INTERNAL_ADMIN_PASSWORD_INVALID =
            "INTERNAL_ADMIN_PASSWORD_INVALID";
    public static final String INTERNAL_ADMIN_PASSWORD_SET =
            "INTERNAL_ADMIN_PASSWORD_SET";
    public static final String LEARNING_STUDENT_PROFILE_NOT_FOUND = "LEARNING_STUDENT_PROFILE_NOT_FOUND";

    // ──────────────────────────────────────────────
    // AI — AI-related operations
    // ──────────────────────────────────────────────
    public static final String AI_NOT_AVAILABLE = "AI_NOT_AVAILABLE";
    public static final String AI_NOT_AVAILABLE_FOR_COURSE = "AI_NOT_AVAILABLE_FOR_COURSE";
    public static final String AI_GENERATION_FAILED = "AI_GENERATION_FAILED";
    public static final String MSG_AI_001 = "MSG-AI-001"; // AI quota or rate limit reached
    public static final String MSG_AI_002 = "MSG-AI-002"; // AI provider temporarily unavailable
    public static final String MSG_AI_005 = "MSG-AI-005"; // Unsafe or out-of-scope AI request
    public static final String MSG_AI_007 = "MSG-AI-007"; // AI content is non-official guidance
    public static final String MSG_AI_008 = "MSG-AI-008"; // AI is unavailable for this course or request

    // ──────────────────────────────────────────────
    // ORDER — course purchase orders (UC-08)
    // ──────────────────────────────────────────────
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_RETRIEVED = "ORDER_RETRIEVED";
    public static final String ORDER_COURSE_NOT_PUBLISHED = "ORDER_COURSE_NOT_PUBLISHED";
    public static final String ORDER_COURSE_ACCESS_ENDED = "ORDER_COURSE_ACCESS_ENDED";
    public static final String ORDER_ALREADY_ENROLLED = "ORDER_ALREADY_ENROLLED";
    public static final String ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    // ──────────────────────────────────────────────
    // PAYMENT — payment processing
    // ──────────────────────────────────────────────
    public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_WEBHOOK_INVALID = "PAYMENT_WEBHOOK_INVALID";
    public static final String PAYMENT_WEBHOOK_PROCESSED = "PAYMENT_WEBHOOK_PROCESSED";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT_INITIATED = "PAYMENT_INITIATED";

    // Canonical payment-result codes defined by the SRS (UC-08, message catalog).
    public static final String MSG_PAY_001 = "MSG-PAY-001"; // Payment pending — awaiting confirmation
    public static final String MSG_PAY_002 = "MSG-PAY-002"; // Payment successful — product unlocked
    public static final String MSG_PAY_003 = "MSG-PAY-003"; // Payment failed
    public static final String MSG_PAY_004 = "MSG-PAY-004"; // Invalid/unverifiable payment webhook
    public static final String MSG_PAY_005 = "MSG-PAY-005"; // Duplicate payment (already processed)

    // ──────────────────────────────────────────────
    // WALLET — wallet operations
    // ──────────────────────────────────────────────
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String WALLET_INSUFFICIENT_BALANCE = "WALLET_INSUFFICIENT_BALANCE";
    // SRS Report 3 §5.2 (rows 87-89) — student wallet withdrawal, UC-43
    public static final String MSG_WALLET_002 = "MSG-WALLET-002";
    public static final String MSG_WALLET_003 = "MSG-WALLET-003";
    public static final String MSG_WALLET_004 = "MSG-WALLET-004";
    public static final String MSG_WALLET_005 = "MSG-WALLET-005";
    public static final String WALLET_TRANSACTION_NOT_FOUND = "WALLET_TRANSACTION_NOT_FOUND";
    public static final String WALLET_HISTORY_UNAVAILABLE = "WALLET_HISTORY_UNAVAILABLE";

    // ──────────────────────────────────────────────
    // REFUND — refund processing
    // ──────────────────────────────────────────────
    public static final String REFUND_NOT_ELIGIBLE = "REFUND_NOT_ELIGIBLE";
    public static final String REFUND_WINDOW_EXPIRED = "REFUND_WINDOW_EXPIRED";
    public static final String REFUND_PROGRESS_LIMIT_REACHED = "REFUND_PROGRESS_LIMIT_REACHED";
    public static final String REFUND_PROTECTED_MATERIALS_DOWNLOADED = "REFUND_PROTECTED_MATERIALS_DOWNLOADED";
    public static final String REFUND_ENROLLMENT_MISSING = "REFUND_ENROLLMENT_MISSING";
    public static final String REFUND_ACTIVE_REQUEST_EXISTS = "REFUND_ACTIVE_REQUEST_EXISTS";
    public static final String REFUND_REQUEST_NOT_FOUND = "REFUND_REQUEST_NOT_FOUND";
    public static final String REFUND_CANCELLATION_NOT_ALLOWED = "REFUND_CANCELLATION_NOT_ALLOWED";
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String REFUND_PROCESSED = "REFUND_PROCESSED";
    public static final String FINANCIAL_INTEGRITY_VIOLATION = "FINANCIAL_INTEGRITY_VIOLATION";
    public static final String REFUND_RECONCILIATION_REQUIRED = "REFUND_RECONCILIATION_REQUIRED";

    // ──────────────────────────────────────────────
    // PAYOUT — teacher payout
    // ──────────────────────────────────────────────
    public static final String PAYOUT_NOT_ELIGIBLE = "PAYOUT_NOT_ELIGIBLE";
    public static final String PAYOUT_REQUESTED = "PAYOUT_REQUESTED";
    public static final String PAYOUT_PROCESSED = "PAYOUT_PROCESSED";
    public static final String PAYOUT_WITHDRAWAL_REQUEST_CREATED = "PAYOUT_WITHDRAWAL_REQUEST_CREATED";
    public static final String PAYOUT_WITHDRAWAL_NOT_FOUND = "PAYOUT_WITHDRAWAL_NOT_FOUND";
    public static final String PAYOUT_AMOUNT_BELOW_MINIMUM = "PAYOUT_AMOUNT_BELOW_MINIMUM";
    public static final String PAYOUT_PENDING_REQUEST_EXISTS = "PAYOUT_PENDING_REQUEST_EXISTS";
    public static final String PAYOUT_MONTHLY_LIMIT_EXCEEDED = "PAYOUT_MONTHLY_LIMIT_EXCEEDED";
    public static final String PAYOUT_BANK_ACCOUNT_REQUIRED = "PAYOUT_BANK_ACCOUNT_REQUIRED";
    public static final String PAYOUT_PHONE_VERIFICATION_REQUIRED = "PAYOUT_PHONE_VERIFICATION_REQUIRED";
    public static final String PAYOUT_BANK_QR_REQUIRED = "PAYOUT_BANK_QR_REQUIRED";
    public static final String PAYOUT_BANK_QR_INVALID = "PAYOUT_BANK_QR_INVALID";
    public static final String PAYOUT_EMAIL_REQUIRED = "PAYOUT_EMAIL_REQUIRED";
    public static final String PAYOUT_INVALID_OTP = "PAYOUT_INVALID_OTP";
    public static final String PAYOUT_OTP_RATE_LIMITED = "PAYOUT_OTP_RATE_LIMITED";
    public static final String PAYOUT_PHONE_AUTH_NOT_CONFIGURED = "PAYOUT_PHONE_AUTH_NOT_CONFIGURED";
    public static final String PHONE_VERIFICATION_OTP_SENT = "PHONE_VERIFICATION_OTP_SENT";
    public static final String PHONE_VERIFICATION_INVALID_OTP = "PHONE_VERIFICATION_INVALID_OTP";
    public static final String PHONE_VERIFICATION_RATE_LIMITED = "PHONE_VERIFICATION_RATE_LIMITED";
    public static final String PHONE_VERIFICATION_ALREADY_VERIFIED = "PHONE_VERIFICATION_ALREADY_VERIFIED";
    public static final String PHONE_VERIFICATION_ALREADY_IN_USE = "PHONE_VERIFICATION_ALREADY_IN_USE";
    public static final String PHONE_VERIFICATION_SMS_NOT_CONFIGURED = "PHONE_VERIFICATION_SMS_NOT_CONFIGURED";
    public static final String PHONE_VERIFICATION_SMS_DELIVERY_FAILED = "PHONE_VERIFICATION_SMS_DELIVERY_FAILED";
    public static final String PHONE_VERIFICATION_REQUIRED = "PHONE_VERIFICATION_REQUIRED";
    public static final String PAYOUT_NOT_FOUND = "PAYOUT_NOT_FOUND";
    public static final String PAYOUT_INVALID_STATUS = "PAYOUT_INVALID_STATUS";
    public static final String PAYOUT_SETTLEMENT_PROCESSING = "PAYOUT_SETTLEMENT_PROCESSING";
    public static final String PAYOUT_SETTLEMENT_COMPLETED = "PAYOUT_SETTLEMENT_COMPLETED";
    public static final String PAYOUT_REJECTED = "PAYOUT_REJECTED";
    public static final String PAYOUT_PERMISSION_DENIED = "PAYOUT_PERMISSION_DENIED";
    public static final String PAYOUT_RECONCILIATION_MISMATCH = "PAYOUT_RECONCILIATION_MISMATCH";
    public static final String PAYOUT_BALANCE_FROZEN = "PAYOUT_BALANCE_FROZEN";
    public static final String PAYOUT_INSUFFICIENT_RESERVED_BALANCE = "PAYOUT_INSUFFICIENT_RESERVED_BALANCE";
    public static final String PAYOUT_GATEWAY_FAILED = "PAYOUT_GATEWAY_FAILED";
    public static final String PAYOUT_PENDING_RETRY = "PAYOUT_PENDING_RETRY";
    public static final String PAYOUT_DUPLICATE_SETTLEMENT = "PAYOUT_DUPLICATE_SETTLEMENT";
    public static final String PAYOUT_MANUAL_REFERENCE_DUPLICATE = "PAYOUT_MANUAL_REFERENCE_DUPLICATE";
    public static final String PAYOUT_DECISION_REASON_REQUIRED = "PAYOUT_DECISION_REASON_REQUIRED";
    public static final String PAYOUT_MANUAL_AMOUNT_MISMATCH = "PAYOUT_MANUAL_AMOUNT_MISMATCH";
    public static final String PAYOUT_PROOF_INVALID = "PAYOUT_PROOF_INVALID";
    public static final String PAYOUT_RETRY_NOT_ALLOWED = "PAYOUT_RETRY_NOT_ALLOWED";
    public static final String PAYOUT_PROOF_NOT_FOUND = "PAYOUT_PROOF_NOT_FOUND";
    public static final String PAYOUT_BANK_OWNERSHIP_REQUIRED = "PAYOUT_BANK_OWNERSHIP_REQUIRED";
    public static final String PAYOUT_BANK_VERIFICATION_UNAVAILABLE = "PAYOUT_BANK_VERIFICATION_UNAVAILABLE";
    public static final String MSG_ADM_004 = "MSG-ADM-004";
    public static final String MSG_ADM_005 = "MSG-ADM-005";

    // ──────────────────────────────────────────────
    // WALLET — wallet operations
    // ──────────────────────────────────────────────
    public static final String WALLET_FROZEN = "WALLET_FROZEN";
    public static final String WALLET_RESERVATION_FAILED = "WALLET_RESERVATION_FAILED";

    // ──────────────────────────────────────────────
    // ADMIN — admin operations
    // ──────────────────────────────────────────────
    public static final String ADMIN_ACTION_SUCCESS = "ADMIN_ACTION_SUCCESS";
    public static final String ADMIN_ACTION_FORBIDDEN = "ADMIN_ACTION_FORBIDDEN";
    public static final String COURSE_MANAGER_REQUIRED = "COURSE_MANAGER_REQUIRED";
    public static final String ADMIN_PERMISSION_DENIED = "ADMIN_PERMISSION_DENIED";

    // Moderation specific codes
    public static final String MODERATION_REPORT_NOT_FOUND = "MODERATION_REPORT_NOT_FOUND";
    public static final String MODERATION_INVALID_STATUS = "MODERATION_INVALID_STATUS";
    public static final String MODERATION_ALREADY_RESOLVED = "MODERATION_ALREADY_RESOLVED";
    public static final String MODERATION_DECISION_NOTE_REQUIRED = "MODERATION_DECISION_NOTE_REQUIRED";
    public static final String MODERATION_ACTION_REQUIRED = "MODERATION_ACTION_REQUIRED";
    public static final String MODERATION_TARGET_NOT_FOUND = "MODERATION_TARGET_NOT_FOUND";
    public static final String MODERATION_PENDING_EVIDENCE = "MODERATION_PENDING_EVIDENCE";
    public static final String MODERATION_REPORT_DISMISSED = "MODERATION_REPORT_DISMISSED";
    public static final String MODERATION_CONCURRENT_UPDATE = "MODERATION_CONCURRENT_UPDATE";
    public static final String MODERATION_SEVERE_PERMISSION_REQUIRED = "MODERATION_SEVERE_PERMISSION_REQUIRED";
    public static final String MODERATION_CONTENT_PERMISSION_REQUIRED = "MODERATION_CONTENT_PERMISSION_REQUIRED";
    public static final String MODERATION_INVALID_ACTION = "MODERATION_INVALID_ACTION";
    public static final String MODERATION_INVALID_TRANSITION = "MODERATION_INVALID_TRANSITION";
    public static final String AUTH_ACCOUNT_RESTRICTED = "AUTH_ACCOUNT_RESTRICTED";
    public static final String MSG_ADM_003 = "MSG-ADM-003"; // Moderation action applied

    // ──────────────────────────────────────────────
    // NOTIFICATION — notification system
    // ──────────────────────────────────────────────
    public static final String NOTIFICATION_SENT = "NOTIFICATION_SENT";
    public static final String NOTIFICATION_FAILED = "NOTIFICATION_FAILED";
    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";

    // ──────────────────────────────────────────────
    // OVERSIGHT — System Admin hậu kiểm quyết định của manager
    // ──────────────────────────────────────────────
    /** §5.2 MSG-OVS-001: đã gửi cảnh báo hậu kiểm tới chủ quyết định. */
    public static final String MSG_OVS_001 = "MSG-OVS-001";

    // ──────────────────────────────────────────────
    // SYSTEM — system-level codes
    // ──────────────────────────────────────────────
    public static final String SYSTEM_MAINTENANCE = "SYSTEM_MAINTENANCE";
    public static final String SYSTEM_RATE_LIMITED = "SYSTEM_RATE_LIMITED";

}
