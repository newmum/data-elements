package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicScriptException;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the canonical batch job endpoint with real Magic transactions and isolated JDBC connections. */
class BatchCreateJobMagicTest {
    private static final String TENANT = "police";
    private static final String SOURCE_ID = "ods_batch_create_job_01";

    @Test
    void sameSourceClaimChecksTheLeaseCommittedAfterItsTentativeRead() throws Exception {
        var f = new Fixture();
        f.pairs(1);
        f.job("job-a", TENANT, "source-000", "target-000");
        f.job("job-b", TENANT, "source-000", "target-000");
        var tentativeRead = new CountDownLatch(1);
        var allowClaim = new CountDownLatch(1);
        var once = new AtomicBoolean();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var second = threads.submit(() -> f.run(claim("job-b"), statement -> {
                if (statement.isReadOf("ods_batch_create_item_t") && once.compareAndSet(false, true)) {
                    tentativeRead.countDown();
                    await(allowClaim);
                }
            }));
            try {
                await(tentativeRead);
                var first = f.run(claim("job-a"));
                assertClaimed(first, "job-a-item", 1);
                assertEquals("RUNNING", f.item("job-a").get("status"));
            } finally {
                allowClaim.countDown();
            }
            var result = second.get(15, TimeUnit.SECONDS);
            assertEquals(true, result.map().get("busy"), "The second claim must observe the first job's committed lease");
            assertEquals(false, result.map().get("done"));
            assertEquals("RUNNING", result.map().get("status"));
            assertFalse(result.map().containsKey("leaseToken"));
            assertEquals(0, result.writes());
            assertEquals("PENDING", f.item("job-b").get("status"));
            assertEquals(0, f.item("job-b").get("attempt_count"));
            assertNull(f.item("job-b").get("lease_token"));
            assertEquals(1, f.jdbc.queryForObject("select count(*) from ods_batch_create_item_t where status='RUNNING'", Integer.class));
            assertFreshSourceLock(result);
        }
    }

    @Test
    void claimOfAnotherSourceCompletesWhileTheFirstSourceRowRemainsLocked() throws Exception {
        var f = new Fixture();
        f.pairs(2);
        f.job("job-a", TENANT, "source-000", "target-000");
        f.job("job-b", TENANT, "source-001", "target-001");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> f.run(claim("job-a"), statement -> {
                if (statement.isSourceLock()) {
                    locked.countDown();
                    await(release);
                }
            }));
            try {
                await(locked);
                var second = threads.submit(() -> f.run(claim("job-b"))).get(10, TimeUnit.SECONDS);
                assertClaimed(second, "job-b-item", 1);
                assertEquals("RUNNING", f.item("job-b").get("status"));
                assertEquals("PENDING", f.item("job-a").get("status"));
            } finally {
                release.countDown();
            }
            assertClaimed(first.get(15, TimeUnit.SECONDS), "job-a-item", 1);
        }
    }

    @Test
    void expiredCrossJobLeaseIsClearedAndCannotCompleteAfterReclaim() throws Exception {
        var f = new Fixture();
        f.pairs(1);
        f.job("job-a", TENANT, "source-000", "target-000");
        f.job("job-b", TENANT, "source-000", "target-000");
        f.expire("job-a", "old-lease");

        var leaseB = assertClaimed(f.run(claim("job-b")), "job-b-item", 1);
        var expired = f.item("job-a");
        assertEquals("PENDING", expired.get("status"));
        assertEquals(1, expired.get("attempt_count"));
        assertNull(expired.get("lease_token"));
        assertNull(expired.get("claimed_time"));
        assertRejected(409, f.run(failedCompletion("job-a", "old-lease")));

        f.task("foreign-task", "other", "source-000", "target-000");
        f.task("wrong-target", TENANT, "source-000", "wrong");
        f.task("correct-task", TENANT, "source-000", "target-000");
        assertRejected(409, f.run(successfulCompletion("job-b", leaseB, "foreign-task")));
        assertRejected(409, f.run(successfulCompletion("job-b", leaseB, "wrong-target")));
        var completed = f.run(successfulCompletion("job-b", leaseB, "correct-task"));
        assertEquals("SUCCEEDED", map(completed.map().get("job")).get("status"));
        assertEquals("correct-task", f.item("job-b").get("task_id"));
        assertEquals(0, f.run(successfulCompletion("job-b", leaseB, "correct-task")).writes());

        var leaseA = assertClaimed(f.run(claim("job-a")), "job-a-item", 2);
        assertNotEquals("old-lease", leaseA);
        assertRejected(409, f.run(failedCompletion("job-a", "old-lease")));
        assertEquals(leaseA, f.item("job-a").get("lease_token"));
        assertEquals("RUNNING", f.item("job-a").get("status"));
        assertEquals("SUCCEEDED", map(f.run(successfulCompletion("job-a", leaseA, "correct-task")).map().get("job")).get("status"));
        assertRejected(409, f.run(successfulCompletion("job-a", "old-lease", "correct-task")));
        assertEquals(0, f.run(successfulCompletion("job-a", leaseA, "correct-task")).writes());
        assertEquals(leaseA, f.item("job-a").get("lease_token"));

        f.job("aged", TENANT, "source-000", "target-000");
        f.expire("aged", "aged-current-lease");
        assertEquals("SUCCEEDED", map(f.run(successfulCompletion("aged", "aged-current-lease", "correct-task"))
                .map().get("job")).get("status"));
    }

    @Test
    void aFreshDuplicateLeaseBlocksClaimAndExpiredDuplicatesResetInOneUpdate() throws Exception {
        var f = new Fixture();
        f.pairs(1);
        for (String job : List.of("a-expired", "b-expired", "z-fresh", "next")) {
            f.job(job, TENANT, "source-000", "target-000");
        }
        f.expire("a-expired", "first-old-lease");
        f.jdbc.update("update ods_batch_create_item_t set status='RUNNING',attempt_count=1,lease_token='second-old-lease' where job_id='b-expired'");
        f.jdbc.update("update ods_batch_create_item_t set status='RUNNING',attempt_count=1,lease_token='current-lease',claimed_time=current_timestamp where job_id='z-fresh'");

        var busy = f.run(claim("next"));
        assertEquals(Map.of("done", false, "busy", true, "status", "RUNNING"), busy.map());
        assertEquals(0, busy.writes());
        assertEquals("RUNNING", f.item("a-expired").get("status"));
        assertEquals("RUNNING", f.item("b-expired").get("status"));
        f.jdbc.update("update ods_batch_create_item_t set status='SUCCEEDED' where job_id='z-fresh'");

        var claimed = f.run(claim("next"));
        assertClaimed(claimed, "next-item", 1);
        assertEquals(1, claimed.statements.stream().filter(call -> call.sql.startsWith("update ods_batch_create_item_t")
                && call.sql.matches("(?s).*\\bitem_id\\s+in\\s*\\(.*")).count());
        for (String job : List.of("a-expired", "b-expired")) {
            var item = f.item(job);
            assertEquals("PENDING", item.get("status"));
            assertEquals(1, item.get("attempt_count"));
            assertNull(item.get("lease_token"));
            assertNull(item.get("claimed_time"));
        }
    }

    @Test
    void earlierPendingItemAndLaterExpiredItemLockTheirSourcesInKeyOrder() throws Exception {
        var f = new Fixture();
        f.pairs(2);
        f.job("job", TENANT, "source-001", "target-001");
        f.addItem("job", "later-item", TENANT, 1, "source-000", "target-000");
        f.jdbc.update("update ods_batch_create_job_t set total_count=2 where job_id='job'");
        f.jdbc.update("update ods_batch_create_item_t set status='RUNNING',attempt_count=1,lease_token='old-lease' where item_id='later-item'");

        var result = f.run(claim("job"));
        assertClaimed(result, "job-item", 1);
        assertEquals(List.of("source-000", "source-001"), result.statements.stream().filter(SqlCall::isSourceLock)
                .map(SqlCall::lockedKey).toList());
        var stale = f.jdbc.queryForMap("select status,lease_token,claimed_time from ods_batch_create_item_t where item_id='later-item'");
        assertEquals("PENDING", stale.get("status"));
        assertNull(stale.get("lease_token"));
        assertNull(stale.get("claimed_time"));
    }

    @Test
    void claimAndRetryEnforceTenantSourceAndFreshLeaseBoundaries() throws Exception {
        var f = new Fixture();
        f.pairs(2);
        f.job("outside", "other", "source-000", "target-000");
        f.job("own", TENANT, "source-000", "target-000");
        f.job("inactive", TENANT, "source-001", "target-001");
        f.jdbc.update("update ods_batch_create_item_t set status='RUNNING',lease_token='outside-token',claimed_time=current_timestamp where job_id='outside'");
        f.jdbc.update("update db_table_t set is_del=1 where tid='source-001'");
        assertRejected(404, f.run(claim("outside")));
        assertRejected(409, f.run(claim("inactive")));
        var lease = assertClaimed(f.run(claim("own")), "own-item", 1);
        assertEquals(Map.of("done", false, "busy", true, "status", "RUNNING"), f.run(claim("own")).map());
        assertRejected(409, f.run(Map.of("action", "retry", "jobId", "own")));
        assertEquals(lease, f.item("own").get("lease_token"));
        f.expire("own", lease);
        var retried = f.run(Map.of("action", "retry", "jobId", "own"));
        assertEquals("RUNNING", map(retried.map().get("job")).get("status"));
        assertEquals("PENDING", f.item("own").get("status"));
        assertNull(f.item("own").get("lease_token"));
        assertNull(f.item("own").get("claimed_time"));
        assertEquals("outside-token", f.item("outside").get("lease_token"));
        assertEquals("PENDING", f.item("inactive").get("status"));
        var stopped = f.run(Map.of("action", "stop", "jobId", "own"));
        assertEquals("STOPPED", map(stopped.map().get("job")).get("status"));
        assertEquals(Map.of("done", true, "stopped", true, "status", "STOPPED"), f.run(claim("own")).map());
        assertEquals("PENDING", f.item("own").get("status"));
    }

    @Test
    void previewAndCreateUseTheSameBoundedReadsAndOneItemBatchAtTwentyAndHundred() throws Exception {
        var f = new Fixture();
        f.pairs(120);
        f.jdbc.execute("""
                insert into db_table_t(tid,tenant_id,datasource_id,table_name,asset_status,business_type,is_del) values
                  ('foreign-source','other','source-db','0000',2,'业务表',0),
                  ('deleted-source','police','source-db','0001',2,'业务表',1);
                insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,asset_status,business_type,is_del)
                  values ('foreign-target','other','target-db','Foreign','source-000',2,'业务表',0);
                """);
        f.task("active-task", TENANT, "source-000", "target-000");
        f.task("foreign-task", "other", "source-001", "target-001");
        f.task("wrong-target-task", TENANT, "source-001", "foreign-target");
        f.task("last-page-task", TENANT, "source-100", "target-100");
        var previewReads = new ArrayList<Integer>();
        var createReads = new ArrayList<Integer>();
        for (int size : List.of(20, 100)) {
            var preview = f.run(Map.of("action", "preview", "datasourceId", "source-db", "pageNum", 1,
                    "pageSize", size, "sortField", "tableName", "sortDir", "asc"));
            assertEquals(120L, ((Number) preview.map().get("total")).longValue());
            var rows = rows(preview.map().get("list"));
            assertEquals(size, rows.size());
            assertEquals("source-000", rows.getFirst().get("tid"));
            assertEquals("source-%03d".formatted(size - 1), rows.getLast().get("tid"));
            assertEquals(List.of("target-000"), rows(rows.getFirst().get("targetTables")).stream()
                    .map(row -> row.get("targetTableId")).toList());
            assertEquals("active-task", rows.getFirst().get("accessTaskId"));
            assertEquals(0, rows.get(1).get("taskCount"));
            assertEquals(0, preview.writes());
            previewReads.add(preview.reads());

            var selected = rows.stream().map(row -> {
                var target = rows(row.get("targetTables")).getFirst();
                return Map.of("sourceTableId", row.get("tid"), "targetTableId", target.get("targetTableId"));
            }).toList();
            var create = create("request-" + size, selected);
            var saved = f.run(create);
            var job = map(saved.map().get("job"));
            assertEquals(size, job.get("totalCount"));
            var items = rows(saved.map().get("items"));
            assertEquals(selected.stream().map(row -> row.get("sourceTableId")).toList(),
                    items.stream().map(row -> row.get("sourceTableId")).toList());
            assertTrue(items.stream().noneMatch(row -> row.containsKey("leaseToken")));
            var itemWrites = saved.statements.stream().filter(call -> call.sql.startsWith("insert into ods_batch_create_item_t")).toList();
            assertEquals(1, itemWrites.size());
            assertEquals("executeBatch", itemWrites.getFirst().operation);
            assertEquals(size, itemWrites.getFirst().batchEntries);
            assertEquals(2, saved.writes());
            createReads.add(saved.reads());

            var replay = f.run(create);
            assertEquals(job.get("jobId"), map(replay.map().get("job")).get("jobId"));
            assertEquals(items.stream().map(row -> row.get("itemId")).toList(),
                    rows(replay.map().get("items")).stream().map(row -> row.get("itemId")).toList());
            assertEquals(0, replay.writes());

            var invalid = new ArrayList<>(selected);
            invalid.set(size - 1, Map.of("sourceTableId", selected.getLast().get("sourceTableId"),
                    "targetTableId", "foreign-target"));
            assertRejected(400, f.run(create("foreign-" + size, invalid)));
        }
        assertEquals(List.of(8, 8), previewReads);
        assertEquals(List.of(10, 10), createReads);
        var pageTwo = f.run(Map.of("action", "preview", "datasourceId", "source-db", "pageNum", 2,
                "pageSize", 100, "sortField", "tableName", "sortDir", "asc"));
        var pageTwoRows = rows(pageTwo.map().get("list"));
        assertEquals(20, pageTwoRows.size());
        assertEquals("source-100", pageTwoRows.getFirst().get("tid"));
        assertEquals("source-119", pageTwoRows.getLast().get("tid"));
        assertEquals("last-page-task", pageTwoRows.getFirst().get("accessTaskId"));
        assertEquals(8, pageTwo.reads());

        var invalidSortSql = new ArrayList<SqlCall>();
        var invalidSort = assertThrows(MagicScriptException.class, () -> f.run(Map.of("action", "preview",
                "datasourceId", "source-db", "pageNum", 1, "pageSize", 20, "sortField", "attemptCount"), invalidSortSql::add));
        assertTrue(invalidSort.getMessage().contains("不支持的排序字段：attemptCount"));
        assertEquals(1, invalidSortSql.size());
        assertTrue(invalidSortSql.getFirst().isReadOf("db_datasource_t"), "Only the existing source check may run before rejecting sort");
        assertEquals(2, f.jdbc.queryForObject("select count(*) from ods_batch_create_job_t", Integer.class));
        System.out.println("Batch job 20/100: preview reads=" + previewReads + ", create reads=" + createReads
                + ", one parent insert and one item batch each");
    }

    private static void assertFreshSourceLock(Outcome outcome) {
        var statements = outcome.statements;
        int sourceLock = -1;
        for (int index = 0; index < statements.size(); index++) {
            if (statements.get(index).isSourceLock()) {
                sourceLock = index;
                break;
            }
        }
        assertTrue(sourceLock >= 0);
        var first = statements.get(sourceLock);
        assertTrue(first.transactional);
        assertTrue(statements.subList(0, sourceLock).stream().anyMatch(call -> call.isReadOf("ods_batch_create_item_t") && !call.transactional));
        assertTrue(statements.subList(0, sourceLock).stream().noneMatch(call -> call.transactional && call.read() && !call.lock()));
        assertTrue(statements.stream().filter(SqlCall::transactional).allMatch(call -> call.connection == first.connection));
    }

    private static String assertClaimed(Outcome result, String itemId, int attempts) {
        var response = result.map();
        assertEquals(false, response.get("done"));
        assertEquals(false, response.get("busy"));
        var item = map(response.get("item"));
        assertEquals(itemId, item.get("itemId"));
        assertEquals("RUNNING", item.get("status"));
        assertEquals(attempts, item.get("attemptCount"));
        assertFalse(item.containsKey("leaseToken"));
        var lease = assertInstanceOf(String.class, response.get("leaseToken"));
        assertFalse(lease.isBlank());
        return lease;
    }

    private static void assertRejected(int code, Outcome result) {
        assertEquals(code, assertInstanceOf(ExitValue.class, result.value).getValues()[0]);
        assertEquals(0, result.writes());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(12, TimeUnit.SECONDS), "The claim did not reach its expected JDBC gate");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The claim fixture was interrupted", failure);
        }
    }

    private static Map<String, Object> claim(String job) {
        return Map.of("action", "claim", "jobId", job);
    }

    private static Map<String, Object> create(String key, List<?> items) {
        return Map.of("action", "create", "requestKey", key, "jobName", "Validation job",
                "sourceDbId", "source-db", "targetDbId", "target-db", "matchRule", Map.of(), "items", items);
    }

    private static Map<String, Object> failedCompletion(String job, String lease) {
        return Map.of("action", "complete", "jobId", job, "itemId", job + "-item", "leaseToken", lease,
                "success", false, "error", "validation failure");
    }

    private static Map<String, Object> successfulCompletion(String job, String lease, String task) {
        return Map.of("action", "complete", "jobId", job, "itemId", job + "-item", "leaseToken", lease,
                "success", true, "taskId", task);
    }

    private static Map<?, ?> map(Object value) { return assertInstanceOf(Map.class, value); }

    private static List<Map<?, ?>> rows(Object value) {
        var list = assertInstanceOf(List.class, value);
        var result = new ArrayList<Map<?, ?>>();
        for (Object item : list) result.add(map(item));
        return result;
    }

    public static final class Session {
        public String id() { return TENANT; }
    }

    private record Outcome(Object value, List<SqlCall> statements) {
        Map<?, ?> map() { return BatchCreateJobMagicTest.map(value); }
        int reads() { return (int) statements.stream().filter(SqlCall::read).count(); }
        int writes() { return statements.size() - reads(); }
    }

    private record SqlCall(String sql, String operation, boolean transactional, Connection connection,
                           int batchEntries, String lockedKey) {
        boolean read() { return sql.startsWith("select"); }
        boolean lock() { return read() && sql.endsWith("for update"); }
        boolean isReadOf(String table) { return read() && sql.matches("(?s).*\\bfrom\\s+" + table + "\\b.*"); }
        boolean isSourceLock() { return lock() && isReadOf("db_table_t"); }
    }

    private record Capture(List<SqlCall> statements, Consumer<SqlCall> after) {}

    private static final class TracingDataSource extends DelegatingDataSource {
        final ThreadLocal<Capture> active = new ThreadLocal<>();

        TracingDataSource(DriverManagerDataSource physical) { super(physical); }

        @Override public Connection getConnection() throws SQLException {
            var real = super.getConnection();
            real.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var sql = ((String) arguments[0]).strip().toLowerCase(Locale.ROOT).replace("\"", "").replace("`", "");
                            return track(real, sql, (PreparedStatement) invoke(real, method, arguments));
                        }
                        if (method.getName().equals("getMetaData")) {
                            var metadata = real.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (unused, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://batch-claim-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("equals")) return proxy == arguments[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return invoke(real, method, arguments);
                    });
        }

        private PreparedStatement track(Connection real, String sql, PreparedStatement delegate) {
            int[] batch = {0};
            String[] lockedKey = {null};
            long lastParameter = sql.endsWith("for update") ? sql.chars().filter(character -> character == '?').count() : -1;
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        String operation = method.getName();
                        if (operation.equals("addBatch")) batch[0]++;
                        if (operation.equals("clearBatch")) batch[0] = 0;
                        if (lastParameter > 0 && (operation.equals("setString") || operation.equals("setObject"))
                                && ((Integer) arguments[0]) == lastParameter && arguments[1] instanceof String value) {
                            lockedKey[0] = value;
                        }
                        var capture = active.get();
                        if (capture == null || !operation.startsWith("execute")) return invoke(delegate, method, arguments);
                        var call = new SqlCall(sql, operation, TransactionSynchronizationManager.hasResource(this), real,
                                batch[0], lockedKey[0]);
                        capture.statements.add(call);
                        var result = invoke(delegate, method, arguments);
                        capture.after.accept(call);
                        return result;
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

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final TracingDataSource trace;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final StructuredTableWriteMagicModule tableWrite;
        final String source;

        Fixture() throws Exception {
            var physical = new DriverManagerDataSource("jdbc:h2:mem:batch_job_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=7000");
            jdbc = new JdbcTemplate(physical);
            trace = new TracingDataSource(physical);
            var magicJdbc = new JdbcTemplate(trace);
            tableRead = new StructuredTableReadMagicModule(magicJdbc);
            tableWrite = new StructuredTableWriteMagicModule(magicJdbc);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(trace);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("batch-job-test"));
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(row -> row);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
            source = CanonicalMagicSources.byId(SOURCE_ID)
                    .replaceAll("(?m)^import (tenantRuntime|tableSort|tableRead|tableWrite)\\R", "");
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64) primary key,tenant_id varchar(32),is_del int,
                        asset_status int,db_name varchar(100),db_type varchar(32),updated_time timestamp,show_connect int);
                    create table db_table_t(tid varchar(64) primary key,tenant_id varchar(32),datasource_id varchar(64),
                        table_name varchar(100),table_name_cn varchar(100),source_table_id varchar(64),source_catalog_id varchar(64),
                        asset_status int,business_type varchar(20),is_del int,created_time timestamp,updated_time timestamp);
                    create table data_access_agg_task_t(tid varchar(64) primary key,tenant_id varchar(32),
                        source_table_id varchar(64),target_table_id varchar(64),is_del int);
                    create table ods_batch_create_job_t(job_id varchar(64) primary key,tenant_id varchar(32) not null,
                        request_key varchar(64) not null,job_name varchar(255) not null,source_db_id varchar(64) not null,
                        target_db_id varchar(64) not null,match_rule_json clob,status varchar(16) not null,
                        stop_requested int not null,total_count int not null,created_time timestamp not null,
                        updated_time timestamp not null,finished_time timestamp,unique(tenant_id,request_key));
                    create table ods_batch_create_item_t(item_id varchar(64) primary key,job_id varchar(64) not null,
                        tenant_id varchar(32) not null,item_index int not null,source_table_id varchar(64) not null,
                        source_table_name varchar(255) not null,source_catalog_id varchar(64),target_table_id varchar(64) not null,
                        target_table_name varchar(255) not null,status varchar(16) not null,attempt_count int not null,
                        lease_token varchar(64),claimed_time timestamp,completed_time timestamp,task_id varchar(64),
                        was_existing int,error_message clob,created_time timestamp not null,updated_time timestamp not null,
                        unique(tenant_id,job_id,item_index),unique(tenant_id,job_id,source_table_id));
                    insert into db_datasource_t values ('source-db','police',0,2,'Source','mysql',current_timestamp,1),
                        ('target-db','police',0,2,'Target','mysql',current_timestamp,1);
                    """);
        }

        void pairs(int count) {
            var sources = new ArrayList<Object[]>();
            var targets = new ArrayList<Object[]>();
            for (int index = 0; index < count; index++) {
                String sequence = "%03d".formatted(index);
                sources.add(new Object[]{"source-" + sequence, "Table " + sequence});
                targets.add(new Object[]{"target-" + sequence, "Target " + sequence, "source-" + sequence});
            }
            jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,table_name,asset_status,business_type,is_del) values(?,'police','source-db',?,2,'业务表',0)", sources);
            jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,asset_status,business_type,is_del) values(?,'police','target-db',?,?,2,'业务表',0)", targets);
        }

        void job(String job, String tenant, String sourceId, String target) {
            jdbc.update("""
                    insert into ods_batch_create_job_t(job_id,tenant_id,request_key,job_name,source_db_id,
                        target_db_id,status,stop_requested,total_count,created_time,updated_time)
                    values(?,?,?,?,'source-db','target-db','RUNNING',0,1,current_timestamp,current_timestamp)
                    """, job, tenant, job, job);
            addItem(job, job + "-item", tenant, 0, sourceId, target);
        }

        void addItem(String job, String itemId, String tenant, int index, String sourceId, String target) {
            jdbc.update("""
                    insert into ods_batch_create_item_t(item_id,job_id,tenant_id,item_index,source_table_id,source_table_name,
                        target_table_id,target_table_name,status,attempt_count,created_time,updated_time)
                    values(?,?,?,?,?,?,?,?,'PENDING',0,current_timestamp,current_timestamp)
                    """, itemId, job, tenant, index, sourceId, sourceId, target, target);
        }

        void expire(String job, String lease) {
            jdbc.update("update ods_batch_create_item_t set status='RUNNING',lease_token=?,attempt_count=1,claimed_time=? where job_id=?",
                    lease, Timestamp.from(Instant.now().minusSeconds(660)), job);
        }

        void task(String task, String tenant, String sourceId, String target) {
            jdbc.update("insert into data_access_agg_task_t values(?,?,?,?,0)", task, tenant, sourceId, target);
        }

        Map<String, Object> item(String job) {
            return jdbc.queryForMap("select * from ods_batch_create_item_t where job_id=?", job);
        }

        Outcome run(Map<String, Object> body) { return run(body, ignored -> {}); }

        Outcome run(Map<String, Object> body, Consumer<SqlCall> after) {
            var statements = new ArrayList<SqlCall>();
            trace.active.set(new Capture(statements, after));
            try (var tenant = TenantContext.use(TENANT)) {
                var context = new MagicScriptContext(Map.of("db", db, "body", body, "tenantRuntime", new Session(),
                        "tableSort", new TableSortMagicModule(), "tableRead", tableRead, "tableWrite", tableWrite));
                return new Outcome(MagicScript.create(source, null).execute(context), List.copyOf(statements));
            } finally {
                trace.active.remove();
            }
        }
    }
}
