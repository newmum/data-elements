{
  "id": "log_tenant_event_query_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "89.构造行业日志事件查询",
  "path": "/logeventquery",
  "parameters": [
    {"name": "kind", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "detailed", "required": true, "dataType": "Boolean", "type": "java.lang.Boolean"}
  ],
  "options": [],
  "description": "为行业日志分页、统计、详情和批读构造受信任会话范围限定的查询及公开列，详情才投影安全快照。"
}
================================
import '@/common/loggrants' as grants
import cn.dev33.satoken.stp.StpUtil
import java.lang.SecurityException
import tableRead
import tenantRuntime

// /common/logeventquery 供四类行业事件读取共用。日志类别由入口固定，租户、账号和应用来自当前会话。
var tenant = tenantRuntime.id()
var actor = StpUtil.getLoginIdAsString()
var application = StpUtil.getTokenSession().get('appId')
var permission = grants(actor, application == null ? '' : application + '', kind)
var mode = permission.modes.get(kind)
if (mode == null) {
    throw new SecurityException('没有此日志模块的访问权限')
}

var relation = kind == 'auth' ? 'log_user_login_t' : kind == 'requests' ? 'log_request_t' : 'log_system_t'
var query = tableRead.from(relation, 'e', tenant).withTimeoutSeconds(15)
var column = (name, type) => tableRead.column('e', name, type)
var category = column('category', 'TEXT')
var actorId = column(kind == 'auth' ? 'created_by' : 'user_id', 'TEXT')
var orgId = column('org_id', 'TEXT')
if (mode == 'OWNER') {
    query.where(tableRead.equal(actorId, actor))
}
if (mode == 'ORG') {
    query.where(tableRead.in(orgId, permission.orgIds))
}
if (kind == 'security') {
    query.where(tableRead.equal(category, 'SECURITY'))
} else if (kind == 'operations') {
    query.where(tableRead.any([tableRead.isNull(category), tableRead.in(category, ['OPERATION', 'TASK'])]))
}

// 一次投影人、部门及操作快照。参数检索列只用于筛选，禁止进入任何响应。
var fields = {}
for (name in ['tid', 'category', 'actor_kind', 'menu_id', 'menu_path', 'page_route', 'operation_name',
    'function_code', 'action_code', 'target_type', 'target_id', 'target_name', 'result', 'reason_code',
    'ip', 'user_agent', 'trace_id', 'request_id', 'session_ref', 'business_code', 'source_app',
    'batch_id', 'task_id', 'route_path']) {
    fields.put(name, column(name, 'TEXT'))
}
for (name in ['duration_ms', 'http_status', 'total_count', 'success_count', 'failure_count']) {
    fields.put(name, column(name, 'NUMBER'))
}
for (name in ['event_time', 'created_time']) {
    fields.put(name, column(name, 'TIME'))
}
fields.put('actor_id', actorId)
fields.put('actor_account', column(relation == 'log_system_t' ? 'user_account' : 'account', 'TEXT'))
fields.put('actor_name', column(relation == 'log_system_t' ? 'user_name' : 'full_name', 'TEXT'))
fields.put('actor_org_id', orgId)
fields.put('actor_org_name', column('org_name', 'TEXT'))
if (kind != 'auth') {
    fields.put('parameter_capture_status', column('parameter_capture_status', 'TEXT'))
    if (detailed) {
        fields.put('parameter_snapshot', column('parameter_snapshot', 'LOB'))
    }
}
if (detailed) {
    fields.put('details', column('detail_json', 'LOB'))
}
return {query: query, fields: fields, tenantId: tenant, mode: mode}
