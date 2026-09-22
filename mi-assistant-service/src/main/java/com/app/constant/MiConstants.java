package com.app.constant;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Business rules of the three Mi levels (design §3). */
public final class MiConstants {

  private MiConstants() {}

  // ── Level 1 · retrieval and generation ─────────────────────────────────────
  public static final int RAG_TOP_K = 6;
  public static final double RAG_SIMILARITY_THRESHOLD = 0.72;
  public static final int RAG_CHUNK_TOKENS = 400;
  public static final int RAG_CHUNK_OVERLAP = 60;
  /** Rough characters-per-token for Vietnamese; only used to size chunks, never billed on. */
  public static final int CHARS_PER_TOKEN = 4;
  public static final int SUGGESTION_COUNT = 4;
  public static final int CHAT_HISTORY_WINDOW = 12;
  public static final int LLM_MAX_TOKENS = 600;
  public static final double LLM_TEMPERATURE_DEFAULT = 0.2;
  public static final int CITATION_MAX = 3;
  public static final String COLLECTION_GENERAL = "general";
  public static final String COLLECTION_HOWTO = "howto";
  /** Width of {@code document_chunk.embedding}; both embedding providers emit this. */
  public static final int EMBEDDING_DIM = 1024;

  // ── Level 2 · Chat Pay ─────────────────────────────────────────────────────
  public static final List<BigDecimal> AUTO_LIMIT_STEPS =
      List.of(
          new BigDecimal("500000"),
          new BigDecimal("1000000"),
          new BigDecimal("2000000"),
          new BigDecimal("5000000"),
          new BigDecimal("10000000"));
  public static final BigDecimal AUTO_LIMIT_DEFAULT = new BigDecimal("2000000");
  public static final Duration PROPOSAL_TTL = Duration.ofMinutes(10);
  public static final int BUDGET_ALERT_PCT_DEFAULT = 80;
  public static final int INSIGHT_TIMEOUT_MS = 300;

  public static final String TYPE_TRANSFER = "TRANSFER";
  public static final String TYPE_OPEN_DEPOSIT = "OPEN_DEPOSIT";
  public static final String TYPE_PAY_BILLS = "PAY_BILLS";
  public static final String TYPE_GOAL_TOPUP = "GOAL_TOPUP";
  public static final String TYPE_SET_BUDGET = "SET_BUDGET";
  public static final String TYPE_CARD_PAY = "CARD_PAY";
  public static final String TYPE_LOAN_REPAY = "LOAN_REPAY";
  /**
   * Opening a card. Carries no {@code amount} — issuing a card moves no money — and is never
   * eligible for AUTO regardless of permissions or limits (design 07 §3.2).
   */
  public static final String TYPE_CARD_ISSUE = "CARD_ISSUE";

  public static final String PHASE_PROPOSE = "PROPOSE";
  public static final String PHASE_EXEC = "EXEC";
  public static final String PHASE_DONE = "DONE";
  public static final String PHASE_CANCEL = "CANCEL";
  public static final String PHASE_EXPIRED = "EXPIRED";
  public static final String PHASE_BLOCKED = "BLOCKED";

  public static final String EXECUTED_BY_USER = "USER_STEP_UP";
  public static final String EXECUTED_BY_AUTO = "AUTO";

  public static final String DECISION_AUTO = "AUTO";
  public static final String DECISION_CONFIRM = "REQUIRE_CONFIRM";
  public static final String REASON_PAUSED = "PAUSED";
  public static final String REASON_PERMISSION_OFF = "PERMISSION_OFF";
  public static final String REASON_OVER_LIMIT = "OVER_LIMIT";
  /**
   * Why a {@code CARD_ISSUE} is never AUTO: no amount-based permission can stand in for consent to
   * a credit product with fees and a bureau footprint (design 07 §3.2).
   */
  public static final String REASON_PRODUCT_CONSENT = "PRODUCT_CONSENT_REQUIRED";
  public static final String REASON_OK = "OK";

