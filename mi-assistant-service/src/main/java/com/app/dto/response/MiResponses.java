package com.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything Mi returns. Shapes follow api/mi-assistant-service-api.md exactly. */
public final class MiResponses {

  private MiResponses() {}

  public record ChipDto(String text, String prompt) {}

  public record CitationDto(UUID docId, String title) {}

  public record KeyValueRow(String k, String v) {}

  public record ChartBar(String label, int pct, BigDecimal amount, String color) {}

  public record ChartDto(String title, List<ChartBar> bars) {}

  public record StepsDto(List<String> steps, String deepLink, String label) {}

  public record ProposalCardDto(
      UUID id,
      String type,
      String title,
      BigDecimal amount,
      List<KeyValueRow> rows,
      String phase,
      String statusLabel,
      boolean requiresStepUp,
      String stepUpScope,
      String footnote,
      String ref,
      Instant expiresAt,
      List<String> invalidates) {}

  public record MessageDto(
      UUID id,
      /** Echoed on the user turn so the app can reconcile its optimistic bubble (api §4b). */
      UUID clientMessageId,
      String role,
      String kind,
      String text,
      ProposalCardDto card,
      ChartDto chart,
      StepsDto steps,
      List<ChipDto> chips,
      List<CitationDto> citations,
      String agent,
      String safety,
      int seq,
      Instant createdAt) {}

  public record SuggestionDto(String text, String prompt, String icon) {}

  public record CurrentConversationResponse(
      UUID conversationId,
      List<MessageDto> messages,
      List<SuggestionDto> suggestions,
      boolean paused,
      boolean chatPayEnabled) {}

  public record NewConversationResponse(UUID conversationId, List<SuggestionDto> suggestions) {}

  public record ConversationSummaryDto(
      UUID id, String title, Instant lastMessageAt, String status) {}

  public record TurnResponse(List<MessageDto> messages, String agent) {}

  public record RecordedResponse(boolean recorded) {}

  public record DataUsageSection(String heading, String body) {}

  public record DataUsageResponse(String title, List<DataUsageSection> sections) {}

  public record AgentSummaryDto(
      String code, String name, String domain, List<SuggestionDto> quickPrompts) {}

  public record PermissionsDto(
      boolean recurringBills, boolean savedRecipients, boolean autoSaving, boolean homeInsights) {}

  public record SettingsResponse(
      PermissionsDto permissions,
      BigDecimal autoLimit,
      List<BigDecimal> autoLimitSteps,
      boolean paused,
      boolean chatPayEnabled) {}

  public record ActivityItemDto(
      UUID id,
      String kind,
      String title,
      String subtitle,
      String tag,
      String icon,
      String iconTone,
      Instant occurredAt,
      UUID proposalId,
      String ref,
      String deepLink) {}

  public record ActivityGroupDto(String day, LocalDate date, List<ActivityItemDto> items) {}

  public record InsightActionDto(String label, String deepLink) {}

  public record InsightDto(
      String placement, String text, String prompt, InsightActionDto action, String tone) {}

  public record CardUpdateDto(
      UUID id, String phase, String ref, String statusLabel, List<String> invalidates) {}

  public record DoneDto(UUID conversationId, String agent, UUID turnId) {}

  /**
   * One read an advance turn performed, streamed as it completes (guideline 09 §4). Distinct from
   * {@code PlanStepDto}, which is a step of a durable multi-day plan (M3.10): this one lives for a
   * single turn and never mutates anything. {@code label} is localised server-side because the app
   * must not invent a name for a step it does not define.
   */
  public record TurnStepDto(int seq, String kind, String label, String status) {}

  public record ErrorEventDto(String code, String message) {}

  public record BlockDto(
      String id, String type, String version, Object payload, String fallback) {}

  public record MemoryRecordDto(
      UUID id,
      String kind,
      String text,
      BigDecimal confidence,
      boolean pinned,
      Instant createdAt,
      String source,
      String state,
      boolean explicit,
      boolean customerConfirmed,
      Instant lastConfirmedAt,
      String persistence,
      String entity,
      String evidenceSummary) {}

  public record MemoryConsentDto(boolean longTerm) {}

  public record MemoryResponse(MemoryConsentDto consent, List<MemoryRecordDto> records) {}

  public record CandidateRequestDto(
      String kind, String text, String entity, String reason,
      BigDecimal confidence, Map<String, String> refs,
      UUID conversationId, UUID messageId) {}

  public record CandidateDecisionDto(String decision, MemoryRecordDto record) {}

  public record EditMemoryRequest(String text, String reason) {}

  public record MemoryLinkDto(
      UUID id, UUID fromRecord, UUID toRecord, String relation, String state,
      Instant createdAt, Instant confirmedAt) {}

  public record MemoryGraphDto(List<MemoryRecordDto> nodes, List<MemoryLinkDto> links) {}

  public record MemoryWhyDto(
      UUID recordId, String kind, String entity,
      String evidenceSummary, String reason, Instant createdAt) {}

  public record DeletedResponse(boolean deleted) {}

  public record DeletedCountResponse(int deleted) {}

  public record ConsentsResponse(
      boolean proactive,
      boolean memoryLongTerm,
      boolean improveModels,
      Map<String, String> versions) {}

  public record PlanStepDto(
      int seq,
      String kind,
      String title,
      String status,
      BigDecimal amount,
      boolean requiresApproval,
      UUID proposalId,
      String waitingFor) {}

  public record PlanLogEntry(Instant at, String text) {}

  public record PlanDto(
      UUID id,
      String title,
      String status,
      Instant createdAt,
      List<PlanStepDto> steps,
      BigDecimal totalAmount,
      boolean approveAllAllowed,
      List<PlanLogEntry> log) {}

  public record NudgeDto(
      UUID id,
      String placement,
      String text,
      String explain,
      String prompt,
      String deepLink,
      Instant createdAt,
      Instant expiresAt) {}

  public record DismissedResponse(boolean dismissed) {}

  public record ExplainInput(String label, String value) {}

  public record ExplainPolicy(String name, String version) {}

  public record ExplainModel(String profile, String version) {}

  public record ExplainResponse(
      String kind,
      String reason,
      List<ExplainInput> inputs,
      ExplainPolicy policy,
      ExplainModel model,
      Instant at) {}

  public record DecideResponse(String decision, String reason, BigDecimal autoLimit) {}

  public record IdResponse(UUID id) {}

  public record AcceptedResponse(boolean accepted) {}

  public record ReindexResponse(UUID jobId, int docs) {}

  public record FeatureFlagsResponse(boolean chatPayEnabled, boolean multiAgentEnabled) {}

  public record RetrievalChunkDto(
      UUID docId, String title, String section, double score, String content, String deepLink) {}

  public record EvalCaseResult(int index, boolean passed, Map<String, Object> actual) {}

  public record EvalRunResponse(UUID runId, int total, int passed, List<EvalCaseResult> details) {}

  public record McpServerDto(
      String code, String url, int toolCount, Instant lastSyncAt, boolean drift) {}

  public record MaskedTurnDto(
      String role,
      String text,
      String agent,
      List<MaskedToolCall> toolCalls,
      Instant createdAt) {}

  public record MaskedToolCall(String code, boolean ok) {}

  public record ResolvedAliasDto(String alias, String beneficiaryId, boolean confirmed) {}

  public record MaskedTranscriptResponse(
      UUID conversationId,
      UUID customerId,
      Instant closedAt,
      List<MaskedTurnDto> turns,
      List<ResolvedAliasDto> aliases) {}
}
