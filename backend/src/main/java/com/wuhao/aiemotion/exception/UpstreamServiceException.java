package com.wuhao.aiemotion.exception;

public class UpstreamServiceException extends RuntimeException {

    private final int upstreamStatus;
    private final String upstreamBody;

    public UpstreamServiceException(String message, Throwable cause, int upstreamStatus, String upstreamBody) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.upstreamBody = upstreamBody;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamBody() {
        return upstreamBody;
    }
}
