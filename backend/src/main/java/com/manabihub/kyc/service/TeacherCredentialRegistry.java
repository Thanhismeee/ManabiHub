package com.manabihub.kyc.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.kyc.domain.TeacherCredentialType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Shared ownership registry for both the legacy JLPT and multilingual workflows. */
@Service @RequiredArgsConstructor
public class TeacherCredentialRegistry {
    private final JdbcTemplate jdbc;
    @Transactional
    public String claim(UUID teacherId, ProficiencyFramework framework, TeacherCredentialType type, String issuer, String code) {
        if (code == null || code.isBlank()) return null;
        String normalized = code.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_.-]", "");
        if (framework == ProficiencyFramework.JLPT && type == TeacherCredentialType.OFFICIAL_CERTIFICATE)
            normalized = normalized.replaceAll("[^A-Z0-9]", "");
        if (normalized.isBlank() || normalized.length() > 120) throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Mã chứng chỉ không hợp lệ.");
        String key = type == TeacherCredentialType.OFFICIAL_CERTIFICATE
                ? "OFFICIAL_CERTIFICATE:" + framework.name() + ":" + normalized
                : type.name() + ":" + framework.name() + ":" + issuer.trim().toUpperCase(Locale.ROOT).replace(":", "") + ":" + normalized;
        jdbc.update("INSERT INTO teacher_credential_registry(credential_key,teacher_id) VALUES (?,?) ON CONFLICT(credential_key) DO NOTHING", key, teacherId);
        UUID owner = jdbc.queryForObject("SELECT teacher_id FROM teacher_credential_registry WHERE credential_key=? FOR UPDATE", UUID.class, key);
        if (!teacherId.equals(owner)) throw new BusinessException(MessageCodes.KYC_CERTIFICATE_ALREADY_CLAIMED,
                "Mã chứng chỉ này đã được liên kết với một giáo viên khác.", HttpStatus.CONFLICT);
        return normalized;
    }

    /** Call after flushing a rejection. A verified legacy claim or another live dossier keeps ownership. */
    @Transactional
    public void releaseRejected(UUID teacherId, ProficiencyFramework framework, TeacherCredentialType type, String issuer, String normalizedCode) {
        if (normalizedCode == null || normalizedCode.isBlank()) return;
        String normalizedIssuer = issuer.trim().toUpperCase(Locale.ROOT).replace(":", "");
        String key = type == TeacherCredentialType.OFFICIAL_CERTIFICATE
                ? "OFFICIAL_CERTIFICATE:" + framework.name() + ":" + normalizedCode
                : type.name() + ":" + framework.name() + ":" + normalizedIssuer + ":" + normalizedCode;
        jdbc.update("""
            DELETE FROM teacher_credential_registry r WHERE r.credential_key=? AND r.teacher_id=?
            AND NOT EXISTS (
                SELECT 1 FROM teacher_language_qualifications q
                WHERE q.teacher_id=r.teacher_id AND q.status IN ('PENDING','APPROVED')
                AND q.proficiency_framework=? AND q.credential_type=?
                AND (q.credential_type='OFFICIAL_CERTIFICATE' OR replace(upper(btrim(q.issuer)),':','')=?)
                AND CASE WHEN q.proficiency_framework='JLPT' AND q.credential_type='OFFICIAL_CERTIFICATE'
                    THEN regexp_replace(upper(q.credential_code),'[^A-Z0-9]','','g')
                    ELSE regexp_replace(upper(btrim(q.credential_code)),'[[:space:]_.-]','','g') END=?
            )
            AND NOT EXISTS (
                SELECT 1 FROM teacher_certificate_claims c
                WHERE 'OFFICIAL_CERTIFICATE:JLPT:'||c.normalized_certificate_code=r.credential_key
            )
            """, key, teacherId, framework.name(), type.name(), normalizedIssuer, normalizedCode);
    }
}
