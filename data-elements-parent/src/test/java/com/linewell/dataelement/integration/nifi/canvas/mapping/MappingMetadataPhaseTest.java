package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MappingMetadataPhaseTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void editorCompileAndPreviewUseRegistrationButPublicationCannotBypassPhysicalValidation()throws Exception {
        for(int count:List.of(20,100)){
            var catalog=mock(RegisteredDictionaryCatalog.class);var schemas=mock(RegisteredMappingSchemaService.class);
            when(catalog.bind(any())).thenAnswer(call->new RegisteredDictionaryCatalog.Bound(call.getArgument(0),Map.of()));
            var service=new UnifiedMappingService(catalog,new FieldMappingService());service.setSchemas(schemas);
            doThrow(new MappingException("SCHEMA_UNAVAILABLE","validation metadata unavailable")).when(schemas).checkContracts(anyMap());
            var spec=JSON.readTree("""
              {"version":"1.0","schemaVerification":"REGISTERED_ONLY","mappings":[{"from":"event_no","to":"event_no","targetType":"STRING"}]}
              """);
            try(var tenant=TenantContext.use("tenant-A")){
                var response=service.compileResponse(spec);assertEquals("REGISTERED_ONLY",response.get("schemaVerification"));assertEquals(true,response.get("executable"));
                var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Map.of("event_no","validation-"+i));
                var preview=service.preview(spec,rows);assertEquals(count,preview.resultRows().size());assertEquals(0,preview.stats().get("dictionarySqlCount"));
                assertEquals("SCHEMA_UNAVAILABLE",assertThrows(MappingException.class,()->service.compileBatch(Map.of("mapping",spec))).code());
            }
            verify(schemas,times(2)).checkRegisteredContracts(anyMap());verify(schemas,times(1)).checkContracts(anyMap());
        }
    }
    @Test void editorStillRejectsAnUnauthorizedReferenceWithoutFallingBackToClientFields()throws Exception {
        var catalog=mock(RegisteredDictionaryCatalog.class);var schemas=mock(RegisteredMappingSchemaService.class);
        when(catalog.bind(any())).thenAnswer(call->new RegisteredDictionaryCatalog.Bound(call.getArgument(0),Map.of()));
        doThrow(new MappingException("RESOURCE_FORBIDDEN","validation denied")).when(schemas).checkRegisteredContracts(anyMap());
        var service=new UnifiedMappingService(catalog,new FieldMappingService());service.setSchemas(schemas);
        assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->service.compileResponse(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[]}"))).code());
        verify(schemas,never()).checkContracts(anyMap());
    }
}
