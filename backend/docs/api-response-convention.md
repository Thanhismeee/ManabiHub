# ManabiHub API Response Convention

> **Version:** 1.0  
> **Last updated:** 2026-06-25  
> **Applies to:** All REST API endpoints in the ManabiHub backend

---

## 1. Standard Response Envelope

Every API response — success or error — is wrapped in a consistent `ApiResponse<T>` JSON envelope:

```json
{
  "success": true,
  "messageCode": "COMMON_SUCCESS",
  "message": "Operation completed successfully",
  "data": { ... },
  "errors": null,
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/courses/123"
}
```

### Field Reference

| Field         | Type                | Required | Description |
|---------------|---------------------|----------|-------------|
| `success`     | `boolean`           | ✅ Always | `true` for successful operations, `false` for errors |
| `messageCode` | `string`            | ✅ Always | Machine-readable code for frontend i18n mapping (e.g., `COMMON_SUCCESS`) |
| `message`     | `string`            | ✅ Always | Human-readable description (for debugging, not end-user display) |
| `data`        | `T` (generic)       | ❌ Nullable | Response payload. Present on success, `null` on error |
| `errors`      | `ErrorResponse[]`   | ❌ Nullable | Field-level/global error details. Present only on validation errors |
| `timestamp`   | `ISO-8601 instant`  | ✅ Always | Server-side timestamp when the response was generated |
| `path`        | `string`            | ❌ Nullable | The request URI that generated this response (present in error responses) |

> **Note:** `null` fields are omitted from the JSON output via `@JsonInclude(NON_NULL)`.

---

## 2. Response Examples

### 2.1 Success Response

```json
{
  "success": true,
  "messageCode": "COMMON_SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "JLPT N3 Grammar Course",
    "status": "PUBLISHED"
  },
  "timestamp": "2026-06-25T04:00:00.000Z"
}
```

### 2.2 Validation Error Response

Returned when `@Valid` / `@Validated` fails on a request body or when `ConstraintViolation` occurs on path/query parameters.

```json
{
  "success": false,
  "messageCode": "VALIDATION_FAILED",
  "message": "Input validation failed",
  "errors": [
    {
      "field": "name",
      "messageCode": "NotBlank",
      "message": "Name must not be blank",
      "rejectedValue": ""
    },
    {
      "field": "email",
      "messageCode": "NotBlank",
      "message": "Email must not be blank",
      "rejectedValue": null
    }
  ],
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/courses"
}
```

### 2.3 Business Error Response

Returned when a `BusinessException` is thrown from service-layer code.

```json
{
  "success": false,
  "messageCode": "COURSE_NOT_FOUND",
  "message": "Course with the given ID was not found",
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/courses/999"
}
```

### 2.4 Unauthorized Response (401)

Returned when Spring Security detects missing or invalid authentication.

```json
{
  "success": false,
  "messageCode": "AUTH_UNAUTHORIZED",
  "message": "Authentication is required to access this resource",
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/profile"
}
```

### 2.5 Forbidden Response (403)

Returned when the authenticated user does not have the required role/permission.

```json
{
  "success": false,
  "messageCode": "AUTH_FORBIDDEN",
  "message": "You do not have permission to access this resource",
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/admin/users"
}
```

### 2.6 Internal Server Error (500)

Returned for unhandled exceptions. **Never exposes stack traces or sensitive details.**

```json
{
  "success": false,
  "messageCode": "COMMON_INTERNAL_ERROR",
  "message": "An unexpected error occurred. Please contact the administrator.",
  "timestamp": "2026-06-25T04:00:00.000Z",
  "path": "/api/v1/courses"
}
```

---

## 3. Message Code Naming Convention

All message codes follow the pattern: **`DOMAIN_ACTION_OR_STATE`**

### Domain Prefixes

| Prefix          | Domain                            |
|-----------------|-----------------------------------|
| `COMMON_`       | Generic cross-cutting operations  |
| `VALIDATION_`   | Input validation                  |
| `AUTH_`         | Authentication & authorization    |
| `PROFILE_`     | User profile management           |
| `KYC_`         | Teacher KYC verification          |
| `COURSE_`      | Course management                 |
| `CONTENT_`     | Lesson / content management       |
| `FINAL_TEST_`  | Final test management             |
| `LEARNING_`    | Student learning progress         |
| `AI_`          | AI-related operations             |
| `PAYMENT_`     | Payment processing                |
| `WALLET_`      | Wallet operations                 |
| `REFUND_`      | Refund processing                 |
| `PAYOUT_`      | Teacher payout                    |
| `ADMIN_`       | Admin operations                  |
| `NOTIFICATION_`| Notification system               |
| `SYSTEM_`      | System-level codes                |

