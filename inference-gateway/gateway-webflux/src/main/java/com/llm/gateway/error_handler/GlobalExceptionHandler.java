package com.llm.gateway.error_handler;

import com.llm.gateway.models.ErrorDetails;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({ResponseStatusException.class})
    public ResponseEntity<ErrorDetails> handleException(ResponseStatusException ex, ServerHttpRequest request) {
        log.error("GlobalExceptionHandler: handle exception", ex);
        val statusCode = ex.getStatusCode();
        val error = ex.getStatusCode().toString().substring(4);
        final var errorDetails = new ErrorDetails(ex.getReason(), statusCode, Instant.now().getEpochSecond(), error, request.getPath().contextPath().toString());
        return ResponseEntity.status(statusCode).body(errorDetails);
    }
}
