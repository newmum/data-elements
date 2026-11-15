package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService.FieldMeta;
import java.util.List;
import java.util.Map;

/** Transport types for Magic's existing field-mapping JSON contracts. */
public final class MappingRequestParameters {
    private MappingRequestParameters() {
    }

    public record ValidateRequest(JsonNode spec, List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema) {}

    public record PreviewRequest(JsonNode spec, List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema,
                                 Map<String, Object> sourceConfig, List<Map<String, Object>> sampleRows) {}

    public record RecommendRequest(List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema, Map<String, Object> options) {}

    public record CompileRequest(JsonNode spec, List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema,
                                 Map<String, Object> sourceConfig) {}
}
