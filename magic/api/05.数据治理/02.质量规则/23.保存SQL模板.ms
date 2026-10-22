{
  "properties" : { },
  "id" : "quality_sql_template_save_v1",
  "script" : null,
  "groupId" : "d5ce0d4911e94d7bb3c22a9f67547406",
  "name" : "23.保存SQL模板",
  "createTime" : 1792504667711,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/templates/save",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "保存当前租户可复用SQL条件模板及默认绑定参数；不执行SQL，不变更已保存任务快照。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
/*
 * 功能：创建或修改SQL异常条件模板。用于任务配置，不提供任意语句执行。
 * 输入：tid可选、ruleName、ruleCode、dimension、sqlTemplate、values对象、description。
 * 输出：tid；名称/编码必填，维度为六类质量维度；条件最多4000字符、最多16个值参数。
 * 权限：当前认证租户；更新显式核对租户、有效记录及SQL模板标识。
 * 数据：data_gov_quality_rule；只写允许字段，申请人取认证会话。
 * 事务：单行写入；修改不传播到历史任务，任务保存时独立复制模板快照。
 */
import tenantRuntime
import qualitySql
import jsons
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
var tenantId=tenantRuntime.id()
var tid=body?.tid == null ? '' : (body.tid+'').trim()
var name=body?.ruleName == null ? '' : (body.ruleName+'').trim()
var code=body?.ruleCode == null ? '' : (body.ruleCode+'').trim()
var dimension=body?.dimension
if(name=='' || code=='') {exit 400,'请填写模板名称和编码'}
if(!['COMPLETENESS','UNIQUENESS','CONSISTENCY','ACCURACY','VALIDITY','TIMELINESS'].contains(dimension)) {exit 400,'请选择质量维度'}
var template=body?.sqlTemplate
var values=body?.values == null ? {} : body.values
qualitySql.validate(template,values)
var config=jsons.toJSONStr({version:1,sqlTemplate:template,values:values})
var actor=StpUtil.getLoginIdAsString()
var now=new Date()
var record={rule_name:name,rule_code:code,check_dimension:dimension,rule_description:body?.description,
    check_logic:config,updated_by:actor,updated_time:now}
if(tid!='') {
    var count=db.table('data_gov_quality_rule').where().eq('tid',tid).eq('tenant_id',tenantId).eq('is_del',0)
        .eq('scope_of_application','QUALITY_SQL_TEMPLATE_V1').update(record)
    if(count!=1) {exit 403,'模板不存在或不在当前租户范围内'}
} else {
    tid=IdUtil.fastSimpleUUID()
    record.tid=tid;record.tenant_id=tenantId;record.is_del=0
    record.created_by=actor;record.created_time=now;record.scope_of_application='QUALITY_SQL_TEMPLATE_V1'
    db.table('data_gov_quality_rule').insert(record)
}
return {tid:tid}