  public static final String LOG_AUTO = "AUTO";
  public static final String LOG_CONFIRM = "CONFIRM";
  public static final String LOG_BLOCKED = "BLOCKED";
  public static final String LOG_INSIGHT = "INSIGHT";
  /** Safety refusals are logged but never shown in the customer's activity list (design §12.4). */
  public static final String LOG_BLOCKED_SAFETY = "BLOCKED_SAFETY";

  public static final List<String> PLACEMENTS =
      List.of("HOME", "TRANSFER_REVIEW", "SAVING_OVERVIEW", "LENDING_OVERVIEW", "CARD_WALLET");

  /** Query keys the app should refresh once a proposal of this type completes. */
  public static final Map<String, List<String>> INVALIDATES =
      Map.of(
          TYPE_TRANSFER, List.of("home", "transactions", "accounts"),
          TYPE_PAY_BILLS, List.of("home", "bills", "transactions"),
          TYPE_OPEN_DEPOSIT, List.of("home", "savings"),
          TYPE_GOAL_TOPUP, List.of("home", "savings"),
          TYPE_CARD_PAY, List.of("home", "cards", "transactions"),
          TYPE_LOAN_REPAY, List.of("home", "loans"),
          TYPE_CARD_ISSUE, List.of("home", "cards"),
          TYPE_SET_BUDGET, List.of("home"));

  /**
   * Which tool argument an on-screen entity fills when the model left it out (M3.12). Keys mirror
   * the app's {@code MiEntityType}; values are the argument names the tools actually read.
   */
  public static final Map<String, String> ENTITY_ARG_BY_TYPE =
      Map.of(
          "LOAN", "loanId",
          "CARD", "cardId",
          "GOAL", "goalId",
          "DEPOSIT", "depositId",
          "ACCOUNT", "accountId",
          "BILL", "billId",
          "BENEFICIARY", "beneficiaryId");

  // ── Level 3 · multi-agent ───────────────────────────────────────────────────
  public static final double ROUTER_CONFIDENCE_MIN = 0.6;
  public static final int HANDOFF_SUMMARY_TOKENS = 200;
  /**
   * Headers the VRM agent's AgentBase runtime requires; it refuses the call without both, and threads
   * its memory on them. mi sends the customer id and the conversation id, which is what makes the
   * agent's memory line up with mi's own conversation.
   */
  public static final String HDR_AGENTBASE_USER = "X-GreenNode-AgentBase-User-Id";

  public static final String HDR_AGENTBASE_SESSION = "X-GreenNode-AgentBase-Session-Id";

  public static final int MAX_AGENT_HOPS_PER_TURN = 2;
  public static final Duration AGENT_CACHE_TTL = Duration.ofSeconds(30);
  public static final Duration WORKING_MEMORY_TTL = Duration.ofMinutes(30);
  public static final Duration RECALL_CACHE_TTL = Duration.ofMinutes(5);
  public static final Duration NUDGE_TTL = Duration.ofDays(3);
  public static final Duration PLAN_TTL = Duration.ofDays(7);

  public static final String PLAN_ACTIVE = "ACTIVE";
  public static final String PLAN_DONE = "DONE";
  public static final String PLAN_CANCELLED = "CANCELLED";
  public static final String PLAN_PAUSED = "PAUSED";
  public static final String STEP_PENDING = "PENDING";
  public static final String STEP_RUNNING = "RUNNING";
  public static final String STEP_DONE = "DONE";
  public static final String STEP_FAILED = "FAILED";
  public static final String STEP_WAITING = "WAITING";

  // ── security (design §12) ──────────────────────────────────────────────────
  public static final int TURNS_PER_MINUTE = 20;
  public static final int TURNS_PER_DAY = 200;
  public static final int INPUT_MAX_CHARS = 1000;
  public static final int ILLEGAL_FLAG_THRESHOLD_24H = 3;
  public static final String UNTRUSTED_TAG = "untrusted";
  public static final List<String> URL_ALLOWLIST = List.of("msb://", "https://msb.com.vn");

  public static final String SAFETY_OK = "OK";
  public static final String SAFETY_REFUSED = "REFUSED";
  public static final String SAFETY_GUARDED = "GUARDED";

