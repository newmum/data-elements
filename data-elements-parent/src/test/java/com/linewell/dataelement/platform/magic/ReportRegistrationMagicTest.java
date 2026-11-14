package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ReportRegistrationMagicTest {
    static String source() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("magic"))) root = root.getParent();
        String content = Files.readString(root.resolve("magic/api/02.数据盘点/02.数据库表/01.元数据管理/26.数据上报登记.ms"));
        return content.substring(content.indexOf("================================") + 32);
    }
    public static class Metadata {
        boolean fail;
        public void refreshRegistrationStatus(String id, boolean finish) {
            if (fail) {
                throw new IllegalStateException("validation metadata refresh failed");
            }
        }
    }
    static class Fixture {
        final TableDetailFieldSaveMagicTest.Fixture base;
        final Map<String,Object> captured = new HashMap<>();
        final Metadata metadata = new Metadata();
        final List<Statement> statements = new ArrayList<>();
        final StructuredTableReadMagicModule tableRead;
        final StructuredTableWriteMagicModule tableWrite;
        Runnable beforeLock;

        Fixture(int count) {
            base = new TableDetailFieldSaveMagicTest.Fixture(count);
            base.jdbc.execute("alter table db_datasource_t add pool_cfg clob");
            base.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");
            base.jdbc.execute("alter table db_datasource_t add created_by varchar(64)");
            base.jdbc.update("update db_datasource_t set pool_cfg=?", "{\"accessMode\":\"upload\"}");
            base.jdbc.execute("alter table db_table_column_t add ordinal_position int default 0");
            base.jdbc.execute("alter table db_table_t add table_name varchar(64) default 'registered_table'");
            base.jdbc.execute("alter table db_table_t add table_name_cn varchar(100) default '登记数据表'");
            base.jdbc.execute("alter table db_table_t add table_comment varchar(100)");
            base.jdbc.execute("alter table db_table_t add record_count bigint default 0");
            base.jdbc.execute("create table data_access_agg_task_t(tenant_id varchar(64),source_table_id varchar(64),target_table_id varchar(64),is_del int)");
            base.jdbc.execute("create table file_upload_t(tid varchar(64),tenant_id varchar(64),created_by varchar(64),is_del int)");
            var source = new TracedDataSource(base.jdbc.getDataSource(), this);
            var jdbc = new JdbcTemplate(source);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            tableWrite = new StructuredTableWriteMagicModule(jdbc);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            base.db.setDataSourceNode(sources.getDataSource());
            base.db.setSqlInterceptors(List.of());
        }
        Object run(Map<String,Object> body) throws Exception {
            return run(body,false);
        }
        Object run(Map<String,Object> body,boolean captureImport) throws Exception {
            String script = source().replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|cn\\.dev33\\.|org\\.apache\\.poi\\.|com\\.linewell\\.|com\\.fasterxml\\.jackson\\.)[^\\r\\n]*\\r?\\n", "");
            script = (captureImport?"var importSnapshot=()=>{captured.payload=body;return {rows:body.tables.map(row=>{tableName:row.tableName,tid:row.tableName=='registered_table'?'table':row.tableName.replace('_','-')})}}\n":"var importSnapshot=()=>{throw new IllegalStateException('existing tables must not be reimported')}\n")+"var configData=()=>{throw new IllegalStateException('storage must not be reached')}\n" + script;
            return execute(script, Map.of("body", body, "db", base.db, "tenantRuntime", base.scope,
                    "dataScope", base.scope, "metadataAsset", metadata, "jsons", new TableDetailFieldSaveMagicTest.Jsons(),
                    "captured", captured));
        }

        Object execute(String script, Map<String, Object> inputs) {
            var bindings = new HashMap<>(inputs);
            bindings.put("tableRead", tableRead);
            bindings.put("tableWrite", tableWrite);
            base.reads = 0;
            statements.clear();
            try (var tenant = TenantContext.use(base.scope.id()); var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::getLoginIdAsString).thenReturn("validation-user");
                return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
            }
        }
    }

    record Statement(String sql, Connection connection, boolean transaction) {
        boolean reads() { return sql.startsWith("select"); }
        boolean locks() { return reads() && sql.contains("for update"); }
    }

    private static final class TracedDataSource extends DelegatingDataSource {
        private final Fixture fixture;

        TracedDataSource(DataSource source, Fixture fixture) {
            super(source);
            this.fixture = fixture;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection physical = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var statement = new Statement(((String) arguments[0]).stripLeading().toLowerCase(Locale.ROOT),
                                    physical, TransactionSynchronizationManager.hasResource(this));
                            fixture.statements.add(statement);
                            if (statement.reads()) {
                                fixture.base.reads++;
                            }
                            if (statement.locks() && fixture.beforeLock != null) {
                                fixture.beforeLock.run();
                            }
                        }
                        if (method.getName().equals("getMetaData")) {
                            var metadata = physical.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (unused, operation, parameters) -> operation.getName().equals("getURL")
                                            ? "jdbc:mysql://reporting-validation" : invoke(metadata, operation, parameters));
                        }
                        return invoke(physical, method, arguments);
                    });
        }

        private static Object invoke(Object receiver, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
    @Test void legacyListsUseThreeReadsForTwentyAndHundredFieldsAndExcludeForeignColumns() throws Exception {
        for (int count : List.of(20,100)) {
            Fixture f = new Fixture(count);
            Map<?,?> result = assertInstanceOf(Map.class,f.run(Map.of("action","list","dbId","source","tenantId","other")));
            List<?> tables = (List<?>)result.get("tables");
            assertEquals(1,tables.size());
            assertEquals(count,((List<?>)((Map<?,?>)tables.getFirst()).get("fields")).size());
            assertEquals(3,f.base.reads);
            System.out.println("Report registration list: fields="+count+", SQL=3, foreign table/columns excluded");
        }
    }
    @Test void twentyAndHundredTablesSaveWithFixedReadsAndRejectMixedOwnerIdsBeforeWriting() throws Exception {
        for (int count : List.of(20,100)) {
            Fixture f = new Fixture(1);
            List<Map<String,Object>> tables = new ArrayList<>();
            for (int i=0;i<count;i++) {
                String id="registered-"+i;
                f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source',?,0)",id,id.replace('-','_'));
                tables.add(new LinkedHashMap<>(Map.of("tid",id,"tableName",id.replace('-','_'),"tableNameCn","登记数据表","recordCount",2,"legacy",true,"fields",List.of(Map.of("columnName","id")))));
            }
            Map<String,Object> body = new LinkedHashMap<>(Map.of("action","save","dbId","source","revision","","files",List.of(),"tables",tables));
            f.base.jdbc.execute("insert into file_upload_t values('registered-file','police','validation-user',0)");
            tables.getFirst().put("legacy",false);
            tables.getFirst().put("fileId","registered-file");
            body.put("files",List.of(Map.of("fileId","registered-file","fileName","validation.csv")));
            tables.getLast().put("tid","foreign");
            assertInstanceOf(ExitValue.class,f.run(body));
            assertEquals("{\"accessMode\":\"upload\"}",f.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class));
            tables.getLast().put("tid","registered-"+(count-1));
            Map<?,?> saved = assertInstanceOf(Map.class,f.run(body));
            assertEquals(count,((List<?>)saved.get("tables")).size());
            assertEquals(4,f.base.reads);
            System.out.println("Report registration save: tables="+count+", SQL=4 including batch file ownership, mixed tenant IDs rejected before writes");
        }
    }

    @Test
    void twentyAndHundredRemovalsCheckBothTaskLinksInOneReadAndRejectForeignTableIds() throws Exception {
        for (int count : List.of(20, 100)) {
            var f = new Fixture(1);
            var ids = new ArrayList<String>();
            for (int i = 0; i < count; i++) {
                String id = "removed-" + i;
                ids.add(id);
                f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,is_del) values(?,'police','source',0)", id);
            }
            var body = new LinkedHashMap<String, Object>(Map.of("action", "save", "dbId", "source",
                    "revision", "", "files", List.of(), "tables", List.of(), "removedTableIds", ids));
            ids.set(count - 1, "foreign");
            assertEquals(403, assertInstanceOf(ExitValue.class, f.run(body)).getValues()[0]);
            assertEquals(2, f.base.reads, "拒绝越权列表后无任务查询或写入");
            ids.set(count - 1, "removed-" + (count - 1));
            f.base.jdbc.update("insert into data_access_agg_task_t values('other',?,null,0)", ids.getFirst());
            f.base.jdbc.update("insert into data_access_agg_task_t values('police',?,null,1)", ids.getFirst());
            for (boolean sourceLink : List.of(true, false)) {
                f.base.jdbc.update("insert into data_access_agg_task_t values('police',?,?,0)",
                        sourceLink ? ids.getFirst() : null, sourceLink ? null : ids.getLast());
                assertEquals(409, assertInstanceOf(ExitValue.class, f.run(body)).getValues()[0]);
                assertEquals(3, f.base.reads);
                assertEquals(1, f.statements.stream().filter(s -> s.sql().contains("data_access_agg_task_t")).count());
                assertTrue(f.statements.stream().allMatch(Statement::reads));
                f.base.jdbc.update("delete from data_access_agg_task_t where tenant_id='police' and is_del=0");
            }
            assertInstanceOf(Map.class, f.run(body));
            assertEquals(4, f.base.reads, "合法删除使用一次源、一次表集合、一次任务集合及一次锁读取");
            assertEquals(count, f.base.jdbc.queryForObject(
                    "select count(*) from db_table_t where tenant_id='police' and is_del=1", Integer.class));
            assertEquals(0, f.base.jdbc.queryForObject("select is_del from db_table_t where tid='foreign'", Integer.class));
        }
    }

    @Test
    void unchangedRegistrationRevisionDoesNotOverwriteConcurrentReceipt() {
        var f = new Fixture(1);
        String updated = """
                {"accessMode":"upload","reportRegistration":{"revision":"","files":[],"tables":[],"receipts":[{"id":"concurrent","status":"sending"}]}}
                """.strip();
        f.beforeLock = () -> f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'", updated);
        var body = Map.<String, Object>of("action", "save", "dbId", "source",
                "revision", "", "files", List.of(), "tables", List.of());
        Exception failure = assertThrows(Exception.class, () -> f.run(body));
        assertTrue(failure.getMessage().contains("数据源已被更新"), failure::getMessage);
        assertEquals(updated, f.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'", String.class));
        assertEquals(1, f.statements.stream().filter(Statement::locks).count());
        assertTrue(f.statements.stream().allMatch(Statement::reads));
    }

    @Test
    void registrationFailureRollsBackPoolAndRowChangesOnTheSameLockedConnection() {
        var f = new Fixture(1);
        f.metadata.fail = true;
        var table = new LinkedHashMap<String, Object>(Map.of("tid", "table", "tableName", "renamed_table",
                "tableNameCn", "登记改名", "recordCount", 2, "legacy", true,
                "fields", List.of(Map.of("columnName", "id"))));
        var body = Map.<String, Object>of("action", "save", "dbId", "source",
                "revision", "", "files", List.of(), "tables", List.of(table));
        Exception failure = assertThrows(Exception.class, () -> f.run(body));
        assertTrue(failure.getMessage().contains("validation metadata refresh failed"), failure::getMessage);
        assertEquals("registered_table", f.base.jdbc.queryForObject("select table_name from db_table_t where tid='table'", String.class));
        assertEquals("{\"accessMode\":\"upload\"}", f.base.jdbc.queryForObject(
                "select pool_cfg from db_datasource_t where tid='source'", String.class));
        assertEquals(4, f.base.reads);
        var locked = f.statements.stream().filter(Statement::locks).findFirst().orElseThrow();
        var writes = f.statements.stream().filter(statement -> !statement.reads()).toList();
        assertEquals(2, writes.size());
        assertTrue(locked.transaction());
        assertTrue(writes.stream().allMatch(statement -> statement.connection() == locked.connection() && statement.transaction()));
    }

    @Test void missingSourceForeignFileAndStaleRevisionAreRejected() throws Exception {
        Fixture f = new Fixture(1);
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","list","dbId","missing")));
        f.base.jdbc.execute("insert into file_upload_t values('foreign','other','validation-user',0),('other-user','police','another-user',0)");
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","parse","fileIds",List.of("foreign","other-user"),"tenantId","other")));
        assertEquals(1,f.base.reads);
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","revision","stale")));
    }
    @Test void pagedFileHistoryUsesOneReadForTwentyAndHundredFiles() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(1);
            var files=new ArrayList<Map<String,Object>>();var tables=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++){
                files.add(Map.of("fileId","file-"+i,"fileName","data-"+i+".csv"));
                tables.add(Map.of("fileId","file-"+i,"recordCount",2,"fieldCount",999,"fields",List.of(Map.of("columnName","id"),Map.of("columnName","name"))));
            }
            tables.add(Map.of("fileId","file-0","recordCount",5,"fields",List.of(Map.of("columnName","code"),Map.of("columnName","label"),Map.of("columnName","time"))));
            f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",Map.of("revision","v1","files",files,"tables",tables))));
            Map<?,?> page=(Map<?,?>)f.run(Map.of("action","history","dbId","source","pageNo",1,"pageSize",20,"sortField","fileName","sortDir","asc"));
            assertEquals(count,((Number)page.get("total")).intValue());assertEquals(20,((List<?>)page.get("list")).size());assertEquals(1,f.base.reads);
            var first=(Map<?,?>)((List<?>)page.get("list")).getFirst();assertEquals(5,((Number)first.get("fieldCount")).intValue());assertEquals(2,((Number)first.get("tableCount")).intValue());assertEquals(7,((Number)first.get("rowCount")).intValue());
            var byFields=(Map<?,?>)f.run(Map.of("action","history","dbId","source","pageNo",1,"pageSize",20,"sortField","fieldCount","sortDir","desc"));
            assertEquals("file-0",((Map<?,?>)((List<?>)byFields.get("list")).getFirst()).get("fileId"));assertEquals(1,f.base.reads);
            var lastPage=(Map<?,?>)f.run(Map.of("action","history","dbId","source","pageNo",count/20,"pageSize",20,"sortField","fieldCount","sortDir","asc"));
            assertEquals("file-0",((Map<?,?>)((List<?>)lastPage.get("list")).getLast()).get("fileId"));assertEquals(1,f.base.reads);
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","history","dbId","source","sortField","unknown","sortDir","asc")));
            f.base.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,pool_cfg) values('foreign-history','other',0,?)","{\"accessMode\":\"upload\"}");
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","history","dbId","foreign-history","tenantId","other")));
            System.out.println("Report file history: files="+count+", SQL=1, tenant constrained, pageSize=20");
        }
    }
    @Test void failedOnlyUploadRemainsVisibleAfterReopenWithoutRestoringLegacyTables() throws Exception {
        Fixture f=new Fixture(1);
        f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",Map.of("revision","saved","tables",List.of(),"files",List.of(Map.of("fileId","empty","fileName","empty.xlsx")),"failedTables",List.of(Map.of("status","failed","error","empty sheet"))))));
        Map<?,?> result=(Map<?,?>)f.run(Map.of("action","list","dbId","source"));
        assertEquals(0,((List<?>)result.get("tables")).size());assertEquals(1,((List<?>)result.get("files")).size());assertEquals(1,((List<?>)result.get("failedTables")).size());assertEquals(1,f.base.reads);
    }
    @Test void historyDateSortKeepsMissingDatesLastAndLegacyFieldCountsRemainUsable() throws Exception {
        var f=new Fixture(1);
        var files=List.of(Map.of("fileId","empty","fileName","empty.csv"),Map.of("fileId","older","fileName","same.csv"),Map.of("fileId","newer","fileName","same.csv"));
        var manifest=Map.of("files",files,"tables",List.of(Map.of("fileId","older","fieldCount",3,"recordCount",2)),"receipts",List.of(Map.of("fileId","older","tableId","old","status","success","reportedAt","2026-10-01T00:00:00Z"),Map.of("fileId","newer","tableId","new","status","success","reportedAt","2026-10-02T00:00:00Z")));
        f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",manifest)));
        for(String direction:List.of("asc","desc")){
            var page=(Map<?,?>)f.run(Map.of("action","history","dbId","source","sortField","reportedAt","sortDir",direction));var rows=(List<?>)page.get("list");
            assertEquals("empty",((Map<?,?>)rows.getLast()).get("fileId"));assertEquals(direction.equals("asc")?"older":"newer",((Map<?,?>)rows.getFirst()).get("fileId"));assertEquals(1,f.base.reads);
            var older=rows.stream().map(row->(Map<?,?>)row).filter(row->"older".equals(row.get("fileId"))).findFirst().orElseThrow();assertEquals(3,((Number)older.get("fieldCount")).intValue());
        }
    }
    @Test void parserRecognizesQuotedCsvSeparateRegionsAndRejectsMissingData() throws Exception {
        String script = source();
        String helpers = script.substring(script.indexOf("var positions ="),script.indexOf("if (action == 'parse')"));
        String imports = "import cn.hutool.core.text.csv.CsvBaseReader\nimport cn.hutool.core.text.csv.CsvReadConfig\nimport java.util.regex.Pattern\nimport cn.hutool.core.util.IdUtil\nimport java.nio.charset.Charset\nimport java.nio.charset.CodingErrorAction\nimport java.nio.ByteBuffer\nimport java.util.stream.IntStream\nimport java.lang.StringBuilder\nvar text=value=>value==null?'':(value+'').trim()\n";
        List<?> rows = assertInstanceOf(List.class,MagicScript.create(imports+helpers+"return regions(csvRows(payload),'测试表','数据.csv')",null)
                .execute(new MagicScriptContext(Map.of("payload","\uFEFF姓名,说明\r\n张三,\"包含,逗号\"\r\n\r\n编号,状态\nA1,正常\n\n只有表头\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        assertEquals(3,rows.size());
        assertEquals("success",((Map<?,?>)rows.get(0)).get("status"));
        assertEquals(1,((Number)((Map<?,?>)rows.get(0)).get("recordCount")).intValue());
        assertEquals("failed",((Map<?,?>)rows.get(2)).get("status"));
        List<?> collisions = (List<?>)MagicScript.create(imports+helpers+"return regions(csvRows(payload),'测试表','数据.csv')",null)
                .execute(new MagicScriptContext(Map.of("payload","姓名,field_1,FIELD_1\n甲,A,B\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        List<?> fields = (List<?>)((Map<?,?>)collisions.getFirst()).get("fields");
        assertEquals(List.of("field_1","field_1_2","FIELD_1_3"),fields.stream().map(field->((Map<?,?>)field).get("columnName")).toList());
    }
}
