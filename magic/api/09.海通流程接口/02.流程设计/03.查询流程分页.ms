{
  "properties": {},
  "id": "nifi_pipeline_page_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "03.查询流程分页",
  "path": "/page",
  "method": "GET",
  "description": "分页读取认证行业租户的有效 NiFi 流程。page 缺省 1，size 缺省 20 并限制到 1 至 100。支持 sortField、sortDir 和按名称及说明搜索的 q。返回原始 list、total、page、size，非法整数沿用参数类型错误契约。只读业务库，不请求 NiFi。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineReadHttp' as withHttpResponse
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Math
import java.util.Locale
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils
import tableRead
import tableSort
import tenantRuntime

// 按页读取当前行业租户的有效流程，支持名称和说明搜索。仅查询业务库。
// 返回原始 list、total、page、size，越界页保留页码并返回空列表。

return withHttpResponse((parameterValues, textParameter, rootCause) => {
    var integerParameter = (name, defaultValue) => {
        var values = parameterValues(name)
        // 单个空值使用默认值。重复参数沿用 Spring 数组到整数的首值转换。
        if (values == null || values.isEmpty() || (values.size() == 1 && values[0] == '')) {
            return defaultValue
        }
        try {
            return NumberUtils.parseNumber(values[0], Integer)
        } catch (failure) {
            if (!(rootCause(failure) instanceof IllegalArgumentException)) {
                throw failure
            }
            return null
        }
    }
    var invalidInteger = name => ResponseEntity.ok(CommonResponse.fail(
        StatusCodeEnum.PARAMS_UNVALID.getCode(),
        '参数[' + name + ']类型错误，期望类型: int',
        '参数类型不匹配'
    ))

    // 按旧入口的绑定顺序先校验页码和页大小，再解析可信行业租户。
    var page = integerParameter('page', 1)
    if (page == null) {
        return invalidInteger('page')
    }
    var size = integerParameter('size', 20)
    if (size == null) {
        return invalidInteger('size')
    }

    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new IllegalStateException('流程查询必须绑定行业租户')
    }
    var sortField = textParameter('sortField')
    var sortDir = textParameter('sortDir')
    var q = textParameter('q')
    var sortRequest = {
        sortField: sortField == null || sortField.isBlank() ? null : sortField,
        sortDir: sortDir == null || sortDir.isBlank() ? null : sortDir
    }
    var sortFields = {name: 'name', nodeCount: 'nodeCount', createdAt: 'createdAt', updatedAt: 'updatedAt'}
    var sortKey = tableSort.field(sortRequest, sortFields, 'updatedAt')
    var direction = tableSort.direction(sortRequest)
    var pageNumber = Math.max(1, page)
    var pageSize = Math.min(100, Math.max(1, size))
    var keyword = q == null ? '' : q.trim().toLowerCase(Locale.ROOT)
    if (keyword.length() > 200) {
        throw new IllegalArgumentException('搜索内容最多 200 个字符')
    }

    var flows = tableRead.from('nifi_pipeline_t', 'p', tenantId)
    var name = tableRead.column('p', 'name', 'TEXT')
    var description = tableRead.column('p', 'description', 'TEXT')
    flows.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
    // 空关键词不附加条件，避免空字符串被解释成 NULL。搜索按字面匹配名称和说明。
    if (!keyword.isEmpty()) {
        var text = tableRead.lower(tableRead.concatWsNative(' ', [name, description]))
        flows.where(tableRead.hasSubstring(text, keyword))
    }

    // 以当前租户的有效画布分组统计节点，供数据库端排序和页内投影，总数查询跳过此关联。
    var source = tableRead.from('nifi_pipeline_t', 'flow', tenantId)
    source.where(tableRead.equal(tableRead.column('flow', 'is_del', 'NUMBER'), 0))
    var flowId = tableRead.column('flow', 'id', 'TEXT')
    var ordinal = source.leftJoinJsonArray('dsl_json', 'nodes', 'node')
    flows.leftJoinGroupedProjection(source, 'nodes', 'flowId', 'id', {
        flowId: flowId,
        nodeCount: tableRead.count(ordinal)
    }, [flowId])

    var projection = {
        id: tableRead.column('p', 'id', 'TEXT'),
        name: name,
        description: description,
        nifiProcessGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
        createdAt: tableRead.column('p', 'created_at', 'NUMBER'),
        updatedAt: tableRead.column('p', 'updated_at', 'NUMBER'),
        nodeCount: tableRead.coalesce([tableRead.column('nodes', 'nodeCount', 'NUMBER'), 0])
    }
    // 完整筛选集先按请求字段排序，空值末置，流程 ID 决定同值行的稳定顺序。
    var result = flows.page(projection, projection[sortKey], direction, 'id', pageNumber, pageSize)
    // 只返回既有接口字段，去掉达梦分页内部辅助列。
    var list = result.rows.map(row => {
        return {
            id: row.id,
            name: row.name,
            description: row.description,
            nifiProcessGroupId: row.nifiProcessGroupId,
            createdAt: row.createdAt,
            updatedAt: row.updatedAt,
            nodeCount: row.nodeCount
        }
    })
    return ResponseEntity.ok({list: list, total: result.total, page: pageNumber, size: pageSize})
})
