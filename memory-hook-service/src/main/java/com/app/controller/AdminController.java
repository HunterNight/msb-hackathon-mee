package com.app.controller;

import com.app.constant.AppConstants;
import com.app.constant.Routes;
import com.app.dto.request.MemoryRequests.KeyRotationRequest;
import com.app.dto.request.MemoryRequests.ReplayRequest;
import com.app.dto.response.ApiResponse;
import com.app.dto.response.MemoryResponses.AuditRow;
import com.app.dto.response.MemoryResponses.DiscardedResponse;
import com.app.dto.response.MemoryResponses.DlqActionResponse;
import com.app.dto.response.MemoryResponses.DlqRow;
import com.app.dto.response.MemoryResponses.KeyRotationResponse;
import com.app.dto.response.MemoryResponses.PoisoningMetrics;
import com.app.dto.response.MemoryResponses.ReplayResponse;
import com.app.service.AdminService;
import com.app.service.AuditService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ops network only, scope {@code memory:admin} (design §6). */
@RestController
@PreAuthorize("hasAuthority('SCOPE_memory:admin')")
public class AdminController {

  private final AdminService adminService;
  private final AuditService auditService;

  public AdminController(AdminService adminService, AuditService auditService) {
    this.adminService = adminService;
    this.auditService = auditService;
  }

  @PostMapping(Routes.ADMIN_REPLAY)
  public ApiResponse<ReplayResponse> replay(@Valid @RequestBody ReplayRequest request) {
    return ApiResponse.ok(adminService.replay(request));
  }

  @GetMapping(Routes.ADMIN_DLQ)
  public ApiResponse<List<DlqRow>> dlq(
      @PageableDefault(size = AppConstants.PAGE_SIZE_DEFAULT) Pageable pageable) {
    return ApiResponse.page(adminService.dlq(pageable));
  }

  @PostMapping(Routes.ADMIN_DLQ_RETRY)
  public ApiResponse<DlqActionResponse> retry(@PathVariable UUID id) {
    return ApiResponse.ok(new DlqActionResponse(adminService.retry(id)));
  }

  @DeleteMapping(Routes.ADMIN_DLQ_ITEM)
  public ApiResponse<DiscardedResponse> discard(@PathVariable UUID id) {
    adminService.discard(id);
    return ApiResponse.ok(new DiscardedResponse(true));
  }

  @GetMapping(Routes.ADMIN_POISONING)
  public ApiResponse<PoisoningMetrics> poisoning(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    Instant end = to == null ? Instant.now() : to;
    Instant start = from == null ? end.minus(java.time.Duration.ofDays(7)) : from;
    return ApiResponse.ok(adminService.poisoning(start, end));
  }

  @PostMapping(Routes.ADMIN_KEYS_ROTATE)
  public ApiResponse<KeyRotationResponse> rotate(@Valid @RequestBody KeyRotationRequest request) {
    return ApiResponse.ok(adminService.rotate(request));
  }

  @GetMapping(Routes.ADMIN_AUDIT)
  public ApiResponse<List<AuditRow>> audit(
      @RequestParam(required = false) String pseudoId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = AppConstants.PAGE_SIZE_MAX) Pageable pageable) {
    Instant end = to == null ? Instant.now() : to;
    Instant start = from == null ? end.minus(java.time.Duration.ofDays(30)) : from;
    return ApiResponse.page(auditService.search(pseudoId, start, end, pageable));
  }
}
