package com.app.service.tool;

import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import com.app.service.client.PeerClients.SavingClient;
import com.app.service.client.PeerClients.TransferClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The domain agents' tools. Read tools return projected DTOs from the owning service; the
 * propose_* ones delegate to {@link ProposalTools}, which is the only thing that creates a
 * proposal (design §3.3).
 */
public final class DomainTools {

  private DomainTools() {}

  /** Small base so each tool is a few lines of intent rather than boilerplate. */
  abstract static class Read implements MiTool {

    @Override
    public boolean mutating() {
      return false;
    }

    protected ToolResult ok(Object data, long startedNanos) {
      return new ToolResult(code(), true, data, null, (System.nanoTime() - startedNanos) / 1_000_000);
    }
  }

  abstract static class Propose implements MiTool {

    protected final ProposalTools proposals;

    protected Propose(ProposalTools proposals) {
      this.proposals = proposals;
    }

    @Override
    public boolean mutating() {
      return true;
    }
  }

  // ── loan ───────────────────────────────────────────────────────────────────
  @Component
  public static class GetLoanOverview extends Read {

    private final LendingClient loans;

    public GetLoanOverview(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.GET_LOAN_OVERVIEW;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(loans.overview(context.customerId()).data(), started);
    }
  }

  @Component
  public static class QuotePrepayment extends Read {

    private final LendingClient loans;

    public QuotePrepayment(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.QUOTE_PREPAYMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      var overview = loans.overview(context.customerId()).data();
      if (overview.activeLoans() == null || overview.activeLoans().isEmpty()) {
        return ok(Map.of("empty", true), started);
      }
      UUID loanId =
          args.get("loanId") == null
              ? overview.activeLoans().get(0).id()
              : UUID.fromString(args.get("loanId").toString());
      BigDecimal amount = new BigDecimal(String.valueOf(args.getOrDefault("amount", "0")));
      return ok(
          loans
              .prepayQuote(loanId, new LendingClient.PrepayQuoteRequest(context.customerId(),
                  amount))
              .data(),
          started);
    }
  }

  @Component
  public static class GetSchedule extends Read {

    private final LendingClient loans;

    public GetSchedule(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.GET_SCHEDULE;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(loans.overview(context.customerId()).data(), started);
    }
  }

  /**
   * The instalment estimate the loan agent quotes. The arithmetic is local, but the rate is not
   * invented — it comes from the customer's pre-approval or the product catalogue, so the number
   * matches the calculator screen (screen 22).
   */
  @Component
  public static class CalculateLoan extends Read {

    /** Only reached when lending-service is unreachable; the catalogue floor at time of writing. */
    private static final BigDecimal FALLBACK_RATE_PA = new BigDecimal("9.5");

    private final LendingClient loans;

    public CalculateLoan(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.CALCULATE_LOAN;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      BigDecimal principal = new BigDecimal(String.valueOf(args.getOrDefault("principal", "0")));
      int months = Integer.parseInt(String.valueOf(args.getOrDefault("termMonths", "12")));
      Object given = args.get("ratePa");
      Map<String, Object> product =
          given == null ? matchingProduct(context, principal, months) : null;
      BigDecimal ratePa =
          given == null ? rateOf(product) : new BigDecimal(String.valueOf(given));
      BigDecimal monthlyRate =
          ratePa.divide(new BigDecimal("1200"), 10, java.math.RoundingMode.HALF_UP);

      BigDecimal instalment;
      if (monthlyRate.signum() == 0) {
        instalment = principal.divide(BigDecimal.valueOf(months), 0,
            java.math.RoundingMode.HALF_UP);
      } else {
        double r = monthlyRate.doubleValue();
        double factor = Math.pow(1 + r, months);
        instalment =
            BigDecimal.valueOf(principal.doubleValue() * r * factor / (factor - 1))
                .setScale(0, java.math.RoundingMode.HALF_UP);
      }
      BigDecimal total = instalment.multiply(BigDecimal.valueOf(months));
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("principal", principal);
      result.put("termMonths", months);
      result.put("ratePa", ratePa);
      result.put("instalment", instalment);
      result.put("totalPayable", total);
      result.put("totalInterest", total.subtract(principal));
      // Naming the product makes the assumption visible: the same amount over the same term
      // costs differently under a mortgage and a consumer loan.
      if (product != null && product.get("name") != null) {
        result.put("productName", String.valueOf(product.get("name")));
      }
      return ok(result, started);
    }

