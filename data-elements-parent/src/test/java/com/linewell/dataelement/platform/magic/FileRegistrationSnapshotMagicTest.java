package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.platform.audit.infrastructure.magic.AuditNamedTableInterceptor;
import com.linewell.dataelement.platform.audit.infrastructure.magic.AuditTableRegistry;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicScriptException;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class FileRegistrationSnapshotMagicTest {
    private static final String ACTOR = "validation-user";
    private static final Timestamp HISTORICAL_TIME = Timestamp.valueOf("2001-01-01 00:00:00");
    private static final List<String> FILE_ATTRIBUTES = List.of("file_name", "file_description", "file_format",
            "max_file_bytes", "related_table_name", "related_field_name");

    public static class Scope {
        String policy="ALL";
        public Map<String,Object> resource(String key){
            assertEquals("MENU:2081000000000000002", key);
            return Map.of("scope",policy,"userId",ACTOR,"organizationIds",List.of("validation-org"));
        }
    }

    public static class Log {
        int refreshWarnings;
        public void warn(String message) { refreshWarnings++; }
    }

    static class Fixture {
        final TableDetailFieldSaveMagicTest.Fixture base=new TableDetailFieldSaveMagicTest.Fixture(0);
        final Scope scope=new Scope();final List<Map<String,Object>> files=new ArrayList<>();
        final DatabaseTrace trace;
        final JdbcTemplate magicJdbc;
        final Log log = new Log();
        int refreshes;

        Fixture(int count){
            base.jdbc.execute("""
                    alter table db_datasource_t add pool_cfg clob;
                    alter table db_datasource_t add org_id varchar(64);
                    alter table db_datasource_t add created_by varchar(64);
                    alter table db_datasource_t add updated_by varchar(64);
                    alter table db_datasource_t add asset_status int default 0;
                    alter table db_datasource_t add flow_status int default 0;
                    alter table db_datasource_t add updated_time timestamp;
                    alter table db_table_t add table_type varchar(32);
                    alter table db_table_t add table_name varchar(128);
                    alter table db_table_t add table_name_cn varchar(255);
                    alter table db_table_t add table_comment varchar(2000);
                    alter table db_table_t add created_time timestamp default current_timestamp;
                    alter table db_table_t add created_by varchar(64);
                    alter table db_table_t add updated_by varchar(64);
                    alter table db_table_t add field_count int;
                    alter table db_table_t add org_id varchar(64);
                    alter table db_table_t add reg_time timestamp;
                    alter table db_table_column_t add column_type varchar(64);
                    alter table db_table_column_t add data_type varchar(64);
                    alter table db_table_column_t add length bigint;
                    alter table db_table_column_t add ordinal_position int;
                    alter table db_table_column_t add created_by varchar(64);
                    alter table db_table_column_t add created_time timestamp;
                    alter table db_table_column_t add updated_by varchar(64);
                    delete from db_table_column_t;
                    delete from db_table_t;
                    """);
            base.jdbc.update("update db_datasource_t set pool_cfg=?,org_id='validation-org',created_by='validation-user'","{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
            for(int i=0;i<count;i++){
                String id="file-"+i,code="scene_"+i;var policy=Map.of("fileCode",code,"fileFormat","pdf","maxFileBytes",1048576,"maxFileSizeMb",1);
                base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,table_name_cn,table_comment,field_governance_config,is_del) values(?,'police','source','FILE',?,?,?, ?,0)",id,"legacy_"+i,"文件"+i,"validation文件",JSONUtil.toJsonStr(Map.of("fileRegistration",policy)));
                files.add(new LinkedHashMap<>(Map.of("tableId",id,"fileCode",code,"fileName","文件"+i,"fileDescription","validation文件","fileFormat","pdf","maxFileSizeMb",1)));
            }
            base.jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,table_name_cn,is_del) values('foreign','other','source','FILE','foreign_file','foreign',0)");
            var physical = base.jdbc.getDataSource();
            trace = new DatabaseTrace(physical);
            magicJdbc = new JdbcTemplate(trace);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(trace);
            base.db.setDataSourceNode(sources.getDataSource());
            base.db.setNamedTableInterceptors(List.of(new AuditNamedTableInterceptor(new AuditTableRegistry(physical))));
            base.db.setSqlInterceptors(List.of());
        }
        Object run(Map<String,Object> body) throws Exception {
            String script=CanonicalMagicSources.byId("metadata_file_registration_01").replaceAll("(?m)^import (?!java\\.|cn\\.)[^\\r\\n]*\\r?\\n","");
            trace.statements.clear();
            MagicScriptLambdaFunction refresh = (runtime, arguments) -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Refresh runs after commit");
                assertEquals(2, base.jdbc.queryForObject("select asset_status from db_datasource_t where tid='source'", Integer.class));
                refreshes++;
                return null;
            };
            try (var tenant = TenantContext.use("police"); var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::isLogin).thenReturn(true);
                login.when(StpUtil::getLoginId).thenReturn(ACTOR);
                return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"db",base.db,
                        "tenantRuntime",base.scope,"dataScope",scope,"tableRead",new StructuredTableReadMagicModule(magicJdbc),
                        "tableWrite",new StructuredTableWriteMagicModule(magicJdbc), "refreshAssetIndex",refresh,"log",log)));
            } finally {
                base.reads = (int) trace.statements.stream().filter(SqlCall::read).count();
            }
        }

        Object preview() throws Exception {
            return run(Map.of("action", "save", "dbId", "source", "dryRun", true, "files", files));
        }

        void linkedTable() {
            base.jdbc.execute("""
                    insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,asset_status,flow_status,is_del)
                    values('linked','police','source','TABLE','person',1,3,0),
                          ('foreign-related','other','source','TABLE','outside_table',0,0,0);
                    insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del)
                    values('linked-column','police','linked','photo_id',0),
                          ('foreign-related-column','other','linked','foreign_field',0);
                    insert into db_datasource_t(tid,tenant_id,is_del)
                    values('foreign-source','other',0);
                    """);
        }

        List<SqlCall> writes() {
            return trace.statements.stream().filter(call -> !call.read()).toList();
        }

        void assertTransactionReadsAndWrites(int reads, int writes) {
            assertEquals(reads, base.reads);
            assertEquals(writes, writes().size());
            assertEquals(1, trace.statements.stream().filter(SqlCall::lock).count());
            assertTrue(trace.statements.getFirst().lock(), "Lock precedes the file snapshot and association reads");
            assertTrue(trace.statements.stream().allMatch(SqlCall::inTransaction));
            assertTrue(trace.statements.stream().allMatch(call -> call.connection() == trace.statements.getFirst().connection()),
                    "Magic named tables and the structured modules use the same JDBC transaction");
        }
    }

    private record SqlCall(String sql, String operation, Connection connection, boolean inTransaction) {
        boolean read() { return sql.startsWith("select"); }
        boolean lock() { return read() && sql.contains("for update"); }
        boolean targets(String table) { return sql.replace("\"", "").contains(table); }
    }

    @FunctionalInterface
    private interface ConnectionAction {
        void run(Connection connection) throws SQLException;
    }

    private static final class DatabaseTrace extends DelegatingDataSource {
        final List<SqlCall> statements = new ArrayList<>();
        Runnable beforeLock;
        ConnectionAction beforeFieldPatch;

        DatabaseTrace(DataSource physical) { super(physical); }

        @Override public Connection getConnection() throws SQLException { return record(super.getConnection()); }

        private Connection record(Connection connection) {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.contains("for update") && beforeLock != null) beforeLock.run();
                            var prepared = (PreparedStatement) invoke(connection, method, args);
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                    (statementProxy, statementMethod, statementArgs) -> {
                                        String operation = statementMethod.getName();
                                        if (operation.startsWith("execute")) {
                                            var call = new SqlCall(sql, operation, connection,
                                                    TransactionSynchronizationManager.hasResource(this));
                                            statements.add(call);
                                            if (beforeFieldPatch != null && operation.equals("executeBatch")
                                                    && sql.startsWith("update") && call.targets("db_table_column_t")) {
                                                beforeFieldPatch.run(connection);
                                            }
                                        }
                                        return invoke(prepared, statementMethod, statementArgs);
                                    });
                        }
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (metaProxy, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://file-registration-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        return invoke(connection, method, args);
                    });
        }

        private static Object invoke(Object receiver, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
    @Test void twentyAndHundredDefinitionsUseThreeReadsAndCannotMixForeignIds() throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);
            var result=(Map<?,?>)f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files));
            assertEquals(count,((List<?>)result.get("list")).size());assertEquals(3,f.base.reads);
            assertTrue(f.writes().isEmpty());
            assertTrue(f.trace.statements.stream().noneMatch(SqlCall::lock));
            f.files.getLast().put("tableId","foreign");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(3,f.base.reads);
        }
    }
    @Test void orgAndOwnerScopesApplyBeforeAnyFileRead() throws Exception {
        for(String policy:List.of("ORG","OWNER")){
            var f=new Fixture(20);f.scope.policy=policy;
            assertEquals(20,((List<?>)((Map<?,?>)f.run(Map.of("dbId","source"))).get("list")).size());assertEquals(3,f.base.reads);
            f.base.jdbc.execute("update db_datasource_t set org_id='other',created_by='another-user'");
            assertInstanceOf(ExitValue.class,f.run(Map.of("dbId","source")));assertEquals(1,f.base.reads);
        }
    }
    @Test void duplicateOrChangedCodesAndForgedTenantAreRejected() throws Exception {
        var f=new Fixture(2);f.files.getLast().put("fileCode","scene_0");
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));
        f.files.getLast().put("fileCode","renamed_code");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));
        assertInstanceOf(ExitValue.class,f.run(Map.of("dbId","source","tenantId","other")));assertEquals(0,f.base.reads);
    }
    @Test void associatedTableIdsUseTheSameRoleScopeInOneBatch() throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);f.scope.policy="OWNER";
            f.base.jdbc.execute("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by) values('other-source','police',0,'other','another-user')");
            f.base.jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,is_del) values('associated','police','source','TABLE','validation_table',0),('hidden','police','other-source','TABLE','hidden_table',0)");
            f.files.forEach(file->file.put("relatedTableName","associated"));
            assertInstanceOf(Map.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(4,f.base.reads);
            f.files.getLast().put("relatedTableName","hidden");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(4,f.base.reads);
        }
    }

    @Test void distinctFileReferencesReadTwoBatchesAndRejectAFieldFromAnotherTable() throws Exception {
        for (int count : List.of(20, 100)) {
            var f = new Fixture(count);
            f.scope.policy = "ORG";
            var tables = new ArrayList<Object[]>();
            var columns = new ArrayList<Object[]>();
            for (int i = 0; i < count; i++) {
                String id = "related-" + i;
                String name = "registered_table_" + i;
                String fieldId = "attribute-" + i;
                tables.add(new Object[] {id, name});
                columns.add(new Object[] {fieldId, id});
                f.files.get(i).put("relatedTableName", i % 2 == 0 ? id : name);
                f.files.get(i).put("relatedFieldName", i % 2 == 0 ? fieldId : "attribute");
            }
            f.base.jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,is_del) "
                    + "values(?,'police','source','TABLE',?,0)", tables);
            f.base.jdbc.batchUpdate("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) "
                    + "values(?,'police',?,'attribute',0)", columns);

            var response = assertInstanceOf(Map.class, f.preview());
            var rows = assertInstanceOf(List.class, response.get("list"));
            assertEquals(count, rows.size());
            assertEquals(5, f.base.reads);
            for (int i = 0; i < count; i++) {
                var row = assertInstanceOf(Map.class, rows.get(i));
                assertEquals("related-" + i, row.get("relatedTableId"));
                assertEquals("attribute-" + i, row.get("relatedFieldId"));
            }
            f.files.getLast().put("relatedFieldName", "attribute-0");
            var denied = assertInstanceOf(ExitValue.class, f.preview());
            assertEquals(422, denied.getValues()[0]);
            assertEquals(5, f.base.reads);
            System.out.println("File registration: " + count + " distinct related tables/fields, 5 reads, wrong-table field denied");
        }
    }

    @Test void rejectsAmbiguousNamesAndAllowsExplicitReferences() throws Exception {
        var f = new Fixture(1);
        f.base.jdbc.execute("""
                insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,is_del) values
                    ('ref-one','police','source','TABLE','shared_name',0),
                    ('ref-two','police','source','TABLE','shared_name',0);
                insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values
                    ('field-one','police','ref-one','shared_field',0),
                    ('field-two','police','ref-one','shared_field',0);
                """);
        var file = f.files.getFirst();
        file.put("relatedTableName", "shared_name");
        file.put("relatedFieldName", "shared_field");
        assertEquals(422, assertInstanceOf(ExitValue.class, f.preview()).getValues()[0]);
        assertEquals(4, f.base.reads, "Ambiguous table names must not trigger field reads");
        file.put("relatedTableName", "ref-one");
        assertEquals(422, assertInstanceOf(ExitValue.class, f.preview()).getValues()[0]);
        assertEquals(5, f.base.reads);
        file.put("relatedFieldName", "field-two");
        var response = assertInstanceOf(Map.class, f.preview());
        var row = assertInstanceOf(Map.class, ((List<?>) response.get("list")).getFirst());
        assertEquals("ref-one", row.get("relatedTableId"));
        assertEquals("field-two", row.get("relatedFieldId"));
    }

    @Test
    void savesReadsReimportsAndCompletesTwentyAndOneHundredFileDefinitions() throws Exception {
        for (int count : List.of(20, 100)) {
            var f = new Fixture(0);
            f.linkedTable();
            f.scope.policy = "ORG";
            var inputs = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < count; i++) {
                inputs.add(newFile(i));
            }

            var saved = assertInstanceOf(Map.class, f.run(saveBody(inputs)));
            assertEquals(true, saved.get("saved"));
            assertEquals(0, saved.get("assetStatus"));
            assertEquals("FILE", saved.get("resourceKind"));
            f.assertTransactionReadsAndWrites(4, 3);
            var originalFiles = fileIds(f);
            var originalAttributes = attributeIds(f);
            assertEquals(count, originalFiles.size());
            assertEquals(count * FILE_ATTRIBUTES.size(), originalAttributes.size());
            assertEquals(count, originalAttributes.stream().map(row -> row.get("table_id")).distinct().count());
            for (String attribute : FILE_ATTRIBUTES) {
                assertEquals(count, originalAttributes.stream().filter(row -> attribute.equals(row.get("column_name"))).count());
            }

            var read = assertInstanceOf(Map.class, f.run(Map.of("dbId", "source", "action", "read")));
            assertEquals(count, read.get("total"));
            assertEquals("FILE", read.get("resourceKind"));
            assertEquals(3, f.base.reads);
            assertTrue(f.writes().isEmpty());
            var rows = assertInstanceOf(List.class, read.get("list"));
            assertEquals(count, rows.size());
            for (Object item : rows) {
                var row = assertInstanceOf(Map.class, item);
                assertEquals(0.1, ((Number) row.get("maxFileSizeMb")).doubleValue());
                assertEquals(104858, ((Number) row.get("maxFileBytes")).intValue());
                assertEquals(6, row.get("fieldCount"));
                assertEquals("linked", row.get("relatedTableId"));
                assertEquals("linked-column", row.get("relatedFieldId"));
                assertEquals(row.get("fileCode"), row.get("collectionId"));
            }

            f.base.jdbc.update("update db_table_t set created_by='original',updated_by='last-editor',created_time=?,updated_time=? where tenant_id='police' and table_type='FILE'", HISTORICAL_TIME, HISTORICAL_TIME);
            f.base.jdbc.update("update db_table_column_t set created_by='original',updated_by='last-editor',created_time=?,updated_time=? where tenant_id='police' and table_id<>'linked'", HISTORICAL_TIME, HISTORICAL_TIME);
            assertInstanceOf(Map.class, f.run(saveBody(rows)));
            f.assertTransactionReadsAndWrites(5, 3);
            assertEquals(originalFiles, fileIds(f));
            assertEquals(originalAttributes, attributeIds(f));
            assertEquals(count, auditCount(f, "db_table_t", "table_type='FILE'", "last-editor"));
            assertEquals(count * 6, auditCount(f, "db_table_column_t", "table_id<>'linked'", "last-editor"));

            var complete = assertInstanceOf(Map.class, f.run(Map.of("dbId", "source", "action", "complete")));
            assertEquals(2, complete.get("assetStatus"));
            assertEquals(count, complete.get("total"));
            f.assertTransactionReadsAndWrites(5, 2);
            assertEquals(1, f.refreshes);
            assertEquals(0, f.log.refreshWarnings);
            assertComplete(f, count);
            assertEquals(count, auditCount(f, "db_table_t", "table_type='FILE'", ACTOR));
            assertEquals(count * 6, auditCount(f, "db_table_column_t", "table_id<>'linked'", "last-editor"));

            var repeated = assertInstanceOf(Map.class, f.run(saveBody(rows)));
            assertEquals(2, repeated.get("assetStatus"));
            f.assertTransactionReadsAndWrites(5, 3);
            assertComplete(f, count);
            assertEquals(originalFiles, fileIds(f));
            assertEquals(originalAttributes, attributeIds(f));
            assertEquals(1, f.refreshes);
            System.out.printf("File registration: %d definitions, save 4 reads/3 writes, read 3/0, reimport 5/3, complete 5/2%n", count);
        }
    }

    @Test
    void actualSaveRejectsForeignFileTableAndFieldBeforeAnyWrite() throws Exception {
        var f = new Fixture(1);
        f.linkedTable();
        var before = persistedState(f);
        var foreignFile = new LinkedHashMap<>(newFile(3));
        foreignFile.put("tableId", "foreign");
        assertRejected(404, f.run(saveBody(List.of(f.files.getFirst(), foreignFile))));
        assertTrue(f.writes().isEmpty());
        assertEquals(before, persistedState(f));

        var foreignReference = new LinkedHashMap<>(newFile(4));
        foreignReference.put("relatedTableName", "outside_table");
        assertRejected(422, f.run(saveBody(List.of(foreignReference))));
        assertTrue(f.writes().isEmpty());
        assertEquals(before, persistedState(f));

        foreignReference.put("relatedTableName", "person");
        foreignReference.put("relatedFieldName", "foreign_field");
        assertRejected(422, f.run(saveBody(List.of(foreignReference))));
        assertTrue(f.writes().isEmpty());
        assertEquals(before, persistedState(f));

        var firstNewFile = newFile(7);
        var duplicateCode = new LinkedHashMap<>(newFile(8));
        duplicateCode.put("fileCode", firstNewFile.get("fileCode"));
        assertRejected(422, f.run(saveBody(List.of(firstNewFile, duplicateCode))));
        assertTrue(f.writes().isEmpty());
        assertEquals(before, persistedState(f));

        assertRejected(404, f.run(Map.of("dbId", "foreign-source", "action", "read")));
        assertTrue(f.writes().isEmpty());
        assertEquals(before, persistedState(f));
        assertEquals(0, f.refreshes);
    }

    @Test
    void failureOfAFieldBatchRollsBackTheEarlierFileBatch() throws Exception {
        var f = new Fixture(0);
        f.linkedTable();
        f.base.jdbc.execute("alter table db_table_column_t add constraint reject_file_format check (column_name <> 'file_format')");
        var before = persistedState(f);
        var failure = assertThrows(MagicScriptException.class, () -> f.run(saveBody(List.of(newFile(1), newFile(2)))));
        assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("reject_file_format"));
        f.assertTransactionReadsAndWrites(4, 2);
        assertTrue(f.writes().getFirst().targets("db_table_t"));
        assertTrue(f.writes().getLast().targets("db_table_column_t"));
        assertEquals(before, persistedState(f));
        assertEquals(0, f.refreshes);
    }

    @Test
    void guardedFieldUpdateMismatchReturnsConflictAndRollsBackTheFilePatch() throws Exception {
        var f = new Fixture(0);
        f.linkedTable();
        assertInstanceOf(Map.class, f.run(saveBody(List.of(newFile(1)))));
        var before = persistedState(f);
        var target = attributeIds(f).getFirst().get("tid");
        f.trace.beforeFieldPatch = connection -> {
            try (var statement = connection.prepareStatement("update db_table_column_t set is_del=1 where tid=?")) {
                statement.setObject(1, target);
                assertEquals(1, statement.executeUpdate());
            }
        };
        var changed = new LinkedHashMap<>(newFile(1));
        changed.put("fileName", "修订文件名");

        var result = assertInstanceOf(ExitValue.class, f.run(saveBody(List.of(changed))));
        assertArrayEquals(new Object[] {409, "文件字段快照已变化，请刷新后重试"}, result.getValues());
        f.assertTransactionReadsAndWrites(5, 2);
        assertTrue(f.writes().stream().allMatch(call -> call.operation().equals("executeBatch")));
        assertEquals(before, persistedState(f));
        assertEquals(0, f.refreshes);
    }

    @Test
    void completionReadsACompetingDefinitionCommittedBeforeTheSourceLock() throws Exception {
        var f = new Fixture(0);
        f.linkedTable();
        assertInstanceOf(Map.class, f.run(saveBody(List.of(newFile(1)))));
        f.trace.beforeLock = () -> addCompetingDefinition(f);

        var result = assertInstanceOf(Map.class, f.run(Map.of("dbId", "source", "action", "complete")));
        assertEquals(2, result.get("total"));
        assertEquals(2, result.get("assetStatus"));
        f.assertTransactionReadsAndWrites(5, 2);
        assertComplete(f, 2);
        assertEquals(1, f.refreshes);
        assertEquals(0, f.log.refreshWarnings);
    }

    private static Map<String, Object> newFile(int index) {
        return Map.of("fileCode", "validation_file_" + index, "fileName", "验证文件" + index,
                "fileDescription", "合成文件登记定义", "fileFormat", "jpg", "maxFileSizeMb", 0.1,
                "relatedTableName", "person", "relatedFieldName", "photo_id");
    }

    private static Map<String, Object> saveBody(List<?> files) {
        return Map.of("dbId", "source", "action", "save", "files", files);
    }

    private static void assertRejected(int code, Object response) {
        assertEquals(code, assertInstanceOf(ExitValue.class, response).getValues()[0]);
    }

    private static List<Map<String, Object>> fileIds(Fixture f) {
        return f.base.jdbc.queryForList("select tid, table_name from db_table_t where tenant_id='police' and table_type='FILE' order by tid");
    }

    private static List<Map<String, Object>> attributeIds(Fixture f) {
        return f.base.jdbc.queryForList("select tid,table_id,column_name from db_table_column_t where tenant_id='police' and table_id<>'linked' order by tid");
    }

    private static int auditCount(Fixture f, String table, String rows, String updater) {
        return f.base.jdbc.queryForObject("select count(*) from " + table + " where tenant_id='police' and " + rows
                + " and created_by='original' and created_time=? and updated_by=? and updated_time>?", Integer.class,
                HISTORICAL_TIME, updater, HISTORICAL_TIME);
    }

    private static void assertComplete(Fixture f, int fileCount) {
        var jdbc = f.base.jdbc;
        assertEquals(fileCount, jdbc.queryForObject("select count(*) from db_table_t where tenant_id='police' and table_type='FILE' and asset_status=2 and flow_status=2", Integer.class));
        assertEquals(Map.of("asset_status", 2, "flow_status", 2, "updated_by", ACTOR),
                jdbc.queryForMap("select asset_status,flow_status,updated_by from db_datasource_t where tid='source'"));
        assertEquals(Map.of("asset_status", 1, "flow_status", 3),
                jdbc.queryForMap("select asset_status,flow_status from db_table_t where tid='linked'"));
    }

    private static List<List<Map<String, Object>>> persistedState(Fixture f) {
        var jdbc = f.base.jdbc;
        return List.of(jdbc.queryForList("select tid,asset_status,flow_status,updated_by,updated_time from db_datasource_t order by tid"),
                jdbc.queryForList("select tid,table_name_cn,table_comment,asset_status,flow_status,updated_by,updated_time,is_del from db_table_t order by tid"),
                jdbc.queryForList("select tid,table_id,column_name,updated_by,updated_time,is_del from db_table_column_t order by tid"));
    }

    private static void addCompetingDefinition(Fixture f) {
        var source = f.base.jdbc.getDataSource();
        assertTrue(TransactionSynchronizationManager.hasResource(f.trace));
        try (var connection = source.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:detail_fields_"));
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,table_name_cn,
                        table_comment,field_governance_config,asset_status,flow_status,is_del)
                    values('file-later','police','source','FILE','validation_file_later','并发保存',
                        '隔离测试文件','{"fileRegistration":{"fileCode":"validation_file_later","fileFormat":"pdf","maxFileSizeMb":1}}',0,0,0)
                    """)) {
                assertEquals(1, insert.executeUpdate());
            }
            try (var insert = connection.prepareStatement("""
                    insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del)
                    values(?,'police','file-later',?,0)
                    """)) {
                for (String attribute : FILE_ATTRIBUTES) {
                    insert.setString(1, "file-later-" + attribute);
                    insert.setString(2, attribute);
                    insert.addBatch();
                }
                assertEquals(FILE_ATTRIBUTES.size(), insert.executeBatch().length);
            }
            connection.commit();
        } catch (SQLException failure) {
            throw new IllegalStateException("Competing in-memory definition could not commit", failure);
        }
    }
}
