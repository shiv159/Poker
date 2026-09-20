package com.pokernight.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
    String message = ex.getMessage() == null ? "INVALID_REQUEST" : ex.getMessage();
    HttpStatus status = "TABLE_NOT_FOUND".equals(message) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return response(status, message);
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
    return response(HttpStatus.CONFLICT, ex.getMessage() == null ? "REQUEST_REJECTED" : ex.getMessage());
  }

  private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(), "status", status.value(), "code", code));
  }
}
