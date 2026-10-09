package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
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
    private static final String SOURCE_ID = "f93a8e653ac6413e89660f5d77d45769";

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
    void missingOrDisplayOnlySnapshotsFailInsteadOfGuessingVarcharLength() throws Exception {
        Fixture missing = new Fixture(2);
        missing.jdbc.update("delete from db_table_column_t where table_id='source-1'");
        assertInstanceOf(ExitValue.class, missing.run(false));
        assertEquals(0, missing.explorer.calls);

        Fixture displayOnly = new Fixture(2);
        displayOnly.jdbc.update("update db_table_column_t set data_type='字符串',column_type='字符串',length=0 where table_id='source-1'");
        assertInstanceOf(ExitValue.class, displayOnly.run(false));
        assertEquals(0, displayOnly.explorer.calls);

        Fixture legalHiveString = new Fixture(2);
        legalHiveString.jdbc.update("update db_table_column_t set data_type='string',column_type='string',length=0 where table_id='source-1'");
        Map<?, ?> response = assertInstanceOf(Map.class, legalHiveString.run(false));
        Map<?, ?> firstField = assertInstanceOf(Map.class, ((List<?>) response.get("tableItems")).getFirst());
        assertEquals(255, ((Number) firstField.get("length")).intValue());
        assertEquals(0, legalHiveString.explorer.calls);
    }

    @Test
    void hiddenOrForeignSourceIsRejectedBeforeExplicitExternalRefresh() throws Exception {
        Fixture hidden = new Fixture(2);
        hidden.scope.deniedId = "source-db";
        assertInstanceOf(ExitValue.class, hidden.run(true));
        assertEquals(0, hidden.explorer.calls);

        Fixture foreign = new Fixture(2);
        foreign.jdbc.update("update db_table_t set tenant_id='other' where tid='source-1'");
        assertInstanceOf(ExitValue.class, foreign.run(true));
        assertEquals(0, foreign.explorer.calls);
    }

    @Test
    void physicalRefreshIsExplicitAndItsFailureIsReported() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.explorer.fail = true;
        assertInstanceOf(ExitValue.class, fixture.run(true));
        assertEquals(1, fixture.explorer.calls);

        fixture.explorer.fail = false;
        Map<?, ?> refreshed = assertInstanceOf(Map.class, fixture.run(true));
        assertEquals(2, fixture.explorer.calls);
        assertEquals("source-1", ((Map<?, ?>) refreshed.get("propList")).get("sourceTableId"));
    }

    @Test
    void hiddenTargetIsNeverChosenAsDefaultAndForeignColumnsAreIgnored() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.scope.deniedId = "targetdb-0";
        fixture.jdbc.update("insert into db_table_column_t values('foreign-col','other','source-1','secret','varchar','varchar(32)',32,0,0,0,1,2,0)");
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(false));
        assertEquals("targetdb-1", ((Map<?, ?>) response.get("propList")).get("dbId"));
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
            return Map.of("jdbcURL", "jdbc:hive2://example.invalid:10000/default", "database", "default");
        }

        Object run(boolean refresh) throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID).replace("\r\n", "\n")
                    .replaceAll("(?m)^import (?:'@/common/datasourceConnectionConfig' as datasourceConnectionConfig|tenantRuntime|dataScope|com\\.linewell\\.dataelement\\.metautil\\.service\\.MetadataExplorerService as metadataExplorerService)\\n", "");
            String prelude = "var datasourceConnectionConfig = (action, id, values, clearAll) => fixture.connectionConfig(id);\n";
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("body", Map.of("tid", "source-1", "refreshPhysicalColumns", refresh));
            inputs.put("db", db);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("jsons", new JsonModule());
            inputs.put("metadataExplorerService", explorer);
            inputs.put("fixture", this);
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
