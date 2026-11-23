package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSourceProjectionTest {
    @Test void complexFieldsBecomeNativeJsonWithSqlNullPreservedAndScalarsUnchanged() {
        String projection = JdbcSourceProjection.columns(config(List.of(
                field("id", "Int64"), field("params", "Map(String, String)"), field("tags", "Array(String)"),
                field("send_time", "DateTime"))));
        assertEquals("`id`,if(isNull(`params`), NULL, toJSONString(`params`)) AS `params`,"
                + "if(isNull(`tags`), NULL, toJSONString(`tags`)) AS `tags`,`send_time`", projection);
        assertFalse(projection.contains("CAST(`id`"));
    }

    @ParameterizedTest @ValueSource(strings = {"Nullable(Array(String))", "LowCardinality(Map(String, String))", "Array(Array(UInt64))"})
    void nativeWrappersAndNestedArraysKeepTheirRegisteredColumnIdentity(String type) {
        var config=config(List.of(field("Mixed Name", type)));
        assertTrue(JdbcSourceProjection.columns(config)
                .contains("toJSONString(`Mixed Name`)) AS `"+JdbcSourceProjection.aliases(config).get("Mixed Name")+"`"));
    }

    @Test void quotedNamesCannotBecomeExecutableProjectionText() {
        String name = "x`), malicious() --";
        var config=config(List.of(field(name, "Array(String)")));
        assertEquals("if(isNull(`x``), malicious() --`), NULL, toJSONString(`x``), malicious() --`)) AS `"+JdbcSourceProjection.aliases(config).get(name)+"`",
                JdbcSourceProjection.columns(config));
        assertThrows(IllegalArgumentException.class, () -> JdbcSourceProjection.columns(config(List.of(field("bad\0field", "Array(String)")))));
        assertThrows(IllegalStateException.class, () -> JdbcSourceProjection.columns(config(List.of(field("same", "Array(String)"), field("same", "String")))));
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void completeRegisteredSnapshotRetainsEveryFieldWithoutMetadataReads(int count) {
        var columns = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++) columns.add(field("field_" + i, i == count - 1 ? "Map(String, String)" : "Int64"));
        String projection = JdbcSourceProjection.columns(config(columns));
        for (int i = 0; i < count; i++) assertTrue(projection.contains("`field_" + i + "`"));
        assertEquals(1, projection.split("toJSONString", -1).length - 1);
    }

    @Test void scalarOnlyAndOtherVendorsRetainExistingNativeQueries() {
        assertEquals("", JdbcSourceProjection.columns(config(List.of(field("id", "Int64")))));
        assertEquals("", JdbcSourceProjection.columns(Map.of("dbType", "MYSQL", "sourceColumns", List.of(field("data", "Array(String)")))));
        assertEquals("", JdbcSourceProjection.columns(Map.of("dbType", "CLICKHOUSE")));
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void openHiveUsesExplicitPhysicalAliasesWithoutChangingFieldNamesOrTypes(int count) {
        var columns = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++) columns.add(field("column_" + i, "string"));
        String projection = JdbcSourceProjection.columns(Map.of("dbType", "HIVE", "hiveConnectionMode", "open-source", "sourceColumns", columns));
        for (int i = 0; i < count; i++) assertTrue(projection.contains("`column_" + i + "` AS `column_" + i + "`"));
        assertFalse(projection.contains("toJSONString"));
        assertFalse(projection.contains("CAST"));
        assertEquals("", JdbcSourceProjection.columns(Map.of("dbType", "HIVE", "hiveConnectionMode", "huawei-mrs", "sourceColumns", columns)));
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void numericPhysicalFieldsHaveStableCollisionFreeAliasesWithoutMetadataReads(int count) {
        var fields=new ArrayList<Map<String,Object>>();
        for(int i=0;i<count;i++)fields.add(field("00"+i,"varchar"));
        var config=Map.<String,Object>of("dbType","MYSQL","sourceColumns",fields);
        var aliases=JdbcSourceProjection.aliases(config);
        assertEquals(count,aliases.size());assertEquals(count,new java.util.HashSet<>(aliases.values()).size());
        String first=aliases.get("000");fields.add(field(first,"varchar"));
        var collision=JdbcSourceProjection.aliases(config);assertEquals(first+"_1",collision.get("000"));
        for(var entry:collision.entrySet())assertTrue(JdbcSourceProjection.columns(config).contains("`"+entry.getKey()+"` AS `"+entry.getValue()+"`"));
        assertTrue(JdbcSourceProjection.columns(config).endsWith("`"+first+"`"));
        assertEquals(collision,JdbcSourceProjection.aliases(config));
        assertTrue(JdbcSourceProjection.aliases(Map.of("dbType","KAFKA","sourceColumns",fields)).isEmpty());
    }
    @Test void numericAliasKeepsOracleDateProjectionAndLiteralQuoting() {
        var config=Map.<String,Object>of("dbType","ORACLE","sourceColumns",List.of(field("1231313","VARCHAR2"),field("CREATED_AT","DATE")));
        var alias=JdbcSourceProjection.aliases(config).get("1231313");
        assertEquals("\"1231313\" AS \""+alias+"\",CAST(\"CREATED_AT\" AS TIMESTAMP) AS \"CREATED_AT\"",JdbcSourceProjection.columns(config));
    }
    private Map<String, Object> config(List<?> columns) { return Map.of("dbType", "CLICKHOUSE", "sourceColumns", columns); }
    private Map<String, Object> field(String name, String type) { return Map.of("columnName", name, "dataType", type); }
}
