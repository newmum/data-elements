package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicScriptException;
import org.ssssssss.script.runtime.ExitValue;

class MultiTableGroupMagicTest {
    private static final String ACTOR = "operator";
    private static final String GROUP = "108ca78f224b4a51b7ab2d564d1b341b";
    private static final String TENANT = "tenant-a";
    private static final Scope OWNER = new Scope("OWNER", ACTOR, List.of());
    private static final Scope ALL = new Scope("ALL", ACTOR, List.of());

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void candidatesAndCreationUseFixedReadsAndBatchWrites(int size) throws Exception {
        var fixture = new Fixture();
        var ids = fixture.tasks(size);
        Map<?, ?> page = map(fixture.candidates(OWNER, Map.of("pageSize", size, "sortDir", "asc")));
        assertThat(page.get("total")).isEqualTo((long) size);
        assertThat(page.get("pageNum")).isEqualTo(1);
        assertThat(page.get("pageSize")).isEqualTo(size);
        assertThat(candidateIds(page)).isEqualTo(ids);
        assertThat(map(((List<?>) page.get("list")).getFirst()).keySet().stream().map(Object::toString))
                .containsExactlyInAnyOrder("taskId", "taskName", "taskStatus", "pipelineId", "sourceTableId",
                        "sourceTableName", "sourceTableNameCn", "datasourceName");
        assertThat(fixture.reads.get()).isEqualTo(2);
        assertThat(fixture.writes.get()).isZero();

        var submitted = ids.reversed();
        fixture.resetCounts();
        assertThat(fixture.save(OWNER, request(submitted))).isEqualTo(result(true, size));
        assertThat(fixture.reads.get()).isEqualTo(2);
        assertThat(fixture.writes.get()).isEqualTo(2);
        assertThat(fixture.savedIds()).containsExactlyElementsOf(submitted);
        System.out.println("Multi-table candidates and save: size=" + size + ", page reads=2, save reads=2, writes=2");

        fixture.resetCounts();
        assertThat(fixture.save(OWNER, request(submitted))).isEqualTo(result(false, size));
        assertThat(fixture.reads.get()).isEqualTo(3);
        assertThat(fixture.writes.get()).isZero();
        assertThat(exitCode(fixture.save(OWNER, request(ids)))).isEqualTo(409);
    }

