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
import java.util.regex.Pattern;

@Service
public class UnifiedMappingService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final RegisteredDictionaryCatalog dictionaries;
    private final FieldMappingService legacy;
    private final Map<String,DruidDataSource> pools=new ConcurrentHashMap<>();
    private MappingVersionStore versions;
    private final DictionaryPolicyProvider.Cache cache=new DictionaryPolicyProvider.Cache();
    private RegisteredMappingSchemaService schemas;
    private com.linewell.dataelement.platform.magic.module.HiveModule serverHive;
    @org.springframework.beans.factory.annotation.Autowired public void setServerHive(com.linewell.dataelement.platform.magic.module.HiveModule serverHive){this.serverHive=serverHive;}
    @org.springframework.beans.factory.annotation.Autowired public void setSchemas(@org.springframework.context.annotation.Lazy RegisteredMappingSchemaService schemas){this.schemas=schemas;}
    @org.springframework.beans.factory.annotation.Autowired public void setVersions(MappingVersionStore versions){this.versions=versions;}
    public UnifiedMappingService(RegisteredDictionaryCatalog dictionaries,FieldMappingService legacy){this.dictionaries=dictionaries;this.legacy=legacy;}
    public record Compiled(MappingEngine.Plan plan,Map<String,Map<String,Object>> connections,JsonNode snapshots) {
        public Compiled(MappingEngine.Plan plan,Map<String,Map<String,Object>> connections){this(plan,connections,JSON.createObjectNode());}
    }
    public Compiled compile(JsonNode raw){return compileBatch(Collections.singletonMap("node",raw)).get("node");}
    public Compiled compileForEditing(JsonNode raw){return compileBatch(Collections.singletonMap("node",raw),false).get("node");}
    public Map<String,Compiled> compileBatch(Map<String,JsonNode> specifications){
        return compileBatch(specifications,true);
    }
    private Map<String,Compiled> compileBatch(Map<String,JsonNode> specifications,boolean physical){
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
        if(schemas!=null){if(physical)schemas.checkContracts(contracts);else schemas.checkRegisteredContracts(contracts);}
        for(var entry:drafts.entrySet()){
            ObjectNode draft=entry.getValue();ArrayNode rules=byNode.getOrDefault(entry.getKey(),JSON.createArrayNode());draft.set("rules",rules);Set<String> used=new HashSet<>();dictionaryReferences(rules,used);ObjectNode bindings=JSON.createObjectNode();
            resolved.path("dictionaryBindings").fields().forEachRemaining(binding->{if(used.contains(binding.getKey())||specifications.size()==1&&rules.isEmpty())bindings.set(binding.getKey(),binding.getValue());});draft.set("dictionaryBindings",bindings);
            applyFieldNamePolicy(draft);
            alignSourceNames(draft);
            alignRegisteredTargetNames(draft);
            applyRegisteredTemporalInputs(draft);
            applyOdsTimestampTargets(draft);
            normalizeTargetLengths(draft);
            applySharedDatePrecision(draft);
            applySharedIntegerBounds(draft);
            MappingEngine.Plan plan;
            try{plan=MappingEngine.compile(draft);}catch(MappingException error){throw describeTargetTypeMismatch(draft,error);}
            validateSharedTargetFields(draft,plan);
            for(JsonNode branch:draft.path("schemaContract").path("targets"))sharedTargetProjection(plan,branch.path("nodeId").asText());
            Map<String,Map<String,Object>>localConnections=new LinkedHashMap<>();plan.dictionaries().values().forEach(dictionary->localConnections.put(dictionary.datasourceId(),connections.get(dictionary.datasourceId())));ObjectNode localSnapshots=JSON.createObjectNode();bindings.forEach(binding->{String id=binding.path("snapshotId").asText();if(snapshots.has(id))localSnapshots.set(id,snapshots.get(id));});compiled.put(entry.getKey(),new Compiled(plan,localConnections,localSnapshots));contracts.put(entry.getKey(),plan.document().path("schemaContract"));
        }
        return compiled;
    }
    /** Old metadata copied timestamp storage widths into character limits. Only text/binary carry lengths. */
    static void normalizeTargetLengths(ObjectNode draft){
        Map<String,String> types=new HashMap<>();draft.path("schemaContract").path("target").forEach(field->types.put(field.path("name").asText(),field.path("logicalType").asText()));
        for(JsonNode rule:draft.path("rules"))normalizeTargetLength(rule,types);
    }
    private static void normalizeTargetLength(JsonNode rule,Map<String,String> types){
        if(rule.path("target") instanceof ObjectNode target){
            String type=ValueTypes.type(target.path("logicalType").asText(types.getOrDefault(target.path("name").asText(),"ANY")));
            if(!Set.of("STRING","BINARY","ANY").contains(type))target.remove("length");
        }
        for(String branch:List.of("then","else"))if(rule.has(branch))normalizeTargetLength(rule.path(branch),types);
    }
    /** Registered database names ignore case, including drafts saved with the former strict default. */
    static void applyFieldNamePolicy(ObjectNode draft){
        ObjectNode policies=draft.withObject("/policies");
        JsonNode contract=draft.path("schemaContract");
        if(contract.path("sourceRef").isObject()||contract.path("targetRef").isObject()||!policies.hasNonNull("caseSensitive"))
            policies.put("caseSensitive",false);
    }
    static void alignSourceNames(ObjectNode draft){
        if(draft.path("policies").path("caseSensitive").asBoolean(false))return;
        Set<String> exact=new HashSet<>();Map<String,List<String>> names=new HashMap<>();
        for(JsonNode field:draft.path("schemaContract").path("source")){
            String name=field.path("name").asText();exact.add(name);names.computeIfAbsent(name.toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(name);
        }
        alignReferences(draft.path("rules"),names,exact,"FIELD","field");
    }
    /** Resolve destination spelling from the authorized schema, including references to earlier outputs. */
    static void alignRegisteredTargetNames(ObjectNode draft){
        JsonNode contract=draft.path("schemaContract");if(!contract.path("targetRef").isObject())return;
        Map<String,List<String>> names=new HashMap<>();Set<String> exact=new HashSet<>();
        for(JsonNode field:contract.path("target")){
            String name=field.path("name").asText();exact.add(name);names.computeIfAbsent(name.toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(name);
        }
        for(JsonNode rule:draft.path("rules"))alignTarget(rule,names,exact);
        alignReferences(draft.path("rules"),names,exact,"TARGET","target");
    }
    private static String targetName(String name,Map<String,List<String>> names,Set<String> exact){
        if(exact.contains(name))return name;
        var matches=names.getOrDefault(name.toLowerCase(Locale.ROOT),List.of());
        if(matches.size()>1)throw new MappingException("AMBIGUOUS_FIELD","字段大小写存在歧义："+name);
        return matches.size()==1?matches.getFirst():name;
    }
    private static void alignReferences(JsonNode node,Map<String,List<String>> names,Set<String> exact,String kind,String reference){
        if(node.isArray()){node.forEach(child->alignReferences(child,names,exact,kind,reference));return;}
        if(!(node instanceof ObjectNode object))return;
        // Literal payloads and enum values are data, even when they resemble the rule protocol.
        if(object.path("kind").asText().equals("LITERAL")||object.path("operator").asText().equals("STATIC_MAP"))return;
        if(object.path("kind").asText().equals(kind)){
            String key=object.has("name")?"name":"field";
            object.put(key,targetName(object.path(key).asText(),names,exact));
        }
        if(object.path("expression").isTextual()){
            var matcher=java.util.regex.Pattern.compile("\\$\\{"+reference+":([^}]+)}").matcher(object.path("expression").asText());
            object.put("expression",matcher.replaceAll(match->java.util.regex.Matcher.quoteReplacement("${"+reference+":"+targetName(match.group(1),names,exact)+"}")));
        }
        object.fields().forEachRemaining(field->{
            if(!Set.of("parameters","defaultValue","fallback","allowedValues","inlineValues").contains(field.getKey()))
                alignReferences(field.getValue(),names,exact,kind,reference);
        });
    }
    private static void alignTarget(JsonNode rule,Map<String,List<String>> names,Set<String> exact){
        if(rule.path("target") instanceof ObjectNode target){
            String name=target.path("name").asText();
            target.put("name",targetName(name,names,exact));
        }
        for(String branch:List.of("then","else"))if(rule.has(branch))alignTarget(rule.path(branch),names,exact);
    }
    /** Preserve the intrinsic day-only domain when Oracle DATE storage is exposed as DATETIME. */
    static void applySharedDatePrecision(ObjectNode draft){
        JsonNode contract=draft.path("schemaContract");
        if(!contract.path("sourceRef").isObject()||contract.path("sourceRangeUnverified").asBoolean()
                ||!contract.path("targetRef").isObject()||!contract.path("targets").isArray())return;
        Map<String,ObjectNode> dates=new HashMap<>();for(JsonNode field:contract.path("source"))
            if("DATE".equals(ValueTypes.type(field.path("logicalType").asText()))&&!field.path("dateContainsTime").asBoolean()
                    &&"DATE".equalsIgnoreCase(field.path("physicalType").asText().trim())
                    &&Set.of("","DATE").contains(field.path("registeredFormat").asText())&&field instanceof ObjectNode object)dates.put(field.path("name").asText(),object);
        Map<String,ObjectNode> storageFields=new HashMap<>();for(JsonNode field:contract.path("target"))if(field instanceof ObjectNode object)storageFields.put(field.path("name").asText(),object);
        Map<String,SchemaContract.Field> primary=new HashMap<>();for(var field:SchemaContract.read(contract.path("target")))primary.put(field.name(),field);
        List<Map<String,SchemaContract.Field>> branches=new ArrayList<>();
        for(JsonNode branch:contract.path("targets")){
            if(!branch.path("reference").isObject())return;
            JsonNode fields=branch.path("target").isArray()?branch.path("target"):branch.path("fields");if(!fields.isArray())return;
            for(JsonNode field:fields)if("DATETIME".equals(field.path("registeredFormat").asText()))return;
            Map<String,SchemaContract.Field> names=new HashMap<>();for(var field:SchemaContract.read(sharedCarrierFields(branch,fields)))
                if(names.putIfAbsent(field.name().toLowerCase(Locale.ROOT),field)!=null)return;
            branches.add(names);
        }
        for(JsonNode rule:draft.path("rules")){
            if(!rule.path("enabled").asBoolean(true)||rule.hasNonNull("when")||rule.hasNonNull("defaultValue")||rule.hasNonNull("fallback")
                    ||!"ERROR".equals(rule.path("onError").asText("ERROR"))||!rule.path("steps").isEmpty()
                    ||!"FIELD".equals(rule.path("input").path("kind").asText())||!dates.containsKey(rule.path("input").path("name").asText())
                    ||!(rule.path("target") instanceof ObjectNode target)||!Set.of("DATE","DATETIME").contains(target.path("logicalType").asText()))continue;
            String name=target.path("name").asText();var storage=primary.get(name);var registered=storageFields.get(name);
            if(storage==null||!storage.logicalType().equals("DATETIME")||registered==null||registered.hasNonNull("registeredFormat"))continue;
            boolean dateReceiver=false,compatible=true;
            for(var branch:branches){var field=branch.get(name.toLowerCase(Locale.ROOT));
                if(field==null||!Set.of("DATE","DATETIME").contains(field.logicalType())){compatible=false;break;}
                dateReceiver|=field.logicalType().equals("DATE");
            }
            if(compatible&&dateReceiver){
                // Reuse the existing DATE-precision contract understood by every deployed NAR.
                // This is a derived default, not a saved user format or altered physical type.
                registered.put("registeredFormat","DATE").put("formatOrigin","NATIVE_SOURCE_DATE");
                ObjectNode source=dates.get(rule.path("input").path("name").asText());
                if(!source.hasNonNull("registeredFormat"))source.put("registeredFormat","DATE").put("formatOrigin","NATIVE_SOURCE_DATE");
                target.put("logicalType","DATE");
                ((ObjectNode)contract).put("sourceHash",MappingEngine.hash(contract.path("source")));
                ((ObjectNode)contract).put("targetHash",MappingEngine.hash(contract.path("target")));
            }
        }
    }
    /** Add an enforced common integer bound after target authorization, never a sampled-source proof. */
    static void applySharedIntegerBounds(ObjectNode draft){
        JsonNode contract=draft.path("schemaContract");
        if(!contract.path("targetRef").isObject()||!contract.path("target").isArray()
                ||!contract.path("targets").isArray()||contract.path("targets").isEmpty())return;
        List<JsonNode> arrays=new ArrayList<>();arrays.add(contract.path("target"));
        for(JsonNode branch:contract.path("targets")){
            if(!branch.path("reference").isObject())return;
            JsonNode fields=branch.path("target").isArray()?branch.path("target"):branch.path("fields");
            if(!fields.isArray())return;arrays.add(sharedCarrierFields(branch,fields));
        }
        List<Map<String,SchemaContract.Field>> fieldsByBranch=new ArrayList<>();
        List<Map<String,String>> physicalByBranch=new ArrayList<>();
        for(JsonNode fields:arrays){
            Map<String,SchemaContract.Field> names=new HashMap<>();Map<String,String> physical=new HashMap<>();
            for(var field:SchemaContract.read(fields))if(names.putIfAbsent(field.name().toLowerCase(Locale.ROOT),field)!=null)return;
            for(JsonNode field:fields)physical.put(field.path("name").asText().toLowerCase(Locale.ROOT),field.path("physicalType").asText());
            fieldsByBranch.add(names);physicalByBranch.add(physical);
        }
        for(JsonNode rule:draft.path("rules")){
            if(!rule.path("enabled").asBoolean(true)||rule.hasNonNull("when")||rule.hasNonNull("defaultValue")
                    ||!"ERROR".equals(rule.path("onError").asText("ERROR"))||!rule.path("steps").isEmpty()
                    ||!"FIELD".equals(rule.path("input").path("kind").asText())||!(rule.path("target") instanceof ObjectNode target))continue;
            if(!"DECIMAL".equals(ValueTypes.type(target.path("logicalType").asText()))||target.path("scale").asInt()!=0)continue;
            String output=target.path("name").asText().toLowerCase(Locale.ROOT);
            int width=64;boolean compatible=true,integerReceiver=false;
            for(int index=0;index<fieldsByBranch.size();index++){
                var field=fieldsByBranch.get(index).get(output);if(field==null){compatible=false;break;}
                if(Set.of("INT","LONG").contains(field.logicalType())){
                    integerReceiver=true;
                    int physical=signedIntegerWidth(physicalByBranch.get(index).get(output),field.logicalType());
                    width=Math.min(width,Math.min(field.logicalType().equals("INT")?32:64,physical));
                }
            }
            if(!compatible||!integerReceiver||width<32||target.path("precision").asInt()<(width==32?10:19))continue;
            String type=width==32?"INT":"LONG";
            var proposed=new MappingEngine.OutputType(type,0,0,target.path("nullable").asBoolean(true));
            for(int index=0;index<fieldsByBranch.size();index++)
                if(!sharedTypeAccepts(proposed,fieldsByBranch.get(index).get(output),physicalByBranch.get(index).get(output),0,false)){compatible=false;break;}
            if(compatible){
                // Keep the decimal output and physical schemas. The existing strict CAST
                // rejects fractions/overflow before either target sees a record.
                ObjectNode step=((ObjectNode)rule).withArray("steps").addObject();step.put("operator","CAST").put("stepId","source-integer-range");
                step.putObject("args").put("logicalType",type);
            }
        }
    }
    /** One shared output must have an unambiguous destination field in every authorized branch. */
    static void validateSharedTargetFields(ObjectNode draft,MappingEngine.Plan plan){
        var outputContracts=plan.outputContracts();
        var integralSources=integralSourceWidths(draft);
        var booleanSources=directBooleanOutputs(draft);
        for(JsonNode branch:draft.path("schemaContract").path("targets")){
            JsonNode fields=branch.path("target").isArray()?branch.path("target"):branch.path("fields");
            if(!fields.isArray())throw new MappingException("INVALID_SCHEMA","目标分支尚未读取字段结构："+branch.path("label").asText(branch.path("nodeId").asText()));
            Map<String,List<SchemaContract.Field>> names=new HashMap<>();
            Map<String,String> physicalTypes=new HashMap<>();
            fields.forEach(field -> physicalTypes.put(ValueTypes.field(field.path("name").asText()),field.path("physicalType").asText()));
            for(var field:SchemaContract.read(sharedCarrierFields(branch,fields)))names.computeIfAbsent(field.name().toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(field);
            for(String output:plan.outputFields()){
                var matches=names.getOrDefault(output.toLowerCase(Locale.ROOT),List.of());
                if(matches.size()!=1)throw new MappingException(matches.isEmpty()?"TARGET_NOT_FOUND":"AMBIGUOUS_FIELD",
                        "共享治理目标“"+branch.path("label").asText(branch.path("nodeId").asText())+"”"+(matches.isEmpty()?"缺少输出字段：":"字段大小写有歧义：")+output);
                var actual=matches.getFirst();var expected=outputContracts.get(output);
                String label="共享治理目标“"+branch.path("label").asText(branch.path("nodeId").asText())+"”字段 "+output;
                if(!actual.nullable()&&expected.nullable())throw new MappingException("TARGET_NULLABILITY_MISMATCH",label+"不允许空值，请为共享规则明确非空输出");
                String physical=branch.path("outputAliases").path(output).asText(actual.name());
                if(!sharedTypeAccepts(expected,actual,physicalTypes.get(physical),integralSources.getOrDefault(output,0),booleanSources.contains(output)&&branch.path("targetBooleanNumberCarrier").asBoolean()))
                    throw new MappingException("TARGET_TYPE_MISMATCH",label+"无法无损接收 "+expected.logicalType()+"("+expected.precision()+","+expected.scale()+")，目标为 "+actual.logicalType()
                            +(actual.logicalType().equals("STRING")?"(长度="+(actual.length()>0?actual.length():"不限")+")":"("+actual.precision()+","+actual.scale()+")"));
            }
            Set<String> outputs=new HashSet<>();plan.outputFields().forEach(name->outputs.add(name.toLowerCase(Locale.ROOT)));
            Set<String> source=new HashSet<>();draft.path("schemaContract").path("source").forEach(field->source.add(field.path("name").asText().toLowerCase(Locale.ROOT)));
            Set<String> excluded=new HashSet<>();plan.excludes().forEach(name->excluded.add(name.toLowerCase(Locale.ROOT)));
            for(JsonNode field:sharedCarrierFields(branch,fields)){String name=field.path("name").asText();
                if(!field.path("nullable").asBoolean(true)&&!field.path("hasDefault").asBoolean()&&!outputs.contains(name.toLowerCase(Locale.ROOT))
                        &&!(plan.passthrough()&&source.contains(name.toLowerCase(Locale.ROOT))&&!excluded.contains(name.toLowerCase(Locale.ROOT))))
                    throw new MappingException("TARGET_REQUIRED","共享治理目标“"+branch.path("label").asText()+"”非空字段缺少赋值："+name);
            }
        }
    }
    private static JsonNode sharedCarrierFields(JsonNode branch,JsonNode fields){
        if(!branch.path("outputAliases").isObject()||branch.path("outputAliases").isEmpty())return fields;
        Map<String,String> inverse=new HashMap<>();branch.path("outputAliases").fields().forEachRemaining(alias->{
            if(inverse.putIfAbsent(alias.getValue().asText(),alias.getKey())!=null)throw new MappingException("AMBIGUOUS_FIELD","共享目标投影不能覆盖同一字段");
        });
        JsonNode result=fields.deepCopy();for(JsonNode field:result){String name=field.path("name").asText();if(inverse.containsKey(name))((ObjectNode)field).put("name",inverse.get(name));}
        return result;
    }
    /** Pure identity projection: physical order/names change; governed values and ODS identities do not. */
    public static MappingEngine.Plan sharedTargetProjection(MappingEngine.Plan shared,String nodeId){
        return sharedTargetProjection(shared,nodeId,false);
    }
    /** A branch-local transport projection also copies unchanged names and every original ODS value. */
    public static MappingEngine.Plan sharedTargetProjection(MappingEngine.Plan shared,String nodeId,boolean transportProjection){
        for(JsonNode branch:shared.document().path("schemaContract").path("targets")){
            if(!branch.path("nodeId").asText().equals(nodeId)||!transportProjection&&branch.path("outputAliases").isEmpty())continue;
            if(transportProjection&&(!branch.path("reference").isObject()
                    ||!(branch.path("target").isArray()||branch.path("fields").isArray())))
                throw new MappingException("INVALID_SCHEMA","Hive二进制传输需要已校验的登记目标字段");
            ObjectNode draft=JSON.createObjectNode().put("schemaVersion","2.0");ObjectNode policies=shared.document().path("policies") instanceof ObjectNode value?value.deepCopy():JSON.createObjectNode();
            policies.put("allowUnmappedPassthrough",false).put("caseSensitive",true).put("onMissingSource","ERROR").put("recordErrorMode","FAIL_FLOWFILE");draft.set("policies",policies);
            ArrayNode rules=draft.putArray("rules");Map<String,String> inputs=new HashMap<>();
            Map<String,MappingEngine.OutputType> types=new LinkedHashMap<>(shared.outputContracts());
            if(shared.passthrough())for(var source:SchemaContract.read(shared.document().path("schemaContract").path("source")))
                if(!shared.excludes().contains(source.name()))types.putIfAbsent(source.name(),new MappingEngine.OutputType(source.logicalType(),source.precision(),source.scale(),source.nullable()));
            types.forEach((name,type)->{
                String physical=branch.path("outputAliases").path(name).asText(name).toLowerCase(Locale.ROOT);
                if(inputs.putIfAbsent(physical,name)!=null)throw new MappingException("AMBIGUOUS_FIELD","共享目标投影发生字段冲突");
            });
            JsonNode fields=branch.path("target").isArray()?branch.path("target"):branch.path("fields");
            for(JsonNode field:fields){String physical=field.path("name").asText(),input=inputs.remove(physical.toLowerCase(Locale.ROOT));if(input==null)continue;
                var type=types.get(input);ObjectNode rule=rules.addObject();rule.putObject("input").put("kind","FIELD").put("name",input);
                rule.putObject("target").put("name",physical).put("logicalType",type.logicalType()).put("precision",type.precision()).put("scale",type.scale()).put("nullable",type.nullable());
                rule.putArray("steps");
            }
            if(inputs.values().stream().anyMatch(shared.outputContracts()::containsKey))throw new MappingException("TARGET_NOT_FOUND","共享目标投影缺少输出字段");
            return MappingEngine.compile(draft);
        }
        return null;
    }
    private static boolean sharedTypeAccepts(MappingEngine.OutputType output,SchemaContract.Field target,
                                              String physicalType,int sourceWidth,boolean booleanSource){
        String source=output.logicalType(),type=target.logicalType();if(type.equals("ANY"))return true;
        // A verified native boolean mapped directly to NUMBER(1) has exactly {0,1}.
        // The registered Hive reader restores those two numeric values to BOOLEAN.
        if(type.equals("BOOLEAN")&&source.equals("DECIMAL")&&output.precision()==1&&output.scale()==0&&booleanSource)return true;
        // Hive STRING is unbounded text. Its record writer renders numeric values without
        // changing the shared numeric output used by the JDBC branch. Bounded text still
        // needs room for every declared value, including sign, decimal point and leading zero.
        if(type.equals("STRING")&&Set.of("INT","LONG","DECIMAL","DOUBLE").contains(source)){
            if(target.length()<=0)return true;
            int characters=switch(source){
                case "INT" -> 11;
                case "LONG" -> 20;
                case "DECIMAL" -> output.precision()>0
                        ? Math.max(output.precision()+Math.max(0,-output.scale()),output.scale()+1)+(output.scale()>0?2:1) : 0;
                default -> 0;
            };
            return characters>0&&target.length()>=characters;
        }
        if(type.equals("DECIMAL")){
            int precision=source.equals("INT")?10:source.equals("LONG")?19:output.precision();
            int scale=Set.of("INT","LONG").contains(source)?0:output.scale();
            return Set.of("INT","LONG","DECIMAL").contains(source)&&(target.precision()==0
                    ||precision>0&&target.scale()>=scale&&target.precision()-target.scale()>=precision-scale);
        }
        if(source.equals("DECIMAL") && Set.of("INT","LONG").contains(type)){
            // A zero-scale, bounded decimal fits only when every value of the declared precision
            // can stay integral in the destination. Do not round or alter the shared output.
            int width = Math.min(type.equals("INT") ? 32 : 64,signedIntegerWidth(physicalType,type));
            int digits = switch(width){
                case 8 -> 2;
                case 16 -> 4;
                case 24 -> 6;
                case 32 -> 9;
                case 64 -> 18;
                default -> 0;
            };
            return output.scale()==0 && output.precision()>0
                    && (output.precision()<=digits || sourceWidth>0 && sourceWidth<=width);
        }
        return source.equals(type)||source.equals("DATE")&&type.equals("DATETIME")
                ||source.equals("INT")&&Set.of("LONG","DOUBLE").contains(type)
                ||type.equals("STRING")&&Set.of("DATE","DATETIME","TIME","INSTANT").contains(source);
    }

    private static int signedIntegerWidth(String physicalType,String absentType){
        String type = physicalType == null || physicalType.isBlank() ? absentType : physicalType.trim().toUpperCase(Locale.ROOT);
        // MySQL display width does not change the signed integer range.
        type = type.replaceFirst("\\(\\s*\\d+\\s*\\)$","").trim();
        return switch(type){
            case "TINYINT" -> 8;
            case "SMALLINT", "INT2" -> 16;
            case "MEDIUMINT" -> 24;
            case "INT", "INTEGER", "INT4" -> 32;
            case "LONG", "BIGINT", "INT8" -> 64;
            default -> 0;  // Includes unsigned and unknown physical types.
        };
    }

    private static final Pattern DIRECT_INTEGER_CAST = Pattern.compile(
            "^CAST\\s*\\(\\s*\\$\\{field:([^}]+)}\\s+AS\\s+(INT|INTEGER|BIGINT)\\s*\\)$", Pattern.CASE_INSENSITIVE);

    /** Only an unchanged, authenticated BOOLEAN input proves the numeric 0/1 domain. */
    private static Set<String> directBooleanOutputs(ObjectNode draft){
        JsonNode contract=draft.path("schemaContract");
        if(!contract.path("sourceRef").isObject()||contract.path("sourceRangeUnverified").asBoolean())return Set.of();
        Set<String> sources=new HashSet<>(),outputs=new HashSet<>();
        for(JsonNode field:contract.path("source"))if("BOOLEAN".equals(ValueTypes.type(field.path("logicalType").asText())))sources.add(field.path("name").asText());
        for(JsonNode rule:draft.path("rules")){
            if(!rule.path("enabled").asBoolean(true)||rule.hasNonNull("when")||rule.hasNonNull("defaultValue")
                    ||!"ERROR".equals(rule.path("onError").asText("ERROR"))||!rule.path("steps").isEmpty())continue;
            JsonNode input=rule.path("input");
            if("FIELD".equals(input.path("kind").asText())&&sources.contains(input.path("name").asText(input.path("field").asText())))outputs.add(rule.path("target").path("name").asText());
        }
        return outputs;
    }

    /** A direct field or identity integer cast can preserve a stricter registered source range. */
    private static Map<String,Integer> integralSourceWidths(ObjectNode draft){
        JsonNode contract=draft.path("schemaContract");
        boolean verified=contract.path("sourceRef").isObject()&&!contract.path("sourceRangeUnverified").asBoolean();
        Map<String,Integer> sources=new HashMap<>();
        if(verified)for(JsonNode field:contract.path("source")){
            if(!Set.of("INT","LONG").contains(ValueTypes.type(field.path("logicalType").asText())))continue;
            String physical=field.path("physicalType").asText().trim().toUpperCase(Locale.ROOT);
            boolean unsigned=physical.endsWith(" UNSIGNED");
            int width=signedIntegerWidth(unsigned ? physical.substring(0,physical.length()-9).trim() : physical,"");
            if(width>0)sources.put(field.path("name").asText(),unsigned ? width+1 : width);
        }
        Map<String,Integer> result=new HashMap<>();
        for(JsonNode rule:draft.path("rules")){
            if(!rule.path("enabled").asBoolean(true) || rule.hasNonNull("when") || rule.hasNonNull("defaultValue")
                    || !"ERROR".equals(rule.path("onError").asText("ERROR")))continue;
            JsonNode steps=rule.path("steps");
            if(steps.isArray()&&steps.size()==1&&"CAST".equals(steps.get(0).path("operator").asText())){
                String type=ValueTypes.type(steps.get(0).path("args").path("logicalType").asText("STRING"));
                if(Set.of("INT","LONG").contains(type))result.put(rule.path("target").path("name").asText(),type.equals("INT")?32:64);
                continue;
            }
            if(!steps.isEmpty())continue;
            JsonNode input=rule.path("input");
            String name;
            int castWidth=64;
            if("FIELD".equals(input.path("kind").asText())){
                name=input.path("name").asText(input.path("field").asText());
            } else if("EXPRESSION".equals(input.path("kind").asText())){
                var match=DIRECT_INTEGER_CAST.matcher(input.path("expression").asText().trim());
                if(!match.matches())continue;
                name=match.group(1);
                if(!match.group(2).equalsIgnoreCase("BIGINT"))castWidth=32;
            } else continue;
            int width=sources.getOrDefault(name,0);
            if(width>0 && width<=castWidth)result.put(rule.path("target").path("name").asText(),width);
        }
        return result;
    }
    /** Old materialization templates called timestamp audit columns DATE. Repair only their
     * untouched clock generators, after the existing batch schema/authorization check above.
     * The emitted plan is ordinary DATETIME and runs on the existing NiFi mapping engine. */
    static void applyOdsTimestampTargets(ObjectNode draft) {
        Map<String,JsonNode> targets=new HashMap<>();
        draft.path("schemaContract").path("target").forEach(field->targets.put(field.path("name").asText(),field));
        for(JsonNode rule:draft.path("rules")) {
            JsonNode output=rule.path("target");String name=output.path("name").asText();JsonNode physical=targets.get(name);
            if(!(output instanceof ObjectNode target)||physical==null
                    ||!Set.of("ODS_RKSJ","ODS_GXSJ").contains(name.toUpperCase(Locale.ROOT))
                    ||!ValueTypes.type(output.path("logicalType").asText()).equals("DATE")
                    ||!ValueTypes.type(physical.path("logicalType").asText()).equals("DATETIME")
                    ||physical.path("registeredFormat").asText().equals("DATE")
                    ||rule.hasNonNull("when")||!rule.path("steps").isArray()||!rule.path("steps").isEmpty())continue;
            JsonNode input=rule.path("input");String kind=input.path("kind").asText();
            boolean clock=kind.equals("SYSTEM")&&input.path("generator").asText().equals("NOW")
                    ||kind.equals("EXPRESSION")&&Set.of("LOCALTIMESTAMP","CURRENT_TIMESTAMP").contains(input.path("expression").asText().trim().toUpperCase(Locale.ROOT));
            if(clock)target.put("logicalType","DATETIME");
        }
    }
    private static MappingException describeTargetTypeMismatch(ObjectNode draft,MappingException error) {
        if(!error.code().equals("TARGET_TYPE_MISMATCH"))return error;
        for(JsonNode rule:draft.path("rules")) {
            String name=rule.path("target").path("name").asText();
            if(name.isBlank()||!error.getMessage().endsWith("："+name))continue;
            for(JsonNode physical:draft.path("schemaContract").path("target"))if(physical.path("name").asText().equals(name)) {
                String expected=ValueTypes.type(physical.path("logicalType").asText());
                return new MappingException(error.code(),error.getMessage()+"（规则输出："
                        +ValueTypes.type(rule.path("target").path("logicalType").asText())+"；目标物理列："
                        +physical.path("physicalType").asText(expected)+"，逻辑类型："+expected
                        +"）。请核对规则输出类型；需要转换时请显式配置转换步骤。",error);
            }
        }
        return error;
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
            String name=new SourceFieldNames(formats.keySet(),false).resolve(ValueTypes.field(match.group(1)));
            String format=formats.get(name);if(format==null)continue;
            ObjectNode object=(ObjectNode)rule;object.putObject("input").put("kind","FIELD").put("name",name);
            ArrayNode steps=JSON.createArrayNode();String prefix=rule.path("ruleId").asText()+"-registered";
            steps.addObject().put("stepId",prefix+"-empty").put("operator","FUNCTION").putObject("args").put("function","blankToNull").putArray("parameters");
            steps.addObject().put("stepId",prefix+"-date").put("operator","DATE_PARSE").putObject("args").put("pattern","AUTO").put("logicalType",format);
            rule.path("steps").forEach(steps::add);object.set("steps",steps);
        }
    }
    private static void renameDictionaryReferences(JsonNode node,Map<String,String> aliases){if(node.isArray()){node.forEach(child->renameDictionaryReferences(child,aliases));return;}if(!node.isObject())return;ObjectNode object=(ObjectNode)node;String id=object.path("dictionaryId").asText();if(aliases.containsKey(id))object.put("dictionaryId",aliases.get(id));JsonNode reference=object.path("definitionRef");if(reference.isObject()&&aliases.containsKey(reference.path("id").asText()))((ObjectNode)reference).put("id",aliases.get(reference.path("id").asText()));object.fields().forEachRemaining(entry->{if(!entry.getKey().equals("dictionaryBindings"))renameDictionaryReferences(entry.getValue(),aliases);});}
    private static void dictionaryReferences(JsonNode node,Set<String> ids){if(node.isArray()){node.forEach(child->dictionaryReferences(child,ids));return;}if(!node.isObject())return;if(node.hasNonNull("dictionaryId"))ids.add(node.path("dictionaryId").asText());if(node.path("definitionRef").isObject())ids.add(node.path("definitionRef").path("id").asText());node.fields().forEachRemaining(entry->dictionaryReferences(entry.getValue(),ids));}
    /** Read an already-authorized dictionary through the mapping JDBC runtime. */
    public JsonNode readSnapshot(Compiled compiled, String dictionaryId) {
        var specification = compiled.plan().dictionaries().get(dictionaryId);
        var definition = compiled.plan().document().path("dictionaryBindings").path(dictionaryId);
        try (var reader = new BatchDictionaryReader(id -> pool(id, compiled.connections()).getConnection())) {
            return reader.snapshot(specification, definition.path("definitionHash").asText());
        }
    }
    public FieldMappingService.PreviewResponse preview(JsonNode raw,List<Map<String,Object>> sample){
        if(sample==null)sample=List.of();if(sample.size()>100)throw new MappingException("BUDGET_EXCEEDED","预览最多100行");RecordBudget.window(sample);var compiled=compileForEditing(raw);var plan=compiled.plan();JsonNode policies=plan.document().path("policies");
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
        Map<String,Object> cfg=connections.get(id);if(cfg==null)throw new MappingException("RESOURCE_FORBIDDEN","字典连接未授权");String url=registeredJdbcUrl(cfg);String user=text(cfg,"username","dbUser"),password=text(cfg,"password","dbPassword");
        if(url.isBlank())throw new MappingException("DICTIONARY_UNAVAILABLE","已登记字典未配置JDBC连接");
        String driver=text(cfg,"driverClassName","driverClass","driver_class_name");
        String family=text(cfg,"dbType","databaseType").toUpperCase(Locale.ROOT).replaceAll("[\\s_-]","");
        var gauss=Set.of("GAUSSDB","OPENGAUSS").contains(family)
                ?com.linewell.dataelement.metautil.jdbc.GaussDbVariant.from(cfg):null;
        if(gauss!=null)gauss.validateUrl(url);
        String hiveMode=text(cfg,"hiveConnectionMode","hive_connection_mode").trim().toLowerCase(Locale.ROOT);
        Map<String,String> jdbcProperties=com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver.resolve(cfg);
        // A changed authentication/client profile must never reuse a prior physical Hive session.
        String key=MappingEngine.hash(JSON.valueToTree(List.of(id,url,user,password,driver,hiveMode,jdbcProperties,gauss==null?"":gauss.code(),
                text(cfg,"authMode","auth_mode"),text(cfg,"userPrincipal","principal"),text(cfg,"keytabPath","keytab"),
                text(cfg,"krb5ConfPath"),text(cfg,"jaasConfPath"),text(cfg,"clientConfigDir"))));
        if(!pools.containsKey(key)&&pools.size()>=32)throw new MappingException("BUDGET_EXCEEDED","字典连接池数量超过32");
        return pools.computeIfAbsent(key,k->{
            DruidDataSource ds=new DruidDataSource();ds.setUrl(url);ds.setUsername(user);ds.setPassword(password);
            if(gauss!=null) {
                // Druid cannot resolve a vendor class that the metadata connector loaded externally.
                // Bind the selected product's actual driver instead of guessing from the shared URL prefix.
                ds.setDriver(com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer.loadGaussDriver(gauss.code()));
            } else if(hiveMode.equals("open-source")&&(url.startsWith("jdbc:hive2:")||url.startsWith("jdbc:hive:"))) {
                // Reuse the metadata connector's isolated Hive/Hadoop client, not the same-name MRS class.
                // Its UGI belongs to that client loader; no application-wide Hadoop authentication is changed.
                ds.setDriver(com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer.loadOpenSourceHiveDriver());
            } else if(!driver.isBlank())ds.setDriverClassName(driver);
            else if(family.equals("VERTICA")) {
                // Druid 1.2.20 does not infer Vertica's driver from its JDBC URL.
                ds.setDriverClassName(com.linewell.dataelement.metautil.model.enums.DatabaseType.VERTICA.getDriverClass());
            }
            Properties properties=new Properties();jdbcProperties.forEach(properties::setProperty);ds.setConnectProperties(properties);
            // Hive's isolated client cold-connects in roughly 30 seconds; retain a bounded first-borrow budget.
            boolean hive=url.startsWith("jdbc:hive2:")||url.startsWith("jdbc:hive:");
            ds.setMaxActive(4);ds.setMinIdle(0);ds.setMaxWait(hive?45000:10000);ds.setTestOnBorrow(false);ds.setTestWhileIdle(false);
            ds.setConnectionErrorRetryAttempts(0);ds.setBreakAfterAcquireFailure(true);ds.setFailFast(true);
            return ds;
        });
    }
    /** Assemble host-mode MySQL/SQL Server/Vertica registrations using the metadata connector's URL rules.
     * Explicit JDBC URLs and every other product retain their existing behavior. No registry write or source query. */
    static String registeredJdbcUrl(Map<String,Object> cfg) {
        String explicit=text(cfg,"jdbcURL","jdbcUrl","url");if(!explicit.isBlank())return explicit;
        com.linewell.dataelement.metautil.model.enums.DatabaseType type;
        try{type=com.linewell.dataelement.metautil.model.enums.DatabaseType.fromCode(text(cfg,"dbType","databaseType"));}
        catch(IllegalArgumentException unknown){return "";}
        if(type!=com.linewell.dataelement.metautil.model.enums.DatabaseType.SQLSERVER
                &&type!=com.linewell.dataelement.metautil.model.enums.DatabaseType.MYSQL
                &&type!=com.linewell.dataelement.metautil.model.enums.DatabaseType.VERTICA)return "";
        String host=text(cfg,"host","dbMetaIp","dbIp","hostname"),database=text(cfg,"database","dbMetaDbName","dbName");
        if(host.isBlank()||database.isBlank())return "";
        String product=type.getDisplayName();
        if(!host.matches("[A-Za-z0-9_.:\\[\\]-]+")||database.matches("(?s).*[;{}\\r\\n].*")
                ||(type==com.linewell.dataelement.metautil.model.enums.DatabaseType.MYSQL
                   ||type==com.linewell.dataelement.metautil.model.enums.DatabaseType.VERTICA)&&database.matches("(?s).*[/?#\\\\].*"))
            throw new MappingException("DICTIONARY_UNAVAILABLE",product+"主机或数据库名称不适用于主机模式，请配置明确的JDBC连接地址");
        var connection=new com.linewell.dataelement.metautil.model.dto.DataSourceConfig();connection.setDatabaseType(type);
        connection.setHost(host);connection.setDatabase(database);connection.setConnectorProperties(cfg);
        connection.setExtraParams(text(cfg,"extraParams","extra_params"));
        String port=text(cfg,"port","dbMetaPort","dbPort");
        if(!port.isBlank()){
            try{int number=Integer.parseInt(port);if(number<1||number>65535)throw new NumberFormatException();connection.setPort(number);}
            catch(NumberFormatException invalid){throw new MappingException("DICTIONARY_UNAVAILABLE",product+"登记端口须在1至65535之间");}
        }
        try{return connection.buildJdbcUrl();}
        catch(IllegalArgumentException invalid){throw new MappingException("DICTIONARY_UNAVAILABLE",product+"登记连接参数无效，请核对连接设置或明确的JDBC连接地址");}
    }
    java.sql.Connection openRegistered(String id,Map<String,Map<String,Object>>connections)throws java.sql.SQLException{
        var config=connections.get(id);
        if(config==null)throw new MappingException("RESOURCE_FORBIDDEN","连接未授权");
        String type=text(config,"dbType","databaseType").toUpperCase(Locale.ROOT).replaceAll("[\\s_-]","");
        String mode=text(config,"metadataAccessMode","metadata_access_mode"),hiveMode=text(config,"hiveConnectionMode","hive_connection_mode");
        boolean managed=Set.of("HIVE","MRSHIVE").contains(type)&&!mode.equalsIgnoreCase("jdbc")&&!hiveMode.equalsIgnoreCase("open-source")
                &&(mode.equalsIgnoreCase("server-managed-mrs")||hiveMode.equalsIgnoreCase("huawei-mrs")||!text(config,"hiveProfile","hive_profile").isBlank());
        if(!managed)return pool(id,connections).getConnection();
        if(serverHive==null)throw new MappingException("SCHEMA_UNAVAILABLE","服务器托管的华为 MRS Hive 连接未配置");
        String profile=text(config,"hiveProfile","hive_profile");
        if(!profile.isBlank()&&!profile.equalsIgnoreCase("default"))throw new MappingException("SCHEMA_UNAVAILABLE","服务器未配置所选华为 MRS Hive 配置集");
        try{return serverHive.getConnection();}
        catch(java.io.IOException|ClassNotFoundException error){
            throw new MappingException("SCHEMA_UNAVAILABLE","华为 MRS Hive 托管客户端不可用，请检查服务器 Hive 配置目录、驱动和认证文件");
        }
    }
    private static String text(Map<String,Object>cfg,String...keys){for(String key:keys){Object value=cfg.get(key);if(value!=null&&!value.toString().isBlank())return value.toString();}return "";}
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
