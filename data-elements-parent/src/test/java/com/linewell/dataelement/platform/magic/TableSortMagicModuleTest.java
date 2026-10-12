package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableSortMagicModuleTest {
    private final TableSortMagicModule sort = new TableSortMagicModule();
    @Test void sqlOnlyUsesCallerAllowlist() {
        Map<String, String> fields = Map.of("name", "t.name", "count", "COALESCE(stats.total,0)");
        assertEquals("t.name", sort.field(Map.of("sortField", "name"), fields, "name"));
        assertEquals("COALESCE(stats.total,0)", sort.field(Map.of("sortField", "count"), fields, "name"));
        assertThrows(IllegalArgumentException.class, () -> sort.field(Map.of("sortField", "name desc;drop table t"), fields, "name"));
        assertThrows(IllegalArgumentException.class, () -> sort.direction(Map.of("sortDir", "desc nulls first")));
    }
    @Test void numericOrderingAndNullPlacementAreStableAt20And100() {
        for (int count : List.of(20, 100)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = count; i > 0; i--) rows.add(Map.of("id", String.format("%03d", i), "count", i % 10));
            rows.add(Map.of("id", "null"));
            for (String dir : List.of("asc", "desc")) {
                List<Map<String, Object>> sorted = sort.rows(rows, Map.of("sortField", "count", "sortDir", dir), Map.of("count", "count"), "count", "id");
                assertEquals("null", sorted.get(count).get("id"));
                for (int i = 1; i < count; i++) {
                    int a = (Integer) sorted.get(i - 1).get("count"), b = (Integer) sorted.get(i).get("count");
                    assertTrue(dir.equals("asc") ? a <= b : a >= b);
                    if (a == b) assertTrue(sorted.get(i - 1).get("id").toString().compareTo(sorted.get(i).get("id").toString()) < 0);
                }
            }
            assertEquals(count + 1, rows.size());
        }
    }
    @Test void refusesUnboundedCollectionSorting() {
        assertThrows(IllegalArgumentException.class, () -> sort.rows(java.util.Collections.nCopies(1001, Map.of("id", "1")), Map.of(), Map.of("id", "id"), "id", "id"));
    }
}