    @Test
    void candidatesRespectNativeSortWildcardAndTheSamePermissionsAsSave() throws Exception {
        var fixture = new Fixture();
        fixture.tasks(3);
        fixture.jdbc.execute("""
                update data_access_agg_task_t set task_name='matchB' where tid='task-001';
                update data_access_agg_task_t set task_name='matchA' where tid='task-002';
                update data_access_agg_task_t set task_name=NULL where tid='task-003';
                update db_table_t set table_name_cn='Beta' where tid='table-001';
                update db_table_t set table_name='Alpha' where tid='table-002';
                update db_table_t set table_name=NULL where tid='table-003';
                insert into db_datasource_t values
                    ('other-owner','tenant-a','Other DB','different-user','org-a',0),
                    ('foreign-db','tenant-b','Foreign DB','operator','org-a',0),
                    ('deleted-db','tenant-a','Deleted DB','operator','org-a',1);
                insert into db_table_t values
                    ('visible-org','tenant-a','other-owner','Org name',NULL,0),
                    ('foreign-table','tenant-b','owned-db','Foreign name',NULL,0),
                    ('foreign-db-table','tenant-a','foreign-db','Foreign DB name',NULL,0),
                    ('deleted-table','tenant-a','owned-db','Deleted name',NULL,1),
                    ('deleted-db-table','tenant-a','deleted-db','Deleted DB name',NULL,0);
                insert into data_access_agg_task_t values
                    ('task-org','tenant-a','visible-org','owned-db','Match Org',0,NULL,0),
                    ('task-foreign','tenant-b','table-001','owned-db','Foreign',0,NULL,0),
                    ('task-foreign-table','tenant-a','foreign-table','owned-db','Foreign table',0,NULL,0),
                    ('task-foreign-db','tenant-a','foreign-db-table','owned-db','Foreign datasource',0,NULL,0),
                    ('task-deleted-table','tenant-a','deleted-table','owned-db','Deleted table',0,NULL,0),
                    ('task-deleted-db','tenant-a','deleted-db-table','owned-db','Deleted datasource',0,NULL,0),
                    ('task-deleted','tenant-a','table-001','owned-db','Deleted task',0,NULL,1)
                """);
        var sourceSort = Map.<String, Object>of("sortField", "sourceTableName", "sortDir", "asc", "tenantId", "tenant-b");
        assertThat(candidateIds(map(fixture.candidates(OWNER, sourceSort))))
                .containsExactly("task-002", "task-001", "task-003");
        assertThat(candidateIds(map(fixture.candidates(OWNER, Map.of("keyword", "match_")))))
                .containsExactly("task-001", "task-002");
        var org = new Scope("ORG", ACTOR, List.of("org-a"));
        assertThat(candidateIds(map(fixture.candidates(org, sourceSort))))
                .containsExactly("task-002", "task-001", "task-org", "task-003");
        assertThat(candidateIds(map(fixture.candidates(ALL, sourceSort))))
                .containsExactly("task-002", "task-001", "task-org", "task-003");
        assertThat(candidateIds(map(fixture.candidates(new Scope("ORG", ACTOR, List.of()), sourceSort)))).isEmpty();
        assertThat(candidateIds(map(fixture.candidates(ALL, Map.of("pageNum", 2, "pageSize", 3, "sortDir", "asc")))))
                .containsExactly("task-003");
        assertThat(fixture.reads.get()).isEqualTo(11);
        assertThat(exitCode(fixture.candidates(new Scope("INVALID", ACTOR, List.of()), sourceSort))).isEqualTo(403);

        fixture.resetCounts();
        for (String denied : List.of("task-org", "task-foreign", "task-foreign-table", "task-foreign-db",
                "task-deleted-table", "task-deleted-db", "task-deleted", "missing")) {
            assertThat(exitCode(fixture.save(OWNER, request(List.of("task-001", denied))))).isEqualTo(403);
        }
        assertThat(fixture.reads.get()).isEqualTo(8);
        assertThat(fixture.writes.get()).isZero();
        assertThat(fixture.groupCount()).isZero();
        assertThat(fixture.save(org, request(List.of("task-001", "task-org")))).isEqualTo(result(true, 2));
    }

    @Test
    void rejectsDuplicateSourcesAndRollsBackFailedMemberBatch() throws Exception {
        var fixture = new Fixture();
        var ids = fixture.tasks(2);
        fixture.jdbc.update("update data_access_agg_task_t set source_table_id='table-001' where tid='task-002'");
        assertThat(exitCode(fixture.save(ALL, request(ids)))).isEqualTo(400);
        assertThat(fixture.writes.get()).isZero();
        fixture.jdbc.update("update data_access_agg_task_t set source_table_id='table-002' where tid='task-002'");
        fixture.jdbc.execute("alter table data_access_multi_group_item_t add constraint failure check(sort_no <> 2)");
        assertThrows(MagicScriptException.class, () -> fixture.save(ALL, request(ids)));
        assertThat(fixture.groupCount()).isZero();
        assertThat(fixture.savedIds()).isEmpty();
        fixture.jdbc.execute("alter table data_access_multi_group_item_t drop constraint failure");
        assertThat(fixture.save(ALL, request(ids))).isEqualTo(result(true, 2));
    }

