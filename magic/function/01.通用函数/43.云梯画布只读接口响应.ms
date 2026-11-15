{
  "properties": {},
  "id": "flowserve_canvas_read_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "43.云梯画布只读接口响应",
  "path": "/flowServeCanvasReadHttp",
  "description": "为云梯画布流程详情和运行节点的 GET 与 HEAD 返回原业务信封。flowRequested 为真时校验 flowId、有效服务关联和角色范围，为假时读取当前租户最多 100 个节点并只投影部署令牌设置状态。无业务写入或外部请求。",
  "parameters": [
    {
      "name": "flowRequested",
      "description": "是否读取流程详情，否时读取运行节点",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    },
    {
      "name": "flowId",
      "description": "流程标识，读取流程时取路径参数，长度为 1 至 64",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.ObjectMapper as objectMapper
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.IllegalArgumentException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import dataScope
import tableRead
import tenantRuntime

// @/common/flowServeCanvasReadHttp 为云梯画布流程详情和运行节点提供 GET 与 HEAD 响应。
// flowRequested 为真时按 flowId 返回已保存的流程、状态、锁版本及更新时间，为假时返回运行节点配置。
// 使用可信行业租户，详情额外校验当前角色的关联服务范围。两类入口均只读，无外部请求。

var failureCause = error => {
    var cause = error
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

var readFlow = tenantId => {
    if (flowId == null || flowId.isBlank() || flowId.length() > 64) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, '流程标识无效')
    }

    // 两种有效服务关联沿用相同角色范围，超限部门在执行流程查询前拒绝。
    var scope = dataScope.dataSource()
    if (scope.scope == 'ORG' && scope.organizationIds.size() > 1000) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, '部门数据范围超出查询上限，请联系系统维护人员')
    }
    var serviceConditions = alias => {
        var conditions = [tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)]
        if (scope.scope == 'OWNER') {
            conditions.add(tableRead.equal(tableRead.column(alias, 'created_by', 'TEXT'), scope.userId))
        } else if (scope.scope == 'ORG') {
            conditions.add(tableRead.in(tableRead.column(alias, 'org_id', 'TEXT'), scope.organizationIds))
        }
        return conditions
    }

    // 主表按当前租户及流程主键读取，关联表自动限定同租户。任一有效服务匹配即允许读取该流程。
    var query = tableRead.from('fs_flow', 'f', tenantId)
    query.where(tableRead.equal(tableRead.column('f', 'id', 'TEXT'), flowId))
    query.where(tableRead.equal(tableRead.column('f', 'deleted', 'NUMBER'), 0))
    query.leftJoin('api_info_t', 'by_flow', 'flow_id', 'id', serviceConditions('by_flow'))
    query.leftJoin('api_info_t', 'by_id', 'tid', 'id', serviceConditions('by_id'))
    query.where(tableRead.any([
        tableRead.not(tableRead.isNull(tableRead.column('by_flow', 'tid', 'TEXT'))),
        tableRead.not(tableRead.isNull(tableRead.column('by_id', 'tid', 'TEXT')))
    ]))
    var rows = query.rows({
        dsl: tableRead.column('f', 'dsl', 'LOB'),
        status: tableRead.column('f', 'status', 'TEXT'),
        lock_version: tableRead.column('f', 'lock_version', 'NUMBER'),
        updated_at: tableRead.column('f', 'updated_at', 'TIME')
    }, 1)
    if (rows.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, '服务流程不存在或不在当前数据范围内')
    }

    // 解析现行 DSL，对照路径标识；历史损坏定义和缺失状态字段沿用原冲突提示。
    var row = rows[0]
    try {
        var flow = objectMapper.readTree(row.dsl == null ? 'null' : row.dsl.toString())
        if (!flow.isObject() || !flowId.equals(flow.path('flowId').asText())) {
            throw new IllegalArgumentException('流程标识与编排内容不一致')
        }
        if (row.status == null || row.lock_version == null || row.updated_at == null) {
            throw new IllegalArgumentException('流程状态、锁版本或更新时间缺失')
        }
        return {flow: flow, status: row.status, lockVersion: row.lock_version, updatedAt: row.updated_at.toString()}
    } catch (error) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            '服务编排内容无效，请检查该服务的已保存流程', failureCause(error))
    }
}

var readNodes = tenantId => {
    var query = tableRead.from('fs_runtime_node', 'n', tenantId)
    var column = (name, type) => tableRead.column('n', name, type)
    var token = column('deploy_token', 'TEXT')
    var tokenMissing = tableRead.any([tableRead.isNull(token), tableRead.equal(token, '')])
    var defaultNode = column('default_node', 'NUMBER')
    var name = column('name', 'TEXT')
    var id = column('id', 'TEXT')

    // 一次读取有序窗口，第 101 项只用于识别超限。令牌只参与数据库表达式。
    var rows = query.rows({
        id: id,
        name: name,
        business_domain: column('business_domain', 'TEXT'),
        service_type: column('service_type', 'TEXT'),
        base_url: column('base_url', 'TEXT'),
        management_url: column('management_url', 'TEXT'),
        deploy_token_set: tableRead.when(tokenMissing, 0, 1),
        local_node: column('local_node', 'NUMBER'),
        default_node: defaultNode,
        enabled: column('enabled', 'NUMBER'),
        created_at: column('created_at', 'TIME')
    }, [defaultNode, name, id], ['desc', 'asc', 'asc'], 101, true)
    if (rows.size() > 100) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, '当前租户运行节点超过100项，请先整理运行节点配置')
    }

    // 明确输出契约字段，兼容驱动的布尔值表示并排除分页辅助列。
    var flag = value => Boolean.TRUE.equals(value) || '1'.equals(value == null ? null : value.toString())
    var result = []
    for (row in rows) {
        result.add({
            id: row.id,
            name: row.name,
            businessDomain: row.business_domain,
            serviceType: row.service_type,
            baseUrl: row.base_url,
            managementUrl: row.management_url,
            deployTokenSet: flag(row.deploy_token_set),
            localNode: flag(row.local_node),
            defaultNode: flag(row.default_node),
            enabled: flag(row.enabled),
            createdAt: row.created_at
        })
    }
    return result
}

try {
    // 请求字段均不参与确定租户。沿用 Spring 的业务信封与内容协商。
    var tenantId = tenantRuntime.id()
    var data = flowRequested ? readFlow(tenantId) : readNodes(tenantId)
    return ResponseEntity.ok(CommonResponse.success(data))
} catch (error) {
    var cause = failureCause(error)
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(handler.handleTenantAccessException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(handler.handleException(cause))
    }
    throw error
}
