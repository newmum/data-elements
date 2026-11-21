{
  "properties": {},
  "id": "50319b91bfff4fe3b19f24db5aa7c8a4",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "03.分页查询接入模板",
  "path": "/templates",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "按当前行业与发布状态分页查询 NiFi 接入模板版本。"
}
================================
import tenantRuntime
import tableRead
import dataScope
import cn.hutool.json.JSONUtil

// POST /nifi/canvas/templates 分页读取当前行业的模板版本，创建任务入口只读取已发布版本。
var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId.isBlank()) { exit 401, '请重新登录' }
var manage = body?.manage == true
if (manage && dataScope.dataSource().scope != 'ALL') { exit 403, '当前角色无权管理接入模板' }
var pageNum = Math.max(1, (body?.pageNum || 1)::int(1))
var pageSize = Math.max(1, Math.min(100, (body?.pageSize || 20)::int(20)))
var keyword = (body?.keyword || '').toString().trim()
if (keyword.length() > 96) { exit 400, '模板关键字最多96个字符' }
var col = (name, kind) => tableRead.column('t', name, kind)
var id = col('tid', 'TEXT')
var name = col('display_name', 'TEXT')
var state = col('status', 'TEXT')
var updated = col('updated_at', 'NUMBER')
var sortField = (body?.sortField || 'updatedAt').toString()
var sortDir = (body?.sortDir || 'desc').toString().toLowerCase()
var sorts = {name:name,version:col('revision_no','NUMBER'),updatedAt:updated}
if (sorts[sortField] == null || !['asc','desc'].contains(sortDir)) { exit 400, '模板排序参数无效' }
var query = tableRead.from('nifi_access_template_t', 't', tenantId)
if (!manage) { query.where(tableRead.equal(state, 'PUBLISHED')) }
else if (body?.includeHistory != true) { query.where(tableRead.not(tableRead.equal(state, 'RETIRED'))) }
if (keyword != '') { query.where(tableRead.contains(name, keyword)) }
var result = query.page({tid: id, familyId: col('family_id', 'TEXT'), version: col('revision_no', 'NUMBER'),
    name: name, status: state, definition: col('definition', 'TEXT'), updatedAt: updated},
    sorts[sortField], sortDir, 'tid', pageNum, pageSize)

var defaults = tableRead.from('sym_config_t','c',tenantId)
defaults.where(tableRead.equal(tableRead.column('c','tid','TEXT'),'nifiaccessdefault' + tenantId))
defaults.where(tableRead.equal(tableRead.column('c','is_del','NUMBER'),0))
var defaultRows = defaults.rows({value:tableRead.column('c','config_value','TEXT')},1)
var defaultVersionId = defaultRows.isEmpty() ? '' : (defaultRows[0].value || '').toString()

var rows = []
for (row in result.rows) {
    var spec = JSONUtil.parseObj(row.definition)
    var summary = {topology: spec.topology, sourceType: spec.sourceType, targets: spec.targets,
        intervalSeconds: spec.intervalSeconds, fetchSize: spec.fetchSize,sourceSchedule:spec.sourceSchedule,
        hiveMergeMaxAgeSeconds: spec.hiveMergeMaxAgeSeconds,
        hasFlow: spec.flow != null, hasNative: spec.nativeSnapshot != null, engine:spec.engine,
        targetDatasourceIds: spec.targetDatasourceIds}
    rows.add({tid: row.tid, familyId: row.familyId, version: row.version, name: row.name,
        status: row.status, isDefault:row.tid == defaultVersionId, spec: summary, updatedAt: row.updatedAt})
}
return {list: rows, total: result.total, pageNum: pageNum, pageSize: pageSize,defaultVersionId:defaultVersionId}
