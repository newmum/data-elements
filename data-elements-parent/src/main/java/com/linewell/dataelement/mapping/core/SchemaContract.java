package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Schema checks concern declared fields, never values inferred from a sample. */
public final class SchemaContract {
    private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    public record Field(String name, String logicalType, boolean nullable, int length, int precision, int scale) {}
    public record Difference(String field, String code, String message) {}
    private SchemaContract() {}

    public static List<Field> read(JsonNode schema) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) return List.of();
        if (!schema.isArray() || schema.size() > 2000) throw new MappingException("INVALID_SCHEMA", "Schema须为最多2000个字段的数组");
        List<Field> fields = new ArrayList<>(); Set<String> names = new HashSet<>();
        for (JsonNode node : schema) {
            String name = ValueTypes.field(node.path("name").asText());
            if (!names.add(name)) throw new MappingException("SCHEMA_DUPLICATE_FIELD", "Schema包含重复字段：" + name);
            String type = ValueTypes.type(node.path("logicalType").asText(node.path("type").asText("ANY")));
            fields.add(new Field(name, type, node.path("nullable").asBoolean(true), node.path("length").asInt(0), node.path("precision").asInt(0), node.path("scale").asInt(0)));
        }
        return List.copyOf(fields);
    }

    public static ArrayNode document(List<Field> fields) {
        ArrayNode result = JSON.createArrayNode();
        fields.stream().sorted(Comparator.comparing(Field::name)).forEach(field -> result.add(JSON.valueToTree(field)));
        return result;
    }

    public static List<Difference> compare(List<Field> expected, List<Field> actual, boolean allowAdditional, Set<String> optional) {
        Map<String,Field> observed = new LinkedHashMap<>();
        for (Field field : actual) if (observed.put(field.name(),field)!=null) throw new MappingException("SCHEMA_DUPLICATE_FIELD","Reader包含重复字段："+field.name());
        List<Difference> result = new ArrayList<>(); Set<String> declared = new HashSet<>();
        for (Field field : expected) {
            declared.add(field.name()); Field found = observed.get(field.name());
            if (found == null) { if (!optional.contains(field.name())) result.add(new Difference(field.name(),"SCHEMA_FIELD_MISSING","已声明字段不存在")); continue; }
            if (!field.logicalType().equals("ANY") && !field.logicalType().equals(found.logicalType())) result.add(new Difference(field.name(),"SCHEMA_TYPE_CHANGED","逻辑类型发生变化"));
            if (!field.nullable() && found.nullable()) result.add(new Difference(field.name(),"SCHEMA_NULLABILITY_CHANGED","非空字段变为可空"));
            if (field.length()>0 && found.length()>0 && field.length()!=found.length()) result.add(new Difference(field.name(),"SCHEMA_LENGTH_CHANGED","字段长度发生变化"));
            if (field.precision()>0 && found.precision()>0 && (field.precision()!=found.precision() || field.scale()!=found.scale())) result.add(new Difference(field.name(),"SCHEMA_PRECISION_CHANGED","数值精度发生变化"));
        }
        if (!allowAdditional) for (String name : observed.keySet()) if (!declared.contains(name)) result.add(new Difference(name,"SCHEMA_FIELD_ADDED","Schema增加了未声明字段"));
        return List.copyOf(result);
    }

    public static void requireInput(MappingEngine.Plan plan,List<Field> actual) {
        JsonNode contract = plan.document().path("schemaContract");
        List<Field> expected = read(contract.path("source")); Set<String> optional = new HashSet<>();
        for (JsonNode rule : plan.document().path("rules")) if (rule.path("sourceOptional").asBoolean() && rule.path("input").path("kind").asText().equals("FIELD")) optional.add(rule.path("input").path("name").asText());
        if (expected.isEmpty()) return;
        if(!contract.path("enforceReaderNullability").asBoolean(false)){
            Map<String,Boolean> nullable=new HashMap<>();expected.forEach(field->nullable.put(field.name(),field.nullable()));
            actual=actual.stream().map(field->new Field(field.name(),field.logicalType(),nullable.getOrDefault(field.name(),field.nullable()),field.length(),field.precision(),field.scale())).toList();
        }
        List<Difference> differences = compare(expected,actual,contract.path("allowAdditionalSourceFields").asBoolean(true),optional);
        if (!differences.isEmpty()) throw new MappingException("SCHEMA_DRIFT", differences.getFirst().code()+"："+differences.getFirst().field());
    }
    public static void prepare(ObjectNode specification){
        JsonNode contract=specification.path("schemaContract");List<Field> target=read(contract.path("target"));Map<String,Field> targets=new HashMap<>();target.forEach(field->targets.put(field.name(),field));
        for(JsonNode rule:specification.path("rules")){if(!(rule.path("target") instanceof ObjectNode output))continue;Field physical=targets.get(output.path("name").asText());if(physical==null)continue;
            if(!output.has("logicalType")||output.path("logicalType").asText().equals("ANY"))output.put("logicalType",physical.logicalType());
            if(!output.has("nullable"))output.put("nullable",physical.nullable());
            if(physical.length()>0&&!output.has("length"))output.put("length",physical.length());
            if(physical.precision()>0){if(!output.has("precision"))output.put("precision",physical.precision());if(!output.has("scale"))output.put("scale",physical.scale());}
        }
    }
    public static void validatePlan(MappingEngine.Plan plan){
        JsonNode document=plan.document(),contract=document.path("schemaContract");Set<String> source=new HashSet<>();read(contract.path("source")).forEach(field->source.add(field.name()));Set<String> optional=new HashSet<>();for(JsonNode rule:document.path("rules"))if(rule.path("sourceOptional").asBoolean()&&rule.path("input").path("logicalType").isTextual())optional.add(rule.path("input").path("name").asText());
        if(contract.path("source").isArray())for(String name:plan.sourceFields())if(!source.contains(name)&&!optional.contains(name))throw new MappingException("SCHEMA_FIELD_MISSING","规则引用的来源字段不属于Schema："+name);
        Map<String,Field> target=new HashMap<>();read(contract.path("target")).forEach(field->target.put(field.name(),field));
        if(!contract.path("target").isArray())return;
        for(var output:plan.outputContracts().entrySet()){Field physical=target.get(output.getKey());if(physical==null)throw new MappingException("TARGET_NOT_FOUND","规则输出不属于目标Schema："+output.getKey());
            if(!physical.logicalType().equals("ANY")&&!physical.logicalType().equals(output.getValue().logicalType()))throw new MappingException("TARGET_TYPE_MISMATCH","规则输出类型与物理目标不一致："+output.getKey());
            if(!physical.nullable()&&output.getValue().nullable())throw new MappingException("TARGET_NULLABILITY_MISMATCH","物理目标非空，规则须配置非空输出："+output.getKey());
            if(physical.precision()>0&&(physical.precision()!=output.getValue().precision()||physical.scale()!=output.getValue().scale()))throw new MappingException("TARGET_PRECISION_MISMATCH","规则小数精度与物理目标不一致："+output.getKey());
        }
        for(JsonNode physical:contract.path("target")){String name=physical.path("name").asText();if(!physical.path("nullable").asBoolean(true)&&!physical.path("hasDefault").asBoolean()&&!plan.outputFields().contains(name)&&!(plan.passthrough()&&source.contains(name)&&!plan.excludes().contains(name)))throw new MappingException("TARGET_REQUIRED","非空目标字段缺少赋值："+name);}
    }
}
