package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MappingDiagnosticPreviewTest {
    @Test void previewRetainsLocationAndReasonsWhileApplyingItsExistingMaskingBoundary() throws Exception {
        var json=new ObjectMapper();var catalog=mock(RegisteredDictionaryCatalog.class);var legacy=mock(FieldMappingService.class);
        when(catalog.bind(any())).thenAnswer(call->new RegisteredDictionaryCatalog.Bound(call.getArgument(0),Map.of()));
        var service=new UnifiedMappingService(catalog,legacy);
        for(String comment:List.of("经度","手机号"))for(int count:List.of(20,100)) {
            var spec=json.readTree("""
              {"schemaVersion":"2.0","policies":{"recordErrorMode":"ROUTE_INVALID"},"rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"raw"},"target":{"name":"output","logicalType":"INT","comment":"COMMENT"}}]}
              """.replace("COMMENT",comment));
            try(var tenant=TenantContext.use("tenant-A")) {
                var response=service.preview(spec,Collections.nCopies(count,Map.of("raw","synthetic-invalid")));
                assertEquals(count,response.rowResults().size());
                for(var row:response.rowResults()) {
                    String message=row.errors().getFirst();assertTrue(message.contains("output（"+comment+"）"));assertTrue(message.contains("原因："));
                    assertEquals(comment.equals("经度"),message.contains("synthetic-invalid"));
                }
            }
        }
        verify(catalog,times(4)).bind(any());verifyNoInteractions(legacy);
    }
}
