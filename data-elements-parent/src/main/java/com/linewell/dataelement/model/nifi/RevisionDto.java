package com.linewell.dataelement.model.nifi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RevisionDto(Long version, String clientId, @JsonProperty("lastModifier") String lastModifier) {
    public static RevisionDto initial(String clientId) {
        return new RevisionDto(0L, clientId, null);
    }
}
