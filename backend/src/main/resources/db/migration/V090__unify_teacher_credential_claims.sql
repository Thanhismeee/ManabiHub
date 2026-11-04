CREATE TABLE teacher_credential_registry (
    credential_key VARCHAR(1000) PRIMARY KEY,
    teacher_id UUID NOT NULL REFERENCES teacher_profiles(id),
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO teacher_credential_registry(credential_key,teacher_id)
SELECT 'OFFICIAL_CERTIFICATE:JLPT:'||normalized_certificate_code,teacher_id FROM teacher_certificate_claims;

INSERT INTO teacher_credential_registry(credential_key,teacher_id)
SELECT CASE WHEN credential_type='OFFICIAL_CERTIFICATE' THEN credential_type||':'||proficiency_framework||':'||
    CASE WHEN proficiency_framework='JLPT' THEN regexp_replace(upper(credential_code),'[^A-Z0-9]','','g')
    ELSE regexp_replace(upper(btrim(credential_code)),'[[:space:]_.-]','','g') END
    ELSE credential_type||':'||proficiency_framework||':'||replace(upper(btrim(issuer)),':','')||':'||regexp_replace(upper(btrim(credential_code)),'[[:space:]_.-]','','g') END,
    teacher_id
FROM teacher_language_qualifications WHERE credential_code IS NOT NULL AND btrim(credential_code)<>'' AND status IN ('PENDING','APPROVED')
ORDER BY submitted_at,id ON CONFLICT(credential_key) DO NOTHING;

-- Never silently reassign ownership when historical workflows contain conflicting claims.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM teacher_language_qualifications q JOIN teacher_credential_registry r ON r.credential_key=
        CASE WHEN q.credential_type='OFFICIAL_CERTIFICATE' THEN q.credential_type||':'||q.proficiency_framework||':'||
            CASE WHEN q.proficiency_framework='JLPT' THEN regexp_replace(upper(q.credential_code),'[^A-Z0-9]','','g')
            ELSE regexp_replace(upper(btrim(q.credential_code)),'[[:space:]_.-]','','g') END
            ELSE q.credential_type||':'||q.proficiency_framework||':'||replace(upper(btrim(q.issuer)),':','')||':'||regexp_replace(upper(btrim(q.credential_code)),'[[:space:]_.-]','','g') END
        WHERE q.credential_code IS NOT NULL AND q.status IN ('PENDING','APPROVED') AND q.teacher_id<>r.teacher_id
    ) THEN RAISE EXCEPTION 'Conflicting teacher credential owners: reconcile legacy and multilingual evidence before applying V090'; END IF;
END $$;
