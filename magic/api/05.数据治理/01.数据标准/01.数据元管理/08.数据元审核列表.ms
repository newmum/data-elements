{
  "properties": {},
  "id": "wx_std_review_page_01",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "08.数据元审核列表",
  "createTime": 1789337365631,
  "updateTime": 1789337443530,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/review/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户、审核状态和分页参数读取待审数据元工单，返回列表与总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.数据元审核列表
// 路由：POST /dwm/standard/element/review/page
// 业务说明：按当前租户、审核状态和分页参数读取待审数据元工单，返回列表与总数。
import tenantRuntime
var tenantId=tenantRuntime.id();var page=body?.page::long(1);var size=body?.size::long(20);var status=body?.status;var rows=db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('asset_type','standard_element').eq('issue_type','STANDARD_REVIEW').eq('is_del',0);if(status != null && status != '') {rows=rows.eq('status',status)};return {list:rows.orderBy('updated_time','desc').page(size,(page-1)*size),total:rows.count(),page:page,size:size}
