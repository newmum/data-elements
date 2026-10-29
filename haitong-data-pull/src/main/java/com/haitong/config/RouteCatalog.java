package com.haitong.config;

import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

@Component
public final class RouteCatalog implements InitializingBean {
    private final RelayProperties p;
    private final Secrets secrets;
    public RouteCatalog(RelayProperties p,Secrets secrets) { this.p=p;this.secrets=secrets; }
    public boolean isPushValidationEnabled() { return p.getSecurity().isPushValidationEnabled(); }
    public RoutePlan route(String code) {
        RelayProperties.Route r=p.getRoutes().get(code);
        if(r==null || !r.isEnabled()) throw RelayException.notFound();
        List<TargetPlan> targets=r.getTargets().stream().map(this::target).toList();
        return new RoutePlan(code,r.getFileFormat(),List.copyOf(r.getRequiredFields()),Map.copyOf(r.getRenameFields()),targets,r.getBusinessKeyField(),r.getBatchKeyField(),r.getBusinessTimeField());
    }
    public TargetPlan target(String code) {
        var t=p.getTargets().get(code);
        if(t==null) throw new IllegalStateException("未配置目标："+code);
        Kind kind=Kind.valueOf(t.getType().toUpperCase(Locale.ROOT));
        if(!Set.of(Kind.KAFKA,Kind.FTP,Kind.LOCAL,Kind.LOG).contains(kind)) throw new IllegalStateException("通用目标仅允许本地配置的 KAFKA、FTP、LOCAL、LOG");
        var k=p.getKafka();var f=p.getFtp();
        if(kind==Kind.KAFKA && !k.isEnabled()) throw new IllegalStateException("路由使用 Kafka，但 relay.kafka.enabled=false");
        if(kind==Kind.FTP && !f.isEnabled()) throw new IllegalStateException("路由使用 FTP，但 relay.ftp.enabled=false");
        String directory=t.getDirectory();
        if(kind==Kind.LOCAL) { if(directory.isBlank()) directory="out"; Rules.within(Path.of(p.getWorkDir()),directory); }
        if(kind==Kind.FTP) directory=Rules.remoteDirectory(directory.isBlank()?f.getOutputDir():directory);
        String topic=t.getTopic().isBlank()?k.getProducerTopic():t.getTopic();
        if(kind==Kind.KAFKA && !topic.matches("[a-zA-Z0-9._-]{1,249}")) throw new IllegalStateException("Kafka Topic 不合法");
        return new TargetPlan(code,kind,topic,directory,kind==Kind.KAFKA?kafka():null,kind==Kind.FTP?ftp():null,null);
    }
    public KafkaAddress kafka() {
        var k=p.getKafka();return new KafkaAddress(k.getBootstrapServers(),k.getSecurityProtocol(),k.getSaslMechanism(),k.getUsername(),k.getTruststoreLocation(),k.getTimeoutSeconds());
    }
    public FtpAddress ftp() {
        var f=p.getFtp();return new FtpAddress(f.getProtocol(),f.getHost(),f.getPort(),f.getUsername(),f.isPassive(),f.isImplicitTls(),f.getEncoding(),f.getTimeoutSeconds());
    }
    public RelayProperties.Action actionConfig(String code) {
        var action=p.getActions().get(code);if(action==null || !action.isEnabled()) throw RelayException.notFound();return action;
    }
    public ActionPlan action(String code) {
        var a=actionConfig(code);return new ActionPlan(code,a.getHandler(),a.getUrl(),a.getMethod(),a.isSafeRetry(),a.getTimeoutSeconds(),List.copyOf(a.getRequiredFields()));
    }
    @Override public void afterPropertiesSet() throws Exception {
        if(p.getLimits().getMaxMessageBytes()<1024 || p.getLimits().getMaxMessageBytes()>16*1024*1024 ||
           p.getLimits().getMaxFileBytes()<1 || p.getLimits().getMaxFileBytes()>1024L*1024*1024 ||
           p.getLimits().getMaxPendingDeliveries()<1 || p.getLimits().getMaxPendingFiles()<1 ||
           p.getLimits().getMinFreeDiskBytes()<0 || p.getLimits().getMaxWorkBytes()<p.getLimits().getMaxFileBytes())
            throw new IllegalStateException("容量配置不合法");
        var w=p.getWorker();
        if(w.getThreadsPerType()<1 || w.getThreadsPerType()>16 || w.getMaxAttempts()<1 || w.getLeaseSeconds()<60 ||
           w.getPollMs()<100 || w.getFileBatchSize()<1 || w.getFileBatchSize()>1000 || w.getPayloadRetentionDays()<0)
            throw new IllegalStateException("执行器配置不合法");
        if(p.getSecurity().getRequestsPerMinute()<1) throw new IllegalStateException("请求速率必须为正数");
        Path root=Path.of(p.getWorkDir()).toAbsolutePath().normalize();Files.createDirectories(root.resolve("spool"));
        for(var item:p.getRoutes().entrySet()) {
            Rules.code(item.getKey());if(!item.getValue().isEnabled()) continue;
            RoutePlan r=route(item.getKey());
            if(r.targets().isEmpty() || new HashSet<>(item.getValue().getTargets()).size()!=r.targets().size())
                throw new IllegalStateException("每条路由必须有至少一个目标，且不能重复");
            if(item.getValue().isRegistrationManaged())
                throw new IllegalStateException("海通仅支持本地投递，请设置 registration-managed=false 并配置 targets");
            if(!Set.of("JSONL","CSV","BINARY").contains(r.fileFormat())) throw new IllegalStateException("file-format 必须为 JSONL、CSV 或 BINARY");
            if(r.fileFormat().equals("BINARY") && r.targets().stream().anyMatch(t->t.kind()!=Kind.FTP && t.kind()!=Kind.LOCAL))
                throw new IllegalStateException("BINARY 整文件仅允许传往 FTP 或 LOCAL");
            for(var rename:r.renameFields().entrySet()) if(rename.getKey().isBlank() || rename.getValue().isBlank()) throw new IllegalStateException("字段映射名称不能为空");
        }
        if(p.getSecurity().isPushValidationEnabled()) for(var source:p.getStandalone().getDatasources().entrySet()) {
            Rules.code(source.getKey());
            var contract=source.getValue();
            if(contract==null || contract.getTables()==null || contract.getTables().isEmpty())
                throw new IllegalStateException("独立数据源必须至少配置一张表："+source.getKey());
            for(String client:contract.getClients()==null?List.<String>of():contract.getClients()) Rules.code(client);
            for(var table:contract.getTables().entrySet()) {
                Rules.code(table.getKey());
                if(table.getValue()==null) throw new IllegalStateException("独立数据表契约不能为空："+table.getKey());
            }
        }
        for(String name:p.getTargets().keySet()) Rules.code(name);
        var k=p.getKafka();var f=p.getFtp();
        if(k.isEnabled()) {
            if(k.getBootstrapServers().isBlank() || k.getTimeoutSeconds()<1 || k.getTimeoutSeconds()>120) throw new IllegalStateException("Kafka 连接配置不合法");
            if(!Set.of("PLAINTEXT","SSL","SASL_PLAINTEXT","SASL_SSL").contains(k.getSecurityProtocol())) throw new IllegalStateException("不支持的 Kafka security-protocol");
            if(k.getSecurityProtocol().startsWith("SASL") && (!Set.of("PLAIN","SCRAM-SHA-256","SCRAM-SHA-512").contains(k.getSaslMechanism()) ||
                k.getUsername().isBlank() || secrets.resolve(k.getPassword()).isBlank())) throw new IllegalStateException("SASL 必须配置机制、用户名和密码");
            if(!k.getTruststorePassword().isBlank()) secrets.resolve(k.getTruststorePassword());
        }
        if(k.getConsumer().isEnabled()) {
            if(!k.isEnabled()) throw new IllegalStateException("Kafka 消费开启前须先 enabled=true");
            var c=k.getConsumer();RoutePlan r=route(c.getChannel());
            if(!Set.of("none","earliest","latest").contains(c.getOffsetReset()) || c.getGroupId().isBlank() || c.getSourceEpoch().isBlank() || c.getSourceEpoch().length()>80)
                throw new IllegalStateException("Kafka 消费组、来源代次或 offset-reset 不合法");
            if(r.targets().stream().anyMatch(t->t.kind()==Kind.KAFKA && t.topic().equals(c.getTopic())))
                throw new IllegalStateException("禁止从同一 Kafka Topic 消费后再写回，避免形成循环");
        }
        if(f.isEnabled()) {
            if(!Set.of("FTP","FTPS","SFTP").contains(f.getProtocol()) || f.getHost().isBlank() || f.getPort()<1 || f.getPort()>65535 || f.getTimeoutSeconds()<1 || f.getTimeoutSeconds()>120)
                throw new IllegalStateException("文件投递连接配置不合法；协议只支持 FTP、FTPS 或 SFTP");
            if(f.getUsername().isBlank()) throw new IllegalStateException("FTP 用户名不能为空");
            Charset.forName(f.getEncoding());secrets.resolve(f.getPassword());Rules.remoteDirectory(f.getOutputDir());
        }
        if(f.getScan().isEnabled()) {
            if(!f.isEnabled() || f.getScan().getIntervalMs()<1000 || f.getScan().getMaxFilesPerScan()<1 || f.getScan().getMaxFilesPerScan()>1000) throw new IllegalStateException("FTP 扫描配置不合法");
            route(f.getScan().getChannel());String input=Rules.remoteDirectory(f.getScan().getInputDir());
            for(var r:p.getRoutes().entrySet()) if(r.getValue().isEnabled()) for(var target:route(r.getKey()).targets())
                if(target.kind()==Kind.FTP && Rules.overlaps(input,target.directory())) throw new IllegalStateException("FTP 输入目录不得与任何输出目录重叠");
        }
        for(var entry:p.getActions().entrySet()) {
            Rules.code(entry.getKey());var a=entry.getValue();if(!a.isEnabled()) continue;
            if(!Set.of("SYNC","ASYNC").contains(a.getMode()) || a.getTimeoutSeconds()<1 || a.getTimeoutSeconds()>120 ||
               !Set.of("POST","PUT","PATCH").contains(a.getMethod())) throw new IllegalStateException("动作模式、方法或超时不合法");
            if(a.getMode().equals("SYNC") && !a.isSafeRetry()) throw new IllegalStateException("同步动作只用于可安全重试的查询或校验；有副作用的动作请用 ASYNC");
            if(a.getHandler().equals("http")) {
                URI uri=URI.create(a.getUrl());
                if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                    throw new IllegalStateException("动作必须配置固定的 HTTP(S) URL，不能内嵌凭证或 fragment");
                if(!p.getBackend().getBearerToken().isBlank()) secrets.resolve(p.getBackend().getBearerToken());
            } else if(!a.getHandler().equals("inspect")) throw new IllegalStateException("未知动作处理器；请登记 Java 实现后再启用");
        }
        if(!p.getGateway().getPublicBaseUrl().isBlank()) {
            URI uri=URI.create(p.getGateway().getPublicBaseUrl());
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalStateException("public-base-url 必须是 HTTP(S) 基础地址");
        }
        if(!p.getGateway().getSharedKey().isBlank() && secrets.resolve(p.getGateway().getSharedKey()).length()<24) throw new IllegalStateException("网关附加密钥长度至少 24");
        var zeroTrust=p.getZeroTrustGateway();
        if(zeroTrust.isEnabled()) {
            URI tokenUri=URI.create(zeroTrust.getTokenUrl()); URI pushUri=URI.create(zeroTrust.getPushUrl());
            if(!Set.of("http","https").contains(tokenUri.getScheme()) || tokenUri.getHost()==null || tokenUri.getUserInfo()!=null || tokenUri.getFragment()!=null ||
               !Set.of("http","https").contains(pushUri.getScheme()) || pushUri.getHost()==null || pushUri.getUserInfo()!=null || pushUri.getFragment()!=null ||
               zeroTrust.getClientId().isBlank() || zeroTrust.getTimeoutSeconds()<1 || zeroTrust.getTimeoutSeconds()>120)
                throw new IllegalStateException("零信任网关连接配置不合法");
            if(secrets.resolve(zeroTrust.getClientSecret()).length()<24) throw new IllegalStateException("零信任网关客户端密钥长度至少 24");
        }
        var standaloneRegistry=p.getStandaloneRegistry();
        if(standaloneRegistry.isSyncEnabled() && secrets.resolve(standaloneRegistry.getSyncKey()).length()<24)
            throw new IllegalStateException("独立登记配置同步密钥长度至少 24");
    }
}
