package com.wuhao.aiemotion.exception;

import com.wuhao.aiemotion.config.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapUpstreamRateLimitTo429WithUpstreamDetails() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/audio/analysis/run");
        req.setAttribute(TraceIdFilter.TRACE_ATTR, "t-1");

        UpstreamRateLimitException exception = new UpstreamRateLimitException(
                "rate limited",
                null,
                429,
                "{\"error\":\"rate_limit\"}",
                15
        );

        ResponseEntity<ApiError> response = handler.handleUpstreamRateLimit(exception, req);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("15", response.getHeaders().getFirst("Retry-After"));
        assertEquals("UPSTREAM_RATE_LIMIT", response.getBody().code());
        assertEquals("t-1", response.getBody().traceId());
        assertNotNull(response.getBody().details());
        assertEquals("Upstream provider is rate-limited, retry later.", response.getBody().message());
    }

    @Test
    void shouldIncludeUpstreamFieldsForNon2xxProviderError() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/audio/analysis/run");
        req.setAttribute(TraceIdFilter.TRACE_ATTR, "t-2");

        UpstreamServiceException exception = new UpstreamServiceException(
                "upstream failed",
                null,
                402,
                "{\"error\":\"payment_required\"}"
        );

        ResponseEntity<ApiError> response = handler.handleUpstreamServiceError(exception, req);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("UPSTREAM_SERVICE_ERROR", response.getBody().code());
        assertEquals("t-2", response.getBody().traceId());
        assertNotNull(response.getBody().details());
    }
}
