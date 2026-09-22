package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.exception.BusinessException;
import com.app.service.client.PeerClients.BillClient;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import com.app.service.client.PeerClients.SavingClient;
import com.app.service.client.PeerClients.TransferClient;
import com.app.service.execution.ProposalExecutor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PeerProposalExecutor implements ProposalExecutor {

  private static final Logger log = LoggerFactory.getLogger(PeerProposalExecutor.class);

  private final TransferClient transfers;
  private final BillClient bills;
  private final SavingClient savings;
  private final CardClient cards;
  private final LendingClient loans;
  private final BudgetWriter budgets;
  private final ObjectMapper objectMapper;

  public PeerProposalExecutor(
      TransferClient transfers,
      BillClient bills,
      SavingClient savings,
      CardClient cards,
      LendingClient loans,
      BudgetWriter budgets,
      ObjectMapper objectMapper) {
    this.transfers = transfers;
    this.bills = bills;
    this.savings = savings;
    this.cards = cards;
    this.loans = loans;
    this.budgets = budgets;
    this.objectMapper = objectMapper;
  }

  @Override
  public Outcome execute(
      String type,
      UUID customerId,
      UUID proposalId,
      Map<String, Object> params,
      String executedBy,
      String stepUpToken) {

    String key = proposalId.toString();
    try {
      return switch (type) {
        case MiConstants.TYPE_TRANSFER -> {
          var receipt =
              transfers
                  .execute(
                      key,
                      new TransferClient.ExecuteRequest(
                          customerId,
                          uuid(params.get("quoteId")),
                          executedBy,
                          stepUpToken,
                          proposalId))
                  .data();
          yield new Outcome(receipt.ref(), receipt.status());
        }
        case MiConstants.TYPE_PAY_BILLS -> {
          var payment =
              bills
                  .pay(
                      key,
                      new BillClient.PayRequest(
                          customerId, uuids(params.get("billIds")), executedBy, stepUpToken,
                          proposalId))
                  .data();
          // A multi-bill batch is all-or-nothing downstream, so one ref covers the lot.
          yield new Outcome(payment.ref(), "DONE");
        }
        case MiConstants.TYPE_OPEN_DEPOSIT -> {
          var deposit =
              savings
                  .open(
                      key,
                      new SavingClient.OpenRequest(
                          customerId,
                          money(params.get("amount")),
                          integer(params.get("termMonths")),
                          bool(params.get("autoRenew"), true),
                          executedBy,
                          stepUpToken,
                          proposalId))
                  .data();
          yield new Outcome(deposit.deposit().ref(), deposit.deposit().status());
        }
        case MiConstants.TYPE_GOAL_TOPUP -> {
          savings.contribute(
              key,
              uuid(params.get("goalId")),
              new SavingClient.ContributionRequest(
                  customerId,
                  money(params.get("amount")),
                  executedBy,
                  stepUpToken,
                  proposalId,
                  MiConstants.EXECUTED_BY_AUTO.equals(executedBy) ? "AUTO" : "MANUAL"));
          yield new Outcome(key, "DONE");
        }
        case MiConstants.TYPE_CARD_PAY -> {
          var payment =
              cards
                  .pay(
                      key,
                      uuid(params.get("cardId")),
                      new CardClient.PayRequest(
                          customerId,
                          String.valueOf(params.getOrDefault("option", "FULL")),
                          money(params.get("amount")),
                          executedBy,
                          stepUpToken,
                          proposalId))
                  .data();
          yield new Outcome(payment.ref(), payment.status());
        }
        case MiConstants.TYPE_LOAN_REPAY -> {
          var repayment =
              loans
                  .repay(
                      key,
                      uuid(params.get("loanId")),
                      new LendingClient.RepayRequest(
                          customerId,
                          String.valueOf(params.getOrDefault("kind", "INSTALMENT")),
                          money(params.get("amount")),
                          executedBy,
                          stepUpToken,
                          proposalId))
                  .data();
          yield new Outcome(repayment.ref(), "DONE");
        }
        case MiConstants.TYPE_CARD_ISSUE -> {
          var application =
              cards
                  .openApplication(
                      key,
                      new CardClient.OpenApplicationRequest(
                          customerId,
                          String.valueOf(params.get("productCode")),
                          bool(params.get("physical"), true),
                          bool(params.get("addToWallet"), true),
                          uuid(params.get("deliveryAddressId")),
                          executedBy,
                          stepUpToken,
                          proposalId))
                  .data();
          // card-service issues the virtual card immediately and the physical one later, so the
          // application's own status is the honest outcome rather than a flat DONE.
          yield new Outcome(
              application.ref() == null ? key : application.ref(),
              application.status() == null ? "DONE" : application.status());
        }
        case MiConstants.TYPE_SET_BUDGET -> {
          budgets.apply(customerId, params);
          yield new Outcome(key, "DONE");
        }
        default -> throw new BusinessException(ErrorCode.CANNOT_UNDERSTAND, Map.of("type", type));
      };
    } catch (RestClientResponseException e) {
      // The peer refused for a reason it already named. Dropping it left the card saying only
      // "BỊ CHẶN", with nothing in the log to tell the customer or an operator why.
      log.warn(
          "proposal {} of type {} refused by peer: status={} code={}",
          proposalId,
          type,
          e.getStatusCode().value(),
          peerErrorCode(e));
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    } catch (RestClientException e) {
      log.warn(
          "proposal {} of type {} failed downstream: {}", proposalId, type, e.getMessage());
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
  }

  /** The peer's domain code from the shared envelope, when it sent one. */
  private String peerErrorCode(RestClientResponseException e) {
    try {
      var body = objectMapper.readTree(e.getResponseBodyAsString());
      var error = body.get("error");
      return error == null || error.get("code") == null ? "none" : error.get("code").asString();
    } catch (RuntimeException ignored) {
      return "unparseable";
    }
  }

  private UUID uuid(Object value) {
    return value == null ? null : UUID.fromString(value.toString());
  }

  private List<UUID> uuids(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(item -> UUID.fromString(item.toString())).toList();
    }
    return List.of();
  }

  private BigDecimal money(Object value) {
    return value == null ? null : new BigDecimal(value.toString()).setScale(0,
        java.math.RoundingMode.HALF_UP);
  }

  private Integer integer(Object value) {
    return value == null ? null : Integer.valueOf(new BigDecimal(value.toString()).intValue());
  }

  private boolean bool(Object value, boolean fallback) {
    return value == null ? fallback : Boolean.parseBoolean(value.toString());
  }
}
