package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ProxyDatasourceCompletionMagicTest {
    public static class ProxyScope extends DatasourceCompletionMagicTest.ScopeRuntime {
        String role = "ALL";
        String userId = "user-a";
        List<String> organizationIds = List.of("org-a");

        public Map<String,Object> dataSource() {
            var decision = new LinkedHashMap<String, Object>();
            decision.put("scope", role);
            decision.put("userId", userId);
            decision.put("organizationIds", organizationIds);
            return decision;
        }
    }

    public static class IndexRuntime {
        private final JdbcTemplate physicalJdbc;
        private final List<Map<String, Object>> patches = new ArrayList<>();
        private int existenceChecks;
        private boolean transactionActive;
        private Integer committedStatus;

        IndexRuntime(JdbcTemplate physicalJdbc) { this.physicalJdbc = physicalJdbc; }

        public boolean existsById(String index, String id) {
            existenceChecks++;
            return true;
        }

        public void partialUpdateFieldsFast(String index, String id, Map<String, Object> fields) {
            assertThat(index).isEqualTo("dataassets");
            assertThat(id).isEqualTo("db");
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            committedStatus = physicalJdbc.queryForObject("select asset_status from db_datasource_t where tid=?", Integer.class, id);
            patches.add(Map.copyOf(fields));
        }
    }

    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture data = new DatasourceCompletionMagicTest.Fixture(0);
        final ProxyScope scope = new ProxyScope();
        final DatabaseTrace trace = new DatabaseTrace(data.jdbc.getDataSource());
        final JdbcTemplate magicJdbc = new JdbcTemplate(trace);
        final IndexRuntime index = new IndexRuntime(data.jdbc);

        Fixture() {
            data.jdbc.execute("""
                    alter table db_datasource_t add pool_cfg varchar(1000);
                    alter table db_datasource_t add updated_by varchar(64);
                    alter table db_datasource_t add db_name varchar(64);
                    alter table db_datasource_t add connection_status varchar(32);
                    create table api_info_t(tid varchar(64) primary key, tenant_id varchar(32),
                        datasource_id varchar(64), is_del int, org_id varchar(64), created_by varchar(64));
                    """);
            data.jdbc.update("""
                    update db_datasource_t set pool_cfg=?, updated_by='previous-editor', db_name='validation-source',
                        connection_status='unprovided', updated_time=TIMESTAMP '2020-01-02 03:04:05', flow_status=0
                    """, "{\"accessMode\":\"proxy\",\"otherConfiguration\":\"retained\"}");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(trace);
            data.db.setDataSourceNode(sources.getDataSource());
        }

        void add(String id, String tenant, String source, String org, String user, int deleted) {
            data.jdbc.update("insert into api_info_t values(?,?,?,?,?,?)", id, tenant, source, deleted, org, user);
        }

        Object run(boolean dryRun) throws Exception {
            String script = CanonicalMagicSources.byId("datapd_save_update_datasource_01")
                .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            trace.calls.clear();
            var bindings = new LinkedHashMap<String,Object>();
            bindings.put("body", Map.of("tid", "db", "propList", Map.of("completeOnly", true, "dryRun", dryRun)));
            bindings.put("db", data.db); bindings.put("tenantRuntime", scope); bindings.put("dataScope", scope);
            bindings.put("metadataAsset", data.metadata); bindings.put("esCommonService", index);
            bindings.put("tableRead", new StructuredTableReadMagicModule(magicJdbc));
            bindings.put("tableWrite", new StructuredTableWriteMagicModule(magicJdbc));
            try (var ignored = TenantContext.use("ga")) {
                return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
            }
        }

        Map<String, Object> source() {
            return data.jdbc.queryForMap("select * from db_datasource_t where tid='db'");
        }

        void assertReads(int expected) {
            var reads = trace.calls.stream().filter(SqlCall::read).toList();
            assertThat(reads).hasSize(expected).allMatch(call -> !call.transactional());
            assertThat(reads.getFirst().sql()).contains("db_datasource_t");
            if (expected == 2) assertThat(reads.getLast().sql()).contains("api_info_t", "count(");
        }

        void assertAtomicWrite() {
            assertThat(trace.calls.stream().filter(SqlCall::write).toList()).singleElement().satisfies(call -> {
                assertThat(call.transactional()).isTrue();
                assertThat(call.sql()).startsWith("update db_datasource_t")
                        .contains("exists (", "api_info_t", "datasource_id", "tenant_id", "is_del");
                assertThat(call.sql().substring(0, call.sql().indexOf(" where ")))
                        .contains("asset_status", "flow_status", "updated_time")
                        .doesNotContain("updated_by", "pool_cfg", "db_name", "connection_status");
            });
        }

        void assertNoWrite() {
            assertThat(trace.calls).noneMatch(SqlCall::write);
            assertNoIndex();
        }

        void assertNoIndex() {
            assertThat(index.existenceChecks).isZero();
            assertThat(index.patches).isEmpty();
        }

        void commitConcurrentChange(String sql, Object... values) {
            try (var connection = data.jdbc.getDataSource().getConnection(); var statement = connection.prepareStatement(sql)) {
                assertThat(connection.getAutoCommit()).isTrue();
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            } catch (SQLException failure) {
                throw new AssertionError("Concurrent change failed", failure);
            }
        }
    }

    private record SqlCall(String sql, boolean transactional) {
        boolean read() { return sql.startsWith("select"); }
        boolean write() { return sql.startsWith("update"); }
    }

    private static final class DatabaseTrace extends DelegatingDataSource {
        final List<SqlCall> calls = new ArrayList<>();
        Runnable beforeWrite;

        DatabaseTrace(DataSource physical) { super(physical); }

        @Override public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("prepareStatement")) return invoke(connection, method, args);
                        String sql = ((String) args[0]).strip().toLowerCase(Locale.ROOT).replace("\"", "").replace("`", "");
                        var prepared = (PreparedStatement) invoke(connection, method, args);
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                (statement, operation, values) -> {
                                    if (operation.getName().startsWith("execute")) {
                                        var call = new SqlCall(sql, TransactionSynchronizationManager.hasResource(this));
                                        calls.add(call);
                                        if (call.write() && beforeWrite != null) beforeWrite.run();
                                    }
                                    return invoke(prepared, operation, values);
                                });
                    });
        }

        private static Object invoke(Object receiver, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void serviceCompletionUsesTwoReadsIndependentOfServiceCount(int count) throws Exception {
        var f = new Fixture();
        for (int i=0; i<count; i++) f.add("service-"+i, "ga", "db", "org-a", "user-a", 0);
        var before = f.source();
        var preview = (Map<?,?>) f.run(true);
        assertThat(((Number) preview.get("serviceCount")).intValue()).isEqualTo(count);
        f.assertReads(2);
        f.assertNoWrite();
        assertThat(f.source()).isEqualTo(before);

        var result = (Map<?, ?>) f.run(false);
        assertThat(result.get("assetStatus")).isEqualTo(2);
        assertThat(result.get("indexSync")).isEqualTo("status-patch");
        f.assertReads(2);
        f.assertAtomicWrite();
        var after = f.source();
        assertThat(after.remove("asset_status")).isEqualTo(2);
        assertThat(after.remove("flow_status")).isEqualTo(2);
        assertThat(after.remove("updated_time")).isNotEqualTo(before.get("updated_time"));
        before.keySet().removeAll(List.of("asset_status", "flow_status", "updated_time"));
        assertThat(after).isEqualTo(before);
        assertThat(f.index.existenceChecks).isOne();
        assertThat(f.index.transactionActive).isFalse();
        assertThat(f.index.committedStatus).isEqualTo(2);
        assertThat(f.index.patches).singleElement().satisfies(patch -> {
            assertThat(patch).containsOnlyKeys("assetStatus", "flowStatus", "updatedTime", "updatedTime_date");
            assertThat(patch).containsEntry("assetStatus", 2).containsEntry("flowStatus", 2);
        });
    }

    @ParameterizedTest @ValueSource(strings={"ORG","OWNER"})
    void otherTenantsSourcesDeletedServicesAndRoleScopesCannotCompleteThisSource(String role) throws Exception {
        var f = new Fixture(); f.scope.role = role;
        f.add("foreign", "other", "db", "org-a", "user-a", 0);
        f.add("wrong-source", "ga", "other", "org-a", "user-a", 0);
        f.add("deleted", "ga", "db", "org-a", "user-a", 1);
        f.add("restricted", "ga", "db", "org-b", "user-b", 0);
        f.add("unassigned", "ga", "db", null, null, 0);
        assertThat(((Map<?,?>) f.run(true)).get("completionEligible")).isEqualTo(false);
        assertRejected(f.run(false), 400, "请先登记至少一项当前数据范围内的数据服务，再结束登记");
        f.assertReads(2);
        f.assertNoWrite();
        assertThat(f.data.status()).isZero();
        f.add("allowed", "ga", "db", "org-a", "user-a", 0);
        assertThat(f.run(false)).isInstanceOf(Map.class);
        assertThat(f.data.status()).isEqualTo(2);
    }

    @Test void parentPermissionRemainsMandatory() throws Exception {
        var f = new Fixture(); f.add("allowed", "ga", "db", "org-a", "user-a", 0);
        f.scope.denied = true;
        assertRejected(f.run(false), 403, "数据源不存在或已删除");
        f.assertReads(1);
        f.assertNoWrite();
        assertThat(f.data.status()).isZero();
        f.scope.denied = false;
        f.data.jdbc.update("update db_datasource_t set tenant_id='other'");
        assertRejected(f.run(false), 404, "数据源不存在或不属于当前租户");
        f.assertReads(1);
        f.assertNoWrite();
        assertThat(f.data.status()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"OWNER", "ORG"})
    void missingScopeValuesNeverMatchUnassignedServices(String role) throws Exception {
        var f = new Fixture();
        f.scope.role = role;
        f.scope.userId = null;
        f.scope.organizationIds = List.of();
        f.add("unassigned", "ga", "db", null, null, 0);
        var preview = (Map<?, ?>) f.run(true);
        assertThat(((Number) preview.get("serviceCount")).longValue()).isZero();
        f.assertReads(2);
        assertRejected(f.run(false), 400, "请先登记至少一项当前数据范围内的数据服务，再结束登记");
        f.assertReads(2);
        f.assertNoWrite();
    }

    @ParameterizedTest
    @CsvSource({"ALL,is_del,1", "ALL,datasource_id,other-source", "ALL,tenant_id,other",
            "ORG,org_id,other-org", "OWNER,created_by,other-user"})
    void concurrentServiceChangeCannotCompleteFromAStaleCount(String role, String field, String value) throws Exception {
        var f = new Fixture();
        f.scope.role = role;
        f.add("first", "ga", "db", "org-a", "user-a", 0);
        f.add("foreign", "other", "db", "org-a", "user-a", 0);
        f.add("wrong-source", "ga", "other", "org-a", "user-a", 0);
        f.add("deleted", "ga", "db", "org-a", "user-a", 1);
        if (!role.equals("ALL")) f.add("restricted", "ga", "db", "org-b", "user-b", 0);
        var before = f.source();
        f.trace.beforeWrite = () -> f.commitConcurrentChange("update api_info_t set " + field + "=? where tid='first'", value);
        assertRejected(f.run(false), 409, "服务清单已变化，请重新确认后结束登记");
        f.assertReads(2);
        f.assertAtomicWrite();
        assertThat(f.source()).isEqualTo(before);
        f.assertNoIndex();
    }

    @Test void anotherVisibleServiceStillAllowsCompletion() throws Exception {
        var f = new Fixture();
        f.scope.role = "OWNER";
        f.add("first", "ga", "db", "org-a", "user-a", 0);
        f.add("second", "ga", "db", "org-a", "user-a", 0);
        f.trace.beforeWrite = () -> f.commitConcurrentChange("update api_info_t set is_del=1 where tid='first'");
        assertThat(((Map<?, ?>) f.run(false)).get("assetStatus")).isEqualTo(2);
        f.assertReads(2);
        f.assertAtomicWrite();
        assertThat(f.index.patches).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"is_del,1", "tenant_id,other"})
    void concurrentlyDeletedOrMovedParentCannotComplete(String field, String value) throws Exception {
        var f = new Fixture();
        f.add("first", "ga", "db", "org-a", "user-a", 0);
        f.trace.beforeWrite = () -> f.commitConcurrentChange("update db_datasource_t set " + field + "=? where tid='db'", value);
        assertRejected(f.run(false), 409, "服务清单已变化，请重新确认后结束登记");
        f.assertReads(2);
        f.assertAtomicWrite();
        assertThat(f.source()).containsEntry("asset_status", 0);
        assertThat(f.source().get(field).toString()).isEqualTo(value);
        f.assertNoIndex();
    }

    @Test void concurrentlyEditedParentRetainsTheNewRevision() throws Exception {
        var f = new Fixture();
        f.add("first", "ga", "db", "org-a", "user-a", 0);
        f.trace.beforeWrite = () -> f.commitConcurrentChange("""
                update db_datasource_t set db_name='new-name', updated_time=TIMESTAMP '2020-01-02 03:04:06' where tid='db'
                """);
        assertRejected(f.run(false), 409, "服务清单已变化，请重新确认后结束登记");
        f.assertReads(2);
        f.assertAtomicWrite();
        assertThat(f.source()).containsEntry("db_name", "new-name").containsEntry("asset_status", 0);
        f.assertNoIndex();
    }

    private static void assertRejected(Object result, int code, String message) {
        assertThat(assertInstanceOf(ExitValue.class, result).getValues()).containsExactly(code, message);
    }
}
