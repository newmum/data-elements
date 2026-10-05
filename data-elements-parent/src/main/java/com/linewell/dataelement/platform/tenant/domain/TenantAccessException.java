package com.linewell.dataelement.platform.tenant.domain;

public class TenantAccessException extends RuntimeException {

    private final String code;

    public TenantAccessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
