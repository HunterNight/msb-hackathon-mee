package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.constant.MiConstants;
import com.app.dto.response.ApiResponse;
import com.app.exception.BusinessException;
import com.app.service.client.PeerClients.BillClient;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import com.app.service.client.PeerClients.SavingClient;
import com.app.service.client.PeerClients.TransferClient;
import com.app.service.execution.ProposalExecutor;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

/**
 * Executing a confirmed card application. The idempotency key matters most here: a retried
 * confirmation must not leave the customer with two card applications.
 */
class PeerProposalExecutorCardIssueTest {

  private static final UUID CUSTOMER = UUID.randomUUID();

  private CardClient cards;
  private PeerProposalExecutor executor;

  @BeforeEach
  void setUp() {
    cards = Mockito.mock(CardClient.class);
    executor =
        new PeerProposalExecutor(
            Mockito.mock(TransferClient.class),
            Mockito.mock(BillClient.class),
            Mockito.mock(SavingClient.class),
            cards,
            Mockito.mock(LendingClient.class),
            Mockito.mock(BudgetWriter.class),
            new ObjectMapper());
  }

  private static Map<String, Object> params() {
    return Map.of("productCode", "VISA_PLATINUM", "physical", true, "addToWallet", true);
  }

  @Test
  @DisplayName("The proposal id is the idempotency key, so a replay cannot open a second card")
  void usesProposalIdAsIdempotencyKey() {
    UUID proposalId = UUID.randomUUID();
    when(cards.openApplication(any(), any()))
        .thenReturn(
            ApiResponse.ok(
                new CardClient.ApplicationDto(
                    UUID.randomUUID(), "CARD-APP-1", "ISSUED", null, "done")));

    ProposalExecutor.Outcome outcome =
        executor.execute(
            MiConstants.TYPE_CARD_ISSUE,
            CUSTOMER,
            proposalId,
            params(),
            MiConstants.EXECUTED_BY_USER,
            "step-up-token");

    verify(cards).openApplication(eq(proposalId.toString()), any());
    assertThat(outcome.ref()).isEqualTo("CARD-APP-1");
    assertThat(outcome.status()).isEqualTo("ISSUED");
  }

  @Test
  @DisplayName("The customer's step-up token and identity are passed through, not the model's")
  void forwardsCustomerIdentityAndStepUp() {
    UUID proposalId = UUID.randomUUID();
    when(cards.openApplication(any(), any()))
        .thenReturn(
            ApiResponse.ok(
                new CardClient.ApplicationDto(
                    UUID.randomUUID(), "CARD-APP-2", "PENDING", null, "done")));

    executor.execute(
        MiConstants.TYPE_CARD_ISSUE,
        CUSTOMER,
        proposalId,
        params(),
        MiConstants.EXECUTED_BY_USER,
        "the-token");

    ArgumentCaptor<CardClient.OpenApplicationRequest> request =
        ArgumentCaptor.forClass(CardClient.OpenApplicationRequest.class);
    verify(cards).openApplication(any(), request.capture());

    assertThat(request.getValue().customerId()).isEqualTo(CUSTOMER);
    assertThat(request.getValue().stepUpToken()).isEqualTo("the-token");
    assertThat(request.getValue().executedBy()).isEqualTo(MiConstants.EXECUTED_BY_USER);
    assertThat(request.getValue().proposalId()).isEqualTo(proposalId);
    assertThat(request.getValue().productCode()).isEqualTo("VISA_PLATINUM");
  }

  @Test
  @DisplayName("A pending physical card is reported as PENDING, not flattened to DONE")
  void reportsTheApplicationStatus() {
    when(cards.openApplication(any(), any()))
        .thenReturn(
            ApiResponse.ok(
                new CardClient.ApplicationDto(
                    UUID.randomUUID(), "CARD-APP-3", "PENDING", null, "done")));

    assertThat(
            executor
                .execute(
                    MiConstants.TYPE_CARD_ISSUE,
                    CUSTOMER,
                    UUID.randomUUID(),
                    params(),
                    MiConstants.EXECUTED_BY_USER,
                    "t")
                .status())
        .isEqualTo("PENDING");
  }

  @Test
  @DisplayName("A card-service refusal surfaces as an upstream error, not a crash")
  void peerRefusalBecomesBusinessException() {
    when(cards.openApplication(any(), any()))
        .thenThrow(
            new org.springframework.web.client.RestClientException("card-service unavailable"));

    assertThatThrownBy(
            () ->
                executor.execute(
                    MiConstants.TYPE_CARD_ISSUE,
                    CUSTOMER,
                    UUID.randomUUID(),
                    params(),
                    MiConstants.EXECUTED_BY_USER,
                    "t"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("An unknown proposal type reaches no peer at all")
  void unknownTypeCallsNobody() {
    assertThatThrownBy(
            () ->
                executor.execute(
                    "SOME_FUTURE_TYPE",
                    CUSTOMER,
                    UUID.randomUUID(),
                    Map.of(),
                    MiConstants.EXECUTED_BY_USER,
                    "t"))
        .isInstanceOf(BusinessException.class);

    verify(cards, never()).openApplication(any(), any());
  }
}
