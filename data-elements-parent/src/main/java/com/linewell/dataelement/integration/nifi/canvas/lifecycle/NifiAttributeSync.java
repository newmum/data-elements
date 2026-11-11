package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Updates explicitly selected attributes of processors in the bound runtime group. */
public final class NifiAttributeSync {
    private static final int MAX_CHANGES = 100;
    private static final Set<String> SOURCE_SCHEDULERS = Set.of("GenerateTableFetch", "GenerateDateTableFetch",
            "QueryDatabaseTableRecord", "ExecuteSQLRecord", "GetFTP", "GetSFTP", "ListFTP", "ListSFTP",
            "GetHwFTP", "GetHwSFTP", "ListS3", "ListHDFS", "GenerateFlowFile", "ConsumeKafkaRecord_2_6");
    private final NifiClient nifi;

    public NifiAttributeSync(NifiClient nifi) {
        this.nifi = nifi;
    }

    public record Selection(String nodeId, String field, String processorId, String currentValue,
                            String designValue, boolean keepNative) {}

    public record Request(String processGroupId, List<Selection> selections) {}

    public record Change(String nodeId, String nodeLabel, String field, String label, String processorId,
                         String currentValue, String designValue, String status, String message) {
        Change result(String status, String message) {
            return new Change(nodeId, nodeLabel, field, label, processorId, currentValue, designValue, status, message);
        }
    }

    public record Preview(String processGroupId, List<Change> changes) {}
    public record Applied(List<Change> results, Map<String, Map<String, NifiNodeMapping.AttributeBaseline>> baselines) {}

    private enum Field {
        PERIOD("schedulingPeriod", "调度周期", "schedulingPeriod", true),
        BATCH("batchSize", "单批最大条数", "Max Rows Per FlowFile", false),
        FETCH("maxRowsPerRun", "JDBC 抓取行数", "Fetch Size", false),
        DIRECTORY("hdfsDirectory", "HDFS 写入目录", "Directory", false);

        final String key;
        final String label;
        final String nativeKey;
        final boolean scheduler;

        Field(String key, String label, String nativeKey, boolean scheduler) {
            this.key = key;
            this.label = label;
            this.nativeKey = nativeKey;
            this.scheduler = scheduler;
        }

        String current(JsonNode processor) {
            JsonNode config = processor.path("component").path("config");
            return (scheduler ? config : config.path("properties")).path(nativeKey).asText(null);
        }

        void write(ObjectNode config, String value) {
            if (scheduler) config.put(nativeKey, value);
            else config.withObjectProperty("properties").put(nativeKey, value);
        }
    }

    private record Key(String nodeId, String field) {}
    private record Target(Field field, Change change, JsonNode processor) {}
    private record Write(Target target, String value) {}

    public Map<String, Map<String, NifiNodeMapping.AttributeBaseline>> capture(
            Pipeline.Dsl dsl, String group, NifiNodeMapping mapping) {
        var result = new LinkedHashMap<String, Map<String, NifiNodeMapping.AttributeBaseline>>();
        for (Target target : targets(dsl, mapping, group, true).values()) {
            if (target.processor() != null) record(result, target.change(), target.change().currentValue());
        }
        return result;
    }

    public Preview preview(Pipeline pipeline) {
        requireRuntime(pipeline);
        var changes = targets(pipeline.dsl(), pipeline.nodeMapping(), pipeline.nifiProcessGroupId(), false)
                .values().stream().map(Target::change).toList();
        return new Preview(pipeline.nifiProcessGroupId(), changes);
    }

