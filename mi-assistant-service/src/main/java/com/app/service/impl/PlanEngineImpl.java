package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.response.MiResponses.PlanDto;
import com.app.dto.response.MiResponses.PlanLogEntry;
import com.app.dto.response.MiResponses.PlanStepDto;
import com.app.exception.BusinessException;
import com.app.model.Plan;
import com.app.model.PlanStep;
import com.app.repository.PlanRepository;
import com.app.repository.PlanStepRepository;
import com.app.service.ProposalService;
import com.app.service.plan.PlanEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanEngineImpl implements PlanEngine {

  private final PlanRepository plans;
  private final PlanStepRepository steps;
  private final ProposalService proposalService;
  private final ObjectMapper objectMapper;

  public PlanEngineImpl(
      PlanRepository plans,
      PlanStepRepository steps,
      ProposalService proposalService,
      ObjectMapper objectMapper) {
    this.plans = plans;
    this.steps = steps;
    this.proposalService = proposalService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanDto> list(UUID customerId, String status) {
    return plans
        .findByCustomerIdAndStatusOrderByCreatedAtDesc(
            customerId, status == null ? MiConstants.PLAN_ACTIVE : status)
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PlanDto get(UUID customerId, UUID planId) {
    return toDto(owned(customerId, planId));
  }

  @Override
  @Transactional
  public PlanDto approve(
      UUID customerId, UUID planId, Integer stepSeq, String stepUpToken, Locale locale) {

    Plan plan = owned(customerId, planId);
    if (plan.getExpiresAt() != null && plan.getExpiresAt().isBefore(Instant.now())) {
      throw new BusinessException(ErrorCode.PLAN_EXPIRED);
    }
    if (MiConstants.PLAN_CANCELLED.equals(plan.getStatus())) {
      throw new BusinessException(ErrorCode.PROPOSAL_NOT_PENDING,
          Map.of("status", plan.getStatus()));
    }

    List<PlanStep> pending =
        steps.findByPlanIdOrderBySeqAsc(planId).stream()
            .filter(step -> MiConstants.STEP_PENDING.equals(step.getStatus()))
            .filter(step -> stepSeq == null || step.getSeq() == stepSeq)
            .toList();

    for (PlanStep step : pending) {
      if (step.getProposalId() == null) {
        // Nothing to execute: the step was informational, so approving it just completes it.
        step.setStatus(MiConstants.STEP_DONE);
        steps.save(step);
        continue;
      }
      step.setStatus(MiConstants.STEP_RUNNING);
      steps.save(step);
      var card = proposalService.confirm(customerId, step.getProposalId(), stepUpToken, false);
      step.setStatus(
          MiConstants.PHASE_DONE.equals(card.phase())
              ? MiConstants.STEP_DONE
              : MiConstants.STEP_FAILED);
      steps.save(step);
      appendLog(plan, "step " + step.getSeq() + " → " + step.getStatus());
    }

    boolean allDone =
        steps.findByPlanIdOrderBySeqAsc(planId).stream()
            .allMatch(step -> MiConstants.STEP_DONE.equals(step.getStatus()));
    if (allDone) {
      plan.setStatus(MiConstants.PLAN_DONE);
    }
    return toDto(plans.save(plan));
  }

  @Override
  @Transactional
  public PlanDto pause(UUID customerId, UUID planId) {
    return transition(customerId, planId, MiConstants.PLAN_PAUSED);
  }

  @Override
  @Transactional
  public PlanDto resume(UUID customerId, UUID planId) {
    return transition(customerId, planId, MiConstants.PLAN_ACTIVE);
  }

  @Override
  @Transactional
  public PlanDto cancel(UUID customerId, UUID planId) {
    return transition(customerId, planId, MiConstants.PLAN_CANCELLED);
  }

  private PlanDto transition(UUID customerId, UUID planId, String status) {
    Plan plan = owned(customerId, planId);
    plan.setStatus(status);
    appendLog(plan, "plan → " + status);
    return toDto(plans.save(plan));
  }

  private Plan owned(UUID customerId, UUID planId) {
    return plans
        .findByIdAndCustomerId(planId, customerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private void appendLog(Plan plan, String text) {
    List<PlanLogEntry> log =
        objectMapper.readValue(plan.getLog(), new TypeReference<List<PlanLogEntry>>() {});
    List<PlanLogEntry> updated = new ArrayList<>(log);
    updated.add(new PlanLogEntry(Instant.now(), text));
    plan.setLog(objectMapper.writeValueAsString(updated));
  }

  private PlanDto toDto(Plan plan) {
    List<PlanStepDto> stepDtos =
        steps.findByPlanIdOrderBySeqAsc(plan.getId()).stream()
            .map(
                step ->
                    new PlanStepDto(
                        step.getSeq(),
                        step.getKind(),
                        step.getTitle(),
                        step.getStatus(),
                        step.getAmount(),
                        step.isRequiresApproval(),
                        step.getProposalId(),
                        step.getWaitingFor()))
            .toList();

    BigDecimal total =
        stepDtos.stream()
            .map(PlanStepDto::amount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new PlanDto(
        plan.getId(),
        plan.getTitle(),
        plan.getStatus(),
        plan.getCreatedAt(),
        stepDtos,
        total,
        // "Approve all" only when nothing in the plan waits on an external event.
        stepDtos.stream().noneMatch(step -> step.waitingFor() != null),
        objectMapper.readValue(plan.getLog(), new TypeReference<List<PlanLogEntry>>() {}));
  }
}
