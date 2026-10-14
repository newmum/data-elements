package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinewellAuditPropertiesTest {
    @Test void databaseClockDefaultsUseRealDescriptorKeysAndKeepCreationTimeOutOfUpdates()throws Exception {
        var intents=LinewellAuditProperties.defaults(List.of("ID","ODS_UUID","ODS_RKSJ","ODS_GXSJ"),Map.of("dbType","OCEANBASE_ORACLE"));
        var descriptors=new ObjectMapper().readTree("""
          {"actual-insert-key":{"displayName":"insert 追加value 值"},"actual-update-key":{"displayName":"update 追加value 值"},"actual-exclude-key":{"displayName":"merge 排除 update"}}
          """);
        var values=LinewellAuditProperties.resolve(intents,descriptors);
        assertEquals("ODS_RKSJ=SYSDATE,ODS_GXSJ=SYSDATE",values.get("actual-insert-key"));
        assertEquals("ODS_GXSJ=SYSDATE",values.get("actual-update-key"));
        assertEquals("ODS_UUID,ODS_RKSJ",values.get("actual-exclude-key"));
        assertFalse(values.keySet().stream().anyMatch(key->key.startsWith(LinewellAuditProperties.PREFIX)));
    }
    @Test void optionalFieldsAndOtherDialectsDoNotEmitInvalidSysdate() {
        assertTrue(LinewellAuditProperties.defaults(List.of("ID"),Map.of("dbType","ORACLE")).isEmpty());
        for(String db:List.of("MYSQL","POSTGRESQL","SQLSERVER","DM","DB2","KINGBASE","HIVE"))assertEquals("ods_gxsj=CURRENT_TIMESTAMP",LinewellAuditProperties.defaults(List.of("ods_gxsj"),Map.of("dbType",db)).get(LinewellAuditProperties.PREFIX+"insert-values"));
    }
    @Test void missingOrDynamicOnlyDescriptorsFailInsteadOfInventingUnsupportedProperties()throws Exception {
        var json=new ObjectMapper();var intents=LinewellAuditProperties.defaults(List.of("ODS_GXSJ"),Map.of("dbType","ORACLE"));
        assertThrows(IllegalStateException.class,()->LinewellAuditProperties.resolve(intents,json.createObjectNode()));
        assertThrows(IllegalStateException.class,()->LinewellAuditProperties.resolve(intents,json.readTree("{\"insert 追加value 值\":{\"dynamic\":true}}")));
    }
}
