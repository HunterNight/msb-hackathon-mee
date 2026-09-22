package com.app.constant;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Business rules of the memoriser (design §5). */
public final class MemoryConstants {

  private MemoryConstants() {}

  // ── Kafka (design §5) ───────────────────────────────────────────────────────
  public static final String CONSUMER_GROUP = "memory-hook";
  public static final String TOPIC_MEMORY_UPDATED = "msb.memory.updated";
  public static final String TOPIC_DLQ = "msb.events.dlq.memory";
  public static final String EVENT_MEMORY_UPDATED = "msb.memory.updated.v1";

  /** Producers we accept events from; anything else is dropped as a poisoning attempt. */
  public static final List<String> ALLOWED_SOURCES =
      List.of(
          "/msb/account-service",
          "/msb/transfer-service",
          "/msb/saving-service",
          "/msb/lending-service",
          "/msb/card-service",
          "/msb/bill-payment-service",
          "/msb/onboarding-service",
          "/msb/mi-assistant-service");

  // ── record kinds, sources, classifications ─────────────────────────────────
  public static final String KIND_NICKNAME = "NICKNAME";
  public static final String KIND_HABIT = "HABIT";
  public static final String KIND_PREFERENCE = "PREFERENCE";
  public static final String KIND_GOAL = "GOAL";
  public static final String KIND_FACT = "FACT";
  public static final String KIND_CONCERN = "CONCERN";
  public static final String KIND_LIFESTYLE = "LIFESTYLE";
  public static final String KIND_RELATIONSHIP = "RELATIONSHIP";
  public static final String KIND_PLAN = "PLAN";
  public static final String KIND_PRODUCT_PREFERENCE = "PRODUCT_PREFERENCE";
  public static final String KIND_EVENT = "EVENT";
  public static final String KIND_TRANSACTION_CONTEXT = "TRANSACTION_CONTEXT";
  public static final String KIND_SENSITIVE = "SENSITIVE";
  public static final List<String> KINDS =
      List.of(
          KIND_NICKNAME, KIND_HABIT, KIND_PREFERENCE, KIND_GOAL, KIND_FACT,
          KIND_CONCERN, KIND_LIFESTYLE, KIND_RELATIONSHIP, KIND_PLAN,
          KIND_PRODUCT_PREFERENCE);
  public static final List<String> KINDS_NOT_STORED =
      List.of(KIND_EVENT, KIND_TRANSACTION_CONTEXT, KIND_SENSITIVE);

  // ── epistemic states (BRD §4 lifecycle) ─────────────────────────────────────
  public static final String STATE_SIGNAL = "SIGNAL";
  public static final String STATE_CANDIDATE = "CANDIDATE";
  public static final String STATE_HYPOTHESIS = "HYPOTHESIS";
  public static final String STATE_CONFIRMED = "CONFIRMED";
  public static final String STATE_REJECTED = "REJECTED";
  public static final String STATE_INACTIVE = "INACTIVE";
  public static final String STATE_EXPIRED = "EXPIRED";
  public static final String STATE_FORGOTTEN = "FORGOTTEN";
  public static final List<String> STATES =
      List.of(
          STATE_SIGNAL, STATE_CANDIDATE, STATE_HYPOTHESIS, STATE_CONFIRMED,
          STATE_REJECTED, STATE_INACTIVE, STATE_EXPIRED, STATE_FORGOTTEN);
  public static final List<String> STATES_RECALLABLE = List.of(STATE_CONFIRMED);
  public static final List<String> STATES_GRAPH =
      List.of(STATE_CONFIRMED, STATE_HYPOTHESIS, STATE_INACTIVE, STATE_EXPIRED);

  // ── persistence levels (BRD §6.2) ──────────────────────────────────────────
  public static final String PERSIST_TEMPORARY = "TEMPORARY";
  public static final String PERSIST_MEDIUM_TERM = "MEDIUM_TERM";
  public static final String PERSIST_LONG_TERM = "LONG_TERM";
  public static final String PERSIST_RESTRICTED = "RESTRICTED";

  // ── memory link relations (BRD §6.4) ───────────────────────────────────────
  public static final String LINK_REASON_FOR = "REASON_FOR";
  public static final String LINK_RELATED_TO = "RELATED_TO";
  public static final String LINK_PART_OF_GOAL = "PART_OF_GOAL";
  public static final List<String> LINK_RELATIONS =
      List.of(LINK_REASON_FOR, LINK_RELATED_TO, LINK_PART_OF_GOAL);

  // ── hypothesis suppression after rejection ─────────────────────────────────
  public static final Duration SUPPRESSION_AFTER_REJECT = Duration.ofDays(90);
  public static final int HYPOTHESES_PER_SESSION = 1;

  public static final String SOURCE_EVENTS = "EVENTS";
  public static final String SOURCE_CHAT = "CHAT";
  public static final String SOURCE_SUMMARY = "SUMMARY";

