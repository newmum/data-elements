{
  "id": "tenant_logarchive_create_post_v1",
  "groupId": "sys_logarchive_v1",
  "name": "02.提交日志归档",
  "path": "/create",
  "method": "POST",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "验证当前行业归档权限、事件范围及在线保留期限，在事务内登记新代次并提交归档文件任务。"
}
================================
import '@/common/loggrants' as grants
import '@/common/logeventcriteria' as eventCriteria
import '@/common/logresponse' as respond
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.feature.logmanagement.LogScope
import com.linewell.dataelement.platform.audit.AuditTrace
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.SecurityException
import java.time.Duration
import java.time.Instant
import java.util.Map
import auditEvents
import tableRead
import tenantRuntime

// POST /sym/logarchive/create。先校验会话、日志来源和保留期，再登记归档批次。后台只生成文件和校验清单。
return respond(() => {
    var tenant = tenantRuntime.id()
    var actor = StpUtil.getLoginIdAsString()
    var application = StpUtil.getTokenSession().get('appId')
    var appId = application == null ? '' : application + ''
    var permission = grants(actor, appId, null)
    var mode = permission.modes.get('archives')
    if (mode == null) { throw new SecurityException('没有此日志模块的访问权限') }
    if (mode != 'ALL') { throw new SecurityException('此操作需要日志模块的完整范围授权') }
    if (!(body instanceof Map)) { throw new IllegalArgumentException('请求体必须是 JSON 对象') }
    var kind = body.get('kind') == null ? '' : body.get('kind') + ''
    if (kind.length() > 20) { throw new IllegalArgumentException('日志查询内容过长') }
    var categories = {auth: 'AUTH', operations: 'OPERATION', requests: 'REQUEST', security: 'SECURITY'}
    var category = categories.get(kind)
    if (category == null) { throw new IllegalArgumentException('请选择日志类别') }
    if (permission.modes.get(kind) == null) { throw new SecurityException('没有此日志模块的访问权限') }
    var criteria = eventCriteria(body, tenant, kind)
    if (body.get('startTime') == null || body.get('endTime') == null) {
        throw new IllegalArgumentException('请选择归档时间区间')
    }
    var end = Instant.parse(body.get('endTime') + '')
    var column = (name, type) => tableRead.column('a', name, type)
    var scope = query => {
        query.where(tableRead.equal(column('scope_kind', 'TEXT'), 'TENANT'))
        query.where(tableRead.equal(column('scope_id', 'TEXT'), tenant))
        query.where(tableRead.equal(column('identity_domain', 'TEXT'), 'global'))
        return query
    }
    var policies = scope(tableRead.from('log_policy_t', 'a', tenant))
    policies.where(tableRead.equal(column('category', 'TEXT'), category))
    var policy = policies.grouped({hot: tableRead.max(column('hot_days', 'NUMBER')),
        retained: tableRead.max(column('total_retention_days', 'NUMBER'))}, [])[0]
    var hot = policy.hot == null ? (kind == 'requests' ? 30 : 180) : policy.hot
    var retained = policy.retained == null ? (kind == 'requests' ? 90 : 365) : policy.retained
    if (end.isAfter(Instant.now().minus(Duration.ofDays(hot)))) {
        throw new IllegalArgumentException('归档区间仍在在线保留期内')
    }

    var startTime = LogTimes.utc(criteria.start)
    var endTime = LogTimes.utc(criteria.end)
    var identity = SpringUtil.getBean('auditRecorder').current()
    var files = SpringUtil.getBean('logLifecycleService')
    var id = AuditTrace.random()
    var created = LogTimes.utc(Instant.now())
    var row = {tid: id, tenant_id: tenant, scope_kind: 'TENANT', scope_id: tenant, identity_domain: 'global',
        event_store: kind, range_start: startTime, range_end: endTime, status: 'PREPARING',
        record_count: 0, byte_count: 0, hold_status: 'NONE', retention_until: LogTimes.utc(end.plus(Duration.ofDays(retained))),
        operator_id: actor, actor_name: identity.name(), actor_account: identity.account(), actor_org_name: identity.orgName(),
        created_time: created, updated_time: created, version: 1}
    row.putAll(files.leaseFields())
    db.transaction(() => {
        // 只读取当前租户同存储同时间窗，唯一约束保护并发提交的代次。
        var batches = scope(tableRead.from('log_archive_batch_t', 'a', tenant))
        batches.where(tableRead.equal(column('event_store', 'TEXT'), kind))
        batches.where(tableRead.equal(column('range_start', 'TIME'), startTime))
        batches.where(tableRead.equal(column('range_end', 'TIME'), endTime))
        var previous = batches.grouped({generation: tableRead.max(column('generation', 'NUMBER'))}, [])[0].generation
        row.put('generation', previous == null ? 1 : previous + 1)
        db.table('log_archive_batch_t').primary('tid').insert(row)
        var event = auditEvents.prepare('OPERATION', 'LOG_ARCHIVE_SUBMITTED', 'ACCEPTED', 'LOG_ARCHIVE', id, '日志归档',
            {category: category})
        db.table(event.table).primary('tid').insert(event.row)
    })
    var query = {kind: kind, receivedBefore: Instant.now().toString()}
    for (name in ['result', 'actorId', 'orgId', 'traceId', 'requestId', 'keyword', 'parameterKeyword',
        'parameterName', 'parameterValue', 'startTime', 'endTime']) {
        if (body.get(name) != null) { query.put(name, body.get(name)) }
    }
    var archiveScope = new LogScope(tenant, actor, appId, null, null, 'ALL', [], 'archives')
    files.enqueueArchive(archiveScope, id, kind, query)
    return {id: id, status: 'PREPARING'}
})
