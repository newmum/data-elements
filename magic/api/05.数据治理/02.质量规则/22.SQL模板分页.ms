{
  "properties" : { },
  "id" : "quality_sql_template_page_v1",
  "script" : null,
  "groupId" : "d5ce0d4911e94d7bb3c22a9f67547406",
  "name" : "22.SQL模板分页",
  "createTime" : 1792504666367,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/templates/page",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "按当前租户分页查询可复用SQL异常条件模板；固定一次计数和一次分页，不按行补查。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
/*
 * 功能：规则菜单及任务选择器分页读取当前租户SQL模板。
 * 输入：page、size(1..100)、keyword、dimension及白名单排序；输出统一分页。
 * 权限：当前认证租户内共享质量模板；不读取来源数据或扩大数据源授权。
 * 数据：只读data_gov_quality_rule；按固定scope标识区别历史规则，无写入副作用。
 */
import tenantRuntime
import tableSort
import jsons
var tenantId=tenantRuntime.id()
var page=Math.max(1,body?.page::int(1))
var size=Math.min(100,Math.max(1,body?.size::int(20)))
var keyword=body?.keyword == null ? '' : '%'+body.keyword+'%'
var dimension=body?.dimension == null ? '' : body.dimension
var sql="SELECT tid,rule_name,rule_code,check_dimension,rule_description,check_logic,updated_time FROM data_gov_quality_rule WHERE tenant_id=#{tenantId} AND is_del=0 AND scope_of_application='QUALITY_SQL_TEMPLATE_V1'"
if(keyword!='') {sql=sql+' AND (rule_name LIKE #{keyword} OR rule_code LIKE #{keyword})'}
if(dimension!='') {sql=sql+' AND check_dimension=#{dimension}'}
var field=tableSort.field(body,{rule_name:'rule_name',rule_code:'rule_code',updated_time:'updated_time'},'updated_time')
var result=db.page(sql+' ORDER BY '+field+' '+tableSort.direction(body)+',tid ASC',size,(page-1)*size)
return result
