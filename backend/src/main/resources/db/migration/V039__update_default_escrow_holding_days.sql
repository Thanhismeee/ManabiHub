-- Keep applied seed migrations immutable. Only advance the untouched seeded
-- value; an administrator's explicit configuration has a non-null updated_at.
UPDATE system_settings
SET setting_value = '14',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'ESCROW_HOLDING_DAYS'
  AND setting_value = '7'
  AND updated_at IS NULL;
