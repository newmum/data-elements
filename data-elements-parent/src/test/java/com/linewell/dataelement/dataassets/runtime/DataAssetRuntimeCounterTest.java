package com.linewell.dataelement.dataassets.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class DataAssetRuntimeCounterTest {
    private static final String TENANT = "validation-tenant";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void callbackBatchReadsAllIdsAndPropertiesOnceThenMergesOneBulk(int size) {
        var fixture = new Fixture();
        var ids = IntStream.range(0, size).mapToObj(i -> "validation-catalog-" + i).toList();
        for (int i = 0; i < size; i++) {
            fixture.catalog(ids.get(i), TENANT, 0, i);
            fixture.property(ids.get(i), TENANT, "accessTaskId", "old-task", 0);
        }
        Map<String, Object> forged = Map.of("accessTaskId", "new-task", "viewCount", 999999,
                "view_count_num", 999999, "market_view_count", 999999, "tenantId", "other", "tid", "forged");

        fixture.batch(ids, forged);

        assertThat(fixture.readSql()).hasSize(2);
        assertThat(fixture.bulkCalls).isEqualTo(1);
        assertThat(fixture.bulkIds).containsExactlyElementsOf(ids);
        assertThat(fixture.bulkDocs).hasSize(size);
        assertThat(fixture.bulkCounts.stream().map(value -> ((Number) value).longValue()).toList())
                .containsExactlyElementsOf(IntStream.range(0, size).mapToObj(i -> (long) i).toList());
        assertThat(fixture.bulkDocs).allSatisfy(doc -> assertThat(doc).containsEntry("assetType", "catalog")
                .containsEntry("tenantId", TENANT).containsEntry("accessTaskId", "new-task")
                .doesNotContainKey("marketViewCount").doesNotContainKey("view_count"));
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM da_prop_t WHERE prop_name='accessTaskId' AND prop_value='new-task'", Integer.class))
                .isEqualTo(size);
        assertThat(fixture.raw.queryForObject("SELECT SUM(market_view_count) FROM da_catalog_t", Long.class))
                .isEqualTo(size * (size - 1L) / 2L);
        verify(fixture.props).updateBatchById(anyCollection());
        verify(fixture.props, never()).saveBatch(anyCollection());
        verify(fixture.es, never()).getDocument(anyString(), anyString());
        verify(fixture.es, never()).saveOrUpdate(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        System.out.printf("Runtime catalog callback items=%d, SQL reads=2, property update batches=1, ES merge bulks=1, ES per-id reads=0%n", size);
    }

    @Test
    void rejectsTheEntireMixedOrInvalidBatchBeforePropertyWrites() {
        var fixture = new Fixture();
        fixture.catalog("validation-own", TENANT, 0, 17);
        fixture.catalog("validation-foreign", "validation-other", 0, 18);
        fixture.catalog("validation-deleted", TENANT, 1, 19);
        for (String invalid : List.of("validation-foreign", "validation-deleted", "validation-missing")) {
            assertThatThrownBy(() -> fixture.batch(List.of("validation-own", invalid), Map.of("accessTaskId", "new")))
                    .hasMessageContaining("目录不存在");
            assertThat(fixture.readSql()).hasSize(1);
        }
        assertThatThrownBy(() -> fixture.batch(List.of("validation-own", "../invalid"), Map.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> fixture.batch(IntStream.range(0, 101).mapToObj(i -> "validation-" + i).toList(), Map.of()))
                .hasMessageContaining("100");
        assertThat(fixture.bulkCalls).isZero();
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM da_prop_t", Integer.class)).isZero();
        verify(fixture.props, never()).saveBatch(anyCollection());
        verify(fixture.props, never()).updateBatchById(anyCollection());
        verifyNoInteractions(fixture.es);
    }

    @Test
    void singletonBatchCallbackUsesTheDatabaseZeroAndMaximumWithoutReadingAnIndexDocument() {
        var fixture = new Fixture();
        fixture.catalog("validation-zero", TENANT, 0, 0);
        fixture.catalog("validation-max", TENANT, 0, Long.MAX_VALUE);
        for (var expected : Map.of("validation-zero", 0L, "validation-max", Long.MAX_VALUE).entrySet()) {
            fixture.batch(List.of(expected.getKey()), Map.of("viewCount", "1", "marketViewCount_num", "1", "accessTaskId", "task"));
            assertThat(fixture.readSql()).hasSize(2);
            assertThat(fixture.bulkIds).containsExactly(expected.getKey());
            assertThat(fixture.bulkCounts).hasSize(1);
            assertThat(((Number) fixture.bulkCounts.getFirst()).longValue()).isEqualTo(expected.getValue());
            assertThat(fixture.bulkDocs).singleElement().satisfies(document -> {
                assertThat(document).containsEntry("accessTaskId", "task");
                assertThat(((Number) document.get("viewCount")).longValue()).isEqualTo(expected.getValue());
            });
            assertThat(fixture.raw.queryForObject("SELECT market_view_count FROM da_catalog_t WHERE tid=?", Long.class, expected.getKey()))
                    .isEqualTo(expected.getValue());
        }
        verify(fixture.es, never()).getDocument(anyString(), anyString());
    }

    @Test
    void queryUsesTheActualCatalogAndItsPropertiesWithoutTheRetiredAssetTable() {
        var fixture = new Fixture();
        fixture.catalog("validation-own", TENANT, 0, 41);
        fixture.property("validation-own", TENANT, "accessTaskId", "task", 0);
        fixture.property("validation-own", TENANT, "viewCount", "99999", 0);
        fixture.property("validation-own", "validation-other", "catalogName", "foreign", 0);
        fixture.property("validation-own", TENANT, "old", "deleted", 1);
        fixture.property("validation-own", TENANT, null, "unnamed", 0);
        when(fixture.es.getDocument("dataassets", "validation-own")).thenReturn(Map.of("viewCount", 99999));

        var result = fixture.query("validation-own");

        assertThat(result).containsEntry("catalogName", "验证目录").containsEntry("accessTaskId", "task")
                .containsEntry("tenantId", TENANT).containsEntry("assetType", "catalog");
        assertThat(((Number) result.get("viewCount")).longValue()).isEqualTo(41);
        assertThat(result).doesNotContainKey("marketViewCount").doesNotContainValue("foreign").doesNotContainValue("deleted");
        assertThat(fixture.readSql()).hasSize(2);
        verify(fixture.es, never()).getDocument(anyString(), anyString());
    }

    @Test
    void deletedAndOrphanCatalogsCannotBeResurrected() {
        var fixture = new Fixture();
        fixture.catalog("validation-deleted", TENANT, 1, 5);
        fixture.catalog("validation-foreign", "validation-other", 0, 6);
        fixture.catalog("validation-null-state", TENANT, 0, 7);
        fixture.raw.update("UPDATE da_catalog_t SET is_del=NULL WHERE tid='validation-null-state'");
        when(fixture.es.getDocument("dataassets", "validation-orphan")).thenReturn(Map.of("assetType", "catalog", "viewCount", 99));
        when(fixture.es.getDocument("dataassets", "validation-foreign")).thenReturn(Map.of("assetType", "catalog", "viewCount", 99));
        when(fixture.es.getDocument("dataassets", "validation-snake-orphan"))
                .thenReturn(Map.of("asset_type", "catalog", "viewCount", 99));
        assertThat(fixture.query("validation-deleted")).isEmpty();
        assertThat(fixture.query("validation-null-state")).isEmpty();
        assertThat(fixture.query("validation-orphan")).isEmpty();
        assertThat(fixture.query("validation-foreign")).isEmpty();
        assertThat(fixture.query("validation-snake-orphan")).isEmpty();
        verify(fixture.es).getDocument("dataassets", "validation-orphan");
        verify(fixture.es).getDocument("dataassets", "validation-foreign");
        verify(fixture.es).getDocument("dataassets", "validation-snake-orphan");
        verifyNoMoreInteractions(fixture.es);
    }

    @Test
    void missingCatalogUsesOnlyAnAllowedNonCatalogProjectionAndDoesNotWrite() {
        var fixture = new Fixture();
        Map<String, Object> database = Map.of("tid", "validation-db", "assetType", "db", "viewCount", 17);
        Map<String, Object> ownTable = Map.of("tid", "validation-table", "asset_type", "table", "tenant_id", TENANT);
        when(fixture.es.getDocument("dataassets", "validation-db")).thenReturn(database);
        when(fixture.es.getDocument("dataassets", "validation-table")).thenReturn(ownTable);
        when(fixture.es.getDocument("dataassets", "validation-foreign-db")).thenReturn(
                Map.of("assetType", "db", "tenantId", "validation-other"));
        when(fixture.es.getDocument("dataassets", "validation-foreign-table")).thenReturn(
                Map.of("asset_type", "table", "tenant_id", "validation-other"));

        var expected = Map.of("validation-db", database, "validation-table", ownTable,
                "validation-missing", Map.<String, Object>of(), "validation-foreign-db", Map.<String, Object>of(),
                "validation-foreign-table", Map.<String, Object>of());
        for (var entry : expected.entrySet()) {
            assertThat(fixture.query(entry.getKey())).isEqualTo(entry.getValue());
            assertThat(fixture.readSql()).singleElement().satisfies(statement -> assertThat(statement).contains("da_catalog_t"));
            verify(fixture.es).getDocument("dataassets", entry.getKey());
        }
        verifyNoMoreInteractions(fixture.es);
        verifyNoInteractions(fixture.props);
    }

    @Test
    void failedIndexMergeRollsBackPropertiesAndRetryDoesNotTouchTheCounter() {
        var fixture = new Fixture();
        fixture.catalog("validation-own", TENANT, 0, 41);
        fixture.failIndex = true;
        assertThatThrownBy(() -> fixture.batch(List.of("validation-own"), Map.of("accessTaskId", "task")))
                .hasMessageContaining("validation index unavailable");
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM da_prop_t", Integer.class)).isZero();
        assertThat(fixture.raw.queryForObject("SELECT market_view_count FROM da_catalog_t", Long.class)).isEqualTo(41);
        fixture.failIndex = false;
        fixture.batch(List.of("validation-own"), Map.of("accessTaskId", "task"));
        fixture.batch(List.of("validation-own"), Map.of("accessTaskId", "task"));
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM da_prop_t", Integer.class)).isEqualTo(1);
        assertThat(fixture.raw.queryForObject("SELECT market_view_count FROM da_catalog_t", Long.class)).isEqualTo(41);
        assertThat(fixture.propertyInsertBatches).isEqualTo(2); // failed transaction then successful retry; unchanged replay writes none.
        assertThat(fixture.propertyUpdateBatches).isZero();
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static class Fixture {
        final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:runtime_counter_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        final JdbcTemplate raw = new JdbcTemplate(source);
        final List<String> sql = new ArrayList<>();
        final IDataPropTService props = mock(IDataPropTService.class);
        final EsCommonService es = mock(EsCommonService.class);
        final DataAssetRuntimeAdapter adapter;
        final TransactionTemplate transaction;
        int bulkCalls;
        List<String> bulkIds;
        List<Map<String, Object>> bulkDocs;
        List<?> bulkCounts;
        boolean failIndex;
        int propertyInsertBatches;
        int propertyUpdateBatches;
        int propertySequence;

        Fixture() {
            var definitions = new ArrayList<String>();
            for (String field : List.of("tid", "tenant_id", "revision", "created_by", "created_time", "updated_by", "updated_time",
                    "is_del", "reg_time", "asset_status", "flow_status", "catalog_name", "catalog_name_en", "asset_desc", "app_id",
                    "app_name", "db_id", "db_name", "source_table_id", "source_table_name", "source_db_id", "source_db_name", "target_table_id",
                    "target_table_name", "target_db_id", "target_db_type", "org_id", "manage_unit", "business_type", "data_source_type",
                    "timestamp_field", "dictionary_table_flag", "resource_revision_no", "market_view_count")) {
                String type = Set.of("is_del", "asset_status", "flow_status", "resource_revision_no").contains(field) ? " INT DEFAULT 0"
                        : field.equals("market_view_count") ? " BIGINT DEFAULT 0" : field.endsWith("_time") ? " TIMESTAMP" : " VARCHAR(255)";
                definitions.add(field + type);
            }
            raw.execute("CREATE TABLE da_catalog_t(" + String.join(",", definitions) + ",PRIMARY KEY(tid))");
            raw.execute("""
                    CREATE TABLE da_prop_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,parent_id VARCHAR,data_type VARCHAR,
                      prop_name VARCHAR,prop_type VARCHAR,prop_value VARCHAR,is_del INT,created_time TIMESTAMP,updated_time TIMESTAMP)
                    """);
            DataSource counted = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(source, method, arguments);
                        if (result instanceof Connection connection) return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (connectionProxy, operation, parameters) -> {
                                    if (operation.getName().equals("prepareStatement")) sql.add((String) parameters[0]);
                                    if (operation.getName().equals("getMetaData")) {
                                        var metadata = connection.getMetaData();
                                        return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                                                new Class<?>[] {DatabaseMetaData.class}, (metadataProxy, info, values) ->
                                                        info.getName().equals("getURL") ? "jdbc:mysql://runtime-counter-validation"
                                                                : invoke(metadata, info, values));
                                    }
                                    return invoke(connection, operation, parameters);
                                });
                        return result;
                    });
            var jdbc = new JdbcTemplate(counted);
            transaction = new TransactionTemplate(new DataSourceTransactionManager(counted));
            adapter = new DataAssetRuntimeAdapter(es, props, new StructuredTableReadMagicModule(jdbc));
            doAnswer(invocation -> {
                propertyInsertBatches++;
                Collection<DataPropT> rows = invocation.getArgument(0);
                jdbc.batchUpdate("INSERT INTO da_prop_t VALUES (?,?,?,?,?,?,?,?,?,?)", rows.stream().map(p -> new Object[] {
                        p.getTid(), p.getTenantId(), p.getParentId(), p.getDataType(), p.getPropName(), p.getPropType(),
                        p.getPropValue(), p.getIsDel(), p.getCreatedTime(), p.getUpdatedTime()}).toList());
                return true;
            }).when(props).saveBatch(anyCollection());
            doAnswer(invocation -> {
                propertyUpdateBatches++;
                Collection<DataPropT> rows = invocation.getArgument(0);
                jdbc.batchUpdate("UPDATE da_prop_t SET prop_value=?,prop_type=?,updated_time=? WHERE tid=? AND tenant_id=?",
                        rows.stream().map(p -> new Object[] {p.getPropValue(), p.getPropType(), p.getUpdatedTime(), p.getTid(), p.getTenantId()}).toList());
                return true;
            }).when(props).updateBatchById(anyCollection());
            when(es.bulkMergeCatalogProjections(anyString(), anyList(), anyList(), anyList())).thenAnswer(invocation -> {
                bulkCalls++;
                bulkIds = invocation.getArgument(1);
                bulkDocs = invocation.getArgument(2);
                bulkCounts = invocation.getArgument(3);
                if (failIndex) throw new IllegalStateException("validation index unavailable");
                return bulkIds;
            });
        }

        void catalog(String id, String tenant, int deleted, long count) {
            raw.update("INSERT INTO da_catalog_t(tid,tenant_id,is_del,market_view_count,catalog_name) VALUES (?,?,?,?,?)",
                    id, tenant, deleted, count, "验证目录");
        }

        void property(String owner, String tenant, String name, String value, int deleted) {
            raw.update("INSERT INTO da_prop_t VALUES (?, ?,?,'catalog',?,'0',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "validation-prop-" + propertySequence++, tenant, owner, name, value, deleted);
        }

        List<String> readSql() { return sql.stream().filter(value -> value.stripLeading().toUpperCase().startsWith("SELECT")).toList(); }

        void batch(List<String> ids, Map<String, Object> values) {
            sql.clear();
            TenantContext.run(TENANT, () -> transaction.executeWithoutResult(status -> adapter.propBatchSaveOrUpdate(ids, values)));
        }

        Map<String, Object> query(String id) {
            sql.clear();
            return TenantContext.call(TENANT, () -> adapter.queryById(id));
        }
    }
}
