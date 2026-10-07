{
  "properties": {},
  "id": "f058946e714846049e38be378047d1c6",
  "script": null,
  "groupId": "86af6a92565d4dc38074fdc22b9127f6",
  "name": "01.保存或修改流程",
  "createTime": 1782955344677,
  "updateTime": 1786191950313,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"flowName\": \"test\",\n    \"flowCode\": \"test\",\n    \"modelValue\": \"CLASSICS\",\n    \"category\": \"测试\",\n    \"listenerType\": \"app\",\n    \"listenerPath\": \"测试\",\n    \"id\": \"2036256483356024834\",\n    \"version\": \"测试\",\n    \"isPublish\": true,\n    \"formCustom\": \"测试\",\n    \"formPath\": \"测试\",\n    \"activityStatus\": 2,\n    \"tenantId\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "审批中心 - 保存或修改流程；使用请求参数 id、flowCode、flowName、modelValue、category、version、isPublish、formCustom；涉及 flow_definition 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.保存或修改流程
// 路由：POST /sym/approval/config/saveOrUpdate
// 业务说明：审批中心 - 保存或修改流程；使用请求参数 id、flowCode、flowName、modelValue、category、version、isPublish、formCustom；涉及 flow_definition 等业务表。
/*
 * 审批中心 - 保存或修改流程
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import org.dromara.warm.flow.core.service.DefService as defService;
import cn.hutool.core.util.IdUtil
import "@post:/sym/user/me" as me
import tenantRuntime
// 解析请求体并为可选参数设置默认值。
var id = body.id
var isUpdate = !!body.id
if (!isUpdate){
  id = IdUtil.getSnowflakeNextIdStr()
}

var user = me
var tenantId = tenantRuntime.id()

// 将校验后的业务数据写入 MySQL 主表。
return db.table("flow_definition").primary("id").update({
    id: id.asLong(),
    flow_code: body.flowCode,
    flow_name: body.flowName,
    model_value: body.modelValue,
    category: body.category,
    version: body.version,
    is_publish: body.isPublish,
    form_custom: body.formCustom,
    form_path: body.formPath,
    activity_status: body.activityStatus,
    listener_type: body.listenerType,
    listener_path: body.listenerPath,
    create_time: isUpdate ? null : new Date(),
    create_by: "admin",
    update_time: new Date(),
    update_by: "admin",
    tenant_id: tenantId,
})
