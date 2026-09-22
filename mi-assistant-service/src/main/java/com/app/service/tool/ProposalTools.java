package com.app.service.tool;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.response.MiResponses.KeyValueRow;
import com.app.dto.response.MiResponses.ProposalCardDto;
import com.app.exception.BusinessException;
import com.app.service.ProposalService;
import com.app.service.client.PeerClients.BillClient;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import com.app.service.client.PeerClients.SavingClient;
import com.app.service.client.PeerClients.TransferClient;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The propose_* family. Every one of these quotes with the owning domain service first, so the
 * card the customer sees carries real numbers and a real name check — the model never supplies
 * them (design §3.2, §12.3).
 */
@Component
public class ProposalTools {

  private final ProposalService proposalService;
  private final TransferClient transfers;
  private final BillClient bills;
  private final SavingClient savings;
  private final CardClient cards;
  private final LendingClient loans;
  private final MessageSource messageSource;

  public ProposalTools(
      ProposalService proposalService,
      TransferClient transfers,
      BillClient bills,
      SavingClient savings,
      CardClient cards,
      LendingClient loans,
      MessageSource messageSource) {
    this.proposalService = proposalService;
    this.transfers = transfers;
    this.bills = bills;
    this.savings = savings;
    this.cards = cards;
    this.loans = loans;
    this.messageSource = messageSource;
  }

  public ToolResult transfer(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    BigDecimal amount = money(args.get("amount"));
    String recipientQuery = string(args.get("recipientQuery"));
    // "Chuyển 6 triệu cho VCB 0123456789": a bank account the customer typed, not a saved person.
    String bankCode = string(args.get("bankCode"));
    String accountNumber = string(args.get("accountNumber"));
    boolean adHoc = bankCode != null && accountNumber != null;
    if (amount == null || amount.signum() <= 0 || (recipientQuery == null && !adHoc)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("tool",
          ToolCodes.PROPOSE_TRANSFER));
    }
    TransferClient.QuoteResponse response;
    try {
      response =
          transfers
              .quote(
                  new TransferClient.QuoteRequest(
                      context.customerId(),
                      amount,
                      adHoc ? null : recipientQuery,
                      adHoc ? bankCode : null,
                      adHoc ? accountNumber : null,
                      string(args.get("note"))))
              .data();
    } catch (org.springframework.web.client.RestClientResponseException e) {
      // "Chuyển 300 nghìn cho chị Lan" for a name with no saved beneficiary and no core-banking
      // match: transfer-service correctly answers 404/TF-004, but nothing here caught it, so it
      // reached the customer as a bare failed turn — both the stream and its JSON fallback came
      // back empty. Told as a question, this is exactly the answer the customer needed, not an
      // error.
      return new ToolResult(
          ToolCodes.PROPOSE_TRANSFER,
          true,
          Map.of("needsChoice", true, "candidates", List.of()),
          null,
          (System.nanoTime() - started) / 1_000_000);
    }
    TransferClient.QuoteDto quote = response.quote();

    // A weak resolution is a question, not a proposal: the card would name the wrong person.
    if (quote == null || quote.quoteId() == null) {
      return new ToolResult(
          ToolCodes.PROPOSE_TRANSFER,
          true,
          Map.of(
              "needsChoice",
              true,
              "candidates",
              response.resolution() == null ? List.of() : response.resolution().candidates()),
          null,
          (System.nanoTime() - started) / 1_000_000);
    }

    List<KeyValueRow> rows = new ArrayList<>();
    TransferClient.BeneficiaryDto beneficiary = quote.beneficiary();
    rows.add(new KeyValueRow("mi.card.row.recipient", beneficiary.name()));
    rows.add(
        new KeyValueRow(
            "mi.card.row.bank", beneficiary.bankShortName() + " · " + beneficiary.accountMasked()));
    rows.add(new KeyValueRow("mi.card.row.note", quote.note() == null ? "" : quote.note()));
    rows.add(new KeyValueRow("mi.card.row.fee", format(quote.fee(), context.locale())));

    Map<String, Object> params = new HashMap<>();
    params.put("quoteId", quote.quoteId());
    params.put("beneficiaryName", beneficiary.name());