    @Test
    void concurrentSameRequestReturnsWinnerAfterTheFailedTransactionRollsBack() throws Exception {
        var fixture = new Fixture();
        var body = request(fixture.tasks(2));
        fixture.concurrentReads = new CyclicBarrier(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> fixture.save(ALL, body));
            var second = threads.submit(() -> fixture.save(ALL, body));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(result(true, 2), result(false, 2));
        }
        assertThat(fixture.groupCount()).isEqualTo(1);
        assertThat(fixture.savedIds()).containsExactly("task-001", "task-002");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void relinkAndDeleteKeepFixedReadsAndPreserveTheOtherMembers(int size) throws Exception {
        var fixture = new Fixture();
        var ids = fixture.tasks(size);
        assertThat(fixture.save(OWNER, request(ids))).isEqualTo(result(true, size));
        fixture.jdbc.execute("""
                insert into data_access_agg_task_t values
                    ('replacement','tenant-a','table-001','owned-db','Replacement',0,NULL,0),
                    ('other-source','tenant-a','table-002','owned-db','Other table',0,NULL,0),
                    ('other-tenant','tenant-b','table-001','owned-db','Foreign',0,NULL,0),
                    ('deleted-task','tenant-a','table-001','owned-db','Deleted',0,NULL,1)
                """);

        for (String invalid : List.of("other-source", "other-tenant", "deleted-task", "absent")) {
            fixture.resetCounts();
            assertThat(exitCode(fixture.relink(OWNER, relinkRequest(invalid)))).isEqualTo(400);
            assertThat(fixture.reads.get()).isEqualTo(2);
            assertThat(fixture.writes.get()).isZero();
        }

        var relink = relinkRequest("replacement");
        var linked = Map.of("groupId", GROUP, "sourceTableId", "table-001", "taskId", "replacement", "linked", true);
        fixture.resetCounts();
        assertThat(fixture.relink(OWNER, relink)).isEqualTo(linked);
        assertThat(fixture.reads.get()).isEqualTo(2);
        assertThat(fixture.writes.get()).isEqualTo(1);
        fixture.resetCounts();
        assertThat(fixture.relink(OWNER, relink)).isEqualTo(linked);
        assertThat(fixture.reads.get()).isEqualTo(2);
        assertThat(fixture.writes.get()).isZero();

        var deleted = Map.of("groupId", GROUP, "deleted", true, "memberTasksPreserved", true);
        fixture.resetCounts();
        assertThat(fixture.delete(OWNER)).isEqualTo(deleted);
        assertThat(fixture.reads.get()).isEqualTo(1);
        assertThat(fixture.writes.get()).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("select is_del from data_access_multi_group_t", Integer.class)).isEqualTo(1);
        assertThat(fixture.savedIds()).containsExactlyElementsOf(ids.stream()
                .map(id -> id.equals("task-001") ? "replacement" : id).toList());
        assertThat(fixture.jdbc.queryForObject("select count(*) from data_access_agg_task_t", Integer.class)).isEqualTo(size + 4);

