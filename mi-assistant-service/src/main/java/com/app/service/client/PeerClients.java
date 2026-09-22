package com.app.service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.app.dto.response.ApiResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The domain services behind Mi's tools. Every mutating call takes {@code proposalId} as its
 * idempotency key and {@code executedBy}, so a replay can never move money twice (design §3.2).
 */
public final class PeerClients {

  private PeerClients() {}

  /** Peer services mark the emphasised row of a card; Mi keeps the flag so the app can too. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KeyValueRow(String k, String v, boolean emphasis) {}

  // ── auth ───────────────────────────────────────────────────────────────────
  @HttpExchange("/internal")
  public interface AuthClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record StepUpVerifyRequest(
        String token, UUID customerId, String scope, BigDecimal amount, boolean consume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record StepUpVerifyResponse(
        boolean valid, UUID customerId, String scope, BigDecimal amount, String jti) {}

    @PostExchange("/step-up/verify")
    StepUpVerifyResponse verifyStepUp(@RequestBody StepUpVerifyRequest request);
  }

  // ── account ────────────────────────────────────────────────────────────────
  @HttpExchange("/internal")
  public interface AccountClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record CategorySpend(String code, String name, BigDecimal amount, int pct, String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record TopCategory(String code, String name, int pct) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record SpendingSummary(
        String month,
        BigDecimal total,
        BigDecimal previousTotal,
        int deltaPct,
        List<CategorySpend> categories,
        TopCategory topCategory) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record AccountDto(
        UUID id,
        String number,
        String numberMasked,
        String type,
        BigDecimal balance,
        BigDecimal available,
        String status) {}

    @GetExchange("/accounts/{customerId}/spending")
    ApiResponse<SpendingSummary> spending(
        @PathVariable UUID customerId, @RequestParam(required = false) String month);

    @GetExchange("/accounts/{customerId}/summary")
    ApiResponse<List<AccountDto>> accounts(@PathVariable UUID customerId);
  }

  // ── transfer ───────────────────────────────────────────────────────────────
  @HttpExchange("/internal/transfers")
  public interface TransferClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record BeneficiaryDto(
        UUID id,
        String name,
        String shortName,
        String initials,
        String avatarColor,
        String bankCode,
        String bankShortName,
        String accountMasked,
        String phone,
        boolean saved,
        boolean verified,
        java.time.Instant lastUsedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record SourceAccountDto(
        UUID accountId, String number, String numberMasked, BigDecimal available) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record NameCheckDto(boolean matched, boolean fraudFlag, String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record Resolution(BigDecimal confidence, List<BeneficiaryDto> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    /** A saved recipient is named in {@code recipientQuery}; a one-off one is {@code bankCode + accountNumber}. */
    record QuoteRequest(
        UUID customerId,
        BigDecimal amount,
        String recipientQuery,
        String bankCode,
        String accountNumber,
        String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteDto(
        UUID quoteId,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal total,
        BeneficiaryDto beneficiary,
        SourceAccountDto sourceAccount,
        String note,
        NameCheckDto nameCheck,
        BigDecimal dailyRemaining,
        java.time.Instant expiresAt) {}

    /** transfer-service wraps the quote next to the resolution it used to find the recipient. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteResponse(QuoteDto quote, Resolution resolution) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ExecuteRequest(
        UUID customerId, UUID quoteId, String executedBy, String stepUpToken, UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record AccountRef(UUID accountId, String number, String numberMasked) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ReceiptDto(
        UUID transferId,
        String ref,
        String status,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal total,
        BeneficiaryDto beneficiary,
        String note,
        AccountRef sourceAccount,
        java.time.Instant executedAt,
        String channel,
        int points,
        UUID templateId) {}

    @PostExchange("/quote")
    ApiResponse<QuoteResponse> quote(@RequestBody QuoteRequest request);

    @PostExchange("/execute")
    ApiResponse<ReceiptDto> execute(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ExecuteRequest request);
  }

  // ── bills ──────────────────────────────────────────────────────────────────
  @HttpExchange("/internal/bills")
  public interface BillClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record BillDto(
        UUID billId,
        UUID linkedId,
        String billerCode,
        String billerName,
        String icon,
        String category,
        String customerCode,
        String alias,
        String period,
        BigDecimal amount,
        LocalDate dueOn,
        Integer dueDay,
        String status,
        boolean autoPay) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteRequest(UUID customerId, Integer withinDays, List<String> billerCodes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteResponse(
        List<BillDto> bills, BigDecimal total, List<KeyValueRow> rows, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PayRequest(
        UUID customerId,
        List<UUID> billIds,
        String executedBy,
        String stepUpToken,
        UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PaidItemDto(UUID billId, String billerName, BigDecimal amount, String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record AccountRef(
        UUID accountId, String number, String numberMasked, BigDecimal available) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PaymentDto(
        String ref,
        BigDecimal total,
        List<PaidItemDto> items,
        int points,
        java.time.Instant paidAt,
        String executedBy,
        String channel,
        AccountRef sourceAccount) {}

    @PostExchange("/quote")
    ApiResponse<QuoteResponse> quote(@RequestBody QuoteRequest request);

    @PostExchange("/pay")
    ApiResponse<PaymentDto> pay(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PayRequest request);
  }

  // ── saving ─────────────────────────────────────────────────────────────────
  @HttpExchange("/internal")
  public interface SavingClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteRequest(
        UUID customerId, BigDecimal amount, Integer termMonths, Boolean autoRenew) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteDto(
        BigDecimal amount,
        int termMonths,
        BigDecimal ratePa,
        BigDecimal interest,
        BigDecimal total,
        LocalDate maturityOn,
        boolean autoRenew,
        String renewText) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record QuoteResponse(QuoteDto quote, List<KeyValueRow> rows) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record OpenRequest(
        UUID customerId,
        BigDecimal amount,
        Integer termMonths,
        Boolean autoRenew,
        String executedBy,
        String stepUpToken,
        UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record DepositDto(
        UUID id,
        String ref,
        String productCode,
        String title,
        String subtitle,
        BigDecimal principal,
        Integer termMonths,
        BigDecimal ratePa,
        LocalDate openedOn,
        LocalDate maturityOn,
        boolean autoRenew,
        BigDecimal accruedInterest,
        BigDecimal projectedInterest,
        String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record AccountRef(UUID accountId, String number, String numberMasked) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record OpenDepositResponse(
        DepositDto deposit, int points, AccountRef sourceAccount, String reminderNote) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ContributionRequest(
        UUID customerId,
        BigDecimal amount,
        String executedBy,
        String stepUpToken,
        UUID proposalId,
        String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record GoalDto(
        UUID id,
        String kindCode,
        String kindName,
        String icon,
        String name,
        BigDecimal target,
        BigDecimal saved,
        int pct,
        LocalDate targetDate,
        Integer monthsLeft,
        BigDecimal monthly,
        Boolean roundUp,
        String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ContributionDto(UUID contributionId, GoalDto goal, String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record RateDto(int termMonths, BigDecimal ratePa) {}

    @PostExchange("/savings/quote")
    ApiResponse<QuoteResponse> quote(@RequestBody QuoteRequest request);

    @PostExchange("/savings/open")
    ApiResponse<OpenDepositResponse> open(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody OpenRequest request);

    @PostExchange("/goals/{id}/contributions")
    ApiResponse<ContributionDto> contribute(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable UUID id,
        @RequestBody ContributionRequest request);

    @GetExchange("/rates")
    ApiResponse<Map<String, Object>> rates();
  }

  // ── card ───────────────────────────────────────────────────────────────────
  @HttpExchange("/internal/cards")
  public interface CardClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record CreditInfo(
        BigDecimal limit, BigDecimal used, int usedPct, BigDecimal due, LocalDate dueDate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record DebitInfo(String linkedAccountMasked, UUID linkedAccountId, BigDecimal available) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record CardDto(
        UUID id,
        String productCode,
        String productName,
        String kind,
        String network,
        String last4,
        String masked,
        String exp,
        String holder,
        List<String> gradient,
        String status,
        String statusLabel,
        boolean locked,
        boolean virtual,
        CreditInfo credit,
        DebitInfo debit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ControlsDto(
        boolean online,
        boolean contactless,
        boolean foreignTx,
        boolean atm,
        BigDecimal dailyLimit,
        BigDecimal dailyLimitMax) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record MiCardRequest(UUID customerId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record MiControlsRequest(UUID customerId, Map<String, Object> controls) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PayRequest(
        UUID customerId,
        String option,
        BigDecimal amount,
        String executedBy,
        String stepUpToken,
        UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PaymentDto(String ref, String status, BigDecimal amount) {}

    /** A card product the customer could open (card-service {@code GET /products}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductDto(
        String code,
        String name,
        String tier,
        String perk,
        String fee,
        String network,
        BigDecimal defaultLimit,
        boolean virtualInstant) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EligibilityRequest(UUID customerId, String productCode) {}

    /**
     * card-service answers "not eligible" as data rather than an error, so Mi can explain the
     * reason instead of failing the turn.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EligibilityDto(
        boolean eligible,
        String message,
        BigDecimal approvedLimit,
        boolean requiresDocuments,
        String reason,
        ProductDto product) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenApplicationRequest(
        UUID customerId,
        String productCode,
        Boolean physical,
        Boolean addToWallet,
        UUID deliveryAddressId,
        String executedBy,
        String stepUpToken,
        UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplicationDto(
        UUID applicationId, String ref, String status, CardDto card, String doneSubtitle) {}

    @GetExchange("/{customerId}")
    ApiResponse<List<CardDto>> cards(@PathVariable UUID customerId);

    @GetExchange("/products")
    ApiResponse<List<ProductDto>> products(@RequestParam(required = false) String kind);

    @PostExchange("/{id}/pay")
    ApiResponse<PaymentDto> pay(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable UUID id,
        @RequestBody PayRequest request);

    @PostExchange("/{id}/lock")
    ApiResponse<CardDto> lock(@PathVariable UUID id, @RequestBody MiCardRequest request);

    @PostExchange("/{id}/unlock")
    ApiResponse<CardDto> unlock(@PathVariable UUID id, @RequestBody MiCardRequest request);

    @PostExchange("/applications/eligibility")
    ApiResponse<EligibilityDto> eligibility(@RequestBody EligibilityRequest request);

    @PostExchange("/applications")
    ApiResponse<ApplicationDto> openApplication(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody OpenApplicationRequest request);

    @PutExchange("/{id}/controls")
    ApiResponse<ControlsDto> controls(
        @PathVariable UUID id, @RequestBody MiControlsRequest request);
  }

  // ── lending ────────────────────────────────────────────────────────────────
  @HttpExchange("/internal/loans")
  public interface LendingClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record NextDueDto(LocalDate date, BigDecimal amount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record LoanDto(
        UUID id,
        String ref,
        String productCode,
        String productName,
        BigDecimal principal,
        BigDecimal outstanding,
        BigDecimal paid,
        int paidPct,
        int instalmentSeq,
        int termMonths,
        NextDueDto nextDue,
        BigDecimal ratePa,
        String status,
        String statusLabel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record OverviewResponse(
        Map<String, Object> preApproval,
        List<LoanDto> activeLoans,
        List<Map<String, Object>> products,
        Map<String, Object> insight) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PrepayQuoteRequest(UUID customerId, BigDecimal amount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record PrepayQuoteResponse(
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal feePct,
        BigDecimal interestSaved,
        BigDecimal newOutstanding,
        String option,
        Integer newTermMonths,
        BigDecimal newMonthly,
        String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record RepayRequest(
        UUID customerId,
        String kind,
        BigDecimal amount,
        String executedBy,
        String stepUpToken,
        UUID proposalId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record RepaymentDto(
        String ref,
        BigDecimal amount,
        Integer instalmentSeq,
        BigDecimal outstanding,
        NextDueDto nextDue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DtiDto(int pct, String level, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MortgageSizeRequest(
        UUID customerId,
        BigDecimal propertyValue,
        Integer termMonths,
        String productCode,
        BigDecimal downPayment) {}

    /**
     * {@code capReason} names which ceiling bound the answer (LTV, product maximum, pre-approval or
     * income), so Mi can say why the number is what it is instead of quoting a bare figure.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MortgageSizeResponse(
        BigDecimal propertyValue,
        BigDecimal ltvPct,
        BigDecimal amount,
        BigDecimal downPayment,
        int termMonths,
        BigDecimal ratePa,
        BigDecimal instalment,
        BigDecimal totalInterest,
        BigDecimal totalRepay,
        DtiDto dti,
        String capReason,
        String productCode,
        String productName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AffordabilityRequest(
        UUID customerId,
        BigDecimal monthlyBudget,
        BigDecimal monthlyIncome,
        Integer termMonths,
        String productCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AffordabilityResponse(
        BigDecimal monthlyBudget,
        BigDecimal maxLoanAmount,
        BigDecimal maxPropertyValue,
        BigDecimal requiredDownPayment,
        int termMonths,
        BigDecimal ratePa,
        BigDecimal ltvPct,
        DtiDto dti,
        String capReason,
        String productCode) {}

    @GetExchange("/{customerId}/overview")
    ApiResponse<OverviewResponse> overview(@PathVariable UUID customerId);

    @PostExchange("/mortgage/size")
    ApiResponse<MortgageSizeResponse> sizeMortgage(@RequestBody MortgageSizeRequest request);

    @PostExchange("/affordability")
    ApiResponse<AffordabilityResponse> affordability(@RequestBody AffordabilityRequest request);

    @PostExchange("/{id}/prepay/quote")
    ApiResponse<PrepayQuoteResponse> prepayQuote(
        @PathVariable UUID id, @RequestBody PrepayQuoteRequest request);

    @PostExchange("/{id}/repay")
    ApiResponse<RepaymentDto> repay(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable UUID id,
        @RequestBody RepayRequest request);
  }

  // ── notification ───────────────────────────────────────────────────────────
  @HttpExchange("/internal/notifications")
  public interface NotificationClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record NotifyRequest(
        UUID customerId,
        String templateCode,
        Map<String, Object> args,
        String channel,
        String deepLink) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record NotifyResponse(UUID id) {}

    @PostExchange
    ApiResponse<NotifyResponse> notify(@RequestBody NotifyRequest request);
  }

  // ── memory-hook ────────────────────────────────────────────────────────────
  @HttpExchange("/internal/memory")
  public interface MemoryClient {

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ConsentFlags(boolean longTerm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record RecordDto(
        UUID id,
        String kind,
        String text,
        BigDecimal confidence,
        boolean pinned,
        String source,
        LocalDate validUntil,
        java.time.Instant createdAt,
        Map<String, String> refs,
        String state,
        boolean explicit,
        boolean customerConfirmed,
        java.time.Instant lastConfirmedAt,
        String persistence,
        String entity,
        String evidenceSummary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ScoredRecord(RecordDto record, double score) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record RecallResponse(
        ConsentFlags consent, List<RecordDto> pinned, List<ScoredRecord> matches) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ConsentResponse(
        boolean longTerm,
        boolean snapshot,
        boolean improveModels,
        String policyVersion,
        java.time.Instant updatedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ConsentRequest(
        Boolean longTerm,
        Boolean snapshot,
        Boolean improveModels,
        String policyVersion,
        String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record DeletedResponse(boolean deleted) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ErasureRequestBody(String reason, String requestedBy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)

    record ErasureResponse(UUID erasureId, String status) {}

    @GetExchange("/{customerId}/recall")
    ApiResponse<RecallResponse> recall(
        @PathVariable UUID customerId,
        @RequestParam String q,
        @RequestParam int k,
        @RequestParam(required = false) String kinds);

    @GetExchange("/{customerId}/snapshot")
    ApiResponse<Map<String, Object>> snapshot(
        @PathVariable UUID customerId,
        @RequestParam String slice,
        @RequestHeader(value = "X-Memory-Slice", required = false) String sliceHeader);

    @GetExchange("/{customerId}/records")
    ApiResponse<List<RecordDto>> records(@PathVariable UUID customerId);

    @DeleteExchange("/{customerId}/records/{id}")
    ApiResponse<DeletedResponse> deleteRecord(
        @PathVariable UUID customerId, @PathVariable UUID id);

    @DeleteExchange("/{customerId}")
    ApiResponse<ErasureResponse> erase(
        @PathVariable UUID customerId, @RequestBody ErasureRequestBody body);

    @GetExchange("/{customerId}/consent")
    ApiResponse<ConsentResponse> consent(@PathVariable UUID customerId);

    @PutExchange("/{customerId}/consent")
    ApiResponse<ConsentResponse> updateConsent(
        @PathVariable UUID customerId, @RequestBody ConsentRequest request);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateRequest(
        String kind, String text, String entity, String reason,
        BigDecimal confidence, Map<String, String> refs,
        UUID conversationId, UUID messageId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateDecisionResponse(String decision, RecordDto record) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EditRequest(String text, String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MemoryLinkDto(
        UUID id, UUID fromRecord, UUID toRecord, String relation, String state,
        java.time.Instant createdAt, java.time.Instant confirmedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MemoryGraphResponse(List<RecordDto> nodes, List<MemoryLinkDto> links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WhyResponse(UUID recordId, String kind, String entity,
        String evidenceSummary, String reason, java.time.Instant createdAt) {}

    @PostExchange("/{customerId}/candidates")
    ApiResponse<CandidateDecisionResponse> submitCandidate(
        @PathVariable UUID customerId, @RequestBody CandidateRequest request);

    @PostExchange("/{customerId}/records/{id}/confirm")
    ApiResponse<RecordDto> confirm(
        @PathVariable UUID customerId, @PathVariable UUID id);

    @PostExchange("/{customerId}/records/{id}/reject")
    ApiResponse<RecordDto> reject(
        @PathVariable UUID customerId, @PathVariable UUID id);

    @PatchExchange("/{customerId}/records/{id}")
    ApiResponse<RecordDto> editRecord(
        @PathVariable UUID customerId, @PathVariable UUID id,
        @RequestBody EditRequest request);

    @PostExchange("/{customerId}/records/{id}/deactivate")
    ApiResponse<RecordDto> deactivate(
        @PathVariable UUID customerId, @PathVariable UUID id);

    @PostExchange("/{customerId}/records/{id}/forget")
    ApiResponse<DeletedResponse> forget(
        @PathVariable UUID customerId, @PathVariable UUID id);

    @GetExchange("/{customerId}/hypotheses")
    ApiResponse<RecordDto> hypotheses(@PathVariable UUID customerId);

    @GetExchange("/{customerId}/graph")
    ApiResponse<MemoryGraphResponse> graph(@PathVariable UUID customerId);

    @GetExchange("/{customerId}/records/{id}/why")
    ApiResponse<WhyResponse> why(
        @PathVariable UUID customerId, @PathVariable UUID id);
  }
}
