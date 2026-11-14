package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class MetadataSummaryRefreshMagicTest {
    @Test void oneTableProbeHasConstantReadsAndPreservesZero() throws Exception {
        for (int size : List.of(20, 100)) {
            Fixture f = new Fixture(size);
            Object actual = f.read("ga", "db", "table");
            assertThat(actual).withFailMessage("Unexpected read result: %s", actual).isInstanceOf(Map.class);
            Map<?,?> summary = (Map<?,?>) actual;
            assertThat(summary.get("recordCount")).isEqualTo(0L);
            assertThat(summary.get("fieldCount")).isEqualTo(2);
            assertThat(f.counter.reads).isEqualTo(2);
            assertThat(f.probe.pages).isEqualTo(1);
            assertThat(f.probe.counts).isEqualTo(1);
            assertThat(f.probe.schema).isEqualTo("public");
            System.out.println("Tables=" + size + ": control SQL=2, one bounded summary query, one current-table count");
        }
    }
    @Test void tenantAndAssociationRejectWhileMenuRoleDoesNotBlockProbe() throws Exception {
        Fixture f = new Fixture(20);
        assertThat(f.read("other", "db", "table")).isInstanceOf(ExitValue.class);
        assertThat(f.read("ga", "wrong-db", "table")).isInstanceOf(ExitValue.class);
        f.scope.denied = true;
        assertThat(f.read("ga", "db", "table")).isInstanceOf(Map.class);
        assertThat(f.probe.pages).isEqualTo(1);
        assertThat(f.probe.counts).isEqualTo(1);
    }
    @Test void oceanBaseLoginSuffixIsNotAnOracleOwnerAndQualifiedResultsStillMatch() throws Exception {
        Fixture f = new Fixture(20);
        f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='DATA_USER@tenant#cluster' where tid='db'");
        f.jdbc.update("update db_table_t set table_name='sample' where tid='table'");
        f.probe.returnedTableName = "DATA_USER.sample";
        assertThat(f.read("ga", "db", "table")).isInstanceOf(Map.class);
        assertThat(f.probe.schema).isEqualTo("DATA_USER");
        assertThat(f.probe.counts).isEqualTo(1);
        f.probe.returnedTableName = "OTHER.sample";
        assertThat(f.read("ga", "db", "table")).isInstanceOf(ExitValue.class);
        assertThat(f.probe.counts).isEqualTo(1);
        f.jdbc.update("update db_datasource_t set username='DATA#USER@tenant#cluster' where tid='db'");
        f.probe.returnedTableName = "DATA#USER.sample";
        assertThat(f.read("ga", "db", "table")).isInstanceOf(Map.class);
        assertThat(f.probe.schema).isEqualTo("DATA#USER");
    }
    @Test void refreshPersistsSummaryOnlyAndDryRunNeverWrites() throws Exception {
        Fixture f = new Fixture(20);
        assertThat(f.refresh("ga", "db", "table", "true")).isInstanceOf(Map.class);
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='table'", Long.class)).isEqualTo(99L);
        assertThat(f.index.calls).isZero();
        assertThat(f.refresh("ga", "db", "table", null)).isInstanceOf(Map.class);
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='table'", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("select field_count from db_table_t where tid='table'", Integer.class)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select storage_size from db_table_t where tid='table'", String.class)).isEqualTo("0 B");
        assertThat(f.jdbc.queryForObject("select ddl_create_time from db_table_t where tid='table'", String.class)).isNull();
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='other-table'", Long.class)).isEqualTo(77L);
        assertThat(f.jdbc.queryForObject("select updated_by from db_table_t where tid='table'", String.class)).isEqualTo("table-editor");
        assertThat(f.jdbc.queryForObject("select field_governance_config from db_table_t where tid='table'", String.class)).isEqualTo("retained");
        assertThat(f.jdbc.queryForObject("select updated_time > TIMESTAMP '2020-01-01 00:00:00' from db_table_t where tid='table'", Boolean.class)).isTrue();
        assertThat(f.index.calls).isEqualTo(1);
        assertThat(f.index.asset.get("tid")).isEqualTo("table");
        assertThat(f.index.asset.get("assetType")).isEqualTo("table");
    }

    @Test void refreshKeepsSourceSummaryAliasesAndCommitsBeforeIndexFailure() throws Exception {
        Fixture f = new Fixture(20);
        assertThat(f.refresh("ga", "db", null, true)).isInstanceOf(Map.class);
        assertThat(f.jdbc.queryForObject("select table_num from db_datasource_t where tid='db'", Integer.class)).isEqualTo(55);
        assertThat(f.index.calls).isZero();
        f.index.fail = true;
        f.index.atRefresh = () -> assertThat(f.jdbc.queryForObject(
                "select table_num from db_datasource_t where tid='db'", Integer.class)).isZero();
        assertThat(f.refresh("ga", "db", null, null)).isInstanceOfSatisfying(Map.class, result -> {
            assertThat(result.get("tid")).isEqualTo("db");
            assertThat(result.get("tableCount")).isEqualTo(0);
            assertThat(result.get("tableNum")).isEqualTo(0);
            assertThat(result.get("table_num")).isEqualTo(0);
            assertThat(result.get("db_version")).isEqualTo("fixture-version");
            assertThat(result.containsKey("procedure_count")).isTrue();
            assertThat(result.get("procedure_count")).isNull();
        });
        assertThat(f.jdbc.queryForObject("select procedure_count from db_datasource_t where tid='db'", Integer.class)).isNull();
        assertThat(f.jdbc.queryForObject("select updated_by from db_datasource_t where tid='db'", String.class)).isEqualTo("source-editor");
        assertThat(f.index.asset.get("tid")).isEqualTo("db");
        assertThat(f.index.asset.get("assetType")).isEqualTo("db");
    }

    @Test void refreshDeniesInvalidAssetsAndSourceScopeBeforeExternalProbe() throws Exception {
        Fixture f = new Fixture(20);
        assertExit(f.refresh("ga", "db", "missing", true), 404);
        assertExit(f.refresh("ga", "db", "other-table", true), 404);
        assertExit(f.refresh("ga", "second-db", "table", true), 403);
        assertExit(f.refresh("other", "db", null, true), 404);
        f.scope.role = "ORG";
        f.scope.org = "other-department";
        assertExit(f.refresh("ga", "db", null, true), 403);
        f.scope.role = "OWNER";
        f.scope.user = "other-user";
        assertExit(f.refresh("ga", "db", "table", null), 403);
        assertThat(f.probe.pages).isZero();
        assertThat(f.probe.databases).isZero();
        assertThat(f.index.calls).isZero();

        f.scope.user = "source-owner";
        assertThat(f.refresh("ga", "db", "table", true)).isInstanceOf(Map.class);
        f.scope.role = "ORG";
        f.scope.org = "source-org";
        assertThat(f.refresh("ga", "db", null, true)).isInstanceOf(Map.class);
        assertThat(f.index.calls).isZero();
    }

    @Test void refreshRejectsOwnershipAndAssetChangesThatOccurDuringProbe() throws Exception {
        Fixture f = new Fixture(20);
        f.scope.role = "OWNER";
        f.scope.user = "source-owner";
        f.probe.duringProbe = () -> f.jdbc.update("update db_datasource_t set created_by='other-user' where tid='db'");
        assertExit(f.refresh("ga", "db", "table", null), 403);
        f.scope.role = "ALL";
        f.probe.duringProbe = () -> f.jdbc.update("update db_table_t set table_name='public.renamed' where tid='table'");
        assertExit(f.refresh("ga", "db", "table", null), 409);
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='table'", Long.class)).isEqualTo(99L);
        assertThat(f.index.calls).isZero();
    }

    private static void assertExit(Object value, int code) {
        assertThat(value).isInstanceOfSatisfying(ExitValue.class, result ->
                assertThat(String.valueOf(result.getValues()[0])).isEqualTo(String.valueOf(code)));
    }
    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();
        final Probe probe = new Probe();
        final Counter counter = new Counter();
        final Index index = new Index();
        final StructuredTableReadMagicModule tableRead;
        final StructuredTableWriteMagicModule tableWrite;
        Fixture(int size) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:summary_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            tableWrite = new StructuredTableWriteMagicModule(jdbc);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(64),db_type varchar(32),
                        jdbc_url varchar(255),username varchar(64),password varchar(64),is_del int,org_id varchar(64),created_by varchar(64),
                        updated_time timestamp,updated_by varchar(64),table_num int,db_version varchar(64),view_count int,
                        procedure_count int,user_count int,data_size varchar(64),total_size varchar(64))
                    """);
            jdbc.execute("""
                    create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(255),
                        record_count bigint,field_count int,storage_size varchar(64),total_size_formatted varchar(64),total_size_bytes bigint,
                        ddl_create_time varchar(64),ddl_update_time varchar(64),updated_time timestamp,is_del int,
                        updated_by varchar(64),field_governance_config varchar(128))
                    """);
            jdbc.update("""
                    insert into db_datasource_t(tid,tenant_id,db_name,db_type,username,is_del,org_id,created_by,updated_by,table_num,procedure_count)
                    values('db','ga','fixture','postgresql','user',0,'source-org','source-owner','source-editor',55,9),
                          ('second-db','ga','fixture two','postgresql','user',0,'source-org','source-owner','source-editor',5,7)
                    """);
            jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,record_count,is_del) values('table','ga','db','public.sample',99,0),('other-table','other','db','sample',77,0)");
            jdbc.update("""
                    update db_table_t set updated_by='table-editor',field_governance_config='retained',
                       ddl_create_time='2020-01-01',updated_time=TIMESTAMP '2020-01-01 00:00:00' where tid='table'
                    """);
            for (int i=0; i<size; i++) jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'ga','db',?,0)", "extra-"+i, "extra_"+i);
            var dynamic = new MagicDynamicDataSource(); dynamic.setDefault(source);
            db = new SQLModule(dynamic); db.setDataSourceNode(dynamic.getDataSource());
            var mapper = new ColumnMapperAdapter(); mapper.add(new DefaultColumnMapperProvider()); mapper.add(new CamelColumnMapperProvider()); mapper.setDefault("camel");
            db.setColumnMapperProvider(mapper); db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper()); db.setRowMapColumnMapper(v->v);
            db.setSqlInterceptors(List.of(counter)); db.setNamedTableInterceptors(List.of());
        }
        Object read(String tenant, String datasource, String table) throws Exception {
            scope.tenant = tenant;
            var bindings = bindings();
            // RequestHandler binds declared GET query parameters at the script root, with no param map.
            bindings.put("tid", datasource); bindings.put("tableId",table);
            String script = CanonicalMagicSources.byId("metadata_db_info_01")
                .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.|com\\.linewell\\.dataelement\\.metautil\\.model\\.)[^\\r\\n]*\\r?\\n", "");
            script = "var datasourceConnectionConfig = (a,b,c,d)=>connection.read()\n" + script;
            return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
        }
        Object refresh(String tenant, String source, String table, Object dryRun) throws Exception {
            scope.tenant = tenant;
            var bindings = bindings();
            bindings.put("tid", source);
            bindings.put("tableId", table);
            bindings.put("dryRun", dryRun);
            bindings.put("log", org.slf4j.LoggerFactory.getLogger(MetadataSummaryRefreshMagicTest.class));
            bindings.put("queryDatabaseInfo", (MagicScriptLambdaFunction) (runtime, arguments) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                try {
                    return read(tenant, source, table);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            bindings.put("indexBridge", index);
            String script = "var refreshAssetIndex = () => indexBridge.refresh(body)\n"
                    + CanonicalMagicSources.byId("metadata_refresh_db_info_01")
                            .replaceAll("(?m)^import (?!java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            return TenantContext.call(tenant, () -> MagicScript.create(script, null).execute(new MagicScriptContext(bindings)));
        }
        Map<String,Object> bindings() {
            var b = new LinkedHashMap<String,Object>();
            b.put("db",db); b.put("tenantRuntime",scope); b.put("dataScope",scope); b.put("metadataExplorerService",probe);
            b.put("connection",new Connection()); b.put("index",index);
            b.put("tableRead",tableRead); b.put("tableWrite",tableWrite);
            b.put("body",null);
            return b;
        }
    }
    public static class Scope {
        String tenant="ga", role="ALL", org, user;
        boolean denied;
        public String id() { return tenant; }
        public List<?> visibleDataSourcesFor(String resource,List<?> rows) { return denied ? List.of() : rows; }
        public boolean canReadDataSourceFor(String resource, Map<String, ?> row) {
            assertThat(resource).isEqualTo("MENU:2081000000000000002");
            return switch (role) {
                case "ORG" -> Objects.equals(org, row.get("org_id"));
                case "OWNER" -> Objects.equals(user, row.get("created_by"));
                default -> !denied;
            };
        }
    }
    public static class Connection { public Map<String,Object> read() { return Map.of("host","localhost","database","fixture","username","user"); } }
    public static class Probe {
        int pages,counts,databases; String schema; String returnedTableName = "sample";
        Runnable duringProbe = () -> {};
        public TablePageResult getTablesPage(DataSourceConfig cfg,Integer page,Integer size,String name) {
            pages++; schema=cfg.getSchema(); assertThat(name).isEqualTo("sample"); assertThat(size).isEqualTo(100);
            var info = new TableInfo(); info.setTableName(returnedTableName); info.setColumnCount(2); info.setTotalSizeBytes(0L); info.setTotalSizeFormatted("0 B");
            var result=new TablePageResult(); result.setRows(List.of(info)); return result;
        }
        public Long getTableRowCount(DataSourceConfig cfg,String name) {
            counts++;
            assertThat(name).isEqualTo("sample");
            duringProbe.run();
            return 0L;
        }
        public DatabaseInfo getDatabaseInfo(DataSourceConfig cfg) {
            databases++;
            duringProbe.run();
            var info = new DatabaseInfo();
            info.setVersion("fixture-version");
            info.setTableCount(0);
            info.setViewCount(0);
            info.setTotalSizeFormatted("0 B");
            info.setDataSizeFormatted("0 B");
            return info;
        }
    }
    public static class Index {
        int calls;
        boolean fail;
        Map<?, ?> asset;
        Runnable atRefresh = () -> {};

        public void refresh(Map<?, ?> body) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls++;
            asset = body;
            atRefresh.run();
            if (fail) throw new IllegalStateException("validation index failure");
        }
    }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql,RequestEntity request) { if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++; }
    }
}