  public static final String LABEL_SAFE = "SAFE";
  public static final String LABEL_INJECTION = "INJECTION";
  public static final String LABEL_ILLEGAL = "ILLEGAL";
  public static final String LABEL_SELF_HARM = "SELF_HARM";
  public static final String LABEL_OFF_TOPIC = "OFF_TOPIC";

  // ── advance mode (guideline 09) ─────────────────────────────────────────────
  public static final String MODE_STANDARD = "STANDARD";
  public static final String MODE_ADVANCE = "ADVANCE";
  /** Step kinds the app renders in the progress list. */
  public static final String STEP_KIND_TOOL = "TOOL";
  public static final String STEP_KIND_KNOWLEDGE = "KNOWLEDGE";
  public static final String STEP_KIND_MEMORY = "MEMORY";
  /**
   * How much gathered material the composer is given. A composed answer is only as trustworthy as
   * the data behind it, and an over-long prompt starts losing the middle of it.
   */
  public static final int ADVANCE_MAX_CONTEXT_CHARS = 6000;
  public static final int ADVANCE_MAX_KNOWLEDGE_CHUNKS = 4;

  /** Case- and diacritic-insensitive markers of an instruction aimed at the assistant. */
  public static final List<String> INJECTION_MARKERS =
      List.of(
          "ignore previous",
          "ignore all previous",
          "disregard the above",
          "bo qua huong dan",
          "bo qua cac quy tac",
          "you are now",
          "ban bay gio la",
          "system prompt",
          "prompt he thong",
          "developer mode",
          "che do nha phat trien",
          "act as",
          "dong vai",
          "reveal your instructions",
          "in ra prompt",
          "jailbreak",
          "do anything now");

  /** Requests Mi refuses outright, with the reason key used for the canned reply. */
  public static final Map<String, String> ILLEGAL_MARKERS =
      Map.ofEntries(
          Map.entry("rua tien", "laundering"),
          Map.entry("launder", "laundering"),
          Map.entry("tranh bao cao", "structuring"),
          Map.entry("chia nho giao dich", "structuring"),
          Map.entry("structuring", "structuring"),
          Map.entry("lua dao", "fraud"),
          Map.entry("kich ban lua", "fraud"),
          Map.entry("scam script", "fraud"),
          Map.entry("vuot otp", "securityBypass"),
          Map.entry("bypass otp", "securityBypass"),
          Map.entry("bypass pin", "securityBypass"),
          Map.entry("hack tai khoan", "accountAccess"),
          Map.entry("tai khoan nguoi khac", "accountAccess"),
          Map.entry("sao ke gia", "forgery"),
          Map.entry("fake statement", "forgery"));

  public static final List<String> SELF_HARM_MARKERS =
      List.of("tu tu", "tu sat", "kill myself", "suicide", "end my life");

  /** Secrets the customer must never send and Mi must never store (design §12.2). */
  public static final List<String> SECRET_MARKERS =
      List.of("cvv", "mat khau", "password", "ma otp", "otp la", "so pin", "pin cua toi");

  // ── deep links the reply guard accepts (mirrors the app's route table) ─────
  public static final List<String> DEEP_LINK_ROUTES =
      List.of(
          "msb://home",
          "msb://transfer",
          "msb://transactions",
          "msb://savings",
          "msb://goals",
          "msb://loans",
          "msb://cards",
          "msb://bills",
          "msb://rewards",
          "msb://mi",
          "msb://mi/settings",
          "msb://mi/activity",
          "msb://mi/memory",
          "msb://profile");

  // ── SSE event names (api contract) ─────────────────────────────────────────
  public static final String SSE_MESSAGE_USER = "message.user";
  public static final String SSE_TYPING = "typing";
  public static final String SSE_MESSAGE_MI = "message.mi";
  public static final String SSE_STEPS = "steps";
  public static final String SSE_CHART = "chart";
  public static final String SSE_CARD = "card";
  public static final String SSE_CARD_UPDATE = "card.update";
  public static final String SSE_CHIPS = "chips";
  public static final String SSE_BLOCK = "block";
  public static final String SSE_BLOCK_UPDATE = "block.update";
  /** Advance mode reports each read as it lands, so a multi-second turn is not a blank screen. */
  public static final String SSE_PLAN_STEP = "plan.step";
  public static final String SSE_ERROR = "error";
  public static final String SSE_DONE = "done";
  public static final Duration SSE_TIMEOUT = Duration.ofMinutes(2);

