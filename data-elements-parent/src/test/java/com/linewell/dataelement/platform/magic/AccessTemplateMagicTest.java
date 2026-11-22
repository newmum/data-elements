package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import cn.hutool.json.JSONUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class AccessTemplateMagicTest {
    private static final String LIST = "api/09.海通流程接口/03.画布模板/03.分页查询接入模板.ms";
    private static final String MANAGE = "api/09.海通流程接口/03.画布模板/04.管理接入模板版本.ms";
    private static final String BIND = "function/01.通用函数/79.读取接入模板版本.ms";
    private static final String PERMISSION = "MENU:2081000000000000002";
    private static final String DEFINITION = """
            {"topology":"PARALLEL","sourceType":"JDBC","targets":["OCEANBASE_ORACLE","HIVE"],
             "intervalSeconds":60,"fetchSize":5000,"targetDatasourceIds":["oracle","hive"]}
            """;

    @Test void allChangedScriptsCompile() throws Exception {
        for (String file : List.of(LIST, MANAGE, BIND, "api/09.海通流程接口/03.画布模板/05.读取接入模板详情.ms",
                "api/09.海通流程接口/03.画布模板/06.模板目标数据源选项.ms",
                "api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms",
                "api/03.数据接入/12.目标数据/01.数据接入物化建表.ms")) {
            MagicScript.create(source(file), null).compile();
        }
    }

    @Test void completeTemplateKeepsExtraComponentsEdgesAndDefaultsWhileBindingAuthorizedConnections() throws Exception {
        String canonical = source("api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms");
        int start = canonical.indexOf("var applyTemplateSourceDefaults =");
        int end = canonical.indexOf("return db.transaction", start);
        String program = """
                import cn.hutool.json.JSONUtil as poolJsons
                var text = value => value == null ? '' : value.toString()
                var parseGovernanceConfig = value => poolJsons.parseObj(value)
                """ + canonical.substring(start, end) + """
                return poolJsons.toJsonStr(instantiateTemplateFlow(template, pipelines, tasks))
                """;
        var definition = JSONUtil.parseObj("""
                {"spec":{"fetchSize":1234,"intervalSeconds":30,"flow":{"version":1,
                "nodes":[
                  {"id":"source","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source","host":"wrong"}},
                  {"id":"filter","manifestKey":"transform.filter","category":"transform","config":{"condition":"score > 10","nullable":null}},
                  {"id":"sink","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","statementType":"INSERT"}}],
                "edges":[{"id":"first","source":"source","target":"filter"},{"id":"second","source":"filter","target":"sink"}]}}}
                """);
        var context = new HashMap<String, Object>();
        context.put("template", definition);
        context.put("pipelines", List.of(Map.of("dsl_json", """
                {"nodes":[
                  {"id":"old-source","manifestKey":"source.oceanbase","category":"source","config":{"host":"authorized","accessTemplate":{"versionId":"v1"}}},
                  {"id":"old-sink","manifestKey":"sink.jdbc","category":"sink","config":{"targetTableId":"authorized-target","host":"authorized-target-host","statementType":"UPSERT"}}]}
                """)));
        context.put("tasks", List.of(Map.of("tid", "validation")));
        context.put("jsons", new TemplateJson());
        var result = JSONUtil.parseObj(MagicScript.create(program, null).execute(new MagicScriptContext(context)).toString());
        var nodes = result.getJSONArray("nodes");
        assertThat(nodes).hasSize(3);
        assertThat(nodes.getJSONObject(0).getStr("manifestKey")).isEqualTo("source.oceanbase");
        assertThat(nodes.getJSONObject(0).getJSONObject("config").getStr("host")).isEqualTo("authorized");
        assertThat(nodes.getJSONObject(0).getJSONObject("config").getInt("batchSize")).isEqualTo(1234);
        assertThat(nodes.getJSONObject(1).getJSONObject("config").getStr("condition")).isEqualTo("score > 10");
        assertThat(nodes.getJSONObject(1).getJSONObject("config").isNull("nullable")).isTrue();
        assertThat(nodes.getJSONObject(2).getJSONObject("config").getStr("statementType")).isEqualTo("INSERT");
        assertThat(nodes.getJSONObject(2).getJSONObject("config").getStr("targetTableId")).isEqualTo("authorized-target");
        assertThat(result.getJSONArray("edges").getJSONObject(0).getStr("target")).isEqualTo("template-validation-filter");
        assertThat(definition.getJSONObject("spec").getJSONObject("flow").getJSONArray("nodes").getJSONObject(0).getStr("id"))
                .isEqualTo("source");
    }

    public static class TemplateJson {
        public String toJSONStr(Object value) { return JSONUtil.toJsonStr(value); }
    }

    @Test void templateValidationScrubsSensitiveFieldsIncludingNestedJsonProperties() throws Exception {
        var fixture = new Fixture();
        when(fixture.scope.dataSource()).thenReturn(Map.of("scope", "ALL"));
        var manifests = mock(ManifestRegistry.class);
        for (String[] type : new String[][]{{"source.mysql", "source"}, {"sink.jdbc", "sink"}}) {
            var manifest = mock(ComponentManifest.class);
            when(manifest.category()).thenReturn(type[1]);
            when(manifests.get(type[0])).thenReturn(manifest);
        }
        var spec = JSONUtil.parseObj("""
                {"topology":"PARALLEL","sourceType":"JDBC","targets":["MYSQL"],
                 "intervalSeconds":60,"fetchSize":5000,"flow":{"version":1,"nodes":[
                   {"id":"source","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source"}},
                   {"id":"sink","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","password":"validation-only"}}],
                   "edges":[{"id":"edge","source":"source","target":"sink"}]},
                 "nativeSnapshot":{"flowContents":{"processors":[{"properties":{"nullable":null}}]}}}
                """);
        spec.getJSONObject("nativeSnapshot").getJSONObject("flowContents").getJSONArray("processors")
                .getJSONObject(0).getJSONObject("properties").set("settings",
                        JSONUtil.toJsonStr(Map.of("password", "validation-only", "publicValue", "retained")));
        String program = source(MANAGE);
        program = program.substring(0, program.indexOf("var actor =")) + "\nreturn definition";
        program = program.replaceAll("(?m)^import (?:tenantRuntime|dataScope|tableRead|tableWrite|jsons|[^\\r\\n]+ as manifests)\\R", "");
        var context = Map.<String, Object>of(
                "body", Map.of("action", "create", "name", "validation-template", "spec", spec),
                "tenantRuntime", new TenantMagicModule(null), "dataScope", fixture.scope,
                "manifests", manifests, "jsons", new TemplateJson());
        try (var ignored = TenantContext.use("ga")) {
            var result = JSONUtil.parseObj(MagicScript.create(program, null).execute(new MagicScriptContext(context)).toString());
            assertThat(result.getJSONObject("flow").getJSONArray("nodes").getJSONObject(1).getJSONObject("config").getStr("password")).isNull();
            String embedded = result.getJSONObject("nativeSnapshot").getJSONObject("flowContents").getJSONArray("processors")
                    .getJSONObject(0).getJSONObject("properties").getStr("settings");
            assertThat(JSONUtil.parseObj(embedded).getStr("password")).isNull();
            assertThat(JSONUtil.parseObj(embedded).getStr("publicValue")).isEqualTo("retained");
        }
    }

    @Test void listReadsPublishedTemplatesAndDefaultInTheTenantWithThreeQueriesAtBothPageSizes() throws Exception {
        var fixture = new Fixture();
        for (int index = 0; index < 108; index++) fixture.insert("published-" + index, "ga", "PUBLISHED");
        fixture.insert("foreign", "ct", "PUBLISHED");
        fixture.insert("draft", "ga", "DRAFT");
        fixture.insert("retired", "ga", "RETIRED");
        fixture.jdbc.update("INSERT INTO sym_config_t VALUES ('nifiaccessdefaultga','ga','published-0',0)");
        when(fixture.scope.dataSource()).thenReturn(Map.of("scope", "OWNER"));
        try (var ignored = TenantContext.use("ga")) {
            for (int size : List.of(20, 100)) {
                fixture.reads.set(0);
                var output = (Map<?, ?>) fixture.run(LIST, Map.of("body", Map.of("pageSize", size)));
                assertThat((List<?>) output.get("list")).hasSize(size);
                assertThat(((Number) output.get("total")).intValue()).isEqualTo(108);
                assertThat(fixture.reads.get()).isEqualTo(3);
                assertThat(output.get("defaultVersionId")).isEqualTo("published-0");
                assertThat((List<?>) output.get("list")).anySatisfy(value -> {
                    var row = (Map<?, ?>) value;
                    assertThat(row.get("tid")).isEqualTo("published-0");
                    assertThat(row.get("isDefault")).isEqualTo(true);
                });
            }
            assertThat(status(fixture.run(LIST, Map.of("body", Map.of("manage", true))))).isEqualTo(403);
        }
    }

    @Test void bindingChecksEveryDatasourceInOneScopeCallAndRejectsForeignVersions() throws Exception {
        var fixture = new Fixture();
        fixture.insert("published", "ga", "PUBLISHED");
        fixture.insert("foreign", "ct", "PUBLISHED");
        fixture.insert("draft", "ga", "DRAFT");
        var source = Map.<String, Object>of("tid", "source", "dbType", "mysql");
        var oracle = Map.<String, Object>of("tid", "oracle", "dbType", "oceanbase",
                "poolCfg", "{\"compatibleMode\":\"oracle\"}");
        var hive = Map.<String, Object>of("tid", "hive", "dbType", "hive");
        var bindings = List.of(source, hive, oracle);
        var input = Map.<String, Object>of("versionId", "published", "source", source, "targets", List.of(hive, oracle));
        when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION), any())).thenReturn(bindings);
        try (var ignored = TenantContext.use("ga")) {
            var result = (Map<?, ?>) fixture.run(BIND, input);
            assertThat(result.get("tid")).isEqualTo("published");
            verify(fixture.scope).visibleDataSourcesFor(PERMISSION, bindings);
            var replacement = Map.<String, Object>of("tid", "different-hive", "dbType", "hive");
            var substituted = Map.<String, Object>of("versionId", "published", "source", source,
                    "targets", List.of(replacement, oracle));
            when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION), any())).thenReturn(List.of(source, replacement, oracle));
            assertThat(status(fixture.run(BIND, substituted))).isEqualTo(403);
            when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION), any())).thenReturn(List.of(source, oracle));
            assertThat(status(fixture.run(BIND, input))).isEqualTo(403);
            for (String id : List.of("foreign", "draft")) {
                var denied = new HashMap<>(input);
                denied.put("versionId", id);
                assertThat(status(fixture.run(BIND, denied))).isEqualTo(409);
            }
        }
    }

    @Test void targetCandidatesUseTwoQueriesForTwentyAndHundredAndOneForBatchIds() throws Exception {
        var fixture = new Fixture();
        when(fixture.scope.dataSource()).thenReturn(Map.of("scope", "ALL"));
        fixture.jdbc.execute("""
                CREATE TABLE db_datasource_t(tid varchar(32),tenant_id varchar(32),db_name varchar(96),
                  db_type varchar(32),pool_cfg clob,is_del int,show_connect int)
                """);
        for (int index = 0; index < 108; index++) fixture.jdbc.update(
                "INSERT INTO db_datasource_t VALUES (?,?,?,'mysql','{}',0,1)", "target-" + index, "ga", "target-" + index);
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('foreign','ct','foreign','mysql','{}',0,1)");
        String route = "api/09.海通流程接口/03.画布模板/06.模板目标数据源选项.ms";
        try (var ignored = TenantContext.use("ga")) {
            for (int size : List.of(20, 100)) {
                fixture.reads.set(0);
                var result = (Map<?, ?>) fixture.run(route, Map.of("body", Map.of("kind", "MYSQL", "pageSize", size)));
                assertThat((List<?>) result.get("list")).hasSize(size);
                assertThat(((Number) result.get("total")).intValue()).isEqualTo(108);
                assertThat(fixture.reads.get()).isEqualTo(2);
                assertThat(JSONUtil.toJsonStr(result)).doesNotContain("poolCfg", "pool_cfg");
            }
            fixture.reads.set(0);
            var result = (Map<?, ?>) fixture.run(route, Map.of("body", Map.of("ids", List.of("target-0", "foreign"))));
            assertThat((List<?>) result.get("list")).hasSize(1);
            assertThat(fixture.reads.get()).isEqualTo(1);
            when(fixture.scope.dataSource()).thenReturn(Map.of("scope", "OWNER"));
            assertThat(status(fixture.run(route, Map.of("body", Map.of())))).isEqualTo(403);
        }
    }

    @Test void universalSourceBindingCoversEveryShippedConnectorAndPushFilesRequireTheSerialChain() throws Exception {
        var fixture=new Fixture();fixture.insert("universal","ga","PUBLISHED");
        var recipe=JSONUtil.parseObj(DEFINITION).set("sourceType","ANY").set("engine","CANVAS");
        fixture.jdbc.update("UPDATE nifi_access_template_t SET definition=? WHERE tid='universal'",recipe.toString());
        when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION),any())).thenAnswer(call->call.getArgument(1));
        var targets=List.of(Map.<String,Object>of("tid","oracle","dbType","oceanbaseoracle"),Map.<String,Object>of("tid","hive","dbType","hive"));
        try(var ignored=TenantContext.use("ga")) {
            for(var kind:com.linewell.dataelement.metautil.model.enums.DatabaseType.values()) {
                if(kind==com.linewell.dataelement.metautil.model.enums.DatabaseType.MAXCOMPUTE)continue;
                var source=Map.<String,Object>of("tid","source","dbType",kind.getCode(),"poolCfg","{}");
                fixture.reads.set(0);
                assertThat(((Map<?,?>)fixture.run(BIND,Map.of("versionId","universal","source",source,"targets",targets))).get("tid"))
                        .isEqualTo("universal");
                assertThat(fixture.reads.get()).isEqualTo(1);
            }
            for(String mode:List.of("receive","push")) {
                var source=Map.<String,Object>of("tid","source","poolCfg","{\"accessMode\":\""+mode+"\"}");
                var input=Map.<String,Object>of("versionId","universal","source",source,"targets",targets);
                assertThat(status(fixture.run(BIND,input))).isEqualTo(400);
                recipe.set("topology","SERIAL").set("engine","GENERATED");
                fixture.jdbc.update("UPDATE nifi_access_template_t SET definition=? WHERE tid='universal'",recipe.toString());
                assertThat(((Map<?,?>)fixture.run(BIND,input)).get("tid")).isEqualTo("universal");
                recipe.set("topology","PARALLEL").set("engine","CANVAS");
                fixture.jdbc.update("UPDATE nifi_access_template_t SET definition=? WHERE tid='universal'",recipe.toString());
            }
        }
    }

    @Test void sourceInstantiationKeepsProtocolLocationsConsumptionAndRealtimeSchedule() throws Exception {
        String canonical=source("api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms");
        int start=canonical.indexOf("var applyTemplateSourceDefaults ="),end=canonical.indexOf("return db.transaction",start);
        String program="import cn.hutool.json.JSONUtil as poolJsons\nvar text = value => value == null ? '' : value.toString()\n"
                +"var blank = value => value == null || text(value).trim() == ''\n"
                +"var parseGovernanceConfig = value => poolJsons.parseObj(value)\n"+canonical.substring(start,end)
                +"\nreturn poolJsons.toJsonStr(instantiateTemplateFlow(template,pipelines,tasks))";
        for(String type:List.of("ftp","sftp","api","kafka","minio","mongodb","elasticsearch","hdfs","hbase","mysql")) {
            var spec=JSONUtil.parseObj("""
                    {"sourceType":"ANY","sourceSchedule":"REGISTERED","intervalSeconds":60,"fetchSize":5000,
                     "flow":{"version":1,"nodes":[
                     {"id":"source","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source","hostname":"wrong","topic":"wrong","remotePath":"wrong","schedulingPeriod":"999 sec"}},
                     {"id":"sink","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","writerType":"","statementType":"MERGE"}}],
                     "edges":[{"id":"edge","source":"source","target":"sink"}]}}
                    """);
            var runtime=new java.util.LinkedHashMap<String,Object>(Map.<String,Object>of("hostname","registered-host","remotePath","/registered/events","topic","registered-topic",
                    "schedulingPeriod",type.equals("kafka")?"0 sec":"15 min","fileConsumptionPolicy","DELETE_AFTER_DATABASE_COMMIT",
                    "fileFormat","json","fileHeaderMode","registered-chinese","minioBucket","registered-bucket"));
            runtime.putAll(Map.of("minioObjectKey","registered/Report#2026.xlsx","excelSheetName","月#统计","jsonRecordPath","$.data.people"));
            var dsl=Map.of("nodes",List.of(Map.of("id","source","manifestKey","source."+type,"category","source","config",runtime),
                    Map.of("id","sink","manifestKey","sink.jdbc","category","sink","config",Map.of("targetTableId","authorized",
                            "writerType","LINEWELL_PUT_DATABASE_RECORD","statementType","INSERT","updateKeys",""))));
            var result=JSONUtil.parseObj(MagicScript.create(program,null).execute(new MagicScriptContext(Map.of(
                    "template",Map.of("spec",spec),"pipelines",List.of(Map.of("dsl_json",JSONUtil.toJsonStr(dsl))),"tasks",List.of(Map.of("tid","validation"))))).toString());
            var node=result.getJSONArray("nodes").getJSONObject(0);
            assertThat(node.getStr("manifestKey")).isEqualTo("source."+type);
            var cfg=node.getJSONObject("config");
            runtime.forEach((key,value)->assertThat(cfg.get(key)).as(type+" "+key).isEqualTo(value));
            assertThat(cfg.getInt("batchSize")).isEqualTo(5000);
            var target=result.getJSONArray("nodes").getJSONObject(1).getJSONObject("config");
            assertThat(target.getStr("statementType")).isEqualTo("INSERT");
            assertThat(target.getStr("writerType")).isEqualTo("LINEWELL_PUT_DATABASE_RECORD");
        }
    }

    private static int status(Object output) {
        return Integer.parseInt(((ExitValue) output).getValues()[0].toString());
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("../magic", file)).split("================================", 2)[1];
    }

    private static final class Fixture {
        private final AtomicInteger reads = new AtomicInteger();
        private final DataScopeMagicModule scope = mock(DataScopeMagicModule.class);
        private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:access_template_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "") {
            @Override public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMetaData")) {
                                var metadata = connection.getMetaData();
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                        (ignored, operation, values) -> operation.getName().equals("getURL")
                                                ? "jdbc:mysql://localhost/access_template_validation"
                                                : operation.invoke(metadata, values));
                            }
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                    && sql.startsWith("SELECT")) reads.incrementAndGet();
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException error) {
                                throw error.getCause();
                            }
                        });
            }
        });
        private final StructuredTableReadMagicModule reader = new StructuredTableReadMagicModule(jdbc);

        Fixture() {
            jdbc.execute("CREATE TABLE sym_config_t(tid varchar(64),tenant_id varchar(32),config_value varchar(64),is_del int)");
            jdbc.execute("""
                    CREATE TABLE nifi_access_template_t(tid varchar(32) primary key, tenant_id varchar(32),
                      family_id varchar(32), revision_no int, display_name varchar(96), status varchar(16),
                      definition clob, updated_at bigint)
                    """);
        }

        void insert(String id, String tenant, String status) {
            jdbc.update("INSERT INTO nifi_access_template_t VALUES (?,?,?,1,?,?,?,0)", id, tenant, id, id, status, DEFINITION);
        }

        Object run(String file, Map<String, Object> input) throws Exception {
            String script = source(file).replaceAll("(?m)^import (?:dataScope|tableRead|tenantRuntime)\\R", "");
            var context = new HashMap<>(input);
            context.putAll(Map.of("dataScope", scope, "tenantRuntime", new TenantMagicModule(null), "tableRead", reader));
            return MagicScript.create(script, null).execute(new MagicScriptContext(context));
        }
    }
}
