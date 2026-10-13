package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.stereotype.Service;
import java.util.*;

/** Upgrade a selected mapping node in place. No source processor is copied, deleted, or cleared. */
@Service
public class MappingUpgradeService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final MappingVersionStore versions;
    private final PipelineRepository pipelines;
    private final UnifiedMappingService engine;
    private final DslCompiler compiler;
    private final NifiClient nifi;
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    public MappingUpgradeService(MappingVersionStore versions,PipelineRepository pipelines,UnifiedMappingService engine,DslCompiler compiler,NifiClient nifi,org.springframework.transaction.PlatformTransactionManager manager){this.versions=versions;this.pipelines=pipelines;this.engine=engine;this.compiler=compiler;this.nifi=nifi;transaction=new org.springframework.transaction.support.TransactionTemplate(manager);}
    public Map<String,Object> upgrade(String pipelineId,String nodeId,long revision,String expectedPlanHash,boolean start){
        Pipeline pipeline=versions.requirePipeline(pipelineId);if(pipeline.updatedAt()==null||pipeline.updatedAt()!=revision)throw new MappingException("DRAFT_VERSION_CONFLICT","画布已变化，请重新加载后升级");
        var node=pipeline.dsl().nodes().stream().filter(value->value.id().equals(nodeId)&&Set.of("transform.field-mapping","transform.field-enrichment").contains(value.manifestKey())).findFirst().orElseThrow(()->new MappingException("RESOURCE_FORBIDDEN","节点不属于当前流程的字段映射"));
        if(pipeline.nodeMapping()==null||pipeline.nodeMapping().primaryProcessorIds()==null||pipeline.nodeMapping().edgeProcessorEndpoints()==null)throw new MappingException("MIGRATION_UNAVAILABLE","旧运行图缺少原生节点与连接标识");
        String primary=pipeline.nodeMapping().primaryProcessorIds().get(nodeId);if(primary==null||pipeline.nifiProcessGroupId()==null)throw new MappingException("MIGRATION_UNAVAILABLE","节点尚未发布");
        JsonNode owner=nifi.getProcessGroup(pipeline.nifiProcessGroupId());String marker="data-elements:v1:"+TenantContext.requireTenantId()+":pipeline:"+pipelineId;if(!marker.equals(owner.path("component").path("comments").asText()))throw new MappingException("RESOURCE_FORBIDDEN","旧运行图缺少可核验的租户及流程归属标记");
        JsonNode original=nifi.get("/processors/"+primary,JsonNode.class);String group=original.path("component").path("parentGroupId").asText();JsonNode rootFlow=nifi.get("/flow/process-groups/"+pipeline.nifiProcessGroupId(),JsonNode.class).path("processGroupFlow").path("flow");
        if(!group.equals(pipeline.nifiProcessGroupId())&&!containsId(rootFlow.path("processGroups"),group))throw new MappingException("RESOURCE_FORBIDDEN","字段处理器不在当前流程组范围内");
        JsonNode flow=group.equals(pipeline.nifiProcessGroupId())?rootFlow:nifi.get("/flow/process-groups/"+group,JsonNode.class).path("processGroupFlow").path("flow");
        if(!flow.path("processGroups").isEmpty()||!group.equals(pipeline.nifiProcessGroupId())&&rootFlow.path("processGroups").size()!=1||group.equals(pipeline.nifiProcessGroupId())&&!rootFlow.path("processGroups").isEmpty())throw new MappingException("MIGRATION_SCOPE_UNSUPPORTED","该图包含多个嵌套范围，不能保证原运行状态完整恢复");
        Map<String,Set<String>> runningBefore=new LinkedHashMap<>();runningBefore.put(pipeline.nifiProcessGroupId(),running(rootFlow));if(!group.equals(pipeline.nifiProcessGroupId()))runningBefore.put(group,running(flow));
        if(flow.path("processors").size()>512||flow.path("connections").size()>1024)throw new MappingException("BUDGET_EXCEEDED","旧图超过有界升级核验规模");
        Set<String> oldProcessors=new LinkedHashSet<>();pipeline.nodeMapping().processorIdToCanvasNode().forEach((id,canvas)->{if(nodeId.equals(canvas))oldProcessors.add(id);});
        Map<String,JsonNode> processors=index(flow.path("processors"));if(!processors.keySet().containsAll(oldProcessors))throw new MappingException("MIGRATION_UNAVAILABLE","节点的原生处理器分散于不同流程组");
        JsonNode services=nifi.get("/flow/process-groups/"+group+"/controller-services?includeAncestorGroups=true&includeDescendantGroups=false",JsonNode.class).path("controllerServices");if(services.size()>512)throw new MappingException("BUDGET_EXCEEDED","Controller Service核验规模超过512");Map<String,JsonNode> serviceMap=index(services);
        JsonNode props=original.path("component").path("config").path("properties");String reader=serviceReference(props,serviceMap,true),typed=props.path("typed-record-reader").asText(reader);
        List<Pipeline.Edge> edges=pipeline.dsl().edges().stream().filter(edge->edge.source().equals(nodeId)||edge.target().equals(nodeId)).toList();if(edges.isEmpty()||edges.size()>128)throw new MappingException("MIGRATION_UNAVAILABLE","字段节点边界连接缺失或超过128");
        Map<String,JsonNode> connections=index(flow.path("connections"));Map<String,JsonNode> selected=new LinkedHashMap<>();String last=primary;
        for(var edge:edges){var endpoint=pipeline.nodeMapping().edgeProcessorEndpoints().get(edge.id());if(endpoint==null||!connections.containsKey(endpoint.connectionId()))throw new MappingException("MIGRATION_UNAVAILABLE","旧图连接发生变化，请重新核对");JsonNode connection=connections.get(endpoint.connectionId());selected.put(edge.id(),connection);if(edge.source().equals(nodeId))last=endpoint.sourceProcessorId();}
        String writer=serviceReference(processors.get(last).path("component").path("config").path("properties"),serviceMap,false);
        var prepared=engine.compile(parse(node.config().get("mappings")));if(expectedPlanHash==null||!expectedPlanHash.equals(prepared.plan().hash()))throw new MappingException("DRAFT_NOT_SAVED","当前规则与已保存草稿不一致，请先保存画布再升级");nifi.requireMappingExtension();requireEmpty(pipeline.nifiProcessGroupId());
        DslCompiler.MappingReplacement replacement=null;Map<String,String> newConnections=new LinkedHashMap<>();Set<String> deleted=new LinkedHashSet<>();Set<String> disabled=new LinkedHashSet<>();boolean committed=false;
        try{
            // Stopping can already change the runtime, even when its response or a later read fails.
            nifi.setProcessGroupState(pipeline.nifiProcessGroupId(),"STOPPED");nifi.waitForAllProcessorsStopped(pipeline.nifiProcessGroupId(),30000);requireEmpty(pipeline.nifiProcessGroupId());
            // Refresh the one tree after stop; component revisions may have advanced while stopping.
            flow=nifi.get("/flow/process-groups/"+group,JsonNode.class).path("processGroupFlow").path("flow");processors=index(flow.path("processors"));connections=index(flow.path("connections"));for(var edge:edges){String id=selected.get(edge.id()).path("id").asText();JsonNode fresh=connections.get(id);if(fresh==null)throw new MappingException("MIGRATION_UNAVAILABLE","旧图边界连接在停止期间变化");selected.put(edge.id(),fresh);}
            replacement=compiler.createMappingReplacement(pipeline,node,group,reader,typed,writer,prepared);String processor=replacement.node().inletProcessorId;
            for(var edge:edges){JsonNode old=selected.get(edge.id());String source=edge.source().equals(nodeId)?processor:old.path("component").path("source").path("id").asText();String target=edge.target().equals(nodeId)?processor:old.path("component").path("destination").path("id").asText();newConnections.put(edge.id(),createConnection(group,old.path("component"),source,target,edge.source().equals(nodeId)) );}
            JsonNode validation=nifi.get("/processors/"+processor,JsonNode.class).path("component");if(!validation.path("validationErrors").isEmpty())throw new MappingException("MIGRATION_VALIDATION_FAILED","新字段处理器校验未通过，旧图保留");
            for(var edge:edges){JsonNode connection=selected.get(edge.id());nifi.delete("/connections/"+connection.path("id").asText()+"?version="+connection.path("revision").path("version").asLong()+"&clientId="+nifi.clientId());deleted.add(edge.id());}
            disabled.addAll(oldProcessors);states(group,oldProcessors,"DISABLED",processors);
            Map<String,String> primaryIds=new LinkedHashMap<>(pipeline.nodeMapping().primaryProcessorIds());primaryIds.put(nodeId,processor);Map<String,String>reverse=new LinkedHashMap<>(pipeline.nodeMapping().processorIdToCanvasNode());oldProcessors.forEach(reverse::remove);reverse.put(processor,nodeId);Map<String,NifiNodeMapping.EdgeEndpoint>endpoints=new LinkedHashMap<>(pipeline.nodeMapping().edgeProcessorEndpoints());
            for(var edge:edges){var old=endpoints.get(edge.id());endpoints.put(edge.id(),new NifiNodeMapping.EdgeEndpoint(edge.source().equals(nodeId)?processor:old.sourceProcessorId(),edge.target().equals(nodeId)?processor:old.targetProcessorId(),newConnections.get(edge.id())));}
            NifiNodeMapping mapping=new NifiNodeMapping(primaryIds,reverse,endpoints);String planId=replacement.planId();
            Pipeline updated=transaction.execute(status->{versions.activateNode(pipeline,nodeId,planId,pipeline.nifiProcessGroupId());return pipelines.update(pipelineId,current->{if(!Objects.equals(current.updatedAt(),pipeline.updatedAt()))throw new MappingException("DRAFT_VERSION_CONFLICT","画布在升级期间被其他入口修改");return runtime(current,mapping,PipelineStatus.STOPPED);});});committed=true;
            if(start){
                for(var scope:runningBefore.entrySet()){Set<String> ids=new LinkedHashSet<>(scope.getValue());boolean mappingRan=ids.stream().anyMatch(oldProcessors::contains);ids.removeAll(oldProcessors);if(scope.getKey().equals(group)&&mappingRan)ids.add(processor);if(!ids.isEmpty()){JsonNode current=nifi.get("/flow/process-groups/"+scope.getKey(),JsonNode.class).path("processGroupFlow").path("flow");states(scope.getKey(),ids,"RUNNING",index(current.path("processors")));}}
                updated=pipelines.updateStatus(pipelineId,pipeline.status());
            }
            return Map.of("pipeline",updated,"mode","mapping-only-preserve-components","planId",replacement.planId(),"sourceProcessorIdsPreserved",true,"oldMappingProcessorsRetainedDisabled",oldProcessors,"otherDraftChangesPublished",false,"previousRunStatesRestored",start,"started",start&&pipeline.status()==PipelineStatus.RUNNING);
        }catch(RuntimeException error){
            boolean recoveryComplete=true,stopped=false;
            try{nifi.setProcessGroupState(pipeline.nifiProcessGroupId(),"STOPPED");nifi.waitForAllProcessorsStopped(pipeline.nifiProcessGroupId(),30000);stopped=true;}catch(RuntimeException stopping){suppress(error,stopping);recoveryComplete=false;}
            if(!committed&&stopped){
              try{
                Map<String,NifiNodeMapping.EdgeEndpoint> restored=new LinkedHashMap<>(pipeline.nodeMapping().edgeProcessorEndpoints());
                if(replacement!=null||!newConnections.isEmpty()||!deleted.isEmpty()||!disabled.isEmpty()){
                JsonNode currentFlow=nifi.get("/flow/process-groups/"+group,JsonNode.class).path("processGroupFlow").path("flow");if(!disabled.isEmpty())states(group,disabled,"STOPPED",index(currentFlow.path("processors")));
                for(String edgeId:deleted){JsonNode old=selected.get(edgeId);String restoredId=createConnection(group,old.path("component"),old.path("component").path("source").path("id").asText(),old.path("component").path("destination").path("id").asText(),false);var endpoint=restored.get(edgeId);restored.put(edgeId,new NifiNodeMapping.EdgeEndpoint(endpoint.sourceProcessorId(),endpoint.targetProcessorId(),restoredId));}
                Map<String,JsonNode> candidates=index(currentFlow.path("connections"));for(String id:newConnections.values()){try{JsonNode connection=candidates.get(id);if(connection!=null)nifi.delete("/connections/"+id+"?version="+connection.path("revision").path("version").asLong());}catch(RuntimeException retained){suppress(error,retained);recoveryComplete=false;}}
                if(replacement!=null)nifi.setProcessorRunStatus(replacement.node().inletProcessorId,"DISABLED");
                }
                NifiNodeMapping mapping=new NifiNodeMapping(pipeline.nodeMapping().primaryProcessorIds(),pipeline.nodeMapping().processorIdToCanvasNode(),restored);pipelines.update(pipelineId,current->{if(!Objects.equals(current.updatedAt(),pipeline.updatedAt()))throw new MappingException("DRAFT_VERSION_CONFLICT","恢复期间画布已变化，不能覆盖其他入口的运行标识");return runtime(current,mapping,PipelineStatus.STOPPED);});
              }catch(RuntimeException recovery){suppress(error,recovery);recoveryComplete=false;}
            }
            if(committed&&stopped)try{pipelines.updateStatus(pipelineId,PipelineStatus.STOPPED);}catch(RuntimeException persistence){suppress(error,persistence);recoveryComplete=false;}
            throw new MappingException(recoveryComplete?"MAPPING_UPGRADE_FAILED":"MAPPING_UPGRADE_RECOVERY_REQUIRED",recoveryComplete?"升级未完成，流程已停止，来源组件及水位保留；请检查连接与计划后重试":"升级及自动恢复未全部完成，来源组件及水位未删除；请核对运行图状态后人工恢复",error);
        }
    }
    private static void suppress(RuntimeException original,RuntimeException recovery){if(original!=recovery)original.addSuppressed(recovery);}
    private void requireEmpty(String group){JsonNode snapshot=nifi.getProcessGroupStatus(group).path("processGroupStatus").path("aggregateSnapshot");long queued=snapshot.path("queuedCount").asLong(snapshot.path("flowFilesQueued").asLong(-1));if(queued!=0)throw new MappingException("MIGRATION_QUEUE_NOT_EMPTY","旧流程队列未确认为空，不能替换字段节点");}
    private void states(String group,Set<String> ids,String state,Map<String,JsonNode> processors){ObjectNode body=JSON.createObjectNode().put("id",group).put("state",state).put("disconnectedNodeAcknowledged",false);ObjectNode components=body.putObject("components");for(String id:ids){JsonNode processor=processors.get(id);if(processor==null)throw new MappingException("MIGRATION_UNAVAILABLE","原生节点版本不可核验");components.set(id,processor.path("revision"));}nifi.put("/flow/process-groups/"+group,body,JsonNode.class);}
    private String createConnection(String group,JsonNode original,String source,String target,boolean mappingOutput){ObjectNode component=JSON.createObjectNode();for(String property:List.of("source","destination","selectedRelationships","backPressureObjectThreshold","backPressureDataSizeThreshold","flowFileExpiration","prioritizers","bends","loadBalanceStrategy","loadBalancePartitionAttribute","loadBalanceCompression","name"))if(original.has(property))component.set(property,original.get(property).deepCopy());((ObjectNode)component.path("source")).put("id",source);((ObjectNode)component.path("destination")).put("id",target);if(mappingOutput)component.putArray("selectedRelationships").add("success");ObjectNode body=JSON.createObjectNode();body.putObject("revision").put("version",0).put("clientId",nifi.clientId());body.set("component",component);return nifi.post("/process-groups/"+group+"/connections",body,JsonNode.class).path("id").asText();}
    private static String serviceReference(JsonNode props,Map<String,JsonNode> services,boolean reader){for(String key:reader?List.of("record-reader","Record Reader","recordReader","reader"):List.of("record-writer","Record Writer","recordWriter","writer")){String id=props.path(key).asText();if(services.containsKey(id))return id;}Set<String> candidates=new HashSet<>();props.forEach(value->{String id=value.asText();JsonNode service=services.get(id);if(service!=null){String type=service.path("component").path("type").asText();if(reader&&type.endsWith("Reader")||!reader&&type.endsWith("RecordSetWriter"))candidates.add(id);}});if(candidates.size()!=1)throw new MappingException("MIGRATION_CODEC_UNAVAILABLE","无法唯一确认旧节点的Record Reader/Writer，未修改来源");return candidates.iterator().next();}
    private static boolean containsId(JsonNode nodes,String id){for(JsonNode node:nodes)if(id.equals(node.path("id").asText()))return true;return false;}
    private static Map<String,JsonNode> index(JsonNode nodes){Map<String,JsonNode> result=new LinkedHashMap<>();nodes.forEach(node->result.put(node.path("id").asText(),node));return result;}
    private static Set<String> running(JsonNode flow){Set<String> result=new LinkedHashSet<>();for(JsonNode processor:flow.path("processors"))if(processor.path("component").path("state").asText().equals("RUNNING"))result.add(processor.path("id").asText());return result;}
    private static JsonNode parse(Object value){try{return value instanceof String text?JSON.readTree(text):JSON.valueToTree(value);}catch(Exception error){throw new MappingException("INVALID_JSON","字段草稿无法解析");}}
    private static Pipeline runtime(Pipeline value,NifiNodeMapping mapping,PipelineStatus status){return new Pipeline(value.id(),value.name(),value.description(),value.createdAt(),value.updatedAt(),value.dsl(),value.nifiProcessGroupId(),status,value.lastDeployedHash(),value.lastDeployedAt(),value.lastStoppedAt(),mapping,value.lastBulletinId());}
}
