package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import com.linewell.dataelement.metautil.explorer.impl.HiveMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.OracleMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.OceanBaseMetadataExplorer;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the canonical DDL API's batch branch with a real tenant metadata query. */
class BatchCreateTableDdlMagicTest {
    private static final String SOURCE_ID = "8a56e7d664934c1499102cfa508cd58a";

    @Test
    void twentyTargetsUseOneScopedDatasourceRead() throws Exception {
        Fixture fixture = new Fixture(20);

        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, result.get("batchGenerate"));
        assertEquals(20, ((Collection<?>) result.get("results")).size());
        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(20, fixture.ddlGenerator.calls);
    }

    @Test
    void aDeniedDatasourceRejectsTheWholeTwentyTargetBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.scope.deniedId = "db-19";

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.ddlGenerator.calls);
    }

    @Test
    void aForeignTenantDatasourceRejectsTheWholeTwentyTargetBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.jdbc.update("update db_datasource_t set tenant_id='other' where tid='db-19'");

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.ddlGenerator.calls);
    }

    @Test
    void oneHundredTargetsAreRejectedBeforeDatabaseAccess() throws Exception {
        Fixture fixture = new Fixture(100);

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(0, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.ddlGenerator.calls);
    }

    @Test
    void oracleAndHiveUseTheSameConfirmedDefaultOrCustomTargetName() throws Exception {
        for (String mode : List.of("oracle", "oceanbaseoracle")) {
          for (String name : List.of("ODS_T_SJYCC_CKB", "ODS_CONFIRMED_CUSTOM")) {
            Fixture fixture = new Fixture(2);
            fixture.jdbc.update("update db_datasource_t set db_type=?,username='OB_OWNER' where tid='db-0'", mode);
            fixture.jdbc.update("update db_datasource_t set db_type='hive' where tid='db-1'");
            fixture.targetNames(name);

            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run());
            List<?> results = assertInstanceOf(List.class, response.get("results"));
            for (Object value : results) {
                Map<?, ?> result = assertInstanceOf(Map.class, value);
                assertEquals(name, result.get("requestedTableName"));
                assertEquals(name, result.get("tableName"));
                assertTrue(String.valueOf(result.get("ddl")).contains(name));
            }
            assertEquals(1, fixture.counter.reads);
            assertEquals(0, fixture.counter.writes);
          }
        }
    }

    @Test
    void qualifiedInputOrUnexpectedGeneratorRenameFailsDuringPreview() throws Exception {
        Fixture qualified = new Fixture(1);
        qualified.targetNames("ODS_TC_RKXT.T_SJYCC_CKB");
        assertInstanceOf(ExitValue.class, qualified.run());
        assertEquals(0, qualified.counter.writes);

        Fixture renamed = new Fixture(1);
        renamed.ddlGenerator.rename = true;
        assertInstanceOf(ExitValue.class, renamed.run());
        assertEquals(0, renamed.counter.writes);
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final QueryCounter counter = new QueryCounter();
        final ScopeRuntime scope = new ScopeRuntime();
        final DdlGenerator ddlGenerator = new DdlGenerator();
        final CanvasTableDdl ddlParser = new CanvasTableDdl();
        final List<Map<String, Object>> targets = new ArrayList<>();

        Fixture(int targetCount) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:ddl_batch_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64),tenant_id varchar(32),is_del int,
                        db_type varchar(32),db_name varchar(100),pool_cfg varchar(1000),username varchar(100))
                    """);
            for (int i = 0; i < targetCount; i++) {
                String dbId = "db-" + i;
                jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,db_type,db_name,pool_cfg) "
                        + "values(?,'police',0,'mysql',?,'{}')", dbId, "ods_" + i);
                targets.add(Map.of(
                        "propList", Map.of("dbId", dbId, "tableName", "ods_person_" + i,
                                "tableNameCn", "人员表"),
                        "tableItems", List.of(Map.of("columnName", "person_id", "columnComment", "人员标识",
                                "dataType", "varchar", "columnType", "varchar(32)", "length", 32,
                                "nullable", 0, "primaryKey", 1))));
            }
        }

        Object run() throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID)
                    .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|cn\\.hutool\\.json\\.JSONUtil|java\\.util\\.Map|java\\.util\\.List)[^\\r\\n]*\\r?\\n", "");
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("body", Map.of("batchGenerate", true, "canvas", true, "targets", targets));
            inputs.put("db", db);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("metadataExplorerService", ddlGenerator);
            inputs.put("canvasTableDdl", ddlParser);
            inputs.put("jsons", new JsonModule());
            counter.reads = 0;
            counter.writes = 0;
            return MagicScript.create(script, null)
                    .execute(new MagicScriptContext(inputs));
        }

        void targetNames(String name) {
            for (int index = 0; index < targets.size(); index++) {
                var target = new LinkedHashMap<>(targets.get(index));
                @SuppressWarnings("unchecked")
                var props = new LinkedHashMap<>((Map<String, Object>) target.get("propList"));
                props.put("tableName", name);
                target.put("propList", props);
                targets.set(index, target);
            }
        }
    }

    public static final class ScopeRuntime {
        String deniedId;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource,
                                                                Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
        }
    }

    public static final class DdlGenerator {
        int calls;
        boolean rename;
        public String generateCreateTableDdl(DataSourceConfig config, String tableName,
                                             List<ColumnInfo> columns, String tableComment) {
            calls++;
            assertEquals(1, columns.size());
            String name = rename ? "UNEXPECTED_TABLE" : tableName;
            return switch (config.getDatabaseType().getCode()) {
                case "oracle" -> new OracleMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                case "oceanbaseoracle" -> new OceanBaseMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                case "hive" -> new HiveMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                default -> new MySqlMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
            };
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int reads;
        int writes;
        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) { reads++; }
            else { writes++; }
        }
    }
}
