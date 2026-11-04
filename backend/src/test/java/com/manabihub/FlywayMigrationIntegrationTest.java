package com.manabihub;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@org.springframework.boot.test.context.SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@ActiveProfiles("it")
@Import(FlywayMigrationIntegrationTest.MockConfig.class)
public class FlywayMigrationIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class MockConfig {
        @org.springframework.context.annotation.Bean(name = "mvcHandlerMappingIntrospector")
        public org.springframework.web.servlet.handler.HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return org.mockito.Mockito.mock(org.springframework.web.servlet.handler.HandlerMappingIntrospector.class);
        }

        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("manabihub_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "25");
        registry.add("manabihub.kyc.identity-secret", () -> "test-only-identity-secret-at-least-32-bytes");
        registry.add("manabihub.payout.security-secret", () -> "test-only-payout-security-secret-at-least-32-bytes");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    // ── Test 1: Clean build from V001 to V084 ──────────────────────────────
    @Test
    void cleanMigrationToLatestVersion() {
        assertThat(flyway).isNotNull();

        MigrationInfo[] all = flyway.info().all();
        assertThat(all).isNotEmpty();

        // No failed or pending migrations
        List<MigrationInfo> bad = Arrays.stream(all)
                .filter(i -> i.getState() == MigrationState.FAILED
                        || i.getState() == MigrationState.PENDING)
                .collect(Collectors.toList());
        assertThat(bad).as("No failed or pending migrations").isEmpty();

        // Exact latest version
        String current = flyway.info().current().getVersion().toString();
        assertThat(current).isEqualTo("084");

        MigrationInfo immutablePhoneMigration = Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null
                        && "069".equals(info.getVersion().toString()))
                .findFirst()
                .orElseThrow();
        assertThat(immutablePhoneMigration.getChecksum())
                .as("released V069 checksum remains immutable")
                .isEqualTo(1213575808);

        // Hibernate ddl-auto=validate already succeeded if context loaded
        verifyConstraintsAndIndexes();
    }

    // ── Test 2: V031 → V084 upgrade preserves representative data ──────────
    @Test
    void upgradeFromV031PreservesData() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS upgrade_test");

        // Migrate to V031 in a separate schema
        Flyway v031 = Flyway.configure()
                .dataSource(dataSource)
                .schemas("upgrade_test")
                .target("031")
                .load();
        v031.migrate();

        // ── Seed representative data ────────────────────────────────────────
        JdbcTemplate lj = new JdbcTemplate(dataSource);
        String s = "upgrade_test"; // schema qualifier

        UUID studentUserId  = UUID.randomUUID();
        UUID teacherUserId  = UUID.randomUUID();
        UUID studentProfId  = UUID.randomUUID();
        UUID teacherProfId  = UUID.randomUUID();
        UUID kycRequestId   = UUID.randomUUID();
        UUID courseId        = UUID.randomUUID();
        UUID moduleId        = UUID.randomUUID();
        UUID lessonId        = UUID.randomUUID();
        UUID lessonBlockId   = UUID.randomUUID();
        UUID clbId           = UUID.randomUUID();
        UUID enrollmentId    = UUID.randomUUID();
        UUID lessonProgId    = UUID.randomUUID();
        UUID lbpId           = UUID.randomUUID();
        UUID walletId        = UUID.randomUUID();
        UUID orderId         = UUID.randomUUID();
        UUID paymentTxId     = UUID.randomUUID();
        UUID auditLogId      = UUID.randomUUID();

        // Users & profiles
        lj.update("INSERT INTO " + s + ".app_users (id, email, full_name, created_at) VALUES (?, 'su@test.com', 'SU', now())", studentUserId);
        lj.update("INSERT INTO " + s + ".app_users (id, email, full_name, created_at) VALUES (?, 'tu@test.com', 'TU', now())", teacherUserId);
        lj.update("INSERT INTO " + s + ".student_profiles (id, user_id, created_at) VALUES (?, ?, now())", studentProfId, studentUserId);
        lj.update("INSERT INTO " + s + ".teacher_profiles (id, user_id, created_at) VALUES (?, ?, now())", teacherProfId, teacherUserId);

        // Legacy KYC request with an existing provider binding
        lj.update("INSERT INTO " + s + ".kyc_requests "
                        + "(id, teacher_id, status, ekyc_provider, provider_session_id, provider_transaction_id, "
                        + "identity_status, certificate_status, created_at) "
                        + "VALUES (?, ?, 'PENDING', 'VNPT_EKYC_WEB_SDK', 'legacy-session', 'legacy-tx', "
                        + "'PROCESSING', 'LOCKED', now())",
                kycRequestId, teacherProfId);

        // Course -> module -> legacy lesson -> legacy lesson_block
        lj.update("INSERT INTO " + s + ".courses (id, teacher_id, title, description, slug, status, created_at) VALUES (?, ?, 'C', 'D', 'slug-upgrade', 'DRAFT', now())", courseId, teacherProfId);
        lj.update("INSERT INTO " + s + ".course_modules (id, course_id, title, order_index, created_at) VALUES (?, ?, 'M', 1, now())", moduleId, courseId);
        lj.update("INSERT INTO " + s + ".lessons (id, module_id, title, lesson_type, order_index, created_at) VALUES (?, ?, 'L', 'VIDEO', 1, now())", lessonId, moduleId);
        lj.update("INSERT INTO " + s + ".lesson_blocks (id, lesson_id, block_type, content, order_index, created_at) VALUES (?, ?, 'TEXT', '{\"text\":\"hello\"}'::jsonb, 1, now())", lessonBlockId, lessonId);

        // course_lesson_blocks (V018 table, exists by V031)
        lj.update("INSERT INTO " + s + ".course_lesson_blocks (id, module_id, block_type, title, order_index, created_at) VALUES (?, ?, 'TEXT', 'CLB', 1, now())", clbId, moduleId);

        // Enrollment (enrolled_at, no created_at/updated_at)
        lj.update("INSERT INTO " + s + ".enrollments (id, student_id, course_id, enrollment_status, enrolled_at) VALUES (?, ?, ?, 'ACTIVE', now())", enrollmentId, studentProfId, courseId);

        // lesson_progress
        lj.update("INSERT INTO " + s + ".lesson_progress (id, enrollment_id, lesson_id, status, created_at) VALUES (?, ?, ?, 'COMPLETED', now())", lessonProgId, enrollmentId, lessonId);

        // lesson_block_progress (V024 table, column is lesson_block_id)
        lj.update("INSERT INTO " + s + ".lesson_block_progress (id, enrollment_id, lesson_block_id, status, created_at, updated_at) VALUES (?, ?, ?, 'COMPLETED', now(), now())", lbpId, enrollmentId, clbId);

        // Wallet
        lj.update("INSERT INTO " + s + ".wallets (id, owner_type, teacher_id, balance, frozen_balance, currency, created_at) VALUES (?, 'TEACHER', ?, 500, 0, 'VND', now())", walletId, teacherProfId);

        // Order (order_code required, order_status, use PAID)
        lj.update("INSERT INTO " + s + ".orders (id, student_id, order_code, total_amount, currency, order_status, created_at) VALUES (?, ?, 'ORD-001', 100, 'VND', 'PAID', now())", orderId, studentProfId);

        // Payment transaction (provider, amount, status=SUCCESS)
        lj.update("INSERT INTO " + s + ".payment_transactions (id, order_id, provider, provider_transaction_id, amount, status, created_at) VALUES (?, ?, 'VNPAY', 'txn_123', 100, 'SUCCESS', now())", paymentTxId, orderId);

        // Audit log (target_id is UUID, actor_type SYSTEM)
        lj.update("INSERT INTO " + s + ".audit_logs (id, actor_type, action, target_type, target_id, before_value, after_value, metadata, created_at) VALUES (?, 'SYSTEM', 'CREATE', 'COURSE', ?, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now())", auditLogId, courseId);

        // ── Upgrade to latest ───────────────────────────────────────────────
        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .schemas("upgrade_test")
                .load();
        latest.migrate();

        // ── Assert every seeded row survived ────────────────────────────────
        assertRowExists(lj, s, "enrollments", enrollmentId);
        assertRowExists(lj, s, "lesson_progress", lessonProgId);
        assertRowExists(lj, s, "course_lesson_blocks", clbId);
        assertRowExists(lj, s, "lesson_block_progress", lbpId);
        assertRowExists(lj, s, "wallets", walletId);
        assertRowExists(lj, s, "payment_transactions", paymentTxId);
        assertRowExists(lj, s, "audit_logs", auditLogId);
        assertRowExists(lj, s, "kyc_requests", kycRequestId);

        // Verify wallet balance preserved
        Integer balance = lj.queryForObject(
                "SELECT balance FROM " + s + ".wallets WHERE id = ?", Integer.class, walletId);
        assertThat(balance).isEqualTo(500);

        // Verify payment idempotency key preserved
        String provTxn = lj.queryForObject(
                "SELECT provider_transaction_id FROM " + s + ".payment_transactions WHERE id = ?",
                String.class, paymentTxId);
        assertThat(provTxn).isEqualTo("txn_123");

        String kycProviderTxn = lj.queryForObject(
                "SELECT provider_transaction_id FROM " + s + ".kyc_requests WHERE id = ?",
                String.class, kycRequestId);
        assertThat(kycProviderTxn).isEqualTo("legacy-tx");

        Integer verificationAttempts = lj.queryForObject(
                "SELECT server_verification_attempt_count FROM " + s + ".kyc_requests WHERE id = ?",
                Integer.class, kycRequestId);
        assertThat(verificationAttempts).isZero();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @Test
    void v074NormalizesLegacyPhonesAndKeepsVerifiedOwner() {
        String schema = "phone_upgrade_test";
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        Flyway beforePhoneHardening = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .target("069")
                .load();
        beforePhoneHardening.migrate();

        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        legacy.execute("ALTER TABLE " + schema + ".app_users "
                + "ALTER COLUMN phone_number TYPE VARCHAR(20)");

        UUID oldestUnverified = UUID.randomUUID();
        UUID verifiedOwner = UUID.randomUUID();
        UUID trimmedDuplicate = UUID.randomUUID();
        UUID blankPhone = UUID.randomUUID();

        legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, phone_verified_at, created_at) "
                        + "VALUES (?, 'legacy-oldest@test.com', 'Legacy Oldest', '+84971693378', NULL, "
                        + "TIMESTAMPTZ '2020-01-01 00:00:00Z')",
                oldestUnverified);
        legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, phone_verified_at, created_at) "
                        + "VALUES (?, 'verified-owner@test.com', 'Verified Owner', '0971693378', "
                        + "TIMESTAMPTZ '2021-01-02 00:00:00Z', TIMESTAMPTZ '2021-01-01 00:00:00Z')",
                verifiedOwner);
        legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, phone_verified_at, created_at) "
                        + "VALUES (?, 'trimmed-duplicate@test.com', 'Trimmed Duplicate', ' 0971693378 ', NULL, "
                        + "TIMESTAMPTZ '2022-01-01 00:00:00Z')",
                trimmedDuplicate);
        legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, phone_verified_at, created_at) "
                        + "VALUES (?, 'blank-phone@test.com', 'Blank Phone', '   ', NULL, "
                        + "TIMESTAMPTZ '2023-01-01 00:00:00Z')",
                blankPhone);

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .load();
        latest.migrate();

        assertThat(legacy.queryForObject(
                "SELECT phone_number FROM " + schema + ".app_users WHERE id = ?",
                String.class, verifiedOwner)).isEqualTo("0971693378");
        assertThat(legacy.queryForObject(
                "SELECT phone_number FROM " + schema + ".app_users WHERE id = ?",
                String.class, oldestUnverified)).isNull();
        assertThat(legacy.queryForObject(
                "SELECT phone_number FROM " + schema + ".app_users WHERE id = ?",
                String.class, trimmedDuplicate)).isNull();
        assertThat(legacy.queryForObject(
                "SELECT phone_number FROM " + schema + ".app_users WHERE id = ?",
                String.class, blankPhone)).isNull();
        assertThat(legacy.queryForObject(
                "SELECT count(*) FROM " + schema + ".app_users WHERE id IN (?, ?, ?, ?)",
                Integer.class, oldestUnverified, verifiedOwner, trimmedDuplicate, blankPhone)).isEqualTo(4);
        assertThat(legacy.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = ? "
                        + "AND indexname = 'uq_app_users_phone_number'",
                Integer.class, schema)).isEqualTo(1);

        UUID conflictingUser = UUID.randomUUID();
        assertThatThrownBy(() -> legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, created_at) "
                        + "VALUES (?, 'new-conflict@test.com', 'New Conflict', '0971693378', now())",
                conflictingUser))
                .hasMessageContaining("uq_app_users_phone_number");
    }

    @Test
    void v074UpgradeRejectsMultipleVerifiedOwnersForManualReview() {
        String schema = "phone_verified_conflict_test";
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        Flyway beforePhoneHardening = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .target("069")
                .load();
        beforePhoneHardening.migrate();

        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        legacy.execute("ALTER TABLE " + schema + ".app_users "
                + "ALTER COLUMN phone_number TYPE VARCHAR(20)");
        legacy.update("INSERT INTO " + schema + ".app_users "
                        + "(id, email, full_name, phone_number, phone_verified_at, created_at) VALUES "
                        + "(?, 'verified-a@test.com', 'Verified A', '0971693378', now(), now()), "
                        + "(?, 'verified-b@test.com', 'Verified B', ' 0971693378 ', now(), now())",
                UUID.randomUUID(), UUID.randomUUID());

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .load();

        assertThatThrownBy(latest::migrate)
                .hasMessageContaining("manual review is required before retrying");
        assertThat(legacy.queryForObject(
                "SELECT count(*) FROM " + schema + ".app_users "
                        + "WHERE BTRIM(phone_number) = '0971693378'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void v072CanonicalizesExistingSyntheticStudentIdentityFixture() {
        String schema = "student_identity_fixture_upgrade_test";
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        Flyway beforeFixtureRepair = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .target("070")
                .load();
        beforeFixtureRepair.migrate();

        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        legacy.update("UPDATE " + schema + ".mock_national_id_registry SET "
                + "full_name = 'LEGACY NAME', date_of_birth = DATE '1999-01-01', "
                + "gender = 'Unknown', permanent_address = 'LEGACY', "
                + "issue_date = DATE '2000-01-01', expiry_date = DATE '2001-01-01', "
                + "issue_place = 'LEGACY', document_status = 'INVALID', "
                + "front_back_match_status = 'MISMATCH', corner_blur_status = 'YES', "
                + "id_quality_status = 'BAD', issue_date_status = 'BAD', "
                + "expiry_status = 'EXPIRED', document_identification_status = 'UPLOAD', "
                + "warning_status = 'WARNING', overlay_image_status = 'YES', "
                + "open_eyes_status = 'NO', blurred_face_status = 'YES', "
                + "face_validation_status = 'INVALID', covered_face_status = 'YES', "
                + "face_matching_score = 1, source_provider = 'LEGACY_PROVIDER', "
                + "source_reference = 'LEGACY_REFERENCE', raw_payload = '{\"legacy\":true,\"synthetic\":true}'::jsonb, "
                + "active = FALSE WHERE id_number = '027204002711'");

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .load();
        latest.migrate();

        Integer canonical = legacy.queryForObject(
                "SELECT count(*) FROM " + schema + ".mock_national_id_registry "
                        + "WHERE id_number = '027204002711' "
                        + "AND full_name = 'NGUYEN XUAN DAT' "
                        + "AND date_of_birth = DATE '2004-08-31' "
                        + "AND gender = 'Nam' "
                        + "AND issue_date = DATE '2021-04-25' "
                        + "AND expiry_date = DATE '2029-08-31' "
                        + "AND document_status = 'VALID' "
                        + "AND front_back_match_status = 'IDENTICAL' "
                        + "AND id_quality_status = 'GOOD' "
                        + "AND expiry_status = 'UNEXPIRED' "
                        + "AND face_validation_status = 'VALID' "
                        + "AND face_matching_score = 97.7800 "
                        + "AND source_provider = 'VNPT_EKYC_DEMO' "
                        + "AND source_reference = 'STUDENT_WITHDRAWAL_DEMO' "
                        + "AND raw_payload ->> 'synthetic' = 'true' "
                        + "AND active = TRUE",
                Integer.class);
        assertThat(canonical)
                .as("the complete synthetic student identity fixture is canonical after upgrade")
                .isEqualTo(1);
    }

    @Test
    void kycIdentityConstraintsRejectCrossAccountReplayAndDuplicateClaims() {
        String schema = "kyc_identity_uniqueness_test";
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        Flyway isolated = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .load();
        isolated.migrate();

        JdbcTemplate jt = new JdbcTemplate(dataSource);
        UUID teacherUserA = UUID.randomUUID();
        UUID teacherUserB = UUID.randomUUID();
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID studentUserA = UUID.randomUUID();
        UUID studentUserB = UUID.randomUUID();
        UUID studentA = UUID.randomUUID();
        UUID studentB = UUID.randomUUID();

        jt.update("INSERT INTO " + schema + ".app_users (id, email, full_name, created_at) "
                        + "VALUES (?, 'kyc-teacher-a@test.com', 'Teacher A', now()), "
                        + "(?, 'kyc-teacher-b@test.com', 'Teacher B', now()), "
                        + "(?, 'kyc-student-a@test.com', 'Student A', now()), "
                        + "(?, 'kyc-student-b@test.com', 'Student B', now())",
                teacherUserA, teacherUserB, studentUserA, studentUserB);
        jt.update("INSERT INTO " + schema + ".teacher_profiles (id, user_id, created_at) "
                        + "VALUES (?, ?, now()), (?, ?, now())",
                teacherA, teacherUserA, teacherB, teacherUserB);
        jt.update("INSERT INTO " + schema + ".student_profiles (id, user_id, created_at) "
                        + "VALUES (?, ?, now()), (?, ?, now())",
                studentA, studentUserA, studentB, studentUserB);

        jt.update("INSERT INTO " + schema + ".kyc_requests "
                        + "(id, teacher_id, status, ekyc_provider, provider_transaction_id, "
                        + "identity_status, certificate_status, created_at) "
                        + "VALUES (?, ?, 'PENDING', 'VNPT_EKYC', 'replayed-provider-tx', "
                        + "'PROCESSING', 'LOCKED', now())",
                UUID.randomUUID(), teacherA);
        assertThatThrownBy(() -> jt.update("INSERT INTO " + schema + ".kyc_requests "
                        + "(id, teacher_id, status, ekyc_provider, provider_transaction_id, "
                        + "identity_status, certificate_status, created_at) "
                        + "VALUES (?, ?, 'PENDING', 'VNPT_EKYC', 'replayed-provider-tx', "
                        + "'PROCESSING', 'LOCKED', now())",
                UUID.randomUUID(), teacherB))
                .hasMessageContaining("uq_kyc_requests_provider_tx");

        // Provider transaction identifiers are provider-scoped, not globally scoped.
        assertThat(jt.update("INSERT INTO " + schema + ".kyc_requests "
                        + "(id, teacher_id, status, ekyc_provider, provider_transaction_id, "
                        + "identity_status, certificate_status, created_at) "
                        + "VALUES (?, ?, 'PENDING', 'OTHER_EKYC', 'replayed-provider-tx', "
                        + "'PROCESSING', 'LOCKED', now())",
                UUID.randomUUID(), teacherB)).isEqualTo(1);

        jt.update("INSERT INTO " + schema + ".teacher_identity_claims "
                        + "(teacher_id, identity_fingerprint) VALUES (?, 'shared-teacher-fingerprint')",
                teacherA);
        assertThatThrownBy(() -> jt.update("INSERT INTO " + schema + ".teacher_identity_claims "
                        + "(teacher_id, identity_fingerprint) VALUES (?, 'shared-teacher-fingerprint')",
                teacherB))
                .hasMessageContaining("uk_teacher_identity_claims_fingerprint");

        jt.update("UPDATE " + schema + ".student_profiles "
                + "SET identity_fingerprint = 'shared-student-fingerprint' WHERE id = ?", studentA);
        assertThatThrownBy(() -> jt.update("UPDATE " + schema + ".student_profiles "
                        + "SET identity_fingerprint = 'shared-student-fingerprint' WHERE id = ?", studentB))
                .hasMessageContaining("uq_student_profiles_identity_fingerprint");
    }

    private void assertRowExists(JdbcTemplate jt, String schema, String table, UUID id) {
        Integer count = jt.queryForObject(
                "SELECT count(*) FROM " + schema + "." + table + " WHERE id = ?",
                Integer.class, id);
        assertThat(count).as(table + " row " + id + " preserved").isEqualTo(1);
    }

    private void verifyConstraintsAndIndexes() {
        // chk_wallets_owner_type (CHECK constraint from V002)
        Integer chk = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_name = 'chk_wallets_owner_type' AND table_schema = 'public'",
                Integer.class);
        assertThat(chk).as("chk_wallets_owner_type exists").isEqualTo(1);

        // uq_lesson_block_progress_enrollment_block (UNIQUE constraint from V024)
        Integer uqLbp = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_name = 'uq_lesson_block_progress_enrollment_block' "
                        + "AND table_schema = 'public'",
                Integer.class);
        assertThat(uqLbp).as("uq_lesson_block_progress_enrollment_block exists").isEqualTo(1);

        // uq_payment_transactions_provider_txn (partial unique index from V031)
        Integer uqPt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'uq_payment_transactions_provider_txn' "
                        + "AND schemaname = 'public'",
                Integer.class);
        assertThat(uqPt).as("uq_payment_transactions_provider_txn exists").isEqualTo(1);
        // wallet_payment_reservations table
        Integer tblWpr = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'wallet_payment_reservations' AND table_schema = 'public'",
                Integer.class);
        assertThat(tblWpr).as("wallet_payment_reservations exists").isEqualTo(1);

        Integer courseEditDraftTable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_name = 'course_edit_drafts' AND table_schema = 'public'",
                Integer.class);
        assertThat(courseEditDraftTable).as("course_edit_drafts exists").isEqualTo(1);

        Integer accountIdentityTable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_name = 'account_identity_verifications' AND table_schema = 'public'",
                Integer.class);
        assertThat(accountIdentityTable).as("account identity verification ledger exists").isEqualTo(1);

        // uq_wallet_transactions_idempotency_key constraint
        Integer uqWti = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_wallet_transactions_idempotency_key' AND schemaname = 'public'",
                Integer.class);
        if (uqWti == 0) {
            uqWti = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name = 'uq_wallet_transactions_idempotency_key' AND table_schema = 'public'",
                    Integer.class);
        }
        assertThat(uqWti).as("uq_wallet_transactions_idempotency_key exists").isEqualTo(1);

        // uq_refund_request_active_order constraint
        Integer uqRro = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_refund_request_active_order' AND schemaname = 'public'",
                Integer.class);
        if (uqRro == 0) {
            uqRro = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name = 'uq_refund_request_active_order' AND table_schema = 'public'",
                    Integer.class);
        }
        assertThat(uqRro).as("uq_refund_request_active_order exists").isEqualTo(1);

        // enrollments.protected_materials_fully_downloaded_at column
        Integer colEpm = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'enrollments' AND column_name = 'protected_materials_fully_downloaded_at' AND table_schema = 'public'",
                Integer.class);
        assertThat(colEpm).as("enrollments.protected_materials_fully_downloaded_at exists").isEqualTo(1);

        Integer accessColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND "
                        + "((table_name = 'courses' AND column_name IN ('access_duration_days', 'access_expires_at')) "
                        + "OR (table_name = 'enrollments' AND column_name = 'expires_at'))",
                Integer.class);
        assertThat(accessColumns).as("course/enrollment access expiration columns exist").isEqualTo(3);

        String enrollmentStatusConstraint = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE c.conname = 'chk_enrollments_status' AND n.nspname = 'public'",
                String.class);
        assertThat(enrollmentStatusConstraint)
                .as("refund lock and expiration statuses are accepted")
                .contains("REFUND_PENDING", "EXPIRED");

        // MHB-73 server-verification indexes
        Integer pendingVerificationIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'idx_kyc_requests_pending_server_verification' "
                        + "AND schemaname = 'public'",
                Integer.class);
        assertThat(pendingVerificationIndex)
                .as("idx_kyc_requests_pending_server_verification exists")
                .isEqualTo(1);

        Integer providerTransactionIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'uq_kyc_requests_provider_tx' "
                        + "AND schemaname = 'public'",
                Integer.class);
        assertThat(providerTransactionIndex)
                .as("uq_kyc_requests_provider_tx exists")
                .isEqualTo(1);

        String identityStatusConstraint = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) "
                        + "FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE c.conname = 'chk_kyc_identity_status' AND n.nspname = 'public'",
                String.class);
        assertThat(identityStatusConstraint)
                .as("chk_kyc_identity_status allows pending server verification")
                .contains("PENDING_SERVER_VERIFICATION");

        String finalTestStatusConstraint = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) "
                        + "FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE c.conname = 'chk_final_test_attempt_status' AND n.nspname = 'public'",
                String.class);
        assertThat(finalTestStatusConstraint)
                .as("chk_final_test_attempt_status allows violation termination")
                .contains("TERMINATED_FOR_VIOLATION");

        Integer identityColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'student_profiles' "
                        + "AND column_name IN ('identity_fingerprint', 'identity_provider', "
                        + "'identity_full_name', 'identity_date_of_birth', 'identity_verified_at')",
                Integer.class);
        assertThat(identityColumns).as("student identity verification columns exist").isEqualTo(5);

        Integer identityIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'uq_student_profiles_identity_fingerprint' "
                        + "AND schemaname = 'public'",
                Integer.class);
        assertThat(identityIndex).as("uq_student_profiles_identity_fingerprint exists").isEqualTo(1);

        Integer demoRegistry = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mock_national_id_registry "
                        + "WHERE id_number = '027204002711' "
                        + "AND full_name = 'NGUYEN XUAN DAT' "
                        + "AND date_of_birth = DATE '2004-08-31' "
                        + "AND source_provider = 'VNPT_EKYC_DEMO' "
                        + "AND source_reference = 'STUDENT_WITHDRAWAL_DEMO' "
                        + "AND raw_payload ->> 'synthetic' = 'true' "
                        + "AND active = TRUE",
                Integer.class);
        assertThat(demoRegistry).as("canonical student demo registry record exists").isEqualTo(1);

        Integer weeklyChallengeTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN ("
                        + "'weekly_learning_challenges', 'weekly_learning_challenge_pairs', "
                        + "'weekly_learning_challenge_attempts', 'weekly_learning_challenge_attempt_cards', "
                        + "'weekly_learning_challenge_rewards', 'daily_learning_attendance_rewards')",
                Integer.class);
        assertThat(weeklyChallengeTables).as("all weekly challenge tables exist").isEqualTo(6);

        Integer dailyRewardIdempotency = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' "
                        + "AND constraint_name = 'uq_daily_learning_attendance_student'",
                Integer.class);
        assertThat(dailyRewardIdempotency)
                .as("daily attendance reward is unique per student and business day")
                .isEqualTo(1);

        String walletTransactionTypeConstraint = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE c.conname = 'chk_wallet_tx_type' AND n.nspname = 'public'",
                String.class);
        assertThat(walletTransactionTypeConstraint)
                .contains("GAME_REWARD", "ATTENDANCE_REWARD");

        Integer phoneNumberIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'uq_app_users_phone_number' "
                        + "AND schemaname = 'public'",
                Integer.class);
        assertThat(phoneNumberIndex).as("uq_app_users_phone_number exists").isEqualTo(1);

        Integer phoneChallengeTable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name = 'phone_verification_challenges'",
                Integer.class);
        assertThat(phoneChallengeTable).as("phone_verification_challenges exists").isEqualTo(1);

        Integer firebaseChallengeColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND ("
                        + "(table_name = 'phone_verification_challenges' "
                        + "AND column_name IN ('challenge_id', 'verification_method')) OR "
                        + "(table_name = 'withdrawal_otp_challenges' "
                        + "AND column_name IN ('challenge_id', 'verification_method', 'phone_number')))",
                Integer.class);
        assertThat(firebaseChallengeColumns)
                .as("Firebase phone-auth challenge columns exist")
                .isEqualTo(5);

        Integer firebaseChallengeIndexes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname IN ('uq_phone_verification_challenge_id', "
                        + "'uq_withdrawal_otp_challenge_id')",
                Integer.class);
        assertThat(firebaseChallengeIndexes)
                .as("Firebase challenge identifiers are unique")
                .isEqualTo(2);

        Integer firebaseProofConstraints = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND constraint_name IN ("
                        + "'chk_phone_verification_method', 'chk_phone_verification_proof_storage', "
                        + "'chk_withdrawal_otp_method', 'chk_withdrawal_otp_proof_storage')",
                Integer.class);
        assertThat(firebaseProofConstraints)
                .as("Firebase proof storage and method constraints exist")
                .isEqualTo(4);

        Integer thumbnailAssetTable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name = 'course_thumbnail_assets'",
                Integer.class);
        assertThat(thumbnailAssetTable)
                .as("course thumbnails use persistent database storage")
                .isEqualTo(1);

        Integer thumbnailFileNameIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE schemaname = 'public' "
                        + "AND tablename = 'course_thumbnail_assets' "
                        + "AND indexdef LIKE '%UNIQUE%file_name%'",
                Integer.class);
        assertThat(thumbnailFileNameIndex)
                .as("course thumbnail file names are unique")
                .isEqualTo(1);
    }
}
