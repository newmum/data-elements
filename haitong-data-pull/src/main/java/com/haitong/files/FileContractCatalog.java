package com.haitong.files;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileContractCatalog implements InitializingBean {
    public record Contract(String key,String tenantId,String datasourceId,String collectionId,long version,String hash,
                           FileTransferProperties.CollectionContract policy,RoutePlan plan) {}
    public record Scope(Contract contract,boolean readAll) {}
    private final FileTransferProperties p;private final RouteCatalog routes;private final Json json;
    private Map<String,Contract> contracts=Map.of();
    private Map<String,Contract> attachmentFields=Map.of();
    public FileContractCatalog(FileTransferProperties p,RouteCatalog routes,Json json){this.p=p;this.routes=routes;this.json=json;}
    @Override public void afterPropertiesSet(){
        if(p.getSingleUploadMaxBytes()<1 || p.getSingleUploadMaxBytes()>p.getMaxFileBytes() ||
           p.getPartSizeBytes()<65536 || p.getPartSizeBytes()>64L*1024*1024 || p.getMaxFileBytes()<1 ||
           p.getMaxFileBytes()>1024L*1024*1024*1024 || p.getStorageLimitBytes()<p.getMaxFileBytes()*2 ||
           p.getMaxStreams()<1 || p.getMaxStreams()>128 || p.getVerificationThreads()<1 || p.getVerificationThreads()>16 ||
           p.getSessionHours()<1 || p.getSessionHours()>168 || p.getLeaseSeconds()<60 || p.getMaxActiveUploads()<1 ||
           p.getScannerTimeoutSeconds()<1 || p.getScannerTimeoutSeconds()>3600 || p.getCollections().size()>100)
            throw new IllegalStateException("文件传输容量或并发配置不合法");
        Map<String,Contract> built=new LinkedHashMap<>(),fields=new LinkedHashMap<>();
        for(var entry:p.getCollections().entrySet()){
            var c=entry.getValue();Rules.code(entry.getKey());
            if(c==null || !c.isEnabled())continue;
            for(String id:List.of(c.getTenantId(),c.getDatasourceId(),c.getCollectionId()))Rules.code(id);
            if(c.getVersion()<1 || c.getMaxFileBytes()<1 || c.getMaxFileBytes()>p.getMaxFileBytes() ||
               c.getQuotaBytes()<2*c.getMaxFileBytes() || c.getMaxActiveUploads()<1 || c.getClients().isEmpty() ||
               c.getTargets().isEmpty() || c.getTargets().size()>8 || c.getMetadataTypes().size()>100 ||
               !c.getMetadataTypes().keySet().containsAll(c.getRequiredMetadata()))
                throw new IllegalStateException("文件集合契约不完整："+entry.getKey());
            for(String client:c.getClients())Rules.code(client);
            for(String client:c.getReadClients())Rules.code(client);
            for(var field:c.getMetadataTypes().entrySet()){
                Rules.code(field.getKey());if(!Set.of("STRING","NUMBER","BOOLEAN").contains(field.getValue()))throw new IllegalStateException("文件元数据类型不支持");
            }
            if(c.getAllowedExtensions().isEmpty() || !Set.of("jpg","jpeg","png","pdf","docx").containsAll(c.getAllowedExtensions()))
                throw new IllegalStateException("文件扩展名白名单不支持");
            if(c.isRequireMalwareScan() && (p.getScannerCommand().isEmpty() || !p.getScannerCommand().contains("{file}")))
                throw new IllegalStateException("集合要求恶意文件检查，必须配置含独立 {file} 参数的 scanner-command");
            List<TargetPlan> targets=new ArrayList<>();
            for(String code:c.getTargets()){
                TargetPlan t=routes.target(code);
                Kind kind=switch(t.kind()){case FTP->Kind.FILE_FTP;case LOCAL->Kind.FILE_LOCAL;case KAFKA->Kind.FILE_KAFKA;default->throw new IllegalStateException("文件只支持 LOCAL、FTP/SFTP 和 Kafka 引用目标");};
                targets.add(new TargetPlan(t.code(),kind,t.topic(),t.directory(),t.kafka(),t.ftp(),null));
            }
            if(new HashSet<>(c.getTargets()).size()!=targets.size() || !new HashSet<>(c.getTargets()).containsAll(c.getReceiptClients().keySet()))
                throw new IllegalStateException("文件目标重复或回执身份引用未知目标");
            for(String client:c.getReceiptClients().values())Rules.code(client);
            RoutePlan plan=new RoutePlan("file."+entry.getKey(),"BINARY",List.of(),Map.of(),List.copyOf(targets));
            String hash=Rules.sha256(json.bytes(Map.of("policy",c,"plan",plan)));
            String lookup=c.getDatasourceId()+"\u0000"+c.getCollectionId();
            Contract contract=new Contract(entry.getKey(),c.getTenantId(),c.getDatasourceId(),c.getCollectionId(),c.getVersion(),hash,c,plan);
            if(built.put(lookup,contract)!=null)
                throw new IllegalStateException("数据源和文件集合必须唯一，不能由请求选择租户");
            String table=c.getTableName(),field=c.getFieldName();
            // Existing explicitly configured table.field collections remain usable without changing their ledger identity.
            if(table.isEmpty()&&field.isEmpty()){
                String[] names=c.getCollectionId().split("\\.",-1);
                if(names.length==2){table=names[0];field=names[1];}
            }
            if(!table.isEmpty()||!field.isEmpty()){
                attachmentName(table);attachmentName(field);
                if(fields.put(c.getDatasourceId()+"\u0000"+table+"\u0000"+field,contract)!=null)
                    throw new IllegalStateException("同一数据源的附件表字段只能绑定一个文件集合");
            }
        }
        contracts=Collections.unmodifiableMap(built);
        attachmentFields=Collections.unmodifiableMap(fields);
    }
    public Collection<Contract> all(){return contracts.values();}
    public Contract get(String source,String collection){
        Contract c=contracts.get(source+"\u0000"+collection);if(c==null)throw RelayException.notFound();return c;
    }
    public Contract upload(Secrets.Identity caller,String source,String collection){
        Contract c=get(source,collection);if(!caller.admin()&&!c.policy().getClients().contains(caller.id()))throw RelayException.notFound();return c;
    }
    public Contract uploadField(Secrets.Identity caller,String source,String table,String field){
        attachmentName(table);attachmentName(field);
        Contract c=attachmentFields.get(source+"\u0000"+table+"\u0000"+field);
        if(c==null||!caller.admin()&&!c.policy().getClients().contains(caller.id()))throw RelayException.notFound();
        return c;
    }
    private static void attachmentName(String name){
        if(name==null||!name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))throw RelayException.invalid("附件表名或字段名格式不正确");
    }
    public List<Scope> scopes(Secrets.Identity caller){
        List<Scope> result=new ArrayList<>();
        for(Contract c:contracts.values()){
            boolean read=caller.admin()||c.policy().getReadClients().contains(caller.id());
            if(read||c.policy().getClients().contains(caller.id()))result.add(new Scope(c,read));
        }
        return result;
    }
    public String quotaKey(Contract c){return Rules.sha256((c.tenantId()+"|"+c.datasourceId()+"|"+c.collectionId()).getBytes(StandardCharsets.UTF_8));}
    public Map<String,Object> metadata(Contract c,Object value){
        if(!(value instanceof Map<?,?> values) || json.bytes(values).length>65536)throw RelayException.invalid("metadata 必须为不超过64KiB的对象");
        Map<String,Object> result=new TreeMap<>();
        for(var item:values.entrySet()){
            String key=Objects.toString(item.getKey(),"");String type=c.policy().getMetadataTypes().get(key);Object v=item.getValue();
            if(type==null || !(v==null || switch(type){case "STRING"->v instanceof String s&&s.length()<=4096;case "NUMBER"->v instanceof Number;case "BOOLEAN"->v instanceof Boolean;default->false;}))
                throw RelayException.invalid("元数据字段未登记或类型不正确："+key);
            result.put(key,v);
        }
        json.require(result,c.policy().getRequiredMetadata());return result;
    }
}
