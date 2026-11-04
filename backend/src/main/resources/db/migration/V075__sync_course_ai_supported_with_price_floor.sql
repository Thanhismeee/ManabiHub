-- Keep the denormalized course capability consistent with the current system
-- price floor. Global AI switches remain runtime settings and are not stored
-- in courses.ai_supported.
UPDATE courses
SET ai_supported = price >= COALESCE(
    (
        SELECT setting_value::numeric
        FROM system_settings
        WHERE setting_key = 'AI_SUPPORT_PRICE_FLOOR'
    ),
    100000
);
