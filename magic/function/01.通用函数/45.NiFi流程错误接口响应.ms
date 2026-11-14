{
  "properties": {},
  "id": "nifi_flow_error_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "45.NiFi流程错误接口响应",
  "path": "/nifiFlowErrorHttp",
  "description": "处理当前认证行业租户的 NiFi 流程错误历史、实时公告和清理请求。flowId 来自路径，operation 由 API 入口固定。流程不可见时返回空体 404，未部署流程的实时公告请求返回 400。清理在共享跨进程锁内操作当前流程，NiFi 清理失败时报告降级结果。",
  "parameters": [
    {
      "name": "flowId",
      "description": "当前租户中的流程标识，来自 API 路径",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "operation",
      "description": "入口固定的操作类型 history、bulletins 或 clear",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as objectMapper
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as PipelineDsl
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.SecurityException
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tableRead
import tenantRuntime

// @/common/nifiFlowErrorHttp 处理两套 NiFi 错误路由的历史、实时公告和清理。
// flowId 来自路径，operation 由入口固定。当前行业租户只操作自己的有效流程。
// 查询返回原始数组，清理返回本地与原生结果，并与其他画布修改共用跨进程锁。

var rootCause = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

var loadPipeline = () => {
    // 只使用可信租户，按主键一次读取授权及原生组所需字段。
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var query = tableRead.from('nifi_pipeline_t', 'p', tenantId)
    query.where(tableRead.equal(tableRead.column('p', 'id', 'TEXT'), flowId))
    var rows = query.rows({
        processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
        deleted: tableRead.column('p', 'is_del', 'NUMBER'),
        dsl: tableRead.column('p', 'dsl_json', 'LOB'),
        mapping: tableRead.column('p', 'node_mapping_json', 'LOB')
    }, 1)
    if (rows.isEmpty() || rows[0].deleted == 1) {
        return null
    }

    // 旧画布允许缺省保存态，但无法解码的流程不提供历史、公告或清理。
    var pipeline = rows[0]
    try {
        if (pipeline.dsl != null && !pipeline.dsl.isBlank()) {
            objectMapper.readValue(pipeline.dsl, PipelineDsl)
        }
        if (pipeline.mapping != null && !pipeline.mapping.isBlank()) {
            objectMapper.readValue(pipeline.mapping, NifiNodeMapping)
        }
    } catch (failure) {
        if (rootCause(failure) instanceof JsonProcessingException) {
            return null
        }
        throw failure
    }
    return pipeline
}

var liveBulletins = processGroupId => {
    if (processGroupId == null || processGroupId.isBlank()) {
        return ResponseEntity.badRequest().body({error: '流程尚未部署'})
    }

    // 从保存的组标识发出一次原生读取，按原顺序仅保留 ERROR 和 WARNING。
    var bulletins = nifi.getBulletinBoard(processGroupId, 0L).path('bulletinBoard').path('bulletins')
    var result = []
    if (bulletins.isArray()) {
        for (item in bulletins.elements()) {
            var bulletin = item.path('bulletin')
            var level = bulletin.path('level').asText('')
            if ('ERROR'.equalsIgnoreCase(level) || 'WARNING'.equalsIgnoreCase(level)) {
                result.add({
                    id: item.path('id').asText(''),
                    level: level,
                    sourceId: bulletin.path('sourceId').asText(null),
                    sourceName: bulletin.path('sourceName').asText(''),
                    message: bulletin.path('message').asText(''),
                    timestamp: bulletin.path('timestampIso').asText(bulletin.path('timestamp').asText(''))
                })
            }
        }
    }
    return ResponseEntity.ok(result)
}

var clearHistory = processGroupId => {
    var cleared = 0
    var nativeCleared = true
    if (processGroupId != null && !processGroupId.isBlank()) {
        try {
            var runtime = nifi.bindRuntimeNode()
            var operationFailure = null
            try {
                cleared = nifi.clearGroupBulletins(processGroupId, Instant.now())
            } catch (failure) {
                operationFailure = rootCause(failure)
            }

            // 保留原生操作的主异常，将关闭异常附加到主异常后再按原异常类型处理。
            if (runtime != null) {
                try {
                    runtime.close()
                } catch (closeFailure) {
                    if (operationFailure == null) {
                        throw closeFailure
                    }
                    operationFailure.addSuppressed(rootCause(closeFailure))
                }
            }
            if (operationFailure != null) {
                throw operationFailure
            }
        } catch (failure) {
            // 原生协议错误允许清除本地历史，其他错误会中止并按原契约返回诊断。
            if (!(rootCause(failure) instanceof NifiException)) {
                throw failure
            }
            nativeCleared = false
        }
    }
    errorStore.clear(flowId)
    return ResponseEntity.ok({historyCleared: true, bulletinsCleared: cleared, nativeCleared: nativeCleared})
}

try {
    if (operation == 'clear') {
        return mutation.withMutationLock(() => {
            // 与原画布写操作一致，持有锁后才核对流程归属并清理。
            var pipeline = loadPipeline()
            return pipeline == null ? ResponseEntity.notFound().build() : clearHistory(pipeline.processGroupId)
        })
    }
    if (operation != 'history' && operation != 'bulletins') {
        throw new IllegalArgumentException('不支持的流程错误操作')
    }
    var pipeline = loadPipeline()
    if (pipeline == null) {
        return ResponseEntity.notFound().build()
    }
    return operation == 'history' ? ResponseEntity.ok(errorStore.list(flowId)) : liveBulletins(pipeline.processGroupId)
} catch (failure) {
    // 业务异常继续使用原 Spring MVC 的响应信封。原始状态分支已在操作中返回。
    var cause = rootCause(failure)
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(handler.handleTenantAccessException(cause))
    }
    if (cause instanceof SecurityException) {
        return ResponseEntity.ok(handler.handleSecurityException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(handler.handleException(cause))
    }
    throw failure
}
