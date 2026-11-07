package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ModelCommandMagicTest {
    private static final String SCRIPT_ID = "ad740688aec155419e89ac237559c727";

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void saveClearsDescriptionAndReplacesOnlyTheCurrentTenantModelProjection(String mode) throws IOException {
        var fixture = new Fixture(mode);
        fixture.modelRow("tenant-a", 1, 0);
        fixture.modelRow("tenant-b", 1, 0);
        fixture.projectionRow("validation-old", "tenant-a", "validation-model", 0);
        fixture.projectionRow("validation-deleted", "tenant-a", "validation-model", 1);
        fixture.projectionRow("validation-foreign", "tenant-b", "validation-model", 0);
        fixture.projectionRow("validation-unrelated", "tenant-a", "validation-other-model", 0);

        var result = StructuredValues.objectMap(fixture.save(Map.of("name", "验证模型", "description", "")));
        assertEquals(2, result.get("version"));
        assertEquals("", fixture.jdbc.queryForObject(
                "SELECT model_description FROM res_logical_model WHERE tenant_id='tenant-a'", String.class));
        assertEquals("原说明", fixture.jdbc.queryForObject(
                "SELECT model_description FROM res_logical_model WHERE tenant_id='tenant-b'", String.class));
        assertEquals(2, fixture.version("tenant-a"));
        assertEquals(1, fixture.version("tenant-b"));
        assertEquals(List.of("validation-entity"), fixture.jdbc.queryForList("""
                SELECT entity_id FROM res_logical_model_field
                WHERE tenant_id='tenant-a' AND model_id='validation-model'
                """, String.class));
        assertEquals(2, fixture.count("res_logical_model_field",
                "tid IN ('validation-foreign','validation-unrelated')"));
        assertEquals(1, fixture.count("res_resource_operation_log", "tenant_id='tenant-a'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void lateModelVersionConflictOrDeletedRowLeavesAllProjectionAndAuditRowsUntouched(String mode) throws IOException {
        var stale = new Fixture(mode);
        stale.modelRow("tenant-a", 2, 0);
        stale.modelRow("tenant-b", 1, 0);
        stale.projectionRow("validation-old", "tenant-a", "validation-model", 0);
        assertEquals("409", code(stale.save(Map.of("description", "新说明"))));
        assertEquals(2, stale.version("tenant-a"));
        assertEquals(1, stale.version("tenant-b"));
        assertEquals(1, stale.count("res_logical_model_field", "tid='validation-old'"));
        assertEquals(0, stale.count("res_resource_operation_log", "1=1"));

        var deleted = new Fixture(mode);
        deleted.modelRow("tenant-a", 1, 1);
        deleted.projectionRow("validation-old", "tenant-a", "validation-model", 0);
        assertEquals("409", code(deleted.save(Map.of("description", "新说明"))));
        assertEquals(1, deleted.count("res_logical_model_field", "tid='validation-old'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void auditFailureRollsBackModelUpdateAndProjectionReplacement(String mode) throws IOException {
        var fixture = new Fixture(mode);
        fixture.modelRow("tenant-a", 1, 0);
        fixture.projectionRow("validation-old", "tenant-a", "validation-model", 0);
        fixture.failAudit = true;

        assertThrows(RuntimeException.class, () -> fixture.save(Map.of("description", "")));
        assertEquals(1, fixture.version("tenant-a"));
        assertEquals("原说明", fixture.jdbc.queryForObject(
                "SELECT model_description FROM res_logical_model WHERE tenant_id='tenant-a'", String.class));
        assertEquals(1, fixture.count("res_logical_model_field", "1=1"));
        assertEquals(1, fixture.count("res_logical_model_field", "tid='validation-old'"));
        assertEquals(0, fixture.count("res_resource_operation_log", "1=1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void itemsetSubmissionUsesTenantAndVersionAndRollsBackCandidatesOnLateConflict(String mode) throws IOException {
        var success = new Fixture(mode);
        success.itemsetRow("tenant-a", 1);
        success.itemsetRow("tenant-b", 1);
        var result = StructuredValues.objectMap(success.submit());
        assertEquals("SUBMITTED", result.get("state"));
        assertEquals(2, result.get("version"));
        assertEquals(1, success.count("da_metadata_t", "tenant_id='tenant-a' AND publish_status=0"));
        assertEquals(1, success.count("res_model_itemset", "tenant_id='tenant-a' AND revision_no=2 AND itemset_status='SUBMITTED'"));
        assertEquals(1, success.count("res_model_itemset", "tenant_id='tenant-b' AND revision_no=1 AND itemset_status='DRAFT'"));
        assertEquals(1, success.count("res_resource_operation_log", "tenant_id='tenant-a'"));

        var stale = new Fixture(mode);
        stale.itemsetRow("tenant-a", 2);
        stale.itemsetRow("tenant-b", 1);
        assertEquals("409", code(stale.submit()));
        assertTrue(stale.candidateInsertAttempts > 0, "version conflict occurred after the candidate write");
        assertEquals(0, stale.count("da_metadata_t", "1=1"));
        assertEquals(1, stale.count("res_model_itemset", "tenant_id='tenant-a' AND revision_no=2 AND itemset_status='DRAFT'"));
        assertEquals(1, stale.count("res_model_itemset", "tenant_id='tenant-b' AND revision_no=1 AND itemset_status='DRAFT'"));
        assertEquals(0, stale.count("res_resource_operation_log", "1=1"));
    }

    private static String code(Object result) {
        return String.valueOf(assertInstanceOf(ExitValue.class, result).getValues()[0]);
    }

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final MagicScript script;
        private boolean failAudit;
        private int candidateInsertAttempts;

        Fixture(String mode) throws IOException {
            var source = new DriverManagerDataSource("jdbc:h2:mem:model_command_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE res_logical_model (tid VARCHAR,tenant_id VARCHAR,is_del INT,revision_no INT,
                      model_name VARCHAR,model_description VARCHAR,design_json CLOB,model_status VARCHAR,
                      updated_by VARCHAR,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_logical_model_field (tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,entity_id VARCHAR,
                      design_field_id VARCHAR,field_code VARCHAR,field_name VARCHAR,data_type VARCHAR,data_length INT,
                      data_precision INT,numeric_scale INT,nullable_flag INT,primary_key_flag INT,standard_id VARCHAR,
                      standard_revision VARCHAR,field_description VARCHAR,sort_no INT,created_by VARCHAR,
                      created_time TIMESTAMP,updated_by VARCHAR,updated_time TIMESTAMP,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_model_itemset (tid VARCHAR,tenant_id VARCHAR,is_del INT,revision_no INT,
                      items_json CLOB,itemset_status VARCHAR,updated_time TIMESTAMP,updated_by VARCHAR)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_metadata_t (tid VARCHAR,tenant_id VARCHAR,meta_name VARCHAR,meta_code VARCHAR,
                      standard_encode VARCHAR,biz_def VARCHAR,field_type VARCHAR,field_length INT,numeric_precision INT,
                      numeric_scale INT,is_nullable INT,publish_status INT,version_no VARCHAR,created_by VARCHAR,
                      created_time TIMESTAMP,updated_by VARCHAR,updated_time TIMESTAMP,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_resource_operation_log (tid VARCHAR,tenant_id VARCHAR,actor_id VARCHAR,
                      action_name VARCHAR,object_id VARCHAR,object_name VARCHAR,detail_text VARCHAR,created_time TIMESTAMP)
                    """);

            var dataSources = new MagicDynamicDataSource();
            dataSources.setDefault(source);
            db = new SQLModule(dataSources);
            db.setDataSourceNode(dataSources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("model-command-validation"));
            var mappers = new ColumnMapperAdapter();
            mappers.add(new DefaultColumnMapperProvider());
            db.setColumnMapperProvider(mappers);
            db.setColumnMapRowMapper(mappers.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql boundSql, RequestEntity request) {
                    String sql = boundSql.getSql().stripLeading().toLowerCase(Locale.ROOT);
                    if (sql.startsWith("insert into da_metadata_t")) candidateInsertAttempts++;
                    if (failAudit && sql.startsWith("insert into res_resource_operation_log")) {
                        throw new IllegalStateException("validation audit unavailable");
                    }
                }
            }));
            String sourceText = CanonicalMagicSources.byId(SCRIPT_ID)
                    .replaceAll("(?m)^import (tenantRuntime|jsons|'@/[^\\r\\n]*)[^\\r\\n]*\\r?\\n", "")
                    .replace("StpUtil.getLoginIdAsString()", "'validation-actor'");
            script = MagicScript.create(sourceText, null);
        }

        void modelRow(String tenant, int version, int deleted) {
            jdbc.update("""
                    INSERT INTO res_logical_model(tid,tenant_id,is_del,revision_no,model_name,model_description,model_status)
                    VALUES('validation-model',?,?,?,'旧模型','原说明','ACTIVE')
                    """, tenant, deleted, version);
        }

        void itemsetRow(String tenant, int version) {
            jdbc.update("""
                    INSERT INTO res_model_itemset(tid,tenant_id,is_del,revision_no,items_json,itemset_status)
                    VALUES('validation-set',?,0,?,'[]','DRAFT')
                    """, tenant, version);
        }

        void projectionRow(String id, String tenant, String model, int deleted) {
            jdbc.update("""
                    INSERT INTO res_logical_model_field(tid,tenant_id,model_id,entity_id,is_del)
                    VALUES(?,?,?,'validation-old-entity',?)
                    """, id, tenant, model, deleted);
        }

        Object save(Map<String, Object> body) {
            return execute(Map.of("kind", "models", "id", "validation-model", "method", "PUT", "version", 1, "body", body));
        }

        Object submit() {
            return execute(Map.of("kind", "itemsets", "id", "validation-set", "method", "POST", "action", "submit", "version", 1));
        }

        int count(String table, String where) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        }

        int version(String tenant) {
            return jdbc.queryForObject("SELECT revision_no FROM res_logical_model WHERE tenant_id=?", Integer.class, tenant);
        }

        Object execute(Map<String, Object> input) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", "validation-model");
            model.put("version", 1);
            model.put("state", "ACTIVE");
            model.put("name", "旧模型");
            model.put("description", "原说明");
            model.put("relationships", new ArrayList<>());
            model.put("nodes", new ArrayList<>());
            model.put("entities", List.of(Map.of("id", "validation-entity", "keys", List.of(), "fields", List.of(
                    Map.of("id", "validation-field", "name", "validation_code", "businessName", "验证字段",
                            "comment", "验证说明", "logicalType", "String", "length", 24, "ordinal", 1, "nullable", "true")))));
            Map<String, Object> itemset = new LinkedHashMap<>(Map.of(
                    "id", "validation-set", "version", 1, "modelId", "validation-model", "state", "DRAFT",
                    "name", "验证数据项集", "items", List.of(Map.of("entityId", "validation-entity", "fieldId", "validation-field"))));
            Function<Object[], Object> readModels = args -> Map.of("models", List.of(model), "itemSets", List.of(itemset));
            Map<String, Object> variables = new HashMap<>();
            variables.put("input", input);
            variables.put("tenantRuntime", new Tenant());
            variables.put("jsons", new JsonModule());
            variables.put("db", db);
            variables.put("readModels", readModels);
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(variables)));
        }
    }
}
