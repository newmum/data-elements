{
  "properties": {},
  "id": "nifi_map_plan_get_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "03.查询映射计划版本",
  "path": "/detail",
  "description": "读取并核对已授权流程的固定计划版本。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingPlanDetail' as read

// GET /nifi/plan/detail 查询映射计划版本。 查询参数 id。
// GET /nifi/plan/detail 读取并核对已授权流程的固定计划版本。
// GET 和 HEAD 共用 @/common/nifiMappingPlanDetail 的业务读取。

return read(param.id)
