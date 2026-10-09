package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.model.dataasset.DataAssetQueryByIdRequest;
import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.dataservice.pull.ApiPullConfigurationMagicModule;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/nifi/api/pipelines")
@Api(tags = "PipelineController NiFi 流水线画布")
public class PipelineController {
    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 数据推送的落地文件统一保存在资源中心 ODS 层的非结构化存储域。
     * 该值是 {@code dwm_center_layer_source_t.layer_code} 的业务编码，
     * 不是某一个环境中固定的数据源 ID。
     */
    private static final String PUSH_FILE_STORAGE_DOMAIN = "fjghcc";
    private final PipelineRepository repo;
    private final NifiClient nifi;
    private final MagicAPIService magicApiService;
    private final DataAssetRuntimeAdapter dataAssetRuntimeAdapter;
    private final JdbcTemplate jdbcTemplate;
    private final DataSourceConnectionPropertyResolver connectionProperties;
    private final ApiPullConfigurationMagicModule apiPullConfig;
    private final PipelineTaskLifecycleSynchronizer taskLifecycle;
    private final PipelineGroupOrganizer groups;
    private final AccessTaskFieldMappingBinder accessTaskFieldMappingBinder;

    public PipelineController(PipelineRepository repo, NifiClient nifi, MagicAPIService magicApiService,
                              DataAssetRuntimeAdapter dataAssetRuntimeAdapter, JdbcTemplate jdbcTemplate,
                              DataSourceConnectionPropertyResolver connectionProperties,
                              ApiPullConfigurationMagicModule apiPullConfig,
                              PipelineTaskLifecycleSynchronizer taskLifecycle, PipelineGroupOrganizer groups) {
        this(repo, nifi, magicApiService, dataAssetRuntimeAdapter, jdbcTemplate, connectionProperties,
                apiPullConfig, taskLifecycle, groups, null);
    }

    @Autowired
    public PipelineController(PipelineRepository repo, NifiClient nifi, MagicAPIService magicApiService,
                              DataAssetRuntimeAdapter dataAssetRuntimeAdapter, JdbcTemplate jdbcTemplate,
                              DataSourceConnectionPropertyResolver connectionProperties,
                              ApiPullConfigurationMagicModule apiPullConfig,
                              PipelineTaskLifecycleSynchronizer taskLifecycle, PipelineGroupOrganizer groups,
                              AccessTaskFieldMappingBinder accessTaskFieldMappingBinder) {
        this.repo = repo;
        this.nifi = nifi;
        this.magicApiService = magicApiService;
        this.dataAssetRuntimeAdapter = dataAssetRuntimeAdapter;
        this.jdbcTemplate = jdbcTemplate;
        this.connectionProperties = connectionProperties;
        this.apiPullConfig = apiPullConfig;
        this.taskLifecycle = taskLifecycle;
        this.groups = groups;
        this.accessTaskFieldMappingBinder = accessTaskFieldMappingBinder;
    }

    @GetMapping
    @Operation(summary = "查询流程列表(list)")
    public List<PipelineSummary> list() {
        return repo.findAll().stream().map(PipelineSummary::of).toList();
    }

