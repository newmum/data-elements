package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MetadataImportAttachmentMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void importsAttachmentPoliciesInFixedReadsAndRejectsForeignSources(int count) throws Exception {
        var fixture = fixture(count);
        var payload = input(count, 20_971_520, List.of("jpg", "pdf"));
        assertInstanceOf(Map.class, execute(fixture, payload));
        assertEquals(4, fixture.reads.get());
        var governance = JSONUtil.parseObj(fixture.jdbc.queryForObject(
                "SELECT field_governance_config FROM db_table_t WHERE tid='table_0'", String.class));
        var attachment = governance.getJSONArray("attachmentFields").getJSONObject(0);
        assertEquals("file_id", attachment.getStr("columnName"));
        assertEquals(20_971_520L, attachment.getLong("maxFileBytes"));
        assertEquals(List.of("jpg", "pdf"), attachment.getJSONArray("allowedExtensions").toList(String.class));
        assertEquals(count, fixture.jdbc.queryForObject(
                "SELECT COUNT(*) FROM db_table_column_t WHERE column_name='file_id' AND is_del=0", Integer.class));

        fixture.scope.denied = true;
        payload.put("dryRun", true);
        assertInstanceOf(Map.class, execute(fixture, payload));
        assertEquals(0, fixture.writes.get());
        payload.remove("dryRun");
        payload.put("dbId", "foreign");
        assertInstanceOf(ExitValue.class, execute(fixture, payload));
        assertEquals(0, fixture.writes.get());
    }

    @Test
    void rejectsInvalidAttachmentPolicyBeforeWriting() throws Exception {
        var fixture = fixture(1);
        var invalid = input(1, 0, List.of("exe"));
        invalid.put("dryRun", true);
        assertInstanceOf(ExitValue.class, execute(fixture, invalid));
        assertEquals(0, fixture.writes.get());
    }

    @Test
    void laterGovernanceSaveKeepsOnlyPreviouslyValidatedAttachmentPolicies() throws Exception {
        var fixture = new TableDetailFieldSaveMagicTest.Fixture(1);
        fixture.governanceOverrides.put("attachmentFields", List.of(Map.of("columnName", "field_0", "maxFileBytes", 20_971_520)));
        assertInstanceOf(Map.class, fixture.save("table", false));
        var governance = JSONUtil.parseObj(fixture.jdbc.queryForObject(
                "SELECT field_governance_config FROM db_table_t WHERE tid='table'", String.class));
        assertEquals(1_024L, governance.getJSONArray("attachmentFields").getJSONObject(0).getLong("maxFileBytes"));

        fixture.jdbc.update("UPDATE db_table_t SET field_governance_config='{}' WHERE tid='table'");
        assertInstanceOf(Map.class, fixture.save("table", false));
        String saved = fixture.jdbc.queryForObject("SELECT field_governance_config FROM db_table_t WHERE tid='table'", String.class);
        assertFalse(JSONUtil.parseObj(saved).containsKey("attachmentFields"));
    }

    private static PushRegistrySyncMagicTest.Fixture fixture(int count) throws Exception {
        var fixture = new PushRegistrySyncMagicTest.Fixture(count);
        fixture.jdbc.execute("""
                ALTER TABLE db_table_t ADD (table_name_en VARCHAR, business_type VARCHAR, business_type_reason VARCHAR,
                    timestamp_field VARCHAR, annotated INT, record_count BIGINT, field_count INT, storage_size VARCHAR,
                    total_size_formatted VARCHAR, total_size_bytes BIGINT, org_id VARCHAR, org_path VARCHAR,
                    data_source_type VARCHAR, source_table_id VARCHAR, source_table_name VARCHAR, manage_unit VARCHAR,
                    asset_desc VARCHAR, flow_status INT, flow_order_id VARCHAR, updated_time TIMESTAMP, created_time TIMESTAMP,
                    reg_time TIMESTAMP)
                """);
        fixture.jdbc.execute("""
                ALTER TABLE db_table_column_t ADD (`length` BIGINT, precision_length INT, scale INT, default_value VARCHAR,
                    auto_increment INT, is_unique INT, indexed INT, data_standard_id VARCHAR, is_dict INT,
                    dict_name VARCHAR, updated_time TIMESTAMP, created_time TIMESTAMP)
                """);
        fixture.jdbc.execute("ALTER TABLE db_datasource_t ADD (table_num INT, updated_time TIMESTAMP)");
        return fixture;
    }

    private static Map<String, Object> input(int count, long maxBytes, List<String> extensions) {
        var tables = new ArrayList<Map<String, Object>>();
        var fields = new LinkedHashMap<String, Object>();
        for (int index = 0; index < count; index++) {
            String name = "table_" + index;
            tables.add(Map.of("tableName", name, "tableComment", "验证业务表", "tableType", "数据表", "businessType", "业务表"));
            var attachment = Map.of("enabled", true, "maxFileBytes", maxBytes, "allowedExtensions", extensions);
            fields.put(name, List.of(Map.of("columnName", "file_id", "columnComment", "附件编号", "columnType", "varchar",
                    "columnLength", 64, "primaryKey", false, "nullable", true, "isTimestamp", false, "attachment", attachment)));
        }
        return new LinkedHashMap<>(Map.of("dbId", "source", "dataSourceType", "receive", "tables", tables, "fieldMap", fields));
    }

    private static Object execute(PushRegistrySyncMagicTest.Fixture fixture, Map<String, Object> input) throws Exception {
        String script = CanonicalMagicSources.byId("metadata_import_snapshot_01")
                .replaceAll("(?m)^import (tenantRuntime|metadataAsset|jsons|log|.* as esCommonService)\\R", "");
        fixture.reads.set(0);
        fixture.writes.set(0);
        var context = Map.of("body", input, "db", fixture.db, "tenantRuntime", fixture.scope,
                "dataScope", fixture.scope, "jsons", new TableDetailFieldSaveMagicTest.Jsons(),
                "metadataAsset", new AssetStatus(), "esCommonService", new Projection(),
                "log", LoggerFactory.getLogger(MetadataImportAttachmentMagicTest.class));
        return MagicScript.create(script, null).execute(new MagicScriptContext(context));
    }

    public static final class AssetStatus {
        public void refreshRegistrationStatus(String sourceId, boolean finish) {
            assertEquals("source", sourceId);
            assertFalse(finish);
        }
    }

    public static final class Projection {
        public void saveOrUpdate(String index, String id, String value, boolean refresh) {
            assertEquals("dataassets", index);
            assertTrue(id.startsWith("table_"));
            assertFalse(refresh);
        }
    }
}
