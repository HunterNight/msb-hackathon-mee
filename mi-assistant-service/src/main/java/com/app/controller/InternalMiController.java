package com.app.controller;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.constant.Routes;
import com.app.dto.request.MiRequests.AgentEventRequest;
import com.app.dto.request.MiRequests.CodeEventRequest;
import com.app.dto.request.MiRequests.DecideRequest;
import com.app.dto.request.MiRequests.EvalRunRequest;
import com.app.dto.request.MiRequests.KnowledgeEventRequest;
import com.app.dto.request.MiRequests.LogActivityRequest;
import com.app.dto.request.MiRequests.ReindexRequest;
import com.app.dto.request.MiRequests.RetrievalPreviewRequest;
import com.app.dto.response.ApiResponse;
import com.app.dto.response.MiResponses.AcceptedResponse;
import com.app.dto.response.MiResponses.DecideResponse;
import com.app.dto.response.MiResponses.EvalRunResponse;
import com.app.dto.response.MiResponses.FeatureFlagsResponse;
import com.app.dto.response.MiResponses.IdResponse;
import com.app.dto.response.MiResponses.InsightDto;
import com.app.dto.response.MiResponses.MaskedTranscriptResponse;
import com.app.dto.response.MiResponses.McpServerDto;
import com.app.dto.response.MiResponses.ReindexResponse;
import com.app.dto.response.MiResponses.RetrievalChunkDto;
import com.app.exception.BusinessException;
import com.app.service.ActivityService;
import com.app.service.ChatService;
import com.app.service.EvaluationService;
import com.app.service.InsightService;
import com.app.service.MiSettingsService;
import com.app.service.agent.AgentRegistry;
import com.app.service.rag.KnowledgeIndexer;
import com.app.service.rag.RetrievalService;
import com.app.service.security.WebhookVerifier;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What peers and the CMS call. Each route names the scope it needs (guideline 05 §2.2). */
@RestController
public class InternalMiController {

  private final ActivityService activityService;
  private final InsightService insightService;
  private final MiSettingsService settingsService;
  private final KnowledgeIndexer indexer;
  private final RetrievalService retrieval;
  private final AgentRegistry registry;
  private final EvaluationService evaluationService;
  private final ChatService chatService;
  private final WebhookVerifier webhookVerifier;
  private final tools.jackson.databind.ObjectMapper objectMapper;
  private final boolean chatPayEnabled;
  private final boolean multiAgentEnabled;

  public InternalMiController(
      ActivityService activityService,
      InsightService insightService,
      MiSettingsService settingsService,
      KnowledgeIndexer indexer,
      RetrievalService retrieval,
      AgentRegistry registry,
      EvaluationService evaluationService,
      ChatService chatService,
      WebhookVerifier webhookVerifier,
      tools.jackson.databind.ObjectMapper objectMapper,
      @Value("${app.mi.chatpay.enabled}") boolean chatPayEnabled,
      @Value("${app.mi.multiagent.enabled}") boolean multiAgentEnabled) {
    this.activityService = activityService;
    this.insightService = insightService;
    this.settingsService = settingsService;
    this.indexer = indexer;
    this.retrieval = retrieval;
    this.registry = registry;
    this.evaluationService = evaluationService;
    this.chatService = chatService;
    this.webhookVerifier = webhookVerifier;
    this.objectMapper = objectMapper;
    this.chatPayEnabled = chatPayEnabled;
    this.multiAgentEnabled = multiAgentEnabled;
  }

  @PostMapping(Routes.INTERNAL_ACTIVITY)
  @PreAuthorize("hasAuthority('SCOPE_mi:activity')")
  public ApiResponse<IdResponse> logActivity(@Valid @RequestBody LogActivityRequest request) {
    return ApiResponse.ok(new IdResponse(activityService.log(request)));
  }

  @PostMapping(Routes.INTERNAL_DECIDE)
  @PreAuthorize("hasAuthority('SCOPE_mi:activity')")
  public ApiResponse<DecideResponse> decide(
      @PathVariable UUID customerId, @Valid @RequestBody DecideRequest request) {
    return ApiResponse.ok(
        settingsService.decide(
            customerId, request.type(), request.amount(), request.beneficiarySaved()));
  }

