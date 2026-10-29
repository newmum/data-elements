package com.haitong.config;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="relay")
public class RelayProperties {
    private String workDir = "./data";
    private Security security = new Security();
    private Limits limits = new Limits();
    private Kafka kafka = new Kafka();
    private Ftp ftp = new Ftp();
    private Backend backend = new Backend();
    private Gateway gateway = new Gateway();
    private ZeroTrustGateway zeroTrustGateway = new ZeroTrustGateway();
    private RegistrationRegistry registrationRegistry = new RegistrationRegistry();
    private StandaloneRegistry standaloneRegistry = new StandaloneRegistry();
    private Standalone standalone = new Standalone();
    private Worker worker = new Worker();
    private Map<String, Target> targets = new LinkedHashMap<>();
    private Map<String, Route> routes = new LinkedHashMap<>();
    private Map<String, Action> actions = new LinkedHashMap<>();
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String value) { this.workDir = value; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security value) { this.security = value; }
    public Limits getLimits() { return limits; }
    public void setLimits(Limits value) { this.limits = value; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka value) { this.kafka = value; }
    public Ftp getFtp() { return ftp; }
    public void setFtp(Ftp value) { this.ftp = value; }
    public Backend getBackend() { return backend; }
    public void setBackend(Backend value) { this.backend = value; }
    public Gateway getGateway() { return gateway; }
    public void setGateway(Gateway value) { this.gateway = value; }
    public ZeroTrustGateway getZeroTrustGateway() { return zeroTrustGateway; }
    public void setZeroTrustGateway(ZeroTrustGateway value) { this.zeroTrustGateway = value; }
    public RegistrationRegistry getRegistrationRegistry() { return registrationRegistry; }
    public void setRegistrationRegistry(RegistrationRegistry value) { this.registrationRegistry = value; }
    public StandaloneRegistry getStandaloneRegistry() { return standaloneRegistry; }
    public void setStandaloneRegistry(StandaloneRegistry value) { this.standaloneRegistry = value; }
    public Standalone getStandalone() { return standalone; }
    public void setStandalone(Standalone value) { this.standalone = value; }
    public Worker getWorker() { return worker; }
    public void setWorker(Worker value) { this.worker = value; }
    public Map<String, Target> getTargets() { return targets; }
    public void setTargets(Map<String, Target> value) { this.targets = value; }
    public Map<String, Route> getRoutes() { return routes; }
    public void setRoutes(Map<String, Route> value) { this.routes = value; }
    public Map<String, Action> getActions() { return actions; }
    public void setActions(Map<String, Action> value) { this.actions = value; }
    public static class Security {
        private boolean pushValidationEnabled = true;
        private String apiKey = "";
        private String adminKey = "";
        private int requestsPerMinute = 600;
        private Map<String,Client> clients = new LinkedHashMap<>();
        public boolean isPushValidationEnabled() { return pushValidationEnabled; }
        public void setPushValidationEnabled(boolean value) { this.pushValidationEnabled = value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { this.apiKey = value; }
        public String getAdminKey() { return adminKey; }
        public void setAdminKey(String value) { this.adminKey = value; }
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int value) { this.requestsPerMinute = value; }
        public Map<String,Client> getClients() { return clients; }
        public void setClients(Map<String,Client> value) { this.clients = value; }
    }
    public static class Client {
        private String apiKey = "";
        private List<String> channels = new ArrayList<>();
        private List<String> actions = new ArrayList<>();
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { this.apiKey = value; }
        public List<String> getChannels() { return channels; }
        public void setChannels(List<String> value) { this.channels = value; }
        public List<String> getActions() { return actions; }
        public void setActions(List<String> value) { this.actions = value; }
    }
    public static class Limits {
        private int maxMessageBytes = 1048576;
        private long maxFileBytes = 104857600L;
        private long maxPendingDeliveries = 100000L;
        private long maxPendingFiles = 1000L;
        private long minFreeDiskBytes = 536870912L;
        private long maxWorkBytes = 10737418240L;
        public int getMaxMessageBytes() { return maxMessageBytes; }
        public void setMaxMessageBytes(int value) { this.maxMessageBytes = value; }
        public long getMaxFileBytes() { return maxFileBytes; }
        public void setMaxFileBytes(long value) { this.maxFileBytes = value; }
        public long getMaxPendingDeliveries() { return maxPendingDeliveries; }
        public void setMaxPendingDeliveries(long value) { this.maxPendingDeliveries = value; }
        public long getMaxPendingFiles() { return maxPendingFiles; }
        public void setMaxPendingFiles(long value) { this.maxPendingFiles = value; }
        public long getMinFreeDiskBytes() { return minFreeDiskBytes; }
        public void setMinFreeDiskBytes(long value) { this.minFreeDiskBytes = value; }
        public long getMaxWorkBytes() { return maxWorkBytes; }
        public void setMaxWorkBytes(long value) { this.maxWorkBytes = value; }
    }
    public static class Kafka {
        private boolean enabled = false;
        private String bootstrapServers = "localhost:9092";
        private String securityProtocol = "PLAINTEXT";
        private String saslMechanism = "PLAIN";
        private String username = "";
        private String password = "";
        private String truststoreLocation = "";
        private String truststorePassword = "";
        private String producerTopic = "relay-output";
        private int timeoutSeconds = 30;
        private Consumer consumer = new Consumer();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String value) { this.bootstrapServers = value; }
        public String getSecurityProtocol() { return securityProtocol; }
        public void setSecurityProtocol(String value) { this.securityProtocol = value; }
        public String getSaslMechanism() { return saslMechanism; }
        public void setSaslMechanism(String value) { this.saslMechanism = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { this.username = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { this.password = value; }
        public String getTruststoreLocation() { return truststoreLocation; }
        public void setTruststoreLocation(String value) { this.truststoreLocation = value; }
        public String getTruststorePassword() { return truststorePassword; }
        public void setTruststorePassword(String value) { this.truststorePassword = value; }
        public String getProducerTopic() { return producerTopic; }
        public void setProducerTopic(String value) { this.producerTopic = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
        public Consumer getConsumer() { return consumer; }
        public void setConsumer(Consumer value) { this.consumer = value; }
    }
    public static class Consumer {
        private boolean enabled = false;
        private String topic = "relay-input";
        private String groupId = "data-relay-v1";
        private String offsetReset = "none";
        private String sourceEpoch = "v1";
        private String channel = "from-kafka";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getTopic() { return topic; }
        public void setTopic(String value) { this.topic = value; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String value) { this.groupId = value; }
        public String getOffsetReset() { return offsetReset; }
        public void setOffsetReset(String value) { this.offsetReset = value; }
        public String getSourceEpoch() { return sourceEpoch; }
        public void setSourceEpoch(String value) { this.sourceEpoch = value; }
        public String getChannel() { return channel; }
        public void setChannel(String value) { this.channel = value; }
    }
    public static class Ftp {
        private boolean enabled = false;
        private String protocol = "FTP";
        private String host = "localhost";
        private int port = 21;
        private String username = "relay";
        private String password = "";
        private boolean passive = true;
        private boolean implicitTls = false;
        private String encoding = "UTF-8";
        private int timeoutSeconds = 30;
        /** Optional SHA256:... fingerprint.  When blank, the first registered SFTP host key is pinned locally (TOFU). */
        private String sftpHostKeyFingerprint = "";
        private String outputDir = "/out";
        private Scan scan = new Scan();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String value) { this.protocol = value; }
        public String getHost() { return host; }
        public void setHost(String value) { this.host = value; }
        public int getPort() { return port; }
        public void setPort(int value) { this.port = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { this.username = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { this.password = value; }
        public boolean isPassive() { return passive; }
        public void setPassive(boolean value) { this.passive = value; }
        public boolean isImplicitTls() { return implicitTls; }
        public void setImplicitTls(boolean value) { this.implicitTls = value; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String value) { this.encoding = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
        public String getSftpHostKeyFingerprint() { return sftpHostKeyFingerprint; }
        public void setSftpHostKeyFingerprint(String value) { this.sftpHostKeyFingerprint = value; }
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String value) { this.outputDir = value; }
        public Scan getScan() { return scan; }
        public void setScan(Scan value) { this.scan = value; }
    }
    public static class Scan {
        private boolean enabled = false;
        private String inputDir = "/in";
        private String channel = "from-ftp";
        private long intervalMs = 30000L;
        private int maxFilesPerScan = 100;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getInputDir() { return inputDir; }
        public void setInputDir(String value) { this.inputDir = value; }
        public String getChannel() { return channel; }
        public void setChannel(String value) { this.channel = value; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long value) { this.intervalMs = value; }
        public int getMaxFilesPerScan() { return maxFilesPerScan; }
        public void setMaxFilesPerScan(int value) { this.maxFilesPerScan = value; }
    }
    public static class Backend {
        private String bearerToken = "";
        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String value) { this.bearerToken = value; }
    }
    public static class Gateway {
        private String publicBaseUrl = "";
        private String sharedKey = "";
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String value) { this.publicBaseUrl = value; }
        public String getSharedKey() { return sharedKey; }
        public void setSharedKey(String value) { this.sharedKey = value; }
    }
    /** Runtime-only configuration used by the Magic zero-trust gateway APIs. */
    public static class ZeroTrustGateway {
        private boolean enabled = false;
        private String tokenUrl = "";
        private String pushUrl = "";
        private String clientId = "";
        private String clientSecret = "";
        private String requesterIp = "";
        private int timeoutSeconds = 30;
        private Map<String,String> auditDefaults = new LinkedHashMap<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String value) { this.tokenUrl = value; }
        public String getPushUrl() { return pushUrl; }
        public void setPushUrl(String value) { this.pushUrl = value; }
        public String getClientId() { return clientId; }
        public void setClientId(String value) { this.clientId = value; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String value) { this.clientSecret = value; }
        public String getRequesterIp() { return requesterIp; }
        public void setRequesterIp(String value) { this.requesterIp = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
        public Map<String,String> getAuditDefaults() { return auditDefaults; }
        public void setAuditDefaults(Map<String,String> value) { this.auditDefaults = value == null ? new LinkedHashMap<>() : value; }
    }
    /**
     * The data platform remains the owner of datasource/table/field metadata.
     * This is only the protected service-to-service lookup used before a batch
     * is accepted by a registration-managed route.
     */
    public static class RegistrationRegistry {
        private boolean enabled = false;
        private String baseUrl = "";
        private String sharedKey = "";
        private int timeoutSeconds = 8;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { this.baseUrl = value; }
        public String getSharedKey() { return sharedKey; }
        public void setSharedKey(String value) { this.sharedKey = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
    }
    /**
     * Protects platform-to-Haitong synchronization of standalone datasource
     * contracts.  This is intentionally separate from business X-Api-Key and
     * from registration-managed lookup credentials.
     */
    public static class StandaloneRegistry {
        private boolean syncEnabled = false;
        private String syncKey = "";
        public boolean isSyncEnabled() { return syncEnabled; }
        public void setSyncEnabled(boolean value) { this.syncEnabled = value; }
        public String getSyncKey() { return syncKey; }
        public void setSyncKey(String value) { this.syncKey = value; }
    }
    /**
     * Local, version-controlled contracts for a deployment that intentionally
     * runs without the data-elements registration service.  A standalone
     * contract never contains destination credentials; routes and targets
     * remain separately configured and are still authenticated by clients.
     */
    public static class Standalone {
        private Map<String, StandaloneDatasource> datasources = new LinkedHashMap<>();
        public Map<String, StandaloneDatasource> getDatasources() { return datasources; }
        public void setDatasources(Map<String, StandaloneDatasource> value) { this.datasources = value; }
    }
    public static class StandaloneDatasource {
        /** Empty means every caller already permitted for the route may use this source. */
        private List<String> clients = new ArrayList<>();
        private Map<String, StandaloneTable> tables = new LinkedHashMap<>();
        public List<String> getClients() { return clients; }
        public void setClients(List<String> value) { this.clients = value; }
        public Map<String, StandaloneTable> getTables() { return tables; }
        public void setTables(Map<String, StandaloneTable> value) { this.tables = value; }
    }
    public static class StandaloneTable {
        private String schemaVersion = "";
        private List<String> requiredFields = new ArrayList<>();
        /** Empty means the standalone owner intentionally accepts extension fields. */
        private List<String> allowedFields = new ArrayList<>();
        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String value) { this.schemaVersion = value; }
        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> value) { this.requiredFields = value; }
        public List<String> getAllowedFields() { return allowedFields; }
        public void setAllowedFields(List<String> value) { this.allowedFields = value; }
    }
    public static class Worker {
        private boolean enabled = true;
        private int threadsPerType = 2;
        private int maxAttempts = 10;
        private long pollMs = 500L;
        private long leaseSeconds = 300L;
        private int fileBatchSize = 100;
        private int payloadRetentionDays = 0;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public int getThreadsPerType() { return threadsPerType; }
        public void setThreadsPerType(int value) { this.threadsPerType = value; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) { this.maxAttempts = value; }
        public long getPollMs() { return pollMs; }
        public void setPollMs(long value) { this.pollMs = value; }
        public long getLeaseSeconds() { return leaseSeconds; }
        public void setLeaseSeconds(long value) { this.leaseSeconds = value; }
        public int getFileBatchSize() { return fileBatchSize; }
        public void setFileBatchSize(int value) { this.fileBatchSize = value; }
        public int getPayloadRetentionDays() { return payloadRetentionDays; }
        public void setPayloadRetentionDays(int value) { this.payloadRetentionDays = value; }
    }
    public static class Target {
        private String type = "LOCAL";
        private String topic = "";
        private String directory = "";
        public String getType() { return type; }
        public void setType(String value) { this.type = value; }
        public String getTopic() { return topic; }
        public void setTopic(String value) { this.topic = value; }
        public String getDirectory() { return directory; }
        public void setDirectory(String value) { this.directory = value; }
    }
    public static class Route {
        private boolean enabled = true;
        private boolean registrationManaged = false;
        private List<String> targets = new ArrayList<>();
        private String fileFormat = "JSONL";
        private List<String> requiredFields = new ArrayList<>();
        private Map<String,String> renameFields = new LinkedHashMap<>();
        private String businessKeyField="",batchKeyField="",businessTimeField="";
        public String getBusinessKeyField(){return businessKeyField;} public void setBusinessKeyField(String v){businessKeyField=v;}
        public String getBatchKeyField(){return batchKeyField;} public void setBatchKeyField(String v){batchKeyField=v;}
        public String getBusinessTimeField(){return businessTimeField;} public void setBusinessTimeField(String v){businessTimeField=v;}
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public boolean isRegistrationManaged() { return registrationManaged; }
        public void setRegistrationManaged(boolean value) { this.registrationManaged = value; }
        public List<String> getTargets() { return targets; }
        public void setTargets(List<String> value) { this.targets = value; }
        public String getFileFormat() { return fileFormat; }
        public void setFileFormat(String value) { this.fileFormat = value; }
        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> value) { this.requiredFields = value; }
        public Map<String,String> getRenameFields() { return renameFields; }
        public void setRenameFields(Map<String,String> value) { this.renameFields = value; }
    }
    public static class Action {
        private boolean enabled = true;
        private String mode = "ASYNC";
        private String handler = "http";
        private String url = "";
        private String method = "POST";
        private boolean safeRetry = false;
        private int timeoutSeconds = 30;
        private List<String> requiredFields = new ArrayList<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getMode() { return mode; }
        public void setMode(String value) { this.mode = value; }
        public String getHandler() { return handler; }
        public void setHandler(String value) { this.handler = value; }
        public String getUrl() { return url; }
        public void setUrl(String value) { this.url = value; }
        public String getMethod() { return method; }
        public void setMethod(String value) { this.method = value; }
        public boolean isSafeRetry() { return safeRetry; }
        public void setSafeRetry(boolean value) { this.safeRetry = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> value) { this.requiredFields = value; }
    }
}
