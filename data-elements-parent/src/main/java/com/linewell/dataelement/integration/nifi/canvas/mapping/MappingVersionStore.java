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
        if(scopes==null)throw new MappingException("AUTHORIZATION_UNAVAILABLE","流程数据范围校验服务未配置");
        String tenant=TenantContext.requireTenantId();
        Set<String> datasourceIds=new LinkedHashSet<>(),tableIds=new LinkedHashSet<>();
        // The task association is authoritative even when a saved draft omits its source fields.
        var tasks=boundedRows("SELECT `source_db_id`,`target_db_id`,`source_table_id`,`target_table_id` FROM `data_access_agg_task_t` WHERE `pipeline_id`=? AND `tenant_id`=? AND `is_del`=0",List.of(pipeline.id(),tenant),100);
        if(tasks.size()>100)throw new MappingException("BUDGET_EXCEEDED","流程关联任务超过100项");
        for(var task:tasks){
            for(String key:List.of("source_db_id","target_db_id"))addReference(datasourceIds,task.get(key));
            for(String key:List.of("source_table_id","target_table_id"))addReference(tableIds,task.get(key));
        }
        if(pipeline.dsl()!=null&&pipeline.dsl().nodes()!=null)for(var node:pipeline.dsl().nodes()){
            if(node!=null&&node.config()!=null)collectReferences(JSON.valueToTree(node.config()),datasourceIds,tableIds,0);
        }
        if(tableIds.size()>200||datasourceIds.size()>200)throw new MappingException("BUDGET_EXCEEDED","流程引用资源超过200项");
        if(!tableIds.isEmpty()){
            List<Object> args=new ArrayList<>(tableIds);args.add(tenant);
            var tables=jdbc.query(sql("SELECT `tid`,`datasource_id` FROM `db_table_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(tableIds.size(),"?"))+") AND `tenant_id`=?"),(rs,i)->row(rs),args.toArray());
            Set<String> resolved=new LinkedHashSet<>();
            for(var table:tables){resolved.add(String.valueOf(table.get("tid")));addReference(datasourceIds,table.get("datasource_id"));}
            if(!resolved.equals(tableIds))throw forbidden();
        }
        if(datasourceIds.isEmpty())throw new MappingException("RESOURCE_FORBIDDEN","无法确定当前流程的已登记数据范围");
        if(datasourceIds.size()>200)throw new MappingException("BUDGET_EXCEEDED","流程引用数据源超过200项");
        List<Object> args=new ArrayList<>(datasourceIds);args.add(tenant);
        var sources=jdbc.query(sql("SELECT `tid`,`created_by`,`org_id` FROM `db_datasource_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(datasourceIds.size(),"?"))+") AND `tenant_id`=? AND `is_del`=0"),(rs,i)->row(rs),args.toArray());
        Set<String> allowed=new LinkedHashSet<>();
        // Resolve the current role scope once for the complete source set, then verify every ID.
        scopes.visibleDataSources("MENU:2081000000000000002",sources).forEach(source->allowed.add(String.valueOf(source.get("tid"))));
        if(!allowed.equals(datasourceIds))throw forbidden();
        return pipeline;
    }
    private static MappingException forbidden(){return new MappingException("RESOURCE_FORBIDDEN","流程或其引用资源不存在、不属于当前租户或超出当前角色数据范围");}
    private static void addReference(Set<String> values,Object value){if(value!=null){String text=value.toString().trim();if(!text.isBlank()&&!text.equals("null"))values.add(text);}}
    private static void collectReferences(JsonNode node,Set<String> sources,Set<String> tables,int depth){
        if(depth>32)throw new MappingException("BUDGET_EXCEEDED","流程资源引用嵌套过深");
        if(node.isArray()){node.forEach(child->collectReferences(child,sources,tables,depth+1));return;}
        if(!node.isObject())return;
        for(String key:List.of("registeredDatasourceId","selectedDatabaseId","sourceDbId","targetDbId","dbId","datasourceId","dataSourceId","source_db_id","target_db_id","datasource_id"))if(node.hasNonNull(key))addReference(sources,node.path(key).asText());
        for(String key:List.of("sourceTableId","targetTableId","registeredTableId","tableId","source_table_id","target_table_id","table_id"))if(node.hasNonNull(key))addReference(tables,node.path(key).asText());
        node.fields().forEachRemaining(entry->{
            // Literal business payloads may contain names such as tableId; they are not resource references.
            if(entry.getKey().equals("value")&&Set.of("LITERAL","CONSTANT").contains(node.path("kind").asText()))return;
            if(entry.getKey().equals("defaultValue")&&(node.has("ruleId")||node.has("stepId")||node.has("onError")||node.has("missPolicy")))return;
            if(Set.of("sampleRows","snapshots").contains(entry.getKey())&&node.has("schemaVersion"))return;
            JsonNode child=entry.getValue();
            if(entry.getKey().equals("mappings")&&child.isTextual())child=parse(child.asText());
            if(child.isContainerNode())collectReferences(child,sources,tables,depth+1);
        });
    }
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

    public Map<String,String> publishedReferences(String pipelineId){
        requirePipeline(pipelineId);
        var rows=boundedRows("SELECT `node_id`,`plan_id` FROM `data_access_mapping_publication_t` WHERE `tenant_id`=? AND `pipeline_id`=?",List.of(TenantContext.requireTenantId(),pipelineId),100);
        if(rows.size()>100)throw new MappingException("BUDGET_EXCEEDED","已发布映射节点超过100项");
        Map<String,String> result=new LinkedHashMap<>();
        for(var value:rows){
            String node=Objects.toString(value.get("node_id"),""),plan=Objects.toString(value.get("plan_id"),"");
            if(node.isBlank()||plan.isBlank()||result.putIfAbsent(node,plan)!=null)throw new MappingException("RESOURCE_CORRUPTED","发布引用缺失或节点重复");
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String,Object> history(String pipelineId,String nodeId,int page,int size,String sortField,String sortDir){
        requirePipeline(pipelineId);page=Math.max(1,page);size=Math.max(1,Math.min(100,size));String tenant=TenantContext.requireTenantId();
        String order=switch(sortField==null?"created_time":sortField){case "plan_revision"->"`plan_revision`";case "created_time"->"`created_time`";case "node_id"->"`node_id`";default->throw new MappingException("INVALID_SORT","计划历史排序字段不支持");};String direction="asc".equals(sortDir)?"ASC":"DESC";
        List<Object> args=new ArrayList<>(List.of(tenant,pipelineId));String where=" WHERE `tenant_id`=? AND `pipeline_id`=?";if(nodeId!=null&&!nodeId.isBlank()){where+=" AND `node_id`=?";args.add(nodeId);}
        Long total=jdbc.queryForObject(sql("SELECT COUNT(*) FROM `data_access_mapping_plan_t`"+where),Long.class,args.toArray());
        String select="SELECT * FROM (SELECT `tid`,`node_id`,`plan_revision`,`draft_revision`,`plan_hash`,`engine_version`,`created_by`,`created_time`,ROW_NUMBER() OVER (ORDER BY "+order+" "+direction+",`tid` ASC) AS `row_no` FROM `data_access_mapping_plan_t`"+where+") `page_rows` WHERE `row_no` BETWEEN ? AND ? ORDER BY `row_no`";args.add((page-1)*size+1);args.add(page*size);
        var rows=jdbc.query(sql(select),(rs,i)->row(rs),args.toArray());return Map.of("rows",rows,"total",total==null?0:total,"page",page,"size",size);
    }
    public JsonNode plan(String pipelineId,String planId){return batchPlans(pipelineId,List.of(planId)).get(planId);}
    public Map<String,JsonNode> batchPlans(String pipelineId,Collection<String> ids){
        if(ids==null||ids.size()>100)throw new MappingException("BUDGET_EXCEEDED","计划引用最多100项");
        Set<String> distinct=new LinkedHashSet<>(ids);
        if(distinct.stream().anyMatch(id->id==null||id.isBlank()))throw new MappingException("RESOURCE_FORBIDDEN","计划引用无效");
        requirePipeline(pipelineId);
        if(distinct.isEmpty())return Map.of();
        List<Object> args=new ArrayList<>(distinct);args.add(TenantContext.requireTenantId());args.add(pipelineId);
        String query=sql("SELECT `tid`,`plan_hash`,`plan_content` FROM `data_access_mapping_plan_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(distinct.size(),"?"))+") AND `tenant_id`=? AND `pipeline_id`=?");
        Map<String,JsonNode> result=jdbc.query(query,statement->{statement.setMaxRows(101);statement.setQueryTimeout(10);for(int i=0;i<args.size();i++)statement.setObject(i+1,args.get(i));},rs->{
            Map<String,JsonNode> found=new LinkedHashMap<>();long bytes=0;
            while(rs.next()){
                String id=rs.getString("tid"),content=readPlanContent(rs);
                bytes+=content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if(bytes>33_554_432)throw new MappingException("BUDGET_EXCEEDED","批量计划内容超过32MiB");
                JsonNode document=parse(content);
                if(!document.isObject()||!MappingEngine.hash(document).equals(rs.getString("plan_hash")))throw new MappingException("RESOURCE_CORRUPTED","计划版本摘要不一致");
                if(!distinct.contains(id)||found.putIfAbsent(id,document)!=null)throw new MappingException("RESOURCE_CORRUPTED","计划版本标识重复或不属于请求范围");
            }
            return found;
        });
        if(result==null||!result.keySet().equals(distinct))throw new MappingException("RESOURCE_FORBIDDEN","包含不存在、其他租户或其他流程的计划版本");
        return Collections.unmodifiableMap(result);
    }
    private static String readPlanContent(ResultSet result)throws SQLException{
        try(java.io.Reader reader=result.getCharacterStream("plan_content")){
            if(reader==null)throw new MappingException("RESOURCE_CORRUPTED","计划内容缺失");
            StringBuilder value=new StringBuilder();char[] buffer=new char[8192];int length;
            while((length=reader.read(buffer))!=-1){if(value.length()+length>2_097_152)throw new MappingException("BUDGET_EXCEEDED","执行计划超过2MiB");value.append(buffer,0,length);}
            String content=value.toString();if(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2_097_152)throw new MappingException("BUDGET_EXCEEDED","执行计划超过2MiB");return content;
        }catch(java.io.IOException error){throw new MappingException("RESOURCE_CORRUPTED","计划内容读取失败");}
    }
    public JsonNode saveResource(String kind,String key,JsonNode definition,JsonNode content){
        if(!Set.of("TEMPLATE","DICTIONARY_SNAPSHOT").contains(kind)||key==null||key.isBlank()||key.length()>128)throw new MappingException("INVALID_RESOURCE","资源类型或名称无效");bounded(content,1_048_576);String tenant=TenantContext.requireTenantId();
        Long maximum=jdbc.queryForObject(sql("SELECT MAX(`version_no`) FROM `data_access_mapping_resource_t` WHERE `tenant_id`=? AND `resource_kind`=? AND `resource_key`=?"),Long.class,tenant,kind,key);long version=maximum==null?1:maximum+1;String resourceId=id();String hash=MappingEngine.hash(content);
        jdbc.update(sql("INSERT INTO `data_access_mapping_resource_t` (`tid`,`tenant_id`,`resource_kind`,`resource_key`,`version_no`,`resource_hash`,`definition_json`,`resource_content`,`created_by`,`created_time`) VALUES (?,?,?,?,?,?,?,?,?,?)"),resourceId,tenant,kind,key,version,hash,definition.toString(),content.toString(),actor(),Timestamp.from(Instant.now()));
        return JSON.createObjectNode().put("id",resourceId).put("kind",kind).put("key",key).put("version",version).put("hash",hash);
    }
    public Map<String,JsonNode> resources(Collection<String> ids,String kind){
        Set<String> distinct=new LinkedHashSet<>(ids);if(distinct.isEmpty())return Map.of();if(distinct.size()>32)throw new MappingException("BUDGET_EXCEEDED","资源引用超过32个");List<Object> args=new ArrayList<>(distinct);args.add(TenantContext.requireTenantId());args.add(kind);
        var rows=jdbc.query(sql("SELECT `tid`,`resource_hash`,`definition_json`,`resource_content` FROM `data_access_mapping_resource_t` WHERE `tid` IN ("+String.join(",",Collections.nCopies(distinct.size(),"?"))+") AND `tenant_id`=? AND `resource_kind`=?"),(rs,i)->row(rs),args.toArray());
        Map<String,JsonNode> result=new LinkedHashMap<>();for(var row:rows){JsonNode content=parse(String.valueOf(row.get("resource_content")));if(!MappingEngine.hash(content).equals(row.get("resource_hash")))throw new MappingException("RESOURCE_CORRUPTED","版本资源摘要不一致");ObjectNode envelope=JSON.createObjectNode();envelope.set("definition",parse(String.valueOf(row.get("definition_json"))));envelope.set("content",content);envelope.put("hash",String.valueOf(row.get("resource_hash")));result.put(String.valueOf(row.get("tid")),envelope);}
        if(!result.keySet().equals(distinct))throw new MappingException("RESOURCE_FORBIDDEN","包含不存在或其他租户的版本资源");return result;
    }
    public Map<String,Object> templates(int page,int size,String sortField,String sortDir){size=Math.max(1,Math.min(100,size));page=Math.max(1,page);String tenant=TenantContext.requireTenantId();String order=switch(sortField==null?"created_time":sortField){case "resource_key"->"`resource_key`";case "version_no"->"`version_no`";case "created_time"->"`created_time`";default->throw new MappingException("INVALID_SORT","模板排序字段不支持");};String direction="asc".equals(sortDir)?"ASC":"DESC";Long total=jdbc.queryForObject(sql("SELECT COUNT(*) FROM `data_access_mapping_resource_t` WHERE `tenant_id`=? AND `resource_kind`='TEMPLATE'"),Long.class,tenant);String select="SELECT * FROM (SELECT `tid`,`resource_key`,`version_no`,`resource_hash`,`created_time`,ROW_NUMBER() OVER (ORDER BY "+order+" "+direction+",`tid` ASC) AS `row_no` FROM `data_access_mapping_resource_t` WHERE `tenant_id`=? AND `resource_kind`='TEMPLATE') `page_rows` WHERE `row_no` BETWEEN ? AND ? ORDER BY `row_no`";return Map.of("rows",jdbc.query(sql(select),(rs,i)->row(rs),tenant,(page-1)*size+1,page*size),"total",total==null?0:total);}
    private static JsonNode parse(String value){try{return JSON.readTree(value);}catch(Exception ex){throw new MappingException("RESOURCE_CORRUPTED","版本内容无法解析");}}
}
