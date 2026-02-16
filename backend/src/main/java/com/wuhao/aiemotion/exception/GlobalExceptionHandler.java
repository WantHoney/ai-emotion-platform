package com.wuhao.aiemotion.exception;

import com.wuhao.aiemotion.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException e, HttpServletRequest req) {
        return error(e.getStatusCode().value(), "HTTP_" + e.getStatusCode().value(), e.getReason(), req, null, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        return error(400, "BAD_REQUEST", "Validation failed", req,
                Map.of("errors", e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ":" + f.getDefaultMessage()).toList()), e);
    }

    @ExceptionHandler(SerServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleSerUnavailable(SerServiceUnavailableException e, HttpServletRequest req) {
        return error(503, "SER_UNAVAILABLE", "SER service unavailable", req, null, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
        return error(400, "BAD_REQUEST", e.getMessage(), req, null, e);
    }

    @ExceptionHandler(UpstreamRateLimitException.class)
    public ResponseEntity<ApiError> handleUpstreamRateLimit(UpstreamRateLimitException e, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        ResponseEntity<ApiError> response = error(429, "UPSTREAM_RATE_LIMIT", "Upstream provider is rate-limited, retry later.", req,
                Map.of("upstreamStatus", e.getUpstreamStatus(), "upstreamBody", e.getUpstreamBody()), e);
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiError> handleUpstreamServiceError(UpstreamServiceException e, HttpServletRequest req) {
        return error(500, "UPSTREAM_SERVICE_ERROR", "Upstream provider error.", req,
                Map.of("upstreamStatus", e.getUpstreamStatus(), "upstreamBody", e.getUpstreamBody()), e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleServerError(Exception e, HttpServletRequest req) {
        return error(500, "INTERNAL_ERROR", e.getMessage(), req, null, e);
    }

    private ResponseEntity<ApiError> error(int status, String code, String message, HttpServletRequest req, Map<String, Object> details, Exception e) {
        String traceId = (String) req.getAttribute(TraceIdFilter.TRACE_ATTR);
        log.error("request failed: path={}, traceId={}, code={}", req.getRequestURI(), traceId, code, e);
        ApiError body = new ApiError(code, message, traceId, Instant.now(), req.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }
}
