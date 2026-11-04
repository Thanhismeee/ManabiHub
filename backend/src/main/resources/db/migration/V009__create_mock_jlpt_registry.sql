-- V009__create_mock_jlpt_registry.sql
-- MHB-11 / UC-22: Create reusable mock JLPT registry for later MBH-12 adapters.

CREATE TABLE IF NOT EXISTS mock_jlpt_registry (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_number            VARCHAR(50) UNIQUE NOT NULL,
    full_name                      VARCHAR(255) NOT NULL,
    date_of_birth                  DATE NOT NULL,
    test_level                     VARCHAR(10) NOT NULL,
    test_date                      DATE NOT NULL,
    test_site                      VARCHAR(255) NOT NULL,
    total_score                    INTEGER NOT NULL,
    pass_status                    VARCHAR(20) NOT NULL,
    active                         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mock_jlpt_registry_active
    ON mock_jlpt_registry (active);

INSERT INTO mock_jlpt_registry (
    registration_number,
    full_name,
    date_of_birth,
    test_level,
    test_date,
    test_site,
    total_score,
    pass_status,
    active
) VALUES (
    '15B2080201-30532',
    'THÂN VĂN THÀNH',
    DATE '2004-08-12',
    'N3',
    DATE '2015-12-06',
    'Vietnam',
    120,
    'PASSED',
    TRUE
)
ON CONFLICT (registration_number) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    date_of_birth = EXCLUDED.date_of_birth,
    test_level = EXCLUDED.test_level,
    test_date = EXCLUDED.test_date,
    test_site = EXCLUDED.test_site,
    total_score = EXCLUDED.total_score,
    pass_status = EXCLUDED.pass_status,
    active = EXCLUDED.active,
    updated_at = NOW();
