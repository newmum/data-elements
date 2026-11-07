package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;

class DepartmentDatasourceReadsMagicTest {
    private static final String SCRIPT = "datapd_dept_datasources_01";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void reportRanksBeforeBusinessFilterAndReadsOneBoundedPage(int count) throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", count);
        var sources = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            String source = "own-" + i;
            sources.add(source);
            fixture.jdbc.update("UPDATE db_datasource_t SET pool_cfg=? WHERE tid=?",
                    "{\"dataAccessMode\":\"upload\",\"reportRegistration\":{\"files\":[{}]}}", source);
            add(fixture, "older-" + i, "ga", source, "Events", "业务表", 1, 2, 0, 1);
            add(fixture, "newer-unannotated-" + i, "ga", source, "events", "日志表", 0, 2, 0, 3);
        }
        add(fixture, "newer-nonbusiness", "ga", "own-0", "EVENTS", "字典表", 1, 2, 0, 2);
        add(fixture, "foreign-event", "foreign", "own-1", "events", "业务表", 1, 2, 0, 4);
        add(fixture, "deleted-event", "ga", "own-1", "events", "字典表", 1, 2, 1, 4);
        sources.add("own-1");
        var result = (Map<?, ?>) fixture.run(SCRIPT,
                Map.of("mode", "reportTables", "datasourceIds", sources, "pageNum", 1, "pageSize", count));
        assertThat(((Number) result.get("total")).intValue()).isEqualTo(count - 1);
        assertThat(rows(result)).contains("older-1").doesNotContain("older-0", "foreign-event", "deleted-event");
        assertThat(fixture.counter.reads).isEqualTo(3);
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.index.reads).isZero();
        var beyond = (Map<?, ?>) fixture.run(SCRIPT,
                Map.of("mode", "reportTables", "datasourceIds", sources, "pageNum", 2, "pageSize", count));
        assertThat(rows(beyond)).isEmpty();
        assertThat(((Number) beyond.get("total")).intValue()).isEqualTo(count - 1);
        assertThat(fixture.counter.reads).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void sourceTablePagesKeepNullTypesAndClampTheLastPage(int count) throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", 1);
        for (int i = 0; i < count + 1; i++) {
            add(fixture, "registered-" + i, "ga", "own-0", "table-" + i,
                    i == 0 ? null : "业务表", 1, 2, 0, i + 1);
        }
        add(fixture, "deferred", "ga", "own-0", "deferred", "DEFERRED", 1, 2, 0, 2);
        add(fixture, "other-owner", "ga", "dept-0", "other", "业务表", 1, 2, 0, 2);
        add(fixture, "wrong-tenant", "foreign", "own-0", "foreign", "业务表", 1, 2, 0, 2);
        var last = (Map<?, ?>) fixture.run(SCRIPT,
                Map.of("mode", "tables", "datasourceId", "own-0", "pageNum", 500, "pageSize", count,
                        "sortField", "createdTime", "sortDir", "desc"));
        assertThat(last.get("pageNum")).isEqualTo(2);
        assertThat(((Number) last.get("total")).intValue()).isEqualTo(count + 1);
        assertThat(rows(last)).containsExactly("registered-0");
        assertThat(fixture.counter.reads).isEqualTo(3);
        assertThat(fixture.index.reads).isEqualTo(1);
    }

    @Test
    void legacyCardsBatchFillSourceTablesAndIgnoreForeignAndDeletedRecords() throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", 1);
        fixture.db.setResultProvider(new DefaultResultProvider("datasource-compatibility-validation"));
        add(fixture, "legacy-business", "ga", "own-0", "business", "业务表", 1, 2, 0, 1);
        add(fixture, "legacy-deleted", "ga", "own-0", "gone", "日志表", 1, 2, 1, 1);
        add(fixture, "legacy-foreign", "foreign", "own-0", "foreign", "业务表", 1, 2, 0, 1);
        var payload = (Map<?, ?>) fixture.run(SCRIPT, Map.of());
        var card = (Map<?, ?>) ((List<?>) payload.get("list")).getFirst();
        assertThat(rows(Map.of("list", card.get("tables")))).containsExactly("legacy-business");
        assertThat(card.get("tableNum")).isEqualTo(1);
        assertThat(fixture.counter.reads).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void legacyEnrichmentReadsOnlyTheIndexWindowOfALargeDatasource(int visible) throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", 1);
        seedLargeDatasource(fixture);
        var index = new LegacyIndex(visible, false);
        var payload = (Map<?, ?>) fixture.run(SCRIPT, Map.of(), index);
        var card = (Map<?, ?>) ((List<?>) payload.get("list")).getFirst();
        var tables = (List<?>) card.get("tables");
        assertThat(tables).hasSize(visible);
        var stale = (Map<?, ?>) tables.getLast();
        assertThat(stale.get("tid")).isEqualTo("stale-table-id");
        assertThat(stale.get("createdTime")).isNotNull();
        assertThat(stale.get("assetStatus")).isEqualTo(2);
        assertThat(card.get("tableNum")).isEqualTo(10_001);
        assertThat(index.reads).isEqualTo(2);
        assertThat(fixture.counter.reads).isEqualTo(4);
    }

    @Test
    void legacyFallbackRetainsTheFullAuthorizedListAcrossReadWindows() throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", 1);
        fixture.db.setResultProvider(new DefaultResultProvider("datasource-compatibility-validation"));
        seedLargeDatasource(fixture);
        var payload = (Map<?, ?>) fixture.run(SCRIPT, Map.of(), new LegacyIndex(0, false));
        var card = (Map<?, ?>) ((List<?>) payload.get("list")).getFirst();
        assertThat(rows(Map.of("list", card.get("tables")))).hasSize(10_001).doesNotHaveDuplicates();
        assertThat(card.get("tableNum")).isEqualTo(10_001);
        assertThat(fixture.counter.reads).isEqualTo(6);
    }

    @Test
    void legacyEmptyScopeRemainsAvailableWhenTheIndexIsUnreachable() throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", 0);
        var index = new LegacyIndex(0, true);
        var payload = (Map<?, ?>) fixture.run(SCRIPT, Map.of(), index);
        assertThat(rows(payload)).isEmpty();
        assertThat(index.reads).isEqualTo(1);
        assertThat(fixture.counter.reads).isEqualTo(1);
    }

    public static final class LegacyIndex {
        private final int size;
        private final boolean unavailable;
        int reads;

        LegacyIndex(int size, boolean unavailable) {
            this.size = size;
            this.unavailable = unavailable;
        }

        public LegacyIndex index(String name) {
            return this;
        }

        public Map<String, Object> search(Map<?, ?> query) {
            reads++;
            if (unavailable) {
                throw new IllegalStateException("Validation index unavailable");
            }
            var hits = new ArrayList<>();
            if (!query.containsKey("track_total_hits")) {
                for (int index = 0; index < size; index++) {
                    String id = index == size - 1 ? "stale-table-id" : "table-" + index;
                    hits.add(Map.of("_id", id, "_source", new LinkedHashMap<>(Map.of(
                            "tid", id, "datasourceId", "own-0", "tableName", "name-" + index))));
                }
            }
            return Map.of("hits", Map.of("hits", hits));
        }
    }

    private static List<String> rows(Map<?, ?> payload) {
        return ((List<?>) payload.get("list")).stream().map(row -> ((Map<?, ?>) row).get("tid").toString()).toList();
    }

    private static void seedLargeDatasource(DatasourceManagementPermissionsMagicTest.Fixture fixture) {
        var stamp = Timestamp.valueOf("2025-01-01 10:00:00");
        var metadata = new ArrayList<Object[]>();
        for (int index = 0; index < 10_001; index++) {
            metadata.add(new Object[]{"table-" + index, "name-" + index, stamp, stamp});
        }
        fixture.jdbc.batchUpdate("""
                INSERT INTO db_table_t (tid,tenant_id,datasource_id,is_del,asset_status,table_name,
                    created_time,updated_time)
                VALUES (?, 'ga','own-0',0,2,?,?,?)
                """, metadata);
    }

    private static void add(DatasourceManagementPermissionsMagicTest.Fixture fixture,
            String id, String tenant, String source, String name, String type, int annotated,
            int status, int deleted, int day) {
        var timestamp = Timestamp.valueOf("2025-01-01 00:00:00").toLocalDateTime().plusDays(day);
        fixture.jdbc.update("""
                INSERT INTO db_table_t (tid,tenant_id,datasource_id,is_del,asset_status,annotated,business_type,
                    field_count,table_name,table_name_en,table_type,record_count,created_time,updated_time)
                VALUES (?,?,?,?,?,?,?,4,?,?, 'TABLE', 10,?,?)
                """, id, tenant, source, deleted, status, annotated, type, name, name,
                Timestamp.valueOf(timestamp), Timestamp.valueOf(timestamp));
    }
}