  @GetMapping(Routes.INTERNAL_INSIGHTS)
  @PreAuthorize("hasAuthority('SCOPE_mi:insights')")
  public ApiResponse<InsightDto> insights(
      @PathVariable UUID customerId, @RequestParam String placement, Locale locale) {
    return ApiResponse.ok(insightService.forPlacement(customerId, placement, locale));
  }

  // ── CMS webhooks ───────────────────────────────────────────────────────────

  @PostMapping(Routes.INTERNAL_KNOWLEDGE_EVENTS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<AcceptedResponse> knowledgeEvent(
      @RequestHeader(value = MiConstants.HDR_WEBHOOK_SIGNATURE, required = false) String signature,
      @RequestBody String rawBody) {

    // The signature covers the exact bytes, so the body is read raw and parsed afterwards.
    webhookVerifier.verify(rawBody, signature);
    KnowledgeEventRequest request = objectMapper.readValue(rawBody, KnowledgeEventRequest.class);
    if ("archived".equals(request.type())) {
      indexer.remove(request.docId());
    } else {
      indexer.index(request.docId());
    }
    return ApiResponse.ok(new AcceptedResponse(true));
  }

  @PostMapping(Routes.INTERNAL_AGENT_EVENTS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<AcceptedResponse> agentEvent(@Valid @RequestBody AgentEventRequest request) {
    registry.invalidate(request.agentCode());
    return ApiResponse.ok(new AcceptedResponse(true));
  }

  @PostMapping(Routes.INTERNAL_TRIGGER_EVENTS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<AcceptedResponse> triggerEvent(@Valid @RequestBody CodeEventRequest request) {
    registry.invalidateAll();
    return ApiResponse.ok(new AcceptedResponse(true));
  }

  @PostMapping(Routes.INTERNAL_MODEL_PROFILE_EVENTS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<AcceptedResponse> modelProfileEvent(
      @Valid @RequestBody CodeEventRequest request) {
    registry.invalidateAll();
    return ApiResponse.ok(new AcceptedResponse(true));
  }

  @PostMapping(Routes.INTERNAL_KNOWLEDGE_REINDEX)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<ReindexResponse> reindex(
      @RequestBody(required = false) ReindexRequest request) {
    int docs = indexer.reindex(request == null ? null : request.collectionCode());
    return ApiResponse.ok(new ReindexResponse(UUID.randomUUID(), docs));
  }

  @PostMapping(Routes.INTERNAL_RETRIEVAL_PREVIEW)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<List<RetrievalChunkDto>> preview(
      @Valid @RequestBody RetrievalPreviewRequest request) {
    Locale locale =
        Locale.forLanguageTag(request.locale() == null ? "vi" : request.locale());
    return ApiResponse.ok(
        retrieval
            .search(request.collections(), request.question(), locale, request.topKOrDefault())
            .stream()
            .map(
                chunk ->
                    new RetrievalChunkDto(
                        chunk.docId(),
                        chunk.title(),
                        chunk.section(),
                        chunk.score(),
                        chunk.content(),
                        chunk.deepLink()))
            .toList());
  }

  @GetMapping(Routes.INTERNAL_FEATURE_FLAGS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<FeatureFlagsResponse> flags() {
    return ApiResponse.ok(new FeatureFlagsResponse(chatPayEnabled, multiAgentEnabled));
  }

  @PostMapping(Routes.INTERNAL_EVAL_RUN)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<EvalRunResponse> evaluate(@Valid @RequestBody EvalRunRequest request) {
    if (request.cases().size() > MiConstants.EVAL_MAX_CASES) {
      throw new BusinessException(
          ErrorCode.VALIDATION_FAILED, java.util.Map.of("max", MiConstants.EVAL_MAX_CASES));
    }
    return ApiResponse.ok(evaluationService.run(request));
  }

  @GetMapping(Routes.INTERNAL_MCP_SERVERS)
  @PreAuthorize("hasAuthority('SCOPE_mi:knowledge')")
  public ApiResponse<List<McpServerDto>> mcpServers() {
    return ApiResponse.ok(evaluationService.mcpServers());
  }

  /** memory-hook only, and audited: this is the one place a transcript leaves Mi. */
  @GetMapping(Routes.INTERNAL_MASKED_TRANSCRIPT)
  @PreAuthorize("hasAuthority('SCOPE_mi:transcript')")
  public ApiResponse<MaskedTranscriptResponse> maskedTranscript(@PathVariable UUID id) {
    return ApiResponse.ok(chatService.maskedTranscript(id));
  }
}
