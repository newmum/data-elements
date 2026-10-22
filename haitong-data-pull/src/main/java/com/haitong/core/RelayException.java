package com.haitong.core;

/** Public messages are intentionally generic: never expose credentials, payloads or upstream URLs. */
public class RelayException extends RuntimeException {
    private final int status;
    private final String code;
    public RelayException(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static RelayException invalid(String message) { return new RelayException(422,"INVALID_BODY",message); }
    public static RelayException conflict() { return new RelayException(409,"IDEMPOTENCY_CONFLICT","相同幂等键已对应不同内容"); }
    public static RelayException notFound() { return new RelayException(404,"RESOURCE_NOT_FOUND","资源不存在或无权访问"); }
    public static RelayException capacity() { return new RelayException(503,"CAPACITY_EXHAUSTED","接收容量不足，请使用原幂等键稍后重试"); }
}
