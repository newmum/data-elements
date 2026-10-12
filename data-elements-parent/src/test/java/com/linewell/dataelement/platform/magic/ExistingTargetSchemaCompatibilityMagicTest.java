package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
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
    void runtimeBoundaryAlsoRejectsEditedClobKeysOnAnOlderBackend() throws Exception {
        String source = CanonicalMagicSources.byId("a0b5bba38fbf428c8e3646864d83d56b");
        String helper = source.substring(source.indexOf("var validateLobKeys ="), source.indexOf("if (body?.batchValidateOnly"));
        String imports = "import com.alibaba.druid.DbType\nimport com.alibaba.druid.sql.SQLUtils\n"
                + "import com.alibaba.druid.sql.ast.statement.SQLUniqueConstraint\nimport com.alibaba.druid.sql.ast.statement.SQLColumnUniqueKey\n"
                + "import com.alibaba.druid.sql.ast.statement.SQLColumnPrimaryKey\n";
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.OCEANBASE_ORACLE);
        for (String definition : List.of("UNID CLOB PRIMARY KEY", "UNID CLOB, PRIMARY KEY(UNID)", "UNID CLOB UNIQUE")) {
            var result = assertInstanceOf(ExitValue.class, MagicScript.create(imports + helper + "validateLobKeys(ddl,config)\nreturn true", null)
                    .execute(new MagicScriptContext(Map.of("ddl", "CREATE TABLE events(" + definition + ")", "config", config))));
            assertEquals(400, result.getValues()[0]);
            assertTrue(result.getValues()[1].toString().contains("UNID"));
        }
    }
    @Test
    void productionStringDatesAndMissingTranslationColumnsAreRejected() throws Exception {
        var requested = List.of(Map.of("columnName", "CSRQ", "dataType", "date"),
                Map.of("columnName", "ZJBGSJ", "dataType", "timestamp"),
                Map.of("columnName", "XB_cn", "dataType", "varchar"),
                Map.of("columnName", "ODS_UUID", "dataType", "varchar"));
        var physical = List.of(column("CSRQ", "string"), column("ZJBGSJ", "string"));
        var result = assertInstanceOf(ExitValue.class, check(requested, physical));
        assertEquals(409, result.getValues()[0]);
        String message = result.getValues()[1].toString();
        for (String field : List.of("CSRQ", "ZJBGSJ", "XB_cn", "ODS_UUID")) assertTrue(message.toLowerCase().contains(field.toLowerCase()));
        assertEquals("string", physical.getFirst().getDataType());
        assertEquals(2, physical.size());
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
            assertEquals(count, assertInstanceOf(List.class, check(requested, physical)).size());
            // No database or external service is available to the helper context.
            assertEquals(count, physical.size());
        }
    }

    private static Object check(Object requested, Object physical) throws Exception {
        String source = CanonicalMagicSources.byId("a0b5bba38fbf428c8e3646864d83d56b");
        String helpers = source.substring(source.indexOf("var textOf ="), source.indexOf("var loadAsset ="))
                + source.substring(source.indexOf("var normalizedType ="), source.indexOf("var hiveColumns ="));
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.HIVE);
        return MagicScript.create("import " + ColumnInfo.class.getName() + "\n" + helpers
                + "return reusedTableItems(requested,physical,config,'ods_t_bdry_jbxx')", null)
                .execute(new MagicScriptContext(Map.of("requested", requested, "physical", physical, "config", config)));
    }

    private static ColumnInfo column(String name, String type) {
        var column = new ColumnInfo(); column.setColumnName(name); column.setDataType(type);
        column.setColumnType(type); column.setNullable(true); return column;
    }
}
