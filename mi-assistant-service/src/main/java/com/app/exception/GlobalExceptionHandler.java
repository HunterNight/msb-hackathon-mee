package com.app.exception;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.dto.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * The single {@code @RestControllerAdvice} of this service. Controllers and services throw; nothing
 * catches and maps by hand (guideline 01 §4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final MessageSource messages;

  public GlobalExceptionHandler(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(
      BusinessException ex, Locale locale, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getArgs(), ex.getDetails(), locale, request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(
      MethodArgumentNotValidException ex, Locale locale, HttpServletRequest request) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(error.getField(), messages.getMessage(error, locale));
    }
    return build(
        ErrorCode.VALIDATION_FAILED, new Object[0], Map.of("fields", fields), locale, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> parameterValidation(
      HandlerMethodValidationException ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.VALIDATION_FAILED, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> malformed(
      Exception ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.VALIDATION_FAILED, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler({AuthenticationException.class, JwtException.class})
  public ResponseEntity<ApiResponse<Void>> unauthenticated(
      Exception ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.UNAUTHORIZED, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> denied(
      Exception ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.FORBIDDEN, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler({EntityNotFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ApiResponse<Void>> notFound(
      Exception ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.NOT_FOUND, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiResponse<Void>> conflict(
      Exception ex, Locale locale, HttpServletRequest request) {
    log.warn("persistence conflict: {}", ex.getMessage());
    return build(ErrorCode.CONFLICT, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> method(
      Exception ex, Locale locale, HttpServletRequest request) {
    return build(ErrorCode.NOT_FOUND, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler(RestClientException.class)
  public ResponseEntity<ApiResponse<Void>> upstream(
      RestClientException ex, Locale locale, HttpServletRequest request) {
    log.warn("upstream call failed: {}", ex.getMessage());
    return build(ErrorCode.UPSTREAM_UNAVAILABLE, new Object[0], Map.of(), locale, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> fallback(
      Exception ex, Locale locale, HttpServletRequest request) {
    log.error("unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(ErrorCode.INTERNAL, new Object[0], Map.of(), locale, request);
  }

  private ResponseEntity<ApiResponse<Void>> build(
      ErrorCode code,
      Object[] args,
      Map<String, Object> details,
      Locale locale,
      HttpServletRequest request) {
    String message = messages.getMessage(code.messageKey, args, code.messageKey, locale);
    return ResponseEntity.status(code.status)
        // Stated explicitly rather than negotiated. The chat endpoint is requested with
        // `Accept: text/event-stream`, and content negotiation then finds no converter able to
        // write a JSON error body for it — the handler itself failed with
        // HttpMediaTypeNotAcceptableException ("No acceptable representation") and the customer got
        // a dropped connection with no error at all. Naming the type skips negotiation, so an error
        // on the SSE endpoint still arrives as a readable envelope the app can map.
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            ApiResponse.error(
                code.code, message, details, request.getHeader(AppConstants.HDR_REQUEST_ID)));
  }
}