    // transfer-service reports every recipient it holds as saved, including the row it creates for a
    // typed account. Auto-approval under the customer's autonomy limit is only for people they chose
    // to save, so a one-off account always waits for the customer's confirmation.
    boolean saved = beneficiary.saved() && !adHoc;

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_TRANSFER,
                "mi.card.title.transfer",
                amount,
                rows,
                params,
                AppConstants.STEP_UP_TRANSFER,
                saved,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_TRANSFER, true, card, null, (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult bills(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    var quote =
        bills
            .quote(
                new BillClient.QuoteRequest(
                    context.customerId(), integer(args.get("withinDays")), null))
            .data();
    if (quote.bills() == null || quote.bills().isEmpty()) {
      return new ToolResult(
          ToolCodes.PROPOSE_BILL_PAYMENT,
          true,
          Map.of("empty", true),
          null,
          (System.nanoTime() - started) / 1_000_000);
    }
    Map<String, Object> params = new HashMap<>();
    params.put("billIds", quote.bills().stream().map(BillClient.BillDto::billId).toList());

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_PAY_BILLS,
                "mi.card.title.bills",
                quote.total(),
                quote.rows() == null ? List.of() : toRows(quote.rows()),
                params,
                AppConstants.STEP_UP_PAYMENT,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_BILL_PAYMENT, true, card, null,
        (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult deposit(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    BigDecimal amount = money(args.get("amount"));
    if (amount == null || amount.signum() <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("tool",
          ToolCodes.PROPOSE_DEPOSIT));
    }
    var response =
        savings
            .quote(
                new SavingClient.QuoteRequest(
                    context.customerId(), amount, integer(args.get("termMonths")), true))
            .data();

    Map<String, Object> params = new HashMap<>();
    params.put("amount", amount);
    params.put("termMonths", response.quote().termMonths());
    params.put("autoRenew", true);

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_OPEN_DEPOSIT,
                "mi.card.title.deposit",
                amount,
                response.rows() == null ? List.of() : toRows(response.rows()),
                params,
                AppConstants.STEP_UP_PAYMENT,
                false,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_DEPOSIT, true, card, null, (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult goalTopUp(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    BigDecimal amount = money(args.get("amount"));
    UUID goalId = uuid(args.get("goalId"));
    if (amount == null || goalId == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("tool",
          ToolCodes.PROPOSE_GOAL_TOPUP));
    }
    Map<String, Object> params = new HashMap<>();
    params.put("amount", amount);
    params.put("goalId", goalId);

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_GOAL_TOPUP,
                "mi.card.title.goalTopUp",
                amount,
                List.of(new KeyValueRow("mi.card.row.amount", format(amount, context.locale()))),
                params,
                AppConstants.STEP_UP_PAYMENT,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_GOAL_TOPUP, true, card, null, (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult cardPayment(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    List<CardClient.CardDto> owned = cards.cards(context.customerId()).data();
    CardClient.CardDto card =
        owned.stream()
            .filter(item -> item.credit() != null && item.credit().due() != null)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    String option = String.valueOf(args.getOrDefault("option", "FULL"));
    BigDecimal requested = money(args.get("amount"));
    BigDecimal amount = requested != null ? requested : card.credit().due();

    Map<String, Object> params = new HashMap<>();
    params.put("cardId", card.id());
    params.put("option", option);
    params.put("amount", amount);

    ProposalCardDto proposal =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_CARD_PAY,
                "mi.card.title.cardPay",
                amount,
                List.of(
                    new KeyValueRow("mi.card.row.card", card.masked()),
                    new KeyValueRow("mi.card.row.amount", format(amount, context.locale()))),
                params,
                AppConstants.STEP_UP_PAYMENT,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_CARD_PAYMENT, true, proposal, null,
        (System.nanoTime() - started) / 1_000_000);
  }

  /**
   * Opening a card. The eligibility call is what supplies the approved limit and the fee, so the
   * card the customer sees carries card-service's numbers rather than the model's. The proposal has
   * no amount: issuing a card moves no money, and {@code AutonomyPolicy} never lets it run
   * automatically (design 07 §3).
   */
  public ToolResult newCard(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    String productCode = string(args.get("productCode"));
    if (productCode == null) {
      throw new BusinessException(
          ErrorCode.VALIDATION_FAILED, Map.of("tool", ToolCodes.PROPOSE_NEW_CARD));
    }
    CardClient.EligibilityDto eligibility =
        cards
            .eligibility(new CardClient.EligibilityRequest(context.customerId(), productCode))
            .data();

    // Not being eligible is an answer, not a failure: Mi explains the reason and offers the
    // alternative instead of dropping a dead proposal card into the conversation.
    if (eligibility == null || !eligibility.eligible()) {
      return new ToolResult(
          ToolCodes.PROPOSE_NEW_CARD,
          true,
          Map.of(
              "eligible",
              false,
              "message",
              eligibility == null ? "" : String.valueOf(eligibility.message()),
              "reason",
              eligibility == null || eligibility.reason() == null ? "" : eligibility.reason(),
              "requiresDocuments",
              eligibility != null && eligibility.requiresDocuments()),
          null,
          (System.nanoTime() - started) / 1_000_000);
    }

    boolean physical = bool(args.get("physical"), true);
    boolean addToWallet = bool(args.get("addToWallet"), true);
    CardClient.ProductDto product = eligibility.product();

    List<KeyValueRow> rows = new ArrayList<>();
    if (eligibility.approvedLimit() != null) {
      rows.add(
          new KeyValueRow(
              "mi.card.row.approvedLimit", format(eligibility.approvedLimit(), context.locale())));
    }
    if (product != null && product.fee() != null) {
      rows.add(new KeyValueRow("mi.card.row.annualFee", product.fee()));
    }
    if (product != null && product.perk() != null) {
      rows.add(new KeyValueRow("mi.card.row.perk", product.perk()));
    }
    rows.add(
        new KeyValueRow(
            "mi.card.row.physical",
            messages(physical ? "mi.card.value.yes" : "mi.card.value.no", context.locale())));

    Map<String, Object> params = new HashMap<>();
    params.put("productCode", productCode);
    params.put("physical", physical);
    params.put("addToWallet", addToWallet);
    if (eligibility.approvedLimit() != null) {
      params.put("approvedLimit", eligibility.approvedLimit());
    }
    UUID deliveryAddressId = uuid(args.get("deliveryAddressId"));
    if (deliveryAddressId != null) {
      params.put("deliveryAddressId", deliveryAddressId);
    }

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_CARD_ISSUE,
                "mi.card.title.cardIssue",
                // No amount: a card application is a product commitment, not a payment.
                null,
                rows,
                params,
                AppConstants.STEP_UP_CARD_ISSUE,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_NEW_CARD, true, card, null, (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult loanRepayment(MiTool.Context context, Map<String, Object> args, String kind) {    long started = System.nanoTime();
    var overview = loans.overview(context.customerId()).data();
    if (overview.activeLoans() == null || overview.activeLoans().isEmpty()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    LendingClient.LoanDto loan = overview.activeLoans().get(0);
    BigDecimal amount =
        "PREPAYMENT".equals(kind)
            ? money(args.get("amount"))
            : loan.nextDue() == null ? null : loan.nextDue().amount();
    if (amount == null || amount.signum() <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("kind", kind));
    }

    List<KeyValueRow> rows = new ArrayList<>();
    rows.add(new KeyValueRow("mi.card.row.loan", loan.ref()));
    rows.add(new KeyValueRow("mi.card.row.amount", format(amount, context.locale())));
    if ("PREPAYMENT".equals(kind)) {
      var quote =
          loans
              .prepayQuote(
                  loan.id(),
                  new LendingClient.PrepayQuoteRequest(context.customerId(), amount))
              .data();
      rows.add(
          new KeyValueRow("mi.card.row.interestSaved", format(quote.interestSaved(),
              context.locale())));
    }

    Map<String, Object> params = new HashMap<>();
    params.put("loanId", loan.id());
    params.put("kind", kind);
    params.put("amount", amount);

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_LOAN_REPAY,
                "mi.card.title.loanRepay",
                amount,
                rows,
                params,
                AppConstants.STEP_UP_PAYMENT,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.PROPOSE_LOAN_REPAYMENT, true, card, null,
        (System.nanoTime() - started) / 1_000_000);
  }

  public ToolResult setBudget(MiTool.Context context, Map<String, Object> args) {
    long started = System.nanoTime();
    BigDecimal limit = money(args.get("monthlyLimit"));
    if (limit == null || limit.signum() <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("tool",
          ToolCodes.SET_BUDGET));
    }
    Map<String, Object> params = new HashMap<>();
    params.put("categoryCode", args.getOrDefault("categoryCode", "OTHER"));
    params.put("monthlyLimit", limit);
    params.put("alertPct", args.getOrDefault("alertPct", MiConstants.BUDGET_ALERT_PCT_DEFAULT));

    ProposalCardDto card =
        proposalService.propose(
            new ProposalService.Draft(
                context.customerId(),
                context.conversationId(),
                context.messageId(),
                MiConstants.TYPE_SET_BUDGET,
                "mi.card.title.budget",
                limit,
                List.of(new KeyValueRow("mi.card.row.limit", format(limit, context.locale()))),
                params,
                null,
                true,
                context.locale()));
    return new ToolResult(
        ToolCodes.SET_BUDGET, true, card, null, (System.nanoTime() - started) / 1_000_000);
  }

  private List<KeyValueRow> toRows(List<com.app.service.client.PeerClients.KeyValueRow> rows) {
    return rows.stream().map(row -> new KeyValueRow(row.k(), row.v())).toList();
  }


  private String format(BigDecimal amount, Locale locale) {
    if (amount == null) {
      return "";
    }
    NumberFormat formatter = NumberFormat.getNumberInstance(locale);
    formatter.setMaximumFractionDigits(0);
    return formatter.format(amount) + " ₫";
  }

  private BigDecimal money(Object value) {
    return value == null
        ? null
        : new BigDecimal(value.toString()).setScale(0, java.math.RoundingMode.HALF_UP);
  }

  private Integer integer(Object value) {
    return value == null ? null : Integer.valueOf(new BigDecimal(value.toString()).intValue());
  }

  private UUID uuid(Object value) {
    try {
      return value == null ? null : UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String string(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  private boolean bool(Object value, boolean fallback) {
    return value == null ? fallback : Boolean.parseBoolean(value.toString());
  }

  /** Row values are ready-to-render text, so a localised value is resolved here, not in the app. */
  private String messages(String key, Locale locale) {
    return messageSource.getMessage(key, null, key, locale);
  }
}
