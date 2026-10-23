package com.haitong.files;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-owned contracts. No request is allowed to choose a tenant or destination. */
@Component
@ConfigurationProperties("relay.files")
public class FileTransferProperties {
    private boolean enabled;
    private long singleUploadMaxBytes=20L*1024*1024;
    private long partSizeBytes=16L*1024*1024, maxFileBytes=10L*1024*1024*1024,
            storageLimitBytes=100L*1024*1024*1024;
    private int maxActiveUploads=32, maxStreams=16, verificationThreads=2, sessionHours=24, leaseSeconds=120;
    private List<String> scannerCommand=new ArrayList<>();
    private int scannerTimeoutSeconds=300;
    private Map<String,CollectionContract> collections=new LinkedHashMap<>();
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public long getSingleUploadMaxBytes(){return singleUploadMaxBytes;} public void setSingleUploadMaxBytes(long v){singleUploadMaxBytes=v;}
    public long getPartSizeBytes(){return partSizeBytes;} public void setPartSizeBytes(long v){partSizeBytes=v;}
    public long getMaxFileBytes(){return maxFileBytes;} public void setMaxFileBytes(long v){maxFileBytes=v;}
    public long getStorageLimitBytes(){return storageLimitBytes;} public void setStorageLimitBytes(long v){storageLimitBytes=v;}
    public int getMaxActiveUploads(){return maxActiveUploads;} public void setMaxActiveUploads(int v){maxActiveUploads=v;}
    public int getMaxStreams(){return maxStreams;} public void setMaxStreams(int v){maxStreams=v;}
    public int getVerificationThreads(){return verificationThreads;} public void setVerificationThreads(int v){verificationThreads=v;}
    public int getSessionHours(){return sessionHours;} public void setSessionHours(int v){sessionHours=v;}
    public int getLeaseSeconds(){return leaseSeconds;} public void setLeaseSeconds(int v){leaseSeconds=v;}
    public List<String> getScannerCommand(){return scannerCommand;} public void setScannerCommand(List<String> v){scannerCommand=v;}
    public int getScannerTimeoutSeconds(){return scannerTimeoutSeconds;} public void setScannerTimeoutSeconds(int v){scannerTimeoutSeconds=v;}
    public Map<String,CollectionContract> getCollections(){return collections;} public void setCollections(Map<String,CollectionContract> v){collections=v;}

    public static class CollectionContract {
        private String tenantId="",datasourceId="",collectionId="",tableName="",fieldName="";
        private long version=1, maxFileBytes=10L*1024*1024*1024,quotaBytes=50L*1024*1024*1024;
        private int maxActiveUploads=8;
        private boolean enabled=true,requireMalwareScan=true;
        private List<String> clients=new ArrayList<>(),readClients=new ArrayList<>(),targets=new ArrayList<>(),
                allowedExtensions=new ArrayList<>(List.of("jpg","jpeg","png","pdf","docx")),requiredMetadata=new ArrayList<>();
        private Map<String,String> metadataTypes=new LinkedHashMap<>(),receiptClients=new LinkedHashMap<>();
        public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
        public String getDatasourceId(){return datasourceId;} public void setDatasourceId(String v){datasourceId=v;}
        public String getCollectionId(){return collectionId;} public void setCollectionId(String v){collectionId=v;}
        public String getTableName(){return tableName;} public void setTableName(String v){tableName=v;}
        public String getFieldName(){return fieldName;} public void setFieldName(String v){fieldName=v;}
        public long getVersion(){return version;} public void setVersion(long v){version=v;}
        public long getMaxFileBytes(){return maxFileBytes;} public void setMaxFileBytes(long v){maxFileBytes=v;}
        public long getQuotaBytes(){return quotaBytes;} public void setQuotaBytes(long v){quotaBytes=v;}
        public int getMaxActiveUploads(){return maxActiveUploads;} public void setMaxActiveUploads(int v){maxActiveUploads=v;}
        public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
        public boolean isRequireMalwareScan(){return requireMalwareScan;} public void setRequireMalwareScan(boolean v){requireMalwareScan=v;}
        public List<String> getClients(){return clients;} public void setClients(List<String> v){clients=v;}
        public List<String> getReadClients(){return readClients;} public void setReadClients(List<String> v){readClients=v;}
        public List<String> getTargets(){return targets;} public void setTargets(List<String> v){targets=v;}
        public List<String> getAllowedExtensions(){return allowedExtensions;} public void setAllowedExtensions(List<String> v){allowedExtensions=v;}
        public List<String> getRequiredMetadata(){return requiredMetadata;} public void setRequiredMetadata(List<String> v){requiredMetadata=v;}
        public Map<String,String> getMetadataTypes(){return metadataTypes;} public void setMetadataTypes(Map<String,String> v){metadataTypes=v;}
        public Map<String,String> getReceiptClients(){return receiptClients;} public void setReceiptClients(Map<String,String> v){receiptClients=v;}
    }
}
