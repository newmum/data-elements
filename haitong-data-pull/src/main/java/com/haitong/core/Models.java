package com.haitong.core;

import java.util.*;

/** Records written to database snapshots deliberately contain NO passwords or bearer tokens. */
public final class Models {
    private Models() {}
    public enum Kind { KAFKA, FTP, PLATFORM_FTP, LOCAL, LOG, ACTION, FILE_LOCAL, FILE_FTP, FILE_KAFKA }
    public record KafkaAddress(String servers,String securityProtocol,String mechanism,String username,String truststore,int timeoutSeconds) {}
    public record FtpAddress(String protocol,String host,int port,String username,boolean passive,boolean implicitTls,String encoding,int timeoutSeconds) {}
    public record ActionPlan(String code,String handler,String url,String method,boolean safeRetry,int timeoutSeconds,List<String> requiredFields) {}
    public record TargetPlan(String code,Kind kind,String topic,String directory,KafkaAddress kafka,FtpAddress ftp,ActionPlan action) {
        public boolean safe() { return kind!=Kind.ACTION || action.safeRetry(); }
    }
    public record RoutePlan(String code,String fileFormat,List<String> requiredFields,Map<String,String> renameFields,List<TargetPlan> targets,
                            String businessKeyField,String batchKeyField,String businessTimeField) {
        public RoutePlan(String code,String fileFormat,List<String> requiredFields,Map<String,String> renameFields,List<TargetPlan> targets) {
            this(code,fileFormat,requiredFields,renameFields,targets,"","","");
        }
    }
    public record Accepted(String id,boolean duplicate) {}
    public record Message(String id,String owner,String sourceType,String sourceScope,String sourceKey,String routeCode,String sha256,
                          String payload,String outputPayload,String fileRef,String originalName,String fileId,Long recordNo,
                          String status,String errorCode,String snapshot,String sourceMetadata,long createdAt) {}
    public record Delivery(String id,String messageId,String targetCode,Kind kind,String plan,String status,int attempts,int maxAttempts,
                           boolean safe,String leaseToken,long leaseUntil,String result,String errorCode,String errorMessage,long nextAttemptAt) {}
    public record FileRow(String id,String owner,String sourceType,String sourceScope,String sourceKey,String routeCode,String name,
                          String path,String sha256,long size,String format,String snapshot,String status,long lastRecordNo,
                          long totalRecords,boolean eof,String leaseToken,long leaseUntil,int attempts,String errorCode,long createdAt) {}
    public record Packet(String messageId,String deliveryId,String routeCode,long createdAt,byte[] bytes,String fileRef,String originalName,Map<String,Object> metadata) {
        public boolean isFile() { return fileRef!=null && !fileRef.isBlank(); }
    }
    public static final class DeliveryFailure extends Exception {
        public final String code;
        public final boolean permanent;
        public final boolean unknown;
        /**
         * Safe operational facts only.  It must never contain request bodies,
         * passwords, API keys, database credentials, or authorization headers.
         */
        public final Map<String,Object> diagnostics;
        public DeliveryFailure(String code,boolean permanent,boolean unknown) { this(code,permanent,unknown,null,Map.of()); }
        public DeliveryFailure(String code,boolean permanent,boolean unknown,Throwable cause) { this(code,permanent,unknown,cause,Map.of()); }
        public DeliveryFailure(String code,boolean permanent,boolean unknown,Throwable cause,Map<String,Object> diagnostics) {
            super(code,cause); this.code=code; this.permanent=permanent; this.unknown=unknown;
            this.diagnostics=diagnostics==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
        }
    }
}
