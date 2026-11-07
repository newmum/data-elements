package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

class MaterializationLoadMagicTest {
    @Test
    void batchesTablesAndRequiredColumnsForTwentyAndOneHundredSources() throws IOException {
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture(size);
            if (size == 20) {
                fixture.jdbc.update("UPDATE db_table_t SET resource_plan_id=NULL WHERE tid='output' AND tenant_id='tenant-a'");
            }
            var reply = result(fixture.run(1));
            assertEquals("SUCCEEDED", reply.get("loadState"));
            assertEquals(3, fixture.reads);
            assertEquals(3, fixture.version());
            assertEquals(7, fixture.recordCount("tenant-a"));
            assertEquals(11, fixture.recordCount("tenant-b"));
            assertEquals(1, fixture.fusion.calls);
            var physicalRequest = fixture.fusion.request;
            assertEquals("registered-target", physicalRequest.get("targetSourceId"));
            assertEquals("output_table", physicalRequest.get("targetTable"));
            assertEquals(size, StructuredValues.objectRows(physicalRequest.get("sources")).size());
            assertEquals(size, StructuredValues.objectRows(physicalRequest.get("mappings")).size());
            var joins = StructuredValues.objectRows(physicalRequest.get("joins"));
            assertEquals(size - 1, joins.size());
            assertEquals("recency", joins.getFirst().get("orderName"));
            assertEquals(Map.of("leftName", "identifier", "rightName", "identifier"),
                    StructuredValues.objectRows(joins.getFirst().get("pairs")).getFirst());
            assertStatus(409, fixture.run(3));
            assertEquals(1, fixture.fusion.calls);
        }
    }

    @Test
    void loadsMoreThanOneColumnBatchWithoutAddingPerFieldReads() throws IOException {
        var fixture = new Fixture(2, 500);
        var reply = result(fixture.run(1));
        assertEquals("SUCCEEDED", reply.get("loadState"));
        assertEquals(4, fixture.reads);
        assertEquals(502, StructuredValues.objectRows(fixture.fusion.request.get("mappings")).size());
    }

    @Test
    void rejectsForeignDeletedOrChangedSourceMetadataBeforeClaimOrLoad() throws IOException {
        var fixture = new Fixture(2);
        assertRejected(fixture, "UPDATE db_table_t SET tenant_id='tenant-b' WHERE tid='table-0'",
                "UPDATE db_table_t SET tenant_id='tenant-a' WHERE tid='table-0'");
        assertRejected(fixture, "UPDATE db_table_t SET is_del=1 WHERE tid='table-0'",
                "UPDATE db_table_t SET is_del=0 WHERE tid='table-0'");
        assertRejected(fixture, "UPDATE db_table_t SET table_name='other' WHERE tid='table-0'",
                "UPDATE db_table_t SET table_name='source_0' WHERE tid='table-0'");
        assertRejected(fixture, "UPDATE db_table_t SET datasource_id='other' WHERE tid='table-0'",
                "UPDATE db_table_t SET datasource_id='registered-source' WHERE tid='table-0'");
        assertRejected(fixture, "UPDATE db_table_column_t SET tenant_id='tenant-b' WHERE tid='column-0'",
                "UPDATE db_table_column_t SET tenant_id='tenant-a' WHERE tid='column-0'");
        assertRejected(fixture, "UPDATE db_table_column_t SET is_del=1 WHERE tid='column-0'",
                "UPDATE db_table_column_t SET is_del=0 WHERE tid='column-0'");
        assertRejected(fixture, "UPDATE db_table_column_t SET table_id='table-1' WHERE tid='column-0'",
                "UPDATE db_table_column_t SET table_id='table-0' WHERE tid='column-0'");
        assertRejected(fixture, "UPDATE db_table_column_t SET column_name='renamed' WHERE tid='column-recency'",
                "UPDATE db_table_column_t SET column_name='recency' WHERE tid='column-recency'");
    }

    @Test
    void rejectsInvalidTargetsAndConcurrentClaimsAndGuardsTheCountWriteback() throws IOException {
        var fixture = new Fixture(2);
        assertRejected(fixture, "UPDATE db_table_t SET is_del=1 WHERE tid='output' AND tenant_id='tenant-a'",
                "UPDATE db_table_t SET is_del=0 WHERE tid='output' AND tenant_id='tenant-a'");
        assertRejected(fixture, "UPDATE db_table_t SET datasource_id='other' WHERE tid='output' AND tenant_id='tenant-a'",
                "UPDATE db_table_t SET datasource_id='registered-target' WHERE tid='output' AND tenant_id='tenant-a'");
        assertRejected(fixture, "UPDATE db_table_t SET table_name='renamed' WHERE tid='output' AND tenant_id='tenant-a'",
                "UPDATE db_table_t SET table_name='output_table' WHERE tid='output' AND tenant_id='tenant-a'");
        assertRejected(fixture, "UPDATE db_table_t SET resource_plan_id='other' WHERE tid='output' AND tenant_id='tenant-a'",
                "UPDATE db_table_t SET resource_plan_id='plan-a' WHERE tid='output' AND tenant_id='tenant-a'");

        fixture.beforeClaim = () -> fixture.jdbc.update("UPDATE res_materialization_plan SET revision_no=2 WHERE tid='plan-a'");
        assertStatus(409, fixture.run(1));
        assertEquals(2, fixture.version());
        assertEquals(0, fixture.fusion.calls);

        fixture.fusion.afterLoad = () -> fixture.jdbc.update("""
                UPDATE db_table_t SET datasource_id='repointed' WHERE tid='output' AND tenant_id='tenant-a'
                """);
        var reply = result(fixture.run(2));
        assertEquals("FAILED", reply.get("loadState"));
        assertTrue(reply.get("loadError").toString().contains("行数未回写"), () -> String.valueOf(reply.get("loadError")));
        assertEquals(1, fixture.fusion.calls);
        assertEquals(0, fixture.recordCount("tenant-a"));
        assertEquals(11, fixture.recordCount("tenant-b"));
    }

    private static void assertRejected(Fixture fixture, String mutate, String restore) {
        fixture.jdbc.update(mutate);
        assertStatus(409, fixture.run(1));
        assertEquals(1, fixture.version());
        assertEquals(0, fixture.fusion.calls);
        fixture.jdbc.update(restore);
    }

    private static void assertStatus(int code, Object value) {
        var exit = assertInstanceOf(ExitValue.class, value);
        assertEquals(code, ((Number) exit.getValues()[0]).intValue());
    }

    private static Map<String, Object> result(Object value) {
        return StructuredValues.objectMap(assertInstanceOf(Map.class, value));
    }

    public static class Session {
        public String id() {
            return TenantContext.requireTenantId();
        }

        public String getLoginIdAsString() {
            return "validation-actor";
        }
    }

    public static class Registration {
        public Map<String, Object> resolve(String tenantId, String sourceId) {
            assertEquals("tenant-a", tenantId);
            assertEquals("registered-target", sourceId);
            return Map.of("dbType", "mysql", "database", "validation");
        }
    }

    public static class Fusion {
        private int calls;
        private Map<String, Object> request;
        private Runnable afterLoad = () -> {};

        public Map<String, Object> loadModelFusion(Map<String, Object> value) {
            calls++;
            request = value;
            afterLoad.run();
            return Map.of("insertedRows", 7);
        }
    }

    private static final class Fixture {
        private final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:fusion_load_"
                + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        private final JdbcTemplate jdbc = new JdbcTemplate(source);
        private final JsonModule jsons = new JsonModule();
        private final Fusion fusion = new Fusion();
        private final Session session = new Session();
        private final SQLModule db;
        private final MagicScript command;
        private final MagicScript validate;
        private int reads;
        private Runnable beforeClaim = () -> {};

        Fixture(int sourceCount) throws IOException {
            this(sourceCount, 0);
        }

        Fixture(int sourceCount, int additionalColumns) throws IOException {
            jdbc.execute("""
                    CREATE TABLE res_materialization_plan (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, plan_json VARCHAR,
                      plan_status VARCHAR, revision_no INT, is_del INT, updated_by VARCHAR, updated_time TIMESTAMP,
                      created_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t (tid VARCHAR, tenant_id VARCHAR, datasource_id VARCHAR, table_name VARCHAR,
                      is_del INT, record_count INT, resource_plan_id VARCHAR, updated_by VARCHAR, updated_time TIMESTAMP,
                      PRIMARY KEY (tenant_id,tid))
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_column_t (tid VARCHAR, tenant_id VARCHAR, table_id VARCHAR, column_name VARCHAR,
                      is_del INT, PRIMARY KEY (tenant_id,tid))
                    """);
            jdbc.execute("""
                    CREATE TABLE res_resource_operation_log (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, actor_id VARCHAR,
                      action_name VARCHAR, object_id VARCHAR, object_name VARCHAR, detail_text VARCHAR, created_time TIMESTAMP)
                    """);
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del,record_count,resource_plan_id) VALUES
                    ('output','tenant-a','registered-target','output_table',0,0,'plan-a'),
                    ('output','tenant-b','registered-target','output_table',0,11,'plan-a')
                    """);
            seedPlan(sourceCount, additionalColumns);

            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql statement, RequestEntity request) {
                    if (statement.getSql().stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                        reads++;
                    } else {
                        Runnable hook = beforeClaim;
                        beforeClaim = () -> {};
                        hook.run();
                    }
                }
            }));
            command = loadScript("2ebdc2a6af4b5e2989cd8add98c849fa");
            validate = loadScript("779419e5edd654dd9f851a610cdb6033");
        }

        private static MagicScript loadScript(String id) throws IOException {
            String body = CanonicalMagicSources.byId(id);
            return MagicScript.create(body.replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\R", ""), null);
        }

        private void seedPlan(int sourceCount, int additionalColumns) throws IOException {
            List<Map<String, Object>> entities = new ArrayList<>();
            List<Map<String, Object>> mappings = new ArrayList<>();
            List<Map<String, Object>> joins = new ArrayList<>();
            List<Map<String, Object>> outputFields = new ArrayList<>();
            for (int index = 0; index < sourceCount; index++) {
                String table = "table-" + index;
                String source = "entity-" + index;
                String tableName = "source_" + index;
                String column = "column-" + index;
                jdbc.update("""
                        INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del)
                        VALUES (?,'tenant-a','registered-source',?,0)
                        """, table, tableName);
                jdbc.update("INSERT INTO db_table_column_t VALUES (?,'tenant-a',?,'identifier',0)", column, table);
                List<Map<String, Object>> fields = new ArrayList<>(List.of(field("identifier", "identifier")));
                var fieldMap = new LinkedHashMap<>(Map.of("identifier", column));
                if (index == 0 && additionalColumns > 0) {
                    var addedRows = new ArrayList<Object[]>();
                    for (int number = 0; number < additionalColumns; number++) {
                        String fieldName = "additional_" + number;
                        String originalColumnId = "column-extra-" + number;
                        fields.add(field(fieldName, fieldName));
                        fieldMap.put(fieldName, originalColumnId);
                        addedRows.add(new Object[]{originalColumnId, table, fieldName});
                        String outputField = "out_extra_" + number;
                        outputFields.add(field(outputField, outputField));
                        mappings.add(Map.of("sourceEntityId", source, "sourceFieldId", fieldName,
                                "targetFieldId", outputField, "transform", "DIRECT"));
                    }
                    jdbc.batchUpdate("INSERT INTO db_table_column_t VALUES (?,'tenant-a',?,?,0)", addedRows);
                }
                if (index == 1) {
                    jdbc.update("INSERT INTO db_table_column_t VALUES ('column-recency','tenant-a',?,'recency',0)", table);
                    fields.add(field("recency", "recency"));
                    fieldMap.put("recency", "column-recency");
                }
                entities.add(Map.of("id", source, "name", tableName, "role", "SOURCE", "fields", fields,
                        "origin", Map.of("entityId", table, "tableName", tableName, "sourceId", "registered-source", "fieldMap", fieldMap)));
                String outputField = "out_" + index;
                outputFields.add(field(outputField, outputField));
                mappings.add(Map.of("sourceEntityId", source, "sourceFieldId", "identifier", "targetFieldId", outputField,
                        "transform", "DIRECT"));
                if (index > 0) {
                    var join = new LinkedHashMap<String, Object>(Map.of("leftEntityId", "entity-0", "rightEntityId", source,
                            "joinType", "LEFT", "pairs", List.of(Map.of("leftFieldId", "identifier", "rightFieldId", "identifier")),
                            "rightCardinality", index == 1 ? "MANY" : "ONE", "dedupe", index == 1 ? "LATEST" : "NONE"));
                    if (index == 1) join.put("orderFieldId", "recency");
                    joins.add(join);
                }
            }
            entities.add(Map.of("id", "design-output", "name", "output_table", "role", "OUTPUT", "fields", outputFields));
            var revision = Map.of("entities", entities, "outputEntityId", "design-output",
                    "fusion", Map.of("baseEntityId", "entity-0", "joins", joins, "mappings", mappings));
            var item = Map.of("id", "item-a", "resourceId", "output", "targetName", "output_table", "state", "SUCCEEDED");
            var plan = Map.of("id", "plan-a", "name", "验证物化", "bindingId", "binding-a", "bindingHash", "validation-hash",
                    "revision", revision, "items", List.of(item));
            jdbc.update("""
                    INSERT INTO res_materialization_plan(tid,tenant_id,plan_json,plan_status,revision_no,is_del)
                    VALUES ('plan-a','tenant-a',?,'SUCCEEDED',1,0)
                    """, jsons.toJSONStr(plan));
        }

        private static Map<String, Object> field(String id, String name) {
            return Map.of("id", id, "name", name, "logicalType", "Int64");
        }

        Object run(int version) {
            reads = 0;
            var input = Map.of("kind", "materializations", "method", "POST", "id", "plan-a", "action", "load", "version", version,
                    "body", Map.of("confirmExecution", true));
            Function<Object[], Object> validateModel = args -> validate.execute(new MagicScriptContext(Map.of("input", args[0],
                    "tenantRuntime", session)));
            Function<Object[], Object> readPlanning = args -> Map.of("bindings", List.of(Map.of(
                    "id", "binding-a", "databaseId", "domain-a", "sourceId", "registered-target", "category", "RELATIONAL",
                    "state", "VALID", "checkedHash", "validation-hash", "currentHash", "validation-hash", "schema", "validation")),
                    "databases", List.of(Map.of("id", "domain-a", "state", "ACTIVE", "mode", "MANAGED")));
            var vars = Map.of("input", input, "db", db, "tenantRuntime", session, "StpUtil", session, "jsons", jsons,
                    "fusionJdbc", fusion, "connectionProperties", new Registration(), "readPlanning", readPlanning,
                    "validateModel", validateModel);
            return TenantContext.call("tenant-a", () -> command.execute(new MagicScriptContext(vars)));
        }

        int version() {
            return jdbc.queryForObject("SELECT revision_no FROM res_materialization_plan WHERE tid='plan-a'", Integer.class);
        }

        int recordCount(String tenant) {
            return jdbc.queryForObject("SELECT record_count FROM db_table_t WHERE tid='output' AND tenant_id=?", Integer.class, tenant);
        }
    }
}
