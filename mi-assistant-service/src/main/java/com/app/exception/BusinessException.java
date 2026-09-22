package com.app.exception;

import com.app.constant.ErrorCode;
import java.util.Map;

/**
 * The only exception business code throws. {@link GlobalExceptionHandler} turns it into the
 * response envelope; nothing else catches it (guideline 01 §4).
 */
public class BusinessException extends RuntimeException {

  private final transient ErrorCode errorCode;
  private final transient Object[] args;
  private final transient Map<String, Object> details;

  public BusinessException(ErrorCode errorCode, Object... args) {
    this(errorCode, Map.of(), args);
  }

  public BusinessException(ErrorCode errorCode, Map<String, Object> details, Object... args) {
    super(errorCode.code);
    this.errorCode = errorCode;
    this.args = args == null ? new Object[0] : args;
    this.details = details == null ? Map.of() : details;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Object[] getArgs() {
    return args;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
