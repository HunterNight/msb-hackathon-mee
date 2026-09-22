package com.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;

/**
 * The single response envelope for every endpoint of every service (guideline 01 §5).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
    boolean success, T data, Meta meta, ErrorBody error, String requestId, Instant timestamp) {

  public record Meta(Integer page, Integer size, Long total) {}

  public record ErrorBody(String code, String message, Map<String, Object> details) {}

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, MDC.get("requestId"), Instant.now());
  }

  public static <T> ApiResponse<List<T>> page(Page<T> page) {
    return new ApiResponse<>(
        true,
        page.getContent(),
        new Meta(page.getNumber(), page.getSize(), page.getTotalElements()),
        null,
        MDC.get("requestId"),
        Instant.now());
  }

  public static <T> ApiResponse<List<T>> page(List<T> content, Page<?> page) {
    return new ApiResponse<>(
        true,
        content,
        new Meta(page.getNumber(), page.getSize(), page.getTotalElements()),
        null,
        MDC.get("requestId"),
        Instant.now());
  }

  public static ApiResponse<Void> error(
      String code, String message, Map<String, Object> details, String requestId) {
    return new ApiResponse<>(
        false, null, null, new ErrorBody(code, message, details), requestId, Instant.now());
  }
}
