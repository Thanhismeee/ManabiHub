import { createBrowserRouter, Navigate } from 'react-router-dom';
import { PublicLayout } from '../shared/layouts/PublicLayout';
import { StudentLayout } from '../shared/layouts/StudentLayout';
import { TeacherLayout } from '../shared/layouts/TeacherLayout';
import { AdminLayout } from '../shared/layouts/AdminLayout';
import { NotificationsPage } from '../features/notifications/pages/NotificationsPage';
import { TeacherKycRoute } from '../features/kyc/TeacherKycRoute';
import { KycQueuePage } from '../features/admin-kyc/pages/KycQueuePage';
import { KycDetailPage } from '../features/admin-kyc/pages/KycDetailPage';
import { StudentDashboardPage } from '../features/student/pages/StudentDashboardPage';
import { StudentCoursesPage } from '../features/student/pages/StudentCoursesPage';
import StudentProfilePage from '../features/profile/StudentProfilePage';
import TeacherProfilePage from '../features/profile/TeacherProfilePage';
import { CourseBuilderPage } from '../features/course-builder/pages/CourseBuilderPage';
import { CourseDraftPage } from '../features/course-builder/pages/CourseDraftPage';
import { TeacherCoursesPage } from '../features/course-builder/pages/TeacherCoursesPage';
import { CourseApprovalQueuePage } from '../features/admin-course-approval/pages/CourseApprovalQueuePage';
import { CourseApprovalDetailPage } from '../features/admin-course-approval/pages/CourseApprovalDetailPage';
import { WeeklyChallengeManagementPage } from '../features/admin-weekly-challenge/WeeklyChallengeManagementPage';
import { AdminRefundQueue } from '../features/admin-refund/components/AdminRefundQueue';
import { AdminRefundDetail } from '../features/admin-refund/components/AdminRefundDetail';
import { FinalTestConfigPage } from '../features/course-builder/pages/FinalTestConfigPage';
import { AdminAuditLogPage } from '../features/admin/pages/AdminAuditLogPage';
import { CourseDetailPage } from '../features/catalog/pages/CourseDetailPage';
import { CourseLearningPage } from '../features/learning/pages/CourseLearningPage';
import { CourseCatalogPage } from '../features/catalog/pages/CourseCatalogPage';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { StudentOnboardingPage } from './pages/StudentOnboardingPage';
import { PublicLoginPage } from './pages/PublicLoginPage';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { AdminSetupPasswordPage } from './pages/AdminSetupPasswordPage';
import {
  AdminChangePasswordPage,
  AdminForgotPasswordPage,
  AdminResetPasswordPage,
} from './pages/AdminPasswordPages';
import { PublicHomePage } from './pages/PublicHomePage/PublicHomePage';
import { AboutUsPage } from './pages/PublicHomePage/AboutUsPage';
import { TeacherDashboardPage } from '../features/teacher/pages/TeacherDashboardPage';
import { AdminDashboardPage } from '../features/admin/pages/AdminDashboardPage';
import { TeacherWritingReviewsPage } from '../features/writing-review/pages/TeacherWritingReviewsPage';
import { TeacherWritingReviewDetailPage } from '../features/writing-review/pages/TeacherWritingReviewDetailPage';
import { TeacherCohortsPage } from '../features/cohorts/TeacherCohortsPage';
import { TeacherCohortWorkspacePage } from '../features/cohorts/TeacherCohortWorkspacePage';
import { StudentCohortsPage } from '../features/cohorts/StudentCohortsPage';
import { CohortReportsPage } from '../features/cohorts/CohortReportsPage';
import { StudentAiChatPage } from '../features/ai-chat/pages/StudentAiChatPage';
import { StudentWishlistPage } from '../features/wishlist/pages/StudentWishlistPage';
import { TeacherWalletPage } from '../features/my-wallet/pages/TeacherWalletPage';
import { PayoutQueuePage } from '../features/admin-payout/pages/PayoutQueuePage';
import { PayoutSettlementPage } from '../features/admin-payout/pages/PayoutSettlementPage';
import { ViolationQueuePage } from '../features/admin-violation/pages/ViolationQueuePage';
import { ViolationDetailPage } from '../features/admin-violation/pages/ViolationDetailPage';
import { CheckoutPage } from '../features/checkout/pages/CheckoutPage';
import { CheckoutReturnPage } from '../features/checkout/pages/CheckoutReturnPage';
import { StudentPaymentsPage } from '../features/payments/pages/StudentPaymentsPage';
import { StudentIdentityVerificationPage } from '../features/wallet/pages/StudentIdentityVerificationPage';
import { PublicTeacherProfilePage } from '../features/teacher-discovery/pages/PublicTeacherProfilePage';
import { SystemSettingsPage } from '../features/system-administration/pages/SystemSettingsPage';
import { InternalAdminAccountsPage } from '../features/system-administration/pages/InternalAdminAccountsPage';
import { OperationsDashboardPage } from '../features/admin-operations/pages/OperationsDashboardPage';
import { FinanceRevenueDashboardPage } from '../features/admin-finance/FinanceRevenueDashboardPage';
import { SystemExpenseManagementPage } from '../features/admin-finance/SystemExpenseManagementPage';
import { DecisionReviewPage } from '../features/admin-oversight/DecisionReviewPage';
import { HelpCenterLayout } from '../features/help-center/layouts/HelpCenterLayout';
import { HelpCenterIndexPage } from '../features/help-center/pages/HelpCenterIndexPage';
import { InstructorRevenueSharePage } from '../features/help-center/pages/articles/InstructorRevenueSharePage';
import { InstructorEscrowPayoutsPage } from '../features/help-center/pages/articles/InstructorEscrowPayoutsPage';
import {
  InstructorCourseReviewPage,
  InstructorVerificationPage,
} from '../features/help-center/pages/articles/InstructorArticles';
import {
  AiAndDataPage,
  LearnerPaymentsRefundsPage,
  TrustSafetyPage,
} from '../features/help-center/pages/articles/GeneralArticles';
import {
  AiNoticePage,
  InstructorTermsPage,
  PrivacyPage,
  RefundPolicyPage,
  TermsPage,
} from '../features/help-center/pages/legal/LegalPages';
import { NotFoundPage } from '../shared/components/NotFoundPage/NotFoundPage';
import { RouteErrorPage } from '../shared/components/RouteErrorPage';

