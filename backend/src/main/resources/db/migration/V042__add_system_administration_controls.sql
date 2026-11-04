-- MHB-52 follows V041 verified course reviews. Deploy V038-V041 first.

INSERT INTO system_settings (
    id,
    setting_key,
    setting_value,
    value_type,
    description,
    is_editable
) VALUES
    (
        gen_random_uuid(),
        'ADMIN_LOCKOUT_MAX_ATTEMPTS',
        '5',
        'NUMBER',
        'Maximum failed admin login attempts before temporary lockout',
        TRUE
    ),
    (
        gen_random_uuid(),
        'ADMIN_LOCKOUT_DURATION_MINUTES',
        '30',
        'NUMBER',
        'Duration of the temporary admin login lockout in minutes',
        TRUE
    ),
    (
        gen_random_uuid(),
        'COURSE_MIN_LEARNING_GOALS',
        '4',
        'NUMBER',
        'Minimum learning goals required for a course draft',
        TRUE
    ),
    (
        gen_random_uuid(),
        'COURSE_MAX_LEARNING_GOAL_LENGTH',
        '160',
        'NUMBER',
        'Maximum number of characters in one course learning goal',
        TRUE
    )
ON CONFLICT (setting_key) DO NOTHING;

-- The Java model and UC-03 allow exactly one internal role per admin account.
CREATE UNIQUE INDEX IF NOT EXISTS uk_internal_admin_roles_account
    ON internal_admin_roles (admin_account_id);
