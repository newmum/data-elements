{
  "properties": {},
  "id": "nifi_canvas_deploy_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "56.海通部署画布",
  "path": "/nifiDeployCanvas",
  "description": "供画布部署、登记表按需部署和强制启动共用，校验画布、原生组、队列和失败补偿。",
  "parameters": [
    {
      "name": "pipelineId",
      "description": "已授权的画布标识",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "options",
      "description": "部署方式及可选目录",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "lockHeld",
      "description": "由内部强制启动传入的写锁状态",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiBindCandidate' as bindCandidate
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleResponse' as respond
import '@/common/nifiOneShotSources' as oneShotSources
import '@/common/nifiTaskState' as syncTask
import '@/common/nifiWriteRuntime' as writeRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService as lineage
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler as compiler
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler$NodeConfigException as NodeConfigException
import com.linewell.dataelement.integration.nifi.canvas.compile.SyncPolicy
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorLevel as ErrorLevel
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorPhase as ErrorPhase
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer as groups
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer as tasks
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.TargetTableCleanupService as targetCleanup
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder as mappingBinder
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore as versions
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping$Candidate as Candidate
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping$EdgeEndpoint as EdgeEndpoint
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalStateException
import java.lang.System
import java.time.Instant
import java.util.LinkedHashSet
import java.util.Locale
import java.util.Map
import java.util.Objects
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.support.TransactionTemplate as transactions
import org.springframework.web.server.ResponseStatusException
import tenantRuntime

// @/common/nifiDeployCanvas 由画布部署 API 和登记表启停的按需部署共用。
// 复核可信行业租户及流程归属，在共享 NiFi 锁内核验设计、组件能力、队列和来源状态。
// 候选分支保留当前运行组。切换失败时，按数据库提交状态决定清理候选组或保留新关联。

return respond(rootCause => {
    var execute = () => {
        var tenantId = tenantRuntime.id()
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
        }
        var saved = findCanvas(pipelineId, tenantId)
        if (saved == null) {
            return ResponseEntity.notFound().build()
        }
        var discardQueueFor = options?.discardQueueFor
        if (discardQueueFor != null && (options?.forceRebuild != true || options?.startAfterDeploy == true
            || options?.preserveExisting == true || discardQueueFor != saved.nifiProcessGroupId())) {
            return ResponseEntity.status(409).body({error: '旧流程关联已改变，请刷新并重新确认清空范围'})
        }

        var log = LoggerFactory.getLogger('com.linewell.dataelement.integration.nifi.canvas.Deployment')
        var attributeSync = new NifiAttributeSync(nifi)
        var update = changes => writeRuntime(pipelineId, tenantId, changes)
        var setStatus = status => update({status: status})
        var blank = value => value == null || value.isBlank()
        var hash = canvas => DslHasher.deploymentHash(canvas.dsl(), DslHasher.CURRENT_COMPILER_REVISION)
        var start = options?.startAfterDeploy == true
        var startNative = (canvas, groupId, mapping) => {
            if (targetCleanup.requested(canvas)) {
                targetCleanup.clear(canvas)
            }
            var oneShot = oneShotSources(canvas, mapping?.primaryProcessorIds())
            if (!oneShot.isEmpty()) {
                nifi.startWithOneShotSources(groupId, oneShot)
            } else {
                nifi.setProcessGroupState(groupId, 'RUNNING')
                nifi.resumeStoppedProcessors(groupId)
            }
        }
        var phase = ErrorPhase.DEPLOYMENT
        var clearErrors = () => {
            errorStore.replacePhase(pipelineId, ErrorPhase.VALIDATION, [])
            errorStore.replacePhase(pipelineId, phase, [])
        }
        var writeError = (errorPhase, message, detail) => errorStore.add(pipelineId,
            new FlowError(null, ErrorLevel.ERROR, errorPhase, null, null, null,
                FlowFailureDetails.safe(message), FlowFailureDetails.safe(detail), null, Instant.now()))
        var failureResponse = (http, code, message, failure, suggestion, extra) => {
            var info = new FlowError(null, ErrorLevel.ERROR, phase, null, null, null, message,
                FlowFailureDetails.describe(failure), suggestion, Instant.now())
            var recorded = errorStore.add(pipelineId, info)
            extra.putAll({error: code, code: code, message: message, detail: info.detail(), flowError: recorded == null ? info : recorded})
            return ResponseEntity.status(http).body(extra)
        }
        var result = (canvas, groupId, mode) => ({pipeline: canvas, processGroupId: groupId, mode: mode, started: false})
        var usableGroup = (compiled, message) => {
            if (compiled == null || blank(compiled.processGroupId())) {
                throw new IllegalStateException(message)
            }
            return compiled.processGroupId()
        }
        var cleanupCandidate = groupId => {
            if (!blank(groupId)) {
                try {
                    nifi.cleanupProcessGroup(groupId)
                } catch (failure) {
                    log.warn('待清理的 NiFi 候选组，group={}', groupId)
                }
            }
        }

        var buildMapping = (canvas, compiled, attributesFrom, includeRuntimePolicy) => {
            var primary = {}
            var reverse = {}
            for (id, node in compiled.nodes()) {
                for (processor in node.processorIds.values()) {
                    if (!primary.containsKey(id)) {
                        primary.put(id, processor)
                    }
                    reverse.put(processor, id)
                }
            }
            var edges = {}
            for (edge in canvas.dsl()?.edges() || []) {
                var source = compiled.nodes().get(edge.source())
                var target = compiled.nodes().get(edge.target())
                if (source != null && target != null && target.inletProcessorId != null) {
                    var outlet = compiler.resolveOutlet(source, edge.outlet())
                    if (outlet != null) {
                        edges.put(edge.id(), new EdgeEndpoint(outlet.processorId(), target.inletProcessorId,
                            compiled.edgeConnectionIds()?.get(edge.id())))
                    }
                }
            }
            var mapping = new NifiNodeMapping(primary, reverse, edges)
            try {
                mapping = mapping.withAttributes(attributeSync.capture(attributesFrom.dsl(), compiled.processGroupId(), mapping))
            } catch (failure) {
                log.warn('NiFi 属性基准暂不可读取，pipeline={}, group={}', pipelineId, compiled.processGroupId())
            }
            if (includeRuntimePolicy) {
                mapping = mapping.withRuntimePolicy(oneShotSources(canvas, primary), SyncPolicy.needsCleanupConfirmation(canvas))
            }
            return mapping
        }

        if (options?.preserveExisting == true) {
            var pending = {groupId: null}
            var runtime = null
            try {
                runtime = nifi.bindRuntimeNode()
                // 模板只初始化一次增量字段。候选编译不得回写用户手工选择。
                var independentSources = ['source.api', 'source.ftp', 'source.sftp', 'source.kafka', 'source.minio',
                    'source.hdfs', 'source.hbase', 'source.elasticsearch', 'source.iotdb']
                for (node in saved.dsl()?.nodes() || []) {
                    if (node.category() == 'source' && node.config() != null && !independentSources.contains(node.manifestKey())) {
                        var mode = Objects.toString(node.config().getOrDefault('syncMode', '')).trim().toUpperCase(Locale.ROOT)
                        var column = Objects.toString(node.config().getOrDefault('incrementalColumn', '')).trim()
                        if (['INCREMENTAL', 'INCR', 'FULL_THEN_INCR'].contains(mode) && (column.isBlank() || column == 'null')) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, '来源节点缺少主增量字段，请手工填写后保存，或选择全量同步')
                        }
                    }
                }
                var existing = saved.nodeMapping()?.candidate()
                if (existing != null && saved.designHash() == existing.designHash()) {
                    try {
                        nifi.getProcessGroup(existing.processGroupId())
                        clearErrors()
                        return ResponseEntity.ok(result(saved, existing.processGroupId(), 'candidate-created'))
                    } catch (failure) {
                        var cause = rootCause(failure)
                        if (!(cause instanceof NifiException) || cause.status() != 404) {
                            throw failure
                        }
                    }
                }
                tasks.ensureCatalogAccessTask(options.catalogTid, saved)
                var design = mappingBinder.bind(saved)
                var layout = groups.prepareCandidate(design)
                var compiled = compiler.compile(design, groupId => { pending.groupId = groupId }, layout)
                pending.groupId = usableGroup(compiled, '未生成可用的 NiFi 流程组')
                var candidate = new Candidate(pending.groupId, hash(design), saved.designHash(),
                    buildMapping(design, compiled, saved, true), compiled.planVersionIds())
                var response = transactions.execute(transaction => {
                    if (blank(saved.nifiProcessGroupId())) {
                        return result(bindCandidate(saved, candidate, tenantId), candidate.processGroupId(), 'first-deploy')
                    }
                    var mapping = saved.nodeMapping() == null ? new NifiNodeMapping(null, null, null) : saved.nodeMapping()
                    return result(update({expectedDesignHash: saved.designHash(), mapping: mapping.withCandidate(candidate)}),
                        candidate.processGroupId(), 'candidate-created')
                })
                pending.groupId = null
                clearErrors()
                return ResponseEntity.ok(response)
            } catch (failure) {
                cleanupCandidate(pending.groupId)
                var cause = rootCause(failure)
                var status = cause instanceof NifiException && cause.status() > 0 ? cause.status() : 422
                return failureResponse(status, 'CANDIDATE_DEPLOYMENT_FAILED', '生成 NiFi 流程失败', cause,
                    '请检查本次生成所需的组件和配置。', {previousGraphRetained: true})
            } finally {
                if (runtime != null) {
                    runtime.close()
                }
            }
        }

        clearErrors()
        errorStore.replacePhase(pipelineId, ErrorPhase.RUNTIME, [])
        setStatus(PipelineStatus.DEPLOYING)
        var design = saved
        try {
            tasks.ensureCatalogAccessTask(options?.catalogTid, design)
            var bound = mappingBinder.bind(design)
            if (bound != design) {
                design = update({expectedDesignHash: design.designHash(), dsl: bound.dsl()})
            }
            var hiveTarget = false
            var hivePartition = false
            var hiveMerge = false
            for (node in design.dsl().nodes()) {
                if (HiveSinkPublicationPolicy.isHiveSink(node)) {
                    hiveTarget = true
                    var partitionPlan = node.config()?.get('partitionPlan')
                    if (partitionPlan instanceof Map && partitionPlan.get('enabled') == true) {
                        hivePartition = true
                    }
                }
                if (HiveSinkPublicationPolicy.supportsHdfsMerge(node)) {
                    hiveMerge = true
                }
            }
            var preflight = (check, code, message, suggestion, extra) => {
                try {
                    check()
                    return null
                } catch (failure) {
                    setStatus(saved.status())
                    return failureResponse(422, code, message, rootCause(failure), suggestion, extra)
                }
            }
            if (hiveTarget) {
                var error = preflight(() => compiler.preflightGlobalRecordServices(design), 'HIVE_GLOBAL_SERVICES_UNAVAILABLE',
                    'Hive 全局服务核验失败', '请检查当前 NiFi 根作用域的公共 Reader/Writer 服务；旧流程已保留。', {})
                if (error != null) {
                    return error
                }
            }
            var deploymentHash = hash(design)
            var layout = groups.prepare(design)
            if (!start && options?.forceRebuild != true && Objects.equals(deploymentHash, design.lastDeployedHash())
                && groups.isCurrent(layout, design.nifiProcessGroupId())) {
                var restored = setStatus(saved.status())
                syncTask(pipelineId, 'group', design.nifiProcessGroupId())
                var reuse = result(restored, design.nifiProcessGroupId(), 'reuse')
                reuse.started = saved.status() == PipelineStatus.RUNNING
                return ResponseEntity.ok(reuse)
            }

            var runtime = nifi.bindRuntimeNode()
            try {
                var preserveOld = () => ({previousGraphRetained: true})
                if (!hiveTarget) {
                    var error = preflight(() => compiler.preflightSqlRecordWriter(design), 'NIFI_SQL_RECORD_WRITER_UNAVAILABLE',
                        'SQL 采集 Writer 核验失败', '请核对当前 NiFi 根作用域已启用 PUBLIC-WRITER-JSON-TIME_FORMAT。', preserveOld())
                    if (error != null) {
                        return error
                    }
                }
                var checks = [
                    {run: () => compiler.preflightSchedules(design), code: 'SCHEDULE_PREFLIGHT_FAILED', message: '调度配置发布前检查失败',
                        hint: '请先保存任务，并核对 NiFi 时区；旧流程已保留。'},
                    {run: () => compiler.preflightSources(design), code: 'SOURCE_FETCH_PREFLIGHT_FAILED', message: '来源时间分片发布前检查失败',
                        hint: '请核对登记时间戳字段与 GenerateDateTableFetch 扩展；旧流程已保留。'},
                    {run: () => compiler.preflightWriters(design), code: 'JDBC_WRITER_PREFLIGHT_FAILED', message: '目标表写入组件发布前检查失败',
                        hint: '请核对各 NiFi 节点已安装的 LinewellPutDatabaseRecord 扩展；系统自动选择最高已注册版本，旧流程已保留。'}
                ]
                var javaMapping = DslHasher.usesJavaMapping(design.dsl())
                if (javaMapping) {
                    checks.add({run: () => compiler.preflightMapping(design), code: 'MAPPING_PREFLIGHT_FAILED', message: '字段映射发布前检查失败',
                        hint: '请根据详情核对字段映射配置、扩展版本及依赖服务；旧流程已保留。'})
                }
                for (check in checks) {
                    var error = preflight(check.run, check.code, check.message, check.hint, preserveOld())
                    if (error != null) {
                        return error
                    }
                }

                if (javaMapping || hivePartition || hiveMerge || discardQueueFor != null) {
                    var staged = {groupId: null}
                    var committed = false
                    var previousStopped = false
                    var stateIds = new LinkedHashSet()
                    var hasState = processor => {
                        var state = nifi.get('/processors/' + processor + '/state', JsonNode).path('componentState')
                        return !state.path('localState').path('state').isEmpty() || !state.path('clusterState').path('state').isEmpty()
                    }
                    try {
                        // 编译前检查全部旧组队列与原生来源状态，阻止重建导致已采集记录或水位丢失。
                        for (groupId in layout.previousIds()) {
                            var aggregate = nifi.getProcessGroupStatus(groupId).path('processGroupStatus').path('aggregateSnapshot')
                            var queued = aggregate.path('queuedCount').asLong(aggregate.path('flowFilesQueued').asLong(-1L))
                            if (queued < 0 || (queued > 0 && groupId != discardQueueFor)) {
                                throw new IllegalStateException(queued < 0 ? '无法确认旧流程队列状态，已保留旧图' : '旧流程还有排队记录，已保留旧图并阻止重建')
                            }
                        }
                        if (!layout.previousIds().isEmpty()) {
                            var processors = design.nodeMapping()?.processorIdToCanvasNode()
                            if (processors == null) {
                                throw new IllegalStateException('旧流程缺少原生组件映射，不能证明来源状态可安全重建')
                            }
                            stateIds.addAll(processors.keySet())
                            if (stateIds.size() > 128) {
                                throw new IllegalStateException('原生组件数量超过安全状态核验上限')
                            }
                            for (processor in stateIds) {
                                if (hasState(processor)) {
                                    setStatus(saved.status())
                                    return failureResponse(409, 'source-state-migration-required', '来源状态需要保留，已阻止流程重建',
                                        new IllegalStateException('来源组件已有增量水位或文件状态。本次保留旧流程，请先采用能够保留来源组件ID的迁移方式。'),
                                        '请保留来源组件 ID 和已有增量水位，不要通过清空状态或队列重试。', {sourceProcessorId: processor, previousGraphRetained: true})
                                }
                            }
                        }
                        var compiled = compiler.compile(design, groupId => { staged.groupId = groupId }, layout)
                        staged.groupId = usableGroup(compiled, '候选流程编译失败')
                        if ((hiveMerge && !hivePartition) || discardQueueFor != null) {
                            nifi.requireValidGeneratedProcessGroup(staged.groupId)
                        }
                        var mapping = buildMapping(design, compiled, design, false)
                        for (groupId in layout.previousIds()) {
                            nifi.setProcessGroupState(groupId, 'STOPPED')
                            nifi.waitForAllProcessorsStopped(groupId, 30000L)
                            if (groupId != discardQueueFor) {
                                nifi.requireEmptyQueuesForRedeploy(groupId)
                            }
                        }
                        previousStopped = true
                        for (processor in stateIds) {
                            if (hasState(processor)) {
                                throw new IllegalStateException('来源状态在候选编译期间发生变化，已保留旧流程与水位')
                            }
                        }
                        var updated = transactions.execute(transaction => {
                            var after = update({expectedDesignHash: design.designHash(), groupId: staged.groupId, status: PipelineStatus.STOPPED,
                                deployedHash: deploymentHash, deployedAt: System.currentTimeMillis(), mapping: mapping, lastBulletinId: 0L})
                            versions.activate(design, compiled.planVersionIds(), staged.groupId)
                            return after
                        })
                        committed = true
                        syncTask(pipelineId, 'group', staged.groupId)
                        var retained = []
                        for (groupId in layout.previousIds()) {
                            try {
                                nifi.cleanupProcessGroup(groupId)
                            } catch (failure) {
                                retained.add(groupId)
                                log.warn('已停止旧流程保留待清理，pipeline={}, group={}', pipelineId, groupId)
                            }
                        }
                        if (start) {
                            startNative(design, staged.groupId, mapping)
                            updated = setStatus(PipelineStatus.RUNNING)
                            syncTask(pipelineId, 'started', null)
                        } else {
                            syncTask(pipelineId, 'status', 0)
                        }
                        try {
                            lineage.captureDeployment(updated)
                        } catch (failure) {
                            log.warn('流程已部署，血缘索引归档待恢复，pipeline={}', pipelineId)
                        }
                        var response = result(updated, staged.groupId,
                            hivePartition ? 'staged-hive-template-deploy' : hiveMerge ? 'staged-hive-merge-deploy'
                                : javaMapping ? 'staged-unified-deploy' : 'full-deploy')
                        response.started = start
                        if (!retained.isEmpty()) {
                            response.retainedStoppedGroups = retained
                        }
                        return ResponseEntity.ok(response)
                    } catch (failure) {
                        if (!committed) {
                            cleanupCandidate(staged.groupId)
                            update({groupId: design.nifiProcessGroupId(), status: previousStopped ? PipelineStatus.STOPPED : saved.status(),
                                deployedHash: design.lastDeployedHash(), deployedAt: design.lastDeployedAt(), stoppedAt: design.lastStoppedAt(),
                                mapping: design.nodeMapping(), lastBulletinId: design.lastBulletinId()})
                        } else {
                            setStatus(PipelineStatus.STOPPED)
                        }
                        return failureResponse(409, hivePartition ? 'hive-template-deployment-failed'
                            : hiveMerge ? 'hive-merge-deployment-failed' : javaMapping ? 'mapping-deployment-failed' : 'redeploy-failed',
                            hivePartition ? 'Hive基础候选流程部署失败' : hiveMerge ? 'Hive小文件合并候选流程部署失败'
                                : javaMapping ? '统一映射候选流程部署失败' : '重部署未完成',
                            rootCause(failure), committed ? '请检查新流程状态及错误详情。' : '旧流程及其状态已保留，请根据详情修复后重新部署。',
                            {previousGraphRetained: !committed})
                    }
                }

                var previousParents = groups.parentIds(layout.previousIds())
                // 常规重部署先核验全部旧组队列，再清理旧组。新组 ID 在建节点前写入，失败后可重试。
                for (groupId in layout.previousIds()) {
                    nifi.requireEmptyQueuesForRedeploy(groupId)
                }
                for (groupId in layout.previousIds()) {
                    try {
                        nifi.cleanupProcessGroup(groupId)
                    } catch (failure) {
                        var detail = FlowFailureDetails.describe(rootCause(failure))
                        setStatus(PipelineStatus.DEPLOY_FAILED)
                        syncTask(pipelineId, 'status', 2)
                        writeError(phase, '无法清理旧 Process Group', '旧 PG ' + groupId + ' 清理失败：' + detail
                            + '。已中止重新部署，避免在 NiFi 中产生孤儿 PG。请在当前节点的 NiFi 原生画布中检查该组后重试。')
                        return ResponseEntity.status(500).body({error: 'cleanup-failed', processGroupId: groupId, detail: detail})
                    }
                }
                groups.pruneEmptyAncestors(previousParents, layout.parentId())
                var recordGroup = groupId => {
                    update({groupId: groupId, status: PipelineStatus.DEPLOYING, deployedHash: null, deployedAt: null, mapping: null, lastBulletinId: null})
                    syncTask(pipelineId, 'group', groupId)
                }
                recordGroup(null)
                var compiled = compiler.compile(design, recordGroup, layout)
                var groupId = usableGroup(compiled, '流程编译未生成可部署的处理组，请检查流程配置后重试')
                var mapping = buildMapping(design, compiled, design, false)
                if (start) {
                    startNative(design, groupId, mapping)
                }
                var updated = update({expectedDesignHash: design.designHash(), groupId: groupId,
                    status: start ? PipelineStatus.RUNNING : PipelineStatus.STOPPED, deployedHash: deploymentHash,
                    deployedAt: System.currentTimeMillis(), mapping: mapping, lastBulletinId: 0L})
                var response = result(updated, groupId, 'full-deploy')
                response.started = start
                try {
                    lineage.captureDeployment(updated)
                } catch (failure) {
                    log.warn('无法归档部署血缘，pipeline={}', pipelineId)
                    response.lineageIndexWarning = '流程已部署，但依赖快照暂未归档；请联系管理员检查血缘索引'
                }
                if (start) {
                    syncTask(pipelineId, 'started', null)
                } else {
                    syncTask(pipelineId, 'status', 0)
                }
                return ResponseEntity.ok(response)
            } finally {
                if (runtime != null) {
                    runtime.close()
                }
            }
        } catch (failure) {
            var cause = rootCause(failure)
            setStatus(PipelineStatus.DEPLOY_FAILED)
            syncTask(pipelineId, 'status', 2)
            if (cause instanceof IllegalStateException) {
                var nodeError = cause instanceof NodeConfigException ? cause : null
                var details = new FlowError(null, ErrorLevel.ERROR, ErrorPhase.VALIDATION, nodeError?.nodeId(), nodeError?.nodeLabel(),
                    nodeError?.fieldKey(), FlowFailureDetails.safe(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
                    FlowFailureDetails.describe(cause), '请检查节点配置/连线', Instant.now())
                errorStore.add(pipelineId, details)
                return ResponseEntity.badRequest().body({error: FlowFailureDetails.describe(cause)})
            }
            if (cause instanceof NifiException) {
                return failureResponse(cause.status() > 0 ? cause.status() : 502, 'NIFI_DEPLOYMENT_FAILED', '部署到 NiFi 失败', cause,
                    '请根据 NiFi 响应检查处理器属性、Controller Service 和节点连接。', {})
            }
            return failureResponse(500, 'DEPLOYMENT_FAILED', '部署异常', cause, '请根据错误详情检查部署配置。', {})
        }
    }
    // 强制启动由已有启动入口在同一锁内调用。直接部署和登记表调用在这里加锁。
    return lockHeld == true ? execute() : mutation.withMutationLock(execute)
})
