-- ============================================================================
-- V007__backfill_modular_kyc_legacy_requests.sql
-- MHB-11 / UC-22: Preserve pre-modular KYC submissions after V006.
-- ============================================================================

UPDATE kyc_requests request
SET identity_status = 'VERIFIED',
    identity_verified_at = COALESCE(request.identity_verified_at, request.submitted_at),
    certificate_status = CASE request.status
        WHEN 'PENDING' THEN 'PENDING_REVIEW'
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        WHEN 'CORRECTION_REQUIRED' THEN 'REJECTED'
        ELSE request.certificate_status
    END,
    certificate_submitted_at = COALESCE(request.certificate_submitted_at, request.submitted_at)
WHERE request.status IN ('PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED')
  AND request.identity_status = 'NOT_STARTED'
  AND EXISTS (
      SELECT 1
      FROM kyc_documents document
      WHERE document.kyc_request_id = request.id
  );
