package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Converts stored NiFi data using the application's Jackson configuration and reads the native root snapshot. */
@Component
@MagicModule("nifiTaskChainRuntime")
public class NifiTaskChainRuntime {
    private final ObjectMapper json;
    private final NifiClient nifi;

    public NifiTaskChainRuntime(ObjectMapper json, NifiClient nifi) {
        this.json = json;
        this.nifi = nifi;
    }

    @Comment("用应用的 JSON 配置解码保存的画布")
    public Pipeline.Dsl dsl(String value) throws JsonProcessingException {
        return json.readValue(value, Pipeline.Dsl.class);
    }

    @Comment("将已解码的画布与持久化字段和节点映射还原为流程对象")
    public Pipeline pipeline(Map<String, Object> row, Pipeline.Dsl dsl) throws JsonProcessingException {
        String mapping = text(row, "node_mapping_json");
        return new Pipeline(text(row, "id"), (String) row.get("name"), (String) row.get("description"),
                number(row, "created_at"), number(row, "updated_at"), dsl, text(row, "nifi_process_group_id"),
                status(text(row, "status")), text(row, "last_deployed_hash"), number(row, "last_deployed_at"),
                number(row, "last_stopped_at"), mapping.isBlank() ? null : json.readValue(mapping, NifiNodeMapping.class),
                number(row, "last_bulletin_id"));
    }

    @Comment("读取 NiFi 原生根流程组状态快照")
    public JsonNode nativeHierarchy() {
        return nifi.getProcessGroupStatus(nifi.getRootProcessGroupId())
                .path("processGroupStatus").path("aggregateSnapshot");
    }

    private static String text(Map<String, Object> row, String key) {
        return Objects.toString(row.get(key), "").trim();
    }

    private static Long number(Map<String, Object> row, String key) {
        return row.get(key) instanceof Number value ? value.longValue() : null;
    }

    private static PipelineStatus status(String value) {
        try {
            return PipelineStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PipelineStatus.SAVED;
        }
    }
}
