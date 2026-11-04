-- The baseline contains deterministic admin identities for local demos. They
-- must never remain usable in a production deployment with a known password.
-- The local profile explicitly re-enables them with a locally generated
-- BCrypt hash after Flyway completes.
UPDATE internal_admin_accounts
SET password_hash = '$2a$10$disabledDemoAccountHashNotUsableOutsideLocalProfile00',
    account_status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE email IN (
    'sysadmin@manabihub.local',
    'course.manager@manabihub.local',
    'finance.manager@manabihub.local'
);
