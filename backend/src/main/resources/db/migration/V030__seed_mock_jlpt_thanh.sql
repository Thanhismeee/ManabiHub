-- V010__seed_mock_jlpt_thanh.sql
-- Seed specific JLPT certificate for testing

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
    '25B2080102-33745',
    'THAN VAN THANH',
    DATE '2004-08-12',
    'N3',
    DATE '2025-12-07',
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
