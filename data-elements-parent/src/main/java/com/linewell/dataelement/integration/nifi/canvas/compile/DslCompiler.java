package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.CompileSpec;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将画布 Pipeline DSL 编译并落地为一个全新的 NiFi Process Group。
 *
 * <p>当前实现布局：
 * <ul>
 *   <li>每条 pipeline 对应一个根 PG。</li>
 *   <li>根 PG 下固定创建两个共享 Controller Service：JsonTreeReader / JsonRecordSetWriter。</li>
 *   <li>每个画布节点按 manifest 生成处理器；完全一致的 DBCP/Kerberos 服务可提升到应用层复用。</li>
 *   <li>边连接通过“源节点 outlet 处理器”直连“目标节点 inlet 处理器”。</li>
 * </ul>
 *
 * <p>为简化实现，组件暂不再嵌套子 PG，全部处理器/服务直接挂在 pipeline 根 PG 下。
 */
@Component
public class DslCompiler {

    private static final Logger log = LoggerFactory.getLogger(DslCompiler.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FLOWFILE_REF = Pattern.compile("\\bFLOWFILE\\b", Pattern.CASE_INSENSITIVE);
    /*
     * Native NiFi cards are wider than their editor counterparts, but a
     * topology column must not consume an entire screen width.  The compiler
     * uses two alternating columns (see assignNodeLayouts), so 1,550px leaves
     * enough room for a three-processor sub-chain while keeping ordinary
     * materialization flows readable at NiFi's normal fit-to-screen zoom.
     *
     * A NodeLayout row is a compact 340px vertical unit.  Components with an
     * internal branch/lookup/file chain reserve multiple units dynamically.
     */
    private static final double CANVAS_ORIGIN_X = 160;
    private static final double CANVAS_ORIGIN_Y = 120;
    private static final double NODE_COLUMN_WIDTH = 1_550;
    private static final double NODE_ROW_HEIGHT = 340;
    private static final double PROCESSOR_COLUMN_GAP = 520;
    private static final double PROCESSOR_ROW_GAP = 260;
    /**
     * 每条 pipeline 固定创建的任务级标准 Controller Service。
     */
    private static final String JSON_READER_TYPE = "org.apache.nifi.json.JsonTreeReader";
    private static final String JSON_WRITER_TYPE = "org.apache.nifi.json.JsonRecordSetWriter";
    private static final String CSV_READER_TYPE = "org.apache.nifi.csv.CSVReader";
    private static final String EXCEL_READER_TYPE = "org.apache.nifi.excel.ExcelReader";
    private static final String CONVERT_RECORD_TYPE = "org.apache.nifi.processors.standard.ConvertRecord";
    private static final String ROUTE_ON_ATTRIBUTE_TYPE = "org.apache.nifi.processors.standard.RouteOnAttribute";
    private static final String SPLIT_RECORD_TYPE = "org.apache.nifi.processors.standard.SplitRecord";
    private static final String EXECUTE_SQL_TYPE = "org.apache.nifi.processors.standard.ExecuteSQL";
    private static final String EVALUATE_JSON_PATH_TYPE = "org.apache.nifi.processors.standard.EvaluateJsonPath";
    private static final String UPDATE_ATTRIBUTE_TYPE = "org.apache.nifi.processors.attributes.UpdateAttribute";
    private static final String MERGE_RECORD_TYPE = "org.apache.nifi.processors.standard.MergeRecord";
    private static final String EXECUTE_SQL_RECORD_TYPE = "org.apache.nifi.processors.standard.ExecuteSQLRecord";
    private static final String PUT_SQL_TYPE = "org.apache.nifi.processors.standard.PutSQL";
    private static final String HIVE_RECORD_PUT_TYPE = "com.linewell.nifi.hive.HiveRecordPut";
    private static final String LINEWELL_DATABASE_RECORD_WRITER = "LinewellPutDatabaseRecord";
    private static final String LINEWELL_DATABASE_RECORD_MODE = "LINEWELL_PUT_DATABASE_RECORD";
    private static final String NATIVE_DATABASE_RECORD_MODE = "NIFI_PUT_DATABASE_RECORD";
    private static final String HIVE_LINEWELL_HDFS_WRITER = "PutHwHDFS";
    private static final String HIVE_LINEWELL_HDFS_MODE = "LINEWELL_HDFS";
    private static final String HIVE_BATCH_LAKE_WRITER = "PutHDFS";
    private static final String HIVE_BATCH_LAKE_MODE = "HDFS_BATCH";
    private static final String HIVE_STANDARD_WRITE_MODE = "HIVE_RECORD_PUT";
    /**
     * ExecuteSQLRecord enrichment aliases are evaluated by the dictionary
     * database, rather than QueryRecord.  These otherwise-valid field names
     * collide with common Oracle/DM/MySQL SQL keywords and data-type names.
     */
    private static final Set<String> JDBC_RESERVED_ALIASES = Set.of(
            "ACTION", "DATE", "GROUP", "KEY", "LEVEL", "ORDER", "ROWID", "TIME", "TIMESTAMP", "USER", "VALUE");

    private final NifiClient nifi;
    private final NifiNodeRuntimeResolver nifiNodeRuntimeResolver;
    private final ManifestRegistry registry;
    private final FieldMappingService fieldMappingService;
    private final HiveModule hiveModule;
    /**
     * The address of this platform as seen from the NiFi execution network.
     * It is deliberately optional: ordinary remote APIs must retain the URL
     * registered by the user, while a registered loopback URL needs this
     * boundary-crossing address when NiFi is deployed in another host or
     * container.
     */
    private final String apiPullLoopbackBaseUrl;
    private final Map<String, Object> sharedServiceLocks = new ConcurrentHashMap<>();

    @Autowired
    public DslCompiler(NifiClient nifi, NifiNodeRuntimeResolver nifiNodeRuntimeResolver,
                       ManifestRegistry registry, FieldMappingService fieldMappingService,
                       HiveModule hiveModule,
                       @Value("${nifi.api-pull.loopback-base-url:}") String apiPullLoopbackBaseUrl) {
        this.nifi = nifi;
        this.nifiNodeRuntimeResolver = nifiNodeRuntimeResolver;
        this.registry = registry;
        this.fieldMappingService = fieldMappingService;
        this.hiveModule = hiveModule;
        this.apiPullLoopbackBaseUrl = apiPullLoopbackBaseUrl == null ? "" : apiPullLoopbackBaseUrl.trim();
    }

    /** Kept for focused compiler tests and callers that do not need network rewriting. */
    public DslCompiler(NifiClient nifi, ManifestRegistry registry, FieldMappingService fieldMappingService,
                       HiveModule hiveModule) {
        this(nifi, null, registry, fieldMappingService, hiveModule, "");
    }

    /** Kept for existing focused tests that explicitly exercise API loopback rewriting. */
    public DslCompiler(NifiClient nifi, ManifestRegistry registry, FieldMappingService fieldMappingService,
                       HiveModule hiveModule, String apiPullLoopbackBaseUrl) {
        this(nifi, null, registry, fieldMappingService, hiveModule, apiPullLoopbackBaseUrl);
    }

    /**
     * 编译并部署 pipeline，返回编译产物（包含 processGroupId 与节点映射信息）。
     * 方法结束后 PG 处于 STOPPED，是否启动由上层控制器决定。
     */
    public CompileResult compile(Pipeline pipeline) {
        return compile(pipeline, null);
    }

    /**
     * 编译并部署 pipeline，并在根 PG 创建后立即回调其 id，
     * 便于上层提前持久化清理所需的关联信息。
     */
    public CompileResult compile(Pipeline pipeline, Consumer<String> processGroupCreatedCallback) {
        return compile(pipeline, processGroupCreatedCallback, null);
    }

    public CompileResult compile(Pipeline pipeline, Consumer<String> processGroupCreatedCallback,
            com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer.Layout layout) {
        Objects.requireNonNull(pipeline.dsl(), "Pipeline DSL is required");
        String parentId = layout == null ? nifi.getRootProcessGroupId() : layout.parentId();
        String pgName = pipeline.name() == null ? pipeline.id() : pipeline.name();
        var position = layout == null
                ? com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer.nextPosition(nifi.listProcessGroups(parentId))
                : layout.position();
        NifiEntity pg = layout == null
                ? nifi.createProcessGroup(parentId, pgName, position.x(), position.y())
                : nifi.createProcessGroup(parentId, pgName, position.x(), position.y(), layout.comments());
        String pgId = pg.id();
        String controllerServiceScopeId = layout == null ? "" : layout.controllerServiceScopeId();
        if (processGroupCreatedCallback != null) {
            processGroupCreatedCallback.accept(pgId);
        }
        log.info("Created NiFi PG {} for pipeline {}", pgId, pipeline.id());

        // 创建全局共享 Controller Service，供多个节点处理器复用。
        Map<String, String> sharedCs = new HashMap<>();
        sharedCs.put("jsonReader", nifi.createControllerService(pgId, JSON_READER_TYPE,
                "JsonReader", Map.of()).id());
        sharedCs.put("jsonWriter", nifi.createControllerService(pgId, JSON_WRITER_TYPE,
                "JsonWriter", Map.of()).id());

        // 节点编译上下文：记录每个节点在 NiFi 中创建出来的 CS/Processor 实体 ID。
        Map<String, NodeCompilation> compiled = new LinkedHashMap<>();
        // 预缓存每个节点的 manifest、配置与原生画布布局，供后续阶段使用。
        Map<String, ComponentManifest> manifestByNode = new LinkedHashMap<>();
        Map<String, Map<String, Object>> cfgByNode = new LinkedHashMap<>();
        Map<String, NodeLayout> layoutByNode = assignNodeLayouts(pipeline.dsl());
        Map<String, FieldMappingService.CompiledMapping> fieldMappingPlans = new LinkedHashMap<>();
        Set<String> sourceConfigOnlyNodes = new LinkedHashSet<>();

        // ---- PHASE 1：创建所有 Controller Service（初始为 DISABLED）。----
        for (Pipeline.Node n : pipeline.dsl().nodes()) {
            ComponentManifest m = registry.get(n.manifestKey());
            if (m == null) {
                throw new IllegalStateException("Unknown manifest: " + n.manifestKey());
            }
            CompileSpec spec = m.compile();
            if (spec == null) {
                throw new IllegalStateException("Manifest " + m.key() + " has no compile spec");
            }
            NodeCompilation nc = new NodeCompilation();
            compiled.put(n.id(), nc);
            manifestByNode.put(n.id(), m);

            Map<String, Object> cfg = new LinkedHashMap<>();
            if (m.fields() != null) {
                for (var f : m.fields()) {
                    if (f.defaultValue() != null) cfg.put(f.key(), f.defaultValue());
                }
            }
            if (n.config() != null) cfg.putAll(n.config());
            materializeServerManagedHuaweiMrsProfile(n.manifestKey(), cfg);
            if ("sink.hive".equals(m.key())) {
                // HiveRecordPut receives database and table separately and renders
                // them as validated Hive identifiers. A qualified table name here
                // would be treated as one unsafe identifier rather than a schema.
                cfg.put("database", firstNonBlankStatic(configText(cfg, "database"), "default"));
            }
            if ("source".equals(m.category())) {
                inferDbTypeFromSourceManifest(n.manifestKey(), m.key()).ifPresent(dbType ->
                        cfg.putIfAbsent("dbType", dbType));
                normalizeJdbcSourceConfig(cfg);
                configureOracleCompatibleFetchSql(cfg);
            }
            cfgByNode.put(n.id(), cfg);

            if (spec.controllerServices() != null) {
                for (var csSpec : spec.controllerServices()) {
                    Map<String, String> resolved = resolveProps(csSpec.properties(), cfg, nc.csIds, sharedCs);
                    String serviceType = resolveString(csSpec.type(), cfg, nc.csIds, sharedCs);
                    appendJdbcDriverProperties(serviceType, cfg, resolved);
                    NifiEntity cs = createOrReuseControllerService(pgId, controllerServiceScopeId,
                            serviceType, n.label() + "/" + csSpec.localId(), resolved);
                    nc.csIds.put(csSpec.localId(), cs.id());
                }
            }
            if (isStructuredObjectSource(m.key())) {
                createFileTransferReaderServices(pgId, n, nc, cfg);
            }
            if (isMinioObjectSource(m.key())) {
                Map<String, String> credentials = new LinkedHashMap<>();
                credentials.put("Use Default Credentials", "false");
                credentials.put("Use Anonymous Credentials", "false");
                credentials.put("Access Key ID", firstNonBlankStatic(configText(cfg, "minioAccessKey"),
                        configText(cfg, "accessKey"), configText(cfg, "username")));
                credentials.put("Secret Access Key", firstNonBlankStatic(configText(cfg, "minioSecretKey"),
                        configText(cfg, "secretKey"), configText(cfg, "password")));
                nc.csIds.put("minioCredentials", nifi.createControllerService(pgId,
                        "org.apache.nifi.processors.aws.credentials.provider.service.AWSCredentialsProviderControllerService",
                        n.label() + "/MinIO 访问凭证", credentials).id());
            }
        }

        for (Pipeline.Node n : pipeline.dsl().nodes()) {
            ComponentManifest manifest = manifestByNode.get(n.id());
            Map<String, Object> cfg = cfgByNode.get(n.id());
            if (manifest == null || cfg == null) continue;
            if (isFieldMappingTransform(manifest)) {
                try {
                    // 数据上报模板将登记字段中文名作为 CSV/Excel 首行表头，而目标表
                    // 使用英文列名。仅这些带首行表头的格式需要在编译 QueryRecord
                    // 前转换输入列名；JSON 的字段名本来就是接口约定的物理英文列名。
                    Map<String, Object> sourceCfg = resolveUpstreamSourceConfig(pipeline, n.id(), manifestByNode, cfgByNode);
                    if (usesRegisteredChineseHeaders(sourceCfg)) {
                        cfg.put("mappings", mapFileHeaderFieldsToRegisteredNames(cfg.get("mappings"), sourceCfg));
                    }
                    FieldMappingService.CompiledMapping plan = fieldMappingService.compilePlan(cfg.get("mappings"));
                    fieldMappingPlans.put(n.id(), plan);
                    if (isFieldEnrichmentTransform(manifest)
                            && sourceDbPushdownRequested(cfg)
                            && fieldMappingService.supportsSourceDbPushdown(cfg.get("mappings"))
                            && canPushFieldEnrichmentToSource(plan, sourceCfg)) {
                        cfg.put("_runtimeSourceSql", fieldMappingService.compileSourceDbQuery(
                                cfg.get("mappings"), sourceSqlTable(sourceCfg), "src",
                                field -> sourceSqlIdentifier(field, sourceCfg)));
                    }
                    if (!plan.lookups().isEmpty() && !isSourceDbFieldEnrichmentNode(manifest, cfg)) {
                        Set<String> lookupConnectionKeys = new LinkedHashSet<>();
                        for (FieldMappingService.LookupPlan lookup : plan.lookups()) {
                            String key = lookupConnectionKey(lookup);
                            if (!lookupConnectionKeys.add(key)) continue;
                            Map<String, Object> lookupCfg = lookup.dataSource() == null || lookup.dataSource().isEmpty()
                                    ? sourceCfg : lookup.dataSource();
                            NifiEntity cs = nifi.createControllerService(pgId, "org.apache.nifi.dbcp.DBCPConnectionPool",
                                    n.label() + "/" + key, buildSourceDbcpProperties(lookupCfg));
                            compiled.get(n.id()).csIds.put(key, cs.id());
                        }
                    }
                } catch (IllegalStateException ex) {
                    throw new NodeConfigException(n.id(), n.label(), "mappings", ex.getMessage(), ex);
                }
            }
            if (isExternalTransformSqlNode(manifest, cfg)) {
                Map<String, Object> sourceCfg = resolveUpstreamSourceConfig(pipeline, n.id(), manifestByNode, cfgByNode);
                NifiEntity cs = nifi.createControllerService(pgId, "org.apache.nifi.dbcp.DBCPConnectionPool",
                        n.label() + "/sql-dbcp", buildSourceDbcpProperties(sourceCfg));
                compiled.get(n.id()).csIds.put("sql-dbcp", cs.id());
            }
        }
        sourceConfigOnlyNodes.addAll(resolveSourceConfigOnlyNodes(pipeline, manifestByNode, cfgByNode));
        attachExternalSqlPreStatementsToDownstreamSinks(pipeline, manifestByNode, cfgByNode);

        // ---- PHASE 2：批量启用 CS，并等待全部进入 ENABLED。----
        // 顺序很关键：若 CS 仍在 ENABLING，NiFi 会拒绝启动依赖它的处理器。
        List<String> allCs = new ArrayList<>(sharedCs.values());
        compiled.values().forEach(nc -> allCs.addAll(nc.csIds.values()));
        for (String csId : allCs) {
            try {
                nifi.setControllerServiceRunStatus(csId, "ENABLED");
            } catch (Exception e) {
                log.warn("Enable CS {} failed: {}", csId, e.getMessage());
            }
        }
        for (String csId : allCs) {
            // 若 CS 因校验失败回落到 DISABLED，这里会抛出带 validationErrors 的异常。
            nifi.waitForControllerServiceEnabled(csId, 30_000L);
        }

        // ---- PHASE 3：创建处理器，并建立节点内部连线。----
        for (Pipeline.Node n : pipeline.dsl().nodes()) {
            ComponentManifest m = manifestByNode.get(n.id());
            CompileSpec spec = m.compile();
            NodeCompilation nc = compiled.get(n.id());
            Map<String, Object> cfg = cfgByNode.get(n.id());
            NodeLayout nodeLayout = layoutByNode.get(n.id());

            if (sourceConfigOnlyNodes.contains(n.id())) {
                continue;
            }

            if (isFileTransferSource(m.key())) {
                buildFileTransferSource(pgId, n, nc, nodeLayout, cfg, sharedCs);
                continue;
            }

            if (isMinioObjectSource(m.key())) {
                buildMinioObjectSource(pgId, n, nc, nodeLayout, cfg, sharedCs);
                continue;
            }

            if ("source.api".equals(m.key())) {
                buildApiSource(pgId, n, nc, nodeLayout, cfg);
                continue;
            }

            if ("sink.jdbc".equals(m.key())) {
                Set<String> availableFields = resolveUpstreamOutputFields(
                        pipeline, n.id(), manifestByNode, cfgByNode, fieldMappingPlans);
                normalizeJdbcSinkKeys(n, cfg, availableFields);
                normalizeJdbcSinkTableName(cfg);
                if (hasAvailableTemporalTargetColumns(cfg, availableFields)) {
                    buildTemporalJdbcSink(pgId, n, m, spec, nc, nodeLayout, cfg, sharedCs, availableFields);
                    continue;
                }
            }

            if ("branch.conditions".equals(m.key())) {
                buildConditionalBranch(pgId, n, nc, nodeLayout, cfg, sharedCs);
                continue;
            }

            if (isSourceDbFieldEnrichmentNode(m, cfg)) {
                buildExternalSqlTransform(pgId, pipeline, n, nc, nodeLayout, cfg, sharedCs, manifestByNode, cfgByNode, compiled);
                continue;
            }

            if (isFieldMappingTransform(m)) {
                FieldMappingService.CompiledMapping plan = fieldMappingPlans.get(n.id());
                if (plan != null && !plan.lookups().isEmpty()) {
                    buildFieldMappingLookupChain(pgId, pipeline, n, spec, nc, nodeLayout, plan,
                            sharedCs, manifestByNode, cfgByNode);
                    continue;
                }
            }

            if (isExternalTransformSqlNode(m, cfg)) {
                buildExternalSqlTransform(pgId, pipeline, n, nc, nodeLayout, cfg, sharedCs, manifestByNode, cfgByNode, compiled);
                continue;
            }

            int sqlInputCount = incomingEdgeCount(pipeline, n.id());
            if (isMultiInputFlowFileSqlNode(pipeline, n.id(), m, cfg)) {
                buildFlowFileSqlTransform(pgId, n, nc, nodeLayout, cfg, sharedCs, sqlInputCount);
                continue;
            }

            if (spec.processors() != null) {
                int row = 0;
                for (var pSpec : spec.processors()) {
                    Map<String, String> resolved = resolveProps(pSpec.properties(), cfg, nc.csIds, sharedCs);
                    applyJdbcSinkPreProcessingSql(m, pSpec.localId(), cfg, resolved);
                    normalizeLinewellJdbcSinkProperties(m, pSpec.localId(), cfg, resolved);
                    // 字段映射节点：将前端 mappings 编译成 NiFi 可执行查询表达式。
                    if (isFieldMappingTransform(m) && "map".equals(pSpec.localId())) {
                        try {
                            resolved.put("success", fieldMappingPlans.getOrDefault(
                                    n.id(), fieldMappingService.compilePlan(cfg.get("mappings"))).baseQuery());
                        } catch (IllegalStateException ex) {
                            throw new NodeConfigException(n.id(), n.label(), "mappings", ex.getMessage(), ex);
                        }
                    }
                    applyIncrementalSourceOptions(m, pSpec.localId(), cfg, resolved);
                    applySourceFetchDatabaseType(m, pSpec.type(), cfg, resolved);
                    applySourceRecordQueryOptions(m, pSpec.type(), resolved);
                    // Processors in one DSL component stay on the same row;
                    // NodeLayout has already separated topological columns and
                    // branch lanes so these cards cannot overlap another node.
                    double x = nodeOriginX(nodeLayout) + row * PROCESSOR_COLUMN_GAP;
                    double y = nodeOriginY(nodeLayout);
                    String period = pSpec.schedulingPeriod() == null ? null
                            : resolveString(pSpec.schedulingPeriod(), cfg, nc.csIds, sharedCs);
                    if (period != null && period.isBlank()) period = null;
                    String strategy = pSpec.schedulingStrategy() == null ? null
                            : resolveString(pSpec.schedulingStrategy(), cfg, nc.csIds, sharedCs);
                    String configuredStrategy = stringConfig(cfg, "schedulingStrategy");
                    if (!configuredStrategy.isBlank()) {
                        strategy = configuredStrategy;
                    }
                    if (strategy != null && strategy.isBlank()) strategy = null;
                    String processorType = resolveProcessorType(m, pSpec.localId(), pSpec.type(), cfg);
                    if (isHiveBatchPutHdfs(m, pSpec.localId(), cfg)) {
                        // PutHDFS writes the FlowFile to HDFS directly.  Its property
                        // contract is different from HiveRecordPut: it must not receive
                        // Hive JDBC/controller-service properties from the manifest.
                        resolved = buildHiveBatchPutHdfsProperties(cfg);
                    }
                    log.info("Create processor node={} manifest={} localId={} type={}",
                            n.id(), m.key(), pSpec.localId(), processorType);
                    //创建处理器
                    NifiEntity proc = nifi.createProcessor(pgId, processorType,
                            n.label() + "/" + pSpec.localId(),
                            x, y, resolved, period, strategy);
                    nc.processorIds.put(pSpec.localId(), proc.id());
                    row++;
                }
            }

            if (spec.internalConnections() != null) {
                for (var ic : spec.internalConnections()) {
                    String src = nc.processorIds.get(ic.from());
                    String dst = nc.processorIds.get(ic.to());
                    if (src == null || dst == null) {
                        throw new IllegalStateException("Bad internal connection in " + m.key()
                                + ": " + ic.from() + "->" + ic.to());
                    }
                    nifi.createConnection(pgId, src, "PROCESSOR", dst, "PROCESSOR",
                            List.of(ic.relationship()));
                    nc.usedOutgoing.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(ic.relationship());
                }
            }

            nc.inletProcessorId = spec.inlet() == null ? null : nc.processorIds.get(spec.inlet());
            if (spec.outlets() != null) {
                for (var o : spec.outlets()) {
                    nc.outlets.put(o.name(), new Outlet(nc.processorIds.get(o.processorRef()), o.relationship()));
                }
            }
        }

        // ---- PHASE 4：创建跨组件边连接（源 outlet -> 目标 inlet）。----
        Map<String, String> edgeConnectionIds = new LinkedHashMap<>();
        if (pipeline.dsl().edges() != null) {
            for (Pipeline.Edge e : pipeline.dsl().edges()) {
                if (isSourceConfigOnlyEdge(e, manifestByNode, cfgByNode)) {
                    continue;
                }
                NodeCompilation src = compiled.get(e.source());
                NodeCompilation dst = compiled.get(e.target());
                if (src == null || dst == null) {
                    throw new IllegalStateException("Edge references unknown node: " + e.source() + "->" + e.target());
                }
                if (dst.inletProcessorId == null) {
                    throw new IllegalStateException("Target component has no inlet: edge " + e.id());
                }
                Outlet out = resolveOutlet(src, e.outlet());
                if (out == null) {
                    throw new IllegalStateException("Source component has no outlet: edge " + e.id());
                }
                NifiEntity connection = nifi.createConnection(pgId, out.processorId, "PROCESSOR",
                        dst.inletProcessorId, "PROCESSOR", List.of(out.relationship));
                edgeConnectionIds.put(e.id(), connection.id());
                src.usedOutgoing.computeIfAbsent(out.processorId, k -> new LinkedHashSet<>()).add(out.relationship);
            }
        }

        // Finalize the expanded native graph, rather than individual DSL-node blocks.
        nifi.finalizeGeneratedProcessGroup(pgId);

        return new CompileResult(pgId, sharedCs, compiled, edgeConnectionIds);
    }

    /**
     * 文件上报不是把文件字节直接交给字段映射：CSV、JSON 和 Excel 必须先
     * 规范化为 JSON RecordSet。这里的服务全部在画布创建时落到同一个根 PG，
     * 与后续字段映射节点共享 {@code JsonRecordSetWriter} 的输出格式。
     */
    private void createFileTransferReaderServices(String pgId, Pipeline.Node node,
                                                  NodeCompilation nc, Map<String, Object> cfg) {
        String encoding = firstNonBlankStatic(configText(cfg, "csvEncoding"),
                configText(cfg, "csvCharset"), configText(cfg, "charset"), "UTF-8");
        String delimiter = firstNonBlankStatic(configText(cfg, "csvDelimiter"),
                configText(cfg, "csvSeparator"), ",");
        if (delimiter.length() != 1) {
            throw new NodeConfigException(node.id(), node.label(), "csvDelimiter",
                    "CSV 分隔符必须是单个字符", null);
        }

        Map<String, String> csvProperties = new LinkedHashMap<>();
        // 数据上报 CSV 的约定是第一行即字段名。当前 NiFi CSVReader 使用
        // csv-header-derived 枚举值从该行派生字段定义，避免把首行写入目标表。
        csvProperties.put("Schema Access Strategy", "csv-header-derived");
        csvProperties.put("Character Set", encoding);
        csvProperties.put("Value Separator", delimiter);
        nc.csIds.put("fileCsvReader", nifi.createControllerService(pgId, CSV_READER_TYPE,
                node.label() + "/CSV 文件读取器", csvProperties).id());

        Map<String, String> xlsProperties = new LinkedHashMap<>();
        xlsProperties.put("Schema Access Strategy", "Use Starting Row");
        xlsProperties.put("Starting Row", "1");
        // 数据上报会保留用户文件名用于审计，却将 FTP 上的实际文件安全重命名为
        // report_<timestamp>_<random>.xlsx。因此不能再从 FlowFile 的 filename
        // 反推工作表名，否则会错误地要求名为“report”的工作表。上报模板的
        // 数据页名称由登记表中文显示名确定；仍未带该元数据的历史/通用 FTP
        // 流程才保留原有的文件名推断，以兼容其既有约定。
        String registeredSheetName = configText(cfg, "excelSheetName");
        xlsProperties.put("Required Sheets", registeredSheetName == null || registeredSheetName.isBlank()
                ? "${filename:replaceFirst('^(?:report_[^_]+_[^_]+_)?([^_]+)_.*$', '$1')}"
                : registeredSheetName);
        xlsProperties.put("Input File Type", "XLS");
        nc.csIds.put("fileXlsReader", nifi.createControllerService(pgId, EXCEL_READER_TYPE,
                node.label() + "/XLS 文件读取器", xlsProperties).id());

        Map<String, String> xlsxProperties = new LinkedHashMap<>(xlsProperties);
        xlsxProperties.put("Input File Type", "XLSX");
        nc.csIds.put("fileXlsxReader", nifi.createControllerService(pgId, EXCEL_READER_TYPE,
                node.label() + "/XLSX 文件读取器", xlsxProperties).id());
    }

    /**
     * 生成文件来源的内部子链：GetFTP/GetSFTP -> 扩展名分流 -> Record 转换 ->
     * 汇合。只有 csv、json、xls、xlsx 会进入字段映射；其它文件由路由节点
     * 自动终止，因而不会被误当作业务数据写入目标表。
     */
    private void buildFileTransferSource(String pgId, Pipeline.Node node, NodeCompilation nc,
                                         NodeLayout nodeLayout, Map<String, Object> cfg,
                                         Map<String, String> sharedCs) {
        // Existing canvases can retain the generic FTP node type while their
        // datasource configuration has already been changed to SFTP. Choose
        // the NiFi processor from both sources so an SSH endpoint on port 22
        // is never contacted by GetFTP.
        boolean sftp = "source.sftp".equals(node.manifestKey())
                || "sftp".equalsIgnoreCase(firstNonBlankStatic(
                        configText(cfg, "protocol"), configText(cfg, "ftpProtocol")));
        String host = firstNonBlankStatic(configText(cfg, "hostname"), configText(cfg, "host"));
        String port = firstNonBlankStatic(configText(cfg, "port"), sftp ? "22" : "21");
        String remotePath = firstNonBlankStatic(configText(cfg, "remotePath"), "/");
        String fileFilter = firstNonBlankStatic(configText(cfg, "fileFilterRegex"),
                "(?i).*\\.(csv|json|xlsx|xls)$");
        String schedulingPeriod = firstNonBlankStatic(configText(cfg, "schedulingPeriod"), "60 sec");

        Map<String, String> fetchProperties = new LinkedHashMap<>();
        fetchProperties.put("Hostname", host);
        fetchProperties.put("Port", port);
        fetchProperties.put("Username", configText(cfg, "username"));
        fetchProperties.put("Password", configText(cfg, "password"));
        fetchProperties.put("Remote Path", remotePath);
        fetchProperties.put("File Filter Regex", fileFilter);
        // Ingestion is a read of the registered source, not ownership of its
        // lifecycle. NiFi defaults to deleting successfully fetched files.
        fetchProperties.put("Delete Original", "false");
        // Do not send legacy Passive Mode / Completion Strategy properties.
        // The configured production NiFi rejects both descriptors before the
        // processor can start, so the downloader intentionally uses its
        // installed defaults for transfer and post-download handling.
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        NifiEntity fetch = nifi.createProcessor(pgId,
                sftp ? "org.apache.nifi.processors.standard.GetSFTP" : "org.apache.nifi.processors.standard.GetFTP",
                node.label() + "/fetch", x, y,
                fetchProperties, schedulingPeriod, "TIMER_DRIVEN");
        nc.processorIds.put("fetch", fetch.id());

        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("csv", "${filename:toLower():matches('.*\\.csv$')}");
        routes.put("json", "${filename:toLower():matches('.*\\.json$')}");
        routes.put("xls", "${filename:toLower():matches('.*\\.xls$')}");
        routes.put("xlsx", "${filename:toLower():matches('.*\\.xlsx$')}");
        NifiEntity route = nifi.createProcessor(pgId, ROUTE_ON_ATTRIBUTE_TYPE,
                node.label() + "/文件类型分流", x, y + PROCESSOR_ROW_GAP,
                routes, null, null);
        nc.processorIds.put("route-file-type", route.id());

        NifiEntity csv = createRecordConverter(pgId, node.label() + "/CSV 转记录",
                x + PROCESSOR_COLUMN_GAP, y, nc.csIds.get("fileCsvReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-csv", csv.id());
        NifiEntity xls = createRecordConverter(pgId, node.label() + "/XLS 转记录",
                x + PROCESSOR_COLUMN_GAP, y + PROCESSOR_ROW_GAP, nc.csIds.get("fileXlsReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-xls", xls.id());
        NifiEntity xlsx = createRecordConverter(pgId, node.label() + "/XLSX 转记录",
                x + PROCESSOR_COLUMN_GAP, y + 2 * PROCESSOR_ROW_GAP, nc.csIds.get("fileXlsxReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-xlsx", xlsx.id());

        NifiEntity normalized = nifi.createProcessor(pgId, UPDATE_ATTRIBUTE_TYPE,
                node.label() + "/规范化记录", x + 2 * PROCESSOR_COLUMN_GAP, y + PROCESSOR_ROW_GAP,
                Map.of(), null, null);
        nc.processorIds.put("normalized", normalized.id());

        connectInternal(pgId, nc, fetch.id(), route.id(), "success");
        connectInternal(pgId, nc, route.id(), csv.id(), "csv");
        // JSON 已是下游 JsonTreeReader 可识别的 RecordSet，不重复转换以保留结构。
        connectInternal(pgId, nc, route.id(), normalized.id(), "json");
        connectInternal(pgId, nc, route.id(), xls.id(), "xls");
        connectInternal(pgId, nc, route.id(), xlsx.id(), "xlsx");
        connectInternal(pgId, nc, csv.id(), normalized.id(), "success");
        connectInternal(pgId, nc, xls.id(), normalized.id(), "success");
        connectInternal(pgId, nc, xlsx.id(), normalized.id(), "success");
        nc.outlets.put("default", new Outlet(normalized.id(), "success"));
    }

    /**
     * Build the MinIO source as an S3-compatible object-storage chain.
     *
     * <p>MinIO is not a JDBC database.  Treating its bucket as a MySQL schema
     * creates a seemingly successful empty run because ExecuteSQL receives no
     * records.  ListS3 emits one FlowFile per object and FetchS3Object replaces
     * its content with the actual object bytes; the same record conversion path
     * used by FTP then produces the JSON RecordSet expected by field mapping.</p>
     */
    private void buildMinioObjectSource(String pgId, Pipeline.Node node, NodeCompilation nc,
                                        NodeLayout nodeLayout, Map<String, Object> cfg,
                                        Map<String, String> sharedCs) {
        String endpoint = firstNonBlankStatic(configText(cfg, "minioEndpoint"),
                configText(cfg, "endpoint"), "http://" + firstNonBlankStatic(configText(cfg, "host"), "127.0.0.1")
                        + ":" + firstNonBlankStatic(configText(cfg, "port"), "9000"));
        String bucket = firstNonBlankStatic(configText(cfg, "minioBucket"), configText(cfg, "bucket"),
                configText(cfg, "database"));
        String accessKey = firstNonBlankStatic(configText(cfg, "minioAccessKey"), configText(cfg, "accessKey"),
                configText(cfg, "username"));
        String secretKey = firstNonBlankStatic(configText(cfg, "minioSecretKey"), configText(cfg, "secretKey"),
                configText(cfg, "password"));
        String prefix = firstNonBlankStatic(configText(cfg, "minioObjectKey"), configText(cfg, "minioPrefix"),
                configText(cfg, "objectPrefix"), configText(cfg, "prefix"));
        String schedulingPeriod = firstNonBlankStatic(configText(cfg, "schedulingPeriod"), "60 sec");
        if (bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new NodeConfigException(node.id(), node.label(), "minioBucket",
                    "MinIO 来源缺少存储桶或访问凭证", null);
        }

        Map<String, String> listProperties = minioS3Properties(endpoint, bucket, nc.csIds.get("minioCredentials"));
        if (!prefix.isBlank()) listProperties.put("Prefix", prefix);
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        NifiEntity list = nifi.createProcessor(pgId, "org.apache.nifi.processors.aws.s3.ListS3",
                node.label() + "/列出对象", x, y,
                listProperties, schedulingPeriod, "TIMER_DRIVEN");
        nc.processorIds.put("list", list.id());

        Map<String, String> fetchProperties = minioS3Properties(endpoint, bucket, nc.csIds.get("minioCredentials"));
        // NiFi 2.x ListS3 emits the complete object key as filename (including
        // the registered prefix).  FetchS3Object no longer receives the legacy
        // s3.key attribute, so use filename to avoid issuing an empty-key read.
        fetchProperties.put("Object Key", "${filename}");
        NifiEntity fetch = nifi.createProcessor(pgId, "org.apache.nifi.processors.aws.s3.FetchS3Object",
                node.label() + "/读取对象", x, y + PROCESSOR_ROW_GAP,
                fetchProperties, null, null);
        nc.processorIds.put("fetch", fetch.id());

        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("csv", "${filename:toLower():matches('.*\\.csv$')}");
        routes.put("json", "${filename:toLower():matches('.*\\.json$')}");
        routes.put("xls", "${filename:toLower():matches('.*\\.xls$')}");
        routes.put("xlsx", "${filename:toLower():matches('.*\\.xlsx$')}");
        NifiEntity route = nifi.createProcessor(pgId, ROUTE_ON_ATTRIBUTE_TYPE,
                node.label() + "/文件类型分流", x, y + 2 * PROCESSOR_ROW_GAP,
                routes, null, null);
        nc.processorIds.put("route-file-type", route.id());

        NifiEntity csv = createRecordConverter(pgId, node.label() + "/CSV 转记录",
                x + PROCESSOR_COLUMN_GAP, y + PROCESSOR_ROW_GAP, nc.csIds.get("fileCsvReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-csv", csv.id());
        NifiEntity xls = createRecordConverter(pgId, node.label() + "/XLS 转记录",
                x + PROCESSOR_COLUMN_GAP, y + 2 * PROCESSOR_ROW_GAP, nc.csIds.get("fileXlsReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-xls", xls.id());
        NifiEntity xlsx = createRecordConverter(pgId, node.label() + "/XLSX 转记录",
                x + PROCESSOR_COLUMN_GAP, y + 3 * PROCESSOR_ROW_GAP, nc.csIds.get("fileXlsxReader"), sharedCs.get("jsonWriter"));
        nc.processorIds.put("convert-xlsx", xlsx.id());

        NifiEntity normalized = nifi.createProcessor(pgId, UPDATE_ATTRIBUTE_TYPE,
                node.label() + "/规范化记录", x + 2 * PROCESSOR_COLUMN_GAP, y + 2 * PROCESSOR_ROW_GAP,
                Map.of(), null, null);
        nc.processorIds.put("normalized", normalized.id());

        connectInternal(pgId, nc, list.id(), fetch.id(), "success");
        connectInternal(pgId, nc, fetch.id(), route.id(), "success");
        connectInternal(pgId, nc, route.id(), csv.id(), "csv");
        connectInternal(pgId, nc, route.id(), normalized.id(), "json");
        connectInternal(pgId, nc, route.id(), xls.id(), "xls");
        connectInternal(pgId, nc, route.id(), xlsx.id(), "xlsx");
        connectInternal(pgId, nc, csv.id(), normalized.id(), "success");
        connectInternal(pgId, nc, xls.id(), normalized.id(), "success");
        connectInternal(pgId, nc, xlsx.id(), normalized.id(), "success");
        nc.outlets.put("default", new Outlet(normalized.id(), "success"));
    }

    private Map<String, String> minioS3Properties(String endpoint, String bucket, String credentialServiceId) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Bucket", bucket);
        properties.put("AWS Credentials Provider Service", credentialServiceId);
        properties.put("Region", "us-east-1");
        properties.put("Endpoint Override URL", endpoint);
        return properties;
    }

    private NifiEntity createRecordConverter(String pgId, String name, double x, double y,
                                             String readerId, String writerId) {
        if (readerId == null || writerId == null) {
            throw new IllegalStateException("File record reader/writer was not created");
        }
        return nifi.createProcessor(pgId, CONVERT_RECORD_TYPE, name, x, y,
                Map.of("Record Reader", readerId, "Record Writer", writerId), null, null);
    }

    private void connectInternal(String pgId, NodeCompilation nc, String sourceId,
                                 String destinationId, String relationship) {
        nifi.createConnection(pgId, sourceId, "PROCESSOR", destinationId, "PROCESSOR", List.of(relationship));
        nc.usedOutgoing.computeIfAbsent(sourceId, ignored -> new LinkedHashSet<>()).add(relationship);
    }

    private static boolean isFileTransferSource(String manifestKey) {
        return "source.ftp".equals(manifestKey) || "source.sftp".equals(manifestKey);
    }

    private static boolean isMinioObjectSource(String manifestKey) {
        return "source.minio".equals(manifestKey);
    }

    private static boolean isStructuredObjectSource(String manifestKey) {
        return isFileTransferSource(manifestKey) || isMinioObjectSource(manifestKey);
    }

    /**
     * API pull sources need a FlowFile before InvokeHTTP can send a POST/PUT body.
     * A generated trigger FlowFile also gives the task one authoritative NiFi
     * schedule: TIMER_DRIVEN for periodic polling and CRON_DRIVEN for the rule's
     * configured cron expression.  The HTTP response itself remains JSON and
     * therefore flows directly into the existing field-mapping component.
     */
    private void buildApiSource(String pgId, Pipeline.Node node, NodeCompilation nc,
                                NodeLayout nodeLayout, Map<String, Object> cfg) {
        String url = configText(cfg, "url");
        if (url.isBlank()) {
            throw new NodeConfigException(node.id(), node.label(), "url", "API 来源缺少接口地址", null);
        }
        String period = firstNonBlankStatic(configText(cfg, "schedulingPeriod"), "60 sec");
        String strategy = firstNonBlankStatic(configText(cfg, "schedulingStrategy"), "TIMER_DRIVEN");
        if (!"TIMER_DRIVEN".equals(strategy) && !"CRON_DRIVEN".equals(strategy)) {
            throw new NodeConfigException(node.id(), node.label(), "schedulingStrategy", "API 调度策略仅支持 TIMER_DRIVEN 或 CRON_DRIVEN", null);
        }
        Map<String, String> triggerProps = new LinkedHashMap<>();
        String body = configText(cfg, "requestBody");
        if (!body.isBlank()) {
            triggerProps.put("Custom Text", body);
            triggerProps.put("Data Format", "Text");
        }
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        NifiEntity trigger = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.GenerateFlowFile",
                node.label() + "/调度触发", x, y, triggerProps, period, strategy);
        nc.processorIds.put("trigger", trigger.id());

        Map<String, String> httpProps = new LinkedHashMap<>();
        httpProps.put("HTTP Method", firstNonBlankStatic(configText(cfg, "method"), "GET").toUpperCase(Locale.ROOT));
        // NiFi 2.x InvokeHTTP calls this required property "HTTP URL".  The
        // historical "Remote URL" label is retained as a dynamic property by
        // newer NiFi versions, so it looks populated but leaves the processor
        // INVALID and makes the subsequent run-status request fail with 409.
        httpProps.put("HTTP URL", apiUrlReachableFromNifi(url));
        // NiFi 2.x uses this formal property to emit a response FlowFile.
        // "Always Output Response" was an older label; under NiFi 2.x it is
        // interpreted as a dynamic HTTP header (with spaces) and every request
        // then fails before it reaches the configured API.
        httpProps.put("Response Generation Required", "true");
        String contentType = firstNonBlankStatic(configText(cfg, "contentType"), "application/json");
        if (!contentType.isBlank()) httpProps.put("Content-Type", contentType);
        String connectTimeout = configText(cfg, "connectTimeout");
        String readTimeout = configText(cfg, "readTimeout");
        if (!connectTimeout.isBlank()) httpProps.put("Connection Timeout", connectTimeout);
        if (!readTimeout.isBlank()) httpProps.put("Socket Read Timeout", readTimeout);
        appendApiHeaders(node, cfg, httpProps);
        NifiEntity invoke = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.InvokeHTTP",
                node.label() + "/调用接口", x + PROCESSOR_COLUMN_GAP, y, httpProps, null, null);
        nc.processorIds.put("invoke", invoke.id());
        connectInternal(pgId, nc, trigger.id(), invoke.id(), "success");
        nc.outlets.put("default", new Outlet(invoke.id(), "Response"));
    }

    /**
     * A source registration is tested by the platform service, but InvokeHTTP
     * runs inside NiFi.  Consequently {@code localhost} in the registered URL
     * means different machines when NiFi is remote/containerized.  Rewrite
     * only a loopback host using the deployment-provided public base URL; do
     * not touch normal user-supplied API hosts.
     */
    private String apiUrlReachableFromNifi(String registeredUrl) {
        if (apiPullLoopbackBaseUrl.isBlank()) {
            return registeredUrl;
        }
        try {
            URI source = URI.create(registeredUrl);
            if (!isLoopbackHost(source.getHost())) {
                return registeredUrl;
            }
            URI reachableBase = URI.create(apiPullLoopbackBaseUrl);
            if (reachableBase.getScheme() == null || reachableBase.getHost() == null) {
                log.warn("Ignoring invalid nifi.api-pull.loopback-base-url; expected an absolute URL: {}",
                        apiPullLoopbackBaseUrl);
                return registeredUrl;
            }
            int port = source.getPort() >= 0 ? source.getPort() : reachableBase.getPort();
            URI resolved = new URI(
                    reachableBase.getScheme(), source.getUserInfo(), reachableBase.getHost(), port,
                    source.getRawPath(), source.getRawQuery(), source.getRawFragment());
            log.info("Resolved API pull loopback URL for NiFi: {} -> {}", registeredUrl, resolved);
            return resolved.toString();
        } catch (Exception exception) {
            log.warn("Unable to resolve API pull URL for NiFi; using registered URL {}", registeredUrl, exception);
            return registeredUrl;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized)
                || normalized.startsWith("127.");
    }

    private void appendApiHeaders(Pipeline.Node node, Map<String, Object> cfg, Map<String, String> properties) {
        String rawHeaders = configText(cfg, "commonHeadersJson");
        if (rawHeaders.isBlank()) return;
        try {
            JsonNode headers = MAPPER.readTree(rawHeaders);
            if (!headers.isObject()) {
                throw new IllegalArgumentException("通用请求头必须是 JSON 对象");
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = headers.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> header = iterator.next();
                String name = header.getKey() == null ? "" : header.getKey().trim();
                if (name.isBlank() || !name.matches("[A-Za-z0-9-]+")) {
                    throw new IllegalArgumentException("请求头名称不合法：" + name);
                }
                if (!header.getValue().isValueNode()) {
                    throw new IllegalArgumentException("请求头值必须为字符串或简单值：" + name);
                }
                properties.put(name, header.getValue().asText());
            }
        } catch (Exception error) {
            throw new NodeConfigException(node.id(), node.label(), "commonHeadersJson",
                    "API 通用请求头配置无效：" + error.getMessage(), error);
        }
    }

    private boolean hasTemporalTargetColumns(Map<String, Object> cfg) {
        return targetColumnMetadata(cfg).stream().anyMatch(column -> isTemporalColumn(column.type()));
    }

    private boolean hasAvailableTemporalTargetColumns(Map<String, Object> cfg, Set<String> availableFields) {
        if (availableFields == null || availableFields.isEmpty()) {
            return false;
        }
        Set<String> availableLower = availableFields.stream()
                .map(field -> field.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return targetColumnMetadata(cfg).stream()
                .anyMatch(column -> isTemporalColumn(column.type())
                        && availableLower.contains(column.name().toLowerCase(Locale.ROOT)));
    }

    private void buildTemporalJdbcSink(String pgId, Pipeline.Node node, ComponentManifest manifest, CompileSpec spec, NodeCompilation nc,
                                       NodeLayout nodeLayout, Map<String, Object> cfg, Map<String, String> sharedCs,
                                       Set<String> availableFields) {
        List<TargetColumnMetadata> columns = targetColumnMetadata(cfg);
        Set<String> availableLower = availableFields.stream()
                .map(field -> field.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> projections = new ArrayList<>();
        for (TargetColumnMetadata column : columns) {
            if (!availableLower.isEmpty() && !availableLower.contains(column.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String identifier = queryIdentifier(column.name());
            projections.add(isTemporalColumn(column.type())
                    ? normalizeTemporalProjection(identifier)
                    : identifier + " AS " + identifier);
        }
        if (projections.isEmpty()) {
            throw new NodeConfigException(node.id(), node.label(), "targetColumns", "请先探查目标字段", null);
        }
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        NifiEntity normalize = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.QueryRecord",
                node.label() + "/normalize", x, y, Map.of(
                        "Record Reader", sharedCs.get("jsonReader"),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Include Zero Record FlowFiles", "false",
                        "success", "SELECT " + String.join(", ", projections) + " FROM FLOWFILE"
                ), null, null);
        nc.processorIds.put("normalize", normalize.id());
        nc.inletProcessorId = normalize.id();

        CompileSpec.ProcessorSpec putSpec = spec.processors().stream()
                .filter(processor -> "put".equals(processor.localId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("sink.jdbc manifest missing processor"));
        Map<String, String> resolved = resolveProps(putSpec.properties(), cfg, nc.csIds, sharedCs);
        applyJdbcSinkPreProcessingSql(null, putSpec.localId(), cfg, resolved);
        normalizeLinewellJdbcSinkProperties(manifest, putSpec.localId(), cfg, resolved);
        String processorType = resolveProcessorType(manifest, putSpec.localId(), putSpec.type(), cfg);
        NifiEntity merge = nifi.createProcessor(pgId, MERGE_RECORD_TYPE, node.label() + "/merge",
                x, y + PROCESSOR_ROW_GAP, jdbcBatchMergeProperties(sharedCs), null, null);
        nc.processorIds.put("merge", merge.id());
        NifiEntity put = nifi.createProcessor(pgId, processorType, node.label() + "/" + putSpec.localId(),
                x, y + PROCESSOR_ROW_GAP * 2, resolved, null, null);
        nc.processorIds.put(putSpec.localId(), put.id());
        nifi.createConnection(pgId, normalize.id(), "PROCESSOR", merge.id(), "PROCESSOR", List.of("success"));
        nifi.createConnection(pgId, merge.id(), "PROCESSOR", put.id(), "PROCESSOR", List.of("merged"));
        nc.usedOutgoing.computeIfAbsent(normalize.id(), key -> new LinkedHashSet<>()).add("success");
        nc.usedOutgoing.computeIfAbsent(merge.id(), key -> new LinkedHashSet<>()).add("merged");
        if (spec.outlets() != null) {
            for (CompileSpec.Outlet outlet : spec.outlets()) {
                nc.outlets.put(outlet.name(), new Outlet(put.id(), outlet.relationship()));
            }
        }
    }

    private Set<String> resolveUpstreamOutputFields(Pipeline pipeline,
                                                    String targetNodeId,
                                                    Map<String, ComponentManifest> manifestByNode,
                                                    Map<String, Map<String, Object>> cfgByNode,
                                                    Map<String, FieldMappingService.CompiledMapping> mappingPlans) {
        return resolveInputFields(pipeline, targetNodeId, manifestByNode, cfgByNode, mappingPlans,
                new LinkedHashSet<>());
    }

    private Set<String> resolveInputFields(Pipeline pipeline,
                                           String nodeId,
                                           Map<String, ComponentManifest> manifestByNode,
                                           Map<String, Map<String, Object>> cfgByNode,
                                           Map<String, FieldMappingService.CompiledMapping> mappingPlans,
                                           Set<String> visited) {
        Set<String> fields = new LinkedHashSet<>();
        if (pipeline.dsl().edges() == null) return fields;
        for (Pipeline.Edge edge : pipeline.dsl().edges()) {
            if (nodeId.equals(edge.target())) {
                fields.addAll(resolveNodeOutputFields(pipeline, edge.source(), manifestByNode, cfgByNode,
                        mappingPlans, visited));
            }
        }
        return fields;
    }

    private Set<String> resolveNodeOutputFields(Pipeline pipeline,
                                                String nodeId,
                                                Map<String, ComponentManifest> manifestByNode,
                                                Map<String, Map<String, Object>> cfgByNode,
                                                Map<String, FieldMappingService.CompiledMapping> mappingPlans,
                                                Set<String> visited) {
        if (!visited.add(nodeId)) return Set.of();
        ComponentManifest manifest = manifestByNode.get(nodeId);
        Map<String, Object> cfg = cfgByNode.getOrDefault(nodeId, Map.of());
        if (manifest == null) return Set.of();

        Set<String> fields = new LinkedHashSet<>();
        if (isFieldMappingTransform(manifest)) {
            FieldMappingService.CompiledMapping plan = mappingPlans.get(nodeId);
            if (plan != null) fields.addAll(plan.outputFields());
            if (mappingPassthroughEnabled(cfg.get("mappings"))) {
                fields.addAll(resolveInputFields(pipeline, nodeId, manifestByNode, cfgByNode, mappingPlans, visited));
            }
            return fields;
        }

        fields.addAll(configuredColumnNames(cfg.get("outputColumns")));
        if ("source".equals(manifest.category())) {
            fields.addAll(configuredColumnNames(cfg.get("sourceColumns")));
            Object byTable = cfg.get("sourceColumnsByTable");
            if (byTable instanceof Map<?, ?> tables) {
                tables.values().forEach(value -> fields.addAll(configuredColumnNames(value)));
            }
            return fields;
        }
        if (!fields.isEmpty()) return fields;

        return resolveInputFields(pipeline, nodeId, manifestByNode, cfgByNode, mappingPlans, visited);
    }

    private Set<String> configuredColumnNames(Object rawColumns) {
        if (!(rawColumns instanceof List<?> columns)) return Set.of();
        Set<String> names = new LinkedHashSet<>();
        for (Object raw : columns) {
            if (raw == null) continue;
            if (raw instanceof String text) {
                if (!text.isBlank()) names.add(text.trim());
                continue;
            }
            Map<String, Object> column = objectMap(raw);
            String name = firstNonBlank(stringValue(column.get("columnName")), stringValue(column.get("name")));
            if (!name.isBlank()) names.add(name);
        }
        return names;
    }

    private boolean mappingPassthroughEnabled(Object rawMappings) {
        if (rawMappings == null) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode spec = rawMappings instanceof String text
                    ? MAPPER.readTree(text)
                    : MAPPER.valueToTree(rawMappings);
            return spec != null && spec.path("passthroughUnmapped").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<TargetColumnMetadata> targetColumnMetadata(Map<String, Object> cfg) {
        Object rawColumns = cfg.get("targetColumns");
        if (!(rawColumns instanceof List<?> columns)) return List.of();
        List<TargetColumnMetadata> result = new ArrayList<>();
        for (Object raw : columns) {
            if (raw == null) continue;
            Map<String, Object> column = objectMap(raw);
            String name = firstNonBlank(stringValue(column.get("columnName")), stringValue(column.get("name")));
            if (name.isBlank()) continue;
            String type = firstNonBlank(stringValue(column.get("dataType")),
                    stringValue(column.get("columnType")), stringValue(column.get("typeName")));
            boolean primaryKey = columnFlag(column, "primaryKey", "primary_key", "isPrimaryKey", "is_primary_key", "isPk", "is_pk");
            boolean unique = columnFlag(column, "isUnique", "is_unique", "unique", "uk");
            result.add(new TargetColumnMetadata(name, type, primaryKey, unique));
        }
        return result;
    }

    private void normalizeJdbcSinkKeys(Pipeline.Node node, Map<String, Object> cfg, Set<String> availableFields) {
        if (cfg == null) return;
        String statementType = jdbcStatementType(
                String.valueOf(cfg.getOrDefault("statementType", "INSERT")),
                String.valueOf(cfg.getOrDefault("dbType", "MYSQL")), cfg);
        if (!Set.of("UPSERT", "MERGE", "UPDATE", "DELETE").contains(statementType)) {
            return;
        }
        List<TargetColumnMetadata> columns = targetColumnMetadata(cfg);
        if (columns.isEmpty()) {
            throw new NodeConfigException(node.id(), node.label(), "targetColumns",
                    "UPSERT requires probed target columns and a business primary or unique key", null);
        }

        Map<String, String> actualNames = new LinkedHashMap<>();
        Map<String, TargetColumnMetadata> metadataByName = new LinkedHashMap<>();
        for (TargetColumnMetadata column : columns) {
            actualNames.put(column.name().toLowerCase(Locale.ROOT), column.name());
            metadataByName.put(column.name().toLowerCase(Locale.ROOT), column);
        }

        String rawKeys = stringConfig(cfg, "updateKeys");
        List<String> normalized = new ArrayList<>();
        for (String part : rawKeys.split(",")) {
            String key = part.trim();
            if (key.isEmpty()) continue;
            TargetColumnMetadata metadata = metadataByName.get(key.toLowerCase(Locale.ROOT));
            if (metadata == null) {
                throw new NodeConfigException(node.id(), node.label(), "updateKeys",
                        "Update key " + key + " does not exist in the target table", null);
            }
            if (!isBusinessStableKey(metadata)) {
                throw new NodeConfigException(node.id(), node.label(), "updateKeys",
                        "Update key " + metadata.name() + " must be a business primary or unique key; ODS_UUID cannot be used", null);
            }
            normalized.add(actualNames.get(key.toLowerCase(Locale.ROOT)));
        }

        if (normalized.isEmpty()) {
            normalized = stableKeyColumnNames(columns);
        }
        if (normalized.isEmpty()) {
            throw new NodeConfigException(node.id(), node.label(), "updateKeys",
                    "UPSERT requires a business primary or unique key. Configure one on the target table, then probe the columns again", null);
        }
        Set<String> availableLower = availableFields == null ? Set.of() : availableFields.stream()
                .filter(Objects::nonNull)
                .map(field -> field.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String key : normalized) {
            if (!availableLower.isEmpty() && !availableLower.contains(key.toLowerCase(Locale.ROOT))) {
                throw new NodeConfigException(node.id(), node.label(), "updateKeys",
                        "Update key " + key + " is not mapped from the source data", null);
            }
        }
        cfg.put("updateKeys", String.join(",", normalized));
    }

    /**
     * NiFi performs metadata lookup before writing records. Oracle-compatible
     * drivers expose unquoted identifiers in upper case, so a lower-case table
     * name from the catalog must be normalized before it reaches PutDatabaseRecord.
     */
    static void normalizeJdbcSinkTableName(Map<String, Object> cfg) {
        normalizeOracleCompatibleTableName(cfg);
    }

    /**
     * Normalize source table names before GenerateTableFetch performs its own
     * metadata query. Oracle does not resolve a table in another owner unless
     * the owner is part of the identifier.
     */
    static void normalizeJdbcSourceTableName(Map<String, Object> cfg) {
        normalizeOracleCompatibleTableName(cfg);
    }

    static void normalizeJdbcSourceConfig(Map<String, Object> cfg) {
        if (cfg == null) return;
        // The shared OceanBase source manifest represents both compatibility modes.
        // Promote Oracle mode before normalizing identifiers so it receives the
        // Oracle owner/table rules, JDBC URL, driver dialect and fetch SQL syntax.
        if (isOceanBaseOracleMode(cfg)) {
            cfg.put("dbType", "OCEANBASE_ORACLE");
            if (configText(cfg, "jdbcUrl").isBlank()) {
                String host = configText(cfg, "host");
                String database = configText(cfg, "database");
                if (!host.isBlank() && !database.isBlank()) {
                    String port = firstNonBlankStatic(configText(cfg, "port"), "2881");
                    cfg.put("jdbcUrl", "jdbc:oceanbase:oracle://" + host + ":" + port + "/" + database);
                }
            }
        }
        normalizeJdbcSourceTableName(cfg);

        String databaseType = configText(cfg, "dbType").toUpperCase(Locale.ROOT);
        if (!"ORACLE".equals(databaseType)) return;

        String service = firstNonBlankStatic(configText(cfg, "sid"),
                configText(cfg, "serviceName"), configText(cfg, "database"));
        if (service.isBlank()) return;

        cfg.put("sid", service);
        cfg.put("serviceName", service);
        cfg.put("database", service);
        if (!configText(cfg, "jdbcUrl").isBlank()) return;

        String host = configText(cfg, "host");
        String port = firstNonBlankStatic(configText(cfg, "port"), "1521");
        String connectionType = firstNonBlankStatic(configText(cfg, "connectionType"),
                configText(cfg, "jdbcType")).toUpperCase(Locale.ROOT);
        String jdbcUrl = "SID".equals(connectionType)
                ? "jdbc:oracle:thin:@" + host + ":" + port + ":" + service
                : "jdbc:oracle:thin:@//" + host + ":" + port + "/" + service;
        cfg.put("jdbcUrl", jdbcUrl);
    }

    /**
     * A pipeline stores only {@code hiveProfile=default}, Hive database and table.
     * Resolve the MRS/Kerberos files immediately before deployment so their paths
     * never become part of the user-saved DSL or a browser response.
     */
    private void materializeServerManagedHuaweiMrsProfile(String manifestKey, Map<String, Object> cfg) {
        if (!"source.hive".equals(manifestKey) && !"sink.hive".equals(manifestKey)) return;
        String profile = firstNonBlank(stringConfig(cfg, "hiveProfile"), stringConfig(cfg, "hive_profile"));
        String mode = firstNonBlank(stringConfig(cfg, "metadataAccessMode"), stringConfig(cfg, "metadata_access_mode"));
        String connectionMode = firstNonBlank(stringConfig(cfg, "hiveConnectionMode"), stringConfig(cfg, "hive_connection_mode"));
        boolean serverManaged = !profile.isBlank()
                || "server-managed-mrs".equalsIgnoreCase(mode)
                || "huawei-mrs".equalsIgnoreCase(connectionMode);
        if (!serverManaged) return;

        String database = stringConfig(cfg, "database");
        String table = stringConfig(cfg, "table");
        String incrementalColumn = stringConfig(cfg, "incrementalColumn");
        String schedulingPeriod = stringConfig(cfg, "schedulingPeriod");
        try {
            cfg.putAll(hiveModule.resolveRuntimeProfile(profile));
        } catch (Exception e) {
            throw new NodeConfigException("", manifestKey, "hiveProfile",
                    "无法读取服务端华为 MRS Hive 配置集：" + e.getMessage(), e);
        }
        // The registered datasource/table remains the tenant-owned business choice.
        if (!database.isBlank()) cfg.put("database", database);
        if (!table.isBlank()) cfg.put("table", table);
        if (!incrementalColumn.isBlank()) cfg.put("incrementalColumn", incrementalColumn);
        if (!schedulingPeriod.isBlank()) cfg.put("schedulingPeriod", schedulingPeriod);
        cfg.remove("jdbcUrl");
        cfg.put("jdbcUrl", buildHiveJdbcUrl(cfg));
    }

    static String sourceFetchDatabaseType(Map<String, Object> cfg) {
        if (cfg == null) return null;
        String databaseType = configText(cfg, "dbType").toUpperCase(Locale.ROOT);
        if ("ORACLE".equals(databaseType)) {
            // Always use the original Oracle dialect, even against newer versions.
            // It emits ROWNUM pagination, which is compatible with Oracle 11g and
            // older Oracle-compatible OceanBase clusters.  The modern dialect
            // produces FETCH NEXT ... ROWS ONLY, which is rejected by those
            // databases (ORA-00933).
            return "Oracle";
        }
        if (Set.of("OCEANBASE_ORACLE", "OCEANBASEORACLE").contains(databaseType)
                || isOceanBaseOracleMode(cfg)) {
            return "Oracle";
        }
        return null;
    }

    private static boolean isOceanBaseOracleMode(Map<String, Object> cfg) {
        String databaseType = configText(cfg, "dbType").toUpperCase(Locale.ROOT);
        if (Set.of("OCEANBASE_ORACLE", "OCEANBASEORACLE").contains(databaseType)) {
            return true;
        }
        return "OCEANBASE".equals(databaseType)
                && "ORACLE".equals(configText(cfg, "compatibleMode").toUpperCase(Locale.ROOT));
    }

    private static void normalizeOracleCompatibleTableName(Map<String, Object> cfg) {
        if (cfg == null) return;
        String table = configText(cfg, "table");
        if (table.isBlank()) return;

        String databaseType = configText(cfg, "dbType").toUpperCase(Locale.ROOT);
        if (!Set.of("ORACLE", "OCEANBASE_ORACLE", "OCEANBASEORACLE", "DM", "DAMENG").contains(databaseType)) {
            return;
        }

        if (table.contains(".")) {
            cfg.put("table", Arrays.stream(table.split("\\\\."))
                    .map(DslCompiler::oracleIdentifier)
                    .collect(java.util.stream.Collectors.joining(".")));
            return;
        }

        String schema = firstNonBlankStatic(configText(cfg, "schema"),
                configText(cfg, "defaultSchema"), configText(cfg, "currentSchema"),
                configText(cfg, "schemaName"), configText(cfg, "schema_name"),
                configText(cfg, "owner"));
        String normalizedTable = oracleIdentifier(table);
        cfg.put("table", schema.isBlank() ? normalizedTable : oracleIdentifier(schema) + "." + normalizedTable);
    }

    private static String configText(Map<String, Object> cfg, String key) {
        Object value = cfg.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * GenerateTableFetch serializes temporal watermarks using ANSI
     * {@code timestamp '...'} literals. Although newer Oracle versions accept
     * that literal, older Oracle and a number of OceanBase Oracle deployments
     * do not handle it consistently with DATE columns and session NLS settings.
     *
     * <p>The source manifests place a ReplaceText processor immediately after
     * GenerateTableFetch. These values tell it how to make the generated SQL
     * explicit and version-independent. DATE values deliberately discard the
     * generated fractional suffix (Oracle DATE has second precision); TIMESTAMP
     * values retain it through {@code FF}.</p>
     */
    static void configureOracleCompatibleFetchSql(Map<String, Object> cfg) {
        if (cfg == null) return;
        String databaseType = configText(cfg, "dbType").toUpperCase(Locale.ROOT);
        boolean oracleCompatible = "ORACLE".equals(databaseType)
                || Set.of("OCEANBASE_ORACLE", "OCEANBASEORACLE").contains(databaseType)
                || isOceanBaseOracleMode(cfg);
        if (!oracleCompatible) {
            // The OceanBase manifest is shared by MySQL and Oracle modes. A
            // guaranteed no-match pattern keeps the normal MySQL SQL untouched.
            cfg.put("oracleWatermarkSearch", "(?!)");
            cfg.put("oracleWatermarkReplacement", "");
            return;
        }

        String incrementalType = sourceColumnType(cfg, configText(cfg, "incrementalColumn"));
        boolean timestamp = incrementalType.toUpperCase(Locale.ROOT).contains("TIMESTAMP");
        if (timestamp) {
            cfg.put("oracleWatermarkSearch", "(?i)timestamp\\s+'([^']+)'");
            cfg.put("oracleWatermarkReplacement",
                    "TO_TIMESTAMP('$1', 'YYYY-MM-DD HH24:MI:SS.FF')");
        } else {
            cfg.put("oracleWatermarkSearch",
                    "(?i)timestamp\\s+'(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?'");
            cfg.put("oracleWatermarkReplacement",
                    "TO_DATE('$1', 'YYYY-MM-DD HH24:MI:SS')");
        }
    }

    private static String sourceColumnType(Map<String, Object> cfg, String incrementalColumn) {
        if (incrementalColumn == null || incrementalColumn.isBlank()) return "";
        String column = incrementalColumn.split(",", 2)[0].trim();
        Object rawColumns = cfg.get("sourceColumns");
        if (!(rawColumns instanceof List<?> columns)) return "";
        for (Object rawColumn : columns) {
            if (!(rawColumn instanceof Map<?, ?> values)) continue;
            Object rawName = values.get("columnName");
            if (rawName == null) rawName = values.get("name");
            if (rawName == null || !column.equalsIgnoreCase(String.valueOf(rawName).trim())) continue;
            for (String key : List.of("dataType", "columnType", "typeName", "type")) {
                Object value = values.get(key);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String firstNonBlankStatic(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String oracleIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        return value.startsWith("\"") && value.endsWith("\"")
                ? value
                : value.toUpperCase(Locale.ROOT);
    }

    private List<String> stableKeyColumnNames(List<TargetColumnMetadata> columns) {
        List<String> keys = new ArrayList<>();
        for (TargetColumnMetadata column : columns) {
            if (isBusinessStableKey(column)) {
                keys.add(column.name());
            }
        }
        return keys;
    }

    private boolean isBusinessStableKey(TargetColumnMetadata column) {
        return column != null
                && !"ODS_UUID".equalsIgnoreCase(column.name())
                && (column.primaryKey() || column.unique());
    }

    private boolean columnFlag(Map<String, Object> column, String... keys) {
        for (String key : keys) {
            Object value = column.get(key);
            if (value instanceof Boolean flag && flag) return true;
            if (value instanceof Number number && number.intValue() == 1) return true;
            String text = stringValue(value);
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    private boolean isTemporalColumn(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return normalized.contains("date") || normalized.contains("time") || normalized.contains("timestamp");
    }

    private void buildConditionalBranch(String pgId, Pipeline.Node node, NodeCompilation nc, NodeLayout nodeLayout,
                                        Map<String, Object> cfg, Map<String, String> sharedCs) {
        Object rawRoutes = cfg.get("routes");
        if (!(rawRoutes instanceof List<?> routes) || routes.isEmpty()) {
            throw new NodeConfigException(node.id(), node.label(), "routes", "请至少配置一个分流规则", null);
        }
        if (hasNonAsciiBranchValue(routes)) {
            buildAttributeConditionalBranch(pgId, node, nc, nodeLayout, routes, sharedCs);
            return;
        }
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Record Reader", sharedCs.get("jsonReader"));
        properties.put("Record Writer", sharedCs.get("jsonWriter"));
        properties.put("Include Zero Record FlowFiles", "false");
        List<String> routeConditions = new ArrayList<>();
        List<String> routeIds = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            Map<String, Object> route = objectMap(routes.get(i));
            String routeId = stringValue(route.get("id"));
            if (routeId.isBlank()) routeId = "route_" + (i + 1);
            if (!routeId.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
                throw new NodeConfigException(node.id(), node.label(), "routes", "分支标识不合法: " + routeId, null);
            }
            String condition = compileBusinessConditions(route.get("conditions"));
            if (condition.isBlank()) {
                throw new NodeConfigException(node.id(), node.label(), "routes", "分支“" + stringValue(route.get("name")) + "”至少需要一条完整条件", null);
            }
            properties.put(routeId, "SELECT * FROM FLOWFILE WHERE " + condition);
            routeConditions.add("(" + condition + ")");
            routeIds.add(routeId);
        }
        String defaultId = "otherwise";
        properties.put(defaultId, "SELECT * FROM FLOWFILE WHERE NOT (" + routeConditions.stream()
                .map(condition -> "COALESCE(" + condition + ", FALSE)")
                .reduce((left, right) -> left + " OR " + right).orElse("FALSE") + ")");
        NifiEntity query = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.QueryRecord",
                node.label() + "/route", nodeOriginX(nodeLayout), nodeOriginY(nodeLayout), properties, null, null);
        nc.processorIds.put("route", query.id());
        nc.inletProcessorId = query.id();
        routeIds.forEach(routeId -> nc.outlets.put(routeId, new Outlet(query.id(), routeId)));
        nc.outlets.put(defaultId, new Outlet(query.id(), defaultId));
    }

    private boolean hasNonAsciiBranchValue(List<?> routes) {
        for (Object rawRoute : routes) {
            Object rawConditions = objectMap(rawRoute).get("conditions");
            if (!(rawConditions instanceof List<?> conditions)) continue;
            for (Object rawCondition : conditions) {
                String value = stringValue(objectMap(rawCondition).get("value"));
                if (value.chars().anyMatch(ch -> ch > 0x7f)) return true;
            }
        }
        return false;
    }

    private void buildAttributeConditionalBranch(String pgId, Pipeline.Node node, NodeCompilation nc,
                                                  NodeLayout nodeLayout, List<?> routes,
                                                  Map<String, String> sharedCs) {
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        NifiEntity split = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.SplitRecord",
                node.label() + "/split", x, y, Map.of(
                        "Record Reader", sharedCs.get("jsonReader"),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Records Per Split", "1"
                ), null, null);
        nc.processorIds.put("split", split.id());
        nc.inletProcessorId = split.id();

        Map<String, String> extractProperties = new LinkedHashMap<>();
        extractProperties.put("Destination", "flowfile-attribute");
        extractProperties.put("Return Type", "auto-detect");
        extractProperties.put("Path Not Found Behavior", "skip");
        Set<String> fields = new LinkedHashSet<>();
        for (Object rawRoute : routes) {
            Object rawConditions = objectMap(rawRoute).get("conditions");
            if (!(rawConditions instanceof List<?> conditions)) continue;
            for (Object rawCondition : conditions) {
                String field = stringValue(objectMap(rawCondition).get("field"));
                if (field.isBlank()) continue;
                validateBranchField(field);
                fields.add(field);
            }
        }
        fields.forEach(field -> extractProperties.put("branch." + field, "$." + field));
        NifiEntity extract = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.EvaluateJsonPath",
                node.label() + "/extract", x, y + PROCESSOR_ROW_GAP, extractProperties, null, null);
        nc.processorIds.put("extract", extract.id());
        nifi.createConnection(pgId, split.id(), "PROCESSOR", extract.id(), "PROCESSOR", List.of("splits"));
        nc.usedOutgoing.computeIfAbsent(split.id(), key -> new LinkedHashSet<>()).add("splits");

        Map<String, String> routeProperties = new LinkedHashMap<>();
        routeProperties.put("Routing Strategy", "Route to Property name");
        List<String> routeIds = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            Map<String, Object> route = objectMap(routes.get(i));
            String routeId = stringValue(route.get("id"));
            if (routeId.isBlank()) routeId = "route_" + (i + 1);
            if (!routeId.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
                throw new NodeConfigException(node.id(), node.label(), "routes", "分支标识不合法: " + routeId, null);
            }
            String expression = compileAttributeConditions(route.get("conditions"));
            if (expression.isBlank()) {
                throw new NodeConfigException(node.id(), node.label(), "routes", "分支“"
                        + stringValue(route.get("name")) + "”至少需要一条完整条件", null);
            }
            routeProperties.put(routeId, expression);
            routeIds.add(routeId);
        }
        NifiEntity route = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.RouteOnAttribute",
                node.label() + "/route", x, y + 2 * PROCESSOR_ROW_GAP, routeProperties, null, null);
        nc.processorIds.put("route", route.id());
        nifi.createConnection(pgId, extract.id(), "PROCESSOR", route.id(), "PROCESSOR", List.of("matched", "unmatched"));
        nc.usedOutgoing.computeIfAbsent(extract.id(), key -> new LinkedHashSet<>()).addAll(List.of("matched", "unmatched"));
        routeIds.forEach(routeId -> nc.outlets.put(routeId, new Outlet(route.id(), routeId)));
        nc.outlets.put("otherwise", new Outlet(route.id(), "unmatched"));
    }

    private String compileAttributeConditions(Object rawConditions) {
        if (!(rawConditions instanceof List<?> conditions)) return "";
        List<String> predicates = new ArrayList<>();
        List<String> connectors = new ArrayList<>();
        for (Object raw : conditions) {
            Map<String, Object> condition = objectMap(raw);
            String field = stringValue(condition.get("field"));
            String operator = stringValue(condition.get("operator")).toUpperCase(Locale.ROOT);
            String value = stringValue(condition.get("value"));
            if (field.isBlank() || operator.isBlank()) continue;
            validateBranchField(field);
            boolean noValue = Set.of("IS_NULL", "NOT_NULL").contains(operator);
            if (!noValue && value.isBlank()) continue;
            String subject = "'branch." + field + "'";
            String escaped = escapeExpressionValue(value);
            String predicate = switch (operator) {
                case "EQ" -> subject + ":equals('" + escaped + "')";
                case "NE" -> subject + ":equals('" + escaped + "'):not()";
                case "GT" -> subject + ":toNumber():gt(" + numericExpressionValue(value) + ")";
                case "GE" -> subject + ":toNumber():ge(" + numericExpressionValue(value) + ")";
                case "LT" -> subject + ":toNumber():lt(" + numericExpressionValue(value) + ")";
                case "LE" -> subject + ":toNumber():le(" + numericExpressionValue(value) + ")";
                case "CONTAINS" -> subject + ":contains('" + escaped + "')";
                case "STARTS_WITH" -> subject + ":startsWith('" + escaped + "')";
                case "IN" -> expressionInPredicate(subject, value);
                case "IS_NULL" -> subject + ":isEmpty()";
                case "NOT_NULL" -> subject + ":isEmpty():not()";
                default -> throw new IllegalStateException("不支持的判断方式: " + operator);
            };
            predicates.add(predicate);
            connectors.add("OR".equalsIgnoreCase(stringValue(condition.get("connector"))) ? "or" : "and");
        }
        if (predicates.isEmpty()) return "";
        String expression = "${" + predicates.getFirst() + "}";
        for (int i = 1; i < predicates.size(); i++) {
            expression = expression.substring(0, expression.length() - 1) + ":" + connectors.get(i - 1)
                    + "(${" + predicates.get(i) + "})}";
        }
        return expression;
    }

    private String expressionInPredicate(String subject, String rawValue) {
        List<String> values = Arrays.stream(rawValue.split("[,，]"))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (values.isEmpty()) return subject + ":equals('__never__')";
        String predicate = subject + ":equals('" + escapeExpressionValue(values.getFirst()) + "')";
        for (int i = 1; i < values.size(); i++) {
            predicate += ":or(${" + subject + ":equals('" + escapeExpressionValue(values.get(i)) + "')})";
        }
        return predicate;
    }

    private String numericExpressionValue(String value) {
        if (!value.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalStateException("数值比较条件必须填写数字: " + value);
        }
        return value;
    }

    private String escapeExpressionValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void validateBranchField(String field) {
        if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("分流字段名不合法: " + field);
        }
    }

    private String compileBusinessConditions(Object rawConditions) {
        if (!(rawConditions instanceof List<?> conditions)) return "";
        List<String> expressions = new ArrayList<>();
        List<String> connectors = new ArrayList<>();
        for (Object raw : conditions) {
            Map<String, Object> condition = objectMap(raw);
            String field = stringValue(condition.get("field"));
            String operator = stringValue(condition.get("operator")).toUpperCase(Locale.ROOT);
            String value = stringValue(condition.get("value"));
            if (field.isBlank() || operator.isBlank()) continue;
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalStateException("分流字段名不合法: " + field);
            }
            String identifier = queryIdentifier(field);
            boolean noValue = Set.of("IS_NULL", "NOT_NULL").contains(operator);
            if (!noValue && value.isBlank()) continue;
            boolean nonAscii = value.chars().anyMatch(ch -> ch > 0x7f);
            String comparableIdentifier = identifier;
            String literal = value.matches("-?\\d+(\\.\\d+)?") ? value : sqlTextLiteral(value);
            String expression = switch (operator) {
                case "EQ" -> nonAscii ? regexpLike(identifier, value, true, true) : comparableIdentifier + " = " + literal;
                case "NE" -> nonAscii ? "NOT " + regexpLike(identifier, value, true, true) : comparableIdentifier + " <> " + literal;
                case "GT" -> comparableIdentifier + " > " + literal;
                case "GE" -> comparableIdentifier + " >= " + literal;
                case "LT" -> comparableIdentifier + " < " + literal;
                case "LE" -> comparableIdentifier + " <= " + literal;
                case "CONTAINS" -> nonAscii ? regexpLike(identifier, value, false, false)
                        : comparableIdentifier + " LIKE " + sqlTextLiteral("%" + value + "%");
                case "STARTS_WITH" -> nonAscii ? regexpLike(identifier, value, true, false)
                        : comparableIdentifier + " LIKE " + sqlTextLiteral(value + "%");
                case "IN" -> nonAscii ? regexpIn(identifier, value)
                        : comparableIdentifier + " IN (" + Arrays.stream(value.split("[,，]"))
                            .map(String::trim).filter(v -> !v.isBlank()).map(this::sqlTextLiteral)
                            .reduce((a, b) -> a + ", " + b).orElse("''") + ")";
                case "IS_NULL" -> "(" + identifier + " IS NULL OR " + identifier + " = '')";
                case "NOT_NULL" -> identifier + " IS NOT NULL AND " + identifier + " <> ''";
                default -> throw new IllegalStateException("不支持的判断方式: " + operator);
            };
            expressions.add(expression);
            connectors.add("OR".equalsIgnoreCase(stringValue(condition.get("connector"))) ? "OR" : "AND");
        }
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < expressions.size(); i++) {
            if (i > 0) sql.append(' ').append(connectors.get(i - 1)).append(' ');
            sql.append(expressions.get(i));
        }
        return sql.toString();
    }

    private String sqlTextLiteral(String value) {
        String escaped = value == null ? "" : value.replace("'", "''");
        boolean nonAscii = escaped.chars().anyMatch(ch -> ch > 0x7f);
        return (nonAscii ? "_UTF8" : "") + "'" + escaped + "'";
    }

    private String regexpLike(String identifier, String value, boolean anchorStart, boolean anchorEnd) {
        String pattern = unicodeRegex(value);
        if (anchorStart) pattern = "^" + pattern;
        if (anchorEnd) pattern = pattern + "$";
        return "REGEXP_LIKE(" + identifier + ", '" + pattern.replace("'", "''") + "')";
    }

    private String regexpIn(String identifier, String value) {
        String alternatives = Arrays.stream(value.split("[,，]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(this::unicodeRegex)
                .reduce((left, right) -> left + "|" + right)
                .orElse("(?!)");
        return "REGEXP_LIKE(" + identifier + ", '^(?:" + alternatives.replace("'", "''") + ")$')";
    }

    private String unicodeRegex(String value) {
        StringBuilder pattern = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint > 0x7f) {
                pattern.append("\\x{").append(Integer.toHexString(codePoint)).append('}');
            } else {
                char ch = (char) codePoint;
                if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0) pattern.append('\\');
                pattern.append(ch);
            }
        });
        return pattern.toString();
    }

    private String normalizeTemporalProjection(String identifier) {
        // External file timestamps commonly arrive as ISO-8601 values such as
        // 2026-09-12T09:00:00Z.  NiFi's JSON reader leaves the UTC suffix in
        // place, while Dameng DATE/DATETIME accepts the local 19-character
        // representation only.  Normalize the common ISO, date-only, minute
        // and fractional-second forms before PutDatabaseRecord binds them.
        String text = "REPLACE(REPLACE(CAST(" + identifier + " AS VARCHAR), 'T', ' '), '/', '-')";
        return "CASE WHEN " + identifier + " IS NULL THEN NULL "
                + "WHEN CHAR_LENGTH(" + text + ") = 10 THEN " + text + " || ' 00:00:00' "
                + "WHEN CHAR_LENGTH(" + text + ") = 16 THEN " + text + " || ':00' "
                + "WHEN CHAR_LENGTH(" + text + ") >= 19 THEN SUBSTRING(" + text + " FROM 1 FOR 19) "
                + "ELSE " + text + " END AS " + identifier;
    }

    private void buildFieldMappingLookupChain(String pgId,
                                              Pipeline pipeline,
                                              Pipeline.Node node,
                                              CompileSpec spec,
                                              NodeCompilation nc,
                                              NodeLayout nodeLayout,
                                              FieldMappingService.CompiledMapping plan,
                                              Map<String, String> sharedCs,
                                              Map<String, ComponentManifest> manifestByNode,
                                              Map<String, Map<String, Object>> cfgByNode) {
        double x = nodeOriginX(nodeLayout);
        double flowRowY = nodeOriginY(nodeLayout);
        NifiEntity baseQuery = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.QueryRecord",
                node.label() + "/base-query", x + PROCESSOR_COLUMN_GAP, flowRowY, Map.of(
                        "Record Reader", sharedCs.get("jsonReader"),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Include Zero Record FlowFiles", "false",
                        "success", plan.baseQuery()
                ), null, null);
        nc.processorIds.put("base-query", baseQuery.id());
        nc.inletProcessorId = baseQuery.id();

        NifiEntity split = nifi.createProcessor(pgId, SPLIT_RECORD_TYPE,
                node.label() + "/split", x, flowRowY, Map.of(
                        "Record Reader", sharedCs.get("jsonReader"),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Records Per Split", "1"
                ), null, null);
        nc.processorIds.put("split", split.id());
        nifi.createConnection(pgId, baseQuery.id(), "PROCESSOR", split.id(), "PROCESSOR", List.of("success"));
        nc.usedOutgoing.computeIfAbsent(baseQuery.id(), k -> new LinkedHashSet<>()).add("success");

        String previous = split.id();
        Set<String> availableFields = new LinkedHashSet<>(plan.baseFields());
        // A lookup plan describes one target value, but it does not need one
        // NiFi chain.  Keep one chain per dictionary connection instead: the
        // ExecuteSQLRecord query below joins all lookup subqueries in one DB
        // round trip and carries all eight converted fields forward together.
        Map<String, List<FieldMappingService.LookupPlan>> lookupGroups = new LinkedHashMap<>();
        for (FieldMappingService.LookupPlan lookup : plan.lookups()) {
            lookupGroups.computeIfAbsent(lookupConnectionKey(lookup), ignored -> new ArrayList<>()).add(lookup);
        }
        int groupIndex = 0;
        for (List<FieldMappingService.LookupPlan> lookups : lookupGroups.values()) {
            int chainIndex = groupIndex++;
            double lookupRowY = flowRowY + PROCESSOR_ROW_GAP + chainIndex * PROCESSOR_ROW_GAP;
            boolean leftToRight = chainIndex % 2 == 0;
            double extractX = x + (leftToRight ? 0 : 2 * PROCESSOR_COLUMN_GAP);
            double argsX = x + PROCESSOR_COLUMN_GAP;
            double execX = x + (leftToRight ? 2 * PROCESSOR_COLUMN_GAP : 0);
            NifiEntity extract = nifi.createProcessor(pgId, EVALUATE_JSON_PATH_TYPE,
                    node.label() + "/lookup-extract-" + chainIndex, extractX, lookupRowY,
                    buildLookupExtractProperties(availableFields, lookups), null, null);
            nc.processorIds.put("lookup-extract-" + chainIndex, extract.id());
            String relationship = previous.equals(split.id()) ? "splits" : "success";
            nifi.createConnection(pgId, previous, "PROCESSOR", extract.id(), "PROCESSOR", List.of(relationship));
            nc.usedOutgoing.computeIfAbsent(previous, k -> new LinkedHashSet<>()).add(relationship);

            Set<String> currentTargets = lookups.stream()
                    .map(FieldMappingService.LookupPlan::targetField)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<String> passThroughFields = availableFields.stream()
                    .filter(field -> !currentTargets.contains(field))
                    .toList();
            NifiEntity attrs = nifi.createProcessor(pgId, UPDATE_ATTRIBUTE_TYPE,
                    node.label() + "/lookup-args-" + chainIndex, argsX, lookupRowY,
                    buildLookupArgumentProperties(lookups, passThroughFields), null, null);
            nc.processorIds.put("lookup-args-" + chainIndex, attrs.id());
            nifi.createConnection(pgId, extract.id(), "PROCESSOR", attrs.id(), "PROCESSOR", List.of("matched"));
            nc.usedOutgoing.computeIfAbsent(extract.id(), k -> new LinkedHashSet<>()).add("matched");

            NifiEntity exec = nifi.createProcessor(pgId, EXECUTE_SQL_RECORD_TYPE,
                    node.label() + (lookups.stream().anyMatch(FieldMappingService.LookupPlan::multiValue)
                            ? "/多值翻译-lookup-exec-" : "/lookup-exec-") + chainIndex, execX, lookupRowY, Map.of(
                            "Database Connection Pooling Service", nc.csIds.get(lookupConnectionKey(lookups.getFirst())),
                            "Record Writer", sharedCs.get("jsonWriter"),
                            "SQL Query", buildLookupEnrichmentQuery(lookups, passThroughFields)
                    ), null, null);
            nc.processorIds.put("lookup-exec-" + chainIndex, exec.id());
            nifi.createConnection(pgId, attrs.id(), "PROCESSOR", exec.id(), "PROCESSOR", List.of("success"));
            nc.usedOutgoing.computeIfAbsent(attrs.id(), k -> new LinkedHashSet<>()).add("success");
            previous = exec.id();
            availableFields.addAll(currentTargets);
        }

        double finalQueryY = flowRowY + PROCESSOR_ROW_GAP + lookupGroups.size() * PROCESSOR_ROW_GAP;
        NifiEntity finalQuery = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.QueryRecord",
                node.label() + "/map", x + PROCESSOR_COLUMN_GAP, finalQueryY, Map.of(
                        "Record Reader", sharedCs.get("jsonReader"),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Include Zero Record FlowFiles", "false",
                        "success", fieldMappingFinalQuery(pipeline, node.id(), manifestByNode, cfgByNode, plan)
                ), null, null);
        nc.processorIds.put("map", finalQuery.id());
        nifi.createConnection(pgId, previous, "PROCESSOR", finalQuery.id(), "PROCESSOR", List.of("success"));
        nc.usedOutgoing.computeIfAbsent(previous, k -> new LinkedHashSet<>()).add("success");
        nc.outlets.put("default", new Outlet(finalQuery.id(), "success"));
    }

    private String fieldMappingFinalQuery(Pipeline pipeline,
                                          String nodeId,
                                          Map<String, ComponentManifest> manifestByNode,
                                          Map<String, Map<String, Object>> cfgByNode,
                                          FieldMappingService.CompiledMapping plan) {
        Set<String> temporalFields = downstreamTargetTemporalFields(pipeline, nodeId, manifestByNode, cfgByNode);
        if (temporalFields.isEmpty()) {
            return plan.finalQuery();
        }
        List<String> projections = new ArrayList<>();
        for (String field : plan.outputFields()) {
            String identifier = queryIdentifier(field);
            if (temporalFields.contains(field.toLowerCase(Locale.ROOT))) {
                projections.add(normalizeTemporalProjection(identifier));
            } else {
                projections.add(identifier + " AS " + identifier);
            }
        }
        return projections.isEmpty() ? plan.finalQuery() : "SELECT " + String.join(", ", projections) + " FROM FLOWFILE";
    }

    private String lookupConnectionKey(FieldMappingService.LookupPlan lookup) {
        String value = lookup.dataSource() == null ? "" : stringValue(lookup.dataSource().get("datasourceId"));
        if (value.isBlank()) value = lookup.dataSource() == null ? "" : stringValue(lookup.dataSource().get("id"));
        if (value.isBlank()) return "lookup-dbcp-source";
        return "lookup-dbcp-" + value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private Set<String> downstreamTargetTemporalFields(Pipeline pipeline,
                                                       String nodeId,
                                                       Map<String, ComponentManifest> manifestByNode,
                                                       Map<String, Map<String, Object>> cfgByNode) {
        Set<String> fields = new LinkedHashSet<>();
        if (pipeline.dsl().edges() == null) return fields;
        for (Pipeline.Edge edge : pipeline.dsl().edges()) {
            if (!nodeId.equals(edge.source())) continue;
            ComponentManifest targetManifest = manifestByNode.get(edge.target());
            if (targetManifest == null || !"sink".equals(targetManifest.category())) continue;
            Map<String, Object> targetCfg = cfgByNode.get(edge.target());
            if (targetCfg == null) continue;
            Object rawColumns = targetCfg.get("targetColumns");
            if (!(rawColumns instanceof List<?> columns)) continue;
            for (Object rawColumn : columns) {
                if (rawColumn == null) continue;
                Map<String, Object> column = objectMap(rawColumn);
                String name = stringValue(column.get("columnName"));
                if (name.isBlank()) name = stringValue(column.get("name"));
                if (name.isBlank()) continue;
                String type = (stringValue(column.get("dataType")) + " "
                        + stringValue(column.get("columnType")) + " "
                        + stringValue(column.get("typeName"))).toLowerCase(Locale.ROOT);
                if (type.contains("date") || type.contains("time") || type.contains("timestamp")) {
                    fields.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return fields;
    }

    private String queryIdentifier(String field) {
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) result.put(String.valueOf(k), v);
            });
            return result;
        }
        return MAPPER.convertValue(value, Map.class);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, String> buildLookupExtractProperties(Set<String> availableFields,
                                                             List<FieldMappingService.LookupPlan> lookups) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("Destination", "flowfile-attribute");
        props.put("Return Type", "scalar");
        props.put("Path Not Found Behavior", "skip");
        Set<String> fields = new LinkedHashSet<>(availableFields);
        for (FieldMappingService.LookupPlan lookup : lookups) {
            fields.addAll(lookup.parameterRecordFields());
        }
        for (String field : fields) {
            props.put(lookupAttr(field), jsonPath(field));
        }
        return props;
    }

    /**
     * Assign each DSL node to a stable, compact native-canvas slot. Adjacent
     * topological levels share a two-column band, and each following pair
     * folds underneath it in the opposite direction. A linear flow is thus
     * rendered as {@code left -> right, down, right -> left, down}, rather
     * than stretching one processor card across the canvas for every level.
     * Nodes in the same level retain separate vertically ordered lanes. The
     * editor's y/x positions are used only to retain the user's branch order.
     *
     * <p>Invalid cyclic graphs are kept deployable for backward compatibility:
     * the first unresolved node starts a new column and the remaining nodes
     * continue through the same deterministic traversal.</p>
     */
    static Map<String, NodeLayout> assignNodeLayouts(Pipeline.Dsl dsl) {
        if (dsl == null || dsl.nodes() == null || dsl.nodes().isEmpty()) {
            return Map.of();
        }

        Map<String, Pipeline.Node> nodesById = new LinkedHashMap<>();
        Map<String, Integer> declarationOrder = new LinkedHashMap<>();
        for (int index = 0; index < dsl.nodes().size(); index++) {
            Pipeline.Node node = dsl.nodes().get(index);
            if (node == null || node.id() == null || node.id().isBlank()) continue;
            nodesById.putIfAbsent(node.id(), node);
            declarationOrder.putIfAbsent(node.id(), index);
        }
        if (nodesById.isEmpty()) return Map.of();

        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> incomingCount = new LinkedHashMap<>();
        Map<String, Integer> levelByNode = new LinkedHashMap<>();
        for (String id : nodesById.keySet()) {
            outgoing.put(id, new ArrayList<>());
            incomingCount.put(id, 0);
            levelByNode.put(id, 0);
        }
        if (dsl.edges() != null) {
            for (Pipeline.Edge edge : dsl.edges()) {
                if (edge == null || !nodesById.containsKey(edge.source()) || !nodesById.containsKey(edge.target())) {
                    continue;
                }
                outgoing.get(edge.source()).add(edge.target());
                incomingCount.merge(edge.target(), 1, Integer::sum);
            }
        }

        Comparator<String> declarationComparator = Comparator.comparingInt(declarationOrder::get);
        PriorityQueue<String> ready = new PriorityQueue<>(declarationComparator);
        incomingCount.forEach((id, count) -> {
            if (count == 0) ready.add(id);
        });

        Set<String> placed = new LinkedHashSet<>();
        while (placed.size() < nodesById.size()) {
            if (ready.isEmpty()) {
                // Break a cycle deterministically. Its outgoing edges still
                // advance the dependent levels below, so the resulting canvas
                // remains readable instead of collapsing at one coordinate.
                nodesById.keySet().stream().filter(id -> !placed.contains(id))
                        .min(declarationComparator).ifPresent(ready::add);
            }
            String id = ready.remove();
            if (!placed.add(id)) continue;
            int level = levelByNode.get(id);
            for (String target : outgoing.getOrDefault(id, List.of())) {
                if (placed.contains(target)) continue;
                levelByNode.merge(target, level + 1, Math::max);
                int remaining = incomingCount.merge(target, -1, Integer::sum);
                if (remaining <= 0) ready.add(target);
            }
        }

        Map<Integer, List<Pipeline.Node>> nodesByLevel = new TreeMap<>();
        for (Pipeline.Node node : nodesById.values()) {
            nodesByLevel.computeIfAbsent(levelByNode.get(node.id()), ignored -> new ArrayList<>()).add(node);
        }
        Comparator<Pipeline.Node> laneOrder = Comparator
                .comparingDouble(Pipeline.Node::y)
                .thenComparingDouble(Pipeline.Node::x)
                .thenComparingInt(node -> declarationOrder.get(node.id()));
        Map<String, NodeLayout> result = new LinkedHashMap<>();
        int baseRow = 0;
        int highestLevel = nodesByLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int firstLevel = 0, band = 0; firstLevel <= highestLevel; firstLevel += 2, band++) {
            List<Pipeline.Node> firstNodes = new ArrayList<>(nodesByLevel.getOrDefault(firstLevel, List.of()));
            List<Pipeline.Node> secondNodes = new ArrayList<>(nodesByLevel.getOrDefault(firstLevel + 1, List.of()));
            firstNodes.sort(laneOrder);
            secondNodes.sort(laneOrder);

            // Fold alternate level pairs so a long linear pipeline remains a
            // compact zig-zag. This also keeps the direction of each connector
            // visually obvious in the native NiFi canvas.
            boolean leftToRight = band % 2 == 0;
            int firstColumn = leftToRight ? 0 : 1;
            int secondColumn = leftToRight ? 1 : 0;
            int firstHeight = placeLayoutBand(result, firstNodes, firstColumn, baseRow);
            int secondHeight = placeLayoutBand(result, secondNodes, secondColumn, baseRow);
            baseRow += Math.max(1, Math.max(firstHeight, secondHeight));
        }
        return result;
    }

