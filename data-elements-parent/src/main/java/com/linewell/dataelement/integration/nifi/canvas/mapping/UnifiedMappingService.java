package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.alibaba.druid.pool.DruidDataSource;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UnifiedMappingService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final RegisteredDictionaryCatalog dictionaries;
    private final FieldMappingService legacy;
    private final Map<String,DruidDataSource> pools=new ConcurrentHashMap<>();
    private MappingVersionStore versions;
    private final DictionaryPolicyProvider.Cache cache=new DictionaryPolicyProvider.Cache();
    private RegisteredMappingSchemaService schemas;
    @org.springframework.beans.factory.annotation.Autowired public void setSchemas(@org.springframework.context.annotation.Lazy RegisteredMappingSchemaService schemas){this.schemas=schemas;}
    @org.springframework.beans.factory.annotation.Autowired public void setVersions(MappingVersionStore versions){this.versions=versions;}
    public UnifiedMappingService(RegisteredDictionaryCatalog dictionaries,FieldMappingService legacy){this.dictionaries=dictionaries;this.legacy=legacy;}
    public record Compiled(MappingEngine.Plan plan,Map<String,Map<String,Object>> connections,JsonNode snapshots) {
        public Compiled(MappingEngine.Plan plan,Map<String,Map<String,Object>> connections){this(plan,connections,JSON.createObjectNode());}
    }
    public Compiled compile(JsonNode raw){return compileBatch(Collections.singletonMap("node",raw)).get("node");}
    public Map<String,Compiled> compileBatch(Map<String,JsonNode> specifications){
        if(specifications.isEmpty())return Map.of();if(specifications.size()>128)throw new MappingException("BUDGET_EXCEEDED","映射节点数量超过128");
        Map<String,ObjectNode> drafts=new LinkedHashMap<>();ObjectNode batch=JSON.createObjectNode().put("schemaVersion","2.0");ArrayNode batchRules=batch.putArray("rules");ObjectNode batchDefinitions=batch.putObject("dictionaryBindings");
        for(var entry:specifications.entrySet()){
            JsonNode raw=entry.getValue();ObjectNode draft=raw!=null&&raw.path("schemaVersion").asText().equals("2.0")?(ObjectNode)raw.deepCopy():MappingEngine.legacy(legacy.normalizeSpec(raw));
            Map<String,String> aliases=new HashMap<>();draft.path("dictionaryBindings").fieldNames().forEachRemaining(id->aliases.put(id,entry.getKey()+":"+id));renameDictionaryReferences(draft,aliases);
            draft.path("dictionaryBindings").fields().forEachRemaining(definition->batchDefinitions.set(aliases.get(definition.getKey()),definition.getValue()));
            for(JsonNode rule:draft.path("rules")){ObjectNode owned=rule.deepCopy();owned.put("__mappingNode",entry.getKey());batchRules.add(owned);}drafts.put(entry.getKey(),draft);
        }
        var bound=dictionaries.bind(batch);Map<String,Map<String,Object>>connections=new LinkedHashMap<>();bound.connections().forEach((id,values)->{Map<String,Object>cfg=new LinkedHashMap<>(values);if(!cfg.containsKey("jdbcUrl")&&cfg.containsKey("jdbcURL"))cfg.put("jdbcUrl",cfg.get("jdbcURL"));connections.put(id,cfg);});
        ObjectNode resolved=bound.specification(),snapshots=JSON.createObjectNode();Set<String>snapshotIds=new LinkedHashSet<>();
        resolved.path("dictionaryBindings").forEach(definition->{String mode=definition.path("freshnessMode").asText("LIVE_BATCH");if(!Set.of("LIVE_BATCH","TTL_BATCH_CACHE","VERSIONED_SNAPSHOT").contains(mode))throw new MappingException("INVALID_DICTIONARY","字典新鲜度模式不支持");if(mode.equals("TTL_BATCH_CACHE")&&(definition.path("ttlSeconds").asInt()<1||definition.path("ttlSeconds").asInt()>600))throw new MappingException("INVALID_DICTIONARY","TTL须在1至600秒之间");if(mode.equals("VERSIONED_SNAPSHOT"))snapshotIds.add(definition.path("snapshotId").asText());});
        Map<String,JsonNode> resources=snapshotIds.isEmpty()?Map.of():versions.resources(snapshotIds,"DICTIONARY_SNAPSHOT");
        resolved.path("dictionaryBindings").forEach(node->{ObjectNode definition=(ObjectNode)node;ObjectNode identity=definition.deepCopy();identity.remove(List.of("id","freshnessMode","snapshotId","snapshotHash","definitionHash","ttlSeconds"));String hash=MappingEngine.hash(identity);definition.put("definitionHash",hash);
            if(definition.path("freshnessMode").asText().equals("VERSIONED_SNAPSHOT")){String id=definition.path("snapshotId").asText();JsonNode resource=resources.get(id);if(resource==null||!resource.path("content").path("definitionHash").asText().equals(hash))throw new MappingException("RESOURCE_FORBIDDEN","快照不属于当前授权字典定义");definition.put("snapshotHash",resource.path("hash").asText());snapshots.set(id,resource.path("content"));}
        });
        Map<String,ArrayNode> byNode=new HashMap<>();for(JsonNode rule:resolved.path("rules")){ObjectNode local=rule.deepCopy();String node=local.path("__mappingNode").asText();local.remove("__mappingNode");byNode.computeIfAbsent(node,ignored->JSON.createArrayNode()).add(local);}
        Map<String,Compiled> compiled=new LinkedHashMap<>();Map<String,JsonNode>contracts=new LinkedHashMap<>();
        drafts.forEach((node,draft)->contracts.put(node,draft.path("schemaContract")));
        if(schemas!=null)schemas.checkContracts(contracts);
        for(var entry:drafts.entrySet()){
            ObjectNode draft=entry.getValue();ArrayNode rules=byNode.getOrDefault(entry.getKey(),JSON.createArrayNode());draft.set("rules",rules);Set<String> used=new HashSet<>();dictionaryReferences(rules,used);ObjectNode bindings=JSON.createObjectNode();
            resolved.path("dictionaryBindings").fields().forEachRemaining(binding->{if(used.contains(binding.getKey())||specifications.size()==1&&rules.isEmpty())bindings.set(binding.getKey(),binding.getValue());});draft.set("dictionaryBindings",bindings);
            applyRegisteredTemporalInputs(draft);
            var plan=MappingEngine.compile(draft);Map<String,Map<String,Object>>localConnections=new LinkedHashMap<>();plan.dictionaries().values().forEach(dictionary->localConnections.put(dictionary.datasourceId(),connections.get(dictionary.datasourceId())));ObjectNode localSnapshots=JSON.createObjectNode();bindings.forEach(binding->{String id=binding.path("snapshotId").asText();if(snapshots.has(id))localSnapshots.set(id,snapshots.get(id));});compiled.put(entry.getKey(),new Compiled(plan,localConnections,localSnapshots));contracts.put(entry.getKey(),plan.document().path("schemaContract"));
        }
        return compiled;
    }
    private static final java.util.regex.Pattern REGISTERED_TEMPORAL_CAST=java.util.regex.Pattern.compile(
            "CAST\\(NULLIF\\(TRIM\\(CAST\\(\\$\\{field:([^}]+)}ASVARCHAR\\)\\),''\\)AS(?:DATE|TIMESTAMP)\\)",java.util.regex.Pattern.CASE_INSENSITIVE);
    /** Only the generator's exact legacy date wrapper is replaced; custom expressions stay intact. */
    static void applyRegisteredTemporalInputs(ObjectNode draft) {
        Map<String,String> formats=new HashMap<>();for(JsonNode field:draft.path("schemaContract").path("source")) {
            String format=field.path("registeredFormat").asText();if(Set.of("DATE","DATETIME").contains(format))formats.put(field.path("name").asText(),format);
        }
        if(formats.isEmpty())return;
        for(JsonNode rule:draft.path("rules")) {
            JsonNode input=rule.path("input");if(!input.path("kind").asText().equals("EXPRESSION"))continue;
            String expression=input.path("expression").asText();if(expression.length()>4000)continue;
            var match=REGISTERED_TEMPORAL_CAST.matcher(expression.replaceAll("\\s+",""));if(!match.matches())continue;
            String name=ValueTypes.field(match.group(1)),format=formats.get(name);if(format==null)continue;
            ObjectNode object=(ObjectNode)rule;object.putObject("input").put("kind","FIELD").put("name",name);
            ArrayNode steps=JSON.createArrayNode();String prefix=rule.path("ruleId").asText()+"-registered";
            steps.addObject().put("stepId",prefix+"-empty").put("operator","FUNCTION").putObject("args").put("function","blankToNull").putArray("parameters");
            steps.addObject().put("stepId",prefix+"-date").put("operator","DATE_PARSE").putObject("args").put("pattern","AUTO").put("logicalType",format);
            rule.path("steps").forEach(steps::add);object.set("steps",steps);
        }
    }
    private static void renameDictionaryReferences(JsonNode node,Map<String,String> aliases){if(node.isArray()){node.forEach(child->renameDictionaryReferences(child,aliases));return;}if(!node.isObject())return;ObjectNode object=(ObjectNode)node;String id=object.path("dictionaryId").asText();if(aliases.containsKey(id))object.put("dictionaryId",aliases.get(id));JsonNode reference=object.path("definitionRef");if(reference.isObject()&&aliases.containsKey(reference.path("id").asText()))((ObjectNode)reference).put("id",aliases.get(reference.path("id").asText()));object.fields().forEachRemaining(entry->{if(!entry.getKey().equals("dictionaryBindings"))renameDictionaryReferences(entry.getValue(),aliases);});}
    private static void dictionaryReferences(JsonNode node,Set<String> ids){if(node.isArray()){node.forEach(child->dictionaryReferences(child,ids));return;}if(!node.isObject())return;if(node.hasNonNull("dictionaryId"))ids.add(node.path("dictionaryId").asText());if(node.path("definitionRef").isObject())ids.add(node.path("definitionRef").path("id").asText());node.fields().forEachRemaining(entry->dictionaryReferences(entry.getValue(),ids));}
    public JsonNode buildSnapshot(ObjectNode definition,String name){ObjectNode draft=JSON.createObjectNode().put("schemaVersion","2.0");draft.putArray("rules");draft.putObject("dictionaryBindings").set("requested",definition);var compiled=compile(draft);var spec=compiled.plan().dictionaries().values().iterator().next();JsonNode binding=compiled.plan().document().path("dictionaryBindings").path(spec.id());try(var reader=new BatchDictionaryReader(id->pool(id,compiled.connections()).getConnection())){JsonNode payload=reader.snapshot(spec,binding.path("definitionHash").asText());return versions.saveResource("DICTIONARY_SNAPSHOT",name,binding,payload);}}
    public Map<String,Object> compileResponse(JsonNode raw){
        var plan=compile(raw).plan();Map<String,Object>result=new LinkedHashMap<>();result.put("mode","ENGINE_PLAN");result.put("query",null);result.put("finalQuery",null);result.put("lookups",List.of());result.put("executable",true);
        result.put("plan",Map.of("planVersion","1.0","engineVersion",MappingEngine.engineVersion(),"functionLibraryVersion",FunctionRegistry.libraryVersion(),"planHash",plan.hash(),"outputFields",plan.outputFields(),"outputTypes",plan.outputTypes(),"dictionaryGroupCount",plan.dictionaries().size(),"executionPlan",plan.document()));
        result.put("nifiComponentConfig",Map.of("processorType","com.linewell.dataelement.nifi.HaitongTransformRecord","planHash",plan.hash()));return result;
    }
    public FieldMappingService.PreviewResponse preview(JsonNode raw,List<Map<String,Object>> sample){
        if(sample==null)sample=List.of();if(sample.size()>100||ValueTypes.bytes(sample,0)>2_097_152)throw new MappingException("BUDGET_EXCEEDED","预览最多100行且不超过2MiB");var compiled=compile(raw);var plan=compiled.plan();JsonNode policies=plan.document().path("policies");
        ExecutionContext context=new ExecutionContext(Instant.now(),ZoneId.of(policies.path("zoneId").asText("Asia/Shanghai")),Locale.forLanguageTag(policies.path("locale").asText("zh-CN")),"preview:"+plan.hash());
        try(var reader=new BatchDictionaryReader(id->pool(id,compiled.connections()).getConnection())){
            var provider=new DictionaryPolicyProvider(reader,plan.document().path("dictionaryBindings"),compiled.snapshots(),cache,com.linewell.dataelement.platform.tenant.domain.TenantContext.requireTenantId());
            var result=MappingEngine.execute(plan,sample,0,context,provider);List<Map<String,Object>> rows=new ArrayList<>();List<FieldMappingService.RowResult>results=new ArrayList<>();int invalid=0;
            Set<String> sensitive=plan.sensitiveOutputs(PreviewMasking.sensitiveNames(plan.document().path("schemaContract").path("source")));
            for(var row:result.rows()){if(row.success())rows.add(row.output());else invalid++;results.add(new FieldMappingService.RowResult((int)row.index(),row.success(),row.errors().stream().map(e->e.code()+" / "+e.ruleId()+" / "+MappingDiagnostics.previewMessage(e,sensitive,!plan.dictionaries().isEmpty())).toList()));}
            Map<String,Object>stats=new LinkedHashMap<>();stats.put("inputRows",sample.size());stats.put("outputRows",rows.size());stats.put("successRows",rows.size());stats.put("invalidRows",invalid);stats.put("dictionarySqlCount",reader.queries());stats.put("stages",result.stages());stats.put("planHash",plan.hash());stats.put("engineVersion",MappingEngine.engineVersion());stats.put("sampleMode","PROVIDED_SAMPLE");
            stats.put("sensitiveValuesMasked",true);
            return new FieldMappingService.PreviewResponse(temporalPreviewRows(PreviewMasking.mask(rows,sensitive,!plan.dictionaries().isEmpty())),results,stats);
        }
    }
    /** Mapping precision must not be truncated by the application's global second-only JSON formatter. */
    static List<Map<String,Object>> temporalPreviewRows(List<Map<String,Object>> rows){
        var result=new ArrayList<Map<String,Object>>(rows.size());
        for(var row:rows){var output=new LinkedHashMap<String,Object>();row.forEach((name,value)->output.put(name,
                value instanceof LocalDateTime date?java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(date).replace('T',' '):
                value instanceof LocalTime time?java.time.format.DateTimeFormatter.ISO_LOCAL_TIME.format(time):
                value instanceof LocalDate||value instanceof Instant?value.toString():value));result.add(output);}
        return result;
    }
    private DruidDataSource pool(String id,Map<String,Map<String,Object>>connections){
        Map<String,Object> cfg=connections.get(id);if(cfg==null)throw new MappingException("RESOURCE_FORBIDDEN","字典连接未授权");String url=text(cfg,"jdbcURL","jdbcUrl","url");String user=text(cfg,"username","dbUser"),password=text(cfg,"password","dbPassword");
        if(url.isBlank())throw new MappingException("DICTIONARY_UNAVAILABLE","已登记字典未配置JDBC连接");String key=MappingEngine.hash(JSON.valueToTree(List.of(id,url,user,password)));
        if(!pools.containsKey(key)&&pools.size()>=32)throw new MappingException("BUDGET_EXCEEDED","字典连接池数量超过32");
        return pools.computeIfAbsent(key,k->{DruidDataSource ds=new DruidDataSource();ds.setUrl(url);ds.setUsername(user);ds.setPassword(password);String driver=text(cfg,"driverClassName","driverClass","driver_class_name");if(!driver.isBlank())ds.setDriverClassName(driver);ds.setMaxActive(4);ds.setMinIdle(0);ds.setMaxWait(10000);ds.setTestOnBorrow(false);ds.setTestWhileIdle(false);return ds;});
    }
    java.sql.Connection openRegistered(String id,Map<String,Map<String,Object>>connections)throws java.sql.SQLException{return pool(id,connections).getConnection();}
    private static String text(Map<String,Object>cfg,String...keys){for(String key:keys){Object value=cfg.get(key);if(value!=null&&!value.toString().isBlank())return value.toString();}return "";}
    public List<Map<String,Object>> capabilities(){List<Map<String,Object>>out=new ArrayList<>();for(String fn:FunctionRegistry.NAMES){String operator=switch(fn){case "uuid"->"SYSTEM_VALUE";case "codeTableLookup"->"LOOKUP";case "expression"->"EXPRESSION";default->"FUNCTION";};out.add(Map.of("name",fn,"version",FunctionRegistry.libraryVersion(),"previewSupported",true,"runtimeSupported",true,"implementation","JAVA_RECORD","operator",operator,"directFunction",operator.equals("FUNCTION"),"parameterSchema",FunctionArgumentSchema.parameters(fn),"additionalParametersAllowed",Set.of("coalesce","nvl").contains(fn)));}return out;}
    public FieldMappingService.ValidationResponse validateSchemas(JsonNode raw,List<FieldMappingService.FieldMeta>source,List<FieldMappingService.FieldMeta>target){
        return validateSchemas(compile(raw).plan(),source,target);
    }
    public FieldMappingService.ValidationResponse validateSchemas(MappingEngine.Plan plan,List<FieldMappingService.FieldMeta>source,List<FieldMappingService.FieldMeta>target){
        List<FieldMappingService.MappingIssue>errors=new ArrayList<>(),warnings=new ArrayList<>();Set<String>targets=new HashSet<>();if(target!=null)target.forEach(f->targets.add(ValueTypes.field(f.pathOrName())));
        Set<String>sources=new HashSet<>();if(source!=null)source.forEach(f->sources.add(ValueTypes.field(f.pathOrName())));
        if(!targets.isEmpty())for(String name:plan.outputFields())if(!targets.contains(name))errors.add(new FieldMappingService.MappingIssue("target",name,"目标字段不属于当前目标Schema","ERROR","TARGET_NOT_FOUND",List.of()));
        if(!sources.isEmpty())for(String name:plan.sourceFields())if(!sources.contains(name))errors.add(new FieldMappingService.MappingIssue("input",name,"来源字段不属于当前来源Schema","ERROR","MISSING_SOURCE",List.of()));
        if(target!=null)for(var field:target){String name=ValueTypes.field(field.pathOrName());if((field.pkRequired()||!field.nullableEffective())&&!field.hasDefaultEffective()&&!plan.outputFields().contains(name)&&!(plan.passthrough()&&sources.contains(name)&&!plan.excludes().contains(name)))errors.add(new FieldMappingService.MappingIssue("target",name,"目标必填/主键字段缺少赋值","ERROR","TARGET_REQUIRED",List.of()));}
        if(sources.isEmpty()||targets.isEmpty())warnings.add(new FieldMappingService.MappingIssue("schema",null,"未提供完整来源/目标Schema，当前校验只覆盖规则本身","WARNING","SCHEMA_NOT_PROVIDED",List.of()));
        return new FieldMappingService.ValidationResponse(errors.isEmpty(),errors,warnings,new FieldMappingService.SchemaCompare(source==null?List.of():source,target==null?List.of():target));
    }
    @PreDestroy public void close(){pools.values().forEach(DruidDataSource::close);pools.clear();}
}
