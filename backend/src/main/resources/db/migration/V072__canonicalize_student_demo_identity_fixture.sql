-- Keep the synthetic student identity fixture deterministic on upgraded databases.
--
-- V065 originally refreshed only the name, date of birth and active flag when
-- the demo CCCD already existed. A legacy/demo row could therefore retain an
-- obsolete provider, source reference or validation status and make the local
-- national-registry mapping environment-dependent. This forward-only repair
-- updates the complete synthetic record without touching real identities.
INSERT INTO mock_national_id_registry (
    id_number,
    full_name,
    date_of_birth,
    gender,
    permanent_address,
    issue_date,
    expiry_date,
    issue_place,
    document_status,
    front_back_match_status,
    corner_blur_status,
    id_quality_status,
    issue_date_status,
    expiry_status,
    document_identification_status,
    warning_status,
    overlay_image_status,
    open_eyes_status,
    blurred_face_status,
    face_validation_status,
    covered_face_status,
    face_matching_score,
    source_provider,
    source_reference,
    raw_payload,
    active
) VALUES (
    '027204002711',
    'NGUYEN XUAN DAT',
    DATE '2004-08-31',
    'Nam',
    'DEMO_RECORD',
    DATE '2021-04-25',
    DATE '2029-08-31',
    'DEMO_PROVIDER',
    'VALID',
    'IDENTICAL',
    'NO',
    'GOOD',
    'GOOD',
    'UNEXPIRED',
    'LIVE_SHOOTING',
    'VALID',
    'NO',
    'YES',
    'NO',
    'VALID',
    'NO',
    97.7800,
    'VNPT_EKYC_DEMO',
    'STUDENT_WITHDRAWAL_DEMO',
    '{
       "idNumber": "027204002711",
       "fullName": "NGUYEN XUAN DAT",
       "dateOfBirth": "2004-08-31",
       "synthetic": true
     }'::jsonb,
    TRUE
)
ON CONFLICT (id_number) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    date_of_birth = EXCLUDED.date_of_birth,
    gender = EXCLUDED.gender,
    permanent_address = EXCLUDED.permanent_address,
    issue_date = EXCLUDED.issue_date,
    expiry_date = EXCLUDED.expiry_date,
    issue_place = EXCLUDED.issue_place,
    document_status = EXCLUDED.document_status,
    front_back_match_status = EXCLUDED.front_back_match_status,
    corner_blur_status = EXCLUDED.corner_blur_status,
    id_quality_status = EXCLUDED.id_quality_status,
    issue_date_status = EXCLUDED.issue_date_status,
    expiry_status = EXCLUDED.expiry_status,
    document_identification_status = EXCLUDED.document_identification_status,
    warning_status = EXCLUDED.warning_status,
    overlay_image_status = EXCLUDED.overlay_image_status,
    open_eyes_status = EXCLUDED.open_eyes_status,
    blurred_face_status = EXCLUDED.blurred_face_status,
    face_validation_status = EXCLUDED.face_validation_status,
    covered_face_status = EXCLUDED.covered_face_status,
    face_matching_score = EXCLUDED.face_matching_score,
    source_provider = EXCLUDED.source_provider,
    source_reference = EXCLUDED.source_reference,
    raw_payload = EXCLUDED.raw_payload,
    active = EXCLUDED.active,
    updated_at = NOW()
WHERE mock_national_id_registry.id_number = '027204002711'
  AND (
      mock_national_id_registry.raw_payload ->> 'synthetic' = 'true'
      OR mock_national_id_registry.source_provider = 'VNPT_EKYC_DEMO'
      OR mock_national_id_registry.source_reference = 'STUDENT_WITHDRAWAL_DEMO'
  );
