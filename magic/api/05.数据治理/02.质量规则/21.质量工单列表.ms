{
  "properties": {},
  "id": "wx_quality_work_page_01",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "21.质量工单列表",
  "createTime": 1789337365813,
  "updateTime": 1789337443571,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/workorders/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户、整改状态和分页参数读取质量整改工单，返回列表与总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime;
import tableSort;
var tenantId=tenantRuntime.id();
var page=Math.max(1,body?.page::int(1));
var size=Math.min(100,Math.max(1,body?.size::int(20)));
var status=body?.status;
var rows=db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId)
    .eq('asset_type','quality_issue').eq('issue_type','QUALITY_REMEDIATION').eq('is_del',0);
if(status!=null && status!='') {rows=rows.eq('status',status);}
var field=tableSort.field(body,{title:'title',created_time:'created_time',updated_time:'updated_time',createdTime:'created_time',updatedTime:'updated_time'},'updated_time');
var direction=tableSort.direction(body);
// The builder executes one count and one ordered, bounded page query.
return rows.orderBy(field,direction).orderBy('tid','asc').page(size,(page-1)*size);
