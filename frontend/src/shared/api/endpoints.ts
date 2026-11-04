export const ENDPOINTS = {
  NOTIFICATIONS: {
    LIST: '/v1/notifications',
    UNREAD_COUNT: '/v1/notifications/unread-count',
    MARK_READ: (id: string) => `/v1/notifications/${id}/read`,
    MARK_ALL_READ: '/v1/notifications/read-all',
  },
  ADMIN_KYC: {
    QUEUE: '/v1/admin/kyc-requests',
    DETAIL: (id: string) => `/v1/admin/kyc-requests/${id}`,
    REVIEW: (id: string) => `/v1/admin/kyc-requests/${id}/review`,
  },
  ADMIN_TEACHER_QUALIFICATIONS: {
    QUEUE: '/v1/admin/teacher-qualifications',
    DECISION: (id: string) => `/v1/admin/teacher-qualifications/${id}/decision`,
    EVIDENCE: (id: string) => `/v1/admin/teacher-qualifications/${id}/evidence`,
  },
  ADMIN_COURSE_APPROVAL: {
    QUEUE: '/v1/admin/course-approvals',
    DETAIL: (id: string) => `/v1/admin/course-approvals/${id}`,
    REVIEW: (id: string) => `/v1/admin/course-approvals/${id}/review`,
  },
  ADMIN_PAYOUT: {
    QUEUE: '/admin/payouts',
    DETAIL: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}`,
    BANK_QR: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}/bank-qr`,
    RECONCILE: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}/reconcile`,
    APPROVE: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}/approve`,
    MOCK_APPROVE: (withdrawalRequestId: string) =>
      `/admin/payouts/${withdrawalRequestId}/mock-approve`,
    RETRY: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}/retry`,
    REJECT: (withdrawalRequestId: string) => `/admin/payouts/${withdrawalRequestId}/reject`,
    MANUAL_TRANSFER: (withdrawalRequestId: string) =>
      `/admin/payouts/${withdrawalRequestId}/manual-transfer`,
    MANUAL_PROOF: (withdrawalRequestId: string) =>
      `/admin/payouts/${withdrawalRequestId}/manual-transfer/proof`,
  },
  ADMIN_VIOLATIONS: {
    QUEUE: '/v1/admin/violations',
    DETAIL: (reportId: string) => `/v1/admin/violations/${reportId}`,
    RESOLVE: (reportId: string) => `/v1/admin/violations/${reportId}/resolve`,
  },
  ADMIN_FINANCE: {
    REVENUE_DASHBOARD: '/v1/admin/finance/revenue/dashboard',
    EXPENSES: '/v1/admin/finance/expenses',
    EXPENSE: (id: string) => `/v1/admin/finance/expenses/${id}`,
  },
  ADMIN_DECISION_REVIEWS: {
    LIST: '/v1/admin/decision-reviews',
    DETAIL: (auditLogId: string) => `/v1/admin/decision-reviews/${auditLogId}`,
    REVIEWED: (auditLogId: string) => `/v1/admin/decision-reviews/${auditLogId}/reviewed`,
    WARNINGS: (auditLogId: string) => `/v1/admin/decision-reviews/${auditLogId}/warnings`,
  },
  SYSTEM_ADMIN: {
    SETTINGS: '/v1/admin/system-settings',
    SETTING: (key: string) => `/v1/admin/system-settings/${encodeURIComponent(key)}`,
    INTERNAL_ACCOUNTS: '/v1/admin/internal-accounts',
    INTERNAL_ACCOUNT_INVITATIONS: '/v1/admin/internal-accounts/invitations',
    INTERNAL_ACCOUNT_INVITATION_RESEND: (adminId: string) =>
      `/v1/admin/internal-accounts/${adminId}/invitation/resend`,
    INTERNAL_ACCOUNT_ROLE: (adminId: string) =>
      `/v1/admin/internal-accounts/${adminId}/role`,
    INTERNAL_ACCOUNT_STATUS: (adminId: string) =>
      `/v1/admin/internal-accounts/${adminId}/status`,
  },
  ADMIN_OPERATIONS: {
    OVERVIEW: '/v1/admin/operations/overview',
    RUNTIME_CONFIG: '/v1/admin/operations/runtime-config',
    LOGS: '/v1/admin/operations/logs',
  },
  teacherKyc: {
    status: '/v1/teacher/kyc/status',
    identityVerifications: '/v1/teacher/kyc/identity-verifications',
    restartVerification: '/v1/teacher/kyc/restart-verification',
    certificateSubmissions: '/v1/teacher/kyc/certificate-submissions',
    qualifications: '/v1/teacher/kyc/qualifications',
    qualificationEvidence: (id: string) => `/v1/teacher/kyc/qualifications/${id}/evidence`,
  },
  teacherCohorts: {
    list: '/v1/teacher/cohorts',
    detail: (id: string) => `/v1/teacher/cohorts/${id}`,
    status: (id: string) => `/v1/teacher/cohorts/${id}/status`,
    members: (id: string) => `/v1/teacher/cohorts/${id}/members`,
    member: (cohortId: string, memberId: string) =>
      `/v1/teacher/cohorts/${cohortId}/members/${memberId}`,
  },
  profile: {
    student: '/v1/student/profile',
    teacher: '/v1/teacher/profile',
    avatar: '/v1/users/avatar',
    studentPhoneVerificationRequest: '/v1/student/profile/phone-verification/request',
    studentPhoneVerificationConfirm: '/v1/student/profile/phone-verification/confirm',
    teacherPhoneVerificationRequest: '/v1/teacher/profile/phone-verification/request',
    teacherPhoneVerificationConfirm: '/v1/teacher/profile/phone-verification/confirm',
  },
  teacherDashboard: {
    stats: '/v1/teacher/dashboard',
  },
  teacherWallet: {
    detail: '/v1/teacher/wallet',
    revenueSummary: '/v1/teacher/wallet/revenue-summary',
    escrow: '/v1/teacher/wallet/escrow',
    transactions: '/v1/teacher/wallet/transactions',
    transactionDetail: (transactionId: string) =>
      `/v1/teacher/wallet/transactions/${transactionId}`,
    withdrawals: '/v1/teacher/withdrawals',
    withdrawalDetail: (id: string) => `/v1/teacher/withdrawals/${id}`,
    cancelWithdrawal: (id: string) => `/v1/teacher/withdrawals/${id}/cancel`,
    sendWithdrawalOtp: '/v1/teacher/withdrawals/send-otp',
    bankAccounts: '/v1/teacher/withdrawals/bank-accounts',
  },
  teacherWriting: {
    submissions: '/v1/teacher/writing-submissions',
    detail: (submissionId: string) => `/v1/teacher/writing-submissions/${submissionId}`,
    feedback: (submissionId: string) => `/v1/teacher/writing-submissions/${submissionId}/feedback`,
  },
  teacherCourseReviews: {
    reply: (reviewId: string) => `/v1/teacher/course-reviews/${reviewId}/reply`,
  },
  student: {
    dashboardStats: '/v1/student/dashboard/stats',
    courses: '/v1/student/courses',
    wishlist: '/v1/student/wishlist',
    wishlistCourse: (courseId: string) => `/v1/student/wishlist/${courseId}`,
    courseReview: (courseId: string) => `/v1/student/courses/${courseId}/review`,
    wallet: '/v1/student/wallet',
    withdrawals: '/v1/student/withdrawals',
    withdrawalDetail: (id: string) => `/v1/student/withdrawals/${id}`,
    cancelWithdrawal: (id: string) => `/v1/student/withdrawals/${id}/cancel`,
    identityVerificationStatus: '/v1/student/identity-verifications/status',
    identityVerification: '/v1/student/identity-verifications',
    sendWithdrawalOtp: '/v1/student/withdrawals/send-otp',
    withdrawalBankAccounts: '/v1/student/withdrawals/bank-accounts',
    walletTransactions: '/v1/student/wallet/transactions',
    walletTransactionDetail: (transactionId: string) =>
      `/v1/student/wallet/transactions/${transactionId}`,
    weeklyChallenge: '/v1/student/weekly-challenge',
    startWeeklyChallenge: (challengeId: string) => `/v1/student/weekly-challenge/${challengeId}/attempts`,
    matchWeeklyChallenge: (attemptId: string) => `/v1/student/weekly-challenge/attempts/${attemptId}/matches`,
    weeklyChallengeLeaderboard: (challengeId: string) => `/v1/student/weekly-challenge/${challengeId}/leaderboard`,
  },
  ADMIN_WEEKLY_CHALLENGES: {
    LIST: '/v1/admin/weekly-challenges',
    DETAIL: (id: string) => `/v1/admin/weekly-challenges/${id}`,
    PUBLISH: (id: string) => `/v1/admin/weekly-challenges/${id}/publish`,
    UNPUBLISH: (id: string) => `/v1/admin/weekly-challenges/${id}/unpublish`,
    LEADERBOARD: (id: string) => `/v1/admin/weekly-challenges/${id}/leaderboard`,
  },
  studentAiChat: {
    eligibility: (courseId: string, lessonBlockId: string) =>
      `/v1/student/courses/${courseId}/lesson-blocks/${lessonBlockId}/ai-chat/eligibility`,
    messages: (courseId: string, lessonBlockId: string) =>
      `/v1/student/courses/${courseId}/lesson-blocks/${lessonBlockId}/ai-chat/messages`,
  },
  teacherCourses: {
    list: '/v1/teacher/courses',
    drafts: '/v1/teacher/courses/drafts',
    draftDetail: (id: string) => `/v1/teacher/courses/drafts/${id}`,
    builder: (id: string) => `/v1/teacher/courses/drafts/${id}/builder`,
    builderModules: (id: string) => `/v1/teacher/courses/drafts/${id}/builder/modules`,
    builderModuleDetail: (id: string, moduleId: string) => `/v1/teacher/courses/drafts/${id}/builder/modules/${moduleId}`,
    builderModuleOrder: (id: string) => `/v1/teacher/courses/drafts/${id}/builder/modules/order`,
    builderBlocks: (id: string, moduleId: string) => `/v1/teacher/courses/drafts/${id}/builder/modules/${moduleId}/blocks`,
    builderBlockDetail: (id: string, moduleId: string, blockId: string) => `/v1/teacher/courses/drafts/${id}/builder/modules/${moduleId}/blocks/${blockId}`,
    builderBlockOrder: (id: string, moduleId: string) => `/v1/teacher/courses/drafts/${id}/builder/modules/${moduleId}/blocks/order`,
    validate: (id: string) => `/v1/teacher/courses/drafts/${id}/validate`,
    submitReview: (id: string) => `/v1/teacher/courses/drafts/${id}/submit-review`,
    publish: (id: string) => `/v1/teacher/courses/${id}/publish`,
    unpublish: (id: string) => `/v1/teacher/courses/${id}/unpublish`,
  },
  teacherCourseAssets: {
    thumbnails: '/v1/teacher/courses/assets/thumbnails',
  },
  courseCategories: {
    list: '/v1/course-categories',
  },
  publicCourses: {
    list: '/v1/public/courses',
    reviews: (courseIdentifier: string) =>
      `/v1/public/courses/${courseIdentifier}/reviews`,
  },
  publicTeachers: {
    list: '/v1/public/teachers',
    detail: (teacherId: string) => `/v1/public/teachers/${teacherId}`,
  },
  publicCommercialPolicy: {
    current: '/v1/public/commercial-policy/current',
  },
  orders: {
    create: '/v1/orders',
    list: '/v1/orders',
    detail: (orderId: string) => `/v1/orders/${orderId}`,
    cancel: (orderId: string) => `/v1/orders/${orderId}/cancel`,
  },
  studentRefunds: {
    create: '/v1/student/refunds',
    list: '/v1/student/refunds',
    detail: (id: string) => `/v1/student/refunds/${id}`,
    cancel: (id: string) => `/v1/student/refunds/${id}/cancel`,
  },
  payments: {
    // Local dev simulator for the VNPay IPN callback (no tunnel needed).
    devIpn: '/v1/payments/dev/ipn',
    // Confirms an order from the browser return redirect (checksum-verified backend-side).
    vnpayReturn: '/v1/payments/vnpay/confirm-return',
  },
  LEARNING: {
    COURSE_LEARN: (courseId: string) => `/v1/student/courses/${courseId}/learn`,
    COURSE_PROGRESS: (courseId: string) => `/v1/student/courses/${courseId}/progress`,
    VIDEO_PROGRESS: (blockId: string) => `/v1/student/lessons/${blockId}/video-progress`,
    MARK_COMPLETE: (blockId: string) => `/v1/student/lessons/${blockId}/complete`,
    FLASHCARD_REVIEW: (blockId: string) => `/v1/student/lessons/${blockId}/flashcards/review`,
    QUIZ_SUBMIT: (blockId: string) => `/v1/student/lessons/${blockId}/quiz-submissions`,
    FINAL_TEST_ELIGIBILITY: (courseId: string) => `/v1/student/courses/${courseId}/final-test/eligibility`,
    FINAL_TEST_START: (courseId: string) => `/v1/student/courses/${courseId}/final-test/attempts`,
    FINAL_TEST_TERMINATE: (courseId: string, attemptId: string) =>
      `/v1/student/courses/${courseId}/final-test/attempts/${attemptId}/terminate`,
    FINAL_TEST_SUBMIT: (courseId: string) => `/v1/student/courses/${courseId}/final-test/submissions`,
    CERTIFICATE: (courseId: string) => `/v1/student/courses/${courseId}/certificate`,
    WRITING_SUBMISSION_GET: (blockId: string) => `/v1/student/lessons/${blockId}/writing-submissions/me`,
    WRITING_SUBMISSION_DRAFT: (blockId: string) => `/v1/student/lessons/${blockId}/writing-submissions/draft`,
    WRITING_SUBMISSION_POST: (blockId: string) => `/v1/student/lessons/${blockId}/writing-submissions`,
    WRITING_SUBMISSION_AI: (blockId: string, submissionId: string) => `/v1/student/lessons/${blockId}/writing-submissions/${submissionId}/ai-assistance`,
  },
  ADMIN_LOGIN: '/admin/auth/login',
  ADMIN_SETUP_PASSWORD: '/admin/auth/setup-password',
  ADMIN_REFRESH: '/admin/auth/refresh',
  ADMIN_LOGOUT: '/admin/auth/logout',
  ADMIN_FORGOT_PASSWORD: '/admin/auth/password/forgot',
  ADMIN_RESET_PASSWORD: '/admin/auth/password/reset',
  ADMIN_CHANGE_PASSWORD: '/admin/auth/password/change',
};
