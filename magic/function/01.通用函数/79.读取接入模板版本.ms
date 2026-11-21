{
  "id": "069cbc99613b42c0b6805199f45a4d58",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "79.读取接入模板版本",
  "path": "/accesstemplate",
  "description": "物化建表与接入任务创建共用模板版本、来源和目标类型校验。",
  "parameters": [
    {
      "name": "versionId",
      "dataType": "Object"
    },
    {
      "name": "source",
      "dataType": "Object"
    },
    {
      "name": "targets",
      "dataType": "Object"
    }
  ]
}
================================
import tenantRuntime
import tableRead
import dataScope
import cn.hutool.json.JSONUtil
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as datasourceTypes
import com.linewell.dataelement.metautil.model.enums.DatabaseType

// /common/accesstemplate 由物化建表和创建任务共享。按已认证行业及逐个数据源校验适配条件。
var requested = (versionId || '').toString().trim()
if (requested == '') { return null }
if (!requested.matches('[A-Za-z0-9_-]{1,32}')) { exit 400, '模板版本标识无效' }
var tenantId = tenantRuntime.id()
var query = tableRead.from('nifi_access_template_t', 't', tenantId)
var id = tableRead.column('t', 'tid', 'TEXT')
query.where(tableRead.equal(id, requested))
query.where(tableRead.equal(tableRead.column('t', 'status', 'TEXT'), 'PUBLISHED'))
var rows = query.rows({tid: id, name: tableRead.column('t', 'display_name', 'TEXT'),
    version: tableRead.column('t', 'revision_no', 'NUMBER'), definition: tableRead.column('t', 'definition', 'TEXT')},
    id, 'asc', 1)
if (rows.isEmpty()) { exit 409, '接入模板未发布、已停用或不属于当前行业，请重新选择' }
var row = rows[0]
var recipe = JSONUtil.parseObj(row.definition)
var sourceConfig = source == null ? {} : JSONUtil.parseObj(source.poolCfg || source.pool_cfg || '{}')
var sourceMode = (sourceConfig.accessMode || sourceConfig.access_mode || sourceConfig.dataAccessMode || sourceConfig.data_access_mode || '').toString().toLowerCase()
var commitFile = ['receive','push'].contains(sourceMode)
if (commitFile && ((recipe.targets.size() > 1 && (recipe.topology == 'PARALLEL' || recipe.flow != null))
    || recipe.targets[0] == 'HIVE')) {
    exit 400, '推送落地文件须先提交到单个关系库，请选择OB Oracle接入等关系库单目标模板'
}
var kindOf = datasource => {
    var raw = (datasource.dbType || datasource.db_type || '').toString()
    var configText = datasource.poolCfg || datasource.pool_cfg
    var config = configText == null ? {} : JSONUtil.parseObj(configText.toString())
    var kind = datasourceTypes.configType(raw, config)
    return kind == 'DAMENG' ? 'DM' : kind
}
var permission = 'MENU:2081000000000000002'
if (source == null) { exit 403, '当前角色无权使用来源数据源' }
var candidates = [source]
for (target in targets) { candidates.add(target) }
var visibleIds = {}
for (datasource in dataScope.visibleDataSourcesFor(permission, candidates)) {
    visibleIds[datasource.tid.toString()] = true
}
if (visibleIds[source.tid.toString()] != true) { exit 403, '当前角色无权使用来源数据源' }
var sourceKind = recipe.sourceType == 'ANY' ? 'ANY' : kindOf(source)
var jdbc = []
for (type in DatabaseType.values()) {
    if (type.isRelational() && type != DatabaseType.MAXCOMPUTE) { jdbc.add(type == DatabaseType.DAMENG ? 'DM' : type.name()) }
}
if ((recipe.sourceType == 'JDBC' && !jdbc.contains(sourceKind))
    || (!['ANY','JDBC'].contains(recipe.sourceType) && recipe.sourceType != sourceKind)) {
    exit 400, '来源数据源类型与接入模板不匹配'
}
if (targets.size() != recipe.targets.size()) { exit 400, '请为模板的所有目标选择实际数据源' }
var configuredIds = recipe.targetDatasourceIds
if (configuredIds == null || configuredIds.size() != targets.size()) { exit 409, '模板尚未配置全部目标库，请在模板管理中完善' }
for (target in targets) {
    if (!configuredIds.contains(target.tid.toString())) { exit 403, '实际目标库与模板预设绑定不一致' }
}
var expected = {}
for (kind in recipe.targets) { expected[kind] = (expected[kind] || 0) + 1 }
for (target in targets) {
    if (target == null || visibleIds[target.tid.toString()] != true) {
        exit 403, '当前角色无权使用所选目标数据源'
    }
    var kind = kindOf(target)
    if ((expected[kind] || 0) <= 0) { exit 400, '所选目标的数据源类型或数量与接入模板不匹配' }
    expected[kind] = expected[kind] - 1
}
var remaining = []
remaining.addAll(targets)
var targetDatasourceOrder = []
for (kind in recipe.targets) {
    var match = null
    for (target in remaining) { if (match == null && kindOf(target) == kind) { match = target } }
    targetDatasourceOrder.add(match.tid.toString())
    remaining.remove(match)
}
return {tid: row.tid, name: row.name, version: row.version, spec: recipe, targetDatasourceOrder: targetDatasourceOrder}
