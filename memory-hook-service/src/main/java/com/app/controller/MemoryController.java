package com.app.controller;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.constant.Routes;
import com.app.dto.request.MemoryRequests.CandidateRequest;
import com.app.dto.request.MemoryRequests.ConsentRequest;
import com.app.dto.request.MemoryRequests.EditRequest;
import com.app.dto.request.MemoryRequests.ErasureRequestBody;
import com.app.dto.request.MemoryRequests.RememberRequest;
import com.app.dto.response.ApiResponse;
import com.app.dto.response.MemoryResponses.CandidateDecisionResponse;
import com.app.dto.response.MemoryResponses.ConsentResponse;
import com.app.dto.response.MemoryResponses.DeletedResponse;
import com.app.dto.response.MemoryResponses.ErasureResponse;
import com.app.dto.response.MemoryResponses.ExportResponse;
import com.app.dto.response.MemoryResponses.MemoryGraphResponse;
import com.app.dto.response.MemoryResponses.MemoryRecordDto;
import com.app.dto.response.MemoryResponses.RecallResponse;
import com.app.dto.response.MemoryResponses.WhyResponse;
import com.app.exception.BusinessException;
import com.app.service.ConsentService;
import com.app.service.ErasureService;
import com.app.service.ExportService;
import com.app.service.MemoryRecordService;
import com.app.service.RecallService;
import com.app.service.SnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * The whole customer-facing surface, internal only. Every method names its scope explicitly rather
 * than relying on a path prefix, because these grants are not interchangeable (design §6).
 */
@RestController
public class MemoryController {

  private final RecallService recallService;
  private final SnapshotService snapshotService;
  private final MemoryRecordService recordService;
  private final ConsentService consentService;
  private final ErasureService erasureService;
  private final ExportService exportService;

  public MemoryController(
      RecallService recallService,
      SnapshotService snapshotService,
      MemoryRecordService recordService,
      ConsentService consentService,
      ErasureService erasureService,
      ExportService exportService) {
    this.recallService = recallService;
    this.snapshotService = snapshotService;
    this.recordService = recordService;
    this.consentService = consentService;
    this.erasureService = erasureService;
    this.exportService = exportService;
  }

  @GetMapping(Routes.RECALL)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<RecallResponse> recall(
      @PathVariable UUID customerId,
      @RequestParam @Size(max = MemoryConstants.RECALL_QUESTION_MAX) String q,
      @RequestParam(required = false, defaultValue = "0") int k,
      @RequestParam(required = false) String kinds,
      @RequestParam(required = false, defaultValue = "false") boolean strict,
      @AuthenticationPrincipal Jwt jwt) {

    List<String> kindList = kinds == null ? List.of() : Arrays.asList(kinds.split(","));
    return ApiResponse.ok(
        recallService.recall(customerId, q, k, kindList, strict, actor(jwt)));
  }

