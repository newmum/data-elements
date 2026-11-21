package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.integration.metadata.TableInventoryProbe;
import com.linewell.dataelement.magic.module.MetadataScanMagicModule;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.audit.infrastructure.magic.AuditNamedTableInterceptor;
import com.linewell.dataelement.platform.audit.infrastructure.magic.AuditTableRegistry;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule.JsonArrayField;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.misc.CompletableFutureWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class CollectionReadMagicTest {
    private static final String API = "api/02.数据盘点/09.表采集/";
    private static final String FUNCTIONS = "function/16.表采集/";
    private static final Timestamp START = Timestamp.valueOf(LocalDateTime.of(2026, 10, 1, 10, 0));
    private static final Timestamp FINISH = Timestamp.valueOf(LocalDateTime.of(2026, 10, 1, 12, 0));
    private static final Timestamp LATER = Timestamp.valueOf(LocalDateTime.of(2026, 10, 2, 12, 0));
    private static final String TENANT = "tenant-a";
    private static final String FOREIGN_TENANT = "tenant-b";

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final DataScopeAuthorizationService scopeService = mock(DataScopeAuthorizationService.class);
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final Map<String, MagicScriptLambdaFunction> functions = new LinkedHashMap<>();
    private final List<String> selects = Collections.synchronizedList(new ArrayList<>());
    private final List<SqlCall> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean measureSql;
    private JdbcTemplate jdbc;

    @BeforeEach
    void configureScripts() throws Exception {
        scope(Scope.ALL);
        bindings.put("tenantRuntime", new TenantMagicModule(null));
        bindings.put("dataScope", new DataScopeMagicModule(scopeService));
        bindings.put("request", new QueryParameters(request));
        function("scope", "01.核对采集数据源范围", "tenantId", "ids");
        function("response", "02.采集接口响应", "action");
        function("job", "03.装配采集任务状态", "row", "sourceId", "previous", "withDeleted");
        function("jobs", "04.定义采集任务读取列", "alias", "withDeleted");
        function("pageargs", "05.读取表清单分页参数");
        function("tablepage", "06.装配采集表清单", "page", "args");
        function("tables", "07.定义采集清单读取列", "alias");
        function("historyargs", "08.读取采集历史分页参数", "defaultSize", "withOffset");
        function("deleted", "09.读取采集任务差异原文", "tenantId", "source", "successfulOnly");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void readsLatestBatchInRequestOrderWithFixedReadsAndRejectsEachOutOfScopeId(String dialect) throws Exception {
        database(dialect);
        var ids = IntStream.range(0, 100).mapToObj(index -> "source-%03d".formatted(index)).toList();
        ids.forEach(id -> source(id, TENANT, "team-a", "operator", 0));
        source("foreign", FOREIGN_TENANT, "team-a", "operator", 0);
        source("wrong-group", TENANT, "team-b", "operator", 0);
        source("wrong-owner", TENANT, "team-a", "someone", 0);
        source("deleted", TENANT, "team-a", "operator", 1);
        job("successful", ids.getFirst(), TENANT, "SUCCEEDED", START, FINISH, "[]");
        job("recent", ids.getFirst(), TENANT, "FAILED", LATER, LATER, "[]");
        job("foreign-job", ids.getFirst(), FOREIGN_TENANT, "SUCCEEDED", LATER, LATER, "[]");

        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            for (int size : List.of(20, 100)) {
                var visible = new ArrayList<>(ids.subList(0, size).reversed());
                bindings.put("body", Map.of("dbIds", visible, "tenantId", FOREIGN_TENANT));
                var items = maps(success("02.批量查询数据源采集状态", "statuses").get("items"));
                assertEquals(visible, items.stream().map(row -> row.get("datasourceId")).toList());
                assertEquals("NOT_COLLECTED", items.getFirst().get("status"));
                assertEquals("recent", items.getLast().get("jobId"));
                assertEquals(FINISH, items.getLast().get("previousSuccessfulAt"));
                assertEquals(3L, ((Number) items.getLast().get("totalCount")).longValue());
                assertFalse(items.getLast().containsKey("deleted"));
                assertEquals(3, selects.size());

                scope(Scope.ORG);
                assertDenied(visible, "wrong-group");
                scope(Scope.OWNER);
                assertDenied(visible, "wrong-owner");
                scope(Scope.ALL);
                assertDenied(visible, "foreign");
                assertDenied(visible, "deleted");
            }
            bindings.put("body", Map.of("dbIds", List.of(ids.getFirst(), " " + ids.getFirst() + " ")));
            assertEquals(1, maps(success("02.批量查询数据源采集状态", "statuses").get("items")).size());
            bindings.put("body", Map.of("dbIds", List.of(42)));
            assertEquals("dbId 格式不正确", error("02.批量查询数据源采集状态", "statuses").getMsg());
            assertEquals(0, selects.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void readsAnAuthorizedSpecificJobAndKeepsParameterAndDetailSemantics(String dialect) throws Exception {
        database(dialect);
        source("allowed", TENANT, "team-a", "operator", 0);
        source("unowned", TENANT, "team-a", "someone", 0);
        job("successful", "allowed", TENANT, "SUCCEEDED", START, FINISH, "[{\"tableName\":\"old\"}]");
        job("recent", "allowed", TENANT, "FAILED", LATER, LATER, "malformed");
        job("foreign", "allowed", FOREIGN_TENANT, "SUCCEEDED", LATER, LATER, "[]");
        job("unowned-job", "unowned", TENANT, "SUCCEEDED", LATER, LATER, "[]");

        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            assertEquals(20001, error("01.查询数据源采集状态", "status").getCode());
            request.setParameter("dbId[]", "allowed");
            request.setParameter("includeDeletedDetails", "wrong");
            assertEquals(20002, error("01.查询数据源采集状态", "status").getCode());
            request.setParameter("includeDeletedDetails", "on");
            request.setParameter("jobId", "successful");
            var status = success("01.查询数据源采集状态", "status");
            assertEquals("successful", status.get("jobId"));
            assertEquals(3, selects.size());
            assertEquals(List.of(Map.of("tableName", "old")), status.get("deleted"));
            request.setParameter("jobId", "recent");
            assertEquals(List.of(), success("01.查询数据源采集状态", "status").get("deleted"));
            request.setParameter("jobId", "foreign");
            assertEquals("NOT_COLLECTED", success("01.查询数据源采集状态", "status").get("status"));
            assertEquals(2, selects.size());
            scope(Scope.OWNER);
            request.setParameter("dbId", "unowned");
            request.removeParameter("jobId");
            assertEquals("NOT_COLLECTED", success("01.查询数据源采集状态", "status").get("status"));
            assertEquals(1, selects.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void filtersAndPagesSavedAndRecentAdditionsWithinTheSameSource(String dialect) throws Exception {
        database(dialect);
        source("allowed", TENANT, "team-a", "operator", 0);
        source("foreign", FOREIGN_TENANT, "team-a", "operator", 0);
        job("successful", "allowed", TENANT, "SUCCEEDED", START, FINISH, "[]");
        job("recent", "allowed", TENANT, "FAILED", LATER, LATER, "[]");
        job("foreign", "allowed", FOREIGN_TENANT, "SUCCEEDED", LATER, LATER, "[]");
        for (int index = 0; index < 120; index++) {
            String name = "table_%03d".formatted(index);
            table("id-" + index, name, "说明" + index, TENANT, "allowed", index, 0, index == 0 ? START : FINISH, 0);
        }
        table("preferred", "TABLE_000", "权威", TENANT, "allowed", 999, 1, START, 0);
        table("outside", "outside", "范围外日期", TENANT, "allowed", 1, 0, LATER, 0);
        table("deleted", "deleted", "已删除", TENANT, "allowed", 1, 0, START, 1);
        table("foreign-row", "foreign", "外租户同数据源", FOREIGN_TENANT, "allowed", 1, 0, START, 0);
        table("other-source-row", "other", "同租户其他数据源", TENANT, "foreign", 1, 0, START, 0);

        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            request.setParameter("dbId", "allowed");
            request.setParameter("sortField", "fieldCount");
            request.setParameter("sortDir", "desc");
            for (int size : List.of(20, 100)) {
                request.setParameter("pageSize", String.valueOf(size));
                var saved = success("03.查询已保存采集表", "saved");
                assertEquals(121L, saved.get("total"));
                assertEquals(size, maps(saved.get("rows")).size());
                assertEquals(saved.get("rows"), saved.get("list"));
                assertEquals("preferred", maps(saved.get("rows")).getFirst().get("tid"));
                assertEquals(3, selects.size());
                var additions = success("04.查询最近新增采集表", "additions");
                assertEquals(121L, additions.get("total"));
                assertEquals(size, maps(additions.get("rows")).size());
                assertEquals("preferred", maps(additions.get("rows")).getFirst().get("tid"));
                assertEquals(0, maps(additions.get("rows")).getFirst().get("recordCount"));
                assertEquals(4, selects.size());
            }
            request.setParameter("keyword", "说明29");
            assertEquals(1L, success("03.查询已保存采集表", "saved").get("total"));
            request.setParameter("keyword", "说明0");
            assertEquals(0L, success("03.查询已保存采集表", "saved").get("total"));
            request.setParameter("sortField", "field_count;--");
            assertEquals("不支持的排序字段：field_count;--", error("04.查询最近新增采集表", "additions").getMsg());
            assertEquals(0, selects.size());
            request.setParameter("sortField", "tableName");
            request.setParameter("pageSize", "NaN");
            assertEquals(20002, error("03.查询已保存采集表", "saved").getCode());
            request.setParameter("pageSize", "20");
            request.setParameter("dbId", "foreign");
            assertEquals("数据源不存在或无访问权限", error("04.查询最近新增采集表", "additions").getMsg());
            assertEquals(1, selects.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void reviewPreservesLargeExactSelectionWithoutPerNameSql(String dialect) throws Exception {
        database(dialect);
        source("validation", TENANT, "team-a", "operator", 0);
        jdbc.batchUpdate("insert into db_table_t values (?, ?, 'validation', 0, ?, null, '', 'TABLE', ?, 40, 0, null, null)",
                IntStream.range(0, 30_000).mapToObj(index -> new Object[]{"id-" + index, TENANT,
                        "table_%05d".formatted(index), index}).toList());
        table("chosen", "table_00001", "权威", TENANT, "validation", 30_001, 1, START, 0);
        table("other-tenant", "foreign", "跨租户同来源", FOREIGN_TENANT, "validation", 999, 1, START, 0);
        table("other-source", "other", "同租户其他来源", TENANT, "other", 999, 1, START, 0);
        table("deleted", "deleted", "已删除表", TENANT, "validation", 999, 1, START, 1);
        var selected = IntStream.range(0, 30_000).filter(index -> index % 2 == 0)
                .mapToObj(index -> "table_%05d".formatted(index)).toList();
        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            for (int size : List.of(20, 100)) {
                bindings.put("body", Map.of("dbId", "validation", "pageSize", size, "allTables", true,
                        "sortField", "fieldCount", "sortDir", "desc"));
                var full = success("07.查询登记审查顺序", "order");
                var allKeys = assertInstanceOf(List.class, full.get("keys"));
                assertEquals(30_000L, ((Number) full.get("total")).longValue());
                assertEquals(size, allKeys.size());
                assertEquals("table_00001", allKeys.getFirst());
                assertEquals(3, selects.size());

                bindings.put("body", Map.of("dbId", "validation", "pageSize", size, "names", new ArrayList<>(selected),
                        "sortField", "fieldCount", "sortDir", "desc"));
                var filtered = success("07.查询登记审查顺序", "order");
                assertEquals(15_000, filtered.get("total"));
                assertEquals(IntStream.range(0, size).mapToObj(index -> "table_%05d".formatted(29998 - index * 2)).toList(),
                        filtered.get("keys"));
                assertEquals(2, selects.size());
            }
            bindings.put("body", Map.of("dbId", "validation", "pageNo", 1501, "pageSize", 20, "allTables", true));
            assertEquals(List.of(), success("07.查询登记审查顺序", "order").get("keys"));
            assertEquals(2, selects.size());
            for (String invalid : List.of("foreign", "other", "deleted", "missing")) {
                bindings.put("body", Map.of("dbId", "validation", "names", List.of("table_00000", invalid)));
                assertEquals("审查范围包含不存在或无权访问的数据表，请刷新清单",
                        error("07.查询登记审查顺序", "order").getMsg());
                assertEquals(2, selects.size());
            }
            bindings.put("body", Map.of("dbId", "validation", "names", List.of("table_00000", "table_00000")));
            assertEquals("数据表名称为空或重复", error("07.查询登记审查顺序", "order").getMsg());
            bindings.put("body", Map.of("dbId", "validation", "allTables", true, "names", List.of()));
            assertEquals("完整范围与筛选范围不能同时指定", error("07.查询登记审查顺序", "order").getMsg());
            bindings.put("body", Map.of("dbId", "validation", "allTables", true, "pageSize", 101));
            assertEquals("分页参数无效，每页最多100张表", error("07.查询登记审查顺序", "order").getMsg());
            bindings.put("body", Map.of("dbId", "validation", "allTables", true, "sortField", "field_count;--"));
            assertEquals("不支持的排序字段：field_count;--", error("07.查询登记审查顺序", "order").getMsg());
            scope(Scope.ORG);
            source("foreign-org", TENANT, "team-b", "operator", 0);
            bindings.put("body", Map.of("dbId", "foreign-org", "allTables", true));
            assertEquals("数据源不存在或无访问权限", error("07.查询登记审查顺序", "order").getMsg());
            assertEquals(1, selects.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void historyUsesTheCorrectJobAndKeepsBothPageWindowsBounded(String dialect) throws Exception {
        database(dialect);
        installHistoryRows();
        source("allowed", TENANT, "team-a", "operator", 0);
        source("unowned", TENANT, "team-b", "another-owner", 0);
        var json = new ObjectMapper();
        var history = IntStream.range(0, 130).mapToObj(index -> Map.of("tid", "missing-" + index,
                "tableName", "removed_" + index, "tableNameCn", "历史" + index,
                "tableComment", "说明" + index, "tableType", "VIEW", "fieldCount", index,
                "recordCount", index * 2)).toList();
        job("ok", "allowed", TENANT, "SUCCEEDED", START, FINISH,
                "[{\"tableName\":\" TABLE_004 \"},{\"tableName\":null}]");
        job("failed", "allowed", TENANT, "FAILED", LATER, LATER, json.writeValueAsString(history));
        job("foreign", "allowed", FOREIGN_TENANT, "FAILED", LATER, LATER, "[]");
        jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,is_del,table_name,table_comment,"
                        + "table_type,field_count,record_count,annotated,business_type) values (?, ?, 'allowed', 0, ?, ?, 'TABLE', ?, 40, 0, ?)",
                IntStream.range(0, 122).mapToObj(index -> new Object[]{"table-" + index, TENANT,
                        "table_%03d".formatted(index), "说明" + index, index, "业务表"}).toList());
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,is_del,table_name,annotated) "
                + "values ('marked', ?, 'allowed', 0, 'already', 1),"
                + " ('foreign-table', ?, 'allowed', 0, 'secret', 0), ('other-table', ?, 'unowned', 0, 'other', 0),"
                + " ('deleted-table', ?, 'allowed', 1, 'deleted', 0)", TENANT, FOREIGN_TENANT, TENANT, TENANT);

        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            request.setParameter("dbId[]", "allowed");
            request.setParameter("sortField", "fieldCount");
            request.setParameter("sortDir", "desc");
            for (int size : List.of(20, 100)) {
                request.setParameter("pageSize", String.valueOf(size));
                var changes = success("05.查询采集缺失表", "changes");
                assertEquals(130L, changes.get("total"));
                var diff = maps(changes.get("rows"));
                assertEquals(size, diff.size());
                assertEquals("removed_129", diff.getFirst().get("tableName"));
                assertEquals(258L, diff.getFirst().get("recordCount"));
                assertEquals(4, selects.size());

                request.setParameter("offset", "7");
                var snapshot = success("06.查询待标注表快照", "snapshot");
                assertEquals(121L, snapshot.get("total"));
                assertEquals(122L, snapshot.get("physicalTotal"));
                assertEquals(7L, ((Number) snapshot.get("offset")).longValue());
                var rows = maps(snapshot.get("rows"));
                assertEquals(size, rows.size());
                assertEquals("table_114", rows.getFirst().get("tableName"));
                assertEquals("说明114", rows.getFirst().get("physicalTableComment"));
                assertEquals(5, selects.size());
            }
            request.setParameter("offset", "0");
            request.setParameter("sortDir", "asc");
            request.setParameter("keyword", "table_004");
            assertEquals(0L, success("06.查询待标注表快照", "snapshot").get("total"));
            request.setParameter("keyword", "历史129");
            assertEquals(1L, success("05.查询采集缺失表", "changes").get("total"));
            request.setParameter("offset", "bad");
            assertEquals(20002, error("06.查询待标注表快照", "snapshot").getCode());
            assertEquals(0, selects.size());
            request.removeParameter("offset");
            request.setParameter("dbId", "unowned");
            scope(Scope.ORG);
            assertEquals("数据源不存在或无访问权限", error("06.查询待标注表快照", "snapshot").getMsg());
            assertEquals(1, selects.size());
        }
    }

    @Test
    void collectionRunsAfterCommitReusesAnActiveJobAndRollsBackTheSnapshotBeforeRetry() throws Exception {
        database("MySQL");
        installCollectionWrites();
        var db = (SQLModule) bindings.get("db");
        var audit = new AuditNamedTableInterceptor(new AuditTableRegistry(jdbc.getDataSource()));
        db.setNamedTableInterceptors(List.of(audit));
        source("allowed", TENANT, "team-a", "operator", 0);
        source("restricted", TENANT, "team-b", "someone", 0);
        table("already", "old", "原治理表", TENANT, "allowed", 1, 1, START, 0);
        job("abandoned", "allowed", TENANT, "RUNNING", START, START, "[]");

        var probe = mock(TableInventoryProbe.class);
        var states = new ConcurrentHashMap<String, AtomicLong>();
        var redis = mock(RedissonClient.class);
        when(redis.getLock(anyString())).thenAnswer(call -> mockedLease(states.computeIfAbsent(call.getArgument(0), key -> new AtomicLong())));
        var module = new MetadataScanMagicModule(probe, redis);
        bindings.put("metadataScan", module);
        var refresh = spy(new MetadataAssetPersistenceModule(jdbc.getDataSource()));
        bindings.put("metadataAsset", refresh);
        var shouldFail = new AtomicBoolean(true);
        doAnswer(call -> {
            if (shouldFail.get()) throw new IllegalStateException("validation-snapshot-write-failed");
            return call.callRealMethod();
        }).when(refresh).refreshRegistrationStatus("allowed", false);
        var scans = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(probe.scan("allowed")).thenAnswer(call -> {
            assertEquals(TENANT, TenantContext.requireTenantId());
            int scan = scans.incrementAndGet();
            assertEquals(1, jdbc.queryForObject("select count(*) from metadata_table_collection_job_t where status='RUNNING'",
                    Integer.class));
            if (scan == 1) {
                entered.countDown();
                assertTrue(release.await(12, TimeUnit.SECONDS));
            }
            return new TableInventoryProbe.Inventory(List.of(physicalTable("new_table")), "");
        });

        var priorContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        try {
            scope(Scope.OWNER);
            var preview = assertInstanceOf(Map.class, collectionRequest(Map.of("dbId", "allowed", "dryRun", true)));
            assertEquals(true, assertInstanceOf(Map.class, preview.get("data")).get("dryRun"));
            assertEquals(0, scans.get());
            var denied = assertInstanceOf(CommonResponse.class, collectionRequest(Map.of("dbId", "restricted")));
            assertEquals("数据源不存在或无访问权限", denied.getMsg());

            var data = Map.<String, Object>of("dbId", "allowed", "mode", "REEXPLORE");
            var first = CompletableFuture.supplyAsync(() -> acceptedId(data));
            var second = CompletableFuture.supplyAsync(() -> acceptedId(data));
            String jobId = first.get(12, TimeUnit.SECONDS);
            assertEquals(jobId, second.get(12, TimeUnit.SECONDS));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(1, scans.get());
            assertEquals("FAILED", jobStatus("abandoned"));
            release.countDown();
            await(() -> "FAILED".equals(jobStatus(jobId)));
            assertTrue(jobError(jobId).contains("validation-snapshot-write-failed"), jobError(jobId));
            assertEquals(1, jdbc.queryForObject("select count(*) from db_table_t", Integer.class));
            assertEquals(0, jdbc.queryForObject("select table_num from db_datasource_t where tid='allowed'", Integer.class));

            shouldFail.set(false);
            String retry = acceptedId(data);
            await(() -> !"RUNNING".equals(jobStatus(retry)));
            assertEquals("SUCCEEDED", jobStatus(retry), jobError(retry));
            assertEquals(2, scans.get());
            assertEquals(1, jdbc.queryForObject("select count(*) from db_table_t where table_name='new_table'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select table_num from db_datasource_t where tid='allowed'", Integer.class));
            assertEquals(1L, jdbc.queryForObject("select deleted_count from metadata_table_collection_job_t where tid=?", Long.class, retry));

            try (var tenant = TenantContext.use(TENANT)) {
                var unused = new AtomicBoolean();
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(transaction -> {
                    module.afterCommit("9999999", () -> unused.set(true), () -> unused.set(true));
                    assertTrue(module.active("9999999"));
                    transaction.setRollbackOnly();
                });
                assertFalse(module.active("9999999"));
                assertFalse(unused.get());
            }
        } finally {
            release.countDown();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(module, "stop");
            SaManager.setSaTokenContext(priorContext);
        }
    }

    @Test
    void renameRollsBackBothSnapshotsWhenHistoryCannotUpdateAndKeepsTheRegisteredIdentity() throws Exception {
        database("MySQL");
        installCollectionWrites();
        source("allowed", TENANT, "team-a", "operator", 0);
        source("restricted", TENANT, "team-b", "someone", 0);
        table("target", "old_name", "已标注", TENANT, "allowed", 9, 1, START, 0);
        table("duplicate", "EXT_NAME", "待标注", TENANT, "allowed", 3, 0, START, 0);
        table("nullable", "ext_name", "历史空标注", TENANT, "allowed", 4, 0, START, 0);
        jdbc.update("update db_table_t set annotated=null where tid='nullable'");
        table("other", "old_name", "同租户其他来源", TENANT, "restricted", 5, 1, START, 0);
        table("foreign", "old_name", "其他租户", FOREIGN_TENANT, "allowed", 5, 1, START, 0);
        job("success", "allowed", TENANT, "SUCCEEDED", START, FINISH, "[{\"tableName\":\"old_name\"}]");
        jdbc.execute("alter table metadata_table_collection_job_t add constraint validation_history_write check (deleted_count >= 1)");
        var body = Map.<String, Object>of("dbId", "allowed", "tableId", "target", "tableName", "ext_name");
        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            scope(Scope.OWNER);
            bindings.put("body", Map.of("dbId", "allowed", "tableId", "other", "tableName", "ext_name"));
            assertEquals("登记表不存在或无访问权限", error("09.关联缺失登记表新名称", "rename").getMsg());
            bindings.put("body", Map.of("dbId", "allowed", "tableId", "foreign", "tableName", "ext_name"));
            assertEquals("登记表不存在或无访问权限", error("09.关联缺失登记表新名称", "rename").getMsg());
            var preview = new LinkedHashMap<>(body);
            preview.put("dryRun", true);
            bindings.put("body", preview);
            assertEquals(2, success("09.关联缺失登记表新名称", "rename").get("removedSnapshotCount"));

            bindings.put("body", body);
            assertEquals(500, error("09.关联缺失登记表新名称", "rename").getCode());
            assertEquals("old_name", jdbc.queryForObject("select table_name from db_table_t where tid='target'", String.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from db_table_t where tid in ('duplicate','nullable') and is_del=0", Integer.class));

            jdbc.execute("alter table metadata_table_collection_job_t drop constraint validation_history_write");
            var one = CompletableFuture.supplyAsync(() -> collectionRequest(body, "09.关联缺失登记表新名称", "rename"));
            var two = CompletableFuture.supplyAsync(() -> collectionRequest(body, "09.关联缺失登记表新名称", "rename"));
            Object first = one.get(12, TimeUnit.SECONDS);
            Object second = two.get(12, TimeUnit.SECONDS);
            var applied = assertInstanceOf(Map.class, assertInstanceOf(Map.class, first instanceof Map ? first : second).get("data"));
            var conflicted = assertInstanceOf(CommonResponse.class, first instanceof Map ? second : first);
            assertEquals("该表不在当前变更列表中，请重新探查后再修改", conflicted.getMsg());
            assertEquals("target", applied.get("tableId"));
            assertEquals(2, applied.get("removedSnapshotCount"));
            assertEquals("ext_name", jdbc.queryForObject("select table_name from db_table_t where tid='target' and annotated=1", String.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from db_table_t where tid in ('duplicate','nullable') and is_del=1", Integer.class));
            assertEquals("[]", jdbc.queryForObject("select deleted_detail from metadata_table_collection_job_t where tid='success'", String.class));
            assertEquals("该表不在当前变更列表中，请重新探查后再修改", error("09.关联缺失登记表新名称", "rename").getMsg());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void writesTwentyOrOneHundredTablesWithOneBatchAndNoPerTableReads(int size) throws Exception {
        database("MySQL");
        installCollectionWrites();
        source("allowed", TENANT, "team-a", "operator", 0);
        var probe = mock(TableInventoryProbe.class);
        when(probe.scan("allowed")).thenReturn(new TableInventoryProbe.Inventory(IntStream.range(0, size)
                .mapToObj(index -> physicalTable("validation_table_" + index)).toList(), ""));
        var locks = new ConcurrentHashMap<String, AtomicLong>();
        var redis = mock(RedissonClient.class);
        when(redis.getLock(anyString())).thenAnswer(call -> mockedLease(locks.computeIfAbsent(call.getArgument(0), key -> new AtomicLong())));
        var module = new MetadataScanMagicModule(probe, redis);
        bindings.put("metadataScan", module);
        bindings.put("metadataAsset", new MetadataAssetPersistenceModule(jdbc.getDataSource()));
        try {
            measureSql = true;
            String id = acceptedId(Map.of("dbId", "allowed"));
            await(() -> !"RUNNING".equals(jobStatus(id)));
            measureSql = false;
            assertEquals("SUCCEEDED", jobStatus(id), jobError(id));
            assertEquals(size, jdbc.queryForObject("select count(*) from db_table_t", Integer.class));
            var statements = List.copyOf(calls);
            long reads = statements.stream().filter(call -> call.sql().startsWith("SELECT")).count();
            long writes = statements.stream().filter(call -> call.sql().startsWith("INSERT") || call.sql().startsWith("UPDATE")).count();
            long snapshotBatches = statements.stream().filter(call -> call.method().equals("executeBatch")
                    && call.sql().contains("DB_TABLE_T")).count();
            System.out.printf("Collection size=%d, requests=1, business selects=%d, JDBC writes=%d, snapshot batches=%d%n",
                    size, reads, writes, snapshotBatches);
            assertEquals(8, reads);
            assertEquals(6, writes);
            assertEquals(1, snapshotBatches);
        } finally {
            measureSql = false;
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(module, "stop");
        }
    }

    private void installCollectionWrites() {
        jdbc.execute("alter table metadata_table_collection_job_t add updated_time timestamp");
        jdbc.execute("alter table db_datasource_t add updated_time timestamp");
        jdbc.execute("alter table db_datasource_t add table_num int default 0");
        jdbc.execute("alter table db_datasource_t add asset_status int default 0");
        jdbc.execute("alter table db_datasource_t add flow_status int default 0");
        jdbc.execute("alter table db_table_t add table_name_en varchar(255)");
        jdbc.execute("alter table db_table_t add business_type varchar(64)");
        jdbc.execute("alter table db_table_t add reg_time timestamp");
        jdbc.execute("alter table db_table_t add asset_status int default 0");
        jdbc.execute("alter table db_table_t add flow_status int default 0");
        var source = (DriverManagerDataSource) jdbc.getDataSource();
        bindings.put("db", DatasourceCompletionMagicTest.magicDb(source));
        bindings.put("tableWrite", new StructuredTableWriteMagicModule(jdbc));
        when(scopeService.canReadDataSource(anyMap())).thenCallRealMethod();
        when(scopeService.canRead(anyMap(), any())).thenCallRealMethod();
    }

    private RLock mockedLease(AtomicLong owner) {
        var lease = mock(RLock.class);
        when(lease.isLocked()).thenAnswer(call -> owner.get() != 0);
        when(lease.isHeldByThread(anyLong())).thenAnswer(call -> owner.get() == (long) call.getArgument(0));
        when(lease.tryLockAsync(eq(0L), eq(-1L), eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenAnswer(call -> new CompletableFutureWrapper<>(owner.compareAndSet(0, call.getArgument(3))));
        when(lease.unlockAsync(anyLong())).thenAnswer(call -> {
            assertTrue(owner.compareAndSet(call.getArgument(0), 0));
            return new CompletableFutureWrapper<Void>((Void) null);
        });
        return lease;
    }

    private String acceptedId(Map<String, Object> body) {
        var response = assertInstanceOf(Map.class, collectionRequest(body));
        var result = assertInstanceOf(Map.class, response.get("data"));
        assertEquals(0, response.get("code"));
        return (String) result.get("jobId");
    }

    private Object collectionRequest(Map<String, Object> body) {
        return collectionRequest(body, "08.启动数据源表采集", "start");
    }

    private Object collectionRequest(Map<String, Object> body, String title, String id) {
        SaTokenContextJakartaServletUtil.setContext(new MockHttpServletRequest(), new MockHttpServletResponse());
        try (var tenant = TenantContext.use(TENANT); var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            var variables = new LinkedHashMap<>(bindings);
            variables.put("body", body);
            String script = CanonicalMagicSources.byPath(API + title + ".ms", "collect_" + id + "_v1");
            return assertInstanceOf(ResponseEntity.class, run(script, variables)).getBody();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            SaTokenContextForThreadLocalStaff.clearModelBox();
        }
    }

    private String jobStatus(String id) {
        return jdbc.queryForObject("/* validation poll */ select status from metadata_table_collection_job_t where tid=?", String.class, id);
    }

    private String jobError(String id) {
        return jdbc.queryForObject("select error_message from metadata_table_collection_job_t where tid=?", String.class, id);
    }

    private void await(BooleanSupplier finished) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!finished.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(finished.getAsBoolean(), "collection job did not complete");
    }

    private TableInfo physicalTable(String name) {
        var table = new TableInfo();
        table.setTableName(name);
        table.setTableType("TABLE");
        return table;
    }

    private void installHistoryRows() {
        for (String column : List.of("table_name_en", "business_type", "business_type_reason", "storage_size", "total_size_formatted")) {
            jdbc.execute("alter table db_table_t add column " + column + " varchar(255)");
        }
        jdbc.execute("alter table db_table_t add column total_size_bytes bigint");
        jdbc.execute("""
                create table validation_history_rows (tenant_id varchar(64), tid varchar(64), table_name varchar(255),
                  table_name_cn varchar(1000), table_comment varchar(1000), table_type varchar(30),
                  field_count bigint, record_count bigint)
                """);
        // H2 executes the actual pagination and tenant predicates. The bound JSON source itself is
        // verified separately with native JSON_TABLE on a local MySQL binding.
        final var json = new ObjectMapper();
        bindings.put("tableRead", new StructuredTableReadMagicModule(jdbc) {
            @Override
            public Query fromJsonArray(String document, String alias, String tenantId, List<JsonArrayField> fields) {
                try {
                    var data = json.readTree(document);
                    jdbc.update("delete from validation_history_rows");
                    var entries = new ArrayList<Object[]>();
                    for (var row : data) {
                        entries.add(new Object[]{tenantId, row.path("tid").asText(null), row.path("tableName").asText(null),
                                row.path("tableNameCn").asText(null), row.path("tableComment").asText(null),
                                row.path("tableType").asText(null), row.path("fieldCount").asLong(0),
                                row.path("recordCount").asLong(0)});
                    }
                    jdbc.batchUpdate("insert into validation_history_rows values (?,?,?,?,?,?,?,?)", entries);
                    return from("validation_history_rows", alias, tenantId);
                } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                    throw new IllegalArgumentException(error);
                }
            }
        });
    }

    private void database(String dialect) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:collection_" + UUID.randomUUID()
                + ";MODE=" + dialect + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "") {
            @Override
            public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("getMetaData")) {
                                var metadata = connection.getMetaData();
                                String url = dialect.equals("MySQL") ? "jdbc:mysql://validation" : "jdbc:postgresql://validation";
                                return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                                        new Class<?>[]{DatabaseMetaData.class}, (ignored, lookup, values) ->
                                                lookup.getName().equals("getURL") ? url : invoke(metadata, lookup, values));
                            }
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                    && sql.stripLeading().startsWith("SELECT")) selects.add(sql);
                            Object result = invoke(connection, method, args);
                            if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                                String sql = ((String) args[0]).stripLeading().toUpperCase(java.util.Locale.ROOT);
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (ignored, action, values) -> {
                                            if (action.getName().equals("setFetchSize") && (int) values[0] == Integer.MIN_VALUE) {
                                                statement.setFetchSize(128);
                                                return null;
                                            }
                                            if (measureSql && action.getName().startsWith("execute")) {
                                                calls.add(new SqlCall(sql, action.getName()));
                                            }
                                            return invoke(statement, action, values);
                                        });
                            }
                            return result;
                        });
            }
        };
        jdbc = new JdbcTemplate(source);
        bindings.put("tableRead", new StructuredTableReadMagicModule(jdbc));
        jdbc.execute("""
                create table db_datasource_t (
                    tid varchar(64), tenant_id varchar(64), is_del int, org_id varchar(64), created_by varchar(64))
                """);
        jdbc.execute("""
                create table db_table_t (
                    tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int,
                    table_name varchar(255), table_name_cn varchar(255), table_comment varchar(255),
                    table_type varchar(30), field_count int, record_count int, annotated int,
                    created_time timestamp, updated_time timestamp)
                """);
        jdbc.execute("""
                create table metadata_table_collection_job_t (
                    tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int,
                    status varchar(30), phase varchar(30), collection_mode varchar(30),
                    scanned_count int, persisted_count int, total_count int, added_count int,
                    unchanged_count int, deleted_count int, error_message varchar(255), deleted_detail varchar(40000),
                    created_time timestamp, started_time timestamp, finished_time timestamp)
                """);
    }

    private void scope(Scope scope) {
        when(scopeService.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE))
                .thenReturn(new ScopeDecision(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE,
                        scope, "operator", List.of("team-a"), List.of(), true));
    }

    private void source(String id, String tenant, String organization, String owner, int deleted) {
        jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by) values (?,?,?,?,?)",
                id, tenant, deleted, organization, owner);
    }

    private void job(String id, String source, String tenant, String status, Timestamp started, Timestamp finished,
                     String details) {
        jdbc.update("""
                insert into metadata_table_collection_job_t
                    (tid,tenant_id,datasource_id,is_del,status,phase,collection_mode,scanned_count,persisted_count,
                     total_count,added_count,unchanged_count,deleted_count,error_message,deleted_detail,created_time,started_time,finished_time)
                    values (?,?,?,0,?,?,'REFRESH',3,1,3,1,2,1,'',?,?,?,?)
                """, id, tenant, source, status, status.equals("FAILED") ? "FAILED" : "COMPLETED",
                details, started, started, finished);
    }

    private void table(String id, String name, String comment, String tenant, String source, int fields, int primary,
                       Timestamp time, int deleted) {
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,is_del,table_name,table_name_cn,table_comment,"
                        + "table_type,field_count,record_count,annotated,created_time,updated_time) values (?,?,?,?,?,null,?,'TABLE',?,40,?,?,?)",
                id, tenant, source, deleted, name, comment, fields, primary, time, time);
    }

    private void assertDenied(List<String> visible, String forbidden) throws Exception {
        var requested = new ArrayList<>(visible);
        requested.set(visible.size() - 1, forbidden);
        bindings.put("body", Map.of("dbIds", requested));
        assertEquals("数据源不存在或无访问权限", error("02.批量查询数据源采集状态", "statuses").getMsg());
        assertEquals(1, selects.size());
    }

    private void function(String slug, String title, String... parameters) throws Exception {
        String script = CanonicalMagicSources.byPath(FUNCTIONS + title + ".ms", "collect_fn_" + slug + "_v1");
        functions.put(slug, (ignored, values) -> {
            var input = new LinkedHashMap<>(bindings);
            for (int index = 0; index < parameters.length; index++) input.put(parameters[index], values[index]);
            return run(script, input);
        });
    }

    private Object run(String script, Map<String, Object> input) {
        var imports = java.util.regex.Pattern.compile("(?m)^import '@/collect/([a-z]+)' as (\\w+)\\R").matcher(script);
        while (imports.find()) input.put(imports.group(2), functions.get(imports.group(1)));
        String fixture = imports.replaceAll("")
                .replaceAll("(?m)^import (?:dataScope|tableRead|tableWrite|request|tenantRuntime|metadataScan|metadataAsset)\\R", "");
        return MagicScript.create(fixture, null).execute(new MagicScriptContext(input));
    }

    private Object entry(String title, String id) throws Exception {
        selects.clear();
        var response = assertInstanceOf(ResponseEntity.class,
                run(CanonicalMagicSources.byPath(API + title + ".ms", "collect_" + id + "_v1"),
                        new LinkedHashMap<>(bindings)));
        assertEquals(200, response.getStatusCode().value());
        return response.getBody();
    }

    private CommonResponse<?> error(String title, String id) throws Exception {
        return assertInstanceOf(CommonResponse.class, entry(title, id));
    }

    private Map<?, ?> success(String title, String id) throws Exception {
        var envelope = assertInstanceOf(Map.class, entry(title, id));
        assertEquals(0, envelope.get("code"));
        assertEquals("success", envelope.get("msg"));
        return assertInstanceOf(Map.class, envelope.get("data"));
    }

    private static List<Map<?, ?>> maps(Object value) {
        List<?> rows = assertInstanceOf(List.class, value);
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object row : rows) result.add(assertInstanceOf(Map.class, row));
        return result;
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public record QueryParameters(MockHttpServletRequest request) {
        public MockHttpServletRequest get() {
            return request;
        }

        public List<String> getValues(String name) {
            String[] values = request.getParameterValues(name);
            return values == null ? null : List.of(values);
        }
    }

    private record SqlCall(String sql, String method) { }
}
