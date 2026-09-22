package com.app.controller;

import com.app.config.CurrentCustomer;
import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.constant.Routes;
import com.app.dto.request.MiRequests.ApprovePlanRequest;
import com.app.dto.request.MiRequests.ConfirmProposalRequest;
import com.app.dto.request.MiRequests.ConsentsRequest;
import com.app.dto.request.MiRequests.MemoryConsentRequest;
import com.app.dto.request.MiRequests.UpdateSettingsRequest;
import com.app.dto.response.ApiResponse;
import com.app.dto.response.MiResponses.ActivityGroupDto;
import com.app.dto.response.MiResponses.AgentSummaryDto;
import com.app.dto.response.MiResponses.CandidateDecisionDto;
import com.app.dto.response.MiResponses.CandidateRequestDto;
import com.app.dto.response.MiResponses.ConsentsResponse;
import com.app.dto.response.MiResponses.DataUsageResponse;
import com.app.dto.response.MiResponses.DataUsageSection;
import com.app.dto.response.MiResponses.DeletedCountResponse;
import com.app.dto.response.MiResponses.DeletedResponse;
import com.app.dto.response.MiResponses.DismissedResponse;
import com.app.dto.response.MiResponses.EditMemoryRequest;
import com.app.dto.response.MiResponses.ExplainResponse;
import com.app.dto.response.MiResponses.InsightDto;
import com.app.dto.response.MiResponses.MemoryConsentDto;
import com.app.dto.response.MiResponses.MemoryGraphDto;
import com.app.dto.response.MiResponses.MemoryRecordDto;
import com.app.dto.response.MiResponses.MemoryResponse;
import com.app.dto.response.MiResponses.MemoryWhyDto;
import com.app.dto.response.MiResponses.NudgeDto;
import com.app.dto.response.MiResponses.PlanDto;
import com.app.dto.response.MiResponses.ProposalCardDto;
import com.app.dto.response.MiResponses.SettingsResponse;
import com.app.exception.BusinessException;
import com.app.service.ActivityService;
import com.app.service.InsightService;
import com.app.service.MiSettingsService;
import com.app.service.ProposalService;
import com.app.service.memory.MemoryService;
import com.app.service.plan.PlanEngine;
import com.app.service.proactive.TriggerEngine;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Proposals, settings, activity, insights, memory, plans and nudges (screens 09–10). */
@RestController
@PreAuthorize("hasRole('customer')")
public class MiController {

  private final ProposalService proposalService;
  private final MiSettingsService settingsService;
  private final ActivityService activityService;
  private final InsightService insightService;
  private final MemoryService memoryService;
  private final PlanEngine planEngine;
  private final TriggerEngine triggerEngine;
  private final MessageSource messages;

  public MiController(
      ProposalService proposalService,
      MiSettingsService settingsService,
      ActivityService activityService,
      InsightService insightService,
      MemoryService memoryService,
      PlanEngine planEngine,
      TriggerEngine triggerEngine,
      MessageSource messages) {
    this.proposalService = proposalService;
    this.settingsService = settingsService;
    this.activityService = activityService;
    this.insightService = insightService;
    this.memoryService = memoryService;
    this.planEngine = planEngine;
    this.triggerEngine = triggerEngine;
    this.messages = messages;
  }

  // ── proposals ──────────────────────────────────────────────────────────────

  @PostMapping(Routes.PROPOSAL_CONFIRM)
  public ApiResponse<ProposalCardDto> confirm(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @RequestHeader(value = AppConstants.HDR_STEP_UP, required = false) String stepUpToken,
      @RequestBody(required = false) ConfirmProposalRequest request) {
    return ApiResponse.ok(
        proposalService.confirm(
            customerId, id, stepUpToken, request != null && request.acceptMismatch()));
  }

  @PostMapping(Routes.PROPOSAL_CANCEL)
  public ApiResponse<ProposalCardDto> cancel(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(proposalService.cancel(customerId, id));
  }

  @GetMapping(Routes.PROPOSAL)
  public ApiResponse<ProposalCardDto> proposal(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(proposalService.get(customerId, id));
  }

