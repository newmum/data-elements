package com.linewell.dataelement.metautil.ddl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PortableDdlConverterTest {
    @ParameterizedTest
    @CsvSource({
        "字符串(64),VARCHAR,varchar", "字符串(200),VARCHAR,varchar", "字符(8),CHAR,varchar",
        "文本,TEXT,text", "长文本,TEXT,text", "整数,INTEGER,int", "长整数,BIGINT,bigint",
        "'小数(20,4)',DECIMAL,decimal", "浮点数,DOUBLE,double", "日期,DATE,date", "时间,TIME,datetime",
        "日期时间,TIMESTAMP,datetime", "布尔,BOOLEAN,tinyint", "二进制,BLOB,blob", "对象,JSON,json", "数组,JSON,json"
    })
    void explicitHistoricalDisplayTypesUseTheSameColumnModel(String raw, String base, String logical) {
        var converter = new PortableDdlConverter();
        assertEquals(base, PortableColumnType.base(raw));
        assertEquals(logical, converter.logicalType(raw, ""));
        assertEquals(raw, converter.originTypeForEditor(logical, raw, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"字符串(64)", "字符串(200)"})
    @SuppressWarnings("unchecked")
    void historicalBoundedStringKeyKeepsLengthAndDoesNotBecomeOracleClob(String raw) {
        var column = new ColumnInfo(); column.setColumnName("id"); column.setColumnType(raw);
        column.setPrimaryKey(true); column.setSourceDatabaseType("");
        var prepared = new PortableDdlConverter().prepare(List.of(column), DatabaseType.ORACLE);
        var ready = ((List<ColumnInfo>) prepared.get("columns")).getFirst();
        String argument = raw.substring(raw.indexOf('('));
        assertEquals("VARCHAR2" + argument, ready.getColumnType());
        assertTrue(ready.getPrimaryKey()); assertTrue(((List<?>) prepared.get("warnings")).isEmpty());
        assertEquals(raw, column.getColumnType()); assertEquals("", column.getSourceDatabaseType());
    }

    @Test
    void historicalDecimalKeepsItsPrecisionAndScaleAndAmbiguousNumbersRemainUnresolved() {
        var column = new ColumnInfo(); column.setColumnType("小数(20,4)");
        assertEquals("NUMBER(20,4)", PortableColumnType.render(DatabaseType.ORACLE, column, column.getColumnType()));
        assertEquals("NUMERIC(20,4)", PortableColumnType.render(DatabaseType.POSTGRESQL, column, column.getColumnType()));
        for (String raw : List.of("数字", "数值", "数字(20)", "数值(20,4)")) {
            assertEquals(PortableColumnType.Kind.UNKNOWN, PortableColumnType.kind(raw));
            assertEquals("text", new PortableDdlConverter().logicalType(raw, null));
        }
    }

    @Test
    void historicalTemporalLabelsKeepOriginSemanticsWithoutBecomingSqlServerRowversion() {
        var converter = new PortableDdlConverter();
        assertEquals("date", converter.logicalType("日期", null));
        assertEquals("datetime", converter.logicalType("日期", "oracle12+"));
        assertEquals("datetime", converter.logicalType("日期时间", "MS SQL 2012+"));
        assertEquals("blob", converter.logicalType("TIMESTAMP", "MS SQL 2012+"));
        var column = new ColumnInfo(); column.setSourceDatabaseType(" Oracle 12+ ");
        assertEquals("TIMESTAMP", PortableColumnType.render(DatabaseType.POSTGRESQL, column, "日期"));
        assertEquals("DATE", PortableColumnType.render(DatabaseType.ORACLE, column, "日期"));
        column.setSourceDatabaseType("MS SQL 2012+");
        assertEquals("DATETIME2", PortableColumnType.render(DatabaseType.SQLSERVER, column, "日期时间"));
        assertEquals("VARBINARY(8)", PortableColumnType.render(DatabaseType.SQLSERVER, column, "TIMESTAMP"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void everyRegisteredSourceCanRenderSnapshotFieldsWithoutAConnection(DatabaseType source) {
        var converter = new PortableDdlConverter();
        assertEquals("varchar", converter.logicalType("varchar(36)", source.getCode()));
        assertEquals("int", converter.logicalType("integer", source.getCode()));
        assertEquals("json", converter.logicalType("json", source.getCode()));
        assertEquals("text", converter.logicalType("vendor_extension", source.getCode()));
        assertEquals("uuid", converter.originTypeForEditor("varchar", "uuid", source.getCode()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "unknown-vendor", "oceanbase"})
    void optionalMissingOrUnrecognizedSourceUsesNeutralSnapshotSemantics(String source) {
        var converter = new PortableDdlConverter();
        assertEquals("varchar", converter.logicalType("varchar(36)", source));
        assertEquals("float", converter.logicalType("float", source));
        assertEquals("date", converter.logicalType("date", source));
        assertEquals("datetime", converter.logicalType("timestamp", source));
        assertEquals("bigint", converter.logicalType("long", source));
        assertEquals("text", converter.logicalType("vendor_extension", source));
        assertEquals("uuid", converter.originTypeForEditor("varchar", "uuid", source));
        assertEquals("timestamp", converter.originTypeForEditor("datetime", "timestamp", source));
        assertNull(converter.originTypeForEditor("int", "uuid", source));
    }

    @ParameterizedTest
    @CsvSource({
        "oracle,date,datetime", "oracle12+,DATE,datetime", " Oracle 12+ ,FLOAT,decimal",
        "dm,FLOAT,decimal", "oceanbase_oracle,LONG,text",
        "mssql,TIMESTAMP,blob", "MS SQL 2012+,timestamp,blob", "MS SQL 2008,timestamp,blob",
        "SQL Server,timestamp,blob", "pg,date,date", "kingbasees,timestamp,datetime",
        "elastic,LONG,bigint", "mysql,LONG,bigint", "ftp,LONG,bigint"
    })
    void registeredAliasesKeepVendorSpecificSnapshotSemantics(String source, String raw, String expected) {
        var converter = new PortableDdlConverter();
        assertEquals(expected, converter.logicalType(raw, source));
        assertEquals(raw, converter.originTypeForEditor(expected, raw, source));
    }

    @Test
    void parenthesizedSourceConstantsConvertAcrossEveryRelationalTargetFamily() {
        for (DatabaseType target : List.of(DatabaseType.MYSQL, DatabaseType.MARIADB, DatabaseType.OCEANBASE_MYSQL,
                DatabaseType.ORACLE, DatabaseType.OCEANBASE_ORACLE, DatabaseType.DAMENG, DatabaseType.POSTGRESQL,
                DatabaseType.KINGBASE, DatabaseType.GAUSSDB, DatabaseType.HIGHGO, DatabaseType.SQLSERVER,
                DatabaseType.DB2, DatabaseType.HETU, DatabaseType.HIVE)) {
            for (String raw : List.of("'Y'::character varying", "('Y'::character varying)", "(('Y'::character varying))")) {
                var converted = PortableDefaultExpression.convert(raw, "postgresql", target);
                assertFalse(converted.unresolved(), target + " " + raw);
                assertEquals(target == DatabaseType.HIVE ? null : "'Y'", converted.value());
            }
        }
        assertEquals("'a::b'", PortableDefaultExpression.convert("'a::b'", "postgresql", DatabaseType.ORACLE).value());
    }

    @Test
    void commonTemporalAndBooleanDefaultsUseTargetSemanticsWithoutRemovingArbitraryCasts() {
        assertEquals("CURRENT_TIMESTAMP", PortableDefaultExpression.convert("now()::timestamp", "postgresql", DatabaseType.ORACLE).value());
        assertEquals("1", PortableDefaultExpression.convert("TRUE::boolean", "postgresql", DatabaseType.SQLSERVER).value());
        assertEquals("DATE '2026-10-12'", PortableDefaultExpression.convert("'2026-10-12'::date", "postgresql", DatabaseType.ORACLE).value());
        var numeric = PortableDefaultExpression.convert("1.4::integer", "postgresql", DatabaseType.ORACLE);
        assertTrue(numeric.unresolved()); assertNotEquals("1.4", numeric.value());
        assertFalse(PortableDefaultExpression.convert("nextval('s'::regclass)", "postgresql", DatabaseType.KINGBASE).unresolved());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importedDdlBecomesOneColumnModelWithCommentsKeysPrecisionAndDefaults() {
        var converter = new PortableDdlConverter();
        var imported = converter.read("""
                CREATE TABLE public.people (
                    id bigint PRIMARY KEY,
                    rybh varchar(36) DEFAULT ('Y'::character varying) NOT NULL,
                    amount numeric(20,4),
                    created_at timestamp DEFAULT now()
                );
                COMMENT ON TABLE public.people IS '人员';
                COMMENT ON COLUMN public.people.rybh IS '人员编号';
                """, "postgresql");
        var columns = (List<ColumnInfo>) imported.get("columns");
        assertEquals(4, columns.size()); assertTrue(columns.getFirst().getPrimaryKey());
        assertEquals("人员编号", columns.get(1).getColumnComment()); assertFalse(columns.get(1).getNullable());
        assertEquals(20, columns.get(2).getPrecision()); assertEquals(4, columns.get(2).getScale());
        for (DatabaseType target : List.of(DatabaseType.MYSQL, DatabaseType.ORACLE, DatabaseType.SQLSERVER, DatabaseType.POSTGRESQL, DatabaseType.HIVE)) {
            var converted = converter.prepare(columns, target);
            var ready = (List<ColumnInfo>) converted.get("columns");
            var config = new DataSourceConfig(); config.setDatabaseType(target); config.setDatabase("ods");
            if (target == DatabaseType.ORACLE) config.setSchema("ODS");
            String ddl = target == DatabaseType.HIVE ? HiveTableDdl.generate("ods_people", ready, "人员")
                    : new MySqlMetadataExplorer().generateCreateTableDdl(config, "ods_people", ready, "人员");
            var parsed = new CanvasTableDdl().parse(ddl, config);
            assertEquals("ods_people", parsed.get("lookupName"));
            assertThat(ddl).doesNotContain("::");
            if (target != DatabaseType.HIVE) assertThat(ddl).contains("DEFAULT 'Y'", "CURRENT_TIMESTAMP");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewRetainsIncompatibleExpressionsAndKeysForEditingInsteadOfRejectingTheTable() {
        var converter = new PortableDdlConverter();
        var column = new ColumnInfo(); column.setColumnName("rybh"); column.setColumnType("text");
        column.setDefaultValue("nextval('s'::regclass)"); column.setSourceDatabaseType("postgresql"); column.setPrimaryKey(true);
        var prepared = converter.prepare(List.of(column), DatabaseType.ORACLE);
        assertEquals(2, ((List<?>) prepared.get("warnings")).size());
        var result = ((List<ColumnInfo>) prepared.get("columns")).getFirst();
        assertTrue(result.getPrimaryKey()); assertNotNull(result.getDefaultValue());
        assertEquals("CLOB", result.getColumnType()); assertEquals("text", column.getColumnType());
    }

    @Test
    void twentyAndHundredFieldsConvertWithoutAnyDatasourceOrTenantDependency() {
        var converter = new PortableDdlConverter();
        for (int count : List.of(20,100)) {
            var columns = IntStream.range(0,count).mapToObj(index -> {
                var column = new ColumnInfo(); column.setColumnName("f"+index); column.setColumnType("varchar(36)");
                column.setDefaultValue("('Y'::varchar)"); return column;
            }).toList();
            assertEquals(count, ((List<?>) converter.prepare(columns, DatabaseType.ORACLE).get("columns")).size());
        }
    }

    @Test
    void identifierModelHandlesQuotedDotsReservedWordsUnicodeAndRejectsExecutableText() {
        assertEquals(List.of(new DdlDialect.Identifier("ODS",false),new DdlDialect.Identifier("a.b",true)), DdlDialect.names("ODS.\"a.b\""));
        assertEquals("人员 表", DdlDialect.names("[人员 表]").getFirst().value());
        assertThrows(IllegalArgumentException.class, () -> DdlDialect.names("t;DROP TABLE other"));
        assertThrows(IllegalArgumentException.class, () -> DdlDialect.names("t -- comment"));
        assertEquals("[a]]b]", DdlDialect.quote(DatabaseType.SQLSERVER,"a]b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotedColumnsAndNativeUuidTimeZoneSemanticsSurviveTheEditableModel() {
        var converter = new PortableDdlConverter();
        var imported = converter.read("CREATE TABLE \"People\"(\"Case.ID\" uuid, \"MixedCase\" timestamptz)","postgresql");
        var columns = (List<ColumnInfo>) imported.get("columns");
        assertEquals("Case.ID",columns.getFirst().getColumnName());
        assertTrue(columns.getFirst().getQuotedIdentifier());
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.POSTGRESQL);
        String ddl = new MySqlMetadataExplorer().generateCreateTableDdl(config,(String) imported.get("tableName"),
                (List<ColumnInfo>) converter.prepare(columns,DatabaseType.POSTGRESQL).get("columns"),"");
        assertThat(ddl).contains("\"People\"", "\"Case.ID\" UUID", "\"MixedCase\" TIMESTAMP WITH TIME ZONE");
        assertEquals("uuid",converter.originTypeForEditor("varchar","uuid","postgresql"));
        assertEquals("timestamptz",converter.originTypeForEditor("datetime","timestamptz","postgresql"));
        assertNull(converter.originTypeForEditor("int","uuid","postgresql"));
    }
}
