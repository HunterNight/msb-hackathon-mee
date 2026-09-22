package com.app.constant;

/**
 * i18n keys as compile-time constants. {@code MessageKeysCompletenessTest} asserts that
 * every key here exists in both {@code messages_vi.properties} and
 * {@code messages_en.properties} (guideline 01 §6).
 */
public final class MessageKeys {

  private MessageKeys() {}

  public static final String COMMON_ERROR_VALIDATION = "common.error.validation";
  public static final String COMMON_ERROR_UNAUTHORIZED = "common.error.unauthorized";
  public static final String COMMON_ERROR_FORBIDDEN = "common.error.forbidden";
  public static final String COMMON_ERROR_NOT_FOUND = "common.error.notFound";
  public static final String COMMON_ERROR_CONFLICT = "common.error.conflict";
  public static final String COMMON_ERROR_STEP_UP_REQUIRED = "common.error.stepUpRequired";
  public static final String COMMON_ERROR_IDEMPOTENT_REPLAY = "common.error.idempotentReplay";
  public static final String COMMON_ERROR_UPSTREAM = "common.error.upstream";
  public static final String COMMON_ERROR_RATE_LIMITED = "common.error.rateLimited";
  public static final String COMMON_ERROR_INTERNAL = "common.error.internal";
  public static final String MEMORY_ERROR_NO_CONSENT = "memory.error.noConsent";
  public static final String MEMORY_ERROR_NOT_FOUND = "memory.error.notFound";
  public static final String MEMORY_ERROR_RECORD_REJECTED = "memory.error.recordRejected";
  public static final String MEMORY_ERROR_RATE_LIMITED = "memory.error.rateLimited";
  public static final String MEMORY_ERROR_ERASURE_IN_PROGRESS = "memory.error.erasureInProgress";
  public static final String MEMORY_ERROR_KMS_UNAVAILABLE = "memory.error.kmsUnavailable";
  public static final String MEMORY_ERROR_UNKNOWN_EVENT_TYPE = "memory.error.unknownEventType";
  public static final String COMMON_VALIDATION_REQUIRED = "common.validation.required";
  public static final String COMMON_VALIDATION_AMOUNT = "common.validation.amount";
  public static final String COMMON_VALIDATION_PHONE = "common.validation.phone";
  public static final String COMMON_VALIDATION_ACCOUNT = "common.validation.account";
  public static final String COMMON_VALIDATION_OTP = "common.validation.otp";
  public static final String COMMON_VALIDATION_PIN = "common.validation.pin";
  public static final String COMMON_VALIDATION_LOCALE = "common.validation.locale";
  public static final String COMMON_VALIDATION_LENGTH = "common.validation.length";
}
