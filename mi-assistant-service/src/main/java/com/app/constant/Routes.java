package com.app.constant;

/** Paths (api/mi-assistant-service-api.md). */
public final class Routes {

  private Routes() {}

  public static final String MI = AppConstants.API_V1 + "/mi";

  public static final String CONVERSATIONS = MI + "/conversations";
  public static final String CONVERSATION_CURRENT = CONVERSATIONS + "/current";
  public static final String CONVERSATION_MESSAGES = CONVERSATIONS + "/{id}/messages";
  public static final String CONVERSATION_FEEDBACK = CONVERSATIONS + "/{id}/feedback";
  public static final String DATA_USAGE = MI + "/data-usage";
  public static final String AGENTS = MI + "/agents";

  public static final String PROPOSAL = MI + "/proposals/{id}";
  public static final String PROPOSAL_CONFIRM = PROPOSAL + "/confirm";
  public static final String PROPOSAL_CANCEL = PROPOSAL + "/cancel";

  public static final String SETTINGS = MI + "/settings";
  public static final String SETTINGS_PAUSE = SETTINGS + "/pause";
  public static final String SETTINGS_RESUME = SETTINGS + "/resume";
  public static final String ACTIVITY = MI + "/activity";
  public static final String INSIGHTS = MI + "/insights";

  public static final String MEMORY = MI + "/memory";
  public static final String MEMORY_RECORD = MEMORY + "/{id}";
  public static final String MEMORY_CONSENT = MEMORY + "/consent";
  public static final String MEMORY_CANDIDATES = MEMORY + "/candidates";
  public static final String MEMORY_CONFIRM = MEMORY_RECORD + "/confirm";
  public static final String MEMORY_REJECT = MEMORY_RECORD + "/reject";
  public static final String MEMORY_DEACTIVATE = MEMORY_RECORD + "/deactivate";
  public static final String MEMORY_FORGET = MEMORY_RECORD + "/forget";
  public static final String MEMORY_HYPOTHESES = MEMORY + "/hypotheses";
  public static final String MEMORY_GRAPH = MEMORY + "/graph";
  public static final String MEMORY_WHY = MEMORY_RECORD + "/why";
  public static final String CONSENTS = MI + "/consents";

  public static final String PLANS = MI + "/plans";
  public static final String PLAN = PLANS + "/{id}";
  public static final String PLAN_APPROVE = PLAN + "/approve";
  public static final String PLAN_PAUSE = PLAN + "/pause";
  public static final String PLAN_RESUME = PLAN + "/resume";
  public static final String PLAN_CANCEL = PLAN + "/cancel";

  public static final String NUDGES = MI + "/nudges";
  public static final String NUDGE_DISMISS = NUDGES + "/{id}/dismiss";
  public static final String EXPLAIN = MI + "/explain/{decisionId}";

  // ── internal ───────────────────────────────────────────────────────────────
  public static final String INTERNAL_ACTIVITY = AppConstants.INTERNAL + "/activity";
  public static final String INTERNAL_DECIDE =
      AppConstants.INTERNAL + "/autonomy/{customerId}/decide";
  public static final String INTERNAL_INSIGHTS = AppConstants.INTERNAL + "/insights/{customerId}";
  public static final String INTERNAL_KNOWLEDGE_EVENTS =
      AppConstants.INTERNAL + "/knowledge/events";
  public static final String INTERNAL_KNOWLEDGE_REINDEX =
      AppConstants.INTERNAL + "/knowledge/reindex";
  public static final String INTERNAL_AGENT_EVENTS = AppConstants.INTERNAL + "/agents/events";
  public static final String INTERNAL_TRIGGER_EVENTS = AppConstants.INTERNAL + "/triggers/events";
  public static final String INTERNAL_MODEL_PROFILE_EVENTS =
      AppConstants.INTERNAL + "/model-profiles/events";
  public static final String INTERNAL_RETRIEVAL_PREVIEW =
      AppConstants.INTERNAL + "/retrieval/preview";
  public static final String INTERNAL_FEATURE_FLAGS = AppConstants.INTERNAL + "/feature-flags";
  public static final String INTERNAL_EVAL_RUN = AppConstants.INTERNAL + "/eval/run";
  public static final String INTERNAL_MCP_SERVERS = AppConstants.INTERNAL + "/mcp/servers";
  public static final String INTERNAL_MASKED_TRANSCRIPT =
      AppConstants.INTERNAL + "/conversations/{id}/masked-transcript";

  public static final String SCOPE_MI_INSIGHTS = "SCOPE_mi:insights";
  public static final String SCOPE_MI_ACTIVITY = "SCOPE_mi:activity";
  public static final String SCOPE_MI_TRANSCRIPT = "SCOPE_mi:transcript";
  public static final String SCOPE_CMS_HOOK = "SCOPE_mi:knowledge";
}
