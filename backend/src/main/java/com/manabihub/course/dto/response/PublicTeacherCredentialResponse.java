package com.manabihub.course.dto.response;

/**
 * A credential summary safe to display on a public teacher profile.
 * Certificate codes, holder details, and source documents must never be exposed here.
 */
public record PublicTeacherCredentialResponse(
        String type,
        String level,
        String verificationStatus
) {
}