    private static int placeLayoutBand(Map<String, NodeLayout> result,
                                       List<Pipeline.Node> nodes,
                                       int column,
                                       int baseRow) {
        int row = baseRow;
        for (Pipeline.Node node : nodes) {
            result.put(node.id(), new NodeLayout(column, row));
            row += nativeCanvasHeightUnits(node);
        }
        return row - baseRow;
    }

    /**
     * Reserve only the vertical space that a generated native sub-flow needs.
     * Ordinary materialization nodes stay one compact unit apart; file and
     * record-routing nodes expand locally because they create several native
     * processors beneath the same editor component.
     */
    private static int nativeCanvasHeightUnits(Pipeline.Node node) {
        if (node == null) return 1;
        String key = node.manifestKey() == null ? "" : node.manifestKey();
        return switch (key) {
            case "source.ftp", "source.sftp" -> 3;
            case "source.minio" -> 4;
            case "branch.conditions" -> 3;
            // A lookup mapping can emit a multi-stage native chain. Reserve
            // the largest standard block rather than allowing its lower
            // processors to collide with the following topology band.
            case "transform.field-mapping", "transform.field-enrichment" -> 6;
            default -> 1;
        };
    }

    private double nodeOriginX(NodeLayout layout) {
        return CANVAS_ORIGIN_X + layout.column() * NODE_COLUMN_WIDTH;
    }

