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

  // ── mi-assistant-service domain codes ──────────────────────────────
  CONVERSATION_NOT_FOUND("MI-001", HttpStatus.NOT_FOUND, "mi.error.conversationNotFound"),
  PROPOSAL_NOT_PENDING("MI-002", HttpStatus.CONFLICT, "mi.error.proposalNotPending"),
  PROPOSAL_EXPIRED("MI-003", HttpStatus.GONE, "mi.error.proposalExpired"),
  MI_PAUSED("MI-004", HttpStatus.FORBIDDEN, "mi.error.paused"),
  OVER_LIMIT_NEEDS_CONFIRM("MI-005", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.overLimitNeedsConfirm"),
  LLM_UNAVAILABLE("MI-006", HttpStatus.SERVICE_UNAVAILABLE, "mi.error.llmUnavailable"),
  CANNOT_UNDERSTAND("MI-007", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.cannotUnderstand"),
  INVALID_LIMIT("MI-008", HttpStatus.BAD_REQUEST, "mi.error.invalidLimit"),
  CHAT_PAY_DISABLED("MI-009", HttpStatus.FORBIDDEN, "mi.error.chatPayDisabled"),
  AGENT_NOT_FOUND("MI-010", HttpStatus.NOT_FOUND, "mi.error.agentNotFound"),
  GUARDRAIL("MI-011", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.guardrail"),
  MI_RATE_LIMITED("MI-012", HttpStatus.TOO_MANY_REQUESTS, "mi.error.rateLimited"),
  REFUSED("MI-013", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.refused"),
  PLAN_EXPIRED("MI-014", HttpStatus.GONE, "mi.error.planExpired"),
  /** {@code propose_new_card} while the agent's {@code guardrails.allowIssue} is absent or false. */
  ISSUE_NOT_ALLOWED("MI-015", HttpStatus.FORBIDDEN, "mi.error.issueNotAllowed"),
  /** Market data disabled or the provider is down; the turn falls back to the calculator. */
  PROPERTY_DATA_UNAVAILABLE(
      "MI-016", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.propertyDataUnavailable"),
  /** An estimated property value may never become the basis of a loan application. */
  ESTIMATE_NOT_FOR_APPROVAL(
      "MI-017", HttpStatus.UNPROCESSABLE_ENTITY, "mi.error.estimateNotForApproval"),
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