        fixture.resetCounts();
        assertThat(fixture.delete(OWNER)).isEqualTo(deleted);
        assertThat(fixture.reads.get()).isEqualTo(1);
        assertThat(fixture.writes.get()).isZero();
        assertThat(exitCode(fixture.relink(OWNER, relink))).isEqualTo(403);
        System.out.println("Multi-table edit: members=" + size + ", relink reads=2, delete reads=1, repeat writes=0");
    }

    @Test
    void everyMemberSourceAndTheGroupCreatorMustAuthorizeAnEdit() throws Exception {
        var fixture = new Fixture();
        var ids = fixture.tasks(3);
        assertThat(fixture.save(OWNER, request(ids))).isEqualTo(result(true, 3));
        fixture.jdbc.execute("""
                insert into db_datasource_t values
                    ('no-owner','tenant-a','Unassigned',NULL,'org-a',0),
                    ('no-org','tenant-a','No department','operator',NULL,0),
                    ('other-owner','tenant-a','Other owner','different-user','org-a',0),
                    ('other-org','tenant-a','Other org','operator','org-b',0),
                    ('foreign-db','tenant-b','Foreign','operator','org-a',0),
                    ('deleted-db','tenant-a','Deleted','operator','org-a',1)
                """);
        var org = new Scope("ORG", ACTOR, List.of("org-a"));
        Map<String, Scope> forbidden = Map.of("no-owner", OWNER, "no-org", org,
                "other-owner", OWNER, "other-org", org, "foreign-db", ALL, "deleted-db", ALL, "missing-db", ALL);
        for (var entry : forbidden.entrySet()) {
            fixture.jdbc.update("update db_table_t set datasource_id=? where tid='table-003'", entry.getKey());
            assertEditDenied(fixture, entry.getValue());
        }
        fixture.jdbc.update("update db_table_t set datasource_id='owned-db',is_del=1 where tid='table-003'");
        assertEditDenied(fixture, ALL);
        fixture.jdbc.update("update db_table_t set is_del=0 where tid='table-003'");
        assertEditDenied(fixture, new Scope("ORG", ACTOR, List.of()));
        fixture.jdbc.update("update data_access_multi_group_t set created_by='different-user'");
        assertEditDenied(fixture, ALL);
        fixture.jdbc.update("update data_access_multi_group_t set created_by='operator',tenant_id='tenant-b'");
        assertEditDenied(fixture, ALL);
        assertThat(fixture.savedIds()).isEqualTo(ids);
        assertThat(fixture.jdbc.queryForObject("select is_del from data_access_multi_group_t", Integer.class)).isZero();
    }

    private static void assertEditDenied(Fixture fixture, Scope scope) {
        fixture.resetCounts();
        assertThat(exitCode(fixture.delete(scope))).isEqualTo(403);
        assertThat(exitCode(fixture.relink(scope, relinkRequest("task-001")))).isEqualTo(403);
        assertThat(fixture.reads.get()).isEqualTo(2);
        assertThat(fixture.writes.get()).isZero();
    }

    private static Map<String, Object> relinkRequest(String taskId) {
        return Map.of("groupId", GROUP, "sourceTableId", "table-001", "taskId", taskId, "tenantId", "tenant-b");
    }

    private static Map<String, Object> request(List<String> taskIds) {
        return Map.of("groupId", GROUP, "groupName", "测试编组", "taskIds", taskIds, "tenantId", "tenant-b");
    }

    private static Map<String, Object> result(boolean created, int count) {
        return Map.of("groupId", GROUP, "created", created, "tableCount", count);
    }

    private static Map<?, ?> map(Object result) {
        return assertInstanceOf(Map.class, result);
    }

    private static List<Object> candidateIds(Map<?, ?> result) {
        return ((List<?>) result.get("list")).stream().map(row -> map(row).get("taskId")).map(Object.class::cast).toList();
    }

    private static int exitCode(Object result) {
        return (int) assertInstanceOf(ExitValue.class, result).getValues()[0];
    }

    public record Scope(String mode, String user, List<String> orgIds) {
        public String id() { return TENANT; }

        public Map<String, Object> dataSource() {
            return Map.of("scope", mode, "userId", user, "organizationIds", orgIds);
        }
    }

    private static final class Fixture {
        final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:multi_group_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger groupReads = new AtomicInteger();
        final SQLModule db;
        final MagicScript candidates;
        final MagicScript save;
        final MagicScript visibleQuery;
        final MagicScript delete;
        final MagicScript relink;
        final MagicScript editQuery;
        CyclicBarrier concurrentReads;

        Fixture() throws IOException {
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("multi-table-group-test"));
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override public boolean match(String connectionUrl) { return connectionUrl.startsWith("jdbc:h2:"); }
            });
            db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override public void preHandle(BoundSql sql, RequestEntity request) {
                    if (sql.getSql().stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
                        reads.incrementAndGet();
                    } else {
                        writes.incrementAndGet();
                    }
                }

                @Override public Object postHandle(BoundSql sql, Object result, RequestEntity request) {
                    String statement = sql.getSql().stripLeading().toLowerCase(Locale.ROOT);
                    if (concurrentReads != null && statement.startsWith("select")
                            && statement.contains("data_access_multi_group_t") && groupReads.incrementAndGet() <= 2) {
                        try {
                            concurrentReads.await(10, TimeUnit.SECONDS);
                        } catch (Exception failure) {
                            throw new IllegalStateException("Both transactions must first observe a missing group", failure);
                        }
                    }
                    return result;
                }
            }));
            jdbc.execute("""
                    create table db_datasource_t(tid varchar primary key, tenant_id varchar, db_name varchar,
                        created_by varchar, org_id varchar, is_del int);
                    create table db_table_t(tid varchar primary key, tenant_id varchar, datasource_id varchar,
                        table_name varchar, table_name_cn varchar, is_del int);
                    create table data_access_agg_task_t(tid varchar primary key, tenant_id varchar, source_table_id varchar,
                        source_db_id varchar, task_name varchar, task_status int, pipeline_id varchar, is_del int);
                    create table data_access_multi_group_t(tid varchar primary key, tenant_id varchar, group_name varchar,
                        created_by varchar, created_time timestamp, updated_time timestamp, is_del int);
                    create table data_access_multi_group_item_t(tid varchar primary key, tenant_id varchar,
                        group_id varchar references data_access_multi_group_t(tid), source_table_id varchar,
                        access_task_id varchar, sort_no int, created_time timestamp, updated_time timestamp,
                        unique(tenant_id, group_id, source_table_id), unique(tenant_id, group_id, sort_no));
                    insert into db_datasource_t values ('owned-db','tenant-a','Owned DB','operator','org-a',0);
                    """);
            candidates = script(CanonicalMagicSources.byId("c8754c53bb644104a178b88fbcebb7cd"));
            save = script(CanonicalMagicSources.byId("c276687fa7cb4a0fada25b972ccda4b1"));
            visibleQuery = script(CanonicalMagicSources.byPath("function/01.通用函数/25.多表编组可见任务查询.ms",
                    "b407fbd38e4c4d048c10eb9e92845d91"));
            delete = script(CanonicalMagicSources.byId("59ce9120d637482a8b63cdc643510727"));
            relink = script(CanonicalMagicSources.byId("9dc102c8fc8845d2a48c161b442385b8"));
            editQuery = script(CanonicalMagicSources.byPath("function/01.通用函数/26.多表编组写权限查询.ms",
                    "19e712f3e2ab4b529d0910896dfc1c39"));
        }

        private static MagicScript script(String source) {
            return MagicScript.create(source.replaceAll(
                    "(?m)^import (?:tenantRuntime|dataScope|tableSort|tableRead|'.+?' as (?:multiTaskVisibleQuery|multiGroupEditQuery))\\R", ""), null);
        }

        List<String> tasks(int count) {
            var tables = new ArrayList<Object[]>();
            var tasks = new ArrayList<Object[]>();
            var ids = IntStream.rangeClosed(1, count).mapToObj(i -> "task-%03d".formatted(i)).toList();
            for (int i = 0; i < count; i++) {
                String id = "table-%03d".formatted(i + 1);
                tables.add(new Object[] {id, id});
                tasks.add(new Object[] {ids.get(i), id});
            }
            jdbc.batchUpdate("insert into db_table_t values (?,'tenant-a','owned-db',?,NULL,0)", tables);
            jdbc.batchUpdate("insert into data_access_agg_task_t values (?,'tenant-a',?,'legacy-physical-db','same name',0,NULL,0)", tasks);
            return ids;
        }

        Object candidates(Scope scope, Map<String, Object> body) {
            return run(candidates, scope, body, false);
        }

        Object save(Scope scope, Map<String, Object> body) {
            return write(save, scope, body);
        }

        Object delete(Scope scope) {
            return write(delete, scope, Map.of("groupId", GROUP, "tenantId", "tenant-b"));
        }

        Object relink(Scope scope, Map<String, Object> body) {
            return write(relink, scope, body);
        }

        private Object write(MagicScript script, Scope scope, Map<String, Object> body) {
            try (var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::isLogin).thenReturn(true);
                login.when(StpUtil::getLoginId).thenReturn(ACTOR);
                return run(script, scope, body, true);
            }
        }

        private Object run(MagicScript script, Scope scope, Map<String, Object> body, boolean transactional) {
            var reader = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> {
                assertThat(TransactionSynchronizationManager.hasResource(source)).isEqualTo(transactional);
                reads.incrementAndGet();
            });
            Function<Object[], Object> query = arguments -> visibleQuery.execute(new MagicScriptContext(Map.of(
                    "tenantId", arguments[0], "scopeMode", arguments[1], "scopeUserId", arguments[2],
                    "scopeOrgIds", arguments[3], "tableRead", reader)));
            Function<Object[], Object> authorizedGroup = arguments -> editQuery.execute(new MagicScriptContext(Map.of(
                    "tenantId", arguments[0], "groupId", arguments[1], "actor", arguments[2],
                    "scopeMode", arguments[3], "scopeOrgIds", arguments[4], "tableRead", reader)));
            try (var tenant = TenantContext.use(TENANT)) {
                return script.execute(new MagicScriptContext(Map.of("db", db, "body", body,
                        "tenantRuntime", scope, "dataScope", scope, "tableRead", reader,
                        "tableSort", new TableSortMagicModule(), "multiTaskVisibleQuery", query,
                        "multiGroupEditQuery", authorizedGroup)));
            }
        }

        List<String> savedIds() {
            return jdbc.queryForList("select access_task_id from data_access_multi_group_item_t order by sort_no", String.class);
        }

        int groupCount() {
            return jdbc.queryForObject("select count(*) from data_access_multi_group_t", Integer.class);
        }

        void resetCounts() {
            reads.set(0);
            writes.set(0);
        }
    }
}