    private double nodeOriginY(NodeLayout layout) {
        return CANVAS_ORIGIN_Y + layout.row() * NODE_ROW_HEIGHT;
    }

    private Map<String, String> buildLookupArgumentProperties(List<FieldMappingService.LookupPlan> lookups,
                                                              List<String> passThroughFields) {
        Map<String, String> props = new LinkedHashMap<>();
        Set<String> retainedFields = new LinkedHashSet<>(passThroughFields);
        int argIndex = 1;
        for (String field : passThroughFields) {
            props.put("sql.args." + argIndex + ".type", "12");
            props.put("sql.args." + argIndex + ".value", "${" + lookupAttr(field) + "}");
            argIndex++;
        }
        for (FieldMappingService.LookupPlan lookup : lookups) {
            for (String field : lookup.parameterRecordFields()) {
                props.put("sql.args." + argIndex + ".type", "12");
                props.put("sql.args." + argIndex + ".value", "${" + lookupAttr(field) + "}");
                argIndex++;
            }
        }
        return props;
    }

    private String buildLookupEnrichmentQuery(List<FieldMappingService.LookupPlan> lookups,
                                              List<String> passThroughFields) {
        FieldMappingService.LookupPlan dialectLookup = lookups.getFirst();
        List<String> projections = new ArrayList<>();
        for (String field : passThroughFields) {
            projections.add("NULLIF(?, '') AS " + jdbcAlias(field, dialectLookup));
        }
        for (int i = 0; i < lookups.size(); i++) {
            FieldMappingService.LookupPlan lookup = lookups.get(i);
            String alias = "lookup_result_" + i;
            projections.add(lookupResultColumnReference(alias, lookup, dialectLookup)
                    + " AS " + jdbcAlias(lookup.targetField(), dialectLookup));
        }
        StringBuilder query = new StringBuilder("SELECT ")
                .append(String.join(", ", projections))
                .append(" FROM (SELECT 1 AS ").append(jdbcAlias("__lookup_anchor", dialectLookup))
                .append(" FROM DUAL) lookup_anchor");
        for (int i = 0; i < lookups.size(); i++) {
            query.append(" LEFT JOIN (")
                    .append(lookups.get(i).parameterizedSql())
                    .append(") lookup_result_").append(i).append(" ON 1 = 1");
        }
        return query.toString();
    }