  public static final String CLASS_INTERNAL = "INTERNAL";
  public static final String CLASS_CONFIDENTIAL = "CONFIDENTIAL";
  public static final String CLASS_SENSITIVE = "SENSITIVE";

  // ── thresholds ─────────────────────────────────────────────────────────────
  public static final BigDecimal CONFIDENCE_MIN = new BigDecimal("0.700");
  public static final BigDecimal CONFIDENCE_DEFAULT = new BigDecimal("0.800");
  public static final int CHAT_RECORDS_PER_DAY = 20;
  public static final int RECORD_TEXT_MAX = 200;
  public static final int RECALL_QUESTION_MAX = 500;
  public static final int RECALL_TOP_K = 8;
  public static final int RECALL_TOP_K_MAX = 20;
  public static final int RECALL_RATE_PER_MINUTE = 60;
  public static final int EMBEDDING_DIM = 384;

  // ── retention (design §4 "Retention", BRD §6.2) ────────────────────────────
  public static final Duration TTL_HABIT = Duration.ofDays(365);
  public static final Duration TTL_PREFERENCE = Duration.ofDays(365);
  public static final Duration TTL_FACT = Duration.ofDays(180);
  public static final Duration TTL_GOAL = Duration.ofDays(180);
  public static final Duration TTL_NICKNAME = Duration.ofDays(365);
  public static final Duration TTL_LIFESTYLE = Duration.ofDays(365);
  public static final Duration TTL_RELATIONSHIP = Duration.ofDays(365);
  public static final Duration TTL_PRODUCT_PREFERENCE = Duration.ofDays(365);
  public static final Duration TTL_CONCERN = Duration.ofDays(180);
  public static final Duration TTL_PLAN = Duration.ofDays(60);
  public static final Duration SNAPSHOT_WINDOW = Duration.ofDays(400);
  public static final Duration AUDIT_RETENTION = Duration.ofDays(730);
  public static final Duration INGEST_LOG_RETENTION = Duration.ofDays(30);
  public static final Duration ERASURE_SLA = Duration.ofHours(24);
  public static final Duration ERASURE_TOMBSTONE_RETENTION = Duration.ofDays(30);

  public static Duration ttlFor(String kind) {
    return switch (kind) {
      case KIND_HABIT -> TTL_HABIT;
      case KIND_PREFERENCE -> TTL_PREFERENCE;
      case KIND_GOAL -> TTL_GOAL;
      case KIND_NICKNAME -> TTL_NICKNAME;
      case KIND_LIFESTYLE -> TTL_LIFESTYLE;
      case KIND_RELATIONSHIP -> TTL_RELATIONSHIP;
      case KIND_PRODUCT_PREFERENCE -> TTL_PRODUCT_PREFERENCE;
      case KIND_CONCERN -> TTL_CONCERN;
      case KIND_PLAN -> TTL_PLAN;
      default -> TTL_FACT;
    };
  }

  public static String persistenceFor(String kind) {
    return switch (kind) {
      case KIND_PLAN -> PERSIST_MEDIUM_TERM;
      case KIND_EVENT, KIND_TRANSACTION_CONTEXT -> PERSIST_TEMPORARY;
      case KIND_SENSITIVE -> PERSIST_RESTRICTED;
      default -> PERSIST_LONG_TERM;
    };
  }

  // ── extraction rules ───────────────────────────────────────────────────────
  public static final int RECURRING_MIN_OCCURRENCES = 3;
  public static final BigDecimal RECURRING_AMOUNT_TOLERANCE = new BigDecimal("0.10");
  public static final BigDecimal SALARY_SHARE_MIN = new BigDecimal("0.6");
  public static final int SALARY_MIN_OCCURRENCES = 2;
  public static final int DISMISSALS_FOR_PREFERENCE = 3;
  public static final int RECURRING_DAY_TOLERANCE = 3;

  // ── money buckets, so prompts never carry an exact balance ─────────────────
  public static final List<String> MONEY_BUCKETS =
      List.of("<1M", "1M-5M", "5M-10M", "10M-20M", "20M-50M", "50M-100M", ">100M");
  public static final List<BigDecimal> MONEY_BUCKET_CEILINGS =
      List.of(
          new BigDecimal("1000000"),
          new BigDecimal("5000000"),
          new BigDecimal("10000000"),
          new BigDecimal("20000000"),
          new BigDecimal("50000000"),
          new BigDecimal("100000000"));

  // ── DLP (design §4 "LLM boundary", §10) ────────────────────────────────────
  public static final int DIGIT_RUN_MAX = 7;
  public static final List<String> SENSITIVE_PATTERNS =
      List.of(
          "bệnh", "thuốc", "tôn giáo", "đảng", "giới tính", "mang thai", "hiv", "ung thư",
          "hospital", "religion", "pregnan", "diagnos", "political");
  public static final String REDACTION = "[…]";