  public static final String KIND_TEXT = "TEXT";
  public static final String KIND_TYPING = "TYPING";
  public static final String KIND_CARD = "CARD";
  public static final String KIND_CHART = "CHART";
  public static final String KIND_STEPS = "STEPS";

  public static final String ROLE_USER = "USER";
  public static final String ROLE_MI = "MI";

  public static final String BLOCKS_VERSION = "1.2";
  public static final String HDR_BLOCKS = "X-Mi-Blocks";
  public static final String HDR_MEMORY_SLICE = "X-Memory-Slice";
  public static final String SLICE_PROMPT_NAME = "PROMPT";
  public static final String SLICE_TRIGGER_NAME = "TRIGGER";
  public static final String HDR_WEBHOOK_SIGNATURE = "X-Webhook-Signature";

  public static final int EVAL_MAX_CASES = 200;
  public static final int ACTIVITY_PAGE_SIZE = 30;
  public static final int MASKED_TRANSCRIPT_MAX_TURNS = 100;
  public static final String POLICY_NAME = "mi.autonomy";
  public static final String POLICY_VERSION = "2026-09-01";

  // ── MEE relevance gate (BRD §6.2, Principle 5, UC06) ───────────────────────
  public static final String INTENT_SERVICE = "SERVICE";
  public static final String INTENT_TRANSACTIONAL = "TRANSACTIONAL";
  public static final String INTENT_INFORMATION = "INFORMATION";
  public static final String INTENT_PLANNING = "PLANNING";
  public static final String INTENT_SMALL_TALK = "SMALL_TALK";

  public static final List<String> INTENT_SERVICE_MARKERS =
      List.of(
          "khoa tai khoan", "khoa the", "mo khoa", "quen mat khau", "that bai", "loi",
          "khong nhan duoc otp", "chuyen tien loi", "khong vao duoc", "bi khoa",
          "can cuoc", "bao mat", "phat hien giao dich", "khach hang khong phai toi",
          "locked", "failed", "error", "fraud", "complaint", "khieu nai");

  public static final List<String> INTENT_INFORMATION_MARKERS =
      List.of("lai suat", "phi", "ky han", "menh gia", "gioi han", "dieu kien",
          "interest rate", "fee", "how much", "bao nhieu");

  public static final Map<String, List<String>> MEMORY_KINDS_BY_INTENT =
      Map.of(
          INTENT_SERVICE, List.of(),
          INTENT_TRANSACTIONAL, List.of("NICKNAME", "HABIT"),
          INTENT_INFORMATION, List.of(),
          INTENT_PLANNING,
              List.of("GOAL", "PLAN", "CONCERN", "PREFERENCE", "LIFESTYLE",
                  "PRODUCT_PREFERENCE", "RELATIONSHIP"),
          INTENT_SMALL_TALK, List.of("PREFERENCE", "LIFESTYLE"));

  public static String memoryIntent(String domain, String text) {
    if (text != null) {
      String lower = java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT),
              java.text.Normalizer.Form.NFD)
          .replaceAll("\\p{M}", "");
      if (INTENT_SERVICE_MARKERS.stream().anyMatch(lower::contains)) {
        return INTENT_SERVICE;
      }
      if (INTENT_INFORMATION_MARKERS.stream().anyMatch(lower::contains)) {
        return INTENT_INFORMATION;
      }
    }
    return switch (domain == null ? "" : domain.toUpperCase(java.util.Locale.ROOT)) {
      case "PAYMENT" -> INTENT_TRANSACTIONAL;
      case "LOAN", "SAVING" -> INTENT_PLANNING;
      case "CARD" -> INTENT_SERVICE;
      default -> INTENT_SMALL_TALK;
    };
  }
}