    /**
     * The rate the customer would actually be offered: their pre-approval if lending-service has
     * one, otherwise the cheapest product that can carry this amount over this term. Taking the
     * catalogue-wide minimum would quote a 300-month mortgage rate for a two-year consumer loan.
     */
    private Map<String, Object> matchingProduct(
        Context context, BigDecimal principal, int months) {
      try {
        LendingClient.OverviewResponse overview = loans.overview(context.customerId()).data();
        if (overview == null) {
          return null;
        }
        Object rate = overview.preApproval() == null ? null : overview.preApproval().get("ratePa");
        if (rate != null) {
          return Map.of("minRatePa", rate);
        }
        if (overview.products() == null) {
          return null;
        }
        return overview.products().stream()
            .filter(product -> product.get("minRatePa") != null)
            .filter(product -> fits(product, principal, months))
            .min(java.util.Comparator.comparing(
                product -> new BigDecimal(String.valueOf(product.get("minRatePa")))))
            .orElse(null);
      } catch (RuntimeException e) {
        // A peer outage must not block the estimate; the catalogue floor is still honest.
        return null;
      }
    }

    private BigDecimal rateOf(Map<String, Object> product) {
      Object rate = product == null ? null : product.get("minRatePa");
      return rate == null ? FALLBACK_RATE_PA : new BigDecimal(String.valueOf(rate));
    }

    private boolean fits(Map<String, Object> product, BigDecimal principal, int months) {
      Object maxAmount = product.get("maxAmount");
      Object maxTerm = product.get("maxTermMonths");
      if (maxAmount != null
          && new BigDecimal(String.valueOf(maxAmount)).compareTo(principal) < 0) {
        return false;
      }
      return maxTerm == null || Integer.parseInt(String.valueOf(maxTerm)) >= months;
    }
  }

  /**
   * The borrowable amount for a property value. The LTV cap, the product ceiling, the pre-approval
   * and the income test all live in lending-service, and the response names which one bound the
   * answer so Mi can explain the number instead of just stating it.
   */
  @Component
  public static class SizeMortgage extends Read {

    private final LendingClient loans;

    public SizeMortgage(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.SIZE_MORTGAGE;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      BigDecimal propertyValue = money(args.get("propertyValue"));
      if (propertyValue == null || propertyValue.signum() <= 0) {
        throw new com.app.exception.BusinessException(
            com.app.constant.ErrorCode.VALIDATION_FAILED, Map.of("tool", code()));
      }
      Integer termMonths =
          args.get("termMonths") == null
              ? null
              : Integer.valueOf(String.valueOf(args.get("termMonths")));
      var response =
          loans
              .sizeMortgage(
                  new LendingClient.MortgageSizeRequest(
                      context.customerId(),
                      propertyValue,
                      termMonths,
                      args.get("productCode") == null
                          ? null
                          : String.valueOf(args.get("productCode")),
                      money(args.get("downPayment"))))
              .data();

      // An estimated property value carries a range, so a single instalment would overstate the
      // precision of the whole answer. Passing the flag through lets the agent quote a range.
      boolean fromEstimate = "ESTIMATE".equals(String.valueOf(args.get("valueSource")));
      if (!fromEstimate) {
        return ok(response, started);
      }
      Map<String, Object> ranged = new java.util.LinkedHashMap<>();
      ranged.put("sizing", response);
      ranged.put("valueSource", "ESTIMATE");
      ranged.put("quoteAsRange", true);
      return ok(ranged, started);
    }

    private BigDecimal money(Object value) {
      return value == null
          ? null
          : new BigDecimal(String.valueOf(value)).setScale(0, java.math.RoundingMode.HALF_UP);
    }
  }

  /** The reverse question: what a given monthly budget or income can carry. Never proposes. */
  @Component
  public static class CheckAffordability extends Read {

    private final LendingClient loans;

    public CheckAffordability(LendingClient loans) {
      this.loans = loans;
    }

    @Override
    public String code() {
      return ToolCodes.CHECK_AFFORDABILITY;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(
          loans
              .affordability(
                  new LendingClient.AffordabilityRequest(
                      context.customerId(),
                      decimal(args.get("monthlyBudget")),
                      decimal(args.get("monthlyIncome")),
                      args.get("termMonths") == null
                          ? null
                          : Integer.valueOf(String.valueOf(args.get("termMonths"))),
                      args.get("productCode") == null
                          ? null
                          : String.valueOf(args.get("productCode"))))
              .data(),
          started);
    }

