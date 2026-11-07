package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketApplicationReadMagicTest {
    private static final String LIST = "c4348978915046c4af25470dd509c239";
    private static final String DETAIL = "db35431a36b34bc88b3b48914d7ba0ff";

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    public record Principal(String userId) {
        public Map<String, Object> principal() { return Map.of("userId", userId); }
    }

    @Test
    void draftArrayUsesTwoReadsAtBothPageSizesAndKeepsApplicantScope() throws Exception {
        var fixture = new Fixture();
        var small = fixture.drafts(Map.of("pageSize", 20, "type", "catalogSubscribe", "flowStatus", 0));
        assertEquals(20, small.size());
        assertEquals(2, fixture.reads);
        var large = fixture.drafts(Map.of("pageSize", 100, "type", "catalogSubscribe", "flowStatus", "0",
                "tenantId", "tenant-b", "applyUserId", "other"));
        assertEquals(100, large.size());
        assertEquals(2, fixture.reads);
        assertEquals(small, large.subList(0, 20));
        assertEquals("validation-000", large.getFirst().get("tid"));
        assertEquals("历史目录申请", large.getFirst().get("applyName"));
        var tail = fixture.drafts(Map.of("pageNum", 2, "type", "catalogSubscribe", "flowStatus", 0));
        assertEquals(List.of("validation-100", "validation-101", "validation-102", "validation-103",
                "validation-104"), tail.stream().map(row -> row.get("tid")).toList());
        assertEquals(2, fixture.reads);
        assertTrue(fixture.drafts(Map.of("pageNum", 3, "flowStatus", 0)).isEmpty());
    }

    @Test
    void detailLoadsOrderedCatalogsAndPropertiesWithFourReadsAtBothSizes() throws Exception {
        var fixture = new Fixture();
        for (int count : List.of(20, 100)) {
            String id = "detail-" + count;
            var detail = StructuredValues.objectMap(fixture.run(DETAIL, "tenant-a", "owner", Map.of("tid", id)));
            assertEquals(4, fixture.reads);
            assertEquals(id, detail.get("tid"));
            assertEquals("保留历史申请用途", detail.get("applyPurpose"));
            assertEquals("目录申请", detail.get("applyName"));
            var catalogs = StructuredValues.objectRows(detail.get("applyResource"));
            assertEquals(count, catalogs.size());
            assertEquals("c099", catalogs.getFirst().get("tid"));
            assertEquals(List.of("table", "api", "file", "dir"), catalogs.getFirst().get("resType"));
            assertEquals("api-validation", catalogs.getFirst().get("apiId"));
            assertEquals("catalog", catalogs.getFirst().get("assetType"));
        }
    }

    @Test
    void detailDoesNotReadForeignOrDeletedRowsAndExplainsUnavailableCatalogs() throws Exception {
        var fixture = new Fixture();
        for (String id : List.of("foreign-form", "other-form", "deleted-form")) {
            assertExit(404, fixture.run(DETAIL, "tenant-a", "owner", Map.of("tid", id, "tenantId", "tenant-b")));
            assertEquals(1, fixture.reads);
        }
        assertExit(404, fixture.run(DETAIL, "tenant-a", "other", Map.of("tid", "detail-20")));
        for (String id : List.of("with-foreign-catalog", "with-deleted-catalog")) {
            assertExit(409, fixture.run(DETAIL, "tenant-a", "owner", Map.of("tid", id)));
            assertEquals(3, fixture.reads);
        }
    }

    private static void assertExit(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:market_apply_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        private int reads;
        private final StructuredTableReadMagicModule read = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> reads++);

        Fixture() {
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_user_id VARCHAR,
                      apply_user_name VARCHAR,apply_org_id VARCHAR,created_by VARCHAR,apply_name VARCHAR,
                      type VARCHAR,catalog_ids VARCHAR,flow_status INT,is_del INT,created_time TIMESTAMP,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,catalog_name VARCHAR,
                      catalog_name_en VARCHAR,org_id VARCHAR,app_id VARCHAR,app_name VARCHAR,target_table_id VARCHAR)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,parent_id VARCHAR,data_type VARCHAR,
                      prop_name VARCHAR,prop_value CLOB,is_del INT,updated_time TIMESTAMP)
                    """);
            for (int i = 0; i < 105; i++) {
                insertForm("validation-%03d".formatted(i), "tenant-a", i == 0 ? "" : "owner",
                        "owner", i == 0 ? null : 0, "c099", 0);
            }
            jdbc.update("UPDATE data_apply_form_t SET apply_name=NULL WHERE tid='validation-000'");
            insertProp("legacy-name", "tenant-a", "validation-000", "apply_form", "applyName", "历史目录申请", 0);
            insertProp("other-name", "tenant-b", "validation-000", "apply_form", "applyName", "其他租户名称", 0);
            insertForm("other-form", "tenant-a", "other", "owner", 0, "c099", 0);
            insertForm("foreign-form", "tenant-b", "owner", "owner", 0, "c099", 0);
            insertForm("deleted-form", "tenant-a", "owner", "owner", 1, "c099", 0);
            for (int i = 0; i < 100; i++) {
                insertCatalog("c%03d".formatted(i), "tenant-a", 0);
            }
            insertCatalog("cforeign", "tenant-b", 0);
            insertCatalog("cdeleted", "tenant-a", 1);
            insertForm("with-foreign-catalog", "tenant-a", "owner", "owner", 0, "c099,cforeign", 2);
            insertForm("with-deleted-catalog", "tenant-a", "owner", "owner", 0, "c099,cdeleted", 2);
            for (int count : List.of(20, 100)) {
                String id = "detail-" + count;
                String catalogs = IntStream.range(0, count).mapToObj(index -> "c%03d".formatted(99 - index))
                        .collect(Collectors.joining(","));
                insertForm(id, "tenant-a", "owner", "owner", 0, catalogs, 2);
                insertProp("own-" + count, "tenant-a", id, "apply_form", "applyPurpose", "保留历史申请用途", 0);
                insertProp("replace-" + count, "tenant-a", id, "apply_form", "tid", "other-form", 0);
                insertProp("foreign-" + count, "tenant-b", id, "apply_form", "applyPurpose", "其他租户字段", 0);
                insertProp("removed-" + count, "tenant-a", id, "apply_form", "applyPurpose", "已删除字段", 1);
            }
            insertProp("api", "tenant-a", "c099", "catalog", "apiId", "api-validation", 0);
            insertProp("file", "tenant-a", "c099", "catalog", "fileId", "file-validation", 0);
            insertProp("dir", "tenant-a", "c099", "catalog", "dirId", "dir-validation", 0);
        }

        private void insertForm(String id, String tenant, String user, String creator, Integer deleted,
                String catalogs, int status) {
            jdbc.update("""
                    INSERT INTO data_apply_form_t VALUES (?,?,?,'申请用户','org-a',?,'目录申请','catalogSubscribe',
                      ?,?,?,CURRENT_TIMESTAMP,TIMESTAMP '2026-11-07 00:00:00')
                    """, id, tenant, user, creator, catalogs, status, deleted);
        }

        private void insertCatalog(String id, String tenant, int deleted) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,?,'验证目录','validation','org-a','app','验证应用','table')",
                    id, tenant, deleted);
        }

        private void insertProp(String id, String tenant, String parent, String type, String name, String value,
                int deleted) {
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", id, tenant, parent, type,
                    name, value, deleted);
        }

        List<Map<String, Object>> drafts(Map<String, Object> body) throws Exception {
            return StructuredValues.objectRows(run(LIST, "tenant-a", "owner", body));
        }

        Object run(String resourceId, String tenant, String user, Map<String, Object> body) throws Exception {
            String canonical = CanonicalMagicSources.byId(resourceId);
            MagicScript.create(canonical, null).compile();
            var script = MagicScript.create(canonical.replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
            reads = 0;
            return TenantContext.call(tenant, () -> script.execute(new MagicScriptContext(Map.of(
                    "body", body, "tableRead", read, "tenantRuntime", new Tenant(), "approvalRuntime", new Principal(user)))));
        }
    }
}
