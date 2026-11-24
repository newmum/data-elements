package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlTimestampCarrierTest {
    @Test void onlyExplicitTimestampPhysicalFieldsAllowTextInTheConfiguredCodecMode() {
        for(String physical:List.of("TIMESTAMP(9)","DATETIME(6)")) {
            var plan=plan(physical);
            var text=List.of(new SchemaContract.Field("event_time","STRING",true,0,0,0));
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,text));
            assertDoesNotThrow(()->SchemaContract.requireInput(plan,text,true));
            for(String type:List.of("LONG","DOUBLE","BOOLEAN","ARRAY"))
                assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("event_time",type,true,0,0,0)),true));
        }
        for(String physical:List.of("VARCHAR(80)","STRING","DATE","TIME",""))
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan(physical),List.of(new SchemaContract.Field("event_time","STRING",true,0,0,0)),true));
    }

    private static MappingEngine.Plan plan(String physical) {
        var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
        spec.putObject("schemaContract").putArray("source").addObject().put("name","event_time").put("logicalType","DATETIME").put("physicalType",physical).put("nullable",true);
        var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","event_time");rule.putObject("target").put("name","event_time").put("logicalType","DATETIME").put("nullable",true);
        return MappingEngine.compile(spec);
    }
}