    private BigDecimal decimal(Object value) {
      return value == null
          ? null
          : new BigDecimal(String.valueOf(value)).setScale(0, java.math.RoundingMode.HALF_UP);
    }
  }

  @Component
  public static class ProposeLoanRepayment extends Propose {

    public ProposeLoanRepayment(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_LOAN_REPAYMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.loanRepayment(context, args, "INSTALMENT");
    }
  }

  @Component
  public static class ProposePrepayment extends Propose {

    public ProposePrepayment(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_PREPAYMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.loanRepayment(context, args, "PREPAYMENT");
    }
  }

  // ── card ───────────────────────────────────────────────────────────────────
  @Component
  public static class GetCards extends Read {

    private final CardClient cards;

    public GetCards(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.GET_CARDS;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(cards.cards(context.customerId()).data(), started);
    }
  }

  @Component
  public static class GetStatement extends Read {

    private final CardClient cards;

    public GetStatement(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.GET_STATEMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      List<CardClient.CardDto> owned = cards.cards(context.customerId()).data();
      return ok(
          owned.stream()
              .filter(card -> card.credit() != null && card.credit().due() != null)
              .findFirst()
              .orElse(null),
          started);
    }
  }

  @Component
  public static class GetCardControls extends Read {

    private final CardClient cards;

    public GetCardControls(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.GET_CARD_CONTROLS;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(cards.cards(context.customerId()).data(), started);
    }
  }

  @Component
  public static class ProposeCardPayment extends Propose {

    public ProposeCardPayment(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_CARD_PAYMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.cardPayment(context, args);
    }
  }

  /**
   * Locking a card moves no money and is the one action the design lets an agent take directly —
   * it is also reversible by the customer in one tap.
   */
  @Component
  public static class LockCard implements MiTool {

    private final CardClient cards;

    public LockCard(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.LOCK_CARD;
    }

    @Override
    public boolean mutating() {
      return true;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      UUID cardId =
          args.get("cardId") == null
              ? cards.cards(context.customerId()).data().get(0).id()
              : UUID.fromString(args.get("cardId").toString());
      var locked =
          cards.lock(cardId, new CardClient.MiCardRequest(context.customerId())).data();
      return new ToolResult(
          code(), true, locked, null, (System.nanoTime() - started) / 1_000_000);
    }
  }

  /**
   * The counterpart of {@link LockCard}. A customer who asked Mi to lock a card must be able to ask
   * Mi to undo it; leaving unlock out meant the only reversal was to go find the screen.
   */
  @Component
  public static class UnlockCard implements MiTool {

    private final CardClient cards;

    public UnlockCard(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.UNLOCK_CARD;
    }

    @Override
    public boolean mutating() {
      return true;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      List<CardClient.CardDto> owned = cards.cards(context.customerId()).data();
      UUID cardId;
      if (args.get("cardId") != null) {
        cardId = UUID.fromString(args.get("cardId").toString());
      } else {
        // With no card named, the locked one is what "mở khoá thẻ" means; falling back to the
        // first card would unlock something the customer never mentioned.
        cardId =
            owned.stream()
                .filter(CardClient.CardDto::locked)
                .findFirst()
                .map(CardClient.CardDto::id)
                .orElse(null);
      }
      if (cardId == null) {
        return new ToolResult(
            code(),
            true,
            Map.of("noLockedCard", true),
            null,
            (System.nanoTime() - started) / 1_000_000);
      }
      var unlocked = cards.unlock(cardId, new CardClient.MiCardRequest(context.customerId())).data();
      return new ToolResult(code(), true, unlocked, null, (System.nanoTime() - started) / 1_000_000);
    }
  }

  /**
   * Reads the customer's eligibility for a card product. Read-only on purpose: the approved limit
   * and fee it returns are what {@code propose_new_card} then puts on the proposal card, so the
   * numbers have provenance and the citation guard is satisfied.
   */
  @Component
  public static class CheckCardEligibility extends Read {

    private final CardClient cards;

    public CheckCardEligibility(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.CHECK_CARD_ELIGIBILITY;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      String productCode =
          args.get("productCode") == null ? null : String.valueOf(args.get("productCode"));
      if (productCode == null) {
        // Without a product the useful answer is the catalogue, so the customer can choose.
        return ok(Map.of("products", cards.products("CREDIT").data()), started);
      }
      return ok(
          cards
              .eligibility(new CardClient.EligibilityRequest(context.customerId(), productCode))
              .data(),
          started);
    }
  }

