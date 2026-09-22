package com.app.dto.request;

import com.app.constant.MemoryConstants;
import jakarta.validation.constraints.DecimalMax;
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

/** Request bodies of the internal API (api/memory-hook-service-api.md). */
public final class MemoryRequests {

  private MemoryRequests() {}

  /** Mi's {@code remember} tool. The text is re-validated here, never trusted from the caller. */
  public record RememberRequest(
      @NotBlank
          @Pattern(
              regexp =
                  "NICKNAME|HABIT|PREFERENCE|GOAL|FACT|CONCERN|LIFESTYLE|RELATIONSHIP|PLAN"
                      + "|PRODUCT_PREFERENCE")
          String kind,
      @NotBlank @Size(max = MemoryConstants.RECORD_TEXT_MAX) String text,
      @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
      Map<String, String> refs,
      @NotNull UUID conversationId,
      @NotNull UUID messageId) {

    public BigDecimal confidenceOrDefault() {
      return confidence == null ? MemoryConstants.CONFIDENCE_DEFAULT : confidence;
    }
  }

  public record CandidateRequest(
      @NotBlank
          @Pattern(
              regexp =
                  "NICKNAME|HABIT|PREFERENCE|GOAL|FACT|CONCERN|LIFESTYLE|RELATIONSHIP|PLAN"
                      + "|PRODUCT_PREFERENCE|EVENT|TRANSACTION_CONTEXT")
          String kind,
      @NotBlank @Size(max = MemoryConstants.RECORD_TEXT_MAX) String text,
      @Size(max = 64) String entity,
      @Size(max = MemoryConstants.RECORD_TEXT_MAX) String reason,
      @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
      Map<String, String> refs,
      @NotNull UUID conversationId,
      @NotNull UUID messageId) {

    public BigDecimal confidenceOrDefault() {
      return confidence == null ? MemoryConstants.CONFIDENCE_DEFAULT : confidence;
    }
  }

  public record EditRequest(
      @NotBlank @Size(max = MemoryConstants.RECORD_TEXT_MAX) String text,
      @Size(max = MemoryConstants.RECORD_TEXT_MAX) String reason) {}

  public record ConsentRequest(
      Boolean longTerm,
      Boolean snapshot,
      Boolean improveModels,
      @NotBlank String policyVersion,
      @NotBlank @Pattern(regexp = "MI_SETTINGS|ONBOARDING|OPS") String source) {}

  public record ErasureRequestBody(
      @NotBlank @Pattern(regexp = "CUSTOMER_REQUEST|CONSENT_WITHDRAWN|ACCOUNT_CLOSED|OPS")
          String reason,
      @NotBlank @Size(max = 120) String requestedBy) {}

  public record ReplayRequest(
      String pseudoId,
      UUID customerId,
      @NotNull Instant from,
      @NotNull Instant to,
      List<String> topics) {}

  public record KeyRotationRequest(@NotBlank String kekVersion) {}
}
