package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** The template must stay local-first, bounded and scoped to the current role. */
class MaterializationTemplateMagicTest {

    @ParameterizedTest
    @CsvSource({"explore,数据抽取方式", "extract,数据抽取方式", "extraction,数据抽取方式",
            "receive,数据推送方式", "push,数据推送方式", "capture,数据抓取方式", "pull,数据抓取方式",
            "upload,数据上报方式", "report,数据上报方式", "proxy,代理访问方式", "agent,代理访问方式",
            "RECEIVE,数据推送方式"})
    void everyAccessModeRetainsItsDisplayLabelAfterSnapshotProcessing(String mode,String label) throws Exception {
        var fixture=new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=null,pool_cfg=? where tid='source-db'",
                "{\"accessMode\":\""+mode+"\"}");
        var result=assertInstanceOf(Map.class,fixture.run(false));
        var props=assertInstanceOf(Map.class,result.get("propList"));
        assertEquals(label,props.get("sourceAccessMode"));
        assertEquals(mode,props.get("sourceAccessModeCode"));
        assertFalse(((List<?>)result.get("tableItems")).isEmpty());
        assertEquals(0,fixture.explorer.calls);assertEquals(0,fixture.connectionConfigCalls);
    }

    @Test
    void manuallyRegisteredPushSnapshotDoesNotWarnAboutAnUnneededSqlDialect() throws Exception {
        var fixture=new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type='receive',pool_cfg=? where tid='source-db'","{\"accessMode\":\"receive\"}");
        var result=assertInstanceOf(Map.class,fixture.run(false));
        assertTrue(((List<?>)result.get("warnings")).isEmpty());
        assertFalse(((List<?>)result.get("tableItems")).isEmpty());
        assertEquals(0,fixture.explorer.calls);assertEquals(0,fixture.fileProbe.calls);assertEquals(0,fixture.connectionConfigCalls);
    }
    private static final String SOURCE_ID = "f93a8e653ac6413e89660f5d77d45769";

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void everyRegisteredSourceTypeCanOpenItsSnapshotWithoutExternalProbing(DatabaseType sourceType) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", sourceType.getCode());

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> props = assertInstanceOf(Map.class, response.get("propList"));
        Map<?, ?> field = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(sourceType.getCode(), props.get("sourceDbType"));
        assertEquals("source-1", props.get("sourceTableId"));
        assertEquals("mysql", props.get("dbType"), "The target dialect must remain independent of the source connector");
        assertEquals("col-1", field.get("sourceColumnId"));
        assertEquals("person_id", field.get("columnName"));
        assertEquals("varchar(32)", field.get("sourceColumnType"));
        assertEquals("varchar", field.get("dataType"));
        assertEquals(32, ((Number) field.get("length")).intValue());
        assertEquals(1, field.get("primaryKey"));
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "pg", "mssql", "kingbasees", "elastic", "FILE_UPLOAD", "data_upload",
            "report", "push", "json_file", "future_connector"})
    void aliasesAndHistoricalSourceMarkersDoNotBlockRegisteredSnapshots(String sourceType) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", sourceType);

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> field = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals("person_id", field.get("columnName"));
        assertEquals("varchar", field.get("sourceDataType"));
        assertEquals("varchar(32)", field.get("sourceColumnType"));
        assertEquals("varchar", field.get("dataType"));
        assertEquals(32, ((Number) field.get("length")).intValue());
        assertEquals(1, field.get("primaryKey"));
        if (sourceType == null || sourceType.isBlank()) {
            assertFalse(((List<?>) response.get("warnings")).isEmpty(), "A missing source dialect must remain visible and editable");
        }
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @ParameterizedTest
    @CsvSource({"oracle,DATE,datetime", "oceanbaseoracle,DATE,datetime", "dameng,DATE,datetime",
            "postgresql,DATE,date", "api,DATE,date", "FILE_UPLOAD,DATE,date",
            "sqlserver,TIMESTAMP,blob", "mssql,TIMESTAMP,blob", "postgresql,TIMESTAMP,datetime",
            "api,TIMESTAMP,datetime", "FILE_UPLOAD,TIMESTAMP,datetime"})
    void optionalSourceDialectPreservesKnownTemporalAndBinarySemantics(String sourceType, String nativeType,
            String logicalType) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", sourceType);
        fixture.jdbc.update("update db_table_column_t set data_type=?,column_type=?,length=0 where tid='col-1'",
                nativeType, nativeType);

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> field = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(nativeType, field.get("sourceDataType"));
        assertEquals(nativeType, field.get("sourceColumnType"));
        assertEquals(logicalType, field.get("dataType"));
        assertEquals(logicalType, field.get("columnType"));
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "api", "ftp", "kafka", "minio", "FILE_UPLOAD"})
    void connectorIndependentSnapshotsKeepConstantReadsForTwentyAndHundredFields(String sourceType) throws Exception {
        List<Integer> reads = new ArrayList<>();
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", sourceType);
            for (int i = 1; i < count; i++) fixture.jdbc.update("insert into db_table_column_t "
                    + "(tid,tenant_id,table_id,column_name,data_type,column_type,length,nullable,is_del) "
                    + "values(?,'police','source-1',?,'varchar','varchar(32)',32,1,0)", "col-" + (i + 1), "f_" + i);

            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
            assertEquals(count + 3, ((List<?>) response.get("tableItems")).size());
            reads.add(fixture.counter.queries);
            assertEquals(0, fixture.explorer.calls);
            assertEquals(0, fixture.fileProbe.calls);
            assertEquals(0, fixture.connectionConfigCalls);
        }
        assertEquals(reads.get(0), reads.get(1), "Snapshot reads must not grow with the field count for " + sourceType);
        assertTrue(reads.get(0) <= 20);
        System.out.println("Connector template " + sourceType + " 20/100 fields: SQL reads=" + reads);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "字符串|字符串(64)|varchar|64|0|0", "字符|字符(8)|varchar|8|0|0",
            "文本|文本|text|0|0|0", "长文本|长文本|text|0|0|0",
            "整数|整数|int|10|0|0", "长整数|长整数|bigint|20|0|0",
            "小数|小数(20,4)|decimal|20|20|4", "浮点数|浮点数|double|0|0|0",
            "日期|日期|date|0|0|0", "时间|时间|datetime|0|0|0",
            "日期时间|日期时间|datetime|0|0|0", "布尔|布尔|tinyint|0|0|0",
            "二进制|二进制|blob|0|0|0", "对象|对象|json|0|0|0", "数组|数组|json|0|0|0"
    })
    void explicitChineseTypeAliasesKeepTheirMeaningForHistoricalSourceZero(String dataType, String columnType,
            String logicalType, int length, int precision, int scale) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type='0' where tid='source-db'");
        fixture.jdbc.update("update db_table_column_t set data_type=?,column_type=?,length=null,precision_length=null,scale=null "
                + "where tid='col-1'", dataType, columnType);

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> props = assertInstanceOf(Map.class, response.get("propList"));
        Map<?, ?> field = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals("0", props.get("sourceDbType"));
        assertEquals("col-1", field.get("sourceColumnId"));
        assertEquals(dataType, field.get("sourceDataType"));
        assertEquals(columnType, field.get("sourceColumnType"));
        assertEquals(logicalType, field.get("dataType"));
        assertEquals(logicalType, field.get("columnType"));
        assertEquals(length, ((Number) field.get("length")).intValue());
        assertEquals(precision, ((Number) field.get("precisionLength")).intValue());
        assertEquals(scale, ((Number) field.get("scale")).intValue());
        assertEquals(1, ((List<?>) response.get("warnings")).size(), "Only the historical source context needs a warning");
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @Test
    void historicalSourceZeroKeepsBoundedChineseStringPrimaryAndOrdinaryFields() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type='0' where tid='source-db'");
        fixture.jdbc.update("update db_table_column_t set data_type='字符串(64)',column_type='字符串(64)',length=null "
                + "where tid='col-1'");
        fixture.jdbc.update("insert into db_table_column_t "
                + "(tid,tenant_id,table_id,column_name,data_type,column_type,primary_key,nullable,ordinal_position,is_del) "
                + "values('col-2','police','source-1','person_name','字符串(200)','字符串(200)',0,1,2,0)");

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        List<?> fields = (List<?>) response.get("tableItems");
        Map<?, ?> primary = assertInstanceOf(Map.class, fields.get(0));
        Map<?, ?> ordinary = assertInstanceOf(Map.class, fields.get(1));
        assertEquals("col-1", primary.get("sourceColumnId"));
        assertEquals("字符串(64)", primary.get("sourceDataType"));
        assertEquals("字符串(64)", primary.get("sourceColumnType"));
        assertEquals("varchar", primary.get("dataType"));
        assertEquals(64, ((Number) primary.get("length")).intValue());
        assertEquals(1, primary.get("primaryKey"));
        assertEquals(0, primary.get("nullable"));
        assertEquals("col-2", ordinary.get("sourceColumnId"));
        assertEquals("字符串(200)", ordinary.get("sourceDataType"));
        assertEquals("字符串(200)", ordinary.get("sourceColumnType"));
        assertEquals("varchar", ordinary.get("dataType"));
        assertEquals(200, ((Number) ordinary.get("length")).intValue());
        assertEquals(0, ordinary.get("primaryKey"));
        assertEquals(1, ordinary.get("nullable"));
        assertEquals(1, ((List<?>) response.get("warnings")).size());
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @Test
    void historicalSourceZeroChineseSnapshotsKeepOneWarningAndConstantReadsForTwentyAndHundredFields() throws Exception {
        List<Integer> reads = new ArrayList<>();
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_datasource_t set db_type='0' where tid='source-db'");
            fixture.jdbc.update("update db_table_column_t set data_type='字符串(64)',column_type='字符串(64)',length=null "
                    + "where tid='col-1'");
            for (int i = 1; i < count; i++) fixture.jdbc.update("insert into db_table_column_t "
                    + "(tid,tenant_id,table_id,column_name,data_type,column_type,primary_key,nullable,is_del) "
                    + "values(?,'police','source-1',?,'字符串(200)','字符串(200)',0,1,0)", "col-" + (i + 1), "f_" + i);

            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
            List<?> fields = (List<?>) response.get("tableItems");
            assertEquals(count + 3, fields.size());
            for (int i = 0; i < count; i++) {
                Map<?, ?> field = assertInstanceOf(Map.class, fields.get(i));
                assertEquals("varchar", field.get("dataType"));
                assertEquals(1 == ((Number) field.get("primaryKey")).intValue() ? 64 : 200,
                        ((Number) field.get("length")).intValue());
            }
            assertEquals(1, ((List<?>) response.get("warnings")).size(), "An unknown source label must not add a warning for each known field");
            reads.add(fixture.counter.queries);
            assertEquals(0, fixture.explorer.calls);
            assertEquals(0, fixture.fileProbe.calls);
            assertEquals(0, fixture.connectionConfigCalls);
        }
        assertEquals(reads.get(0), reads.get(1));
        assertTrue(reads.get(0) <= 20);
        System.out.println("Source 0 Chinese snapshots 20/100 fields: SQL reads=" + reads);
    }

    @Test
    void unboundedSourceTextIsPreservedInsteadOfBeingGuessedAsVarchar() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type='postgresql' where tid='source-db'");
        fixture.jdbc.update("update db_table_column_t set data_type='text',column_type='text',length=0 where tid='col-1'");
        var result = assertInstanceOf(Map.class, fixture.run(false));
        var item = (Map<?, ?>) ((List<?>) result.get("tableItems")).getFirst();
        assertEquals("text", item.get("sourceColumnType"));
        assertEquals("text", item.get("dataType"));
        assertEquals(0, ((Number) item.get("length")).intValue());
        assertEquals(1, item.get("primaryKey"));
        assertEquals(0, fixture.explorer.calls);
    }

    @Test
    void ftpFieldCollectionReadsStayConstantAndUnauthorizedIdsNeverProbe() throws Exception {
        List<Integer> reads = new ArrayList<>();
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_datasource_t set db_type='ftp' where tid='source-db'");
            List<Map<String, Object>> columns = new ArrayList<>();
            for (int i = 0; i < count; i++) columns.add(Map.of("columnName", "field_" + i, "dataType", "varchar"));
            fixture.fileProbe.columns = columns;
            Map<?, ?> result = assertInstanceOf(Map.class, fixture.runCollection());
            assertEquals(count, ((Number) result.get("sourceFieldCount")).intValue());
            assertEquals(1, fixture.fileProbe.calls);
            reads.add(fixture.counter.queries);
        }
        assertEquals(reads.get(0), reads.get(1));
        System.out.println("FTP collection 20/100 fields: SQL reads=" + reads);
        for (boolean foreignTenant : List.of(false, true)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_datasource_t set db_type='ftp' where tid='source-db'");
            if (foreignTenant) fixture.jdbc.update("update db_table_t set tenant_id='other' where tid='source-1'");
            else fixture.jdbc.update("update db_datasource_t set is_del=1 where tid='source-db'");
            assertInstanceOf(ExitValue.class, fixture.runCollection());
            assertEquals(0, fixture.fileProbe.calls);
        }
    }

    @Test
    void hiveTemplateAndSubmitBoundaryNormalizeOnlyTheTargetTableName() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_table_t set table_name='OWNER.Mixed_TABLE' where tid='source-1'");
        fixture.jdbc.update("update db_datasource_t set db_type='hive' where tid='targetdb-0'");
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> props = assertInstanceOf(Map.class, response.get("propList"));
        assertEquals("OWNER.Mixed_TABLE", props.get("sourceTableName"));
        assertEquals("ods_mixed_table", props.get("tableName"));
        String canonical = CanonicalMagicSources.byId("e3320ec899a24426adf5f77e2dab36f9");
        // This isolated helper test does not execute the endpoint's runtime initialization.
        String helpers = canonical.substring(canonical.indexOf("var text ="), canonical.indexOf("var upsertCatalogProp"))
                .replace("var tenantId = tenantRuntime.id()", "");
        String boundary = canonical.substring(canonical.indexOf("// Hive 的物理表名"), canonical.indexOf("// The submitted rows"));
        for (String type : List.of("hive", "mrshive", "oracle", "postgresql", "mysql")) {
            Map<String, Object> submitted = new LinkedHashMap<>(Map.of("tableName", "ODS_Mixed_TABLE", "dbType", "mysql"));
            Map<?, ?> normalized = assertInstanceOf(Map.class, MagicScript.create(helpers + boundary + "return propList", null)
                    .execute(new MagicScriptContext(Map.of("propList", submitted, "targetDatasource", Map.of("dbType", type)))));
            assertEquals(type.equals("hive") || type.equals("mrshive") ? "ods_mixed_table" : "ODS_Mixed_TABLE", normalized.get("tableName"));
        }
    }

    @ParameterizedTest
    @CsvSource({"mysql,datetime", "oceanbasemysql,datetime", "oceanbaseoracle,date", "oracle,date",
            "dameng,date", "postgresql,timestamp", "kingbase8,timestamp", "hive,timestamp",
            "gaussdb,timestamp", "highgo,timestamp", "tdsql_pg,timestamp", "vastbase,timestamp", "sqlserver,datetime2"})
    void registeredTemporalFormatsOverrideVarcharOnlyOnTheTarget(String dialect, String timeType) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.execute("alter table db_table_column_t add data_standard_id varchar(64)");
        fixture.jdbc.execute("alter table db_table_column_t add default_value varchar(64)");
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='targetdb-0'", dialect);
        for (String format : List.of("DATE", "DATETIME")) {
            fixture.jdbc.update("update db_table_column_t set data_standard_id=?,default_value='not-a-date' where tid='col-1'", format);
            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
            Map<?, ?> item = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
            assertEquals(format.equals("DATE") ? "date" : timeType, item.get("dataType"));
            assertEquals(item.get("dataType"), item.get("columnType"));
            assertEquals(format, item.get("standardField"));
            assertEquals("varchar", item.get("sourceDataType"));
            assertEquals("varchar(32)", item.get("sourceColumnType"));
            assertEquals(0, ((Number) item.get("length")).intValue());
            assertNull(item.get("defaultValue"));
            assertEquals(0, fixture.explorer.calls);
        }
    }

    @Test
    void savedGovernanceFallbackAndExplicitClearAreRespected() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.execute("alter table db_table_column_t add data_standard_id varchar(64)");
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'",
                "{\"fields\":[{\"columnName\":\"person_id\",\"standardField\":\"DATE\"}]}");
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals("date", ((Map<?, ?>) ((List<?>) response.get("tableItems")).getFirst()).get("dataType"));
        fixture.jdbc.update("update db_table_column_t set data_standard_id='DATETIME' where tid='col-1'");
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'",
                "{\"fields\":[{\"columnName\":\"person_id\",\"standardField\":\"DATE\",\"standardFieldExplicitlyCleared\":true}]}");
        response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals("varchar", ((Map<?, ?>) ((List<?>) response.get("tableItems")).getFirst()).get("dataType"));
        fixture.jdbc.update("update db_table_t set field_governance_config=null where tid='source-1'");
        fixture.jdbc.update("update db_table_column_t set data_standard_id='LXDH' where tid='col-1'");
        response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals("varchar", ((Map<?, ?>) ((List<?>) response.get("tableItems")).getFirst()).get("dataType"));
    }

    @Test
    void temporalFormatReadsStayConstantForTwentyAndHundredFields() throws Exception {
        List<Integer> reads = new ArrayList<>();
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.execute("alter table db_table_column_t add data_standard_id varchar(64)");
            fixture.jdbc.update("update db_table_column_t set data_standard_id='DATETIME'");
            for (int i = 1; i < count; i++) fixture.jdbc.update("insert into db_table_column_t "
                    + "(tid,tenant_id,table_id,column_name,data_type,column_type,length,nullable,is_del,data_standard_id) "
                    + "values(?,'police','source-1',?,'varchar','varchar(100)',100,1,0,'DATE')", "col-" + (i + 1), "d_" + i);
            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
            assertEquals(count + 3, ((List<?>) response.get("tableItems")).size());
            reads.add(fixture.counter.queries);
            assertEquals(0, fixture.explorer.calls);
        }
        assertEquals(reads.get(0), reads.get(1));
        System.out.println("Temporal template 20/100 fields: SQL reads=" + reads);
    }

    @Test
    void submissionNormalizesGovernedTypesBeforeDdlAndMetadataPersistence() throws Exception {
        String canonical = CanonicalMagicSources.byId("e3320ec899a24426adf5f77e2dab36f9");
        String helpers = canonical.substring(canonical.indexOf("var text ="), canonical.indexOf("var upsertCatalogProp"))
                .replace("var tenantId = tenantRuntime.id()", "")
                + canonical.substring(canonical.indexOf("var materializationTimeType"), canonical.indexOf("var safeIdentifier"));
        for (String dialect : List.of("oracle", "hive", "oceanbasemysql")) {
            String expected = dialect.equals("oracle") ? "date" : dialect.equals("hive") ? "timestamp" : "datetime";
            List<?> rows = assertInstanceOf(List.class, MagicScript.create(helpers
                    + "return ensureOdsSystemColumns([{columnName:'event_time',standardField:'DATETIME',dataType:'varchar',length:200,defaultValue:''},"
                    + "{columnName:'event_date',standardField:'DATE',dataType:'varchar',length:32}], dialect)", null)
                    .execute(new MagicScriptContext(Map.of("dialect", dialect))));
            assertEquals(expected, ((Map<?, ?>) rows.get(0)).get("dataType"));
            assertEquals("date", ((Map<?, ?>) rows.get(1)).get("dataType"));
            assertNull(((Map<?, ?>) rows.get(0)).get("defaultValue"));
            assertEquals(expected, ((Map<?, ?>) rows.get(3)).get("dataType"));
        }
    }

    @Test
    void qualifiedSourceKeepsItsReadIdentityAndOnlyPrefixesTheTargetLeaf() throws Exception {
        for (String sourceName : List.of("TC_RKXT.T_SJYCC_CKB", "public.ODS_person")) {
            Fixture fixture = new Fixture(2);
            fixture.jdbc.update("update db_table_t set table_name=? where tid='source-1'", sourceName);
            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
            Map<?, ?> props = assertInstanceOf(Map.class, response.get("propList"));
            assertEquals(sourceName, props.get("sourceTableName"));
            assertEquals(sourceName.startsWith("TC_RKXT") ? "ODS_T_SJYCC_CKB" : "ODS_person", props.get("tableName"));
            assertEquals(0, fixture.explorer.calls);
        }
    }

    @Test
    void twentyAndHundredLegacyCatalogsAndDomainsUseConstantReadsWithoutPhysicalProbe() throws Exception {
        Fixture twenty = new Fixture(20);
        Fixture hundred = new Fixture(100);

        Map<?, ?> first = assertInstanceOf(Map.class, twenty.run(false));
        Map<?, ?> second = assertInstanceOf(Map.class, hundred.run(false));

        assertEquals(0, twenty.explorer.calls);
        assertEquals(0, hundred.explorer.calls);
        assertEquals(twenty.counter.queries, hundred.counter.queries);
        assertEquals(18, twenty.counter.queries);
        assertEquals(18, hundred.counter.queries);
        assertEquals(1, twenty.counter.legacySiblingBatches, twenty.counter.statements.toString());
        assertEquals(1, hundred.counter.legacySiblingBatches, hundred.counter.statements.toString());
        assertTrue(hundred.counter.queries <= 20, "catalog and domain reads must be batch queries");
        assertEquals("source-1", ((Map<?, ?>) second.get("propList")).get("sourceTableId"));
        assertEquals("targetdb-0", ((Map<?, ?>) second.get("propList")).get("dbId"));
        assertEquals(4, ((Collection<?>) first.get("tableItems")).size());
    }

    @Test
    void missingOrDisplayOnlySnapshotsAreEditableWithoutGuessingAVarcharBound() throws Exception {
        Fixture missing = new Fixture(2);
        missing.jdbc.update("delete from db_table_column_t where table_id='source-1'");
        Map<?, ?> missingResponse = assertInstanceOf(Map.class, missing.run(false));
        assertFalse(((List<?>) missingResponse.get("warnings")).isEmpty());
        assertEquals(0, missing.explorer.calls);

        Fixture displayOnly = new Fixture(2);
        displayOnly.jdbc.update("update db_table_column_t set data_type='数字',column_type='数字',length=0 where table_id='source-1'");
        Map<?, ?> displayResponse = assertInstanceOf(Map.class, displayOnly.run(false));
        assertFalse(((List<?>) displayResponse.get("warnings")).isEmpty());
        Map<?, ?> displayField = (Map<?, ?>) ((List<?>) displayResponse.get("tableItems")).getFirst();
        assertEquals("数字", displayField.get("sourceColumnType"));
        assertEquals("text", displayField.get("dataType"));
        assertEquals(0, displayOnly.explorer.calls);

        Fixture legalHiveString = new Fixture(2);
        legalHiveString.jdbc.update("update db_table_column_t set data_type='string',column_type='string',length=0 where table_id='source-1'");
        Map<?, ?> response = assertInstanceOf(Map.class, legalHiveString.run(false));
        Map<?, ?> firstField = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(0, ((Number) firstField.get("length")).intValue());
        assertEquals("text", firstField.get("dataType"));
        assertEquals(0, legalHiveString.explorer.calls);
    }

    @ParameterizedTest
    @CsvSource({
            "oracle,RAW,RAW(16),0,varbinary,16", "oracle,NCLOB,NCLOB,0,text,0",
            "oracle,BINARY_FLOAT,BINARY_FLOAT,0,float,0", "oracle,BINARY_DOUBLE,BINARY_DOUBLE,0,double,0",
            "oracle,ROWID,ROWID,0,varchar,18", "oracle,UROWID,UROWID,0,varchar,4000",
            "oracle,LONG,LONG,0,text,0", "api,long,long,0,bigint,20",
            "postgresql,USER-DEFINED,uuid,16,varchar,36", "postgresql,bytea,bytea,0,blob,0",
            "postgresql,double precision,float8,0,double,0", "postgresql,real,float4,0,float,0",
            "postgresql,bigint,int8,0,bigint,20", "sqlserver,uniqueidentifier,uniqueidentifier,16,varchar,36"
    })
    void nativeSnapshotsProduceUsableTypesAndKeepSourceIdentity(String dbType, String sourceType,
            String columnType, int length, String targetType, int targetLength) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", dbType);
        fixture.jdbc.update("update db_table_column_t set data_type=?,column_type=?,length=? where tid='col-1'",
                sourceType, columnType, length);
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> item = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(targetType, item.get("dataType"));
        assertEquals(targetType, item.get("columnType"));
        assertEquals(targetLength, ((Number) item.get("length")).intValue());
        assertEquals(sourceType, item.get("sourceDataType"));
        assertEquals(columnType, item.get("sourceColumnType"));
        assertEquals("col-1", item.get("sourceColumnId"));
        assertEquals(0, fixture.explorer.calls);
    }

    @Test
    void decimalArgumentsRecoverMissingSeparatePrecisionAndScale() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_table_column_t set data_type='NUMBER',column_type='NUMBER(24,6)',length=0 where tid='col-1'");
        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("tableItems")).getFirst();
        assertEquals(24, ((Number) item.get("precisionLength")).intValue());
        assertEquals(6, ((Number) item.get("scale")).intValue());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "mysql|enum|enum('Y','N')|text|0|0|0",
            "mysql|set|set('A','B')|text|0|0|0",
            "oracle|NUMBER|NUMBER(*,0)|decimal|18|0|0",
            "oracle|NUMBER|NUMBER(2147483648,0)|decimal|18|0|0",
            "mysql|varchar|varchar(2147483648)|varchar|0|0|0",
            "mysql|decimal|decimal(999999999999999999999999999999999999,2)|decimal|18|0|0",
            "mysql|DECIMAL|DECIMAL(20,999999999999999999999999999999999999)|decimal|20|20|0",
            "mysql|varchar|varchar(32)|varchar|32|0|0",
            "oracle|NUMBER|NUMBER(24,6)|decimal|24|24|6",
            "oracle|RAW|RAW(16)|varbinary|16|0|0"
    })
    void nativeTypeArgumentsRemainEditableWithoutIntegerParsingFailures(String sourceType, String dataType,
            String columnType, String logicalType, int length, int precision, int scale) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type=? where tid='source-db'", sourceType);
        fixture.jdbc.update("update db_table_column_t set data_type=?,column_type=?,length=null,precision_length=null,scale=null "
                + "where tid='col-1'", dataType, columnType);

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        Map<?, ?> field = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(dataType, field.get("sourceDataType"));
        assertEquals(columnType, field.get("sourceColumnType"));
        assertEquals("col-1", field.get("sourceColumnId"));
        assertEquals(logicalType, field.get("dataType"));
        assertEquals(logicalType, field.get("columnType"));
        assertEquals(length, ((Number) field.get("length")).intValue());
        assertEquals(precision, ((Number) field.get("precisionLength")).intValue());
        assertEquals(scale, ((Number) field.get("scale")).intValue());
        assertEquals(1, field.get("primaryKey"));
        if (dataType.equals("enum") || dataType.equals("set")) {
            assertFalse(((List<?>) response.get("warnings")).isEmpty(), "Native enumerations must remain visible for manual review");
        }
        assertEquals(0, fixture.explorer.calls);
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.connectionConfigCalls);
    }

    @Test
    void missingSnapshotsAndUnsupportedNativeTypesReturnEditableFieldsWithActionableWarnings() throws Exception {
        for (String type : List.of("", "数字", "数值", "mystery_type", "业务自定义类型")) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_table_column_t set data_type=?,column_type=? where tid='col-1'", type, type);
            Map<?,?> result = assertInstanceOf(Map.class, fixture.run(false));
            var warnings = (List<?>) result.get("warnings");
            assertFalse(warnings.isEmpty());
            assertTrue(warnings.toString().contains("person_id"));
            assertTrue(warnings.toString().contains("dataType="));
            assertEquals(0, fixture.explorer.calls);
        }
    }

    @Test
    void nativeFieldReadsAndBoundedDiagnosticsStayConstantForTwentyAndHundredFields() throws Exception {
        List<Integer> reads = new ArrayList<>();
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture(1);
            fixture.jdbc.update("update db_table_column_t set data_type='RAW',column_type='RAW(16)',length=16 where tid='col-1'");
            for (int i = 1; i < count; i++) fixture.jdbc.update("insert into db_table_column_t "
                    + "(tid,tenant_id,table_id,column_name,data_type,column_type,length,nullable,is_del) "
                    + "values(?,'police','source-1',?,'uuid','uuid',16,1,0)", "col-" + (i + 1), "f_" + i);
            Map<?, ?> result = assertInstanceOf(Map.class, fixture.run(false));
            assertEquals(count + 3, ((List<?>) result.get("tableItems")).size());
            reads.add(fixture.counter.queries);
            fixture.jdbc.update("update db_table_column_t set data_type='mystery_type',column_type='mystery_type'");
            Map<?,?> warningResult = assertInstanceOf(Map.class, fixture.run(false));
            assertEquals(10, ((List<?>) warningResult.get("warnings")).size());
            assertEquals(count+3, ((List<?>) warningResult.get("tableItems")).size());
            assertEquals(0, fixture.explorer.calls);
        }
        assertEquals(reads.get(0), reads.get(1));
        System.out.println("Native type template 20/100 fields: SQL reads=" + reads);
    }

    @Test
    void deletedOrForeignSourceIsRejectedBeforeExplicitExternalRefresh() throws Exception {
        Fixture hidden = new Fixture(2);
        hidden.jdbc.update("update db_datasource_t set is_del=1 where tid='source-db'");
        assertInstanceOf(ExitValue.class, hidden.run(true));
        assertEquals(0, hidden.explorer.calls);

        Fixture foreign = new Fixture(2);
        foreign.jdbc.update("update db_table_t set tenant_id='other' where tid='source-1'");
        assertInstanceOf(ExitValue.class, foreign.run(true));
        assertEquals(0, foreign.explorer.calls);
    }

    @Test
    void retiredRefreshAndFtpResolveParametersNeverOverrideRegisteredSnapshots() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.explorer.fail = true;
        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run(true));
        assertEquals(0, fixture.explorer.calls);
        assertEquals(4, ((List<?>) result.get("tableItems")).size());
        assertEquals("col-1", ((Map<?, ?>) ((List<?>) result.get("tableItems")).getFirst()).get("sourceColumnId"));
        fixture.jdbc.update("update db_datasource_t set db_type='ftp' where tid='source-db'");
        result = assertInstanceOf(Map.class, fixture.run(true));
        assertEquals(0, fixture.fileProbe.calls);
        assertEquals(0, fixture.fileProbe.locatorCalls);
        assertEquals("source-1", ((Map<?, ?>) result.get("propList")).get("sourceTableId"));
    }

    @Test
    void currentTenantTargetRemainsSelectableAndForeignColumnsAreIgnored() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.scope.deniedId = "targetdb-0";
        fixture.jdbc.update("insert into db_table_column_t values('foreign-col','other','source-1','secret','varchar','varchar(32)',32,0,0,0,1,2,0)");
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals("targetdb-0", ((Map<?, ?>) response.get("propList")).get("dbId"));
        assertEquals(4, ((Collection<?>) response.get("tableItems")).size());
        assertEquals(0, fixture.explorer.calls);
    }

    @Test
    void foreignTenantLegacyCatalogPropertiesCannotChangeFieldRules() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.jdbc.update("delete from da_prop_t where prop_name='fieldGovernanceConfig'");
        fixture.jdbc.update("insert into da_prop_t values('other','foreign-catalog','sourceTableId','source-1',0)");
        fixture.jdbc.update("insert into da_prop_t values('other','foreign-catalog','fieldGovernanceConfig',?,0)",
                "{\"fields\":[{\"columnName\":\"person_id\",\"dictionaryRelation\":{\"enabled\":true,\"sourceType\":\"dictionary\"}}]}");

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals(4, ((Collection<?>) response.get("tableItems")).size());
        assertEquals(0, fixture.explorer.calls);
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Counter counter = new Counter();
        final Scope scope = new Scope();
        final Explorer explorer = new Explorer();
        final FtpSourceProbeStub fileProbe = new FtpSourceProbeStub();
        int connectionConfigCalls;

        Fixture(int count) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:material_template_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("material-template-test"));
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override public boolean match(String url) { return url.contains(":h2:"); }
            });
            db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            jdbc = new JdbcTemplate(source);

            jdbc.execute("create table da_prop_t(tenant_id varchar(32),parent_id varchar(64),prop_name varchar(100),prop_value varchar(4000),is_del int)");
            jdbc.execute("create table da_catalog_t(tid varchar(64),tenant_id varchar(32),source_table_id varchar(64),field_governance_config varchar(4000),updated_time timestamp,is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(100),field_governance_config varchar(4000),is_del int)");
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(100),db_type varchar(32),pool_cfg varchar(100),show_connect int,node_id varchar(64),is_del int)");
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),column_name varchar(100),data_type varchar(64),column_type varchar(64),length int,precision_length int,scale int,primary_key int,nullable int,ordinal_position int,is_del int)");
            jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(32),dict_code varchar(64),parent_id varchar(64),dict_name varchar(64),sort_no int,is_del int)");
            jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(32),target_type varchar(32),target_id varchar(64),datasource_id varchar(64),updated_time timestamp,is_del int)");
            jdbc.update("insert into db_table_t values('source-1','police','source-db','person',null,0)");
            jdbc.update("insert into db_datasource_t values('source-db','police','source','hive','{}',1,null,0)");
            jdbc.update("insert into db_table_column_t values('col-1','police','source-1','person_id','varchar','varchar(32)',32,0,0,1,0,1,0)");
            jdbc.update("insert into sym_dict_t values('root','police','dataSourceType','0','root',0,0)");
            jdbc.update("insert into sym_dict_t values('ods','police','ods','root','ODS',0,0)");
            for (int i = 0; i < count; i++) {
                String domainId = "domain-" + i;
                String dbId = "targetdb-" + i;
                String catalogId = "catalog-" + i;
                jdbc.update("insert into da_prop_t values('police',?,'sourceTableId','source-1',0)", catalogId);
                jdbc.update("insert into sym_dict_t values(?,'police','original','ods','domain',?,0)", domainId, i);
                jdbc.update("insert into dwm_center_layer_source_t values('police','domain',?,?,current_timestamp,0)", domainId, dbId);
                jdbc.update("insert into db_datasource_t values(?,'police',?,'mysql','{}',1,null,0)", dbId, dbId);
            }
            jdbc.update("insert into da_prop_t values('police',?,'fieldGovernanceConfig',?,0)",
                    "catalog-" + (count - 1), "{\"fields\":[{\"columnName\":\"person_id\",\"primaryKey\":true}]}");
        }

        public Map<String, Object> connectionConfig(String id) {
            connectionConfigCalls++;
            return Map.of("jdbcURL", "jdbc:hive2://example.invalid:10000/default", "database", "default");
        }

        Object run(boolean refresh) throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID).replace("\r\n", "\n")
                    .replaceAll("(?m)^import (?:'@/common/datasourceConnectionConfig' as datasourceConnectionConfig|tenantRuntime|portableDdl|dataScope|com\\.linewell\\.dataelement\\.metautil\\.service\\.MetadataExplorerService as metadataExplorerService|com\\.linewell\\.dataelement\\.metautil\\.structured\\.StructuredSourceProbeService as structuredFileProbe)\\n", "");
            String prelude = "var datasourceConnectionConfig = (action, id, values, clearAll) => fixture.connectionConfig(id);\n";
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("body", Map.of("tid", "source-1", "refreshPhysicalColumns", refresh, "resolveSourceFile", true));
            inputs.put("db", db);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("jsons", new JsonModule());
            inputs.put("metadataExplorerService", explorer);
            inputs.put("fixture", this);
            inputs.put("portableDdl", new com.linewell.dataelement.metautil.ddl.PortableDdlConverter());
            inputs.put("structuredFileProbe", fileProbe);
            counter.reset();
            return MagicScript.create(prelude + script, null).execute(new MagicScriptContext(inputs));
        }

        Object runCollection() throws Exception {
            String script = CanonicalMagicSources.byId("metadata_columns_collect_24").replace("\r\n", "\n")
                    .replaceAll("(?m)^import (?:'@/common/datasourceConnectionConfig' as datasourceConnectionConfig|tenantRuntime|portableDdl|dataScope|hwhive|metadataAsset|jsons|com\\.linewell\\.dataelement\\.metautil\\.structured\\.StructuredSourceProbeService as structuredSourceProbeService|com\\.linewell\\.dataelement\\.metautil\\.service\\.MetadataExplorerService as metadataExplorerService)\\n", "");
            String prelude = "var datasourceConnectionConfig = (action, id, values, clearAll) => fixture.connectionConfig(id);\n";
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("body", Map.of("tableId", "source-1", "force", true, "dryRun", true,
                    "sourceFileConfig", Map.of("charset", "GB18030", "delimiter", "\t")));
            inputs.put("db", db);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("jsons", new JsonModule());
            inputs.put("metadataExplorerService", explorer);
            inputs.put("structuredSourceProbeService", fileProbe);
            inputs.put("fixture", this);
            inputs.put("portableDdl", new com.linewell.dataelement.metautil.ddl.PortableDdlConverter());
            counter.reset();
            return MagicScript.create(prelude + script, null).execute(new MagicScriptContext(inputs));
        }
    }

    public static final class Scope {
        String deniedId;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
        }
    }

    public static final class Explorer {
        int calls;
        boolean fail;
        public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
            calls++;
            if (fail) { throw new IllegalStateException("simulated source outage"); }
            ColumnInfo column = new ColumnInfo();
            column.setColumnName("person_id");
            column.setDataType("varchar");
            column.setColumnType("varchar(32)");
            column.setLength(32L);
            column.setPrimaryKey(true);
            column.setNullable(false);
            column.setOrdinalPosition(1);
            return List.of(column);
        }
    }

    private static final class Counter implements SQLInterceptor {
        int queries;
        int legacySiblingBatches;
        final List<String> statements = new ArrayList<>();
        void reset() { queries = 0; legacySiblingBatches = 0; statements.clear(); }
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            String statement = sql.getSql().stripLeading().toLowerCase();
            if (!statement.startsWith("select")) { return; }
            queries++;
            statements.add(statement);
            if (statement.startsWith("select * from da_prop_t")
                    && statement.matches("(?s).*parent_id\\s+in\\s*\\(.*")) {
                legacySiblingBatches++;
            }
        }
    }
}
