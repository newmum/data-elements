{
  "properties": {},
  "id": "a1000000000000000000000000000004",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "04.保存任务",
  "path": "/task/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskName\":\"常住人口基础质量检查\",\"taskCode\":\"DQ_POLICE_POPULATION\",\"datasourceId\":\"dq_demo_source_20260802\",\"tableId\":\"dq_demo_population_meta\",\"shardKey\":\"person_id\",\"shardCount\":8,\"sampleLimit\":200,\"triggerMode\":\"MANUAL\",\"rules\":[{\"ruleName\":\"姓名非空\",\"ruleType\":\"NOT_EMPTY\",\"columnName\":\"person_name\",\"severity\":\"HIGH\",\"weight\":20,\"parameters\":{}}]}",
  "headers": [],
  "paths": [],
  "description": "保存当前租户真实质检任务。SQL条件模板按当前租户一次批量读取并保存独立快照，已有任务及执行实例不受模板后续修改影响。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：04.保存任务
// 业务说明：POST /dwm/quality/task/save；写入 dq_quality_task_t 与 dq_quality_task_rule_t；保存真实可执行任务，任意SQL不在允许模型内；任务保存主接口。

import tenantRuntime
import dataQuality
import jsons
import qualitySql
import dataScope

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 先检查必需资料；规则和任务在 Java 仓储的一次事务中保存。
if (body == null) { exit 400, '请填写质检任务' }
if (body?.taskName == null || ('' + body.taskName).trim() == '') { exit 400, '任务名称不能为空' }
var source=db.table('db_datasource_t').columns('tid','org_id','created_by').where().eq('tid',body?.datasourceId).eq('tenant_id',tenantId).eq('is_del',0).selectOne()
if(source==null || !dataScope.canReadDataSource(source)) {exit 403,'数据源不存在或不在当前角色范围内'}
// SQL模板只从当前租户的现用模板库解析，不采用请求中的SQL或模板名称。
// 一次读取全部模板；逐规则只在内存映射，不能按规则补查数据库。
var templateIds=[]
for(rule in body?.rules || []) {
    if(rule.ruleType=='SQL_TEMPLATE') {
        var templateId=rule?.parameters?.templateId
        if(templateId==null || templateId=='') {exit 400,'请先选择已保存的SQL模板'}
        if(!templateIds.contains(templateId)) {templateIds.add(templateId)}
    }
}
if(templateIds.size()>64) {exit 400,'最多配置64个模板'}
var templates={}
if(templateIds.size()>0) {
    var rows=db.table('data_gov_quality_rule').columns('tid','rule_name','check_dimension','check_logic')
        .where().eq('tenant_id',tenantId).eq('scope_of_application','QUALITY_SQL_TEMPLATE_V1').eq('is_del',0).in('tid',templateIds).select()
    if(rows.size()!=templateIds.size()) {exit 403,'部分SQL模板已删除或不在当前租户范围内，请重新选择'}
    for(row in rows) {templates[row.tid]=row}
}
for(rule in body?.rules || []) {
    if(rule.ruleType=='SQL_TEMPLATE') {
        var current=templates[rule.parameters.templateId]
        var config=jsons.toJSONObject(current.checkLogic)
        var values={}
        values.putAll(config.values || {})
        values.putAll(rule.parameters.values || {})
        var inspected=qualitySql.validate(config.sqlTemplate,values)
        if(inspected.usesRelatedColumn && (rule?.relatedColumn==null || rule.relatedColumn=='')) {exit 400,'该SQL模板需要选择关联字段'}
        rule.dimension=current.checkDimension
        rule.parameters={templateId:current.tid,templateName:current.ruleName,sqlTemplate:config.sqlTemplate,values:values}
    }
}
var taskId = dataQuality.persistTask(body)
// 返回仓储生成的任务标识及最新规则配置。
return dataQuality.taskDetail(taskId)
