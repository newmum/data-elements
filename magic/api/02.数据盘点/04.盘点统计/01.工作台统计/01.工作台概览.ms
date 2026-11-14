{
  "properties": {},
  "id": "30f59993bf5b43c994b4db87eee9d94d",
  "script": null,
  "groupId": "2cb941922e934cb0984438557189e854",
  "name": "01.工作台概览",
  "path": "/overview",
  "createTime": 1792844788330,
  "updateTime": null,
  "parameters": [],
  "options": [],
  "description": "只读汇总当前租户和工作台角色范围内的首屏概览。scope 选择视图，section 选择登记、治理、健康、任务或变更，省略时返回完整结果。字段治理从同名代表行统计已保存配置，并在单次数据库读取中判断体积上限；越限或格式异常时返回不可用原因。",
  "method": "POST",
  "requestBody": "{\"scope\":\"auto\"}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import '@/common/workbenchData' as workbenchData
import '@/common/workbenchChanges' as workbenchChanges
import cn.hutool.json.JSONUtil
import tableRead
import tableSort

// POST /dst/statistics/workbench/overview 在当前租户和工作台角色授权范围内汇总首屏指标和待办。
// scope 选择可用视图，section 可单独查询登记、治理、健康、任务或变更，省略时返回完整概览。
// 字段治理按代表行的已保存配置统计，越限和异常会注明不可用原因。接口只读，不连接来源库。
var section = body?.section == null ? 'all' : body.section + ''
if (!['all', 'registration', 'governance', 'health', 'tasks', 'changes'].contains(section)) {
    exit 400, '工作台区域参数无效'
}
var context = workbenchData(body)
var tenantId = context.tenantId
var userId = context.userId
var scopeOrgIds = context.scopeOrgIds
var cte = context.cte
var lightCte = context.lightCte
var counts = {}
var stages = []
var types = []
var governance = {}
var pending = []
var health = []
var taskList = []
var changes = {}
if(section=='all'||section=='registration') {
var summaryRows=db.select(cte+"""
 SELECT totals.*, kinds.type_name,kinds.type_count FROM (

 SELECT COUNT(*) sources,COALESCE(SUM(ss.table_count),0) table_total,
        COALESCE(SUM(ss.marked_count),0) marked,COALESCE(SUM(ss.completed_count),0) registered,
        SUM(CASE WHEN COALESCE(ss.table_count,0)>0 OR j.status='SUCCEEDED' THEN 1 ELSE 0 END) explored_sources,
        SUM(CASE WHEN ss.table_count>0 AND ss.marked_count=ss.table_count AND ss.dictionary_count=ss.dictionary_saved THEN 1 ELSE 0 END) dictionary_sources,
        SUM(CASE WHEN ss.table_count>0 AND ss.marked_count=ss.table_count AND ss.business_count=ss.business_saved THEN 1 ELSE 0 END) business_sources,
        SUM(CASE WHEN s.asset_status=2 THEN 1 ELSE 0 END) reviewed_sources
 FROM source_scope s LEFT JOIN source_stats ss ON ss.datasource_id=s.datasource_id
 LEFT JOIN latest_job j ON j.datasource_id=s.source_key

 ) totals LEFT JOIN (

 SELECT COALESCE(business_type,'未分类') type_name,COUNT(*) type_count FROM table_scope
 WHERE asset_status=2 GROUP BY COALESCE(business_type,'未分类')

 ) kinds ON 1=1 ORDER BY kinds.type_count DESC,kinds.type_name
""")
var summary=summaryRows[0]
types=summaryRows.filter(row=>row.typeName!=null).map(row=>({name:row.typeName,count:row.typeCount}))
counts={sources:summary.sources,tables:summary.tableTotal,marked:summary.marked,registered:summary.registered,
    unmarked:summary.tableTotal-summary.marked,markedUnregistered:summary.tableTotal-summary.registered,
    dictTables:0,businessTables:0,logTables:0}
for(type in types) {
    if(type.name=='字典表') {counts.dictTables=type.count}
    if(type.name=='业务表') {counts.businessTables=type.count}
    if(type.name=='日志表') {counts.logTables=type.count}
}
var stage=(name,done,total,unit,step)=>({name:name,done:done==null?0:done,total:total,unit:unit,step:step,
    tip:(total-(done==null?0:done))+' '+unit+'待完成'})
stages=[stage('探查数据表',summary.exploredSources,counts.sources,'个数据源',1),
    stage('标注数据表',counts.marked,counts.tables,'张表',1),
    stage('登记字典表',summary.dictionarySources,counts.sources,'个数据源',2),
    stage('登记业务/日志表',summary.businessSources,counts.sources,'个数据源',3),
    stage('登记内容审查',summary.reviewedSources,counts.sources,'个数据源',4)]
var pendingColumn=tableSort.field({sortField:body?.pendingSortField},
    {displayName:"COALESCE(NULLIF(t.table_name_cn,''),NULLIF(t.table_comment,''),t.table_name)",createdTime:'t.created_time'},'createdTime')
var pendingDirection=body?.pendingSortDir==null||body.pendingSortDir==''?'desc':tableSort.direction({sortDir:body.pendingSortDir})
var pendingOrder='CASE WHEN '+pendingColumn+' IS NULL THEN 1 ELSE 0 END,'+pendingColumn+' '+pendingDirection+',t.tid'
pending=db.select(cte+"""
 SELECT * FROM (SELECT t.tid id,t.datasource_id,t.table_name,
        COALESCE(NULLIF(t.table_name_cn,''),t.table_comment,t.table_name) table_label,s.db_name source,
        COALESCE(t.business_type,'待确认') suggestion,t.business_type_reason reason,t.created_time discovered_at,t.field_count column_count,
        ROW_NUMBER() OVER(ORDER BY
"""+pendingOrder+""") item_rank
 FROM table_scope t JOIN source_scope s ON s.datasource_id=t.datasource_id WHERE COALESCE(t.annotated,0)<>1) q WHERE item_rank<=5 ORDER BY item_rank
""")
}
if (section == 'all' || section == 'governance') {
    governance = {available: true, standardFields: 0, dictionaryFields: 0, timestampRate: 0}
    var text = (alias, field) => tableRead.column(alias, field, 'TEXT')
    var number = (alias, field) => tableRead.column(alias, field, 'NUMBER')
    var configColumn = (alias, field) => tableRead.column(alias, field, 'LOB')
    var maxGovernanceConfigs = 20000
    var maxGovernanceLength = 67108864

    // 仅从当前角色可读的有效数据源选表。ALL 的“我负责的”仍限为本人创建的来源。
    var sources = [tableRead.equal(number('s', 'is_del'), 0)]
    if (context.scope == 'ORG') {
        sources.add(tableRead.in(text('s', 'org_id'), scopeOrgIds))
    } else if (context.scope == 'OWNER' || (context.scope == 'ALL' && context.mode == 'mine')) {
        sources.add(tableRead.equal(text('s', 'created_by'), userId))
    }
    var tables = tableRead.from('db_table_t', 't', tenantId)
    tables.leftJoin('db_datasource_t', 's', 'tid', 'datasource_id', sources)
    tables.where(tableRead.not(tableRead.isNull(text('s', 'tid'))))
    tables.where(tableRead.equal(number('t', 'is_del'), 0))

    // 先按来源和表名选择代表行，再排除空配置，避免旧快照的配置重新参与统计。
    var tableId = text('t', 'tid')
    var sourceId = text('t', 'datasource_id')
    var updatedAt = tableRead.column('t', 'updated_time', 'TIME')
    var ranked = tables.ranked('r', {tid: tableId, config: configColumn('t', 'field_governance_config')}, [
        sourceId, tableRead.lower(text('t', 'table_name'))
    ], [
        tableRead.coalesce([number('t', 'annotated'), 0]), tableRead.coalesce([number('t', 'field_count'), 0]),
        updatedAt, tableId
    ], ['desc', 'desc', 'desc', 'desc'], true)
    var savedConfig = configColumn('r', 'config')
    var rankedId = text('r', 'tid')
    ranked.where(tableRead.not(tableRead.isNull(savedConfig)))
    ranked.where(tableRead.not(tableRead.equal(savedConfig, '')))

    // 同一次读取完成数量、原生文本长度和配置获取。越限时只投影一条标记，配置列置空。
    var volume = ranked.projected('v', {
        tid: rankedId,
        config: savedConfig,
        total_configs: tableRead.overAll(tableRead.count()),
        total_length: tableRead.overAll(tableRead.sum(tableRead.textLength(savedConfig))),
        marker_id: tableRead.overAll(tableRead.max(rankedId))
    })
    var overBudget = tableRead.any([
        tableRead.greaterThan(number('v', 'total_configs'), maxGovernanceConfigs),
        tableRead.greaterThan(number('v', 'total_length'), maxGovernanceLength)
    ])
    volume.where(tableRead.any([
        tableRead.not(overBudget), tableRead.equal(text('v', 'tid'), text('v', 'marker_id'))
    ]))
    // 只投影统计所需的标志和计数。旧格式按原文返回，继续走同一 Hutool 解析规则。
    var projectedConfig = tableRead.jsonProjection(configColumn('v', 'config'), [
        tableRead.jsonTextEquals(['registrationSavedPhase'], 'business'),
        tableRead.jsonNonblank(['timestampField'])
    ], 'fields', [
        [tableRead.jsonNonblank(['standardField'])],
        [tableRead.jsonTrue(['dictionaryRelation', 'enabled']), tableRead.jsonNonblank(['dictionaryRelation', 'sql'])]
    ])
    var configs = volume.grouped({
        overBudget: tableRead.when(overBudget, 'exceeded', 'within'),
        config: tableRead.when(overBudget, null, projectedConfig)
    }, [])
    if (!configs.isEmpty() && 'exceeded'.equals(configs[0].overBudget)) {
        governance.available = false
        governance.reason = '治理配置超过单次安全统计范围，保留静态参考值'
    } else {
        // 沿用已保存治理配置的解析规则。历史配置格式异常时向页面明确报告不可用。
        var businessTotal = 0
        var timestampTotal = 0
        try {
            for (row in configs) {
                var encoded = row.config + ''
                if (encoded.startsWith('P')) {
                    var tuple = JSONUtil.parseArray(encoded.substring(1))
                    governance.standardFields = governance.standardFields + tuple.getInt(2)
                    governance.dictionaryFields = governance.dictionaryFields + tuple.getInt(3)
                    if (tuple.getInt(0) == 1) {
                        businessTotal = businessTotal + 1
                        if (tuple.getInt(1) == 1) {
                            timestampTotal = timestampTotal + 1
                        }
                    }
                    continue
                }
                var config = JSONUtil.parseObj(encoded.substring(1))
                var fields = config.fields == null ? [] : config.fields
                for (field in fields) {
                    if (field.standardField != null && (field.standardField + '').trim() != '') {
                        governance.standardFields = governance.standardFields + 1
                    }
                    var dictionary = field.dictionaryRelation
                    if (dictionary?.enabled == true && dictionary.sql != null && (dictionary.sql + '').trim() != '') {
                        governance.dictionaryFields = governance.dictionaryFields + 1
                    }
                }
                if (config.registrationSavedPhase == 'business') {
                    businessTotal = businessTotal + 1
                    if (config.timestampField != null && (config.timestampField + '').trim() != '') {
                        timestampTotal = timestampTotal + 1
                    }
                }
            }
            governance.timestampRate = businessTotal == 0 ? 0 : Math.round(100.0 * timestampTotal / businessTotal)
        } catch (error) {
            governance.available = false
            governance.reason = '部分历史治理配置格式异常，暂不能准确统计'
        }
    }
}
if(section=='all'||section=='health') {
health=db.select(lightCte+"""
 SELECT * FROM (SELECT s.datasource_id id,s.db_name name,s.connection_status,s.show_connect,
        j.status collection_status,j.total_count,j.finished_time,
        ROW_NUMBER() OVER(ORDER BY CASE WHEN COALESCE(s.show_connect,1)<>0 AND s.connection_status IN ('failed','error') THEN 0 ELSE 1 END,s.updated_time DESC,s.datasource_id) item_rank
 FROM source_scope s LEFT JOIN latest_job j ON j.datasource_id=s.source_key) q WHERE item_rank<=5 ORDER BY item_rank
""")
}
if(section=='all'||section=='tasks') {
var taskRows=db.select(lightCte+"""
 SELECT totals.*, recent.* FROM (

 SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN runtime_state='healthy' THEN 1 ELSE 0 END),0) running,
        COALESCE(SUM(CASE WHEN runtime_state='error' THEN 1 ELSE 0 END),0) errors FROM task_scope

 ) totals LEFT JOIN (

 SELECT * FROM (SELECT k.*,ROW_NUMBER() OVER(ORDER BY CASE WHEN k.runtime_state='error' THEN 0 ELSE 1 END,k.updated_time DESC,k.tid) item_rank
 FROM task_scope k) q WHERE item_rank<=5

 ) recent ON 1=1 ORDER BY recent.item_rank
""")
var tasks=taskRows[0]
counts.accessTasks=tasks.total
counts.accessRunningTasks=tasks.running
counts.accessExceptionTasks=tasks.errors
taskList=taskRows.filter(row=>row.tid!=null)
}
if(section=='all'||section=='changes') {
context.cte=lightCte
changes=workbenchChanges({pageNo:1,pageSize:5,changeType:'all'},context)
if(changes.available) {
    counts.addedTables=changes.counts.added
    if(changes.modifiedAvailable) {counts.changedRegisteredTables=changes.counts.modified}
    counts.deletedRegisteredTables=changes.counts.deleted
}
}
return {section:section,mode:context.mode,canViewAll:context.canViewAll,scope:context.scope,counts:counts,stages:stages,
    types:types,governance:governance,pending:pending,health:health,tasks:taskList,changes:changes,
    unavailable:[
        {key:'modifiedTables',label:'结构修改表数及完整变更总数',reason:'结构修改差异只保存在一次探查的临时视图，尚无完整持久化记录；新增和源端缺失另按采集记录真实统计'},
        {key:'requiredFix',label:'登记异常必改及占比',reason:'第五步审查结论随页面展开即时计算，尚未保存可聚合的完整审查结果'},
        {key:'rectification',label:'待整改表、涉及数据源及整改清单',reason:'现有驳回只重置登记状态，未记录独立的驳回原因、整改状态和处理历史，无法与首次未登记区分'},
        {key:'healthScore',label:'数据源综合健康分',reason:'已有连通和采集结果，但尚未定义综合评分算法与历史观测窗口'},
        {key:'confidence',label:'智能建议置信度',reason:'已有业务分类和规则说明，尚无模型置信度或评估记录；原示例86%–97%仅供参考，不对应真实数据表'},
        {key:'registrationEndTime',label:'登记完成时间（导出）',reason:'当前未记录独立的登记结束时间，保持原空值，不以更新时间代替'}],
    timeBasis:'任务状态为平台最近保存的运行状态；连通状态与变更来自最近一次检查或探查，不在工作台主动连接来源库'}