  @GetMapping(Routes.SNAPSHOT)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<Object> snapshot(
      @PathVariable UUID customerId,
      @RequestParam(defaultValue = MemoryConstants.SLICE_PROMPT) String slice,
      @RequestHeader(value = MemoryConstants.HDR_MEMORY_SLICE, required = false) String sliceHeader,
      @AuthenticationPrincipal Jwt jwt) {

    if (MemoryConstants.SLICE_TRIGGER.equals(slice)) {
      // The exact slice is for the proactive policy engine only: the header proves the caller
      // meant to ask for it, and `azp` proves which service is asking.
      boolean confirmed = MemoryConstants.SLICE_TRIGGER.equals(sliceHeader);
      boolean fromMi =
          jwt != null
              && MemoryConstants.TRIGGER_SLICE_CALLER.equals(jwt.getClaimAsString("azp"));
      if (!confirmed || !fromMi) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }
      return ApiResponse.ok(snapshotService.triggerSlice(customerId, actor(jwt)));
    }
    return ApiResponse.ok(snapshotService.promptSlice(customerId, actor(jwt)));
  }

  @GetMapping(Routes.RECORDS)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<List<MemoryRecordDto>> records(
      @PathVariable UUID customerId,
      @RequestParam(required = false) String kind,
      @PageableDefault(size = AppConstants.PAGE_SIZE_DEFAULT) Pageable pageable) {
    return ApiResponse.page(recordService.list(customerId, kind, pageable));
  }

  @PostMapping(Routes.RECORDS)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<MemoryRecordDto> remember(
      @PathVariable UUID customerId,
      @Valid @RequestBody RememberRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.remember(customerId, request, actor(jwt)));
  }

  @PostMapping(Routes.CANDIDATES)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<CandidateDecisionResponse> submitCandidate(
      @PathVariable UUID customerId,
      @Valid @RequestBody CandidateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    var decision = recordService.submitCandidate(customerId, request, actor(jwt));
    return ApiResponse.ok(new CandidateDecisionResponse(decision.decision(), decision.record()));
  }

  @PostMapping(Routes.CONFIRM)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<MemoryRecordDto> confirm(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.confirm(customerId, id, actor(jwt)));
  }

  @PostMapping(Routes.REJECT)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<MemoryRecordDto> reject(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.reject(customerId, id, actor(jwt)));
  }

  @PatchMapping(Routes.RECORD)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<MemoryRecordDto> editRecord(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @Valid @RequestBody EditRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.edit(customerId, id, request, actor(jwt)));
  }

  @PostMapping(Routes.DEACTIVATE)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<MemoryRecordDto> deactivate(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.deactivate(customerId, id, actor(jwt)));
  }

  @PostMapping(Routes.FORGET)
  @PreAuthorize("hasAuthority('SCOPE_memory:write:chat')")
  public ApiResponse<DeletedResponse> forget(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    recordService.forget(customerId, id, actor(jwt));
    return ApiResponse.ok(new DeletedResponse(true));
  }

  @GetMapping(Routes.HYPOTHESES)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<MemoryRecordDto> hypotheses(
      @PathVariable UUID customerId,
      @RequestParam(defaultValue = "1") int limit,
      @AuthenticationPrincipal Jwt jwt) {
    Optional<MemoryRecordDto> hypothesis = recordService.nextHypothesis(customerId, actor(jwt));
    return ApiResponse.ok(hypothesis.orElse(null));
  }

  @GetMapping(Routes.GRAPH)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<MemoryGraphResponse> graph(
      @PathVariable UUID customerId, @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(recordService.graph(customerId, actor(jwt)));
  }

  @GetMapping(Routes.WHY)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<WhyResponse> why(
      @PathVariable UUID customerId,
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    String evidence = recordService.why(customerId, id, actor(jwt));
    return ApiResponse.ok(new WhyResponse(id, null, null, evidence, null, null));
  }

  @DeleteMapping(Routes.RECORD)
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ApiResponse<DeletedResponse> deleteRecord(
      @PathVariable UUID customerId, @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    recordService.delete(customerId, id, actor(jwt));
    return ApiResponse.ok(new DeletedResponse(true));
  }

  @DeleteMapping(Routes.CUSTOMER)
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ResponseEntity<ApiResponse<ErasureResponse>> erase(
      @PathVariable UUID customerId,
      @Valid @RequestBody ErasureRequestBody body,
      @AuthenticationPrincipal Jwt jwt) {
    // 202: the work is queued and the customer is told when it will be done, not that it is.
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.ok(erasureService.request(customerId, body, actor(jwt))));
  }

  @GetMapping(Routes.ERASURE)
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ApiResponse<ErasureResponse> erasure(
      @PathVariable UUID customerId, @PathVariable UUID id) {
    return ApiResponse.ok(erasureService.status(customerId, id));
  }

  @GetMapping(Routes.CONSENT)
  @PreAuthorize("hasAuthority('SCOPE_memory:read')")
  public ApiResponse<ConsentResponse> consent(@PathVariable UUID customerId) {
    return ApiResponse.ok(consentService.get(customerId));
  }

  @PutMapping(Routes.CONSENT)
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ApiResponse<ConsentResponse> updateConsent(
      @PathVariable UUID customerId,
      @Valid @RequestBody ConsentRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(consentService.update(customerId, request, actor(jwt)));
  }

  @GetMapping(Routes.EXPORT)
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ApiResponse<ExportResponse> export(
      @PathVariable UUID customerId, @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(exportService.export(customerId, actor(jwt)));
  }

  /** The signed URL from {@code /export} lands here; the token is single-use. */
  @GetMapping(Routes.MEMORY + "/exports/{token}")
  @PreAuthorize("hasAuthority('SCOPE_memory:erase')")
  public ResponseEntity<String> download(@PathVariable String token) {
    return ResponseEntity.ok()
        .header("Content-Type", "application/json")
        .header("Content-Disposition", "attachment; filename=\"memory-export.json\"")
        .body(exportService.fetch(token));
  }

  private String actor(Jwt jwt) {
    if (jwt == null) {
      return "system";
    }
    String azp = jwt.getClaimAsString("azp");
    return azp != null ? azp : jwt.getSubject();
  }
}
