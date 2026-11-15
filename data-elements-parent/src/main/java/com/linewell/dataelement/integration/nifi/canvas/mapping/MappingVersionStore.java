package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.dev33.satoken.stp.StpUtil;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Append-only plans/resources; publication references are the only mutable version state. */
@Service
public class MappingVersionStore {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final JdbcTemplate jdbc;
    private final PipelineRepository pipelines;
    private final DataScopeAuthorizationService scopes;
    private final Map<String,String> quotes=new ConcurrentHashMap<>();
    public MappingVersionStore(JdbcTemplate jdbc,PipelineRepository pipelines){this(jdbc,pipelines,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public MappingVersionStore(JdbcTemplate jdbc,PipelineRepository pipelines,DataScopeAuthorizationService scopes){this.jdbc=jdbc;this.pipelines=pipelines;this.scopes=scopes;}
    private String sql(String value){String quote=quotes.computeIfAbsent(TenantContext.requireTenantId(),ignored->jdbc.execute((ConnectionCallback<String>)connection->connection.getMetaData().getIdentifierQuoteString().trim()));if(quote==null||quote.isBlank())throw new MappingException("UNSUPPORTED_DATABASE","平台驱动不支持标识符引用");return value.replace("`",quote);}
    private List<Map<String,Object>> boundedRows(String query,List<?> args,int maximum){return jdbc.query(sql(query),statement->{statement.setMaxRows(maximum+1);statement.setQueryTimeout(10);for(int i=0;i<args.size();i++)statement.setObject(i+1,args.get(i));},(rs,i)->row(rs));}
    public Pipeline requirePipeline(String id){
        Pipeline pipeline=pipelines.findById(id).orElseThrow(()->forbidden());
        String tenant=TenantContext.requireTenantId();
        if(TenantContext.usesControlDatabase()||TenantContext.isIgnored())throw forbidden();
        var references=PipelineResourceReferences.from(pipeline);
        Set<String> datasourceIds=references.datasourceIds(),tableIds=references.tableIds();
        // The task association is authoritative even when a saved draft omits its source fields.
        var tasks=boundedRows("SELECT `source_db_id`,`target_db_id`,`source_table_id`,`target_table_id` FROM `data_access_agg_task_t` WHERE `pipeline_id`=? AND `tenant_id`=? AND `is_del`=0",List.of(pipeline.id(),tenant),100);
        if(tasks.size()>100)throw new MappingException("BUDGET_EXCEEDED","流程关联任务超过100项");
        tasks.forEach(task->PipelineResourceReferences.addTask(pipeline,task,references));
        if(tableIds.size()>200||datasourceIds.size()>200)throw new MappingException("BUDGET_EXCEEDED","流程引用资源超过200项");
        if(!tableIds.isEmpty()){
            List<Object> args=new ArrayList<>(tableIds);args.add(tenant);
            var tables=jdbc.query(sql("SELECT `tid`,`datasource_id` FROM `db_table_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(tableIds.size(),"?"))+") AND `tenant_id`=?"),(rs,i)->row(rs),args.toArray());
            Set<String> resolved=new LinkedHashSet<>();
            for(var table:tables){resolved.add(String.valueOf(table.get("tid")));addReference(datasourceIds,table.get("datasource_id"));}
            if(!resolved.equals(tableIds))throw forbidden();
        }
        // Tenant-owned standalone flows need no fabricated registration to archive or run.
        if(datasourceIds.isEmpty()){
            if(!tableIds.isEmpty())throw forbidden();
            return pipeline;
        }
        if(datasourceIds.size()>200)throw new MappingException("BUDGET_EXCEEDED","流程引用数据源超过200项");
        List<Object> args=new ArrayList<>(datasourceIds);args.add(tenant);
        var sources=jdbc.query(sql("SELECT `tid`,`created_by`,`org_id` FROM `db_datasource_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(datasourceIds.size(),"?"))+") AND `tenant_id`=? AND `is_del`=0"),(rs,i)->row(rs),args.toArray());
        Set<String> allowed=new LinkedHashSet<>();
        // The query already checks every source ID in the active tenant; no additional menu scope applies.
        sources.forEach(source->allowed.add(String.valueOf(source.get("tid"))));
        if(!allowed.equals(datasourceIds))throw forbidden();
        return pipeline;
    }
    private static MappingException forbidden(){return new MappingException("RESOURCE_FORBIDDEN","流程或其引用资源不存在或不属于当前租户");}
    private static void addReference(Set<String> values,Object value){if(value!=null){String text=value.toString().trim();if(!text.isBlank()&&!text.equals("null"))values.add(text);}}
    private static String actor(){try{return StpUtil.isLogin()?StpUtil.getLoginIdAsString():"system";}catch(cn.dev33.satoken.exception.SaTokenContextException unavailable){return "system";}}
    private static String id(){return UUID.randomUUID().toString().replace("-","");}
    private static void bounded(JsonNode content,int maximum){if(content==null||!content.isObject()||content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>maximum)throw new MappingException("BUDGET_EXCEEDED","版本内容无效或超过容量限制");}
    private static Map<String,Object> row(ResultSet rs)throws SQLException{Map<String,Object> result=new LinkedHashMap<>();ResultSetMetaData meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++){Object value=rs.getObject(i);if(value instanceof Clob clob){value=clob.getSubString(1,(int)clob.length());clob.free();}result.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),value);}return result;}

    @Transactional public Map<String,String> archive(Pipeline pipeline,Map<String,MappingEngine.Plan> plans){
        requirePipeline(pipeline.id());if(plans.size()>100)throw new MappingException("BUDGET_EXCEEDED","节点计划数量超过100");if(plans.isEmpty())return Map.of();String tenant=TenantContext.requireTenantId();long draft=pipeline.updatedAt()==null?0:pipeline.updatedAt();
        List<Object> maximumArgs=new ArrayList<>(List.of(tenant,pipeline.id()));maximumArgs.addAll(plans.keySet());
        List<Map<String,Object>> maximums=jdbc.query(sql("SELECT `node_id`,MAX(`plan_revision`) AS `plan_revision` FROM `data_access_mapping_plan_t` WHERE `tenant_id`=? AND `pipeline_id`=? AND `node_id` IN ("+String.join(",",Collections.nCopies(plans.size(),"?"))+") GROUP BY `node_id`"),(rs,i)->row(rs),maximumArgs.toArray());
        List<Object> previousArgs=new ArrayList<>(List.of(tenant,pipeline.id(),draft));List<String> pairs=new ArrayList<>();plans.forEach((node,plan)->{pairs.add("(`node_id`=? AND `plan_hash`=?)");previousArgs.add(node);previousArgs.add(plan.hash());});
        List<Map<String,Object>> previous=jdbc.query(sql("SELECT `tid`,`node_id`,`plan_hash`,`draft_revision` FROM `data_access_mapping_plan_t` WHERE `tenant_id`=? AND `pipeline_id`=? AND `draft_revision`=? AND ("+String.join(" OR ",pairs)+")"),(rs,i)->row(rs),previousArgs.toArray());
        Map<String,Long> revisions=new HashMap<>();Map<String,String> existing=new HashMap<>();
        maximums.forEach(item->revisions.put(String.valueOf(item.get("node_id")),((Number)item.get("plan_revision")).longValue()));
        for(var item:previous){String node=String.valueOf(item.get("node_id"));existing.put(node+":"+item.get("plan_hash")+":"+item.get("draft_revision"),String.valueOf(item.get("tid")));}
        Map<String,String> result=new LinkedHashMap<>();List<Object[]> inserts=new ArrayList<>();
        for(var entry:plans.entrySet()){
            if(pipeline.dsl().nodes().stream().noneMatch(node->node.id().equals(entry.getKey())))throw new MappingException("RESOURCE_FORBIDDEN","节点不属于当前流程");
            var plan=entry.getValue();String identity=entry.getKey()+":"+plan.hash()+":"+draft;String versionId=existing.get(identity);
            if(versionId==null){versionId=id();JsonNode document=plan.document();bounded(document,2_097_152);long revision=revisions.getOrDefault(entry.getKey(),0L)+1;
                inserts.add(new Object[]{versionId,tenant,pipeline.id(),entry.getKey(),revision,draft,plan.hash(),"1.0",MappingEngine.engineVersion(),FunctionRegistry.libraryVersion(),MappingEngine.hash(document.path("schemaContract").path("source")),MappingEngine.hash(document.path("schemaContract").path("target")),document.toString(),document.path("dictionaryBindings").toString(),actor(),Timestamp.from(Instant.now())});}
            result.put(entry.getKey(),versionId);
        }
        if(!inserts.isEmpty())jdbc.batchUpdate(sql("INSERT INTO `data_access_mapping_plan_t` (`tid`,`tenant_id`,`pipeline_id`,`node_id`,`plan_revision`,`draft_revision`,`plan_hash`,`plan_version`,`engine_version`,`function_version`,`source_schema_hash`,`target_schema_hash`,`plan_content`,`resource_version_set`,`created_by`,`created_time`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"),inserts);
        return result;
    }

    @Transactional public void activate(Pipeline pipeline,Map<String,String> plans,String group){
        requirePipeline(pipeline.id());String tenant=TenantContext.requireTenantId();if(plans.size()>100)throw new MappingException("BUDGET_EXCEEDED","发布节点数量超过100");
        if(!plans.isEmpty()){
            List<Object> args=new ArrayList<>(plans.values());args.add(tenant);args.add(pipeline.id());
            List<Map<String,Object>> rows=jdbc.query(sql("SELECT `tid`,`node_id` FROM `data_access_mapping_plan_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(plans.size(),"?"))+") AND `tenant_id`=? AND `pipeline_id`=?"),(rs,i)->row(rs),args.toArray());
            Map<String,String> allowed=new HashMap<>();rows.forEach(item->allowed.put(String.valueOf(item.get("node_id")),String.valueOf(item.get("tid"))));if(!allowed.equals(plans))throw new MappingException("RESOURCE_FORBIDDEN","发布计划引用不属于当前流程节点");
        }
        jdbc.update(sql("DELETE FROM `data_access_mapping_publication_t` WHERE `tenant_id`=? AND `pipeline_id`=?"),tenant,pipeline.id());
        List<Object[]> rows=new ArrayList<>();plans.forEach((node,plan)->rows.add(new Object[]{id(),tenant,pipeline.id(),node,plan,group,Timestamp.from(Instant.now())}));
        if(!rows.isEmpty())jdbc.batchUpdate(sql("INSERT INTO `data_access_mapping_publication_t` (`tid`,`tenant_id`,`pipeline_id`,`node_id`,`plan_id`,`process_group_id`,`published_time`) VALUES (?,?,?,?,?,?,?)"),rows);
    }
    @Transactional public void activateNode(Pipeline pipeline,String node,String plan,String group){
        var rows=jdbc.query(sql("SELECT `node_id`,`plan_id` FROM `data_access_mapping_publication_t` WHERE `tenant_id`=? AND `pipeline_id`=?"),(rs,i)->row(rs),TenantContext.requireTenantId(),pipeline.id());if(rows.size()>100)throw new MappingException("BUDGET_EXCEEDED","已有发布引用超过100个节点");Map<String,String> plans=new LinkedHashMap<>();rows.forEach(item->plans.put(String.valueOf(item.get("node_id")),String.valueOf(item.get("plan_id"))));plans.put(node,plan);activate(pipeline,plans,group);
    }

    public Map<String,JsonNode> resources(Collection<String> ids,String kind){
        Set<String> distinct=new LinkedHashSet<>(ids);if(distinct.isEmpty())return Map.of();if(distinct.size()>32)throw new MappingException("BUDGET_EXCEEDED","资源引用超过32个");List<Object> args=new ArrayList<>(distinct);args.add(TenantContext.requireTenantId());args.add(kind);
        var rows=jdbc.query(sql("SELECT `tid`,`resource_hash`,`definition_json`,`resource_content` FROM `data_access_mapping_resource_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(distinct.size(),"?"))+") AND `tenant_id`=? AND `resource_kind`=?"),(rs,i)->row(rs),args.toArray());
        Map<String,JsonNode> result=new LinkedHashMap<>();for(var row:rows){JsonNode content=parse(String.valueOf(row.get("resource_content")));if(!MappingEngine.hash(content).equals(row.get("resource_hash")))throw new MappingException("RESOURCE_CORRUPTED","版本资源摘要不一致");ObjectNode envelope=JSON.createObjectNode();envelope.set("definition",parse(String.valueOf(row.get("definition_json"))));envelope.set("content",content);envelope.put("hash",String.valueOf(row.get("resource_hash")));result.put(String.valueOf(row.get("tid")),envelope);}
        if(!result.keySet().equals(distinct))throw new MappingException("RESOURCE_FORBIDDEN","包含不存在或其他租户的版本资源");return result;
    }
    private static JsonNode parse(String value){try{return JSON.readTree(value);}catch(Exception ex){throw new MappingException("RESOURCE_CORRUPTED","版本内容无法解析");}}
}
