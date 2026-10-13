package com.linewell.dataelement.nifi;

import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.documentation.*;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.dbcp.DBCPService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.nifi.processor.util.StandardValidators;
import java.util.*;

@Tags({"haitong","dictionary","batch","record"})
@CapabilityDescription("按已发布字典定义及有界编码集合批量读取，保持真实连接、schema、过滤条件和键类型。")
public class JdbcHaitongDictionaryService extends AbstractControllerService implements HaitongDictionaryService {
    private volatile ConfigurationContext context;
    private volatile JsonNode authorized;
    private volatile JsonNode snapshots;
    private final DictionaryPolicyProvider.Cache cache=new DictionaryPolicyProvider.Cache();
    private final Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>> snapshotIndex=new java.util.concurrent.ConcurrentHashMap<>();
    public static final PropertyDescriptor AUTHORIZED=new PropertyDescriptor.Builder().name("authorized-definitions").description("发布时经租户、角色与登记关联校验的字典定义").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor SNAPSHOTS=new PropertyDescriptor.Builder().name("snapshot-payloads").description("仅执行服务使用的固定版本完整字典快照").sensitive(true).defaultValue("{}").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    @Override protected List<PropertyDescriptor>getSupportedPropertyDescriptors(){return List.of(AUTHORIZED,SNAPSHOTS,MappingCompatibility.REQUIRED_VERSION);}
    @OnEnabled public void enabled(ConfigurationContext context)throws Exception{this.context=context;authorized=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(context.getProperty(AUTHORIZED).getValue());snapshots=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(context.getProperty(SNAPSHOTS).getValue());cache.clear();snapshotIndex.clear();}
    @org.apache.nifi.annotation.lifecycle.OnDisabled public void clearCaches(){cache.clear();snapshotIndex.clear();}
    @Override protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(String name){
        return new PropertyDescriptor.Builder().name(name).description("已授权datasourceId对应的DBCP连接服务").dynamic(true).identifiesControllerService(DBCPService.class).required(false).build();
    }
    @Override public DictionarySpec.Provider provider(){
        DictionarySpec.MeteredProvider live=new DictionarySpec.MeteredProvider() {
          private int queryCount;
          public int queryCount(){return queryCount;}
          public Map<DictionarySpec.Key,DictionarySpec.LookupValue> fetch(DictionarySpec spec,Set<DictionarySpec.Key> keys) {
            JsonNode allowed=authorized.path(spec.id());
            if(allowed.isMissingNode()||!allowed.path("tableId").asText().equals(spec.tableId())||!allowed.path("table").asText().equals(spec.table())||!allowed.path("datasourceId").asText().equals(spec.datasourceId())||!allowed.path("valueColumn").asText().equals(spec.valueColumn())||!allowed.path("schema").asText().equals(spec.schema()))throw new MappingException("RESOURCE_FORBIDDEN","字典定义不属于本服务授权范围");
            if(!Objects.equals(allowed.get("filter"),spec.filter()))throw new MappingException("RESOURCE_FORBIDDEN","字典条件与授权定义不一致");
            List<String>keysAllowed=new ArrayList<>();allowed.path("keyColumns").forEach(n->keysAllowed.add(n.asText()));if(!keysAllowed.equals(spec.keyColumns()))throw new MappingException("RESOURCE_FORBIDDEN","字典键与授权定义不一致");
            List<String>typesAllowed=new ArrayList<>();allowed.path("keyTypes").forEach(n->typesAllowed.add(n.asText()));if(!typesAllowed.equals(spec.keyTypes())||!allowed.path("duplicatePolicy").asText("ERROR").equals(spec.duplicatePolicy()))throw new MappingException("RESOURCE_FORBIDDEN","字典类型或重复键策略与授权定义不一致");
            var property=context.getProperties().keySet().stream().filter(p->p.getName().equals(spec.datasourceId())).findFirst().orElseThrow(()->new MappingException("RESOURCE_FORBIDDEN","执行计划的字典连接未绑定"));
            DBCPService pool=context.getProperty(property).asControllerService(DBCPService.class);
            try(var reader=new BatchDictionaryReader(id->{if(!id.equals(spec.datasourceId()))throw new MappingException("RESOURCE_FORBIDDEN","字典数据源与服务绑定不一致");return pool.getConnection();})){try{return reader.fetch(spec,keys);}finally{queryCount+=reader.queries();}}
          }
        };
        return new DictionaryPolicyProvider(live,authorized,snapshots,cache,getIdentifier(),snapshotIndex);
    }
}
