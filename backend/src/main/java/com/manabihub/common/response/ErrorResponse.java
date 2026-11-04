package com.manabihub.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single field-level or global-level error in an {@link ApiResponse}.
 * <p>
 * When {@code field} is {@code null}, this represents a global (object-level) error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    /** The field name that caused the error, or null for global errors. */
    private String field;
    /** Machine-readable error code for frontend i18n mapping. */
    private String messageCode;
    /** Human-readable error description (for debugging; not for end-user display). */
    private String message;
    /** The rejected value that caused the validation failure, if applicable. */
    private Object rejectedValue;
}
