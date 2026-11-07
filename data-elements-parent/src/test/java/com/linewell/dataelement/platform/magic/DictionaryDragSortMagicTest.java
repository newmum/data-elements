package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class DictionaryDragSortMagicTest {
    private static final String RESOURCE_ID = "fa050435331a44da84dafa64d74d39c5";

    @Test
    void siblingReorderUsesFourReadsAndOneSetWriteAtTwentyAndOneHundredRows() throws Exception {
        for (int size : List.of(20, 100)) {
            var fixture = rootSiblings(size);
            assertEquals(true, fixture.drag("n" + size, "n1", "before"));
            assertEquals(4, fixture.totalReads());
            assertEquals(1, fixture.jdbc.writes);
            assertEquals(1, fixture.row("n" + size).get("sort_no"));
            assertEquals(2, fixture.row("n1").get("sort_no"));
            assertEquals(size, fixture.row("n" + (size - 1)).get("sort_no"));
            assertEquals(2, fixture.row("n" + size).get("level_no"));
            assertEquals("0", fixture.row("n" + size).get("parent_id"));
        }
    }

    @Test
    void moveIntoDeepParentHasBoundedReadsAndOneWriteAndPreservesOtherBranches() throws Exception {
        int reads = 0;
        for (int depth : List.of(20, 100)) {
            var fixture = new Fixture();
            String parent = "0";
            String path = "0";
            for (int i = 1; i <= depth; i++) {
                String id = "p" + i;
                path += "." + id;
                fixture.node(id, "tenant-a", parent, 1, i + 1, path, 0);
                parent = id;
            }
            fixture.node("moving_root", "tenant-a", "0", 2, 2, "0.moving_root", 0);
            fixture.node("moving_child", "tenant-a", "moving_root", 1, 3, "0.moving_root.moving_child", 0);
            fixture.node("different", "tenant-a", "0", 3, 4, "0.movingXroot.different", 0);
            fixture.node("foreign", "tenant-b", "moving_root", 1, 3, "0.moving_root.foreign", 0);
            fixture.node("deleted", "tenant-a", "moving_root", 1, 3, "0.moving_root.deleted", 1);

            assertEquals(true, fixture.drag("moving_root", parent, "inner"));
            if (depth == 20) reads = fixture.totalReads();
            assertEquals(reads, fixture.totalReads());
            assertEquals(6, reads);
            assertEquals(1, fixture.jdbc.writes);
            assertEquals(parent, fixture.row("moving_root").get("parent_id"));
            assertEquals(path + ".moving_root", fixture.row("moving_root").get("tree_path"));
            assertEquals(path + ".moving_root.moving_child", fixture.row("moving_child").get("tree_path"));
            assertEquals(depth + 3, fixture.row("moving_child").get("level_no"));
            assertEquals("0.movingXroot.different", fixture.row("different").get("tree_path"));
            assertEquals("0.moving_root.foreign", fixture.row("foreign").get("tree_path"));
            assertEquals("0.moving_root.deleted", fixture.row("deleted").get("tree_path"));
        }
    }

    @Test
    void beforeAndAfterUseVerifiedTargetAndRejectForeignMissingAndCycle() throws Exception {
        var fixture = new Fixture();
        fixture.node("parent", "tenant-a", "0", 1, 2, "0.parent", 0);
        fixture.node("child", "tenant-a", "parent", 1, 3, "0.parent.child", 0);
        fixture.node("grandchild", "tenant-a", "child", 1, 4, "0.parent.child.grandchild", 0);
        fixture.node("foreign", "tenant-b", "0", 1, 2, "0.foreign", 0);
        assertInstanceOf(ExitValue.class, fixture.drag("child", "foreign", "before"));
        assertInstanceOf(ExitValue.class, fixture.drag("foreign", "child", "after"));
        assertInstanceOf(ExitValue.class, fixture.drag("parent", "grandchild", "after"));
        assertInstanceOf(ExitValue.class, fixture.drag("parent", "grandchild", "inner"));
        assertInstanceOf(ExitValue.class, fixture.drag("child", "missing", "after"));
        assertEquals(0, fixture.jdbc.writes);
        assertEquals("parent", fixture.row("child").get("parent_id"));
        assertEquals("0", fixture.row("parent").get("parent_id"));

        fixture.node("peer", "tenant-a", "parent", 2, 3, "0.parent.peer", 0);
        assertEquals(true, fixture.drag("child", "peer", "after"));
        assertEquals(2, fixture.row("child").get("sort_no"));
        assertEquals(1, fixture.row("peer").get("sort_no"));
        assertEquals("0.parent.child.grandchild", fixture.row("grandchild").get("tree_path"));
    }

    @Test
    void staleAncestorScopeCannotMoveAndBatchFailureRollsBackPriorWrites() throws Exception {
        var fixture = rootSiblings(101);
        fixture.node("unsafe", "tenant-a", "foreign-ancestor", 1, 3, "0.foreign-ancestor.unsafe", 0);
        fixture.node("foreign-ancestor", "tenant-b", "0", 1, 2, "0.foreign-ancestor", 0);
        assertInstanceOf(ExitValue.class, fixture.drag("n101", "unsafe", "inner"));
        assertEquals(0, fixture.jdbc.writes);
        fixture.jdbc.failWrite = 2;
        assertThrows(Exception.class, () -> fixture.drag("n101", "n1", "before"));
        assertEquals(2, fixture.jdbc.writes);
        assertEquals(1, fixture.row("n1").get("sort_no"));
        assertEquals(101, fixture.row("n101").get("sort_no"));
    }

    @Test
    void aConcurrentParentEditAbortsTheDragWithoutOverwritingEitherBranch() throws Exception {
        var fixture = rootSiblings(2);
        fixture.node("target", "tenant-a", "n1", 1, 3, "0.n1.target", 0);
        fixture.jdbc.beforeSnapshotLock = () -> CompletableFuture.runAsync(() -> fixture.setup.update(
            "UPDATE sym_dict_t SET parent_id = ?, level_no = ?, tree_path = ? WHERE tid = ?",
            "n2", 3, "0.n2.target", "target"
        )).orTimeout(5, TimeUnit.SECONDS).join();

        assertThrows(Exception.class, () -> fixture.drag("n2", "target", "inner"));
        assertEquals(0, fixture.jdbc.writes);
        assertEquals("0", fixture.row("n2").get("parent_id"));
        assertEquals("0.n2.target", fixture.row("target").get("tree_path"));
    }

    private static Fixture rootSiblings(int size) throws Exception {
        var fixture = new Fixture();
        for (int i = 1; i <= size; i++) {
            fixture.node("n" + i, "tenant-a", "0", i, 2, "0.n" + i, 0);
        }
        return fixture;
    }

    public static class Tenant {
        public String id() {
            return TenantContext.requireTenantId();
        }
    }

    private static class CountingJdbc extends JdbcTemplate {
        int reads;
        int writes;
        int failWrite;
        Runnable beforeSnapshotLock = () -> {};

        CountingJdbc(DriverManagerDataSource ds) {
            super(ds);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            beforeSnapshotLock.run();
            reads++;
            return super.queryForList(sql, args);
        }

        @Override
        public int update(String sql, Object... args) {
            writes++;
            if (writes == failWrite) throw new IllegalStateException("validation update failure");
            return super.update(sql, args);
        }
    }

    private static class ReadCounter implements SQLInterceptor {
        int reads;

        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            reads++;
        }
    }

    private static class Fixture {
        final JdbcTemplate setup;
        final CountingJdbc jdbc;
        final SQLModule db;
        final ReadCounter counter = new ReadCounter();
        final MagicScript script;

        Fixture() throws Exception {
            var ds = new DriverManagerDataSource("jdbc:h2:mem:dictionary_drag_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            setup = new JdbcTemplate(ds);
            jdbc = new CountingJdbc(ds);
            setup.execute("""
                CREATE TABLE sym_dict_t (
                    tid VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL, parent_id VARCHAR(32),
                    sort_no INT, level_no INT, tree_path VARCHAR(1000), is_del INT NOT NULL
                )
                """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(ds);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override
                public boolean match(String url) {
                    return url.contains(":h2:");
                }
            });
            db.setDialectAdapter(dialects);
            db.setResultProvider(new DefaultResultProvider("dictionary-validation"));
            var mapper = new ColumnMapperAdapter();
            mapper.add(new DefaultColumnMapperProvider());
            mapper.add(new LowerColumnMapperProvider());
            mapper.setDefault("lower");
            db.setColumnMapperProvider(mapper);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            String source = CanonicalMagicSources.byId(RESOURCE_ID)
                .replaceAll("(?m)^import (tenantRuntime|dictionaryTreeStorage)\\r?\\n", "");
            script = MagicScript.create(source, null);
        }

        int totalReads() {
            return counter.reads + jdbc.reads;
        }

        Object drag(String moving, String target, String type) {
            try (var ignored = TenantContext.use("tenant-a")) {
                return script.execute(new MagicScriptContext(Map.of(
                    "body", Map.of("tid", moving, "targetTid", target, "dropType", type,
                        "tenantId", "tenant-b", "targetParentId", "foreign"),
                    "tenantRuntime", new Tenant(),
                    "dictionaryTreeStorage", new DictionaryTreeStorageMagicModule(jdbc),
                    "db", db)));
            }
        }

        void node(String id, String tenant, String parent, int order, int level, String path, int deleted) {
            assertEquals(1, setup.update("INSERT INTO sym_dict_t VALUES(?, ?, ?, ?, ?, ?, ?)",
                id, tenant, parent, order, level, path, deleted));
        }

        Map<String, Object> row(String id) {
            return setup.queryForMap("SELECT * FROM sym_dict_t WHERE tid = ?", id);
        }
    }
}
