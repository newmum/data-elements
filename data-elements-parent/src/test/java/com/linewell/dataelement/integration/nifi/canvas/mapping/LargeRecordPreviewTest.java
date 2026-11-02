package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LargeRecordPreviewTest {
    @Test void previewUsesTheRuntimeBudgetAndOmitsThePictureFromItsResponse()throws Exception {
        var json=new ObjectMapper();var spec=json.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"picture","input":{"kind":"FIELD","name":"pic_base64"},"target":{"name":"pic_base64","logicalType":"STRING","nullable":false},"steps":[{"operator":"DIRECT"}]}]}
            """);
        var service=spy(new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),new FieldMappingService()));
        doReturn(new UnifiedMappingService.Compiled(MappingEngine.compile(spec),Map.of())).when(service).compile(any());
        try(var ignored=TenantContext.use("validation-tenant")){
            for(int count:new int[]{20,100}){
                var rows=Collections.nCopies(count,Map.<String,Object>of("pic_base64","A".repeat(32_000)));
                var result=service.preview(spec,rows);assertEquals(count,result.resultRows().size());assertEquals(0,result.stats().get("dictionarySqlCount"));
                assertTrue(result.resultRows().getFirst().get("pic_base64").toString().length()<100);
            }
            var sample=List.of(Map.<String,Object>of("pic_base64","A".repeat(2_000_000)));
            assertEquals(1,service.preview(spec,sample).resultRows().size());
            assertThrows(MappingException.class,()->service.preview(spec,List.of(Map.of("pic_base64","A".repeat((int)RecordBudget.RECORD_BYTES)))));
        }
    }
}
