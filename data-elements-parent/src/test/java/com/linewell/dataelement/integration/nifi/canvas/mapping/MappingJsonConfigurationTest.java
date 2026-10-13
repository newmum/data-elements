package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingJsonConfigurationTest {
    @Test void exactDecimalsAreScopedToMappingRequests()throws Exception{List<HttpMessageConverter<?>> converters=new ArrayList<>();new MappingJsonConfiguration(new ObjectMapper()).extendMessageConverters(converters);var converter=(MappingJackson2HttpMessageConverter)converters.getFirst();assertTrue(converter.canRead(FieldMappingController.PreviewRequest.class,FieldMappingController.class,MediaType.APPLICATION_JSON));assertFalse(converter.canRead(Map.class,String.class,MediaType.APPLICATION_JSON));var value=converter.getObjectMapper().readTree("{\"value\":12345678901234567890.123456789012345678}");assertEquals("12345678901234567890.123456789012345678",value.path("value").decimalValue().toPlainString());}
}
