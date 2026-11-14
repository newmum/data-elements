package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.ssssssss.script.runtime.ExitValue;

class ReportSubmitMagicTest {
    public static class Delivery {
        int calls;
        boolean fail;
        Runnable duringUpload = () -> {};

        public Map<String, Object> upload(String source, String table, MultipartFile file) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "外部投递不能持有业务事务");
            calls++;
            duringUpload.run();
            if (fail) {
                throw new IllegalStateException("transfer failed");
            }
            return Map.of("remoteFileName", "receipt.csv", "targetDatasourceName", "验证存储");
        }
    }

    static class Fixture {
        final ReportRegistrationMagicTest.Fixture base = new ReportRegistrationMagicTest.Fixture(1);
        final Delivery delivery = new Delivery();

        Fixture() {
            base.base.jdbc.update("update db_table_t set table_name='people' where tid='table'");
            saveConfig(JSONUtil.toJsonStr(Map.of("accessMode", "upload", "reportRegistration", Map.of(
                    "revision", "v1",
                    "files", List.of(Map.of("fileId", "file", "fileName", "data.csv")),
                    "tables", List.of(Map.of("tid", "table", "fileId", "file", "tableName", "people",
                            "fileName", "data.csv", "recordCount", 2, "normalized", true, "normalizationVersion", 1))))));
        }

        String readConfig() {
            return base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'", String.class);
        }

        void saveConfig(String config) {
            base.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'", config);
        }

        Object run(String source,String table,String request) throws Exception {
            String script = CanonicalMagicSources.byId("data_report_submit_01")
                    .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|cn\\.dev33\\.)[^\\r\\n]*\\r?\\n", "");
            return base.execute(script, Map.of(
                    "datasourceId", source, "tableId", table, "fileId", "file", "requestId", request,
                    "file", new MockMultipartFile("file", "people.csv", "text/csv", "id,name\n1,甲\n".getBytes(StandardCharsets.UTF_8)),
                    "db", base.base.db, "tenantRuntime", base.base.scope, "jsons", new TableDetailFieldSaveMagicTest.Jsons(),
                    "reportUploadService", delivery));
        }
    }
    @Test void successIsRecordedOnlyAfterDeliveryAndRepeatedRequestDoesNotResend() throws Exception {
        Fixture f=new Fixture();Map<?,?> result=(Map<?,?>)f.run("source","table","request-1");assertEquals("success",result.get("status"));assertEquals("people",((Map<?,?>)result.get("registrationSnapshot")).get("tableName"));
        assertEquals(4, f.base.base.reads);
        assertEquals("success",((Map<?,?>)f.run("source","table","request-1")).get("status"));assertEquals(1,f.delivery.calls);
        assertEquals(3, f.base.base.reads);
        assertEquals("success",((Map<?,?>)f.run("source","table","request-2")).get("status"));assertEquals(1,f.delivery.calls);
        assertTrue(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class).contains("\"status\":\"success\""));
    }
    @Test void foreignSourceAndUnrelatedTableNeverReachDelivery() throws Exception {
        Fixture f=new Fixture();assertInstanceOf(ExitValue.class,f.run("foreign-source","table","request-1"));assertInstanceOf(ExitValue.class,f.run("source","foreign","request-1"));assertEquals(0,f.delivery.calls);
    }
    @Test void deliveryFailureIsPersistedAsFailure() throws Exception {
        Fixture f=new Fixture();f.delivery.fail=true;assertInstanceOf(ExitValue.class,f.run("source","table","request-1"));
        assertTrue(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class).contains("\"status\":\"failed\""));
    }
    @Test void concurrentNewRequestDoesNotStartAnotherDeliveryForSendingTable() throws Exception {
        Fixture f=new Fixture();var config=JSONUtil.parseObj(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class));
        config.getJSONObject("reportRegistration").set("receipts",List.of(Map.of("requestId","sending-1","tableId","table","fileId","file","status","sending")));
        f.base.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",config.toString());
        assertInstanceOf(ExitValue.class,f.run("source","table","sending-2"));assertEquals(0,f.delivery.calls);
    }

    @Test
    void concurrentRegistrationBeforeReservationPreventsDeliveryAndDoesNotWrite() {
        var f = new Fixture();
        var config = JSONUtil.parseObj(f.readConfig());
        config.getJSONObject("reportRegistration").set("revision", "v2");
        f.base.beforeLock = () -> f.saveConfig(config.toString());
        Exception failure = assertThrows(Exception.class, () -> f.run("source", "table", "stale-request"));
        assertTrue(failure.getMessage().contains("数据规范已更新"), failure::getMessage);
        assertEquals(config.toString(), f.readConfig());
        assertEquals(0, f.delivery.calls);
        assertEquals(3, f.base.base.reads);
        assertTrue(f.base.statements.stream().allMatch(ReportRegistrationMagicTest.Statement::reads));
    }

    @Test
    void twoShortLockTransactionsPreserveUnrelatedRegistrationAndReceiptsDuringDelivery() throws Exception {
        var f = new Fixture();
        f.delivery.duringUpload = () -> {
            var config = JSONUtil.parseObj(f.readConfig());
            var manifest = config.getJSONObject("reportRegistration");
            var receipts = manifest.getJSONArray("receipts");
            assertEquals("sending", receipts.getJSONObject(0).getStr("status"), "占位先提交，再开始投递");
            manifest.set("revision", "v2");
            receipts.add(Map.of("id", "other-receipt", "requestId", "other-request", "status", "success"));
            f.saveConfig(config.toString());
        };
        var result = assertInstanceOf(Map.class, f.run("source", "table", "request-1"));
        assertEquals("success", result.get("status"));
        var manifest = JSONUtil.parseObj(f.readConfig()).getJSONObject("reportRegistration");
        assertEquals("v2", manifest.getStr("revision"));
        var rows = manifest.getJSONArray("receipts");
        assertEquals("success", rows.getJSONObject(0).getStr("status"));
        assertEquals("other-receipt", rows.getJSONObject(1).getStr("id"));
        assertEquals(4, f.base.base.reads);
        var locks = f.base.statements.stream().filter(ReportRegistrationMagicTest.Statement::locks).toList();
        var writes = f.base.statements.stream().filter(statement -> !statement.reads()).toList();
        assertEquals(2, locks.size());
        assertEquals(2, writes.size());
        assertNotSame(locks.getFirst().connection(), locks.getLast().connection());
        for (int i = 0; i < 2; i++) {
            assertTrue(locks.get(i).transaction());
            assertTrue(writes.get(i).transaction());
            assertSame(locks.get(i).connection(), writes.get(i).connection());
        }
    }

    @Test
    void replacesNoReceiptIfReservationIdentityChangesDuringDelivery() {
        var f = new Fixture();
        f.delivery.duringUpload = () -> {
            var config = JSONUtil.parseObj(f.readConfig());
            config.getJSONObject("reportRegistration").getJSONArray("receipts").getJSONObject(0).set("id", "replacement");
            f.saveConfig(config.toString());
        };
        Exception failure = assertThrows(Exception.class, () -> f.run("source", "table", "request-1"));
        assertTrue(failure.getMessage().contains("上报回执已变化"), failure::getMessage);
        var current = JSONUtil.parseObj(f.readConfig()).getJSONObject("reportRegistration").getJSONArray("receipts").getJSONObject(0);
        assertEquals("sending", current.getStr("status"));
        assertEquals("replacement", current.getStr("id"));
        assertEquals(1, f.delivery.calls);
    }
}
