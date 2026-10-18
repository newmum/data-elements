package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.ddl.PortableDdlConverter;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The real reuse guard must retain schema differences without mutating existing Hive data. */
class ExistingTargetSchemaCompatibilityMagicTest {
    @Test
    @SuppressWarnings("unchecked")
    void importedClobKeysRemainEditableWithOneConversionWarning() {
        var converter = new PortableDdlConverter();
        for (String definition : List.of("UNID CLOB PRIMARY KEY", "UNID CLOB, PRIMARY KEY(UNID)", "UNID CLOB UNIQUE")) {
            var imported = converter.read("CREATE TABLE events(" + definition + ")", "oceanbase-oracle");
            var result = converter.prepare((List<ColumnInfo>) imported.get("columns"), DatabaseType.OCEANBASE_ORACLE);
            assertEquals(1, ((List<?>) result.get("warnings")).size());
            assertTrue(result.get("warnings").toString().contains("UNID"));
            assertEquals(1, ((List<?>) result.get("columns")).size());
        }
    }
    @Test
    void missingTranslationColumnsAreRejectedWhileHiveStringDatesProduceWarnings() throws Exception {
        var requested = List.of(Map.of("columnName", "CSRQ", "dataType", "date"),
                Map.of("columnName", "ZJBGSJ", "dataType", "timestamp"),
                Map.of("columnName", "XB_cn", "dataType", "varchar"),
                Map.of("columnName", "ODS_UUID", "dataType", "varchar"));
        var physical = List.of(column("CSRQ", "string"), column("ZJBGSJ", "string"));
        var warnings = new ArrayList<String>();
        var result = assertInstanceOf(ExitValue.class, check(requested, physical, warnings));
        assertEquals(409, result.getValues()[0]);
        String message = result.getValues()[1].toString();
        for (String field : List.of("XB_cn", "ODS_UUID")) assertTrue(message.toLowerCase().contains(field.toLowerCase()));
        assertFalse(message.contains("CSRQ"));
        assertFalse(message.contains("ZJBGSJ"));
        assertEquals(2, warnings.size());
        for (String field : List.of("CSRQ", "ZJBGSJ")) {
            assertTrue(warnings.stream().anyMatch(warning -> warning.contains(field) && warning.contains("沿用已有Hive表的文本类型")));
        }
        assertEquals("string", physical.getFirst().getDataType());
        assertEquals(2, physical.size());
    }

    @Test
    void completeHiveScalarSnapshotKeepsPhysicalStringTypesAndReturnsWarnings() throws Exception {
        var types = List.of("decimal", "date", "timestamp", "boolean");
        var requested = new ArrayList<Map<String, Object>>();
        var physical = new ArrayList<ColumnInfo>();
        for (String type : types) {
            requested.add(Map.of("columnName", "field_" + type, "dataType", type));
            physical.add(column("field_" + type, "string"));
        }
        var warnings = new ArrayList<String>();
        var result = assertInstanceOf(List.class, check(requested, physical, warnings));
        assertEquals(types.size(), result.size());
        assertEquals(types.size(), warnings.size());
        for (Object field : result) {
            assertEquals("string", assertInstanceOf(Map.class, field).get("dataType"));
        }
        assertTrue(physical.stream().allMatch(column -> "string".equals(column.getDataType())));
    }

    @Test
    void twentyAndHundredCompatibleHiveFieldsUseOnlyTheProvidedSchemaSnapshot() throws Exception {
        for (int count : List.of(20, 100)) {
            var requested = new ArrayList<Map<String, Object>>();
            var physical = new ArrayList<ColumnInfo>();
            for (int i = 0; i < count; i++) {
                requested.add(Map.of("columnName", "field_" + i, "dataType", "varchar", "length", 255));
                physical.add(column("field_" + i, "string"));
            }
            var warnings = new ArrayList<String>();
            assertEquals(count, assertInstanceOf(List.class, check(requested, physical, warnings)).size());
            assertTrue(warnings.isEmpty());
            // No database or external service is available to the helper context.
            assertEquals(count, physical.size());
        }
    }

    private static Object check(Object requested, Object physical, List<String> warnings) throws Exception {
        String source = CanonicalMagicSources.byId("a0b5bba38fbf428c8e3646864d83d56b");
        String helpers = source.substring(source.indexOf("var textOf ="), source.indexOf("var loadAsset ="))
                + source.substring(source.indexOf("var normalizedType ="), source.indexOf("var hiveValue ="));
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.HIVE);
        return MagicScript.create("import " + ColumnInfo.class.getName() + "\n"
                + "import " + DatabaseType.class.getName() + "\n" + helpers
                + "return reusedTableItems(requested,physical,config,'ods_t_bdry_jbxx',warnings)", null)
                .execute(new MagicScriptContext(Map.of("requested", requested, "physical", physical,
                        "config", config, "warnings", warnings)));
    }

    private static ColumnInfo column(String name, String type) {
        var column = new ColumnInfo(); column.setColumnName(name); column.setDataType(type);
        column.setColumnType(type); column.setNullable(true); return column;
    }
}
