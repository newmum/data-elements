package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class TableSnapshotListMagicTest {
    private static final List<String> GOVERNANCE_COLUMNS = List.of(
            "field_governance_config", "dictionary_profiles", "dictionary_profile", "dictionary_categories");
    private static final List<String> GOVERNANCE_KEYS = List.of(
            "fieldGovernanceConfig", "dictionaryProfiles", "dictionaryProfile", "dictionaryCategories");

    @Test
    void twentyAndHundredSnapshotsHaveFixedReadsAndStatsBeforeListFilters() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            for (int count : List.of(20, 100)) {
                var f = new Fixture(dialect);
                for (int i = 0; i < count; i++) {
                    f.table("row-" + i, "table_" + i, "表" + i, i % 2 == 0 ? "业务表" : "字典表",
                            i % 2, i, "2026-10-24");
                }
                f.table("preferred", "TABLE_0", "已标注名称", "业务表", 1, 999, "2026-10-24");

                var all = f.page(count, Map.of("allSnapshots", true));
                assertEquals(count, number(all, "total"));
                assertStats(all, count, count / 2 + 1, count / 2, count / 2);
                var rows = StructuredValues.objectRows(all.get("rows"));
                assertEquals(rows, all.get("list"));
                assertEquals(count, rows.size());
                assertTrue(rows.stream().anyMatch(row -> number(row, "annotated") == 0));
                assertTrue(rows.stream().anyMatch(row -> number(row, "annotated") == 1));
                assertTrue(rows.stream().allMatch(row -> "police".equals(row.get("tenantId"))));
                var winner = rows.stream().filter(row -> "TABLE_0".equals(row.get("tableName"))).findFirst().orElseThrow();
                assertEquals("preferred", winner.get("tid"));
                assertEquals("已标注名称", winner.get("registeredResourceName"));
                assertTrue(rows.stream().noneMatch(row -> GOVERNANCE_KEYS.stream().anyMatch(row::containsKey)));
                assertEquals(4, f.source.reads.size());
                assertTrue(f.source.reads.stream().noneMatch(sql -> GOVERNANCE_COLUMNS.stream().anyMatch(sql::contains)));

                var marked = f.page(count, Map.of());
                assertEquals(count / 2 + 1, number(marked, "total"));
                assertStats(marked, count / 2 + 1, count / 2 + 1, 1, count / 2);
                assertEquals(4, f.source.reads.size());

                var filtered = f.page(count, Map.of("allSnapshots", true, "businessType", "字典表", "keyword", " TABLE_1 "));
                var filteredRows = StructuredValues.objectRows(filtered.get("rows"));
                assertEquals(6, number(filtered, "total"));
                assertTrue(filteredRows.stream().allMatch(row -> "字典表".equals(row.get("businessType"))));
                assertStats(filtered, count, count / 2 + 1, count / 2, count / 2);
                assertEquals(4, f.source.reads.size());
            }
        }
    }

    @Test
    void ranksSameNameBeforeKeywordFilterAndReadsGovernanceOnlyWhenRequested() throws Exception {
        var f = new Fixture("MySQL");
        f.table("unmarked", "same", "loser only", "字典表", 0, 999, "2026-10-25");
        f.table("lower-count", "same", "lower count", "字典表", 1, 9, "2026-10-25");
        f.table("older", "same", "older", "字典表", 1, 10, "2026-10-23");
        f.table("tie-a", "same", "tie loser", "字典表", 1, 10, "2026-10-24");
        f.table("tie-z", "SAME", "winner", "业务表", 1, 10, "2026-10-24");
        f.table("uncategorized", "other", "other", null, null, 1, null);
        f.jdbc.update("""
                update db_table_t set field_governance_config=?, dictionary_profiles=?,
                    dictionary_profile=?, dictionary_categories=? where tid='tie-z'
                """, "{\"fields\":[]}", "[]", "{}", "[\"test\"]");

        var page = f.page(20, Map.of("allSnapshots", true, "includeGovernance", true, "keyword", "WINNER"));
        assertEquals(List.of("tie-z"), ids(page));
        assertStats(page, 2, 1, 2, 0);
        var row = StructuredValues.objectRows(page.get("rows")).getFirst();
        assertEquals(List.of("{\"fields\":[]}", "[]", "{}", "[\"test\"]"),
                GOVERNANCE_KEYS.stream().map(row::get).toList());
        assertEquals(4, f.source.reads.size());
        assertTrue(GOVERNANCE_COLUMNS.stream().allMatch(f.source.reads.getLast()::contains));
        var statsSql = f.source.reads.stream().filter(sql -> sql.contains("group by")).findFirst().orElseThrow();
        assertTrue(GOVERNANCE_COLUMNS.stream().noneMatch(statsSql::contains));

        var noMatches = f.page(20, Map.of("allSnapshots", true, "keyword", "loser only"));
        assertEquals(0, number(noMatches, "total"));
        assertEquals(List.of(), ids(noMatches));
        assertStats(noMatches, 2, 1, 2, 0);
        assertEquals(3, f.source.reads.size());
    }

    @Test
    void orderingFallsBackToLatestAndUsesNullLastAndDescendingIdTies() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            var f = new Fixture(dialect);
            f.table("sort-a", "a", "first", "业务表", 1, 2, "2026-10-24");
            f.table("sort-z", "z", "tie", "业务表", 1, 2, "2026-10-24");
            f.table("sort-new", "latest", "latest", "业务表", 1, 10, "2026-10-25");
            f.table("sort-null", "unset", null, "业务表", 1, null, null);
            assertEquals(List.of("sort-z", "sort-a", "sort-new", "sort-null"),
                    ids(f.page(20, Map.of("sortField", "fieldCount"))));
            var latest = List.of("sort-new", "sort-z", "sort-a", "sort-null");
            assertEquals(latest, ids(f.page(20, Map.of("sortField", "fieldCount", "sortDir", "DESC"))));
            assertEquals(latest, ids(f.page(20, Map.of("sortField", "fieldCount; drop table db_table_t", "sortDir", "asc"))));
            assertTrue(f.source.reads.stream().noneMatch(sql -> sql.contains("drop table")));
            assertEquals(List.of("sort-a"), ids(f.page(1, Map.of("sortField", "fieldCount", "pageNo", 2))));
        }
    }

    @Test
    void rejectsForeignDeletedOrMissingSourcesBeforeReadingSnapshots() throws Exception {
        var f = new Fixture("MySQL");
        for (String sourceId : List.of("foreign-source", "deleted-source", "unknown-source")) {
            assertInstanceOf(ExitValue.class, f.read(100, Map.of("dbId", sourceId)));
            assertEquals(1, f.source.reads.size());
        }
        f.scope.tenant = "unknown-tenant";
        assertInstanceOf(ExitValue.class, f.read(100, Map.of()));
        assertEquals(1, f.source.reads.size());
        f.scope.tenant = "police";
        f.scope.denied = true;
        f.table("current-table", "current", "有效表", "业务表", 1, 1, "2026-10-24");
        assertEquals(List.of("current-table"), ids(f.page(100, Map.of())));
        assertEquals(4, f.source.reads.size());
    }

    @Test
    void legacyArraysKeepTenantIsolationAndFixedReads() throws Exception {
        for (int count : List.of(20, 100)) {
            var f = new Fixture("MySQL");
            var marked = IntStream.range(0, count).mapToObj(i -> "marked-" + i).toList();
            for (String id : marked) {
                f.table(id, id, "已标注", "业务表", 1, 1, "2026-10-24");
            }
            f.table("unmarked", "unmarked", "待处理", "暂不处理", 0, 1, "2026-10-25");
            f.jdbc.update("""
                    insert into db_table_t(tid, tenant_id, datasource_id, table_name, annotated, is_del)
                    values ('foreign-pending','other','source','foreign-pending',0,0)
                    """);
            var all = new ArrayList<>(marked);
            all.add("unmarked");

            assertLegacyRows(f, Map.of("paged", false, "tenantId", "other"), all);
            assertLegacyRows(f, Map.of("paged", false, "pending", true), List.of("unmarked"));
            assertLegacyRows(f, Map.of("paged", false, "annotatedOnly", true), marked);
        }
    }

    @Test
    void annotatedArrayPreservesDatabaseRankNativeNullsAndTheSecondNamePass() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            var f = new Fixture(dialect);
            boolean nullsFirst = dialect.equals("PostgreSQL");
            f.jdbc.execute("set default_null_ordering " + (nullsFirst ? "HIGH" : "LOW"));

            f.table("field-count-old", "RANKED", "Rank winner", "业务表", 1, 10, "2026-01-02");
            f.table("field-count-new", "ranked", "More recent", "业务表", 1, 9, "2026-01-09");
            f.table("unmarked-ranked", "ranked", "Unmarked", "日志表", 0, 1000, "2026-01-20");
            f.table("native-timed", "NATIVE", null, "业务表", 1, 7, "2026-01-05");
            f.table("native-null", "native", null, "业务表", 1, 7, null);
            f.table("tie-a", "TIE", null, "业务表", 1, null, "2026-01-03");
            f.table("tie-z", "tie", null, "业务表", 1, 0, "2026-01-03");
            f.table("outer-z", "outer", null, "业务表", 1, 2, "2026-01-03");
            f.table("trim-plain", "trimmed", null, "业务表", 1, 100, "2026-01-08");
            f.table("trim-winner", " trimmed", "Fallback comment", "业务表", 1, 0, null);
            f.table("equal-plain", "equal", null, "业务表", 1, 1, "2026-01-06");
            f.table("equal-null", " equal", null, "业务表", 1, 1, null);
            f.table("blank-name", "   ", null, "业务表", 1, 10, "2026-01-21");
            f.table("null-name", null, null, "业务表", 1, 10, "2026-01-21");
            f.jdbc.execute("""
                    insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,field_count,is_del) values
                        ('foreign-ranked','other','source','ranked',1,1000,0),
                        ('deleted-ranked','police','source','ranked',1,1000,1),
                        ('other-source-ranked','police','different','ranked',1,1000,0);
                    update db_table_t set created_time=timestamp '2026-01-12 00:00:00',
                        catalog_name='Stored catalog',catalog_name_en='stored_catalog',asset_desc='Stored asset',
                        field_governance_config='{"fields":[]}',dictionary_profiles='[]',
                        dictionary_profile='{}',dictionary_categories='["test"]' where tid='trim-winner';
                    update db_table_t set created_time=timestamp '2026-01-06 00:00:00' where tid='equal-null';
                    """);

            // Body tenant and page filters do not override the legacy current-tenant array contract.
            f.scope.denied = true;
            var rows = StructuredValues.objectRows(f.read(1, Map.of("paged", false, "annotatedOnly", " TrUE ",
                    "tenantId", "other", "pageNo", 8, "keyword", "no such table", "businessType", "暂不处理",
                    "sortField", "tableName", "sortDir", "asc", "includeGovernance", true)));
            var expected = nullsFirst
                    ? List.of("trim-winner", "native-null", "equal-null", "tie-z", "outer-z", "field-count-old")
                    : List.of("equal-plain", "native-timed", "tie-z", "outer-z", "field-count-old", "trim-winner");
            assertEquals(expected, rows.stream().map(row -> row.get("tid")).toList(), dialect);
            assertTrue(rows.stream().allMatch(row -> "police".equals(row.get("tenantId")) && number(row, "annotated") == 1));
            var trim = rows.stream().filter(row -> "trim-winner".equals(row.get("tid"))).findFirst().orElseThrow();
            assertEquals(" trimmed", trim.get("tableName"));
            assertEquals("app", trim.get("appId"));
            assertEquals("org", trim.get("orgId"));
            assertEquals("Stored catalog", trim.get("catalogName"));
            assertEquals("stored_catalog", trim.get("catalogNameEn"));
            assertEquals("Stored asset", trim.get("assetDesc"));
            assertEquals(List.of("{\"fields\":[]}", "[]", "{}", "[\"test\"]"),
                    GOVERNANCE_KEYS.stream().map(trim::get).toList());
            assertEquals(2, f.source.reads.size());
            assertEquals(0, f.source.writes);
        }
    }

    @Test
    void annotatedArrayKeepsTheCompleteResultBeyondTheBoundedRowsLimit() throws Exception {
        var f = new Fixture("MySQL");
        f.jdbc.update("""
                insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,field_count,updated_time,is_del)
                select 'marked-' || lpad(cast(n as varchar),5,'0'),'police','source',
                    'name_' || lpad(cast(n as varchar),5,'0'),1,1,timestamp '2026-01-01 00:00:00',0
                from system_range(1,10002) as entries(n)
                """);
        var rows = StructuredValues.objectRows(f.read(1, Map.of("paged", false, "annotatedOnly", "1", "pageNo", 999)));
        assertEquals(10002, rows.size());
        assertEquals("marked-10002", rows.getFirst().get("tid"));
        assertEquals("marked-00001", rows.getLast().get("tid"));
        assertEquals(10002, rows.stream().map(row -> row.get("tid")).distinct().count());
        assertEquals(2, f.source.reads.size());
        assertEquals(0, f.source.writes);
    }

    @Test
    void annotatedArrayYieldsToCatalogPendingAndPagedModes() throws Exception {
        var f = new Fixture("MySQL");
        f.table("marked", "marked", "Marked", "业务表", 1, 3, "2026-01-03");
        f.table("unmarked", "unmarked", "Pending", "业务表", 0, 2, "2026-01-02");
        f.table("linked", "linked", "Linked", "业务表", 0, 1, "2026-01-01");
        f.catalog("catalog", "police", "Existing catalog", "catalog", 0);
        f.link("linked", "catalog");

        var catalog = f.page(20, Map.of("catalogPaged", true, "pending", true, "paged", true, "annotatedOnly", true));
        assertCatalogTotals(catalog, 2, 1, 2);
        assertEquals(List.of("marked", "unmarked"), ids(catalog));
        assertLegacyRows(f, Map.of("pending", true, "paged", true, "annotatedOnly", true), List.of("unmarked", "linked"));

        var paged = f.page(20, Map.of("annotatedOnly", true, "allSnapshots", true));
        assertEquals(List.of("marked", "unmarked", "linked"), ids(paged));
        assertStats(paged, 3, 1, 3, 0);
        assertLegacyRows(f, Map.of("paged", false, "annotatedOnly", true), List.of("marked"));
    }

    @Test
    void annotatedArrayOptOutDoesNotSelectGovernanceColumns() throws Exception {
        var f = new Fixture("MySQL");
        f.table("marked", "table", null, "业务表", 1, 1, null);
        f.jdbc.update("update db_table_t set field_governance_config='{}',dictionary_categories='[]' where tid='marked'");

        var included = StructuredValues.objectRows(f.read(20, Map.of("paged", false, "annotatedOnly", true, "includeGovernance", true)));
        assertEquals("{}", included.getFirst().get("fieldGovernanceConfig"));
        assertEquals("[]", included.getFirst().get("dictionaryCategories"));
        assertTrue(GOVERNANCE_COLUMNS.stream().allMatch(f.source.reads.getLast()::contains));
        var excluded = StructuredValues.objectRows(f.read(20, Map.of("paged", false, "annotatedOnly", true, "includeGovernance", "0")));
        assertTrue(GOVERNANCE_KEYS.stream().noneMatch(excluded.getFirst()::containsKey));
        assertTrue(f.source.reads.stream().noneMatch(sql -> GOVERNANCE_COLUMNS.stream().anyMatch(sql::contains)));
        assertEquals(2, f.source.reads.size());
        assertEquals(0, f.source.writes);
    }

    @Test
    void catalogPaginationKeepsTabTotalsAndReadCountsAtTwentyAndHundred() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            var f = new Fixture(dialect);
            var expected = IntStream.range(0, 120).mapToObj(i -> "pending-%03d".formatted(i)).toList();
            for (String name : expected) {
                f.table(name, name, null, "业务表", 0, 1, "2026-10-24");
            }
            f.catalog("own-catalog", "police", "现用目录", "current_catalog", 0);
            f.table("registered", "registered", null, "业务表", 0, 1, "2026-10-24");
            f.link("registered", "own-catalog");

            for (int size : List.of(20, 100)) {
                var page = f.catalogPage(size, Map.of("sortField", "fieldCount", "sortDir", "desc"));
                assertCatalogTotals(page, 120, 1, 120);
                assertEquals(1, number(page, "pageNo"));
                assertEquals(size, number(page, "pageSize"));
                assertEquals(expected.subList(0, size), ids(page));
                assertEquals(page.get("rows"), page.get("list"));
                assertEquals(4, f.source.reads.size());
                assertTrue(f.source.reads.stream().noneMatch(sql -> GOVERNANCE_COLUMNS.stream().anyMatch(sql::contains)));

                var second = f.catalogPage(size, Map.of("pageNo", 2));
                assertEquals(expected.subList(size, Math.min(size * 2, expected.size())), ids(second));
                assertCatalogTotals(second, 120, 1, 120);
                assertEquals(4, f.source.reads.size());

                var missing = f.catalogPage(size, Map.of("tableNames", "not_found"));
                assertCatalogTotals(missing, 120, 1, 0);
                assertEquals(List.of(), ids(missing));
                assertEquals(3, f.source.reads.size());
            }
        }
    }

    @Test
    void catalogRanksScopedActiveLinksBeforeFiltersAndKeepsCatalogAliases() throws Exception {
        var f = new Fixture("MySQL");
        f.catalog("live-catalog", "police", "现用目录", "live_catalog", 0);
        f.catalog("foreign-catalog", "other", "外租户目录", "foreign_catalog", 0);
        f.catalog("deleted-catalog", "police", "已删除目录", "deleted_catalog", 1);
        f.table("linked", "ALPHA", "已登记表名", "业务表", 0, 1, "2026-10-20");
        f.link("linked", "live-catalog");
        f.table("new-unlinked", "alpha ", "未登记快照", "字典表", 1, 99, "2026-10-25");
        f.table("old-link", "alpha", "更旧的已登记表名", "字典表", 0, 100, "2026-10-19");
        f.link("old-link", "live-catalog");
        f.table("foreign-reference", "foreign_reference", null, "业务表", 1, 2, "2026-10-21");
        f.link("foreign-reference", "foreign-catalog");
        f.table("deleted-reference", "deleted_reference", null, "业务表", 1, 2, "2026-10-21");
        f.link("deleted-reference", "deleted-catalog");

        var registered = f.catalogPage(20, Map.of("catalogStatus", "registered", "catalogNames", "不存在\n现用",
                "tableNames", "nobody\nALPHA"));
        assertCatalogTotals(registered, 2, 1, 1);
        assertEquals(List.of("linked"), ids(registered));
        var row = StructuredValues.objectRows(registered.get("rows")).getFirst();
        assertEquals("linked", row.get("id"));
        assertEquals("source", row.get("dbId"));
        assertEquals("police", row.get("tenantId"));
        assertEquals("live-catalog", row.get("catalogId"));
        assertEquals("live-catalog", row.get("sourceCatalogId"));
        assertEquals("现用目录", row.get("catalogName"));
        assertEquals("live_catalog", row.get("catalogNameEn"));
        assertEquals("已登记表名", row.get("registeredResourceName"));

        var pending = f.catalogPage(20, Map.of());
        assertCatalogTotals(pending, 2, 1, 2);
        assertEquals(List.of("deleted-reference", "foreign-reference"), ids(pending));
        for (var item : StructuredValues.objectRows(pending.get("rows"))) {
            assertNull(item.get("catalogId"));
            assertNull(item.get("catalogName"));
            assertNull(item.get("catalogNameEn"));
        }
        var rejectedLoser = f.catalogPage(20, Map.of("catalogStatus", "registered", "businessType", "字典表"));
        assertCatalogTotals(rejectedLoser, 2, 1, 0);
        assertEquals(List.of(), ids(rejectedLoser));
        for (String sourceId : List.of("foreign-source", "deleted-source", "unknown-source")) {
            var rejected = assertInstanceOf(ExitValue.class, f.read(100, Map.of("catalogPaged", true, "dbId", sourceId)));
            assertEquals(404, rejected.getValues()[0]);
            assertEquals(1, f.source.reads.size());
        }
    }

    @Test
    void catalogMissingCollectionKeepsAnnotatedAndLinkedRowsAndClassifiesBeforePaging() throws Exception {
        var f = new Fixture("MySQL");
        f.catalog("live-catalog", "police", "现用目录", "live_catalog", 0);
        f.table("missing", " missing_tbl ", null, null, 0, 1, "2026-10-24");
        f.table("missing-marked", "missing_marked", null, "业务表", 1, 1, "2026-10-24");
        f.table("missing-linked", "missing_linked", null, "业务表", 0, 1, "2026-10-24");
        f.link("missing-linked", "live-catalog");
        f.table("backup", "validation-history", "优先识别为备份，即使包含审计日志", null, 0, 1, "2026-10-24");
        f.table("dated-backup", "validation_20261024", null, null, 0, 1, "2026-10-24");
        f.table("log", "validation_audit", null, null, 0, 1, "2026-10-24");
        f.table("dictionary", "validation_config", null, null, 0, 1, "2026-10-24");
        f.table("process", "validation_process", null, null, 0, 1, "2026-10-24");
        f.table("fallback-comment", "validation_detail", null, null, 0, 1, "2026-10-24");
        f.table("empty-comment", "validation_empty", "", null, 0, 1, "2026-10-24");
        f.jdbc.update("update db_table_t set table_name_cn=? where tid in (?,?)", "流程明细", "fallback-comment", "empty-comment");
        f.table("manual", "validation_workflow", null, "业务表", 0, 1, "2026-10-24");
        f.jdbc.update("update db_table_t set business_type_reason=? where tid=?", "上次人工调整后保留", "manual");
        f.table("uncertain", "validation_backup", null, "不确定", 0, 1, "2026-10-24");
        f.job("older", "police", "source", "SUCCEEDED", 0, "2026-10-23", "[{\"tableName\":\"validation_audit\"}]");
        f.job("newest-failed", "police", "source", "FAILED", 0, "2026-10-26", "[{\"tableName\":\"validation_config\"}]");
        f.job("newest-foreign", "other", "source", "SUCCEEDED", 0, "2026-10-26", "[{\"tableName\":\"validation_process\"}]");
        f.job("newest-deleted", "police", "source", "SUCCEEDED", 1, "2026-10-26", "[{\"tableName\":\"validation_detail\"}]");
        f.job("current", "police", "source", "SUCCEEDED", 0, "2026-10-25", """
                [{"tableName":" MISSING_TBL "},{"table_name":"MISSING_MARKED"},{"tableName":"MISSING_LINKED"}]
                """);

        var enabled = Map.<String, Object>of("checkCollectionMissing", 1);
        var pending = f.catalogPage(20, enabled);
        assertCatalogTotals(pending, 10, 1, 10);
        assertFalse(ids(pending).contains("missing"));
        assertTrue(ids(pending).contains("missing-marked"));
        assertEquals(5, f.source.reads.size());
        assertCatalogTotals(f.catalogPage(100, enabled), 10, 1, 10);
        assertEquals(5, f.source.reads.size());
        assertCatalogTotals(f.catalogPage(100, Map.of()), 11, 1, 11);

        var categories = Map.of("备份表", List.of("backup", "dated-backup"), "日志表", List.of("log"),
                "字典表", List.of("dictionary"), "过程表", List.of("fallback-comment", "process"),
                "业务表", List.of("missing-marked", "empty-comment", "manual"), "不确定", List.of("uncertain"));
        for (var category : categories.entrySet()) {
            var filtered = f.catalogPage(20, Map.of("checkCollectionMissing", 1, "businessType", category.getKey()));
            assertCatalogTotals(filtered, 10, 1, category.getValue().size());
            assertEquals(category.getValue(), ids(filtered));
            assertEquals(5, f.source.reads.size());
        }
        var registered = f.catalogPage(20, Map.of("checkCollectionMissing", 1, "catalogStatus", "registered"));
        assertCatalogTotals(registered, 10, 1, 1);
        assertEquals(List.of("missing-linked"), ids(registered));
    }

    private static void assertCatalogTotals(Map<String, Object> page, long pending, long registered, long filtered) {
        assertEquals(pending, number(page, "pendingTotal"));
        assertEquals(registered, number(page, "registeredTotal"));
        assertEquals(filtered, number(page, "total"));
    }

    private static void assertLegacyRows(Fixture fixture, Map<String, Object> options, List<String> expected)
            throws Exception {
        var response = assertInstanceOf(List.class, fixture.read(20, options));
        var ids = StructuredValues.objectRows(response).stream().map(row -> (String) row.get("tid")).sorted().toList();
        assertEquals(expected.stream().sorted().toList(), ids);
        assertEquals(2, fixture.source.reads.size());
        assertEquals(0, fixture.source.writes);
    }

    private static long number(Map<String, Object> row, String key) {
        return assertInstanceOf(Number.class, row.get(key), key).longValue();
    }

    private static List<Object> ids(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("rows")).stream().map(row -> row.get("tid")).toList();
    }

    private static void assertStats(Map<String, Object> page, int snapshots, int annotated, int business, int dictionary) {
        assertEquals(snapshots, number(page, "snapshotTotal"));
        assertEquals(annotated, number(page, "annotatedTotal"));
        var stats = StructuredValues.objectMap(page.get("businessStats"));
        assertEquals(business, number(stats, "业务表"));
        assertEquals(dictionary, ((Number) stats.getOrDefault("字典表", 0)).longValue());
    }

    public static class Scope {
        String tenant = "police";
        boolean denied;
        public String id() { return tenant; }
        public List<?> visibleDataSourcesFor(String resource, List<?> rows) { return denied ? List.of() : rows; }
    }

    private static class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();

        Fixture(String dialect) {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64), tenant_id varchar(64), db_name varchar(64),
                        app_id varchar(64), org_id varchar(64), is_del int)
                    """);
            jdbc.execute("""
                    insert into db_datasource_t values ('source','police','db','app','org',0),
                        ('foreign-source','other','secret','app','org',0), ('deleted-source','police','deleted','app','org',1)
                    """);
            jdbc.execute("""
                    create table db_table_t(tid varchar(64), datasource_id varchar(64), table_name varchar(255),
                        table_name_en varchar(255), table_name_cn varchar(255), table_comment varchar(500), table_type varchar(64),
                        business_type varchar(64), business_type_reason varchar(255), record_count bigint, storage_size varchar(64),
                        total_size_bytes bigint, total_size_formatted varchar(64), org_id varchar(64), org_path varchar(500),
                        related_directory varchar(64), data_source_type varchar(64), timestamp_field varchar(64),
                        dictionary_structure_type varchar(64), dictionary_table_flag int, source_catalog_id varchar(64),
                        field_governance_config varchar(500), dictionary_profiles varchar(500), dictionary_profile varchar(500),
                        dictionary_categories varchar(500), source_table_id varchar(64), source_table_name varchar(255),
                        manage_unit varchar(64), asset_desc varchar(500), catalog_name varchar(255), catalog_name_en varchar(255),
                        asset_status int, flow_status int, reg_time timestamp, created_time timestamp,
                        updated_time timestamp, tenant_id varchar(64), field_count int, annotated int, is_del int)
                    """);
            jdbc.execute("""
                    insert into db_table_t(tid, tenant_id, datasource_id, table_name, business_type, annotated, field_count, is_del)
                    values ('foreign-table','other','source','table_0','日志表',1,1000,0),
                        ('deleted-table','police','source','table_0','日志表',1,1000,1),
                        ('wrong-source-table','police','different','table_0','日志表',1,1000,0),
                        ('deleted-source-table','police','deleted-source','deleted-source-table','业务表',1,1000,0)
                    """);
            jdbc.execute("""
                    create table da_catalog_t(tid varchar(64), tenant_id varchar(64), catalog_name varchar(255),
                        catalog_name_en varchar(255), is_del int)
                    """);
            jdbc.execute("""
                    create table metadata_table_collection_job_t(tid varchar(64), tenant_id varchar(64),
                        datasource_id varchar(64), status varchar(32), is_del int, created_time timestamp,
                        deleted_detail varchar(16000))
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("snapshot-list-test"));
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }

        void table(String id, String name, String comment, String type, Integer annotated, Integer fields, String updated) {
            jdbc.update("""
                    insert into db_table_t(tid, tenant_id, datasource_id, table_name, table_comment, business_type,
                        annotated, field_count, updated_time, is_del) values (?,'police','source',?,?,?,?,?,?,0)
                    """, id, name, comment, type, annotated, fields,
                    updated == null ? null : Timestamp.valueOf(updated + " 00:00:00"));
        }

        void catalog(String id, String tenant, String name, String englishName, int deleted) {
            jdbc.update("insert into da_catalog_t values (?,?,?,?,?)", id, tenant, name, englishName, deleted);
        }

        void link(String table, String catalog) {
            jdbc.update("update db_table_t set source_catalog_id=? where tid=?", catalog, table);
        }

        void job(String id, String tenant, String source, String status, int deleted, String created, String detail) {
            jdbc.update("insert into metadata_table_collection_job_t values (?,?,?,?,?,?,?)", id, tenant, source,
                    status, deleted, Timestamp.valueOf(created + " 00:00:00"), detail);
        }

        Map<String, Object> catalogPage(int size, Map<String, Object> options) throws Exception {
            var arguments = new HashMap<String, Object>(options);
            arguments.put("catalogPaged", true);
            return page(size, arguments);
        }

        Map<String, Object> page(int size, Map<String, Object> options) throws Exception {
            return StructuredValues.objectMap(assertInstanceOf(Map.class, read(size, options)));
        }

        Object read(int size, Map<String, Object> options) throws Exception {
            var script = MagicScript.create(CanonicalMagicSources.byId("52e398a9f2fc429da79048b9ea719b37")
                    .replaceAll("(?m)^import (?:tenantRuntime|jsons|tableRead)[ \\t]*\\r?\\n", ""), null);
            var vars = new HashMap<String, Object>();
            vars.put("db", db);
            vars.put("tableRead", new StructuredTableReadMagicModule(jdbc));
            vars.put("tenantRuntime", scope);
            vars.put("jsons", new JsonModule());
            vars.put("dataScope", scope);
            vars.put("dbId", "source");
            vars.put("paged", true);
            vars.put("pageNo", 1);
            vars.put("pageSize", size);
            vars.put("allSnapshots", false);
            vars.put("includeGovernance", false);
            vars.putAll(options);
            source.reads.clear();
            source.writes = 0;
            return TenantContext.call(scope.tenant, () -> script.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        final List<String> reads = new ArrayList<>();
        int writes;
        private final String dialectUrl;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:snapshot_" + UUID.randomUUID() + ";MODE=" + dialect
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            dialectUrl = "jdbc:" + dialect.toLowerCase(Locale.ROOT) + "://snapshot-validation";
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? dialectUrl : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) {
                            String sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) {
                                reads.add(sql);
                            } else {
                                writes++;
                            }
                        }
                        return invoke(connection, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
