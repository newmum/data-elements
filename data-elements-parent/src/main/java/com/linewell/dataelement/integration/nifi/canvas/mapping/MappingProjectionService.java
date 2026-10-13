package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** The canvas owns v2 rules; task rows are a versioned, node-specific display projection. */
@Service
public class MappingProjectionService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final JdbcTemplate jdbc;
    private final com.linewell.dataelement.platform.persistence.TenantIdentifierSql identifiers;
    public MappingProjectionService(JdbcTemplate jdbc){this.jdbc=jdbc;identifiers=new com.linewell.dataelement.platform.persistence.TenantIdentifierSql(jdbc);}
    @Transactional public void persist(Pipeline pipeline){
        if(pipeline.dsl()==null||pipeline.dsl().nodes()==null)return;
        Map<String,ObjectNode>specs=new LinkedHashMap<>();for(var node:pipeline.dsl().nodes()){
            if(node.config()==null||!node.config().containsKey("mappings"))continue;
            try{Object raw=node.config().get("mappings");JsonNode spec=raw instanceof String s?JSON.readTree(s):JSON.valueToTree(raw);if(spec.path("schemaVersion").asText().equals("2.0"))specs.put(node.id(),(ObjectNode)spec);}
            catch(Exception ex){throw new MappingException("INVALID_JSON","保存的字段规则JSON无效");}
        }
        String tenant=TenantContext.requireTenantId();
        List<String>tasks=jdbc.queryForList(identifiers.adapt("SELECT tid FROM data_access_agg_task_t WHERE pipeline_id=? AND tenant_id=? AND is_del=0"),String.class,pipeline.id(),tenant);
        if(tasks.isEmpty())return;if(tasks.size()!=1)throw new MappingException("TASK_BINDING_CONFLICT","画布关联的接入任务不唯一");String task=tasks.getFirst();
        List<Map<String,Object>>old=jdbc.queryForList(identifiers.adapt("SELECT tid,target_field,func_code,func_value FROM data_access_field_mapping WHERE task_id=? AND tenant_id=? AND is_del=0"),task,tenant);
        Map<String,String>byIdentity=new HashMap<>();Map<String,String>legacyByTarget=new HashMap<>();List<Object[]>deletions=new ArrayList<>();
        for(var row:old){String id=String.valueOf(row.get("tid"));if("UNIFIED_RULE_V2".equals(row.get("func_code"))){try{JsonNode p=JSON.readTree(String.valueOf(row.get("func_value")));String node=p.path("nodeId").asText();if(!pipeline.id().equals(p.path("pipelineId").asText()))throw new MappingException("PROJECTION_INVALID","投影所属流程与任务关联不一致");byIdentity.put(node+":"+p.path("ruleId").asText(),id);deletions.add(new Object[]{id,tenant});}catch(Exception ex){throw new MappingException("PROJECTION_INVALID","现有规则投影损坏，请先核对后再保存");}}
            else if(specs.size()==1)legacyByTarget.put(String.valueOf(row.get("target_field")),id);
        }
        if(!deletions.isEmpty())jdbc.batchUpdate(identifiers.adapt("UPDATE data_access_field_mapping SET is_del=1 WHERE tid=? AND tenant_id=?"),deletions);
        List<Object[]>updates=new ArrayList<>(),inserts=new ArrayList<>();LocalDateTime now=LocalDateTime.now();int order=0;
        for(var entry:specs.entrySet())for(JsonNode rule:entry.getValue().path("rules")){
            String ruleId=rule.path("ruleId").asText();if(ruleId.isBlank())throw new MappingException("MISSING_RULE_ID","统一规则必须有稳定ruleId");String target=ValueTypes.field(rule.path("target").path("name").asText());String source=rule.path("input").path("name").asText(rule.path("input").path("kind").asText());
            ObjectNode projection=JSON.createObjectNode().put("projectionVersion","2.0").put("pipelineId",pipeline.id()).put("nodeId",entry.getKey()).put("ruleId",ruleId).put("draftRevision",pipeline.updatedAt()==null?0:pipeline.updatedAt()).put("ruleHash",MappingEngine.hash(rule));projection.set("ruleSnapshot",rule);
            if(projection.toString().getBytes(StandardCharsets.UTF_8).length>60_000){projection.remove("ruleSnapshot");projection.put("canonicalRuleRef",entry.getKey()+":"+ruleId);}
            String id=byIdentity.get(entry.getKey()+":"+ruleId);if(id==null)id=legacyByTarget.remove(target);String type=rule.path("target").path("logicalType").asText("ANY");
            if(id==null){id=UUID.randomUUID().toString().replace("-","");inserts.add(new Object[]{id,task,source,target,type,projection.toString(),order++,tenant,now,now});}
            else updates.add(new Object[]{source,target,type,projection.toString(),order++,now,id,tenant});
        }
        if(!updates.isEmpty())jdbc.batchUpdate(identifiers.adapt("UPDATE data_access_field_mapping SET source_field=?,target_field=?,target_data_type=?,func_value=?,sort_no=?,updated_time=?,func_code='UNIFIED_RULE_V2',func_enable=1,dict_enable=0,dict_code=NULL,dict_value=NULL,is_del=0 WHERE tid=? AND tenant_id=?"),updates);
        if(!inserts.isEmpty())jdbc.batchUpdate(identifiers.adapt("INSERT INTO data_access_field_mapping(tid,task_id,source_field,target_field,target_data_type,func_value,sort_no,tenant_id,created_time,updated_time,func_code,func_enable,dict_enable,is_del) VALUES(?,?,?,?,?,?,?,?,?,?,'UNIFIED_RULE_V2',1,0,0)"),inserts);
    }
}
