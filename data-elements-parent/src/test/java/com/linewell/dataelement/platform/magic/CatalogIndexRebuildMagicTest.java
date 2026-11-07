package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Locks the deployed targeted-catalog rebuild contract without rebuilding a real index. */
class CatalogIndexRebuildMagicTest {
    private static final String RESOURCE_ID = "datapd_assets_batch_refresh_01";
    private static final String TENANT = "validation-tenant";
    private static final String PHYSICAL_INDEX = "validation-dataassets__tenant_" + TENANT;

    @Test
    void canonicalResourceCompilesWithItsRealImports() throws IOException {
        MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID), null).compile();
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void targetedCatalogsUseTwoTenantBoundReadsAndOneBulkWrite(int count) throws IOException {
        var fixture = new Fixture();
        var ids = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            String id = "validation-catalog-" + i;
            ids.add(id);
            fixture.catalog(id, TENANT, 0, "目录 " + i);
            fixture.property(id, TENANT, "catalogName", "动态目录 " + i, 0);
        }

        var result = assertInstanceOf(Map.class, fixture.run(ids));

        assertThat(result).containsEntry("targeted", true).containsEntry("refreshed", count)
                .containsEntry("requested", count).containsEntry("touched", count).containsEntry("removed", 0);
        assertThat(fixture.reads).hasSize(2).allSatisfy(sql -> assertThat(sql.toLowerCase())
                .contains("tenant_id", "is_del", " in "));
        assertThat(fixture.writes).isZero();
        assertThat(fixture.index.bulkCalls).isEqualTo(1);
        assertThat(fixture.index.ids).containsExactlyElementsOf(ids);
        assertThat(fixture.index.documents).hasSize(count).allSatisfy(doc -> assertThat(doc)
                .containsEntry("assetType", "catalog").containsEntry("tenantId", TENANT));
        verifyNoInteractions(fixture.operations, fixture.indexOperations);
        System.out.printf("Targeted catalog rebuild items=%d, API=1, SQL reads=%d, SQL writes=%d, ES bulk=%d, extra ES refresh=0 (bulk refresh policy)%n",
                count, fixture.reads.size(), fixture.writes, fixture.index.bulkCalls);
    }

    @Test
    void eachIdAndItsPropertiesStayInTheCurrentTenantAndActiveScope() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-own", TENANT, 0, "本租户目录");
        fixture.catalog("validation-foreign", "validation-other-tenant", 0, "其他租户目录");
        fixture.catalog("validation-deleted", TENANT, 1, "已删除目录");
        fixture.asset("validation-foreign-asset", "validation-other-tenant", 0, "catalog");
        fixture.asset("validation-deleted-asset", TENANT, 1, "catalog");
        fixture.asset("validation-wrong-type", TENANT, 0, "table");
        fixture.property("validation-own", "validation-other-tenant", "catalogName", "越权属性", 0);
        fixture.property("validation-own", TENANT, "assetDesc", "已删除属性", 1);

        var result = assertInstanceOf(Map.class, fixture.run(List.of("validation-own", "validation-foreign",
                "validation-deleted", "validation-foreign-asset", "validation-deleted-asset",
                "validation-wrong-type", "validation-missing")));

        assertThat(result).containsEntry("refreshed", 1).containsEntry("requested", 7);
        assertThat(fixture.reads).hasSize(2);
        assertThat(fixture.index.ids).containsExactly("validation-own");
        assertThat(fixture.index.documents).singleElement().satisfies(doc -> assertThat(doc)
                .containsEntry("catalogName", "本租户目录").doesNotContainValue("越权属性")
                .doesNotContainValue("已删除属性"));
        assertThat(fixture.writes).isZero();
    }

    @Test
    void mainTableFillsAttributesAndRejectsLegacyIndexOnlyCatalogs() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-main", TENANT, 0, "主表目录");
        fixture.jdbc.update("UPDATE da_catalog_t SET catalog_name_en='validation_main', asset_desc='主表说明', app_id='validation-app' WHERE tid='validation-main'");
        fixture.jdbc.update("UPDATE da_catalog_t SET market_view_count=41 WHERE tid='validation-main'");
        fixture.property("validation-main", TENANT, "catalogName", "属性目录", 0);
        fixture.property("validation-main", TENANT, "viewCount", "999", 0);
        fixture.property("validation-main", TENANT, "fieldGovernanceConfig", "不应索引的治理配置", 0);
        fixture.property("validation-main", TENANT, "tenantId", "validation-forged-tenant", 0);
        fixture.property("validation-main", TENANT, "tid", "validation-forged-id", 0);
        fixture.asset("validation-legacy", TENANT, 0, "catalog");
        fixture.property("validation-legacy", TENANT, "catalogName", "历史属性目录", 0);

        assertInstanceOf(Map.class, fixture.run(List.of("validation-main", "validation-legacy")));

        assertThat(fixture.index.documents).hasSize(1);
        assertThat(fixture.index.documents.get(0)).containsEntry("catalogName", "属性目录")
                .containsEntry("catalogNameEn", "validation_main").containsEntry("assetDesc", "主表说明")
                .containsEntry("appId", "validation-app").containsEntry("assetStatus", 2)
                .containsEntry("flowStatus", 2).containsEntry("tenantId", TENANT)
                .containsEntry("tid", "validation-main").containsEntry("id", "validation-main")
                .containsEntry("viewCount", 41L)
                .doesNotContainKey("fieldGovernanceConfig");
        assertThat(fixture.index.counters).containsExactly(41L);
    }

    @Test
    void duplicatesAndBlankIdsDoNotProduceDuplicateDocumentsAndTwoHundredIsAccepted() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-one", TENANT, 0, "单个目录");
        var result = assertInstanceOf(Map.class,
                fixture.run(List.of("validation-one", " validation-one ", "", " ", "validation-missing")));
        assertThat(result).containsEntry("requested", 5).containsEntry("refreshed", 1);
        assertThat(fixture.index.ids).containsExactly("validation-one");
        assertThat(fixture.reads).hasSize(2);

        var ids = new ArrayList<String>();
        for (int i = 0; i < 200; i++) ids.add("validation-missing-" + i);
        result = assertInstanceOf(Map.class, fixture.run(ids));
        assertThat(result).containsEntry("requested", 200).containsEntry("refreshed", 0);
        assertThat(fixture.reads).hasSize(2);
        assertThat(fixture.index.bulkCalls).isZero();
    }

    @Test
    void oversizedOrBlankOnlyTargetBatchesDoNotReadOrWriteTheIndex() throws IOException {
        var fixture = new Fixture();
        var oversized = new ArrayList<String>();
        for (int i = 0; i < 201; i++) oversized.add("validation-" + i);
        var exit = assertInstanceOf(ExitValue.class, fixture.run(oversized));
        assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(400);
        assertThat(fixture.reads).isEmpty();
        assertThat(fixture.index.bulkCalls).isZero();

        var empty = assertInstanceOf(Map.class, fixture.run(List.of("", " ")));
        assertThat(empty).containsEntry("targeted", true).containsEntry("refreshed", 0);
        assertThat(fixture.reads).isEmpty();
        assertThat(fixture.index.bulkCalls).isZero();
        verifyNoInteractions(fixture.operations, fixture.indexOperations);
        assertThat(fixture.writes).isZero();
    }

    @Test
    void failedBulkDoesNotRefreshOrReturnASuccessfulResult() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-one", TENANT, 0, "验证目录");
        fixture.index.fail = true;

        assertThatThrownBy(() -> fixture.run(List.of("validation-one")))
                .hasRootCauseMessage("validation bulk unavailable");

        assertThat(fixture.index.bulkCalls).isEqualTo(1);
        assertThat(fixture.writes).isZero();
        verifyNoInteractions(fixture.operations, fixture.indexOperations);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void fullCatalogRebuildUsesActualMainTableWithoutCompatibilityView(int count) throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("DROP TABLE da_asset_t");
        for (int i = 0; i < count; i++) {
            fixture.catalog("validation-full-" + i, TENANT, 0, "主表目录" + i);
        }
        fixture.catalog("validation-foreign", "validation-other-tenant", 0, "其他租户");
        fixture.property("validation-full-0", "validation-other-tenant", "catalogName", "越权名称", 0);
        fixture.property("validation-full-0", TENANT, "viewCount", "999", 0);
        fixture.jdbc.update("UPDATE da_catalog_t SET market_view_count=41 WHERE tid='validation-full-0'");

        var result = assertInstanceOf(Map.class, fixture.runBody(Map.of(
                "assetType", List.of("catalog"), "purgeStale", false)));

        assertThat(result).containsEntry("refreshed", count).containsEntry("removed", 0);
        assertThat(fixture.reads).hasSize(5).allSatisfy(sql -> assertThat(sql.toLowerCase()).contains("tenant_id"));
        assertThat(fixture.index.bulkCalls).isEqualTo(1);
        assertThat(fixture.index.documents).hasSize(count).allSatisfy(doc -> assertThat(doc)
                .containsEntry("assetType", "catalog").containsEntry("tenantId", TENANT)
                .doesNotContainValue("越权名称"));
        assertThat(fixture.index.documents.get(0)).containsEntry("catalogName", "主表目录0")
                .containsEntry("viewCount", 41L);
        assertThat(fixture.index.counters.get(0)).isEqualTo(41L);
        assertThat(fixture.writes).isZero();
    }

    public static class Scope {
        public String id() { return TENANT; }
    }

    public static class Jsons {
        public String toJSONStr(Object value) { return JSONUtil.toJsonStr(value); }
    }

    public static class Index {
        int bulkCalls;
        boolean fail;
        List<String> ids = List.of();
        List<Map<String, Object>> documents = List.of();
        List<Object> counters = List.of();

        public List<String> bulkReplaceCatalogProjections(String index, List<String> requestedIds, List<Map<String, Object>> docs, List<?> incoming) {
            assertThat(index).isEqualTo("dataassets");
            bulkCalls++;
            if (fail) throw new IllegalStateException("validation bulk unavailable");
            ids = List.copyOf(requestedIds);
            counters = new ArrayList<>(incoming);
            documents = new ArrayList<>();
            for (var doc : docs) documents.add(new LinkedHashMap<>(doc));
            return ids;
        }

        public String resolveTenantIndexName(String index) {
            assertThat(index).isEqualTo("dataassets");
            return PHYSICAL_INDEX;
        }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MagicScript script;
        final List<String> reads = new ArrayList<>();
        int writes;
        final Index index = new Index();
        final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        final IndexOperations indexOperations = mock(IndexOperations.class);

        Fixture() throws IOException {
            var source = new DriverManagerDataSource("jdbc:h2:mem:catalog_rebuild_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t (tid VARCHAR(64), tenant_id VARCHAR(64), is_del INT,
                      catalog_name VARCHAR(255), catalog_name_en VARCHAR(255), asset_desc VARCHAR(255),
                      app_id VARCHAR(64), asset_status INT, flow_status INT, market_view_count BIGINT DEFAULT 0)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_asset_t (tid VARCHAR(64), tenant_id VARCHAR(64), is_del INT,
                      asset_type VARCHAR(64), asset_status INT, flow_status INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t (parent_id VARCHAR(64), tenant_id VARCHAR(64), data_type VARCHAR(64),
                      prop_name VARCHAR(64), prop_value VARCHAR(255), is_del INT)
                    """);
            jdbc.execute("CREATE TABLE sym_application_t(tid VARCHAR(64),tenant_id VARCHAR(64),is_del INT)");
            jdbc.execute("CREATE TABLE db_datasource_t(tid VARCHAR(64),tenant_id VARCHAR(64),is_del INT)");
            jdbc.execute("CREATE TABLE db_table_t(tid VARCHAR(64),tenant_id VARCHAR(64),is_del INT)");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.add(new LowerColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql statement, RequestEntity request) {
                    if (statement.getSql().stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                        reads.add(statement.getSql());
                    } else {
                        writes++;
                    }
                }
            }));
            when(operations.indexOps(IndexCoordinates.of(PHYSICAL_INDEX))).thenReturn(indexOperations);
            String sourceScript = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|org\\.springframework\\.data\\.elasticsearch\\.core\\.mapping\\.IndexCoordinates)[^\\r\\n]*\\R", "");
            script = MagicScript.create(sourceScript, null);
        }

        void catalog(String id, String tenant, int deleted, String name) {
            jdbc.update("INSERT INTO da_catalog_t(tid,tenant_id,is_del,catalog_name,catalog_name_en,asset_desc,app_id,asset_status,flow_status) VALUES (?,?,?,?,NULL,NULL,NULL,2,2)", id, tenant, deleted, name);
        }

        void asset(String id, String tenant, int deleted, String type) {
            jdbc.update("INSERT INTO da_asset_t VALUES (?,?,?,?,1,0)", id, tenant, deleted, type);
        }

        void property(String owner, String tenant, String name, String value, int deleted) {
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,'catalog',?,?,?)", owner, tenant, name, value, deleted);
        }

        Object run(List<String> ids) {
            return runBody(Map.of("assetType", List.of("catalog"), "tids", ids, "purgeStale", false));
        }

        Object runBody(Map<String, Object> body) {
            reads.clear();
            writes = 0;
            index.bulkCalls = 0;
            index.ids = List.of();
            index.documents = List.of();
            return script.execute(new MagicScriptContext(Map.of("body",
                    body,
                    "db", db, "tenantRuntime", new Scope(), "esCommonService", index,
                    "esOperations", operations, "jsons", new Jsons())));
        }
    }
}
