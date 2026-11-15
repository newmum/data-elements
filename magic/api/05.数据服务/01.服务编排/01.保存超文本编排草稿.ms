{
  "properties": {},
  "id": "334f3354e13e57c89cbbf359dcf39e9c",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "01.保存超文本编排草稿",
  "path": "/draft",
  "method": "PUT",
  "description": "按认证租户和关联服务权限保存HTTP编排草稿",
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

// 接口：保存HTTP编排草稿。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'save',flowId:param.id,body:body})