  @Component
  public static class ProposeNewCard extends Propose {

    public ProposeNewCard(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_NEW_CARD;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.newCard(context, args);
    }
  }

  @Component
  public static class SetCardControl implements MiTool {
    private final CardClient cards;

    public SetCardControl(CardClient cards) {
      this.cards = cards;
    }

    @Override
    public String code() {
      return ToolCodes.SET_CARD_CONTROL;
    }

    @Override
    public boolean mutating() {
      return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      UUID cardId = UUID.fromString(String.valueOf(args.get("cardId")));
      Map<String, Object> controls =
          args.get("controls") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
      var updated =
          cards
              .controls(cardId, new CardClient.MiControlsRequest(context.customerId(), controls))
              .data();
      return new ToolResult(
          code(), true, updated, null, (System.nanoTime() - started) / 1_000_000);
    }
  }

  // ── saving ─────────────────────────────────────────────────────────────────
  @Component
  public static class GetRates extends Read {

    private final SavingClient savings;

    public GetRates(SavingClient savings) {
      this.savings = savings;
    }

    @Override
    public String code() {
      return ToolCodes.GET_RATES;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(savings.rates().data(), started);
    }
  }

  @Component
  public static class GetSavingsOverview extends Read {

    private final SavingClient savings;

    public GetSavingsOverview(SavingClient savings) {
      this.savings = savings;
    }

    @Override
    public String code() {
      return ToolCodes.GET_SAVINGS_OVERVIEW;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(savings.rates().data(), started);
    }
  }

  @Component
  public static class PreviewGoal extends Read {

    private final SavingClient savings;

    public PreviewGoal(SavingClient savings) {
      this.savings = savings;
    }

    @Override
    public String code() {
      return ToolCodes.PREVIEW_GOAL;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      BigDecimal target = new BigDecimal(String.valueOf(args.getOrDefault("target", "0")));
      int months = Integer.parseInt(String.valueOf(args.getOrDefault("months", "12")));
      // Round the instalment up to the nearest 1.000 ₫, as saving-service does.
      BigDecimal monthly =
          target
              .divide(BigDecimal.valueOf(months), 0, java.math.RoundingMode.CEILING)
              .divide(new BigDecimal("1000"), 0, java.math.RoundingMode.CEILING)
              .multiply(new BigDecimal("1000"));
      return ok(Map.of("target", target, "months", months, "monthly", monthly), started);
    }
  }

  @Component
  public static class ProposeDeposit extends Propose {

    public ProposeDeposit(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_DEPOSIT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.deposit(context, args);
    }
  }

  @Component
  public static class ProposeGoalTopUp extends Propose {

    public ProposeGoalTopUp(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_GOAL_TOPUP;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.goalTopUp(context, args);
    }
  }

  // ── payment ────────────────────────────────────────────────────────────────
  @Component
  public static class FindBeneficiary extends Read {

    private final TransferClient transfers;

    public FindBeneficiary(TransferClient transfers) {
      this.transfers = transfers;
    }

    @Override
    public String code() {
      return ToolCodes.FIND_BENEFICIARY;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      // A zero-amount quote is how transfer-service exposes pure resolution.
      var quote =
          transfers
              .quote(
                  new TransferClient.QuoteRequest(
                      context.customerId(),
                      BigDecimal.ZERO,
                      String.valueOf(args.getOrDefault("query", "")),
                      null,
                      null,
                      null))
              .data();
      return ok(quote.resolution(), started);
    }
  }

  @Component
  public static class GetDueBills extends Read {

    private final com.app.service.client.PeerClients.BillClient bills;

    public GetDueBills(com.app.service.client.PeerClients.BillClient bills) {
      this.bills = bills;
    }

    @Override
    public String code() {
      return ToolCodes.GET_DUE_BILLS;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return ok(
          bills
              .quote(
                  new com.app.service.client.PeerClients.BillClient.QuoteRequest(
                      context.customerId(), 7, null))
              .data(),
          started);
    }
  }

  @Component
  public static class ProposeTransfer extends Propose {

    public ProposeTransfer(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_TRANSFER;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.transfer(context, args);
    }
  }

  @Component
  public static class ProposeBillPayment extends Propose {

    public ProposeBillPayment(ProposalTools proposals) {
      super(proposals);
    }

    @Override
    public String code() {
      return ToolCodes.PROPOSE_BILL_PAYMENT;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposals.bills(context, args);
    }
  }
}
