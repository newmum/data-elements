package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineRuntimeStatusResolver;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.TargetTableCleanupService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.function.FailableSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class NifiDeploymentMagicFixture {
    private static final String COMMON = "function/01.通用函数/";
    private static final String API = "api/10.海通流程接口/";
    private static final String TENANT = "deployment-validation";
    private final PipelineRepository repository;
    private final PipelineTaskLifecycleSynchronizer tasks;
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final MagicScript deploy = script(COMMON + "56.海通部署画布.ms", "nifi_canvas_deploy_v1");
    private final MagicScript bind = script(COMMON + "54.海通部署候选绑定.ms", "nifi_candidate_bind_v1");
    private final MagicScript respond = script(COMMON + "53.海通生命周期错误响应.ms", "nifi_lifecycle_response_v1");
    private final MagicScript activate = script(API + "06.部署与启停/02.切换候选流程.ms", "nifi_candidate_activate_post_v1");
    private final MagicScript start = script(COMMON + "58.海通启动流程.ms", "nifi_start_canvas_v1");
    private final MagicScript stop = script(COMMON + "59.海通停止流程.ms", "nifi_stop_canvas_v1");
    private final MagicScript undeploy = script(API + "06.部署与启停/05.卸载流程.ms", "nifi_pipeline_undeploy_post_v1");
    private final MagicScript oneShot = script(COMMON + "57.海通一次性来源处理器.ms", "nifi_one_shot_sources_v1");

    NifiDeploymentMagicFixture(PipelineRepository repository, DslCompiler compiler, NifiClient nifi,
            ErrorService errors, PipelineTaskLifecycleSynchronizer tasks, PipelineGroupOrganizer groups,
            TargetTableCleanupService cleanup) {
        this(repository, compiler, nifi, errors, tasks, groups, cleanup, false);
    }

    NifiDeploymentMagicFixture(PipelineRepository repository, DslCompiler compiler, NifiClient nifi,
            ErrorService errors, PipelineTaskLifecycleSynchronizer tasks, PipelineGroupOrganizer groups,
            TargetTableCleanupService cleanup, boolean nativeScope) {
        this.repository = repository;
        this.tasks = tasks;
        bindings.putAll(Map.of("compiler", compiler, "nifi", nifi, "errorStore", errors, "tasks", tasks,
                "groups", groups, "targetCleanup", new Cleanup(cleanup), "tenantRuntime", new TenantMagicModule(null),
                "lineage", mock(com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService.class),
                "versions", mock(com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore.class),
                "mappingBinder", new MappingBinder()));
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        bindings.put("transactions", transactions);
        bindings.put("mappingVersions", bindings.get("versions"));
        bindings.put("mutation", new Lock());
        bindings.put("statusResolver", nativeScope ? new PipelineRuntimeStatusResolver(nifi) : mock(PipelineRuntimeStatusResolver.class));
        bindings.put("organizer", groups);
        bindings.put("tableRead", new TaskGroups());
        if (nativeScope) {
            var deletionScope = script(COMMON + "46.海通删除校验原生组.ms", "40fabd244d0a1e4572f9e8803b25be8d");
            bindings.put("groupScope", (MagicScriptLambdaFunction) (ctx, args) -> evaluate(deletionScope,
                    Map.of("targets", args[0], "rootId", args[1], "owner", args[2])));
        } else {
            bindings.put("groupScope", (MagicScriptLambdaFunction) (ctx, args) -> ((List<?>) args[0]).stream()
                    .map(target -> Map.of("id", ((Map<?, ?>) target).get("id"), "parentId", "")).toList());
        }
        bindings.put("findCanvas", (MagicScriptLambdaFunction) (ctx, args) -> repository.findById((String) args[0]).orElse(null));
        bindings.put("syncTask", (MagicScriptLambdaFunction) (ctx, args) -> {
            tasks.syncState((String) args[0], (String) args[1], args[2]);
            return null;
        });
        bindings.put("writeRuntime", (MagicScriptLambdaFunction) (ctx, args) -> write((String) args[0], (Map<?, ?>) args[2]));
        bindings.put("respond", (MagicScriptLambdaFunction) (ctx, args) -> evaluate(respond, Map.of("action", args[0])));
        bindings.put("bindCandidate", (MagicScriptLambdaFunction) (ctx, args) -> evaluate(bind,
                Map.of("canvas", args[0], "candidate", args[1], "tenantId", args[2])));
        bindings.put("deploy", (MagicScriptLambdaFunction) (ctx, args) -> deploy((String) args[0], (Map<?, ?>) args[1],
                Boolean.TRUE.equals(args[2])));
        bindings.put("oneShotSources", (MagicScriptLambdaFunction) (ctx, args) -> {
            var arguments = new LinkedHashMap<String, Object>();
            arguments.put("canvas", args[0]);
            arguments.put("primary", args[1]);
            return evaluate(oneShot, arguments);
        });
    }

    ResponseEntity<?> deploy(String id, Map<?, ?> options, boolean lockHeld) {
        var arguments = new LinkedHashMap<String, Object>();
        arguments.put("pipelineId", id);
        arguments.put("options", options);
        arguments.put("lockHeld", lockHeld);
        String tenant = TenantContext.getTenantId();
        return TenantContext.call(tenant == null ? TENANT : tenant, () -> (ResponseEntity<?>) evaluate(deploy, arguments));
    }

    ResponseEntity<?> activate(String id, String group) {
        return TenantContext.call(TENANT, () -> (ResponseEntity<?>) evaluate(activate, Map.of("param", Map.of("id", id),
                "body", Map.of(), "readBody", (MagicScriptLambdaFunction) (ctx, args) ->
                        new com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiDeploymentParameters.CandidateRequest(group))));
    }

    ResponseEntity<?> start(String id) {
        return start(id, false, false);
    }

    ResponseEntity<?> start(String id, boolean force) {
        return start(id, force, false);
    }

    ResponseEntity<?> start(String id, boolean force, boolean confirmCleanup) {
        return lifecycle(start, Map.of("pipelineId", id, "force", force, "confirmCleanup", confirmCleanup));
    }

    ResponseEntity<?> stop(String id) {
        return lifecycle(stop, Map.of("pipelineId", id));
    }

    ResponseEntity<?> undeploy(String id) {
        return lifecycle(undeploy, Map.of("param", Map.of("id", id)));
    }

    private ResponseEntity<?> lifecycle(MagicScript command, Map<String, ?> args) {
        var tenant = TenantContext.getTenantId();
        return TenantContext.call(tenant == null ? TENANT : tenant, () -> (ResponseEntity<?>) evaluate(command, args));
    }

    private Object evaluate(MagicScript script, Map<String, ?> arguments) {
        var variables = new LinkedHashMap<>(bindings);
        variables.putAll(arguments);
        return script.execute(new MagicScriptContext(variables));
    }

    private Pipeline write(String id, Map<?, ?> changes) {
        if (changes.size() == 1 && changes.get("status") instanceof PipelineStatus status) {
            return repository.updateStatus(id, status);
        }
        return repository.update(id, current -> {
            if (changes.containsKey("expectedDesignHash") && !Objects.equals(changes.get("expectedDesignHash"), current.designHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "流程已被其他入口修改，请重新加载后重试");
            }
            return new Pipeline(current.id(), current.name(), current.description(), current.createdAt(), current.updatedAt(),
                    value(changes, "dsl", current.dsl()), value(changes, "groupId", current.nifiProcessGroupId()),
                    value(changes, "status", current.status()), value(changes, "deployedHash", current.lastDeployedHash()),
                    value(changes, "deployedAt", current.lastDeployedAt()), value(changes, "stoppedAt", current.lastStoppedAt()),
                    value(changes, "mapping", current.nodeMapping()), value(changes, "lastBulletinId", current.lastBulletinId()));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Map<?, ?> changes, String key, T fallback) {
        return changes.containsKey(key) ? (T) changes.get(key) : fallback;
    }

    private static MagicScript script(String path, String id) {
        try {
            String source = CanonicalMagicSources.byPath(path, id);
            MagicScript.create(source, null).compile();
            return MagicScript.create(source.replaceAll("(?m)^import (?:'@/[^']+' as \\w+|(?:tableRead|tableWrite|tenantRuntime)|[^\\r\\n]+ as (?:lineage|compiler|errorStore|mutation|groups|tasks|mappingBinder|versions|nifi|transactions|mappingVersions|targetCleanup|statusResolver|organizer))\\R", ""), null);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Lock {
        public Object withMutationLock(FailableSupplier<Object, Throwable> action) throws Throwable {
            return action.get();
        }
    }

    public static final class TaskGroups {
        public TaskGroups from(String table, String alias, String tenant) { return this; }
        public String column(String alias, String column, String kind) { return column; }
        public Object equal(String column, Object value) { return value; }
        public void where(Object predicate) { }
        public List<Map<String, Object>> rows(Map<?, ?> columns, List<?> order, List<?> direction, int limit, boolean nullLast) {
            return List.of();
        }
    }

    public static final class MappingBinder {
        public Pipeline bind(Pipeline pipeline) {
            return pipeline;
        }
    }

    public record Cleanup(TargetTableCleanupService service) {
        public boolean requested(Pipeline pipeline) {
            return TargetTableCleanupService.requested(pipeline);
        }

        public void clear(Pipeline pipeline) {
            service.clear(pipeline);
        }
    }
}
