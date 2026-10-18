package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Row values belong to the controlled error content, never exceptions, attributes or logs. */
public final class MappingDiagnostics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int VALUE_LIMIT = 512;
    public record FieldValue(String name, String comment, Object value, boolean missing, boolean truncated) {}
    private record Value(Object value, boolean truncated) {}
    static final class Catalog {
        final Map<String,String> sourceComments = new HashMap<>(), targetComments = new HashMap<>();
        final Map<String,JsonNode> rules = new HashMap<>();
        Catalog(JsonNode document) {
            for (JsonNode field : document.path("schemaContract").path("source")) sourceComments.put(field.path("name").asText(), comment(field));
            for (JsonNode field : document.path("schemaContract").path("target")) targetComments.put(field.path("name").asText(), comment(field));
            for (JsonNode rule : document.path("rules")) rules.put(rule.path("ruleId").asText(), rule);
        }
    }
    private MappingDiagnostics() {}

    static MappingEngine.Issue issue(Catalog catalog, String mode, long index, String ruleId, String stepId, JsonNode executedStep,
            String target, String type, int length, int precision, int scale, boolean nullable,
            Map<String,Object> sources, Object input, Object problem, MappingException error) {
        JsonNode rule = catalog.rules.getOrDefault(ruleId, JSON.createObjectNode());
        JsonNode step = executedStep == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : executedStep;
        String operator = stepId.equals("target") ? "TARGET_VALIDATION" : stepId.equals("condition") ? "CONDITION" : step.path("operator").asText("INPUT");
        List<FieldValue> fields = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            if (fields.size() == 32) break;
            Value snapshot = snapshot(entry.getValue());
            String comment = catalog.sourceComments.getOrDefault(entry.getKey(), "");
            if (comment.isBlank() && rule.path("input").path("name").asText().equals(entry.getKey())) comment = comment(rule.path("input"));
            fields.add(new FieldValue(entry.getKey(), comment, snapshot.value(), entry.getValue() == ValueTypes.MISSING, snapshot.truncated()));
        }
        String targetComment = catalog.targetComments.getOrDefault(target, "");
        if (targetComment.isBlank()) targetComment = comment(rule.path("target"));
        String expected = type + (type.equals("DECIMAL") ? precision == 0 ? "（保留原精度）" : "(" + precision + "," + scale + ")" : "")
                + (length > 0 ? "，最大长度=" + length + "字符" : "") + (nullable ? "，允许NULL" : "，不允许NULL");
        if (!step.isMissingNode() && !step.isEmpty()) expected += "；步骤要求：" + stepExpectation(step);
        String effect = mode.equals("ROUTE_INVALID") ? "本条记录转入 invalid，不写入 success；其他有效记录继续处理"
                : "整批输入转入 failure 并保留原文，本批不输出 success";
        Value original = snapshot(input), failed = snapshot(problem);
        String message = describe(index, ruleId, stepId, operator, fields, target, targetComment, original.value(), failed.value(), expected, error.getMessage(), effect, null, false);
        return new MappingEngine.Issue(error.code(), ruleId, stepId, message, List.copyOf(fields), target, targetComment,
                operator, original.value(), failed.value(), original.truncated() || failed.truncated(), expected, error.getMessage(), effect, index);
    }

    public static String previewMessage(MappingEngine.Issue issue, Set<String> sensitive, boolean maskAll) {
        return describe(issue.recordIndex(), issue.ruleId(), issue.stepId(), issue.operator(), issue.sourceFields(), issue.targetField(), issue.targetComment(),
                issue.inputValue(), issue.problemValue(), issue.expected(), issue.reason(), issue.effect(), sensitive, maskAll);
    }
    public static String summary(MappingEngine.Issue issue) { return previewMessage(issue, Set.of(), true); }

    private static String describe(long index, String rule, String step, String operator, List<FieldValue> sources,
            String target, String targetComment, Object input, Object problem, String expected, String reason, String effect,
            Set<String> sensitive, boolean maskAll) {
        boolean masked = sensitive != null && (maskAll || sensitive.contains(target) || PreviewMasking.isSensitiveName(target) || PreviewMasking.isSensitiveName(targetComment)
                || sources.stream().anyMatch(field -> sensitive.contains(field.name()) || PreviewMasking.isSensitiveName(field.name()) || PreviewMasking.isSensitiveName(field.comment())));
        List<String> origin = new ArrayList<>();
        for (FieldValue field : sources) {
            if (origin.size() == 8) { origin.add("其余来源见 sourceFields"); break; }
            origin.add(label(field.name(), field.comment()) + "=" + (masked ? "***" : field.missing() ? "（字段缺失）" : display(field.value())));
        }
        String row = index == Long.MAX_VALUE ? Long.toString(index) : Long.toString(index + 1);
        return "第" + row + "条记录（inputRecordIndex=" + index + "），规则=" + rule + "；来源字段：" + (origin.isEmpty() ? "无直接来源字段（常量、系统值或目标引用）" : String.join("，", origin))
                + "；目标字段：" + label(target, targetComment) + "；规则输入值=" + (masked ? "***" : display(input))
                + "；步骤=" + step + "（" + operatorName(operator) + "）；问题值=" + (masked ? "***" : display(problem))
                + "；要求：" + expected + "；原因：" + reason + "；结果：" + effect;
    }
    private static String label(String name, String comment) { return name + "（" + (comment.isBlank() ? "未登记中文注释" : comment) + "）"; }
    private static String comment(JsonNode field) {
        for (String key : List.of("comment", "columnComment", "label", "description")) if (!field.path(key).asText().isBlank()) { String text=field.path(key).asText();return text.codePointCount(0,text.length())<=VALUE_LIMIT?text:text.substring(0,text.offsetByCodePoints(0,VALUE_LIMIT))+"…"; }
        return "";
    }
    private static Value snapshot(Object value) {
        if (value == ValueTypes.MISSING) return new Value("（字段缺失或输入尚未生成）", false);
        if (value == null || value instanceof Boolean || value instanceof Number && value.toString().length() <= VALUE_LIMIT) return new Value(value, false);
        String text;
        try { text = value instanceof Map<?,?> || value instanceof Collection<?> || value.getClass().isArray() ? JSON.writeValueAsString(value) : value.toString(); }
        catch (Exception ignored) { text = "（无法显示的值类型：" + value.getClass().getSimpleName() + "）"; }
        int size = text.codePointCount(0, text.length());
        return new Value(size <= VALUE_LIMIT ? text : text.substring(0, text.offsetByCodePoints(0, VALUE_LIMIT)) + "…（共" + size + "字符，完整原值见record）", size > VALUE_LIMIT);
    }
    private static String display(Object value) {
        if (value == null) return "null";
        String text = value.toString();
        if (text.codePointCount(0, text.length()) > 160) text = text.substring(0, text.offsetByCodePoints(0, 160)) + "…（完整摘要见结构化字段）";
        try { return value instanceof String ? JSON.writeValueAsString(text) : text; }
        catch (Exception ignored) { return "（值显示失败）"; }
    }
    private static String stepExpectation(JsonNode step) {
        JsonNode args = step.path("args");
        return switch (step.path("operator").asText()) {
            case "CAST" -> args.path("logicalType").asText("STRING") + (args.path("logicalType").asText().equals("DECIMAL") ? "(" + args.path("precision").asInt(38) + "," + args.path("scale").asInt(10) + ")" : "") + "，舍入=" + args.path("roundingMode").asText("UNNECESSARY");
            case "DATE_PARSE", "DATE_FORMAT" -> "日期格式=" + args.path("pattern").asText("AUTO") + "，输出类型=" + args.path("logicalType").asText("DATE");
            case "LOOKUP", "LOOKUP_MULTI" -> "字典=" + args.path("definitionRef").path("id").asText(args.path("dictionaryId").asText("内联字典")) + "，未命中策略=" + args.path("missPolicy").asText("NULL");
            case "VALIDATE" -> "必填=" + args.path("required").asBoolean() + "，最大长度=" + args.path("maxLength").asText("未限制") + "，最小值=" + args.path("minimum").asText("未限制") + "，最大值=" + args.path("maximum").asText("未限制");
            case "FUNCTION", "STRING_OP", "NUMBER_OP" -> "函数=" + args.path("function").asText();
            default -> operatorName(step.path("operator").asText());
        };
    }
    private static String operatorName(String operator) {
        return switch (operator) {
            case "INPUT" -> "规则输入或输入表达式"; case "TARGET_VALIDATION" -> "目标类型及约束校验"; case "CONDITION" -> "条件判断";
            case "CAST" -> "类型转换 CAST"; case "DATE_PARSE" -> "日期解析"; case "DATE_FORMAT" -> "日期格式化";
            case "LOOKUP", "LOOKUP_MULTI" -> "字典映射"; case "VALIDATE" -> "字段校验"; default -> operator;
        };
    }
}