  // ── settings ───────────────────────────────────────────────────────────────

  @GetMapping(Routes.SETTINGS)
  public ApiResponse<SettingsResponse> settings(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(settingsService.settings(customerId));
  }

  @PutMapping(Routes.SETTINGS)
  public ApiResponse<SettingsResponse> updateSettings(
      @CurrentCustomer UUID customerId, @Valid @RequestBody UpdateSettingsRequest request) {
    return ApiResponse.ok(settingsService.update(customerId, request));
  }

  @PostMapping(Routes.SETTINGS_PAUSE)
  public ApiResponse<SettingsResponse> pause(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(settingsService.pause(customerId, true));
  }

  @PostMapping(Routes.SETTINGS_RESUME)
  public ApiResponse<SettingsResponse> resume(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(settingsService.pause(customerId, false));
  }

  @GetMapping(Routes.CONSENTS)
  public ApiResponse<ConsentsResponse> consents(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(settingsService.consents(customerId));
  }

  @PutMapping(Routes.CONSENTS)
  public ApiResponse<ConsentsResponse> updateConsents(
      @CurrentCustomer UUID customerId, @Valid @RequestBody ConsentsRequest request) {
    return ApiResponse.ok(settingsService.updateConsents(customerId, request));
  }

  // ── activity, insights ─────────────────────────────────────────────────────

  @GetMapping(Routes.ACTIVITY)
  public ApiResponse<List<ActivityGroupDto>> activity(
      @CurrentCustomer UUID customerId,
      @RequestParam(defaultValue = "ALL") String filter,
      @PageableDefault(size = MiConstants.ACTIVITY_PAGE_SIZE) Pageable pageable,
      Locale locale) {
    return ApiResponse.ok(activityService.feed(customerId, filter, pageable, locale));
  }

