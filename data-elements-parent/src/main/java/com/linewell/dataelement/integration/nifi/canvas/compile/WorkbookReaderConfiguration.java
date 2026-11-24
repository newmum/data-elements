package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.mapping.core.WorkbookRows;

import java.util.Map;

/** Converts an authoritative source snapshot into the native workbook reader contract. */
public final class WorkbookReaderConfiguration {
    public static final String TYPE = "com.linewell.dataelement.nifi.HaitongWorkbookRecordReader";
    public static final String LAYOUT = "workbook-layout";

    private WorkbookReaderConfiguration() {
    }

    public static Map<String, String> properties(JsonNode registeredShape) {
        JsonNode definition = registeredShape.path("workbook");
        if (!registeredShape.path("skipBlankRecords").asBoolean(false) || !definition.isObject()) {
            throw new IllegalArgumentException("已登记工作簿缺少工作表和字段位置，请重新采集");
        }
        WorkbookRows.Layout.parse(definition);
        return Map.of(LAYOUT, definition.toString());
    }
}
