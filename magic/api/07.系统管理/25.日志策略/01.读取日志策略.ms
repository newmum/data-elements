{
  "id": "tenant_logpolicy_list_get_v1",
  "groupId": "sys_logpolicy_v1",
  "name": "01.读取日志策略",
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "paths": [],
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
  "description": "读取行业日志策略，补齐各类别默认值并沿用原排序。"
}
================================
import '@/common/logresponse' as respond
import '@/common/loggrants' as grants
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.Double
import java.lang.Number
import java.lang.SecurityException
import java.util.List
import java.util.Locale
import tableRead
import tenantRuntime
import request

// GET /sym/logpolicy/list。校验当前行业的完整权限并读取策略，按原规则补齐类别及排序。
return respond(() => {
    var http = request
    var tenant = tenantRuntime.id()
    var actor = StpUtil.getLoginIdAsString()
    var application = StpUtil.getTokenSession().get('appId')
    var mode = grants(actor, application == null ? '' : application + '', 'policies').modes.get('policies')
    if (mode == null) { throw new SecurityException('没有此日志模块的访问权限') }
    if (mode != 'ALL') { throw new SecurityException('此操作需要日志模块的完整范围授权') }

    var categories = {AUTH: '登录日志', OPERATION: '操作日志', REQUEST: '接口日志', SECURITY: '安全事件', TASK: '任务与集成'}
    var column = (name, type) => tableRead.column('p', name, type)
    var category = column('category', 'TEXT')
    var query = tableRead.from('log_policy_t', 'p', tenant)
    query.where(tableRead.equal(column('scope_kind', 'TEXT'), 'TENANT'))
    query.where(tableRead.equal(column('scope_id', 'TEXT'), tenant))
    query.where(tableRead.equal(column('identity_domain', 'TEXT'), 'global'))
    query.where(tableRead.in(category, List.copyOf(categories.keySet())))
    var fields = {category: category, updated_time: column('updated_time', 'TIME')}
    for (name in ['actor_name', 'actor_account', 'actor_org_name']) { fields.put(name, column(name, 'TEXT')) }
    for (name in ['enabled', 'hot_days', 'total_retention_days', 'sample_rate', 'detail_limit_bytes', 'archive_enabled', 'version']) {
        fields.put(name, column(name, 'NUMBER'))
    }
    var saved = {}
    query.forEachOrderedRow(fields, [category], ['asc'], true, row => saved.put(row.category, row))

    var text = (name, fallback, limit) => {
        var values = http.getValues(name)
        if (values == null || values.isEmpty()) { return fallback }
        var value = values[0]
        if (value.length() > limit) { throw new IllegalArgumentException('日志查询内容过长') }
        return value
    }
    var field = text('sortField', 'category', 40)
    var direction = text('sortDir', 'asc', 4)
    if (!['category', 'hot_days', 'total_retention_days', 'sample_rate', 'actor_name', 'updated_time'].contains(field)
        || !['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序参数无效')
    }
    var keyword = text('keyword', '', 100).toLowerCase(Locale.ROOT)
    var result = []
    for (name in categories.keySet()) {
        if (keyword.isBlank() || name.toLowerCase(Locale.ROOT).contains(keyword) || categories.get(name).contains(keyword)) {
            var row = {category: name, enabled: 1, hot_days: name == 'REQUEST' ? 30 : 180,
                total_retention_days: name == 'REQUEST' ? 90 : 365, sample_rate: 1,
                detail_limit_bytes: 32768, archive_enabled: 1, version: 0}
            if (saved.get(name) != null) { row.putAll(saved.get(name)) }
            if (row.updated_time != null) { row.put('updated_time', LogTimes.instant(row.updated_time).toString()) }
            result.add(row)
        }
    }
    // 与旧响应一致，任意排序方向均将没有快照的默认值放在最后。
    return result.sort((left, right) => {
        var a = left.get(field)
        var b = right.get(field)
        if (a == null) { return b == null ? 0 : 1 }
        if (b == null) { return -1 }
        var compared = a instanceof Number && b instanceof Number ? Double.compare(a.doubleValue(), b.doubleValue())
            : field == 'updated_time' ? LogTimes.instant(a).compareTo(LogTimes.instant(b)) : a.toString().compareTo(b.toString())
        return direction == 'desc' ? -compared : compared
    })
})
