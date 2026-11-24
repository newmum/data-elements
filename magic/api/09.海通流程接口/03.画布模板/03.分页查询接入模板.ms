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
var includeTargets = !manage && body?.includeTargets == true
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

var defaults = tableRead.from('nifi_access_template_t','t',tenantId)
defaults.where(tableRead.equal(col('default_tenant_id','TEXT'),tenantId))
defaults.where(tableRead.equal(state,'PUBLISHED'))
var defaultRows = defaults.rows({tid:id, familyId:col('family_id','TEXT'), version:col('revision_no','NUMBER'),
    name:name, status:state, definition:col('definition','LOB'), updatedAt:updated},1)
var defaultVersionId = defaultRows.isEmpty() ? '' : defaultRows[0].tid.toString()

// 候选页和页外默认项一起批量取目标；每个ID仍在当前租户与角色范围内核对。
var candidates = []
candidates.addAll(result.rows)
if (includeTargets && !defaultRows.isEmpty()) {
    var present = false
    for (row in candidates) { if (row.tid.toString() == defaultVersionId) { present = true } }
    if (!present) { candidates.add(defaultRows[0]) }
}
var definitions = {}
var targetIds = []
for (row in candidates) {
    var spec = JSONUtil.parseObj(row.definition)
    definitions[row.tid.toString()] = spec
    if (includeTargets && spec.targetDatasourceIds != null) {
        if (spec.targetDatasourceIds.size() > 8) { exit 409, '模板目标超过8个，请在模板管理中核对' }
        for (targetId in spec.targetDatasourceIds) {
            var value = targetId.toString()
            if (!targetIds.contains(value)) { targetIds.add(value) }
        }
    }
}
if (targetIds.size() > 808) { exit 400, '本页模板目标过多，请缩小分页' }
var visibleTargets = {}
if (!targetIds.isEmpty()) {
    var targets = tableRead.from('db_datasource_t','d',tenantId)
    var targetCol = (name,kind) => tableRead.column('d',name,kind)
    var targetId = targetCol('tid','TEXT')
    targets.where(tableRead.in(targetId,targetIds))
    targets.where(tableRead.equal(targetCol('is_del','NUMBER'),0))
    targets.where(tableRead.equal(targetCol('show_connect','NUMBER'),1))
    var records = targets.rows({tid:targetId,dbName:targetCol('db_name','TEXT'),dbType:targetCol('db_type','TEXT'),
        createdBy:targetCol('created_by','TEXT'),orgId:targetCol('org_id','TEXT')},targetId,'asc',targetIds.size())
    for (target in dataScope.visibleDataSourcesFor('MENU:2081000000000000002',records)) {
        visibleTargets[target.tid.toString()] = target
    }
}
var option = row => {
    var spec = definitions[row.tid.toString()]
    var summary = {topology: spec.topology, sourceType: spec.sourceType, targets: spec.targets,
        intervalSeconds: spec.intervalSeconds, fetchSize: spec.fetchSize,sourceSchedule:spec.sourceSchedule,
        hiveMergeMaxAgeSeconds: spec.hiveMergeMaxAgeSeconds,
        hasFlow: spec.flow != null, hasNative: spec.nativeSnapshot != null, engine:spec.engine,
        targetDatasourceIds: spec.targetDatasourceIds}
    var item = {tid: row.tid, familyId: row.familyId, version: row.version, name: row.name,
        status: row.status, isDefault:row.tid == defaultVersionId, spec: summary, updatedAt: row.updatedAt}
    if (includeTargets) {
        var ids = spec.targetDatasourceIds == null ? [] : spec.targetDatasourceIds
        var bound = []
        for (id in ids) {
            var target = visibleTargets[id.toString()]
            if (target != null) {
                bound.add({tid:target.tid,value:target.tid,label:target.dbName,dbName:target.dbName,
                    dbType:target.dbType,layerCode:'ODS',tablePrefix:'ODS',parentValue:'TEMPLATE'})
            }
        }
        item.available = !ids.isEmpty() && bound.size() == ids.size() && ids.size() == spec.targets.size()
        item.selectionReady = true
        item.targets = item.available ? bound : []
    }
    return item
}
var rows = []
for (row in result.rows) { rows.add(option(row)) }
return {list: rows, total: result.total, pageNum: pageNum, pageSize: pageSize,defaultVersionId:defaultVersionId,
    defaultTemplate:includeTargets && !defaultRows.isEmpty() ? option(defaultRows[0]) : null}
