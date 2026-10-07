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
// 接口：21.质量工单列表
// 路由：POST /dwm/quality/workorders/page
// 业务说明：按当前租户、整改状态和分页参数读取质量整改工单，返回列表与总数。
import tenantRuntime
var tenantId=tenantRuntime.id();var page=body?.page::long(1);var size=body?.size::long(20);var status=body?.status;var rows=db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('asset_type','quality_issue').eq('issue_type','QUALITY_REMEDIATION').eq('is_del',0);if(status != null && status != '') {rows=rows.eq('status',status)};return {list:rows.orderBy('updated_time','desc').page(size,(page-1)*size),total:rows.count(),page:page,size:size}
