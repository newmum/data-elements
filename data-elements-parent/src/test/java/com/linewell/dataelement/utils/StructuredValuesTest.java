package com.linewell.dataelement.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructuredValuesTest {
    @Test void keepsMutableResponseIdentityAndNullValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", null);
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row));
        assertSame(row, StructuredValues.objectMap(row));
        assertSame(rows, StructuredValues.objectRows(rows));
        StructuredValues.objectRows(rows).getFirst().put("status", "READY");
        assertEquals("READY", row.get("status"));
        assertTrue(row.containsKey("value"));
    }

    @Test void rejectsNonStringKeysAndMixedRowsBeforeReturningTypedContainers() {
        assertThrows(IllegalArgumentException.class, () -> StructuredValues.objectMap(Map.of(1, "value")));
        assertThrows(IllegalArgumentException.class, () -> StructuredValues.objectMap("invalid"));
        assertThrows(IllegalArgumentException.class, () -> StructuredValues.objectRows(List.of(Map.of("id", 1), "invalid")));
        assertThrows(IllegalArgumentException.class, () -> StructuredValues.objectRows(List.of(Map.of("id", 1), Map.of(2, "invalid"))));
    }

    @Test void preservesNullableContainerAndElementContracts() {
        assertNull(StructuredValues.objectMap(null));
        assertNull(StructuredValues.objectRows(null));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(null);
        assertSame(rows, StructuredValues.objectRows(rows));
    }
}
