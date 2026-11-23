package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.approval.application.ApprovalFlowService;
import com.linewell.dataelement.magic.module.ApprovalFlowMagicModule;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class AssetApprovalCompletionMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String FOREIGN = "validation-other-tenant";
    private static final String APPROVER = "validation-reviewer";

    @Test
    void publishesOnlyTheLockedVersionAndReplaysWithoutAdvancingTheHead() throws Exception {
        var fixture = new Fixture();
        var jdbc = fixture.jdbc;
        jdbc.update("""
                INSERT INTO res_catalog_publish_request(tid, tenant_id, publication_id, catalog_id, request_status)
                VALUES ('request', ?, 'publication', 'catalog', 'IN_REVIEW')
                """, TENANT);
        jdbc.update("""
                INSERT INTO da_catalog_publication_t(tid, tenant_id, request_id, catalog_id, status)
                VALUES ('publication', ?, 'request', 'catalog', 'VALIDATED')
                """, TENANT);
        jdbc.update("""
                INSERT INTO da_catalog_publication_head_t(catalog_id, tenant_id, current_publication_id, revision)
                VALUES ('catalog', ?, 'old', 4), ('catalog', ?, 'foreign', 8)
                """, TENANT, FOREIGN);

        assertThat(fixture.complete("haoyuePublication", "request")).isEqualTo(true);
        assertThat(fixture.complete("haoyuePublication", "request")).isEqualTo(true);

        assertThat(jdbc.queryForMap("SELECT request_status, reviewer_id FROM res_catalog_publish_request"))
                .containsAllEntriesOf(Map.of("request_status", "PUBLISHED", "reviewer_id", APPROVER));
        assertThat(jdbc.queryForMap("SELECT status, published_by FROM da_catalog_publication_t"))
                .containsAllEntriesOf(Map.of("status", "PUBLISHED", "published_by", APPROVER));
        assertThat(jdbc.queryForMap("""
                SELECT current_publication_id, revision FROM da_catalog_publication_head_t WHERE tenant_id=?
                """, TENANT)).containsAllEntriesOf(Map.of("current_publication_id", "publication", "revision", 5L));
        assertThat(jdbc.queryForObject("""
                SELECT current_publication_id FROM da_catalog_publication_head_t WHERE tenant_id=?
                """, String.class, FOREIGN)).isEqualTo("foreign");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void approvesOnlyTheCurrentSubmissionUsingFourBusinessReads(int count) throws Exception {
        var fixture = new Fixture();
        fixture.subscription(count);

        assertThat(fixture.complete("haoyueSubscription", "form")).isEqualTo(true);
        assertThat(fixture.source.reads()).isEqualTo(4);
        assertThat(fixture.source.writes()).isEqualTo(5);
        assertThat(fixture.count("da_apply_scope_t", "APPROVED")).isEqualTo(count);
        assertThat(fixture.count("da_authorization_scope_t", "PENDING")).isEqualTo(count);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM data_api_authorization_t", Integer.class))
                .isEqualTo(count / 2);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM data_distribution_task_t", Integer.class))
                .isEqualTo(count / 2);
        assertThat(fixture.jdbc.queryForList("""
                SELECT status FROM da_apply_scope_t WHERE tid IN ('previous', 'foreign') ORDER BY tid
                """, String.class)).containsExactly("SUBMITTED", "SUBMITTED");
        assertThat(fixture.jdbc.queryForMap("SELECT flow_status, revision FROM data_apply_form_t"))
                .containsAllEntriesOf(Map.of("flow_status", 2, "revision", "8"));

        assertThat(fixture.complete("haoyueSubscription", "form")).isEqualTo(true);
        assertThat(fixture.source.reads()).isEqualTo(1);
        assertThat(fixture.source.writes()).isZero();
        assertThat(fixture.count("da_authorization_scope_t", "PENDING")).isEqualTo(count);
    }

    @Test
    void foreignSnapshotRejectsTheEntireSubmission() throws Exception {
        var fixture = new Fixture();
        fixture.subscription(2);
        fixture.jdbc.update("UPDATE da_catalog_resource_snapshot_t SET tenant_id=? WHERE tid='api'", FOREIGN);

        assertThatThrownBy(() -> fixture.complete("haoyueSubscription", "form"))
                .hasStackTraceContaining("交付资源快照不存在或不属于该发布版本");

        assertThat(fixture.count("da_apply_scope_t", "APPROVED")).isZero();
        assertThat(fixture.count("da_authorization_scope_t", "PENDING")).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT flow_status FROM data_apply_form_t", Integer.class)).isEqualTo(1);
    }

    @Test
    void lateWriteFailureRollsBackScopeUpdatesAndBothDeliveryBatches() throws Exception {
        var fixture = new Fixture();
        fixture.subscription(2);
        fixture.jdbc.execute("ALTER TABLE da_authorization_scope_t ADD CONSTRAINT validation_failure CHECK (status <> 'PENDING')");

        assertThatThrownBy(() -> fixture.complete("haoyueSubscription", "form"))
                .hasStackTraceContaining("validation_failure");

        assertThat(fixture.count("da_apply_scope_t", "APPROVED")).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM data_api_authorization_t", Integer.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM data_distribution_task_t", Integer.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT flow_status FROM data_apply_form_t", Integer.class)).isEqualTo(1);
    }

    public record Session(String id) {}

    private static final class Fixture {
        final CountingDataSource source = new CountingDataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        final StructuredTableReadMagicModule read = new StructuredTableReadMagicModule(jdbc);
        final StructuredTableWriteMagicModule write = new StructuredTableWriteMagicModule(jdbc);
        final ApprovalFlowService service = mock(ApprovalFlowService.class);
        final ApprovalFlowMagicModule approval = new ApprovalFlowMagicModule(service, new ObjectMapper());
        final SQLModule db = sql(source);
        final MagicScript script;

        Fixture() throws Exception {
            String canonical = CanonicalMagicSources.byId("c30f3718868148ce84e65217f49a7720");
            script = MagicScript.create(canonical.replaceAll("(?m)^import (?:approvalFlow|tableRead|tableWrite|tenantRuntime)\\R", ""), null);
            when(service.finish(any(), any())).thenAnswer(call -> {
                ApprovalCallbackRequest event = call.getArgument(0);
                Function<Map<String, Object>, Object> callback = call.getArgument(1);
                return transaction.execute(status -> callback.apply(Map.of("type", event.getApproveType(),
                        "id", event.getBusinessId(), "actor", APPROVER, "decision", 2)));
            });
            for (String statement : SCHEMA.split(";")) {
                jdbc.execute(statement.strip());
            }
        }

        Object complete(String type, String id) {
            var event = new ApprovalCallbackRequest();
            event.setBusinessId(id);
            event.setApproveType(type);
            var variables = Map.of("engineEvent", event, "approvalFlow", approval, "tenantRuntime", new Session(TENANT),
                    "tableRead", read, "tableWrite", write, "db", db);
            source.commands.clear();
            source.measuring = true;
            try (var ignored = TenantContext.use(TENANT)) {
                return script.execute(new MagicScriptContext(variables));
            } finally {
                source.measuring = false;
            }
        }

        void subscription(int size) {
            jdbc.update("""
                    INSERT INTO data_apply_form_t VALUES ('form', ?, 'CATALOG_SUBSCRIPTION', 1, 2, '7',
                        'validation-request', 'validation-applicant', 'validation-department', NULL, NULL)
                    """, TENANT);
            jdbc.update("""
                    INSERT INTO da_catalog_resource_snapshot_t VALUES ('api', ?, 'publication', ?), ('table', ?, 'publication', ?)
                    """, TENANT, "{\"resource\":{\"id\":\"api-id\",\"kind\":\"API\"}}",
                    TENANT, "{\"resource\":{\"id\":\"table-id\",\"kind\":\"TABLE\",\"name\":\"table-name\"}}");
            jdbc.update("""
                    INSERT INTO da_apply_scope_t
                    SELECT 'scope-' || n, ?, 'form', 2, 'SUBMITTED', CASE WHEN MOD(n, 2)=0 THEN 'api' ELSE 'table' END,
                        'catalog', 'publication', CASE WHEN MOD(n, 2)=0 THEN 'API' ELSE 'TABLE' END, '{}',
                        NULL, TIMESTAMP '2099-01-01 00:00:00', 0, NULL, NULL, NULL, NULL
                    FROM SYSTEM_RANGE(1, ?) AS numbers(n)
                    """, TENANT, size);
            jdbc.update("""
                    INSERT INTO da_apply_scope_t
                    SELECT ?, ?, 'form', ?, 'SUBMITTED', 'table', 'catalog', 'publication', 'TABLE', '{}',
                        NULL, TIMESTAMP '2099-01-01 00:00:00', 0, NULL, NULL, NULL, NULL
                    """, "previous", TENANT, 1);
            jdbc.update("""
                    INSERT INTO da_apply_scope_t
                    SELECT ?, ?, 'form', ?, 'SUBMITTED', 'table', 'catalog', 'publication', 'TABLE', '{}',
                        NULL, TIMESTAMP '2099-01-01 00:00:00', 0, NULL, NULL, NULL, NULL
                    """, "foreign", FOREIGN, 2);
        }

        int count(String table, String status) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE status=?", Integer.class, status);
        }

        private static SQLModule sql(CountingDataSource source) {
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            var module = new SQLModule(sources);
            module.setDataSourceNode(sources.getDataSource());
            var dialect = new DialectAdapter();
            dialect.add(new MySQLDialect() {
                @Override public boolean match(String productName) { return true; }
            });
            module.setDialectAdapter(dialect);
            module.setResultProvider((request, status, message, data) -> data);
            var mapper = new ColumnMapperAdapter();
            mapper.add(new DefaultColumnMapperProvider());
            mapper.setDefault("default");
            module.setColumnMapperProvider(mapper);
            module.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            module.setRowMapColumnMapper(value -> value);
            module.setNamedTableInterceptors(List.of());
            module.setSqlInterceptors(List.of());
            return module;
        }
    }

    private static final class CountingDataSource extends DriverManagerDataSource {
        final List<String> commands = new ArrayList<>();
        boolean measuring;

        CountingDataSource() {
            super("jdbc:h2:mem:validation-asset-approval-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        long reads() {
            return commands.stream().filter("SELECT"::equals).count();
        }

        long writes() {
            return commands.size() - reads();
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (measuring && method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                            commands.add(sql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT));
                        }
                        try {
                            return method.invoke(actual, args);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    private static final String SCHEMA = """
            CREATE TABLE res_catalog_publish_request(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, publication_id VARCHAR,
                catalog_id VARCHAR, request_status VARCHAR, reviewed_time TIMESTAMP, published_time TIMESTAMP,
                reviewer_id VARCHAR, updated_time TIMESTAMP, updated_by VARCHAR);
            CREATE TABLE da_catalog_publication_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, request_id VARCHAR,
                catalog_id VARCHAR, status VARCHAR, published_at TIMESTAMP, published_by VARCHAR,
                updated_time TIMESTAMP, updated_by VARCHAR);
            CREATE TABLE da_catalog_publication_head_t(catalog_id VARCHAR, tenant_id VARCHAR, current_publication_id VARCHAR,
                revision BIGINT, updated_time TIMESTAMP, updated_by VARCHAR, PRIMARY KEY(catalog_id, tenant_id));
            CREATE TABLE data_apply_form_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, type VARCHAR, flow_status INTEGER,
                submission_version INTEGER, revision VARCHAR, apply_name VARCHAR, apply_user_id VARCHAR, apply_org_id VARCHAR,
                updated_time TIMESTAMP, updated_by VARCHAR);
            CREATE TABLE da_apply_scope_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, apply_form_id VARCHAR,
                submission_version INTEGER, status VARCHAR, resource_snapshot_id VARCHAR, catalog_id VARCHAR,
                publication_id VARCHAR, delivery_channel VARCHAR, requested_scope CLOB, requested_from TIMESTAMP,
                requested_until TIMESTAMP, revision BIGINT, approved_scope CLOB, decided_at TIMESTAMP,
                updated_time TIMESTAMP, updated_by VARCHAR);
            CREATE TABLE da_catalog_resource_snapshot_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR,
                publication_id VARCHAR, contract_snapshot CLOB);
            CREATE TABLE da_authorization_scope_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, apply_scope_id VARCHAR,
                authorization_version INTEGER, publication_id VARCHAR, resource_snapshot_id VARCHAR, delivery_channel VARCHAR,
                api_authorization_id VARCHAR, distribution_task_id VARCHAR, grantee_user_id VARCHAR, grantee_org_id VARCHAR,
                scope_snapshot CLOB, valid_from TIMESTAMP, valid_until TIMESTAMP, status VARCHAR, idempotency_key VARCHAR,
                created_by VARCHAR, created_time TIMESTAMP, updated_by VARCHAR, updated_time TIMESTAMP, revision BIGINT,
                UNIQUE(tenant_id, apply_scope_id, authorization_version));
            CREATE TABLE data_api_authorization_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, apply_form_id VARCHAR,
                catalog_id VARCHAR, api_id VARCHAR, apply_user_id VARCHAR, authorization_status VARCHAR, gateway_sync_status VARCHAR,
                authorization_scope VARCHAR, gateway_message VARCHAR, authorized_time TIMESTAMP, expire_time TIMESTAMP,
                created_by VARCHAR, created_time TIMESTAMP, updated_by VARCHAR, updated_time TIMESTAMP, is_del INTEGER);
            CREATE TABLE data_distribution_task_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, apply_form_id VARCHAR,
                catalog_id VARCHAR, catalog_name VARCHAR, source_table_id VARCHAR, source_table_name VARCHAR,
                task_code VARCHAR, task_name VARCHAR, task_status VARCHAR, progress INTEGER, execution_engine VARCHAR,
                execution_mode VARCHAR, status_message VARCHAR, created_by VARCHAR, created_time TIMESTAMP,
                updated_by VARCHAR, updated_time TIMESTAMP, is_del INTEGER)
            """;
}
