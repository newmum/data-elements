package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.linewell.dataelement.metautil.model.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

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
    @Test void tenantRoleAndAssociationAreCheckedBeforeAnyProbe() throws Exception {
        Fixture f = new Fixture(20);
        assertThat(f.read("other", "db", "table")).isInstanceOf(ExitValue.class);
        assertThat(f.read("ga", "wrong-db", "table")).isInstanceOf(ExitValue.class);
        f.scope.denied = true;
        assertThat(f.read("ga", "db", "table")).isInstanceOf(ExitValue.class);
        assertThat(f.probe.pages).isZero();
        assertThat(f.probe.counts).isZero();
    }
    @Test void refreshPersistsSummaryOnlyAndDryRunNeverWrites() throws Exception {
        Fixture f = new Fixture(20);
        f.refresh(true);
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='table'", Long.class)).isEqualTo(99L);
        assertThat(f.index.calls).isZero();
        f.refresh(false);
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='table'", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("select field_count from db_table_t where tid='table'", Integer.class)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select storage_size from db_table_t where tid='table'", String.class)).isEqualTo("0 B");
        assertThat(f.jdbc.queryForObject("select ddl_create_time from db_table_t where tid='table'", String.class)).isNull();
        assertThat(f.jdbc.queryForObject("select record_count from db_table_t where tid='other-table'", Long.class)).isEqualTo(77L);
        assertThat(f.index.calls).isEqualTo(1);
    }
    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();
        final Probe probe = new Probe();
        final Counter counter = new Counter();
        final Index index = new Index();
        Fixture(int size) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:summary_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(64),db_type varchar(32),jdbc_url varchar(255),username varchar(64),password varchar(64),is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(255),record_count bigint,field_count int,storage_size varchar(64),total_size_formatted varchar(64),total_size_bytes bigint,ddl_create_time varchar(64),ddl_update_time varchar(64),updated_time timestamp,is_del int)");
            jdbc.update("insert into db_datasource_t values('db','ga','fixture','postgresql',null,'user',null,0)");
            jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,record_count,is_del) values('table','ga','db','public.sample',99,0),('other-table','other','db','sample',77,0)");
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
        Object refresh(boolean dryRun) throws Exception {
            var bindings = bindings();
            bindings.put("tid", "db"); bindings.put("tableId","table"); bindings.put("dryRun",dryRun ? "true" : null);
            bindings.put("summary", Map.of("recordCount",0L,"fieldCount",2,"storageSize","0 B","totalSizeFormatted","0 B","totalSizeBytes",0L));
            String script = CanonicalMagicSources.byId("metadata_refresh_db_info_01").replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
            script = "var queryDatabaseInfo=()=>summary\nvar refreshAssetIndex=()=>index.refresh()\n" + script;
            return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
        }
        Map<String,Object> bindings() {
            var b = new LinkedHashMap<String,Object>();
            b.put("db",db); b.put("tenantRuntime",scope); b.put("dataScope",scope); b.put("metadataExplorerService",probe);
            b.put("connection",new Connection()); b.put("index",index); return b;
        }
    }
    public static class Scope {
        String tenant="ga"; boolean denied;
        public String id() { return tenant; }
        public List<?> visibleDataSourcesFor(String resource,List<?> rows) { return denied ? List.of() : rows; }
    }
    public static class Connection { public Map<String,Object> read() { return Map.of("host","localhost","database","fixture","username","user"); } }
    public static class Probe {
        int pages,counts; String schema;
        public TablePageResult getTablesPage(DataSourceConfig cfg,Integer page,Integer size,String name) {
            pages++; schema=cfg.getSchema(); assertThat(name).isEqualTo("sample"); assertThat(size).isEqualTo(100);
            var info = new TableInfo(); info.setTableName("sample"); info.setColumnCount(2); info.setTotalSizeBytes(0L); info.setTotalSizeFormatted("0 B");
            var result=new TablePageResult(); result.setRows(List.of(info)); return result;
        }
        public Long getTableRowCount(DataSourceConfig cfg,String name) { counts++; assertThat(name).isEqualTo("sample"); return 0L; }
    }
    public static class Index { int calls; public void refresh(){calls++;} }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql,RequestEntity request) { if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++; }
    }
}
