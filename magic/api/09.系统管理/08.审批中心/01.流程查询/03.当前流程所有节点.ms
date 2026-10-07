{
  "properties": {},
  "id": "5464e3d758ff45b9b337258b587e5151",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "03.当前流程所有节点",
  "createTime": 1782955344460,
  "updateTime": 1784446270674,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/nodes",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"definitionId\": \"2034448996486922200\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "审批中心 - 当前流程所有节点；使用请求参数 definitionId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.当前流程所有节点
// 路由：POST /sym/approval/query/nodes
// 业务说明：审批中心 - 当前流程所有节点；使用请求参数 definitionId。
/*
 * 审批中心 - 当前流程所有节点
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.InsService as insService
import org.dromara.warm.flow.core.service.TaskService as taskService
import org.dromara.warm.flow.core.service.NodeService as nodeService
import log

// 解析请求体并为可选参数设置默认值。
var list = nodeService.getByDefId(body?.definitionId::long)
// 返回当前业务分支的标准结果。
return list
