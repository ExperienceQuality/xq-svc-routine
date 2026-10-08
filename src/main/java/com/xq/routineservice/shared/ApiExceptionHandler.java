package com.xq.routineservice.shared;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public final class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e) {
    return response(
        e.status(),
        e.code(),
        e.getMessage(),
        e.field() == null ? null : List.of(Map.of("field", e.field(), "message", e.getMessage())),
        e.retryAfter());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    var v =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                x ->
                    Map.of(
                        "field",
                        x.getField(),
                        "message",
                        x.getDefaultMessage() == null ? "Invalid value" : x.getDefaultMessage()))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", v, null);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  ResponseEntity<ProblemDetail> malformed(Exception e) {
    String message = e.getMessage() == null ? "" : e.getMessage();
    Throwable cause = e;
    while (cause != null) {
      message += " " + String.valueOf(cause.getMessage());
      if (cause.getClass().getName().contains("UnrecognizedPropertyException"))
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Request validation failed",
            List.of(Map.of("field", "body", "message", "Unknown property")),
            null);
      cause = cause.getCause();
    }
    if (message.contains("Unrecognized field") || message.contains("unknown property"))
      return response(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Request validation failed",
          List.of(Map.of("field", "body", "message", "Unknown property")),
          null);
    return response(
        HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not valid JSON", null, null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return response(
        HttpStatus.CONFLICT,
        "CONSTRAINT_CONFLICT",
        "Request conflicts with existing data",
        null,
        null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unavailable(Exception e) {
    return response(
        HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Service unavailable", null, null);
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus s, String c, String d, List<Map<String, String>> v, String retry) {
    var p = ProblemDetail.forStatusAndDetail(s, d);
    p.setTitle(s.getReasonPhrase());
    p.setProperty("code", c);
    if (v != null) p.setProperty("violations", v);
    var b = ResponseEntity.status(s);
    if (retry != null) b.header("Retry-After", retry);
    return b.body(p);
  }
}
