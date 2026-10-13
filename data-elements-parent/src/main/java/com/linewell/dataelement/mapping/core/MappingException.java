package com.linewell.dataelement.mapping.core;

/** Stable, value-free diagnostics shared by preview and NiFi. */
public final class MappingException extends RuntimeException {
    private final String code;
    public MappingException(String code, String message) { super(message); this.code = code; }
    public MappingException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
