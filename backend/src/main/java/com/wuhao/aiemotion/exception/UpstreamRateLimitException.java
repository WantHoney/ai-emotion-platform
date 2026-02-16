package com.wuhao.aiemotion.exception;

public class UpstreamRateLimitException extends RuntimeException {

    private final int upstreamStatus;
    private final String upstreamBody;
    private final int retryAfterSeconds;

    public UpstreamRateLimitException(String message, Throwable cause, int upstreamStatus, String upstreamBody, int retryAfterSeconds) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.upstreamBody = upstreamBody;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamBody() {
        return upstreamBody;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