  @GetMapping(Routes.INSIGHTS)
  public ApiResponse<InsightDto> insights(
      @CurrentCustomer UUID customerId, @RequestParam String placement, Locale locale) {
    if (!MiConstants.PLACEMENTS.contains(placement)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          java.util.Map.of("placement", placement));
    }
    return ApiResponse.ok(insightService.forPlacement(customerId, placement, locale));
  }

  @GetMapping(Routes.DATA_USAGE)
  public ApiResponse<DataUsageResponse> dataUsage(Locale locale) {
    List<DataUsageSection> sections =
        List.of("balances", "transactions", "products", "memory", "retention").stream()
            .map(
                key ->
                    new DataUsageSection(
                        messages.getMessage("mi.data." + key + ".heading", null, locale),
                        messages.getMessage("mi.data." + key + ".body", null, locale)))
            .toList();
    return ApiResponse.ok(
        new DataUsageResponse(messages.getMessage("mi.settings.data", null, locale), sections));
  }

  @GetMapping(Routes.AGENTS)
  public ApiResponse<List<AgentSummaryDto>> agents(Locale locale) {
    return ApiResponse.ok(settingsService.agents(locale));
  }

  // ── memory ─────────────────────────────────────────────────────────────────

  @GetMapping(Routes.MEMORY)
  public ApiResponse<MemoryResponse> memory(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(memoryService.list(customerId));
  }

  @DeleteMapping(Routes.MEMORY_RECORD)
  public ApiResponse<DeletedResponse> deleteMemory(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(new DeletedResponse(memoryService.delete(customerId, id)));
  }

  @DeleteMapping(Routes.MEMORY)
  public ApiResponse<DeletedCountResponse> deleteAllMemory(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(new DeletedCountResponse(memoryService.deleteAll(customerId)));
  }

  @PutMapping(Routes.MEMORY_CONSENT)
  public ApiResponse<MemoryConsentDto> memoryConsent(
      @CurrentCustomer UUID customerId, @Valid @RequestBody MemoryConsentRequest request) {
    return ApiResponse.ok(
        new MemoryConsentDto(memoryService.setConsent(customerId, request.longTerm())));
  }

  @PostMapping(Routes.MEMORY_CANDIDATES)
  public ApiResponse<CandidateDecisionDto> submitCandidate(
      @CurrentCustomer UUID customerId, @Valid @RequestBody CandidateRequestDto request) {
    return ApiResponse.ok(memoryService.submitCandidate(customerId, request));
  }

  @PostMapping(Routes.MEMORY_CONFIRM)
  public ApiResponse<MemoryRecordDto> confirmMemory(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(memoryService.confirm(customerId, id));
  }

  @PostMapping(Routes.MEMORY_REJECT)
  public ApiResponse<MemoryRecordDto> rejectMemory(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(memoryService.reject(customerId, id));
  }

  @PatchMapping(Routes.MEMORY_RECORD)
  public ApiResponse<MemoryRecordDto> editMemory(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @Valid @RequestBody EditMemoryRequest request) {
    return ApiResponse.ok(memoryService.edit(customerId, id, request));
  }

  @PostMapping(Routes.MEMORY_DEACTIVATE)
  public ApiResponse<MemoryRecordDto> deactivateMemory(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(memoryService.deactivate(customerId, id));
  }

  @PostMapping(Routes.MEMORY_FORGET)
  public ApiResponse<DeletedResponse> forgetMemory(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(new DeletedResponse(memoryService.forget(customerId, id)));
  }

  @GetMapping(Routes.MEMORY_HYPOTHESES)
  public ApiResponse<MemoryRecordDto> memoryHypotheses(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(memoryService.nextHypothesis(customerId));
  }

  @GetMapping(Routes.MEMORY_GRAPH)
  public ApiResponse<MemoryGraphDto> memoryGraph(@CurrentCustomer UUID customerId) {
    return ApiResponse.ok(memoryService.graph(customerId));
  }

  @GetMapping(Routes.MEMORY_WHY)
  public ApiResponse<MemoryWhyDto> memoryWhy(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(memoryService.why(customerId, id));
  }

  // ── plans, nudges, explain ─────────────────────────────────────────────────

  @GetMapping(Routes.PLANS)
  public ApiResponse<List<PlanDto>> plans(
      @CurrentCustomer UUID customerId,
      @RequestParam(defaultValue = MiConstants.PLAN_ACTIVE) String status) {
    return ApiResponse.ok(planEngine.list(customerId, status));
  }

  @GetMapping(Routes.PLAN)
  public ApiResponse<PlanDto> plan(@CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(planEngine.get(customerId, id));
  }

  @PostMapping(Routes.PLAN_APPROVE)
  public ApiResponse<PlanDto> approvePlan(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @RequestHeader(value = AppConstants.HDR_STEP_UP, required = false) String stepUpToken,
      @RequestBody(required = false) ApprovePlanRequest request,
      Locale locale) {
    return ApiResponse.ok(
        planEngine.approve(
            customerId, id, request == null ? null : request.stepSeq(), stepUpToken, locale));
  }

  @PostMapping(Routes.PLAN_PAUSE)
  public ApiResponse<PlanDto> pausePlan(@CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(planEngine.pause(customerId, id));
  }

  @PostMapping(Routes.PLAN_RESUME)
  public ApiResponse<PlanDto> resumePlan(@CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(planEngine.resume(customerId, id));
  }

  @PostMapping(Routes.PLAN_CANCEL)
  public ApiResponse<PlanDto> cancelPlan(@CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(planEngine.cancel(customerId, id));
  }

  @GetMapping(Routes.NUDGES)
  public ApiResponse<List<NudgeDto>> nudges(@CurrentCustomer UUID customerId, Locale locale) {
    return ApiResponse.ok(settingsService.nudges(customerId, locale));
  }

  @PostMapping(Routes.NUDGE_DISMISS)
  public ApiResponse<DismissedResponse> dismiss(
      @CurrentCustomer UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(new DismissedResponse(triggerEngine.dismiss(customerId, id)));
  }

  @GetMapping(Routes.EXPLAIN)
  public ApiResponse<ExplainResponse> explain(
      @CurrentCustomer UUID customerId, @PathVariable UUID decisionId, Locale locale) {
    return ApiResponse.ok(settingsService.explain(customerId, decisionId, locale));
  }
}
