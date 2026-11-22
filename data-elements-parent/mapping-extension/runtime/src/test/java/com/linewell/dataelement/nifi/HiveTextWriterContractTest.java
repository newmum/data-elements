package com.linewell.dataelement.nifi;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.csv.CSVUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.StringWriter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs NiFi's real configuration factory, including its Java unescape step. */
class HiveTextWriterContractTest {
    @Test void singleRawBackslashReproducesTheObservedLossOfSqlNull() throws Exception {
        var format = CSVUtils.createCSVFormat(context("\\N"), Map.of());
        assertEquals("N", format.getNullString());
        var wire = new StringWriter();
        format.printRecord(wire, (Object) null);
        assertEquals("N\n", wire.toString());
    }

    @ParameterizedTest @ValueSource(strings = {"\\\\N", "\\u005cN", "\\134N"})
    void escapedRawMarkerKeepsNullEmptyLiteralMarkerAndJsonDistinct(String rawNull) throws Exception {
        var format = CSVUtils.createCSVFormat(context(rawNull), Map.of());
        assertEquals("\\N", format.getNullString());
        var wire = new StringWriter();
        String json = "{\"text\":\"中文\\n\\\\路径\"}";
        format.printRecord(wire, null, "", "\\N", json, "a\nb\rc\u0001d");
        assertEquals("\\N\u0001\u0001\\\\N\u0001{\"text\":\"中文\\\\n\\\\\\\\路径\"}\u0001a\\nb\\rc\\\u0001d\n", wire.toString());
    }

    private PropertyContext context(String rawNull) {
        var settings = Map.of("CSV Format", "custom", "Value Separator", "\\u0001",
                "Record Separator", "\\n", "Quote Mode", "NONE", "Quote Character", "\"",
                "Escape Character", "\\", "Null String", rawNull, "Trim Fields", "false");
        var context = mock(PropertyContext.class);
        when(context.getProperty(any(PropertyDescriptor.class))).thenAnswer(call -> {
            PropertyDescriptor descriptor = call.getArgument(0);
            String value = settings.getOrDefault(descriptor.getName(), descriptor.getDefaultValue());
            var property = mock(PropertyValue.class);
            when(property.getValue()).thenReturn(value);
            when(property.isSet()).thenReturn(value != null);
            when(property.asBoolean()).thenReturn(Boolean.parseBoolean(value));
            when(property.evaluateAttributeExpressions(anyMap())).thenReturn(property);
            return property;
        });
        return context;
    }
}
