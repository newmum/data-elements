package com.linewell.dataelement.integration.nifi.canvas.nifi;

public class NifiException extends RuntimeException {
    private final int status;
    private final String body;

    public NifiException(String message, int status, String body) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public NifiException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
        this.body = null;
    }

    public int status() { return status; }
    public String body() { return body; }
}
