{
  "properties": {},
  "id": "63a6b386f1d54d05aa0a622aa92d4510",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "02.保存数据接入任务",
  "createTime": 1782955344789,
  "updateTime": 1786191945693,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dataCatalogId\": \"CAT-20240522\",\n    \"taskName\": \"订单数据同步任务\",\n    \"taskTypeName\": \"增量同步\",\n    \"taskTypeId\": \"SYNC_02\",\n    \"taskDesc\": \"从业务库定时抓取订单增量数据至数仓\",\n    \"sourceDbId\": \"DB_SOURCE_001\",\n    \"sourceTableId\": \"order_main\",\n    \"sourceTablePrimaryKey\": \"id\",\n    \"sourceTableIncrementKey\": \"update_time\",\n    \"targetDbId\": \"DB_TARGET_001\",\n    \"targetTableId\": \"ods_order_main\",\n    \"targetTablePrimaryKey\": \"id\",\n    \"scheduleStrategy\": 2,\n    \"scheduleCycle\": 0,\n    \"scheduleRunning\": \"2024-05-22 01:00:00\",\n    \"scheduleRunningStart\": \"2024-05-01 00:00:00\",\n    \"scheduleFaildRetry\": 3,\n    \"cronExpress\": \"0 0 1 * * ?\",\n    \"taskStatus\": 0,\n    \"lastRunning\": \"2024-05-21 01:00:05\",\n    \"lastStatusValue\": \"COMPLETED\",\n    \"endRunning\": \"2024-05-21 01:05:20\",\n    \"tenantId\": \"T-9527\",\n    \"columnMapping\": [\n        {\n            \"sourceField\": \"order_id\",\n            \"sourceFieldCn\": \"订单编号\",\n            \"sourceFieldLength\": 32,\n            \"sourceDataType\": \"VARCHAR\",\n            \"targetField\": \"id\",\n            \"targetFieldCn\": \"主键ID\",\n            \"targetFieldLength\": 32,\n            \"targetDataType\": \"VARCHAR\",\n            \"dictEnable\": 0,\n            \"funcEnable\": 0\n        },\n        {\n            \"sourceField\": \"user_gender\",\n            \"sourceFieldCn\": \"用户性别\",\n            \"sourceFieldLength\": 1,\n            \"sourceDataType\": \"INT\",\n            \"targetField\": \"gender_code\",\n            \"targetFieldCn\": \"性别代码\",\n            \"targetFieldLength\": 1,\n            \"targetDataType\": \"INT\",\n            \"dictEnable\": 1,\n            \"dictValue\": \"性别映射表\",\n            \"dictCode\": \"DICT_GENDER\",\n            \"funcEnable\": 0\n        },\n        {\n            \"sourceField\": \"total_amount\",\n            \"sourceFieldCn\": \"总金额\",\n            \"sourceFieldLength\": 12,\n            \"sourceDataType\": \"DECIMAL\",\n            \"targetField\": \"total_price\",\n            \"targetFieldCn\": \"支付金额\",\n            \"targetFieldLength\": 12,\n            \"targetDataType\": \"DECIMAL\",\n            \"dictEnable\": 0,\n            \"funcEnable\": 1,\n            \"funcValue\": \"金额单位转换\",\n            \"funcCode\": \"FUNC_CURRENCY_CONVERT\"\n        },\n        {\n            \"sourceField\": \"user_mobile\",\n            \"sourceFieldCn\": \"手机号\",\n            \"sourceFieldLength\": 11,\n            \"sourceDataType\": \"VARCHAR\",\n            \"targetField\": \"phone_secret\",\n            \"targetFieldCn\": \"脱敏手机号\",\n            \"targetFieldLength\": 11,\n            \"targetDataType\": \"VARCHAR\",\n            \"dictEnable\": 0,\n            \"funcEnable\": 1,\n            \"funcValue\": \"手机号脱敏\",\n            \"funcCode\": \"FUNC_MOBILE_MASK\"\n        },\n        {\n            \"sourceField\": \"create_time\",\n            \"sourceFieldCn\": \"创建时间\",\n            \"sourceFieldLength\": 0,\n            \"sourceDataType\": \"DATETIME\",\n            \"targetField\": \"ods_create_time\",\n            \"targetFieldCn\": \"同步录入时间\",\n            \"targetFieldLength\": 0,\n            \"targetDataType\": \"DATETIME\",\n            \"dictEnable\": 0,\n            \"funcEnable\": 0\n        }\n    ],\n    \"tid\": \"2e83a7abf33140c7a3df27d0c79e98c1\",\n    \"processGroupId\": \"测试\",\n    \"processGroupVersion\": \"测试\",\n    \"scheduleRunningEnd\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n        \"dataCatalogId\": \"CAT-20240522\",\n        \"taskName\": \"订单数据同步任务\",\n        \"taskTypeName\": \"增量同步\",\n        \"taskTypeId\": \"SYNC_02\",\n        \"taskDesc\": \"从业务库定时抓取订单增量数据至数仓\",\n        \"sourceDbId\": \"DB_SOURCE_001\",\n        \"sourceTableId\": \"order_main\",\n        \"sourceTablePrimaryKey\": \"id\",\n        \"sourceTableIncrementKey\": \"update_time\",\n        \"targetDbId\": \"DB_TARGET_001\",\n        \"targetTableId\": \"ods_order_main\",\n        \"targetTablePrimaryKey\": \"id\",\n        \"scheduleStrategy\": 2,\n        \"scheduleCycle\": 0,\n        \"scheduleRunning\": \"2024-05-22 01:00:00\",\n        \"scheduleRunningStart\": \"2024-05-01 00:00:00\",\n        \"scheduleFaildRetry\": 3,\n        \"cronExpress\": \"0 0 1 * * ?\",\n        \"taskStatus\": 0,\n        \"lastRunning\": \"2024-05-21 01:00:05\",\n        \"lastStatusValue\": \"COMPLETED\",\n        \"endRunning\": \"2024-05-21 01:05:20\",\n        \"tenantId\": \"T-9527\",\n        \"revision\": null,\n        \"createdBy\": null,\n        \"createdTime\": \"2026-03-02 11:10:22\",\n        \"updatedBy\": null,\n        \"updatedTime\": \"2026-03-02 11:10:22\",\n        \"isDel\": 0,\n        \"columnMapping\": [\n            {\n                \"tid\": \"ef0076399f5245498ead8e34febc26f5\",\n                \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n                \"sourceField\": \"order_id\",\n                \"sourceFieldCn\": \"订单编号\",\n                \"sourceFieldLength\": 32,\n                \"sourceDataType\": \"VARCHAR\",\n                \"targetField\": \"id\",\n                \"targetFieldCn\": \"主键ID\",\n                \"targetFieldLength\": 32,\n                \"targetDataType\": \"VARCHAR\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 0,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 11:10:22\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 11:10:22\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"b24f5169c9e843e8bd5c2ad26f676c28\",\n                \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n                \"sourceField\": \"user_gender\",\n                \"sourceFieldCn\": \"用户性别\",\n                \"sourceFieldLength\": 1,\n                \"sourceDataType\": \"INT\",\n                \"targetField\": \"gender_code\",\n                \"targetFieldCn\": \"性别代码\",\n                \"targetFieldLength\": 1,\n                \"targetDataType\": \"INT\",\n                \"dictEnable\": 1,\n                \"dictValue\": \"性别映射表\",\n                \"dictCode\": \"DICT_GENDER\",\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 1,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 11:10:22\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 11:10:22\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"6aeffbf49a6d41ecaf8bb5c94c5d2140\",\n                \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n                \"sourceField\": \"total_amount\",\n                \"sourceFieldCn\": \"总金额\",\n                \"sourceFieldLength\": 12,\n                \"sourceDataType\": \"DECIMAL\",\n                \"targetField\": \"total_price\",\n                \"targetFieldCn\": \"支付金额\",\n                \"targetFieldLength\": 12,\n                \"targetDataType\": \"DECIMAL\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 1,\n                \"funcValue\": \"金额单位转换\",\n                \"funcCode\": \"FUNC_CURRENCY_CONVERT\",\n                \"sortNo\": 2,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 11:10:22\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 11:10:22\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"e94d588077fd46c8813348eadc1bfea2\",\n                \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n                \"sourceField\": \"user_mobile\",\n                \"sourceFieldCn\": \"手机号\",\n                \"sourceFieldLength\": 11,\n                \"sourceDataType\": \"VARCHAR\",\n                \"targetField\": \"phone_secret\",\n                \"targetFieldCn\": \"脱敏手机号\",\n                \"targetFieldLength\": 11,\n                \"targetDataType\": \"VARCHAR\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 1,\n                \"funcValue\": \"手机号脱敏\",\n                \"funcCode\": \"FUNC_MOBILE_MASK\",\n                \"sortNo\": 3,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 11:10:22\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 11:10:22\",\n                \"isDel\": 0\n            },\n            {\n                \"tid\": \"e54dba14592648b3a345a20d371b3f9b\",\n                \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n                \"sourceField\": \"create_time\",\n                \"sourceFieldCn\": \"创建时间\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"DATETIME\",\n                \"targetField\": \"ods_create_time\",\n                \"targetFieldCn\": \"同步录入时间\",\n                \"targetFieldLength\": 0,\n                \"targetDataType\": \"DATETIME\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null,\n                \"sortNo\": 4,\n                \"tenantId\": \"T-9527\",\n                \"revision\": null,\n                \"createdBy\": null,\n                \"createdTime\": \"2026-03-02 11:10:22\",\n                \"updatedBy\": null,\n                \"updatedTime\": \"2026-03-02 11:10:22\",\n                \"isDel\": 0\n            }\n        ]\n    }\n}",
  "description": "Magic 负责参数校验、任务与字段映射持久化以及 NiFi 进程组创建编排；使用请求参数 taskName、taskTypeId、tid、processGroupId、processGroupVersion、dataCatalogId、taskTypeName、taskDesc；涉及 data_access_agg_task_t、data_access_field_mapping 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.保存数据接入任务
// 路由：POST /ods/task/saveOrUpdate
// 业务说明：Magic 负责参数校验、任务与字段映射持久化以及 NiFi 进程组创建编排；使用请求参数 taskName、taskTypeId、tid、processGroupId、processGroupVersion、dataCatalogId、taskTypeName、taskDesc；涉及 data_access_agg_task_t、data_access_field_mapping 等业务表。
/*
 * 保存数据接入任务
 *
 * Magic 负责参数校验、任务与字段映射持久化以及 NiFi 进程组创建编排。
 * NiFiClient 仅作为鉴权和 HTTP 运行时适配器使用。更新任务时，仅在父分组
 * 发生变化后创建新进程组并清理旧组，避免无意义地重建运行资源。
 */
import cn.hutool.core.util.IdUtil
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import log
import tenantRuntime
if (body == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '请求参数不能为空'
}
var tenantId = tenantRuntime.id()
// 解析请求体并为可选参数设置默认值。
if (body.taskName == null || body.taskName == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'taskName不能为空'
}
if (body.taskTypeId == null || body.taskTypeId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'taskTypeId不能为空'
}

var now = new Date()
var tid = body.tid
var updating = tid != null && tid != ''
var old = updating
    // 从 MySQL 业务表读取当前有效数据。
    ? db.table('data_access_agg_task_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
    : null
if (updating && old == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '任务不存在'
}

var processGroupId = body.processGroupId || old?.processGroupId
var processGroupVersion = body.processGroupVersion || old?.processGroupVersion
var needProcessGroup = !updating || old.taskTypeId != body.taskTypeId
if (needProcessGroup) {
    var request = {
        revision: { version: 0 },
        disconnectedNodeAcknowledged: false,
        component: {
            position: { x: 0, y: 0 },
            name: body.taskName
        }
    }
    var created = nifiClient.post(
        '/process-groups/' + body.taskTypeId + '/process-groups',
        request,
        JsonNode.class
    )
    processGroupId = created.path('id').asText()
    if (processGroupId == null || processGroupId == '') {
        processGroupId = created.path('component').path('id').asText()
    }
    processGroupVersion = created.path('revision').path('version').asInt(0)
    if (processGroupId == null || processGroupId == '') {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, 'NiFi进程组创建失败'
    }
}

var task = {
    tid: updating ? tid : IdUtil.getSnowflakeNextIdStr(),
    data_catalog_id: body.dataCatalogId,
    task_name: body.taskName,
    task_type_name: body.taskTypeName,
    task_type_id: body.taskTypeId,
    task_desc: body.taskDesc,
    source_db_id: body.sourceDbId,
    source_table_id: body.sourceTableId,
    source_table_primary_key: body.sourceTablePrimaryKey,
    source_table_increment_key: body.sourceTableIncrementKey,
    target_db_id: body.targetDbId,
    target_table_id: body.targetTableId,
    target_table_primary_key: body.targetTablePrimaryKey,
    schedule_strategy: body.scheduleStrategy,
    schedule_cycle: body.scheduleCycle,
    schedule_running: body.scheduleRunning,
    schedule_running_start: body.scheduleRunningStart,
    schedule_running_end: body.scheduleRunningEnd,
    schedule_faild_retry: body.scheduleFaildRetry,
    cron_express: body.cronExpress,
    task_status: body.taskStatus,
    last_running: body.lastRunning,
    last_status_value: body.lastStatusValue,
    end_running: body.endRunning,
    tenant_id: tenantId,
    process_group_id: processGroupId,
    process_group_version: processGroupVersion,
    updated_time: now
}

if (updating) {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_access_agg_task_t').primary('tid').update(task)
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_access_field_mapping').where()
        .eq('task_id', tid).eq('is_del', 0)
        .update({is_del: 1, updated_time: now})
} else {
    tid = task.tid
    task.is_del = 0
    task.created_time = now
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_access_agg_task_t').primary('tid').insert(task)
}

var mappings = []
for (index, item in body.columnMapping || []) {
    mappings.add({
        tid: IdUtil.getSnowflakeNextIdStr(),
        task_id: tid,
        source_field: item.sourceField,
        source_field_cn: item.sourceFieldCn,
        source_field_length: item.sourceFieldLength,
        source_data_type: item.sourceDataType,
        target_field: item.targetField,
        target_field_cn: item.targetFieldCn,
        target_field_length: item.targetFieldLength,
        target_data_type: item.targetDataType,
        dict_enable: item.dictEnable,
        dict_value: item.dictValue,
        dict_code: item.dictCode,
        func_enable: item.funcEnable,
        func_value: item.funcValue,
        func_code: item.funcCode,
        sort_no: index,
        tenant_id: tenantId,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}
if (mappings.size() > 0) {
    // 从 MySQL 业务表读取当前有效数据。
    db.table('data_access_field_mapping').batchInsert(mappings)
}

if (updating && needProcessGroup && old.processGroupId != null && old.processGroupId != '') {
    try {
        nifiClient.cleanupProcessGroup(old.processGroupId)
    } catch (e) {
        log.warn('旧NiFi进程组清理失败, processGroupId={}, message={}', old.processGroupId, e.message)
    }
}

// 从 MySQL 业务表读取当前有效数据。
var result = db.table('data_access_agg_task_t').where()
    .eq('tid', tid).eq('is_del', 0).selectOne()
// 从 MySQL 业务表读取当前有效数据。
result.columnMapping = db.table('data_access_field_mapping').where()
    .eq('task_id', tid).eq('is_del', 0)
    .orderBy('sort_no', 'asc').select()
// 返回当前业务分支的标准结果。
return result
