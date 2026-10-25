{
  "properties": {},
  "id": "93fcc3a46552459d836f50a112addafd",
  "script": null,
  "groupId": "2cb941922e934cb0984438557189e854",
  "name": "02.工作台事项分页",
  "path": "/items",
  "createTime": 1792844788331,
  "updateTime": null,
  "parameters": [],
  "options": [],
  "description": "按工作台相同租户和角色范围分页查询表、数据源、任务或变更；批量处理清单最多100个表ID并逐ID核验，不允许混入无权或外租户项。",
  "method": "POST",
  "requestBody": "{\"scope\":\"auto\"}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 工作台下钻与本次处理清单：所有筛选先应用授权，再排序和分页。
import '@/common/workbenchData' as workbenchData
import '@/common/workbenchChanges' as workbenchChanges
import tableSort
import java.util.List
var context=workbenchData(body)
var tenantId=context.tenantId
var userId=context.userId
var scopeOrgIds=context.scopeOrgIds
var pageNo=Math.max(1,body?.pageNo==null?1:body.pageNo::int)
var pageSize=Math.min(100,Math.max(1,body?.pageSize==null?20:body.pageSize::int))
var kind=body?.kind==null?'tables':body.kind+''
if(kind=='changes') {return workbenchChanges(body,context)}
if(!['tables','sources','tasks'].contains(kind)) {exit 400,'工作台事项类型无效'}
var keyword=body?.keyword==null?'':(body.keyword+'').trim()
if(keyword.length()>100) {exit 400,'搜索关键字最多100字'}
var keywordLike='%'+keyword+'%'
var businessType=body?.businessType==null?'':body.businessType+''
var status=body?.status==null?'':body.status+''
var whereSql=' WHERE 1=1 '
var sql=''
var fields={}
if(kind=='tables') {
    sql=context.cte+"""
        SELECT t.tid id,t.datasource_id,t.table_name,t.table_name_cn,t.table_comment,
               COALESCE(NULLIF(t.table_name_cn,''),t.table_comment,t.table_name) table_label,
               s.db_name source,t.business_type,t.annotated,t.asset_status,t.field_count column_count,
               t.business_type_reason,t.created_time,t.updated_time
        FROM table_scope t JOIN source_scope s ON s.datasource_id=t.datasource_id
    """
    if(status=='pending') {whereSql=whereSql+' AND COALESCE(t.annotated,0)<>1 '}
    else if(status=='unregistered') {whereSql=whereSql+' AND COALESCE(t.asset_status,0)<>2 '}
    else if(status=='registered') {whereSql=whereSql+' AND t.asset_status=2 '}
    else if(status!='') {exit 400,'表状态无效'}
    if(businessType!='') {whereSql=whereSql+' AND t.business_type=#{businessType} '}
    if(keyword!='') {whereSql=whereSql+' AND (t.table_name LIKE #{keywordLike} OR t.table_name_cn LIKE #{keywordLike} OR t.table_comment LIKE #{keywordLike} OR s.db_name LIKE #{keywordLike}) '}
    var ids=body?.ids
    if(ids!=null) {
        if(!(ids instanceof List) || ids.size()==0 || ids.size()>100 || ids.distinct().size()!=ids.size()) {exit 400,'处理清单须包含1至100个不重复表ID'}
        // 先对完整ID集合校验，不能通过分页或静默忽略无权ID扩大处理范围。
        var allowed=db.selectOne(context.cte+' SELECT COUNT(*) total FROM table_scope t WHERE t.tid IN (#{ids})')
        if(allowed.total!=ids.size()) {exit 403,'处理清单包含已失效、跨租户或无权访问的数据表'}
        whereSql=whereSql+' AND t.tid IN (#{ids}) '
    }
    fields={name:'t.table_name',tableName:'t.table_name',source:'s.db_name',createdTime:'t.created_time',updatedTime:'t.updated_time',columnCount:'t.field_count'}
} else if(kind=='sources') {
    sql=context.cte+"""
        SELECT s.datasource_id id,s.db_name name,s.db_name source,s.asset_status,s.connection_status,
               s.created_time,s.updated_time,COALESCE(ss.table_count,0) table_count,
               COALESCE(ss.marked_count,0) marked_count,j.status collection_status
        FROM source_scope s LEFT JOIN source_stats ss ON ss.datasource_id=s.datasource_id
        LEFT JOIN latest_job j ON j.datasource_id=s.source_key
    """
    var stage=body?.stage==null?'':body.stage+''
    if(stage=='explore') {whereSql=whereSql+" AND COALESCE(ss.table_count,0)=0 AND COALESCE(j.status,'')<>'SUCCEEDED' "}
    else if(stage=='dictionary') {whereSql=whereSql+' AND NOT (COALESCE(ss.table_count,0)>0 AND COALESCE(ss.marked_count,0)=ss.table_count AND COALESCE(ss.dictionary_count,0)=COALESCE(ss.dictionary_saved,0)) '}
    else if(stage=='business') {whereSql=whereSql+' AND NOT (COALESCE(ss.table_count,0)>0 AND COALESCE(ss.marked_count,0)=ss.table_count AND COALESCE(ss.business_count,0)=COALESCE(ss.business_saved,0)) '}
    else if(stage=='review') {whereSql=whereSql+' AND COALESCE(s.asset_status,0)<>2 '}
    else if(stage!='') {exit 400,'登记阶段无效'}
    if(keyword!='') {whereSql=whereSql+' AND s.db_name LIKE #{keywordLike} '}
    fields={name:'s.db_name',createdTime:'s.created_time',updatedTime:'s.updated_time',tableCount:'ss.table_count',markedCount:'ss.marked_count'}
} else {
    sql=context.cte+' SELECT k.tid id,k.task_name name,k.db_name source,k.datasource_id,k.source_table_id,k.table_name,k.runtime_state,k.monitor_time,k.monitor_status,k.monitor_msg,k.delay_ms_estimate,k.queued_count,k.flow_files_in,k.flow_files_out,k.active_thread_count,k.last_running,k.end_running,k.updated_time,k.created_time FROM task_scope k '
    if(keyword!='') {whereSql=whereSql+' AND (k.task_name LIKE #{keywordLike} OR k.db_name LIKE #{keywordLike}) '}
    fields={name:'k.task_name',createdTime:'k.created_time',updatedTime:'k.updated_time'}
}
var column=tableSort.field(body,fields,keyword==''?'updatedTime':'name')
var direction=tableSort.direction(body)
var stable=kind=='tasks'?'k.tid':kind=='sources'?'s.datasource_id':'t.tid'
var order=' ORDER BY CASE WHEN '+column+' IS NULL THEN 1 ELSE 0 END,'+column+' '+direction+','+stable+' ASC'
var page=db.page(sql+whereSql+order,pageSize,(pageNo-1)*pageSize)
return {list:page.list,total:page.total,pageNo:pageNo,pageSize:pageSize,mode:context.mode,scope:context.scope}
