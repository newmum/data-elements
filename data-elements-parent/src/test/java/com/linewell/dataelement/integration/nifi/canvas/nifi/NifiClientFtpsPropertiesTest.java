package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NifiClientFtpsPropertiesTest {
    @Test void credentialsAreDeclaredSensitiveInTheActualCreationRequest() {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),new ObjectMapper()));
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/validation/processors"),any(),eq(NifiEntity.class));
        client.createProcessor("validation","org.apache.nifi.processors.groovyx.ExecuteGroovyScript","validation",0,0,
                Map.of("ftps.password",UUID.randomUUID().toString(),"ftps.trustStorePassword","","Script Body","// validation"),"60 sec","TIMER_DRIVEN");
        var body=ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/validation/processors"),body.capture(),eq(NifiEntity.class));
        JsonNode configuration=((JsonNode)body.getValue()).path("component").path("config");
        assertTrue(configuration.path("sensitiveDynamicPropertyNames").toString().contains("ftps.password"));
        assertTrue(configuration.path("sensitiveDynamicPropertyNames").toString().contains("ftps.trustStorePassword"));
    }
}
