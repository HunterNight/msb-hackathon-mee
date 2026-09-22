package com.app.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal shapes: what the router, the registry and the tool layer pass between themselves. */
public final class MiInternal {

  private MiInternal() {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record LocalizedText(String vi, String en) {

    public String forLocale(java.util.Locale locale) {
      String value = "en".equals(locale.getLanguage()) ? en : vi;
      return value == null || value.isBlank() ? (vi == null ? en : vi) : value;
    }
  }

  /** Mirrors cms-service's {@code GuardrailsDto} field for field, so the JSON maps directly. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Guardrails(
      List<String> allowedTopics,
      String refusalKey,
      BigDecimal maxProposalAmount,
      boolean requireCitationForNumbers,
      int maxChipCount,
      /** Whether this agent may open a product (a card). Absent/false refuses with MI-015. */
      boolean allowIssue) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record QuickPrompt(UUID id, LocalizedText text, int sort, boolean enabled) {}

  /** An agent as the CMS defines it; Mi never hard-codes any of this (design §M3.5). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AgentSpec(
      String code,
      LocalizedText name,
      String domain,
      boolean enabled,
      int version,
      LocalizedText systemPrompt,
      String model,
      BigDecimal temperature,
      List<String> toolCodes,
      List<String> collectionCodes,
      Guardrails guardrails,
      List<QuickPrompt> quickPrompts,
      java.time.Instant updatedAt,
      String updatedBy) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record RouteDecision(String domain, double confidence, String summary) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record RouterDomain(String agentCode, LocalizedText label) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record RouterFewShot(String utterance, String domain) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record RouterConfig(
      BigDecimal confidenceMin,
      List<RouterDomain> domains,
      List<RouterFewShot> fewShots,
      int version) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record ToolSpec(
      String code,
      LocalizedText description,
      String beanName,
      String serverCode,
      String toolName,
      Map<String, Object> jsonSchema,
      boolean mutating,
      String stepUpScope,
      boolean enabled,
      List<String> usedByAgents) {}

  /** A retrieved passage, already tagged with its document so the citation survives the model. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RetrievedChunk(
      UUID docId,
      String title,
      String section,
      String content,
      String deepLink,
      String collectionCode,
      double score) {}

  /** The result of one tool invocation, as the orchestrator sees it. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ToolResult(
      String code, boolean ok, Object data, String errorCode, long latencyMs) {}

  /** What the model layer returns: text plus, optionally, one validated tool call. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ModelReply(
      String text, String toolCode, Map<String, Object> toolArgs, String modelProfile) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record TriggerRule(
      String code,
      List<String> eventTypes,
      String conditionExpr,
      int cooldownHours,
      int dailyCap,
      String templateCode,
      LocalizedText explainText,
      String agentCode,
      boolean enabled) {}

  @JsonIgnoreProperties(ignoreUnknown = true)

  public record ModelProfile(
      String taskClass,
      String model,
      BigDecimal temperature,
      int maxTokens,
      int timeoutMs,
      List<String> fallbackChain,
      String piiMode) {}

  /** The screening verdict for one turn (design §12.2). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ScreeningResult(
      String label, boolean injectionSuspected, String sanitisedText, String refusalReasonKey) {

    public boolean refused() {
      return refusalReasonKey != null;
    }
  }
}