    /**
     * Build the single-lookup form with the dictionary datasource's identifier
     * rules. The production canvas groups lookups by connection and uses the
     * list overload above; retaining this focused form keeps one-lookup SQL
     * generation correct for callers that need the datasource-specific alias
     * dialect (notably MySQL's backticks for reserved aliases).
     */
    private String buildLookupEnrichmentQuery(FieldMappingService.LookupPlan lookup,
                                              List<String> passThroughFields) {
        List<String> projections = new ArrayList<>();
        for (String field : passThroughFields) {
            projections.add("NULLIF(?, '') AS " + jdbcAlias(field, lookup));
        }
        projections.add(lookupResultColumnReference("lookup_result", lookup, lookup)
                + " AS " + jdbcAlias(lookup.targetField(), lookup));
        return "SELECT " + String.join(", ", projections)
                + " FROM (SELECT 1 AS " + jdbcAlias("__lookup_anchor", lookup) + ") lookup_anchor"
                + " LEFT JOIN (" + lookup.parameterizedSql() + ") lookup_result ON 1 = 1";
    }

    private String lookupAttr(String field) {
        return "fm.lookup." + field.replaceAll("[^A-Za-z0-9_\\-.]", "_");
    }

    /**
     * A registration-generated dictionary query normally exposes a plain
     * alias, for example {@code SELECT DICT_NAME AS SCR_CYZJDM_cn ...}.  Oracle
     * (and DM in Oracle compatibility mode) fold that unquoted alias to upper
     * case.  Referencing it later as {@code "SCR_CYZJDM_cn"} is therefore a
     * different, nonexistent identifier and makes ExecuteSQLRecord fail with
     * ORA-00904 before the record can reach the target table.
     *
     * <p>Use an unquoted reference for ordinary generated field names so the
     * dictionary database applies its native folding rule.  The final output
     * alias remains dialect-quoted by {@link #jdbcAlias(String,
     * FieldMappingService.LookupPlan)}, which preserves the configured target
     * field name (including generated {@code _cn} fields) in the NiFi record.
     * Non-ordinary/reserved aliases retain the existing dialect quoting because
     * those rules may intentionally use a quoted identifier in custom SQL.</p>
     */
    private String lookupResultColumnReference(String tableAlias,
                                               FieldMappingService.LookupPlan lookup,
                                               FieldMappingService.LookupPlan dialectLookup) {
        String column = lookup.resultColumn();
        boolean plainIdentifier = column.matches("[A-Za-z_][A-Za-z0-9_]*");
        boolean reserved = JDBC_RESERVED_ALIASES.contains(column.toUpperCase(Locale.ROOT));
        if (plainIdentifier && !reserved) {
            return tableAlias + "." + column;
        }
        return tableAlias + "." + jdbcAlias(column, dialectLookup);
    }

