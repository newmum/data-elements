package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.integration.nifi.canvas.mapping.*;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/nifi/api/field-mapping")
public class MappingAssetController {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final MappingVersionStore store;
    private final UnifiedMappingService engine;
    private final RegisteredMappingSchemaService schemas;
    private final PipelineRepository pipelines;
    private final MappingProjectionService projections;
    private final MappingUpgradeService upgrades;
    private PublishedMappingLifecycleService publishedLifecycle;
    public MappingAssetController(MappingVersionStore store,UnifiedMappingService engine,RegisteredMappingSchemaService schemas,PipelineRepository pipelines,MappingProjectionService projections,MappingUpgradeService upgrades){this.store=store;this.engine=engine;this.schemas=schemas;this.pipelines=pipelines;this.projections=projections;this.upgrades=upgrades;}
    @org.springframework.beans.factory.annotation.Autowired public void setPublishedLifecycle(PublishedMappingLifecycleService publishedLifecycle){this.publishedLifecycle=publishedLifecycle;}
    @ExceptionHandler(MappingException.class) public ResponseEntity<?> error(MappingException error){return ResponseEntity.unprocessableEntity().body(Map.of("code",error.code(),"error",error.getMessage()));}
    @GetMapping("/plans") public Map<String,Object> plans(@RequestParam String pipelineId,@RequestParam(required=false)String nodeId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="created_time")String sortField,@RequestParam(defaultValue="desc")String sortDir){return store.history(pipelineId,nodeId,page,size,sortField,sortDir);}
    @GetMapping("/plans/{id}") public JsonNode plan(@PathVariable String id,@RequestParam String pipelineId){JsonNode content=store.plan(pipelineId,id);engine.compile(content);return content;}
    @PostMapping("/plans/{id}/restore-draft") @com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation @Transactional public Pipeline restore(@PathVariable String id,@RequestBody JsonNode body){
        String pipelineId=body.path("pipelineId").asText(),nodeId=body.path("nodeId").asText();Pipeline current=store.requirePipeline(pipelineId);if(!body.has("expectedUpdatedAt")||current.updatedAt()==null||body.path("expectedUpdatedAt").asLong()!=current.updatedAt())throw new MappingException("DRAFT_VERSION_CONFLICT","流程已变化，请重新加载后恢复");
        JsonNode restored=store.plan(pipelineId,id);engine.compile(restored);if(current.dsl().nodes().stream().noneMatch(node->node.id().equals(nodeId)))throw new MappingException("RESOURCE_FORBIDDEN","目标节点不属于当前流程");
        return pipelines.update(pipelineId,value->{if(!Objects.equals(current.updatedAt(),value.updatedAt()))throw new MappingException("DRAFT_VERSION_CONFLICT","流程已被其他入口修改");List<Pipeline.Node> nodes=new ArrayList<>();for(var node:value.dsl().nodes()){if(node.id().equals(nodeId)){Map<String,Object>cfg=new LinkedHashMap<>(node.config());cfg.put("mappings",restored.toString());nodes.add(new Pipeline.Node(node.id(),node.manifestKey(),node.label(),node.category(),node.x(),node.y(),cfg));}else nodes.add(node);}Pipeline next=new Pipeline(value.id(),value.name(),value.description(),value.createdAt(),value.updatedAt(),new Pipeline.Dsl(value.dsl().version(),nodes,value.dsl().edges()),value.nifiProcessGroupId(),value.status(),value.lastDeployedHash(),value.lastDeployedAt(),value.lastStoppedAt(),value.nodeMapping(),value.lastBulletinId());projections.persist(next);return next;});
    }
    @GetMapping("/templates") public Map<String,Object> templates(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="created_time")String sortField,@RequestParam(defaultValue="desc")String sortDir){return store.templates(page,size,sortField,sortDir);}
    @PostMapping("/templates") public JsonNode saveTemplate(@RequestBody JsonNode body){
        ObjectNode specification=(ObjectNode)engine.compile(body.path("spec")).plan().document();rejectSecrets(specification);specification.remove(List.of("planId","sampleRows","sourceConfig"));return store.saveResource("TEMPLATE",body.path("name").asText(),JSON.createObjectNode().put("schemaVersion","2.0").put("engineVersion",MappingEngine.VERSION),specification);
    }
    @GetMapping("/templates/{id}") public JsonNode template(@PathVariable String id){
        JsonNode content=store.resources(List.of(id),"TEMPLATE").get(id).path("content");
        // An immutable version does not freeze a user's access to its registered resources.
        // Compile once to batch-authorize all current references, but return the saved version unchanged.
        engine.compile(content);
        return content;
    }
    @PostMapping("/templates/{id}/import") public JsonNode importTemplate(@PathVariable String id,@RequestBody JsonNode body){ObjectNode template=template(id).deepCopy();JsonNode mapping=body.path("fieldBindings");rebind(template,mapping);template.remove("schemaContract");for(JsonNode rule:template.path("rules"))((ObjectNode)rule).put("ruleId",UUID.randomUUID().toString());if(body.path("dictionaryBindings").isObject())template.set("dictionaryBindings",body.path("dictionaryBindings"));if(body.path("schemaContract").isObject())template.set("schemaContract",body.path("schemaContract"));var compiled=engine.compile(template);ObjectNode result=compiled.plan().document();result.putObject("templateOrigin").put("templateId",id);return result;}
    @PostMapping("/dictionary-snapshots") public JsonNode snapshot(@RequestBody JsonNode body){if(!body.path("definition").isObject())throw new MappingException("INVALID_DICTIONARY","快照须提供结构化字典定义");ObjectNode definition=body.path("definition").deepCopy();definition.remove(List.of("snapshotId","snapshotHash","definitionHash"));definition.put("freshnessMode","LIVE_BATCH");return engine.buildSnapshot(definition,body.path("name").asText());}
    @PostMapping("/schema-contract") public JsonNode schemaContract(@RequestBody JsonNode body){Map<String,JsonNode> references=new LinkedHashMap<>();for(String side:List.of("source","target"))if(body.path(side+"Ref").isObject())references.put(side,body.path(side+"Ref"));if(references.isEmpty())throw new MappingException("INVALID_SCHEMA","请选择已登记来源或目标表");var bound=schemas.resolve(references);var actuals=schemas.physicalBatch(bound);ObjectNode result=JSON.createObjectNode().put("allowAdditionalSourceFields",body.path("allowAdditionalSourceFields").asBoolean(true));for(var entry:bound.entrySet()){JsonNode actual=actuals.get(entry.getKey());result.set(entry.getKey(),actual);result.put(entry.getKey()+"Hash",MappingEngine.hash(actual));result.putObject(entry.getKey()+"Ref").put("datasourceId",entry.getValue().datasourceId()).put("tableId",entry.getValue().tableId());}return result;}
    @PostMapping("/sample") public Map<String,Object> sample(@RequestBody JsonNode body){
        var resolved=schemas.resolve(Map.of("source",body.path("sourceRef"))).get("source");int limit=body.path("limit").asInt(20);if(limit<1||limit>100)throw new MappingException("BUDGET_EXCEEDED","样本数量须为1至100");var rows=schemas.sample(resolved,limit);var preview=engine.preview(body.path("spec"),rows);Map<String,Object> stats=new LinkedHashMap<>(preview.stats());stats.put("sampleMode","REGISTERED_SOURCE_MASKED");stats.put("masked",true);return Map.of("sampleRows",PreviewMasking.mask(rows,Set.of(),true),"resultRows",PreviewMasking.mask(preview.resultRows(),Set.of(),true),"rowResults",preview.rowResults(),"stats",stats);
    }
    @GetMapping("/retention-policy") public Map<String,Object> retention(){return Map.of("policy","KEEP_ALL","automaticDeletion",false,"reason","保留运行、重试、错误队列及回退所需的不可变版本；当前接口不删除历史资源");}
    @GetMapping("/dictionary-tables") public Map<String,Object> dictionaryTables(@RequestParam String datasourceId,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return schemas.dictionaryTables(datasourceId,search,page,size);}
    @GetMapping("/dictionary-columns") public JsonNode dictionaryColumns(@RequestParam String datasourceId,@RequestParam String tableId){return schemas.dictionaryColumns(datasourceId,tableId);}
    @PostMapping("/upgrade-runtime") @com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation public Map<String,Object> upgrade(@RequestBody JsonNode body){return upgrades.upgrade(body.path("pipelineId").asText(),body.path("nodeId").asText(),body.path("expectedUpdatedAt").asLong(-1),body.path("expectedPlanHash").asText(),body.path("start").asBoolean(false));}
    @PostMapping("/start-published") @com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation public Map<String,Object> startPublished(@RequestBody JsonNode body){return publishedLifecycle.start(body.path("pipelineId").asText(),body.path("expectedUpdatedAt").asLong(-1));}
    private static void rejectSecrets(JsonNode node){if(node.isArray()){node.forEach(MappingAssetController::rejectSecrets);return;}if(!node.isObject())return;node.fields().forEachRemaining(entry->{if(Set.of("password","jdbcURL","jdbcUrl","token","keytab","privateKey","sampleRows","snapshots").contains(entry.getKey()))throw new MappingException("TEMPLATE_SECRET_FORBIDDEN","模板不能保存凭据或业务样本");rejectSecrets(entry.getValue());});}
    private static void rebind(JsonNode node,JsonNode mapping){if(node.isArray()){node.forEach(child->rebind(child,mapping));return;}if(!node.isObject())return;ObjectNode object=(ObjectNode)node;for(String key:List.of("name","expression")){JsonNode value=object.get(key);if(value!=null&&value.isTextual()){String text=value.asText();if(mapping.has(text))text=mapping.path(text).asText();else if(key.equals("expression")){Iterator<Map.Entry<String,JsonNode>> entries=mapping.fields();while(entries.hasNext()){var entry=entries.next();text=text.replace("${field:"+entry.getKey()+"}","${field:"+entry.getValue().asText()+"}");}}object.put(key,text);}}object.fields().forEachRemaining(entry->rebind(entry.getValue(),mapping));}
}
