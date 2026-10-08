package com.xq.routineservice.shared;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final String field;
  private final String retryAfter;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, null, null);
  }

  public ApiException(HttpStatus status, String code, String message, String field) {
    this(status, code, message, field, null);
  }

  public ApiException(
      HttpStatus status, String code, String message, String field, String retryAfter) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
    this.retryAfter = retryAfter;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String field() {
    return field;
  }

  public String retryAfter() {
    return retryAfter;
  }

  public static ApiException badRequest(String c, String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, c, m);
  }

  public static ApiException validation(String m, String f) {
    return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", m, f);
  }

  public static ApiException notFound(String c, String m) {
    return new ApiException(HttpStatus.NOT_FOUND, c, m);
  }
}
