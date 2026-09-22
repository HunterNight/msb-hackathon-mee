package com.app.service.impl;

import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.service.client.PeerClients.AccountClient;
import com.app.service.client.PeerClients.BillClient;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a read tool's result into the sentence Mi actually says.
 *
 * <p>Without this every data question — "Số dư của tôi còn bao nhiêu?", "Khoản vay còn bao nhiêu?"
 * — collapsed into the same content-free "Mình đã kiểm tra xong thông tin cho bạn.", because
 * {@code narrate} only had cases for the proposal tools. The tool had already fetched the answer;
 * nothing ever read it out.
 *
 * <p>Every number here comes from the peer service that owns it, so the reply cannot drift from
 * the screen the customer would open next.
 */
@Component
public class ToolNarrator {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final MessageSource messages;
  private final ObjectMapper objectMapper;

  public ToolNarrator(MessageSource messages, ObjectMapper objectMapper) {
    this.messages = messages;
    this.objectMapper = objectMapper;
  }

  /** The spoken answer, or {@code null} when this tool has no narration of its own. */
  public String narrate(ToolResult result, Locale locale) {
    if (result == null || !result.ok() || result.data() == null) {
      return null;
    }
    try {
      return switch (result.code()) {
        case ToolCodes.GET_ACCOUNT_SUMMARY -> accounts(result.data(), locale);
        case ToolCodes.GET_SPENDING_SUMMARY -> spending(result.data(), locale);
        case ToolCodes.GET_CARDS, ToolCodes.GET_CARD_CONTROLS -> cards(result.data(), locale);
        case ToolCodes.GET_STATEMENT -> statement(result.data(), locale);
        case ToolCodes.GET_LOAN_OVERVIEW, ToolCodes.GET_SCHEDULE -> loans(result.data(), locale);
        case ToolCodes.QUOTE_PREPAYMENT -> prepayment(result.data(), locale);
        case ToolCodes.CALCULATE_LOAN -> calculation(result.data(), locale);
        case ToolCodes.SIZE_MORTGAGE -> mortgage(result.data(), locale);
        case ToolCodes.CHECK_AFFORDABILITY -> affordability(result.data(), locale);
        case ToolCodes.CHECK_CARD_ELIGIBILITY -> cardEligibility(result.data(), locale);
        case ToolCodes.UNLOCK_CARD -> unlocked(result.data(), locale);
        case ToolCodes.GET_RATES, ToolCodes.GET_SAVINGS_OVERVIEW -> rates(result.data(), locale);
        case ToolCodes.PREVIEW_GOAL -> goal(result.data(), locale);
        case ToolCodes.GET_DUE_BILLS -> bills(result.data(), locale);
        default -> null;
      };
    } catch (RuntimeException e) {
      // A shape the peer changed must not take the whole turn down; the caller falls back to the
      // generic acknowledgement.
      return null;
    }
  }

