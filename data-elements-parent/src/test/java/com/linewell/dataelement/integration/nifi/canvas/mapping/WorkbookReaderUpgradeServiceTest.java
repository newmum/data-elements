package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkbookReaderUpgradeServiceTest {
    @Test
    void retainedConversionFailureCanStayInTheSelectedProcessorsOwnRetryConnection() {
        var dto = new ObjectMapper().createObjectNode();
        var component = dto.putObject("component");
        component.putObject("source").put("id", "workbook-converter").put("type", "PROCESSOR");
        var destination = component.putObject("destination").put("id", "workbook-converter").put("type", "PROCESSOR");
        component.putArray("selectedRelationships").add("failure");
        var graph = new LegacyNativeGraph.Graph(Map.of(), Map.of("retained", dto), Map.of(), Map.of());

        assertDoesNotThrow(() -> WorkbookReaderUpgradeService.requireLocalFailure(graph, Set.of("workbook-converter"), "retained"));

        destination.put("id", "unrelated-processor");
        assertThrows(MappingException.class, () -> WorkbookReaderUpgradeService.requireLocalFailure(graph, Set.of("workbook-converter"), "retained"));
    }
}
