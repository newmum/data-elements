{
  "properties": {},
  "id": "82d911b837ad4a5bb40bf7f9c2d0fbd8",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "21.工作台变更查询",
  "path": "/workbenchChanges",
  "createTime": 1792844788329,
  "updateTime": null,
  "parameters": [
    {
      "name": "payload",
      "value": null,
      "description": "当前视图、列表类型及分页条件；不接受前端租户或用户标识",
      "required": true,
      "dataType": "Object",
      "type": "java.util.Map",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "context",
      "value": null,
      "description": "已核验的工作台数据范围与SQL片段",
      "required": true,
      "dataType": "Object",
      "type": "java.util.Map",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "description": "批量汇总授权来源最近成功探查的新增数与源端缺失表；新增按来源办理，缺失逐ID及来源复核，固定三次批量读取后服务端分页；结构修改无完整持久化记录时明确保留静态参考，不以检索空结果冒充零。"
}
================================
import cn.hutool.json.JSONUtil
var tenantId=context.tenantId
var userId=context.userId
var scopeOrgIds=context.scopeOrgIds
var pageNo=Math.max(1,payload?.pageNo==null?1:payload.pageNo::int)
var pageSize=Math.min(100,Math.max(1,payload?.pageSize==null?5:payload.pageSize::int))
var changeType=payload?.changeType==null?'all':payload.changeType+''
if(!['all','added','modified','deleted'].contains(changeType)) {exit 400,'变更类型无效'}
var keyword=payload?.keyword==null?'':(payload.keyword+'').trim().toLowerCase()
if(keyword.length()>100) {exit 400,'搜索关键字最多100字'}
var sortField=payload?.sortField==null?'updatedTime':payload.sortField+''
if(!['name','updatedTime'].contains(sortField)) {exit 400,'变更排序字段无效'}
var direction=payload?.sortDir=='asc'?1:-1
// 只读取授权有效快照的精简身份集合，不下载字段配置或逐表查询。
var eligible=db.select(context.cte+' SELECT tid,datasource_id FROM table_scope ORDER BY tid LIMIT 60001')
if(eligible.size()>60000) {return {available:false,reason:'授权表快照超过60000张，尚未配置大规模变更聚合',list:[],total:0}}
var allowed={}
for(row in eligible) {allowed[row.tid+'']=row.datasourceId+''}
var jobs=db.select(context.cte+"""
 SELECT * FROM (SELECT j.datasource_id,s.db_name source,j.added_count,j.deleted_detail,j.finished_time,
   ROW_NUMBER() OVER(PARTITION BY j.datasource_id ORDER BY j.created_time DESC,j.tid DESC) success_rank
 FROM metadata_table_collection_job_t j JOIN source_scope s ON s.source_key=CAST(j.datasource_id AS CHAR(64))
 WHERE j.tenant_id=#{tenantId} AND j.is_del=0 AND j.status='SUCCEEDED') q WHERE success_rank=1
""")
var changed={}
var sourceRows=[]
var addedCount=0
// 新增明细未保存作业关联ID，按来源展示准确新增数，不用创建时间猜测具体新增表。
try {
 for(job in jobs) {
  var amount=job.addedCount==null?0:job.addedCount
  if(amount>0) {
   addedCount=addedCount+amount
   sourceRows.add({id:'source:'+job.datasourceId,datasourceId:job.datasourceId+'',objectType:'source',
    tableName:job.source,comment:'最近探查新增 '+amount+' 张',source:job.source,type:'added',
    time:job.finishedTime,description:'按来源汇总 '+amount+' 张；进入第二步查看已保存表快照',tableCount:amount})
  }
  var details=job.deletedDetail==null||job.deletedDetail==''?[]:JSONUtil.parseArray(job.deletedDetail+'')
  for(item in details) {
   var id=(item.tid==null?item.tableId:item.tid)+''
   if(allowed[id]!=null && allowed[id]==job.datasourceId+'') {changed[id]={type:'deleted',time:job.finishedTime,origin:'collection'}}
  }
 }
} catch(error) {return {available:false,reason:'最近探查差异记录格式异常，暂不能准确统计变更',list:[],total:0}}
var changedIds=[]
for(id in changed.keySet()) {changedIds.add(id)}
if(changedIds.size()>10000) {return {available:false,reason:'待复核变更超过10000张，需分来源办理，当前保留静态参考',list:[],total:0}}
var rows=changedIds.size()==0?[]:db.select(context.cte+"""
 SELECT t.tid id,t.datasource_id,t.table_name,t.table_name_cn,t.table_comment,t.updated_time,s.db_name source
 FROM table_scope t JOIN source_scope s ON s.datasource_id=t.datasource_id WHERE t.tid IN (#{changedIds})
""")
var counts={added:addedCount,modified:null,deleted:0}
var list=sourceRows
for(row in rows) {
 var change=changed[row.id+'']
 row.type=change.type
 row.time=change.time==null?row.updatedTime:change.time
 row.comment=row.tableNameCn||row.tableComment||row.tableName
 row.description=row.type=='deleted'?'最近探查未找到来源表，请进入登记复核，尚未确认物理删除':'保存的结构变更投影，等待复核'
 counts[row.type]=counts[row.type]+1
 list.add(row)
}
list=list.filter(row=>(changeType=='all'||row.type==changeType) && (keyword=='' || ((row.tableName||'')+' '+(row.comment||'')+' '+(row.source||'')).toLowerCase().contains(keyword)))
list=list.sort((a,b)=>{
 var av=sortField=='name'?a.tableName:a.time
 var bv=sortField=='name'?b.tableName:b.time
 if(av==null && bv!=null) {return 1}
 if(av!=null && bv==null) {return -1}
 var compare=av==null?0:(av+'').compareToIgnoreCase(bv+'')
 return compare==0?(a.id+'').compareTo(b.id+''):compare*direction
})
var total=list.size()
var start=Math.min((pageNo-1)*pageSize,total)
return {available:true,modifiedAvailable:false,modifiedReason:'结构修改差异仅存在于单次探查临时结果，尚未持久化完整记录，统计保留静态参考',list:list.subList(start,Math.min(start+pageSize,total)),total:total,counts:counts,pageNo:pageNo,pageSize:pageSize,
 basis:'新增按数据源汇总，点击“查看新增表”进入登记第二步；源端缺失需逐表复核，尚未确认物理删除。结构修改尚无完整持久化记录，修改数保留静态参考，不计入已记录新增/缺失数。'}
