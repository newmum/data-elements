package com.linewell.dataelement.model.nifi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Generic NiFi entity envelope: { revision, component, status, ... }. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record NifiEntity(
        RevisionDto revision,
        Map<String, Object> component,
        Map<String, Object> status,
        Boolean disconnectedNodeAcknowledged
) {
    public String id() {
        if (component == null) return null;
        Object value = component.get("id");
        return value == null ? null : value.toString();
    }
}
