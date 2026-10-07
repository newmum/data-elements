{
  "properties": {},
  "id": "47f25be1c4eb4ed89c6f3e7fb36dfd17",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "04.根据id查询接入任务信息",
  "createTime": 1782955344810,
  "updateTime": 1784437771822,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"3147156bbcc54704822f534f93a7913f\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"3147156bbcc54704822f534f93a7913f\",\n        \"dataCatalogId\": \"CAT-20240522\",\n        \"taskName\": \"订单数据同步任务\",\n        \"taskTypeName\": \"增量同步\",\n        \"taskTypeId\": \"SYNC_02\",\n        \"taskDesc\": \"从业务库定时抓取订单增量数据至数仓\",\n        \"sourceDbId\": \"DB_SOURCE_001\",\n        \"sourceTableId\": \"order_main\",\n        \"sourceTablePrimaryKey\": \"id\",\n        \"sourceTableIncrementKey\": \"update_time\",\n        \"targetDbId\": \"DB_TARGET_001\",\n        \"targetTableId\": \"ods_order_main\",\n        \"targetTablePrimaryKey\": \"id\",\n        \"scheduleStrategy\": 2,\n        \"scheduleCycle\": 0,\n        \"scheduleRunning\": \"2024-05-22 01:00:00\",\n        \"scheduleRunningStart\": \"2024-05-01 00:00:00\",\n        \"scheduleFaildRetry\": 3,\n        \"cronExpress\": \"0 0 1 * * ?\",\n        \"taskStatus\": 0,\n        \"lastRunning\": \"2024-05-21 01:00:05\",\n        \"lastStatusValue\": \"COMPLETED\",\n        \"endRunning\": \"2024-05-21 01:05:20\",\n        \"tenantId\": \"T-9527\",\n        \"revision\": null,\n        \"createdBy\": null,\n        \"createdTime\": \"2026-03-02 10:52:50\",\n        \"updatedBy\": null,\n        \"updatedTime\": \"2026-03-02 10:52:50\",\n        \"isDel\": 0,\n        \"columnMapping\": [\n            {\n                \"tid\": \"34d5a68192ff4d9ca29e87ce3f748eb4\",\n                \"taskId\": \"3147156bbcc54704822f534f93a7913f\",\n                \"sourceField\": \"order_id\",\n                \"sourceFieldCn\": \"订单编号\",\n                \"sourceFieldLength\": 32,\n                \"sourceDataType\": \"VARCHAR\",\n                \"targetField\": \"id\",\n                \"targetFieldCn\": \"主键ID\",\n                \"targetFieldLength\": 32,\n                \"targetDataType\": \"VARCHAR\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 0,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 10:52:50\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 10:52:50\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"2126a6f1b8c74044861451d9424caa9e\",\n                \"taskId\": \"3147156bbcc54704822f534f93a7913f\",\n                \"sourceField\": \"user_gender\",\n                \"sourceFieldCn\": \"用户性别\",\n                \"sourceFieldLength\": 1,\n                \"sourceDataType\": \"INT\",\n                \"targetField\": \"gender_code\",\n                \"targetFieldCn\": \"性别代码\",\n                \"targetFieldLength\": 1,\n                \"targetDataType\": \"INT\",\n                \"dictEnable\": 1,\n                \"dictValue\": \"性别映射表\",\n                \"dictCode\": \"DICT_GENDER\",\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 1,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 10:52:50\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 10:52:50\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"a993181fdeba41a2a3ca7b6248dcbf4b\",\n                \"taskId\": \"3147156bbcc54704822f534f93a7913f\",\n                \"sourceField\": \"total_amount\",\n                \"sourceFieldCn\": \"总金额\",\n                \"sourceFieldLength\": 12,\n                \"sourceDataType\": \"DECIMAL\",\n                \"targetField\": \"total_price\",\n                \"targetFieldCn\": \"支付金额\",\n                \"targetFieldLength\": 12,\n                \"targetDataType\": \"DECIMAL\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 1,\n                \"funcValue\": \"金额单位转换\",\n                \"funcCode\": \"FUNC_CURRENCY_CONVERT\",\n                \"sortNo\": 2,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 10:52:50\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 10:52:50\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"0ad290ef16dc46d8b57fb7793e95b404\",\n                \"taskId\": \"3147156bbcc54704822f534f93a7913f\",\n                \"sourceField\": \"user_mobile\",\n                \"sourceFieldCn\": \"手机号\",\n                \"sourceFieldLength\": 11,\n                \"sourceDataType\": \"VARCHAR\",\n                \"targetField\": \"phone_secret\",\n                \"targetFieldCn\": \"脱敏手机号\",\n                \"targetFieldLength\": 11,\n                \"targetDataType\": \"VARCHAR\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 1,\n                \"funcValue\": \"手机号脱敏\",\n                \"funcCode\": \"FUNC_MOBILE_MASK\",\n                \"sortNo\": 3,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 10:52:50\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 10:52:50\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"6f5aadc763d8419daf290d36de43469a\",\n                \"taskId\": \"3147156bbcc54704822f534f93a7913f\",\n                \"sourceField\": \"create_time\",\n                \"sourceFieldCn\": \"创建时间\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"DATETIME\",\n                \"targetField\": \"ods_create_time\",\n                \"targetFieldCn\": \"同步录入时间\",\n                \"targetFieldLength\": 0,\n                \"targetDataType\": \"DATETIME\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 4,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 10:52:50\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 10:52:50\",\n                \"isDel\": 0\n            }\n        ]\n    }\n}",
  "description": "按接入任务标识读取未删除的主记录及字段映射，并按保存顺序组装编辑页面详情。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.根据id查询接入任务信息
// 路由：POST /ods/task/queryById
// 业务说明：按接入任务标识读取未删除的主记录及字段映射，并按保存顺序组装编辑页面详情。
/*
 * 数据接入任务详情
 *
 * 主表和字段映射均以逻辑删除标记为准。字段映射按 sort_no 返回，
 * 保证编辑页面再次进入时顺序与保存时一致。
 */
var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tid不能为空'
}

// 从 MySQL 业务表读取当前有效数据。
var task = db.table('data_access_agg_task_t').where()
    .eq('tid', tid).eq('is_del', 0).selectOne()
if (task == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '任务不存在'
}

// 从 MySQL 业务表读取当前有效数据。
task.columnMapping = db.table('data_access_field_mapping').where()
    .eq('task_id', tid).eq('is_del', 0)
    .orderBy('sort_no', 'asc').select()
// 返回当前业务分支的标准结果。
return task
