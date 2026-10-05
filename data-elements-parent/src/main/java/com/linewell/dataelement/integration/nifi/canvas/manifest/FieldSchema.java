package com.linewell.dataelement.integration.nifi.canvas.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FieldSchema(
        String key,
        String label,
        String type,
        Boolean required,
        @JsonProperty("default") Object defaultValue,
        List<Option> options,
        String placeholder,
        VisibleWhen visibleWhen,
        String help,
        String group
) {
    public record Option(String label, String value) {}
    public record VisibleWhen(String key, Object equals) {}
}