    @GetMapping("/runtime-list")
    @Operation(summary = "查询流程运行列表(runtimeList)")
    public List<Map<String, Object>> runtimeList() {
        List<Pipeline> pipelines = repo.findAll();
        Map<String, JsonNode> statusByGroupId = Map.of();
        String statusError = null;
        if (pipelines.stream().anyMatch(p -> !isBlank(p.nifiProcessGroupId()))) {
            try {
                JsonNode root = nifi.getProcessGroupStatus(nifi.getRootProcessGroupId())
                        .path("processGroupStatus").path("aggregateSnapshot");
                if (root.isMissingNode() || root.isNull() || !root.isObject()) {
                    throw new IllegalStateException("NiFi 根流程组状态响应缺少 aggregateSnapshot");
                }
                statusByGroupId = processGroupSnapshots(root);
            } catch (Exception e) {
                statusError = e.getMessage() == null ? "NiFi 根流程组状态获取失败" : e.getMessage();
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < pipelines.size(); i++) {
            Pipeline pipeline = pipelines.get(i);
            Map<String, Object> row = runtimeRow(pipeline, i + 1);
            if (!isBlank(pipeline.nifiProcessGroupId())) {
                JsonNode snapshot = statusByGroupId.get(pipeline.nifiProcessGroupId());
                if (statusError != null) {
                    row.put("nifiStatusError", statusError);
                } else if (snapshot == null || !snapshot.has("queuedCount") || !snapshot.has("activeThreadCount")) {
                    row.put("nifiStatusError", "NiFi 流程组不存在或状态指标不完整");
                } else {
                    addRuntimeMetrics(row, pipeline, snapshot);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /** Root recursive status contains nested process groups; never fetch a missing group per row. */
    private static Map<String, JsonNode> processGroupSnapshots(JsonNode root) {
        Map<String, JsonNode> snapshots = new HashMap<>();
        collectProcessGroupSnapshots(root, snapshots);
        return snapshots;
    }

    private static void collectProcessGroupSnapshots(JsonNode snapshot, Map<String, JsonNode> snapshots) {
        String id = snapshot.path("id").asText(null);
        if (id != null && !id.isBlank()) snapshots.put(id, snapshot);
        for (JsonNode child : snapshot.path("processGroupStatusSnapshots")) {
            JsonNode childSnapshot = child.path("processGroupStatusSnapshot");
            if (childSnapshot.isObject()) collectProcessGroupSnapshots(childSnapshot, snapshots);
        }
    }

    /**
     * Overview needs lifecycle counts and five recent rows, not one NiFi status call per flow.
     * findAll still deserializes every tenant pipeline to keep counts identical to runtimeList;
     * use database aggregates and a page query if a tenant grows beyond this small flow set.
     */
    @GetMapping("/runtime-overview")
    @Operation(summary = "查询流程总览(runtimeOverview)")
    public Map<String, Object> runtimeOverview() {
        List<Pipeline> pipelines = repo.findAll();
        long running = pipelines.stream().filter(p -> p.status() == PipelineStatus.RUNNING).count();
        long failed = pipelines.stream().filter(p -> p.status() == PipelineStatus.RUN_ERROR
                || p.status() == PipelineStatus.DEPLOY_FAILED).count();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(5, pipelines.size()); i++) {
            rows.add(runtimeRow(pipelines.get(i), i + 1));
        }
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("total", pipelines.size());
        overview.put("running", running);
        overview.put("failed", failed);
        overview.put("flows", rows);
        return overview;
    }

    private Map<String, Object> runtimeRow(Pipeline p, int seq) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", seq);
            row.put("id", p.id());
            row.put("taskName", p.name());
            row.put("currentStatus", p.status());
            row.put("processGroupId", p.nifiProcessGroupId());
            row.put("latestRunTime", p.lastDeployedAt());
            row.put("endTime", p.lastStoppedAt());
            row.put("executionTimeMs", p.lastDeployedAt() != null && p.lastStoppedAt() != null
                    ? Math.max(0L, p.lastStoppedAt() - p.lastDeployedAt())
                    : null);

            Pipeline.Node source = firstNode(p, "source");
            Pipeline.Node sink = firstNode(p, "sink");
            Map<String, Object> sourceConfig = source == null ? Map.of() : source.config();
            Map<String, Object> sinkConfig = sink == null ? Map.of() : sink.config();
            row.put("scheduleFrequency", firstNonBlank(sourceConfig, "schedulingPeriod", "schedule", "cron"));
            row.put("syncMode", firstNonBlank(sourceConfig, "syncMode"));
            row.put("sourceDatabase", sourceDatabaseText(sourceConfig));
            row.put("sourceTables", sourceTablesText(sourceConfig));
            row.put("targetDatabase", targetDatabaseText(sinkConfig));
            row.put("targetTable", firstNonBlank(sinkConfig, "table", "targetTable", "tableName"));
            row.put("sourceNodeName", source == null ? null : source.label());
            row.put("targetNodeName", sink == null ? null : sink.label());

            return row;
    }

    private void addRuntimeMetrics(Map<String, Object> row, Pipeline pipeline, JsonNode snapshot) {
        long activeThreads = parseLong(snapshot.path("activeThreadCount"));
        long queuedCount = parseLong(snapshot.path("queuedCount"));
        row.put("activeThreads", activeThreads);
        row.put("queuedCount", queuedCount);
        row.put("queued", snapshot.path("queued").asText(null));
        row.put("inputCount", maxMetric(snapshot, "flowFilesIn", "flowFilesReceived"));
        row.put("outputCount", maxMetric(snapshot, "flowFilesOut", "flowFilesSent", "flowFilesTransferred"));
        row.put("bytesRead", snapshot.path("bytesRead").asText(null));
        row.put("bytesWritten", snapshot.path("bytesWritten").asText(null));
        row.put("bytesSent", snapshot.path("bytesSent").asText(null));
        row.put("running", activeThreads > 0 || pipeline.status() == PipelineStatus.RUNNING);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询流程详情(get)")
    public ResponseEntity<Pipeline> get(@PathVariable String id) {
        return repo.findById(id)
                // Existing tasks created before API-pull support used an empty
                // JDBC source node.  Repair only that invalid legacy shape as
                // the task is reopened; user-configured valid nodes stay intact.
                .map(this::repairLegacySource)
                // Registration-derived dictionary/standard rules must be visible
                // in the editor, not only materialized on deploy/start.
                .map(this::bindAccessTaskMappings)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Pipeline bindAccessTaskMappings(Pipeline pipeline) {
        if (pipeline == null || accessTaskFieldMappingBinder == null) return pipeline;
        try {
            Pipeline bound = accessTaskFieldMappingBinder.bind(pipeline);
            if (bound == pipeline) return pipeline;
            return repo.update(pipeline.id(), current -> new Pipeline(current.id(), current.name(), current.description(),
                    current.createdAt(), current.updatedAt(), bound.dsl(), current.nifiProcessGroupId(), current.status(),
                    current.lastDeployedHash(), current.lastDeployedAt(), current.lastStoppedAt(), current.nodeMapping(),
                    current.lastBulletinId()));
        } catch (IllegalStateException configurationError) {
            // Field-rule completion is required to deploy, but it must never
            // make an already saved design unreadable.  Keep the stored DSL
            // visible in the editor so users can inspect and correct it; the
            // deployment compiler still performs the same strict validation.
            log.warn("Pipeline {} has incomplete registration mappings; returning saved canvas for editing: {}",
                    pipeline.id(), configurationError.getMessage());
            return pipeline;
        }
    }

    /**
     * template 根据资源目录构建数据源与目标数据库
     */
    @PostMapping("/template")
    @Operation(summary = "生成流程模板(template)")
    public ResponseEntity<Pipeline> template(@RequestBody PipelineTemplateRequest request) {
        if (request != null && "structured-surveillance".equalsIgnoreCase(request.templateType())) {
            return ResponseEntity.ok(structuredSurveillanceTemplate());
        }
        if (request == null || isBlank(request.tid())) {
            return ResponseEntity.badRequest().build();
        }

        Pipeline existing = findCatalogPipeline(request.tid().trim());
        if (existing != null) return ResponseEntity.ok(existing);
        Map<String, Object> catalog = loadAssetByTid(request.tid().trim());
        if (catalog == null || catalog.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String sourceTableId = firstNonBlank(catalog, "sourceTableId");
        String sourceDbId = firstNonBlank(catalog, "dbId");
        String targetTableId = firstNonBlank(catalog, "targetTableId");
        String targetDbId = firstNonBlank(catalog, "targetDbId");

        Map<String, Object> sourceTableAsset = isBlank(sourceTableId) ? null : loadAssetByTid(sourceTableId);
        Map<String, Object> sourceDbAsset = isBlank(sourceDbId) ? null : loadDatasourceDetail(sourceDbId, loadAssetByTid(sourceDbId));
        Map<String, Object> targetTableAsset = isBlank(targetTableId) ? null : loadAssetByTid(targetTableId);
        Map<String, Object> targetDbAsset = isBlank(targetDbId) ? null : loadAssetByTid(targetDbId);

        Map<String, Object> source = firstNonEmptyMap(catalog,
                "source", "sourceInfo", "sourceTableInfo", "input", "inputTable");
        if ((source == null || source.isEmpty()) && sourceTableAsset != null) {
            source = new LinkedHashMap<>(sourceTableAsset);
        }
        Map<String, Object> target = firstNonEmptyMap(catalog,
                "target", "targetInfo", "targetTableInfo", "output", "outputTable");
        if ((target == null || target.isEmpty()) && targetTableAsset != null) {
            target = new LinkedHashMap<>(targetTableAsset);
        }

        String sourceTable = firstNonBlank(catalog,
                "sourceTable", "source_table", "fromTable", "from_table");
        if (isBlank(sourceTable) && source != null) {
            sourceTable = firstNonBlank(source, "tableName", "table", "name", "tableCode");
        }
        if (isBlank(sourceTable) && sourceTableAsset != null) {
            sourceTable = firstNonBlank(sourceTableAsset, "tableName", "tableNameCn", "name");
        }
        if ((target == null || target.isEmpty()) && !isBlank(firstNonBlank(catalog,
                "targetTable", "target_table", "toTable", "to_table"))) {
            target = new LinkedHashMap<>();
            target.put("tableName", firstNonBlank(catalog,
                    "targetTable", "target_table", "toTable", "to_table"));
        }
        if (isBlank(sourceTable) || target == null || target.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String sourceNodeId = "source-1";
        String transformNodeId = "transform-1";
        String sinkNodeId = "sink-1";

        Map<String, Object> sourceDs = sourceDbAsset != null ? sourceDbAsset : firstNonEmptyMap(catalog,
                "sourceDatasource", "sourceDataSource", "sourceDs", "belongDataSource");
        sourceDs = loadDatasourceDetail(firstNonBlank(sourceDs, "tid", "id", "dbId", "datasourceId", "dataSourceId"), sourceDs);
        List<?> sourceColumns = loadColumnsByTableId(sourceTableId);
        List<?> targetColumns = loadColumnsByTableId(targetTableId);
        Map<String, Object> physicalSourceDs = resolvePushFileStorage(sourceDs, sourceTable);
        Map<String, Object> sourceConfig = buildSourceConfig(physicalSourceDs, sourceTableAsset, sourceColumns);
        sourceConfig.put("table", sourceTable.trim());
        String sourceDbType = firstNonBlank(physicalSourceDs, "dbType", "db_type", "databaseType", "database_type");
        if (isBlank(sourceDbType)) sourceDbType = firstNonBlank(catalog, "sourceDbType");
        String sourceNodeType = normalizeSourceNodeType(sourceDbType, physicalSourceDs);
        String sourceNodeLabel = firstNonBlank(sourceTableAsset, "tableNameCn", "table_name_cn");
        if (isBlank(sourceNodeLabel)) sourceNodeLabel = firstNonBlank(source, "tableNameCn", "table_name_cn");
        if (isBlank(sourceNodeLabel)) sourceNodeLabel = sourceTable.trim();

        Map<String, Object> transformConfig = new LinkedHashMap<>();
        transformConfig.put("mappings", buildMappingConfig(sourceColumns, targetColumns, sourceTableId, targetTableId));

        Map<String, Object> targetDs = targetDbAsset != null ? targetDbAsset : firstNonEmptyMap(catalog,
                "targetDatasource", "targetDataSource", "targetDs", "targetBelongDataSource");
        String targetTableName = firstNonBlank(target, "tableName", "targetTableName", "table", "name");
        String targetNodeLabel = firstNonBlank(targetTableAsset, "tableNameCn", "table_name_cn");
        if (isBlank(targetNodeLabel)) targetNodeLabel = firstNonBlank(target, "tableNameCn", "table_name_cn");
        if (isBlank(targetNodeLabel)) targetNodeLabel = targetTableName;
        targetDs = loadDatasourceDetail(firstNonBlank(targetDs, "tid", "id", "dbId", "datasourceId", "dataSourceId"), targetDs);
        Map<String, Object> sinkConfig = buildSinkConfig(targetDs,
                targetTableAsset == null ? target : targetTableAsset, targetColumns);
        sinkConfig.put("table", nifiTargetTableName(
                String.valueOf(sinkConfig.getOrDefault("dbType", "")),
                expandDatasourceConnection(targetDs), targetTableName));

        Pipeline template = new Pipeline(
                null,
                isBlank(firstNonBlank(catalog, "name", "catalogName", "title"))
                        ? "资源目录表转换模板"
                        : firstNonBlank(catalog, "name", "catalogName", "title").trim(),
                firstNonBlank(catalog, "description", "desc", "remark"),
                null,
                null,
                new Pipeline.Dsl(
                        1,
                        List.of(
                                new Pipeline.Node(sourceNodeId, sourceNodeType, sourceNodeLabel, "source", -120, 120,
                                        sourceConfig),
                                new Pipeline.Node(transformNodeId, "transform.field-enrichment", "字段映射", "transform", 420, 120, transformConfig),
                                new Pipeline.Node(sinkNodeId,
                                        "HIVE".equalsIgnoreCase(String.valueOf(sinkConfig.get("dbType"))) ? "sink.hive" : "sink.jdbc",
                                        targetNodeLabel, "sink", 960, 120, sinkConfig)
                        ),
                        List.of(
                                new Pipeline.Edge("edge-1", sourceNodeId, transformNodeId, null),
                                new Pipeline.Edge("edge-2", transformNodeId, sinkNodeId, null)
                        )
                ),
                null, null, null, null, null, null, null
        );
        return ResponseEntity.ok(template);
    }

    private Pipeline structuredSurveillanceTemplate() {
        List<Pipeline.Node> nodes = new ArrayList<>();
        List<Pipeline.Edge> edges = new ArrayList<>();
        String[] channels = {"person", "mobile", "vehicle"};
        int index = 0;
        for (String channel : channels) {
            int x = index * 1280;
            String source = "surveillance-" + channel + "-source";
            String match = "surveillance-" + channel + "-match";
            String matched = "surveillance-" + channel + "-matched";
            nodes.add(new Pipeline.Node(source, "source.kafka", channel + " 输入 Kafka", "source", x, 80,
                    new LinkedHashMap<>(Map.of("groupId", "structured-surveillance-" + channel,
                            "schedulingPeriod", "0 sec",
                            "messageFormat", "JSON",
                            "schemaRequired", true,
                            "credentialRef", "credential://kafka/structured-surveillance"))));
            nodes.add(new Pipeline.Node(match, "transform.surveillance-match", channel + " 布控匹配", "transform", x + 420, 80,
                    new LinkedHashMap<>(Map.of("engineCode", "structured-surveillance", "channelCode", channel,
                            "matchUrl", "/api/v1/surveillance/match/batch",
                            "batchSize", 100, "requestTimeout", "30 sec"))));
            nodes.add(kafkaOutput(matched, channel + " 命中结果 Topic", x + 860, 0, channel + ".result"));
            edges.add(new Pipeline.Edge("edge-" + source + "-" + match, source, match, null));
            edges.add(new Pipeline.Edge("edge-" + match + "-" + matched, match, matched, "matched"));
            index++;
        }
        return new Pipeline(null, "结构化数据布控引擎", "Kafka 多通道布控匹配模板", null, null,
                new Pipeline.Dsl(1, nodes, edges), null, null, null, null, null, null, null);
    }

    private Pipeline.Node kafkaOutput(String id, String label, int x, int y, String topic) {
        return new Pipeline.Node(id, "sink.kafka", label, "sink", x, y,
                new LinkedHashMap<>(Map.of("topic", topic, "deliveryGuarantee", "EXACTLY_ONCE",
                        "credentialRef", "credential://kafka/structured-surveillance")));
    }

    @PostMapping("/template/node")
    @Operation(summary = "生成单组件模板(templateNode)")
    public ResponseEntity<TemplateNodeResponse> templateNode(@RequestBody PipelineTemplateNodeRequest request) {
        if (request == null || isBlank(request.key())) {
            return ResponseEntity.badRequest().build();
        }
        String key = request.key().trim();
        if ("transform.field-mapping".equals(key) || "transform.field-enrichment".equals(key)) {
            return buildTransformTemplateNode(request, key);
        }
        if ("sink.jdbc".equals(key) || "sink.hive".equals(key)) {
            return buildSinkTemplateNode(request);
        }
        if (key.startsWith("source.")) {
            return buildSourceTemplateNode(request);
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping
    @Operation(summary = "创建流程(create)")
    @SerializedNifiMutation
    @Transactional
    public Pipeline create(@RequestBody Pipeline body, @RequestParam(required = false) String catalogTid) {
        Pipeline existing = findCatalogPipeline(catalogTid);
        if (existing != null) return saveDesign(existing, body);
        // Force-create: clear any incoming id so save() generates one.
        Pipeline p = new Pipeline(null, body.name(), body.description(),
                null, null, body.dsl(), null, null, null, null, null, null, null);
        Pipeline saved = repo.save(p);
        taskLifecycle.ensureCatalogAccessTask(catalogTid, saved);
        bindApiPullRule(catalogTid, saved.id());
        return saved;
    }

    @PostMapping("/{id}")
    @Operation(summary = "更新流程(update)")
    @SerializedNifiMutation
    public ResponseEntity<Pipeline> update(@PathVariable String id, @RequestBody Pipeline body,
                                           @RequestParam(required=false) Long expectedUpdatedAt) {
        Pipeline current = repo.findById(id).orElse(null);
        if (current == null) return ResponseEntity.notFound().build();
        if (expectedUpdatedAt != null) {
            return ResponseEntity.ok(repo.update(id, cur -> {
                if (!java.util.Objects.equals(cur.updatedAt(),expectedUpdatedAt))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"加工图已被其他入口修改，请重新加载后再保存");
                return new Pipeline(cur.id(),body.name(),body.description(),cur.createdAt(),cur.updatedAt(),body.dsl(),cur.nifiProcessGroupId(),cur.status(),cur.lastDeployedHash(),cur.lastDeployedAt(),cur.lastStoppedAt(),cur.nodeMapping(),cur.lastBulletinId());
            }));
        }
        return ResponseEntity.ok(saveDesign(current, body));
    }

    private Pipeline saveDesign(Pipeline current, Pipeline body) {
        return repo.update(current.id(), cur -> new Pipeline(cur.id(), body.name(), body.description(),
                cur.createdAt(), cur.updatedAt(), body.dsl(), cur.nifiProcessGroupId(), cur.status(),
                cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(), cur.nodeMapping(), cur.lastBulletinId()));
    }

    // Compatibility for existing in-process callers; HTTP clients may send an optimistic timestamp.
    public ResponseEntity<Pipeline> update(String id, Pipeline body) { return update(id,body,null); }

    private Pipeline findCatalogPipeline(String catalogTid) {
        if (isBlank(catalogTid) || "null".equals(catalogTid) || "undefined".equals(catalogTid)) return null;
        List<String> ids = jdbcTemplate.queryForList("""
                select pipeline_id from data_access_agg_task_t
                 where tenant_id = ? and data_catalog_id = ? and is_del = 0
                   and pipeline_id is not null and pipeline_id <> '' order by created_time, tid
                """, String.class, TenantContext.requireTenantId(), catalogTid.trim());
        for (String id : ids) {
            Pipeline pipeline = repo.findById(id).orElse(null);
            if (pipeline != null) return pipeline;
        }
        return null;
    }

    private Pipeline repairLegacySource(Pipeline pipeline) {
        try {
            Pipeline.Node currentSource = firstNode(pipeline, "source");
            if (currentSource == null) return pipeline;
            String catalogId = "";
            try {
                String resolvedCatalogId = jdbcTemplate.queryForObject("""
                        select data_catalog_id from data_access_agg_task_t
                         where tenant_id=? and pipeline_id=? and is_del=0
                         order by created_time desc limit 1
                        """, String.class, TenantContext.requireTenantId(), pipeline.id());
                catalogId = resolvedCatalogId == null ? "" : resolvedCatalogId.trim();
            } catch (Exception ignored) {
                // Older flows can predate the catalog-task relation. Their
                // source node still owns the registered source-table identity.
            }
            Map<String, Object> catalog = isBlank(catalogId) ? Map.of() : loadAssetByTid(catalogId);
            String sourceTableId = firstNonBlank(catalog, "sourceTableId", "source_table_id");
            if (isBlank(sourceTableId)) {
                sourceTableId = firstNonBlank(currentSource.config(), "sourceTableId", "source_table_id");
            }
            if (isBlank(sourceTableId)) return pipeline;
            Map<String, Object> sourceTable = isBlank(sourceTableId) ? null : loadAssetByTid(sourceTableId);
            if (sourceTable == null || sourceTable.isEmpty()) return pipeline;
            Map<String, Object> sourceDs = loadDbAssetByTable(sourceTable);
            if (sourceDs == null || sourceDs.isEmpty()) return pipeline;
            sourceDs = loadDatasourceDetail(firstNonBlank(sourceDs, "tid", "id", "dbId", "datasourceId", "dataSourceId"), sourceDs);
            String sourceTableName = firstNonBlank(sourceTable, "tableName", "table", "name", "tableCode", "tableNameCn");
            Map<String, Object> physicalSourceDs = resolvePushFileStorage(sourceDs, sourceTableName);
            String nodeType = normalizeSourceNodeType(firstNonBlank(physicalSourceDs,
                    "dbType", "db_type", "databaseType", "database_type"), physicalSourceDs);
            boolean pushFileSource = isPushReceiveDatasource(sourceDs);
            boolean generatedMysqlFallback = !pushFileSource
                    && "source.mysql".equals(currentSource.manifestKey()) && !nodeType.equals(currentSource.manifestKey())
                    && currentSource.id().equals("source-" + sourceTableId)
                    && Objects.equals(firstNonBlank(currentSource.config(), "sourceDbId", "registeredDatasourceId"),
                            firstNonBlank(sourceDs, "tid", "id", "datasourceId", "dataSourceId"));
            boolean incompleteFileSource = !pushFileSource
                    && ("source.ftp".equals(nodeType) || "source.sftp".equals(nodeType))
                    && (isBlank(firstNonBlank(currentSource.config(), "hostname", "host"))
                    || isBlank(firstNonBlank(currentSource.config(), "remotePath"))
                    || isBlank(firstNonBlank(currentSource.config(), "username")));
            if (!pushFileSource && !generatedMysqlFallback && (!"source.api".equals(nodeType)
                    || !isBlank(firstNonBlank(currentSource.config(), "url"))) && !incompleteFileSource) {
                return refreshRegisteredSourceMetadata(pipeline, currentSource, sourceTable, sourceTableId);
            }
            Map<String, Object> config = buildSourceConfig(physicalSourceDs, sourceTable, loadColumnsByTableId(sourceTableId));
            if (generatedMysqlFallback) {
                config = repairMistypedSourceConfig(config, currentSource.config());
            }
            if (incompleteFileSource) {
                config = repairFileSourceConfig(config, currentSource.config());
            }
            if (pushFileSource) {
                String expectedPath = firstNonBlank(config, "remotePath");
                boolean sameSourceEndpoint = nodeType.equals(currentSource.manifestKey())
                        && expectedPath.equals(firstNonBlank(currentSource.config(), "remotePath"));
                String expectedSheetName = firstNonBlank(config, "excelSheetName");
                String expectedHeaderMode = firstNonBlank(config, "fileHeaderMode");
                if (sameSourceEndpoint) {
                    boolean registrationMetadataCurrent = Objects.equals(expectedSheetName,
                            firstNonBlank(currentSource.config(), "excelSheetName"))
                            && Objects.equals(expectedHeaderMode,
                            firstNonBlank(currentSource.config(), "fileHeaderMode"));
                    if (registrationMetadataCurrent) {
                        return pipeline;
                    }
                    // This repair only adds registration-derived file metadata.
                    // Do not replace a live FTP/SFTP node's credentials or other
                    // manually saved transport settings while doing so.
                    config = new LinkedHashMap<>(currentSource.config());
                    config.put("excelSheetName", expectedSheetName);
                    config.put("fileHeaderMode", expectedHeaderMode);
                    config.put("sourceColumns", loadColumnsByTableId(sourceTableId));
                    config.put("sourceTableId", firstNonBlank(sourceTable, "tid", "id", "tableId"));
                    config.put("logicalSourceDatasourceId", firstNonBlank(physicalSourceDs,
                            "logicalSourceDatasourceId", "logical_source_datasource_id"));
                    config.put("pushStorageDomain", firstNonBlank(physicalSourceDs,
                            "pushStorageDomain", "push_storage_domain"));
                }
            } else if (!generatedMysqlFallback && isBlank(firstNonBlank(config, "url"))) {
                return pipeline;
            }
            List<Pipeline.Node> nodes = new ArrayList<>();
            for (Pipeline.Node node : pipeline.dsl().nodes()) {
                if (node.id().equals(currentSource.id())) {
                    String label = node.label();
                    if ("来源表".equals(label) || "源表".equals(label) || "FTP 文件来源".equals(label)
                            || "API 接口来源".equals(label)) {
                        String tableLabel = firstNonBlank(sourceTable, "tableNameCn", "table_name_cn");
                        if (isBlank(tableLabel)) tableLabel = firstNonBlank(sourceTable, "tableName", "table_name");
                        if (!isBlank(tableLabel)) label = tableLabel;
                    }
                    nodes.add(new Pipeline.Node(node.id(), nodeType, label, node.category(), node.x(), node.y(), config));
                } else {
                    nodes.add(node);
                }
            }
            Pipeline.Dsl dsl = new Pipeline.Dsl(pipeline.dsl().version(), nodes, pipeline.dsl().edges());
            Pipeline repaired = repo.update(pipeline.id(), current -> new Pipeline(
                    current.id(), current.name(), current.description(), current.createdAt(), current.updatedAt(), dsl,
                    current.nifiProcessGroupId(), current.status(), current.lastDeployedHash(), current.lastDeployedAt(),
                    current.lastStoppedAt(), current.nodeMapping(), current.lastBulletinId()));
            bindApiPullRule(catalogId, repaired.id());
            return repaired;
        } catch (Exception ignored) {
            // A task must remain readable even when an old catalog was deleted
            // or its source metadata is temporarily unavailable.
            return pipeline;
        }
    }

    static Map<String, Object> repairFileSourceConfig(Map<String, Object> registered,
                                                     Map<String, Object> saved) {
        Map<String, Object> repaired = new LinkedHashMap<>(registered);
        saved.forEach((key, value) -> {
            if (value != null && !value.toString().isBlank()) repaired.put(key, value);
        });
        for (String key : List.of("jdbcUrl", "jdbcURL", "database", "sid", "serviceName",
                "connectionType", "schema", "defaultSchema", "host")) repaired.remove(key);
        if (isBlank(firstNonBlank(saved, "fileFilterRegex"))) {
            String table = firstNonBlank(repaired, "sourceTableName", "table");
            if (!isBlank(table)) repaired.put("fileFilterRegex", "(?i)^" + java.util.regex.Pattern.quote(table)
                    + "\\.(csv|json|xlsx|xls)$");
        }
        return repaired;
    }

    /** Repair the generated connector identity while retaining manually edited query/schedule/mapping options. */
    static Map<String, Object> repairMistypedSourceConfig(Map<String, Object> registered, Map<String, Object> saved) {
        Map<String, Object> repaired = new LinkedHashMap<>(registered);
        repaired.putAll(saved);
        for (String key : List.of("dbType", "jdbcUrl", "jdbcURL", "host", "port", "database", "sid",
                "serviceName", "connectionType", "defaultSchema", "schema", "compatibleMode")) {
            if (registered.containsKey(key)) repaired.put(key, registered.get(key));
            else repaired.remove(key);
        }
        return repaired;
    }

    /**
     * Adds only registration-owned source metadata to an otherwise valid
     * existing JDBC/API node. This brings legacy canvases into the same
     * timestamp-default contract without replacing connection credentials or
     * other settings that users may have adjusted in the canvas.
     */
    private Pipeline refreshRegisteredSourceMetadata(Pipeline pipeline, Pipeline.Node sourceNode,
                                                     Map<String, Object> sourceTable, String requestedTableId) {
        Map<String, Object> config = new LinkedHashMap<>(sourceNode.config());
        String sourceTableId = firstNonBlank(sourceTable, "tid", "id", "tableId");
        if (isBlank(sourceTableId)) sourceTableId = requestedTableId;
        boolean changed = false;
        if (!isBlank(sourceTableId) && !sourceTableId.equals(firstNonBlank(config, "sourceTableId"))) {
            config.put("sourceTableId", sourceTableId);
            changed = true;
        }

        String registeredTimestamp = registeredTimestampColumnForTable(sourceTable,
                loadColumnsByTableId(sourceTableId));
        // A present registration selection is authoritative. When registration
        // has no extraction timestamp, do not erase a potentially deliberate
        // manual choice on an old canvas; newly generated templates are blank.
        if (!isBlank(registeredTimestamp)
                && !registeredTimestamp.equalsIgnoreCase(firstNonBlank(config, "incrementalColumn"))) {
            config.put("incrementalColumn", registeredTimestamp);
            changed = true;
        }
        if (!changed) return pipeline;

        List<Pipeline.Node> nodes = new ArrayList<>();
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            nodes.add(node.id().equals(sourceNode.id())
                    ? new Pipeline.Node(node.id(), node.manifestKey(), node.label(), node.category(),
                    node.x(), node.y(), config)
                    : node);
        }
        Pipeline.Dsl dsl = new Pipeline.Dsl(pipeline.dsl().version(), nodes, pipeline.dsl().edges());
        return repo.update(pipeline.id(), current -> new Pipeline(
                current.id(), current.name(), current.description(), current.createdAt(), current.updatedAt(), dsl,
                current.nifiProcessGroupId(), current.status(), current.lastDeployedHash(), current.lastDeployedAt(),
                current.lastStoppedAt(), current.nodeMapping(), current.lastBulletinId()));
    }

    private void bindApiPullRule(String catalogId, String pipelineId) {
        if (isBlank(catalogId) || isBlank(pipelineId)) return;
        try {
            Map<String, Object> catalog = loadAssetByTid(catalogId);
            String sourceTableId = firstNonBlank(catalog, "sourceTableId", "source_table_id");
            Map<String, Object> table = isBlank(sourceTableId) ? null : loadAssetByTid(sourceTableId);
            Map<String, Object> datasource = table == null ? null : loadDbAssetByTable(table);
            String datasourceId = firstNonBlank(datasource, "tid", "id", "dbId", "datasourceId", "dataSourceId");
            if (!isBlank(datasourceId) && !isBlank(sourceTableId)) {
                apiPullConfig.bindTask(datasourceId, sourceTableId, pipelineId);
            }
        } catch (Exception ignored) {
            // API source compilation is independent of the optional run-ledger
            // binding.  A later explicit save/reopen will retry this association.
        }
    }

    @PostMapping("/delete/{id}")
    @Operation(summary = "删除流程(delete)")
    @SerializedNifiMutation
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Pipeline pipeline = repo.findById(id).orElse(null);
        if (pipeline == null) return ResponseEntity.notFound().build();
        List<String> groupIds = groups.existingIds(pipeline);
        List<String> parentIds = groups.parentIds(groupIds);
        groupIds.forEach(nifi::cleanupProcessGroup);
        groups.pruneEmptyAncestors(parentIds, null);
        taskLifecycle.unlinkCatalogTasks(id);
        jdbcTemplate.update("""
                update data_access_agg_task_t set is_del = 1, task_status = 0, process_group_id = null,
                       updated_time = current_timestamp where tenant_id = ? and pipeline_id = ? and is_del = 0
                """, TenantContext.requireTenantId(), id);
        return repo.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/deleteByPgId")
    @Operation(summary = "按流程组ID删除(deleteByPgId)")
    public ResponseEntity<?> deleteByPgId(@RequestParam("processGroupId") String processGroupId) {
        if (processGroupId == null || processGroupId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "deleted", false,
                    "error", "processGroupId is required"));
        }
        try {
            nifi.cleanupProcessGroup(processGroupId.trim());
            return ResponseEntity.ok(Map.of(
                    "deleted", true,
                    "processGroupId", processGroupId.trim()));
        } catch (NifiException e) {
            int status = e.status() > 0 ? e.status() : 502;
            return ResponseEntity.status(status).body(Map.of(
                    "deleted", false,
                    "processGroupId", processGroupId.trim(),
                    "error", e.getMessage(),
                    "status", status,
                    "body", e.body()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "deleted", false,
                    "processGroupId", processGroupId.trim(),
                    "error", e.getMessage()));
        }
    }

    @PostMapping("/deleteTreeByParentPgId")
    @Operation(summary = "按父流程组ID删除树(deleteTreeByParentPgId)")
    public ResponseEntity<?> deleteTreeByParentPgId(@RequestParam("parentPgId") String parentPgId) {
        if (parentPgId == null || parentPgId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "deleted", false,
                    "error", "parentPgId is required"));
        }
        try {
            String pgId = parentPgId.trim();
            nifi.cleanupProcessGroupTree(pgId);
            return ResponseEntity.ok(Map.of(
                    "deleted", true,
                    "parentPgId", pgId));
        } catch (NifiException e) {
            int status = e.status() > 0 ? e.status() : 502;
            return ResponseEntity.status(status).body(Map.of(
                    "deleted", false,
                    "parentPgId", parentPgId.trim(),
                    "error", e.getMessage(),
                    "status", status,
                    "body", e.body()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "deleted", false,
                    "parentPgId", parentPgId.trim(),
                    "error", e.getMessage()));
        }
    }

    /**
     * Lightweight projection for the list page.
     */
    public record PipelineSummary(
            String id, String name, String description,
            Long createdAt, Long updatedAt, Integer nodeCount, String nifiProcessGroupId
    ) {
        static PipelineSummary of(Pipeline p) {
            int nodes = p.dsl() != null && p.dsl().nodes() != null ? p.dsl().nodes().size() : 0;
            return new PipelineSummary(p.id(), p.name(), p.description(),
                    p.createdAt(), p.updatedAt(), nodes, p.nifiProcessGroupId());
        }
    }

    public record PipelineTemplateRequest(
            String tid,
            String templateType
    ) {
        public PipelineTemplateRequest(String tid) {
            this(tid, null);
        }
    }

    public record PipelineTemplateNodeRequest(
            String key,
            String tableId,
            String sourceTableId,
            String targetTableId
    ) {
    }

    public record TemplateNodeResponse(
            String manifestKey,
            Map<String, Object> config
    ) {
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : null;
    }

    private static Object unwrapData(Object raw) {
        Map<String, Object> body = asMap(raw);
        if (body == null) return raw;
        Object data = body.get("data");
        return data != null ? data : raw;
    }

    private static Map<String, Object> firstNonEmptyMap(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Map<String, Object> v = asMap(map.get(key));
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static String firstNonBlank(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof String s && !s.isBlank()) return s;
            if (v != null && !(v instanceof String) && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    private static Pipeline.Node firstNode(Pipeline p, String category) {
        if (p == null || p.dsl() == null || p.dsl().nodes() == null) return null;
        return p.dsl().nodes().stream()
                .filter(n -> category.equalsIgnoreCase(n.category()))
                .findFirst()
                .orElse(null);
    }

    private static String sourceDatabaseText(Map<String, Object> cfg) {
        String jdbc = firstNonBlank(cfg, "jdbcUrl");
        if (!isBlank(jdbc)) return jdbc;
        String host = firstNonBlank(cfg, "host");
        String port = firstNonBlank(cfg, "port");
        String database = firstNonBlank(cfg, "database", "dbName", "schema");
        return joinNonBlank(":", host, port) + (isBlank(database) ? "" : "/" + database);
    }

    private static String targetDatabaseText(Map<String, Object> cfg) {
        String jdbc = firstNonBlank(cfg, "jdbcUrl");
        if (!isBlank(jdbc)) return jdbc;
        return sourceDatabaseText(cfg);
    }

    private static String sourceTablesText(Map<String, Object> cfg) {
        String tables = firstNonBlank(cfg, "tables", "tableList");
        if (!isBlank(tables)) return tables;
        return firstNonBlank(cfg, "table", "tableName", "sourceTable");
    }

    private static String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) parts.add(value);
        }
        return String.join(delimiter, parts);
    }

    private static long maxMetric(JsonNode node, String... keys) {
        long max = 0L;
        for (String key : keys) {
            max = Math.max(max, parseLong(node.path(key)));
        }
        return max;
    }

    private static long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0L;
        if (node.isNumber()) return node.asLong(0L);
        String text = node.asText("");
        if (text == null || text.isBlank()) return 0L;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) digits.append(ch);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return 0L;
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Map<String, Object> loadAssetByTid(String tid) {
        // Collected technical tables are authoritative db_table_t records, not
        // necessarily compiled da_asset_t/Elasticsearch documents. A new or
        // restored table must be usable before business cataloguing/index sync.
        String tenantId = TenantContext.requireTenantId();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select t.tid, t.datasource_id as datasourceId, t.table_name as tableName, "
                            + "t.table_name_en as tableNameEn, t.table_name_cn as tableNameCn, "
                            + "t.table_comment as tableComment, t.table_type as tableType "
                            + "from db_table_t t join db_datasource_t d on d.tid=t.datasource_id "
                            + "and d.tenant_id=? and d.is_del=0 "
                            + "where t.tid=? and t.tenant_id=? and t.is_del=0",
                    tenantId, tid, tenantId);
            if (!rows.isEmpty()) return new LinkedHashMap<>(rows.get(0));
        } catch (org.springframework.dao.DataAccessException legacySchema) {
            log.debug("Technical table projection unavailable; use existing asset adapter");
        }
        try {
            DataAssetQueryByIdRequest request = new DataAssetQueryByIdRequest();
            request.setTid(tid);
            return dataAssetRuntimeAdapter.queryById(request);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<?> loadColumnsByTableId(String tableTid) {
        if (isBlank(tableTid)) return List.of();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("tid", tableTid);
            Object raw = magicApiService.execute("GET",
                    "/dst/database/metadata/columns", body);
            Object data = unwrapData(raw);
            List<?> list = asList(data);
            return list == null ? List.of() : list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ResponseEntity<TemplateNodeResponse> buildSourceTemplateNode(PipelineTemplateNodeRequest request) {
        if (request == null || isBlank(request.tableId())) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> tableAsset = loadAssetByTid(request.tableId().trim());
        if (tableAsset == null || tableAsset.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> dbAsset = loadDbAssetByTable(tableAsset);
        if (dbAsset == null || dbAsset.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> sourceDatasource = expandDatasourceConnection(dbAsset);
        Map<String, Object> physicalSourceDatasource = resolvePushFileStorage(sourceDatasource,
                firstNonBlank(tableAsset, "tableName", "table", "name", "tableCode", "tableNameCn"));
        List<?> sourceColumns = loadColumnsByTableId(request.tableId().trim());
        Map<String, Object> config = buildSourceConfig(physicalSourceDatasource, tableAsset, sourceColumns);
        String manifestKey = normalizeSourceNodeType(firstNonBlank(physicalSourceDatasource,
                "dbType", "db_type", "databaseType", "database_type"), physicalSourceDatasource);
        return ResponseEntity.ok(new TemplateNodeResponse(manifestKey, config));
    }

    private ResponseEntity<TemplateNodeResponse> buildSinkTemplateNode(PipelineTemplateNodeRequest request) {
        if (request == null || isBlank(request.tableId())) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> tableAsset = loadAssetByTid(request.tableId().trim());
        if (tableAsset == null || tableAsset.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> dbAsset = loadDbAssetByTable(tableAsset);
        if (dbAsset == null || dbAsset.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<?> targetColumns = loadColumnsByTableId(request.tableId().trim());
        Map<String, Object> config = buildSinkConfig(dbAsset, tableAsset, targetColumns);
        String targetDbType = firstNonBlank(config, "dbType", "db_type", "databaseType", "database_type");
        String manifestKey = "HIVE".equalsIgnoreCase(targetDbType) ? "sink.hive" : "sink.jdbc";
        return ResponseEntity.ok(new TemplateNodeResponse(manifestKey, config));
    }

    private ResponseEntity<TemplateNodeResponse> buildTransformTemplateNode(PipelineTemplateNodeRequest request, String manifestKey) {
        if (request == null || isBlank(request.sourceTableId()) || isBlank(request.targetTableId())) {
            return ResponseEntity.badRequest().build();
        }
        List<?> sourceColumns = loadColumnsByTableId(request.sourceTableId().trim());
        List<?> targetColumns = loadColumnsByTableId(request.targetTableId().trim());
        Map<String, Object> config = new LinkedHashMap<>();
        if ("transform.field-enrichment".equals(manifestKey)) {
            config.put("executionMode", "AUTO");
        }
        config.put("mappings", buildMappingConfig(sourceColumns, targetColumns,
                request.sourceTableId().trim(), request.targetTableId().trim()));
        return ResponseEntity.ok(new TemplateNodeResponse(manifestKey, config));
    }

    private Map<String, Object> loadDbAssetByTable(Map<String, Object> tableAsset) {
        String dbId = firstNonBlank(tableAsset, "dbId", "belongDbId", "databaseId", "dataSourceId", "datasourceId");
        if (isBlank(dbId)) {
            Map<String, Object> nestedDb = firstNonEmptyMap(tableAsset, "db", "database", "dataSource", "datasource", "belongDataSource");
            if (nestedDb != null && !nestedDb.isEmpty()) {
                return loadDatasourceDetail(firstNonBlank(nestedDb, "tid", "id", "dbId", "datasourceId", "dataSourceId"), nestedDb);
            }
            return null;
        }
        return loadDatasourceDetail(dbId, loadAssetByTid(dbId));
    }

    private Map<String, Object> loadDatasourceDetail(String datasourceId, Map<String, Object> fallback) {
        if (isBlank(datasourceId)) {
            return fallback;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (fallback != null) {
            merged.putAll(fallback);
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("tid", datasourceId);
            Map<String, Object> detail = asMap(unwrapData(magicApiService.execute(
                    "POST", "/dst/database/detail", body)));
            if (detail != null && !detail.isEmpty()) {
                merged.putAll(detail);
            }
        } catch (Exception ignored) {
            // The asset projection below remains available when the Magic API is not registered locally.
        }
        try {
            String tenantId = TenantContext.requireTenantId();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select tid, db_name as dbName, db_type as dbType, "
                            + "username, password, jdbc_url as jdbcUrl "
                            + "from db_datasource_t where tid = ? and tenant_id = ? and is_del = 0",
                    datasourceId, tenantId);
            if (!rows.isEmpty()) {
                merged.putAll(rows.get(0));
                merged.putAll(connectionProperties.resolve(tenantId, datasourceId));
            }
        } catch (Exception ignored) {
            // Keep metadata-derived fields for installations with a different historical datasource schema.
        }
        return merged;
    }

    /**
     * 推送模式并不从登记的数据源本身读取：上游系统已将文件投递到 ODS 非结构化存储。
     * 因而必须以资源中心中 {@value #PUSH_FILE_STORAGE_DOMAIN} 域绑定的 FTP/SFTP 数据源作为
     * 物理来源，并把登记来源数据源 ID 与来源表名拼到该 FTP 的已配置根目录下。
     */
    private Map<String, Object> resolvePushFileStorage(Map<String, Object> logicalDatasource,
                                                        String sourceTableName) {
        if (!isPushReceiveDatasource(logicalDatasource)) {
            return logicalDatasource == null ? Map.of() : logicalDatasource;
        }
        String logicalDatasourceId = firstNonBlank(logicalDatasource,
                "tid", "id", "datasourceId", "dataSourceId", "dbId");
        if (isBlank(logicalDatasourceId) || isBlank(sourceTableName)) {
            throw new IllegalStateException("数据推送任务缺少来源数据源或来源表，无法定位 ODS 文件目录");
        }
        String tenantId = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select d.tid, d.db_name as dbName, d.db_type as dbType, d.jdbc_url as jdbcUrl,
                       d.username, d.password, d.pool_cfg as poolCfg
                  from dwm_center_layer_source_t binding
                  join db_datasource_t d on d.tid = binding.datasource_id
                 where binding.tenant_id = ? and binding.target_type = 'domain'
                   and binding.layer_code = ? and binding.is_del = 0
                   and d.tenant_id = ? and d.is_del = 0
                 order by binding.updated_time desc, binding.tid desc
                 limit 1
                """, tenantId, PUSH_FILE_STORAGE_DOMAIN, tenantId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("资源中心 ODS 域 fjghcc 未绑定可用的 FTP 数据源");
        }

        Map<String, Object> storage = new LinkedHashMap<>(rows.get(0));
        storage.putAll(jsonObject(storage.get("poolCfg")));
        String storageId = firstNonBlank(storage, "tid", "id");
        if (isBlank(storageId)) {
            throw new IllegalStateException("资源中心 ODS 域 fjghcc 的 FTP 数据源配置不完整");
        }
        storage.putAll(connectionProperties.resolve(tenantId, storageId));
        String nodeType = normalizeSourceNodeType(firstNonBlank(storage,
                "dbType", "db_type", "databaseType", "database_type"), storage);
        if (!"source.ftp".equals(nodeType) && !"source.sftp".equals(nodeType)) {
            throw new IllegalStateException("资源中心 ODS 域 fjghcc 绑定的数据源不是 FTP/SFTP 类型");
        }
        String root = firstNonBlank(storage, "ftpPath", "ftp_path", "remotePath", "path");
        if (isBlank(root)) {
            throw new IllegalStateException("资源中心 ODS 域 fjghcc 的 FTP 根路径未配置");
        }
        storage.put("ftpPath", pushFileDirectory(root, logicalDatasourceId, sourceTableName));
        storage.put("logicalSourceDatasourceId", logicalDatasourceId);
        storage.put("pushStorageDomain", PUSH_FILE_STORAGE_DOMAIN);
        return storage;
    }

    private boolean isPushReceiveDatasource(Map<String, Object> datasource) {
        if (datasource == null || datasource.isEmpty()) return false;
        String accessMode = firstNonBlank(datasource, "accessMode", "access_mode",
                "dataAccessMode", "data_access_mode");
        if (isBlank(accessMode)) {
            Map<String, Object> pool = jsonObject(datasource.get("poolCfg"));
            if (pool.isEmpty()) pool = jsonObject(datasource.get("pool_cfg"));
            accessMode = firstNonBlank(pool, "accessMode", "access_mode", "dataAccessMode", "data_access_mode");
        }
        // Historical registrations used both receive and upload for the same
        // user-facing “数据推送方式”; accept both values during migration.
        return "receive".equalsIgnoreCase(accessMode)
                || "upload".equalsIgnoreCase(accessMode)
                || "push".equalsIgnoreCase(accessMode);
    }

    private static String pushFileDirectory(String root, String datasourceId, String tableName) {
        String normalizedRoot = root.trim().replace('\\', '/').replaceAll("/+$", "");
        if (normalizedRoot.isEmpty()) normalizedRoot = "/";
        if (!normalizedRoot.startsWith("/")) normalizedRoot = "/" + normalizedRoot;
        String sourceId = datasourceId.trim().replaceAll("^/+|/+$", "");
        String sourceTable = tableName.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
        if (sourceId.isEmpty() || sourceTable.isEmpty() || sourceId.contains("..") || sourceTable.contains("..")) {
            throw new IllegalArgumentException("来源数据源 ID 或表名不能用于构造 FTP 文件目录");
        }
        return "/".equals(normalizedRoot)
                ? "/" + sourceId + "/" + sourceTable + "/"
                : normalizedRoot + "/" + sourceId + "/" + sourceTable + "/";
    }

    private Map<String, Object> buildSourceConfig(Map<String, Object> sourceDs, Map<String, Object> tableAsset, List<?> sourceColumns) {
        sourceDs = expandDatasourceConnection(sourceDs);
        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        sourceConfig.put("registeredDatasourceId", firstNonBlank(sourceDs, "tid", "id", "datasourceId", "dataSourceId", "dbId"));
        sourceConfig.put("sourceDbId", sourceConfig.get("registeredDatasourceId"));
        sourceConfig.put("sourceTableId", firstNonBlank(tableAsset, "tid", "id", "tableId"));
        copyJdbcDriverProperties(sourceDs, sourceConfig);
        String sourceNodeType = normalizeSourceNodeType(firstNonBlank(sourceDs,
                "dbType", "db_type", "databaseType", "database_type"), sourceDs);
        if ("source.ftp".equals(sourceNodeType) || "source.sftp".equals(sourceNodeType)) {
            boolean sftp = "source.sftp".equals(sourceNodeType);
            Integer ftpPort = parsePort(sourceDs, "ftpPort", "ftp_port", "port");
            sourceConfig.put("protocol", sftp ? "sftp" : firstNonBlank(sourceDs, "ftpProtocol", "ftp_protocol", "protocol"));
            sourceConfig.put("hostname", firstNonBlank(sourceDs, "ftpHost", "ftp_host", "hostname", "host"));
            sourceConfig.put("port", ftpPort == null ? (sftp ? 22 : 21) : ftpPort);
            sourceConfig.put("username", firstNonBlank(sourceDs, "ftpUsername", "ftp_username", "username"));
            sourceConfig.put("password", firstNonBlank(sourceDs, "ftpPassword", "ftp_password", "password"));
            sourceConfig.put("remotePath", firstNonBlank(sourceDs, "ftpPath", "ftp_path", "remotePath", "path") == null
                    ? "/" : firstNonBlank(sourceDs, "ftpPath", "ftp_path", "remotePath", "path"));
            sourceConfig.put("fileFilterRegex", firstNonBlank(sourceDs, "ftpFilePattern", "ftp_file_pattern", "fileFilterRegex", "filePattern") == null
                    ? ".*" : firstNonBlank(sourceDs, "ftpFilePattern", "ftp_file_pattern", "fileFilterRegex", "filePattern"));
            sourceConfig.put("passiveMode", firstNonBlank(sourceDs, "ftpPassiveMode", "ftp_passive_mode", "passiveMode"));
            sourceConfig.put("schedulingPeriod", "60 sec");
            sourceConfig.put("sourceColumns", sourceColumns == null ? List.of() : sourceColumns);
            sourceConfig.put("table", firstNonBlank(tableAsset, "tableName", "table", "name", "tableCode", "tableNameCn"));
            String registeredTable = firstNonBlank(sourceConfig, "table");
            if (isBlank(firstNonBlank(sourceDs, "logicalSourceDatasourceId", "logical_source_datasource_id"))
                    && !isBlank(registeredTable)) {
                sourceConfig.put("fileFilterRegex", "(?i)^" + java.util.regex.Pattern.quote(registeredTable)
                        + "\\.(csv|json|xlsx|xls)$");
            }
            // Data-report templates expose registered Chinese display labels
            // in their first row. Persist that input contract with the FTP
            // node so the compiler never queries the workbook with physical
            // English column names such as vehicle_id.
            sourceConfig.put("fileHeaderMode", isBlank(firstNonBlank(sourceDs,
                    "logicalSourceDatasourceId", "logical_source_datasource_id"))
                    ? "physical" : "registered-chinese");
            // The report client uses the same display-name precedence when it
            // creates the one-sheet upload workbook. Persist it with the
            // source configuration instead of trying to infer it from the FTP
            // file name, which is intentionally sanitized during upload.
            sourceConfig.put("excelSheetName", firstNonBlank(tableAsset,
                    "tableNameCn", "table_name_cn", "tableComment", "table_comment",
                    "tableName", "table", "name", "tableCode"));
            sourceConfig.put("registeredDatasourceId", firstNonBlank(sourceDs,
                    "tid", "id", "datasourceId", "dataSourceId", "dbId"));
            sourceConfig.put("logicalSourceDatasourceId", firstNonBlank(sourceDs,
                    "logicalSourceDatasourceId", "logical_source_datasource_id"));
            sourceConfig.put("pushStorageDomain", firstNonBlank(sourceDs,
                    "pushStorageDomain", "push_storage_domain"));
            sourceConfig.put("sourceTableId", firstNonBlank(tableAsset, "tid", "id", "tableId"));
            return sourceConfig;
        }
        if ("source.api".equals(sourceNodeType)) {
            Map<String, Object> api = resolveApiPullItem(sourceDs, tableAsset);
            String url = apiRequestUrl(api);
            String resolvedUrl = isBlank(url) ? firstNonBlank(sourceDs, "apiUrl", "api_url", "url") : url;
            String resolvedMethod = firstNonBlank(api, "method", "endpointMethod", "endpoint_method", "apiMethod", "api_method")
                    == null ? firstNonBlank(sourceDs, "apiMethod", "api_method", "method") : firstNonBlank(api, "method", "endpointMethod", "endpoint_method", "apiMethod", "api_method");
            sourceConfig.put("url", resolvedUrl);
            sourceConfig.put("apiUrl", resolvedUrl);
            sourceConfig.put("method", resolvedMethod);
            sourceConfig.put("apiMethod", resolvedMethod);
            String headers = apiHeadersJson(api);
            String contentType = apiContentType(headers, firstNonBlank(api, "contentType", "content_type", "requestContentType", "request_content_type"));
            sourceConfig.put("contentType", contentType == null ? firstNonBlank(sourceDs, "contentType", "content_type") : contentType);
            // Keep the deployed NiFi field and the generic field-probe field in
            // sync.  This lets the canvas re-run registration's API discovery
            // without asking users to re-enter headers or request bodies.
            sourceConfig.put("commonHeadersJson", headers);
            sourceConfig.put("apiHeaders", headers);
            sourceConfig.put("headers", headers);
            String requestBody = firstNonBlank(api, "requestTemplateJson", "request_template_json", "requestBody", "request_body", "apiBody", "api_body")
                    == null ? firstNonBlank(sourceDs, "apiBody", "api_body", "requestBody", "request_body") : firstNonBlank(api, "requestTemplateJson", "request_template_json", "requestBody", "request_body", "apiBody", "api_body");
            sourceConfig.put("requestBody", requestBody);
            sourceConfig.put("apiBody", requestBody);
            sourceConfig.put("authType", firstNonBlank(api, "authType", "auth_type"));
            sourceConfig.put("credentialRef", firstNonBlank(api, "credentialRef", "credential_ref"));
            sourceConfig.put("connectTimeout", apiDuration(api, sourceDs, "connectTimeout", "connect_timeout", "connectionTimeout"));
            sourceConfig.put("readTimeout", apiDuration(api, sourceDs, "readTimeout", "read_timeout"));
            applyApiSchedule(sourceConfig, api);
            sourceConfig.put("sourceColumns", sourceColumns == null ? List.of() : sourceColumns);
            sourceConfig.put("table", firstNonBlank(tableAsset, "tableName", "table", "name", "tableCode", "tableNameCn"));
            sourceConfig.put("registeredDatasourceId", firstNonBlank(sourceDs, "tid", "id", "datasourceId", "dataSourceId", "dbId"));
            sourceConfig.put("sourceTableId", firstNonBlank(tableAsset, "tid", "id", "tableId"));
            sourceConfig.put("responseConfigJson", firstNonBlank(api, "responseConfigJson", "response_config_json"));
            sourceConfig.put("paginationConfigJson", firstNonBlank(api, "paginationConfigJson", "pagination_config_json"));
            sourceConfig.put("incrementalConfigJson", firstNonBlank(api, "incrementalConfigJson", "incremental_config_json"));
            return sourceConfig;
        }
        // Non-JDBC connectors need their own registered endpoint/security options too.
        for (String option : List.of("apiKey", "authMethod", "bootstrapServers", "credentialRef", "defaultIndex",
                "fileFilterRegex", "groupId", "kerberosPrincipal", "kerberosServiceName", "maxPollRecords",
                "messageFormat", "minioAccessKey", "minioBucket", "minioEndpoint", "minioFilePattern",
                "minioPrefix", "minioSecretKey", "nodes", "path", "protocol", "queryJson", "rowStart",
                "rowStop", "saslMechanism", "schemaRegistryUrl", "schemaRequired", "securityProtocol",
                "topic", "trustStorePath", "version", "extraParams", "compatibleMode")) {
            if (sourceDs.get(option) != null) sourceConfig.put(option, sourceDs.get(option));
        }
        sourceConfig.put("host", firstNonBlank(sourceDs, "host", "dbMetaIp"));
        sourceConfig.put("port", parsePort(sourceDs, "port", "dbMetaPort"));
        sourceConfig.put("database", firstNonBlank(sourceDs, "database", "dbName", "dbMetaDbName"));
        sourceConfig.put("username", firstNonBlank(sourceDs, "username", "dbMetaUser"));
        sourceConfig.put("password", firstNonBlank(sourceDs, "password", "dbMetaPassword"));
        String normalizedDbType = RegisteredDatasourceType.configType(firstNonBlank(sourceDs,
                "dbType", "db_type", "databaseType", "database_type"), sourceDs);
        sourceConfig.put("dbType", normalizedDbType);
        // The node manifest and the connection payload are separate concerns:
        // retain the registered endpoint contract when a source template is
        // generated instead of relying on a client-side default.
        if ("source.oracle".equals(sourceNodeType)) {
            String connectionType = normalizeOracleConnectionType(firstNonBlank(sourceDs,
                    "connectionType", "connection_type", "jdbcType", "jdbc_type"));
            if (!isBlank(connectionType)) {
                sourceConfig.put("connectionType", connectionType);
                sourceConfig.put("jdbcType", connectionType);
            }
            String service = firstNonBlank(sourceDs, "sid", "serviceName", "service_name", "service", "database", "dbName");
            if (!isBlank(service)) {
                sourceConfig.put("sid", service);
                sourceConfig.put("serviceName", service);
                sourceConfig.put("database", service);
            }
        }
        if ("source.kingbase".equals(sourceNodeType)) {
            copyIfPresent(sourceDs, sourceConfig, "compatibleMode", "compatibleMode", "compatible_mode");
        }
        if ("source.oceanbase".equals(sourceNodeType)) {
            sourceConfig.put("compatibleMode", "OCEANBASE_ORACLE".equals(normalizedDbType) ? "ORACLE" : "MYSQL");
            copyIfPresent(sourceDs, sourceConfig, "tenant", "tenant", "obTenant", "ob_tenant");
            copyIfPresent(sourceDs, sourceConfig, "clusterId", "clusterId", "cluster_id");
        }
        // The canvas keeps a reference to the registered datasource so MRS Hive
        // probes can reload server-owned connection settings in the active tenant.
        sourceConfig.put("registeredDatasourceId", firstNonBlank(sourceDs,
                "tid", "id", "datasourceId", "dataSourceId", "dbId"));
        sourceConfig.put("metadataAccessMode", firstNonBlank(sourceDs,
                "metadataAccessMode", "metadata_access_mode"));
        sourceConfig.put("hiveConnectionMode", firstNonBlank(sourceDs,
                "hiveConnectionMode", "hive_connection_mode"));
        sourceConfig.put("hiveProfile", firstNonBlank(sourceDs,
                "hiveProfile", "hive_profile"));
        if (!usesServerManagedHiveProfile(sourceConfig)) {
            copyHuaweiMrsConnectionSettings(sourceDs, sourceConfig);
        }
        sourceConfig.put("jdbcUrl", RegisteredDatasourceType.jdbcUrl(firstNonBlank(sourceDs,
                "dbType", "db_type", "databaseType", "database_type"), sourceDs, Map.of()));
        if (usesServerManagedHiveProfile(sourceConfig)) {
            for (String privateKey : List.of("jdbcURL", "jdbc_url", "keytabPath", "krb5ConfPath",
                    "jaasConfPath", "trustStorePassword", "userPrincipal", "principal", "password")) {
                sourceConfig.remove(privateKey);
            }
            sourceConfig.remove("jdbcUrl");
        } else if ("HIVE".equalsIgnoreCase(String.valueOf(sourceConfig.get("dbType")))
                && isBlank(firstNonBlank(sourceConfig, "jdbcUrl"))) {
            sourceConfig.put("jdbcUrl", buildHuaweiMrsHiveJdbcUrl(sourceConfig));
        }
        if ("KERBEROS".equalsIgnoreCase(String.valueOf(sourceConfig.get("authMode")))) {
            sourceConfig.remove("username");
            sourceConfig.remove("password");
        }
        sourceConfig.put("useSSL", false);
        sourceConfig.put("serverTimezone", "Asia/Shanghai");
        sourceConfig.put("defaultFetchSize", 1000);
        sourceConfig.put("table", firstNonBlank(tableAsset, "tableName", "table", "name", "tableCode", "tableNameCn"));
        // Keep the registered table identity, not merely its display name, so
        // the editor can re-read the same tenant-scoped governance metadata.
        sourceConfig.put("sourceTableId", firstNonBlank(tableAsset, "tid", "id", "tableId"));
        sourceConfig.put("sourceColumns", sourceColumns == null ? List.of() : sourceColumns);
        // 第四步登记时明确指定的“抽取时间/时间戳”才可以作为增量水位。
        // 不能再把主键当作默认增量字段：主键不一定单调递增，且会掩盖业务表
        // 尚未完成时间戳登记的情况。没有已登记的时间戳时，保留为空并由用户手工选择。
        String registeredTimestamp = registeredTimestampColumnForTable(tableAsset, sourceColumns);
        if (!isBlank(registeredTimestamp)) {
            sourceConfig.put("incrementalColumn", registeredTimestamp);
        }
        sourceConfig.put("schedulingPeriod", "60 sec");
        sourceConfig.put("syncMode", "FULL_THEN_INCR");
        sourceConfig.put("initialStrategy", "START_AT_BEGINNING");
        sourceConfig.put("lookbackSeconds", 5);
        sourceConfig.put("safetyLagSeconds", 1);
        sourceConfig.put("batchSize", 5000);
        sourceConfig.put("maxRowsPerRun", 200000);
        sourceConfig.put("softDeleteEnabled", false);
        sourceConfig.put("deleteReconcileStrategy", "NONE");
        return sourceConfig;
    }

    /**
     * The first registration step stores one API definition per logical table in
     * db_datasource_t.pool_cfg.  A task must use the definition for its source
     * table, rather than the obsolete single apiUrl/apiBody columns.
     */
    private Map<String, Object> resolveApiPullItem(Map<String, Object> source, Map<String, Object> table) {
        String tableName = firstNonBlank(table, "tableName", "table", "name", "tableCode", "tableNameCn");
        List<Map<String, Object>> items = apiPullItems(source);
        for (Map<String, Object> item : items) {
            String candidate = firstNonBlank(item, "tableName", "table_name", "sourceTableName", "source_table_name");
            if (!isBlank(tableName) && tableName.equalsIgnoreCase(candidate)) return item;
        }
        // A legacy single-interface source has no table name.  It is still safe
        // to use it only when it is the sole registered interface.
        return items.size() == 1 ? items.get(0) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> apiPullItems(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return List.of();
        Object raw = source.get("apiPullItems");
        if (raw == null) raw = source.get("api_pull_items");
        if (raw == null) {
            Object poolCfg = source.get("poolCfg");
            if (poolCfg == null) poolCfg = source.get("pool_cfg");
            Map<String, Object> pool = jsonObject(poolCfg);
            raw = pool.get("apiPullItems");
            if (raw == null) raw = pool.get("api_pull_items");
        }
        if (raw instanceof String text) {
            try {
                raw = JSON.readValue(text, new TypeReference<List<Map<String, Object>>>() { });
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((key, value) -> item.put(String.valueOf(key), value));
                result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> jsonObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (!(value instanceof String text) || text.isBlank()) return Map.of();
        try {
            return JSON.readValue(text, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String apiRequestUrl(Map<String, Object> api) {
        String direct = firstNonBlank(api, "url", "requestUrl", "request_url", "apiUrl", "api_url", "endpointUrl", "endpoint_url");
        if (!isBlank(direct)) return direct;
        String base = firstNonBlank(api, "baseUrl", "base_url");
        String path = firstNonBlank(api, "endpointPath", "endpoint_path", "path");
        if (isBlank(base)) return path;
        if (isBlank(path)) return base;
        return base.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
    }

    private String apiHeadersJson(Map<String, Object> api) {
        Object raw = api.get("requestHeadersJson");
        if (raw == null) raw = api.get("commonHeadersJson");
        if (raw == null) raw = api.get("requestHeaders");
        if (raw == null) return "";
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw instanceof String text) {
            try {
                JsonNode parsed = JSON.readTree(text);
                if (parsed != null && parsed.isObject()) {
                    parsed.fields().forEachRemaining(entry -> addApiHeader(headers, entry.getKey(), entry.getValue().asText("")));
                } else if (parsed != null && parsed.isArray()) {
                    parsed.forEach(entry -> addApiHeader(headers,
                            firstJsonText(entry, "key", "name", "headerName"), firstJsonText(entry, "value", "headerValue")));
                }
            } catch (Exception ignored) {
                return "";
            }
        } else if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> addApiHeader(headers, String.valueOf(key), value == null ? "" : String.valueOf(value)));
        } else if (raw instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) continue;
                addApiHeader(headers, firstMapText(map, "key", "name", "headerName"), firstMapText(map, "value", "headerValue"));
            }
        }
        try {
            return headers.isEmpty() ? "" : JSON.writeValueAsString(headers);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void addApiHeader(Map<String, String> headers, String name, String value) {
        if (isBlank(name) || isSensitiveHeader(name)) return;
        headers.put(name.trim(), value == null ? "" : value);
    }

    private static boolean isSensitiveHeader(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return key.equals("authorization") || key.contains("token") || key.contains("secret")
                || key.contains("password") || key.contains("api-key") || key.contains("apikey");
    }

    private static String firstJsonText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.asText("").isBlank()) return value.asText();
        }
        return null;
    }

    private static String firstMapText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private String apiContentType(String headers, String fallback) {
        try {
            JsonNode parsed = JSON.readTree(headers == null ? "{}" : headers);
            if (parsed != null && parsed.isObject()) {
                java.util.Iterator<Map.Entry<String, JsonNode>> entries = parsed.fields();
                while (entries.hasNext()) {
                    Map.Entry<String, JsonNode> entry = entries.next();
                    if ("content-type".equalsIgnoreCase(entry.getKey())) return entry.getValue().asText();
                }
            }
        } catch (Exception ignored) { }
        return isBlank(fallback) ? "application/json" : fallback;
    }

    private String apiDuration(Map<String, Object> api, Map<String, Object> source, String... keys) {
        String value = firstNonBlank(api, keys);
        if (isBlank(value)) value = firstNonBlank(source, keys);
        if (!isBlank(value)) return value;
        String seconds = firstNonBlank(source, "apiTimeoutSeconds", "api_timeout_seconds");
        return isBlank(seconds) ? null : seconds + " sec";
    }

    private void applyApiSchedule(Map<String, Object> config, Map<String, Object> api) {
        Map<String, Object> schedule = jsonObject(api.get("captureSchedule"));
        if (schedule.isEmpty()) schedule = jsonObject(api.get("capture_schedule"));
        String mode = firstNonBlank(schedule, "mode");
        if (isBlank(mode)) mode = firstNonBlank(api, "scheduleMode", "schedule_mode", "triggerMode", "trigger_mode");
        mode = isBlank(mode) ? "once" : mode.trim().toLowerCase(Locale.ROOT);
        String cron = firstNonBlank(schedule, "cronExpression", "cron_expression");
        if (isBlank(cron)) cron = firstNonBlank(api, "cronExpression", "cron_expression");
        if (!isBlank(cron) || "cron".equals(mode) || "specified".equals(mode)) {
            config.put("schedulingStrategy", "CRON_DRIVEN");
            config.put("schedulingPeriod", isBlank(cron) ? "0 0 9 * * ?" : cron);
            config.put("scheduleMode", "cron");
            return;
        }
        if ("interval".equals(mode)) {
            String minutes = firstNonBlank(schedule, "intervalMinutes", "interval_minutes");
            if (isBlank(minutes)) minutes = firstNonBlank(api, "scheduleIntervalMinutes", "schedule_interval_minutes");
            config.put("schedulingStrategy", "TIMER_DRIVEN");
            config.put("schedulingPeriod", (isBlank(minutes) ? "60" : minutes) + " min");
            config.put("scheduleMode", "interval");
            return;
        }
        if ("time".equals(mode)) {
            String time = firstNonBlank(schedule, "timeOfDay", "time_of_day");
            if (isBlank(time)) time = firstNonBlank(api, "scheduleTimeOfDay", "schedule_time_of_day");
            String[] parts = (isBlank(time) ? "09:00" : time).split(":", 2);
            String hour = parts.length > 0 && parts[0].matches("\\d{1,2}") ? parts[0] : "9";
            String minute = parts.length > 1 && parts[1].matches("\\d{1,2}") ? parts[1] : "0";
            String period = firstNonBlank(schedule, "timePeriod", "time_period");
            if (isBlank(period)) period = firstNonBlank(api, "scheduleTimePeriod", "schedule_time_period");
            String expression = "0 " + minute + " " + hour + " * * ?";
            if ("workday".equalsIgnoreCase(period)) expression = "0 " + minute + " " + hour + " ? * MON-FRI";
            if ("weekly".equalsIgnoreCase(period)) {
                String weekday = firstNonBlank(schedule, "weekday");
                if (isBlank(weekday)) weekday = firstNonBlank(api, "scheduleWeekday", "schedule_weekday");
                expression = "0 " + minute + " " + hour + " ? * " + (isBlank(weekday) ? "MON" : weekday.toUpperCase(Locale.ROOT));
            }
            config.put("schedulingStrategy", "CRON_DRIVEN");
            config.put("schedulingPeriod", expression);
            config.put("scheduleMode", "time");
            return;
        }
        // “仅执行一次” is represented as one execution for each explicit task
        // start.  A very long timer prevents the scheduler from polling again.
        config.put("schedulingStrategy", "TIMER_DRIVEN");
        config.put("schedulingPeriod", "365 days");
        config.put("scheduleMode", "once");
    }

    private Map<String, Object> buildSinkConfig(Map<String, Object> targetDs, Map<String, Object> tableAsset, List<?> targetColumns) {
        targetDs = expandDatasourceConnection(targetDs);
        Map<String, Object> sinkConfig = new LinkedHashMap<>();
        // Task linkage is a registered identifier, never a JDBC URL or table name.
        sinkConfig.put("targetDbId", firstNonBlank(targetDs, "tid", "id", "datasourceId", "dataSourceId", "dbId"));
        sinkConfig.put("targetTableId", firstNonBlank(tableAsset, "tid", "id", "tableId"));
        copyJdbcDriverProperties(targetDs, sinkConfig);
        String dbType = firstNonBlank(targetDs, "dbType", "db_type", "databaseType", "database_type");
        String host = firstNonBlank(targetDs, "host", "dbMetaIp");
        Integer port = parsePort(targetDs, "port", "dbMetaPort");
        String database = firstNonBlank(targetDs, "database", "dbName", "dbMetaDbName");
        String normalizedDbType = normalizeSinkDbType(dbType, targetDs);
        sinkConfig.put("dbType", normalizedDbType);
        sinkConfig.put("hiveProfile", firstNonBlank(targetDs, "hiveProfile", "hive_profile"));
        sinkConfig.put("metadataAccessMode", firstNonBlank(targetDs,
                "metadataAccessMode", "metadata_access_mode"));
        sinkConfig.put("hiveConnectionMode", firstNonBlank(targetDs,
                "hiveConnectionMode", "hive_connection_mode"));
        if (!usesServerManagedHiveProfile(sinkConfig)) {
            copyHuaweiMrsConnectionSettings(targetDs, sinkConfig);
        }
        sinkConfig.put("jdbcUrl", firstNonBlank(targetDs, "jdbcUrl", "jdbcURL", "jdbc_url") == null
                ? ("HIVE".equalsIgnoreCase(normalizedDbType)
                        ? buildHuaweiMrsHiveJdbcUrl(mergeConnectionBasics(sinkConfig, host, port, database))
                        : RegisteredDatasourceType.jdbcUrl(dbType, targetDs, Map.of()))
                : firstNonBlank(targetDs, "jdbcUrl", "jdbcURL", "jdbc_url"));
        if (usesServerManagedHiveProfile(sinkConfig)) {
            sinkConfig.remove("jdbcUrl");
        }
        sinkConfig.put("username", firstNonBlank(targetDs, "username", "dbMetaUser"));
        sinkConfig.put("password", firstNonBlank(targetDs, "password", "dbMetaPassword"));
        if ("KERBEROS".equalsIgnoreCase(String.valueOf(sinkConfig.get("authMode")))) {
            sinkConfig.remove("username");
            sinkConfig.remove("password");
        }
        sinkConfig.put("table", nifiTargetTableName(
                normalizedDbType,
                targetDs,
                firstNonBlank(tableAsset, "tableName", "targetTableName", "table", "name", "tableCode", "tableNameCn")));
        sinkConfig.put("targetColumns", targetColumns == null ? List.of() : targetColumns);
        String updateKeys = joinUpdateKeys(targetColumns);
        // A generated ODS_UUID identifies an ingestion event, not a stable source record.
        // Only choose UPSERT when the target exposes a business primary/unique key.
        boolean hiveSink = "HIVE".equalsIgnoreCase(normalizedDbType);
        boolean linewellOracleWriter = isLinewellOracleWriterTarget(normalizedDbType);
        sinkConfig.put("statementType", hiveSink ? "INSERT"
                : (linewellOracleWriter ? "MERGE" : (isBlank(updateKeys) ? "INSERT" : "UPSERT")));
        sinkConfig.put("updateKeys", hiveSink ? "" : updateKeys);
        // New materialization flows explicitly persist their selected writer.
        // This makes the deployment deterministic even when a manifest later adds
        // another option, and keeps old flows unchanged until they are edited.
        if (hiveSink) {
            sinkConfig.put("hiveWriteMode", "LINEWELL_HDFS");
        } else {
            sinkConfig.put("writerType", linewellOracleWriter
                    ? "LINEWELL_PUT_DATABASE_RECORD"
                    : "NIFI_PUT_DATABASE_RECORD");
        }
        return sinkConfig;
    }

    private static Map<String, Object> expandDatasourceConnection(Map<String, Object> datasource) {
        if (datasource == null || datasource.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> expanded = new LinkedHashMap<>(datasource);
        return expanded;
    }

    private static void copyJdbcDriverProperties(Map<String, Object> datasource, Map<String, Object> target) {
        Map<String, String> jdbcProperties = JdbcDriverPropertyResolver.resolve(datasource);
        if (!jdbcProperties.isEmpty()) {
            target.put("jdbcProperties", jdbcProperties);
        }
    }

    /**
     * Carries only non-secret MRS connection settings and server-local credential paths into
     * the canvas. The keytab itself is never read, uploaded or persisted by the canvas.
     */
    private static void copyHuaweiMrsConnectionSettings(Map<String, Object> source, Map<String, Object> target) {
        copyIfPresent(source, target, "authMode", "authMode", "auth_mode", "auth");
        copyIfPresent(source, target, "zookeeperQuorum", "zookeeperQuorum", "zookeeper_quorum", "zkQuorum", "zk_quorum");
        copyIfPresent(source, target, "serviceDiscoveryMode", "serviceDiscoveryMode", "service_discovery_mode");
        copyIfPresent(source, target, "zookeeperNamespace", "zookeeperNamespace", "zooKeeperNamespace", "zookeeper_namespace");
        copyIfPresent(source, target, "principal", "principal", "hivePrincipal", "servicePrincipal");
        copyIfPresent(source, target, "userPrincipal", "userPrincipal", "clientPrincipal", "kerberosUserPrincipal");
        copyIfPresent(source, target, "keytabPath", "keytabPath", "keytab", "userKeytab");
        copyIfPresent(source, target, "krb5ConfPath", "krb5ConfPath", "krb5Conf", "krb5");
        copyIfPresent(source, target, "clientConfigDir", "clientConfigDir", "mrsClientConfigDir");
        copyIfPresent(source, target, "saslQop", "saslQop", "sasl.qop");
        copyIfPresent(source, target, "ssl", "ssl");
        copyIfPresent(source, target, "driverLocations", "driverLocations", "nifiDriverLocations");
        copyIfPresent(source, target, "extraParams", "extraParams");
    }

    private static boolean usesServerManagedHiveProfile(Map<String, Object> config) {
        if (config == null) return false;
        String profile = firstNonBlank(config, "hiveProfile", "hive_profile");
        String mode = firstNonBlank(config, "metadataAccessMode", "metadata_access_mode");
        String connectionMode = firstNonBlank(config, "hiveConnectionMode", "hive_connection_mode");
        return !isBlank(profile)
                || "server-managed-mrs".equalsIgnoreCase(mode)
                || "huawei-mrs".equalsIgnoreCase(connectionMode);
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target,
                                      String targetKey, String... sourceKeys) {
        String value = firstNonBlank(source, sourceKeys);
        if (!isBlank(value)) {
            target.put(targetKey, value);
        }
    }

    private static Map<String, Object> mergeConnectionBasics(Map<String, Object> config,
                                                              String host, Integer port, String database) {
        Map<String, Object> result = new LinkedHashMap<>(config);
        result.put("host", host);
        result.put("port", port == null ? 10000 : port);
        result.put("database", database);
        return result;
    }

    private static String buildHuaweiMrsHiveJdbcUrl(Map<String, Object> config) {
        String quorum = firstNonBlank(config, "zookeeperQuorum", "zkQuorum");
        String host = firstNonBlank(config, "host");
        String port = firstNonBlank(config, "port");
        String database = firstNonBlank(config, "database", "dbName");
        String endpoint = isBlank(quorum)
                ? (isBlank(host) ? "" : host + ":" + (isBlank(port) ? "10000" : port))
                : quorum;
        if (isBlank(endpoint)) {
            return null;
        }
        StringBuilder url = new StringBuilder("jdbc:hive2://")
                .append(endpoint).append("/").append(isBlank(database) ? "default" : database);
        if (!isBlank(quorum)) {
            appendHiveJdbcParameter(url, "serviceDiscoveryMode", firstNonBlank(config,
                    "serviceDiscoveryMode", "service_discovery_mode") == null ? "zooKeeper" : firstNonBlank(config, "serviceDiscoveryMode", "service_discovery_mode"));
            appendHiveJdbcParameter(url, "zooKeeperNamespace", firstNonBlank(config,
                    "zookeeperNamespace", "zooKeeperNamespace", "zookeeper_namespace") == null ? "hiveserver2" : firstNonBlank(config, "zookeeperNamespace", "zooKeeperNamespace", "zookeeper_namespace"));
        }
        if ("KERBEROS".equalsIgnoreCase(firstNonBlank(config, "authMode", "auth"))) {
            appendHiveJdbcParameter(url, "auth", "KERBEROS");
            appendHiveJdbcParameter(url, "sasl.qop", firstNonBlank(config, "saslQop", "sasl.qop"));
            appendHiveJdbcParameter(url, "principal", firstNonBlank(config, "principal", "hivePrincipal"));
            appendHiveJdbcParameter(url, "ssl", firstNonBlank(config, "ssl"));
            appendHiveJdbcParameter(url, "user.principal", firstNonBlank(config, "userPrincipal", "clientPrincipal"));
            appendHiveJdbcParameter(url, "user.keytab", firstNonBlank(config, "keytabPath", "keytab"));
        } else if (!isBlank(quorum)) {
            appendHiveJdbcParameter(url, "auth", "none");
        }
        String extra = firstNonBlank(config, "extraParams");
        if (!isBlank(extra)) {
            url.append(extra.startsWith(";") ? extra : ";" + extra.replace("&", ";"));
        }
        return url.toString();
    }

    private static void appendHiveJdbcParameter(StringBuilder url, String key, String value) {
        if (!isBlank(value)) {
            url.append(";").append(key).append("=").append(value.trim());
        }
    }

    /**
     * Resolves the timestamp selected in the data-source registration's fourth
     * step. The registered source table snapshot is authoritative: the fourth
     * step persists its field roles in {@code db_table_t.field_governance_config}.
     * Older registrations may still have the same snapshot in {@code da_prop_t},
     * and the asset projection remains a final compatibility fallback.
     */
    private String registeredTimestampColumnForTable(Map<String, Object> tableAsset, List<?> sourceColumns) {
        String tableId = firstNonBlank(tableAsset, "tid", "id", "tableId");
        Map<String, Object> governance = loadRegisteredGovernanceConfig(tableId);
        if (governance.isEmpty()) {
            governance = governanceConfigFromAsset(tableAsset);
        }
        return registeredTimestampColumn(governance, sourceColumns);
    }

    private Map<String, Object> loadRegisteredGovernanceConfig(String tableId) {
        if (isBlank(tableId)) return Map.of();
        try {
            String tenantId = TenantContext.requireTenantId();
            // Step 4 (business/log table registration) saves the explicit
            // “data extraction time” choice on the registered table itself.
            // Do not depend solely on the legacy directory-property mirror:
            // it may not exist for a newly registered table, which previously
            // caused the source-node primary incremental field to stay blank.
            List<Map<String, Object>> tableRows = jdbcTemplate.queryForList("""
                    select field_governance_config as fieldGovernanceConfig
                      from db_table_t
                     where tenant_id = ? and tid = ?
                       and (is_del = 0 or is_del is null)
                    """, tenantId, tableId.trim());
            for (Map<String, Object> row : tableRows) {
                Map<String, Object> config = jsonObject(firstNonBlank(row,
                        "fieldGovernanceConfig", "field_governance_config"));
                if (!config.isEmpty()) return config;
            }

            // Compatibility for historical records created before governance
            // snapshots were persisted on db_table_t.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    select prop_value as propValue
                      from da_prop_t
                     where tenant_id = ? and parent_id = ?
                       and prop_name = 'fieldGovernanceConfig'
                       and (is_del = 0 or is_del is null)
                     order by updated_time desc, tid desc
                    """, tenantId, tableId.trim());
            for (Map<String, Object> row : rows) {
                Map<String, Object> config = jsonObject(firstNonBlank(row, "propValue", "prop_value"));
                if (!config.isEmpty()) return config;
            }
        } catch (Exception ignored) {
            // Older installations may not have migrated the unified property
            // table yet. The table asset fallback below keeps them readable.
        }
        return Map.of();
    }

    private Map<String, Object> governanceConfigFromAsset(Map<String, Object> tableAsset) {
        if (tableAsset == null || tableAsset.isEmpty()) return Map.of();
        for (String key : List.of("fieldGovernanceConfig", "field_governance_config", "fieldGovernanceCfg")) {
            Map<String, Object> config = jsonObject(tableAsset.get(key));
            if (!config.isEmpty()) return config;
        }
        return Map.of();
    }

    /**
     * Returns a physical source-column name only when registration explicitly
     * marked it as the timestamp/extraction field. No name or SQL-type heuristic
     * is used here: a DATE column can be a business date rather than a safe
     * incremental watermark.
     */
    static String registeredTimestampColumn(Map<String, Object> governance, List<?> sourceColumns) {
        if (governance == null || governance.isEmpty() || sourceColumns == null || sourceColumns.isEmpty()) {
            return null;
        }
        String selected = firstNonBlank(governance, "timestampField", "timestamp_field");
        String physical = physicalColumnName(sourceColumns, selected);
        if (!isBlank(physical)) return physical;

        List<?> fields = asList(governance.get("fields"));
        if (fields == null) return null;
        for (Object field : fields) {
            if (!isRegisteredTimestampField(field)) continue;
            String name = columnName(field);
            physical = physicalColumnName(sourceColumns, name);
            if (!isBlank(physical)) return physical;
        }
        return null;
    }

    private static boolean isRegisteredTimestampField(Object field) {
        if (isTruthyColumnFlag(field, "isTimestampField")
                || isTruthyColumnFlag(field, "is_timestamp_field")
                || isTruthyColumnFlag(field, "isTimestamp")) {
            return true;
        }
        Object roles = columnValue(field, "timeRoles");
        if (roles instanceof List<?> list) {
            return list.stream().anyMatch(role -> "timestamp".equalsIgnoreCase(String.valueOf(role).trim()));
        }
        if (roles != null && "timestamp".equalsIgnoreCase(String.valueOf(roles).trim())) {
            return true;
        }
        String role = columnString(field, "timeRole");
        if (isBlank(role)) role = columnString(field, "time_role");
        return !isBlank(role) && "timestamp".equalsIgnoreCase(role.trim());
    }

    private static String physicalColumnName(List<?> columns, String configuredName) {
        if (isBlank(configuredName) || columns == null) return null;
        for (Object column : columns) {
            String name = columnName(column);
            if (!isBlank(name) && configuredName.trim().equalsIgnoreCase(name.trim())) return name;
        }
        return null;
    }

    private static String columnName(Object column) {
        for (String key : List.of("columnName", "column_name", "fieldName", "field_name", "name")) {
            String value = columnString(column, key);
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private static String joinUpdateKeys(List<?> columns) {
        if (columns == null || columns.isEmpty()) return "";
        List<String> keys = new ArrayList<>();
        for (Object c : columns) {
            if (isBusinessStableKey(c)) {
                String name = columnString(c, "columnName");
                if (!isBlank(name)) keys.add(name);
            }
        }
        return String.join(",", keys);
    }

    private static boolean isBusinessStableKey(Object column) {
        String name = columnString(column, "columnName");
        if ("ODS_UUID".equalsIgnoreCase(name)) return false;
        return isTruthyColumnFlag(column, "primaryKey")
                || isTruthyColumnFlag(column, "primary_key")
                || isTruthyColumnFlag(column, "isPrimaryKey")
                || isTruthyColumnFlag(column, "is_primary_key")
                || isTruthyColumnFlag(column, "isPk")
                || isTruthyColumnFlag(column, "is_pk")
                || isTruthyColumnFlag(column, "isUnique")
                || isTruthyColumnFlag(column, "is_unique");
    }

    private static boolean isTruthyColumnFlag(Object column, String field) {
        Object value = columnValue(column, field);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() == 1;
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    /**
     * A signed 32-bit millisecond difference overflows for present-day dates in
     * Calcite. Seconds remain within the supported range and the random suffix
     * makes concurrently ingested rows distinct while preserving a numeric ID.
     */
    static final String ODS_UUID_EXPRESSION =
            "CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) "
                    + "|| CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)";

    private static String odsSystemMapping(String targetName) {
        if (isBlank(targetName)) return null;
        return switch (targetName.trim().toUpperCase(Locale.ROOT)) {
            case "ODS_UUID" -> "    {\n"
                    + "      \"expression\": \"" + ODS_UUID_EXPRESSION + "\",\n"
                    + "      \"to\": \"/ODS_UUID\",\n"
                    + "      \"comment\": \"NiFi生成正数ODS主键（秒级时间戳加随机尾数）\"\n"
                    + "    }";
            case "ODS_RKSJ" -> "    {\n"
                    + "      \"expression\": \"LOCALTIMESTAMP\",\n"
                    + "      \"to\": \"/ODS_RKSJ\",\n"
                    + "      \"comment\": \"NiFi写入当前入库时间\"\n"
                    + "    }";
            case "ODS_GXSJ" -> "    {\n"
                    + "      \"expression\": \"LOCALTIMESTAMP\",\n"
                    + "      \"to\": \"/ODS_GXSJ\",\n"
                    + "      \"comment\": \"NiFi写入当前更新时间\"\n"
                    + "    }";
            default -> null;
        };
    }

    static String buildMappingConfig(List<?> sourceColumns, List<?> targetColumns,
                                     String sourceTableId, String targetTableId) {
        Set<String> sourceSet = new LinkedHashSet<>();
        if (sourceColumns != null) {
            for (Object c : sourceColumns) {
                String name = columnString(c, "columnName");
                if (!isBlank(name)) sourceSet.add(name);
            }
        }
        List<String> pairs = new ArrayList<>();
        if (targetColumns != null) {
            // Target metadata determines the mapping order and guarantees that all
            // ODS system fields are emitted, including ODS_RKSJ and ODS_GXSJ.
            for (Object c : targetColumns) {
                String targetName = columnString(c, "columnName");
                String systemMapping = odsSystemMapping(targetName);
                if (systemMapping != null) {
                    pairs.add(systemMapping);
                } else if (!isBlank(targetName) && sourceSet.contains(targetName)) {
                    pairs.add("    {\n      \"from\": \"/" + targetName
                            + "\",\n      \"to\": \"/" + targetName + "\"\n    }");
                }
            }
        }
        if (pairs.isEmpty() && sourceColumns != null) {
            for (Object c : sourceColumns) {
                String name = columnString(c, "columnName");
                if (!isBlank(name)) {
                    pairs.add("    {\n      \"from\": \"/" + name
                            + "\",\n      \"to\": \"/" + name + "\"\n    }");
                    break;
                }
            }
        }
        return "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"passthroughUnmapped\": false,\n" +
                "  \"passthroughCaseSensitive\": true,\n" +
                "  \"onMissingSource\": \"NULL\",\n" +
                "  \"onTypeMismatch\": \"SKIP_ROW\",\n" +
                "  \"mappings\": [\n" +
                String.join(",\n", pairs) + "\n" +
                "  ]\n" +
                "}";
    }

    private static Object columnValue(Object column, String field) {
        if (column == null || isBlank(field)) return null;
        if (column instanceof Map<?, ?> map) {
            return map.get(field);
        }
        try {
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            return column.getClass().getMethod(getter).invoke(column);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String columnString(Object column, String field) {
        Object value = columnValue(column, field);
        if (value instanceof String s && !s.isBlank()) return s;
        return null;
    }

    private static Integer parsePort(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    static String normalizeSourceNodeType(String dbType, Map<String, Object> source) {
        return RegisteredDatasourceType.sourceManifest(dbType, source);
    }

    private static String normalizeSinkDbType(String dbType, Map<String, Object> source) {
        return RegisteredDatasourceType.sinkType(dbType, source);
    }

    private static boolean isLinewellOracleWriterTarget(String dbType) {
        if (isBlank(dbType)) return false;
        String normalized = dbType.trim().toUpperCase(Locale.ROOT);
        return "ORACLE".equals(normalized)
                || "OCEANBASE_ORACLE".equals(normalized)
                || "OCEANBASEORACLE".equals(normalized);
    }

    static String normalizeOracleConnectionType(String connectionType) {
        if (isBlank(connectionType)) return null;
        String normalized = connectionType.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        return switch (normalized) {
            case "SID" -> "SID";
            case "SERVICENAME", "SERVICE" -> "SERVICE_NAME";
            default -> null;
        };
    }

    static String nifiTargetTableName(String dbType, Map<String, Object> datasource, String tableName) {
        if (isBlank(tableName)) return tableName;
        String value = tableName.trim();
        String type = isBlank(dbType) ? "" : dbType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ORACLE", "OCEANBASE_ORACLE", "OCEANBASEORACLE", "DM", "DAMENG").contains(type)) {
            return value;
        }
        if (value.contains(".")) {
            return value;
        }

        String table = oracleIdentifier(value);
        String schema = firstNonBlank(datasource, "schema", "defaultSchema", "currentSchema");
        return isBlank(schema) ? table : oracleIdentifier(schema) + "." + table;
    }

    private static String oracleIdentifier(String identifier) {
        String value = identifier.trim();
        return value.startsWith("\"") && value.endsWith("\"")
                ? value
                : value.toUpperCase(Locale.ROOT);
    }
}