  private String accounts(Object data, Locale locale) {
    List<AccountClient.AccountDto> list =
        objectMapper.convertValue(
            data, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, AccountClient.AccountDto.class));
    if (list == null || list.isEmpty()) {
      return msg("mi.narrate.accounts.none", locale);
    }
    StringBuilder text = new StringBuilder(msg("mi.narrate.accounts.lead", locale));
    for (AccountClient.AccountDto account : list) {
      text.append("\n• ")
          .append(
              msg(
                  "mi.narrate.accounts.line",
                  locale,
                  account.numberMasked() == null ? account.number() : account.numberMasked(),
                  money(account.available() == null ? account.balance() : account.available())));
    }
    return text.toString();
  }

  private String spending(Object data, Locale locale) {
    AccountClient.SpendingSummary summary =
        objectMapper.convertValue(data, AccountClient.SpendingSummary.class);
    if (summary == null || summary.total() == null) {
      return null;
    }
    String top =
        summary.topCategory() == null
            ? null
            : msg(
                "mi.narrate.spending.top",
                locale,
                summary.topCategory().name(),
                summary.topCategory().pct());
    String lead = msg("mi.narrate.spending.lead", locale, summary.month(), money(summary.total()));
    return top == null ? lead : lead + " " + top;
  }

  private String cards(Object data, Locale locale) {
    List<CardClient.CardDto> list =
        objectMapper.convertValue(
            data, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, CardClient.CardDto.class));
    if (list == null || list.isEmpty()) {
      return msg("mi.narrate.cards.none", locale);
    }
    StringBuilder text = new StringBuilder(msg("mi.narrate.cards.lead", locale, list.size()));
    for (CardClient.CardDto card : list) {
      text.append("\n• ")
          .append(
              msg(
                  "mi.narrate.cards.line",
                  locale,
                  card.productName(),
                  card.last4() == null ? "" : card.last4(),
                  card.statusLabel() == null ? card.status() : card.statusLabel()));
      if (card.credit() != null && card.credit().limit() != null) {
        text.append(' ')
            .append(
                msg(
                    "mi.narrate.cards.credit",
                    locale,
                    money(card.credit().limit()),
                    money(card.credit().used())));
      }
    }
    return text.toString();
  }

  private String statement(Object data, Locale locale) {
    CardClient.CardDto card = objectMapper.convertValue(data, CardClient.CardDto.class);
    if (card == null || card.credit() == null || card.credit().due() == null) {
      return msg("mi.narrate.statement.none", locale);
    }
    return msg(
        "mi.narrate.statement.line",
        locale,
        card.productName(),
        card.last4() == null ? "" : card.last4(),
        money(card.credit().due()),
        day(card.credit().dueDate()));
  }

  private String loans(Object data, Locale locale) {
    LendingClient.OverviewResponse overview =
        objectMapper.convertValue(data, LendingClient.OverviewResponse.class);
    if (overview == null || overview.activeLoans() == null || overview.activeLoans().isEmpty()) {
      return msg("mi.narrate.loans.none", locale);
    }
    StringBuilder text =
        new StringBuilder(msg("mi.narrate.loans.lead", locale, overview.activeLoans().size()));
    for (LendingClient.LoanDto loan : overview.activeLoans()) {
      text.append("\n• ")
          .append(
              msg(
                  "mi.narrate.loans.line",
                  locale,
                  loan.productName(),
                  money(loan.outstanding()),
                  loan.paidPct()));
      if (loan.nextDue() != null && loan.nextDue().amount() != null) {
        text.append(' ')
            .append(
                msg(
                    "mi.narrate.loans.due",
                    locale,
                    money(loan.nextDue().amount()),
                    day(loan.nextDue().date())));
      }
    }
    return text.toString();
  }

  private String prepayment(Object data, Locale locale) {
    LendingClient.PrepayQuoteResponse quote =
        objectMapper.convertValue(data, LendingClient.PrepayQuoteResponse.class);
    if (quote == null) {
      return null;
    }
    if (quote.text() != null && !quote.text().isBlank()) {
      return quote.text();
    }
    return msg(
        "mi.narrate.prepay.line",
        locale,
        money(quote.amount()),
        money(quote.fee()),
        money(quote.interestSaved()),
        money(quote.newOutstanding()));
  }

  private String calculation(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null || map.get("instalment") == null) {
      return null;
    }
    String line =
        msg(
            "mi.narrate.calc.line",
            locale,
            money(decimal(map.get("principal"))),
            map.get("termMonths"),
            decimal(map.get("ratePa")),
            money(decimal(map.get("instalment"))),
            money(decimal(map.get("totalInterest"))));
    Object product = map.get("productName");
    return product == null
        ? line
        : line + " " + msg("mi.narrate.calc.product", locale, product);
  }

  /**
   * A mortgage sizing. When the property value came from an estimate the tool wraps the sizing and
   * asks for a range, so the spoken answer widens rather than implying a precision it lacks.
   */
  private String mortgage(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null) {
      return null;
    }
    boolean asRange = Boolean.TRUE.equals(map.get("quoteAsRange"));
    Map<String, Object> sizing = asRange ? asMap(map.get("sizing")) : map;
    if (sizing == null || sizing.get("amount") == null) {
      return null;
    }
    StringBuilder text =
        new StringBuilder(
            msg(
                asRange ? "mi.narrate.mortgage.range" : "mi.narrate.mortgage.line",
                locale,
                money(decimal(sizing.get("propertyValue"))),
                decimal(sizing.get("ltvPct")),
                money(decimal(sizing.get("amount"))),
                money(decimal(sizing.get("instalment"))),
                sizing.get("termMonths")));

    // The binding cap is the actionable part: it tells the customer what to change.
    Object capReason = sizing.get("capReason");
    if (capReason != null) {
      String key = "mi.narrate.mortgage.cap." + capReason;
      String cap = messages.getMessage(key, null, null, locale);
      if (cap != null) {
        text.append(' ').append(cap);
      }
    }
    Map<String, Object> dti = asMap(sizing.get("dti"));
    if (dti != null && dti.get("text") != null && !String.valueOf(dti.get("text")).isBlank()) {
      text.append(' ').append(dti.get("text"));
    }
    if (asRange) {
      text.append(' ').append(msg("mi.narrate.mortgage.estimateNote", locale));
    }
    return text.toString();
  }

  private String affordability(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null || map.get("maxPropertyValue") == null) {
      return null;
    }
    StringBuilder text =
        new StringBuilder(
            msg(
                "mi.narrate.affordability.line",
                locale,
                money(decimal(map.get("monthlyBudget"))),
                money(decimal(map.get("maxLoanAmount"))),
                money(decimal(map.get("maxPropertyValue"))),
                map.get("termMonths")));
    Object down = map.get("requiredDownPayment");
    if (down != null) {
      text.append(' ')
          .append(msg("mi.narrate.affordability.down", locale, money(decimal(down))));
    }
    return text.toString();
  }

  private String cardEligibility(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null) {
      return null;
    }
    // No product named: the tool answered with the catalogue instead.
    if (map.get("products") instanceof List<?> products && !products.isEmpty()) {
      StringBuilder text = new StringBuilder(msg("mi.narrate.cardProducts.lead", locale));
      for (Object item : products) {
        Map<String, Object> row = asMap(item);
        if (row == null) {
          continue;
        }
        text.append("\n• ")
            .append(
                msg(
                    "mi.narrate.cardProducts.line",
                    locale,
                    row.get("name"),
                    row.get("perk") == null ? "" : row.get("perk"),
                    row.get("fee") == null ? "" : row.get("fee")));
      }
      return text.toString();
    }
    if (map.get("eligible") == null) {
      return null;
    }
    if (!Boolean.TRUE.equals(map.get("eligible"))) {
      Object message = map.get("message");
      return message == null || String.valueOf(message).isBlank()
          ? msg("mi.narrate.cardEligibility.no", locale)
          : String.valueOf(message);
    }
    return msg(
        "mi.narrate.cardEligibility.yes", locale, money(decimal(map.get("approvedLimit"))));
  }

  private String unlocked(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null) {
      return null;
    }
    if (Boolean.TRUE.equals(map.get("noLockedCard"))) {
      return msg("mi.narrate.unlock.none", locale);
    }
    Object masked = map.get("masked");
    return masked == null ? null : msg("mi.card.unlocked", locale, masked);
  }

  private String rates(Object data, Locale locale) {    Map<String, Object> map = asMap(data);
    if (map == null) {
      return null;
    }
    Object terms = map.get("terms");
    if (!(terms instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    StringBuilder text = new StringBuilder(msg("mi.narrate.rates.lead", locale));
    for (Object term : list) {
      Map<String, Object> row = asMap(term);
      if (row == null) {
        continue;
      }
      text.append("\n• ")
          .append(
              msg(
                  "mi.narrate.rates.line",
                  locale,
                  row.get("termMonths"),
                  decimal(row.get("ratePa"))));
    }
    return text.toString();
  }

  private String goal(Object data, Locale locale) {
    Map<String, Object> map = asMap(data);
    if (map == null || map.get("monthly") == null) {
      return null;
    }
    return msg(
        "mi.narrate.goal.line",
        locale,
        money(decimal(map.get("target"))),
        map.get("months"),
        money(decimal(map.get("monthly"))));
  }

  private String bills(Object data, Locale locale) {
    BillClient.QuoteResponse quote =
        objectMapper.convertValue(data, BillClient.QuoteResponse.class);
    if (quote == null || quote.bills() == null || quote.bills().isEmpty()) {
      return msg("mi.narrate.bills.none", locale);
    }
    if (quote.text() != null && !quote.text().isBlank()) {
      return quote.text();
    }
    StringBuilder text =
        new StringBuilder(
            msg("mi.narrate.bills.lead", locale, quote.bills().size(), money(quote.total())));
    for (BillClient.BillDto bill : quote.bills()) {
      text.append("\n• ")
          .append(
              msg(
                  "mi.narrate.bills.line",
                  locale,
                  bill.billerName(),
                  money(bill.amount()),
                  day(bill.dueOn())));
    }
    return text.toString();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object data) {
    return data instanceof Map<?, ?> ? (Map<String, Object>) data
        : objectMapper.convertValue(data, Map.class);
  }

  private BigDecimal decimal(Object value) {
    return value == null ? null : new BigDecimal(String.valueOf(value));
  }

  /** Money is whole đồng with a thousands separator, matching every screen in the app. */
  private String money(BigDecimal amount) {
    if (amount == null) {
      return "0";
    }
    return String.format(Locale.GERMANY, "%,d", amount.setScale(0, java.math.RoundingMode.HALF_UP)
        .toBigInteger());
  }

  private String day(LocalDate date) {
    return date == null ? "" : date.format(DAY);
  }

  private String msg(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args, locale);
  }
}
