{
  "properties": {},
  "id": "f541570df3bc4bf3a38e9466d2e16cd7",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "06.任务监控快照分页",
  "createTime": 1784439136875,
  "updateTime": 1784439136875,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitorSnapPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"taskId\": \"测试\",\n    \"pipelineId\": \"测试\",\n    \"processGroupId\": \"测试\",\n    \"delayLevel\": \"测试\",\n    \"monitorStatus\": 2,\n    \"beginTime\": \"2026-07-20 10:00:00\",\n    \"endTime\": \"2026-07-20 10:00:00\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤；使用请求参数 pageNum、pageSize、taskId、pipelineId、processGroupId、delayLevel、monitorStatus、beginTime；涉及 data_access_task_monitor_snap_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import tableSort
var sortColumn = tableSort.field(body, {'delayMsEstimate':'delay_ms_estimate','thresholdMs':'threshold_ms','queuedBytes':'queued_bytes','monitorTime': 'monitor_time', 'createdTime': 'created_time', 'queuedCount': 'queued_count', 'flowFilesIn': 'flow_files_in', 'flowFilesOut': 'flow_files_out', 'bytesIn': 'bytes_in', 'bytesOut': 'bytes_out', 'activeThreadCount': 'active_thread_count'}, 'monitorTime')
var sortDirection = tableSort.direction(body)

// 接口：06.任务监控快照分页
// 路由：POST /ods/task/monitorSnapPage
// 业务说明：支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤；使用请求参数 pageNum、pageSize、taskId、pipelineId、processGroupId、delayLevel、monitorStatus、beginTime；涉及 data_access_task_monitor_snap_t 等业务表。
/*
 * 数据接入任务运行快照分页
 *
 * 支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤。
 * 本接口只读持久化快照，不触发 NiFi 运行时调用。
 */
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = Math.min(100,pageSize < 1 ? 20 : pageSize)

// 从 MySQL 业务表读取当前有效数据。
import tenantRuntime
var tenantId=tenantRuntime.id()
if(tenantId==null || tenantId=='') {exit 401,'当前登录租户无效'}
import dataScope
var scope=dataScope.dataSource();
var scopeMode=scope?.scope;
var scopeUserId=scope?.userId;
var scopeOrgIds=scope?.organizationIds||[];
if(!['ALL','ORG','OWNER'].contains(scopeMode)||scopeUserId==null) {exit 403,'无法确定当前数据范围';}
var sourceScope=scopeMode=='ALL'?'1=1':scopeMode=='OWNER'?'d.created_by=#{scopeUserId}':scopeOrgIds.size()==0?'1=0':'d.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>';
var filter='';
for(key in ['taskId','pipelineId','processGroupId','delayLevel','monitorStatus','beginTime','endTime']) {
 if(body[key]!=null&&body[key]!='') {
   if((''+body[key]).length()>100) {exit 400,'筛选内容过长';}
   var columns={taskId:'task_id',pipelineId:'pipeline_id',processGroupId:'process_group_id',delayLevel:'delay_level',monitorStatus:'monitor_status',beginTime:'monitor_time',endTime:'monitor_time'};
   var op=key=='beginTime'?'>=':key=='endTime'?'<=':'=';
   filter=filter+' AND s.'+columns.get(key)+op+'#{body.'+key+'}';
 }
}
var result=db.page("""
 SELECT s.*,s.monitor_time monitorTime,s.monitor_status monitorStatus,s.monitor_msg monitorMsg,
 s.delay_level delayLevel,s.queued_count queuedCount,s.flow_files_in flowFilesIn,s.flow_files_out flowFilesOut,
 s.bytes_in bytesIn,s.bytes_out bytesOut,s.active_thread_count activeThreadCount
 FROM data_access_task_monitor_snap_t s
 LEFT JOIN data_access_agg_task_t a ON a.tid=s.task_id AND a.tenant_id=s.tenant_id AND a.is_del=0
 LEFT JOIN db_datasource_t d ON d.tid=a.source_db_id AND d.tenant_id=a.tenant_id AND d.is_del=0
 WHERE s.tenant_id=#{tenantId} AND s.is_del=0 AND (${sourceScope})
 """+filter+' ORDER BY s.'+sortColumn+' '+sortDirection+',s.tid ASC',pageSize,(pageNum-1)*pageSize);
result.list=result.list||[];
return result;
