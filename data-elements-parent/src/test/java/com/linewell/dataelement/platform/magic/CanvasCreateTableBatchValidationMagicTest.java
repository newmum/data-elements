package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** Runs the canonical createTable Magic source through its read-only batch validation path. */
class CanvasCreateTableBatchValidationMagicTest {
    private static final String SOURCE_ID = "a0b5bba38fbf428c8e3646864d83d56b";
    private SQLModule db;
    private QueryCounter counter;
    private Scope scope;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:ddl_validation_" + UUID.randomUUID()
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
        counter = new QueryCounter();
        db.setSqlInterceptors(List.of(counter));
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,db_type varchar(32),username varchar(64),pool_cfg varchar(512))");
        jdbc.update("insert into db_datasource_t values(?,?,?,?,?,?)", "target-db", "police", 0, "hive", null,
                "{\"database\":\"warehouse\",\"metadataAccessMode\":\"server-managed-mrs\"}");
        scope = new Scope();
    }

    @Test
    void twentyEditedDdlsUseOneTenantReadAndNoWrites() throws Exception {
        Map<?, ?> response = assertInstanceOf(Map.class, run(request(20)));
        assertEquals(true, response.get("batchValidateOnly"));
        assertEquals(false, response.get("sideEffectsApplied"));
        assertEquals(20, ((Collection<?>) response.get("results")).size());
        assertEquals(1, counter.queries);
        assertEquals(0, counter.writes);
    }

    @Test
    void hundredTargetsAreRejectedBeforeSql() throws Exception {
        assertInstanceOf(ExitValue.class, run(request(100)));
        assertEquals(0, counter.queries);
        assertEquals(0, counter.writes);
    }

    @Test
    void hiddenDatasourceIsRejectedBeforeDdlParsing() throws Exception {
        scope.deniedId = "target-db";
        assertInstanceOf(ExitValue.class, run(request(1)));
        assertEquals(1, counter.queries);
        assertEquals(0, counter.writes);
    }

    private Map<String, Object> request(int count) {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String table = "ods_target_" + i;
            targets.add(Map.of("dbId", "target-db", "tableName", table,
                    "ddl", "CREATE TABLE " + table + " (id STRING)"));
        }
        return Map.of("canvas", true, "validateOnly", true, "batchValidateOnly", true, "targets", targets);
    }

    private Object run(Map<String, Object> body) throws Exception {
        String script = CanonicalMagicSources.byId(SOURCE_ID)
                .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|java\\.util\\.(?:Map|List))[^\\n]*\\n", "");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("body", body);
        inputs.put("db", db);
        inputs.put("tenantRuntime", scope);
        inputs.put("dataScope", scope);
        inputs.put("poolJsons", new JsonFixture());
        inputs.put("canvasTableDdl", new CanvasTableDdl());
        return MagicScript.create(script, null).execute(new MagicScriptContext(inputs));
    }

    public static class Scope {
        String deniedId;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
        }
    }

    public static class JsonFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        public Map<String, Object> parse(String source) throws Exception {
            return mapper.readValue(source, Map.class);
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int queries;
        int writes;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) queries++;
            else writes++;
        }
    }
}