export const router = createBrowserRouter([
  {
    path: '/',
    index: true,
    element: <PublicHomePage />,
  },
  {
    path: '/login',
    element: <PublicLoginPage />,
  },
  {
    path: '/admin/login',
    element: <AdminLoginPage />,
  },
  {
    path: '/admin/setup-password',
    element: <AdminSetupPasswordPage />,
  },
  {
    path: '/admin/forgot-password',
    element: <AdminForgotPasswordPage />,
  },
  {
    path: '/admin/reset-password',
    element: <AdminResetPasswordPage />,
  },
  {
    path: '/onboarding/student',
    element: <StudentOnboardingPage />,
  },
  {
    path: '/help',
    element: <HelpCenterLayout />,
    children: [
      { index: true, element: <HelpCenterIndexPage /> },
      { path: 'instructors/verification', element: <InstructorVerificationPage /> },
      { path: 'instructors/revenue-share', element: <InstructorRevenueSharePage /> },
      { path: 'instructors/escrow-and-payouts', element: <InstructorEscrowPayoutsPage /> },
      { path: 'instructors/course-review-and-unpublishing', element: <InstructorCourseReviewPage /> },
      { path: 'learners/payments-refunds-access', element: <LearnerPaymentsRefundsPage /> },
      { path: 'trust-safety/reporting-and-actions', element: <TrustSafetyPage /> },
      { path: 'ai-and-data', element: <AiAndDataPage /> },
    ],
  },
  {
    path: '/legal',
    element: <HelpCenterLayout />,
    children: [
      { index: true, element: <Navigate to="/help" replace /> },
      { path: 'terms', element: <TermsPage /> },
      { path: 'privacy', element: <PrivacyPage /> },
      { path: 'instructor-terms', element: <InstructorTermsPage /> },
      { path: 'refund-policy', element: <RefundPolicyPage /> },
      { path: 'ai-notice', element: <AiNoticePage /> },
    ],
  },
  {
    path: '/',
    element: <PublicLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        path: 'auth/callback',
        element: <AuthCallbackPage />,
      },
      {
        path: 'courses',
        element: <CourseCatalogPage />,
      },
      {
        path: 'courses/:id',
        element: <CourseDetailPage />,
      },
      {
        path: 'teachers/:teacherId',
        element: <PublicTeacherProfilePage />,
      },
      {
        path: 'register',
        element: <Navigate to="/login" replace />,
      },
      {
        path: 'about',
        element: <AboutUsPage />,
      },
      {
        path: 'checkout/return',
        element: <CheckoutReturnPage />,
      },
      {
        path: 'checkout/:orderId',
        element: <CheckoutPage />,
      },
    ],
  },
  {
    path: '/student',
    element: <StudentLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'cohorts', element: <StudentCohortsPage /> },
      { path: 'cohorts/:cohortId', element: <StudentCohortsPage /> },
      {
        index: true,
        element: <Navigate to="/student/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: <StudentDashboardPage />,
      },
      {
        path: 'browse',
        element: <CourseCatalogPage />,
      },
      {
        path: 'profile',
        element: <StudentProfilePage />,
      },
      {
        path: 'notifications',
        element: <NotificationsPage />,
      },
      {
        path: 'payments',
        element: <StudentPaymentsPage />,
      },
        {
          path: 'wallet',
          element: <Navigate to="/student/payments" replace />,
        },
      {
        path: 'identity-verification',
        element: <StudentIdentityVerificationPage />,
      },
      {
        path: 'courses',
        element: <StudentCoursesPage />,
      },
      {
        path: 'wishlist',
        element: <StudentWishlistPage />,
      },
      {
        path: 'courses/:courseId/learn',
        element: <CourseLearningPage />,
      },
      {
        path: 'courses/:courseId/lesson-blocks/:lessonBlockId/ai-chat',
        element: <StudentAiChatPage />,
      },
    ],
  },
  {
    path: '/teacher',
    element: <TeacherLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'cohorts/:cohortId/workspace', element: <TeacherCohortWorkspacePage /> },
      { path: 'cohort-reports', element: <CohortReportsPage /> },
      {
        index: true,
        element: <Navigate to="/teacher/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: <TeacherDashboardPage />,
      },
      {
        path: 'courses',
        element: <TeacherCoursesPage />,
      },
      {
        path: 'cohorts',
        element: <TeacherCohortsPage />,
      },
      {
        path: 'courses/new',
        element: <CourseDraftPage />,
      },
      {
        path: 'courses/:draftId/builder',
        element: <CourseBuilderPage />,
      },
      {
        path: 'courses/:courseId/final-test',
        element: <FinalTestConfigPage />,
      },
      {
        path: 'writing-reviews',
        element: <TeacherWritingReviewsPage />,
      },
      {
        path: 'writing-reviews/:submissionId',
        element: <TeacherWritingReviewDetailPage />,
      },
      {
        path: 'profile',
        element: <TeacherProfilePage />,
      },
      {
        path: 'notifications',
        element: <NotificationsPage />,
      },
      {
        path: 'kyc',
        element: <TeacherKycRoute />,
      },
      {
        path: 'wallet',
        element: <TeacherWalletPage />,
      },
    ],
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'cohort-reports', element: <CohortReportsPage admin /> },
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: <AdminDashboardPage />,
      },
      {
        path: 'change-password',
        element: <AdminChangePasswordPage />,
      },
      {
        path: 'notifications',
        element: <NotificationsPage />,
      },
      {
        path: 'settings',
        element: <SystemSettingsPage />,
      },
      {
        path: 'operations',
        element: <OperationsDashboardPage />,
      },
      {
        path: 'users',
        element: <InternalAdminAccountsPage />,
      },
      {
        path: 'audit-logs',
        element: <AdminAuditLogPage />,
      },
      {
        path: 'decision-reviews',
        element: <DecisionReviewPage />,
      },
      {
        path: 'kyc/review',
        element: <Navigate to="/admin/kyc" replace />,
      },
      {
        path: 'kyc',
        element: <KycQueuePage />,
      },
      {
        path: 'kyc/:id',
        element: <KycDetailPage />,
      },
      {
        path: 'courses/approvals',
        element: <CourseApprovalQueuePage />,
      },
      {
        path: 'courses/approvals/:id',
        element: <CourseApprovalDetailPage />,
      },
      {
        path: 'weekly-challenges',
        element: <WeeklyChallengeManagementPage />,
      },
      {
        path: 'refunds',
        element: <AdminRefundQueue />,
      },
      {
        path: 'refunds/:id',
        element: <AdminRefundDetail />,
      },
      {
        path: 'payouts',
        element: <PayoutQueuePage />,
      },
      {
        path: 'payouts/:id',
        element: <PayoutSettlementPage />,
      },
      {
        path: 'finance/revenue',
        element: <FinanceRevenueDashboardPage />,
      },
      {
        path: 'finance/expenses',
        element: <SystemExpenseManagementPage />,
      },
      {
        path: 'finance/expenses/:id',
        element: <SystemExpenseManagementPage />,
      },
      {
        path: 'violations',
        element: <ViolationQueuePage />,
      },
      {
        path: 'violations/:id',
        element: <ViolationDetailPage />,
      },
    ],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
]);
