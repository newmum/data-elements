{
  "properties": {},
  "id": "492aa26450c85c2ca40ea4c7e77d33d3",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "02.调试超文本编排",
  "path": "/debug",
  "method": "POST",
  "description": "按认证租户和关联服务权限调试HTTP编排",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的编排流程标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：调试HTTP编排。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'debug',flowId:param.id,body:body})
