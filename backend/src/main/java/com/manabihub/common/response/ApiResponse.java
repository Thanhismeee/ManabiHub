package com.manabihub.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard API response envelope used by all endpoints.
 * <p>
 * Frontend consumers should rely on {@code messageCode} for i18n display
 * mapping, not parse the raw {@code message} text.
 *
 * @param <T> type of the response payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String messageCode;
    private String message;
    private T data;
    private List<ErrorResponse> errors;
    @Builder.Default
    private Instant timestamp = Instant.now();
    private String path;

    // ──────────────────────────────────────────────
    // Success factories
    // ──────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .messageCode("COMMON_SUCCESS")
                .message("Operation completed successfully")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String messageCode, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .messageCode(messageCode)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String messageCode, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .messageCode(messageCode)
                .message(message)
                .build();
    }

    // ──────────────────────────────────────────────
    // Error factories
    // ──────────────────────────────────────────────

    public static <T> ApiResponse<T> error(String messageCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .messageCode(messageCode)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String messageCode, String message, List<ErrorResponse> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .messageCode(messageCode)
                .message(message)
                .errors(errors)
                .build();
    }

    public static <T> ApiResponse<T> error(String messageCode, String message, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .messageCode(messageCode)
                .message(message)
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> error(String messageCode, String message, List<ErrorResponse> errors, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .messageCode(messageCode)
                .message(message)
                .errors(errors)
                .path(path)
                .build();
    }
}
