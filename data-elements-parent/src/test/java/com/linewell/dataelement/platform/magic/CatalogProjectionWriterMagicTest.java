package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Executes changed projection boundaries without touching a live index or business row. */
class CatalogProjectionWriterMagicTest {
    @Test
    void allTouchedCanonicalMagicResourcesCompile() throws IOException {
        for (String id : List.of("datapd_asset_refresh_01", "datapd_assets_batch_refresh_01",
                "datapd_table_catalog_save_01", "datapd_registered_table_catalog_projection_01",
                "084a92c02bd05f8bb8507cc4c4660e40", "0454d01c38ab5d66aafdaf02d9b547d6",
                "e3320ec899a24426adf5f77e2dab36f9", "f12fde8d078541abb7083a4cfc6fb3b4",
                "64c0bb65fad54c538d5b80423623cbf7", "bc7ab46f70354dab95ca7deb785d72fd")) {
            MagicScript.create(CanonicalMagicSources.byId(id), null).compile();
        }
    }

    @Test
    void singleRefreshUsesCatalogMainTableWithoutCompatibilityView() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-catalog", "validation-tenant", 41);
        String source = CanonicalMagicSources.byId("datapd_asset_refresh_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\R", "");
        String delegate = """
                var batchRefresh = () => {
                    if (body.assetType[0] != 'catalog' || body.tids[0] != 'validation-catalog' || body.purgeStale != false) {
                        throw new IllegalStateException('Wrong targeted rebuild contract')
                    }
                    return {targeted: true}
                }
                """;

        var result = assertInstanceOf(Map.class, fixture.execute(delegate + source,
                Map.of("body", Map.of("tid", "validation-catalog"))));

        assertThat(result).containsEntry("assetType", "catalog").containsEntry("refreshed", true);
        assertThat(assertInstanceOf(Map.class, result.get("result"))).containsEntry("targeted", true);
        assertThat(fixture.index.deleted).isEmpty();
    }

    @Test
    void singleRefreshCannotResolveAForeignCatalogIntoTheCurrentTenant() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-foreign", "validation-other-tenant", 99);
        String source = CanonicalMagicSources.byId("datapd_asset_refresh_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\R", "");
        var result = assertInstanceOf(Map.class, fixture.execute(source,
                Map.of("body", Map.of("tid", "validation-foreign"))));
        assertThat(result).containsEntry("removed", true).containsEntry("refreshed", false);
        assertThat(fixture.index.deleted).containsExactly("validation-foreign");
    }

    @Test
    void catalogSaveReadsItsCounterAfterPropertiesAndCallsOnlyMonotonicMerge() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("validation-catalog", "validation-tenant", 41);
        fixture.jdbc.update("INSERT INTO da_prop_t VALUES ('validation-catalog','validation-tenant',0,'viewCount','999')");
        String canonical = CanonicalMagicSources.byId("datapd_table_catalog_save_01");
        String closures = canonical.substring(canonical.indexOf("var buildAssetResultData ="),
                canonical.indexOf("var itemValue ="));
        String helpers = """
                var tenantId = 'validation-tenant'
                var blank = value => value == null || (value + '').trim() == ''
                var rowValue = (row, keys) => {
                    if (row == null) { return null }
                    for (key in keys) { if (row[key] != null) { return row[key] } }
                    return null
                }
                """;
        var result = assertInstanceOf(Map.class, fixture.execute(helpers + closures + """
                var document = buildAssetResultData('validation-catalog')
                saveToEs('validation-catalog', document)
                return document
                """, Map.of()));

        assertThat(result).containsEntry("viewCount", 41L);
        assertThat(fixture.index.incoming).isEqualTo(41L);
        assertThat(fixture.index.merges).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT market_view_count FROM da_catalog_t WHERE tid='validation-catalog'", Long.class))
                .isEqualTo(41L);
    }

    public static class Scope {
        public String id() { return "validation-tenant"; }
    }

    public static class Index {
        final List<String> deleted = new ArrayList<>();
        int merges;
        Object incoming;
        public boolean deleteById(String index, String id) { deleted.add(id); return true; }
        public String mergeCatalogProjection(String index, String id, String content, Object value) {
            assertThat(index).isEqualTo("dataassets");
            assertThat(id).isEqualTo("validation-catalog");
            assertThat(cn.hutool.json.JSONUtil.parseObj(content).getLong("viewCount")).isEqualTo(41L);
            merges++;
            incoming = value;
            return id;
        }
    }

    public static class Logger {
        public void warn(String template, Object... values) { }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Index index = new Index();
        Fixture() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:catalog_writers_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            for (String table : List.of("sym_application_t", "db_datasource_t", "db_table_t")) {
                jdbc.execute("CREATE TABLE " + table + " (tid VARCHAR(64),tenant_id VARCHAR(64),is_del INT)");
            }
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR(64),tenant_id VARCHAR(64),is_del INT,
                        asset_status INT DEFAULT 2,flow_status INT DEFAULT 2,market_view_count BIGINT DEFAULT 0)
                    """);
            jdbc.execute("CREATE TABLE da_prop_t(parent_id VARCHAR(64),tenant_id VARCHAR(64),is_del INT,prop_name VARCHAR(64),prop_value VARCHAR(255))");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var mapper = new ColumnMapperAdapter();
            mapper.add(new CamelColumnMapperProvider());
            mapper.setDefault("camel");
            db.setColumnMapperProvider(mapper);
            db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }
        void catalog(String id, String tenant, long value) {
            jdbc.update("INSERT INTO da_catalog_t(tid,tenant_id,is_del,market_view_count) VALUES(?,?,0,?)", id, tenant, value);
        }
        Object execute(String source, Map<String, Object> arguments) {
            var variables = new java.util.LinkedHashMap<String, Object>(arguments);
            variables.put("db", db);
            variables.put("tenantRuntime", new Scope());
            variables.put("esCommonService", index);
            variables.put("log", new Logger());
            variables.put("jsons", new com.linewell.dataelement.platform.magic.module.JsonModule());
            return MagicScript.create(source, null).execute(new MagicScriptContext(variables));
        }
    }
}
