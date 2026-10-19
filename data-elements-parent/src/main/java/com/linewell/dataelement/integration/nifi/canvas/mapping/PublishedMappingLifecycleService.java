package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.stereotype.Service;
import java.util.*;

/** Explicitly start the existing native graph while keeping unpublished DSL edits pending. */
@Service
public class PublishedMappingLifecycleService {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final MappingVersionStore versions;
    private final PipelineRepository pipelines;
    private final NifiClient nifi;
    private UnifiedMappingService engine;
    @org.springframework.beans.factory.annotation.Autowired public void setEngine(UnifiedMappingService engine){this.engine=engine;}
    public PublishedMappingLifecycleService(MappingVersionStore versions,PipelineRepository pipelines,NifiClient nifi){this.versions=versions;this.pipelines=pipelines;this.nifi=nifi;}
    public Map<String,Object> start(String pipelineId,long expectedUpdatedAt){
        Pipeline pipeline=versions.requirePipeline(pipelineId);
        if(pipeline.updatedAt()==null||pipeline.updatedAt()!=expectedUpdatedAt)throw new MappingException("DRAFT_VERSION_CONFLICT","流程已变化，请重新读取后启动运行版本");
        if(pipeline.nifiProcessGroupId()==null||pipeline.nodeMapping()==null)throw new MappingException("RUNTIME_UNAVAILABLE","流程没有可核验的已发布运行图");
        Map<String,String> published=versions.publishedReferences(pipelineId);if(published.isEmpty())throw new MappingException("RUNTIME_UNAVAILABLE","没有字段映射发布引用，不能绕过整图部署检查");
        if(engine==null)throw new MappingException("AUTHORIZATION_UNAVAILABLE","已发布计划重验服务未配置");engine.compileBatch(versions.batchPlans(pipelineId,published.values()));
        String group=pipeline.nifiProcessGroupId();JsonNode owner=nifi.getProcessGroup(group);
        if(!("data-elements:v1:"+TenantContext.requireTenantId()+":pipeline:"+pipelineId).equals(owner.path("component").path("comments").asText()))throw new MappingException("RESOURCE_FORBIDDEN","原生运行图归属标记不一致");
        Map<String,JsonNode> flows=new LinkedHashMap<>();JsonNode root=flow(group);flows.put(group,root);
        if(root.path("processGroups").size()>1)throw new MappingException("MIGRATION_SCOPE_UNSUPPORTED","当前启动入口仅支持已核验的单层运行图");
        for(JsonNode child:root.path("processGroups")){String id=child.path("id").asText();JsonNode nested=flow(id);if(!nested.path("processGroups").isEmpty())throw new MappingException("MIGRATION_SCOPE_UNSUPPORTED","运行图嵌套范围未被核验");flows.put(id,nested);}
        Map<String,JsonNode> all=new HashMap<>();for(JsonNode scope:flows.values()){if(scope.path("processors").size()>512)throw new MappingException("BUDGET_EXCEEDED","启动核验最多512个原生处理器");for(JsonNode entity:scope.path("processors"))all.put(entity.path("id").asText(),entity);}
        if(pipeline.nodeMapping().processorIdToCanvasNode()==null||!all.keySet().containsAll(pipeline.nodeMapping().processorIdToCanvasNode().keySet()))throw new MappingException("RUNTIME_UNAVAILABLE","来源、目标或辅助处理器缺失，未启动运行图");
        Map<String,JsonNode> edges=new HashMap<>();for(JsonNode scope:flows.values())for(JsonNode edge:scope.path("connections"))edges.put(edge.path("id").asText(),edge);
        if(pipeline.nodeMapping().edgeProcessorEndpoints()==null)throw new MappingException("RUNTIME_UNAVAILABLE","运行图缺少边界连接索引");
        Set<String> boundarySources=new HashSet<>(),knownConnections=new HashSet<>();for(var endpoint:pipeline.nodeMapping().edgeProcessorEndpoints().values()){JsonNode edge=edges.get(endpoint.connectionId());if(edge==null||!endpoint.sourceProcessorId().equals(edge.path("component").path("source").path("id").asText())||!endpoint.targetProcessorId().equals(edge.path("component").path("destination").path("id").asText()))throw new MappingException("RUNTIME_UNAVAILABLE","来源或目标边界连接发生变化，未启动运行图");boundarySources.add(endpoint.sourceProcessorId());knownConnections.add(endpoint.connectionId());}
        for(var edge:edges.entrySet())if(boundarySources.contains(edge.getValue().path("component").path("source").path("id").asText())&&!knownConnections.contains(edge.getKey()))for(JsonNode relation:edge.getValue().path("component").path("selectedRelationships"))if(relation.asText().equals("success"))throw new MappingException("MAPPING_UPGRADE_RECOVERY_REQUIRED","发现未登记的成功分支连接，请先完成运行图恢复");
        for(var entry:published.entrySet()){String id=pipeline.nodeMapping().primaryProcessorIds().get(entry.getKey());JsonNode entity=all.get(id);if(entity==null||!entry.getValue().equals(entity.path("component").path("config").path("properties").path("plan-id").asText()))throw new MappingException("PLAN_VERSION_CONFLICT","当前原生字段节点与已发布计划引用不一致");}
        int once=0;
        try{
            // Read revisions once per native scope; no per-processor GET calls.
            for(var scope:flows.entrySet()){
                ObjectNode body=JSON.createObjectNode().put("id",scope.getKey()).put("state","RUNNING").put("disconnectedNodeAcknowledged",false);ObjectNode components=body.putObject("components");
                for(JsonNode entity:scope.getValue().path("processors")){if(!entity.path("component").path("state").asText().equals("DISABLED")&&!oneShot(entity)&&!entity.path("component").path("state").asText().equals("RUNNING"))components.set(entity.path("id").asText(),entity.path("revision"));}
                if(!components.isEmpty())nifi.put("/flow/process-groups/"+scope.getKey(),body,JsonNode.class);
            }
            for(JsonNode entity:all.values())if(!entity.path("component").path("state").asText().equals("DISABLED")&&oneShot(entity)&&!entity.path("component").path("state").asText().equals("RUNNING")){
                ObjectNode body=JSON.createObjectNode();body.set("revision",entity.path("revision"));body.put("state","RUN_ONCE").put("disconnectedNodeAcknowledged",false);nifi.put("/processors/"+entity.path("id").asText()+"/run-status",body,JsonNode.class);once++;
            }
            Pipeline updated=pipelines.updateStatus(pipelineId,PipelineStatus.RUNNING);return Map.of("pipeline",updated,"mode","PUBLISHED_NATIVE_GRAPH","draftPublished",false,"oneShotSourceCount",once);
        }catch(RuntimeException error){
            boolean stopped=false;try{nifi.setProcessGroupState(group,"STOPPED");nifi.waitForAllProcessorsStopped(group,30000);stopped=true;pipelines.updateStatus(pipelineId,PipelineStatus.STOPPED);}catch(RuntimeException recovery){error.addSuppressed(recovery);}
            throw new MappingException(stopped?"PUBLISHED_START_FAILED":"MAPPING_UPGRADE_RECOVERY_REQUIRED",stopped?"已发布运行图启动失败，流程已停止，未发布草稿保留":"启动及停止确认未完成，请核对原生运行状态",error);
        }
    }
    private JsonNode flow(String group){return nifi.get("/flow/process-groups/"+group,JsonNode.class).path("processGroupFlow").path("flow");}
    private static boolean oneShot(JsonNode entity){JsonNode component=entity.path("component");String type=component.path("type").asText();return com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.isTableFetch(type)||type.endsWith(".GenerateFlowFile")||(type.endsWith(".QueryDatabaseTable")||type.endsWith(".QueryDatabaseTableRecord"))&&component.path("config").path("properties").path("Maximum-value Columns").asText().isBlank();}
}
