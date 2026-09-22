package com.app.service.impl;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.response.MiResponses.KeyValueRow;
import com.app.dto.response.MiResponses.ProposalCardDto;
import com.app.exception.BusinessException;
import com.app.model.CustomerAutonomy;
import com.app.model.Proposal;
import com.app.repository.ProposalRepository;
import com.app.service.ActivityService;
import com.app.service.AutonomyPolicy;
import com.app.service.EventBus;
import com.app.service.ProposalService;
import com.app.service.execution.ProposalExecutor;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProposalServiceImpl implements ProposalService {

  private static final Logger log = LoggerFactory.getLogger(ProposalServiceImpl.class);

  private final ProposalRepository proposals;
  private final AutonomyPolicy autonomyPolicy;
  private final ProposalExecutor executor;
  private final ActivityService activityService;
  private final MessageSource messages;
  private final EventBus eventBus;
  private final ObjectMapper objectMapper;

  public ProposalServiceImpl(
      ProposalRepository proposals,
      AutonomyPolicy autonomyPolicy,
      ProposalExecutor executor,
      ActivityService activityService,
      MessageSource messages,
      EventBus eventBus,
      ObjectMapper objectMapper) {
    this.proposals = proposals;
    this.autonomyPolicy = autonomyPolicy;
    this.executor = executor;
    this.activityService = activityService;
    this.messages = messages;
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public ProposalCardDto propose(Draft draft) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(draft.customerId());
    AutonomyPolicy.Decision decision =
        autonomyPolicy.decide(
            autonomy, draft.type(), draft.amount(), draft.beneficiarySaved());

    Proposal proposal = new Proposal();
    proposal.setCustomerId(draft.customerId());
    proposal.setConversationId(draft.conversationId());
    proposal.setMessageId(draft.messageId());
    proposal.setType(draft.type());
    proposal.setAmount(draft.amount() == null ? BigDecimal.ZERO : draft.amount());
    proposal.setParams(objectMapper.writeValueAsString(draft.params()));
    proposal.setPhase(MiConstants.PHASE_PROPOSE);
    proposal.setRequiresStepUp(!decision.auto());
    proposal.setStepUpScope(decision.auto() ? null : draft.stepUpScope());
    // The idempotency key is the proposal id itself, so a retried execution is a no-op downstream.
    proposal.setIdempotencyKey(proposal.getId().toString());
    proposal.setExpiresAt(Instant.now().plus(MiConstants.PROPOSAL_TTL));
    proposal.setCard(
        objectMapper.writeValueAsString(
            Map.of("titleKey", draft.titleKey(), "rows", draft.rows())));
    Proposal saved = proposals.save(proposal);

    if (decision.auto()) {
      // Within the customer's own limits and permissions: Mi acts and reports, per screen 10.
      return execute(saved, MiConstants.EXECUTED_BY_AUTO, null, draft.locale());
    }
    return card(saved, draft.locale());
  }

  @Override
  @Transactional
  public ProposalCardDto confirm(
      UUID customerId, UUID proposalId, String stepUpToken, boolean acceptMismatch) {

    Proposal proposal = owned(customerId, proposalId);
    if (!MiConstants.PHASE_PROPOSE.equals(proposal.getPhase())) {
      throw new BusinessException(ErrorCode.PROPOSAL_NOT_PENDING,
          Map.of("phase", proposal.getPhase()));
    }
    if (proposal.getExpiresAt().isBefore(Instant.now())) {
      expire(proposal);
      throw new BusinessException(ErrorCode.PROPOSAL_EXPIRED);
    }
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);
    if (autonomy.isPaused()) {
      throw new BusinessException(ErrorCode.MI_PAUSED);
    }
    if (proposal.isRequiresStepUp() && (stepUpToken == null || stepUpToken.isBlank())) {
      throw new BusinessException(ErrorCode.STEP_UP_REQUIRED,
          Map.of("scope", String.valueOf(proposal.getStepUpScope())));
    }
    return execute(proposal, MiConstants.EXECUTED_BY_USER, stepUpToken, AppConstants.DEFAULT_LOCALE);
  }

  @Override
  @Transactional
  public ProposalCardDto cancel(UUID customerId, UUID proposalId) {
    Proposal proposal = owned(customerId, proposalId);
    if (!MiConstants.PHASE_PROPOSE.equals(proposal.getPhase())) {
      throw new BusinessException(ErrorCode.PROPOSAL_NOT_PENDING,
          Map.of("phase", proposal.getPhase()));
    }
    proposal.setPhase(MiConstants.PHASE_CANCEL);
    proposal.setDecidedAt(Instant.now());
    return card(proposals.save(proposal), AppConstants.DEFAULT_LOCALE);
  }

  @Override
  @Transactional(readOnly = true)
  public ProposalCardDto get(UUID customerId, UUID proposalId) {
    return card(owned(customerId, proposalId), AppConstants.DEFAULT_LOCALE);
  }

  @Override
  @Transactional
  public int expireOverdue(int batchSize) {
    List<Proposal> overdue =
        proposals.findByPhaseAndExpiresAtBefore(
            MiConstants.PHASE_PROPOSE, Instant.now(), PageRequest.of(0, batchSize));
    overdue.forEach(this::expire);
    return overdue.size();
  }

  private ProposalCardDto execute(
      Proposal proposal, String executedBy, String stepUpToken, Locale locale) {

    proposal.setPhase(MiConstants.PHASE_EXEC);
    proposal.setExecutedBy(executedBy);
    proposal.setDecidedAt(Instant.now());
    proposals.save(proposal);

    try {
      ProposalExecutor.Outcome outcome =
          executor.execute(
              proposal.getType(),
              proposal.getCustomerId(),
              proposal.getId(),
              params(proposal),
              executedBy,
              stepUpToken);

      proposal.setPhase(MiConstants.PHASE_DONE);
      proposal.setRef(outcome.ref());
      proposals.save(proposal);

      activityService.log(
          proposal.getCustomerId(),
          MiConstants.EXECUTED_BY_AUTO.equals(executedBy)
              ? MiConstants.LOG_AUTO
              : MiConstants.LOG_CONFIRM,
          "mi.log." + proposal.getType().toLowerCase(Locale.ROOT) + ".title",
          Map.of("0", money(proposal.getAmount(), locale)),
          "mi.log.done.subtitle",
          Map.of("0", outcome.ref() == null ? "" : outcome.ref()),
          proposal.getId(),
          outcome.ref(),
          null);

      eventBus.publish(
          "msb.mi.proposalExecuted.v1",
          proposal.getCustomerId(),
          objectMapper.writeValueAsString(
              Map.of(
                  "customerId", proposal.getCustomerId(),
                  "proposalId", proposal.getId(),
                  "type", proposal.getType(),
                  "amount", proposal.getAmount(),
                  "executedBy", executedBy,
                  "ref", outcome.ref() == null ? "" : outcome.ref())));

    } catch (BusinessException e) {
      // A downstream refusal is the customer's answer, not a 500: the card says BLOCKED and the
      // domain code travels in the log so the app can explain it.
      proposal.setPhase(MiConstants.PHASE_BLOCKED);
      proposals.save(proposal);
      activityService.log(
          proposal.getCustomerId(),
          MiConstants.LOG_BLOCKED,
          "mi.log.blocked.title",
          Map.of("0", money(proposal.getAmount(), locale)),
          "mi.log.blocked.subtitle",
          Map.of("0", e.getErrorCode().code),
          proposal.getId(),
          null,
          null);
      log.info("proposal {} blocked by {}", proposal.getId(), e.getErrorCode().code);
    }
    return card(proposal, locale);
  }

  private void expire(Proposal proposal) {
    proposal.setPhase(MiConstants.PHASE_EXPIRED);
    proposal.setDecidedAt(Instant.now());
    proposals.save(proposal);
    activityService.log(
        proposal.getCustomerId(),
        MiConstants.LOG_BLOCKED,
        "mi.log.expired.title",
        Map.of("0", money(proposal.getAmount(), AppConstants.DEFAULT_LOCALE)),
        "mi.log.expired.subtitle",
        Map.of(),
        proposal.getId(),
        null,
        null);
  }

  private Proposal owned(UUID customerId, UUID proposalId) {
    return proposals
        .findByIdAndCustomerId(proposalId, customerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  @SuppressWarnings("unchecked")
  private ProposalCardDto card(Proposal proposal, Locale locale) {
    Map<String, Object> stored =
        objectMapper.readValue(proposal.getCard(), new TypeReference<Map<String, Object>>() {});
    // Rows are stored as message keys where Mi produced them and as ready text where a peer did,
    // so each label is resolved with itself as the fallback.
    List<KeyValueRow> rows =
        objectMapper
            .convertValue(
                stored.getOrDefault("rows", List.of()), new TypeReference<List<KeyValueRow>>() {})
            .stream()
            .map(row -> new KeyValueRow(messages.getMessage(row.k(), null, row.k(), locale),
                row.v()))
            .toList();
    String titleKey = String.valueOf(stored.getOrDefault("titleKey", "mi.card.title.generic"));

    return new ProposalCardDto(
        proposal.getId(),
        proposal.getType(),
        messages.getMessage(titleKey, null, titleKey, locale),
        proposal.getAmount(),
        rows,
        proposal.getPhase(),
        messages.getMessage(
            "mi.card.status." + proposal.getPhase(), null, proposal.getPhase(), locale),
        proposal.isRequiresStepUp(),
        proposal.getStepUpScope(),
        messages.getMessage("mi.card.footnote", null, "", locale),
        proposal.getRef(),
        proposal.getExpiresAt(),
        MiConstants.INVALIDATES.getOrDefault(proposal.getType(), List.of("home")));
  }

  private Map<String, Object> params(Proposal proposal) {
    return objectMapper.readValue(
        proposal.getParams(), new TypeReference<Map<String, Object>>() {});
  }

  private String money(BigDecimal amount, Locale locale) {
    NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setMaximumFractionDigits(0);
    return format.format(amount == null ? BigDecimal.ZERO : amount) + " ₫";
  }
}
