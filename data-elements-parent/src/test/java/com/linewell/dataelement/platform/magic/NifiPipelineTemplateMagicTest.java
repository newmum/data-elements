package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.metautil.service.MetadataExplorerService;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiPipelineTemplateMagicTest {
    @Test
    void returnsRawNodeConfigAndPreservesRegisteredTimestampAndTargetKeys() throws Exception {
        var fixture = new Fixture();
        var input = fixture.node(Map.of("key", "source.jdbc", "tableId", "source-table"));
        assertThat(input.getStatusCode()).isEqualTo(HttpStatus.OK);
        var node = JSON.valueToTree(input.getBody());
        assertThat(node.path("manifestKey").asText()).isEqualTo("source.mysql");
        assertThat(node.path("config").path("incrementalColumn").asText()).isEqualTo("UPDATE_TIME");
        assertThat(node.path("config").path("sourceTableId").asText()).isEqualTo("source-table");
        assertThat(node.path("config").path("registeredDatasourceId").asText()).isEqualTo("source-db");
        assertThat(node.path("config").path("jdbcUrl").asText()).isEqualTo("jdbc:mysql://validation/input");

        node = JSON.valueToTree(fixture.node(Map.of("key", "sink.hive", "tableId", "target-table")).getBody());
        assertThat(node.path("manifestKey").asText()).isEqualTo("sink.jdbc");
        assertThat(node.path("config").path("statementType").asText()).isEqualTo("MERGE");
        assertThat(node.path("config").path("updateKeys").asText()).isEqualTo("CASE_ID");
        assertThat(node.path("config").path("table").asText()).isEqualTo("ODS_OWNER.ODS_CASE");
        assertThat(node.path("config").path("targetTableId").asText()).isEqualTo("target-table");
        assertThat(node.path("config").path("targetDbId").asText()).isEqualTo("target-db");
        verifyNoInteractions(fixture.explorer);
    }

    @Test
    void checksBothRegisteredTablesAndKeepsOdsMappingsInPhysicalOrder() throws Exception {
        var fixture = new Fixture();
        var body = new LinkedHashMap<String, Object>(Map.of("key", "transform.field-enrichment",
                "sourceTableId", "source-table", "targetTableId", "target-table", "tenantId", "foreign"));
        var transformation = fixture.node(body);
        assertThat(transformation.getStatusCode()).isEqualTo(HttpStatus.OK);
        var config = JSON.valueToTree(transformation.getBody()).path("config");
        assertThat(config.path("executionMode").asText()).isEqualTo("AUTO");
        var fields = new ArrayList<String>();
        JsonNode mappings = JSON.readTree(config.path("mappings").asText()).path("mappings");
        mappings.forEach(mapping -> fields.add(mapping.path("to").asText()));
        assertThat(fields).containsExactly("/CASE_ID", "/ODS_UUID", "/ODS_RKSJ", "/ODS_GXSJ");
        assertThat(mappings.get(1).path("expression").asText()).contains("TIMESTAMPDIFF(SECOND");
        body.put("targetTableId", "foreign-table");
        assertThat(fixture.node(body).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        body.put("targetTableId", "target-table");
        fixture.denied = "target-db";
        assertThat(fixture.node(body).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(fixture.explorer);
    }

    @Test
    void readsTwentyOrOneHundredRegisteredFieldsWithTheSameFourQueries() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("DELETE FROM db_table_column_t");
        for (int count : List.of(20, 100)) {
            int n = 0;
            for (String table : List.of("source-table", "target-table")) {
                for (int column = 0; column < count; column++) {
                    fixture.jdbc.update("""
                            INSERT INTO db_table_column_t (tid,table_id,tenant_id,is_del,column_name,ordinal_position)
                            VALUES (?,?, 'tenant-a', 0, ?, ?)
                            """, "field-" + n++, table, "FIELD_" + column, column);
                }
            }
            fixture.jdbc.update("""
                    INSERT INTO db_table_column_t (tid,table_id,tenant_id,is_del,column_name,ordinal_position)
                    VALUES ('foreign-field','target-table','foreign',0,'ODS_UUID',-1)
                    """);
            fixture.jdbc.update("UPDATE db_table_t SET field_governance_config=?",
                    JSON.writeValueAsString(Map.of("timestampField", "FIELD_1")));
            fixture.sql.sql.clear();
            fixture.sql.reads = 0;
            var response = fixture.node(Map.of("key", "transform.field-mapping",
                    "sourceTableId", "source-table", "targetTableId", "target-table"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var mappings = JSON.valueToTree(response.getBody()).path("config").path("mappings");
            assertThat(JSON.readTree(mappings.asText()).path("mappings")).hasSize(count);
            assertThat(fixture.sql.reads).isEqualTo(4);
            assertThat(fixture.sql.sql).allMatch(sql -> sql.contains("tenant_id"));
            fixture.jdbc.update("DELETE FROM db_table_column_t");
        }
        verifyNoInteractions(fixture.explorer);
    }

    @Test
    void buildsCanvasAndCompatibleSurveillanceTemplate() throws Exception {
        var fixture = new Fixture();
        var template = fixture.canvas(Map.of("tid", "catalog"));
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(template.getBody()).isInstanceOfSatisfying(Pipeline.class, canvas -> {
            assertThat(canvas.id()).isNull();
            assertThat(canvas.name()).isEqualTo("验证目录");
            assertThat(canvas.dsl().nodes()).extracting(Pipeline.Node::manifestKey)
                    .containsExactly("source.mysql", "transform.field-enrichment", "sink.jdbc");
            assertThat(canvas.dsl().edges()).hasSize(2);
        });
        var surveillance = fixture.canvas(Map.of("templateType", "STRUCTURED-SURVEILLANCE"));
        assertThat(surveillance.getBody()).isInstanceOfSatisfying(Pipeline.class, canvas -> {
            assertThat(canvas.dsl().nodes()).hasSize(9);
            assertThat(canvas.dsl().edges()).hasSize(6);
            assertThat(canvas.dsl().nodes().getFirst().config()).containsEntry("credentialRef", "credential://kafka/structured-surveillance");
            assertThat(canvas.dsl().nodes().stream().filter(node -> "transform.surveillance-match".equals(node.manifestKey())))
                    .hasSize(3).allSatisfy(node -> assertThat(node.config())
                            .containsEntry("matchUrl", "/api/v1/surveillance/match/batch"));
        });
        assertThat(fixture.canvas(Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fixture.canvas(Map.of("tid", "absent")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void choosesExistingReadableCanvasInOneTenantScopedJoin() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO nifi_pipeline_t (id,tenant_id,is_del,status,nifi_process_group_id,dsl_json)
                VALUES ('foreign','foreign',0,'RUNNING','foreign-group','{}'),
                       ('deleted','tenant-a',1,'RUNNING','deleted-group','{}'),
                       ('malformed','tenant-a',0,'RUNNING','broken-group','{'),
                       ('saved','tenant-a',0,'sToP','original-group','{"nodes":[],"edges":[]}')
                """);
        fixture.jdbc.update("""
                INSERT INTO data_access_agg_task_t (tid, tenant_id, is_del, data_catalog_id, pipeline_id, created_time)
                VALUES ('1','tenant-a',0,'catalog','foreign',CURRENT_TIMESTAMP),
                       ('2','tenant-a',0,'catalog','deleted',CURRENT_TIMESTAMP),
                       ('3','tenant-a',0,'catalog','malformed',CURRENT_TIMESTAMP),
                       ('4','tenant-a',0,'catalog','saved',CURRENT_TIMESTAMP)
                """);
        var response = fixture.canvas(Map.of("tid", "catalog"));
        assertThat(response.getBody()).isInstanceOfSatisfying(Pipeline.class, canvas -> {
            assertThat(canvas.id()).isEqualTo("saved");
            assertThat(canvas.nifiProcessGroupId()).isEqualTo("original-group");
            assertThat(canvas.status()).isEqualTo(PipelineStatus.STOPPED);
        });
        assertThat(fixture.structuredReads).singleElement().satisfies(sql ->
                assertThat(sql).contains("data_access_agg_task_t", "nifi_pipeline_t", "tenant_id"));
        verifyNoInteractions(fixture.explorer);
    }

    @Test
    void keepsRegisteredSourceVendorAliases() {
        assertThat(RegisteredDatasourceType.sourceManifest("kingbase8", Map.of())).isEqualTo("source.kingbase");
        assertThat(RegisteredDatasourceType.sourceManifest("oceanbaseoracle", Map.of())).isEqualTo("source.oceanbase");
        assertThat(RegisteredDatasourceType.sourceManifest("OCEANBASE_ORACLE", Map.of())).isEqualTo("source.oceanbase");
        assertThat(RegisteredDatasourceType.sourceManifest("oracle", Map.of())).isEqualTo("source.oracle");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> RegisteredDatasourceType.sourceManifest("unknown-vendor", Map.of()));
    }

    @Test
    void loadsRegisteredApiConfigWithAuthorizedFieldsAndWithoutPassword() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("UPDATE db_datasource_t SET db_type='api', pool_cfg=? WHERE tid='source-db'",
                "{\"apiPullItems\":[{\"tableName\":\"source_table\",\"url\":\"https://validation.invalid/data\"}]}");
        var response = fixture.node(Map.of("key", "source.api", "tableId", "source-table"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var node = JSON.valueToTree(response.getBody());
        assertThat(node.path("manifestKey").asText()).isEqualTo("source.api");
        var config = node.path("config");
        assertThat(config.path("url").asText()).isEqualTo("https://validation.invalid/data");
        assertThat(config.has("password")).isFalse();
        assertThat(config.path("sourceColumns").size()).isEqualTo(2);
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String COMMON = "function/01.通用函数/";
    private static final String API = "api/10.NiFi流程接口/01.接口/";

    private static class Fixture {
        final JdbcTemplate jdbc;
        final MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        final Map<String, Object> bindings = new LinkedHashMap<>();
        final List<String> structuredReads = new ArrayList<>();
        final TaskCategoryWarehouseScopeMagicTest.Counter sql = new TaskCategoryWarehouseScopeMagicTest.Counter();
        String denied = "";

        Fixture() throws Exception {
            var datasource = new DriverManagerDataSource("jdbc:h2:mem:nifi_template_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(datasource);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(tid varchar(32), tenant_id varchar(32), is_del int, db_name varchar(32),
                        db_type varchar(32), jdbc_url varchar(255), username varchar(32), password varchar(32), pool_cfg clob,
                        created_by varchar(32), org_id varchar(32));
                    CREATE TABLE db_table_t(tid varchar(32), tenant_id varchar(32), is_del int, datasource_id varchar(32),
                        table_name varchar(32), table_name_en varchar(32), table_name_cn varchar(32), table_comment varchar(32),
                        table_type varchar(32), field_governance_config clob);
                    CREATE TABLE db_table_column_t(tid varchar(32), table_id varchar(32), tenant_id varchar(32), is_del int,
                        column_name varchar(32), primary_key int, ordinal_position int, revision int, created_by varchar(32),
                        created_time timestamp, updated_by varchar(32), updated_time timestamp);
                    CREATE TABLE da_prop_t(tid varchar(32), parent_id varchar(32), tenant_id varchar(32), is_del int,
                        prop_name varchar(32), prop_value clob, updated_time timestamp);
                    CREATE TABLE da_catalog_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        created_by varchar(32), org_id varchar(32));
                    INSERT INTO da_catalog_t (tid,tenant_id,is_del) VALUES ('catalog','tenant-a',0);
                    CREATE TABLE data_access_agg_task_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        data_catalog_id varchar(32), pipeline_id varchar(32), created_time timestamp);
                    CREATE TABLE nifi_pipeline_t(id varchar(32), tenant_id varchar(32), is_del int, name varchar(32),
                        description varchar(32), created_at bigint, updated_at bigint, status varchar(32),
                        dsl_json clob, nifi_process_group_id varchar(32), last_deployed_hash varchar(32),
                        last_deployed_at bigint, last_stopped_at bigint, node_mapping_json clob, last_bulletin_id bigint);
                    INSERT INTO db_datasource_t (tid,tenant_id,is_del,db_name,db_type,jdbc_url,username,password,pool_cfg)
                      VALUES ('source-db','tenant-a',0,'input','mysql','jdbc:mysql://validation/input','test','fake','{}'),
                      ('target-db','tenant-a',0,'ods','oracle','jdbc:oracle:thin:@//validation/ods','test','fake','{"schema":"ods_owner"}');
                    INSERT INTO db_table_t (tid,tenant_id,is_del,datasource_id,table_name,field_governance_config)
                      VALUES ('source-table','tenant-a',0,'source-db','source_table','{"timestampField":"update_time"}'),
                      ('target-table','tenant-a',0,'target-db','ods_case','{}'),
                      ('foreign-table','foreign',0,'source-db','foreign_table','{}');
                    INSERT INTO db_table_column_t (tid,table_id,tenant_id,is_del,column_name,primary_key,ordinal_position)
                      VALUES ('s1','source-table','tenant-a',0,'CASE_ID',1,1), ('s2','source-table','tenant-a',0,'UPDATE_TIME',0,2),
                      ('t1','target-table','tenant-a',0,'CASE_ID',1,1), ('t2','target-table','tenant-a',0,'ODS_UUID',1,2),
                      ('t3','target-table','tenant-a',0,'ODS_RKSJ',0,3), ('t4','target-table','tenant-a',0,'ODS_GXSJ',0,4);
                    """);
            var dataSources = new MagicDynamicDataSource();
            dataSources.setDefault(datasource);
            var db = new SQLModule(dataSources);
            db.setDataSourceNode(dataSources.getDataSource());
            var mapping = new ColumnMapperAdapter();
            mapping.add(new DefaultColumnMapperProvider());
            mapping.add(new CamelColumnMapperProvider());
            mapping.setDefault("camel");
            db.setColumnMapperProvider(mapping);
            db.setColumnMapRowMapper(mapping.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(sql));
            var scope = mock(DataScopeMagicModule.class);
            when(scope.resource("MENU:2081000000000000002")).thenReturn(Map.of("scope", "ALL"));
            when(scope.visibleDataSourcesFor(eq("MENU:2081000000000000002"), any())).thenAnswer(invocation -> {
                List<Map<String, Object>> rows = invocation.getArgument(1);
                return rows.stream().filter(row -> !denied.equals(row.get("tid"))).toList();
            });
            bindings.putAll(Map.of("db", db, "tableRead", TaskRuntimeMagicFixture.structuredReads(jdbc, structuredReads::add), "tenantRuntime",
                    new TenantMagicModule(null), "mapper", JSON, "metadataExplorer", explorer, "dataScope", scope));
            register("respond", "nifi_pipe_list_response_v1", "35.NiFi流程列表HTTP响应.ms", "action");
            register("readAssets", "nifi_canvas_template_assets_v1", "37.NiFi画布模板资产.ms", "assetIds");
            register("fieldMappings", "nifi_canvas_template_mapping_v1", "38.NiFi模板字段映射.ms", "sourceColumns", "targetColumns");
            register("sourceNode", "nifi_canvas_template_source_v1", "39.NiFi模板来源节点.ms", "datasource", "tableAsset", "columns", "sourceTableName");
            register("hiveConnection", "nifi_canvas_template_hive_v1", "40.NiFi模板Hive连接.ms", "datasource", "config", "rawType", "isSink");
            register("targetNode", "nifi_canvas_template_sink_v1", "41.NiFi模板目标节点.ms", "datasource", "tableAsset", "columns", "explicitTableName");
            register("apiTemplate", "d410778b200d4fcca1d3f6da2a68fbd3", "23.NiFi画布API来源模板.ms", "datasource", "tableName");
            register("readSavedCanvas", "71a48eec67605a2d5da8ce93b2cc02cb", "42.NiFi目录关联画布.ms", "catalogId");
            bindings.put("readCatalog", (MagicScriptLambdaFunction) (context, args) -> "catalog".equals(((Map<?, ?>) args[0]).get("id"))
                    ? Map.of("tid", "catalog", "catalogName", "验证目录", "sourceTableId", "source-table", "targetTableId", "target-table") : null);
        }

        void register(String alias, String id, String path, String... parameterNames) throws Exception {
            var script = script(COMMON + path, id);
            bindings.put(alias, (MagicScriptLambdaFunction) (context, args) -> {
                var values = new LinkedHashMap<>(bindings);
                for (int i = 0; i < parameterNames.length; i++) {
                    values.put(parameterNames[i], args[i]);
                }
                return script.execute(new MagicScriptContext(values));
            });
        }

        ResponseEntity<?> canvas(Map<String, Object> body) throws Exception {
            return request("30.生成画布模板.ms", "nifi_canvas_template_post_v1", body);
        }

        ResponseEntity<?> node(Map<String, Object> body) throws Exception {
            return request("31.生成单节点模板.ms", "2e2c144806278f8c48faf98938ca63f5", body);
        }

        ResponseEntity<?> request(String path, String id, Map<String, Object> body) throws Exception {
            var script = script(API + path, id);
            var values = new LinkedHashMap<>(bindings);
            values.put("body", body);
            return TenantContext.call("tenant-a", () -> (ResponseEntity<?>) script.execute(new MagicScriptContext(values)));
        }

        private static MagicScript script(String path, String id) throws Exception {
            String source = CanonicalMagicSources.byPath(path, id);
            MagicScript.create(source, null).compile();
            return MagicScript.create(source.replaceAll("(?m)^import (?:'@[^\\r\\n]+|(?:tenantRuntime|dataScope|tableRead|jsons)|[^\\r\\n]+ as (?:mapper|metadataExplorer))\\R", ""), null);
        }
    }
}
