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
  "description": "只读汇总当前租户及工作台授权范围内的标注、登记、五步进度、字段治理、采集状态与接入任务，返回首屏事项；未记录的审查结论与整改历史明确声明不可统计，不伪造真实指标。",
  "method": "POST",
  "requestBody": "{\"scope\":\"auto\"}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 数据资产工作台概览：固定批量查询，不连接来源库、不触发探查或逐行读取。
import '@/common/workbenchData' as workbenchData
import '@/common/workbenchChanges' as workbenchChanges
import cn.hutool.json.JSONUtil
import tableSort
var context=workbenchData(body)
var tenantId=context.tenantId
var userId=context.userId
var scopeOrgIds=context.scopeOrgIds
var cte=context.cte
var summary=db.selectOne(cte+"""
 SELECT COUNT(*) sources,COALESCE(SUM(ss.table_count),0) table_total,
        COALESCE(SUM(ss.marked_count),0) marked,COALESCE(SUM(ss.completed_count),0) registered,
        SUM(CASE WHEN COALESCE(ss.table_count,0)>0 OR j.status='SUCCEEDED' THEN 1 ELSE 0 END) explored_sources,
        SUM(CASE WHEN ss.table_count>0 AND ss.marked_count=ss.table_count AND ss.dictionary_count=ss.dictionary_saved THEN 1 ELSE 0 END) dictionary_sources,
        SUM(CASE WHEN ss.table_count>0 AND ss.marked_count=ss.table_count AND ss.business_count=ss.business_saved THEN 1 ELSE 0 END) business_sources,
        SUM(CASE WHEN s.asset_status=2 THEN 1 ELSE 0 END) reviewed_sources
 FROM source_scope s LEFT JOIN source_stats ss ON ss.datasource_id=s.datasource_id
 LEFT JOIN latest_job j ON j.datasource_id=s.source_key
""")
var types=db.select(cte+"""
 SELECT COALESCE(business_type,'未分类') name,COUNT(*) count FROM table_scope
 WHERE asset_status=2 GROUP BY COALESCE(business_type,'未分类') ORDER BY count DESC,name
""")
var counts={sources:summary.sources,tables:summary.tableTotal,marked:summary.marked,registered:summary.registered,
    unmarked:summary.tableTotal-summary.marked,markedUnregistered:summary.tableTotal-summary.registered,
    dictTables:0,businessTables:0,logTables:0}
for(type in types) {
    if(type.name=='字典表') {counts.dictTables=type.count}
    if(type.name=='业务表') {counts.businessTables=type.count}
    if(type.name=='日志表') {counts.logTables=type.count}
}
var tasks=db.selectOne(cte+"""
 SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN runtime_state='healthy' THEN 1 ELSE 0 END),0) running,
        COALESCE(SUM(CASE WHEN runtime_state='error' THEN 1 ELSE 0 END),0) errors FROM task_scope
""")
counts.accessTasks=tasks.total
counts.accessRunningTasks=tasks.running
counts.accessExceptionTasks=tasks.errors
var stage=(name,done,total,unit,step)=>({name:name,done:done==null?0:done,total:total,unit:unit,step:step,
    tip:(total-(done==null?0:done))+' '+unit+'待完成'})
var stages=[stage('探查数据表',summary.exploredSources,counts.sources,'个数据源',1),
    stage('标注数据表',counts.marked,counts.tables,'张表',1),
    stage('登记字典表',summary.dictionarySources,counts.sources,'个数据源',2),
    stage('登记业务/日志表',summary.businessSources,counts.sources,'个数据源',3),
    stage('登记内容审查',summary.reviewedSources,counts.sources,'个数据源',4)]
// 治理字段按已保存配置批量读取并在内存统计，不调用逐字段详情接口。
var governance={available:true,standardFields:0,dictionaryFields:0,timestampRate:0}
var volume=db.selectOne(cte+"""
 SELECT COUNT(*) total,COALESCE(SUM(LENGTH(COALESCE(raw.field_governance_config,''))),0) bytes
 FROM table_scope t JOIN db_table_t raw ON raw.tid=t.tid AND raw.tenant_id=#{tenantId}
 WHERE raw.field_governance_config IS NOT NULL AND raw.field_governance_config<>''
""")
if(volume.total>20000 || volume.bytes>67108864) {
    governance.available=false
    governance.reason='治理配置超过单次安全统计范围，保留静态参考值'
} else {
    var configs=db.select(cte+"""
        SELECT raw.field_governance_config config FROM table_scope t
        JOIN db_table_t raw ON raw.tid=t.tid AND raw.tenant_id=#{tenantId}
        WHERE raw.field_governance_config IS NOT NULL AND raw.field_governance_config<>''
    """)
    var businessTotal=0
    var timestampTotal=0
    try {
        for(row in configs) {
            var config=JSONUtil.parseObj(row.config+'')
            var fields=config.fields==null?[]:config.fields
            for(field in fields) {
                if(field.standardField!=null && (field.standardField+'').trim()!='') {governance.standardFields=governance.standardFields+1}
                if(field.dictionaryRelation?.enabled==true && field.dictionaryRelation?.sql!=null && (field.dictionaryRelation.sql+'').trim()!='') {governance.dictionaryFields=governance.dictionaryFields+1}
            }
            if(config.registrationSavedPhase=='business') {
                businessTotal=businessTotal+1
                if(config.timestampField!=null && (config.timestampField+'').trim()!='') {timestampTotal=timestampTotal+1}
            }
        }
        governance.timestampRate=businessTotal==0?0:Math.round(100.0*timestampTotal/businessTotal)
    } catch(error) {governance.available=false;governance.reason='部分历史治理配置格式异常，暂不能准确统计'}
}
var pendingColumn=tableSort.field({sortField:body?.pendingSortField},
    {displayName:"COALESCE(NULLIF(t.table_name_cn,''),NULLIF(t.table_comment,''),t.table_name)",createdTime:'t.created_time'},'createdTime')
