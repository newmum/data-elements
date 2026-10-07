{
  "properties": {},
  "id": "03831b3087e643fca3665f5908ab126c",
  "script": null,
  "groupId": "86af6a92565d4dc38074fdc22b9127f6",
  "name": "02.导入流程",
  "createTime": 1782955344686,
  "updateTime": 1784446270728,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/import",
  "method": "POST",
  "parameters": [
    {
      "name": "file",
      "value": null,
      "description": "真实数据示例参数：file",
      "required": false,
      "dataType": "MultipartFile",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行导入流程，返回保存或配置结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.导入流程
// 路由：POST /sym/approval/config/import
// 业务说明：校验请求资料后执行导入流程，返回保存或配置结果。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
/*
 * 审批中心 - 导入流程
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import org.dromara.warm.flow.core.service.DefService as defService;

defService.importIs(file.getInputStream());
return;