### Rules

- Codes are always **UPPER_SNAKE_CASE**.
- Codes are defined as `public static final String` in `MessageCodes.java`.
- New feature modules must add their codes to `MessageCodes.java` before use.
- Never use raw string literals for message codes in handler/service code.

---

## 4. ErrorResponse Structure

Each entry in the `errors` array follows this structure:

```json
{
  "field": "fieldName",
  "messageCode": "NotBlank",
  "message": "Field must not be blank",
  "rejectedValue": ""
}
```

| Field           | Type     | Description |
|-----------------|----------|-------------|
| `field`         | `string` | Field that caused the error. `null` for global/object-level errors. |
| `messageCode`   | `string` | Validation constraint name (e.g., `NotBlank`, `Size`, `Pattern`) |
| `message`       | `string` | Constraint violation message |
| `rejectedValue` | `any`    | The value that failed validation. `null` if not applicable. |

---

## 5. Rules for Frontend Consumers

1. **Use `messageCode` for display mapping.** The frontend must maintain an i18n mapping from `messageCode` to user-facing text. Do NOT parse or display the raw `message` field to end-users.

2. **Check `success` first.** Always check the `success` boolean before processing `data` or `errors`.

3. **Handle `errors` array for forms.** When `messageCode` is `VALIDATION_FAILED`, iterate over the `errors` array and map each `field` + `messageCode` to the corresponding form field error.

4. **Graceful fallback.** If a `messageCode` is not yet mapped in the frontend i18n dictionary, display a generic fallback message.

---

## 6. Rules for Backend Developers

1. **Always return `ApiResponse`.** All controller endpoints must return `ResponseEntity<ApiResponse<T>>`. Raw objects or plain strings are not allowed.

2. **Use `MessageCodes` constants.** Never use raw string literals for message codes. Always reference `MessageCodes.CONSTANT_NAME`.

3. **Throw `BusinessException` for domain errors.** Service-layer business rule violations should throw `BusinessException` with the appropriate message code and HTTP status.

4. **Never expose internals.** The `GlobalExceptionHandler` catch-all handler ensures stack traces, class names, and database details are never leaked to the client.

5. **Consistent HTTP status codes:**

   | Scenario                      | HTTP Status | Message Code Pattern      |
   |-------------------------------|-------------|---------------------------|
   | Successful operation          | `200 OK`    | `COMMON_SUCCESS`, `*_CREATED`, `*_UPDATED`, etc. |
   | Resource created              | `201 Created` | `COMMON_CREATED`        |
   | Validation failure            | `400 Bad Request` | `VALIDATION_FAILED`  |
   | Business rule violation       | `400 Bad Request` | Domain-specific code  |
   | Resource not found            | `404 Not Found` | `*_NOT_FOUND`         |
   | Unauthenticated               | `401 Unauthorized` | `AUTH_UNAUTHORIZED`  |
   | Insufficient permissions      | `403 Forbidden` | `AUTH_FORBIDDEN`       |
   | Unexpected server error       | `500 Internal Server Error` | `COMMON_INTERNAL_ERROR` |

---

## 7. Exception Handling Chain

The `GlobalExceptionHandler` processes exceptions in the following priority:

```text
BusinessException          → domain-specific code, status from exception
MethodArgumentNotValid     → VALIDATION_FAILED, 400
ConstraintViolation        → VALIDATION_FAILED, 400
MissingServletRequestParam → COMMON_BAD_REQUEST, 400
MethodArgumentTypeMismatch → COMMON_BAD_REQUEST, 400
AccessDeniedException      → AUTH_FORBIDDEN, 403
AuthenticationException    → AUTH_UNAUTHORIZED, 401
NoResourceFoundException   → COMMON_NOT_FOUND, 404
Exception (catch-all)      → COMMON_INTERNAL_ERROR, 500
```

---

## 8. Related Files

| File | Purpose |
|------|---------|
| `common/response/ApiResponse.java` | Response envelope class |
| `common/response/ErrorResponse.java` | Field-level error detail class |
| `common/exception/BusinessException.java` | Base business exception |
| `common/exception/GlobalExceptionHandler.java` | Centralized exception handler |
| `common/constants/MessageCodes.java` | Message code constants |
| `common/demo/DemoController.java` | Setup verification endpoints (remove before production) |
