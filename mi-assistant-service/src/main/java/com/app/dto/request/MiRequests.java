package com.app.dto.request;

import com.app.constant.MiConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request bodies (api/mi-assistant-service-api.md). */
public final class MiRequests {

  private MiRequests() {}

  public record NewConversationRequest(@Size(max = 80) String title) {}

  /** {@code context} lets Mi resolve "this loan/card" from the screen the customer is on. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TurnContext(String screen, String entityType, UUID entityId,
      Map<String, Object> selection) {}

  /**
   * The chat turn.
   *
   * <p>Unknown properties are ignored here specifically, against the service-wide
   * {@code fail-on-unknown-properties: true}. A mobile build that ships a new optional field before
   * the backend knows it would otherwise fail every message with a 400 — the whole chat feature
   * down for a purely additive change. Found exactly that way: the app began sending {@code mode}
   * and every turn was rejected with "Unrecognized property".
   *
   * <p>The relaxation is deliberately narrow. It applies to this endpoint's envelope, not to the
   * internal DTOs that carry amounts and ids, which stay strict so a mistyped money field is a loud
   * failure rather than a silent zero.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SendMessageRequest(
      @NotBlank @Size(max = MiConstants.INPUT_MAX_CHARS) String text,
      @Pattern(regexp = "TYPED|CHIP|DEEPLINK|VOICE|NUDGE|FORM") String source,
      TurnContext context,
      Map<String, Object> form,
      /**
       * How hard Mi should work on the turn (guideline/09-mi-advance-mode.md). Absent means
       * {@code STANDARD}, which is the one-hop turn every existing client expects.
       */
      @Pattern(regexp = "STANDARD|ADVANCE") String mode,
      UUID clientMessageId) {

    public String sourceOrDefault() {
      return source == null ? "TYPED" : source;
    }

    public String modeOrDefault() {
      return mode == null ? "STANDARD" : mode;
    }
  }

  public record FeedbackRequest(
      @NotNull UUID messageId,
      @NotBlank @Pattern(regexp = "UP|DOWN") String rating,
      @Size(max = 500) String comment) {}

  public record ConfirmProposalRequest(boolean acceptMismatch) {}

  public record PermissionsPatch(
      Boolean recurringBills, Boolean savedRecipients, Boolean autoSaving, Boolean homeInsights) {}

  public record UpdateSettingsRequest(PermissionsPatch permissions, BigDecimal autoLimit) {}

  public record MemoryConsentRequest(@NotNull Boolean longTerm) {}

  public record ConsentsRequest(Boolean proactive, Boolean memoryLongTerm, Boolean improveModels) {}

  public record ApprovePlanRequest(Integer stepSeq) {}

  public record LogActivityRequest(
      @NotNull UUID customerId,
      @NotBlank String kind,
      @NotBlank String titleKey,
      Map<String, Object> titleArgs,
      @NotBlank String subtitleKey,
      Map<String, Object> subtitleArgs,
      String ref,
      UUID proposalId,
      String deepLink,
      Instant occurredAt) {}

  public record DecideRequest(
      @NotBlank @Pattern(regexp = "PAY_BILLS|CARD_PAY|GOAL_TOPUP|TRANSFER") String type,
      @NotNull @DecimalMin("0") BigDecimal amount,
      Boolean beneficiarySaved) {}

  public record KnowledgeEventRequest(
      @NotBlank @Pattern(regexp = "published|archived") String type,
      @NotNull UUID docId,
      String collectionCode,
      Integer version) {}

  public record AgentEventRequest(
      @NotBlank @Pattern(regexp = "updated|disabled|enabled") String type,
      @NotBlank String agentCode,
      Integer version) {}

  public record CodeEventRequest(@NotBlank String type, String triggerCode, String profileCode) {}

  public record ReindexRequest(String collectionCode) {}

  public record RetrievalPreviewRequest(
      @NotBlank String question,
      @NotNull List<String> collections,
      String locale,
      Integer topK) {

    public int topKOrDefault() {
      return topK == null || topK <= 0 ? MiConstants.RAG_TOP_K : topK;
    }
  }

  public record EvalCase(
      String input,
      List<UUID> expectedDocIds,
      String expectedTool,
      Map<String, Object> expectedArgs,
      String expectedDomain) {}

  public record EvalRunRequest(@NotBlank String setName, @NotNull List<EvalCase> cases) {}
}