    public Applied apply(Pipeline pipeline, Request request) {
        requireRuntime(pipeline);
        if (request == null || request.selections() == null || request.selections().isEmpty()
                || request.selections().size() > MAX_CHANGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 100 项属性");
        }
        if (!Objects.equals(request.processGroupId(), pipeline.nifiProcessGroupId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前 NiFi 流程关联已变化，请重新预览");
        }
        Set<Key> selected = new LinkedHashSet<>();
        for (Selection selection : request.selections()) {
            if (selection == null || selection.nodeId() == null || selection.field() == null
                    || !selected.add(new Key(selection.nodeId(), selection.field()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "属性选择为空或重复");
            }
        }
        var targets = targets(pipeline.dsl(), pipeline.nodeMapping(), pipeline.nifiProcessGroupId(), true);
        var results = new LinkedHashMap<Key, Change>();
        var writes = new LinkedHashMap<String, List<Write>>();
        var baselines = new LinkedHashMap<String, Map<String, NifiNodeMapping.AttributeBaseline>>();
        if (pipeline.nodeMapping().attributeBaselines() != null) {
            pipeline.nodeMapping().attributeBaselines().forEach((key, values) -> baselines.put(key, new LinkedHashMap<>(values)));
        }
        for (Selection selection : request.selections()) {
            var key = new Key(selection.nodeId(), selection.field());
            var target = targets.get(key);
            if (target == null || target.processor() == null) {
                results.put(key, new Change(key.nodeId(), null, key.field(), null, null, null, null,
                        "UNAVAILABLE", "属性或对应处理器已不可用，请重新打开画布核对关联"));
                continue;
            }
            Change change = target.change();
            if (!Objects.equals(change.processorId(), selection.processorId())
                    || !Objects.equals(change.designValue(), selection.designValue())
                    || (!Objects.equals(change.currentValue(), selection.currentValue())
                        && (selection.keepNative() || !Objects.equals(change.currentValue(), change.designValue())))) {
                results.put(key, change.result("CHANGED", "预览后属性已变化，请重新核对"));
            } else if ("UNAVAILABLE".equals(change.status())) {
                results.put(key, change);
            } else if (selection.keepNative() || Objects.equals(change.currentValue(), change.designValue())) {
                record(baselines, change, change.currentValue());
                results.put(key, change.result(selection.keepNative() ? "KEPT_NATIVE" : "SYNCED", null));
            } else if ("STOP_REQUIRED".equals(change.status())) {
                results.put(key, change);
            } else if (!target.processor().path("revision").path("version").isIntegralNumber()) {
                results.put(key, change.result("RETRY", "NiFi 未返回组件版本，请重新预览"));
            } else {
                writes.computeIfAbsent(change.processorId(), ignored -> new ArrayList<>())
                        .add(new Write(target, change.designValue()));
            }
        }
        for (Map.Entry<String, List<Write>> entry : writes.entrySet()) {
            List<Write> updates = entry.getValue();
            ObjectNode requestBody = JsonNodeFactory.instance.objectNode();
            requestBody.set("revision", updates.getFirst().target().processor().path("revision"));
            ObjectNode config = requestBody.putObject("component").put("id", entry.getKey()).putObject("config");
            updates.forEach(update -> update.target().field().write(config, update.value()));
            JsonNode response = null;
            String failure = null;
            try {
                response = nifi.put("/processors/" + entry.getKey(), requestBody, JsonNode.class);
            } catch (NifiException e) {
                failure = e.status() == 409 ? "NiFi 组件版本或状态已变化，请重新预览"
                        : e.status() == 404 ? "NiFi 处理器已移除，请核对关联"
                        : "NiFi 未确认更新（HTTP " + e.status() + "），请重新预览后重试";
            }
            for (Write update : updates) {
                Change change = update.target().change();
                var key = new Key(change.nodeId(), change.field());
                String actual = response == null ? null : update.target().field().current(response);
                if (Objects.equals(actual, update.value())) {
                    record(baselines, change, actual);
                    results.put(key, change.result("SYNCED", null));
                } else {
                    results.put(key, change.result("RETRY", failure == null ? "NiFi 返回的属性值需复核" : failure));
                }
            }
        }
        return new Applied(selected.stream().map(results::get).toList(), baselines);
    }

    private Map<Key, Target> targets(Pipeline.Dsl dsl, NifiNodeMapping mapping, String group, boolean includeUnchanged) {
        var index = new LinkedHashMap<String, List<JsonNode>>();
        JsonNode processors = nifi.get("/process-groups/" + group + "/processors?includeDescendantGroups=true",
                JsonNode.class).path("processors");
        if (!processors.isArray()) throw new IllegalStateException("NiFi 未返回可用的处理器清单");
        for (JsonNode processor : processors) {
            String nodeId = mapping.processorIdToCanvasNode().get(processor.path("id").asText());
            if (nodeId != null) index.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(processor);
        }
        var result = new LinkedHashMap<Key, Target>();
        for (Pipeline.Node node : dsl.nodes()) {
            if (node.config() == null) continue;
            List<JsonNode> nativeNodes = index.getOrDefault(node.id(), List.of());
            for (Field field : Field.values()) {
                if (!eligible(node, field)) continue;
                String design = desired(node, field);
                var baseline = mapping.attributeBaselines() == null ? null
                        : mapping.attributeBaselines().getOrDefault(node.id(), Map.of()).get(field.key);
                if (!includeUnchanged && Objects.equals(design, baseline == null ? null : baseline.designValue())) continue;
                List<JsonNode> matches = nativeNodes.stream().filter(processor -> matches(node, processor, field, mapping)).toList();
                JsonNode processor = matches.size() == 1 ? matches.getFirst() : null;
                String id = processor == null ? null : processor.path("id").asText();
                String current = processor == null ? null : field.current(processor);
                String status;
                String message = null;
                if (processor == null) {
                    status = "UNAVAILABLE";
                    message = "对应处理器缺失或有多个匹配项，请核对 NiFi 关联";
                } else if (design == null) {
                    status = "UNAVAILABLE";
                    message = "请在海通填写有效的属性值";
                } else if (Objects.equals(current, design)) {
                    status = "MATCHED";
                } else if ("RUNNING".equals(processor.path("component").path("state").asText())
                        || processor.path("status").path("aggregateSnapshot").path("activeThreadCount").asInt() > 0) {
                    status = "STOP_REQUIRED";
                    message = "请先在 NiFi 停止对应处理器，等待正在运行的任务结束";
                } else if (baseline == null || !Objects.equals(baseline.processorId(), id)) {
                    status = "REVIEW";
                    message = "此属性暂无已同步记录，请核对当前值";
                } else if (!Objects.equals(baseline.nativeValue(), current)) {
                    status = "CONFLICT";
                    message = "NiFi 中也修改过此属性";
                } else {
                    status = "READY";
                }
                if (!includeUnchanged && "MATCHED".equals(status) && baseline == null) continue;
                var change = new Change(node.id(), node.label(), field.key, field.label, id, current, design, status, message);
                result.put(new Key(node.id(), field.key), new Target(field, change, processor));
            }
        }
        return result;
    }

    private static boolean eligible(Pipeline.Node node, Field field) {
        if (field == Field.DIRECTORY) {
            return HiveSinkPublicationPolicy.supportsHdfsMerge(node);
        }
        if (!"source".equals(node.category()) || "PERIODIC_FULL".equals(node.config().get("syncMode"))) return false;
        return field != Field.PERIOD || !Set.of("DAILY_IMMEDIATE", "DAILY_STAGGERED")
                .contains(Objects.toString(node.config().get("batchSchedulePolicy"), ""));
    }

    private static boolean matches(Pipeline.Node node, JsonNode processor, Field field, NifiNodeMapping mapping) {
        JsonNode component = processor.path("component");
        String type = component.path("type").asText();
        type = type.substring(type.lastIndexOf('.') + 1);
        return switch (field) {
            case DIRECTORY -> ("PutHwHDFS".equals(type) || "PutHDFS".equals(type))
                    && component.path("config").path("properties").has(field.nativeKey)
                    && !Objects.toString(field.current(processor), "").contains("${");
            case BATCH, FETCH -> "ExecuteSQLRecord".equals(type)
                    && component.path("config").path("properties").has(field.nativeKey);
            case PERIOD -> SOURCE_SCHEDULERS.contains(type)
                    && Objects.equals(mapping.primaryProcessorIds().get(node.id()), processor.path("id").asText())
                    && Objects.toString(node.config().get("schedulingStrategy"), "TIMER_DRIVEN")
                        .equals(component.path("config").path("schedulingStrategy").asText());
        };
    }

    private static String desired(Pipeline.Node node, Field field) {
        String value = Objects.toString(node.config().get(field.key), "").strip();
        if (value.isEmpty() || value.length() > 1024 || value.chars().anyMatch(Character::isISOControl)) return null;
        if ((field == Field.BATCH || field == Field.FETCH) && !value.matches("[0-9]{1,9}")) return null;
        return value;
    }

    private static void record(Map<String, Map<String, NifiNodeMapping.AttributeBaseline>> result, Change change, String nativeValue) {
        result.computeIfAbsent(change.nodeId(), ignored -> new LinkedHashMap<>()).put(change.field(),
                new NifiNodeMapping.AttributeBaseline(change.processorId(), change.designValue(), nativeValue));
    }

    private static void requireRuntime(Pipeline pipeline) {
        if (pipeline.nifiProcessGroupId() == null || pipeline.nifiProcessGroupId().isBlank()
                || pipeline.dsl() == null || pipeline.nodeMapping() == null
                || pipeline.nodeMapping().primaryProcessorIds() == null || pipeline.nodeMapping().processorIdToCanvasNode() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务缺少已部署的 NiFi 组件关联");
        }
    }
}
