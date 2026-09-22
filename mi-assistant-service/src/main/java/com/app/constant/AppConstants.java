package com.app.constant;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * THE constants area (guideline 01 §3). No {@code static final} literal lives anywhere else in this
 * service; domain rules from the design live in the {@code *Constants} classes beside this one.
 */
public final class AppConstants {

  private AppConstants() {}

  // ── identity of this deployable ──────────────────────────────────────────────
  public static final String SERVICE_NAME = "mi-assistant-service";
  public static final String SCHEMA = "mi";
  public static final String REDIS_PREFIX = "mi";
  public static final String CONSUMER_GROUP = "mi";

  // ── routing ─────────────────────────────────────────────────────────────────
  public static final String API_V1 = "/api/v1";
  public static final String INTERNAL = "/internal";
  public static final String HEALTH = "/health";
  public static final String[] PUBLIC_PATHS = {
    "/health", "/actuator/health", "/actuator/health/**", "/actuator/info",
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  // ── headers (api/README.md §2) ───────────────────────────────────────────────
  public static final String HDR_CUSTOMER_ID = "X-Customer-Id";
  public static final String HDR_REQUEST_ID = "X-Request-Id";
  public static final String HDR_STEP_UP = "X-Step-Up-Token";
  public static final String HDR_IDEMPOTENCY = "Idempotency-Key";
  public static final String HDR_DEVICE_ID = "X-Device-Id";
  public static final String HDR_ONBOARDING_TOKEN = "X-Onboarding-Token";
  public static final String HDR_MOCK_KEY = "X-Mock-Key";
  public static final String HDR_ACCEPT_LANGUAGE = "Accept-Language";

  // ── JWT claims and authorities (guideline 05 §1–2) ──────────────────────────
  public static final String CLAIM_CUSTOMER_ID = "customerId";
  public static final String CLAIM_CIF = "cif";
  public static final String ROLE_CUSTOMER = "ROLE_customer";
  public static final String ROLE_CUSTOMER_RESTRICTED = "ROLE_customer-restricted";
  public static final String SCOPE_MI_AUTO = "SCOPE_mi_auto";

  // ── step-up scopes (guideline 05 §2.3) ──────────────────────────────────────
  public static final String STEP_UP_TRANSFER = "transfer";
  public static final String STEP_UP_PAYMENT = "payment";
  public static final String STEP_UP_LOAN_SIGN = "loan_sign";
  public static final String STEP_UP_CARD_REVEAL = "card_reveal";
  public static final String STEP_UP_PIN_CHANGE = "pin_change";
  /**
   * Opening a card. Its own scope rather than {@code payment}: the per-transaction auto limit is a
   * money control and cannot express consent to a credit product (design 07 §3.1).
   */
  public static final String STEP_UP_CARD_ISSUE = "card_issue";

  // ── locale and money (guideline README "Conventions") ───────────────────────
  public static final ZoneId USER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");
  public static final Locale ENGLISH_LOCALE = Locale.ENGLISH;
  public static final List<Locale> SUPPORTED_LOCALES =
      List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH);
  public static final String CURRENCY = "VND";
  public static final int MONEY_SCALE = 0;

  // ── logging / tracing ───────────────────────────────────────────────────────
  public static final String MDC_REQUEST_ID = "requestId";

  // ── infrastructure tuning ───────────────────────────────────────────────────
  public static final int OUTBOX_BATCH_SIZE = 200;
  public static final int OUTBOX_MAX_ATTEMPTS = 10;
  public static final int CONSUMER_MAX_ATTEMPTS = 4;
  public static final String DLQ_SUFFIX = ".dlq";
  public static final long DEFAULT_CACHE_TTL_SECONDS = 60L;
  public static final long SERVICE_TOKEN_TTL_SECONDS = 300L;
  public static final long IDEMPOTENCY_TTL_HOURS = 24L;
  public static final int PAGE_SIZE_DEFAULT = 20;
  public static final int PAGE_SIZE_MAX = 100;

  // ── event backbone (guideline 06 §3) ────────────────────────────────────────
  public static final String EVENT_TOPIC_PREFIX = "msb.events.";

  /** Maps a CloudEvents {@code type} such as {@code msb.transfer.completed.v1} onto its topic. */
  public static String topicFor(String eventType) {
    String[] parts = eventType.split("\\.");
    return parts.length >= 2 ? EVENT_TOPIC_PREFIX + parts[1] : EVENT_TOPIC_PREFIX + "misc";
  }
}
