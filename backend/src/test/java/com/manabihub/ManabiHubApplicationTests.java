package com.manabihub;

import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.ai.repository.AiUsageLogRepository;
import com.manabihub.course.repository.CourseCategoryRepository;
import com.manabihub.course.repository.CourseModuleRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.CourseThumbnailAssetRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.identity.mapper.StudentProfileMapper;
import com.manabihub.identity.mapper.TeacherProfileMapper;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.IdentityTeacherProfileRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminInvitationRepository;
import com.manabihub.identity.repository.InternalAdminPasswordResetRepository;
import com.manabihub.identity.repository.InternalAdminRefreshTokenRepository;
import com.manabihub.identity.repository.InternalAdminSessionRepository;
import com.manabihub.identity.repository.PhoneVerificationChallengeRepository;
import com.manabihub.identity.repository.RoleRepository;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.repository.AccountIdentityVerificationRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.repository.KycDocumentRepository;
import com.manabihub.kyc.repository.TeacherCertificateClaimRepository;
import com.manabihub.kyc.repository.TeacherIdentityClaimRepository;
import com.manabihub.kyc.repository.VnptIdentityTransactionClaimRepository;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.service.TeacherKycService;
import com.manabihub.mock.repository.MockJlptRegistryRepository;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import com.manabihub.notification.repository.NotificationRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.course.repository.CourseApprovalDecisionRepository;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.FinalTestAttemptRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.repository.LearningCertificateRepository;
import com.manabihub.learning.repository.WishlistItemRepository;
import com.manabihub.learning.repository.QuizAttemptRepository;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import com.manabihub.review.repository.CourseReviewRepository;
import com.manabihub.writing.repository.AiWritingSuggestionRepository;
import com.manabihub.writing.repository.TeacherWritingFeedbackRepository;
import com.manabihub.writing.repository.WritingSubmissionRepository;
import com.manabihub.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@SpringBootTest(properties = {
        "manabihub.kyc.identity-secret=test-secret-key-1234567890-32chars-min-length",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class ManabiHubApplicationTests {

    @MockBean
    private DataSource dataSource;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @MockBean
    private EntityManager entityManager;

    @MockBean
    private KycRequestRepository kycRequestRepository;

    @MockBean
    private com.manabihub.kyc.repository.InternalAdminAccountRepository kycInternalAdminAccountRepository;

    @MockBean
    private TeacherProfileRepository teacherProfileRepository;

    @MockBean
    private KycDocumentRepository kycDocumentRepository;

    @MockBean
    private TeacherIdentityClaimRepository teacherIdentityClaimRepository;

    @MockBean
    private TeacherCertificateClaimRepository teacherCertificateClaimRepository;

    @MockBean
    private VnptIdentityTransactionClaimRepository vnptIdentityTransactionClaimRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private com.manabihub.identity.repository.PublicUserDeviceRepository publicUserDeviceRepository;

    @MockBean
    private com.manabihub.identity.repository.PublicUserSessionRepository publicUserSessionRepository;

    @MockBean
    private com.manabihub.identity.repository.ActiveLearningSessionRepository activeLearningSessionRepository;

    @MockBean
    private PhoneVerificationChallengeRepository phoneVerificationChallengeRepository;

    @MockBean
    private InternalAdminAccountRepository identityInternalAdminAccountRepository;

    @MockBean
    private InternalAdminInvitationRepository internalAdminInvitationRepository;

    @MockBean
    private InternalAdminPasswordResetRepository internalAdminPasswordResetRepository;

    @MockBean
    private InternalAdminRefreshTokenRepository internalAdminRefreshTokenRepository;

    @MockBean
    private InternalAdminSessionRepository internalAdminSessionRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private StudentProfileRepository studentProfileRepository;

    @MockBean
    private AccountIdentityVerificationRepository accountIdentityVerificationRepository;

    @MockBean
    private IdentityTeacherProfileRepository identityTeacherProfileRepository;

    @MockBean
    private TeacherKycService teacherKycService;

    @MockBean
    private MockNationalIdRegistryRepository mockNationalIdRegistryRepository;

    @MockBean
    private MockJlptRegistryRepository mockJlptRegistryRepository;

    @MockBean
    private StudentProfileMapper studentProfileMapper;

    @MockBean
    private TeacherProfileMapper teacherProfileMapper;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private CourseEditDraftService courseEditDraftService;

    @MockBean
    private CourseThumbnailAssetRepository courseThumbnailAssetRepository;

    @MockBean
    private CourseCategoryRepository courseCategoryRepository;

    @MockBean
    private CourseApprovalDecisionRepository courseApprovalDecisionRepository;

    @MockBean
    private CourseModuleRepository courseModuleRepository;

    @MockBean
    private LessonBlockRepository lessonBlockRepository;

    @MockBean
    private FinalTestRepository finalTestRepository;

    @MockBean
    private EnrollmentRepository enrollmentRepository;

    @MockBean
    private LessonBlockProgressRepository LessonBlockProgressRepository;

    @MockBean
    private QuizAttemptRepository quizAttemptRepository;

    @MockBean
    private FinalTestAttemptRepository finalTestAttemptRepository;

    @MockBean
    private LearningCertificateRepository learningCertificateRepository;

    @MockBean
    private WishlistItemRepository wishlistItemRepository;

    @MockBean
    private WritingSubmissionRepository writingSubmissionRepository;

    @MockBean
    private com.manabihub.learning.repository.FlashcardProgressRepository flashcardProgressRepository;

    @MockBean
    private AiWritingSuggestionRepository aiWritingSuggestionRepository;

    @MockBean
    private TeacherWritingFeedbackRepository teacherWritingFeedbackRepository;

    @MockBean
    private AiUsageLogRepository aiUsageLogRepository;

    @MockBean
    private SystemSettingRepository systemSettingRepository;

    @MockBean
    private CourseReviewRepository courseReviewRepository;

    @MockBean
    private com.manabihub.moderation.repository.ViolationEvidenceRepository violationEvidenceRepository;

    @MockBean
    private com.manabihub.moderation.repository.ModerationDecisionRepository moderationDecisionRepository;

    @MockBean
    private com.manabihub.moderation.repository.ModerationActionRecordRepository moderationActionRecordRepository;

    // UC-08 purchase flow repositories.
    @MockBean
    private com.manabihub.order.repository.OrderRepository orderRepository;

    @MockBean
    private com.manabihub.order.repository.OrderItemRepository orderItemRepository;

    @MockBean
    private com.manabihub.payment.repository.PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private WalletRepository walletRepository;

    @MockBean
    private com.manabihub.wallet.repository.WalletTransactionRepository walletTransactionRepository;

    @MockBean
    private com.manabihub.wallet.repository.WalletPaymentReservationRepository walletPaymentReservationRepository;

    @MockBean
    private com.manabihub.wallet.repository.EscrowLedgerRepository escrowLedgerRepository;

    @MockBean
    private com.manabihub.order.repository.OrderItemSnapshotRepository orderItemSnapshotRepository;

    @MockBean
    private com.manabihub.wallet.repository.PlatformCommissionLedgerRepository platformCommissionLedgerRepository;

    @MockBean
    private TransactionTemplate transactionTemplate;

    @MockBean
    private PlatformTransactionManager platformTransactionManager;


    @MockBean
    private com.manabihub.payout.repository.WithdrawalRequestRepository withdrawalRequestRepository;

    @MockBean
    private com.manabihub.payout.repository.PayoutSettlementRepository payoutSettlementRepository;

    @MockBean
    private com.manabihub.payout.repository.PayoutReconciliationLogRepository payoutReconciliationLogRepository;

    @MockBean
    private com.manabihub.payout.repository.TeacherBankAccountRepository teacherBankAccountRepository;

    @MockBean
    private com.manabihub.payout.repository.StudentBankAccountRepository studentBankAccountRepository;

    @MockBean
    private com.manabihub.payout.repository.WithdrawalOtpChallengeRepository withdrawalOtpChallengeRepository;

    @MockBean
    private com.manabihub.refund.repository.RefundRequestRepository refundRequestRepository;

    @MockBean
    private com.manabihub.refund.repository.RefundProviderAttemptRepository refundProviderAttemptRepository;

    @MockBean
    private com.manabihub.finance.repository.SystemExpenseRepository systemExpenseRepository;

    @MockBean
    private com.manabihub.oversight.repository.OperationalDecisionReviewRepository operationalDecisionReviewRepository;

    @MockBean
    private com.manabihub.moderation.repository.ViolationReportRepository moderationViolationReportRepository;

    @MockBean
    private com.manabihub.violation.repository.ViolationReportRepository violationReportRepository;

    @MockBean
    private com.manabihub.challenge.repository.WeeklyLearningChallengeRepository weeklyLearningChallengeRepository;

    @MockBean
    private com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository weeklyLearningChallengeAttemptRepository;

    @MockBean
    private com.manabihub.challenge.repository.WeeklyLearningChallengePairRepository weeklyLearningChallengePairRepository;

    @MockBean
    private com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptCardRepository weeklyLearningChallengeAttemptCardRepository;

    @MockBean
    private com.manabihub.challenge.repository.WeeklyLearningChallengeRewardRepository weeklyLearningChallengeRewardRepository;

    @MockBean
    private com.manabihub.challenge.repository.DailyLearningAttendanceRewardRepository dailyLearningAttendanceRewardRepository;

    @Test
    void contextLoads() {
        // Basic integration test to verify the Spring Application Context loads
        // correctly.
    }
}
