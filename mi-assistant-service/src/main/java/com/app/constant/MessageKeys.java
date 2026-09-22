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
  public static final String MI_ERROR_CONVERSATION_NOT_FOUND = "mi.error.conversationNotFound";
  public static final String MI_ERROR_PROPOSAL_NOT_PENDING = "mi.error.proposalNotPending";
  public static final String MI_ERROR_PROPOSAL_EXPIRED = "mi.error.proposalExpired";
  public static final String MI_ERROR_PAUSED = "mi.error.paused";
  public static final String MI_ERROR_OVER_LIMIT_NEEDS_CONFIRM = "mi.error.overLimitNeedsConfirm";
  public static final String MI_ERROR_LLM_UNAVAILABLE = "mi.error.llmUnavailable";
  public static final String MI_ERROR_CANNOT_UNDERSTAND = "mi.error.cannotUnderstand";
  public static final String MI_ERROR_INVALID_LIMIT = "mi.error.invalidLimit";
  public static final String MI_ERROR_CHAT_PAY_DISABLED = "mi.error.chatPayDisabled";
  public static final String MI_ERROR_AGENT_NOT_FOUND = "mi.error.agentNotFound";
  public static final String MI_ERROR_GUARDRAIL = "mi.error.guardrail";
  public static final String MI_ERROR_RATE_LIMITED = "mi.error.rateLimited";
  public static final String MI_ERROR_REFUSED = "mi.error.refused";
  public static final String COMMON_VALIDATION_REQUIRED = "common.validation.required";
  public static final String COMMON_VALIDATION_AMOUNT = "common.validation.amount";
  public static final String COMMON_VALIDATION_PHONE = "common.validation.phone";
  public static final String COMMON_VALIDATION_ACCOUNT = "common.validation.account";
  public static final String COMMON_VALIDATION_OTP = "common.validation.otp";
  public static final String COMMON_VALIDATION_PIN = "common.validation.pin";
  public static final String COMMON_VALIDATION_LOCALE = "common.validation.locale";
  public static final String COMMON_VALIDATION_LENGTH = "common.validation.length";
  public static final String MI_ERROR_PLAN_EXPIRED = "mi.error.planExpired";
}
