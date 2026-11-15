{
  "properties": {},
  "id": "tenant_save_magic_01",
  "script": null,
  "groupId": "d28618bcdcca47279ce2c6f513002948",
  "name": "02.保存租户",
  "createTime": 1787162475891,
  "updateTime": 1787182300446,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"\",\n  \"code\": \"TENANT_CODE\",\n  \"name\": \"租户名称\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "POST /sym/tenant/save，新增或维护租户；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 tid、code、name、status、sortNo、isolationMode、jsonConfig；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.保存租户
// 路由：POST /sym/tenant/save
// 业务说明：POST /sym/tenant/save，新增或维护租户；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 tid、code、name、status、sortNo、isolationMode、jsonConfig；执行数据库写入并返回处理结果。
import cn.hutool.json.JSONUtil
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import tenantRuntime


var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
  for (key in keys) {
    if (row != null && row[key] != null) {
      return row[key]
    }
  }
  return null
}
var currentTenantId = text(tenantRuntime.id())
if (currentTenantId == '') {
  exit 401, '未获取到当前租户'
}
var operator = text(StpUtil.getLoginId())
var now = new Date()
var tenantRecordId = text(body == null ? null : body.tid)
var code = text(body == null ? null : body.code).toUpperCase()
var name = text(body == null ? null : body.name)
if (code == '' || name == '') {
  exit 400, '租户编码和租户名称不能为空'
}
if (!code.matches('[A-Z0-9_-]{2,64}')) {
  exit 400, '租户编码仅允许大写字母、数字、下划线和短横线'
}
var status = body != null && (body.status == 0 || body.status == '0') ? 0 : 1
var sortNo = body == null || body.sortNo == null ? 0 : body.sortNo::int
var isolationMode = text(body == null ? null : body.isolationMode)
if (isolationMode == '') {
  isolationMode = 'ROW'
}
if (isolationMode != 'ROW' && isolationMode != 'DATABASE') {
  exit 400, '隔离模式仅支持 ROW 或 DATABASE'
}
var jsonConfig = text(body == null ? null : body.jsonConfig)
var duplicate = db.table('baseline.sym_tenant_t').where().eq('code',code).eq('is_del',0).selectOne()
if (duplicate != null && (tenantRecordId == '' || text(duplicate.tid) != tenantRecordId)) {
  exit 400, '租户编码已存在'
}
if (tenantRecordId == '') {
  tenantRecordId = IdUtil.getSnowflakeNextIdStr()
  db.table('baseline.sym_tenant_t').primary('tid').insert({ tid:tenantRecordId, code:code, name:name, init_flag:0, json_config:jsonConfig == '' ? null : jsonConfig, sort_no:sortNo, created_time:now, created_by:operator, updated_time:now, updated_by:operator, is_del:0, tenant_id:tenantRecordId, status:status, isolation_mode:isolationMode })
} else {
  var existed = db.table('baseline.sym_tenant_t').where().eq('tid',tenantRecordId).eq('is_del',0).selectOne()
  if (existed == null) {
    exit 404, '租户不存在或已删除'
  }
  // Changing an account source requires a migration, never an ordinary tenant-settings save.
  var previousConfig=JSONUtil.parseObj(rowValue(existed,['jsonConfig','json_config'])||'{}');
  var nextConfig=JSONUtil.parseObj(jsonConfig==''?'{}':jsonConfig);
  if(previousConfig.containsKey('accountSource')) {nextConfig.set('accountSource',previousConfig.get('accountSource'));}
  else {nextConfig.remove('accountSource');}
  jsonConfig=JSONUtil.toJsonStr(nextConfig);
  db.table('baseline.sym_tenant_t').primary('tid').update({ tid:tenantRecordId, code:code, name:name, json_config:jsonConfig == '' ? null : jsonConfig, sort_no:sortNo, updated_time:now, updated_by:operator, status:status, isolation_mode:isolationMode }, true)
}
return { tid: tenantRecordId }
