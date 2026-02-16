package com.wuhao.aiemotion.integration.ser;

public class SerClientException extends RuntimeException {
    private final String category;

    public SerClientException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public SerClientException(String category, String message) {
        super(message);
        this.category = category;
    }

    public String getCategory() {
        return category;
    }
}
