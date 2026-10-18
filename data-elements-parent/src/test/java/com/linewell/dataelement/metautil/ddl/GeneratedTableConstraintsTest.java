package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedTableConstraintsTest {
    @Test void generatedPrimaryKeysAndUniqueConstraintsHaveDifferentStableNamesWithoutPromotingUnique() {
        var service=new TablePartitions();var cfg=config(DatabaseType.OCEANBASE_ORACLE);
        String base="CREATE TABLE ODS_T (ID NUMBER, CODE VARCHAR2(32), TENANT_CODE VARCHAR2(8), ODS_UUID VARCHAR2(32) UNIQUE, PRIMARY KEY(ID)); COMMENT ON TABLE ODS_T IS '测试'";
        var facts=List.<Map<String,Object>>of(Map.of("name","PK_misleading_unique","type","U","columns",List.of("CODE","TENANT_CODE")));
        String ddl=service.constraints(base,facts,cfg);
        assertTrue(ddl.contains("CONSTRAINT PK_ODS_T_"));assertTrue(ddl.contains("CONSTRAINT UK_ODS_T_"));
        assertTrue(ddl.contains("UNIQUE (CODE, TENANT_CODE)"));assertTrue(ddl.contains("PRIMARY KEY (ID)"));
        assertFalse(ddl.contains("PRIMARY KEY (CODE"));assertTrue(ddl.contains("COMMENT ON TABLE"));
        assertEquals(ddl,service.constraints(base,facts,cfg));
        assertFalse(service.constraints(base,List.of(Map.of("type","U","columns",List.of("MISSING"))),cfg).contains("MISSING"));
        String noPrimary=service.constraints("CREATE TABLE ODS_T (CODE VARCHAR2(32))",List.of(Map.of("type","U","name","PK_CODE","columns",List.of("CODE"))),cfg);
        assertTrue(noPrimary.contains("UNIQUE (CODE)"));assertFalse(noPrimary.contains("PRIMARY KEY"));
        assertEquals(base,service.constraints(base,facts,config(DatabaseType.HIVE)));
    }
    private static DataSourceConfig config(DatabaseType type){var config=new DataSourceConfig();config.setDatabaseType(type);config.setDatabase("ods");config.setUsername("APP");return config;}
}
