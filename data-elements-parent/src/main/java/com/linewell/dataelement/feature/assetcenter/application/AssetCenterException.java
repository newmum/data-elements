package com.linewell.dataelement.feature.assetcenter.application;

/** Public, credential-free business error used by the asset aggregation boundary. */
public class AssetCenterException extends RuntimeException {
    private final int status;
    private final String code;
    public AssetCenterException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
