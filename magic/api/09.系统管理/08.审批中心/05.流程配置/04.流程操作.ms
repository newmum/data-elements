{
  "properties": {},
  "id": "c07b80ac993947689394afcb66e3efba",
  "script": null,
  "groupId": "86af6a92565d4dc38074fdc22b9127f6",
  "name": "04.流程操作",
  "createTime": 1782955344704,
  "updateTime": 1784446270733,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/exec",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"id\": \"2036256483356024834\",\n    \"action\": \"copy\",\n    \"file\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据 action 与流程标识调用审批流程服务执行对应操作，并返回操作结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.流程操作
// 路由：POST /sym/approval/config/exec
// 业务说明：根据 action 与流程标识调用审批流程服务执行对应操作，并返回操作结果。
/*
 * 审批中心 - 流程操作
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import org.dromara.warm.flow.core.service.DefService as defService;
import response;
import org.springframework.cglib.beans.BeanMap;

// 解析请求体并为可选参数设置默认值。
var id = body.id;
action = body.action;

if (!action) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit - 1, "请指定操作"
}
if (!id){
  // 参数或业务状态不满足要求时立即终止请求。
  exit - 1, "流程定义id不能为空"
}
// 从 MySQL 业务表读取当前有效数据。
var flow = db.table("flow_definition").where().eq("id", id).selectOne()
if (!flow) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit - 1, "不存在此流程定义"
}
flow.id = flow.id+""
id = id.asLong();

if (action == 'detail'){
    // 返回当前业务分支的标准结果。
    return flow
}

if (action == 'export'){
    var json = defService.exportJson(id);
    // 返回当前业务分支的标准结果。
    return response.download(json,  flow.flowCode + '.json');
}

if (action == 'import'){
    defService.importIs(body.file.getInputStream());
    return;
}

if (action == 'copy'){
    // 返回当前业务分支的标准结果。
    return defService.copyDef(id);
}

if (action == 'publish'){
    // 返回当前业务分支的标准结果。
    return defService.publish(id);
}

if (action == 'unPublish'){
    // 返回当前业务分支的标准结果。
    return defService.unPublish(id);
}

if (action == 'unActive'){
    // 返回当前业务分支的标准结果。
    return defService.unActive(id);
}

if (action == 'active'){
    // 返回当前业务分支的标准结果。
    return defService.active(id);
}

if (action == 'delete'){
    // 返回当前业务分支的标准结果。
    return defService.removeDef([id]);
}
