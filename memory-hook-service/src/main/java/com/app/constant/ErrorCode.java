package com.app.constant;

import org.springframework.http.HttpStatus;

/**
 * Every error this service can return. The code is what the mobile app switches on, the
 * message key is resolved per {@code Accept-Language} by the global handler
 * (guideline 01 §3–4, api/README.md §5).
 */
public enum ErrorCode {

  // ── common, identical in every service (api/README.md §5) ───────────────────
  VALIDATION_FAILED("CM-001", HttpStatus.BAD_REQUEST, "common.error.validation"),
  UNAUTHORIZED("CM-002", HttpStatus.UNAUTHORIZED, "common.error.unauthorized"),
  FORBIDDEN("CM-003", HttpStatus.FORBIDDEN, "common.error.forbidden"),
  NOT_FOUND("CM-004", HttpStatus.NOT_FOUND, "common.error.notFound"),
  CONFLICT("CM-005", HttpStatus.CONFLICT, "common.error.conflict"),
  STEP_UP_REQUIRED("CM-006", HttpStatus.UNAUTHORIZED, "common.error.stepUpRequired"),
  IDEMPOTENT_REPLAY("CM-007", HttpStatus.CONFLICT, "common.error.idempotentReplay"),
  UPSTREAM_UNAVAILABLE("CM-008", HttpStatus.SERVICE_UNAVAILABLE, "common.error.upstream"),
  RATE_LIMITED("CM-009", HttpStatus.TOO_MANY_REQUESTS, "common.error.rateLimited"),
  INTERNAL("CM-999", HttpStatus.INTERNAL_SERVER_ERROR, "common.error.internal"),

  // ── memory-hook-service domain codes ───────────────────────────────
  NO_CONSENT("MH-001", HttpStatus.FORBIDDEN, "memory.error.noConsent"),
  MEMORY_NOT_FOUND("MH-002", HttpStatus.NOT_FOUND, "memory.error.notFound"),
  RECORD_REJECTED("MH-003", HttpStatus.UNPROCESSABLE_ENTITY, "memory.error.recordRejected"),
  MEMORY_RATE_LIMITED("MH-004", HttpStatus.TOO_MANY_REQUESTS, "memory.error.rateLimited"),
  ERASURE_IN_PROGRESS("MH-005", HttpStatus.CONFLICT, "memory.error.erasureInProgress"),
  KMS_UNAVAILABLE("MH-006", HttpStatus.SERVICE_UNAVAILABLE, "memory.error.kmsUnavailable"),
  UNKNOWN_EVENT_TYPE("MH-007", HttpStatus.BAD_REQUEST, "memory.error.unknownEventType"),
  ;

  public final String code;
  public final HttpStatus status;
  public final String messageKey;

  ErrorCode(String code, HttpStatus status, String messageKey) {
    this.code = code;
    this.status = status;
    this.messageKey = messageKey;
  }
}
