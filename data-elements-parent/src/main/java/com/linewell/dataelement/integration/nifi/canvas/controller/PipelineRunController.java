package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.compile.SyncPolicy;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineRuntimeStatusResolver;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.TargetTableCleanupService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder;
import com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pipeline 运行控制器：负责启动、停止、部署、卸载和状态查询。
 *
 * <p>接口语义如下：
 * <ul>
 *   <li>{@code POST /start}：启动当前关联的 NiFi 流程组。</li>
 *   <li>{@code POST /stop}：停止 NiFi 处理器，但保留 NiFi 中的 Process Group（状态置为 STOPPED）。</li>
 *   <li>{@code GET /status}：返回聚合状态（本地生命周期状态 + NiFi 运行态 + 错误信息等）。</li>
 *   <li>{@code POST /deploy}：仅部署，不启动处理器。</li>
 * </ul>
 */
@RestController
@RequestMapping("/nifi/api/pipelines/{id}")
@Api(tags = "PipelineRunController NiFi 流程运行")
public class PipelineRunController {
    private com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService runtimeStatuses;
    @Autowired public void setRuntimeStatuses(com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService service) { runtimeStatuses = service; }
    private com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore mappingVersions;
    @org.springframework.beans.factory.annotation.Autowired public void setMappingVersions(com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore store){mappingVersions=store;}

    private static final Logger log = LoggerFactory.getLogger(PipelineRunController.class);
    /**
     * Bump this whenever generated NiFi graph semantics change.  A DSL-only hash
     * cannot distinguish a graph produced by an older compiler (for example an
     * FTP fetch processor for an SFTP datasource) from the same DSL produced by
     * the corrected compiler.  Including this revision makes the next explicit
     * save-and-deploy safely rebuild that stale graph once, then retain normal
     * fast-starts.
     */
    /**
     * Included in the deployment hash so a change to generated NiFi graph
     * semantics triggers one safe rebuild of previously deployed pipelines.
     * Package visibility keeps the controller tests on the exact same
     * compatibility baseline instead of duplicating a version literal.
     */
    static final String DEPLOYMENT_COMPILER_REVISION = DslHasher.CURRENT_COMPILER_REVISION;
    private final PipelineRepository repo;
    private final DslCompiler compiler;
    private final NifiClient nifi;
    private final NifiAttributeSync attributeSync;
    private final ErrorService errors;
    private final PipelineTaskLifecycleSynchronizer taskLifecycle;
    private final PipelineRuntimeStatusResolver statusResolver;
    private final PipelineGroupOrganizer groups;
    private final TargetTableCleanupService targetCleanup;
    private final AccessTaskFieldMappingBinder accessTaskFieldMappingBinder;
    @Autowired(required = false)
    private LineageAnalysisService lineageAnalysis;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;

    public PipelineRunController(PipelineRepository repo, DslCompiler compiler, NifiClient nifi, ErrorService errors,
                                 PipelineTaskLifecycleSynchronizer taskLifecycle,
                                 PipelineRuntimeStatusResolver statusResolver, PipelineGroupOrganizer groups,
                                 TargetTableCleanupService targetCleanup) {
        this(repo, compiler, nifi, errors, taskLifecycle, statusResolver, groups, targetCleanup, null);
    }

    @Autowired
    public PipelineRunController(PipelineRepository repo, DslCompiler compiler, NifiClient nifi, ErrorService errors,
                                 PipelineTaskLifecycleSynchronizer taskLifecycle,
                                 PipelineRuntimeStatusResolver statusResolver, PipelineGroupOrganizer groups,
                                 TargetTableCleanupService targetCleanup,
                                 AccessTaskFieldMappingBinder accessTaskFieldMappingBinder) {
        this.repo = repo;
        this.compiler = compiler;
        this.nifi = nifi;
        this.attributeSync = new NifiAttributeSync(nifi);
        this.errors = errors;
        this.taskLifecycle = taskLifecycle;
        this.statusResolver = statusResolver;
        this.groups = groups;
        this.targetCleanup = targetCleanup;
        this.accessTaskFieldMappingBinder = accessTaskFieldMappingBinder;
    }

    @GetMapping("/ui-link")
    @ApiOperation(value = "获取当前流程的 NiFi 原生页面地址")
    public ResponseEntity<?> uiLink(@PathVariable String id) {
        Pipeline pipeline = repo.findById(id).orElse(null);
        if (pipeline == null) return ResponseEntity.notFound().build();
        // Viewing the existing native group does not execute the saved design.
        // A newer draft/compiler or failed replacement must not hide a running group.
        if (pipeline.nifiProcessGroupId() == null || pipeline.nifiProcessGroupId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前任务尚未部署到 NiFi，请先部署流程"));
        }
        return ResponseEntity.ok(Map.of(
                "url", "/nifi-ui/#/process-groups/" + pipeline.nifiProcessGroupId(),
                "processGroupId", pipeline.nifiProcessGroupId(),
                "reusesBrowserSession", true,
                "authenticatedByBackend", true));
    }

