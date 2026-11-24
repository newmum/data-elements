package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyTransportUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacySourceShapeUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.WorkbookReaderUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyExactValueUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredDictionaryCatalog;
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.mapping.core.DictionarySpec;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.magic.runtime.route.MagicJsonBody;
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiMappingAssetReadMagicTest {
    private static final String COMMON = "function/01.通用函数/";
    private static final String API = "api/09.海通流程接口/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Timestamp CREATED = Timestamp.from(Instant.parse("2025-01-02T00:00:00Z"));

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesReadBoundedScopedRowsAndRejectMixedRegistrationReferences(int size) throws Throwable {
        var fixture = new Fixture();
        fixture.canvas("canvas", "tenant-a");
        fixture.canvas("other", "tenant-b");
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('db','tenant-a',0),('foreign-db','tenant-b',0)");
        for (int i = size - 1; i >= 0; i--) {
            String id = String.format("row-%03d", i);
            fixture.jdbc.update("INSERT INTO db_table_t VALUES (?,'db','tenant-a',0,1,?,?)",
                    id, "table" + id, "字面%_搜索");
            fixture.jdbc.update("INSERT INTO data_access_agg_task_t VALUES ('canvas','tenant-a',0,'db',NULL,?,NULL)", id);
            fixture.plan("plan-" + id, "tenant-a", "canvas", id, "{}");
            fixture.template("template-" + id, "tenant-a", "TEMPLATE", "{}");
        }
        fixture.jdbc.update("INSERT INTO db_table_t VALUES ('foreign','db','tenant-b',0,1,'first','字面%_搜索'),"
                + "('unrelated','db','tenant-a',0,1,'first','字面ab搜索')");
        fixture.plan("foreign", "tenant-b", "canvas", "first", "{}");
        fixture.plan("elsewhere", "tenant-a", "other", "first", "{}");
        fixture.template("foreign", "tenant-b", "TEMPLATE", "{}");
        fixture.template("dictionary", "tenant-a", "DICTIONARY", "{}");
        try (var tenant = TenantContext.use("tenant-a")) {
            var sorted = Map.of("pipelineId", "canvas", "sortField", "node_id", "sortDir", "asc", "size", "" + size);
            var plans = fixture.run("plans", sorted, Map.of());
            assertThat(fixture.queries).hasSize(6).allMatch(sql -> sql.contains("tenant_id"));
            assertThat(rows(plans)).hasSize(size);
            assertThat(rows(plans).getFirst()).containsEntry("node_id", "row-000").containsEntry("row_no", 1L);
            assertThat(page(plans)).containsEntry("total", (long) size);

            var second = fixture.run("plans", Map.of("pipelineId", "canvas", "sortField", "node_id", "sortDir", "asc",
                    "page", "2", "size", "10"), Map.of());
            assertThat(rows(second).getFirst()).containsEntry("node_id", "row-010").containsEntry("row_no", 11L);

            var templates = fixture.run("templates", Map.of("size", "" + size), Map.of());
            assertThat(rows(templates)).hasSize(size);
            assertThat(page(templates)).containsEntry("total", (long) size).doesNotContainKeys("page", "size");
            assertThat(fixture.queries).hasSize(2).allMatch(sql -> sql.contains("tenant_id"));

            var tables = fixture.run("tables", Map.of("datasourceId", "db", "search", "%_", "size", "" + size), Map.of());
            assertThat(rows(tables)).hasSize(size);
            assertThat(rows(tables).getFirst()).containsEntry("tid", "row-000").doesNotContainKey("row_no");
            assertThat(fixture.queries).hasSize(3).allMatch(sql -> sql.contains("tenant_id"));
            var foreign = fixture.run("tables", Map.of("datasourceId", "foreign-db"), Map.of());
            assertThat(JSON.valueToTree(foreign.getBody()).path("code").asInt()).isEqualTo(500);
            assertThat(fixture.queries).hasSize(1);

            fixture.jdbc.update("UPDATE data_access_agg_task_t SET source_table_id='foreign' WHERE source_table_id='row-000'");
            assertError(fixture.run("plans", sorted, Map.of()), "RESOURCE_FORBIDDEN");
            assertThat(fixture.queries).hasSize(3).noneMatch(sql -> sql.contains("data_access_mapping_plan_t"));
            verifyNoInteractions(fixture.mapping);
        }
    }

    @Test
    void immutableDetailsAndImportCheckHashesAndRevokedReferencesBeforeReturningOrApplyingBindings() throws Throwable {
        var fixture = new Fixture();
        fixture.canvas("canvas", "tenant-a");
        String source = """
                {"schemaVersion":"2.0","savedVersionMarker":"original","rules":[{"ruleId":"stored",
                "input":{"kind":"FIELD","name":"before"},"target":{"name":"output","logicalType":"STRING"},"steps":[],
                "verification":{"expression":"x ${field:before}"}}]}
                """;
        JsonNode document = JSON.readTree(source);
        fixture.plan("plan", "tenant-a", "canvas", "node", source);
        fixture.plan("another", "tenant-a", "outside-canvas", "node", source);
        fixture.template("template", "tenant-a", "TEMPLATE", source);
        fixture.template("foreign", "tenant-b", "TEMPLATE", source);
        fixture.template("corrupt", "tenant-a", "TEMPLATE", source);
        fixture.jdbc.update("UPDATE data_access_mapping_resource_t SET resource_hash='wrong' WHERE tid='corrupt'");
        when(fixture.mapping.compile(any())).thenAnswer(call -> new UnifiedMappingService.Compiled(
                MappingEngine.compile(call.getArgument(0)), Map.of()));
        var templatePath = Map.<String, Object>of("param", Map.of("id", "template"));
        var planPath = Map.<String, Object>of("param", Map.of("id", "plan"));
        var pipeline = Map.of("pipelineId", "canvas");
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(fixture.run("template", Map.of(), templatePath).getBody()).isEqualTo(document);
            assertThat(fixture.run("plan", pipeline, planPath).getBody()).isEqualTo(document);
            assertError(fixture.run("plan", pipeline, Map.of("param", Map.of("id", "another"))), "RESOURCE_FORBIDDEN");
            assertError(fixture.run("template", Map.of(), Map.of("param", Map.of("id", "foreign"))), "RESOURCE_FORBIDDEN");
            assertError(fixture.run("template", Map.of(), Map.of("param", Map.of("id", "corrupt"))), "RESOURCE_CORRUPTED");

            var input = Map.<String, Object>of("param", Map.of("id", "template"), "body",
                    JSON.readTree("{\"fieldBindings\":{\"before\":\"after\"}}"));
            var imported = fixture.run("import", Map.of(), input);
            assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode result = (JsonNode) imported.getBody();
            assertThat(result.at("/templateOrigin/templateId").asText()).isEqualTo("template");
            assertThat(result.at("/rules/0/input/name").asText()).isEqualTo("after");
            assertThat(result.at("/rules/0/verification/expression").asText()).isEqualTo("x ${field:after}");
            assertThat(result.at("/rules/0/ruleId").asText()).isNotBlank().isNotEqualTo("stored");
            assertThat(fixture.run("template", Map.of(), templatePath).getBody()).isEqualTo(document);

            doThrow(new MappingException("RESOURCE_FORBIDDEN", "revoked")).when(fixture.mapping).compile(document);
            assertError(fixture.run("template", Map.of(), templatePath), "RESOURCE_FORBIDDEN");
            assertError(fixture.run("import", Map.of(), input), "RESOURCE_FORBIDDEN");
            assertError(fixture.run("plan", pipeline, planPath), "RESOURCE_FORBIDDEN");

            fixture.jdbc.update("UPDATE data_access_mapping_plan_t SET plan_content=? WHERE tid='plan'", "密".repeat(750000));
            assertError(fixture.run("plan", pipeline, planPath), "BUDGET_EXCEEDED");
            fixture.jdbc.update("UPDATE data_access_mapping_plan_t SET plan_content=? WHERE tid='plan'", "x".repeat(2097153));
            assertError(fixture.run("plan", pipeline, planPath), "BUDGET_EXCEEDED");
        }
    }

    @Test
    void mappingBodyRetainsExactDecimalBeyondLifecycleCacheAndPreservesJsonErrors() throws Throwable {
        var fixture = new Fixture();
        var magic = new MagicConfiguration();
        magic.setHttpMessageConverters(List.of());
        SmartInitializingSingleton installer = ReflectionTestUtils.invokeMethod(
                new MagicBootConfiguration(), "magicMappingJsonConverter", magic, JSON);
        installer.afterSingletonsInstantiated();
        var converter = magic.getHttpMessageConverters().getFirst();
        var request = new MockHttpServletRequest("POST", "/nifi/template/import");
        var attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isFalse();
            String matched = MagicJsonBody.class.getName() + ".matched";
            request.setRequestURI("/validation/registered-mapping");
            request.setAttribute(matched, MagicJsonBody.EXACT_TREE);
            assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isTrue();
            String source = "{\"fraction\":0.123456789123456789,\"padding\":\"" + "a".repeat(70000) + "\"}";
            var input = new MockHttpInputMessage(source.getBytes(StandardCharsets.UTF_8));
            input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            MagicScriptLambdaFunction reader = (MagicScriptLambdaFunction) fixture.globals.get("nifiLifecycleBody");
            var decoded = (JsonNode) reader.apply(null, new Object[]{readJson(converter, input), JsonNode.class,
                    JsonNode.class.getName(), "unused"});
            assertThat(decoded.path("fraction").decimalValue()).isEqualByComparingTo(new BigDecimal("0.123456789123456789"));
            var malformed = new MockHttpInputMessage("{".getBytes(StandardCharsets.UTF_8));
            malformed.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> readJson(converter, malformed))
                    .isInstanceOf(HttpMessageNotReadableException.class);
            assertThat(request.getAttribute(MagicBootConfiguration.MAGIC_MAPPING_JSON_ERROR))
                    .isInstanceOf(HttpMessageNotReadableException.class);
            request.setAttribute(matched, MagicJsonBody.CACHED);
            assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isFalse();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            attributes.requestCompleted();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object readJson(org.springframework.http.converter.HttpMessageConverter<?> converter, MockHttpInputMessage input)
            throws java.io.IOException {
        return ((org.springframework.http.converter.HttpMessageConverter<Object>) converter).read(Object.class, input);
    }

    @Test
    void dictionaryColumnsUseSameAuthorizedRegisteredSchemaAsMappingEngine() throws Throwable {
        var fixture = new Fixture();
        JsonNode fields = JSON.readTree("[{\"name\":\"code\"}]");
        var ref = JSON.readTree("{\"datasourceId\":\"db\",\"tableId\":\"codes\"}");
        when(fixture.schemas.resolve(Map.of("dictionary", ref))).thenReturn(Map.of("dictionary",
                new RegisteredMappingSchemaService.Resolved("db", "codes", "validation", "codes", fields, Map.of())));
        assertThat(fixture.run("columns", Map.of("datasourceId", "db", "tableId", "codes"), Map.of()).getBody()).isEqualTo(fields);
    }

    @Test
    void schemaReadsBothSidesTogetherAndUsesPhysicalMetadataOnlyWhenRequested() throws Throwable {
        var fixture = new Fixture();
        ArrayNode fields = JSON.createArrayNode().add(JSON.createObjectNode().put("name", "code"));
        var bound = Map.of(
                "source", new RegisteredMappingSchemaService.Resolved("db", "codes", "validation", "codes", fields, Map.of()),
                "target", new RegisteredMappingSchemaService.Resolved("db", "out", "validation", "out", fields, Map.of()));
        var input = JSON.createObjectNode();
        input.putObject("sourceRef").put("datasourceId", "db").put("tableId", "codes");
        input.putObject("targetRef").put("datasourceId", "db").put("tableId", "out");
        var references = Map.of("source", input.path("sourceRef"), "target", input.path("targetRef"));
        when(fixture.schemas.resolve(references)).thenReturn(bound);
        when(fixture.schemas.physicalBatch(bound)).thenReturn(Map.of("source", fields, "target", fields));

        JsonNode registered = pageBody(fixture.run("schema", Map.of(), Map.of("body", input)));
        assertThat(registered.path("schemaOrigin").asText()).isEqualTo("REGISTERED");
        assertThat(registered.path("source")).isEqualTo(fields);
        assertThat(registered.path("targetHash").asText()).isEqualTo(MappingEngine.hash(fields));
        assertThat(registered.at("/targetRef/tableId").asText()).isEqualTo("out");
        verify(fixture.schemas, never()).physicalBatch(any());

        input.put("verifyPhysical", true);
        JsonNode physical = pageBody(fixture.run("schema", Map.of(), Map.of("body", input)));
        assertThat(physical.path("schemaOrigin").asText()).isEqualTo("PHYSICAL");
        verify(fixture.schemas, times(2)).resolve(references);
        verify(fixture.schemas).physicalBatch(bound);
        verify(fixture.schemas, never()).physical(any());

        doThrow(new MappingException("RESOURCE_FORBIDDEN", "denied")).when(fixture.schemas).resolve(references);
        assertError(fixture.run("schema", Map.of(), Map.of("body", input)), "RESOURCE_FORBIDDEN");
        verify(fixture.schemas, times(1)).physicalBatch(bound);
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void sharedSchemaReadsAllTargetsTogetherAndRejectsAnyUnauthorizedBranch(int size)throws Throwable {
        var fixture=new Fixture();var fields=JSON.createArrayNode();
        for(int i=0;i<size;i++)fields.addObject().put("name","field_"+i).put("logicalType","STRING");
        var input=JSON.createObjectNode().put("verifyPhysical",true);
        var primary=input.putObject("targetRef").put("datasourceId","oracle-db").put("tableId","oracle-table");
        var targets=input.putArray("targetRefs");
        targets.addObject().put("nodeId","ob").put("label","OB目标").set("reference",primary.deepCopy());
        var hive=targets.addObject().put("nodeId","hive").put("label","Hive目标").putObject("reference").put("datasourceId","hive-db").put("tableId","hive-table");
        var references=Map.<String,JsonNode>of("target",primary,"target:0",primary,"target:1",hive);
        var ob=new RegisteredMappingSchemaService.Resolved("oracle-db","oracle-table","validation","out",fields,Map.of());
        var hv=new RegisteredMappingSchemaService.Resolved("hive-db","hive-table","validation","out",fields,Map.of());
        var bound=Map.of("target",ob,"target:0",ob,"target:1",hv);
        when(fixture.schemas.resolve(references)).thenReturn(bound);
        when(fixture.schemas.physicalBatch(bound)).thenReturn(Map.of("target",fields,"target:0",fields,"target:1",fields));
        var result=pageBody(fixture.run("schema",Map.of(),Map.of("body",input)));
        assertThat(result.path("targets").size()).isEqualTo(2);
        assertThat(result.at("/targets/1/fields").size()).isEqualTo(size);
        assertThat(result.at("/targets/1/reference/tableId").asText()).isEqualTo("hive-table");
        verify(fixture.schemas).resolve(references);verify(fixture.schemas).physicalBatch(bound);
        doThrow(new MappingException("RESOURCE_FORBIDDEN","denied")).when(fixture.schemas).resolve(references);
        assertError(fixture.run("schema",Map.of(),Map.of("body",input)),"RESOURCE_FORBIDDEN");
        verify(fixture.schemas,times(1)).physicalBatch(bound);
        ((com.fasterxml.jackson.databind.node.ObjectNode)targets.get(1)).put("nodeId","ob");
        assertError(fixture.run("schema",Map.of(),Map.of("body",input)),"INVALID_SCHEMA");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void sampleMasksAllValuesAndMakesOneRegisteredSampleAndOnePreview(int size) throws Throwable {
        var fixture = new Fixture();
        var input = JSON.createObjectNode().put("limit", size);
        input.putObject("sourceRef").put("datasourceId", "db").put("tableId", "codes");
        input.putObject("spec").put("schemaVersion", "2.0");
        var table = new RegisteredMappingSchemaService.Resolved("db", "codes", "validation", "codes",
                JSON.createArrayNode(), Map.of());
        when(fixture.schemas.resolve(Map.of("source", input.path("sourceRef")))).thenReturn(Map.of("source", table));
        List<Map<String, Object>> sample = java.util.Collections.nCopies(size, Map.of("code", "business value"));
        when(fixture.schemas.sample(table, size)).thenReturn(sample);
        when(fixture.mapping.preview(input.path("spec"), sample)).thenReturn(new FieldMappingService.PreviewResponse(
                sample, List.of(), Map.of("inputRows", size)));
        JsonNode output = pageBody(fixture.run("sample", Map.of(), Map.of("body", input)));
        assertThat(output.path("sampleRows").size()).isEqualTo(size);
        assertThat(output.path("resultRows").size()).isEqualTo(size);
        assertThat(output.at("/sampleRows/0/code").asText()).isEqualTo("***");
        assertThat(output.at("/resultRows/0/code").asText()).isEqualTo("***");
        assertThat(output.at("/stats/masked").asBoolean()).isTrue();
        verify(fixture.schemas).sample(table, size);
        verify(fixture.mapping).preview(input.path("spec"), sample);
    }

    @Test
    void templateWritePreservesVersionsAndTenantScopeWithStructuredReads() throws Throwable {
        var fixture = new Fixture();
        when(fixture.mapping.compile(any())).thenAnswer(call -> new UnifiedMappingService.Compiled(
                MappingEngine.compile(call.getArgument(0)), Map.of()));
        var input = JSON.createObjectNode().put("name", "validation");
        var spec = input.putObject("spec").put("schemaVersion", "2.0").put("marker", "original");
        spec.putArray("rules");
        input.put("tenantId", "tenant-b");
        String id;
        try (var tenant = TenantContext.use("tenant-a")) {
            var result = fixture.run("saveTemplate", Map.of(), Map.of("body", input));
            assertThat(pageBody(result).has("error")).as("%s", pageBody(result)).isFalse();
            assertThat(pageBody(result).path("version").asLong()).isOne();
            assertThat(fixture.queries).hasSize(1).allMatch(sql -> sql.contains("tenant_id"));
            id = pageBody(result).path("id").asText();
            spec.put("marker", "changed");
            assertThat(pageBody(fixture.run("saveTemplate", Map.of(), Map.of("body", input))).path("version").asLong())
                    .isEqualTo(2);
            var stored = fixture.run("template", Map.of(), Map.of("param", Map.of("id", id)));
            assertThat(pageBody(stored).path("marker").asText()).isEqualTo("original");
            spec.put("token", "forbidden");
            assertError(fixture.run("saveTemplate", Map.of(), Map.of("body", input)), "TEMPLATE_SECRET_FORBIDDEN");
            assertThat(fixture.queries).isEmpty();
        }
        try (var tenant = TenantContext.use("tenant-b")) {
            assertError(fixture.run("template", Map.of(), Map.of("param", Map.of("id", id))), "RESOURCE_FORBIDDEN");
            assertThat(rows(fixture.run("templates", Map.of(), Map.of()))).isEmpty();
        }
    }

    @Test
    void dictionarySnapshotAuthenticatesLiveDefinitionAndWritesNormalizedVersion() throws Throwable {
        var fixture = new Fixture();
        var input = JSON.createObjectNode().put("name", "validation");
        var request = input.putObject("definition").put("tableId", "codes").put("snapshotId", "untrusted")
                .put("freshnessMode", "SNAPSHOT").put("definitionHash", "untrusted");
        var definition = request.deepCopy();
        definition.remove(List.of("snapshotId", "definitionHash"));
        definition.put("freshnessMode", "LIVE_BATCH");
        var draft = JSON.createObjectNode().put("schemaVersion", "2.0");
        draft.putArray("rules");
        draft.putObject("dictionaryBindings").set("requested", definition);
        var normalized = draft.deepCopy();
        normalized.putObject("dictionaryBindings").putObject("requested").put("definitionHash", "authorized");
        var plan = mock(MappingEngine.Plan.class);
        var dictionary = new DictionarySpec("requested", "db", "codes", "", "codes", List.of("code"),
                List.of("STRING"), "label", null, "ERROR");
        when(plan.dictionaries()).thenReturn(java.util.Collections.unmodifiableMap(Map.of("requested", dictionary)));
        when(plan.document()).thenReturn(normalized);
        var compiled = new UnifiedMappingService.Compiled(plan, Map.of());
        when(fixture.mapping.compile(draft)).thenReturn(compiled);
        var payload = JSON.createObjectNode().put("version", 1);
        when(fixture.mapping.readSnapshot(compiled, "requested")).thenReturn(payload);
        try (var tenant = TenantContext.use("tenant-a")) {
            var response = fixture.run("snapshot", Map.of(), Map.of("body", input));
            assertThat(pageBody(response).path("kind").asText()).isEqualTo("DICTIONARY_SNAPSHOT");
            String stored = fixture.jdbc.queryForObject("SELECT definition_json FROM data_access_mapping_resource_t", String.class);
            assertThat(JSON.readTree(stored).path("definitionHash").asText()).isEqualTo("authorized");
            assertThat(fixture.queries).hasSize(1);
            verify(fixture.mapping).readSnapshot(compiled, "requested");
        }
    }

    @Test
    void concurrentVersionConflictRollsBackAndRetriesAgainstTheCommittedWinner() throws Throwable {
        var fixture = new Fixture();
        var once = new AtomicBoolean();
        var db = (SQLModule) fixture.globals.get("db");
        db.setNamedTableInterceptors(List.of((mode, table) -> {
            if (once.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> fixture.jdbc.update("""
                        INSERT INTO data_access_mapping_resource_t
                        (tid,tenant_id,resource_kind,resource_key,version_no,resource_hash,created_time,resource_content,definition_json)
                        VALUES ('winner','tenant-a','TEMPLATE','validation',1,'digest',CURRENT_TIMESTAMP,'{}','{}')
                        """)).orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
            }
        }));
        when(fixture.mapping.compile(any())).thenAnswer(call -> new UnifiedMappingService.Compiled(
                MappingEngine.compile(call.getArgument(0)), Map.of()));
        var input = JSON.createObjectNode().put("name", "validation");
        input.putObject("spec").put("schemaVersion", "2.0").putArray("rules");
        try (var tenant = TenantContext.use("tenant-a")) {
            var response = fixture.run("saveTemplate", Map.of(), Map.of("body", input));
            assertThat(pageBody(response).path("version").asLong()).isEqualTo(2);
            assertThat(fixture.queries).hasSize(2);
            assertThat(fixture.jdbc.queryForList("SELECT version_no FROM data_access_mapping_resource_t ORDER BY version_no",
                    Integer.class)).containsExactly(1, 2);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void publishedStartBatchesReferencesAndReauthorizesEveryImmutablePlanBeforeNativeReads(int size) throws Throwable {
        var fixture = new Fixture();
        fixture.nativePublishedGraph(size);
        var document = JSON.createObjectNode().put("schemaVersion", "2.0").put("marker", "original");
        for (int index = 0; index < size; index++) {
            String id = "plan-" + index;
            fixture.publishedPlan(id, "node-" + index, document);
        }
        fixture.plan("outside", "tenant-b", "canvas", "foreign-node", document.toString());
        fixture.plan("wrong-canvas", "tenant-a", "outside", "node", document.toString());
        fixture.jdbc.update("INSERT INTO data_access_mapping_publication_t VALUES ('tenant-b','canvas','foreign-node','outside'),"
                + "('tenant-a','outside','unrelated','wrong-canvas')");
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("expectedUpdatedAt", 5);
        String savedDraft = fixture.savedDraft();
        try (var tenant = TenantContext.use("tenant-a")) {
            var valid = fixture.run("published", Map.of(), Map.of("body", request));
            assertThat(page(valid)).containsEntry("draftPublished", false).containsEntry("mode", "PUBLISHED_NATIVE_GRAPH")
                    .containsEntry("oneShotSourceCount", 1);
            assertThat(fixture.queries).hasSize(4).allMatch(sql -> sql.contains("tenant_id"));
            verify(fixture.mapping).compileBatch(org.mockito.ArgumentMatchers.argThat(plans ->
                    plans.size() == size && document.equals(plans.get("plan-0"))));
            verify(fixture.nifi).getProcessGroup("group");
            verify(fixture.nifi).get("/flow/process-groups/group", JsonNode.class);
            verify(fixture.nifi).put(eq("/flow/process-groups/group"),
                    org.mockito.ArgumentMatchers.argThat((JsonNode command) -> command.path("state").asText().equals("RUNNING")
                            && command.path("components").size() == size
                            && !command.path("components").has("source")), eq(JsonNode.class));
            verify(fixture.nifi).put(eq("/processors/source/run-status"),
                    org.mockito.ArgumentMatchers.argThat((JsonNode command) -> command.path("state").asText().equals("RUN_ONCE")
                            && command.path("revision").path("version").asInt() == 1), eq(JsonNode.class));
            verifyNoMoreInteractions(fixture.nifi);
            assertThat(fixture.runtimeStatus()).isEqualTo("RUNNING");
            assertThat(fixture.savedDraft()).isEqualTo(savedDraft);
            request.put("expectedUpdatedAt", fixture.runtimeVersion());
            org.mockito.Mockito.clearInvocations(fixture.nifi, fixture.mapping);
            doThrow(new MappingException("RESOURCE_FORBIDDEN", "revoked")).when(fixture.mapping).compileBatch(any());
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), "RESOURCE_FORBIDDEN");
            verifyNoInteractions(fixture.nifi);
            org.mockito.Mockito.clearInvocations(fixture.mapping);
            for (String invalid : List.of("outside", "wrong-canvas", "missing")) {
                fixture.jdbc.update("UPDATE data_access_mapping_publication_t SET plan_id=? WHERE tenant_id='tenant-a' AND node_id='node-0'", invalid);
                assertError(fixture.run("published", Map.of(), Map.of("body", request)), "RESOURCE_FORBIDDEN");
                assertThat(fixture.queries).hasSize(4);
            }
            fixture.jdbc.update("UPDATE data_access_mapping_publication_t SET plan_id='plan-0' WHERE tenant_id='tenant-a' AND node_id='node-0'");
            fixture.jdbc.update("INSERT INTO data_access_mapping_publication_t VALUES ('tenant-a','canvas','node-0','plan-1')");
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), size == 100 ? "BUDGET_EXCEEDED" : "RESOURCE_CORRUPTED");
            verifyNoInteractions(fixture.mapping, fixture.nifi);
        }
    }

    @Test
    void publishedStartChecksTheEmbeddedReferenceInTheSingleChildScope() throws Throwable {
        var fixture = new Fixture();
        var root = fixture.nativePublishedGraph(1);
        fixture.publishedPlan("plan-0", "node-0", JSON.createObjectNode());
        var processors = (ArrayNode) root.path("processors");
        var transform = processors.remove(1);
        var properties = (ObjectNode) transform.path("component").path("config").path("properties");
        properties.remove("plan-id");
        properties.put("execution-plan", "{\"_deployment\":{\"planId\":\"plan-0\"}}");
        ((ArrayNode) root.path("processGroups")).addObject().put("id", "child");
        var child = JSON.createObjectNode();
        child.putArray("processGroups");
        child.putArray("connections");
        child.putArray("processors").add(transform);
        fixture.stubFlow("child", child);
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("expectedUpdatedAt", 5);
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(page(fixture.run("published", Map.of(), Map.of("body", request)))).containsEntry("draftPublished", false);
            verify(fixture.nifi).get("/flow/process-groups/group", JsonNode.class);
            verify(fixture.nifi).get("/flow/process-groups/child", JsonNode.class);
            verify(fixture.nifi).put(eq("/flow/process-groups/child"), any(), eq(JsonNode.class));
            verify(fixture.nifi).put(eq("/processors/source/run-status"), any(), eq(JsonNode.class));
            request.put("expectedUpdatedAt", fixture.runtimeVersion());
            org.mockito.Mockito.clearInvocations(fixture.nifi);
            properties.put("execution-plan", "{\"_deployment\":{\"planId\":\"foreign\"}}");
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), "PLAN_VERSION_CONFLICT");
            verify(fixture.nifi, never()).put(any(), any(), any());
        }
    }

    @Test
    void publishedStartDeniesAnUnownedGraphMissingSourceOrUnknownSuccessBranchBeforeWriting() throws Throwable {
        var fixture = new Fixture();
        var root = fixture.nativePublishedGraph(1);
        fixture.publishedPlan("plan-0", "node-0", JSON.createObjectNode());
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("expectedUpdatedAt", 5);
        try (var tenant = TenantContext.use("tenant-a")) {
            fixture.stubGroupOwner("tenant-b");
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), "RESOURCE_FORBIDDEN");
            verify(fixture.nifi, never()).get(any(), any());
            fixture.stubGroupOwner("tenant-a");
            var processors = (ArrayNode) root.path("processors");
            var source = processors.remove(0);
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), "RUNTIME_UNAVAILABLE");
            processors.insert(0, source);
            ((ArrayNode) root.path("connections")).add(JSON.readTree("""
                    {"id":"untracked","component":{"source":{"id":"source"},"destination":{"id":"candidate"},
                    "selectedRelationships":["success"]}}
                    """));
            assertError(fixture.run("published", Map.of(), Map.of("body", request)), "MAPPING_UPGRADE_RECOVERY_REQUIRED");
            verify(fixture.nifi, never()).put(any(), any(), any());
            assertThat(fixture.runtimeVersion()).isEqualTo(5L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publishedStartCompensatesAfterNativeWriteFailure(boolean stopConfirmed) throws Throwable {
        var fixture = new Fixture();
        fixture.nativePublishedGraph(1);
        fixture.publishedPlan("plan-0", "node-0", JSON.createObjectNode());
        var savedDraft = fixture.savedDraft();
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("expectedUpdatedAt", 5);
        doThrow(new IllegalStateException("validation start failed")).when(fixture.nifi)
                .put(eq("/processors/source/run-status"), any(), eq(JsonNode.class));
        if (!stopConfirmed) {
            doThrow(new IllegalStateException("validation stop unconfirmed")).when(fixture.nifi)
                    .waitForAllProcessorsStopped("group", 30_000L);
        }
        try (var tenant = TenantContext.use("tenant-a")) {
            assertError(fixture.run("published", Map.of(), Map.of("body", request)),
                    stopConfirmed ? "PUBLISHED_START_FAILED" : "MAPPING_UPGRADE_RECOVERY_REQUIRED");
        }
        verify(fixture.nifi).put(eq("/flow/process-groups/group"), any(), eq(JsonNode.class));
        verify(fixture.nifi).setProcessGroupState("group", "STOPPED");
        verify(fixture.nifi).waitForAllProcessorsStopped("group", 30_000L);
        assertThat(fixture.savedDraft()).isEqualTo(savedDraft);
        assertThat(fixture.runtimeStatus()).isEqualTo("STOPPED");
        if (stopConfirmed) {
            assertThat(fixture.runtimeVersion()).isGreaterThan(5L);
        } else {
            assertThat(fixture.runtimeVersion()).isEqualTo(5L);
        }
    }

    @Test
    void planBatchRejectsLargeAggregateAndDeduplicatesExactPlanIds() throws Throwable {
        var fixture = new Fixture();
        var document = JSON.createObjectNode().put("value", "x".repeat(2_000_000));
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            String id = "plan-" + index;
            fixture.plan(id, "tenant-a", "canvas", "node", document.toString());
            ids.add(id);
        }
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(fixture.planBatch(List.of("plan-0", "plan-0"))).containsOnlyKeys("plan-0");
            assertThat(fixture.planBatch(List.of())).isEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.planBatch(ids))
                    .hasMessageContaining("32MiB");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.planBatch(java.util.Collections.nCopies(101, "plan-0")))
                    .hasMessageContaining("计划引用最多100项");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.planBatch(List.of("plan-0", "")))
                    .hasMessageContaining("计划引用无效");
        }
    }

    @Test
    void draftRestoreChecksSharedLockAndRevisionAndRollsBackProjectionWithDraft() throws Throwable {
        var fixture = new Fixture();
        fixture.nativeCanvas();
        var original = fixture.sampleCanvas();
        var restored = JSON.createObjectNode().put("schemaVersion", "2.0").put("marker", "original");
        fixture.plan("plan", "tenant-a", "canvas", "mapping", restored.toString());
        var input = JSON.createObjectNode().put("pipelineId", "canvas").put("nodeId", "mapping").put("expectedUpdatedAt", 5);
        var request = Map.<String, Object>of("body", input, "param", Map.of("id", "plan"));
        try (var tenant = TenantContext.use("tenant-a")) {
            fixture.lockAvailable.set(false);
            var locked = fixture.run("restore", Map.of(), request);
            assertThat(pageBody(locked).path("msg").asText()).contains("409 CONFLICT");
            assertThat(fixture.queries).isEmpty();
            verifyNoInteractions(fixture.repository, fixture.projections, fixture.mapping);

            fixture.lockAvailable.set(true);
            input.put("expectedUpdatedAt", 4);
            assertError(fixture.run("restore", Map.of(), request), "DRAFT_VERSION_CONFLICT");
            verifyNoInteractions(fixture.repository, fixture.projections, fixture.mapping);
            input.put("expectedUpdatedAt", 5);
            input.put("nodeId", "outside");
            assertError(fixture.run("restore", Map.of(), request), "RESOURCE_FORBIDDEN");
            verifyNoInteractions(fixture.repository, fixture.projections);
            input.put("nodeId", "mapping");

            org.mockito.Mockito.doAnswer(call -> {
                fixture.jdbc.update("INSERT INTO validation_projection VALUES ('mapping')");
                return null;
            }).when(fixture.projections).persist(any());
            var conflict = new MappingException("DRAFT_VERSION_CONFLICT", "流程已被其他入口修改");
            org.mockito.Mockito.doAnswer(call -> {
                java.util.function.UnaryOperator<Pipeline> update = call.getArgument(1);
                update.apply(original);
                throw conflict;
            }).when(fixture.repository).update(eq("canvas"), any());
            assertError(fixture.run("restore", Map.of(), request), "DRAFT_VERSION_CONFLICT");
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM validation_projection",Integer.class)).isZero();
            assertThat(fixture.canvasRecord()).isEqualTo(original);

            org.mockito.Mockito.doAnswer(call -> {
                java.util.function.UnaryOperator<Pipeline> update = call.getArgument(1);
                return update.apply(original);
            }).when(fixture.repository).update(eq("canvas"), any());
            var successful = fixture.run("restore", Map.of(), request);
            assertThat(successful.getStatusCode()).as("%s", successful.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(successful.getBody()).isInstanceOf(Pipeline.class);
            Pipeline changed = (Pipeline) successful.getBody();
            assertThat(changed.dsl().nodes().getFirst()).isEqualTo(original.dsl().nodes().getFirst());
            assertThat(changed.dsl().edges()).isEqualTo(original.dsl().edges());
            assertThat(changed.dsl().nodes().get(1).config()).containsEntry("mappings", restored.toString()).containsEntry("custom", true);
            assertThat(changed.nifiProcessGroupId()).isEqualTo(original.nifiProcessGroupId());
            assertThat(changed.nodeMapping()).isEqualTo(original.nodeMapping());
            assertThat(changed.status()).isEqualTo(original.status());
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM validation_projection",Integer.class)).isOne();
        }
    }

    @Test
    void mysqlTransportUpgradeReusesTenantScopeAndRequiresTheCurrentDraftVersion() throws Throwable {
        var fixture = new Fixture();
        fixture.nativeCanvas();
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("nodeId", "source")
                .put("expectedUpdatedAt", 5).put("mode", "mysql-read-pool").put("dryRun", true)
                .put("tenantId", "foreign-tenant").put("expectedFingerprint", "existing-preview");
        when(fixture.transportUpgrade.upgradeMySqlReadPool(any(), eq("tenant-a"), eq("source"), eq(true), eq("existing-preview")))
                .thenReturn(Map.of("dryRun", true, "started", false));
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(page(fixture.run("upgrade", Map.of(), Map.of("body", request)))).containsEntry("dryRun", true);
            request.put("start", true);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "MIGRATION_UNAVAILABLE");
            request.put("start", false).put("expectedUpdatedAt", 4);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "DRAFT_VERSION_CONFLICT");
        }
        verify(fixture.transportUpgrade).upgradeMySqlReadPool(any(), eq("tenant-a"), eq("source"), eq(true), eq("existing-preview"));
        verifyNoInteractions(fixture.nativeUpgrade);
    }

    @Test
    void legacyTransportModesUseTrustedTenantAndNeverStartTheSource() throws Throwable {
        var fixture = new Fixture();
        fixture.nativeCanvas();
        var request = JSON.createObjectNode().put("pipelineId", "canvas").put("nodeId", "source")
                .put("expectedUpdatedAt", 5).put("mode", "source-normalization").put("dryRun", true)
                .put("tenantId", "other").put("expectedFingerprint", "preview");
        when(fixture.shapeUpgrade.upgrade(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview")))
                .thenReturn(Map.of("dryRun", true, "started", false));
        when(fixture.exactUpgrade.upgrade(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview")))
                .thenReturn(Map.of("dryRun", true, "started", false));
        when(fixture.shapeUpgrade.upgradeWorkbookRows(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview")))
                .thenReturn(Map.of("dryRun", true, "started", false));
        when(fixture.workbookReaderUpgrade.upgrade(any(), eq("tenant-a"), eq("source"), eq("retained"), eq(true), eq("preview")))
                .thenReturn(Map.of("dryRun", true, "started", false));
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(page(fixture.run("upgrade", Map.of(), Map.of("body", request)))).containsEntry("dryRun", true);
            request.put("start", true);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "MIGRATION_UNAVAILABLE");
            request.put("start", false).put("mode", "exact-value-readers");
            assertThat(page(fixture.run("upgrade", Map.of(), Map.of("body", request)))).containsEntry("dryRun", true);
            request.put("start", true);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "MIGRATION_UNAVAILABLE");
            request.put("start", false).put("mode", "workbook-record-policy");
            assertThat(page(fixture.run("upgrade", Map.of(), Map.of("body", request)))).containsEntry("dryRun", true);
            request.put("start", true);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "MIGRATION_UNAVAILABLE");
            request.put("start", false).put("mode", "workbook-reader").put("retainedFailureConnectionId", "retained");
            assertThat(page(fixture.run("upgrade", Map.of(), Map.of("body", request)))).containsEntry("dryRun", true);
            request.put("start", true);
            assertError(fixture.run("upgrade", Map.of(), Map.of("body", request)), "MIGRATION_UNAVAILABLE");
        }
        verify(fixture.shapeUpgrade).upgrade(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview"));
        verify(fixture.exactUpgrade).upgrade(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview"));
        verify(fixture.shapeUpgrade).upgradeWorkbookRows(any(), eq("tenant-a"), eq("source"), eq(true), eq("preview"));
        verify(fixture.workbookReaderUpgrade).upgrade(any(), eq("tenant-a"), eq("source"), eq("retained"), eq(true), eq("preview"));
        verifyNoInteractions(fixture.nativeUpgrade, fixture.transportUpgrade);
    }

    @Test
    void upgradePreparesTheSavedRulesAndRejectsMismatchedPlanBeforeReplacement() throws Throwable {
        var fixture = new Fixture();
        fixture.nativeCanvas();
        var request = JSON.createObjectNode().put("pipelineId","canvas").put("nodeId","mapping")
                .put("expectedUpdatedAt",5).put("expectedPlanHash","mismatch").put("start",true);
        JsonNode specification = JSON.readTree("{\"schemaVersion\":\"2.0\",\"rules\":[]}");
        var compiled = new UnifiedMappingService.Compiled(MappingEngine.compile(specification),Map.of());
        when(fixture.mapping.compile(specification)).thenReturn(compiled);
        when(fixture.nativeUpgrade.replaceNode(any(),any(),eq("tenant-a"),eq(true),any())).thenAnswer(call -> {
            java.util.function.Supplier<UnifiedMappingService.Compiled> prepare = call.getArgument(4);
            assertThat(prepare.get()).isSameAs(compiled);
            return Map.of("sourceProcessorIdsPreserved", true);
        });
        try (var tenant = TenantContext.use("tenant-a")) {
            assertError(fixture.run("upgrade",Map.of(),Map.of("body",request)),"DRAFT_NOT_SAVED");
            request.put("expectedPlanHash",compiled.plan().hash());
            assertThat(page(fixture.run("upgrade",Map.of(),Map.of("body",request))))
                    .containsEntry("sourceProcessorIdsPreserved",true);
            request.put("nodeId","source");
            assertError(fixture.run("upgrade",Map.of(),Map.of("body",request)),"RESOURCE_FORBIDDEN");
            verify(fixture.nativeUpgrade,times(2)).replaceNode(any(),any(),eq("tenant-a"),eq(true),any());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void compilerAndPreviewPreserveExactDecimalsAndUseTheSameRegisteredPlan(int size) throws Throwable {
        var fixture = new Fixture();
        var catalog = mock(RegisteredDictionaryCatalog.class);
        when(catalog.bind(any())).thenAnswer(call -> new RegisteredDictionaryCatalog.Bound(call.getArgument(0), Map.of()));
        var engine = new UnifiedMappingService(catalog, new FieldMappingService());
        fixture.globals.put("mapping", engine);
        JsonNode spec = JSON.readTree("""
                {"schemaVersion":"2.0","rules":[{"ruleId":"decimal-rule","input":{"kind":"FIELD","name":"decimal"},
                "target":{"name":"converted","logicalType":"DECIMAL","precision":38,"scale":18},"steps":[]}]}
                """);
        var exact = new BigDecimal("12345678901234567890.123456789012345678");
        var samples = JSON.createArrayNode();
        for (int index=0; index<size; index++) {
            samples.addObject().put("decimal", exact);
        }
        try (var tenant = TenantContext.use("tenant-a")) {
            var input = JSON.createObjectNode().set("spec",spec);
            var compile = fixture.run("compileRules",Map.of(),Map.of("body",input));
            assertThat(compile.getBody()).as("%s",pageBody(compile)).isInstanceOf(Map.class);
            assertThat(page(compile)).containsEntry("mode","ENGINE_PLAN").containsEntry("schemaVerification","REGISTERED_ONLY")
                    .containsEntry("executable",true);
            var expectedHash = pageBody(compile).at("/plan/planHash").asText();
            ((com.fasterxml.jackson.databind.node.ObjectNode) input).set("sampleRows",samples);
            var preview = fixture.run("previewRules",Map.of(),Map.of("body",input));
            assertThat(preview.getBody()).isInstanceOf(FieldMappingService.PreviewResponse.class);
            var result = (FieldMappingService.PreviewResponse) preview.getBody();
            assertThat(result.resultRows()).hasSize(size);
            assertThat((BigDecimal) result.resultRows().getFirst().get("converted")).isEqualByComparingTo(exact);
            assertThat(result.stats()).containsEntry("planHash",expectedHash).containsEntry("dictionarySqlCount",0);
            assertThat(fixture.queries).isEmpty();
            verify(catalog,times(2)).bind(any());
        } finally {
            engine.close();
        }
    }

    @Test
    void validationBindingAndCapabilityRoutesPreserveThePublicContracts() throws Throwable {
        var fixture = new Fixture();
        var caps = fixture.run("capabilities",Map.of(),Map.of());
        assertThat(page(caps)).containsEntry("engineVersion",MappingEngine.engineVersion()).containsEntry("maxPreviewRows",100);
        var functions = pageBody(caps).path("functions");
        assertThat(functions.size()).isEqualTo(com.linewell.dataelement.mapping.core.FunctionRegistry.NAMES.size());
        assertThat(functions.findValuesAsText("name")).contains("uuid", "expression", "codeTableLookup");

        var input = JSON.createObjectNode();
        input.putObject("spec").put("schemaVersion","2.0");
        input.putArray("sourceSchema").addObject().put("name","code");
        doThrow(new MappingException("RESOURCE_FORBIDDEN","registration revoked")).when(fixture.mapping).compileForEditing(any());
        var invalid = fixture.run("validateRules",Map.of(),Map.of("body",input));
        JsonNode validation = pageBody(invalid);
        assertThat(validation.path("valid").asBoolean(true)).isFalse();
        assertThat(validation.at("/errors/0/code").asText()).isEqualTo("RESOURCE_FORBIDDEN");
        assertThat(validation.at("/schemaCompare/sourceFields/0/name").asText()).isEqualTo("code");
        input.putObject("sourceSchema").put("field","invalid");
        var diagnostic = pageBody(fixture.run("validateRules",Map.of(),Map.of("body",input)));
        assertThat(diagnostic.path("msg").asText()).startsWith("JSON parse error:");
        verify(fixture.mapping).compileForEditing(any());
    }

    @Test
    void lookupTemplateUsesOneScopedColumnReadAndDoesNotExposeCredentials() throws Throwable {
        var fixture = new Fixture();
        var endpoint = new RegisteredJdbcEndpoint("db","Validation","table","app.codes","jdbc:mysql://validation",
                "restricted","private","driver");
        when(fixture.endpoints.resolve("tenant-a","db","table")).thenReturn(endpoint);
        when(fixture.endpoints.resolve("tenant-b","db","table")).thenThrow(new TenantAccessException(
                "REGISTERED-JDBC-ENDPOINT-NOT-FOUND","数据源或数据表不存在，或者不属于当前租户"));
        when(fixture.endpoints.resolve("tenant-a","null","null")).thenThrow(new TenantAccessException(
                "REGISTERED-JDBC-ENDPOINT-NOT-FOUND","数据源或数据表不存在，或者不属于当前租户"));
        fixture.jdbc.update("INSERT INTO db_table_column_t VALUES ('tenant-a','table',0,'code'),"
                + "('tenant-a','table',0,'description'),('tenant-b','table',0,'secret'),"
                + "('tenant-a','table',1,'deleted')");
        var request = JSON.createObjectNode().put("tableId","table").put("datasourceId","db")
                .put("keyColumn","code").put("valueColumn","description").put("tenantId","tenant-b");
        try (var tenant = TenantContext.use("tenant-a")) {
            var lookup = page(fixture.run("lookupRules",Map.of(),Map.of("body",request)));
            assertThat(lookup).containsOnlyKeys("sql","resultColumn","onMissing","dataSource")
                    .containsEntry("sql","SELECT `description` FROM `app`.`codes` WHERE `code` = ?")
                    .containsEntry("dataSource",Map.of("datasourceId","db"));
            assertThat(fixture.queries).hasSize(1).allMatch(sql -> sql.contains("tenant_id"));
            assertThat(new RegisteredJdbcEndpoint("db","Validation","table","app.codes","jdbc:dm://validation",
                    null,null,null).lookupStatement("code","description"))
                    .isEqualTo("SELECT \"description\" FROM \"app\".\"codes\" WHERE \"code\" = ?");
            for (String forbidden : List.of("secret","deleted","CODE", "code\" OR 1=1")) {
                request.put("valueColumn",forbidden);
                assertThat(pageBody(fixture.run("lookupRules",Map.of(),Map.of("body",request))).path("msg").asText())
                        .isEqualTo("字典查询字段不属于选定的已登记表");
                assertThat(fixture.queries).hasSize(1);
            }
            var nullIds = JSON.createObjectNode().putNull("datasourceId").putNull("tableId");
            assertThat(pageBody(fixture.run("lookupRules",Map.of(),Map.of("body",nullIds))).path("code").asInt()).isEqualTo(403);
            assertThat(fixture.queries).isEmpty();
            var invalidBody = pageBody(fixture.run("lookupRules",Map.of(),Map.of("body",JSON.createArrayNode())));
            assertThat(invalidBody.path("msg").asText()).contains("java.util.LinkedHashMap<java.lang.String,java.lang.Object>");
            assertThat(fixture.queries).isEmpty();
        }
        try (var tenant = TenantContext.use("tenant-b")) {
            assertThat(pageBody(fixture.run("lookupRules",Map.of(),Map.of("body",request))).path("code").asInt()).isEqualTo(403);
            assertThat(fixture.queries).isEmpty();
        }
    }

    private static Map<String, Object> page(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).as("page response %s", response.getBody()).isEqualTo(HttpStatus.OK);
        return StructuredValues.objectMap(response.getBody());
    }

    private static List<Map<String, Object>> rows(ResponseEntity<?> response) {
        return StructuredValues.objectRows(page(response).get("rows"));
    }

    private static void assertError(ResponseEntity<?> response, String code) {
        assertThat(response.getStatusCode()).as("error response %s", response.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(pageBody(response).path("code").asText()).isEqualTo(code);
    }

    private static JsonNode pageBody(ResponseEntity<?> response) {
        return JSON.valueToTree(response.getBody());
    }

    private static final class Fixture {
        final List<String> queries = new ArrayList<>();
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:map_asset_"
                + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        final UnifiedMappingService mapping = mock(UnifiedMappingService.class);
        final RegisteredMappingSchemaService schemas = mock(RegisteredMappingSchemaService.class);
        final PipelineRepository repository = mock(PipelineRepository.class);
        final RegisteredJdbcDataSourceResolver endpoints = mock(RegisteredJdbcDataSourceResolver.class);
        final MappingProjectionService projections = mock(MappingProjectionService.class);
        final MappingUpgradeService nativeUpgrade = mock(MappingUpgradeService.class);
        final LegacyTransportUpgradeService transportUpgrade = mock(LegacyTransportUpgradeService.class);
        final LegacySourceShapeUpgradeService shapeUpgrade = mock(LegacySourceShapeUpgradeService.class);
        final WorkbookReaderUpgradeService workbookReaderUpgrade = mock(WorkbookReaderUpgradeService.class);
        final LegacyExactValueUpgradeService exactUpgrade = mock(LegacyExactValueUpgradeService.class);
        final NifiClient nifi = mock(NifiClient.class);
        final AtomicBoolean lockAvailable = new AtomicBoolean(true);
        final Map<String, Object> globals = new LinkedHashMap<>();
        final Map<String, MagicScript> routes = new LinkedHashMap<>();

        Fixture() throws Throwable {
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_t (id VARCHAR, tenant_id VARCHAR, name VARCHAR, description VARCHAR,
                    created_at BIGINT, updated_at BIGINT, dsl_json CLOB, nifi_process_group_id VARCHAR, status VARCHAR,
                    last_deployed_hash VARCHAR, last_deployed_at BIGINT, last_stopped_at BIGINT, node_mapping_json CLOB,
                    last_bulletin_id BIGINT, is_del INT);
                    CREATE TABLE data_access_agg_task_t (pipeline_id VARCHAR, tenant_id VARCHAR, is_del INT,
                    source_db_id VARCHAR, target_db_id VARCHAR, source_table_id VARCHAR, target_table_id VARCHAR);
                    CREATE TABLE db_datasource_t (tid VARCHAR, tenant_id VARCHAR, is_del INT);
                    CREATE TABLE db_table_t (tid VARCHAR, datasource_id VARCHAR, tenant_id VARCHAR, is_del INT,
                    asset_status INT, table_name VARCHAR, table_name_cn VARCHAR);
                    CREATE TABLE db_table_column_t (tenant_id VARCHAR, table_id VARCHAR, is_del INT, column_name VARCHAR);
                    CREATE TABLE data_access_mapping_plan_t (tid VARCHAR, tenant_id VARCHAR, pipeline_id VARCHAR,
                    node_id VARCHAR, plan_revision INT, draft_revision INT, plan_hash VARCHAR, engine_version VARCHAR,
                    created_by VARCHAR, created_time TIMESTAMP, plan_content CLOB);
                    CREATE TABLE data_access_mapping_publication_t (tenant_id VARCHAR, pipeline_id VARCHAR, node_id VARCHAR, plan_id VARCHAR);
                    CREATE TABLE validation_projection (node_id VARCHAR);
                    CREATE TABLE data_access_mapping_resource_t (tid VARCHAR, tenant_id VARCHAR, resource_kind VARCHAR,
                    resource_key VARCHAR, version_no INT, resource_hash VARCHAR, created_time TIMESTAMP,
                    resource_content CLOB, definition_json CLOB, created_by VARCHAR,
                    UNIQUE (tenant_id, resource_kind, resource_key, version_no));
                    """);
            globals.put("tableRead", TaskRuntimeMagicFixture.structuredReads(jdbc, queries::add));
            globals.put("tableWrite", new StructuredTableWriteMagicModule(jdbc));
            globals.put("transactions", new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
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
            globals.put("db", db);
            globals.put("tenantRuntime", new TenantMagicModule(null));
            globals.put("mapper", JSON);
            globals.put("mapping", mapping);
            globals.put("schemas", schemas);
            globals.put("pipelines", repository);
            globals.put("sources", endpoints);
            globals.put("projections", projections);
            globals.put("nativeUpgrade", nativeUpgrade);
            globals.put("transportUpgrade", transportUpgrade);
            globals.put("shapeUpgrade", shapeUpgrade);
            globals.put("workbookReaderUpgrade", workbookReaderUpgrade);
            globals.put("exactUpgrade", exactUpgrade);
            globals.put("nifi", nifi);
            var lockDataSource = mock(javax.sql.DataSource.class);
            var lockConnection = mock(java.sql.Connection.class);
            var metadata = mock(java.sql.DatabaseMetaData.class);
            var statement = mock(java.sql.PreparedStatement.class);
            var lockResponse = mock(java.sql.ResultSet.class);
            when(lockDataSource.getConnection()).thenReturn(lockConnection);
            when(lockConnection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn("jdbc:mysql://validation");
            when(lockConnection.prepareStatement(any())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(lockResponse);
            when(lockResponse.next()).thenReturn(true);
            when(lockResponse.getInt(1)).thenAnswer(call -> lockAvailable.get() ? 1 : 0);
            globals.put("mutation", new NifiMutationAspect(lockDataSource));
            function("nifiDecodeCanvas", "43.海通解码保存画布.ms", "e06d4eb123494419e67f70f2fc03bceb", "row");
            function("nifiFindCanvas", "48.海通读取租户画布.ms", "nifi_pipeline_find_v1", "flowId", "tenantId");
            function("nifiWriteRuntime", "52.海通更新画布运行记录.ms", "nifi_canvas_runtime_write_v1",
                    "pipelineId", "tenantId", "changes");
            function("nifiPipelineReadHttp", "29.海通流程只读接口响应.ms", "nifi_pipeline_read_http_v1", "action");
            function("nifiLifecycleBody", "55.海通读取生命周期请求体.ms", "nifi_lifecycle_body_v1",
                    "input", "type", "oldType", "requiredSignature");
            function("nifiMappingReadHttp", "61.海通映射读取响应.ms", "nifi_mapping_read_http_v1", "action");
            function("nifiMappingPlanScope", "62.海通映射计划范围.ms", "nifi_mapping_plan_scope_v1", "pipelineId", "tenantId");
            function("nifiReadMappingTemplate", "63.海通读取映射模板.ms", "nifi_mapping_template_read_v1", "templateId", "tenantId");
            function("nifiSaveMappingVersion", "71.海通追加映射资源版本.ms", "nifi_mapping_save_version_v1",
                    "kind", "key", "definition", "content");
            function("nifiReadMappingPlans", "72.海通批量读取映射计划.ms", "nifi_mapping_plan_read_v1",
                    "pipelineId", "tenantId", "ids");
            route("capabilities", "13.字段映射/03.查询字段映射能力.ms", "nifi_field_map_caps_get_v1");
            route("validateRules", "13.字段映射/04.校验字段映射.ms", "nifi_field_map_validate_v1");
            route("previewRules", "13.字段映射/05.预览字段映射.ms", "nifi_field_map_preview_v1");
            route("recommendRules", "13.字段映射/06.推荐字段映射.ms", "nifi_field_map_recommend_v1");
            route("compileRules", "13.字段映射/07.编译字段映射.ms", "nifi_field_map_compile_v1");
            route("legacyRules", "13.字段映射/08.转换旧字段映射.ms", "nifi_field_map_legacy_v1");
            route("lookupRules", "12.映射字典/04.生成字典查询模板.ms", "nifi_field_map_lookup_v1");
            route("restore", "10.映射计划/04.恢复映射计划草稿.ms", "nifi_map_restore_post_v1");
            route("upgrade", "14.映射运行/01.升级运行字段节点.ms", "nifi_map_upgrade_post_v1");
            route("published", "14.映射运行/02.启动已发布映射流程.ms", "nifi_map_start_pub_post_v1");
            route("saveTemplate", "11.映射模板/04.保存映射模板.ms", "nifi_map_templates_post_v1");
            route("snapshot", "12.映射字典/03.生成字典快照.ms", "nifi_map_dict_snapshot_post_v1");
            route("schema", "13.字段映射/01.构造映射字段结构.ms", "nifi_map_schema_post_v1");
            route("sample", "13.字段映射/02.取样预览映射.ms", "nifi_map_sample_post_v1");
            route("plans", "10.映射计划/01.查询映射计划历史.ms", "nifi_map_plans_get_v1");
            route("plan", "10.映射计划/02.查询映射计划版本.ms", "nifi_map_plan_get_v1");
            route("templates", "11.映射模板/01.查询映射模板版本.ms", "nifi_map_templates_get_v1");
            route("template", "11.映射模板/02.查询映射模板详情.ms", "nifi_map_template_get_v1");
            route("tables", "12.映射字典/01.查询字典候选表.ms", "nifi_map_dict_tables_get_v1");
            route("columns", "12.映射字典/02.查询字典候选字段.ms", "nifi_map_dict_cols_get_v1");
            route("import", "11.映射模板/03.导入映射模板.ms", "nifi_map_template_import_v1");
        }

        void nativeCanvas() throws Throwable {
            var pipeline = sampleCanvas();
            jdbc.update("INSERT INTO nifi_pipeline_t (id,tenant_id,updated_at,status,is_del,dsl_json,nifi_process_group_id,node_mapping_json)"
                            + " VALUES ('canvas','tenant-a',5,'STOPPED',0,?,'group',?)",
                    JSON.writeValueAsString(pipeline.dsl()), JSON.writeValueAsString(pipeline.nodeMapping()));
        }

        ObjectNode nativePublishedGraph(int size) throws Throwable {
            nativeCanvas();
            var primary = new LinkedHashMap<String, String>();
            var origins = new LinkedHashMap<String, String>();
            primary.put("source", "source");
            origins.put("source", "source");
            var flow = JSON.createObjectNode();
            flow.putArray("processGroups");
            flow.putArray("connections").add(JSON.readTree("""
                    {"id":"connection","component":{"source":{"id":"source"},"destination":{"id":"processor-0"},
                    "selectedRelationships":["success"]}}
                    """));
            var processors = flow.putArray("processors");
            var source = processors.addObject().put("id", "source");
            source.putObject("revision").put("version", 1);
            source.putObject("component").put("type", DateTableFetchPolicy.TYPE).put("state", "STOPPED");
            for (int index = 0; index < size; index++) {
                String processorId = "processor-" + index;
                String nodeId = "node-" + index;
                primary.put(nodeId, processorId);
                origins.put(processorId, nodeId);
                var processor = processors.addObject().put("id", processorId);
                processor.putObject("revision").put("version", 2);
                processor.putObject("component").put("type", "validation.TransformRecord").put("state", "STOPPED")
                        .putObject("config").putObject("properties").put("plan-id", "plan-" + index);
            }
            var mapping = new NifiNodeMapping(primary, origins,
                    Map.of("edge", new NifiNodeMapping.EdgeEndpoint("source", "processor-0", "connection")));
            jdbc.update("UPDATE nifi_pipeline_t SET node_mapping_json=? WHERE tenant_id='tenant-a' AND id='canvas'",
                    JSON.writeValueAsString(mapping));
            stubGroupOwner("tenant-a");
            stubFlow("group", flow);
            return flow;
        }

        void stubGroupOwner(String tenant) {
            var response = JSON.createObjectNode();
            response.putObject("component").put("comments", "data-elements:v1:" + tenant + ":pipeline:canvas");
            when(nifi.getProcessGroup("group")).thenReturn(response);
        }

        void stubFlow(String id, ObjectNode flow) {
            var response = JSON.createObjectNode();
            response.putObject("processGroupFlow").set("flow", flow);
            when(nifi.get("/flow/process-groups/" + id, JsonNode.class)).thenReturn(response);
        }

        void publishedPlan(String id, String node, JsonNode content) throws Throwable {
            plan(id, "tenant-a", "canvas", node, content.toString());
            jdbc.update("INSERT INTO data_access_mapping_publication_t VALUES ('tenant-a','canvas',?,?)", node, id);
        }

        String savedDraft() {
            return jdbc.queryForObject("SELECT dsl_json FROM nifi_pipeline_t WHERE tenant_id='tenant-a' AND id='canvas'", String.class);
        }

        String runtimeStatus() {
            return jdbc.queryForObject("SELECT status FROM nifi_pipeline_t WHERE tenant_id='tenant-a' AND id='canvas'", String.class);
        }

        long runtimeVersion() {
            return jdbc.queryForObject("SELECT updated_at FROM nifi_pipeline_t WHERE tenant_id='tenant-a' AND id='canvas'", Long.class);
        }

        Pipeline sampleCanvas() {
            var source = new Pipeline.Node("source","source.query","source","source",1,2,Map.of());
            var transform = new Pipeline.Node("mapping","transform.field-mapping","mapping","transform",3,4,
                    Map.of("mappings","{\"schemaVersion\":\"2.0\",\"rules\":[]}", "custom",true));
            return new Pipeline("canvas",null,null,null,5L,new Pipeline.Dsl(1,List.of(source,transform),
                    List.of(new Pipeline.Edge("edge","source","mapping","success"))),"group",PipelineStatus.STOPPED,
                    null,null,null,new NifiNodeMapping(Map.of("mapping","processor"),Map.of("processor","mapping"),Map.of()),null);
        }

        Object canvasRecord() {
            var find = (MagicScriptLambdaFunction) globals.get("nifiFindCanvas");
            return find.apply(null, new Object[]{"canvas","tenant-a"});
        }

        Map<String,Object> planBatch(List<String> ids) {
            var read = (MagicScriptLambdaFunction) globals.get("nifiReadMappingPlans");
            return StructuredValues.objectMap(read.apply(null,new Object[]{"canvas","tenant-a",ids}));
        }

        void canvas(String id, String tenant) {
            jdbc.update("INSERT INTO nifi_pipeline_t (id,tenant_id,status,is_del) VALUES (?,?,'SAVED',0)", id, tenant);
        }

        void plan(String id, String tenant, String pipeline, String node, String content) throws Throwable {
            jdbc.update("INSERT INTO data_access_mapping_plan_t VALUES (?,?,?,?,1,1,?,'2','validation',?,?)",
                    id, tenant, pipeline, node, MappingEngine.hash(JSON.readTree(content)), CREATED, content);
        }

        void template(String id, String tenant, String kind, String content) throws Throwable {
            jdbc.update("INSERT INTO data_access_mapping_resource_t                     (tid,tenant_id,resource_kind,resource_key,version_no,resource_hash,created_time,resource_content,definition_json)                     VALUES (?,?,?,?,1,?,?,?,'{}')",
                    id, tenant, kind, id, MappingEngine.hash(JSON.readTree(content)), CREATED, content);
        }

        ResponseEntity<?> run(String name, Map<String, String> parameters, Map<String, Object> inputs) throws Throwable {
            var request = new NifiPipelineReadMagicTest.QueryParameters();
            parameters.forEach(request::put);
            globals.put("request", request);
            var context = new LinkedHashMap<>(globals);
            context.putAll(inputs);
            queries.clear();
            return (ResponseEntity<?>) routes.get(name).execute(new MagicScriptContext(context));
        }

        private void function(String name, String path, String id, String... parameters) throws Throwable {
            MagicScript script = script(COMMON + path, id);
            MagicScriptLambdaFunction function = (context, args) -> {
                var bindings = new LinkedHashMap<>(globals);
                for (int i = 0; i < parameters.length; i++) {
                    bindings.put(parameters[i], args[i]);
                }
                return script.execute(new MagicScriptContext(bindings));
            };
            globals.put(name, function);
        }

        private void route(String name, String path, String id) throws Throwable {
            routes.put(name, script(API + path, id));
        }

        private static MagicScript script(String path, String id) throws Throwable {
            String source = CanonicalMagicSources.byPath(path, id);
            MagicScript.create(source, null).compile();
            source = source.replaceAll("(?m)^import '@/common/(\\w+)' as (\\w+)$", "var $2 = $1");
            source = source.replaceAll("(?m)^import (db|tableRead|tableWrite|tenantRuntime|request)$", "");
            source = source.replaceAll("(?m)^import (\\S+\\.)?\\w+ as (mapper|mapping|schemas|pipelines|projections|mutation|nativeUpgrade|transportUpgrade|shapeUpgrade|workbookReaderUpgrade|exactUpgrade|nifi|transactions|sources)$", "");
            return MagicScript.create(source, null);
        }
    }
}
