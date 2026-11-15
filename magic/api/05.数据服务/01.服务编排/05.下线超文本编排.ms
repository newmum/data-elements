{
  "properties": {},
  "id": "3d31df37f14553e48a5721cf3e073309",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "05.下线超文本编排",
  "path": "/offline",
  "method": "POST",
  "description": "按认证租户和关联服务权限下线HTTP编排",
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

// 接口：下线HTTP编排。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'offline',flowId:param.id,body:body})
