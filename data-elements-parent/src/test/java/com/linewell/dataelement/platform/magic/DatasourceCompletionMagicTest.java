package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.model.SqlMode;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.exception.MagicExitException;
import org.ssssssss.script.runtime.ExitValue;

class DatasourceCompletionMagicTest {
    private static final String SAVE_RESOURCE = "datapd_save_update_datasource_01";
    private static final String CONNECTION_RESOURCE = "datapd_datasource_connection_config_01";

    @Test void basicSaveKeepsOnlyServerOwnedUploadManifestWithoutAConnection() throws Exception {
        String source = CanonicalMagicSources.byId(SAVE_RESOURCE).replace("\r\n", "\n");
        int start = source.indexOf("var persistConnection = () =>");
        int end = source.indexOf("var persistSource = () =>", start);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        String persistence = source.substring(start, end);
        for (String mode : List.of("upload", "explore")) {
            Map<String,Object> manifest = Map.of("revision", "server-revision", "files", List.of("registered-file"));
            var bindings = new LinkedHashMap<String,Object>();
            bindings.put("showConnect",0);bindings.put("tid","db");bindings.put("accessMode",mode);
            bindings.put("oldConnectionConfig",Map.of("reportRegistration",manifest));
            bindings.put("connectionConfig",Map.of("reportRegistration",Map.of("revision","forged")));
            bindings.put("receiveProfile",Map.of());
            String harness = "var calls=[]\nvar blank=v=>v==null||(v+'').trim()==''\nvar datasourceConnectionConfig=(action,id,config,reset)=>calls.add(config)\n";
            List<?> calls = (List<?>)MagicScript.create(harness + persistence + "\npersistConnection()\nreturn calls", null)
                    .execute(new MagicScriptContext(bindings));
            assertThat(calls).hasSize(2);
            Map<?,?> saved = (Map<?,?>)calls.getLast();
            if (mode.equals("upload")) assertThat(saved.get("reportRegistration")).isEqualTo(manifest);
            else assertThat(saved.containsKey("reportRegistration")).isFalse();
        }
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void finishPersistsOneForPartialAndTwoForAllRegistered(int count) throws Exception {
        Fixture fixture = new Fixture(count);
        Map<?,?> result = (Map<?,?>) fixture.run(false);
        assertThat(result.get("assetStatus")).isEqualTo(1);
        assertThat(fixture.status()).isEqualTo(1);
        fixture.jdbc.update("update db_table_t set asset_status=2 where tenant_id='ga'");
        result = (Map<?,?>) fixture.run(false);
        assertThat(result.get("assetStatus")).isEqualTo(2);
        assertThat(fixture.status()).isEqualTo(2);
        assertThat(fixture.scope.calls).isEqualTo(2);
        assertThat(fixture.es.calls).isEqualTo(2);
    }
    @Test void completionPreviewEnforcesMenuRoleScopeAndNeverWrites() throws Exception {
        Fixture fixture = new Fixture(20);
        Map<?,?> result = (Map<?,?>) fixture.run(true);
        assertThat(result.get("assetStatus")).isEqualTo(1);
        assertThat(fixture.status()).isZero();
        fixture.scope.denied=true;
        assertThat(fixture.run(true)).isInstanceOf(ExitValue.class);
        assertThat(fixture.status()).isZero();
        assertThat(fixture.es.calls).isZero();
    }

    @Test
    void apiSavePreviewKeepsGenericValidationMessageAndNeverWrites() throws Exception {
        var fixture = new SaveFixture(false);
        var before = fixture.row("db");
        var definition = new LinkedHashMap<String, Object>(Map.of(
                "tableName", "validation_table", "tableComment", "验证表", "serviceName", "验证服务",
                "baseUrl", "https://validation.example", "endpointPath", "/items", "authType", "NONE"));
        var properties = Map.<String, Object>of("dbType", "api", "apiPullItems", List.of(definition));
        var preview = Map.<String, Object>of("dryRun", true);

        assertThat(fixture.save("db", "capture", properties, preview)).isInstanceOf(Map.class);

        definition.put("requestHeadersJson", "{\"Authorization\":\"validation\"}");
        assertThat(fixture.save("db", "capture", properties, preview))
                .isInstanceOfSatisfying(ExitValue.class, exit -> {
                    assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(400);
                    assertThat(exit.getValues()[1]).isEqualTo("API 抓取登记定义无效，请核对地址、表名及凭据引用");
                });
        assertThat(fixture.connection.saveCalls).isZero();
        assertThat(fixture.row("db")).isEqualTo(before);
    }

    @Test
    void failedSecondConnectionWriteRollsBackMasterAndFirstPoolWrite() throws Exception {
        var fixture = new SaveFixture(false);
        var before = fixture.row("db");
        fixture.jdbc.execute("""
                ALTER TABLE db_datasource_t ADD CONSTRAINT reject_validation_upload
                CHECK (pool_cfg IS NULL OR pool_cfg NOT LIKE '%upload%')
                """);

        assertThatThrownBy(() -> fixture.save("db", "upload"))
                .hasStackTraceContaining("reject_validation_upload");

        assertThat(fixture.connection.saveCalls).isEqualTo(2);
        assertThat(fixture.connection.completedSaves).isEqualTo(1);
        assertThat(fixture.row("db")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "soft-delete"})
    void rejectsConcurrentVersionOrDeletionBeforeWritingConnectionConfig(String change) throws Exception {
        var fixture = new SaveFixture(false);
        var originalPool = fixture.row("db").get("pool_cfg");
        fixture.connection.afterRead = () -> {
            if (change.equals("version")) {
                fixture.jdbc.update("""
                        UPDATE db_datasource_t SET db_name='Concurrent edit', updated_time=?
                        WHERE tid='db' AND tenant_id='ga'
                        """, Timestamp.valueOf("2026-10-22 10:20:30.123457"));
            } else {
                fixture.jdbc.update("UPDATE db_datasource_t SET is_del=1 WHERE tid='db' AND tenant_id='ga'");
            }
        };

        var result = fixture.save("db", "explore");
        assertThat(result).isInstanceOfSatisfying(ExitValue.class,
                exit -> assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(409));
        assertThat(fixture.connection.saveCalls).isZero();
        assertThat(fixture.row("db")).containsEntry("pool_cfg", originalPool)
                .containsEntry("updated_by", "original-editor");
        if (change.equals("version")) {
            assertThat(fixture.row("db")).containsEntry("db_name", "Concurrent edit")
                    .containsEntry("updated_time", Timestamp.valueOf("2026-10-22 10:20:30.123457"));
        } else {
            assertThat(fixture.row("db")).containsEntry("is_del", 1)
                    .containsEntry("db_name", "Original datasource");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existingSaveHandlesNullVersionsAndClearsConnectionWithoutLosingAuditFields(boolean nullVersion)
            throws Exception {
        var fixture = new SaveFixture(nullVersion);
        var before = fixture.row("db");

        assertThat(fixture.save("db", "explore")).isInstanceOf(Map.class);

        var saved = fixture.row("db");
        assertThat(fixture.connection.completedSaves).isEqualTo(2);
        assertThat(saved).containsEntry("db_name", "Edited datasource").containsEntry("show_connect", 0)
                .containsEntry("tenant_id", "ga").containsEntry("org_id", "original-org")
                .containsEntry("created_by", "original-creator").containsEntry("created_time", before.get("created_time"))
                .containsEntry("reg_time", before.get("reg_time")).containsEntry("updated_by", "validation-editor")
                .containsEntry("jdbc_url", null).containsEntry("username", null).containsEntry("password", null)
                .containsEntry("driver_class_name", null).containsEntry("pool_cfg", "{\"accessMode\":\"explore\"}");
        assertThat(saved.get("updated_time")).isNotNull().isNotEqualTo(before.get("updated_time"));
    }

    @Test
    void newSaveInsertsMasterAndConnectionTogether() throws Exception {
        var fixture = new SaveFixture(false);

        assertThat(fixture.save("new-db", "explore")).isInstanceOf(Map.class);

        var saved = fixture.row("new-db");
        assertThat(saved).containsEntry("db_name", "Edited datasource").containsEntry("tenant_id", "ga")
                .containsEntry("created_by", "validation-editor").containsEntry("updated_by", "validation-editor")
                .containsEntry("pool_cfg", "{\"accessMode\":\"explore\"}");
        assertThat(saved.get("created_time")).isNotNull();
        assertThat(saved.get("reg_time")).isNotNull();
        assertThat(fixture.connection.completedSaves).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryDryRunReturnsProposedValuesWithoutMasterConfigOrIndexChanges(boolean nested) throws Exception {
        var fixture = new SaveFixture(false);
        fixture.jdbc.update("UPDATE db_datasource_t SET asset_status=2, show_connect=0 WHERE tid='db'");
        var before = fixture.row("db");
        var properties = nested ? Map.<String, Object>of("dryRun", true) : Map.<String, Object>of();
        var request = nested ? Map.<String, Object>of("deferIndexRefresh", false)
                : Map.<String, Object>of("dryRun", true, "deferIndexRefresh", false);

        assertThat(fixture.save("db", "explore", properties, request)).isEqualTo(
                Map.of("tid", "db", "assetStatus", 2, "indexRefreshPending", false));
        assertThat(fixture.save("new-db", "explore", properties, request)).isEqualTo(
                Map.of("tid", "new-db", "assetStatus", 0, "indexRefreshPending", false));

        assertThat(fixture.row("db")).isEqualTo(before);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_datasource_t", Integer.class)).isEqualTo(1);
        assertThat(fixture.connection.saveCalls).isZero();
        assertThat(fixture.index.refreshCalls).isZero();
    }

    @Test
    void ordinaryDryRunStillRejectsInvalidPushConfiguration() throws Exception {
        var fixture = new SaveFixture(false);
        var before = fixture.row("db");

        var result = fixture.save("new-db", "receive", Map.of(), Map.of("dryRun", true, "deferIndexRefresh", false));

        assertThat(result).isInstanceOfSatisfying(ExitValue.class, exit -> {
            assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(400);
            assertThat(exit.getValues()[1]).isEqualTo("请填写数据来源地址");
        });
        assertThat(fixture.row("db")).isEqualTo(before);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_datasource_t", Integer.class)).isEqualTo(1);
        assertThat(fixture.connection.saveCalls).isZero();
        assertThat(fixture.index.refreshCalls).isZero();
    }

    @Test
    void legacyProxyFullSaveRechecksWitnessBeforeChangingMasterAndPool() throws Exception {
        var stale = new SaveFixture(false);
        stale.addProxyService();
        var before = stale.row("db");
        stale.connection.afterRead = () -> stale.jdbc.update("UPDATE api_info_t SET is_del=1 WHERE tid='service'");

        var rejected = stale.save("db", "proxy", Map.of("assetStatus", 2));

        assertThat(rejected).isInstanceOfSatisfying(ExitValue.class,
                exit -> assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(409));
        assertThat(stale.connection.saveCalls).isZero();
        assertThat(stale.row("db")).isEqualTo(before);

        var eligible = new SaveFixture(false);
        eligible.addProxyService();

        assertThat(eligible.save("db", "proxy", Map.of("assetStatus", 1))).isInstanceOf(Map.class);
        assertThat(eligible.row("db")).containsEntry("asset_status", 2).containsEntry("flow_status", 2)
                .containsEntry("db_name", "Edited datasource").containsEntry("org_id", "original-org")
                .containsEntry("pool_cfg", "{\"accessMode\":\"proxy\"}");
        assertThat(eligible.connection.completedSaves).isEqualTo(2);
    }

    private static SQLModule magicDb(DriverManagerDataSource source) {
        var dynamic = new MagicDynamicDataSource();
        dynamic.setDefault(source);
        var db = new SQLModule(dynamic);
        db.setDataSourceNode(dynamic.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        return db;
    }

    private static final class SaveFixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final ConnectionRuntime connection;
        final SaveScope scope = new SaveScope();
        final IndexRuntime index = new IndexRuntime();

        SaveFixture(boolean nullVersion) throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:source_save_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(
                        tid VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(32), db_name VARCHAR, database_type INT,
                        db_type VARCHAR, driver_class_name VARCHAR, jdbc_url VARCHAR, username VARCHAR, password VARCHAR,
                        remark VARCHAR, is_enable INT, app_id VARCHAR, org_id VARCHAR, storage_domain VARCHAR,
                        node_id VARCHAR, db_version VARCHAR, user_count INT, view_count INT, procedure_count INT,
                        data_size VARCHAR, total_size VARCHAR, show_connect INT, connection_status VARCHAR, table_num INT,
                        asset_status INT, flow_status INT, flow_order_id VARCHAR, is_del INT, updated_time TIMESTAMP(6),
                        reg_time TIMESTAMP(6), created_time TIMESTAMP(6), created_by VARCHAR, updated_by VARCHAR, pool_cfg VARCHAR
                    )
                    """);
            jdbc.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,db_name,db_type,driver_class_name,jdbc_url,username,password,
                        org_id,show_connect,asset_status,flow_status,is_del,updated_time,reg_time,created_time,created_by,updated_by,pool_cfg)
                    VALUES('db','ga','Original datasource','mysql','validation.Driver','validation-url','original-user',
                        'fixture-password','original-org',1,0,0,0,?,TIMESTAMP '2026-10-20 07:00:00',
                        TIMESTAMP '2026-10-20 07:00:00','original-creator','original-editor',
                        '{"accessMode":"explore","host":"validation-host"}')
                    """, nullVersion ? null : Timestamp.valueOf("2026-10-22 10:20:30.123456"));
            db = magicDb(source);
            // Distinct audit actors make a switch away from the audited NamedTable path visible.
            db.setNamedTableInterceptors(List.of((mode, table) -> {
                if (mode == SqlMode.INSERT) table.column("created_by", "validation-editor");
                if (mode == SqlMode.INSERT || mode == SqlMode.UPDATE) table.column("updated_by", "validation-editor");
            }));
            connection = new ConnectionRuntime(db, scope);
        }

        Object save(String id, String mode) throws Exception {
            return save(id, mode, Map.of());
        }

        Object save(String id, String mode, Map<String, Object> extraProperties) throws Exception {
            return save(id, mode, extraProperties, Map.of());
        }

        Object save(String id, String mode, Map<String, Object> extraProperties, Map<String, Object> request)
                throws Exception {
            String canonical = CanonicalMagicSources.byId(SAVE_RESOURCE)
                    .replaceAll("(?m)^import [^\r\n]+\r?\n", "");
            String importsAndBridge = """
                    import cn.hutool.core.util.IdUtil
                    import cn.hutool.json.JSONUtil
                    import java.util.Map
                    var datasourceConnectionConfig = (action, id, values, clearAll) =>
                        connectionRuntime.invoke(action, id, values, clearAll)
                    var refreshAssetIndex = () => indexRuntime.refresh()
                    """;
            var properties = new LinkedHashMap<String, Object>(Map.of(
                    "dbName", "Edited datasource", "dbType", "mysql", "showConnect", 0,
                    "sourceOrgId", "submitted-org", "accessMode", mode));
            properties.putAll(extraProperties);
            var body = new LinkedHashMap<String, Object>(Map.of("tid", id, "deferIndexRefresh", true,
                    "propList", properties));
            body.putAll(request);
            var inputs = Map.of("db", db, "body", body, "tenantRuntime", scope, "dataScope", scope,
                    "connectionRuntime", connection, "tableRead", new StructuredTableReadMagicModule(jdbc),
                    "tableWrite", new StructuredTableWriteMagicModule(jdbc), "indexRuntime", index,
                    "apiPullDefinitionError", ApiPullMagicFixture.definitionError());
            return TenantContext.call("ga", () -> MagicScript.create(importsAndBridge + canonical, null)
                    .execute(new MagicScriptContext(inputs)));
        }

        void addProxyService() {
            jdbc.update("UPDATE db_datasource_t SET pool_cfg=? WHERE tid='db' AND tenant_id='ga'",
                    "{\"accessMode\":\"proxy\",\"host\":\"validation-host\"}");
            jdbc.execute("""
                    CREATE TABLE api_info_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, datasource_id VARCHAR, is_del INT)
                    """);
            jdbc.update("INSERT INTO api_info_t VALUES ('service','ga','db',0)");
        }

        Map<String, Object> row(String id) {
            return jdbc.queryForMap("SELECT * FROM db_datasource_t WHERE tid=? AND tenant_id='ga'", id);
        }
    }

    public static final class SaveScope extends ScopeRuntime {
        public boolean canReadDataSourceFor(String resource, Map<String, Object> row) {
            return !denied;
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", "ALL");
        }
    }

    public static final class ConnectionRuntime {
        private final SQLModule db;
        private final ScopeRuntime scope;
        private final MagicScript helper;
        Runnable afterRead;
        int saveCalls;
        int completedSaves;

        ConnectionRuntime(SQLModule db, ScopeRuntime scope) throws Exception {
            this.db = db;
            this.scope = scope;
            String source = CanonicalMagicSources.byId(CONNECTION_RESOURCE)
                    .replaceAll("(?m)^import tenantRuntime[ \\t]*\\r?\\n", "");
            helper = MagicScript.create(source, null);
        }

        public Object invoke(String action, String id, Object values, boolean clearAll) {
            var input = new LinkedHashMap<String, Object>();
            input.put("db", db);
            input.put("tenantRuntime", scope);
            input.put("action", action);
            input.put("datasourceId", id);
            input.put("values", values);
            input.put("clearAll", clearAll);
            if (action.equals("save")) saveCalls++;
            Object result = helper.execute(new MagicScriptContext(input));
            // Magic API turns an imported function's exit value back into a propagated exception.
            if (result instanceof ExitValue exit) throw new MagicExitException(exit);
            if (action.equals("save")) completedSaves++;
            if (action.equals("read") && afterRead != null) afterRead.run();
            return result;
        }
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MetadataAssetPersistenceModule metadata;
        final ScopeRuntime scope=new ScopeRuntime();
        final IndexRuntime es=new IndexRuntime();
        Fixture(int count) {
            var source=new DriverManagerDataSource("jdbc:h2:mem:finish_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
            jdbc=new JdbcTemplate(source);
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),asset_status int,flow_status int,updated_time timestamp,is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),asset_status int,is_del int)");
            // Match production identity lookups without forbidding deliberate cross-tenant shadow IDs.
            jdbc.execute("create index idx_test_source_identity on db_datasource_t(tid,tenant_id,is_del)");
            jdbc.execute("create index idx_test_table_identity on db_table_t(tid,tenant_id,is_del)");
            jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del) values('db','ga',0,0)");
            for(int i=0;i<count;i++) jdbc.update("insert into db_table_t values(?,'ga','db',?,0)","table-"+i,i==0?0:2);
            db = magicDb(source);
            metadata=new MetadataAssetPersistenceModule(source);
        }
        int status() { return jdbc.queryForObject("select asset_status from db_datasource_t where tid='db'",Integer.class); }
        Object run(boolean dryRun) throws Exception {
            String script=CanonicalMagicSources.byId(SAVE_RESOURCE)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            Map<String,Object> bindings=new LinkedHashMap<>();
            bindings.put("body",Map.of("tid","db","propList",Map.of("completeOnly",true,"dryRun",dryRun)));
            bindings.put("db",db);bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
            bindings.put("metadataAsset",metadata);bindings.put("esCommonService",es);
            try(var ignored=TenantContext.use("ga")) {
                return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
            }
        }
    }
    public static class ScopeRuntime {
        int calls;boolean denied;
        public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){calls++;return denied?List.of():rows;}
    }
    public static class IndexRuntime {
        int calls;
        int refreshCalls;
        public boolean existsById(String index,String id){return true;}
        public void partialUpdateFieldsFast(String index,String id,Map<String,Object> fields){calls++;}
        public void refresh() { refreshCalls++; }
    }
}
