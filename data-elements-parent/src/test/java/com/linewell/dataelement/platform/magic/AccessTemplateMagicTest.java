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
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class AccessTemplateMagicTest {
    private static final String LIST = "api/09.海通流程接口/03.画布模板/03.分页查询接入模板.ms";
    private static final String MANAGE = "api/09.海通流程接口/03.画布模板/04.管理接入模板版本.ms";
    private static final String DETAIL = "api/09.海通流程接口/03.画布模板/05.读取接入模板详情.ms";
    private static final String BIND = "function/01.通用函数/79.读取接入模板版本.ms";
    private static final String PERMISSION = "MENU:2081000000000000002";
    private static final String DEFINITION = """
            {"topology":"PARALLEL","sourceType":"JDBC","targets":["OCEANBASE_ORACLE","HIVE"],
             "intervalSeconds":60,"fetchSize":5000,"targetDatasourceIds":["oracle","hive"]}
            """;

    @Test void allChangedScriptsCompile() throws Exception {
        for (String file : List.of(LIST, MANAGE, BIND, DETAIL,
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

    @Test void newlyInsertedTasksApplyDailyScheduleAfterTemplateInstantiationWithoutChangingExplicitOrReplayPolicies() throws Exception {
        String canonical = source("api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms");
        int start = canonical.indexOf("// 新建任务调度："), end = canonical.indexOf("// 新建任务调度结束", start);
        assertThat(canonical.indexOf("applyNewTaskSchedules(pipelineRows)"))
                .isGreaterThan(canonical.indexOf("primaryPipeline.dsl_json ="));
        String program = """
                import cn.hutool.json.JSONUtil as poolJsons
                import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
                var text = value => value == null ? '' : value.toString()
                var parseGovernanceConfig = value => poolJsons.parseObj(value)
                """ + canonical.substring(start, end) + "\napplyNewTaskSchedules(pipelineRows)\nreturn poolJsons.toJsonStr(pipelineRows)";
        for (int size : List.of(20, 100)) {
            var pipelines = new java.util.ArrayList<Map<String, Object>>();
            for (int i = 0; i < size; i++) {
                var nodes = List.of(
                        Map.of("id", "source-final-" + i, "manifestKey", "source.mysql", "category", "source", "config", Map.of("schedulingPeriod", "60 sec")),
                        Map.of("id", "custom", "manifestKey", "source.api", "category", "source", "config", Map.of("batchSchedulePolicy", "CUSTOM", "schedulingPeriod", "10 min")),
                        Map.of("id", "immediate", "manifestKey", "source.sftp", "category", "source", "config", Map.of("batchSchedulePolicy", "DAILY_IMMEDIATE", "schedulingPeriod", "1 day")),
                        Map.of("id", "kafka", "manifestKey", "source.kafka", "category", "source", "config", Map.of("schedulingPeriod", "0 sec")),
                        Map.of("id", "backfill", "manifestKey", "source.mysql", "category", "source", "config", Map.of("backfillSource", true, "enabled", false, "schedulingPeriod", "60 sec")));
                pipelines.add(new HashMap<>(Map.of("id", "validation-pipeline-" + i, "dsl_json", JSONUtil.toJsonStr(Map.of("nodes", nodes)))));
            }
            var context = Map.<String, Object>of("tenantId", "trusted-tenant", "pipelineRows", pipelines);
            var rows = JSONUtil.parseArray(MagicScript.create(program, null).execute(new MagicScriptContext(context)).toString());
            assertThat(rows).hasSize(size);
            var clocks = new java.util.HashSet<String>();
            for (int i = 0; i < size; i++) {
                var nodes = JSONUtil.parseObj(rows.getJSONObject(i).getStr("dsl_json")).getJSONArray("nodes");
                var generated = nodes.getJSONObject(0).getJSONObject("config");
                assertThat(generated.getStr("batchSchedulePolicy")).isEqualTo("DAILY_STAGGERED");
                assertThat(generated.getStr("schedulingStrategy")).isEqualTo("CRON_DRIVEN");
                assertThat(generated.getStr("batchScheduleZone")).isEqualTo("Asia/Shanghai");
                clocks.add(generated.getStr("schedulingPeriod"));
                assertThat(nodes.getJSONObject(1).getJSONObject("config").getStr("schedulingPeriod")).isEqualTo("10 min");
                assertThat(nodes.getJSONObject(2).getJSONObject("config").getStr("batchSchedulePolicy")).isEqualTo("DAILY_IMMEDIATE");
                assertThat(nodes.getJSONObject(3).getJSONObject("config").getStr("schedulingPeriod")).isEqualTo("0 sec");
                assertThat(nodes.getJSONObject(4).getJSONObject("config").getStr("schedulingPeriod")).isEqualTo("60 sec");
                assertThat(nodes.getJSONObject(4).getJSONObject("config").getBool("enabled")).isFalse();
            }
            assertThat(clocks.size()).isGreaterThan(size * 9 / 10);
        }
    }

    @Test void sharedGovernanceBindsEveryTargetAndOptionalBackfillUsesOnlyTheAuthorizedSource() throws Exception {
        String canonical=source("api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms");
        int start=canonical.indexOf("var applyTemplateSourceDefaults ="),end=canonical.indexOf("return db.transaction",start);
        String program="""
                import cn.hutool.json.JSONUtil as poolJsons
                import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as sourceTypes
                var text = value => value == null ? '' : value.toString()
                var blank = value => value == null || text(value).trim() == ''
                var isOdsSystemColumn = value => ['ODS_UUID','ODS_RKSJ','ODS_GXSJ'].contains(text(value).toUpperCase())
                var parseGovernanceConfig = value => poolJsons.parseObj(value)
                """+canonical.substring(start,end)+"\nreturn poolJsons.toJsonStr(instantiateTemplateFlow(template,pipelines,tasks))";
        var template=JSONUtil.parseObj("""
                {"spec":{"sourceType":"ANY","sourceSchedule":"REGISTERED","fetchSize":5000,"intervalSeconds":60,
                "flow":{"version":1,"nodes":[
                {"id":"source","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source"}},
                {"id":"backfill","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source:backfill","enabled":true,"customWherePredicate":"ID BETWEEN 1 AND 2","host":"wrong"}},
                {"id":"governance","manifestKey":"transform.field-enrichment","category":"transform","config":{"templateBinding":"governance:0","sharedGovernance":true}},
                {"id":"ob","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","insertThenMerge":true}},
                {"id":"hive","manifestKey":"sink.hive","category":"sink","config":{"templateBinding":"target:1"}}],
                "edges":[{"id":"main","source":"source","target":"governance"},{"id":"backfill","source":"backfill","target":"governance"},
                {"id":"ob","source":"governance","target":"ob"},{"id":"hive","source":"governance","target":"hive"}]}}}
                """);
        for(String sourceKind:List.of("ORACLE","FTP")) for(String keys:List.of("ID","","ODS_UUID")) {
            var sourceCfg=Map.<String,Object>of("dbType",sourceKind,"host","authorized-host","sourceTableId","authorized-source",
                    "registeredDatasourceId","source-db","incrementalColumn","UPDATE_TIME","password","validation-private");
            var pipelines=new java.util.ArrayList<Map<String,Object>>();
            for(int branch=0;branch<2;branch++) {
                var target=Map.<String,Object>of("targetDbId","target-db-"+branch,"targetTableId","target-"+branch,"table","target_table_"+branch,"updateKeys",keys,
                        "targetColumns",List.of(Map.of("columnName",branch==0?"ODS_UUID":"ods_uuid")),"password","not-a-public-snapshot");
                pipelines.add(Map.of("dsl_json",JSONUtil.toJsonStr(Map.of("nodes",List.of(
                        Map.of("id","old-source","manifestKey",sourceKind.equals("ORACLE")?"source.oracle":"source.ftp","category","source","config",sourceCfg),
                        Map.of("id","old-mapping","manifestKey","transform.field-enrichment","category","transform","config",Map.of("mappings","validation-mapping-"+branch)),
                        Map.of("id","old-target","manifestKey",branch==0?"sink.jdbc":"sink.hive","category","sink","config",target))))));
            }
            var result=JSONUtil.parseObj(MagicScript.create(program,null).execute(new MagicScriptContext(Map.of(
                    "template",template,"pipelines",pipelines,"tasks",List.of(Map.of("tid","validation-shared"))))).toString());
            var nodes=result.getJSONArray("nodes");
            assertThat(nodes).hasSize(sourceKind.equals("ORACLE")?5:4);
            var mapping=nodes.stream().map(JSONUtil::parseObj).filter(n->n.getStr("id").endsWith("-governance")).findFirst().orElseThrow().getJSONObject("config");
            assertThat(mapping.getStr("mappings")).isEqualTo("validation-mapping-0");
            var snapshots=mapping.getJSONArray("sharedTargets");
            assertThat(snapshots).hasSize(2);
            assertThat(snapshots.getJSONObject(0).getStr("nodeId")).isEqualTo("template-validation-shared-ob");
            assertThat(snapshots.getJSONObject(1).getStr("tableId")).isEqualTo("target-1");
            assertThat(snapshots.toString()).doesNotContain("password","not-a-public-snapshot");
            var sink=nodes.stream().map(JSONUtil::parseObj).filter(n->n.getStr("id").endsWith("-ob")).findFirst().orElseThrow().getJSONObject("config");
            assertThat(sink.getBool("insertThenMerge")).isEqualTo(keys.equals("ID"));
            assertThat(sink.getStr("updateKeys")).isEqualTo(keys.equals("ID")?"ID":"");
            if(sourceKind.equals("ORACLE")) {
                var backup=nodes.stream().map(JSONUtil::parseObj).filter(n->n.getStr("id").endsWith("-backfill")).findFirst().orElseThrow();
                assertThat(backup.getStr("manifestKey")).isEqualTo("source.oracle");
                var cfg=backup.getJSONObject("config");
                assertThat(cfg.getStr("host")).isEqualTo("authorized-host");
                assertThat(cfg.getStr("sourceTableId")).isEqualTo("authorized-source");
                assertThat(cfg.getBool("enabled")).isFalse();assertThat(cfg.getBool("backfillSource")).isTrue();
                assertThat(cfg.getStr("scheduledState")).isEqualTo("DISABLED");
                assertThat(cfg.getStr("incrementalColumn")).isEmpty();
                assertThat(cfg.getStr("syncMode")).isEqualTo("FULL");
                assertThat(cfg.getStr("customWherePredicate")).isEqualTo("ID BETWEEN 1 AND 2");
            } else {
                assertThat(result.getJSONArray("edges")).hasSize(3);
                assertThat(result.getJSONArray("edges").toString()).doesNotContain("backfill");
            }
            // Current templates keep replay solely inside the native source component.
            var nativeOnly=JSONUtil.parseObj(template.toString());
            var nativeFlow=nativeOnly.getJSONObject("spec").getJSONObject("flow");
            nativeFlow.getJSONArray("nodes").removeIf(value->"backfill".equals(JSONUtil.parseObj(value).getStr("id")));
            nativeFlow.getJSONArray("edges").removeIf(value->"backfill".equals(JSONUtil.parseObj(value).getStr("source")));
            nativeFlow.getJSONArray("nodes").getJSONObject(0).getJSONObject("config").set("manualBackfill",true);
            var nativeResult=JSONUtil.parseObj(MagicScript.create(program,null).execute(new MagicScriptContext(Map.of(
                    "template",nativeOnly,"pipelines",pipelines,"tasks",List.of(Map.of("tid","validation-shared"))))).toString());
            assertThat(nativeResult.getJSONArray("nodes")).hasSize(4);
            assertThat(nativeResult.getJSONArray("edges")).hasSize(3);
            assertThat(nativeResult.getJSONArray("nodes").getJSONObject(0).getJSONObject("config").getBool("manualBackfill"))
                    .isEqualTo(sourceKind.equals("ORACLE"));
            assertThat(nativeResult.getJSONArray("nodes").toString()).doesNotContain("source:backfill");
        }
    }

    @Test void templateAcceptsOnlyOneOptionalDisabledBackfillRootAndKeepsSinksTerminal() throws Exception {
        var fixture=new Fixture();when(fixture.scope.dataSource()).thenReturn(Map.of("scope","ALL"));
        var manifests=mock(ManifestRegistry.class);
        for(String[] type:new String[][]{{"source.oracle","source"},{"transform.field-enrichment","transform"},{"sink.jdbc","sink"},{"sink.hive","sink"}}) {
            var manifest=mock(ComponentManifest.class);when(manifest.category()).thenReturn(type[1]);when(manifests.get(type[0])).thenReturn(manifest);
        }
        var spec=JSONUtil.parseObj("""
                {"topology":"PARALLEL","sourceType":"ANY","targets":["ORACLE","HIVE"],"intervalSeconds":60,"fetchSize":5000,
                "flow":{"version":1,"nodes":[
                {"id":"source","manifestKey":"source.oracle","category":"source","config":{"templateBinding":"source"}},
                {"id":"backfill","manifestKey":"source.oracle","category":"source","config":{"templateBinding":"source:backfill","enabled":true}},
                {"id":"mapping","manifestKey":"transform.field-enrichment","category":"transform","config":{"templateBinding":"governance:0","sharedGovernance":true}},
                {"id":"ob","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","insertThenMerge":true}},
                {"id":"hive","manifestKey":"sink.hive","category":"sink","config":{"templateBinding":"target:1"}}],
                "edges":[{"id":"main","source":"source","target":"mapping"},{"id":"backfill","source":"backfill","target":"mapping"},
                {"id":"ob","source":"mapping","target":"ob"},{"id":"hive","source":"mapping","target":"hive"}]}}
                """);
        String program=source(MANAGE);program=program.substring(0,program.indexOf("var actor ="))+"\nreturn definition";
        program=program.replaceAll("(?m)^import (?:tenantRuntime|dataScope|tableRead|tableWrite|jsons|[^\\r\\n]+ as manifests)\\R","");
        var inputs=new HashMap<String,Object>(Map.of("body",Map.of("action","create","name","validation-shared","spec",spec),
                "tenantRuntime",new TenantMagicModule(null),"dataScope",fixture.scope,"manifests",manifests,"jsons",new TemplateJson()));
        try(var ignored=TenantContext.use("ga")) {
            var result=JSONUtil.parseObj(MagicScript.create(program,null).execute(new MagicScriptContext(inputs)).toString());
            var cfg=result.getJSONObject("flow").getJSONArray("nodes").getJSONObject(1).getJSONObject("config");
            assertThat(cfg.getBool("enabled")).isFalse();assertThat(cfg.getBool("backfillSource")).isTrue();
            assertThat(cfg.getStr("syncMode")).isEqualTo("FULL");assertThat(cfg.getStr("customWherePredicate")).isEqualTo("1=0");
            spec.getJSONObject("flow").getJSONArray("edges").add(Map.of("id","invalid-sink-edge","source","ob","target","hive"));
            assertThat(status(MagicScript.create(program,null).execute(new MagicScriptContext(inputs)))).isEqualTo(400);
        }
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

    @Test void selectionPageHydratesAuthorizedTargetsAndPinnedDefaultWithFourQueriesAtTwentyAndHundredRows() throws Exception {
        var fixture=new Fixture();
        for(int index=0;index<108;index++)fixture.insert("published-"+index,"ga","PUBLISHED");
        fixture.insert("foreign","ct","PUBLISHED");
        fixture.jdbc.update("UPDATE nifi_access_template_t SET default_tenant_id=tenant_id WHERE tid='published-0'");
        fixture.jdbc.execute("""
                CREATE TABLE db_datasource_t(tid varchar(32),tenant_id varchar(32),db_name varchar(64),db_type varchar(32),
                  is_del int,show_connect int,created_by varchar(32),org_id varchar(32))
                """);
        fixture.jdbc.execute("INSERT INTO db_datasource_t VALUES ('oracle','ga','ga-oracle','oceanbaseoracle',0,1,'owner','org'),('hive','ga','ga-hive','hive',0,1,'owner','org'),('hive','ct','forbidden-foreign','hive',0,1,'owner','org')");
        when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION),any())).thenAnswer(call->call.getArgument(1));
        try(var ignored=TenantContext.use("ga")){
            for(int size:List.of(20,100)){
                fixture.reads.set(0);
                var result=(Map<?,?>)fixture.run(LIST,Map.of("body",Map.of("pageSize",size,"includeTargets",true,"tenantId","ct")));
                assertThat(fixture.reads.get()).isEqualTo(4);
                assertThat((List<?>)result.get("list")).hasSize(size);
                for(var value:(List<?>)result.get("list")){
                    var item=(Map<?,?>)value;
                    assertThat(item.get("selectionReady")).isEqualTo(true);
                    assertThat(item.get("available")).isEqualTo(true);
                    assertThat((List<?>)item.get("targets")).hasSize(2);
                    assertThat(item.toString()).doesNotContain("forbidden-foreign");
                }
                var pinned=(Map<?,?>)result.get("defaultTemplate");
                assertThat(pinned.get("tid")).isEqualTo("published-0");
                assertThat((List<?>)pinned.get("targets")).hasSize(2);
            }
            when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION),any())).thenAnswer(call->
                call.<List<Map<String,Object>>>getArgument(1).stream().filter(row->row.get("tid").equals("oracle")).toList());
            var denied=(Map<?,?>)fixture.run(LIST,Map.of("body",Map.of("includeTargets",true)));
            for(var value:(List<?>)denied.get("list")){
                var item=(Map<?,?>)value;assertThat(item.get("available")).isEqualTo(false);
                assertThat((List<?>)item.get("targets")).isEmpty();
                assertThat(item.toString()).doesNotContain("ga-hive","forbidden-foreign");
            }
        }
    }

    @Test void listReadsPublishedTemplatesAndDefaultInTheTenantWithThreeQueriesAtBothPageSizes() throws Exception {
        var fixture = new Fixture();
        for (int index = 0; index < 108; index++) fixture.insert("published-" + index, "ga", "PUBLISHED");
        fixture.insert("foreign", "ct", "PUBLISHED");
        fixture.insert("draft", "ga", "DRAFT");
        fixture.insert("retired", "ga", "RETIRED");
        fixture.jdbc.update("UPDATE nifi_access_template_t SET default_tenant_id=tenant_id WHERE tid IN ('published-0','foreign')");
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
            var filtered = (Map<?, ?>) fixture.run(LIST, Map.of("body", Map.of("keyword", "published-77")));
            assertThat(filtered.get("defaultVersionId")).isEqualTo("published-0");
            fixture.jdbc.update("UPDATE nifi_access_template_t SET definition='{}' WHERE tid='published-0'");
            fixture.reads.set(0);
            var detail = (Map<?, ?>) fixture.run(DETAIL, Map.of("body", Map.of("tid", "published-0")));
            assertThat(detail.get("isDefault")).isEqualTo(true);
            assertThat(fixture.reads.get()).isEqualTo(1);
            assertThat(status(fixture.run(DETAIL, Map.of("body", Map.of("tid", "foreign"))))).isEqualTo(404);
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

    @Test void defaultMovesWithFamilyPublicationAndRejectsRetirementOfTheSelectedVersion() throws Exception {
        var fixture = new Fixture();
        fixture.insert("original", "ga", "PUBLISHED");
        fixture.insert("new-family", "ga", "PUBLISHED");
        fixture.insert("next-version", "ga", "DRAFT");
        fixture.insert("foreign", "ct", "PUBLISHED");
        fixture.jdbc.update("UPDATE nifi_access_template_t SET family_id='new-family',revision_no=2,definition=? WHERE tid='next-version'",
                JSONUtil.parseObj(DEFINITION).set("engine", "GENERATED").toString());
        fixture.jdbc.execute("""
                CREATE TABLE db_datasource_t(tid varchar(32),tenant_id varchar(32),db_type varchar(32),
                  pool_cfg clob,is_del int,show_connect int,created_by varchar(32),org_id varchar(32))
                """);
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('oracle','ga','oceanbase',?,0,1,'owner','org')",
                "{\"compatibleMode\":\"ORACLE\"}");
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('hive','ga','hive','{}',0,1,'owner','org')");
        when(fixture.scope.dataSource()).thenReturn(Map.of("scope", "ALL"));
        when(fixture.scope.visibleDataSourcesFor(eq(PERMISSION), any())).thenAnswer(call -> call.getArgument(1));
        try (var ignored = TenantContext.use("ga")) {
            assertThat(status(fixture.manage("default", "foreign"))).isEqualTo(404);
            assertThat(((Map<?, ?>) fixture.manage("default", "original")).get("isDefault")).isEqualTo(true);
            assertThat(status(fixture.manage("retire", "original"))).isEqualTo(409);
            fixture.manage("default", "new-family");
            fixture.manage("publish", "next-version");
            assertThat(fixture.jdbc.queryForList("SELECT tid FROM nifi_access_template_t WHERE default_tenant_id='ga'", String.class))
                    .containsExactly("next-version");
            assertThat(fixture.jdbc.queryForObject("SELECT status FROM nifi_access_template_t WHERE tid='new-family'", String.class))
                    .isEqualTo("RETIRED");
            assertThat(fixture.jdbc.queryForObject("SELECT status FROM nifi_access_template_t WHERE tid='original'", String.class))
                    .isEqualTo("PUBLISHED");
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
                     {"id":"source","manifestKey":"source.mysql","category":"source","config":{"templateBinding":"source","hostname":"wrong","topic":"wrong","remotePath":"wrong","schedulingPeriod":"999 sec","minioFilePatternScope":"OBJECT_KEY"}},
                     {"id":"sink","manifestKey":"sink.jdbc","category":"sink","config":{"templateBinding":"target:0","writerType":"","statementType":"MERGE"}}],
                     "edges":[{"id":"edge","source":"source","target":"sink"}]}}
                    """);
            var runtime=new java.util.LinkedHashMap<String,Object>(Map.<String,Object>of("hostname","registered-host","remotePath","/registered/events","topic","registered-topic",
                    "schedulingPeriod",type.equals("kafka")?"0 sec":"15 min","fileConsumptionPolicy","DELETE_AFTER_DATABASE_COMMIT",
                    "fileFormat","json","fileHeaderMode","registered-chinese","minioBucket","registered-bucket"));
            runtime.putAll(Map.of("minioObjectKey","registered/Report#2026.xlsx","excelSheetName","月#统计","jsonRecordPath","$.data.people","minioFilePatternScope","FILE_NAME"));
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

    @Test void reboundOpenSourceHiveUsesNativeHdfsWithoutReplacingExplicitJdbcOrRegisteredAuthentication() throws Exception {
        String canonical=source("api/03.数据接入/03.接入任务/05.按来源表补齐接入任务.ms");
        int start=canonical.indexOf("var applyTemplateSourceDefaults ="),end=canonical.indexOf("return db.transaction",start);
        String program="import cn.hutool.json.JSONUtil as poolJsons\nvar text = value => value == null ? '' : value.toString()\n"
                +"var blank = value => value == null || text(value).trim() == ''\n"
                +"var first = (row,keys) => { for(key in keys) { if(!blank(row[key])) { return row[key] } }; return null }\n"
                +"var parseGovernanceConfig = value => poolJsons.parseObj(value)\n"+canonical.substring(start,end)
                +"\nreturn poolJsons.toJsonStr(instantiateTemplateFlow(template,pipelines,tasks))";
        for(String mode:List.of("","LINEWELL_HDFS","LINEWELL","HDFS_BATCH","HIVE_RECORD_PUT")) {
            var preset=Map.of("templateBinding","target:0","hiveWriteMode",mode,"authMode","KERBEROS",
                    "clientConfigDir","/preset/hadoop","hdfsDatabaseDirectory","hdfs://preset/warehouse");
            var flow=Map.of("nodes",List.of(Map.of("id","source","manifestKey","source.mysql","category","source","config",Map.of("templateBinding","source")),
                    Map.of("id","sink","manifestKey","sink.hive","category","sink","config",preset)),"edges",List.of());
            var runtime=Map.of("targetTableId","authorized-target","hiveConnectionMode","open-source","authMode","none",
                    "clientConfigDir","/registered/hadoop","hdfsDatabaseDirectory","hdfs://registered/database.db");
            var dsl=Map.of("nodes",List.of(Map.of("id","old-source","manifestKey","source.mysql","category","source","config",Map.of()),
                    Map.of("id","old-target","manifestKey","sink.hive","category","sink","config",runtime)));
            var result=JSONUtil.parseObj(MagicScript.create(program,null).execute(new MagicScriptContext(Map.of(
                    "template",Map.of("spec",Map.of("flow",flow)),"pipelines",List.of(Map.of("dsl_json",JSONUtil.toJsonStr(dsl))),
                    "tasks",List.of(Map.of("tid","validation"))))).toString());
            var cfg=result.getJSONArray("nodes").getJSONObject(1).getJSONObject("config");
            assertThat(cfg.getStr("hiveWriteMode")).isEqualTo(mode.equals("HIVE_RECORD_PUT")?mode:"HDFS_BATCH");
            assertThat(cfg.getStr("authMode")).isEqualTo("none");
            assertThat(cfg.getStr("clientConfigDir")).isEqualTo("/registered/hadoop");
            assertThat(cfg.getStr("hdfsDatabaseDirectory")).isEqualTo("hdfs://registered/database.db");
            assertThat(cfg.getStr("targetTableId")).isEqualTo("authorized-target");
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
            jdbc.execute("""
                    CREATE TABLE nifi_access_template_t(tid varchar(32) primary key, tenant_id varchar(32),
                      family_id varchar(32), revision_no int, display_name varchar(96), status varchar(16),
                      definition clob, updated_at bigint, default_tenant_id varchar(32) unique,
                      check (default_tenant_id is null or (default_tenant_id=tenant_id and status='PUBLISHED')))
                    """);
        }

        void insert(String id, String tenant, String status) {
            jdbc.update("INSERT INTO nifi_access_template_t VALUES (?,?,?,1,?,?,?,0,null)", id, tenant, id, id, status, DEFINITION);
        }

        Object run(String file, Map<String, Object> input) throws Exception {
            String script = source(file).replaceAll("(?m)^import (?:dataScope|tableRead|tenantRuntime)\\R", "");
            var context = new HashMap<>(input);
            context.putAll(Map.of("dataScope", scope, "tenantRuntime", new TenantMagicModule(null), "tableRead", reader));
            return MagicScript.create(script, null).execute(new MagicScriptContext(context));
        }

        Object manage(String action, String id) throws Exception {
            var sources = new MagicDynamicDataSource();
            sources.setDefault(jdbc.getDataSource());
            var db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            String script = source(MANAGE).replaceAll("(?m)^import (?:dataScope|tableRead|tableWrite|tenantRuntime)\\R", "")
                    .replace("StpUtil.getLoginIdAsString()", "'validation-operator'");
            var context = Map.<String, Object>of("body", Map.of("action", action, "tid", id),
                    "db", db, "tableWrite", new StructuredTableWriteMagicModule(jdbc), "tableRead", reader,
                    "dataScope", scope, "tenantRuntime", new TenantMagicModule(null));
            return MagicScript.create(script, null).execute(new MagicScriptContext(context));
        }
    }
}