    private String jsonPath(String field) {
        // SplitRecord emits one JSON object for each split FlowFile with the
        // JsonRecordSetWriter used by the field-enrichment chain. Treating
        // that object as an array ($[0]) makes EvaluateJsonPath resolve every
        // lookup argument to an empty value, so the downstream SQL and sink
        // receive nulls for otherwise present source fields.
        if (field.matches("[A-Za-z_][A-Za-z0-9_]*")) return "$." + field;
        return "$['" + field.replace("'", "\\'") + "']";
    }

    private String jdbcAlias(String field, FieldMappingService.LookupPlan lookup) {
        String databaseType = lookup.dataSource() == null ? "" : firstNonBlank(
                stringValue(lookup.dataSource().get("dbType")),
                stringValue(lookup.dataSource().get("databaseType")),
                stringValue(lookup.dataSource().get("database_type")));
        databaseType = databaseType == null ? "" : databaseType;
        // A missing dialect historically represents the Oracle/ANSI execution
        // path. Quote every projection there so Oracle preserves the field's
        // original case, including generated __lookup_* aliases.
        if (databaseType.isBlank() || databaseType.toLowerCase(Locale.ROOT).contains("oracle")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        boolean plainIdentifier = field.matches("[A-Za-z_][A-Za-z0-9_]*");
        boolean reserved = JDBC_RESERVED_ALIASES.contains(field.toUpperCase(Locale.ROOT));
        if (plainIdentifier && !reserved) return field;
        if (databaseType.toLowerCase(Locale.ROOT).contains("mysql")) {
            return "`" + field.replace("`", "``") + "`";
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    private void buildExternalSqlTransform(String pgId,
                                           Pipeline pipeline,
                                           Pipeline.Node node,
                                           NodeCompilation nc,
                                           NodeLayout nodeLayout,
                                           Map<String, Object> cfg,
                                           Map<String, String> sharedCs,
                                           Map<String, ComponentManifest> manifestByNode,
                                           Map<String, Map<String, Object>> cfgByNode,
                                           Map<String, NodeCompilation> compiled) {
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        Map<String, Object> sourceCfg = resolveUpstreamSourceConfig(pipeline, node.id(), manifestByNode, cfgByNode);
        String schedulingPeriod = stringConfig(sourceCfg, "schedulingPeriod");
        if (schedulingPeriod.isBlank()) {
            schedulingPeriod = "60 sec";
        }
        String schedulingStrategy = stringConfig(sourceCfg, "schedulingStrategy");
        if (schedulingStrategy.isBlank()) {
            schedulingStrategy = "TIMER_DRIVEN";
        }
        NifiEntity proc = nifi.createProcessor(pgId, EXECUTE_SQL_RECORD_TYPE,
                node.label() + "/query", x, y, Map.of(
                        "Database Connection Pooling Service", nc.csIds.get("sql-dbcp"),
                        "SQL Query", lastResultQuery(stringConfig(cfg, "_runtimeSourceSql").isBlank()
                                ? String.valueOf(cfg.get("sql")) : stringConfig(cfg, "_runtimeSourceSql")),
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Output Empty FlowFile", "false"
                ), schedulingPeriod, schedulingStrategy);
        nc.processorIds.put("query", proc.id());
        nc.inletProcessorId = proc.id();
        nc.outlets.put("default", new Outlet(proc.id(), "success"));
    }

    private String lastResultQuery(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            return "SELECT 1";
        }
        List<String> statements = splitSqlStatements(rawSql);
        for (int i = statements.size() - 1; i >= 0; i--) {
            String stmt = statements.get(i).trim();
            if (isResultQuery(stmt)) {
                return stmt;
            }
        }
        return rawSql.trim();
    }

    private List<String> preResultStatements(String rawSql) {
        List<String> statements = splitSqlStatements(rawSql == null ? "" : rawSql);
        int resultIndex = -1;
        for (int i = statements.size() - 1; i >= 0; i--) {
            if (isResultQuery(statements.get(i))) {
                resultIndex = i;
                break;
            }
        }
        if (resultIndex <= 0) {
            return List.of();
        }
        List<String> pre = new ArrayList<>();
        for (int i = 0; i < resultIndex; i++) {
            String stmt = statements.get(i).trim();
            if (!stmt.isBlank() && !isResultQuery(stmt)) {
                pre.add(stmt);
            }
        }
        return pre;
    }

    private void attachExternalSqlPreStatementsToDownstreamSinks(Pipeline pipeline,
                                                                 Map<String, ComponentManifest> manifestByNode,
                                                                 Map<String, Map<String, Object>> cfgByNode) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null) {
            return;
        }
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            ComponentManifest manifest = manifestByNode.get(node.id());
            Map<String, Object> cfg = cfgByNode.get(node.id());
            if (!isExternalTransformSqlNode(manifest, cfg)) {
                continue;
            }
            List<String> preStatements = preResultStatements(stringConfig(cfg, "sql"));
            if (preStatements.isEmpty()) {
                continue;
            }
            String sinkId = downstreamSinkNodeId(pipeline, node.id(), manifestByNode);
            if (sinkId == null) {
                continue;
            }
            Map<String, Object> sinkCfg = cfgByNode.get(sinkId);
            ComponentManifest sinkManifest = manifestByNode.get(sinkId);
            if (sinkCfg == null || sinkManifest == null || !"sink.jdbc".equals(sinkManifest.key())) {
                continue;
            }
            String existing = stringConfig(sinkCfg, "__preProcessingSql");
            String incoming = String.join(";\n", preStatements);
            sinkCfg.put("__preProcessingSql", existing.isBlank() ? incoming : existing + ";\n" + incoming);
        }
    }

    private void applyJdbcSinkPreProcessingSql(ComponentManifest manifest,
                                               String localId,
                                               Map<String, Object> cfg,
                                               Map<String, String> resolved) {
        if (!"put".equals(localId)) {
            return;
        }
        if (manifest != null && !"sink.jdbc".equals(manifest.key())) {
            return;
        }
        String preSql = stringConfig(cfg, "__preProcessingSql");
        if (!preSql.isBlank()) {
            resolved.put("Pre-Processing SQL", preSql);
        }
    }

    /**
     * Batch records immediately before JDBC persistence. These values mirror the
     * native-canvas MergeRecord baseline and are intentionally independent of a
     * target database vendor or PutDatabaseRecord implementation.
     */
    private static Map<String, String> jdbcBatchMergeProperties(Map<String, String> sharedCs) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Record Reader", sharedCs.get("jsonReader"));
        properties.put("Record Writer", sharedCs.get("jsonWriter"));
        properties.put("Merge Strategy", "Bin-Packing Algorithm");
        properties.put("Correlation Attribute Name", "filename");
        properties.put("Attribute Strategy", "Keep Only Common Attributes");
        properties.put("Minimum Number of Records", "1");
        properties.put("Maximum Number of Records", "10000");
        properties.put("Minimum Bin Size", "0 B");
        properties.put("Maximum Number of Bins", "10");
        return properties;
    }

    /**
     * LinewellPutDatabaseRecord declares the required properties by stable API
     * keys, while the native processor accepts the display names used in the
     * generic JDBC manifest. Sending display names to Linewell creates dynamic
     * properties with the same caption and leaves its real required properties
     * empty, so the processor can never become valid. Translate only for the
     * selected Linewell writer; the native component deliberately keeps its
     * existing manifest contract.
     */
    private void normalizeLinewellJdbcSinkProperties(ComponentManifest manifest,
                                                      String localId,
                                                      Map<String, Object> cfg,
                                                      Map<String, String> properties) {
        if ("sink.jdbc".equals(manifest.key()) && "put".equals(localId)
                && !usesLinewellJdbcWriter(manifest, localId, cfg)) {
            normalizeNativeJdbcTableProperties(properties);
        }
        if (!usesLinewellJdbcWriter(manifest, localId, cfg) || properties == null) {
            return;
        }
        moveProcessorProperty(properties, "Record Reader", "put-db-record-record-reader");
        moveProcessorProperty(properties, "Database Connection Pooling Service", "put-db-record-dcbp-service");
        moveProcessorProperty(properties, "Database Type", "db-type");
        moveProcessorProperty(properties, "Statement Type", "put-db-record-statement-type");
        moveProcessorProperty(properties, "Update Keys", "put-db-record-update-keys");
        moveProcessorProperty(properties, "Table Name", "put-db-record-table-name");
        moveProcessorProperty(properties, "Unmatched Field Behavior", "put-db-record-unmatched-field-behavior");
        moveProcessorProperty(properties, "Unmatched Column Behavior", "put-db-record-unmatched-column-behavior");

        // LinewellPutDatabaseRecord has a MERGE-specific matching property in
        // addition to the inherited Update Keys property.  The compiler has
        // already normalized updateKeys from the probed target table (and thus
        // fills its business primary key for generated tasks), so copy that
        // canonical value to the custom processor's actual property key.
        // Without this, the native card displays an empty “merge 方式更新依据字段”
        // and Linewell MERGE cannot reliably match existing target records.
        String updateKeys = properties.get("put-db-record-update-keys");
        if (updateKeys != null && !updateKeys.isBlank()) {
            properties.put("merge update keys", updateKeys);
        }
    }

    /** Native PutDatabaseRecord performs separate JDBC schema/table lookups. */
    static void normalizeNativeJdbcTableProperties(Map<String, String> properties) {
        if (properties == null) return;
        String table = properties.get("Table Name");
        if (table == null) return;
        String[] parts = table.split("\\.", -1);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            properties.put("Schema Name", parts[0]);
            properties.put("Table Name", parts[1]);
        }
    }

    private static void moveProcessorProperty(Map<String, String> properties, String displayName, String apiName) {
        String value = properties.remove(displayName);
        if (value != null) {
            properties.put(apiName, value);
        }
    }

    private boolean usesLinewellJdbcWriter(ComponentManifest manifest, String localId, Map<String, Object> cfg) {
        if (manifest == null || !"sink.jdbc".equals(manifest.key()) || !"put".equals(localId)) {
            return false;
        }
        return LINEWELL_DATABASE_RECORD_MODE.equals(effectiveJdbcWriterType(cfg));
    }

    private boolean isResultQuery(String sql) {
        String lower = sql == null ? "" : sql.stripLeading().toLowerCase(Locale.ROOT);
        return lower.matches("^(select|with)\\b[\\s\\S]*");
    }

    private String downstreamSinkNodeId(Pipeline pipeline,
                                        String nodeId,
                                        Map<String, ComponentManifest> manifestByNode) {
        if (pipeline.dsl() == null || pipeline.dsl().edges() == null) {
            return null;
        }
        for (Pipeline.Edge edge : pipeline.dsl().edges()) {
            if (!nodeId.equals(edge.source())) {
                continue;
            }
            ComponentManifest targetManifest = manifestByNode.get(edge.target());
            if (targetManifest != null && "sink".equals(targetManifest.category())) {
                return edge.target();
            }
        }
        return null;
    }

    private List<String> splitSqlStatements(String rawSql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < rawSql.length(); i++) {
            char ch = rawSql.charAt(i);
            char next = i + 1 < rawSql.length() ? rawSql.charAt(i + 1) : 0;
            if (quote != 0) {
                current.append(ch);
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '-' && next == '-') {
                int end = rawSql.indexOf('\n', i + 2);
                if (end < 0) {
                    current.append(rawSql.substring(i));
                    break;
                }
                current.append(rawSql, i, end + 1);
                i = end;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
                continue;
            }
            if (ch == ';') {
                String stmt = current.toString().trim();
                if (!stmt.isBlank()) statements.add(stmt);
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isBlank()) statements.add(tail);
        return statements;
    }

    private void buildFlowFileSqlTransform(String pgId,
                                           Pipeline.Node node,
                                           NodeCompilation nc,
                                           NodeLayout nodeLayout,
                                           Map<String, Object> cfg,
                                           Map<String, String> sharedCs,
                                           int mergeInputCount) {
        double x = nodeOriginX(nodeLayout);
        double y = nodeOriginY(nodeLayout);
        String queryInputProcessorId;
        boolean mergeInputs = mergeInputCount > 1;
        String recordReader = sharedCs.get("jsonReader");
        String mergeSchemaText = stringConfig(cfg, "mergeSchemaText");
        if (!mergeSchemaText.isBlank()) {
            NifiEntity reader = nifi.createControllerService(pgId, JSON_READER_TYPE,
                    node.label() + "/merge-json-reader", Map.of(
                            "Schema Access Strategy", "schema-text-property",
                            "Schema Text", mergeSchemaText
                    ));
            recordReader = reader.id();
            nifi.setControllerServiceRunStatus(reader.id(), "ENABLED");
            nifi.waitForControllerServiceEnabled(reader.id(), 30_000L);
            nc.csIds.put("merge-json-reader", reader.id());
        }
        if (mergeInputs) {
            String minRecords = stringConfig(cfg, "mergeMinimumRecords");
            if (minRecords.isBlank()) {
                minRecords = "2147483647";
            }
            String maxBinAge = stringConfig(cfg, "mergeMaxBinAge");
            if (maxBinAge.isBlank()) {
                maxBinAge = "30 sec";
            }
            NifiEntity merge = nifi.createProcessor(pgId, MERGE_RECORD_TYPE,
                    node.label() + "/merge-inputs", x, y, Map.of(
                            "Record Reader", recordReader,
                            "Record Writer", sharedCs.get("jsonWriter"),
                            "Minimum Number of Records", minRecords,
                            "Maximum Number of Records", minRecords,
                            "Max Bin Age", maxBinAge
                    ), null, null);
            nc.processorIds.put("merge-inputs", merge.id());
            nc.inletProcessorId = merge.id();
            queryInputProcessorId = merge.id();
            y += 140;
        } else {
            queryInputProcessorId = null;
        }

        NifiEntity query = nifi.createProcessor(pgId, "org.apache.nifi.processors.standard.QueryRecord",
                node.label() + "/query", x, y, Map.of(
                        "Record Reader", recordReader,
                        "Record Writer", sharedCs.get("jsonWriter"),
                        "Include Zero Record FlowFiles", "false",
                        "result", String.valueOf(cfg.getOrDefault("sql", "SELECT * FROM FLOWFILE"))
                ), null, null);
        nc.processorIds.put("query", query.id());
        if (mergeInputs) {
            nifi.createConnection(pgId, queryInputProcessorId, "PROCESSOR", query.id(), "PROCESSOR", List.of("merged"));
            nc.usedOutgoing.computeIfAbsent(queryInputProcessorId, k -> new LinkedHashSet<>()).add("merged");
        } else {
            nc.inletProcessorId = query.id();
        }
        nc.outlets.put("default", new Outlet(query.id(), "result"));
    }

    private boolean isMultiInputFlowFileSqlNode(Pipeline pipeline,
                                                String nodeId,
                                                ComponentManifest manifest,
                                                Map<String, Object> cfg) {
        if (manifest == null || !"transform.sql".equals(manifest.key())) {
            return false;
        }
        if (isExternalTransformSqlNode(manifest, cfg)) {
            return false;
        }
        return incomingEdgeCount(pipeline, nodeId) > 1;
    }

    private int incomingEdgeCount(Pipeline pipeline, String nodeId) {
        if (pipeline.dsl() == null || pipeline.dsl().edges() == null) {
            return 0;
        }
        int count = 0;
        for (Pipeline.Edge edge : pipeline.dsl().edges()) {
            if (nodeId.equals(edge.target())) {
                count++;
            }
        }
        return count;
    }

    private boolean isExternalTransformSqlNode(ComponentManifest manifest, Map<String, Object> cfg) {
        if (isSourceDbFieldEnrichmentNode(manifest, cfg)) {
            return true;
        }
        if (manifest == null || !"transform.sql".equals(manifest.key())) {
            return false;
        }
        String executionMode = transformSqlExecutionMode(cfg);
        if ("SOURCE_DB".equals(executionMode)) {
            return true;
        }
        if ("FLOWFILE".equals(executionMode)) {
            return false;
        }
        String sql = cfg == null ? "" : String.valueOf(cfg.getOrDefault("sql", ""));
        return !FLOWFILE_REF.matcher(sql).find();
    }

    private String transformSqlExecutionMode(Map<String, Object> cfg) {
        String raw = cfg == null ? null : stringConfig(cfg, "executionMode");
        if (raw == null || raw.isBlank()) {
            return "AUTO";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SOURCE_DB", "SOURCE", "DATABASE", "DB" -> "SOURCE_DB";
            case "FLOWFILE", "STREAM", "RECORD" -> "FLOWFILE";
            default -> "AUTO";
        };
    }

    private boolean isFieldMappingTransform(ComponentManifest manifest) {
        return manifest != null && ("transform.field-mapping".equals(manifest.key())
                || "transform.field-enrichment".equals(manifest.key()));
    }

    private boolean isFieldEnrichmentTransform(ComponentManifest manifest) {
        return manifest != null && "transform.field-enrichment".equals(manifest.key());
    }

    private boolean sourceDbPushdownRequested(Map<String, Object> cfg) {
        String mode = stringConfig(cfg, "executionMode").toUpperCase(Locale.ROOT);
        return mode.isBlank() || "AUTO".equals(mode) || "SOURCE_DB".equals(mode);
    }

    private boolean isSourceDbFieldEnrichmentNode(ComponentManifest manifest, Map<String, Object> cfg) {
        return isFieldEnrichmentTransform(manifest) && !stringConfig(cfg, "_runtimeSourceSql").isBlank();
    }

    private boolean canPushFieldEnrichmentToSource(FieldMappingService.CompiledMapping plan,
                                                    Map<String, Object> sourceCfg) {
        if (plan == null || sourceCfg == null || sourceCfg.isEmpty()) return false;
        // ExecuteSQLRecord runs the generated statement as one whole-table query.  Do not
        // replace an incremental GenerateTableFetch source with it: doing so would lose
        // the source watermark/pagination semantics.  Incremental sources retain the
        // existing record-mode mapping path until a query-aware incremental compiler is
        // introduced.
        String syncMode = stringConfig(sourceCfg, "syncMode");
        if (!"FULL".equalsIgnoreCase(syncMode)) return false;
        return plan.lookups().stream().allMatch(lookup -> {
            if ("FAIL".equalsIgnoreCase(lookup.onMissing())) return false;
            return sameJdbcSource(sourceCfg, lookup.dataSource());
        });
    }

    private boolean sameJdbcSource(Map<String, Object> sourceCfg, Map<String, Object> lookupCfg) {
        if (lookupCfg == null || lookupCfg.isEmpty()) return true;
        String sourceId = firstNonBlankStatic(configText(sourceCfg, "datasourceId"), configText(sourceCfg, "sourceDbId"),
                configText(sourceCfg, "dbId"), configText(sourceCfg, "id"));
        String lookupId = firstNonBlankStatic(configText(lookupCfg, "datasourceId"), configText(lookupCfg, "sourceDbId"),
                configText(lookupCfg, "dbId"), configText(lookupCfg, "id"));
        if (!sourceId.isBlank() && !lookupId.isBlank()) return sourceId.equals(lookupId);
        String sourceUrl = buildSourceJdbcUrl(sourceCfg);
        String lookupUrl = buildSourceJdbcUrl(lookupCfg);
        return !sourceUrl.isBlank() && sourceUrl.equalsIgnoreCase(lookupUrl);
    }

    private String sourceSqlTable(Map<String, Object> sourceCfg) {
        String table = firstNonBlankStatic(configText(sourceCfg, "table"), configText(sourceCfg, "sourceTableName"));
        if (table.isBlank()) throw new IllegalStateException("字段增强来源 SQL 下推需要来源表名");
        String[] parts = table.replace("`", "").replace("\"", "").split("\\.");
        List<String> identifiers = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
                throw new IllegalStateException("来源表名不支持来源 SQL 下推: " + table);
            }
            identifiers.add(sourceSqlIdentifier(value, sourceCfg));
        }
        return String.join(".", identifiers);
    }

    private String sourceSqlIdentifier(String identifier, Map<String, Object> sourceCfg) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("非法 SQL 标识符: " + identifier);
        }
        String dbType = stringConfig(sourceCfg, "dbType").toUpperCase(Locale.ROOT);
        if (dbType.contains("MYSQL") || dbType.contains("MARIADB")) {
            return "`" + identifier + "`";
        }
        return "\"" + identifier + "\"";
    }

    private Set<String> resolveSourceConfigOnlyNodes(Pipeline pipeline,
                                                     Map<String, ComponentManifest> manifestByNode,
                                                     Map<String, Map<String, Object>> cfgByNode) {
        Set<String> ids = new HashSet<>();
        if (pipeline.dsl() == null || pipeline.dsl().edges() == null) {
            return ids;
        }
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            ComponentManifest sourceManifest = manifestByNode.get(node.id());
            if (sourceManifest == null || !"source".equals(sourceManifest.category())) {
                continue;
            }
            List<Pipeline.Edge> outgoing = pipeline.dsl().edges().stream()
                    .filter(edge -> node.id().equals(edge.source()))
                    .toList();
            if (outgoing.isEmpty()) {
                continue;
            }
            boolean configOnly = outgoing.stream().allMatch(edge ->
                    isExternalTransformSqlNode(manifestByNode.get(edge.target()), cfgByNode.get(edge.target())));
            if (configOnly) {
                ids.add(node.id());
            }
        }
        return ids;
    }

    private boolean isSourceConfigOnlyEdge(Pipeline.Edge edge,
                                           Map<String, ComponentManifest> manifestByNode,
                                           Map<String, Map<String, Object>> cfgByNode) {
        ComponentManifest sourceManifest = manifestByNode.get(edge.source());
        ComponentManifest targetManifest = manifestByNode.get(edge.target());
        return sourceManifest != null
                && "source".equals(sourceManifest.category())
                && isExternalTransformSqlNode(targetManifest, cfgByNode.get(edge.target()));
    }

    /**
     * ExcelReader uses the first row as Record field names.  Data-report
     * templates deliberately expose the registered Chinese labels to users,
     * whereas generated field mappings target the physical English columns.
     * Convert only CSV and workbook mapping inputs to those labels before the
     * QueryRecord SQL is generated. JSON keys are part of the data contract
     * and must remain physical column names.
     */
    private boolean usesRegisteredChineseHeaders(Map<String, Object> sourceCfg) {
        if (sourceCfg == null) return false;
        String protocol = configText(sourceCfg, "protocol");
        if (!"ftp".equalsIgnoreCase(protocol) && !"sftp".equalsIgnoreCase(protocol)) return false;
        // Data-report uploads are generated from the registration template.  Its
        // first row contains the registered Chinese display labels, while the
        // mapping DSL necessarily uses physical column names.  Keep this an
        // explicit source contract instead of guessing from a file name so the
        // runtime can handle the same rule for CSV and workbook uploads.
        if ("registered-chinese".equalsIgnoreCase(configText(sourceCfg, "fileHeaderMode"))) {
            return true;
        }
        String fileFormat = configText(sourceCfg, "fileFormat");
        return "csv".equalsIgnoreCase(fileFormat)
                || "xls".equalsIgnoreCase(fileFormat)
                || "xlsx".equalsIgnoreCase(fileFormat);
    }

    private String mapFileHeaderFieldsToRegisteredNames(Object rawMappings, Map<String, Object> sourceCfg) {
        if (rawMappings == null) return null;
        Map<String, String> headerNames = registeredHeaderNames(sourceCfg.get("sourceColumns"));
        if (headerNames.isEmpty()) return stringValue(rawMappings);
        try {
            JsonNode parsed = rawMappings instanceof String text ? MAPPER.readTree(text) : MAPPER.valueToTree(rawMappings);
            if (parsed == null || !parsed.isObject()) return stringValue(rawMappings);
            com.fasterxml.jackson.databind.node.ObjectNode mappingSpec = ((com.fasterxml.jackson.databind.node.ObjectNode) parsed).deepCopy();
            JsonNode mappings = mappingSpec.path("mappings");
            if (mappings.isArray()) {
                for (JsonNode mapping : mappings) {
                    if (!(mapping instanceof com.fasterxml.jackson.databind.node.ObjectNode object) || !object.hasNonNull("from")) continue;
                    String from = object.path("from").asText("");
                    if (!from.startsWith("/")) continue;
                    String registeredName = from.substring(1);
                    String headerName = headerNames.get(registeredName.toLowerCase(Locale.ROOT));
                    if (headerName != null && !headerName.isBlank()) {
                        object.put("from", "/" + headerName);
                    }
                }
            }
            return MAPPER.writeValueAsString(mappingSpec);
        } catch (Exception exception) {
            throw new IllegalStateException("无法按登记字段转换文件表头映射：" + exception.getMessage(), exception);
        }
    }

    private Map<String, String> registeredHeaderNames(Object rawColumns) {
        Map<String, String> names = new LinkedHashMap<>();
        if (!(rawColumns instanceof Iterable<?> columns)) return names;
        for (Object rawColumn : columns) {
            Map<String, Object> column = columnValues(rawColumn);
            String physicalName = firstNonBlank(
                    stringValue(column.get("columnName")), stringValue(column.get("column_name")),
                    stringValue(column.get("fieldName")), stringValue(column.get("field_name")),
                    stringValue(column.get("name")));
            String displayName = firstNonBlank(
                    stringValue(column.get("columnComment")), stringValue(column.get("column_comment")),
                    stringValue(column.get("fieldComment")), stringValue(column.get("field_comment")),
                    stringValue(column.get("displayName")), stringValue(column.get("display_name")));
            if (physicalName != null && displayName != null) {
                names.put(physicalName.toLowerCase(Locale.ROOT), displayName);
            }
        }
        return names;
    }

    private Map<String, Object> columnValues(Object rawColumn) {
        if (!(rawColumn instanceof Map<?, ?> rawMap)) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) values.put(String.valueOf(key), value);
        });
        return values;
    }

    private Map<String, Object> resolveUpstreamSourceConfig(Pipeline pipeline,
                                                            String nodeId,
                                                            Map<String, ComponentManifest> manifestByNode,
                                                            Map<String, Map<String, Object>> cfgByNode) {
        if (pipeline.dsl() == null || pipeline.dsl().edges() == null) {
            throw new IllegalStateException("lookup/sql transform requires upstream source node");
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (Pipeline.Edge edge : pipeline.dsl().edges()) {
            if (nodeId.equals(edge.target())) {
                queue.add(edge.source());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            ComponentManifest manifest = manifestByNode.get(current);
            if (manifest != null && "source".equals(manifest.category())) {
                return cfgByNode.get(current);
            }
            for (Pipeline.Edge edge : pipeline.dsl().edges()) {
                if (current.equals(edge.target())) {
                    queue.add(edge.source());
                }
            }
        }
        throw new IllegalStateException("lookup/sql transform requires upstream source node");
    }

    private Optional<String> inferDbTypeFromSourceManifest(String nodeManifestKey, String manifestKey) {
        String key = firstNonBlank(nodeManifestKey, manifestKey);
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("source.")) {
            return Optional.empty();
        }
        String suffix = normalized.substring("source.".length()).replace('-', '_');
        if (suffix.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(switch (suffix) {
            case "dameng", "dm" -> "DM";
            case "postgres", "postgresql" -> "POSTGRESQL";
            case "tdsql_pg" -> "TDSQL_PG";
            case "tdsql_mysql" -> "TDSQL_MYSQL";
            case "sqlserver", "mssql", "ms_sql" -> "SQLSERVER";
            default -> suffix.toUpperCase(Locale.ROOT);
        });
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> buildSourceDbcpProperties(Map<String, Object> sourceCfg) {
        Map<String, String> props = new LinkedHashMap<>();
        JdbcDriverSpec driver = jdbcDriverSpec(sourceCfg);
        props.put("Database Connection URL", buildSourceJdbcUrl(sourceCfg));
        props.put("Database Driver Class Name", driver.driverClass());
        props.put("Database Driver Locations", requiredJdbcDriverLocation(sourceCfg, driver.driverLocations()));
        props.put("Database User", String.valueOf(sourceCfg.getOrDefault("username", "")));
        props.put("Password", String.valueOf(sourceCfg.getOrDefault("password", "")));
        appendJdbcDriverProperties("org.apache.nifi.dbcp.DBCPConnectionPool", sourceCfg, props);
        return props;
    }

    static void appendJdbcDriverProperties(String serviceType, Map<String, Object> config,
                                           Map<String, String> properties) {
        if (!"org.apache.nifi.dbcp.DBCPConnectionPool".equals(serviceType) || properties == null) {
            return;
        }
        JdbcDriverPropertyResolver.resolve(config).forEach(properties::putIfAbsent);
    }

    private String buildSourceJdbcUrl(Map<String, Object> sourceCfg) {
        String dbType = String.valueOf(sourceCfg.getOrDefault("dbType", "MYSQL")).trim().toUpperCase(Locale.ROOT);
        Object jdbcUrl = sourceCfg.get("jdbcUrl");
        if (jdbcUrl != null && !String.valueOf(jdbcUrl).isBlank()) {
            return normalizeJdbcUrlForDbType(String.valueOf(jdbcUrl), dbType);
        }
        String host = String.valueOf(sourceCfg.getOrDefault("host", ""));
        String port = String.valueOf(sourceCfg.getOrDefault("port", defaultSourcePort(dbType)));
        if (port.isBlank() || "null".equalsIgnoreCase(port)) {
            port = defaultSourcePort(dbType);
        }
        String database = String.valueOf(sourceCfg.getOrDefault("database", ""));
        if ("null".equalsIgnoreCase(database)) {
            database = "";
        }
        return switch (dbType) {
            case "DM", "DAMENG" -> "jdbc:dm://" + host + ":" + port + "/" + database;
            case "POSTGRESQL", "TDSQL_PG" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "GAUSSDB", "OPENGAUSS" -> buildGaussJdbcUrl(host, port, database, sourceCfg);
            case "ORACLE" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
            case "HIVE" -> buildHiveJdbcUrl(sourceCfg);
            case "HETU", "TRINO", "PRESTO" -> "jdbc:trino://" + host + ":" + port + "/" + (database.isBlank() ? "hive/default" : database);
            case "DORIS", "STARROCKS" -> normalizeJdbcUrlForDbType("jdbc:mysql://" + host + ":" + port + "/" + database, dbType);
            case "CLICKHOUSE" -> "jdbc:clickhouse://" + host + ":" + port + "/" + database + "?compress=1";
            case "IOTDB" -> "jdbc:iotdb://" + host + ":" + port + "/";
            case "OCEANBASE" -> "jdbc:oceanbase://" + host + ":" + port + "/" + database
                    + "?characterEncoding=utf8";
            case "OCEANBASE_ORACLE", "OCEANBASEORACLE" -> "jdbc:oceanbase:oracle://" + host + ":" + port + "/" + database;
            case "OCEANBASE_MYSQL", "OCEANBASEMYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database;
            default -> normalizeJdbcUrlForDbType("jdbc:mysql://" + host + ":" + port + "/" + database, dbType);
        };
    }

    private String defaultSourcePort(String dbType) {
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HIVE" -> "10000";
            case "HETU", "TRINO", "PRESTO" -> "29861";
            case "DORIS", "STARROCKS" -> "9030";
            case "CLICKHOUSE" -> "8123";
            case "IOTDB" -> "22260";
            case "POSTGRESQL", "GAUSSDB", "OPENGAUSS" -> "5432";
            default -> "3306";
        };
    }

    /** Builds the PostgreSQL-compatible GaussDB URL used by both source and lookup DBCP services. */
    private String buildGaussJdbcUrl(String host, String port, String database, Map<String, Object> config) {
        String schema = String.valueOf(config.getOrDefault("currentSchema", "public")).trim();
        String sslMode = String.valueOf(config.getOrDefault("sslMode", "disable")).trim();
        List<String> parameters = new ArrayList<>();
        if (!schema.isBlank() && !"null".equalsIgnoreCase(schema)) {
            parameters.add("currentSchema=" + schema);
        }
        if (!sslMode.isBlank() && !"null".equalsIgnoreCase(sslMode)) {
            parameters.add("sslmode=" + sslMode);
        }
        parameters.add("ApplicationName=data-elements");
        parameters.add("prepareThreshold=0");
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?" + String.join("&", parameters);
    }

    // ---------------------------------------------------------------------
    // 模板变量解析
    // ---------------------------------------------------------------------

    /**
     * 批量解析属性模板（如 ${config.xxx} / ${cs.xxx} / ${jdbc.xxx}）。
     */
    /**
     * DBCP and Kerberos services are infrastructure resources. When the
     * hierarchy provides an application parent, keep one deterministic service
     * there and let child task groups reference it. Other services stay in the
     * task group because their schema or behavior is part of that flow.
     */
    private NifiEntity createOrReuseControllerService(String taskGroupId, String applicationScopeId,
                                                       String type, String taskScopedName,
                                                       Map<String, String> properties) {
        if (applicationScopeId == null || applicationScopeId.isBlank() || !isApplicationShareable(type)) {
            return nifi.createControllerService(taskGroupId, type, taskScopedName, properties);
        }
        String identity = controllerServiceFingerprint(type, properties);
        String name = sharedServiceName(type, identity);
        String lockKey = applicationScopeId + ':' + identity;
        Object lock = sharedServiceLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            for (JsonNode existing : nifi.listControllerServices(applicationScopeId)) {
                JsonNode component = existing.path("component");
                if (!type.equals(component.path("type").asText())
                        || !name.equals(component.path("name").asText())) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> reused = MAPPER.convertValue(component, Map.class);
                log.info("Reusing application Controller Service {} ({}) in scope {}", name,
                        component.path("id").asText(), applicationScopeId);
                return new NifiEntity(null, reused, null, null);
            }
            log.info("Creating application Controller Service {} in scope {}", name, applicationScopeId);
            return nifi.createControllerService(applicationScopeId, type, name, properties);
        }
    }

    private static boolean isApplicationShareable(String type) {
        return "org.apache.nifi.dbcp.DBCPConnectionPool".equals(type)
                || "org.apache.nifi.kerberos.KerberosKeytabUserService".equals(type);
    }

    /** A short, non-reversible identifier: the service name never exposes credentials. */
    private static String controllerServiceFingerprint(String type, Map<String, String> properties) {
        StringBuilder canonical = new StringBuilder(type == null ? "" : type).append('\n');
        if (properties != null) {
            properties.keySet().stream().sorted().forEach(key -> canonical.append(key).append('=')
                    .append(properties.getOrDefault(key, "")).append('\n'));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) out.append(String.format("%02x", digest[i]));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String sharedServiceName(String type, String fingerprint) {
        String category = type.contains("Kerberos") ? "Kerberos" : "数据库连接";
        return "共享/" + category + '/' + fingerprint;
    }

    private Map<String, String> resolveProps(Map<String, String> template,
                                             Map<String, Object> cfg,
                                             Map<String, String> localCs,
                                             Map<String, String> sharedCs) {
        if (template == null || template.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        template.forEach((k, v) -> {
            String resolved = resolveString(v, cfg, localCs, sharedCs);
            if (resolved != null && !resolved.isEmpty()) {
                out.put(k, resolved);
            }
        });
        applyNodeJdbcDriverLocation(cfg, out);
        return out;
    }

    /**
     * A node-specific setting must replace a manifest's development-machine path, but it must
     * remain a concrete JAR path.  Previously this method replaced a correctly resolved value
     * such as {@code /opt/nifi/lib/jdbc/oceanbase-client-2.4.17.jar} with just
     * {@code /opt/nifi/lib/jdbc/}.  Whether a DBCP service can discover a directory is dependent
     * on the native NiFi/Leefy version and class-loader configuration, so deployments must never
     * rely on that behaviour.
     */
    private void applyNodeJdbcDriverLocation(Map<String, Object> cfg, Map<String, String> properties) {
        if (properties == null || !properties.containsKey("Database Driver Locations")) {
            return;
        }
        String configuredLocation = jdbcDriverSpec(cfg).driverLocations();
        if (configuredLocation == null || configuredLocation.isBlank()) {
            // Focused compiler tests and explicitly embedded legacy manifests may not have a
            // runtime-node override. Keep a manifest value only when it already names a JAR.
            requiredJdbcDriverLocation(cfg, properties.get("Database Driver Locations"));
            return;
        }
        properties.put("Database Driver Locations", requiredJdbcDriverLocation(cfg, configuredLocation));
    }

    private String requiredJdbcDriverLocation(Map<String, Object> cfg, String location) {
        String resolved = location == null ? "" : location.trim();
        String dbType = String.valueOf(cfg.getOrDefault("dbType", "MYSQL")).trim().toUpperCase(Locale.ROOT);
        // Isolated compiler tests intentionally have no selected runtime node and therefore no
        // portable container path to validate. Real deployments always resolve a runtime node.
        if (resolved.isBlank() && nifiNodeRuntimeResolver == null) return resolved;
        // Hive is the intentional exception: its JDBC client needs several transitive JARs and
        // is loaded from a dedicated directory by the NiFi startup configuration.
        if ("HIVE".equals(dbType)) return resolved;
        if (resolved.matches("(?i).*\\.jar(?:\\s*,\\s*.*\\.jar)*")) return resolved;
        String examplePath = resolveNodeJdbcDriverLocation(dbType, "/opt/nifi/nifi-current/lib/jdbc");
        throw new IllegalStateException("NiFi JDBC 驱动必须配置为容器内可访问的完整 JAR 文件路径，不能只填写目录。"
                + "当前画布节点的数据库类型为 " + dbType + "；请在【节点管理】为该类型配置例如 "
                + dbType + '=' + examplePath);
    }

    /**
     * Resolves the runtime extension selected in the canvas rather than relying
     * on a Java package recorded by the frontend. Product-level names stay
     * stable while the deployed Leafy/NiFi NAR can use its own package name.
     */
    private String resolveProcessorType(ComponentManifest manifest, String localId, String declaredType,
                                        Map<String, Object> cfg) {
        if (manifest == null || !"put".equals(localId)) {
            return declaredType;
        }
        if ("sink.jdbc".equals(manifest.key())) {
            String writerType = effectiveJdbcWriterType(cfg);
            if (LINEWELL_DATABASE_RECORD_MODE.equals(writerType)) {
                return nifi.findProcessorType(LINEWELL_DATABASE_RECORD_WRITER)
                        .orElseThrow(() -> new IllegalStateException(
                                "当前 Leafy/NiFi 节点未安装 " + LINEWELL_DATABASE_RECORD_WRITER
                                        + "，请安装 datadevs-database-nar，或在画布中改选“NiFi 原生 PutDatabaseRecord”。"));
            }
            if (NATIVE_DATABASE_RECORD_MODE.equals(writerType)) {
                return declaredType;
            }
            throw new IllegalStateException("不支持的 JDBC 目标表写入组件：" + writerType);
        }
        if (!"sink.hive".equals(manifest.key())) {
            return declaredType;
        }
        String mode = stringConfig(cfg, "hiveWriteMode").toUpperCase(Locale.ROOT);
        if (mode.isBlank()) {
            mode = HIVE_LINEWELL_HDFS_MODE;
        }
        if (HIVE_STANDARD_WRITE_MODE.equals(mode) || "STANDARD".equals(mode)) {
            return HIVE_RECORD_PUT_TYPE;
        }
        if (HIVE_LINEWELL_HDFS_MODE.equals(mode) || "LINEWELL".equals(mode)) {
            return nifi.findProcessorType(HIVE_LINEWELL_HDFS_WRITER)
                    .orElseThrow(() -> new IllegalStateException(
                            "当前 Leafy/NiFi 节点未安装 " + HIVE_LINEWELL_HDFS_WRITER
                                    + "，请安装 Linewell 华为 Hive 写入 NAR，或改选“NiFi 原生 PutHDFS”。"));
        }
        if (HIVE_BATCH_LAKE_MODE.equals(mode) || "BATCH".equals(mode)) {
            return nifi.findProcessorType(HIVE_BATCH_LAKE_WRITER)
                    .orElseThrow(() -> new IllegalStateException(
                            "当前 NiFi 节点未安装 " + HIVE_BATCH_LAKE_WRITER
                                    + "，请安装 NiFi Hadoop NAR，或改选其他 Hive 写入组件。"));
        }
        throw new IllegalStateException("不支持的 Hive 写入方式：" + mode);
    }

    private boolean isHiveBatchPutHdfs(ComponentManifest manifest, String localId,
                                       Map<String, Object> cfg) {
        if (manifest == null || !"sink.hive".equals(manifest.key()) || !"put".equals(localId)) {
            return false;
        }
        String mode = stringConfig(cfg, "hiveWriteMode").toUpperCase(Locale.ROOT);
        return mode.isBlank() || HIVE_LINEWELL_HDFS_MODE.equals(mode) || "LINEWELL".equals(mode)
                || HIVE_BATCH_LAKE_MODE.equals(mode) || "BATCH".equals(mode);
    }

    /**
     * Builds the shared PutHDFS/PutHwHDFS configuration for batch Hive writes.
     * Unlike HiveRecordPut, these processors write bytes to the physical table location
     * and does not speak to HiveServer2, so Hive database/table JDBC properties
     * must never be carried across to this processor.
     */
    private static Map<String, String> buildHiveBatchPutHdfsProperties(Map<String, Object> cfg) {
        String clientConfigDir = configText(cfg, "clientConfigDir");
        if (clientConfigDir.isBlank()) {
            throw new IllegalStateException("批量入湖（PutHDFS）缺少 MRS 客户端配置目录");
        }
        String directory = firstNonBlankStatic(configText(cfg, "hdfsDirectory"),
                defaultHiveWarehouseDirectory(configText(cfg, "database"), configText(cfg, "table")));
        if (directory.isBlank()) {
            throw new IllegalStateException("批量入湖（PutHDFS）缺少目标 Hive 表或 HDFS 写入目录");
        }

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Hadoop Configuration Resources", joinHadoopConfigResources(clientConfigDir));
        properties.put("Directory", directory);
        String principal = firstNonBlankStatic(configText(cfg, "userPrincipal"),
                configText(cfg, "kerberosPrincipal"), configText(cfg, "clientPrincipal"));
        String keytab = firstNonBlankStatic(configText(cfg, "keytabPath"), configText(cfg, "keytab"));
        if (!principal.isBlank()) properties.put("Kerberos Principal", principal);
        if (!keytab.isBlank()) properties.put("Kerberos Keytab", keytab);
        return properties;
    }

    private static String joinHadoopConfigResources(String clientConfigDir) {
        String normalized = clientConfigDir.replace('\\', '/').replaceAll("/+$", "");
        return normalized + "/core-site.xml," + normalized + "/hdfs-site.xml";
    }

    private static String defaultHiveWarehouseDirectory(String database, String table) {
        String normalizedTable = table == null ? "" : table.trim();
        if (normalizedTable.isBlank()) return "";
        String normalizedDatabase = database == null ? "default" : database.trim();
        if (normalizedDatabase.isBlank() || "default".equalsIgnoreCase(normalizedDatabase)) {
            return "/user/hive/warehouse/" + normalizedTable;
        }
        return "/user/hive/warehouse/" + normalizedDatabase + ".db/" + normalizedTable;
    }

    /**
     * 解析单个模板字符串，支持一个字符串内包含多个占位符。
     */
    private String resolveString(String input, Map<String, Object> cfg,
                                 Map<String, String> localCs, Map<String, String> sharedCs) {
        if (input == null) return null;
        Matcher m = VAR.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1).trim();
            String val = lookup(expr, cfg, localCs, sharedCs);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按变量前缀分发解析来源：config/cs/jdbc。
     */
    private String lookup(String expr, Map<String, Object> cfg,
                          Map<String, String> localCs, Map<String, String> sharedCs) {
        if (expr.startsWith("config.")) {
            String key = expr.substring("config.".length());
            Object v = cfg.get(key);
            if ("jdbcUrl".equals(key)) {
                return normalizeJdbcUrlForDbType(v == null ? null : String.valueOf(v), String.valueOf(cfg.getOrDefault("dbType", "MYSQL")));
            }
            return v == null ? null : String.valueOf(v);
        }
        if (expr.startsWith("cs.")) {
            String key = expr.substring("cs.".length());
            String id = localCs.get(key);
            if (id != null) return id;
            return sharedCs.get(key);
        }
        if (expr.startsWith("jdbc.")) {
            JdbcDriverSpec spec = jdbcDriverSpec(cfg);
            return switch (expr.substring("jdbc.".length())) {
                case "url" -> buildSourceJdbcUrl(cfg);
                case "driverClass" -> spec.driverClass();
                case "driverLocations" -> spec.driverLocations();
                case "dialectServiceType" -> spec.dialectServiceType();
                case "databaseType" -> nifiDatabaseType(String.valueOf(cfg.getOrDefault("dbType", "MYSQL")));
                case "statementType" -> jdbcStatementType(
                        String.valueOf(cfg.getOrDefault("statementType", "INSERT")),
                        String.valueOf(cfg.getOrDefault("dbType", "MYSQL")), cfg);
                default -> null;
            };
        }
        log.warn("Unknown template variable: ${}{}{}", "{", expr, "}");
        return "${" + expr + "}";
    }

    /**
     * 根据库类型返回 JDBC 驱动、驱动包位置和 NiFi 方言服务类型。
     */
    private JdbcDriverSpec jdbcDriverSpec(Map<String, Object> config) {
        String dbType = String.valueOf(config.getOrDefault("dbType", "MYSQL"));
        Object value = config.get("jdbcUrl");
        String jdbcUrl = value == null ? null : String.valueOf(value);
        JdbcDriverSpec resolved = jdbcDriverSpec(dbType, jdbcUrl);
        String configuredLocations = firstNonBlank(
                stringConfig(config, "driverLocations"),
                stringConfig(config, "nifiDriverLocations"));
        if (configuredLocations != null) {
            return new JdbcDriverSpec(resolved.driverClass(),
                    resolveNodeJdbcDriverLocation(dbType, configuredLocations), resolved.dialectServiceType());
        }
        // Focused compiler tests do not bootstrap a tenant runtime node. Production compilation
        // always has a resolver and must not silently fall back to a path from the developer host.
        if (nifiNodeRuntimeResolver == null) {
            return resolved;
        }
        NifiNodeRuntimeResolver.RuntimeNode node = nifiNodeRuntimeResolver.resolve();
        String nodeConfiguredLocation = node.jdbcDriverLocation(dbType);
        if (nodeConfiguredLocation.isBlank()) {
            throw new IllegalStateException("NiFi 节点“" + node.code()
                    + "”未配置 JDBC 驱动目录；请在【节点管理】编辑该节点后补充实际目录路径。");
        }
        return new JdbcDriverSpec(resolved.driverClass(),
                resolveNodeJdbcDriverLocation(dbType, nodeConfiguredLocation), resolved.dialectServiceType());
    }

    /**
     * The node-management contract stores a container-local JDBC <em>directory</em>,
     * not a vendor-version-specific JAR.  Keep the filename in the compiler's
     * managed compatibility map so a task can be deployed consistently after a
     * node is moved.  Explicit JAR paths remain untouched for installations
     * that use a different driver version.
     */
    private String resolveNodeJdbcDriverLocation(String dbType, String configuredLocation) {
        String location = configuredLocation == null ? "" : configuredLocation.trim();
        if (location.isBlank() || location.matches("(?i).*\\.jar(?:\\s*,\\s*.*\\.jar)*")) {
            return location;
        }
        String type = dbType == null ? "" : dbType.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        // Huawei MRS Hive intentionally loads a client library directory; it
        // has several transitive JARs and is handled by the Hive-specific path.
        if ("HIVE".equals(type)) {
            return location;
        }
        String driverFile = switch (type) {
            case "MYSQL", "TDSQL_MYSQL", "DORIS", "STARROCKS" -> "mysql-connector-j-8.4.0.jar";
            case "MARIADB" -> "mariadb-java-client-3.4.1.jar";
            case "POSTGRESQL", "TDSQL_PG" -> "postgresql-42.7.4.jar";
            case "GAUSSDB", "OPENGAUSS" -> "postgresql-42.6.0.jar";
            case "ORACLE" -> "ojdbc8-12.2.0.1.jar";
            case "SQLSERVER" -> "mssql-jdbc-12.6.4.jre11.jar";
            case "DB2" -> "jcc-11.5.9.0.jar";
            case "DM", "DAMENG" -> "DmJdbcDriver18-8.1.3.140.jar";
            case "KINGBASE", "KINGBASE8" -> "kingbase8.jar";
            case "GBASE8A" -> "gbase-connector-java.jar";
            case "GBASE8S" -> "gbasedbtjdbc.jar";
            case "OCEANBASE_ORACLE", "OCEANBASEORACLE", "OCEANBASE_MYSQL", "OCEANBASEMYSQL" ->
                    "oceanbase-client-2.4.10.jar";
            case "CLICKHOUSE" -> "clickhouse-jdbc-0.6.5-all.jar";
            case "HIGHGO" -> "HgdbJdbc-6.0.4.jar";
            case "OSCAR" -> "oscarJDBC-7.jar";
            default -> "";
        };
        if (driverFile.isBlank()) {
            return location;
        }
        return location.replaceAll("/+$", "") + '/' + driverFile;
    }

    private JdbcDriverSpec jdbcDriverSpec(String dbType) {
        return jdbcDriverSpec(dbType, null);
    }

    private JdbcDriverSpec jdbcDriverSpec(String dbType, String jdbcUrl) {
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        String lowerUrl = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (("OCEANBASE_ORACLE".equals(type) || "OCEANBASEORACLE".equals(type))
                && lowerUrl.startsWith("jdbc:oracle:")) {
            return new JdbcDriverSpec("oracle.jdbc.OracleDriver",
                    "",
                    "org.apache.nifi.dbcp.OracleDatabaseDialectService");
        }
        if (("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type))
                && lowerUrl.startsWith("jdbc:mysql:")) {
            return new JdbcDriverSpec("com.mysql.cj.jdbc.Driver",
                    "",
                    "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
        }
        if (("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type))
                && lowerUrl.startsWith("jdbc:oceanbase:")) {
            return new JdbcDriverSpec("com.oceanbase.jdbc.Driver",
                    "",
                    "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
        }
        return switch (type) {
            case "MARIADB" ->
                    new JdbcDriverSpec("org.mariadb.jdbc.Driver", "", "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
            case "POSTGRESQL", "TDSQL_PG" ->
                    new JdbcDriverSpec("org.postgresql.Driver", "", "org.apache.nifi.dbcp.PostgreSQLDatabaseDialectService");
            case "GAUSSDB", "OPENGAUSS" ->
                    new JdbcDriverSpec("org.postgresql.Driver", "", "org.apache.nifi.dbcp.PostgreSQLDatabaseDialectService");
            case "ORACLE" ->
                    new JdbcDriverSpec("oracle.jdbc.OracleDriver", "", "org.apache.nifi.dbcp.OracleDatabaseDialectService");
            case "SQLSERVER" ->
                    new JdbcDriverSpec("com.microsoft.sqlserver.jdbc.SQLServerDriver", "", "org.apache.nifi.dbcp.MicrosoftSQLServerDatabaseDialectService");
            case "DB2" ->
                    new JdbcDriverSpec("com.ibm.db2.jcc.DB2Driver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "DM", "DAMENG" ->
                    new JdbcDriverSpec("dm.jdbc.driver.DmDriver", "", "org.apache.nifi.dbcp.OracleDatabaseDialectService");
        // Use the managed file name instead of a vendor versioned name so the canvas
        // configuration and the NiFi driver mount stay compatible across driver upgrades.
        case "KINGBASE", "KINGBASE8" ->
                new JdbcDriverSpec("com.kingbase8.Driver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "GBASE8A" ->
                    new JdbcDriverSpec("com.gbase.jdbc.Driver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "GBASE8S" ->
                    new JdbcDriverSpec("com.gbasedbt.jdbc.IfxDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "OSCAR" ->
                    new JdbcDriverSpec("com.oscar.Driver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "HIGHGO" ->
                    new JdbcDriverSpec("com.highgo.jdbc.Driver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "OCEANBASE" ->
                    new JdbcDriverSpec("com.oceanbase.jdbc.Driver", "", "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
            case "OCEANBASE_MYSQL", "OCEANBASEMYSQL" ->
                    new JdbcDriverSpec("com.mysql.cj.jdbc.Driver", "", "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
            case "OCEANBASE_ORACLE", "OCEANBASEORACLE" ->
                    new JdbcDriverSpec("com.oceanbase.jdbc.Driver", "", "org.apache.nifi.dbcp.OracleDatabaseDialectService");
            case "CLICKHOUSE" ->
                    new JdbcDriverSpec("com.clickhouse.jdbc.ClickHouseDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "HIVE" ->
                    new JdbcDriverSpec("org.apache.hive.jdbc.HiveDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "HETU", "TRINO", "PRESTO" ->
                    new JdbcDriverSpec("io.trino.jdbc.TrinoDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "DORIS", "STARROCKS" ->
                    new JdbcDriverSpec("com.mysql.cj.jdbc.Driver", "", "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
            case "IOTDB" ->
                    new JdbcDriverSpec("org.apache.iotdb.jdbc.IoTDBDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            case "ELASTICSEARCH" ->
                    new JdbcDriverSpec("org.elasticsearch.xpack.sql.jdbc.EsDriver", "", "org.apache.nifi.dbcp.GenericDatabaseDialectService");
            default ->
                    new JdbcDriverSpec("com.mysql.cj.jdbc.Driver", "", "org.apache.nifi.dbcp.MySQLDatabaseDialectService");
        };
    }

    /**
     * 将画布 dbType 映射为 PutDatabaseRecord 的 Database Type 允许值。
     */
    private String nifiDatabaseType(String dbType) {
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MYSQL", "MARIADB", "TDSQL_MYSQL", "OCEANBASE", "OCEANBASE_MYSQL", "OCEANBASEMYSQL" -> "MySQL";
            case "POSTGRESQL", "TDSQL_PG", "GAUSSDB", "OPENGAUSS", "KINGBASE", "KINGBASE8", "HIGHGO" -> "PostgreSQL";
            case "ORACLE", "OCEANBASE_ORACLE", "OCEANBASEORACLE" -> "Oracle";
            case "ORACLE 12+", "ORACLE12+", "ORACLE12", "ORACLE_12", "ORACLE 12" -> "Oracle 12+";
            case "DM", "DAMENG" -> "Oracle 12+";
            case "SQLSERVER", "MSSQL", "MS SQL", "MS SQL 2012+" -> "MS SQL 2012+";
            case "MS SQL 2008" -> "MS SQL 2008";
            case "PHOENIX" -> "Phoenix";
            // 其余数据库类型在 PutDatabaseRecord 中无专属枚举时回退 Generic。
            default -> "Generic";
        };
    }

    /**
     * source/fetch 节点的增量同步选项处理：
     * FULL 模式移除 Maximum-value Columns；INCREMENTAL 模式强校验增量字段并拼接条件参数。
     */
    private void applyIncrementalSourceOptions(ComponentManifest manifest, String localProcessorId,
                                               Map<String, Object> cfg, Map<String, String> resolved) {
        if (!"source".equals(manifest.category()) || !"fetch".equals(localProcessorId)) return;
        if (!resolved.containsKey("Maximum-value Columns")) return;

        String defaultSyncMode = stringConfig(cfg, "incrementalColumn").isBlank() ? "FULL" : "INCREMENTAL";
        String syncMode = String.valueOf(cfg.getOrDefault("syncMode", defaultSyncMode)).trim().toUpperCase(Locale.ROOT);
        if (syncMode.isBlank() || "FULL".equals(syncMode)) {
            resolved.remove("Maximum-value Columns");
        } else {
            String primary = stringConfig(cfg, "incrementalColumn");
            String tie = stringConfig(cfg, "tieBreakerColumn");
            if (primary.isBlank()) {
                throw new IllegalStateException("启用增量同步时必须配置主增量字段");
            }
            validateColumnList(primary, "主增量字段");
            String columns = primary.trim();
            if (!tie.isBlank()) {
                validateColumnList(tie, "次级字段");
                columns = columns + "," + tie.trim();
            }
            resolved.put("Maximum-value Columns", columns);
        }

        String batchSize = stringConfig(cfg, "batchSize");
        if (!batchSize.isBlank()) {
            resolved.put("Partition Size", String.valueOf(Math.max(1, (int) Math.round(Double.parseDouble(batchSize)))));
        }

        String where = stringConfig(cfg, "customWherePredicate");
        if (!where.isBlank()) {
            resolved.put("Where Clause", validateWherePredicate(where));
        }
    }

    /** Build the Huawei MRS-compatible Hive JDBC URL used by the NiFi DBCP service. */
    private String buildHiveJdbcUrl(Map<String, Object> config) {
        String configuredUrl = String.valueOf(config.getOrDefault("jdbcUrl", "")).trim();
        if (!configuredUrl.isBlank() && !"null".equalsIgnoreCase(configuredUrl)) {
            return configuredUrl;
        }
        String quorum = stringConfig(config, "zookeeperQuorum");
        if (quorum.isBlank()) {
            quorum = stringConfig(config, "zkQuorum");
        }
        String host = stringConfig(config, "host");
        String port = firstNonBlank(stringConfig(config, "port"), "10000");
        String database = firstNonBlank(stringConfig(config, "database"), "default");
        StringBuilder url = new StringBuilder("jdbc:hive2://")
                .append(quorum.isBlank() ? host + ":" + port : quorum)
                .append("/").append(database);
        String authMode = firstNonBlank(stringConfig(config, "authMode"), stringConfig(config, "auth"), "none");
        String discovery = firstNonBlank(stringConfig(config, "serviceDiscoveryMode"),
                quorum.isBlank() ? null : "zooKeeper");
        String namespace = firstNonBlank(stringConfig(config, "zookeeperNamespace"),
                stringConfig(config, "zooKeeperNamespace"), quorum.isBlank() ? null : "hiveserver2");
        appendHiveJdbcParam(url, "serviceDiscoveryMode", discovery);
        appendHiveJdbcParam(url, "zooKeeperNamespace", namespace);
        if ("KERBEROS".equalsIgnoreCase(authMode)) {
            appendHiveJdbcParam(url, "auth", "KERBEROS");
            appendHiveJdbcParam(url, "sasl.qop", firstNonBlank(stringConfig(config, "saslQop"), stringConfig(config, "sasl.qop")));
            appendHiveJdbcParam(url, "principal", firstNonBlank(stringConfig(config, "principal"), stringConfig(config, "hivePrincipal")));
            appendHiveJdbcParam(url, "ssl", stringConfig(config, "ssl"));
            appendHiveJdbcParam(url, "user.principal", firstNonBlank(stringConfig(config, "userPrincipal"), stringConfig(config, "clientPrincipal")));
            appendHiveJdbcParam(url, "user.keytab", firstNonBlank(stringConfig(config, "keytabPath"), stringConfig(config, "keytab")));
        } else if (!quorum.isBlank()) {
            appendHiveJdbcParam(url, "auth", "none");
        }
        String extra = stringConfig(config, "extraParams");
        if (!extra.isBlank()) {
            url.append(extra.startsWith(";") ? extra : ";" + extra.replace("&", ";"));
        }
        return url.toString();
    }

    private void appendHiveJdbcParam(StringBuilder url, String key, String value) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
            url.append(";").append(key).append("=").append(value.trim());
        }
    }

    /**
     * GenerateTableFetch defaults to Generic SQL, which produces LIMIT/OFFSET.
     * Oracle and OceanBase Oracle require the processor's Oracle dialect instead.
     */
    private void applySourceFetchDatabaseType(ComponentManifest manifest, String processorType,
                                              Map<String, Object> cfg, Map<String, String> resolved) {
        if (!"source".equals(manifest.category())) return;
        if (!"org.apache.nifi.processors.standard.GenerateTableFetch".equals(processorType)) return;
        String databaseType = sourceFetchDatabaseType(cfg);
        if (databaseType != null) {
            resolved.put("Database Type", databaseType);
        }
    }

    /**
     * 对 ExecuteSQLRecord 追加统一行为配置，避免输出空 FlowFile。
     */
    private void applySourceRecordQueryOptions(ComponentManifest manifest, String processorType, Map<String, String> resolved) {
        if (!"source".equals(manifest.category())) return;
        if (!"org.apache.nifi.processors.standard.ExecuteSQLRecord".equals(processorType)) return;
        resolved.put("Output Empty FlowFile", "false");
    }

    /**
     * 读取并 trim 配置值，空值返回空串，便于后续判空逻辑统一。
     */
    private String stringConfig(Map<String, Object> cfg, String key) {
        Object raw = cfg.get(key);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /**
     * 校验列名列表，仅允许字段标识符，防止把表达式/SQL 片段误填入字段位。
     */
    private void validateColumnList(String raw, String label) {
        for (String part : raw.split(",")) {
            String column = part.trim();
            if (column.isEmpty()) continue;
            if (!column.matches("[A-Za-z_][A-Za-z0-9_.$]*")) {
                throw new IllegalStateException(label + "只能包含字段名，多个字段用逗号分隔: " + column);
            }
        }
    }

    /**
     * 校验自定义 WHERE 片段，仅允许条件表达式，禁止完整 SQL 与注释语法。
     */
    private String validateWherePredicate(String where) {
        String normalized = where.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new IllegalStateException("自定义 WHERE 不能包含分号或 SQL 注释");
        }
        if (lower.matches(".*\\b(select|insert|update|delete|drop|truncate|create|alter|merge|call|exec)\\b.*")) {
            throw new IllegalStateException("自定义 WHERE 只能填写条件片段，不能包含 SQL 语句关键字");
        }
        return normalized;
    }

    /**
     * 针对 MySQL 兼容库补齐常用 JDBC 参数（缺失才补）。
     */
    private String normalizeJdbcUrlForDbType(String url, String dbType) {
        if (url == null || url.isBlank()) return url;
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        String normalizedUrl = url.trim();
        String lowerUrl = normalizedUrl.toLowerCase(Locale.ROOT);
        if ("OCEANBASE_ORACLE".equals(type) || "OCEANBASEORACLE".equals(type)) {
            if (lowerUrl.startsWith("jdbc:oceanbaseoracle://")) {
                normalizedUrl = "jdbc:oceanbase:oracle://" + normalizedUrl.substring("jdbc:oceanbaseoracle://".length());
            }
            return normalizedUrl;
        }
        if ("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type)) {
            if (lowerUrl.startsWith("jdbc:oceanbasemysql://")) {
                normalizedUrl = "jdbc:mysql://" + normalizedUrl.substring("jdbc:oceanbasemysql://".length());
            }
        }
        lowerUrl = normalizedUrl.toLowerCase(Locale.ROOT);
        boolean mysqlCompatible = "MYSQL".equals(type)
                || "MARIADB".equals(type)
                || "TDSQL_MYSQL".equals(type)
                || "OCEANBASE".equals(type)
                || "OCEANBASE_MYSQL".equals(type)
                || "OCEANBASEMYSQL".equals(type)
                || "DORIS".equals(type)
                || "STARROCKS".equals(type)
                || lowerUrl.startsWith("jdbc:mysql:")
                || lowerUrl.startsWith("jdbc:mariadb:")
                || lowerUrl.startsWith("jdbc:oceanbase:");
        if (!mysqlCompatible) return normalizedUrl;
        String normalized = appendJdbcParamIfMissing(normalizedUrl, "useSSL", "false");
        normalized = appendJdbcParamIfMissing(normalized, "serverTimezone", "Asia/Shanghai");
        normalized = appendJdbcParamIfMissing(normalized, "allowPublicKeyRetrieval", "true");
        return normalized;
    }

    /**
     * 若 URL 未包含目标参数，则以 query 参数方式追加。
     */
    private String appendJdbcParamIfMissing(String url, String key, String value) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.matches(".*([?&])" + Pattern.quote(key.toLowerCase(Locale.ROOT)) + "=.*")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
    }

    /**
     * 规范化写入策略，且对不支持 UPSERT 的数据库回退为 INSERT。
     */
    private String jdbcStatementType(String statementType, String dbType, Map<String, Object> cfg) {
        String type = statementType == null ? "INSERT" : statementType.trim().toUpperCase(Locale.ROOT);
        String databaseType = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        boolean linewellWriter = usesLinewellJdbcWriter(cfg);
        // A JDBC sink created before writerType/statementType were persisted
        // historically uses Linewell on Oracle targets.  Keep that default
        // only while the write mode has never been selected; an explicit
        // INSERT, UPSERT, UPDATE or DELETE on Linewell must remain intact.
        if (linewellWriter && (stringConfig(cfg, "writerType").isBlank()
                || stringConfig(cfg, "statementType").isBlank())) {
            return "MERGE";
        }
        if ("MERGE".equals(type) && !linewellWriter) {
            throw new IllegalStateException("MERGE 仅支持 LinewellPutDatabaseRecord；请切换目标表写入组件后再部署。");
        }
        if ("UPSERT".equals(type) && !linewellWriter
                && Set.of("GBASE8A", "CLICKHOUSE", "ELASTICSEARCH").contains(databaseType)) {
            log.warn("{} does not support PutDatabaseRecord UPSERT in this canvas adapter; using INSERT instead", databaseType);
            return "INSERT";
        }
        return switch (type) {
            case "UPSERT", "MERGE", "UPDATE", "DELETE" -> type;
            default -> "INSERT";
        };
    }

    private boolean usesLinewellJdbcWriter(Map<String, Object> cfg) {
        return LINEWELL_DATABASE_RECORD_MODE.equals(effectiveJdbcWriterType(cfg));
    }

    /**
     * Respects an explicit writer selection for every JDBC target. For legacy
     * flows with no selection, keep the historical default: Oracle variants
     * use Linewell and all other databases use NiFi's native writer.
     */
    private String effectiveJdbcWriterType(Map<String, Object> cfg) {
        String configured = stringConfig(cfg, "writerType").toUpperCase(Locale.ROOT);
        if (LINEWELL_DATABASE_RECORD_MODE.equals(configured) || "LINEWELL".equals(configured)) {
            return LINEWELL_DATABASE_RECORD_MODE;
        }
        if (NATIVE_DATABASE_RECORD_MODE.equals(configured) || "NATIVE".equals(configured)) {
            return NATIVE_DATABASE_RECORD_MODE;
        }
        if (configured.isBlank()) {
            return isLinewellOracleWriterTarget(stringConfig(cfg, "dbType"))
                    ? LINEWELL_DATABASE_RECORD_MODE : NATIVE_DATABASE_RECORD_MODE;
        }
        throw new IllegalStateException("不支持的 JDBC 目标表写入组件：" + configured);
    }

    private static boolean isLinewellOracleWriterTarget(String dbType) {
        String normalized = dbType == null ? "" : dbType.trim().toUpperCase(Locale.ROOT);
        return "ORACLE".equals(normalized)
                || "ORACLE 12+".equals(normalized)
                || "ORACLE12+".equals(normalized)
                || "ORACLE12".equals(normalized)
                || "ORACLE_12".equals(normalized)
                || "OCEANBASE_ORACLE".equals(normalized)
                || "OCEANBASEORACLE".equals(normalized);
    }

    // ---------------------------------------------------------------------
    // 编译结果与辅助类型
    // ---------------------------------------------------------------------

    public record CompileResult(String processGroupId, Map<String, String> sharedControllerServiceIds,
                                Map<String, NodeCompilation> nodes,
                                Map<String, String> edgeConnectionIds) {
    }

    private record JdbcDriverSpec(String driverClass, String driverLocations, String dialectServiceType) {
    }

    private record TargetColumnMetadata(String name, String type, boolean primaryKey, boolean unique) {
    }

    /** Native NiFi canvas grid coordinates for one DSL component. */
    record NodeLayout(int column, int row) {
    }

    /**
     * Edges name a component outlet, not a native NiFi relationship. Older
     * governance designs incorrectly saved "success" for a single default
     * outlet. Accept only that unambiguous legacy form; never guess a branch.
     * Runtime node mappings must use the same resolver as compilation.
     */
    public static Outlet resolveOutlet(NodeCompilation node, String requested) {
        if (node == null) return null;
        if (requested == null) return node.outlets.values().stream().findFirst().orElse(null);
        Outlet exact = node.outlets.get(requested);
        if (exact != null) return exact;
        Outlet fallback = node.outlets.get("default");
        return "success".equals(requested) && node.outlets.size() == 1
                && fallback != null && "success".equals(fallback.relationship()) ? fallback : null;
    }

    public static final class NodeCompilation {
        public final Map<String, String> csIds = new LinkedHashMap<>();
        public final Map<String, String> processorIds = new LinkedHashMap<>();
        /**
         * processorId -> 已被连线使用的 relationship（这些关系不能被 auto-terminate）。
         */
        public final Map<String, Set<String>> usedOutgoing = new LinkedHashMap<>();
        public String inletProcessorId;
        public final Map<String, Outlet> outlets = new LinkedHashMap<>();
    }

    public record Outlet(String processorId, String relationship) {
    }

    public static class NodeConfigException extends IllegalStateException {
        private final String nodeId;
        private final String nodeLabel;
        private final String fieldKey;

        /**
         * 用于把节点级配置错误精准回传到前端定位。
         */
        public NodeConfigException(String nodeId, String nodeLabel, String fieldKey, String message, Throwable cause) {
            super(message, cause);
            this.nodeId = nodeId;
            this.nodeLabel = nodeLabel;
            this.fieldKey = fieldKey;
        }

        public String nodeId() {
            return nodeId;
        }

        public String nodeLabel() {
            return nodeLabel;
        }

        public String fieldKey() {
            return fieldKey;
        }
    }
}
