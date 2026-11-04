-- Keep the last approved course aggregate immutable while a teacher prepares
-- a replacement revision. The JSON document is never exposed to students;
-- it is applied atomically only after Course Manager approval.
CREATE TABLE course_edit_drafts (
    course_id          UUID PRIMARY KEY REFERENCES courses (id) ON DELETE CASCADE,
    snapshot_json      TEXT NOT NULL,
    base_published_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
