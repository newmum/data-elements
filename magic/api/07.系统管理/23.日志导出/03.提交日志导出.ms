{
  "id": "tenant_logexport_create_post_v1",
  "groupId": "sys_logexport_v1",
  "name": "03.提交日志导出",
  "path": "/create",
  "method": "POST",
  "parameters": [],
  "paths": [],
  "options": [
    {"name": "access", "value": "TENANT_SESSION"}
  ],
  "description": "验证当前行业的导出和源日志权限，提交受范围、行数与时间限制的 CSV 或 JSONL 文件任务。"
}
================================
import '@/common/loggrants' as grants
import '@/common/logeventcriteria' as eventCriteria
import '@/common/logresponse' as respond
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.feature.logmanagement.LogScope
import com.linewell.dataelement.platform.audit.AuditTrace
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.Long
import java.lang.SecurityException
import java.time.Instant
import java.util.Map
import auditEvents
import tenantRuntime

// POST /sym/logexport/create。行业授权与输入校验在提交前完成，任务和审计同事务，文件队列在提交后启动。
return respond(() => {
    var tenant = tenantRuntime.id()
    var actor = StpUtil.getLoginIdAsString()
    var application = StpUtil.getTokenSession().get('appId')
    var appId = application == null ? '' : application + ''
    var permission = grants(actor, appId, null)
    var exportMode = permission.modes.get('exports')
    if (exportMode == null) { throw new SecurityException('没有此日志模块的访问权限') }
    if (!(body instanceof Map)) { throw new IllegalArgumentException('请求体必须是 JSON 对象') }
    var text = (value, maximum) => {
        var rendered = value == null ? '' : value + ''
        if (rendered.length() > maximum) { throw new IllegalArgumentException('日志查询内容过长') }
        return rendered
    }
    var kind = text(body.get('kind'), 20)
    if (!['auth', 'operations', 'requests', 'security'].contains(kind)) {
        throw new IllegalArgumentException('请选择日志类别')
    }
    var readMode = permission.modes.get(kind)
    if (readMode == null) { throw new SecurityException('没有此日志模块的访问权限') }

    // 两类授权取交集。若角色分别限制至本人及可见部门，后台文件同时满足两个条件。
    var owner = readMode == 'OWNER' || exportMode == 'OWNER'
    var org = readMode == 'ORG' || exportMode == 'ORG'
    var mode = owner && org ? 'OWNER_AND_ORG' : owner ? 'OWNER' : org ? 'ORG' : 'ALL'
    var capability = exportMode == 'ALL' && readMode != 'ALL' ? kind : 'exports'
    var scope = new LogScope(tenant, actor, appId, null, null, mode, org ? permission.orgIds : [], capability)
    eventCriteria(body, tenant, kind)
    var format = text(body.getOrDefault('format', 'CSV'), 10)
    if (!['CSV', 'JSONL'].contains(format)) { throw new IllegalArgumentException('导出格式无效') }
    var limit = 100000
    try {
        if (body.get('rowLimit') != null) { limit = Long.parseLong(body.get('rowLimit') + '') }
        if (limit < 1 || limit > 100000) { throw new IllegalArgumentException('分页参数无效') }
    } catch (error) { throw new IllegalArgumentException('分页参数无效') }

    var cutoff = Instant.now()
    // 保存后台会实际读取的条件，忽略与筛选无关的请求字段。
    var query = {kind: kind}
    for (name in ['result', 'actorId', 'orgId', 'traceId', 'requestId', 'keyword', 'parameterKeyword',
        'parameterName', 'parameterValue', 'startTime']) {
        if (body.get(name) != null) { query.put(name, body.get(name)) }
    }
    query.put('endTime', cutoff.toString())
    query.put('receivedBefore', cutoff.toString())
    if (body.get('endTime') != null && Instant.parse(body.get('endTime') + '').isBefore(cutoff)) {
        query.put('endTime', body.get('endTime'))
    }
    var mapper = SpringUtil.getBean(ObjectMapper.class)
    var identity = SpringUtil.getBean('auditRecorder').current()
    var files = SpringUtil.getBean('logLifecycleService')
    var id = AuditTrace.random()
    var created = LogTimes.utc(Instant.now())
    var row = {tid: id, tenant_id: tenant, scope_kind: 'TENANT', scope_id: tenant, identity_domain: 'global',
        owner_kind: 'TENANT_USER', owner_id: actor, actor_name: identity.name(), actor_account: identity.account(),
        actor_org_name: identity.orgName(), query_json: mapper.writeValueAsString(query), scope_snapshot_json: mapper.writeValueAsString(scope),
        cutoff_time: LogTimes.utc(cutoff), status: 'PENDING', format_code: format, row_limit: limit,
        row_count: 0, created_time: created, updated_time: created, version: 1}
    row.putAll(files.leaseFields())
    db.transaction(() => {
        db.table('log_export_job_t').primary('tid').insert(row)
        var event = auditEvents.prepare('OPERATION', 'LOG_EXPORT_SUBMITTED', 'ACCEPTED', 'LOG_EXPORT', id, '日志导出',
            {format: format, count: limit})
        db.table(event.table).primary('tid').insert(event.row)
    })
    files.enqueueExport(scope, id, kind, query, format, limit)
    return {id: id, status: 'PENDING'}
})
