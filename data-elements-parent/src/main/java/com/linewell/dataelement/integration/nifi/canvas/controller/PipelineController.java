package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineTemplateProvider;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.script.exception.MagicScriptException;
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
    private static final List<String> API_TEMPLATE_SOURCE_FIELDS = List.of(
            "apiPullItems", "api_pull_items", "poolCfg", "pool_cfg", "apiUrl", "api_url", "url",
            "apiMethod", "api_method", "method", "apiBody", "api_body", "requestBody", "request_body",
            "connectTimeout", "connect_timeout", "connectionTimeout", "readTimeout", "read_timeout",
            "apiTimeoutSeconds", "api_timeout_seconds");
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
    private final TransactionTemplate apiPullBindingTransaction;
    private final PipelineTaskLifecycleSynchronizer taskLifecycle;
    private final PipelineGroupOrganizer groups;
    private List<PipelineTemplateProvider> templateProviders = List.of();

    @Autowired(required = false)
    public void setTemplateProviders(List<PipelineTemplateProvider> providers) {
        templateProviders = List.copyOf(providers);
    }
    private com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService mappingProjections;
    @Autowired public void setMappingProjections(com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService service){mappingProjections=service;}

    public PipelineController(PipelineRepository repo, NifiClient nifi, MagicAPIService magicApiService,
                              DataAssetRuntimeAdapter dataAssetRuntimeAdapter, JdbcTemplate jdbcTemplate,
                              DataSourceConnectionPropertyResolver connectionProperties,
                              PlatformTransactionManager transactionManager,
                              PipelineTaskLifecycleSynchronizer taskLifecycle, PipelineGroupOrganizer groups) {
        this.repo = repo;
        this.nifi = nifi;
        this.magicApiService = magicApiService;
        this.dataAssetRuntimeAdapter = dataAssetRuntimeAdapter;
        this.jdbcTemplate = jdbcTemplate;
        this.connectionProperties = connectionProperties;
        this.apiPullBindingTransaction = new TransactionTemplate(transactionManager);
        this.apiPullBindingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.taskLifecycle = taskLifecycle;
        this.groups = groups;
    }

    /** Keep MVC parameter binding and the established raw response for the Magic flow page. */
    @GetMapping("/page")
    public Map<String, Object> listPage(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String sortField,
                                        @RequestParam(required = false) String sortDir,
                                        @RequestParam(required = false) String q) {
        TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            throw new IllegalStateException("流程查询必须绑定行业租户");
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", page);
        parameters.put("size", size);
        if (sortField != null) parameters.put("sortField", sortField);
        if (sortDir != null) parameters.put("sortDir", sortDir);
        if (q != null) parameters.put("q", q);
        return invokeMagicRead("/common/nifiPipelinePage", parameters);
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

    /** Keep the established HTTP path and raw response while the overview runs in Magic. */
    @GetMapping("/runtime-overview")
    @Operation(summary="查询流程总览(runtimeOverview)")
    public Map<String, Object> runtimeOverview(@RequestParam(required = false) String sortField,
                                               @RequestParam(required = false) String sortDir) {
        Map<String, Object> parameters = new HashMap<>();
        if (sortField != null) parameters.put("sortField", sortField);
        if (sortDir != null) parameters.put("sortDir", sortDir);
        return invokeMagicRead("/common/nifiRuntimeOverview", parameters);
    }

    private Map<String, Object> invokeMagicRead(String function, Map<String, Object> parameters) {
        try {
            return magicApiService.invoke(function, parameters);
        } catch (MagicScriptException failure) {
            // Preserve the MVC error contract for the underlying query or argument exception.
            if (failure.getCause() instanceof RuntimeException original) throw original;
            throw failure;
        }
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
                // A detail read must preserve the saved design and version. In a
                // chained task the catalog identifies the root business source,
                // while this pipeline's source is the previous materialized hop.
                // Registration-rule binding remains an explicit deploy/start action.
                .map(stored -> com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
                        .forCanvas(stored).withStoredDesignVersion(stored))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * template 根据资源目录构建数据源与目标数据库
     */
    @PostMapping("/template")
    @Operation(summary = "生成流程模板(template)")
    public ResponseEntity<Pipeline> template(@RequestBody PipelineTemplateRequest request) {
        if (request != null) {
            for (PipelineTemplateProvider provider : templateProviders) {
                if (provider.supports(request.templateType())) {
                    return ResponseEntity.ok(provider.template());
                }
            }
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
                                new Pipeline.Node(transformNodeId, "transform.field-enrichment", "字段治理", "transform", 420, 120, transformConfig),
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
        body = com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy.forCanvas(body);
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
    @Transactional
    public ResponseEntity<Pipeline> update(@PathVariable String id, @RequestBody Pipeline body,
                                           @RequestParam(required=false) Long expectedUpdatedAt,
                                           @RequestParam(required=false) String expectedDesignHash) {
        Pipeline current = repo.findById(id).orElse(null);
        if (current == null) return ResponseEntity.notFound().build();
        final Pipeline prepared = com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy.forCanvas(body);
        return ResponseEntity.ok(repo.update(id, cur -> {
            // Native/runtime observations change updatedAt without changing the editable design.
            // New clients compare the design they loaded; legacy timestamp callers retain their check.
            boolean conflict = expectedDesignHash != null
                    ? !Objects.equals(cur.designHash(), expectedDesignHash)
                    : expectedUpdatedAt != null && !Objects.equals(cur.updatedAt(), expectedUpdatedAt);
            if (conflict) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "平台画布设计已被其他入口修改，请重新加载后再保存；NiFi原生修改不影响此校验");
            Pipeline next = new Pipeline(cur.id(), prepared.name(), savedDescription(cur, prepared),
                    cur.createdAt(), cur.updatedAt(), prepared.dsl(), cur.nifiProcessGroupId(), cur.status(),
                    cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(), cur.nodeMapping(), cur.lastBulletinId());
            if (mappingProjections != null) mappingProjections.persist(next);
            return next;
        }));
    }

    /** Template generation initializes extraction fields once; saved/manual choices are never re-read or replaced. */
    static Pipeline validateIncrementalFields(Pipeline body) {
        if (body.dsl() == null || body.dsl().nodes() == null) return body;
        for (var node : body.dsl().nodes()) {
            if (!"source".equals(node.category()) || node.config() == null
                    || List.of("source.api", "source.ftp", "source.sftp", "source.kafka", "source.minio",
                        "source.hdfs", "source.hbase", "source.elasticsearch", "source.iotdb").contains(node.manifestKey())) continue;
            String mode = String.valueOf(node.config().getOrDefault("syncMode", "")).trim().toUpperCase(Locale.ROOT);
            String column = String.valueOf(node.config().getOrDefault("incrementalColumn", "")).trim();
            if (List.of("INCREMENTAL", "INCR", "FULL_THEN_INCR").contains(mode) && (column.isBlank() || "null".equals(column)))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "来源节点缺少主增量字段，请手工填写后保存，或选择全量同步");
        }
        return body;
    }

    private Pipeline saveDesign(Pipeline current, Pipeline body) {
        return repo.update(current.id(), cur -> {
            Pipeline next = new Pipeline(cur.id(), body.name(), savedDescription(cur, body),
                cur.createdAt(), cur.updatedAt(), body.dsl(), cur.nifiProcessGroupId(), cur.status(),
                cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(), cur.nodeMapping(), cur.lastBulletinId());
            if(mappingProjections!=null)mappingProjections.persist(next);
            return next;
        });
    }

    /** The canvas omits the description. Match the repository's partial update behavior. */
    private static String savedDescription(Pipeline current, Pipeline submitted) {
        return submitted.description() == null ? current.description() : submitted.description();
    }

    // Compatibility for existing in-process callers and timestamp-only clients.
    public ResponseEntity<Pipeline> update(String id, Pipeline body, Long expectedUpdatedAt) {
        return update(id, body, expectedUpdatedAt, null);
    }

    // Compatibility for existing in-process callers; HTTP clients may send a design revision.
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

    private void bindApiPullRule(String catalogId, String pipelineId) {
        if (isBlank(catalogId) || isBlank(pipelineId)) return;
        try {
            Map<String, Object> catalog = loadAssetByTid(catalogId);
            String sourceTableId = firstNonBlank(catalog, "sourceTableId", "source_table_id");
            Map<String, Object> table = isBlank(sourceTableId) ? null : loadAssetByTid(sourceTableId);
            Map<String, Object> datasource = table == null ? null : loadDbAssetByTable(table);
            String datasourceId = firstNonBlank(datasource, "tid", "id", "dbId", "datasourceId", "dataSourceId");
            if (!isBlank(datasourceId) && !isBlank(sourceTableId)) {
                // The original binding committed on a separate tenant connection.
                apiPullBindingTransaction.executeWithoutResult(transaction ->
                        magicApiService.invoke("/common/bindCanvasApiPullRules", Map.of(
                                "datasourceId", datasourceId, "sourceTableId", sourceTableId, "pipelineId", pipelineId)));
            }
        } catch (Exception failure) {
            // Optional binding failures must leave the canvas creation transaction usable.
            log.warn("Optional API pull rule binding failed for pipeline {} ({})",
                    pipelineId, failure.getClass().getSimpleName());
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
        // A new or restored technical table must be usable from its registration
        // before cataloguing or index synchronization.
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
            return dataAssetRuntimeAdapter.queryById(tid);
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
            for(String option:List.of("ftpsImplicit","trustStorePath","trustStorePassword","nifiTrustStorePath","ftpRecursive","ftpPassiveMode"))
                copyIfPresent(sourceDs,sourceConfig,option,option);
            sourceConfig.put("hostname", firstNonBlank(sourceDs, "ftpHost", "ftp_host", "hostname", "host"));
            sourceConfig.put("port", ftpPort == null ? (sftp ? 22 : 21) : ftpPort);
            sourceConfig.put("username", firstNonBlank(sourceDs, "ftpUsername", "ftp_username", "username"));
            sourceConfig.put("password", firstNonBlank(sourceDs, "ftpPassword", "ftp_password", "password"));
            sourceConfig.put("remotePath", firstNonBlank(sourceDs, "ftpPath", "ftp_path", "remotePath", "path") == null
                    ? "/" : firstNonBlank(sourceDs, "ftpPath", "ftp_path", "remotePath", "path"));
            sourceConfig.put("fileFilterRegex", firstNonBlank(sourceDs, "ftpFilePattern", "ftp_file_pattern", "fileFilterRegex", "filePattern") == null
                    ? ".*" : firstNonBlank(sourceDs, "ftpFilePattern", "ftp_file_pattern", "fileFilterRegex", "filePattern"));
            sourceConfig.put("passiveMode", firstNonBlank(sourceDs, "ftpPassiveMode", "ftp_passive_mode", "passiveMode"));
            sourceConfig.putAll(com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy.markDefault(sourceConfig));
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
            String tableName = firstNonBlank(tableAsset, "tableName", "table", "name", "tableCode", "tableNameCn");
            var datasource = new LinkedHashMap<String, Object>();
            for (String field : API_TEMPLATE_SOURCE_FIELDS) {
                if (sourceDs.containsKey(field)) datasource.put(field, sourceDs.get(field));
            }
            var parameters = new HashMap<String, Object>();
            parameters.put("datasource", datasource);
            parameters.put("tableName", tableName);
            sourceConfig.putAll(invokeMagicRead("/common/nifiApiTemplateConfig", parameters));
            sourceConfig.put("sourceColumns", sourceColumns == null ? List.of() : sourceColumns);
            sourceConfig.put("table", tableName);
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
        sourceConfig.put("gaussdbVariant", firstNonBlank(sourceDs,"gaussdbVariant","gaussdb_variant"));
        sourceConfig.put("transportMode", firstNonBlank(sourceDs,"transportMode"));
        sourceConfig.put("httpPath", firstNonBlank(sourceDs,"httpPath"));
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
        sourceConfig.putAll(com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy.markDefault(sourceConfig));
        sourceConfig.put("syncMode", "FULL_THEN_INCR");
        sourceConfig.put("initialStrategy", "START_AT_BEGINNING");
        sourceConfig.put("lookbackSeconds", 5);
        sourceConfig.put("safetyLagSeconds", 1);
        sourceConfig.put("batchSize", 5000);
        sourceConfig.put("maxRowsPerRun", 20000);
        sourceConfig.put("softDeleteEnabled", false);
        sourceConfig.put("deleteReconcileStrategy", "NONE");
        return sourceConfig;
    }

    private Map<String, Object> jsonObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (!(value instanceof String text) || text.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JSON.readValue(text, new TypeReference<Map<String, Object>>() { });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
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
        sinkConfig.put("gaussdbVariant", firstNonBlank(targetDs,"gaussdbVariant","gaussdb_variant"));
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
        copyIfPresent(source, target, "transportMode", "transportMode");
        copyIfPresent(source, target, "httpPath", "httpPath");
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
        if ("open-source".equalsIgnoreCase(connectionMode)) return false;
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
