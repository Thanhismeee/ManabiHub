package com.manabihub.common.exception;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.beans.factory.ObjectProvider;
import com.manabihub.audit.service.AccessDenialAuditService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import com.manabihub.common.exception.ValidationBusinessException;

/**
 * Centralized exception handler that guarantees all API responses follow
 * the {@link ApiResponse} envelope format.
 * <p>
 * Handler ordering (most specific first):
 * <ol>
 *   <li>{@link BusinessException} — domain/business-rule violations</li>
 *   <li>{@link MethodArgumentNotValidException} — @Valid / @Validated failures</li>
 *   <li>{@link ConstraintViolationException} — Jakarta Bean Validation on path/query params</li>
 *   <li>{@link AccessDeniedException} — Spring Security 403</li>
 *   <li>{@link AuthenticationException} — Spring Security 401</li>
 *   <li>{@link Exception} — catch-all for unexpected errors</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final java.util.regex.Pattern HAS_ROLE_PATTERN =
            java.util.regex.Pattern.compile("hasRole\\(\\s*['\"]([A-Z_]+)['\"]\\s*\\)");

    private final ObjectProvider<AccessDenialAuditService> accessDenialAuditServiceProvider;

    // ──────────────────────────────────────────────
    // Business errors
    // ──────────────────────────────────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("Business rule violation: [{}] - {}", ex.getMessageCode(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessageCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getHttpStatus().value()).body(response);
    }

    @ExceptionHandler(ValidationBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationBusinessException(
            ValidationBusinessException ex, HttpServletRequest request) {

        log.warn("Validation Business rule violation: [{}] - {} with {} errors", 
                ex.getMessageCode(), ex.getMessage(), ex.getValidationErrors().size());

        List<ErrorResponse> fieldErrors = ex.getValidationErrors().stream()
                .map(err -> ErrorResponse.builder()
                        .messageCode(err.code())
                        .message(err.message())
                        .build())
                .collect(Collectors.toList());

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessageCode(),
                ex.getMessage(),
                fieldErrors,
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getHttpStatus().value()).body(response);
    }

    // ──────────────────────────────────────────────
    // Validation errors
    // ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.warn("Validation error on request to {}", request.getRequestURI());

        List<ErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ErrorResponse.builder()
                        .field(fieldError.getField())
                        .messageCode(fieldError.getCode())
                        .message(fieldError.getDefaultMessage())
                        .rejectedValue(fieldError.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        // Also capture global (object-level) errors
        ex.getBindingResult().getGlobalErrors().forEach(globalError ->
                fieldErrors.add(ErrorResponse.builder()
                        .messageCode(globalError.getCode())
                        .message(globalError.getDefaultMessage())
                        .build()));

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.VALIDATION_FAILED,
                "Input validation failed",
                fieldErrors,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.warn("Constraint violation on request to {}", request.getRequestURI());

        List<ErrorResponse> errors = ex.getConstraintViolations().stream()
                .map(violation -> ErrorResponse.builder()
                        .field(violation.getPropertyPath().toString())
                        .messageCode("ConstraintViolation")
                        .message(violation.getMessage())
                        .rejectedValue(violation.getInvalidValue())
                        .build())
                .collect(Collectors.toList());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.VALIDATION_FAILED,
                "Constraint validation failed",
                errors,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing request parameter: {}", ex.getParameterName());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }


    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {

        log.warn("Missing request part: {}", ex.getRequestPartName());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Missing required part: " + ex.getRequestPartName(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        log.warn("Missing required request header: {}", ex.getHeaderName());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Missing required request header: " + ex.getHeaderName(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Invalid value for parameter: " + ex.getName(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported Media Type on request to {}: {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Content-Type not supported: " + ex.getContentType(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(
            MultipartException ex, HttpServletRequest request) {

        log.warn("Multipart error on request to {}: {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Invalid multipart request: " + ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload size exceeded on request to {}: {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_BAD_REQUEST,
                "Uploaded file exceeds the maximum allowed size limit",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ──────────────────────────────────────────────
    // Security errors
    // ──────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request, HandlerMethod handlerMethod) {

        log.warn("Access denied for request to {}", request.getRequestURI());

        String code = resolveRoleMessageCode(handlerMethod);
        AccessDenialAuditService auditService = accessDenialAuditServiceProvider.getIfAvailable();
        if (auditService != null) {
            auditService.record(request, code);
        }
        ApiResponse<Void> response = ApiResponse.error(
                code,
                roleMessage(code),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN.value()).body(response);
    }

    /**
     * §5.2 MSG-ADM-006/007/008: khi endpoint bị chặn bởi @PreAuthorize("hasRole('X')"),
     * trả về mã tương ứng với vai trò được yêu cầu thay vì mã chung AUTH_FORBIDDEN.
     */
    private String resolveRoleMessageCode(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return MessageCodes.AUTH_FORBIDDEN;
        }
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), PreAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), PreAuthorize.class);
        }
        if (annotation == null || annotation.value() == null) {
            return MessageCodes.AUTH_FORBIDDEN;
        }
        java.util.regex.Matcher matcher = HAS_ROLE_PATTERN.matcher(annotation.value());
        if (!matcher.find()) {
            return MessageCodes.AUTH_FORBIDDEN;
        }
        switch (matcher.group(1)) {
            case "SYSTEM_ADMIN":
                return MessageCodes.MSG_ADM_006;
            case "COURSE_MANAGER":
                return MessageCodes.MSG_ADM_007;
            case "FINANCE_MANAGER":
                return MessageCodes.MSG_ADM_008;
            default:
                return MessageCodes.AUTH_FORBIDDEN;
        }
    }


    private String roleMessage(String code) {
        switch (code) {
            case MessageCodes.MSG_ADM_006:
                return "Thao tác này yêu cầu quyền Quản trị viên hệ thống.";
            case MessageCodes.MSG_ADM_007:
                return "Thao tác này yêu cầu quyền Quản lý khóa học.";
            case MessageCodes.MSG_ADM_008:
                return "Thao tác này yêu cầu quyền Quản lý tài chính.";
            default:
                return "You do not have permission to access this resource";
        }
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed for request to {}: {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.AUTH_UNAUTHORIZED,
                "Authentication is required to access this resource",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).body(response);
    }

    // ──────────────────────────────────────────────
    // Resource not found
    // ──────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.warn("No resource found: {}", request.getRequestURI());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_NOT_FOUND,
                "The requested resource was not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(response);
    }

    // ──────────────────────────────────────────────
    // Wrong HTTP method on a mapped path
    // ──────────────────────────────────────────────

    /**
     * MHB-025: khong co bo xu ly rieng thi ngoai le nay roi vao bo bat-tat-ca ben
     * duoi va tra ve 500 kem stack trace o muc ERROR - mot loi cua phia goi bi bao
     * cao thanh su co may chu. Phai dat trong lop nay chu khong duoc de framework
     * lo, vi ExceptionHandlerExceptionResolver chay truoc
     * DefaultHandlerExceptionResolver.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        var supported = ex.getSupportedHttpMethods();
        String allow = supported == null ? ""
                : supported.stream().map(Object::toString).collect(Collectors.joining(", "));

        log.warn("Phuong thuc {} khong duoc ho tro cho {} (cho phep: {})",
                ex.getMethod(), request.getRequestURI(), allow.isEmpty() ? "-" : allow);

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_METHOD_NOT_ALLOWED,
                "The " + ex.getMethod() + " method is not supported for this resource",
                request.getRequestURI()
        );

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED.value());
        if (!allow.isEmpty()) {
            builder.header("Allow", allow);
        }
        return builder.body(response);
    }


    // ──────────────────────────────────────────────
    // Catch-all for unexpected errors
    // ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on request to {}: ", request.getRequestURI(), ex);

        // Never expose stack traces or internal details to the client
        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.COMMON_INTERNAL_ERROR,
                "An unexpected error occurred. Please contact the administrator.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed JSON or invalid enum payload on request to {}: {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                MessageCodes.VALIDATION_FAILED,
                "Input validation failed: Malformed payload or invalid enum format",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
