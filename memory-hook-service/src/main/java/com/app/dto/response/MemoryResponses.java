package com.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response bodies of the internal API. Only abstract text ever crosses this boundary. */
public final class MemoryResponses {

  private MemoryResponses() {}

  /** {@code refs} carries ids Mi needs to act (a beneficiary, a goal) — never account numbers. */
  public record MemoryRecordDto(
      UUID id,
      String kind,
      String text,
      BigDecimal confidence,
      boolean pinned,
      String source,
      LocalDate validUntil,
      Instant createdAt,
      Map<String, String> refs,
      String state,
      boolean explicit,
      boolean customerConfirmed,
      Instant lastConfirmedAt,
      String persistence,
      String entity,
      String evidenceSummary) {}

  public record ScoredRecord(MemoryRecordDto record, double score) {}

  public record ConsentFlags(boolean longTerm) {}

  public record RecallResponse(
      ConsentFlags consent, List<MemoryRecordDto> pinned, List<ScoredRecord> matches) {}

  public record CandidateDecisionResponse(String decision, MemoryRecordDto record) {}

  public record MemoryLinkDto(
      UUID id, UUID fromRecord, UUID toRecord, String relation, String state,
      Instant createdAt, Instant confirmedAt) {}

  public record MemoryGraphResponse(List<MemoryRecordDto> nodes, List<MemoryLinkDto> links) {}

  public record WhyResponse(UUID recordId, String kind, String entity,
      String evidenceSummary, String reason, Instant createdAt) {}

  public record UpcomingDue(String type, int dayOfMonth, String amountBucket) {}

  public record ProductCounts(int deposits, int goals, int cards, int loans) {}

  /** Bucketed and prompt-safe: nothing here identifies an account or an exact balance. */
  public record SnapshotPromptSlice(
      Integer payDay,
      String incomeBucket,
      String idleBalanceBucket,
      Map<String, Integer> spendByCategoryPct,
      int spendDeltaPct,
      List<UpcomingDue> upcomingDues,
      ProductCounts products,
      List<String> preferences,
      Instant updatedAt) {}

  public record ExactDue(String type, LocalDate dueOn, BigDecimal amount) {}

  public record GoalProgress(String goalId, int pct, int behindPct) {}

  public record NudgeHistory(String triggerCode, Instant at, boolean dismissed) {}

  /** Exact numbers, identifiers excluded — the proactive policy engine only. */
  public record SnapshotTriggerSlice(
      BigDecimal idleBalance,
      BigDecimal monthSpend,
      Map<String, Integer> categoryShares,
      List<ExactDue> dues,
      List<GoalProgress> goalProgress,
      List<NudgeHistory> lastNudges,
      Instant updatedAt) {}

  public record ConsentResponse(
      boolean longTerm,
      boolean snapshot,
      boolean improveModels,
      String policyVersion,
      Instant updatedAt) {}

  public record DeletedResponse(boolean deleted) {}

  public record ErasureProof(
      int records, int vectors, boolean snapshot, boolean dekDestroyed, long tombstoneOffset,
      String hash) {}

  public record ErasureResponse(
      UUID erasureId,
      String status,
      Instant requestedAt,
      Instant slaAt,
      Instant completedAt,
      ErasureProof proof) {}

  public record ExportResponse(String url, Instant expiresAt, String sha256) {}

  public record ExportPayload(
      List<MemoryRecordDto> records,
      SnapshotPromptSlice snapshot,
      ConsentResponse consent,
      List<AuditSummary> access,
      Instant generatedAt) {}

  public record AuditSummary(String actor, String operation, long reads, Instant lastAt) {}

  public record AuditRow(
      UUID id, String pseudoId, String actor, String scope, String operation, int records,
      String requestId, Instant at) {}

  public record ReplayResponse(UUID jobId, int events) {}

  public record DlqRow(
      UUID id, String eventId, String eventType, String reason, int attempts, Instant at) {}

  public record DlqActionResponse(String status) {}

  public record DiscardedResponse(boolean discarded) {}

  public record PoisoningMetrics(
      int burstCustomers,
      Map<String, Long> rejectedRecords,
      long chatCapHits,
      List<Anomaly> anomalies) {}

  public record Anomaly(String pseudoId, String reason, Instant at) {}

  public record KeyRotationResponse(int rewrapped, int failed) {}
}
