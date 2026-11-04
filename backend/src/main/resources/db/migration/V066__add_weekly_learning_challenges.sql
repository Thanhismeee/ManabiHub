-- Weekly learning challenges are curated by COURSE_MANAGER. Gameplay state,
-- ranking and rewards remain server-owned so browser data cannot mint money.

CREATE TABLE weekly_learning_challenges (
    id UUID PRIMARY KEY,
    week_start DATE NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    jlpt_level VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    daily_ranked_limit INTEGER NOT NULL DEFAULT 3,
    wrong_penalty_seconds INTEGER NOT NULL DEFAULT 2,
    daily_attendance_reward NUMERIC(12, 2) NOT NULL DEFAULT 0,
    first_prize NUMERIC(12, 2) NOT NULL DEFAULT 0,
    second_prize NUMERIC(12, 2) NOT NULL DEFAULT 0,
    third_prize NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    published_by UUID,
    published_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weekly_learning_challenges_week UNIQUE (week_start),
    CONSTRAINT chk_weekly_challenge_week_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT chk_weekly_challenge_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_weekly_challenge_limits CHECK (
        daily_ranked_limit BETWEEN 1 AND 10 AND wrong_penalty_seconds BETWEEN 0 AND 30
    ),
    CONSTRAINT chk_weekly_challenge_rewards CHECK (
        daily_attendance_reward BETWEEN 0 AND 10000
        AND first_prize BETWEEN 0 AND 500000
        AND second_prize BETWEEN 0 AND 500000
        AND third_prize BETWEEN 0 AND 500000
        AND first_prize >= second_prize AND second_prize >= third_prize
    )
);

CREATE TABLE weekly_learning_challenge_pairs (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES weekly_learning_challenges(id) ON DELETE CASCADE,
    prompt VARCHAR(120) NOT NULL,
    answer VARCHAR(240) NOT NULL,
    order_index INTEGER NOT NULL,
    CONSTRAINT uq_weekly_challenge_pair_order UNIQUE (challenge_id, order_index),
    CONSTRAINT uq_weekly_challenge_pair_prompt UNIQUE (challenge_id, prompt)
);

CREATE TABLE weekly_learning_challenge_attempts (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES weekly_learning_challenges(id),
    student_id UUID NOT NULL REFERENCES student_profiles(id),
    state VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    ranked BOOLEAN NOT NULL,
    ranked_day DATE NOT NULL,
    matched_pairs INTEGER NOT NULL DEFAULT 0,
    penalty_millis BIGINT NOT NULL DEFAULT 0,
    total_millis BIGINT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_weekly_challenge_attempt_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT chk_weekly_challenge_attempt_numbers CHECK (
        matched_pairs >= 0 AND penalty_millis >= 0 AND (total_millis IS NULL OR total_millis >= 0)
    )
);

CREATE INDEX idx_weekly_challenge_attempt_rank
    ON weekly_learning_challenge_attempts (challenge_id, ranked, state, total_millis);
CREATE INDEX idx_weekly_challenge_attempt_daily_limit
    ON weekly_learning_challenge_attempts (challenge_id, student_id, ranked_day, ranked);

CREATE TABLE weekly_learning_challenge_attempt_cards (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES weekly_learning_challenge_attempts(id) ON DELETE CASCADE,
    pair_id UUID NOT NULL REFERENCES weekly_learning_challenge_pairs(id),
    card_kind VARCHAR(10) NOT NULL,
    display_value VARCHAR(240) NOT NULL,
    position INTEGER NOT NULL,
    matched BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_weekly_challenge_attempt_card_position UNIQUE (attempt_id, position),
    CONSTRAINT chk_weekly_challenge_card_kind CHECK (card_kind IN ('PROMPT', 'ANSWER'))
);

CREATE TABLE weekly_learning_challenge_rewards (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES weekly_learning_challenges(id),
    student_id UUID NOT NULL REFERENCES student_profiles(id),
    rank_position INTEGER NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    wallet_transaction_id UUID REFERENCES wallet_transactions(id),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weekly_challenge_reward_student UNIQUE (challenge_id, student_id),
    CONSTRAINT chk_weekly_challenge_reward_rank CHECK (rank_position BETWEEN 1 AND 3),
    CONSTRAINT chk_weekly_challenge_reward_amount CHECK (amount >= 0)
);

CREATE TABLE daily_learning_attendance_rewards (
    id UUID PRIMARY KEY,
    reward_date DATE NOT NULL,
    challenge_id UUID NOT NULL REFERENCES weekly_learning_challenges(id),
    student_id UUID NOT NULL REFERENCES student_profiles(id),
    amount NUMERIC(12, 2) NOT NULL,
    wallet_transaction_id UUID REFERENCES wallet_transactions(id),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_daily_learning_attendance_student UNIQUE (reward_date, student_id),
    CONSTRAINT chk_daily_learning_attendance_amount CHECK (amount >= 0)
);

ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS chk_wallet_tx_type;
ALTER TABLE wallet_transactions ADD CONSTRAINT chk_wallet_tx_type
    CHECK (transaction_type IN (
        'PURCHASE', 'REFUND', 'REVENUE_SHARE', 'PAYOUT', 'ADJUSTMENT',
        'ESCROW_HOLD', 'ESCROW_RELEASE', 'REVENUE_CREDITED', 'REVENUE_CLEARED',
        'WITHDRAWAL_RESERVATION', 'WITHDRAWAL_COMPLETED',
        'WITHDRAWAL_REJECTED', 'WITHDRAWAL_CANCELLED',
        'ADMIN_ADJUSTMENT', 'TOP_UP', 'GAME_REWARD', 'ATTENDANCE_REWARD'
    ));