    /**
     * 启动已部署的流程。
     *
     * <p>普通启动使用当前关联的原生流程组。force=true 用于显式运维重建。</p>
     */
    @PostMapping("/start")
    @SerializedNifiMutation
    @ApiOperation(value = "启动流程")
    @Operation(summary = "开始(start)")
    public ResponseEntity<?> start(@ApiParam(value = "pipeline ID", required = true)
                                   @PathVariable String id,
                                   @RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                   @RequestParam(value = "confirmCleanup", required = false, defaultValue = "false") boolean confirmCleanup) {
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!force && isBlank(p.nifiProcessGroupId())) {
            return deploymentRequired(p, deploymentHash(p), "请先部署流程，再启动 NiFi");
        }
        if (force) p = bindAccessTaskMappings(p);
        Boolean deployedCleanup = p.nodeMapping() == null ? null : p.nodeMapping().cleanupConfirmationRequired();
        boolean requiresCleanup = force ? SyncPolicy.needsCleanupConfirmation(p)
                : deployedCleanup != null ? deployedCleanup
                : Objects.equals(p.lastDeployedHash(), deploymentHash(p)) && SyncPolicy.needsCleanupConfirmation(p);
        if (requiresCleanup && !confirmCleanup) {
            return ResponseEntity.status(409).body(Map.of("error", "cleanup-confirmation-required",
                    "message", "请核对已部署版本的目标表及清空范围，再确认启动。"));
        }
        if (!force) return fastStart(p, true);
        return fullDeploy(p, deploymentHash(p), true, null, true);
    }

    private ResponseEntity<?> deploymentRequired(Pipeline pipeline, String currentHash, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "deployment-required");
        body.put("message", message);
        body.put("deployed", pipeline.nifiProcessGroupId() != null && pipeline.lastDeployedHash() != null);
        body.put("currentHash", currentHash);
        body.put("lastDeployedHash", pipeline.lastDeployedHash());
        return ResponseEntity.status(409).body(body);
    }

    /** Java-call convenience overload retained for service tests and internal callers. */
    public ResponseEntity<?> start(String id) {
        return start(id, false, false);
    }

    public ResponseEntity<?> start(String id, boolean force) {
        return start(id, force, false);
    }

    @PostMapping("/deploy")
    @SerializedNifiMutation
    @ApiOperation(value = "部署流程")
    @Operation(summary = "部署(deploy)")
    public ResponseEntity<?> deploy(@ApiParam(value = "pipeline ID", required = true)
                                    @PathVariable String id,
                                    @RequestBody(required = false) DeployRequest request) {
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (request != null && request.preserveExisting()) return deployCandidate(p, request.catalogTid());
        return fullDeploy(p, deploymentHash(p), false, request, false);
    }

    @GetMapping("/attribute-sync")
    public NifiAttributeSync.Preview previewAttributes(@PathVariable String id) {
        return attributeSync.preview(requirePipeline(id));
    }

    @PostMapping("/attribute-sync")
    @SerializedNifiMutation
    public List<NifiAttributeSync.Change> syncAttributes(@PathVariable String id, @RequestBody NifiAttributeSync.Request request) {
        Pipeline pipeline = requirePipeline(id);
        var result = attributeSync.apply(pipeline, request);
        if (result.results().stream().anyMatch(row -> "SYNCED".equals(row.status()) || "KEPT_NATIVE".equals(row.status()))) {
            repo.update(id, current -> withMapping(current, current.nodeMapping().withAttributes(
                    result.baselines())));
        }
        return result.results();
    }

    @PostMapping("/activate-candidate")
    @SerializedNifiMutation
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> activateCandidate(@PathVariable String id, @RequestBody CandidateRequest request) {
        Pipeline p = requirePipeline(id);
        var candidate = p.nodeMapping() == null ? null : p.nodeMapping().candidate();
        if (candidate == null || !candidate.processGroupId().equals(request.processGroupId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "候选流程已变化，请刷新");
        }
        requireStoppedGroup(p.nifiProcessGroupId(), true);
        requireStoppedGroup(candidate.processGroupId(), false);
        return bindCandidate(p, candidate, "candidate-activated");
    }

    private Pipeline requirePipeline(String id) {
        return repo.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "流程不存在"));
    }

    private void requireStoppedGroup(String id, boolean allowMissing) {
        if (isBlank(id)) return;
        JsonNode aggregate;
        try {
            aggregate = nifi.getProcessGroupStatus(id).path("processGroupStatus").path("aggregateSnapshot");
            JsonNode group = nifi.getProcessGroup(id);
            int running = group.path("runningCount").asInt(group.path("component").path("runningCount").asInt(-1));
            if (aggregate.path("activeThreadCount").asInt(-1) == 0 && running == 0) return;
        } catch (NifiException e) {
            if (allowMissing && e.status() == 404) return;
            throw e;
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "请先停止对应 NiFi 流程组并等待运行任务结束，再切换关联");
    }

    private ResponseEntity<?> deployCandidate(Pipeline saved, String catalogTid) {
        String[] createdGroup = {null};
        try (var runtime = nifi.bindRuntimeNode()) {
            PipelineController.validateIncrementalFields(saved);
            var existing = saved.nodeMapping() == null ? null : saved.nodeMapping().candidate();
            if (existing != null && saved.designHash().equals(existing.designHash())) {
                try {
                    nifi.getProcessGroup(existing.processGroupId());
                    return ResponseEntity.ok(candidateResult(saved, existing, "candidate-created"));
                } catch (NifiException e) {
                    if (e.status() != 404) throw e;
                }
            }
            taskLifecycle.ensureCatalogAccessTask(catalogTid, saved);
            Pipeline design = accessTaskFieldMappingBinder == null ? saved : accessTaskFieldMappingBinder.bind(saved);
            var layout = groups.prepareCandidate(design);
            var compiled = compiler.compile(design, group -> createdGroup[0] = group, layout);
            if (compiled == null || isBlank(compiled.processGroupId())) {
                throw new IllegalStateException("未生成可用的 NiFi 流程组");
            }
            createdGroup[0] = compiled.processGroupId();
            NifiNodeMapping mapping = buildMapping(design, compiled);
            Map<String, Map<String, NifiNodeMapping.AttributeBaseline>> baselines = null;
            try {
                baselines = attributeSync.capture(saved.dsl(), compiled.processGroupId(), mapping);
            } catch (RuntimeException e) {
                log.warn("NiFi 属性基准暂不可读取，pipeline={}, group={}", saved.id(), compiled.processGroupId());
            }
            var candidate = new NifiNodeMapping.Candidate(compiled.processGroupId(), deploymentHash(design),
                    saved.designHash(), mapping.withAttributes(baselines)
                            .withRuntimePolicy(oneShotSourceIds(design, mapping), SyncPolicy.needsCleanupConfirmation(design)),
                    compiled.planVersionIds());
            java.util.function.Supplier<Map<String, Object>> persist = () -> {
                if (isBlank(saved.nifiProcessGroupId())) return bindCandidate(saved, candidate, "first-deploy");
                Pipeline updated = repo.update(saved.id(), current -> withMapping(current,
                        (current.nodeMapping() == null ? new NifiNodeMapping(null, null, null) : current.nodeMapping())
                                .withCandidate(candidate)));
                return candidateResult(updated, candidate, "candidate-created");
            };
            Map<String, Object> response = transactions == null ? persist.get() : transactions.execute(status -> persist.get());
            createdGroup[0] = null;
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (!isBlank(createdGroup[0])) {
                try { nifi.cleanupProcessGroup(createdGroup[0]); }
                catch (RuntimeException cleanup) { log.warn("部署候选组清理待处理，group={}", createdGroup[0]); }
            }
            return deploymentFailure(saved.id(), e instanceof NifiException nifiError && nifiError.status() > 0
                            ? nifiError.status() : 422, "CANDIDATE_DEPLOYMENT_FAILED", "生成 NiFi 流程失败", e,
                    "请检查本次生成所需的组件和配置。", Map.of("previousGraphRetained", true));
        }
    }

    private Map<String, Object> bindCandidate(Pipeline saved, NifiNodeMapping.Candidate candidate, String mode) {
        Pipeline updated = repo.update(saved.id(), current -> new Pipeline(current.id(), current.name(),
                current.description(), current.createdAt(), current.updatedAt(), current.dsl(), candidate.processGroupId(),
                PipelineStatus.STOPPED, candidate.deploymentHash(), System.currentTimeMillis(), current.lastStoppedAt(),
                candidate.mapping(), 0L));
        if (mappingVersions != null && candidate.planVersionIds() != null) {
            mappingVersions.activate(saved, candidate.planVersionIds(), candidate.processGroupId());
        }
        taskLifecycle.updateProcessGroup(saved.id(), candidate.processGroupId());
        taskLifecycle.statusOnly(saved.id(), 0);
        return candidateResult(updated, candidate, mode);
    }

    private static Map<String, Object> candidateResult(Pipeline pipeline, NifiNodeMapping.Candidate candidate, String mode) {
        return Map.of("pipeline", pipeline, "processGroupId", candidate.processGroupId(), "mode", mode, "started", false);
    }

    private static Pipeline withMapping(Pipeline current, NifiNodeMapping mapping) {
        return new Pipeline(current.id(), current.name(), current.description(), current.createdAt(), current.updatedAt(),
                current.dsl(), current.nifiProcessGroupId(), current.status(), current.lastDeployedHash(),
                current.lastDeployedAt(), current.lastStoppedAt(), mapping, current.lastBulletinId());
    }

    @PostMapping("/stop")
    @SerializedNifiMutation
    @ApiOperation(value = "停止流程")
    @Operation(summary = "停止(stop)")
    public ResponseEntity<?> stop(@ApiParam(value = "pipeline ID", required = true)
                                  @PathVariable String id) {
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (p.nifiProcessGroupId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Pipeline has not been deployed yet"));
        }
        try {
            nifi.setProcessGroupState(p.nifiProcessGroupId(), "STOPPED");
        } catch (NifiException e) {
            errors.add(id, runtimeErr(FlowError.ErrorPhase.RUNTIME, "停止流程组失败", FlowFailureDetails.describe(e)));
            return errorResponse(e);
        }
        repo.updateStatus(id, PipelineStatus.STOPPING);
        try {
            nifi.waitForAllProcessorsStopped(p.nifiProcessGroupId(), 30_000L);
            return markPipelineStopped(p);
        } catch (NifiException e) {
            return ResponseEntity.accepted().body(Map.of("processGroupId", p.nifiProcessGroupId(),
                    "state", "STOPPING", "warning", "NiFi 已接受停止请求，正在等待运行中的任务结束"));
        }
    }

    private ResponseEntity<?> markPipelineStopped(Pipeline p) {
        repo.update(p.id(), cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                cur.createdAt(), cur.updatedAt(), cur.dsl(),
                cur.nifiProcessGroupId(), PipelineStatus.STOPPED,
                cur.lastDeployedHash(), cur.lastDeployedAt(), System.currentTimeMillis(),
                cur.nodeMapping(), cur.lastBulletinId()));
        taskLifecycle.stopped(p.id());
        return ResponseEntity.ok(Map.of("processGroupId", p.nifiProcessGroupId(), "state", "STOPPED"));
    }

    @GetMapping("/status/page")
    public ResponseEntity<?> statusPage(@PathVariable String id,@RequestParam(required=false) String sortField,
            @RequestParam(required=false) String sortDir,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) {
        com.linewell.dataelement.platform.tenant.domain.TenantContext.requireTenantId();
        if(com.linewell.dataelement.platform.tenant.domain.TenantContext.usesControlDatabase()
            ||com.linewell.dataelement.platform.tenant.domain.TenantContext.isIgnored())throw new IllegalStateException("流程状态必须绑定行业租户");
        Pipeline pipeline=repo.findById(id).orElse(null);
        if(pipeline==null)return ResponseEntity.notFound().build();
        JsonNode snapshot=pipeline.nifiProcessGroupId()==null?null:nifi.getProcessGroupStatus(pipeline.nifiProcessGroupId());
        Map<String,Object> result=new LinkedHashMap<>(ProcessorStatusPage.page(snapshot,sortField,sortDir,page,size));
        result.put("deployed",pipeline.nifiProcessGroupId()!=null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    @ApiOperation(value = "查询流程状态")
    @Operation(summary = "状态(status)")
    public ResponseEntity<?> status(@ApiParam(value = "pipeline ID", required = true)
                                    @PathVariable String id) {
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", p.name());
        body.put("status", p.status());
        body.put("deployed", p.nifiProcessGroupId() != null);
        body.put("processGroupId", p.nifiProcessGroupId());
        body.put("lastDeployedHash", p.lastDeployedHash());
        body.put("currentHash", deploymentHash(p));
        body.put("lastDeployedAt", p.lastDeployedAt());
        body.put("lastStoppedAt", p.lastStoppedAt());
        body.put("errors", errors.list(id));
        body.put("nodeMapping", p.nodeMapping());

        if (runtimeStatuses != null) {
            Map<String,Object> snapshot = runtimeStatuses.snapshot();
            Map<String,Object> runtime = runtimeStatuses.describe(snapshot, p.nifiProcessGroupId(), p.status().name());
            body.putAll(runtime);
            body.put("runtimeRevision", snapshot.get("revision"));
            String state = String.valueOf(runtime.get("runtimeState"));
            PipelineStatus effective = switch (state) {
                case "RUNNING" -> PipelineStatus.RUNNING;
                case "STOPPING" -> PipelineStatus.STOPPING;
                case "STOPPED", "EMPTY" -> PipelineStatus.STOPPED;
                case "INVALID", "RUN_ERROR" -> PipelineStatus.RUN_ERROR;
                case "UNDEPLOYED" -> p.status() == PipelineStatus.DRAFT ? PipelineStatus.DRAFT : PipelineStatus.SAVED;
                default -> p.status();
            };
            body.put("status", effective);
            var index = (com.linewell.dataelement.integration.nifi.canvas.runtime.RuntimeStatusIndex) snapshot.get("index");
            if (index != null && p.nifiProcessGroupId() != null) body.put("nifiStatus", index.rawStatus(p.nifiProcessGroupId()));
            if (!Boolean.TRUE.equals(runtime.get("runtimeFresh")) || state.equals("MISSING"))
                body.put("nifiStatusError", state.equals("MISSING") ? "NiFi 中未找到已关联的流程" : snapshot.get("message"));
        } else if (p.nifiProcessGroupId() != null) {
            // Compatibility for standalone callers; status reads never rewrite lifecycle state.
            JsonNode raw = nifi.getProcessGroupStatus(p.nifiProcessGroupId());
            body.put("nifiStatus", raw);
            body.put("status", statusResolver.resolve(p.status(), raw, p.nifiProcessGroupId()));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/undeploy")
    @SerializedNifiMutation
    @ApiOperation(value = "卸载流程")
    @Operation(summary = "卸载(undeploy)")
    public ResponseEntity<?> undeploy(@ApiParam(value = "pipeline ID", required = true)
                                      @PathVariable String id) {
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            List<String> groupIds = groups.existingIds(p);
            List<String> parentIds = groups.parentIds(groupIds);
            for (String groupId : groupIds) nifi.cleanupProcessGroup(groupId);
            groups.pruneEmptyAncestors(parentIds, null);
        } catch (Exception e) {
            log.warn("cleanupProcessGroup({}) failed during undeploy: {}", p.nifiProcessGroupId(), e.getMessage());
            errors.add(p.id(), runtimeErr(FlowError.ErrorPhase.DEPLOYMENT,
                    "清理 NiFi Process Group 失败",
                    "PG " + p.nifiProcessGroupId() + " 清理失败：" + e.getMessage()
                            + "。请到 NiFi 原生 UI 手动停止并删除该 Process Group。"));
            return ResponseEntity.status(500).body(Map.of(
                    "undeployed", false,
                    "processGroupId", p.nifiProcessGroupId() == null ? "" : p.nifiProcessGroupId(),
                    "error", e.getMessage()));
        }
        repo.update(id, cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                cur.createdAt(), cur.updatedAt(), cur.dsl(),
                null, PipelineStatus.SAVED,
                null, null, cur.lastStoppedAt(),
                null, null));
        taskLifecycle.updateProcessGroup(id, null);
        taskLifecycle.statusOnly(id, 0);
        errors.clear(id);
        if (lineageAnalysis != null) {
            try { lineageAnalysis.retireDeployment(id); }
            catch (RuntimeException error) { log.warn("Unable to retire lineage snapshot for pipeline {}: {}", id, error.getMessage()); }
        }
        return ResponseEntity.ok(Map.of("undeployed", true));
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    /** Start the existing native group using its deployed execution policy. */
    private ResponseEntity<?> fastStart(Pipeline p, boolean boundOnly) {
        repo.updateStatus(p.id(), PipelineStatus.DEPLOYING);
        try {
            boolean ranOnce;
            if (boundOnly) {
                Set<String> oneShot = p.nodeMapping() == null ? Set.of() : p.nodeMapping().oneShotSources();
                if (oneShot == null) {
                    oneShot = Objects.equals(p.lastDeployedHash(), deploymentHash(p))
                            ? oneShotSourceIds(p, p.nodeMapping()) : Set.of();
                }
                ranOnce = !oneShot.isEmpty() && nifi.startWithOneShotSourcesIfPresent(p.nifiProcessGroupId(), oneShot);
                if (!ranOnce) {
                    if (isNifiAlreadyRunning(p)) return reconcileAlreadyRunning(p);
                    nifi.setProcessGroupState(p.nifiProcessGroupId(), "RUNNING");
                }
            } else {
                if (TargetTableCleanupService.requested(p)) targetCleanup.clear(p);
                ranOnce = startNativeFlow(p, p.nifiProcessGroupId(), p.nodeMapping());
            }
            Pipeline updated = repo.update(p.id(), cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                    cur.createdAt(), cur.updatedAt(), cur.dsl(),
                    cur.nifiProcessGroupId(), PipelineStatus.RUNNING,
                    cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(),
                    cur.nodeMapping(), cur.lastBulletinId()));
            taskLifecycle.started(p.id());
            return ResponseEntity.ok(Map.of(
                    "pipeline", updated,
                    "processGroupId", p.nifiProcessGroupId(),
                    "started", true,
                    "mode", "fast-restart",
                    "oneShot", ranOnce));
        } catch (NifiException e) {
            repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
            taskLifecycle.statusOnly(p.id(), 2);
            errors.add(p.id(), runtimeErr(FlowError.ErrorPhase.DEPLOYMENT, "快速启动失败", FlowFailureDetails.describe(e)));
            return errorResponse(e);
        } catch (IllegalStateException e) {
            if (boundOnly || !oneShotSourceIds(p, p.nodeMapping()).isEmpty() && !TargetTableCleanupService.requested(p)) {
                repo.updateStatus(p.id(), p.status());
                return ResponseEntity.status(409).body(Map.of("error", "full-source-not-ready", "message", e.getMessage()));
            }
            repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
            taskLifecycle.statusOnly(p.id(), 2);
            errors.add(p.id(), runtimeErr(FlowError.ErrorPhase.VALIDATION, "Target cleanup failed", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * A previous start can have reached NiFi successfully but failed while the
     * platform was issuing a redundant per-processor start.  Do not send a
     * second start request in that state: it only causes a 409 and leaves the
     * persisted lifecycle status out of sync with the live process group.
     */
    private boolean isNifiAlreadyRunning(Pipeline pipeline) {
        if (pipeline.nifiProcessGroupId() == null || pipeline.nifiProcessGroupId().isBlank()) return false;
        try {
            JsonNode raw = nifi.getProcessGroupStatus(pipeline.nifiProcessGroupId());
            return statusResolver.resolve(pipeline.status(), raw, pipeline.nifiProcessGroupId()) == PipelineStatus.RUNNING;
        } catch (Exception error) {
            log.debug("Unable to preflight NiFi runtime state for pipeline {}: {}", pipeline.id(), error.getMessage());
            return false;
        }
    }

    private java.util.Set<String> oneShotSourceIds(Pipeline p, NifiNodeMapping mapping) {
        if (mapping == null || mapping.primaryProcessorIds() == null || p.dsl() == null) return java.util.Set.of();
        return p.dsl().nodes().stream().filter(node -> "source".equals(node.category())
                && compiler.supportsSyncSettings(node.manifestKey()) && "FULL".equals(SyncPolicy.mode(node.config())))
                .map(node -> mapping.primaryProcessorIds().get(node.id())).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean startNativeFlow(Pipeline p, String groupId, NifiNodeMapping mapping) {
        Set<String> oneShot = oneShotSourceIds(p, mapping);
        if (!oneShot.isEmpty()) {
            nifi.startWithOneShotSources(groupId, oneShot);
            return true;
        }
        nifi.setProcessGroupState(groupId, "RUNNING");
        nifi.resumeStoppedProcessors(groupId);
        return false;
    }

    private ResponseEntity<?> reconcileAlreadyRunning(Pipeline pipeline) {
        errors.replacePhase(pipeline.id(), FlowError.ErrorPhase.DEPLOYMENT, List.of());
        Pipeline updated = repo.update(pipeline.id(), current -> new Pipeline(current.id(), current.name(), current.description(),
                current.createdAt(), current.updatedAt(), current.dsl(), current.nifiProcessGroupId(), PipelineStatus.RUNNING,
                current.lastDeployedHash(), current.lastDeployedAt(), current.lastStoppedAt(),
                current.nodeMapping(), current.lastBulletinId()));
        taskLifecycle.started(pipeline.id());
        return ResponseEntity.ok(Map.of(
                "pipeline", updated,
                "processGroupId", pipeline.nifiProcessGroupId(),
                "started", true,
                "mode", "already-running"));
    }

    private static String deploymentHash(Pipeline pipeline) {
        return DslHasher.deploymentHash(pipeline.dsl(), DEPLOYMENT_COMPILER_REVISION);
    }

    /**
     * The registration workflow owns the field-level governance rules.  Before
     * deciding whether a running graph can be reused, materialize any missing
     * rules into the saved DSL so the hash and the subsequently compiled graph
     * describe the same mapping contract.
     */
    private Pipeline bindAccessTaskMappings(Pipeline pipeline) {
        if (pipeline == null || accessTaskFieldMappingBinder == null) {
            return pipeline;
        }
        Pipeline bound = accessTaskFieldMappingBinder.bind(pipeline);
        if (bound == pipeline) {
            return pipeline;
        }
        return repo.update(pipeline.id(), current -> new Pipeline(current.id(), current.name(), current.description(),
                current.createdAt(), current.updatedAt(), bound.dsl(), current.nifiProcessGroupId(), current.status(),
                current.lastDeployedHash(), current.lastDeployedAt(), current.lastStoppedAt(),
                current.nodeMapping(), current.lastBulletinId()));
    }

    /**
     * 全量部署：
     * 1) 清理历史错误与状态；
     * 2) 如已存在旧 PG，先做 fail-fast 清理，避免产生孤儿 PG；
     * 3) 编译 DSL 并下发到 NiFi；
     * 4) 按 start 参数决定是否启动；
     * 5) 回写映射、hash、部署时间及任务调度状态。
     */
    private ResponseEntity<?> fullDeploy(Pipeline p, String currentHash, boolean start, DeployRequest request,
                                         boolean forceRebuild) {
        errors.replacePhase(p.id(), FlowError.ErrorPhase.VALIDATION, List.of());
        errors.replacePhase(p.id(), FlowError.ErrorPhase.DEPLOYMENT, List.of());
        errors.replacePhase(p.id(), FlowError.ErrorPhase.RUNTIME, List.of());
        repo.updateStatus(p.id(), PipelineStatus.DEPLOYING);

        try {
            taskLifecycle.ensureCatalogAccessTask(request == null ? null : request.catalogTid(), p);
            p = bindAccessTaskMappings(p);
            if(p.dsl().nodes().stream().anyMatch(com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy::isHiveSink)) {
                try { compiler.preflightGlobalRecordServices(p); }
                catch (RuntimeException ex) {
                    repo.updateStatus(p.id(),p.status());
                    return deploymentFailure(p.id(), 422, "HIVE_GLOBAL_SERVICES_UNAVAILABLE", "Hive 全局服务核验失败", ex,
                            "请检查当前 NiFi 根作用域的公共 Reader/Writer 服务；旧流程已保留。", Map.of());
                }
            }
            currentHash = deploymentHash(p);
            final Pipeline deploymentPipeline = p;
            final String deploymentHash = currentHash;
            PipelineGroupOrganizer.Layout layout = groups.prepare(p);
            if (!forceRebuild
                    && !(start && TargetTableCleanupService.requested(p))
                    && Objects.equals(currentHash, p.lastDeployedHash()) && groups.isCurrent(layout, p.nifiProcessGroupId())) {
                if (start) return fastStart(p, false);
                Pipeline restored = repo.updateStatus(p.id(), p.status());
                taskLifecycle.updateProcessGroup(p.id(), p.nifiProcessGroupId());
                return ResponseEntity.ok(Map.of("pipeline", restored, "processGroupId", p.nifiProcessGroupId(),
                        "started", p.status() == PipelineStatus.RUNNING, "mode", "reuse"));
            }
            try (var runtime = nifi.bindRuntimeNode()) {
            try { compiler.preflightSchedules(p); }
            catch (RuntimeException ex) {
                repo.updateStatus(p.id(), p.status());
                return deploymentFailure(p.id(), 422, "SCHEDULE_PREFLIGHT_FAILED", "调度配置发布前检查失败", ex,
                        "请先保存任务，并核对 NiFi 时区；旧流程已保留。", Map.of());
            }
            try { compiler.preflightSources(p); }
            catch (RuntimeException ex) {
                repo.updateStatus(p.id(), p.status());
                return deploymentFailure(p.id(), 422, "SOURCE_FETCH_PREFLIGHT_FAILED", "来源时间分片发布前检查失败", ex,
                        "请核对登记时间戳字段与 GenerateDateTableFetch 扩展；旧流程已保留。", Map.of());
            }
            try { compiler.preflightWriters(p); }
            catch (RuntimeException ex) {
                repo.updateStatus(p.id(), p.status());
                return deploymentFailure(p.id(), 422, "JDBC_WRITER_PREFLIGHT_FAILED", "目标表写入组件发布前检查失败", ex,
                        "请核对各 NiFi 节点已安装的 LinewellPutDatabaseRecord 扩展；系统自动选择最高已注册版本，旧流程已保留。", Map.of());
            }
            if (DslHasher.usesJavaMapping(p.dsl())) {
                // Reused graphs retain their existing bundle. Only a new unified
                // deployment needs plan/bundle checks before touching the old graph.
                try { compiler.preflightMapping(p); }
                catch (RuntimeException ex) {
                    repo.updateStatus(p.id(), p.status());
                    return deploymentFailure(p.id(), 422, "MAPPING_PREFLIGHT_FAILED", "字段映射发布前检查失败", ex,
                            "请根据详情核对字段映射配置、扩展版本及依赖服务；旧流程已保留。", Map.of());
                }
            }
            if (DslHasher.usesJavaMapping(p.dsl()) || hasHivePartitionPlan(p)
                    || p.dsl().nodes().stream().anyMatch(HiveSinkPublicationPolicy::supportsHdfsMerge)) {
                return stagedMappingDeploy(p, currentHash, start, layout);
            }
            List<String> previousParents = groups.parentIds(layout.previousIds());
            // Check every previous group before deleting any of them.
            for (String oldPg : layout.previousIds()) {
                nifi.requireEmptyQueuesForRedeploy(oldPg);
            }
            for (String oldPg : layout.previousIds()) {
                // ★ Fail-fast: must successfully clean up the OLD PG before creating a new one.
                // Otherwise we leak orphan PGs in NiFi and the mapping loses track of them.
                try {
                    nifi.cleanupProcessGroup(oldPg);
                } catch (Exception ce) {
                    log.error("Cleanup of old PG {} failed; aborting redeploy to avoid orphan PG", oldPg, ce);
                    repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
                    taskLifecycle.statusOnly(p.id(), 2);
                    errors.add(p.id(), runtimeErr(FlowError.ErrorPhase.DEPLOYMENT,
                            "无法清理旧 Process Group",
                            "旧 PG " + oldPg + " 清理失败：" + FlowFailureDetails.describe(ce)
                                    + "。已中止重新部署，避免在 NiFi 中产生孤儿 PG。"
                                    + "请在当前节点的 NiFi 原生画布中检查该组后重试。"));
                    return ResponseEntity.status(500).body(Map.of(
                            "error", "cleanup-failed",
                            "processGroupId", oldPg,
                            "detail", FlowFailureDetails.describe(ce)));
                }
            }
            groups.pruneEmptyAncestors(previousParents, layout.parentId());
            recordDeploymentGroup(p.id(), null);
            taskLifecycle.updateProcessGroup(p.id(), null);
            Consumer<String> onProcessGroupCreated = pgId -> {
                // Record the ID before creating processors so failure/retry cannot orphan the group.
                recordDeploymentGroup(deploymentPipeline.id(), pgId);
                taskLifecycle.updateProcessGroup(deploymentPipeline.id(), pgId);
            };
            DslCompiler.CompileResult res = compiler.compile(p, onProcessGroupCreated, layout);
            if (res == null || res.processGroupId() == null || res.processGroupId().isBlank()) {
                throw new IllegalStateException("流程编译未生成可部署的处理组，请检查流程配置后重试");
            }

            // Note: CS were enabled + waited inside compiler.compile() so that
            // processors are created against already-ENABLED services. We only
            // start the PG here.
            NifiNodeMapping mapping = buildMapping(p, res);
            if (start) {
                if (TargetTableCleanupService.requested(p)) targetCleanup.clear(p);
                startNativeFlow(p, res.processGroupId(), mapping);
            }

            long now = System.currentTimeMillis();
            Pipeline updated = repo.update(p.id(), cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                    cur.createdAt(), cur.updatedAt(), cur.dsl(),
                    res.processGroupId(),
                    start ? PipelineStatus.RUNNING : PipelineStatus.STOPPED,
                    deploymentHash, now, cur.lastStoppedAt(),
                    mapping, 0L));
            Map<String, Object> body = new HashMap<>();
            body.put("pipeline", updated);
            body.put("processGroupId", res.processGroupId());
            body.put("started", start);
            body.put("mode", "full-deploy");
            if (lineageAnalysis != null) {
                try { lineageAnalysis.captureDeployment(updated); }
                catch (RuntimeException error) {
                    log.warn("Unable to capture deployed lineage for pipeline {}: {}", p.id(), error.getMessage());
                    body.put("lineageIndexWarning", "流程已部署，但依赖快照暂未归档；请联系管理员检查血缘索引");
                }
            }
            if (start) {
                taskLifecycle.started(p.id());
            }
            if (!start) {
                taskLifecycle.statusOnly(p.id(), 0);
            }
            return ResponseEntity.ok(body);
            }
        } catch (IllegalStateException ise) {
            repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
            taskLifecycle.statusOnly(p.id(), 2);
            String nodeId = null;
            String nodeLabel = null;
            String fieldKey = null;
            if (ise instanceof DslCompiler.NodeConfigException nce) {
                nodeId = nce.nodeId();
                nodeLabel = nce.nodeLabel();
                fieldKey = nce.fieldKey();
            }
            errors.add(p.id(), new FlowError(null, FlowError.ErrorLevel.ERROR,
                    FlowError.ErrorPhase.VALIDATION, nodeId, nodeLabel, fieldKey,
                    FlowFailureDetails.safe(ise.getMessage() == null ? ise.getClass().getSimpleName() : ise.getMessage()),
                    FlowFailureDetails.describe(ise), "请检查节点配置/连线", Instant.now()));
            log.warn("Compile pipeline {} failed: {}", p.id(), ise.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", FlowFailureDetails.describe(ise)));
        } catch (NifiException e) {
            repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
            taskLifecycle.statusOnly(p.id(), 2);
            return deploymentFailure(p.id(), e.status() > 0 ? e.status() : 502, "NIFI_DEPLOYMENT_FAILED", "部署到 NiFi 失败", e,
                    "请根据 NiFi 响应检查处理器属性、Controller Service 和节点连接。", Map.of());
        } catch (Exception e) {
            repo.updateStatus(p.id(), PipelineStatus.DEPLOY_FAILED);
            taskLifecycle.statusOnly(p.id(), 2);
            log.error("Run pipeline {} failed", p.id(), e);
            return deploymentFailure(p.id(), 500, "DEPLOYMENT_FAILED", "部署异常", e,
                    "请根据错误详情检查部署配置。", Map.of());
        }
    }

    private static boolean hasHivePartitionPlan(Pipeline pipeline) {
        return pipeline.dsl().nodes().stream().anyMatch(node ->
                com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy.isHiveSink(node)
                        && node.config() != null && node.config().get("partitionPlan") instanceof Map<?, ?> plan
                        && Boolean.TRUE.equals(plan.get("enabled")));
    }

    /** A candidate graph never starts before validation; previous sources and queues remain recoverable. */
    private ResponseEntity<?> stagedMappingDeploy(Pipeline pipeline, String hash, boolean start,
                                                 PipelineGroupOrganizer.Layout layout) {
        boolean manualHiveTemplate = hasHivePartitionPlan(pipeline);
        boolean hiveMergeChange = pipeline.dsl().nodes().stream().anyMatch(HiveSinkPublicationPolicy::supportsHdfsMerge);
        final String[] staged = {null};
        boolean committed = false;
        boolean previousStopped = false;
        Set<String> stateIds = new LinkedHashSet<>();
        try {
            for (String group : layout.previousIds()) {
                JsonNode snapshot=nifi.getProcessGroupStatus(group).path("processGroupStatus").path("aggregateSnapshot");
                long queued=snapshot.path("queuedCount").asLong(snapshot.path("flowFilesQueued").asLong(-1));
                if(queued!=0)throw new IllegalStateException(queued<0?"无法确认旧流程队列状态，已保留旧图":"旧流程还有排队记录，已保留旧图并阻止重建");
            }
            // NiFi's public state API supports read/clear, but no generic state import.
            // Rebuilding an incremental source would lose its watermark even with an empty queue.
            if (!layout.previousIds().isEmpty()) {
                if (pipeline.nodeMapping() == null || pipeline.nodeMapping().processorIdToCanvasNode() == null) throw new IllegalStateException("旧流程缺少原生组件映射，不能证明来源状态可安全重建");
                stateIds.addAll(pipeline.nodeMapping().processorIdToCanvasNode().keySet());
                if (stateIds.size() > 128) throw new IllegalStateException("原生组件数量超过安全状态核验上限");
                for (String source : stateIds) {
                    JsonNode state = nifi.get("/processors/" + source + "/state", JsonNode.class).path("componentState");
                    if (!state.path("localState").path("state").isEmpty()
                            || !state.path("clusterState").path("state").isEmpty()) {
                        repo.updateStatus(pipeline.id(), pipeline.status());
                        return deploymentFailure(pipeline.id(), 409, "source-state-migration-required", "来源状态需要保留，已阻止流程重建",
                                new IllegalStateException("来源组件已有增量水位或文件状态。本次保留旧流程，请先采用能够保留来源组件ID的迁移方式。"),
                                "请保留来源组件 ID 和已有增量水位，不要通过清空状态或队列重试。", Map.of("sourceProcessorId", source, "previousGraphRetained", true));
                    }
                }
            }
            DslCompiler.CompileResult result = compiler.compile(pipeline, group -> staged[0] = group, layout);
            if (result == null || isBlank(result.processGroupId())) throw new IllegalStateException("候选流程编译失败");
            if (hiveMergeChange && !manualHiveTemplate) nifi.requireValidGeneratedProcessGroup(result.processGroupId());
            // Hive partition targets deliberately publish the ordinary template for native editing.
            // Keep candidate/queue/watermark protection, without automatic partition validation.
            NifiNodeMapping mapping = buildMapping(pipeline, result);
            for (String group : layout.previousIds()) {
                nifi.setProcessGroupState(group, "STOPPED");
                nifi.waitForAllProcessorsStopped(group, 30_000L);
                nifi.requireEmptyQueuesForRedeploy(group);
            }
            // Recheck after stop: sources may have advanced while the dormant graph was compiling.
            previousStopped = true;
            for (String nativeId : stateIds) {
                JsonNode state = nifi.get("/processors/" + nativeId + "/state",JsonNode.class).path("componentState");
                if (!state.path("localState").path("state").isEmpty() || !state.path("clusterState").path("state").isEmpty()) throw new IllegalStateException("来源状态在候选编译期间发生变化，已保留旧流程与水位");
            }
            long now = System.currentTimeMillis();
            Pipeline updated = repo.update(pipeline.id(), current -> new Pipeline(current.id(), current.name(), current.description(),
                    current.createdAt(), current.updatedAt(), current.dsl(), result.processGroupId(), PipelineStatus.STOPPED,
                    hash, now, current.lastStoppedAt(), mapping, 0L));
            if(mappingVersions!=null)mappingVersions.activate(pipeline,result.planVersionIds(),result.processGroupId());
            committed = true;
            taskLifecycle.updateProcessGroup(pipeline.id(), result.processGroupId());
            List<String> retained = new ArrayList<>();
            for (String group : layout.previousIds()) {
                try { nifi.cleanupProcessGroup(group); }
                catch (RuntimeException ex) { retained.add(group); log.warn("已停止旧流程保留待清理，pipeline={}, group={}",pipeline.id(),group); }
            }
            if (start) {
                if (TargetTableCleanupService.requested(pipeline)) targetCleanup.clear(pipeline);
                startNativeFlow(pipeline, result.processGroupId(), mapping);
                updated = repo.updateStatus(pipeline.id(), PipelineStatus.RUNNING);
                taskLifecycle.started(pipeline.id());
            } else taskLifecycle.statusOnly(pipeline.id(), 0);
            if (lineageAnalysis != null) { try { lineageAnalysis.captureDeployment(updated); } catch (RuntimeException ex) { log.warn("流程已部署，血缘索引归档待恢复，pipeline={}",pipeline.id()); } }
            Map<String,Object> response = new LinkedHashMap<>();response.put("pipeline",updated);response.put("processGroupId",result.processGroupId());response.put("started",start);
            response.put("mode", manualHiveTemplate ? "staged-hive-template-deploy"
                    : hiveMergeChange ? "staged-hive-merge-deploy" : "staged-unified-deploy");
            if (!retained.isEmpty()) response.put("retainedStoppedGroups",retained);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            if (!committed) {
                if (!isBlank(staged[0])) {
                    try { nifi.cleanupProcessGroup(staged[0]); }
                    catch (RuntimeException cleanup) { log.warn("候选流程清理待恢复，pipeline={}, group={}",pipeline.id(),staged[0]); }
                }
                repo.update(pipeline.id(), current -> new Pipeline(current.id(),current.name(),current.description(),current.createdAt(),current.updatedAt(),current.dsl(),
                        pipeline.nifiProcessGroupId(),pipeline.status(),pipeline.lastDeployedHash(),pipeline.lastDeployedAt(),pipeline.lastStoppedAt(),pipeline.nodeMapping(),pipeline.lastBulletinId()));
                if (previousStopped) repo.updateStatus(pipeline.id(),PipelineStatus.STOPPED);
            } else repo.updateStatus(pipeline.id(),PipelineStatus.STOPPED);
            return deploymentFailure(pipeline.id(), 409, manualHiveTemplate ? "hive-template-deployment-failed"
                            : hiveMergeChange ? "hive-merge-deployment-failed" : "mapping-deployment-failed",
                    manualHiveTemplate ? "Hive基础候选流程部署失败"
                            : hiveMergeChange ? "Hive小文件合并候选流程部署失败" : "统一映射候选流程部署失败", ex,
                    committed ? "请检查新流程状态及错误详情。" : "旧流程及其状态已保留，请根据详情修复后重新部署。",
                    Map.of("previousGraphRetained", !committed));
        }
    }

    /** Clear the previous deployment hash while persisting a new or removed group. */
    private void recordDeploymentGroup(String id, String groupId) {
        repo.update(id, cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                cur.createdAt(), cur.updatedAt(), cur.dsl(), groupId, PipelineStatus.DEPLOYING,
                null, null, cur.lastStoppedAt(), null, null));
    }

    /** Map canvas nodes to NiFi components for status and error lookup. */
    private NifiNodeMapping buildMapping(Pipeline pipeline, DslCompiler.CompileResult res) {
        Map<String, String> primary = new LinkedHashMap<>();
        Map<String, String> reverse = new LinkedHashMap<>();
        res.nodes().forEach((canvasId, nc) -> {
            String first = nc.processorIds.values().stream().findFirst().orElse(null);
            if (first != null) primary.put(canvasId, first);
            nc.processorIds.values().forEach(pid -> reverse.put(pid, canvasId));
        });
        Map<String, NifiNodeMapping.EdgeEndpoint> edgeEndpoints = new LinkedHashMap<>();
        if (pipeline.dsl() != null && pipeline.dsl().edges() != null) {
            for (Pipeline.Edge edge : pipeline.dsl().edges()) {
                DslCompiler.NodeCompilation src = res.nodes().get(edge.source());
                DslCompiler.NodeCompilation dst = res.nodes().get(edge.target());
                if (src == null || dst == null || dst.inletProcessorId == null) continue;
                DslCompiler.Outlet outlet = DslCompiler.resolveOutlet(src, edge.outlet());
                if (outlet == null) continue;
                String connectionId = res.edgeConnectionIds() == null ? null : res.edgeConnectionIds().get(edge.id());
                edgeEndpoints.put(edge.id(), new NifiNodeMapping.EdgeEndpoint(outlet.processorId(), dst.inletProcessorId, connectionId));
            }
        }
        return new NifiNodeMapping(primary, reverse, edgeEndpoints);
    }

    private void safeUndeploy(String pgId) {
        // Kept as a best-effort fallback for callers that explicitly want to ignore failures.
        // Prefer NifiClient.cleanupProcessGroup directly when fail-fast is required.
        try {
            nifi.cleanupProcessGroup(pgId);
        } catch (Exception e) {
            log.warn("cleanupProcessGroup({}) failed: {}", pgId, e.getMessage());
        }
    }

    private FlowError runtimeErr(FlowError.ErrorPhase phase, String message, String detail) {
        return new FlowError(null, FlowError.ErrorLevel.ERROR, phase,
                null, null, null, FlowFailureDetails.safe(message), FlowFailureDetails.safe(detail), null, Instant.now());
    }

    private ResponseEntity<?> deploymentFailure(String id, int status, String code, String message,
                                                Throwable cause, String suggestion, Map<String, Object> extra) {
        FlowError failure = new FlowError(null, FlowError.ErrorLevel.ERROR, FlowError.ErrorPhase.DEPLOYMENT,
                null, null, null, message, FlowFailureDetails.describe(cause), suggestion, Instant.now());
        FlowError recorded = errors.add(id, failure);
        Map<String, Object> body = new LinkedHashMap<>(extra);
        body.put("error", code);
        body.put("code", code);
        body.put("message", message);
        body.put("detail", failure.detail());
        body.put("flowError", recorded == null ? failure : recorded);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<?> errorResponse(NifiException e) {
        int status = e.status() > 0 ? e.status() : 502;
        Map<String, Object> body = new HashMap<>();
        body.put("error", FlowFailureDetails.describe(e));
        body.put("status", status);
        body.put("body", FlowFailureDetails.safe(e.body()));
        return ResponseEntity.status(status).body(body);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DeployRequest(String catalogTid, boolean preserveExisting) {
        public DeployRequest(String catalogTid) { this(catalogTid, false); }
    }

    public record CandidateRequest(String processGroupId) {}
}