var pendingDirection=body?.pendingSortDir==null||body.pendingSortDir==''?'desc':tableSort.direction({sortDir:body.pendingSortDir})
var pendingOrder='CASE WHEN '+pendingColumn+' IS NULL THEN 1 ELSE 0 END,'+pendingColumn+' '+pendingDirection+',t.tid'
var pending=db.select(cte+"""
 SELECT * FROM (SELECT t.tid id,t.datasource_id,t.table_name,
        COALESCE(NULLIF(t.table_name_cn,''),t.table_comment,t.table_name) table_label,s.db_name source,
        COALESCE(t.business_type,'待确认') suggestion,t.business_type_reason reason,t.created_time discovered_at,t.field_count column_count,
        ROW_NUMBER() OVER(ORDER BY
"""+pendingOrder+""") item_rank
 FROM table_scope t JOIN source_scope s ON s.datasource_id=t.datasource_id WHERE COALESCE(t.annotated,0)<>1) q WHERE item_rank<=5 ORDER BY item_rank
""")
var health=db.select(cte+"""
 SELECT * FROM (SELECT s.datasource_id id,s.db_name name,s.connection_status,s.show_connect,
        j.status collection_status,j.total_count,j.finished_time,
        ROW_NUMBER() OVER(ORDER BY CASE WHEN COALESCE(s.show_connect,1)<>0 AND s.connection_status IN ('failed','error') THEN 0 ELSE 1 END,s.updated_time DESC,s.datasource_id) item_rank
 FROM source_scope s LEFT JOIN latest_job j ON j.datasource_id=s.source_key) q WHERE item_rank<=5 ORDER BY item_rank
""")
var taskList=db.select(cte+"""
 SELECT * FROM (SELECT k.*,ROW_NUMBER() OVER(ORDER BY CASE WHEN k.runtime_state='error' THEN 0 ELSE 1 END,k.updated_time DESC,k.tid) item_rank
 FROM task_scope k) q WHERE item_rank<=5 ORDER BY item_rank
""")
var changes=workbenchChanges({pageNo:1,pageSize:5,changeType:'all'},context)
if(changes.available) {
    counts.addedTables=changes.counts.added
    if(changes.modifiedAvailable) {counts.changedRegisteredTables=changes.counts.modified}
    counts.deletedRegisteredTables=changes.counts.deleted
}
return {mode:context.mode,canViewAll:context.canViewAll,scope:context.scope,counts:counts,stages:stages,
    types:types,governance:governance,pending:pending,health:health,tasks:taskList,changes:changes,
    unavailable:[
        {key:'modifiedTables',label:'结构修改表数及完整变更总数',reason:'结构修改差异只保存在一次探查的临时视图，尚无完整持久化记录；新增和源端缺失另按采集记录真实统计'},
        {key:'requiredFix',label:'登记异常必改及占比',reason:'第五步审查结论随页面展开即时计算，尚未保存可聚合的完整审查结果'},
        {key:'rectification',label:'待整改表、涉及数据源及整改清单',reason:'现有驳回只重置登记状态，未记录独立的驳回原因、整改状态和处理历史，无法与首次未登记区分'},
        {key:'healthScore',label:'数据源综合健康分',reason:'已有连通和采集结果，但尚未定义综合评分算法与历史观测窗口'},
        {key:'confidence',label:'智能建议置信度',reason:'已有业务分类和规则说明，尚无模型置信度或评估记录；原示例86%–97%仅供参考，不对应真实数据表'},
        {key:'registrationEndTime',label:'登记完成时间（导出）',reason:'当前未记录独立的登记结束时间，保持原空值，不以更新时间代替'}],
    timeBasis:'任务状态为平台最近保存的运行状态；连通状态与变更来自最近一次检查或探查，不在工作台主动连接来源库'}