  // ── fact types kept in event_fact ──────────────────────────────────────────
  public static final String FACT_TRANSFER = "TRANSFER";
  public static final String FACT_INCOME = "INCOME";
  public static final String FACT_BILL = "BILL";
  public static final String FACT_SPEND = "SPEND";
  public static final String FACT_DEPOSIT = "DEPOSIT";
  public static final String FACT_LOAN = "LOAN";
  public static final String FACT_GOAL = "GOAL";
  public static final String FACT_NUDGE_DISMISSED = "NUDGE_DISMISSED";
  public static final String FACT_CARD_DUE = "CARD_DUE";
  public static final String FACT_BALANCE = "BALANCE";

  // ── ingest outcomes written to ingest_log ──────────────────────────────────
  public static final String INGEST_PROCESSED = "PROCESSED";
  public static final String INGEST_DUPLICATE = "DUPLICATE";
  public static final String INGEST_SKIPPED = "SKIPPED_NO_CONSENT";
  public static final String INGEST_DROPPED_SENSITIVE = "DROPPED_SENSITIVE";
  public static final String INGEST_REJECTED = "REJECTED";

  // ── erasure ────────────────────────────────────────────────────────────────
  public static final String ERASURE_QUEUED = "QUEUED";
  public static final String ERASURE_RUNNING = "RUNNING";
  public static final String ERASURE_DONE = "DONE";
  public static final List<String> ERASURE_REASONS =
      List.of("CUSTOMER_REQUEST", "CONSENT_WITHDRAWN", "ACCOUNT_CLOSED", "OPS");

  // ── record rejection reasons (MH-003 details.reason) ───────────────────────
  public static final String REJECT_PII = "PII";
  public static final String REJECT_SENSITIVE = "SENSITIVE";
  public static final String REJECT_LENGTH = "LENGTH";
  public static final String REJECT_SCHEMA = "SCHEMA";
  public static final String REJECT_REF_NOT_OWNED = "REF_NOT_OWNED";

  // ── consent ────────────────────────────────────────────────────────────────
  public static final String POLICY_VERSION_DEFAULT = "2026-09-01";
  public static final List<String> CONSENT_SOURCES = List.of("MI_SETTINGS", "ONBOARDING", "OPS");

  // ── snapshot slices ────────────────────────────────────────────────────────
  public static final String SLICE_PROMPT = "PROMPT";
  public static final String SLICE_TRIGGER = "TRIGGER";
  public static final String HDR_MEMORY_SLICE = "X-Memory-Slice";
  public static final String TRIGGER_SLICE_CALLER = "mi-assistant-service";

  /** Spend categories mirrored from account-service, used for the snapshot shares. */
  public static final List<String> CATEGORIES =
      List.of("FOOD", "SHOPPING", "BILLS", "TRANSPORT", "ENTERTAINMENT", "HEALTH", "OTHER");

  /** Event type → the fact it distils to. Anything absent is MH-007 and goes to the DLQ. */
  public static final Map<String, String> EVENT_FACTS =
      Map.ofEntries(
          Map.entry("msb.account.ledgerPosted.v1", FACT_SPEND),
          Map.entry("msb.account.salary.credited.v1", FACT_INCOME),
          Map.entry("msb.transfer.completed.v1", FACT_TRANSFER),
          Map.entry("msb.saving.depositOpened.v1", FACT_DEPOSIT),
          Map.entry("msb.saving.goalProgressed.v1", FACT_GOAL),
          Map.entry("msb.lending.loanDisbursed.v1", FACT_LOAN),
          Map.entry("msb.lending.instalmentDue.v1", FACT_LOAN),
          Map.entry("msb.card.authorised.v1", FACT_SPEND),
          Map.entry("msb.card.statementClosed.v1", FACT_CARD_DUE),
          Map.entry("msb.bill.paid.v1", FACT_BILL),
          Map.entry("msb.bill.due.v1", FACT_BILL),
          Map.entry("msb.onboarding.completed.v1", FACT_GOAL),
          Map.entry("msb.mi.proposalExecuted.v1", FACT_TRANSFER),
          Map.entry("msb.mi.conversation.closed.v1", FACT_GOAL),
          Map.entry("msb.mi.nudgeDismissed.v1", FACT_NUDGE_DISMISSED));

  public static final String EVENT_CONVERSATION_CLOSED = "msb.mi.conversation.closed.v1";
  public static final String EVENT_NUDGE_DISMISSED = "msb.mi.nudgeDismissed.v1";
  public static final String EVENT_SALARY = "msb.account.salary.credited.v1";
  public static final String EVENT_TRANSFER_COMPLETED = "msb.transfer.completed.v1";
  public static final String EVENT_BILL_PAID = "msb.bill.paid.v1";
  public static final String EVENT_GOAL_PROGRESSED = "msb.saving.goalProgressed.v1";
}
