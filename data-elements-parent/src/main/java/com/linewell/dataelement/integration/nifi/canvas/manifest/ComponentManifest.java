package com.linewell.dataelement.integration.nifi.canvas.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ComponentManifest(
        String key,
        String category,
        String label,
        String icon,
        String description,
        List<FieldSchema> fields,
        CompileSpec compile
) {}
